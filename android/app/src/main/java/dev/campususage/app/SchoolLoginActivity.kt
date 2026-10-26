package dev.campususage.app

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.util.Base64
import android.view.WindowManager
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import dev.campususage.app.data.SchoolRepository
import dev.campususage.app.data.auth.*
import dev.campususage.app.data.remote.NetworkClient
import dev.campususage.app.data.remote.NetworkCookies
import okhttp3.HttpUrl.Companion.toHttpUrl
import androidx.webkit.CookieManagerCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

class SchoolLoginActivity : ComponentActivity() {
    private val school by lazy { SchoolRepository.get(this) }
    private var portal: WebView? = null
    private var checking = false
    private var error by mutableStateOf("")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xfff2a8cb), background = Color(0xff0b0e18))) {
                Surface(Modifier.fillMaxSize()) { if (intent.getStringExtra("system") == "network") NetworkLogin() else WaxyLogin() }
            }
        }
    }
    private fun done() { setResult(RESULT_OK); finish() }
    private fun explanation(e: Exception) = if (e is SchoolFailure) e.explanation else "连接暂未完成，请检查网络后重试"

    @Composable private fun WaxyLogin() {
        var username by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }
        var rememberLogin by remember { mutableStateOf(true) }; var busy by remember { mutableStateOf(false) }
        var captcha by remember { mutableStateOf<JSONObject?>(null) }; var code by remember { mutableStateOf("") }
        val scope = rememberCoroutineScope()
        fun refreshCaptcha() { scope.launch { try { captcha = withContext(Dispatchers.IO) { school.waxy.captcha() } } catch (e: Exception) { error = explanation(e) } } }
        LaunchedEffect(Unit) { refreshCaptcha() }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("连接水电能源", style = MaterialTheme.typography.headlineMedium)
            Text("使用微信“水电能源”小程序的账号和密码。电费与能源费共用这处连接。")
            Text("官方服务：www.waxy.online", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(username, { username = it }, label = { Text("学号或账号") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(password, { password = it }, label = { Text("密码") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
            if (captcha?.optBoolean("captchaEnabled", false) == true) {
                val image = remember(captcha) { runCatching { val bytes = Base64.decode(captcha!!.getString("img"), Base64.DEFAULT); BitmapFactory.decodeByteArray(bytes, 0, bytes.size).asImageBitmap() }.getOrNull() }
                image?.let { Image(it, "学校图形验证码", Modifier.height(70.dp)) }
                OutlinedTextField(code, { code = it }, label = { Text("图形验证码") }, singleLine = true)
                TextButton(onClick = { refreshCaptcha() }) { Text("换一张验证码") }
            }
            Row { Checkbox(rememberLogin, { rememberLogin = it }, enabled = !busy); Column { Text("保持登录"); Text("在本机加密保存账号密码，失效时尝试恢复；学校要求验证时需你确认。", style = MaterialTheme.typography.bodySmall) } }
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            Button(enabled = !busy && username.isNotBlank() && password.isNotEmpty(), onClick = {
                busy = true; error = ""
                scope.launch {
                    try { withContext(Dispatchers.IO) { school.waxy.login(username, password, rememberLogin, code, captcha?.optString("uuid", "") ?: "") }; password = ""; done() }
                    catch (e: Exception) { error = explanation(e); refreshCaptcha() }
                    finally { busy = false }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "正在连接…" else "登录并连接") }
            TextButton(onClick = { finish() }, enabled = !busy) { Text("返回") }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Composable private fun NetworkLogin() {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { finish() }) { Text("返回") }
                Text("学校官方统一认证", modifier = Modifier.padding(top = 12.dp))
                TextButton(onClick = { portal?.let(::checkPortal) }) { Text("验证登录") }
            }
            Text("认证页面仅允许学校官方站点。登录完成后自动返回，App 保留会话。", Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodySmall)
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp))
            AndroidView(modifier = Modifier.weight(1f).fillMaxWidth(), factory = { context ->
                WebView(context).apply {
                    portal = this
                    WebView.setWebContentsDebuggingEnabled(false)
                    settings.javaScriptEnabled = true; settings.domStorageEnabled = true
                    settings.allowFileAccess = false; settings.allowContentAccess = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    webChromeClient = object : WebChromeClient() { override fun onConsoleMessage(message: ConsoleMessage): Boolean = true }
                    CookieManager.getInstance().setAcceptCookie(true)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                            request.isForMainFrame && (request.url.scheme != "https" || request.url.host !in setOf("hkwxy.hust.edu.cn", "pass.hust.edu.cn"))
                        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) { handler.cancel(); this@SchoolLoginActivity.error = "学校连接的证书校验未通过，已停止登录" }
                        override fun onPageFinished(view: WebView, url: String) { checkPortal(view) }
                    }
                    lifecycleScope.launch {
                        val saved = withContext(Dispatchers.IO) { school.sessions.read("network")?.let { NetworkCookies.browserSnapshot(it) } }
                        val manager = CookieManager.getInstance()
                        manager.removeAllCookies {
                            val entries = if (saved == null) emptyList() else (0 until saved.length()).map { saved.getJSONObject(it) }
                                .filter { it.optString("origin") in setOf("hkwxy.hust.edu.cn", "pass.hust.edu.cn") }
                            if (entries.isEmpty()) loadUrl(NetworkClient.LOGIN_URL)
                            else {
                                var remaining = entries.size
                                entries.forEach { item -> manager.setCookie("https://${item.getString("origin")}/", item.getString("raw")) { remaining--; if (remaining == 0) loadUrl(NetworkClient.LOGIN_URL) } }
                            }
                        }
                    }
                }
            })
        }
    }
    private fun checkPortal(view: WebView) {
        if (checking || Uri.parse(view.url).host != "hkwxy.hust.edu.cn") return
        view.evaluateJavascript("typeof id_number === 'string' ? id_number : ''") { value ->
            if (checking) return@evaluateJavascript
            val identifier = runCatching { JSONTokener(value).nextValue() as? String }.getOrNull()?.takeIf { it.isNotBlank() } ?: return@evaluateJavascript
            val cookies = JSONArray()
            for (host in listOf("hkwxy.hust.edu.cn", "pass.hust.edu.cn")) {
                val path = if (host.startsWith("hkwxy")) "/tp_up/apps/campusNetwork/servicePlan" else "/cas/login"
                val url = "https://$host$path"
                val manager = CookieManager.getInstance()
                val full = if (WebViewFeature.isFeatureSupported(WebViewFeature.GET_COOKIE_INFO))
                    runCatching { CookieManagerCompat.getCookieInfo(manager, url) }.getOrNull() else null
                val captured = NetworkCookies.browserEntries(url.toHttpUrl(), full, manager.getCookie(url))
                for (index in 0 until captured.length()) cookies.put(captured.getJSONObject(index))
            }
            if (cookies.length() == 0) return@evaluateJavascript
            checking = true; error = "正在验证校园网会话…"
            lifecycleScope.launch {
                val agent = view.settings.userAgentString
                try { withContext(Dispatchers.IO) { school.importNetwork(JSONObject().put("scope", accountHash("network|$identifier")).put("cookies", cookies)
                    .put("cookieSchemaVersion", NetworkCookies.schema).put("userAgent", agent).put("savedAt", System.currentTimeMillis())) }; done() }
                catch (e: Exception) { error = explanation(e) }
                finally { checking = false }
            }
        }
    }
    override fun onDestroy() {
        portal?.apply { stopLoading(); clearCache(true); clearHistory(); destroy() }
        if (intent.getStringExtra("system") == "network") {
            CookieManager.getInstance().removeAllCookies { CookieManager.getInstance().flush() }
            WebStorage.getInstance().deleteAllData()
        }
        super.onDestroy()
    }
}
