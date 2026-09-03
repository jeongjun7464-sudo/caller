package com.cardcaller.magic

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/** Receives data-only fallback notifications. RTDB remains the primary live channel. */
class CardMessagingService:FirebaseMessagingService(){
    override fun onMessageReceived(message:RemoteMessage){
        val messageId=message.data["messageId"]?:return
        val cardId=message.data["cardId"]?:return
        if(PlayingCard.fromId(cardId)==null)return
        val seen=getSharedPreferences("fcm_dedupe",MODE_PRIVATE)
        if(seen.getBoolean(messageId,false))return
        seen.edit().putBoolean(messageId,true).apply()
        sendBroadcast(android.content.Intent(ACTION_CARD_COMMAND).setPackage(packageName).putExtra("messageId",messageId).putExtra("cardId",cardId))
    }
    companion object{const val ACTION_CARD_COMMAND="com.cardcaller.magic.CARD_COMMAND"}
}
