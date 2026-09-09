package com.cardcaller.magic

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import java.util.UUID

private val Context.dataStore by preferencesDataStore("card_caller_settings")
class SettingsRepository(private val context: Context) {
    private object K { val delay=intPreferencesKey("delay"); val vibrate=booleanPreferencesKey("vibrate"); val ringtone=booleanPreferencesKey("ringtone"); val bright=booleanPreferencesKey("bright"); val format=stringPreferencesKey("format"); val theme=stringPreferencesKey("theme"); val sensitivity=floatPreferencesKey("sensitivity"); val hide=booleanPreferencesKey("hide"); val flip=booleanPreferencesKey("flip"); val volume=booleanPreferencesKey("volume"); val clear=booleanPreferencesKey("clear");val caller=stringPreferencesKey("custom_caller");val phrase=stringPreferencesKey("custom_phrase");val bg=stringPreferencesKey("custom_bg");val accent=stringPreferencesKey("custom_accent");val text=stringPreferencesKey("custom_text");val target=intPreferencesKey("target_accuracy");val nTitle=stringPreferencesKey("notification_title");val nContent=stringPreferencesKey("notification_content");val nDelay=intPreferencesKey("notification_delay");val tts=booleanPreferencesKey("tts_enabled");val ttsLang=stringPreferencesKey("tts_language");val ttsSpeed=floatPreferencesKey("tts_speed");val ttsDelay=intPreferencesKey("tts_delay");val labMode=stringPreferencesKey("lab_mode");val qrExpiry=intPreferencesKey("qr_expiry") }
    val settings: Flow<AppSettings> = context.dataStore.data.catch { emit(emptyPreferences()) }.map { p -> AppSettings(delaySeconds=(p[K.delay]?:3).coerceIn(0,60),vibrate=p[K.vibrate]?:true,ringtone=p[K.ringtone]?:true,maxBrightness=p[K.bright]?:false,displayFormat=enumValueOrDefault(p[K.format],CardDisplayFormat.SYMBOL),callTheme=enumValueOrDefault(p[K.theme],CallTheme.DARK),gestureSensitivity=p[K.sensitivity]?:80f,hideGestureResult=p[K.hide]?:true,flipTrigger=p[K.flip]?:false,volumeTrigger=p[K.volume]?:false,clearLastCard=p[K.clear]?:true,customCallerName=p[K.caller].orEmpty(),customPhrase=p[K.phrase].orEmpty(),customBackgroundColor=p[K.bg]?:"#07111F",customAccentColor=p[K.accent]?:"#3DDC84",customTextColor=p[K.text]?:"#FFFFFF",targetAccuracy=(p[K.target]?:90).coerceIn(1,100),notificationTitle=p[K.nTitle]?:"카드 예언",notificationContent=p[K.nContent]?:"당신이 선택한 카드는 %CARD%입니다",notificationDelay=p[K.nDelay]?:5,ttsEnabled=p[K.tts]?:true,ttsLanguage=enumValueOrDefault(p[K.ttsLang],TtsLanguage.KOREAN),ttsSpeed=p[K.ttsSpeed]?:1f,ttsDelay=p[K.ttsDelay]?:1,labMode=enumValueOrDefault(p[K.labMode],LabMode.PRACTICE),qrExpiryMinutes=p[K.qrExpiry]?:5) }
    suspend fun update(s: AppSettings) = context.dataStore.edit { p -> p[K.delay]=s.delaySeconds.coerceIn(0,60);p[K.vibrate]=s.vibrate;p[K.ringtone]=s.ringtone;p[K.bright]=s.maxBrightness;p[K.format]=s.displayFormat.name;p[K.theme]=s.callTheme.name;p[K.sensitivity]=s.gestureSensitivity;p[K.hide]=s.hideGestureResult;p[K.flip]=s.flipTrigger;p[K.volume]=s.volumeTrigger;p[K.clear]=s.clearLastCard;p[K.caller]=CallerSettings.sanitizeName(s.customCallerName);p[K.phrase]=s.customPhrase.trim().take(60);p[K.bg]=ThemeSettings.validColor(s.customBackgroundColor,"#07111F");p[K.accent]=ThemeSettings.validColor(s.customAccentColor,"#3DDC84");p[K.text]=ThemeSettings.validColor(s.customTextColor,"#FFFFFF");p[K.target]=s.targetAccuracy.coerceIn(1,100);p[K.nTitle]=s.notificationTitle;p[K.nContent]=s.notificationContent;p[K.nDelay]=s.notificationDelay;p[K.tts]=s.ttsEnabled;p[K.ttsLang]=s.ttsLanguage.name;p[K.ttsSpeed]=s.ttsSpeed;p[K.ttsDelay]=s.ttsDelay;p[K.labMode]=s.labMode.name;p[K.qrExpiry]=s.qrExpiryMinutes }
    private inline fun <reified T:Enum<T>> enumValueOrDefault(v:String?, d:T)=runCatching{enumValueOf<T>(v?:"")}.getOrDefault(d)
}
interface CardRepository { fun cards(): List<PlayingCard> = PlayingCard.deck }
class DefaultCardRepository: CardRepository
interface RemoteCardRepository { val available:Boolean; suspend fun createRoom():String; suspend fun send(code:String,card:PlayingCard); fun listen(code:String):Flow<RemoteMessage>; suspend fun consume(code:String,id:String) }
class FirebaseRemoteCardRepository: RemoteCardRepository {
    override val available get() = runCatching { FirebaseApp.getInstance() }.isSuccess
    private val db get()=FirebaseDatabase.getInstance().reference
    private suspend fun uid():String { val a=FirebaseAuth.getInstance(); return a.currentUser?.uid ?: a.signInAnonymously().await().user!!.uid }
    override suspend fun createRoom():String { require(available); val code=RoomCode.generate(); db.child("rooms/$code/meta").setValue(mapOf("ownerId" to uid(),"expiresAt" to ServerValue.TIMESTAMP)).await(); return code }
    override suspend fun send(code:String,card:PlayingCard) { val id=UUID.randomUUID().toString(); db.child("rooms/$code/messages/$id").setValue(RemoteMessage(code,card.id,uid(),id,System.currentTimeMillis())).await() }
    override fun listen(code:String)=callbackFlow { val q=db.child("rooms/$code/messages").orderByChild("status").equalTo("pending"); val l=object:ChildEventListener { override fun onChildAdded(s:DataSnapshot,p:String?){s.getValue(RemoteMessage::class.java)?.let{trySend(it)}};override fun onCancelled(e:DatabaseError){close(e.toException())};override fun onChildChanged(s:DataSnapshot,p:String?){};override fun onChildMoved(s:DataSnapshot,p:String?){};override fun onChildRemoved(s:DataSnapshot){} };q.addChildEventListener(l);awaitClose{q.removeEventListener(l)} }
    override suspend fun consume(code:String,id:String){db.child("rooms/$code/messages/$id").updateChildren(mapOf("status" to "consumed","consumedAt" to ServerValue.TIMESTAMP)).await()}
}
object RoomCode { fun generate()=(100000..999999).random().toString(); fun valid(s:String)=s.length==6&&s.all(Char::isDigit) }
class MessageDeduplicator { private val consumed=mutableSetOf<String>(); fun shouldConsume(id:String)=consumed.add(id) }
