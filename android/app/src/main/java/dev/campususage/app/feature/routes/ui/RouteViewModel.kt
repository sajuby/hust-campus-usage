package dev.campususage.app.feature.routes.ui

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.campususage.app.feature.routes.data.*
import dev.campususage.app.feature.routes.model.*
import kotlinx.coroutines.*
import java.time.*
import org.json.JSONObject
import java.io.File
import android.util.Log
import dev.campususage.app.BuildConfig

class RouteViewModel(application: Application): AndroidViewModel(application) {
    private val client=CityTongClient()
    private val cache=RouteCache(application)
    private val preferences=application.getSharedPreferences("route-preferences",0)
    private val generation=RequestGeneration()
    private val metroGeneration=RequestGeneration()
    private val metroRepository=OfficialMetroRepository(JSONObject(application.assets.open("metro/wuhan-reference.json").bufferedReader().use { it.readText() }),File(application.noBackupFilesDir,"official-metro"))
    private var metroJob:Job?=null
    private var queryJob:Job?=null
    private var searchJob:Job?=null
    private var locationJob:Job?=null
    private var inputVersion=0L
    private var searchGeneration=0L
    var city by mutableStateOf(RouteCity.wuhan);private set
    var origin by mutableStateOf<RoutePlace?>(null);private set
    var destination by mutableStateOf<RoutePlace?>(null);private set
    var strategy by mutableStateOf(RouteStrategy.RECOMMENDED);private set
    var departure by mutableStateOf<ZonedDateTime?>(null);private set
    var result by mutableStateOf<RouteResult?>(null);private set
    var loading by mutableStateOf(false);private set
    var searching by mutableStateOf(false);private set
    var locating by mutableStateOf(false);private set
    var candidates by mutableStateOf<List<RoutePlace>>(emptyList());private set
    var message by mutableStateOf("城市通公共交通查询 · 输入目的地开始");private set
    var locationMessage by mutableStateOf("尚未定位 · 可手动选择城市和起点");private set
    var selected by mutableStateOf<TransitRoute?>(null);private set
    var demonstration by mutableStateOf(false);private set
    var lateDemo by mutableStateOf(false);private set
    var metro by mutableStateOf(MetroReport());private set
    var consent by mutableStateOf(preferences.getBoolean("locationNoticeAccepted",false));private set
    var locateOnEntry by mutableStateOf(!preferences.getBoolean("entryLocationAttempted",false));private set
    init {
        cache.read()?.let { old -> city=old.request.city;origin=old.request.origin;destination=old.request.destination;strategy=old.request.strategy;result=old;locateOnEntry=false
            message="已恢复上次路线，点击更新获取当前结果" }
    }
    private fun changed() { generation.next();queryJob?.cancel();loading=false;inputVersion++;selected=null;result=null;clearMetro() }
    fun changeCity(value:RouteCity) { changed();searchJob?.cancel();candidates=emptyList();searching=false;city=value;origin=null;destination=null;message="已切换城市，请选择起终点";locationMessage="手动选择 ${value.name}";locateOnEntry=false }
    fun choose(place:RoutePlace, start:Boolean) { changed();if(start)origin=place else destination=place;candidates=emptyList();searchJob?.cancel();searching=false }
    fun swap() { if(origin==null&&destination==null)return;changed();val old=origin;origin=destination;destination=old }
    fun changeStrategy(value:RouteStrategy) { if(value==strategy)return;strategy=value;changed();if(origin!=null&&destination!=null)query() }
    fun changeDeparture(value:ZonedDateTime?) { departure=value;changed() }
    private fun clearMetro(){metroGeneration.next();metroJob?.cancel();metro=MetroReport()}
    fun select(value:TransitRoute?) { selected=value;clearMetro();if(value!=null)loadMetro(value,!demonstration) }
    fun refreshMetro(){selected?.let { loadMetro(it,!demonstration,forceIndex=true) }}
    private fun loadMetro(route:TransitRoute,refresh:Boolean,forceIndex:Boolean=false) {
        val id=metroGeneration.next();metroJob?.cancel();val requestedCity=city
        metro=metro.copy(loading=true)
        metroJob=viewModelScope.launch {
            try {
                val cached=withContext(Dispatchers.IO){metroRepository.view(route,requestedCity)}
                if(!metroGeneration.current(id))return@launch
                metro=cached.copy(loading=refresh&&cached.supported)
                if(refresh&&cached.supported) {
                    val fresh=withContext(Dispatchers.IO){metroRepository.refresh(route,requestedCity,forceIndex)}
                    if(metroGeneration.current(id))metro=fresh
                }
            }catch(e:CancellationException){throw e}
            catch(e:Exception){
                if(BuildConfig.DEBUG)Log.w("OfficialMetro","${e.javaClass.simpleName}: ${e.message?.take(180)}; ${e.stackTrace.firstOrNull { it.className.contains("OfficialMetro") }}")
                if(metroGeneration.current(id))metro=metro.copy(loading=false,message="官方资料暂未更新，保留已有内容")
            }
        }
    }
    fun cancelSearch() { searchGeneration++;searchJob?.cancel();candidates=emptyList();searching=false }
    fun search(text:String) {
        val id=++searchGeneration;searchJob?.cancel();candidates=emptyList()
        if(text.isBlank()){searching=false;return}
        val searchedCity=city;searching=true
        searchJob=viewModelScope.launch {
            try { delay(350);val found=if(demonstration)listOf(RouteDemo.origin,RouteDemo.destination,RouteDemo.transfer).filter { it.name.contains(text)||it.address.contains(text) }
                else withContext(Dispatchers.IO){client.search(searchedCity,text.trim().take(80))};if(id==searchGeneration)candidates=found }
            catch(e:CancellationException){throw e}
            catch(e:Exception){message=error(e)}
            finally{if(id==searchGeneration)searching=false}
        }
    }
    fun acceptLocationNotice() { consent=true;preferences.edit().putBoolean("locationNoticeAccepted",true).apply() }
    fun entryAttempted() { locateOnEntry=false;preferences.edit().putBoolean("entryLocationAttempted",true).apply() }
    fun stopLocating() { locationJob?.cancel();locating=false;if(locationMessage.startsWith("正在定位"))locationMessage="定位已停止，可手动选择起点" }
    fun permissionDenied() { locationMessage="未允许定位，可手动选择起点";locateOnEntry=false }
    fun locate() {
        if(locating||demonstration||!consent)return
        locateOnEntry=false;val version=inputVersion;val requestedCity=city;locating=true;locationMessage="正在定位，最长约 10 秒"
        locationJob=viewModelScope.launch {
            try {
                val fix=ForegroundLocation(getApplication()).locate()
                val place=withContext(Dispatchers.IO){client.reverse(fix.point,requestedCity)}.copy(accuracyMeters=fix.accuracy,locatedAt=fix.capturedAt)
                if(version==inputVersion) {
                    val resolved=RouteCity.choices.find { it.name==place.cityName }?:RouteCity(place.cityName,place.adCode.take(4)+"00")
                    if(city!=resolved){destination=null;city=resolved}
                    changed();origin=if(fix.accuracy<=500)place else null
                    locationMessage=if(fix.accuracy<=500)"已定位${city.name} · 精度约 ${fix.accuracy.toInt()} 米" else "已识别${city.name}大致位置，请手动选择起点"
                }
            }catch(e:CancellationException){throw e}
            catch(e:Exception){locationMessage=error(e)}
            finally{locating=false}
        }
    }
    fun query() {
        if(loading)return
        val from=origin;val to=destination
        if(from==null||to==null){message="请先选择明确的起点和目的地";return}
        if(from.point==to.point){message="起点和目的地相同，请重新选择";return}
        if(!sameCity(from)||!sameCity(to)){message="暂仅支持所选城市内的路线，请检查候选地点的城市";return}
        if(from.name=="我的位置"&&(from.locatedAt==null||Duration.between(from.locatedAt,Instant.now()).seconds !in 0..300)){message="当前位置已过期，请重新定位或手动选择起点";return}
        val request=RouteRequest(city,from,to,departure?:ZonedDateTime.now(routeZone),strategy)
        val id=generation.next();loading=true;selected=null;lateDemo=false;clearMetro()
        message=if(demonstration)"正在更新演示路线" else "正在从城市通查询"
        queryJob=viewModelScope.launch {
            try {
                val response=if(demonstration){delay(450);RouteDemo.result(request)}else withContext(Dispatchers.IO){client.routes(request)}
                if(generation.current(id)) {
                    result=response
                    message=if(response.routes.isEmpty())"暂未找到可展示的市内公交/地铁路线，请调整地点或策略" else if(demonstration)"演示数据，未请求城市通" else "已从城市通取得 ${response.routes.size} 条路线"
                    if(!demonstration&&response.routes.isNotEmpty())withContext(Dispatchers.IO){generation.ifCurrent(id){runCatching { cache.save(response) }}}
                }
            }catch(e:CancellationException){throw e}
            catch(e:Exception){if(generation.current(id))message=error(e)+if(result!=null)"；保留此前结果" else ""}
            finally{if(generation.current(id))loading=false}
        }
    }
    private fun sameCity(place:RoutePlace)=place.cityName==city.name || (city.code.take(2) in setOf("11","12","31","50")&&place.adCode.take(2)==city.code.take(2)) || place.adCode.take(4)==city.code.take(4)
    fun enableDemonstration(enabled:Boolean) {
        changed();cancelSearch();locationJob?.cancel();locating=false;demonstration=enabled;lateDemo=false;departure=null
        if(enabled){city=RouteCity.wuhan;origin=RouteDemo.origin;destination=RouteDemo.destination;locationMessage="手动演示地点，未使用手机定位";message="独立界面演示，不调用城市通"}
        else {origin=null;destination=null;cache.read()?.let { city=it.request.city;origin=it.request.origin;destination=it.request.destination;result=it };message="已返回城市通真实查询";locationMessage="尚未定位 · 可手动选择起点"}
    }
    fun toggleLateDemo(){if(demonstration){lateDemo=!lateDemo;selected=if(lateDemo)RouteDemo.lateRoute else null}}
    fun clearCache(){changed();cache.clear();message="已清除本机最近路线"}
    private fun error(e:Exception)=when(e){is RouteFailure->e.explanation;is java.io.IOException->"网络连接失败，请稍后更新";else->"暂未完成路线查询，可稍后重试"}
}
