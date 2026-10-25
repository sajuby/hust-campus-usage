package dev.campususage.app.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.campususage.app.core.design.DesignConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val LocalAppearance=staticCompositionLocalOf{Appearance()}

@Composable fun LocalArtwork(slot:ArtSlot,description:String?,modifier:Modifier=Modifier,contentScale:ContentScale=ContentScale.Crop,alignment:Alignment=Alignment.Center,defaultResource:Int=slot.fallback,complete:Boolean=false) {
    val appearance=LocalAppearance.current;val file=appearance.images[slot]
    val bitmap by produceState<ImageBitmap?>(null,file?.path){value=withContext(Dispatchers.IO){file?.let{runCatching{AppearanceStore.decode(it).asImageBitmap()}.getOrNull()}}}
    val image=bitmap
    val ratio=if(image!=null)image.width.toFloat()/image.height else if(slot==ArtSlot.TRENDS)1256f/708f else 1.5f
    val actual=if(complete)modifier.aspectRatio(ratio)else modifier
    val actualDescription=if(file!=null&&description!=null)slot.label+if(complete)"，完整画幅" else "" else description
    if(image==null)Image(painterResource(defaultResource),actualDescription,actual,alignment=alignment,contentScale=if(complete)ContentScale.Fit else contentScale)
    else Image(image,actualDescription,actual,alignment=alignment,contentScale=if(complete)ContentScale.Fit else contentScale)
}
@Composable fun CustomBackground(modifier:Modifier=Modifier) {
    if(LocalAppearance.current.images.containsKey(ArtSlot.BACKGROUND))Box(modifier){
        LocalArtwork(ArtSlot.BACKGROUND,null,Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha=.62f)))
    }
}
@Composable fun AppearanceEditor(d:DesignConfig,vm:UsageViewModel,onClose:()->Unit) {
    var pendingName by rememberSaveable{mutableStateOf<String?>(null)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        val slot=ArtSlot.entries.firstOrNull{it.name==pendingName};pendingName=null
        if(uri!=null&&slot!=null)vm.importArtwork(slot,uri)
    }
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)){
        Surface(Modifier.fillMaxSize(),color=d.color("background"),contentColor=d.color("text")){
            Column(Modifier.statusBarsPadding().navigationBarsPadding()){
                Row(Modifier.fillMaxWidth().padding(10.dp),verticalAlignment=Alignment.CenterVertically){IconButton(onClick=onClose){Icon(Icons.Outlined.ArrowBack,"返回主题设置")};Text("主题与外观",Modifier.weight(1f),fontSize=22.sp);TextButton(enabled=!vm.artBusy,onClick={vm.resetArtwork()}){Text("全部恢复默认")}}
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
                    Text("从设备本地选择图片，保存在本机应用内。三张配图和背景可分别更换；趋势配图保持完整画幅。",color=d.color("blue"),fontSize=12.sp)
                    if(vm.artMessage.isNotBlank())Text(vm.artMessage,fontSize=12.sp)
                    ArtSlot.entries.forEach{slot->
                        Column(Modifier.fillMaxWidth().background(d.color("surface"),RoundedCornerShape(18.dp)).padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                            Row(verticalAlignment=Alignment.CenterVertically){Text(slot.label,Modifier.weight(1f),fontSize=16.sp);Text(if(vm.appearance.images.containsKey(slot))"本地图片" else "默认图片",fontSize=11.sp,color=d.color("blue"))}
                            LocalArtwork(slot,slot.label+"预览",Modifier.fillMaxWidth().height(125.dp),contentScale=ContentScale.Fit)
                            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                                Button(enabled=!vm.artBusy,onClick={pendingName=slot.name;picker.launch(arrayOf("image/*"))}){Text("选择${slot.label}")}
                                OutlinedButton(enabled=!vm.artBusy&&vm.appearance.images.containsKey(slot),modifier=Modifier.semantics{contentDescription="恢复${slot.label}默认"},onClick={vm.resetArtwork(slot)}){Text("恢复默认")}
                            }
                        }
                    }
                }
            }
        }
    }
}
