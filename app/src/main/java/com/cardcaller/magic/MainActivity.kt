package com.cardcaller.magic

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.*

class MainActivity:ComponentActivity(){private val vm by viewModels<CardCallerViewModel>();private var sensor:SensorTriggerManager?=null
    override fun onCreate(b:Bundle?){super.onCreate(b);enableEdgeToEdge();setContent{val u by vm.ui.collectAsState();LaunchedEffect(u.screen,u.settings.maxBrightness){if(u.screen==Screen.WAITING||u.screen==Screen.INCOMING)window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);window.attributes=window.attributes.apply{screenBrightness=if(u.settings.maxBrightness)1f else -1f}};CardCallerApp(vm,u)}}
    override fun onResume(){super.onResume();sensor=SensorTriggerManager(this){if(vm.ui.value.settings.flipTrigger)vm.triggerNow()}.also{it.start()}}
    override fun onPause(){sensor?.stop();super.onPause()}
    override fun onKeyDown(k:Int,e:KeyEvent?):Boolean=if((k==KeyEvent.KEYCODE_VOLUME_UP||k==KeyEvent.KEYCODE_VOLUME_DOWN)&&vm.ui.value.settings.volumeTrigger){vm.volumeSecret(if(k==KeyEvent.KEYCODE_VOLUME_UP)VolumeKey.UP else VolumeKey.DOWN);true}else super.onKeyDown(k,e)
}
