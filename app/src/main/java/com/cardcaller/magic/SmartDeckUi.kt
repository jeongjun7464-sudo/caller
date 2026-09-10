package com.cardcaller.magic

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DeckBlue=Color(0xFF087CFF)

@Composable private fun DeckPage(title:String,back:()->Unit,content:@Composable ColumnScope.()->Unit){Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)){Row(verticalAlignment=Alignment.CenterVertically){IconButton(back){Icon(Icons.AutoMirrored.Filled.ArrowBack,"뒤로")};Text(title,fontSize=26.sp,fontWeight=FontWeight.Bold,color=DeckBlue)};Spacer(Modifier.height(8.dp));content()}}
@Composable private fun RowScope.DeckMetric(label:String,value:String,color:Color=Color.White){Card(Modifier.weight(1f),colors=CardDefaults.cardColors(containerColor=Color(0xFF10243E))){Column(Modifier.padding(12.dp)){Text(label,fontSize=12.sp,color=Color(0xFFAFC6E2));Text(value,fontSize=17.sp,fontWeight=FontWeight.Bold,color=color)}}}

@Composable fun SmartDeckDashboard(vm:CardCallerViewModel,u:UiState){
    val s=u.smartDeck;val settings=u.smartDeckSettings
    DeckPage("Smart Deck",{vm.navigate(Screen.HOME)}){
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).then(if(settings.secretControl==SecretDeckControl.HIDDEN_TOUCH)Modifier.pointerInput(s.ledOn){detectTapGestures(onTap={vm.smartDeckLed(!s.ledOn)},onLongPress={vm.smartDeckFinale()})}else Modifier)){
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){DeckMetric("연결",friendlyState(s,u.settings.labMode),if(s.connected)Color(0xFF55E28A)else Color.White);DeckMetric("케이스",if(s.deckOpen)"열림" else "닫힘")}
            Spacer(Modifier.height(8.dp));Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){DeckMetric("LED",if(s.ledOn)"ON" else "OFF",if(s.ledOn)DeckBlue else Color.White);DeckMetric("배터리",s.battery?.let{"$it%"}?:"—",if((s.battery?:100)<15)Color(0xFFFF6B6B)else Color.White)}
            Spacer(Modifier.height(12.dp));Text("선택 카드",color=Color(0xFFAFC6E2));Text((u.selected?:u.lastCard)?.display(if(u.settings.labMode==LabMode.PERFORMANCE)CardDisplayFormat.HIDDEN else CardDisplayFormat.KOREAN)?:"선택 안 됨",fontSize=22.sp,fontWeight=FontWeight.Bold)
            s.errorCode?.let{Text(if(u.settings.labMode==LabMode.PERFORMANCE)"장치 상태를 확인하세요" else "개발자 오류: $it",color=MaterialTheme.colorScheme.error)}
            Spacer(Modifier.height(14.dp));Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button({vm.armSmartDeck(true)},Modifier.weight(1f)){Text("ARM")};OutlinedButton({vm.armSmartDeck(false)},Modifier.weight(1f)){Text("DISARM")}}
            Button(vm::smartDeckFinale,Modifier.fillMaxWidth()){Icon(Icons.Default.AutoAwesome,null);Text(" 피날레 실행")}
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton({vm.smartDeckLed(true)},Modifier.weight(1f)){Text("LED ON")};Button({vm.smartDeckLed(false)},Modifier.weight(1f),colors=ButtonDefaults.buttonColors(containerColor=Color(0xFFD83333))){Text("긴급 LED OFF")}}
            if(settings.fakeDevice){HorizontalDivider(Modifier.padding(vertical=12.dp));Text("Fake 리허설 센서",fontWeight=FontWeight.Bold);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton({vm.fakeDeckEvent(true)},Modifier.weight(1f)){Text("케이스 열기")};OutlinedButton({vm.fakeDeckEvent(false)},Modifier.weight(1f)){Text("케이스 닫기")}}}
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton({vm.navigate(Screen.SMART_DECK_SETTINGS)},Modifier.weight(1f)){Text("장치 설정")};OutlinedButton({vm.navigate(Screen.SMART_DECK_REHEARSAL)},Modifier.weight(1f)){Text("리허설")}}
    }
}

