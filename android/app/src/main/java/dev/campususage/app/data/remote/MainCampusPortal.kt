package dev.campususage.app.data.remote

import java.net.URI

/** Verified official H5; no guessed native API or reuse of cyber-base identifiers. */
object MainCampusPortal {
    const val HOME="http://sdhq.hust.edu.cn/MobileUtilityPay1/#/home"
    const val HISTORY="http://sdhq.hust.edu.cn/MobileUtilityPay1/#/recently"
    fun secureAuthentication(url:String):String?=runCatching{
        val u=URI(url)
        if(u.userInfo==null&&u.host.equals("pass.hust.edu.cn",true)&&u.scheme.equals("http",true)&&u.port in setOf(-1,80))
            "https://pass.hust.edu.cn"+(u.rawPath?:"/")+(u.rawQuery?.let{"?$it"}?:"")+(u.rawFragment?.let{"#$it"}?:"")
        else null
    }.getOrNull()
    fun allowed(url:String):Boolean=runCatching{
        val u=URI(url);val host=u.host?.lowercase();val scheme=u.scheme?.lowercase()
        u.userInfo==null&&when(host){
            "sdhq.hust.edu.cn" -> scheme in setOf("http","https")&&u.port in setOf(-1,80,443)&&((u.path?:"").lowercase().startsWith("/mobileutilitypay1/")||(u.path?:"").lowercase().startsWith("/icbs/"))
            "pass.hust.edu.cn" -> scheme=="https"&&u.port in setOf(-1,443)
            else->false
        }
    }.getOrDefault(false)
}
