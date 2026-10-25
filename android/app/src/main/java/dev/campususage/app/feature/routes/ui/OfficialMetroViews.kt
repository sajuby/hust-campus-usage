package dev.campususage.app.feature.routes.ui

import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.campususage.app.core.design.DesignConfig
import dev.campususage.app.feature.routes.data.*
import dev.campususage.app.feature.routes.model.routeZone
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable internal fun OfficialMetroCard(d:DesignConfig,report:MetroReport,date:LocalDate,refresh:()->Unit) {
    val context=LocalContext.current
    RouteSurface(d,padding=14) {
        Column(verticalArrangement=Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Icon(Icons.Outlined.Schedule,null,Modifier.size(18.dp),tint=d.color("blue"))
                Text(" 官方首末班",fontSize=15.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
                TextButton(onClick=refresh,enabled=!report.loading&&report.supported,contentPadding=PaddingValues(horizontal=4.dp)) {
                    if(report.loading)CircularProgressIndicator(Modifier.size(13.dp),strokeWidth=1.5.dp)else Icon(Icons.Default.Refresh,null,Modifier.size(14.dp))
                    Text(if(report.loading)" 更新中"else" 更新首末班",fontSize=10.sp)
                }
            }
            report.legs.forEach { leg ->
                var otherDays by remember(leg.key){mutableStateOf(false)}
                var chosen by remember(leg.key,leg.days){mutableStateOf<String?>(null)}
                val day=leg.days.find { it.label==chosen }?:leg.regularDay(date)
                Column(Modifier.fillMaxWidth().background(Color(0xff151e2e).copy(alpha=.7f),RoundedCornerShape(12.dp)).padding(11.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text("${leg.station} · ${leg.line}",fontSize=13.sp,fontWeight=FontWeight.SemiBold)
                    Text("开往${leg.direction}方向",fontSize=11.sp,color=d.color("blue"))
                    if(day!=null) {
                        Text(if(chosen!=null)"手动查看 · ${day.label}"else"${date} · 常规${day.label}",fontSize=10.sp,color=routeMuted)
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                            Text("首班 ${day.first.display()}",fontSize=13.sp,color=d.color("text"))
                            Text("末班 ${day.last.display(day.first)}",fontSize=13.sp,color=d.color("pink"),fontWeight=FontWeight.Medium)
                        }
                    } else Text(leg.problem?:"请选择日期类型查看官方原始资料",fontSize=11.sp,color=routeMuted)
                    if(leg.days.isNotEmpty()) {
                        Text(if(otherDays)"收起日期类型"else"查看其他日期类型",fontSize=10.sp,color=d.color("blue"),modifier=Modifier.clickable{otherDays=!otherDays}.padding(vertical=6.dp))
                        if(otherDays)Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                            leg.days.forEach { option ->
                                Text(option.label,fontSize=11.sp,color=if(option==day)d.color("ink")else d.color("text"),modifier=Modifier
                                    .background(if(option==day)d.color("blue")else Color(0xff283348),RoundedCornerShape(16.dp))
                                    .clickable{chosen=option.label}.padding(horizontal=11.dp,vertical=12.dp))
                            }
                        }
                    }
                    leg.fetchedAt?.let { Text("${leg.origin.label} · ${it.atZone(routeZone).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))} 采集",fontSize=9.sp,color=routeMuted) }
                    if(leg.code!=null)Text("查看官方原表 ›",fontSize=10.sp,color=d.color("blue"),modifier=Modifier.clickable {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(leg.officialUrl()))) }
                    }.padding(vertical=5.dp))
                }
            }
            report.message?.let { Text(it,fontSize=10.sp,color=routeMuted,lineHeight=15.sp) }
            Text("来源：武汉地铁官方。按常规日期显示，节假日及调休请核对官方原表。仅查询首末班，不计算最晚出发。",fontSize=10.sp,color=routeMuted,lineHeight=16.sp)
        }
    }
}

@Composable internal fun OfficialMetroMap(d:DesignConfig,onDismiss:()->Unit) {
    val context=LocalContext.current
    val web=remember {
        WebView(context).apply {
            layoutParams=android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT,android.view.ViewGroup.LayoutParams.MATCH_PARENT)
            // Old Android 12 WebView versions can leave a blank hardware layer
            // inside a Compose dialog. This local static map uses a software layer.
            setLayerType(android.view.View.LAYER_TYPE_SOFTWARE,null)
            setBackgroundColor(android.graphics.Color.WHITE)
            settings.javaScriptEnabled=true
            settings.allowFileAccess=false;settings.allowContentAccess=false
            settings.domStorageEnabled=false;settings.mixedContentMode=android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webViewClient=object:WebViewClient(){override fun shouldOverrideUrlLoading(view:WebView,request:android.webkit.WebResourceRequest)=true}
            val data=context.assets.open("metro/wuhan-map.json").bufferedReader().use { it.readText() }.replace("<","\\u003c")
            val html=context.assets.open("metro/map.html").bufferedReader().use { it.readText() }.replace("__METRO_DATA__",data)
            loadDataWithBaseURL("https://campususage.invalid/",html,"text/html","UTF-8",null)
        }
    }
    DisposableEffect(web){onDispose { web.stopLoading();web.destroy() }}
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Column(Modifier.fillMaxSize().background(d.color("background")).safeDrawingPadding().padding(14.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("武汉地铁官方线路图",fontSize=18.sp,fontWeight=FontWeight.SemiBold,color=d.color("text"),modifier=Modifier.weight(1f))
                IconButton(onClick=onDismiss){Icon(Icons.Default.Close,"关闭线路图",tint=d.color("text"))}
            }
            Text("内置官方线网资料 · 2026-10-25 采集 · 可离线缩放和搜索",fontSize=10.sp,color=routeMuted)
            AndroidView(factory={web},modifier=Modifier.fillMaxWidth().weight(1f).border(1.dp,Color(0xff8293af),RoundedCornerShape(12.dp)))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                TextButton(onClick={runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://h5.whrtmpay.com/whrtMap/#/?download=true")))}}){Text("官方在线图")}
                TextButton(onClick={runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://map.amap.com/subway/index.html")))}}){Text("高德备用图")}
            }
        }
    }
}
