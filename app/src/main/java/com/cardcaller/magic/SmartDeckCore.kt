package com.cardcaller.magic

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import kotlin.random.Random

enum class SmartDeckState { DISCONNECTED, SCANNING, CONNECTING, CONNECTED, ARMED, DECK_CLOSED, DECK_OPEN, LED_ON, LED_OFF, ERROR }
enum class SmartDeckEffect { SOLID, BLINK, FADE, RAINBOW, COURT }
enum class SmartDeckMode { AUTOMATIC, DELAYED, FINALE }
enum class SecretDeckControl { DISABLED, HIDDEN_TOUCH, VOLUME }

data class SmartDeckSettings(
    val lastDeviceId:String="",
    val autoConnect:Boolean=true,
    val color:String="#006DFF",
    val brightness:Int=80,
    val effect:SmartDeckEffect=SmartDeckEffect.SOLID,
    val delayMs:Int=0,
    val randomDelay:Boolean=false,
    val autoOffSeconds:Int=30,
    val sensorInverted:Boolean=false,
    val secretControl:SecretDeckControl=SecretDeckControl.DISABLED,
    val mode:SmartDeckMode=SmartDeckMode.AUTOMATIC,
    val suitColors:Boolean=true,
    val rankBlink:Boolean=false,
    val fakeDevice:Boolean=true,
    val cardEffects:Map<String,SmartDeckEffect> = emptyMap()
) { fun sanitized()=copy(color=SmartDeckColor.valid(color),brightness=brightness.coerceIn(1,100),delayMs=delayMs.coerceIn(0,5000),autoOffSeconds=autoOffSeconds.coerceIn(1,120)) }

data class SmartDeckSnapshot(
    val state:SmartDeckState=SmartDeckState.DISCONNECTED,
    val connected:Boolean=false,
    val armed:Boolean=false,
    val deviceId:String?=null,
    val deviceName:String?=null,
    val deckOpen:Boolean=false,
    val ledOn:Boolean=false,
    val battery:Int?=null,
    val sensorHealthy:Boolean=true,
    val errorCode:String?=null,
    val lastEventAt:Long=0
)

sealed interface SmartDeckCommand {
    data class LedOn(val color:String,val brightness:Int):SmartDeckCommand
    data object LedOff:SmartDeckCommand
    data class Effect(val type:SmartDeckEffect,val durationMs:Int,val flashes:Int?=null):SmartDeckCommand
    data class SetMaxOn(val seconds:Int):SmartDeckCommand
    data object Arm:SmartDeckCommand
    data object Disarm:SmartDeckCommand
    data object GetStatus:SmartDeckCommand
}

sealed interface SmartDeckEvent {
    val battery:Int?
    data class DeckOpen(override val battery:Int?):SmartDeckEvent
    data class DeckClosed(override val battery:Int?):SmartDeckEvent
    data class LedState(val enabled:Boolean,override val battery:Int?=null):SmartDeckEvent
    data class DeviceStatus(override val battery:Int?,val sensor:Boolean):SmartDeckEvent
    data class Error(val code:String,override val battery:Int?=null):SmartDeckEvent
}

object SmartDeckColor {
    private val pattern=Regex("^#[0-9A-Fa-f]{6}$")
    fun valid(value:String)=if(pattern.matches(value))value.uppercase(Locale.US) else "#006DFF"
}

