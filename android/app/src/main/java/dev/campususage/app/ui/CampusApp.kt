package dev.campususage.app.ui

import android.app.DatePickerDialog
import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.campususage.app.BuildConfig
import dev.campususage.app.R
import dev.campususage.app.SchoolLoginActivity
import dev.campususage.app.core.design.DesignConfig
import dev.campususage.app.core.model.*
import dev.campususage.app.feature.routes.model.QuerySection
import dev.campususage.app.feature.routes.ui.RouteViewModel
import dev.campususage.app.feature.routes.ui.RoutesScreen
import dev.campususage.app.feature.routes.ui.RouteExperience
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.max

private fun fetchedTime(epoch: Long?): String = epoch?.let { java.time.Instant.ofEpochMilli(it).atZone(PublicationPolicy.zone).format(DateTimeFormatter.ofPattern("MM-dd HH:mm")) } ?: "尚未更新"

@Composable fun CampusApp(d: DesignConfig, vm: UsageViewModel = viewModel()) {
    val routes: RouteViewModel = viewModel()
    val text=d.color("text");val muted=Color(0xff9ca7bf)
    val context=LocalContext.current
    val login=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        vm.authRequest=null
        if(result.resultCode==Activity.RESULT_OK)vm.authenticated()
    }
    LaunchedEffect(vm.authRequest) { vm.authRequest?.let { login.launch(Intent(context,SchoolLoginActivity::class.java).putExtra("system",it)) } }
    CompositionLocalProvider(LocalAppearance provides vm.appearance) {
    MaterialTheme(colorScheme=darkColorScheme(primary=d.color("pink"),secondary=d.color("blue"),background=d.color("background"),surface=d.color("surface"),onSurface=text,onBackground=text)) {
        if(vm.page=="query"&&vm.querySection==QuerySection.ROUTES) {
            RouteExperience(d,routes,onSection={section->if(section==QuerySection.ROUTES)vm.selectRoutes()else vm.selectCategory(Category.valueOf(section.name))},onNavigate={vm.go(it)})
            return@MaterialTheme
        }
        if(vm.page in listOf("home","trends","profile")) {
            CharacterExperience(d,vm)
            return@MaterialTheme
        }
        Scaffold(containerColor=Color.Transparent,contentColor=text,modifier=Modifier.background(d.color("background")).drawBehind {
            if(d.glow) {
                drawRect(Brush.radialGradient(listOf(d.color("pink").copy(alpha=.06f),Color.Transparent),center=androidx.compose.ui.geometry.Offset(0f,size.height*.35f),radius=size.width*1.2f))
                drawRect(Brush.radialGradient(listOf(d.color("blue").copy(alpha=.035f),Color.Transparent),center=androidx.compose.ui.geometry.Offset(size.width,size.height*.7f),radius=size.width))
            }
        },bottomBar={
            CampusBottomNavigation(d,vm.page){vm.go(it)}
        }) { padding ->
            Box(Modifier.fillMaxSize()) {
            CustomBackground(Modifier.fillMaxSize())
            key(vm.page) {
                Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal=d.size("pagePadding").dp),verticalArrangement=Arrangement.spacedBy(d.size("gap").dp)) {
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                        Column {Text("CAMPUS · EVERYDAY",fontSize=8.sp,letterSpacing=2.sp,color=muted);Text(d.copy("appTitle")+" ✧",fontFamily=FontFamily.Serif,fontSize=25.sp,color=Color(0xffffedf7),modifier=Modifier.padding(top=6.dp))}
                        OutlinedButton(onClick={vm.go("profile")},contentPadding=PaddingValues(horizontal=10.dp),border=BorderStroke(.5.dp,muted.copy(alpha=.6f)),modifier=Modifier.heightIn(min=48.dp)) {Icon(Icons.Default.PersonOutline,null,Modifier.size(14.dp));Text(if(vm.demo)"演示账户" else "学校账户",fontSize=10.sp)}
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {Text(if(vm.page=="query"&&vm.querySection==QuerySection.ROUTES)if(routes.demonstration)"✦ 路线演示" else "✦ 城市通查询" else if(vm.demo)"✦ 演示数据" else "✦ 学校数据",color=d.color("pink"),fontSize=10.sp);Text("设计 v${d.revision} · "+if(vm.demo)"独立演示" else "本地缓存优先",color=muted,fontSize=10.sp)}
                    when(vm.page) {
                        "home" -> d.order("sections").forEach { section -> when(section) {
                            "hero" -> Hero(d)
                            "summary" -> Summary(d,vm)
                            "quick" -> Quick(d,vm)
                            "trend" -> Glass(d) {PanelHeading(d.copy("trendTitle"),Icons.Default.BarChart,d);UsageChart(d,vm,Category.ELECTRICITY,7)}
                            "notice" -> Glass(d,tint=d.color("pink").copy(alpha=.1f)) {Row(verticalAlignment=Alignment.CenterVertically) {Icon(Icons.Default.NotificationsNone,null,tint=d.color("pink"),modifier=Modifier.padding(end=12.dp));Column {Text(d.copy("noticeTitle"),fontSize=13.sp);Text(d.copy("noticeBody"),fontSize=11.sp,color=muted,modifier=Modifier.padding(top=8.dp))}}}
                        }}
                        "query" -> Query(d,vm,routes)
                        "trends" -> Trends(d,vm)
                        else -> Profile(d,vm)
                    }
                    Spacer(Modifier.height(18.dp))
                }
            }
            }
        }
    }
    }
}

