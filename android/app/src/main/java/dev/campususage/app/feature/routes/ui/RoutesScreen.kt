package dev.campususage.app.feature.routes.ui

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.campususage.app.BuildConfig
import dev.campususage.app.R
import dev.campususage.app.core.design.DesignConfig
import dev.campususage.app.feature.routes.data.RouteDemo
import dev.campususage.app.feature.routes.domain.*
import dev.campususage.app.feature.routes.model.*
import dev.campususage.app.ui.CampusBottomNavigation
import kotlinx.coroutines.delay
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

private val timeFormat=DateTimeFormatter.ofPattern("HH:mm")
private fun minutes(seconds:Long?)=seconds?.let { ceil(it/60.0).toLong().toString() }?:"—"
private fun shortName(name:String)=name.removeSuffix("[地铁站]")

/** Three layouts matching the approved mockups, with shared fixed chrome and independent body scrolling. */
@Composable fun RouteExperience(d:DesignConfig,vm:RouteViewModel,onSection:(QuerySection)->Unit,onNavigate:(String)->Unit) {
    var cityDialog by remember { mutableStateOf(false) }
    val scroll=rememberScrollState()
    LaunchedEffect(vm.selected?.id,vm.result?.fetchedAt,vm.result==null) { scroll.scrollTo(0) }
    Box(Modifier.fillMaxSize()) {
        RouteBackdrop(d,Modifier.fillMaxSize())
        CompositionLocalProvider(LocalTextStyle provides TextStyle(fontFamily=FontFamily.SansSerif,fontSize=14.sp,lineHeight=TextUnit.Unspecified)) {
        Scaffold(containerColor=Color.Transparent,contentColor=d.color("text"),bottomBar={CampusBottomNavigation(d,"query",onNavigate)}) { insets ->
            Column(Modifier.fillMaxSize().padding(insets).padding(horizontal=d.size("pagePadding").dp)) {
                RouteHeader(d,vm.city.name,vm.result!=null||vm.selected!=null){cityDialog=true}
                RouteCategoryTabs(d,onSection)
                Column(Modifier.weight(1f).verticalScroll(scroll).padding(top=8.dp,bottom=6.dp),verticalArrangement=Arrangement.spacedBy((d.size("gap")*.85f).dp)) {
                    RoutesScreen(d,vm)
                }
            }
        }
        }
    }
    if(cityDialog)CityDialog(vm){cityDialog=false}
}

