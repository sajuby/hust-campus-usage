package dev.campususage.app.feature.routes.data

import dev.campususage.app.feature.routes.model.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.*
import java.util.concurrent.TimeUnit

data class MetroClock(val raw:String, val time:LocalTime?, val arrivalOnly:Boolean=false, val explicitNextDay:Boolean=false) {
    fun display(first:MetroClock?=null):String {
        if(time==null)return "—"
        val next=explicitNextDay || (first?.time?.let { time.isBefore(it) } == true)
        return (if(next)"次日 "else"")+time.toString()+(if(arrivalOnly)"（到达）"else"")
    }
}
data class MetroDay(val label:String,val first:MetroClock,val last:MetroClock)
enum class MetroOrigin(val label:String) { BUNDLED("内置资料"), CACHED("本机缓存"), NETWORK("已联网更新") }
data class MetroLeg(val key:String,val station:String,val line:String,val direction:String,val code:String?=null,
                    val days:List<MetroDay> = emptyList(),val fetchedAt:Instant?=null,val origin:MetroOrigin=MetroOrigin.BUNDLED,val problem:String?=null) {
    fun regularDay(date:LocalDate):MetroDay? {
        val exact=when(date.dayOfWeek){DayOfWeek.MONDAY->"周一";DayOfWeek.TUESDAY->"周二";DayOfWeek.WEDNESDAY->"周三";DayOfWeek.THURSDAY->"周四";DayOfWeek.FRIDAY->"周五";DayOfWeek.SATURDAY->"周六";DayOfWeek.SUNDAY->"周日"}
        days.singleOrNull { it.label==exact }?.let { return it }
        if(date.dayOfWeek.value<=4)days.singleOrNull { it.label=="周一至周四" }?.let { return it }
        return days.singleOrNull { it.label==if(date.dayOfWeek.value<=5)"工作日"else"休息日" }
    }
    fun officialUrl():String = "https://advh5.whrtmpay.com/H5/iafcSiteInfo/#/timeTable?stationId=${code.orEmpty()}&stationName="+java.net.URLEncoder.encode(station,"UTF-8")
}
data class MetroReport(val supported:Boolean=true,val legs:List<MetroLeg> = emptyList(),val loading:Boolean=false,val message:String?=null)
data class MetroSite(val code:String,val name:String,val order:Int)
data class MetroLine(val code:String,val name:String,val sites:List<MetroSite>)