@Composable private fun Glass(d: DesignConfig, tint: Color=Color.Transparent, content:@Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(d.size("radius").dp)).background(Brush.linearGradient(listOf(tint,d.color("surface"))))
        .border(.6.dp,Color(0xffd8def3).copy(alpha=.22f),RoundedCornerShape(d.size("radius").dp)).padding(15.dp),verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
}
@Composable private fun PanelHeading(title:String,icon:ImageVector,d:DesignConfig) {Row(verticalAlignment=Alignment.CenterVertically) {Icon(icon,null,Modifier.size(18.dp),tint=d.color("blue"));Text(title,Modifier.padding(start=8.dp),fontSize=d.size("bodySize").sp,fontWeight=FontWeight.Medium)}}
@Composable private fun Hero(d: DesignConfig) {
    Box(Modifier.fillMaxWidth().height(d.size("heroHeight").dp).clip(RoundedCornerShape(d.size("radius").dp)).background(Color(0xffd6e8fc)).border(1.dp,Color.White.copy(alpha=.8f),RoundedCornerShape(d.size("radius").dp))) {
        Image(painterResource(R.drawable.campus_banner),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,Color(0xfff5f4ff).copy(alpha=.9f),Color(0xfff5f4ff)),startY=80f)))
        Text(d.copy("heroNote"),Modifier.align(Alignment.TopEnd).padding(13.dp),fontSize=14.sp,fontFamily=FontFamily.Serif,color=Color(0xff394e77))
        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {Text(d.copy("greeting"),fontFamily=FontFamily.Serif,fontSize=23.sp,color=d.color("ink"));Text(d.copy("subtitle"),fontSize=11.sp,color=Color(0xff58617d),modifier=Modifier.padding(top=4.dp))}
    }
}
@Composable private fun Summary(d: DesignConfig,vm: UsageViewModel) {
    val narrow=LocalConfiguration.current.screenWidthDp<360
    val columns=if(LocalDensity.current.fontScale>1.25f||d.size("bodySize")>16||(narrow&&d.size("valueSize")>32))1 else 2
    Column(verticalArrangement=Arrangement.spacedBy(d.size("gap").dp)) {
        d.order("cards").chunked(columns).forEach { row -> Row(horizontalArrangement=Arrangement.spacedBy(d.size("gap").dp)) {row.forEach { id ->
            val category=if(id=="energy")Category.ENERGY else Category.ELECTRICITY
            val reading=vm.reading(category,vm.latestDate)
            val monthRecords=(1..vm.now.dayOfMonth).mapNotNull { day -> vm.reading(Category.ELECTRICITY,vm.now.toLocalDate().withDayOfMonth(day)) }
            val value=when(id){"balance"->vm.network()?.balance?.toPlainString()?:"—";"month"->if(monthRecords.isEmpty())"—" else monthRecords.fold(java.math.BigDecimal.ZERO){sum,r->sum+r.total}.setScale(2,java.math.RoundingMode.HALF_UP).toPlainString();else->reading?.total?.toPlainString()?:"—"}
            val label=when(id){"electricity"->"electricityLabel";"energy"->"energyLabel";"month"->"monthLabel";else->"balanceLabel"}
            val icon=when(id){"electricity"->Icons.Default.Bolt;"energy"->Icons.Default.BlurOn;"month"->Icons.Default.BarChart;else->Icons.Default.AccountBalanceWallet}
            val accent=if(id=="energy")d.color("blue") else if(id=="month")d.color("purple") else d.color("pink")
            Column(Modifier.weight(1f).clip(RoundedCornerShape(d.size("radius").dp)).background(Brush.linearGradient(listOf(accent.copy(alpha=.9f),d.color("card"))))
                .clickable {vm.go(if(id=="month")"trends" else "query",if(id=="balance")Category.NETWORK else category)}.padding(12.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {Box(Modifier.size(27.dp).background(Color.White.copy(alpha=.6f),CircleShape),contentAlignment=Alignment.Center) {Icon(icon,null,Modifier.size(17.dp),tint=if(id=="energy")Color(0xff4c9ac1) else Color(0xffbf6b9d))};Text(d.copy(label),Modifier.padding(start=5.dp).weight(1f),fontSize=(d.size("bodySize")-3).sp,color=d.color("ink"));Icon(Icons.Default.ChevronRight,null,Modifier.size(13.dp),tint=d.color("ink"))}
                Row(verticalAlignment=Alignment.Bottom) {Text(value,fontFamily=FontFamily.Serif,fontSize=d.size("valueSize").sp,color=d.color("ink"));Text(if(id=="balance")" 元" else " kWh",fontSize=10.sp,color=d.color("ink"),modifier=Modifier.padding(bottom=5.dp))}
                Text(if(id=="balance")if(vm.demo)"演示快照 · 09:41" else vm.network()?.let { "获取于 "+fetchedTime(it.fetchedAt) }?:"连接校园网后更新" else if(id=="month")"已取得 ${monthRecords.size}/${vm.now.dayOfMonth} 日" else "统计日期 ${vm.latestDate.format(DateTimeFormatter.ofPattern("MM-dd"))}",fontSize=10.sp,color=Color(0xff747e96))
            }
        }}}
    }
}
@Composable private fun Quick(d:DesignConfig,vm:UsageViewModel) {Glass(d) {PanelHeading(d.copy("quickTitle"),Icons.Default.AutoAwesome,d);Row(horizontalArrangement=Arrangement.spacedBy(7.dp)) {listOf(Triple("电费查询",Icons.Default.Bolt,Category.ELECTRICITY),Triple("能源查询",Icons.Default.BlurOn,Category.ENERGY),Triple("校园网",Icons.Default.Wifi,Category.NETWORK),Triple("历史趋势",Icons.Default.BarChart,null)).forEachIndexed { i,(title,icon,category) ->
    val accent=d.color(listOf("pink","blue","purple","pink")[i]);Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha=.1f)).border(.7.dp,accent.copy(alpha=.6f),RoundedCornerShape(14.dp)).clickable{vm.go(if(category==null)"trends" else "query",category)}.padding(vertical=14.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(9.dp)) {Icon(icon,null,Modifier.size(25.dp),tint=accent);Text(title,fontSize=11.sp)}
}}}}
@Composable private fun UsageChart(d:DesignConfig,vm:UsageViewModel,category:Category,count:Int,endDate:LocalDate=vm.latestDate) {
    val records=(count-1 downTo 0).map { delta -> val date=endDate.minusDays(delta.toLong()); date to vm.reading(category,date) }
    Text("${category.label}用量 · kWh    缺口保留为空",fontSize=10.sp,color=Color(0xff9ca7bf))
    Canvas(Modifier.fillMaxWidth().height(150.dp).semantics{contentDescription="${count}日${category.label}${if(vm.demo)"演示" else "学校"}用量趋势"}) {
        val left=24.dp.toPx();val bottom=size.height-22.dp.toPx();val top=16.dp.toPx();val step=(size.width-left-7.dp.toPx())/count
        val maximum=kotlin.math.ceil(records.maxOfOrNull { it.second?.total?.toFloat()?:0f }?.toDouble()?:1.0).toFloat().coerceAtLeast(1f)
        val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {color=android.graphics.Color.rgb(160,173,198);textSize=9.sp.toPx()}
        for(n in 0..4) {val y=bottom-(bottom-top)*n/4f;drawLine(Color.White.copy(alpha=.09f),androidx.compose.ui.geometry.Offset(left,y),androidx.compose.ui.geometry.Offset(size.width,y),strokeWidth=1f);drawContext.canvas.nativeCanvas.drawText(java.lang.String.format(java.util.Locale.ROOT,"%.1f",maximum*n/4f),0f,y+3.dp.toPx(),paint)}
        records.forEachIndexed { i,(date,reading) ->
            val x=left+i*step+step*.22f
            if(reading!=null) {val height=max(2.dp.toPx(),reading.total.toFloat()/maximum*(bottom-top));drawRoundRect(Brush.verticalGradient(listOf(d.color(if(category==Category.ENERGY)"blue" else "pink"),d.color("purple"))),androidx.compose.ui.geometry.Offset(x,bottom-height),androidx.compose.ui.geometry.Size(step*.56f,height),androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))}
            if(i%max(1,count/7)==0 && i<count-1 || i==count-1) drawContext.canvas.nativeCanvas.drawText(date.format(DateTimeFormatter.ofPattern("M/d")),x-4.dp.toPx(),bottom+16.dp.toPx(),paint)
        }
    }
}
@Composable private fun Title(eyebrow:String,title:String) {Column(Modifier.padding(top=10.dp,bottom=6.dp)) {Text(eyebrow,fontSize=8.sp,letterSpacing=1.5.sp,color=Color(0xff9c91b6));Text(title,fontFamily=FontFamily.Serif,fontSize=23.sp,modifier=Modifier.padding(top=10.dp))}}
@Composable private fun CategoryTabs(vm:UsageViewModel,network:Boolean=true) {Row(horizontalArrangement=Arrangement.spacedBy(7.dp)) {(if(network)Category.entries else listOf(Category.ELECTRICITY,Category.ENERGY)).forEach { category -> FilterChip(selected=vm.category==category,onClick={vm.selectCategory(category)},label={Text(category.label)},modifier=Modifier.weight(1f).heightIn(min=48.dp))}}}
@Composable private fun Query(d:DesignConfig,vm:UsageViewModel,routes:RouteViewModel) {
    val context=LocalContext.current
    CampusPicker(d,vm)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
        QuerySection.entries.forEach { section -> FilterChip(selected=vm.querySection==section,onClick={if(section==QuerySection.ROUTES)vm.selectRoutes()else vm.selectCategory(Category.valueOf(section.name))},label={Text(if(section==QuerySection.ROUTES)section.label else vm.categoryLabel(Category.valueOf(section.name)),fontSize=12.sp)},modifier=Modifier.heightIn(min=48.dp)) }
    }
    if(vm.querySection==QuerySection.ROUTES){RoutesScreen(d,routes);return}
    if(vm.campus==Campus.MAIN&&vm.category!=Category.NETWORK){MainCampusEntry(d);return}
    Title("YOUR CAMPUS, IN FOCUS","每一份用量，都清楚。")
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
        if(vm.category!=Category.NETWORK) Row {listOf("day" to "日","month" to "月").forEach { (key,label) -> TextButton(onClick={vm.selectPeriod(key)}) {Text(label,color=if(vm.period==key)d.color("pink") else Color.Gray)} }} else Text("当前套餐快照",fontSize=12.sp)
        Button(onClick=vm::refresh,enabled=!vm.updating,shape=RoundedCornerShape(12.dp)) {if(vm.updating)CircularProgressIndicator(Modifier.size(16.dp),strokeWidth=2.dp) else Icon(Icons.Default.Refresh,null,Modifier.size(16.dp));Text(if(vm.updating)" 更新中" else " 更新")}
    }
    if(vm.category!=Category.NETWORK) Row(Modifier.fillMaxWidth().background(d.color("surface"),RoundedCornerShape(12.dp)),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
        IconButton(onClick={vm.selectDate(vm.date.minusDays(1))}) {Icon(Icons.Default.ChevronLeft,"前一天")}
        TextButton(onClick={DatePickerDialog(context,{_,year,month,day->vm.selectDate(LocalDate.of(year,month+1,day))},vm.date.year,vm.date.monthValue-1,vm.date.dayOfMonth).show()}) {Icon(Icons.Default.CalendarToday,null,Modifier.size(15.dp));Text(" ${vm.date}")}
        IconButton(onClick={vm.selectDate(vm.date.plusDays(1))}) {Icon(Icons.Default.ChevronRight,"后一天")}
    }
    Text(vm.message,fontSize=11.sp,color=if(vm.scenario==Scenario.OFFLINE||vm.scenario==Scenario.LOGIN)Color(0xffd6b697) else Color(0xff95beb1),modifier=Modifier.fillMaxWidth().background(d.color("surface"),RoundedCornerShape(9.dp)).padding(11.dp))
    if(vm.category==Category.NETWORK) Network(d,vm) else if(vm.period=="month") Glass(d) {PanelHeading("${vm.date.toString().take(7)} 用量分布",Icons.Default.BarChart,d);UsageChart(d,vm,vm.category,vm.date.lengthOfMonth(),vm.date.withDayOfMonth(vm.date.lengthOfMonth()));Text("已取得日期合计，缺失不补零",fontSize=11.sp)} else {
        val reading=vm.reading(vm.category,vm.date)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(d.size("radius").dp)).background(Brush.linearGradient(listOf(Color(0xfff2def2),d.color("card")))).padding(22.dp),verticalArrangement=Arrangement.spacedBy(13.dp)) {
            Text("${vm.category.label}用量 · ${vm.date}",color=Color(0xff737e97),fontSize=12.sp)
            Row(verticalAlignment=Alignment.Bottom) {Text(reading?.total?.setScale(4,java.math.RoundingMode.HALF_UP)?.toPlainString()?:"—",fontSize=45.sp,fontFamily=FontFamily.Serif,color=d.color("ink"));Text(" kWh",color=d.color("ink"),fontSize=13.sp,modifier=Modifier.padding(bottom=8.dp))}
            Text(if(reading!=null)if(vm.demo)"有效日统计 · 演示数据" else "学校日统计 · "+fetchedTime(reading.fetchedAt) else if(PublicationPolicy.isExpected(vm.date,vm.now))"暂未取得该日统计，稍后可再更新" else "该日用量一般于次日早上 8 点后可查",color=Color(0xff828ba1),fontSize=11.sp)
            HorizontalDivider(color=Color(0xffd6d6e4))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {listOf("房间用量" to reading?.room,"公区用量" to reading?.publicArea).forEach { (label,value) -> Column {Text(label,fontSize=11.sp,color=Color(0xff7a869d));Text("${value?.toPlainString()?:"—"} kWh",color=d.color("ink"),fontSize=16.sp,modifier=Modifier.padding(top=8.dp))}}}
        }
        Glass(d) {PanelHeading("近期用量",Icons.Default.BarChart,d);UsageChart(d,vm,vm.category,7)}
    }
    Text(if(vm.demo)"✦ 当前为隔离演示，更新不会访问学校。" else "来源：学校水电能源 / 校园网。更新失败时保留此前记录。",fontSize=10.sp,color=Color(0xff8692ac))
}
@Composable private fun Network(d:DesignConfig,vm:UsageViewModel) {
    val snapshot=vm.network()
    if(snapshot==null) {
        Glass(d) {PanelHeading("校园网尚未取得数据",Icons.Default.Wifi,d);Text("完成学校统一认证后，点击更新读取官方套餐。",fontSize=12.sp);Button(onClick={vm.requestLogin("network")}){Text("连接校园网")}}
        return
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(d.size("radius").dp)).background(Brush.linearGradient(listOf(Color(0xffd8dcfa),Color(0xfff1ddf0)))).padding(22.dp),verticalArrangement=Arrangement.spacedBy(13.dp)) {
        Text("CAMPUS NETWORK · "+if(vm.demo)"演示" else "学校快照",fontSize=9.sp,color=Color(0xff9384a9));Text(snapshot.plan,fontSize=24.sp,fontFamily=FontFamily.Serif,color=d.color("ink"));Text("账户余额",fontSize=12.sp,color=Color(0xff84758e));Text("${snapshot.balance.toPlainString()} 元",fontSize=37.sp,fontFamily=FontFamily.Serif,color=d.color("ink"));Text("到期日    ${snapshot.expires}",fontSize=12.sp,color=Color(0xff80748d))
    }
    Glass(d) {PanelHeading("分类流量",Icons.Default.Wifi,d);snapshot.traffic.forEach { usage ->
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {Text(usage.name,fontSize=12.sp);Text("${usage.used.toPlainString()} ${usage.unit} 已用",fontSize=13.sp,color=d.color("blue"))}
        if(usage.total!=null) {val progress=if(usage.total.signum()>0)(usage.used.toFloat()/usage.total.toFloat()).coerceIn(0f,1f) else 0f;LinearProgressIndicator(progress={progress},modifier=Modifier.fillMaxWidth().padding(vertical=5.dp),color=d.color("blue"));Text("总量 ${usage.total.toPlainString()} ${usage.unit} · 剩余 ${usage.remaining?.toPlainString()?:"—"} ${usage.unit}",fontSize=10.sp,color=Color(0xff8b96ac))} else Text("仅展示已用量，不计算剩余比例",fontSize=10.sp,color=Color(0xff8b96ac))
        Spacer(Modifier.height(6.dp))
    }}
    Text(if(vm.demo)"演示快照" else "学校校园网 · 获取于 ${fetchedTime(snapshot.fetchedAt)}",fontSize=11.sp,color=Color(0xff9ca7bf))
}
@Composable private fun Trends(d:DesignConfig,vm:UsageViewModel) {
    Title("A LITTLE MORE PERSPECTIVE","看见日常的节奏。")
    CategoryTabs(vm,false)
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {listOf(7,30).forEach { days -> FilterChip(selected=vm.days==days,onClick={vm.selectDays(days)},label={Text("近 $days 日")})}}
    Glass(d) {PanelHeading("${vm.category.label}用量趋势",Icons.Default.BarChart,d);UsageChart(d,vm,vm.category,vm.days)}
    Glass(d) {PanelHeading("每日记录",Icons.Default.CalendarToday,d);repeat(vm.days) { offset -> val date=vm.latestDate.minusDays(offset.toLong());val reading=vm.reading(vm.category,date)
        Row(Modifier.fillMaxWidth().clickable{vm.selectDate(date);vm.go("query")}.heightIn(min=58.dp).padding(vertical=9.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {Column {Text(date.toString(),fontSize=12.sp);Text(if(reading==null)"暂未取得统计" else if(vm.demo)"演示日记录" else "学校日记录",fontSize=10.sp,color=Color(0xff8897b2))};Text("${reading?.total?.toPlainString()?:"—"} kWh",fontSize=13.sp,color=d.color("pink"))}
        HorizontalDivider(color=Color.White.copy(alpha=.05f))
    }}
}
@Composable private fun Profile(d:DesignConfig,vm:UsageViewModel) {
    Column(Modifier.fillMaxWidth().padding(vertical=20.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(65.dp).background(d.color("purple").copy(alpha=.2f),RoundedCornerShape(22.dp)).border(.6.dp,d.color("pink").copy(alpha=.5f),RoundedCornerShape(22.dp)),contentAlignment=Alignment.Center) {Icon(Icons.Default.PersonOutline,null,Modifier.size(33.dp),tint=d.color("pink"))}
        Text("你好，校园同学",fontFamily=FontFamily.Serif,fontSize=23.sp);Text("两处连接，一个清楚的日常。",fontSize=12.sp,color=Color(0xff939db3));Text((if(vm.demo)"独立演示环境" else "学校真实数据")+" · 设计 v${d.revision}",fontSize=11.sp,color=d.color("pink"))
    }
    Glass(d) {PanelHeading("学校系统连接",Icons.Default.Settings,d);listOf("waxy" to "水电能源","network" to "校园网").forEach { (system,name) ->
        val connected=if(system=="waxy")vm.waxyConnected else vm.networkConnected
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {Icon(if(system=="network")Icons.Default.Wifi else Icons.Default.BlurOn,null,tint=d.color("blue"),modifier=Modifier.padding(end=12.dp));Column(Modifier.weight(1f)) {Text(name,fontSize=14.sp);Text(if(connected)"已保存登录，可直接更新" else "尚未连接",fontSize=11.sp,color=Color(0xff8d9bb5))};TextButton(enabled=!vm.demo,onClick={vm.requestLogin(system)}) {Text(if(connected)"重新连接" else "连接",fontSize=11.sp)}}
        if(connected&&!vm.demo)TextButton(onClick={vm.disconnect(system)}){Text("清除${name}本机登录与缓存",fontSize=10.sp)}
    }}
    if(BuildConfig.DEBUG) Glass(d) {Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {Column(Modifier.weight(1f)){Text("界面演示模式",fontSize=14.sp);Text("开启后使用隔离样例，停止学校查询",fontSize=10.sp,color=Color(0xff939db3))};Switch(checked=vm.demo,onCheckedChange=vm::setDemoMode)}}
    if(BuildConfig.DEBUG&&vm.demo) Glass(d) {PanelHeading("预览测试场景",Icons.Default.Tune,d);Scenario.entries.forEach { scenario -> Row(Modifier.fillMaxWidth().clickable{vm.scenario=scenario}.heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) {RadioButton(selected=vm.scenario==scenario,onClick={vm.scenario=scenario});Text(scenario.label,fontSize=12.sp)}};Text("场景设置仅用于隔离演示，不代表真实登录状态。",fontSize=10.sp,color=Color(0xff939db3))}
    Text("登录凭据由本机密钥保护，学校数据按账户分别保留。设计可从电脑工作台保存并应用。",fontSize=11.sp,color=Color(0xff939db3))
}
