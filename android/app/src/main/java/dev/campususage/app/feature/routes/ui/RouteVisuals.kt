package dev.campususage.app.feature.routes.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.campususage.app.R
import dev.campususage.app.core.design.DesignConfig
import dev.campususage.app.feature.routes.model.QuerySection

internal val routeMuted = Color(0xffa8b4cd)
internal fun DesignConfig.routeText(key:String,default:String)=json.getJSONObject("copy").optString(key,default)

@Composable internal fun RouteBackdrop(d:DesignConfig,modifier:Modifier=Modifier) {
    Box(modifier.background(d.color("background"))) {
        if(dev.campususage.app.ui.LocalAppearance.current.images.containsKey(dev.campususage.app.ui.ArtSlot.BACKGROUND))dev.campususage.app.ui.CustomBackground(Modifier.fillMaxSize())
        else if(d.glow)Image(painterResource(R.drawable.route_background),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
    }
}

@Composable internal fun RouteHeader(d:DesignConfig,city:String,subtitle:Boolean,onCity:()->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=56.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Box {
                if(d.glow)Canvas(Modifier.size(125.dp,42.dp)) {
                    rotate(-19f,Offset(size.width*.43f,size.height*.6f)) {
                        drawOval(Brush.horizontalGradient(listOf(d.color("blue"),Color.Transparent,d.color("pink"))),Offset(-3.dp.toPx(),size.height*.37f),Size(size.width*.98f,size.height*.5f),style=Stroke(1.dp.toPx()))
                    }
                    val center=Offset(size.width*.9f,size.height*.2f)
                    drawLine(d.color("pink"),center-Offset(0f,9.dp.toPx()),center+Offset(0f,9.dp.toPx()),1.dp.toPx())
                    drawLine(Color.White,center-Offset(7.dp.toPx(),0f),center+Offset(7.dp.toPx(),0f),.6.dp.toPx())
                }
                Text("查询",fontSize=34.sp,fontWeight=FontWeight.Bold,color=Color(0xfffcf3fb),modifier=Modifier.padding(start=6.dp,top=2.dp))
            }
            if(subtitle)Text(d.copy("subtitle"),fontSize=10.sp,color=Color(0xffbcc4d7),modifier=Modifier.padding(start=8.dp,top=1.dp),maxLines=1)
        }
        Row(Modifier.heightIn(min=48.dp).clip(RoundedCornerShape(24.dp)).clickable(onClick=onCity)
            .padding(vertical=5.dp).border(.7.dp,Color(0xff8399bb),RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(Color(0xff1a2233),Color(0xff111724))),RoundedCornerShape(22.dp)).padding(horizontal=13.dp,vertical=9.dp),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.LocationOn,null,tint=d.color("blue"),modifier=Modifier.size(18.dp))
            Text(city,fontSize=13.sp,color=d.color("text"));Icon(Icons.Default.ExpandMore,null,Modifier.size(17.dp),tint=Color(0xffd6dcec))
        }
    }
}

@Composable internal fun RouteCategoryTabs(d:DesignConfig,onSelect:(QuerySection)->Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width=((maxWidth-21.dp)/4).coerceAtLeast(76.dp)
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            QuerySection.entries.forEach { section ->
                val selected=section==QuerySection.ROUTES;val shape=RoundedCornerShape(17.dp)
                val icon=when(section){QuerySection.ELECTRICITY->Icons.Default.Bolt;QuerySection.ENERGY->Icons.Outlined.WaterDrop;QuerySection.NETWORK->Icons.Outlined.Wifi;else->Icons.Outlined.Train}
                Row(Modifier.width(width).heightIn(min=48.dp).shadow(if(selected&&d.glow)5.dp else 0.dp,shape,ambientColor=d.color("pink"),spotColor=d.color("blue"))
                    .clip(shape).background(Brush.linearGradient(if(selected)listOf(d.color("pink"),Color(0xffe6ddff),d.color("blue"))else listOf(Color(0xff232d3e),Color(0xff141a27))))
                    .border(if(selected)1.dp else .6.dp,if(selected)Color(0xffeaf6ff)else Color(0xff71839d),shape).clickable{onSelect(section)}.padding(horizontal=7.dp,vertical=8.dp),
                    verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center) {
                    Icon(icon,null,Modifier.size(if(section==QuerySection.NETWORK)21.dp else 23.dp),tint=if(selected)Color(0xffc6589a)else if(section==QuerySection.ENERGY||section==QuerySection.NETWORK)d.color("blue")else Color(0xffe7e6fa))
                    Text(section.label,fontSize=12.sp,fontWeight=if(selected)FontWeight.Bold else FontWeight.Medium,color=if(selected)d.color("ink")else d.color("text"),modifier=Modifier.padding(start=4.dp),maxLines=1)
                }
            }
        }
    }
}

@Composable internal fun RouteSurface(d:DesignConfig,light:Boolean=false,modifier:Modifier=Modifier,padding:Int=16,rose:Boolean=false,content:@Composable BoxScope.()->Unit) {
    val shape=RoundedCornerShape(d.size("radius").dp)
    Box(modifier.fillMaxWidth().shadow(if(light&&d.glow)7.dp else 0.dp,shape,ambientColor=d.color("pink").copy(alpha=.4f),spotColor=d.color("blue").copy(alpha=.3f))
        .clip(shape).background(Brush.linearGradient(if(light)if(rose)listOf(Color(0xffffedf8),Color(0xfff6e6fb),Color(0xffffdef5))else listOf(Color(0xffffedf8),d.color("card"),Color(0xffe3f4ff))else listOf(Color(0xff212a3b).copy(alpha=.94f),d.color("surface").copy(alpha=.94f),Color(0xff29243c).copy(alpha=.9f))))
        .border(if(light).8.dp else .6.dp,if(light)Color.White.copy(alpha=.9f)else Color(0xffa0b2d0).copy(alpha=.65f),shape).padding(padding.dp),content=content)
}