object OfficialMetroParser {
    // Android 12's ISO_INSTANT parser rejects some fractional timestamps with an
    // explicit +08:00 offset that the desktop JDK accepts. Parse the offset first.
    fun collectedAt(value:String):Instant=OffsetDateTime.parse(value).toInstant()
    private fun objects(array:JSONArray)= (0 until array.length()).map { array.getJSONObject(it) }
    fun name(value:String)=value.trim().removeSuffix("[地铁站]").removeSuffix("（地铁站）").removeSuffix("(地铁站)").removeSuffix("站")
    fun lineCode(value:String):String? = if(value.contains("阳逻"))"21"else Regex("(\\d{1,2})号线").find(value)?.groupValues?.get(1)?.padStart(2,'0')
    fun lines(data:JSONArray):List<MetroLine> {
        val result=objects(data).map { line ->
            val code=line.getString("lineCode");require(Regex("[0-9]{2}").matches(code))
            val sites=objects(line.getJSONArray("stationInfos")).filter { it.optString("state","1")=="1" }.map { site ->
                val id=site.getString("siteCode");require(Regex("[0-9]{4}").matches(id));require(site.getString("lineCode")==code)
                MetroSite(id,site.getString("siteName").also { require(it.isNotBlank()) },site.getString("sorting").toInt())
            }.sortedBy { it.order }
            require(sites.isNotEmpty()&&sites.map { it.code }.distinct().size==sites.size)
            MetroLine(code,line.getString("lineName"),sites)
        }
        require(result.isNotEmpty()&&result.map { it.code }.distinct().size==result.size)
        return result
    }
    fun clock(value:String):MetroClock {
        val match=Regex("^(次日)?(\\d{1,2}):(\\d{2})([（(]到达[）)])?$").matchEntire(value.trim()) ?: return MetroClock(value,null)
        val time=runCatching { LocalTime.of(match.groupValues[2].toInt(),match.groupValues[3].toInt()) }.getOrNull()
        return MetroClock(value,time,match.groupValues[4].isNotEmpty(),match.groupValues[1].isNotEmpty())
    }
    fun validateTimes(data:JSONArray) {
        require(data.length()>0)
        require(objects(data).map { it.getString("date") }.distinct().size==data.length())
        objects(data).forEach { day ->
            require(day.getString("date").isNotBlank())
            val rows=objects(day.getJSONArray("timetable"));require(rows.isNotEmpty())
            rows.forEach { row ->
                require(Regex("[0-9]{4}").matches(row.getString("siteCode")))
                listOf("lineName","siteName","firstSite","lastSite","downFirst","downLast","upFirst","upLast").forEach { require(row.has(it)&&row.get(it) is String) }
            }
        }
    }
    /** Filter the grouped response by platform, line AND direction before displaying times. */
    fun days(data:JSONArray,site:MetroSite,line:MetroLine,direction:String):List<MetroDay> {
        validateTimes(data)
        return objects(data).mapNotNull { day ->
            val rows=objects(day.getJSONArray("timetable")).filter { it.getString("siteCode")==site.code&&lineCode(it.getString("lineName"))==line.code&&name(it.getString("siteName"))==name(site.name) }
            if(rows.size!=1)return@mapNotNull null
            val row=rows.single();val prefix=when(name(direction)){name(row.getString("firstSite"))->"down";name(row.getString("lastSite"))->"up";else->return@mapNotNull null}
            val first=clock(row.getString(prefix+"First"));val last=clock(row.getString(prefix+"Last"))
            // An end-of-line arrival cannot become a boarding departure.
            if(first.arrivalOnly||last.arrivalOnly)return@mapNotNull null
            MetroDay(day.getString("date"),first,last)
        }.also { require(it.map { day -> day.label }.distinct().size==it.size) }
    }
    fun match(lines:List<MetroLine>,ride:RouteSegment.Ride):Pair<MetroLine,MetroSite>? {
        if(!ride.subway)return null
        val line=lines.singleOrNull { it.code==lineCode(ride.name) }?:return null
        val from=line.sites.singleOrNull { name(it.name)==name(ride.origin.name) }?:return null
        val to=line.sites.singleOrNull { name(it.name)==name(ride.destination.name) }?:return null
        val direction=ride.direction.trim().removePrefix("开往").removeSuffix("方向")
        val terminal=line.sites.singleOrNull { name(it.name)==name(direction) }?:return null
        val delta=to.order-from.order;val end=terminal.order-from.order
        if(delta==0||end==0||delta.toLong()*end<=0||kotlin.math.abs(delta)>kotlin.math.abs(end))return null
        return line to from
    }
}

class OfficialMetroClient(private val baseUrl:String="https://advh5.whrtmpay.com/",private val http:OkHttpClient=OkHttpClient.Builder()
    .connectTimeout(6,TimeUnit.SECONDS).readTimeout(8,TimeUnit.SECONDS).callTimeout(12,TimeUnit.SECONDS).followRedirects(false).build()) {
    private fun post(path:String,body:JSONObject?=null):JSONArray {
        val media=if(body==null)"application/x-www-form-urlencoded"else"application/json; charset=utf-8"
        val request=Request.Builder().url(baseUrl+path).post((body?.toString()?:"").toRequestBody(media.toMediaType())).header("User-Agent","CampusUsage/0.3.2").build()
        return http.newCall(request).execute().use { response ->
            if(!response.isSuccessful)throw RouteFailure("官方时刻表暂时无法连接")
            val source=response.body?.source()?:throw RouteFailure("官方时刻表未返回内容")
            source.request(2_000_001);if(source.buffer.size>2_000_000)throw RouteFailure("官方时刻表内容异常")
            val json=runCatching { JSONObject(source.readUtf8()) }.getOrElse { throw RouteFailure("官方时刻表格式发生变化") }
            if(json.optString("rtCode")!="0000"||!json.optBoolean("success"))throw RouteFailure("官方时刻表暂未完成查询")
            json.optJSONArray("rtData")?:throw RouteFailure("官方时刻表数据格式发生变化")
        }
    }
    fun index()=post("siteInfo/getLineAndStation").also { OfficialMetroParser.lines(it) }
    fun times(code:String):JSONArray {
        require(Regex("[0-9]{4}").matches(code))
        return post("siteInfo/getStationTime",JSONObject().put("siteCode",code)).also { data ->
            OfficialMetroParser.validateTimes(data)
            require((0 until data.length()).any { day -> val rows=data.getJSONObject(day).getJSONArray("timetable");(0 until rows.length()).any { rows.getJSONObject(it).getString("siteCode")==code } })
        }
    }
}