@Composable fun RoutesScreen(d:DesignConfig,vm:RouteViewModel) {
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_STOP)vm.stopLocating() }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer);vm.stopLocating();vm.cancelSearch() }
    }
    var placeDialog by remember { mutableStateOf<Boolean?>(null) }
    var locationNotice by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(Instant.now()) }
    var mapError by remember { mutableStateOf<String?>(null) }
    var officialMap by remember { mutableStateOf(false) }
    fun showMap(){if(vm.city.code=="420100")officialMap=true else if(!openSubway(context,vm.city))mapError="暂未找到可打开地铁图的应用"}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { values ->
        if(values.values.any { it })vm.locate() else vm.permissionDenied()
    }
    fun requestLocation() {
        if(!vm.consent){locationNotice=true;return}
        if(ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED)vm.locate()
        else permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION))
    }
    fun selectTime() {
        val start=vm.departure?:ZonedDateTime.now(routeZone)
        DatePickerDialog(context,{_,year,month,day ->
            TimePickerDialog(context,{_,hour,minute ->vm.changeDeparture(LocalDate.of(year,month+1,day).atTime(hour,minute).atZone(routeZone))},start.hour,start.minute,true).show()
        },start.year,start.monthValue-1,start.dayOfMonth).show()
    }
    LaunchedEffect(Unit) { if(vm.locateOnEntry&&!vm.demonstration){vm.entryAttempted();requestLocation()} }
    LaunchedEffect(vm.result) { while(true){now=Instant.now();delay(30_000)} }
    BackHandler(enabled=vm.selected!=null){vm.select(null)}
    val results=vm.result?.takeIf { it.routes.isNotEmpty() }
    val detail=vm.selected
    if(detail!=null) {
        RouteDetails(d,vm,detail,now)
    } else if(results!=null) {
        RouteHero(d,vm.city.name)
        CompactQuery(d,vm,{placeDialog=true},{placeDialog=false},::selectTime)
        StrategyRow(d,vm)
        if(vm.message.contains("失败")||vm.message.contains("保留此前"))StatusText(vm.message,warning=true)
        if(!results.demonstration&&!results.freshAt(now))StatusText("此前查询的缓存 · ${results.request.departure.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))} 出发；请更新",warning=true)
        results.routes.forEachIndexed { index,route ->RouteCard(d,route,results,index){vm.select(route)} }
        RouteMapEntry(d,vm.city.name,true,::showMap)
        if(!results.demonstration)Text("耗时为路线估算，候车与安检可能延长；未接入列车实时到站。",fontSize=10.sp,color=routeMuted,lineHeight=16.sp)
        if(vm.departure!=null)Text("指定时刻按当前路线耗时估算，尚未核实该时刻班次。",fontSize=10.sp,color=routeMuted)
    } else {
        Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(d.routeText("routeTitle","查路线")+" ✦",fontSize=27.sp,lineHeight=31.sp,fontWeight=FontWeight.SemiBold,color=d.color("text"))
            Row(Modifier.heightIn(min=25.dp).clickable(enabled=!vm.locating&&!vm.demonstration){requestLocation()},verticalAlignment=Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn,"使用当前位置",Modifier.size(19.dp),tint=d.color("blue"))
                Text(if(vm.demonstration)"演示地点 · 可手动切换"else vm.locationMessage,fontSize=12.sp,color=d.color("blue"),modifier=Modifier.padding(start=6.dp))
            }
        }
        LargeQuery(d,vm,{placeDialog=true},{placeDialog=false},::selectTime)
        Row(Modifier.fillMaxWidth().heightIn(min=38.dp),horizontalArrangement=Arrangement.End,verticalAlignment=Alignment.CenterVertically) {
            Text("定位不准确？手动选择起点  ›",fontSize=12.sp,color=d.color("text"),modifier=Modifier.clickable{placeDialog=true}.padding(vertical=9.dp))
        }
        if(vm.loading||vm.message.contains("请先")||vm.message.contains("相同")||vm.message.contains("失败")||vm.message.contains("暂未找到")||vm.message.contains("未完成"))StatusText(vm.message,vm.message.contains("失败"))
        RouteMapEntry(d,vm.city.name,false,::showMap)
    }
    mapError?.let { StatusText(it,true) }
    if(officialMap)OfficialMetroMap(d){officialMap=false}
    if(detail==null)RouteFooter(d,if(vm.demonstration)"方案预览 · 演示数据"else vm.result?.let { "城市通 · 更新于 "+it.fetchedAt.atZone(routeZone).format(timeFormat) }?:"城市通 · 公共交通路线查询")
    placeDialog?.let { start ->PlaceDialog(vm,start){placeDialog=null;vm.cancelSearch()} }
    if(locationNotice)AlertDialog(onDismissRequest={locationNotice=false;vm.permissionDenied()},title={Text("使用当前位置查询路线")},
        text={Text("仅在查询页获取一次位置，用于识别城市和查找附近起点。坐标会发送给城市通进行地点解析与路线查询；不会读取学校账号，也不持续追踪位置。你也可以手动选择起点。")},
        confirmButton={TextButton(onClick={vm.acceptLocationNotice();locationNotice=false;requestLocation()}){Text("同意并定位")}},
        dismissButton={TextButton(onClick={locationNotice=false;vm.permissionDenied()}){Text("手动选择")}})
}

