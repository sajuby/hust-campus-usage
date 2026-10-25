package dev.campususage.app.data.cache

import android.content.Context
import androidx.room.*
import dev.campususage.app.core.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

@Entity(tableName = "daily_usage", primaryKeys = ["scope", "category", "date"])
data class DailyEntity(val scope: String, val category: String, val date: String, val total: String, val room: String?, val publicArea: String?, val fetchedAt: Long) {
    fun model() = DailyUsage(LocalDate.parse(date), Category.valueOf(category), total.toBigDecimal(), room?.toBigDecimal(), publicArea?.toBigDecimal(), fetchedAt)
    companion object { fun from(scope: String, value: DailyUsage) = DailyEntity(scope, value.category.name, value.date.toString(), value.total.toPlainString(), value.room?.toPlainString(), value.publicArea?.toPlainString(), value.fetchedAt ?: System.currentTimeMillis()) }
}
@Entity(tableName = "network_usage") data class NetworkEntity(@PrimaryKey val scope: String, val snapshot: String)
@Dao interface UsageDao {
    @Query("SELECT * FROM daily_usage WHERE scope = :scope") fun daily(scope: String): List<DailyEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun putDaily(values: List<DailyEntity>)
    @Query("SELECT * FROM network_usage WHERE scope = :scope") fun network(scope: String): NetworkEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun putNetwork(value: NetworkEntity)
    @Query("DELETE FROM daily_usage WHERE scope = :scope") fun clearDaily(scope: String)
    @Query("DELETE FROM network_usage WHERE scope = :scope") fun clearNetwork(scope: String)
}
@Database(entities = [DailyEntity::class, NetworkEntity::class], version = 1, exportSchema = true)
abstract class UsageDatabase : RoomDatabase() {
    abstract fun usage(): UsageDao
    companion object { fun open(context: Context): UsageDatabase = Room.databaseBuilder(context.applicationContext, UsageDatabase::class.java, "campus-usage-real.db").build() }
}
object NetworkSnapshotJson {
    fun encode(value: NetworkUsage): String = JSONObject().put("plan", value.plan).put("balance", value.balance.toPlainString())
        .put("expires", value.expires.toString()).put("fetchedAt", value.fetchedAt).put("traffic", JSONArray().apply {
            value.traffic.forEach { put(JSONObject().put("name", it.name).put("used", it.used.toPlainString()).put("unit", it.unit)
                .put("remaining", it.remaining?.toPlainString() ?: JSONObject.NULL).put("total", it.total?.toPlainString() ?: JSONObject.NULL)) }
        }).toString()
    fun decode(text: String): NetworkUsage {
        val j = JSONObject(text); val rows = j.getJSONArray("traffic")
        return NetworkUsage(j.getString("plan"), j.getString("balance").toBigDecimal(), LocalDate.parse(j.getString("expires")),
            (0 until rows.length()).map { i -> val r = rows.getJSONObject(i); TrafficUsage(r.getString("name"), r.getString("used").toBigDecimal(), r.getString("unit"), if (r.isNull("remaining")) null else r.getString("remaining").toBigDecimal(), if (r.isNull("total")) null else r.getString("total").toBigDecimal()) }, j.getLong("fetchedAt"))
    }
}
