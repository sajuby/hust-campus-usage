package dev.campususage.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.campususage.app.MainUtilitiesActivity
import dev.campususage.app.core.design.DesignConfig
import dev.campususage.app.core.model.Campus

@Composable fun CampusPicker(d:DesignConfig,vm:UsageViewModel) {
    var expanded by remember{mutableStateOf(false)}
    Box{OutlinedButton(onClick={expanded=true},shape=RoundedCornerShape(24.dp),contentPadding=PaddingValues(horizontal=12.dp,vertical=4.dp)) {Text(vm.campus.label+" ﹀",color=d.color("blue"),fontSize=12.sp)}
        DropdownMenu(expanded,onDismissRequest={expanded=false}){Campus.entries.forEach{campus->DropdownMenuItem(text={Text(campus.label+if(campus==vm.campus)" ✓" else "")},onClick={expanded=false;vm.selectCampus(campus)})}}
    }
}
@Composable fun MainCampusEntry(d:DesignConfig,modifier:Modifier=Modifier) {
    val context=LocalContext.current
    Card(modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=d.color("surface")),shape=RoundedCornerShape(20.dp)){
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Text("主校区 · 水电查询",fontSize=20.sp,color=d.color("text"))
            Text("使用企业微信“水电费充值”对应的独立官方平台。房间信息、昨日用量、计费及历史用量以官方页面为准。",fontSize=12.sp,color=d.color("blue"))
            Text("主校区水电与网安基地记录分开显示。尚未绑定房间时，请在官方页面自行选择本人房间。",fontSize=11.sp,color=d.color("text"))
            Button(onClick={context.startActivity(Intent(context,MainUtilitiesActivity::class.java))}){Text("打开主校区水电查询")}
            OutlinedButton(onClick={context.startActivity(Intent(context,MainUtilitiesActivity::class.java).putExtra("history",true))}){Text("查看主校区历史用量")}
        }
    }
}
