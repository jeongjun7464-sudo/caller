package com.cardcaller.magic

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

data class OverlayTransform(val x:Float=.5f,val y:Float=.5f,val scale:Float=.35f,val rotation:Float=0f,val alpha:Float=1f,val shadow:Float=12f)
object PhotoComposer {
    fun cameraUri(context:Context):Uri{val dir=File(context.cacheDir,"magic_photos").apply{mkdirs()};return FileProvider.getUriForFile(context,"${context.packageName}.files",File(dir,"capture_${System.currentTimeMillis()}.jpg"))}
    fun composeAndSave(context:Context,source:Uri,card:PlayingCard,t:OverlayTransform):Uri{val input=context.contentResolver.openInputStream(source)?:error("사진을 열 수 없습니다");val original=BitmapFactory.decodeStream(input).also{input.close()};val max=2048f;val factor=minOf(1f,max/maxOf(original.width,original.height));val bitmap=Bitmap.createScaledBitmap(original,(original.width*factor).toInt(),(original.height*factor).toInt(),true).copy(Bitmap.Config.ARGB_8888,true);val canvas=Canvas(bitmap);val w=bitmap.width*t.scale;val h=w*1.45f;canvas.save();canvas.translate(bitmap.width*t.x,bitmap.height*t.y);canvas.rotate(t.rotation);val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{alpha=(t.alpha*255).toInt();setShadowLayer(t.shadow,0f,t.shadow/3,Color.argb(140,0,0,0))};canvas.drawRoundRect(-w/2,-h/2,w/2,h/2,w*.06f,w*.06f,paint.apply{color=Color.WHITE});paint.clearShadowLayer();paint.color=if(card.suit in listOf(Suit.HEARTS,Suit.DIAMONDS))Color.RED else Color.BLACK;paint.textAlign=Paint.Align.CENTER;paint.textSize=w*.28f;paint.typeface=Typeface.DEFAULT_BOLD;canvas.drawText("${card.suit.symbol} ${card.rank.label}",0f,paint.textSize*.25f,paint);canvas.restore();val values=ContentValues().apply{put(MediaStore.Images.Media.DISPLAY_NAME,"CardCaller_${System.currentTimeMillis()}.jpg");put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");if(Build.VERSION.SDK_INT>=29)put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/CardCaller")};val uri=context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)?:error("저장 위치를 만들 수 없습니다");context.contentResolver.openOutputStream(uri)!!.use{bitmap.compress(Bitmap.CompressFormat.JPEG,94,it)};return uri}
}
