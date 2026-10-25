package dev.campususage.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.campususage.app.BuildConfig
import dev.campususage.app.R
import dev.campususage.app.core.design.DesignConfig
import dev.campususage.app.core.model.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.*

private val quiet=Color(0xffa4b0c6)
private fun number(value:BigDecimal?)=value?.setScale(2,RoundingMode.HALF_UP)?.toPlainString()?:"—"
private fun range(vm:UsageViewModel,count:Int,end:LocalDate=vm.latestDate)=
    (count-1 downTo 0).map{end.minusDays(it.toLong())}.map{it to vm.reading(vm.category.takeUnless{c->c==Category.NETWORK}?:Category.ELECTRICITY,it)}

@Composable internal fun CharacterExperience(d:DesignConfig,vm:UsageViewModel) {
    Box(Modifier.fillMaxSize().background(d.color("background"))) {
    CustomBackground(Modifier.fillMaxSize())
    Scaffold(containerColor=Color.Transparent,contentColor=d.color("text"),bottomBar={CampusBottomNavigation(d,vm.page){vm.go(it)}}) { insets ->
        key(vm.page) {
            Column(Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(9.dp)) {
                when(vm.page){"home"->CharacterHome(d,vm);"trends"->CharacterTrends(d,vm);else->CharacterProfile(d,vm)}
                Spacer(Modifier.height(12.dp))
            }
        }
    }
    }
}
@Composable private fun StarTitle(title:String,d:DesignConfig,modifier:Modifier=Modifier) {
    Text(title+" ✦",modifier,fontFamily=FontFamily.Serif,fontWeight=FontWeight.SemiBold,fontSize=23.sp,color=d.color("text"))
}
@Composable private fun Panel(d:DesignConfig,modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {
    val shape=RoundedCornerShape(d.size("radius").dp)
    Column(modifier.fillMaxWidth().clip(shape).background(Brush.linearGradient(listOf(d.color("surface"),Color(0xff101725)))).border(.65.dp,Color(0xff8293af).copy(alpha=.5f),shape).padding(13.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content)
}
@Composable private fun Subheading(title:String,d:DesignConfig,action:(@Composable ()->Unit)?=null){
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("✦",color=d.color("pink"),modifier=Modifier.padding(end=7.dp));Text(title,Modifier.weight(1f),fontSize=d.size("bodySize").sp,fontWeight=FontWeight.Medium);action?.invoke()}
}
@Composable private fun CharacterHome(d:DesignConfig,vm:UsageViewModel) {
    d.order("sections").forEach { section -> when(section) {
        "hero" -> Box(Modifier.fillMaxWidth().height((d.size("heroHeight")+120).dp)) {
            LocalArtwork(ArtSlot.HOME,null,Modifier.fillMaxSize())
            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically){StarTitle(d.copy("appTitle"),d,Modifier.weight(1f));CampusPicker(d,vm)}
            Column(Modifier.align(Alignment.CenterStart).padding(start=18.dp,top=30.dp).width(150.dp)) {
                Text(d.copy("greeting").replaceFirst("，","，\n"),fontFamily=FontFamily.Serif,fontSize=29.sp,lineHeight=35.sp,color=d.color("text"))
                Text(d.copy("subtitle"),fontSize=12.sp,color=d.color("text"),modifier=Modifier.padding(top=8.dp).width(132.dp))
            }
        }
        "summary" -> CharacterSummary(d,vm)
        "quick" -> Column(Modifier.padding(horizontal=d.size("pagePadding").dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Subheading(d.copy("quickTitle"),d)
            Row(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                listOf(Triple("电费",Icons.Outlined.Bolt,Category.ELECTRICITY),Triple(vm.categoryLabel(Category.ENERGY),Icons.Outlined.WaterDrop,Category.ENERGY),Triple("校园网",Icons.Outlined.Wifi,Category.NETWORK),Triple("查路线",Icons.Outlined.Train,null)).forEachIndexed { i,(label,icon,cat) ->
                    val accent=d.color(if(i%2==0)"pink" else "blue");val shape=RoundedCornerShape(15.dp)
                    Column(Modifier.weight(1f).clip(shape).background(d.color("surface")).border(.7.dp,accent.copy(alpha=.6f),shape).clickable{if(cat==null){vm.selectRoutes();vm.go("query")}else vm.go("query",cat)}.padding(vertical=12.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)){
                        Icon(icon,null,Modifier.size(27.dp),tint=accent);Text(label,fontSize=12.sp,color=d.color("text"))
                    }
                }
            }
        }
        "trend" -> Panel(d,Modifier.padding(horizontal=d.size("pagePadding").dp)) {
            Subheading(d.copy("trendTitle"),d){IconButton(onClick={vm.go("trends")},modifier=Modifier.size(30.dp)){Icon(Icons.Outlined.ChevronRight,"打开趋势",tint=quiet)}}
            if(vm.campus==Campus.MAIN)Text("主校区历史用量请在独立官方页查看",color=quiet,fontSize=12.sp)else CharacterChart(d,vm,Category.ELECTRICITY,7,compact=true)
        }
        "notice" -> Text("ⓘ "+(if(vm.campus==Campus.MAIN)"主校区数据以官方查询页为准" else d.copy("noticeBody"))+if(vm.demo)" · 演示数据" else " · 学校数据",Modifier.padding(horizontal=d.size("pagePadding").dp),color=quiet,fontSize=10.sp)
    }}
}
@Composable private fun CharacterSummary(d:DesignConfig,vm:UsageViewModel) {
    if(vm.campus==Campus.MAIN){MainCampusEntry(d,Modifier.padding(horizontal=d.size("pagePadding").dp));return}
    val electricity=vm.reading(Category.ELECTRICITY,vm.latestDate)
    Panel(d,Modifier.padding(horizontal=d.size("pagePadding").dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable{vm.go("query",Category.ELECTRICITY)}) {
                Text("✦ "+d.copy("electricityLabel")+"  "+vm.latestDate.format(DateTimeFormatter.ofPattern("MM月dd日")),color=quiet,fontSize=11.sp)
                Row(verticalAlignment=Alignment.Bottom){Text(number(electricity?.total),fontSize=(d.size("valueSize")+15).sp,fontFamily=FontFamily.Serif,color=d.color("text"));Text(" kWh",fontSize=13.sp,color=d.color("text"),modifier=Modifier.padding(bottom=8.dp))}
            }
            IconButton(enabled=!vm.updating,onClick={vm.category=Category.ELECTRICITY;vm.refresh()}) {if(vm.updating)CircularProgressIndicator(Modifier.size(22.dp),strokeWidth=2.dp)else Icon(Icons.Outlined.Refresh,"更新首页用电",tint=d.color("blue"))}
        }
        HorizontalDivider(color=quiet.copy(alpha=.2f))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            d.order("cards").filter{it=="energy"||it=="balance"}.forEach { id ->
                val energy=id=="energy"
                Row(Modifier.weight(1f).clickable{vm.go("query",if(energy)Category.ENERGY else Category.NETWORK)},verticalAlignment=Alignment.CenterVertically) {
                    Icon(if(energy)Icons.Outlined.WaterDrop else Icons.Outlined.AccountBalanceWallet,null,Modifier.size(23.dp),tint=d.color(if(energy)"blue" else "pink"))
                    Column(Modifier.padding(start=8.dp)){Text(d.copy(if(energy)"energyLabel" else "balanceLabel"),fontSize=10.sp,color=quiet);Text((if(energy)number(vm.reading(Category.ENERGY,vm.latestDate)?.total) else "¥"+number(vm.network()?.balance))+(if(energy)" kWh" else ""),fontSize=18.sp,color=d.color("text"),fontFamily=FontFamily.Serif)}
                }
            }
        }
        val month=(1..vm.now.dayOfMonth).mapNotNull{vm.reading(Category.ELECTRICITY,vm.now.toLocalDate().withDayOfMonth(it))}
        Text(d.copy("monthLabel")+" "+(if(month.isEmpty())"—" else number(month.fold(BigDecimal.ZERO){sum,r->sum+r.total}))+" kWh · ${month.size}/${vm.now.dayOfMonth} 日",color=quiet,fontSize=9.sp,modifier=Modifier.clickable{vm.go("trends",Category.ELECTRICITY)})
        if(vm.message.contains("失败")||vm.message.contains("保留"))Text(vm.message,color=d.color("pink"),fontSize=10.sp)
    }
}
@Composable private fun CharacterTrends(d:DesignConfig,vm:UsageViewModel) {
    Column(Modifier.padding(horizontal=16.dp,vertical=8.dp)){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){StarTitle("用量趋势",d,Modifier.weight(1f));CampusPicker(d,vm)};Text("从数据中，看到更好的自己。",fontSize=10.sp,color=quiet)}
    // Fit the complete original JPG. No crop, tint, opacity or artwork overlay.
    LocalArtwork(ArtSlot.TRENDS,"水面人物原图，完整画幅",Modifier.fillMaxWidth(),complete=true)
    if(vm.campus==Campus.MAIN){MainCampusEntry(d,Modifier.padding(horizontal=14.dp));return}
    Row(Modifier.padding(horizontal=14.dp).fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        listOf(Category.ELECTRICITY,Category.ENERGY).forEach{cat->FilterChip(selected=vm.category==cat,onClick={vm.selectCategory(cat)},label={Text(if(cat==Category.ELECTRICITY)"电量" else "能源",fontSize=12.sp)},shape=RoundedCornerShape(24.dp),colors=FilterChipDefaults.filterChipColors(selectedContainerColor=d.color("pink").copy(alpha=.13f),selectedLabelColor=d.color("text"),labelColor=quiet),border=BorderStroke(.7.dp,d.color(if(vm.category==cat)"pink" else "blue").copy(alpha=.6f)))}
        Spacer(Modifier.weight(1f));var expanded by remember{mutableStateOf(false)}
        Box{TextButton(onClick={expanded=true}){Text("近${vm.days}日 ﹀",color=d.color("text"),fontSize=12.sp)};DropdownMenu(expanded,onDismissRequest={expanded=false}){listOf(7,30).forEach{n->DropdownMenuItem(text={Text("近${n}日")},onClick={expanded=false;vm.selectDays(n)})}}}
        IconButton(onClick=vm::refresh,enabled=!vm.updating,modifier=Modifier.size(38.dp)){if(vm.updating)CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp)else Icon(Icons.Outlined.Refresh,"更新趋势",tint=d.color("blue"))}
    }
    val records=range(vm,vm.days);val available=records.mapNotNull{it.second}
    Panel(d,Modifier.padding(horizontal=14.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
            Column{Text("✦ 近${vm.days}日"+(if(available.size==vm.days)"用量" else "已取得合计"),fontSize=11.sp,color=quiet);Row(verticalAlignment=Alignment.Bottom){Text(if(available.isEmpty())"—" else number(available.fold(BigDecimal.ZERO){s,r->s+r.total}),fontSize=35.sp,fontFamily=FontFamily.Serif,color=d.color("text"));Text(" kWh",fontSize=12.sp,color=d.color("text"),modifier=Modifier.padding(bottom=6.dp))}}
            Column(horizontalAlignment=Alignment.End){Text("最近一日",color=quiet,fontSize=10.sp);Text(number(records.last().second?.total)+" kWh",fontSize=17.sp,color=d.color("text"))}
        }
        CharacterChart(d,vm,vm.category,vm.days)
        Text("截至 ${vm.latestDate} · 已取得 ${available.size}/${vm.days} 日"+if(vm.demo)" · 演示" else " · 学校数据",color=quiet,fontSize=9.sp)
    }
    Panel(d,Modifier.padding(horizontal=14.dp)) {
        Subheading("用量对比",d)
        val before=range(vm,vm.days,vm.latestDate.minusDays(vm.days.toLong())).mapNotNull{it.second}
        val sums=listOf(available,before).map{rows->rows.takeIf{it.isNotEmpty()}?.fold(BigDecimal.ZERO){s,r->s+r.total}}
        val maximum=sums.mapNotNull{it?.toFloat()}.maxOrNull()?.coerceAtLeast(1f)?:1f
        listOf(available,before).forEachIndexed { i,rows ->
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text((if(i==0)"近" else "前")+"${vm.days}日",color=quiet,fontSize=11.sp)
                LinearProgressIndicator(progress={sums[i]?.toFloat()?.div(maximum)?:0f},modifier=Modifier.weight(1f).height(7.dp),color=d.color(if(i==0)"blue" else "pink"),trackColor=Color(0xff303b51))
                Text(number(sums[i])+" kWh",color=d.color("text"),fontSize=11.sp)
            }
            if(rows.size!=vm.days)Text("已取得 ${rows.size}/${vm.days} 日，缺失不补零",fontSize=9.sp,color=quiet)
        }
        Text("ⓘ 以已发布数据为准；日期覆盖不全时不计算增减比例。",fontSize=9.sp,color=quiet)
    }
    Text(vm.message,Modifier.padding(horizontal=16.dp),fontSize=10.sp,color=quiet)
    var showRecords by remember {mutableStateOf(false)}
    TextButton(onClick={showRecords=!showRecords},modifier=Modifier.padding(horizontal=8.dp)){Text(if(showRecords)"收起每日记录" else "查看每日记录",color=d.color("blue"))}
    if(showRecords)Panel(d,Modifier.padding(horizontal=14.dp)){records.reversed().forEach{(date,r)->Row(Modifier.fillMaxWidth().clickable{vm.selectDate(date);vm.go("query")}.padding(vertical=10.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(date.toString(),color=quiet,fontSize=12.sp);Text(number(r?.total)+" kWh",color=d.color("pink"),fontSize=13.sp)}}}
}
@Composable private fun CharacterChart(d:DesignConfig,vm:UsageViewModel,category:Category,count:Int,compact:Boolean=false) {
    val records=(count-1 downTo 0).map{vm.latestDate.minusDays(it.toLong())}.map{it to vm.reading(category,it)}
    Canvas(Modifier.fillMaxWidth().height(if(compact)92.dp else 155.dp).semantics{contentDescription="${count}日${category.label}用量图；缺失保留为空，零为有效值"}.pointerInput(records){detectTapGestures{point->val step=(size.width-35.dp.toPx())/(count-1).coerceAtLeast(1);val index=((point.x-28.dp.toPx())/step).roundToInt().coerceIn(0,count-1);vm.selectDate(records[index].first);vm.go("query",category)}}) {
        val left=28.dp.toPx();val bottom=size.height-19.dp.toPx();val top=14.dp.toPx();val step=(size.width-left-8.dp.toPx())/(count-1).coerceAtLeast(1)
        val maximum=ceil((records.maxOfOrNull{it.second?.total?.toFloat()?:0f}?:0f).toDouble()).toFloat().coerceAtLeast(1f)
        val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply{color=android.graphics.Color.rgb(164,176,198);textSize=9.sp.toPx()}
        for(n in 0..3){val y=bottom-(bottom-top)*n/3;drawLine(Color.White.copy(alpha=.08f),Offset(left,y),Offset(size.width,y));drawContext.canvas.nativeCanvas.drawText(String.format(java.util.Locale.ROOT,"%.1f",maximum*n/3),0f,y+3.dp.toPx(),paint)}
        var previous:Offset?=null
        records.forEachIndexed{i,(date,r)->val x=left+i*step
            if(r!=null){val y=bottom-(r.total.toFloat()/maximum)*(bottom-top);val point=Offset(x,y)
                if(compact)drawRoundRect(d.color(if(i==count-1)"pink" else "blue"),Offset(x-5.dp.toPx(),y.coerceAtMost(bottom-2.dp.toPx())),Size(10.dp.toPx(),max(2.dp.toPx(),bottom-y)))
                else{previous?.let{drawLine(d.color("blue"),it,point,2.dp.toPx())};drawCircle(d.color(if(i==count-1)"pink" else "blue"),3.dp.toPx(),point)};previous=point
            }else previous=null
            if(i%ceil(count/7f).toInt()==0||i==count-1)drawContext.canvas.nativeCanvas.drawText(date.format(DateTimeFormatter.ofPattern("M/d")),x-9.dp.toPx(),bottom+15.dp.toPx(),paint)
        }
    }
}
@Composable private fun CharacterProfile(d:DesignConfig,vm:UsageViewModel) {
    var dialog by remember{mutableStateOf<String?>(null)}
    val connected=(vm.campus==Campus.CYBER&&vm.waxyConnected)||vm.networkConnected
    Box(Modifier.fillMaxWidth().height(345.dp)) {
        LocalArtwork(ArtSlot.PROFILE,null,Modifier.fillMaxSize(),alignment=Alignment.CenterEnd)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,Color.Transparent,d.color("background")))))
        Column(Modifier.align(Alignment.BottomStart).padding(18.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){Text("同学",color=d.color("text"),fontSize=29.sp,fontFamily=FontFamily.Serif);Text("我的校园",color=d.color("text"),fontSize=13.sp);Text(if(vm.demo)"界面演示 · 非真实连接" else if(vm.campus==Campus.MAIN)"主校区水电使用独立官方页面" else if(vm.waxyConnected&&vm.networkConnected)"✓ 学校账户已连接" else if(connected)"学校账户部分连接" else "尚未连接学校账户",fontSize=11.sp,color=if(connected&&!vm.demo)Color(0xff8fe0c5)else d.color("pink"))}
    }
    Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically){Text("水电查询校区",Modifier.weight(1f),color=quiet,fontSize=12.sp);CampusPicker(d,vm)}
    Panel(d,Modifier.padding(horizontal=15.dp)) {
        listOf(Triple("账号与连接",Icons.Outlined.PersonOutline,"accounts"),Triple("主题与外观",Icons.Outlined.Palette,"theme"),Triple("更新设置",Icons.Outlined.Settings,"updates"),Triple("关于应用",Icons.Outlined.Info,"about")).forEachIndexed { i,(label,icon,id)->
            Row(Modifier.fillMaxWidth().heightIn(min=55.dp).clickable{dialog=id},verticalAlignment=Alignment.CenterVertically){Icon(icon,null,Modifier.size(25.dp),tint=d.color(if(i%2==0)"blue" else "pink"));Text(label,Modifier.padding(start=15.dp).weight(1f),fontSize=d.size("bodySize").sp,color=d.color("text"));if(id=="accounts")Text(if(vm.demo)"演示" else if(connected)"已连接" else "待连接",fontSize=10.sp,color=quiet);Icon(Icons.Outlined.ChevronRight,null,tint=quiet,modifier=Modifier.size(20.dp))}
            if(i<3)HorizontalDivider(color=quiet.copy(alpha=.18f))
        }
    }
    Panel(d,Modifier.padding(horizontal=15.dp)){Row(Modifier.fillMaxWidth().clickable{dialog="accounts"}.heightIn(min=44.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Outlined.Lock,null,tint=d.color("pink"));Column(Modifier.padding(start=14.dp).weight(1f)){Text("保持登录",color=d.color("text"),fontSize=14.sp);Text("在各系统连接页管理",color=quiet,fontSize=10.sp)};Text(if(connected&&!vm.demo)"会话已保存" else "连接时设置",color=d.color("blue"),fontSize=11.sp)}}
    Text("设计 v${d.revision} · "+if(vm.demo)"隔离演示环境" else "学校真实数据",Modifier.padding(horizontal=18.dp),fontSize=10.sp,color=quiet)
    if(BuildConfig.DEBUG)Panel(d,Modifier.padding(horizontal=15.dp)){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("界面演示模式",Modifier.weight(1f),color=d.color("text"),fontSize=14.sp);Switch(checked=vm.demo,onCheckedChange=vm::setDemoMode)};if(vm.demo)Scenario.entries.forEach{scenario->Row(Modifier.fillMaxWidth().clickable{vm.scenario=scenario}.heightIn(min=42.dp),verticalAlignment=Alignment.CenterVertically){RadioButton(selected=vm.scenario==scenario,onClick={vm.scenario=scenario});Text(scenario.label,color=quiet,fontSize=12.sp)}}}
    if(dialog=="theme")AppearanceEditor(d,vm){dialog=null}
    if(dialog!=null&&dialog!="theme")AlertDialog(onDismissRequest={dialog=null},title={Text(when(dialog){"accounts"->"账号与连接";"theme"->"主题与外观";"updates"->"更新设置";else->"关于应用"})},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
        when(dialog){
            "accounts"->Column{
                if(vm.campus==Campus.MAIN)MainCampusEntry(d)
                (if(vm.campus==Campus.MAIN)listOf("network" to "校园网") else listOf("waxy" to "水电能源","network" to "校园网")).forEach{(system,name)->val ready=if(system=="waxy")vm.waxyConnected else vm.networkConnected
                Text(name+" · "+if(vm.demo)"演示，不代表真实连接" else if(ready)"已保存登录" else "尚未连接")
                TextButton(enabled=!vm.demo,onClick={dialog=null;vm.requestLogin(system)}){Text(if(ready)"重新连接$name" else "连接$name")}
                if(ready&&!vm.demo)TextButton(onClick={vm.disconnect(system)}){Text("清除${name}本机登录与缓存")}
            }
            }
            "theme"->Text("黑色人物主题。配色、文案、首页顺序和字号可在电脑设计工作台修改后应用。当前设计 v${d.revision}。")
            "updates"->Text("点击用量页面的更新主动请求学校数据。每日用量一般于次日 8 点后可查。断网保留已有记录；会话失效按连接时的授权恢复，必要时进入登录页。")
            else->Text("校园用量助手 ${BuildConfig.VERSION_NAME}\n校园用量、校园网及城市通查路线。人物美术与数据分开渲染，趋势人物使用原图。")
        }
    }},confirmButton={TextButton(onClick={dialog=null}){Text("关闭")}})
}