@Composable private fun LargeQuery(d:DesignConfig,vm:RouteViewModel,onOrigin:()->Unit,onDestination:()->Unit,onTime:()->Unit) {
    RouteSurface(d,light=true) {
        Column {
            Box(Modifier.fillMaxWidth()) {
                Canvas(Modifier.width(17.dp).height(130.dp)) {
                    drawLine(Color(0xffa9b2d1),Offset(7.dp.toPx(),36.dp.toPx()),Offset(7.dp.toPx(),99.dp.toPx()),1.dp.toPx(),pathEffect=androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(),3.dp.toPx())))
                    listOf(31.dp.toPx() to d.color("pink"),99.dp.toPx() to d.color("blue")).forEach { (y,c)->drawCircle(c.copy(alpha=.25f),9.dp.toPx(),Offset(7.dp.toPx(),y));drawCircle(Color.White,8.dp.toPx(),Offset(7.dp.toPx(),y));drawCircle(c,6.dp.toPx(),Offset(7.dp.toPx(),y)) }
                }
                Column(Modifier.fillMaxWidth().padding(start=29.dp,end=48.dp)) {
                    PlaceField(d,"起点",vm.origin?.name?.let(::shortName)?:"选择起点 / 使用定位",onOrigin)
                    HorizontalDivider(color=Color(0xffd2d9eb))
                    PlaceField(d,"终点",vm.destination?.name?.let(::shortName)?:"输入目的地 / 地铁站",onDestination)
                }
                IconButton(onClick=vm::swap,modifier=Modifier.align(Alignment.CenterEnd).size(42.dp).border(.7.dp,Color(0xffd4c9f3),CircleShape)) {
                    Icon(Icons.Default.SwapVert,"交换起终点",Modifier.size(25.dp),tint=Color(0xffb579ba))
                }
            }
            HorizontalDivider(color=Color(0xffd2d9eb),modifier=Modifier.padding(top=7.dp))
            Row(Modifier.fillMaxWidth().heightIn(min=46.dp).clickable(onClick=onTime),verticalAlignment=Alignment.CenterVertically) {
                Icon(Icons.Outlined.Schedule,null,tint=d.color("ink"),modifier=Modifier.size(19.dp));Text(vm.departure?.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))?:"现在出发",color=d.color("ink"),fontSize=14.sp,modifier=Modifier.weight(1f).padding(start=8.dp));Icon(Icons.Default.ChevronRight,null,tint=d.color("ink"),modifier=Modifier.size(18.dp))
            }
            RoutePrimary(d,if(vm.loading)"查询中"else d.routeText("routeQuery","查询路线"),vm.loading,Icons.Outlined.Search,vm::query)
        }
    }
}

