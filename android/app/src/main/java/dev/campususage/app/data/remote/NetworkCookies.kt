package dev.campususage.app.data.remote

import dev.campususage.app.data.auth.SessionStorage
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject

/** Only these school origins may receive stored school cookies. */
object NetworkCookies {
    val hosts = setOf("hkwxy.hust.edu.cn", "pass.hust.edu.cn")
    const val schema = 2
    data class Entry(val origin: String, val cookie: Cookie)
    private fun key(entry: Entry) = "${entry.cookie.domain}|${entry.cookie.path}|${entry.cookie.name}"
    fun allowed(url: HttpUrl) = url.scheme == "https" && url.host in hosts

    /** Version 1 manufactured a trailing slash, making the old and renewed session coexist. */
    fun decode(state: JSONObject): List<Entry> {
        val saved = state.optJSONArray("cookies") ?: return emptyList()
        val legacy = state.optInt("cookieSchemaVersion", 1) < schema
        val map = linkedMapOf<String, Entry>()
        val authoritative = mutableSetOf<String>()
        for (index in 0 until saved.length()) {
            val item = saved.optJSONObject(index) ?: continue
            val origin = item.optString("origin")
            if (origin !in hosts) continue
            val parsed = Cookie.parse("https://$origin/".toHttpUrl(), item.optString("raw")) ?: continue
            val applicationPath = when (origin) { "hkwxy.hust.edu.cn" -> "/tp_up"; else -> "/cas" }
            val routingName = parsed.name.startsWith("BIGipServer") || parsed.name in setOf("foobar", "Language")
            val canonicalPath = if (routingName) "/" else applicationPath
            val sessionName = parsed.name == "JSESSIONID" || (origin == "pass.hust.edu.cn" && parsed.name == "CASTGC")
            val manufactured = legacy && (sessionName || routingName) && parsed.path == "$applicationPath/"
            val cookie = if (manufactured) Cookie.Builder().name(parsed.name).value(parsed.value).path(canonicalPath).apply {
                if (parsed.hostOnly) hostOnlyDomain(parsed.domain) else domain(parsed.domain)
                if (parsed.persistent) expiresAt(parsed.expiresAt)
                if (parsed.secure) secure()
                if (parsed.httpOnly) httpOnly()
            }.build() else parsed
            val entry = Entry(origin, cookie); val identity = key(entry)
            // A real server cookie wins over the v1 synthetic path, independent of array order.
            if (!manufactured || identity !in authoritative) map[identity] = entry
            if (!manufactured) authoritative += identity
        }
        return map.values.filter { it.cookie.expiresAt > System.currentTimeMillis() }
    }

    fun encode(entries: List<Entry>) = JSONArray().apply {
        entries.forEach { put(JSONObject().put("origin", it.origin).put("raw", it.cookie.toString())) }
    }
    fun browserSnapshot(state: JSONObject) = encode(decode(state))

    /** Prefer WebView's full Set-Cookie records; old providers get a labelled, scoped fallback. */
    fun browserEntries(url: HttpUrl, fullCookies: List<String>?, requestHeader: String?): JSONArray {
        require(allowed(url))
        val raw = fullCookies ?: requestHeader.orEmpty().split(';').mapNotNull { part ->
            val pair = part.trim(); val name = pair.substringBefore('=')
            if (!pair.contains('=') || name.isBlank()) return@mapNotNull null
            val path = when {
                name == "JSESSIONID" && url.host == "hkwxy.hust.edu.cn" -> "/tp_up"
                name in setOf("JSESSIONID", "CASTGC") && url.host == "pass.hust.edu.cn" -> "/cas"
                else -> "/"
            }
            "$pair; Path=$path; Secure" + if (name in setOf("JSESSIONID", "CASTGC")) "; HttpOnly" else ""
        }.distinctBy { it.substringBefore('=') }
        return encode(raw.mapNotNull { value -> Cookie.parse(url, value)?.let { Entry(url.host, it) } }
            .filter { it.cookie.expiresAt > System.currentTimeMillis() })
    }
}

class SchoolCookieJar(private val sessions: SessionStorage) : CookieJar {
    private fun state(): Pair<JSONObject, List<NetworkCookies.Entry>>? {
        val state = sessions.read("network") ?: return null
        val entries = NetworkCookies.decode(state)
        if (state.optInt("cookieSchemaVersion", 1) < NetworkCookies.schema) {
            state.put("cookieSchemaVersion", NetworkCookies.schema).put("cookies", NetworkCookies.encode(entries))
            sessions.write("network", state)
        }
        return state to entries
    }
    @Synchronized override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (!NetworkCookies.allowed(url)) return
        val (state, stored) = state() ?: return
        val entries = stored.associateBy { "${it.cookie.domain}|${it.cookie.path}|${it.cookie.name}" }.toMutableMap()
        cookies.forEach { cookie ->
            val identity = "${cookie.domain}|${cookie.path}|${cookie.name}"
            if (cookie.expiresAt <= System.currentTimeMillis()) entries.remove(identity)
            else entries[identity] = NetworkCookies.Entry(url.host, cookie)
        }
        state.put("cookies", NetworkCookies.encode(entries.values.toList())).put("cookieSchemaVersion", NetworkCookies.schema)
        sessions.write("network", state)
    }
    @Synchronized override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (!NetworkCookies.allowed(url)) return emptyList()
        return state()?.second.orEmpty().map { it.cookie }.filter { it.matches(url) }
            .sortedByDescending { it.path.length }
    }
}
