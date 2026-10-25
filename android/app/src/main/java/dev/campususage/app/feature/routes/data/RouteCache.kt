package dev.campususage.app.feature.routes.data

import android.content.Context
import android.util.AtomicFile
import dev.campususage.app.feature.routes.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZonedDateTime

/** Only the last successful route, in private no-backup storage. School databases are not migrated. */
class RouteCache(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir,"last-transit-route.json"))
    fun clear() { file.delete() }
    @Synchronized fun save(result: RouteResult) {
        if (result.demonstration || result.routes.isEmpty()) return
        val r=result.request
        val json=JSONObject().put("version",1).put("city",JSONObject().put("name",r.city.name).put("code",r.city.code))
            .put("origin",place(r.origin)).put("destination",place(r.destination)).put("departure",r.departure.toString())
            .put("strategy",r.strategy.name).put("fetchedAt",result.fetchedAt.toString()).put("routes",JSONArray(result.routes.map { route ->
                JSONObject().put("id",route.id).put("duration",route.durationSeconds ?: JSONObject.NULL).put("walk",route.walkMeters).put("fare",route.fare ?: JSONObject.NULL)
                    .put("segments",JSONArray(route.segments.map { segment -> when(segment) {
                        is RouteSegment.Walk -> JSONObject().put("type","walk").put("meters",segment.distanceMeters).put("seconds",segment.durationSeconds ?: JSONObject.NULL)
                        is RouteSegment.Ride -> JSONObject().put("type","ride").put("id",segment.lineId).put("name",segment.name).put("direction",segment.direction).put("subway",segment.subway)
                            .put("from",stop(segment.origin)).put("to",stop(segment.destination)).put("via",JSONArray(segment.via.map(::stop)))
                            .put("seconds",segment.durationSeconds ?: JSONObject.NULL).put("first",segment.first ?: JSONObject.NULL).put("last",segment.last ?: JSONObject.NULL)
                    }}))
            }))
        val stream=file.startWrite()
        try { stream.write(json.toString().toByteArray()); file.finishWrite(stream) } catch (e:Exception) { file.failWrite(stream); throw e }
    }
    @Synchronized fun read(): RouteResult? = runCatching {
        if (!file.baseFile.exists() || file.baseFile.length()>500_000) return null
        val json=JSONObject(String(file.readFully()));require(json.getInt("version")==1)
        val c=json.getJSONObject("city")
        val request=RouteRequest(RouteCity(c.getString("name"),c.getString("code")),readPlace(json.getJSONObject("origin")),readPlace(json.getJSONObject("destination")),
            ZonedDateTime.parse(json.getString("departure")),RouteStrategy.valueOf(json.getString("strategy")))
        val routes=json.getJSONArray("routes")
        RouteResult(request,(0 until routes.length()).map { i ->
            val r=routes.getJSONObject(i);val segments=r.getJSONArray("segments")
            TransitRoute(r.getString("id"),number(r,"duration"),r.getInt("walk"),text(r,"fare"),(0 until segments.length()).map { index ->
                val s=segments.getJSONObject(index)
                if(s.getString("type")=="walk") RouteSegment.Walk(s.getInt("meters"),number(s,"seconds")) else {
                    val via=s.getJSONArray("via")
                    RouteSegment.Ride(s.getString("id"),s.getString("name"),s.getString("direction"),s.getBoolean("subway"),readStop(s.getJSONObject("from")),readStop(s.getJSONObject("to")),
                        (0 until via.length()).map { readStop(via.getJSONObject(it)) },number(s,"seconds"),text(s,"first"),text(s,"last"))
                }
            })
        },Instant.parse(json.getString("fetchedAt")))
    }.getOrNull()
    private fun number(j:JSONObject,key:String):Long?=if(j.isNull(key))null else j.getLong(key)
    private fun text(j:JSONObject,key:String):String?=if(j.isNull(key))null else j.getString(key)
    private fun stop(s:RouteStop)=JSONObject().put("id",s.id).put("name",s.name)
    private fun readStop(j:JSONObject)=RouteStop(j.getString("id"),j.getString("name"))
    private fun place(p:RoutePlace)=JSONObject().put("id",p.id).put("name",p.name).put("address",p.address).put("city",p.cityName).put("ad",p.adCode)
        .put("lng",p.point.longitude).put("lat",p.point.latitude).put("locatedAt",p.locatedAt?.toString()?:JSONObject.NULL).put("accuracy",p.accuracyMeters?:JSONObject.NULL)
    private fun readPlace(j:JSONObject)=RoutePlace(j.getString("id"),j.getString("name"),j.getString("address"),j.getString("city"),j.getString("ad"),GeoPoint(j.getDouble("lng"),j.getDouble("lat")),
        if(j.isNull("accuracy"))null else j.getDouble("accuracy").toFloat(),if(j.isNull("locatedAt"))null else Instant.parse(j.getString("locatedAt")))
}