@Composable private fun PlaceField(d:DesignConfig,label:String,value:String,onClick:()->Unit) {
    Column(Modifier.fillMaxWidth().heightIn(min=65.dp).clickable(onClick=onClick).padding(vertical=9.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Text(label,fontSize=11.sp,color=Color(0xff7885a0));Text(value,fontSize=if(value.length>13)15.sp else 17.sp,color=if(value.startsWith("输入")||value.startsWith("选择"))Color(0xff8490aa)else d.color("ink"),fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis)
    }
}

@Composable private fun CompactQuery(d:DesignConfig,vm:RouteViewModel,onOrigin:()->Unit,onDestination:()->Unit,onTime:()->Unit) {
    RouteSurface(d,light=true,padding=13) {
        Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable(onClick=onOrigin),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text("出发地",color=Color(0xff697791),fontSize=10.sp);Text(shortName(vm.origin?.name?:"选择起点"),fontSize=16.sp,fontWeight=FontWeight.Bold,color=d.color("ink"),maxLines=2)
                }
                IconButton(onClick=vm::swap,modifier=Modifier.padding(horizontal=4.dp).size(38.dp).border(.6.dp,Color(0xffccd5ed),CircleShape)) {Icon(Icons.Default.SwapHoriz,"交换起终点",tint=d.color("ink"),modifier=Modifier.size(24.dp))}
                Column(Modifier.weight(1f).clickable(onClick=onDestination),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text("目的地",color=Color(0xff697791),fontSize=10.sp);Text(shortName(vm.destination?.name?:"选择目的地"),fontSize=16.sp,fontWeight=FontWeight.Bold,color=d.color("ink"),maxLines=2)
                }
            }
            HorizontalDivider(color=Color(0xffcfd7ec))
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Row(Modifier.weight(1f).heightIn(min=36.dp).clickable(onClick=onTime),verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Schedule,null,tint=d.color("ink"),modifier=Modifier.size(18.dp))
                    val departure=vm.result?.request?.departure?:vm.departure
                    Text(vm.departure?.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))?:"现在出发 · ${departure?.format(timeFormat)?:"—"}",fontSize=11.sp,color=d.color("ink"),modifier=Modifier.padding(start=6.dp))
                    Icon(Icons.Default.ExpandMore,null,tint=d.color("ink"),modifier=Modifier.size(15.dp))
                }
                Row(Modifier.heightIn(min=36.dp).clip(RoundedCornerShape(20.dp)).border(.6.dp,Color(0xff9dbbd8),RoundedCornerShape(20.dp)).clickable(enabled=!vm.loading){vm.query()}.padding(horizontal=12.dp,vertical=7.dp),verticalAlignment=Alignment.CenterVertically) {
                    if(vm.loading)CircularProgressIndicator(Modifier.size(14.dp),strokeWidth=2.dp,color=d.color("ink"))else Icon(Icons.Default.Refresh,null,Modifier.size(15.dp),tint=d.color("ink"))
                    Text(if(vm.loading)" 更新中"else" 更新路线",color=d.color("ink"),fontSize=11.sp)
                }
            }
        }
    }
}

@Composable private fun StrategyRow(d:DesignConfig,vm:RouteViewModel) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
        RouteStrategy.entries.forEach { strategy ->
            val selected=vm.strategy==strategy
            Text(strategy.label,color=if(selected)d.color("ink")else d.color("text"),fontSize=12.sp,fontWeight=if(selected)FontWeight.SemiBold else FontWeight.Normal,
                modifier=Modifier.heightIn(min=34.dp).clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(if(selected)listOf(d.color("pink"),d.color("blue"))else listOf(Color(0xff263143),Color(0xff141c28))))
                    .border(.7.dp,if(selected)Color.White.copy(alpha=.8f)else Color(0xff74869d),RoundedCornerShape(22.dp)).clickable{vm.changeStrategy(strategy)}.padding(horizontal=14.dp,vertical=9.dp))
        }
    }
}

@Composable private fun RoutePrimary(d:DesignConfig,text:String,loading:Boolean=false,icon:androidx.compose.ui.graphics.vector.ImageVector=Icons.Default.Refresh,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=50.dp).clip(RoundedCornerShape(18.dp)).background(Brush.horizontalGradient(listOf(d.color("pink"),Color(0xffe4d9fa),d.color("blue"))))
        .border(.8.dp,Color.White.copy(alpha=.8f),RoundedCornerShape(18.dp)).clickable(enabled=!loading,onClick=onClick).padding(horizontal=13.dp,vertical=12.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center) {
        if(loading)CircularProgressIndicator(Modifier.size(19.dp),strokeWidth=2.dp,color=d.color("ink"))else Icon(icon,null,Modifier.size(22.dp),tint=d.color("ink"))
        Text(" $text",color=d.color("ink"),fontSize=16.sp,fontWeight=FontWeight.SemiBold)
    }
}

