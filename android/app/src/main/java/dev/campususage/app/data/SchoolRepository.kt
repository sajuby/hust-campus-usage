package dev.campususage.app.data

import android.content.Context
import dev.campususage.app.core.model.*
import dev.campususage.app.data.auth.*
import dev.campususage.app.data.cache.*
import dev.campususage.app.data.remote.*
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth

data class SchoolSnapshot(val waxyConnected: Boolean, val networkConnected: Boolean, val readings: List<DailyUsage>, val network: NetworkUsage?)

/** Blocking operations are called only on Dispatchers.IO, never from Compose. */
class SchoolRepository(context: Context) {
    val sessions: SessionStorage = SessionVault(context)
    val waxy = WaxyClient(sessions)
    val network = NetworkClient(sessions)
    private val cache = UsageDatabase.open(context).usage()
    fun snapshot(): SchoolSnapshot {
        val waxyScope = sessions.read("waxy")?.optString("scope")?.takeIf { it.isNotBlank() }
        val networkScope = sessions.read("network")?.optString("scope")?.takeIf { it.isNotBlank() }
        return SchoolSnapshot(waxyScope != null, networkScope != null,
            waxyScope?.let { cache.daily(it).map(DailyEntity::model) } ?: emptyList(),
            networkScope?.let { cache.network(it)?.let { e -> NetworkSnapshotJson.decode(e.snapshot) } })
    }
    private fun store(scope: String, values: List<DailyUsage>) {
        if (sessions.read("waxy")?.optString("scope") != scope) throw SchoolFailure("账户已切换，本次结果未应用")
        cache.putDaily(values.map { DailyEntity.from(scope, it) })
    }
    fun refresh(category: Category, date: LocalDate, period: String, page: String, days: Int): Boolean =
        synchronized(if (category == Category.NETWORK) network else waxy) {
            refreshLocked(category, date, period, page, days)
        }
    private fun refreshLocked(category: Category, date: LocalDate, period: String, page: String, days: Int): Boolean {
        if (category == Category.NETWORK) {
            val (scope, value) = network.fetch()
            if (sessions.read("network")?.optString("scope") != scope) throw SchoolFailure("账户已切换，本次结果未应用")
            cache.putNetwork(NetworkEntity(scope, NetworkSnapshotJson.encode(value)))
            return true
        }
        waxy.refreshBinding()
        if (page == "trends") {
            val first = date.minusDays((days - 1).toLong())
            val months = listOf(YearMonth.from(first), YearMonth.from(date)).distinct()
            months.forEach { val (scope, values) = waxy.month(category, it); store(scope, values) }
            return true
        }
        if (period == "month") {
            val (scope, values) = waxy.month(category, YearMonth.from(date)); store(scope, values)
            return values.isNotEmpty()
        }
        val (scope, value) = waxy.daily(category, date)
        if (value != null) store(scope, listOf(value))
        // One month response supplies history without issuing one request per day.
        val (monthScope, values) = waxy.month(category, YearMonth.from(date)); store(monthScope, values)
        return value != null
    }
    fun importNetwork(value: JSONObject): Unit = synchronized(network) {
        require(Regex("[a-f0-9]{64}").matches(value.getString("scope")))
        val cookies = value.getJSONArray("cookies")
        require(cookies.length() in 1..100)
        for (i in 0 until cookies.length()) require(cookies.getJSONObject(i).getString("origin") in setOf("hkwxy.hust.edu.cn", "pass.hust.edu.cn"))
        val previous = sessions.read("network")
        sessions.write("network", value)
        try {
            val (scope, data) = network.fetch()
            cache.putNetwork(NetworkEntity(scope, NetworkSnapshotJson.encode(data)))
        } catch (error: Exception) {
            if (previous == null) sessions.clear("network") else sessions.write("network", previous)
            throw error
        }
    }
    fun disconnect(system: String): Unit = synchronized(if (system == "waxy") waxy else network) {
        val scope = sessions.read(system)?.optString("scope")
        sessions.clear(system)
        if (!scope.isNullOrBlank()) { if (system == "waxy") cache.clearDaily(scope) else cache.clearNetwork(scope) }
    }
    companion object {
        @Volatile private var instance: SchoolRepository? = null
        fun get(context: Context): SchoolRepository = instance ?: synchronized(this) { instance ?: SchoolRepository(context.applicationContext).also { instance = it } }
    }
}
