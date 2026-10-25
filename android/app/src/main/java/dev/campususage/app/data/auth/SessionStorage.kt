package dev.campususage.app.data.auth

import org.json.JSONObject

interface SessionStorage {
    fun read(system: String): JSONObject?
    fun write(system: String, value: JSONObject)
    fun clear(system: String)
}

fun accountHash(value: String): String = java.security.MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

open class SchoolFailure(val explanation: String) : Exception(explanation)
class AuthenticationRequired(message: String = "登录需要恢复，请重新连接学校系统") : SchoolFailure(message)
class CaptchaRequired : SchoolFailure("学校要求图形验证，请在登录页完成验证")