object SmartDeckCodec {
    fun encode(command:SmartDeckCommand):String=when(command){
        is SmartDeckCommand.LedOn->"{\"command\":\"LED_ON\",\"color\":\"${SmartDeckColor.valid(command.color)}\",\"brightness\":${command.brightness.coerceIn(1,100)}}"
        SmartDeckCommand.LedOff->"{\"command\":\"LED_OFF\"}"
        is SmartDeckCommand.Effect->buildString{append("{\"command\":\"EFFECT\",\"type\":\"${command.type.name}\",\"durationMs\":${command.durationMs.coerceIn(100,120000)}");command.flashes?.let{append(",\"flashes\":${it.coerceIn(1,13)}")};append("}")}
        is SmartDeckCommand.SetMaxOn->"{\"command\":\"SET_MAX_ON\",\"seconds\":${command.seconds.coerceIn(1,120)}}"
        SmartDeckCommand.Arm->"{\"command\":\"ARM\"}"
        SmartDeckCommand.Disarm->"{\"command\":\"DISARM\"}"
        SmartDeckCommand.GetStatus->"{\"command\":\"GET_STATUS\"}"
    }
    fun decodeEvent(json:String):SmartDeckEvent? {
        fun string(key:String)=Regex("\\\"$key\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").find(json)?.groupValues?.get(1)
        fun int(key:String)=Regex("\\\"$key\\\"\\s*:\\s*(\\d+)").find(json)?.groupValues?.get(1)?.toIntOrNull()
        fun bool(key:String)=Regex("\\\"$key\\\"\\s*:\\s*(true|false)",RegexOption.IGNORE_CASE).find(json)?.groupValues?.get(1)?.toBooleanStrictOrNull()
        val battery=int("battery")?.coerceIn(0,100)
        return when(string("event")){
            "DECK_OPEN"->SmartDeckEvent.DeckOpen(battery)
            "DECK_CLOSED"->SmartDeckEvent.DeckClosed(battery)
            "LED_STATE"->bool("enabled")?.let{SmartDeckEvent.LedState(it,battery)}
            "DEVICE_STATUS"->SmartDeckEvent.DeviceStatus(battery,bool("sensor")?:false)
            "ERROR"->SmartDeckEvent.Error(string("code")?:"UNKNOWN",battery)
            else->null
        }
    }
}

object CardLedEffectMapper {
    fun color(card:PlayingCard)=when(card.suit){Suit.HEARTS->"#FF2038";Suit.DIAMONDS->"#FF7A20";Suit.SPADES->"#006DFF";Suit.CLUBS->"#16C060";Suit.JOKER->"#FFFFFF"}
    fun effect(card:PlayingCard,rankBlink:Boolean=false)=when{
        card.suit==Suit.JOKER->SmartDeckEffect.RAINBOW
        card.rank in listOf(Rank.JACK,Rank.QUEEN,Rank.KING)->SmartDeckEffect.COURT
        else->if(rankBlink)SmartDeckEffect.BLINK else SmartDeckEffect.SOLID
    }
    fun flashes(card:PlayingCard)=when(card.rank){Rank.ACE->1;Rank.TWO->2;Rank.THREE->3;Rank.FOUR->4;Rank.FIVE->5;Rank.SIX->6;Rank.SEVEN->7;Rank.EIGHT->8;Rank.NINE->9;Rank.TEN->10;Rank.JACK->11;Rank.QUEEN->12;Rank.KING->13;Rank.JOKER->1}
}

class SensorEventGate(private val debounceMs:Long=200) {
    private var lastType:String?=null;private var lastAt=Long.MIN_VALUE
    fun accept(type:String,now:Long):Boolean{if(type==lastType&&now-lastAt<debounceMs)return false;lastType=type;lastAt=now;return true}
}

interface SmartDeckDevice {
    val snapshot:StateFlow<SmartDeckSnapshot>
    val events:Flow<SmartDeckEvent>
    suspend fun scan():List<SmartDeckCandidate>
    suspend fun connect(deviceId:String)
    suspend fun disconnect()
    suspend fun send(command:SmartDeckCommand)
    fun emergencyClose()
}
data class SmartDeckCandidate(val id:String,val name:String,val signal:Int?=null)

