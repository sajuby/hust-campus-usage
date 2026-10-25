package dev.campususage.app.core.design

import android.content.Context
import androidx.compose.ui.graphics.Color
import dev.campususage.app.BuildConfig
import org.json.JSONObject
import java.io.File

data class DesignConfig(val json: JSONObject) {
    private val tokens get() = json.getJSONObject("tokens")
    val revision get() = json.getInt("revision")
    val glow get() = tokens.getBoolean("glow")
    fun color(key: String): Color = Color(android.graphics.Color.parseColor(tokens.getString(key)))
    fun size(key: String): Float = tokens.getDouble(key).toFloat()
    fun copy(key: String): String = json.getJSONObject("copy").getString(key)
    fun order(key: String): List<String> {
        val array = json.getJSONObject("layout").getJSONArray(key)
        return List(array.length()) { array.getString(it) }
    }
    companion object {
        fun load(context: Context): DesignConfig {
            val bundled = context.assets.open("project.json").bufferedReader().use { it.readText() }
            val override = File(context.filesDir, "design.json")
            return if (BuildConfig.DEBUG && override.exists()) runCatching { parse(override.readText()) }.getOrElse { parse(bundled) } else parse(bundled)
        }
        fun parse(text: String): DesignConfig {
            val j = JSONObject(text)
            require(j.getInt("schemaVersion") == 1 && j.getInt("revision") > 0)
            val t = j.getJSONObject("tokens")
            t.getBoolean("glow")
            listOf("background", "surface", "card", "pink", "blue", "purple", "text", "ink").forEach {
                require(Regex("#[a-fA-F0-9]{6}").matches(t.getString(it)))
            }
            val ranges = mapOf("radius" to 8.0..36.0,"gap" to 8.0..24.0,"pagePadding" to 12.0..28.0,"bodySize" to 14.0..20.0,"valueSize" to 24.0..38.0,"heroHeight" to 120.0..220.0)
            ranges.forEach { (key, range) -> require(t.getDouble(key) in range) }
            val config = DesignConfig(j)
            val sections = listOf("hero","summary","quick","trend","notice")
            val cards = listOf("electricity","energy","month","balance")
            require(config.order("sections").size == sections.size && config.order("sections").toSet() == sections.toSet())
            require(config.order("cards").size == cards.size && config.order("cards").toSet() == cards.toSet())
            listOf("appTitle","greeting","subtitle","heroNote","quickTitle","trendTitle","noticeTitle","noticeBody","electricityLabel","energyLabel","monthLabel","balanceLabel").forEach { require(config.copy(it).isNotBlank() && config.copy(it).length <= 120) }
            listOf("routeTitle","routeSubtitle","routeQuery","routeMapTitle","routeWarningTitle").forEach { key ->
                if(j.getJSONObject("copy").has(key))require(config.copy(key).isNotBlank() && config.copy(key).length<=120)
            }
            return config
        }
    }
}
