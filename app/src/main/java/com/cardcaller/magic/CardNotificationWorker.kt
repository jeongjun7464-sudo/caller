package com.cardcaller.magic

import android.Manifest
import android.app.*
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import java.util.concurrent.TimeUnit

class CardNotificationWorker(context:Context,params:WorkerParameters):Worker(context,params){
    override fun doWork():Result{val card=PlayingCard.fromId(inputData.getString("cardId"))?:return Result.failure();val channel="card_prophecy";if(android.os.Build.VERSION.SDK_INT>=26)(applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)as NotificationManager).createNotificationChannel(NotificationChannel(channel,"카드 예언",NotificationManager.IMPORTANCE_HIGH));val title=inputData.getString("title")?:"카드 예언";val content=MagicLabLogic.notificationContent(inputData.getString("content")?:"당신이 선택한 카드는 %CARD%입니다",card);if(android.os.Build.VERSION.SDK_INT<33||applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)NotificationManagerCompat.from(applicationContext).notify(card.id.hashCode(),NotificationCompat.Builder(applicationContext,channel).setSmallIcon(R.drawable.ic_launcher).setContentTitle(title).setContentText(content).setStyle(NotificationCompat.BigTextStyle().bigText(content)).setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).build());return Result.success()}
    companion object{fun schedule(context:Context,card:PlayingCard,title:String,content:String,delaySeconds:Int){val work=OneTimeWorkRequestBuilder<CardNotificationWorker>().setInitialDelay(delaySeconds.toLong(),TimeUnit.SECONDS).setInputData(workDataOf("cardId" to card.id,"title" to title,"content" to content)).build();WorkManager.getInstance(context).enqueue(work)}}
}
