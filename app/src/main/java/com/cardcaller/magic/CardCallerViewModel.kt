package com.cardcaller.magic

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class Screen { HOME, PICKER, GESTURE, REMOTE, SETTINGS, HELP, WAITING, INCOMING, REVEAL }
data class UiState(val screen:Screen=Screen.HOME,val selected:PlayingCard?=null,val settings:AppSettings=AppSettings(),val gestureSuit:Suit?=null,val taps:Int=0,val roomCode:String="",val connection:ConnectionState=ConnectionState.DISCONNECTED,val error:String?=null,val remoteMode:Boolean=false)
class CardCallerViewModel(app:Application):AndroidViewModel(app) {
    private val settingsRepo=SettingsRepository(app); private val remote:RemoteCardRepository=FirebaseRemoteCardRepository(); private var callJob:Job?=null;private var listenJob:Job?=null;private val dedupe=MessageDeduplicator()
    private val _ui=MutableStateFlow(UiState());val ui:StateFlow<UiState> = _ui.asStateFlow()
    init { viewModelScope.launch { settingsRepo.settings.collect{_ui.update{s->s.copy(settings=it)}} } }
    fun navigate(s:Screen)=_ui.update{it.copy(screen=s,error=null)}
    fun openRemotePicker()=_ui.update{it.copy(screen=Screen.PICKER,remoteMode=true,error=null)}
    fun select(c:PlayingCard)=_ui.update{it.copy(selected=c)}
    fun schedule(delaySeconds:Int=_ui.value.settings.delaySeconds){val c=_ui.value.selected?:return;callJob?.cancel();_ui.update{it.copy(screen=Screen.WAITING)};callJob=viewModelScope.launch{delay(delaySeconds.coerceAtLeast(0)*1000L);_ui.update{it.copy(screen=Screen.INCOMING,selected=c)}}}
    fun triggerNow(){if(_ui.value.selected!=null){callJob?.cancel();_ui.update{it.copy(screen=Screen.INCOMING)}}}
    fun accept()=navigate(Screen.REVEAL)
    fun reject()=home()
    fun home(){callJob?.cancel();_ui.update{UiState(settings=it.settings,selected=if(it.settings.clearLastCard)null else it.selected)}}
    fun gestureSuit(s:Suit)=_ui.update{it.copy(gestureSuit=s)}
    fun tap(){_ui.update{it.copy(taps=(it.taps+1).coerceAtMost(13))};completeGesture()}
    fun resetGesture()=_ui.update{it.copy(gestureSuit=null,taps=0,selected=null)}
    private fun completeGesture(){val u=_ui.value;val r=GestureInputManager.rank(u.taps);if(u.gestureSuit!=null&&r!=null)_ui.update{it.copy(selected=PlayingCard(u.gestureSuit,r))}}
    fun saveSettings(s:AppSettings)=viewModelScope.launch{settingsRepo.update(s)}
    fun createRoom(){if(!remote.available){_ui.update{it.copy(error="Firebase 설정이 필요합니다")};return};listenJob=viewModelScope.launch{runCatching{remote.createRoom()}.onSuccess{code->_ui.update{it.copy(roomCode=code,connection=ConnectionState.CONNECTED)};listen(code)}.onFailure{e->_ui.update{it.copy(error=e.message,connection=ConnectionState.DISCONNECTED)}}}}
    fun join(code:String){if(!RoomCode.valid(code)){_ui.update{it.copy(error="6자리 방 코드를 입력하세요")};return};_ui.update{it.copy(roomCode=code,connection=ConnectionState.CONNECTED)};listen(code)}
    fun sendRemote(c:PlayingCard)=viewModelScope.launch{runCatching{remote.send(_ui.value.roomCode,c)}.onFailure{e->_ui.update{it.copy(error=e.message)}}}
    private fun listen(code:String){listenJob?.cancel();listenJob=viewModelScope.launch{remote.listen(code).retry{_ui.update{u->u.copy(connection=ConnectionState.RECONNECTING)};delay(2000);true}.collect{m->if(dedupe.shouldConsume(m.messageId)){PlayingCard.fromId(m.cardId)?.let{select(it);_ui.update{u->u.copy(connection=ConnectionState.CARD_RECEIVED)};schedule()};remote.consume(code,m.messageId)}}}}
}