class FakeSmartDeckDevice:SmartDeckDevice {
    private val mutableSnapshot=MutableStateFlow(SmartDeckSnapshot())
    private val mutableEvents=MutableSharedFlow<SmartDeckEvent>(extraBufferCapacity=16)
    override val snapshot:StateFlow<SmartDeckSnapshot> = mutableSnapshot
    override val events:Flow<SmartDeckEvent> = mutableEvents
    val commands=mutableListOf<SmartDeckCommand>()
    override suspend fun scan():List<SmartDeckCandidate>{mutableSnapshot.value=mutableSnapshot.value.copy(state=SmartDeckState.SCANNING);return listOf(SmartDeckCandidate("FAKE-SMART-DECK","Smart Deck Simulator",-42))}
    override suspend fun connect(deviceId:String){mutableSnapshot.value=SmartDeckSnapshot(state=SmartDeckState.CONNECTED,connected=true,deviceId=deviceId,deviceName="Smart Deck Simulator",battery=87)}
    override suspend fun disconnect(){send(SmartDeckCommand.LedOff);mutableSnapshot.value=SmartDeckSnapshot()}
    override suspend fun send(command:SmartDeckCommand){commands+=command;mutableSnapshot.value=when(command){is SmartDeckCommand.LedOn->mutableSnapshot.value.copy(state=SmartDeckState.LED_ON,ledOn=true);SmartDeckCommand.LedOff->mutableSnapshot.value.copy(state=SmartDeckState.LED_OFF,ledOn=false);SmartDeckCommand.Arm->mutableSnapshot.value.copy(state=SmartDeckState.ARMED,armed=true);SmartDeckCommand.Disarm->mutableSnapshot.value.copy(state=SmartDeckState.CONNECTED,armed=false);else->mutableSnapshot.value}}
    override fun emergencyClose(){mutableSnapshot.value=SmartDeckSnapshot();commands+=SmartDeckCommand.LedOff}
    suspend fun emit(event:SmartDeckEvent,now:Long=System.currentTimeMillis()){mutableSnapshot.value=when(event){is SmartDeckEvent.DeckOpen->mutableSnapshot.value.copy(state=SmartDeckState.DECK_OPEN,deckOpen=true,battery=event.battery,lastEventAt=now);is SmartDeckEvent.DeckClosed->mutableSnapshot.value.copy(state=SmartDeckState.DECK_CLOSED,deckOpen=false,battery=event.battery,lastEventAt=now);is SmartDeckEvent.LedState->mutableSnapshot.value.copy(state=if(event.enabled)SmartDeckState.LED_ON else SmartDeckState.LED_OFF,ledOn=event.enabled,lastEventAt=now);is SmartDeckEvent.DeviceStatus->mutableSnapshot.value.copy(battery=event.battery,sensorHealthy=event.sensor,lastEventAt=now);is SmartDeckEvent.Error->mutableSnapshot.value.copy(state=SmartDeckState.ERROR,errorCode=event.code,lastEventAt=now)};mutableEvents.emit(event)}
}

fun selectedDelay(settings:SmartDeckSettings,random:Random=Random.Default)=if(settings.randomDelay)random.nextInt(0,settings.delayMs+1) else settings.delayMs

object SmartDeckAutomation {
    fun commandsFor(event:SmartDeckEvent,settings:SmartDeckSettings,card:PlayingCard?,finaleArmed:Boolean=false):List<SmartDeckCommand>{
        val safe=settings.sanitized()
        return when(event){
            is SmartDeckEvent.DeckClosed->listOf(SmartDeckCommand.LedOff)
            is SmartDeckEvent.DeckOpen->if(safe.mode==SmartDeckMode.FINALE&&!finaleArmed)emptyList()else cardCommands(safe,card,safe.mode==SmartDeckMode.FINALE)
            is SmartDeckEvent.Error->listOf(SmartDeckCommand.LedOff)
            else->emptyList()
        }
    }
    fun cardCommands(settings:SmartDeckSettings,card:PlayingCard?,finale:Boolean):List<SmartDeckCommand>{
        val color=if(settings.suitColors&&card!=null)CardLedEffectMapper.color(card)else settings.color
        val on=SmartDeckCommand.LedOn(color,settings.brightness)
        val effect=card?.let{settings.cardEffects[it.id]}?:if(finale&&card!=null)CardLedEffectMapper.effect(card,settings.rankBlink)else settings.effect
        return if(effect==SmartDeckEffect.SOLID)listOf(on)else listOf(on,SmartDeckCommand.Effect(effect,1500,card?.takeIf{settings.rankBlink}?.let(CardLedEffectMapper::flashes)))
    }
}