@Composable private fun RouteCard(d:DesignConfig,route:TransitRoute,result:RouteResult,index:Int,onClick:()->Unit) {
    val ink=if(index==0)d.color("ink")else d.color("text")
    RouteSurface(d,light=index==0,padding=11,modifier=Modifier.clickable(onClick=onClick)) {
        Column(verticalArrangement=Arrangement.spacedBy(if(index==0)6.dp else 4.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                Text(if(index==0)"★  推荐路线"else if(route.walkMeters<(result.routes.firstOrNull()?.walkMeters?:0))"步行更少"else"其他方案",color=if(index==0)Color(0xffc35a96)else d.color("blue"),fontSize=11.sp,
                    modifier=Modifier.background(if(index==0)d.color("pink").copy(alpha=.18f)else d.color("blue").copy(alpha=.08f),RoundedCornerShape(15.dp)).padding(horizontal=10.dp,vertical=4.dp))
                Text("查看详情 ›",fontSize=11.sp,color=if(index==0)Color(0xffc35a96)else d.color("text"))
            }
            Row(verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment=Alignment.Bottom) {Text(minutes(route.durationSeconds),fontSize=if(index==0)37.sp else 31.sp,fontWeight=FontWeight.Bold,color=ink,lineHeight=if(index==0)40.sp else 34.sp);Text(" 分钟",fontSize=14.sp,fontWeight=FontWeight.SemiBold,color=ink,modifier=Modifier.padding(bottom=5.dp))}
                Text(TransitTimeEvaluator.estimatedArrival(route,result.request.departure)?.let{"预计 ${it.format(timeFormat)} 到达"}?:"耗时暂无",fontSize=11.sp,color=if(index==0)Color(0xff586b88)else Color(0xffc1cadf),modifier=Modifier.padding(bottom=6.dp))
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    route.rides.forEachIndexed { i,ride ->
                        if(i>0)Text("→",color=ink,fontSize=14.sp)
                        val color=if(ride.name.contains("4号线"))Color(0xff38b985)else if(ride.name.contains("2号线"))Color(0xffee78b4)else d.color(if(i%2==0)"blue"else"pink")
                        Text(ride.name.removePrefix("轨道交通"),color=if(color==d.color("blue"))d.color("ink")else Color.White,fontSize=11.sp,modifier=Modifier.background(color,RoundedCornerShape(12.dp)).padding(horizontal=11.dp,vertical=6.dp))
                    }
                }
                if(route.transfers>0)MiniTransfer(d,light=index==0,station=route.rides.first().destination.name)
            }
            if(route.transfers>0)Text("⌖  "+route.rides.dropLast(1).joinToString("、"){it.destination.name}+"换乘",color=ink,fontSize=11.sp)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.AutoMirrored.Filled.DirectionsWalk,null,Modifier.size(14.dp),tint=ink);Text("步行 ${route.walkMeters} 米",color=ink,fontSize=10.sp)}
                Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Outlined.Train,null,Modifier.size(14.dp),tint=ink);Text(" ${route.stations} 站",color=ink,fontSize=10.sp)}
                Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Outlined.AccountBalanceWallet,null,Modifier.size(14.dp),tint=ink);Text(route.fare?.let{"约 $it 元"}?:"票价暂无",color=ink,fontSize=10.sp)}
            }
            if(!result.demonstration&&route.rides.any{it.subway})Text("详情可查官方首末班 · 路线耗时估算",fontSize=9.sp,color=if(index==0)Color(0xff9c6849)else Color(0xffd5b596))
        }
    }
}

