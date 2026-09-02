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
    private object K { val delay=intPreferencesKey("delay"); val vibrate=booleanPreferencesKey("vibrate"); val ringtone=booleanPreferencesKey("ringtone"); val bright=booleanPreferencesKey("bright"); val format=stringPreferencesKey("format"); val theme=stringPreferencesKey("theme"); val sensitivity=floatPreferencesKey("sensitivity"); val hide=booleanPreferencesKey("hide"); val flip=booleanPreferencesKey("flip"); val volume=booleanPreferencesKey("volume"); val clear=booleanPreferencesKey("clear") }
    val settings: Flow<AppSettings> = context.dataStore.data.catch { emit(emptyPreferences()) }.map { p -> AppSettings(p[K.delay]?:3,p[K.vibrate]?:true,p[K.ringtone]?:true,p[K.bright]?:false,enumValueOrDefault(p[K.format],CardDisplayFormat.SYMBOL),enumValueOrDefault(p[K.theme],CallTheme.DARK),p[K.sensitivity]?:80f,p[K.hide]?:true,p[K.flip]?:false,p[K.volume]?:false,p[K.clear]?:true) }
    suspend fun update(s: AppSettings) = context.dataStore.edit { p -> p[K.delay]=s.delaySeconds;p[K.vibrate]=s.vibrate;p[K.ringtone]=s.ringtone;p[K.bright]=s.maxBrightness;p[K.format]=s.displayFormat.name;p[K.theme]=s.callTheme.name;p[K.sensitivity]=s.gestureSensitivity;p[K.hide]=s.hideGestureResult;p[K.flip]=s.flipTrigger;p[K.volume]=s.volumeTrigger;p[K.clear]=s.clearLastCard }
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
