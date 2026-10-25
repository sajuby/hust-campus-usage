package dev.campususage.app.ui

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.campususage.app.core.model.*
import dev.campususage.app.data.DemoRepository
import dev.campususage.app.data.SchoolRepository
import dev.campususage.app.data.auth.*
import dev.campususage.app.feature.routes.model.QuerySection
import kotlinx.coroutines.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZonedDateTime
import android.net.Uri

class UsageViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = application.getSharedPreferences("settings", 0)
    private val artStore = AppearanceStore(application)
    var appearance by mutableStateOf(artStore.load()); private set
    var artBusy by mutableStateOf(false); private set
    var artMessage by mutableStateOf(""); private set
    var campus by mutableStateOf(Campus.from(settings.getString("campus", null))); private set
    val school = SchoolRepository.get(application)
    private val demoRepository = DemoRepository()
    private val readings = mutableStateMapOf<Pair<Category, LocalDate>, DailyUsage>()
    var demo by mutableStateOf(settings.getBoolean("demoMode", false)); private set
    var waxyConnected by mutableStateOf(false); private set
    var networkConnected by mutableStateOf(false); private set
    var networkUsage by mutableStateOf<NetworkUsage?>(null); private set
    var authRequest by mutableStateOf<String?>(null)
    var page by mutableStateOf("home")
    var category by mutableStateOf(Category.ELECTRICITY)
    var querySection by mutableStateOf(QuerySection.ELECTRICITY); private set
    var now by mutableStateOf(clock()); private set
    var date by mutableStateOf(latestDate)
    var scenario by mutableStateOf(Scenario.AVAILABLE)
    var period by mutableStateOf("day")
    var days by mutableStateOf(7)
    var updating by mutableStateOf(false); private set
    var message by mutableStateOf(if (demo) "隔离演示环境" else "连接学校系统后，可更新真实数据"); private set
    private var job: Job? = null
    private var requestId = 0
    private var lastResume = 0L
    val latestDate get() = now.toLocalDate().minusDays(if (PublicationPolicy.isExpected(now.toLocalDate().minusDays(1),now)) 1 else 2)
    private fun clock() = if (demo) ZonedDateTime.parse("2026-10-23T09:41:00+08:00") else ZonedDateTime.now(PublicationPolicy.zone)
    fun reading(category: Category, date: LocalDate): DailyUsage? = if(!campus.usesNativeUtilities()) null else if (demo) demoRepository.query(category, date, scenario, now) else readings[category to date]
    fun categoryLabel(category:Category)=if(campus==Campus.MAIN&&category==Category.ENERGY)"水费" else category.label
    fun selectCampus(value:Campus) {
        if(value==campus)return
        requestId++;job?.cancel();updating=false;campus=value;settings.edit().putString("campus",value.name).apply()
        message=if(value==Campus.MAIN)"主校区使用独立官方水电查询页，房间绑定及数据以该页面为准" else "已切换到网安基地，原登录和记录继续保留"
    }
    fun importArtwork(slot:ArtSlot,uri:Uri) {
        if(artBusy)return;artBusy=true;artMessage="正在保存本地图片…"
        viewModelScope.launch{try{appearance=withContext(Dispatchers.IO){artStore.import(slot,uri)};artMessage="${slot.label}已更换，仅保存在本机"}catch(e:Exception){artMessage=e.message?:"未能读取图片，原设置保留"}finally{artBusy=false}}
    }
    fun resetArtwork(slot:ArtSlot?=null) {
        if(artBusy)return;artBusy=true
        viewModelScope.launch{try{appearance=withContext(Dispatchers.IO){artStore.reset(slot)};artMessage=if(slot==null)"已全部恢复默认图片" else "${slot.label}已恢复默认"}catch(_:Exception){artMessage="未能恢复默认，请重试"}finally{artBusy=false}}
    }
    fun network(): NetworkUsage? = if (demo) NetworkUsage("校园畅享套餐", BigDecimal("123.45"), LocalDate.parse("2026-10-31"),
        listOf(TrafficUsage("免费流量", BigDecimal("20"), "G", BigDecimal.ZERO, BigDecimal("20")), TrafficUsage("计费流量", BigDecimal("40"), "G", BigDecimal.ZERO, BigDecimal("40")), TrafficUsage("不限使用流量", BigDecimal("12.34"), "G", null, null)), 0) else networkUsage
    private suspend fun load() {
        val result = withContext(Dispatchers.IO) { school.snapshot() }
        readings.clear(); result.readings.forEach { readings[it.category to it.date] = it }
        networkUsage = result.network; waxyConnected = result.waxyConnected; networkConnected = result.networkConnected
    }
    fun resume() {
        appearance=artStore.load()
        now = clock()
        if (demo) return
        viewModelScope.launch {
            try { load() } catch (_: Exception) { message = "本地记录暂不可读，请重新连接学校系统" }
            if (querySection != QuerySection.ROUTES && System.currentTimeMillis() - lastResume > 5 * 60_000 && (if (category == Category.NETWORK) networkConnected else campus.usesNativeUtilities()&&waxyConnected)) refresh()
            lastResume = System.currentTimeMillis()
        }
    }
    fun setDemoMode(enabled: Boolean) {
        requestId++; job?.cancel(); updating = false; demo = enabled; settings.edit().putBoolean("demoMode", enabled).apply()
        now = clock(); date = latestDate; message = if (demo) "当前为隔离演示环境，未调用学校接口" else "已切换到学校真实数据，点击更新查询"
        if (!demo) resume()
    }
    fun go(destination: String, nextCategory: Category? = null) {
        requestId++; job?.cancel(); updating = false; page = destination
        nextCategory?.let { category = it; querySection = QuerySection.valueOf(it.name) }
        if (destination == "trends" && category == Category.NETWORK) category = Category.ELECTRICITY
        if (!demo && destination in listOf("query", "trends") && (destination != "query" || querySection != QuerySection.ROUTES) && (if (category == Category.NETWORK) networkConnected else campus.usesNativeUtilities()&&waxyConnected)) refresh()
    }
    fun selectCategory(value: Category) { category = value; querySection = QuerySection.valueOf(value.name); selectionChanged() }
    fun selectRoutes() { requestId++; job?.cancel(); updating = false; querySection = QuerySection.ROUTES }
    fun selectDate(value: LocalDate) { date = value; selectionChanged() }
    fun selectPeriod(value: String) { period = value; selectionChanged() }
    fun selectDays(value: Int) { days = value; selectionChanged() }
    private fun selectionChanged() { requestId++; job?.cancel(); updating = false; if (!demo && (if (category == Category.NETWORK) networkConnected else campus.usesNativeUtilities()&&waxyConnected)) refresh() }
    fun requestLogin(system: String) { authRequest = system }
    fun authenticated() { authRequest = null; lastResume = 0; resume() }
    fun disconnect(system: String) {
        requestId++; job?.cancel(); updating = false
        viewModelScope.launch { withContext(Dispatchers.IO) { school.disconnect(system) }; load(); message = "已清除本机该系统的登录与缓存" }
    }
    fun refresh() {
        if(category!=Category.NETWORK&&!campus.usesNativeUtilities()){message="请在主校区官方查询页更新水电数据";return}
        if (!demo && !(if (category == Category.NETWORK) networkConnected else waxyConnected)) { requestLogin(if (category == Category.NETWORK) "network" else "waxy"); return }
        job?.cancel(); val id = ++requestId
        val requestedDate = if (page in listOf("home", "trends")) latestDate else date
        val requestedCategory = category; val requestedPeriod = if(page=="home") "day" else period; val requestedPage = page; val requestedDays = days
        val requestedScenario = scenario; updating = true
        job = viewModelScope.launch {
            try {
                if (demo) {
                    delay(750)
                    if (id == requestId) message = when (requestedScenario) {
                        Scenario.OFFLINE -> "网络不可用，保留此前演示数据"
                        Scenario.LOGIN -> "演示会话需要恢复，已有记录继续可读"
                        Scenario.PENDING -> "演示查询完成，暂未提供该日统计"
                        else -> "演示更新完成，数据未连接学校接口"
                    }
                } else {
                    val provided = withContext(Dispatchers.IO) { school.refresh(requestedCategory, requestedDate, requestedPeriod, requestedPage, requestedDays) }
                    if (id == requestId) { load(); message = if (provided) "已从学校更新 · ${ZonedDateTime.now(PublicationPolicy.zone).toLocalTime().withNano(0)}" else "学校暂未提供所选日期统计；已有缓存保留" }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (id == requestId) {
                // A binding or account may have changed before a later request failed.
                // Reload only the current scope instead of retaining the old room's UI.
                if (!demo) try { load() } catch (_: Exception) { readings.clear(); networkUsage = null }
                message = when (failure) {
                    is SchoolFailure -> failure.explanation
                    is java.io.IOException -> "网络连接失败，保留此前学校数据，可稍后再更新"
                    else -> "暂未完成查询，保留此前记录，请稍后再试"
                }
            } }
            finally { if (id == requestId) updating = false }
        }
    }
}