@Composable private fun MiniTransfer(d:DesignConfig,light:Boolean,station:String) {
    Column(Modifier.width(105.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Canvas(Modifier.fillMaxWidth().height(16.dp)) {
            val y=size.height*.5f;val first=Offset(8.dp.toPx(),y);val middle=Offset(size.width*.5f,y);val last=Offset(size.width-8.dp.toPx(),y)
            drawLine(Color(0xff38b985),first,middle,2.dp.toPx());drawLine(Color(0xffee78b4),middle,last,2.dp.toPx())
            listOf(first to Color(0xff38b985),last to Color(0xffee78b4)).forEach { (p,c)->drawCircle(if(light)Color(0xfffff5fd)else d.color("surface"),4.dp.toPx(),p);drawCircle(c,4.dp.toPx(),p,style=Stroke(1.7.dp.toPx())) }
            drawCircle(if(light)d.color("card")else d.color("surface"),6.dp.toPx(),middle);drawCircle(d.color("purple"),6.dp.toPx(),middle,style=Stroke(1.4.dp.toPx()));drawCircle(d.color("pink"),2.dp.toPx(),middle)
        }
        Text(station,fontSize=10.sp,color=if(light)Color(0xff526782)else routeMuted)
    }
}

@Composable private fun RouteDetails(d:DesignConfig,vm:RouteViewModel,route:TransitRoute,now:Instant) {
    Row(verticalAlignment=Alignment.CenterVertically) {
        IconButton(onClick={vm.select(null)},modifier=Modifier.size(34.dp)){Icon(Icons.AutoMirrored.Filled.ArrowBack,"返回推荐路线",Modifier.size(23.dp))}
        Column(Modifier.padding(start=5.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text("换乘提醒",fontSize=21.sp,fontWeight=FontWeight.SemiBold);Text("${shortName(vm.origin?.name?:"")} → ${shortName(vm.destination?.name?:"")}",fontSize=11.sp,color=routeMuted)
        }
    }
    val assessment=if(vm.lateDemo&&vm.demonstration)RouteDemo.lastAssessment()else TransitTimeEvaluator.lastConnection(route,vm.result?.request?.departure?:ZonedDateTime.now(routeZone),now,emptyList())
    if(assessment.latestDeparture!=null) {
        RouteSurface(d,light=true,padding=13,rose=true) {
            Image(painterResource(R.drawable.route_clock),null,Modifier.size(145.dp).align(Alignment.TopEnd).offset(x=15.dp,y=(-7).dp),contentScale=ContentScale.Fit)
            Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text("◷  时间紧张",fontSize=12.sp,color=Color(0xffd77b33),modifier=Modifier.background(Color(0xffffdfbe).copy(alpha=.8f),RoundedCornerShape(20.dp)).padding(horizontal=11.dp,vertical=4.dp))
                Text(d.routeText("routeWarningTitle","建议最晚出发"),fontSize=19.sp,fontWeight=FontWeight.SemiBold,color=d.color("ink"))
                Text(assessment.latestDeparture.format(timeFormat),fontSize=if(LocalConfiguration.current.screenWidthDp<360)54.sp else 60.sp,lineHeight=60.sp,fontWeight=FontWeight.Bold,color=Color(0xffc54f92))
                Text("距建议时间还有 3 分钟",fontSize=14.sp,color=Color(0xff576684))
                Row(Modifier.fillMaxWidth().background(Color.White.copy(alpha=.35f),RoundedCornerShape(12.dp)).padding(8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                    Text("⚑  预计 ${assessment.arrival?.format(timeFormat)} 到达",color=d.color("ink"),fontSize=12.sp,fontWeight=FontWeight.SemiBold)
                    Text("已预留进站与换乘时间",fontSize=9.sp,color=Color(0xff5f6c8b))
                }
            }
        }
    } else OfficialMetroCard(d,vm.metro,vm.result?.request?.departure?.toLocalDate()?:LocalDate.now(routeZone),vm::refreshMetro)
    RouteSurface(d,padding=12) {
        Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Outlined.Route,null,Modifier.size(21.dp),tint=d.color("pink"));Text(" 关键换乘",fontSize=18.sp,fontWeight=FontWeight.SemiBold)}
            Column(Modifier.fillMaxWidth().background(Color(0xff151e2e).copy(alpha=.6f),RoundedCornerShape(14.dp)).border(.5.dp,Color(0xff697a9e).copy(alpha=.3f),RoundedCornerShape(14.dp)).padding(12.dp)) {
                val positions=route.segments.indices.filter { route.segments[it] is RouteSegment.Ride }
                route.rides.forEachIndexed { index,ride ->
                    TimelineStop(d,ride.origin.name,if(index==0)d.color("pink")else d.color("blue")) {
                        if(index>0) {
                            assessment.runs.getOrNull(index-1)?.let {Text("${it.arrives.format(timeFormat)} 到达",fontSize=13.sp,color=d.color("pink"),fontWeight=FontWeight.Medium)}
                            val walking=route.segments.subList(positions[index-1]+1,positions[index]).filterIsInstance<RouteSegment.Walk>().sumOf { it.durationSeconds?:0 }
                            Text("换乘步行 ${minutes(walking)} 分钟"+if(vm.lateDemo)" · 预留 4 分钟"else"",fontSize=11.sp,color=routeMuted)
                        }
                        if(index>0) {
                            Column(Modifier.fillMaxWidth().background(d.color("blue").copy(alpha=.09f),RoundedCornerShape(12.dp)).border(.6.dp,d.color("blue").copy(alpha=.6f),RoundedCornerShape(12.dp)).padding(10.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                Text("▣ ${ride.name.removePrefix("轨道交通")} · ${ride.direction}方向",fontSize=12.sp,color=d.color("blue"),fontWeight=FontWeight.Medium)
                                Text(if(vm.lateDemo)"23:00 示例末班"else "本站首末班见上方官方资料",fontSize=11.sp,color=d.color("blue"))
                            }
                        } else {
                            Text((assessment.runs.getOrNull(index)?.let{"${it.departs.format(timeFormat)} 上车 · "}?:"")+ride.name.removePrefix("轨道交通"),fontSize=13.sp,color=if(vm.lateDemo)d.color("pink")else d.color("text"),fontWeight=FontWeight.Medium)
                            Text("${ride.direction}方向 · ${ride.via.size+1}站 · ${minutes(ride.durationSeconds)}分钟",fontSize=11.sp,color=routeMuted)
                            if(!vm.lateDemo)Text("本站首末班见上方官方资料",fontSize=10.sp,color=Color(0xffdbb9a5))
                        }
                        if(!vm.lateDemo&&ride.via.isNotEmpty()) {
                            var expanded by remember(ride.lineId){mutableStateOf(false)}
                            Text(if(expanded)"收起途经站"else"查看途经站",fontSize=10.sp,color=d.color("blue"),modifier=Modifier.clickable{expanded=!expanded}.padding(vertical=5.dp))
                            if(expanded)Text(ride.via.joinToString(" → "){it.name},fontSize=10.sp,color=routeMuted)
                        }
                    }
                }
                route.rides.lastOrNull()?.let { ride ->TimelineStop(d,ride.destination.name,d.color("pink"),last=true) {
                    val endWalk=route.segments.drop(positions.last()+1).filterIsInstance<RouteSegment.Walk>().sumOf { it.durationSeconds?:0 }
                    Text((assessment.runs.lastOrNull()?.let{"${it.arrives.format(timeFormat)} 到站 · "}?:"")+"步行 ${minutes(endWalk)} 分钟",fontSize=11.sp,color=routeMuted)
                } }
            }
        }
    }
    Text(if(vm.lateDemo)"ⓘ 演示时刻表，实际请以运营信息为准"else"ⓘ 耗时为估算，请以现场运营信息为准",fontSize=10.sp,color=routeMuted,modifier=Modifier.fillMaxWidth().padding(horizontal=10.dp))
    Row(horizontalArrangement=Arrangement.spacedBy(9.dp)) {
        Box(Modifier.weight(1f)){RoutePrimary(d,if(vm.loading)"更新中"else if(vm.lateDemo)"更新判断"else"更新路线",vm.loading,Icons.Default.Refresh,vm::query)}
        Row(Modifier.weight(1f).heightIn(min=50.dp).clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(listOf(Color(0xff283348),d.color("surface")))).border(.7.dp,Color(0xff9badc9),RoundedCornerShape(18.dp)).clickable{vm.select(null)}.padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center) {
            Icon(Icons.Outlined.Route,null,Modifier.size(21.dp));Text(" 其他路线",fontSize=14.sp,fontWeight=FontWeight.Medium)
        }
    }
}

