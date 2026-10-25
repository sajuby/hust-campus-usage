package dev.campususage.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.campususage.app.core.design.DesignConfig

@Composable fun CampusBottomNavigation(d:DesignConfig,page:String,onNavigate:(String)->Unit) {
    val shape=RoundedCornerShape(topStart=29.dp,topEnd=29.dp)
    Row(Modifier.fillMaxWidth().clip(shape).background(Brush.verticalGradient(listOf(Color(0xff101520).copy(alpha=.97f),Color(0xff090d16))))
        .border(.65.dp,Color(0xff8d98b2).copy(alpha=.7f),shape).navigationBarsPadding().padding(top=4.dp,bottom=2.dp),horizontalArrangement=Arrangement.SpaceAround) {
        listOf(Triple("home","首页",Icons.Outlined.Home),Triple("query","查询",Icons.Outlined.Search),Triple("trends","趋势",Icons.Outlined.BarChart),Triple("profile","我的",Icons.Outlined.PersonOutline)).forEach { (route,label,icon) ->
            val selected=route==page
            Column(Modifier.weight(1f).heightIn(min=49.dp).clickable{onNavigate(route)}.padding(top=1.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Box(Modifier.size(28.dp),contentAlignment=Alignment.Center) {
                    if(selected&&d.glow)Box(Modifier.size(38.dp).background(Brush.radialGradient(listOf(d.color("pink").copy(alpha=.27f),Color.Transparent))))
                    Icon(icon,label,tint=if(selected)d.color("pink")else Color(0xffdce2f1),modifier=Modifier.size(26.dp))
                }
                Text(label,fontSize=11.sp,lineHeight=14.sp,fontWeight=if(selected)FontWeight.Medium else FontWeight.Normal,color=if(selected)Color(0xfff9e8f4)else Color(0xffdce2f1))
                Box(Modifier.width(16.dp).height(2.dp).background(if(selected)d.color("pink")else Color.Transparent,RoundedCornerShape(2.dp)))
            }
        }
    }
}
