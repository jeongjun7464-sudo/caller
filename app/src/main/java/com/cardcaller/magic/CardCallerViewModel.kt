package com.cardcaller.magic

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class Screen { HOME, PICKER, GESTURE, REMOTE, INSTAGRAM, MAGIC_LAB, QR_PROPHECY, NOTIFICATION_PROPHECY, LIE_DETECTOR, PHOTO_MAGIC, MULTI_AUDIENCE, DEMO, REHEARSAL, SETTINGS, HELP, WAITING, INCOMING, AI_CALL, REVEAL, RESULT }
data class UiState(val screen:Screen=Screen.HOME,val selected:PlayingCard?=null,val lastCard:PlayingCard?=null,val settings:AppSettings=AppSettings(),val gestureSuit:Suit?=null,val taps:Int=0,val roomCode:String="",val connection:ConnectionState=ConnectionState.DISCONNECTED,val error:String?=null,val remoteMode:Boolean=false,val instagramAccount:InstagramAccount=InstagramAccount(),val instagramType:InstagramPublishType=InstagramPublishType.STORY,val instagramStatus:InstagramPublishStatus=InstagramPublishStatus.IDLE,val instagramResult:InstagramPublishResult?=null,val instagramRequestId:String?=null,val prophecy:QrProphecy?=null,val prophecyLoading:Boolean=false,val audiences:List<AudienceEntry> = emptyList(),val notificationScheduled:Boolean=false,val session:PerformanceSession?=null,val dialogue:AiDialogue?=null,val revealStep:Int=0,val rehearsal:List<RehearsalAttempt> = emptyList())
class CardCallerViewModel(app:Application):AndroidViewModel(app) {
    private val settingsRepo=SettingsRepository(app); private val remote:RemoteCardRepository=FirebaseRemoteCardRepository(); private val instagram:InstagramPublishRepository=FastApiInstagramRepository();private val magicLab:MagicLabRepository=FastApiMagicLabRepository(); private var callJob:Job?=null;private var listenJob:Job?=null;private val dedupe=MessageDeduplicator()
    private val _ui=MutableStateFlow(UiState());val ui:StateFlow<UiState> = _ui.asStateFlow()
    init { viewModelScope.launch { settingsRepo.settings.collect{_ui.update{s->s.copy(settings=it)}} } }
    fun navigate(s:Screen){_ui.update{it.copy(screen=s,error=null)};if(s==Screen.INSTAGRAM)loadInstagramAccount()}
    fun openRemotePicker()=_ui.update{it.copy(screen=Screen.PICKER,remoteMode=true,error=null)}
    fun select(c:PlayingCard)=_ui.update{it.copy(selected=c,lastCard=c,session=advanceToCard(it.session,c))}
    fun schedule(delaySeconds:Int=_ui.value.settings.delaySeconds){val c=_ui.value.selected?:return;callJob?.cancel();_ui.update{it.copy(screen=Screen.WAITING)};callJob=viewModelScope.launch{delay(delaySeconds.coerceAtLeast(0)*1000L);_ui.update{it.copy(screen=Screen.INCOMING,selected=c)}}}
    fun triggerNow(){if(_ui.value.selected!=null){callJob?.cancel();_ui.update{it.copy(screen=Screen.INCOMING)}}}
    fun accept(){_ui.update{it.copy(screen=Screen.AI_CALL,revealStep=0)};generateDialogue()}
    fun reject()=home()
    fun home(){callJob?.cancel();_ui.update{UiState(settings=it.settings,selected=if(it.settings.clearLastCard)null else it.selected,lastCard=it.lastCard,instagramAccount=it.instagramAccount,prophecy=it.prophecy,audiences=it.audiences)}}
    fun gestureSuit(s:Suit)=_ui.update{it.copy(gestureSuit=s)}
    fun tap(){_ui.update{it.copy(taps=(it.taps+1).coerceAtMost(13))};completeGesture()}
    fun resetGesture()=_ui.update{it.copy(gestureSuit=null,taps=0,selected=null)}
    private fun completeGesture(){val u=_ui.value;val r=GestureInputManager.rank(u.taps);if(u.gestureSuit!=null&&r!=null)_ui.update{it.copy(selected=PlayingCard(u.gestureSuit,r))}}
    fun saveSettings(s:AppSettings)=viewModelScope.launch{settingsRepo.update(s)}
    fun createRoom(){if(!remote.available){_ui.update{it.copy(error="Firebase 설정이 필요합니다")};return};listenJob=viewModelScope.launch{runCatching{remote.createRoom()}.onSuccess{code->_ui.update{it.copy(roomCode=code,connection=ConnectionState.CONNECTED)};listen(code)}.onFailure{e->_ui.update{it.copy(error=e.message,connection=ConnectionState.DISCONNECTED)}}}}
    fun join(code:String){if(!RoomCode.valid(code)){_ui.update{it.copy(error="6자리 방 코드를 입력하세요")};return};_ui.update{it.copy(roomCode=code,connection=ConnectionState.CONNECTED)};listen(code)}
    fun sendRemote(c:PlayingCard)=viewModelScope.launch{runCatching{remote.send(_ui.value.roomCode,c)}.onFailure{e->_ui.update{it.copy(error=e.message)}}}
    private fun loadInstagramAccount()=viewModelScope.launch{runCatching{instagram.account()}.onSuccess{a->_ui.update{it.copy(instagramAccount=a)}}.onFailure{e->_ui.update{it.copy(error=e.message)}}}
    fun confirmInstagram(type:InstagramPublishType)=_ui.update{it.copy(instagramType=type,instagramStatus=InstagramPublishStatus.CONFIRMING,error=null)}
    fun cancelInstagramConfirm()=_ui.update{it.copy(instagramStatus=InstagramPublishStatus.IDLE)}
    fun publishInstagram(retry:Boolean=false){val card=_ui.value.selected?:_ui.value.lastCard?:return;val id=if(retry)_ui.value.instagramRequestId?:java.util.UUID.randomUUID().toString() else java.util.UUID.randomUUID().toString();_ui.update{it.copy(instagramStatus=if(retry)InstagramPublishStatus.RETRYING else InstagramPublishStatus.PUBLISHING,instagramRequestId=id,error=null)};viewModelScope.launch{runCatching{instagram.publish(card,_ui.value.instagramType,id)}.onSuccess{r->_ui.update{it.copy(instagramStatus=r.status,instagramResult=r)}}.onFailure{e->_ui.update{it.copy(instagramStatus=InstagramPublishStatus.FAILED,error=e.message,instagramResult=InstagramPublishResult(id,InstagramPublishStatus.FAILED,error=e.message))}}}}
    fun setLabMode(mode:LabMode){val next=_ui.value.settings.copy(labMode=mode);_ui.update{it.copy(settings=next)};saveSettings(next)}
    fun createProphecy(withCard:Boolean=true)=viewModelScope.launch{_ui.update{it.copy(prophecyLoading=true,error=null)};val card=(_ui.value.selected?:_ui.value.lastCard).takeIf{withCard};runCatching{magicLab.createProphecy(card,_ui.value.settings.qrExpiryMinutes)}.onSuccess{p->_ui.update{it.copy(prophecy=p,prophecyLoading=false)}}.onFailure{e->_ui.update{it.copy(prophecyLoading=false,error=e.message)}}}
    fun setProphecyCard()=viewModelScope.launch{val p=_ui.value.prophecy?:return@launch;val card=_ui.value.selected?:_ui.value.lastCard?:return@launch;runCatching{magicLab.setCard(p,card)}.onSuccess{n->_ui.update{it.copy(prophecy=n)}}.onFailure{e->_ui.update{it.copy(error=e.message)}}}
    fun scheduleNotification(delay:Int){val card=_ui.value.selected?:_ui.value.lastCard?:return;CardNotificationWorker.schedule(getApplication(),card,_ui.value.settings.notificationTitle,_ui.value.settings.notificationContent,delay);_ui.update{it.copy(notificationScheduled=true)}}
    fun addAudience(name:String)=viewModelScope.launch{val card=_ui.value.selected?:_ui.value.lastCard;runCatching{magicLab.createProphecy(card,_ui.value.settings.qrExpiryMinutes)}.onSuccess{p->_ui.update{it.copy(audiences=it.audiences+AudienceEntry(name,p,card))}}.onFailure{e->_ui.update{it.copy(error=e.message)}}}
    fun refreshAudiences()=viewModelScope.launch{val refreshed=_ui.value.audiences.map{entry->runCatching{val p=magicLab.status(entry.prophecy);entry.copy(prophecy=p,revealed=p.consumed)}.getOrDefault(entry)};_ui.update{it.copy(audiences=refreshed)}}
    fun startDemo(index:Int){val cards=listOf(PlayingCard(Suit.SPADES,Rank.SEVEN),PlayingCard(Suit.HEARTS,Rank.QUEEN),PlayingCard(Suit.CLUBS,Rank.ACE));val c=cards[index.coerceIn(0,2)];select(c);schedule(listOf(5,0,0)[index.coerceIn(0,2)])}
    fun nextDialogue(){val n=(_ui.value.revealStep+1).coerceAtMost(4);_ui.update{it.copy(revealStep=n)};if(n==4)viewModelScope.launch{delay(900);_ui.update{it.copy(screen=Screen.REVEAL)}}}
    fun finishPerformance(reaction:String){_ui.update{it.copy(screen=Screen.RESULT,session=it.session?.copy(state=PerformanceState.COMPLETED,reaction=reaction,completedAt=System.currentTimeMillis()))}}
    fun recordRehearsal(expected:PlayingCard,actual:PlayingCard?,method:String,durationMs:Long)=_ui.update{it.copy(rehearsal=it.rehearsal+RehearsalAttempt(expected.id,actual?.id,method,durationMs))}
    private val volumeInput=VolumeSecretInputController()
    fun volumeSecret(key:VolumeKey){volumeInput.input(key)?.let(::select)}
    private fun generateDialogue(){val c=_ui.value.selected?:return;viewModelScope.launch{_ui.update{it.copy(dialogue=SafeDialogueGenerator(null).generate(c,it.settings.ttsLanguage,"MENTALIST"))}}}
    private fun advanceToCard(session:PerformanceSession?,card:PlayingCard):PerformanceSession {var s=session?:PerformanceSession();if(s.state==PerformanceState.CREATED)s=PerformanceStateMachine.transition(s,PerformanceEvent.Configure)!!;if(s.state==PerformanceState.READY)s=PerformanceStateMachine.transition(s,PerformanceEvent.AwaitInput)!!;return PerformanceStateMachine.transition(s,PerformanceEvent.SelectCard(card))?:s.copy(card=card,state=PerformanceState.CARD_SELECTED)}
    private fun listen(code:String){listenJob?.cancel();listenJob=viewModelScope.launch{remote.listen(code).retry{_ui.update{u->u.copy(connection=ConnectionState.RECONNECTING)};delay(2000);true}.collect{m->if(dedupe.shouldConsume(m.messageId)){PlayingCard.fromId(m.cardId)?.let{select(it);_ui.update{u->u.copy(connection=ConnectionState.CARD_RECEIVED)};schedule()};remote.consume(code,m.messageId)}}}}
}
