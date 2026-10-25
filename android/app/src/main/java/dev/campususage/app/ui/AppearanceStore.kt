package dev.campususage.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.AtomicFile
import dev.campususage.app.R
import org.json.JSONObject
import java.io.File
import java.util.UUID

enum class ArtSlot(val label:String,val fallback:Int) {
    HOME("首页人物",R.drawable.character_home),TRENDS("趋势配图",R.drawable.character_trends_original),
    PROFILE("我的人物",R.drawable.character_profile),BACKGROUND("背景图片",R.drawable.route_background)
}
data class Appearance(val revision:Long=0,val images:Map<ArtSlot,File> = emptyMap())

/** Copies image bytes into private storage. It never edits or uploads the source. */
class AppearanceStore(private val context:Context) {
    private val directory=File(context.filesDir,"appearance").apply{mkdirs()}
    private val manifest=AtomicFile(File(directory,"images.json"))
    @Synchronized fun load():Appearance=runCatching {
        val data=JSONObject(String(manifest.readFully(),Charsets.UTF_8))
        Appearance(data.optLong("revision"),ArtSlot.entries.mapNotNull{slot->
            val name=data.optString(slot.name)
            if(!Regex("[a-f0-9-]{36}\\.image").matches(name))null else File(directory,name).takeIf{it.isFile}?.let{slot to it}
        }.toMap())
    }.getOrDefault(Appearance())
    private fun save(value:Appearance) {
        val json=JSONObject().put("revision",value.revision)
        value.images.forEach{(slot,file)->json.put(slot.name,file.name)}
        val stream=manifest.startWrite()
        try{stream.write(json.toString().toByteArray());manifest.finishWrite(stream)}catch(e:Exception){manifest.failWrite(stream);throw e}
    }
    @Synchronized fun import(slot:ArtSlot,uri:Uri):Appearance {
        val old=load();val file=File(directory,"${UUID.randomUUID()}.image")
        try{
            context.contentResolver.openInputStream(uri).use{input->requireNotNull(input){"无法读取所选图片"};file.outputStream().use{out->
                val bytes=ByteArray(65536);var total=0L
                while(true){val n=input.read(bytes);if(n<0)break;total+=n;require(total<=32L*1024*1024){"请选择不超过 32 MB 的图片"};out.write(bytes,0,n)}
            }}
            val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(file.path,bounds)
            require(bounds.outWidth>0&&bounds.outHeight>0){"图片格式不受支持，请选择 JPEG、PNG、WebP 等图片"}
            // Validate the actual decoder before replacing a working choice.
            decode(file).recycle()
            val next=Appearance(old.revision+1,old.images+(slot to file));save(next)
            old.images[slot]?.delete();return next
        }catch(e:Exception){file.delete();throw e}
    }
    @Synchronized fun reset(slot:ArtSlot?=null):Appearance {
        val old=load();val next=Appearance(old.revision+1,if(slot==null)emptyMap() else old.images-slot);save(next)
        old.images.filterKeys{slot==null||it==slot}.values.forEach{it.delete()};return next
    }
    companion object {
        fun decode(file:File):Bitmap=ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)){decoder,info,_->
            decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
            val ratio=(2048f/maxOf(info.size.width,info.size.height)).coerceAtMost(1f)
            decoder.setTargetSize((info.size.width*ratio).toInt().coerceAtLeast(1),(info.size.height*ratio).toInt().coerceAtLeast(1))
        }
    }
}