@Composable internal fun RouteHero(d:DesignConfig,city:String) {
    Box(Modifier.fillMaxWidth().height(82.dp).clip(RoundedCornerShape(18.dp)).border(.9.dp,Color.White.copy(alpha=.9f),RoundedCornerShape(18.dp))) {
        Image(painterResource(R.drawable.route_hero),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        Column(Modifier.align(Alignment.CenterStart).padding(start=16.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Text("从校园出发\n探索更大的${city.removeSuffix("市")}",fontSize=15.sp,lineHeight=22.sp,fontWeight=FontWeight.SemiBold,color=d.color("ink"))
            Text("便捷出行，让每一次抵达都更简单",fontSize=9.sp,color=Color(0xff4e5b79))
        }
    }
}

@Composable internal fun MetroArtwork(d:DesignConfig,modifier:Modifier=Modifier) {
    Canvas(modifier) {
        fun at(x:Float,y:Float)=Offset(x*size.width,y*size.height)
        val blue=listOf(at(-.04f,.8f),at(.12f,.75f),at(.25f,.56f),at(.48f,.56f),at(.65f,.19f),at(.85f,.19f),at(1.04f,.08f))
        val pink=listOf(at(.07f,.97f),at(.36f,.97f),at(.58f,.6f),at(.82f,.6f),at(.94f,.79f),at(1.04f,.53f))
        val vertical=listOf(at(.68f,.04f),at(.68f,.37f),at(.68f,.98f))
        fun line(points:List<Offset>,color:Color) {
            val path=Path().apply { moveTo(points[0].x,points[0].y);points.drop(1).forEach{lineTo(it.x,it.y)} }
            drawPath(path,color.copy(alpha=.12f),style=Stroke(11.dp.toPx()));drawPath(path,color.copy(alpha=.85f),style=Stroke(2.8.dp.toPx()))
            points.drop(1).dropLast(1).forEach { drawCircle(Color.White.copy(alpha=.12f),6.dp.toPx(),it);drawCircle(color,3.7.dp.toPx(),it) }
        }
        line(blue,d.color("blue"));line(pink,d.color("pink"));line(vertical,Color(0xffd1a5df))
        val accent=at(.68f,.6f);drawCircle(d.color("pink").copy(alpha=.2f),10.dp.toPx(),accent);drawCircle(Color(0xfffff0fb),5.dp.toPx(),accent)
    }
}

@Composable internal fun RouteMapEntry(d:DesignConfig,city:String,compact:Boolean,onClick:()->Unit) {
    RouteSurface(d,modifier=Modifier.height(if(compact)64.dp else 184.dp).clickable(onClick=onClick),padding=0) {
        MetroArtwork(d,if(compact)Modifier.width(114.dp).height(70.dp).align(Alignment.CenterEnd)else Modifier.fillMaxWidth().height(132.dp).align(Alignment.BottomCenter))
        Row(Modifier.fillMaxWidth().align(if(compact)Alignment.Center else Alignment.TopStart).padding(horizontal=15.dp,vertical=if(compact)9.dp else 18.dp),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(if(compact)28.dp else 38.dp).then(if(compact)Modifier else Modifier.background(d.color("pink").copy(alpha=.12f),CircleShape).border(.7.dp,d.color("pink"),CircleShape)),contentAlignment=Alignment.Center) {
                Icon(if(compact)Icons.Outlined.Map else Icons.Outlined.Train,null,tint=if(compact)d.color("text")else d.color("pink"),modifier=Modifier.size(if(compact)23.dp else 25.dp))
            }
            Column(Modifier.weight(1f).padding(start=if(compact)8.dp else 12.dp)) {
                val title=d.routeText("routeMapTitle","地铁线路图").let { if(it=="地铁线路图")city.removeSuffix("市")+it else it }
                Text(title,fontSize=if(compact)14.sp else 19.sp,fontWeight=FontWeight.SemiBold)
                Text(if(city=="武汉市")"官方线网资料 · 可离线查看"else if(compact)"查看全城地铁线路"else"查看线路图  ›",fontSize=if(compact)9.sp else 13.sp,color=routeMuted,modifier=Modifier.padding(top=4.dp))
            }
            if(compact)Text("查看线路图 ›",color=d.color("text"),fontSize=10.sp,modifier=Modifier.background(Color(0xff1b2333).copy(alpha=.9f),RoundedCornerShape(18.dp)).border(.6.dp,Color(0xff8293af),RoundedCornerShape(18.dp)).padding(9.dp))
        }
    }
}

@Composable internal fun RouteFooter(d:DesignConfig,text:String) {
    Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center) {
        Box(Modifier.width(35.dp).height(.5.dp).background(Brush.horizontalGradient(listOf(Color.Transparent,d.color("blue").copy(alpha=.6f)))))
        Text("  ✦  ",color=d.color("pink"),fontSize=15.sp);Text(text,fontSize=10.sp,color=routeMuted)
        Box(Modifier.padding(start=10.dp).width(35.dp).height(.5.dp).background(Brush.horizontalGradient(listOf(d.color("blue").copy(alpha=.6f),Color.Transparent))))
    }
}