/** Public timetable cache has no dependency on school credentials or the school database. */
class OfficialMetroRepository(private val seed:JSONObject,private val directory:File,private val client:OfficialMetroClient=OfficialMetroClient(),private val clock:Clock=Clock.systemUTC()) {
    private data class Snapshot(val data:JSONArray,val at:Instant,val origin:MetroOrigin)
    @Synchronized private fun read(key:String,validator:(JSONArray)->Unit):Snapshot?=runCatching {
        val file=File(directory,"$key.json");if(!file.exists()||file.length()>2_000_000)return null
        val json=JSONObject(file.readText());val at=OfficialMetroParser.collectedAt(json.getString("fetchedAt"));require(!at.isAfter(clock.instant().plusSeconds(300)))
        val data=json.getJSONArray("data");validator(data);Snapshot(data,at,MetroOrigin.CACHED)
    }.getOrNull()
    private fun bundled(json:JSONObject,validator:(JSONArray)->Unit):Snapshot {
        val data=json.getJSONArray("data");validator(data)
        return Snapshot(data,OfficialMetroParser.collectedAt(json.getString("fetchedAt")),MetroOrigin.BUNDLED)
    }
    private fun index()=read("index"){OfficialMetroParser.lines(it)}?:bundled(seed.getJSONObject("index")){OfficialMetroParser.lines(it)}
    private fun station(code:String):Snapshot? {
        read(code,OfficialMetroParser::validateTimes)?.let { return it }
        val stations=seed.getJSONObject("stations")
        val group=stations.keys().asSequence().map { stations.getJSONObject(it) }.firstOrNull { snapshot ->
            val data=snapshot.getJSONArray("data")
            (0 until data.length()).any { day -> val rows=data.getJSONObject(day).getJSONArray("timetable");(0 until rows.length()).any { rows.getJSONObject(it).optString("siteCode")==code } }
        }
        return group?.let { bundled(it,OfficialMetroParser::validateTimes) }
    }
    @Synchronized private fun save(key:String,data:JSONArray) {
        directory.mkdirs();val target=File(directory,"$key.json");val temporary=File(directory,"$key.tmp")
        temporary.outputStream().use { it.write(JSONObject().put("fetchedAt",clock.instant().toString()).put("data",data).toString().toByteArray()) }
        Files.move(temporary.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING)
    }
    fun view(route:TransitRoute,city:RouteCity,network:Set<String> = emptySet()):MetroReport {
        if(city.code!="420100")return MetroReport(supported=false,message="官方首末班目前覆盖武汉；其他城市可查看地图官方信息")
        val lines=OfficialMetroParser.lines(index().data)
        val legs=route.rides.filter { it.subway }.map { ride ->
            val key=ride.lineId+":"+ride.origin.id;val direction=ride.direction.removePrefix("开往").removeSuffix("方向")
            val match=OfficialMetroParser.match(lines,ride)
            if(match==null)MetroLeg(key,ride.origin.name,ride.name,direction,problem="未能匹配官网线路、站点与方向")
            else {
                val (line,site)=match;val snapshot=station(site.code)
                val days=snapshot?.let { OfficialMetroParser.days(it.data,site,line,direction) }.orEmpty()
                MetroLeg(key,site.name,line.name,direction,site.code,days,snapshot?.at,
                    if(site.code in network)MetroOrigin.NETWORK else snapshot?.origin?:MetroOrigin.BUNDLED,
                    if(days.isEmpty())"暂无该站方向的官方资料，可打开官方原表"else null)
            }
        }
        return MetroReport(legs=legs,message=if(legs.isEmpty())"本方案没有地铁段"else null)
    }
    fun refresh(route:TransitRoute,city:RouteCity,forceIndex:Boolean=false):MetroReport {
        if(city.code!="420100")return view(route,city)
        var failed=false;val updated=mutableSetOf<String>()
        if(forceIndex||Duration.between(index().at,clock.instant()).seconds>86_400)runCatching { save("index",client.index()) }.onFailure { failed=true }
        val codes=view(route,city).legs.mapNotNull { it.code }.distinct()
        for(code in codes)runCatching { save(code,client.times(code));updated+=code }.onFailure { failed=true }
        return view(route,city,updated).copy(message=if(failed)"官方资料更新未完成，保留已取得的资料"else if(codes.isNotEmpty())"已更新官方首末班"else"未找到可匹配的官方站点")
    }
}
