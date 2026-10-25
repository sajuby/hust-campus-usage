package dev.campususage.app

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.view.WindowManager
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import dev.campususage.app.data.auth.SessionVault
import dev.campususage.app.data.remote.MainCampusPortal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File

/** The official portal has an isolated WebView process/profile and its own session. */
class MainUtilitiesActivity:ComponentActivity(){
    private var portal:WebView?=null
    private var notice by mutableStateOf("")
    private var pageError=false
    private var normalAgent:String?=null
    private val vault by lazy{SessionVault(this,"CampusUsage.main.utilities.sessions.v1")}
    companion object{private var profileReady=false}
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        if(!profileReady){WebView.setDataDirectorySuffix("main_utilities");profileReady=true}
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent{MaterialTheme(colorScheme=darkColorScheme(primary=Color(0xfff2a8cb))){Surface(Modifier.fillMaxSize(),color=Color(0xff0b0e18)){
            Column(Modifier.statusBarsPadding().navigationBarsPadding().imePadding()){
                Row(Modifier.fillMaxWidth().padding(8.dp),horizontalArrangement=Arrangement.SpaceBetween){TextButton(onClick={finish()}){Text("返回")};TextButton(onClick={navigate(MainCampusPortal.HOME)}){Text("官方首页")};TextButton(onClick={navigate(MainCampusPortal.HISTORY)}){Text("历史用量")};TextButton(onClick={portal?.url?.let(::navigate)}){Text("刷新")}}
                Text("主校区独立官方查询 · 房间信息以学校页面为准",Modifier.padding(horizontal=12.dp),color=Color(0xffbfe6ff))
                if(notice.isNotBlank())Text(notice,Modifier.padding(12.dp),color=Color(0xfff2a8cb))
                AndroidView(modifier=Modifier.weight(1f).fillMaxWidth(),factory={context->WebView(context).apply{
                    portal=this;settings.javaScriptEnabled=true;settings.domStorageEnabled=true
                    val browserAgent=settings.userAgentString
                    normalAgent=browserAgent
                    val queryAgent=browserAgent+" CampusUsage/${BuildConfig.VERSION_NAME} wxwork"
                    // The public H5 checks container compatibility via UA. Keep our
                    // product identity; CAS uses the ordinary browser agent/auth.
                    settings.userAgentString=queryAgent
                    settings.allowFileAccess=false;settings.allowContentAccess=false;settings.mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    webChromeClient=object:WebChromeClient(){override fun onConsoleMessage(message:ConsoleMessage)=true}
                    webViewClient=object:WebViewClient(){
                        override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean{
                            if(!request.isForMainFrame)return false
                            val secure=MainCampusPortal.secureAuthentication(request.url.toString())
                            if(secure!=null){navigate(secure);return true}
                            val url=request.url.toString()
                            if(!MainCampusPortal.allowed(url)){notice="该链接不属于当前官方查询或学校认证页面，已停止跳转";return true}
                            if(view.settings.userAgentString!=agentFor(url)){navigate(url);return true}
                            return false
                        }
                        override fun onPageStarted(view:WebView,url:String,favicon:android.graphics.Bitmap?){
                            if(MainCampusPortal.allowed(url)&&view.settings.userAgentString!=agentFor(url)){navigate(url);return}
                            pageError=false;recordNavigation(url,"loading")
                        }
                        override fun onReceivedSslError(view:WebView,handler:SslErrorHandler,error:SslError){handler.cancel();pageError=true;notice="学校页面证书校验未通过，请稍后重试";recordNavigation(view.url?:"","certificate-error")}
                        override fun onReceivedError(view:WebView,request:WebResourceRequest,error:WebResourceError){if(request.isForMainFrame){pageError=true;notice="未能载入学校页面，请检查校园网或稍后刷新";recordNavigation(request.url.toString(),"network-error")}}
                        override fun onReceivedHttpError(view:WebView,request:WebResourceRequest,response:WebResourceResponse){if(request.isForMainFrame){pageError=true;recordNavigation(request.url.toString(),"http-error")}}
                        override fun onPageFinished(view:WebView,url:String){
                            if(!pageError&&view.url==url){
                                if(Uri.parse(url).host=="sdhq.hust.edu.cn"&&Uri.parse(url).path.orEmpty().startsWith("/MobileUtilityPay1/")){
                                    view.evaluateJavascript("JSON.stringify({restricted:!!document.querySelector('.block-page'),appRendered:!!document.querySelector('.globalTitle')})"){raw->
                                        if(!isDestroyed&&!isFinishing&&view.url==url){val flags=runCatching{JSONObject(JSONTokener(raw).nextValue() as String)}.getOrNull();recordNavigation(url,"loaded",flags)}
                                    }
                                }else recordNavigation(url,"loaded")
                            }
                            saveCookies()
                        }
                    }
                    val manager=CookieManager.getInstance();manager.setAcceptCookie(true)
                    lifecycleScope.launch{
                        val cookies=withContext(Dispatchers.IO){vault.read("main_utilities")?.optJSONArray("cookies")}
                        val entries=if(cookies==null)emptyList()else (0 until cookies.length()).map{cookies.getJSONObject(it)}.filter{it.optString("origin") in setOf("http://sdhq.hust.edu.cn","https://pass.hust.edu.cn")}
                        val start=if(intent.getBooleanExtra("history",false))MainCampusPortal.HISTORY else MainCampusPortal.HOME
                        if(entries.isEmpty())navigate(start)else {
                            var remaining=entries.size
                            entries.forEach{item->manager.setCookie(item.getString("origin"),item.getString("raw")){remaining--;if(remaining==0)navigate(start)}}
                        }
                    }
                }})
            }
            BackHandler{val w=portal;if(w?.canGoBack()==true)w.goBack()else finish()}
        }}}
    }
    private fun agentFor(url:String):String=(normalAgent?:"")+if(Uri.parse(url).host.equals("pass.hust.edu.cn",true))"" else " CampusUsage/${BuildConfig.VERSION_NAME} wxwork"
    private fun navigate(url:String){
        val target=MainCampusPortal.secureAuthentication(url)?:url
        if(!MainCampusPortal.allowed(target))return
        portal?.let{view->val agent=agentFor(target);if(view.settings.userAgentString!=agent)view.settings.userAgentString=agent;view.loadUrl(target)}
    }
    private fun recordNavigation(url:String,event:String,flags:JSONObject?=null){
        if(!BuildConfig.DEBUG)return
        val u=Uri.parse(url);if(u.host !in setOf("sdhq.hust.edu.cn","pass.hust.edu.cn"))return
        // Debug health metadata only: never query strings, fragments or page content.
        runCatching{val record=JSONObject().put("event",event).put("scheme",u.scheme).put("host",u.host).put("path",u.path);flags?.let{record.put("restricted",it.optBoolean("restricted")).put("appRendered",it.optBoolean("appRendered"))};File(noBackupFilesDir,"main-portal-navigation.json").writeText(record.toString())}
    }
    private fun saveCookies(){
        val manager=CookieManager.getInstance();val cookies=JSONArray()
        for(origin in listOf("http://sdhq.hust.edu.cn","https://pass.hust.edu.cn")){
            val raw=manager.getCookie(origin+if(origin.contains("sdhq"))"/ICBS/" else "/cas/")
            raw?.split(';')?.filter{it.contains('=')}?.forEach{cookies.put(JSONObject().put("origin",origin).put("raw",it.trim()+"; Path=/; HttpOnly"+if(origin.startsWith("https"))"; Secure" else ""))}
        }
        manager.flush()
        if(cookies.length()>0)lifecycleScope.launch(Dispatchers.IO){runCatching{vault.write("main_utilities",JSONObject().put("cookies",cookies))}}
    }
    override fun onPause(){saveCookies();super.onPause()}
    override fun onDestroy(){portal?.apply{stopLoading();destroy()};super.onDestroy()}
}
