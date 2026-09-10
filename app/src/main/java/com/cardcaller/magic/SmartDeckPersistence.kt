package com.cardcaller.magic

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.smartDeckDataStore by preferencesDataStore("smart_deck_settings")

class SmartDeckSettingsRepository(private val context:Context){
    private object K{val device=stringPreferencesKey("device");val auto=booleanPreferencesKey("auto");val color=stringPreferencesKey("color");val brightness=intPreferencesKey("brightness");val effect=stringPreferencesKey("effect");val delay=intPreferencesKey("delay");val random=booleanPreferencesKey("random");val off=intPreferencesKey("off");val invert=booleanPreferencesKey("invert");val secret=stringPreferencesKey("secret");val mode=stringPreferencesKey("mode");val suit=booleanPreferencesKey("suit");val blink=booleanPreferencesKey("blink");val fake=booleanPreferencesKey("fake");val cardEffects=stringPreferencesKey("card_effects")}
    val settings:Flow<SmartDeckSettings> = context.smartDeckDataStore.data.catch{emit(emptyPreferences())}.map{p->SmartDeckSettings(lastDeviceId=p[K.device].orEmpty(),autoConnect=p[K.auto]?:true,color=p[K.color]?:"#006DFF",brightness=p[K.brightness]?:80,effect=enum(p[K.effect],SmartDeckEffect.SOLID),delayMs=p[K.delay]?:0,randomDelay=p[K.random]?:false,autoOffSeconds=p[K.off]?:30,sensorInverted=p[K.invert]?:false,secretControl=enum(p[K.secret],SecretDeckControl.DISABLED),mode=enum(p[K.mode],SmartDeckMode.AUTOMATIC),suitColors=p[K.suit]?:true,rankBlink=p[K.blink]?:false,fakeDevice=p[K.fake]?:true,cardEffects=decodeEffects(p[K.cardEffects])).sanitized()}
    suspend fun save(raw:SmartDeckSettings){val s=raw.sanitized();context.smartDeckDataStore.edit{p->p[K.device]=s.lastDeviceId;p[K.auto]=s.autoConnect;p[K.color]=s.color;p[K.brightness]=s.brightness;p[K.effect]=s.effect.name;p[K.delay]=s.delayMs;p[K.random]=s.randomDelay;p[K.off]=s.autoOffSeconds;p[K.invert]=s.sensorInverted;p[K.secret]=s.secretControl.name;p[K.mode]=s.mode.name;p[K.suit]=s.suitColors;p[K.blink]=s.rankBlink;p[K.fake]=s.fakeDevice;p[K.cardEffects]=s.cardEffects.entries.joinToString(","){"${it.key}:${it.value.name}"}}}
    private fun decodeEffects(value:String?)=value.orEmpty().split(',').mapNotNull{item->val parts=item.split(':');if(parts.size!=2)null else runCatching{parts[0] to SmartDeckEffect.valueOf(parts[1])}.getOrNull()}.toMap()
    private inline fun <reified T:Enum<T>> enum(value:String?,fallback:T)=runCatching{enumValueOf<T>(value.orEmpty())}.getOrDefault(fallback)
}