@Composable fun SmartDeckSettingsScreen(vm:CardCallerViewModel,u:UiState){
    var s by remember(u.smartDeckSettings){mutableStateOf(u.smartDeckSettings)};var permissionDenied by remember{mutableStateOf(false)};val context=LocalContext.current
    val permissions=if(Build.VERSION.SDK_INT>=31)arrayOf(Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT)else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION)
    fun granted()=permissions.all{context.checkSelfPermission(it)==PackageManager.PERMISSION_GRANTED}
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){result->permissionDenied=!result.values.all{it};if(!permissionDenied)vm.scanSmartDeck()}
    DeckPage("Smart Deck 설정",{vm.navigate(Screen.SMART_DECK)}){
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())){
            SettingRow("Fake 장치 사용",s.fakeDevice){s=s.copy(fakeDevice=it)}
            Text("실제 장치 검색은 이 화면에서만 Bluetooth 권한을 요청합니다.",fontSize=12.sp,color=Color(0xFFAFC6E2))
            Button({if(s.fakeDevice||granted())vm.scanSmartDeck()else launcher.launch(permissions)},Modifier.fillMaxWidth()){Text("장치 검색")}
            if(permissionDenied){Text("Smart Deck 장치를 찾고 연결하려면 주변 기기 권한이 필요합니다. Android 11 이하는 BLE 검색 정책상 위치 권한이 필요합니다.",color=MaterialTheme.colorScheme.error,fontSize=12.sp);OutlinedButton({context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}")))},Modifier.fillMaxWidth()){Text("앱 권한 설정 열기")}}
            u.smartDeckCandidates.forEach{d->Card(Modifier.fillMaxWidth().padding(vertical=3.dp).clickable{vm.connectSmartDeck(d)}){Row(Modifier.padding(12.dp)){Text(d.name,Modifier.weight(1f));Text(d.signal?.let{"$it dBm"}?:"")}}}
            SettingRow("최근 장치 자동 연결",s.autoConnect){s=s.copy(autoConnect=it)}
            Text("공연 모드");ChoiceRow(SmartDeckMode.entries,s.mode){s=s.copy(mode=it)}
            OutlinedTextField(s.color,{s=s.copy(color=it.take(7))},label={Text("LED 색상 #RRGGBB")},modifier=Modifier.fillMaxWidth())
            Text("밝기 ${s.brightness}%");Slider(s.brightness.toFloat(),{s=s.copy(brightness=it.toInt())},valueRange=1f..100f)
            Text("효과");ChoiceRow(SmartDeckEffect.entries,s.effect){s=s.copy(effect=it)}
            Text("점등 지연 ${s.delayMs}ms");Slider(s.delayMs.toFloat(),{s=s.copy(delayMs=it.toInt())},valueRange=0f..5000f)
            SettingRow("무작위 지연",s.randomDelay){s=s.copy(randomDelay=it)}
            Text("자동 종료 ${s.autoOffSeconds}초 (최대 120초)");Slider(s.autoOffSeconds.toFloat(),{s=s.copy(autoOffSeconds=it.toInt())},valueRange=1f..120f)
            SettingRow("센서 방향 반전",s.sensorInverted){s=s.copy(sensorInverted=it)}
            SettingRow("문양별 색상 자동 설정",s.suitColors){s=s.copy(suitColors=it)}
            SettingRow("숫자만큼 점멸",s.rankBlink){s=s.copy(rankBlink=it)}
            (u.selected?:u.lastCard)?.let{card->Text("${card.shortCode} 전용 효과");ChoiceRow(SmartDeckEffect.entries,s.cardEffects[card.id]?:s.effect){chosen->s=s.copy(cardEffects=s.cardEffects+(card.id to chosen))}}
            Text("비밀 조작");ChoiceRow(SecretDeckControl.entries,s.secretControl){s=s.copy(secretControl=it)}
        }
        Button({vm.saveSmartDeckSettings(s);vm.navigate(Screen.SMART_DECK)},Modifier.fillMaxWidth()){Text("설정 저장")}
    }
}

@Composable fun SmartDeckRehearsal(vm:CardCallerViewModel,u:UiState){val steps:List<Pair<String,()->Unit>> = listOf("1. 장치 검색" to {vm.scanSmartDeck();Unit},"2. 카드 선택" to {vm.navigate(Screen.PICKER)},"3. 가짜 전화 실행" to {vm.schedule(0);Unit},"4. 케이스 열기" to {vm.fakeDeckEvent(true);Unit},"5. 피날레 실행" to {vm.smartDeckFinale();Unit},"6. 케이스 닫기" to {vm.fakeDeckEvent(false);Unit});DeckPage("Smart Deck 리허설",{vm.navigate(Screen.SMART_DECK)}){Column(Modifier.weight(1f).verticalScroll(rememberScrollState())){Text("Fake 장치로 카드 선택 → 수신 → 공개 → 피날레 순서를 시험합니다.",color=Color(0xFFAFC6E2));steps.forEach{(label,action)->OutlinedButton(onClick=action,modifier=Modifier.fillMaxWidth()){Text(label)}};Text("이벤트 시간",fontWeight=FontWeight.Bold);u.smartDeckLog.asReversed().forEach{Text(it,fontSize=12.sp)}}}}

@Composable private fun SettingRow(label:String,value:Boolean,set:(Boolean)->Unit){Row(Modifier.fillMaxWidth().heightIn(min=52.dp),verticalAlignment=Alignment.CenterVertically){Text(label,Modifier.weight(1f));Switch(value,set)}}
@Composable private fun <T> ChoiceRow(values:List<T>,selected:T,set:(T)->Unit){Row(Modifier.fillMaxWidth()){values.forEach{FilterChip(selected==it,{set(it)},label={Text(it.toString(),fontSize=11.sp)},modifier=Modifier.padding(end=4.dp))}}}
private fun friendlyState(snapshot:SmartDeckSnapshot,mode:LabMode)=if(mode==LabMode.PERFORMANCE)when{snapshot.state==SmartDeckState.ERROR->"확인 필요";!snapshot.connected->"대기";snapshot.armed->"준비됨";else->"연결됨"}else if(snapshot.connected)"CONNECTED · ${if(snapshot.armed)"ARMED" else "DISARMED"}" else snapshot.state.name