@Composable private fun TimelineStop(d:DesignConfig,name:String,color:Color,last:Boolean=false,content:@Composable ColumnScope.()->Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Canvas(Modifier.width(23.dp).fillMaxHeight()) {
            val center=Offset(8.dp.toPx(),12.dp.toPx())
            if(!last)drawLine(color.copy(alpha=.85f),center,Offset(center.x,size.height+10.dp.toPx()),1.2.dp.toPx(),pathEffect=androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(),3.dp.toPx())))
            drawCircle(color.copy(alpha=.12f),10.dp.toPx(),center);drawCircle(color.copy(alpha=.25f),8.dp.toPx(),center);drawCircle(Color.White,6.5.dp.toPx(),center);drawCircle(color,5.dp.toPx(),center)
        }
        Column(Modifier.weight(1f).padding(start=3.dp,bottom=if(last)0.dp else 10.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(name,fontSize=17.sp,fontWeight=FontWeight.SemiBold);content()
        }
    }
}
@Composable private fun StatusText(message:String,warning:Boolean=false) {Text(message,fontSize=11.sp,color=if(warning)Color(0xffe4b99c)else routeMuted,lineHeight=17.sp)}

@Composable private fun PlaceDialog(vm:RouteViewModel,start:Boolean,onDismiss:()->Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest=onDismiss,title={Text(if(start)"选择起点"else"选择目的地")},text={Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("${vm.city.name} · 请选择明确的地点或车站",fontSize=12.sp)
        OutlinedTextField(text,{text=it;vm.search(it)},label={Text("搜索地点")},singleLine=true,modifier=Modifier.fillMaxWidth())
        if(vm.searching)LinearProgressIndicator(Modifier.fillMaxWidth())
        vm.candidates.forEach { candidate ->Column(Modifier.fillMaxWidth().clickable{vm.choose(candidate,start);onDismiss()}.heightIn(min=56.dp).padding(vertical=8.dp)) {
            Text(candidate.name,fontWeight=FontWeight.Medium,fontSize=14.sp);Text("${candidate.cityName} · ${candidate.address}",fontSize=11.sp,color=routeMuted)
        };HorizontalDivider(color=Color.White.copy(alpha=.08f)) }
        if(text.isNotBlank()&&!vm.searching&&vm.candidates.isEmpty())Text("暂无候选，可换一个关键词；${vm.message}",fontSize=12.sp,color=routeMuted)
    }},confirmButton={TextButton(onClick=onDismiss){Text("关闭")}})
}
@Composable private fun CityDialog(vm:RouteViewModel,onDismiss:()->Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest=onDismiss,title={Text("选择城市")},text={Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState())) {
        OutlinedTextField(name,{name=it},label={Text("搜索城市")},singleLine=true)
        RouteCity.choices.filter{it.name.contains(name)}.forEach { city ->TextButton(onClick={vm.changeCity(city);onDismiss()},modifier=Modifier.fillMaxWidth()){Text(city.name)} }
        Text("其他城市可使用定位识别，实际以服务覆盖为准。",fontSize=11.sp,color=routeMuted)
        if(BuildConfig.DEBUG) {
            HorizontalDivider(Modifier.padding(vertical=10.dp))
            TextButton(onClick={vm.enableDemonstration(true);onDismiss()}){Text("界面演示")}
            TextButton(onClick={vm.enableDemonstration(true);vm.toggleLateDemo();onDismiss()}){Text("末班提醒演示")}
            if(vm.demonstration)TextButton(onClick={vm.enableDemonstration(false);onDismiss()}){Text("返回真实查询")}
            TextButton(onClick={vm.clearCache();onDismiss()}){Text("清除最近路线")}
        }
    }},confirmButton={TextButton(onClick=onDismiss){Text("关闭")}})
}
private fun openSubway(context:Context,city:RouteCity):Boolean {
    val code=when(city.name){"北京市"->"110000";"上海市"->"310000";"重庆市"->"500000";else->city.code}
    val uri=Uri.parse("androidamap://openFeature").buildUpon().appendQueryParameter("featureName","Subway").appendQueryParameter("adcode",code).appendQueryParameter("sourceApplication","CampusUsage").appendQueryParameter("page","Subway").build()
    if(runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,uri).setPackage("com.autonavi.minimap"))}.isSuccess)return true
    return runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://map.amap.com/subway/index.html")))}.isSuccess
}
