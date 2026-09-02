package com.cardcaller.magic

import android.content.Context
import android.hardware.*
import kotlin.math.abs

object GestureInputManager {
    fun suit(dx:Float,dy:Float,threshold:Float):Suit? { if(maxOf(abs(dx),abs(dy))<threshold)return null; return if(abs(dx)>abs(dy)) if(dx>0)Suit.HEARTS else Suit.DIAMONDS else if(dy<0)Suit.SPADES else Suit.CLUBS }
    fun rank(taps:Int)=Rank.entries.getOrNull(taps-1)
}
class SensorTriggerManager(context:Context, private val onFlip:()->Unit):SensorEventListener {
    private val manager=context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer=manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var armed=true
    fun start(){accelerometer?.let{manager.registerListener(this,it,SensorManager.SENSOR_DELAY_NORMAL)}}
    fun stop()=manager.unregisterListener(this)
    override fun onAccuracyChanged(sensor:Sensor?,accuracy:Int){}
    override fun onSensorChanged(e:SensorEvent){val faceDown=e.values[2]<-7;if(faceDown&&armed){armed=false;onFlip()};if(e.values[2]>3)armed=true}
}
