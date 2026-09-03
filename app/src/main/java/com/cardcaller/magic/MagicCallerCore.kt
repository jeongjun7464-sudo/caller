package com.cardcaller.magic

import java.util.UUID
import kotlin.math.max

enum class PerformanceState { CREATED, READY, WAITING_FOR_SECRET_INPUT, CARD_SELECTED, TRIGGER_SCHEDULED, INCOMING, CONNECTED, REVEALING, COMPLETED, CANCELLED, FAILED }
data class PerformanceSession(val id:String=UUID.randomUUID().toString(),val state:PerformanceState=PerformanceState.CREATED,val card:PlayingCard?=null,val startedAt:Long=System.currentTimeMillis(),val completedAt:Long?=null,val reaction:String?=null)
sealed interface PerformanceEvent { data object Configure:PerformanceEvent;data object AwaitInput:PerformanceEvent;data class SelectCard(val card:PlayingCard):PerformanceEvent;data object Schedule:PerformanceEvent;data object Ring:PerformanceEvent;data object Accept:PerformanceEvent;data object Reveal:PerformanceEvent;data object Complete:PerformanceEvent;data object Cancel:PerformanceEvent;data object Fail:PerformanceEvent }
object PerformanceStateMachine {
    fun transition(session:PerformanceSession,event:PerformanceEvent):PerformanceSession? {
        val next=when {
            session.state==PerformanceState.CREATED&&event==PerformanceEvent.Configure->PerformanceState.READY
            session.state==PerformanceState.READY&&event==PerformanceEvent.AwaitInput->PerformanceState.WAITING_FOR_SECRET_INPUT
            session.state==PerformanceState.WAITING_FOR_SECRET_INPUT&&event is PerformanceEvent.SelectCard->PerformanceState.CARD_SELECTED
            session.state==PerformanceState.CARD_SELECTED&&event==PerformanceEvent.Schedule->PerformanceState.TRIGGER_SCHEDULED
            session.state==PerformanceState.TRIGGER_SCHEDULED&&event==PerformanceEvent.Ring->PerformanceState.INCOMING
            session.state==PerformanceState.INCOMING&&event==PerformanceEvent.Accept->PerformanceState.CONNECTED
            session.state==PerformanceState.CONNECTED&&event==PerformanceEvent.Reveal->PerformanceState.REVEALING
            session.state==PerformanceState.REVEALING&&event==PerformanceEvent.Complete->PerformanceState.COMPLETED
            else->when(event){PerformanceEvent.Cancel->PerformanceState.CANCELLED;PerformanceEvent.Fail->PerformanceState.FAILED;else->null}
        }?:return null
        return session.copy(state=next,card=(event as? PerformanceEvent.SelectCard)?.card?:session.card,completedAt=if(next in listOf(PerformanceState.COMPLETED,PerformanceState.CANCELLED,PerformanceState.FAILED))System.currentTimeMillis() else null)
    }
}

enum class VolumeKey { UP, DOWN }
class VolumeSecretInputController(private val timeoutMs:Long=2500) {
    private var ups=0;private var downs=0;private var lastAt=0L
    fun input(key:VolumeKey,at:Long=System.currentTimeMillis()):PlayingCard? {
        if(lastAt>0&&at-lastAt>timeoutMs) reset();lastAt=at
        if(key==VolumeKey.UP&&downs>0)return confirm()
        if(key==VolumeKey.UP)ups++ else downs++
        return null
    }
    fun confirm():PlayingCard? {val card=if(ups in 1..4&&downs in 1..13)PlayingCard(listOf(Suit.SPADES,Suit.HEARTS,Suit.CLUBS,Suit.DIAMONDS)[ups-1],Rank.entries.filter{it!=Rank.JOKER}[downs-1])else null;reset();return card}
    fun reset(){ups=0;downs=0;lastAt=0}
}

object VoiceCodeParser {
    private val ranks=mapOf("에이스" to Rank.ACE,"일곱" to Rank.SEVEN,"칠" to Rank.SEVEN,"퀸" to Rank.QUEEN,"킹" to Rank.KING,"잭" to Rank.JACK,"ace" to Rank.ACE,"seven" to Rank.SEVEN,"queen" to Rank.QUEEN,"king" to Rank.KING,"jack" to Rank.JACK)
    fun parse(text:String):PlayingCard? {val s=text.lowercase();val suit=when{listOf("스페이드","검은 창","spade").any{s.contains(it)}->Suit.SPADES;listOf("하트","붉은 마음","heart").any{s.contains(it)}->Suit.HEARTS;listOf("다이아","diamond").any{s.contains(it)}->Suit.DIAMONDS;listOf("클럽","클로버","club").any{s.contains(it)}->Suit.CLUBS;else->null};val rank=ranks.entries.firstOrNull{s.contains(it.key)}?.value?:Regex("(?:^|\\D)(10|[1-9])(?:\\D|$)").find(s)?.groupValues?.get(1)?.toIntOrNull()?.let{Rank.entries.filter{r->r!=Rank.JOKER}.getOrNull(it-1)};return if(suit!=null&&rank!=null)PlayingCard(suit,rank) else null}
}

data class AiDialogue(val opening:String,val colorReveal:String,val suitReveal:String,val rankReveal:String,val finalReveal:String,val estimatedSeconds:Int)
interface DialogueGenerator { suspend fun generate(card:PlayingCard,language:TtsLanguage,style:String):AiDialogue }
class TemplateDialogueGenerator:DialogueGenerator {override suspend fun generate(card:PlayingCard,language:TtsLanguage,style:String):AiDialogue {val ko=language==TtsLanguage.KOREAN;val red=card.suit in listOf(Suit.HEARTS,Suit.DIAMONDS);return if(ko)AiDialogue("집중해 주세요.","${if(red)"붉은" else "검은"} 카드가 느껴집니다.","무늬는 ${card.suit.korean}입니다.","값은 ${card.rank.korean}입니다.","당신의 카드는 ${card.display(CardDisplayFormat.KOREAN)}!",18) else AiDialogue("Focus on your card.","I sense a ${if(red)"red" else "black"} card.","The suit is ${card.suit.name.lowercase()}.","The value is ${card.rank.name.lowercase()}.","Your card is ${card.display(CardDisplayFormat.ENGLISH)}!",18)}}
class SafeDialogueGenerator(private val remote:DialogueGenerator?,private val fallback:DialogueGenerator=TemplateDialogueGenerator()):DialogueGenerator {override suspend fun generate(card:PlayingCard,language:TtsLanguage,style:String)=runCatching{remote?.generate(card,language,style)?:error("offline")}.getOrNull()?.takeIf{it.finalReveal.length<=160&&it.estimatedSeconds in 5..60}?:fallback.generate(card,language,style)}
data class RehearsalAttempt(val expected:String,val actual:String?,val method:String,val durationMs:Long,val at:Long=System.currentTimeMillis()){val correct get()=expected==actual}
object RehearsalStats {fun accuracy(items:List<RehearsalAttempt>)=if(items.isEmpty())0.0 else items.count{it.correct}*100.0/items.size;fun averageSeconds(items:List<RehearsalAttempt>)=if(items.isEmpty())0.0 else items.sumOf{it.durationMs}.toDouble()/max(1,items.size)/1000.0}
