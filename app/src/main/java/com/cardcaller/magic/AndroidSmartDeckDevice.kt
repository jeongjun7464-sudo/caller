package com.cardcaller.magic

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@SuppressLint("MissingPermission")
class AndroidSmartDeckDevice(private val context:Context):SmartDeckDevice {
    private val manager=context.getSystemService(BluetoothManager::class.java)
    private val adapter get()=manager?.adapter
    private val mutableSnapshot=MutableStateFlow(SmartDeckSnapshot())
    private val mutableEvents=MutableSharedFlow<SmartDeckEvent>(extraBufferCapacity=32)
    override val snapshot:StateFlow<SmartDeckSnapshot> = mutableSnapshot
    override val events:Flow<SmartDeckEvent> = mutableEvents
    private var gatt:BluetoothGatt?=null
    private var commandCharacteristic:BluetoothGattCharacteristic?=null
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val operationMutex=Mutex()
    private var writeResult:CompletableDeferred<Int>?=null
    private var descriptorResult:CompletableDeferred<Int>?=null

    private val callback=object:BluetoothGattCallback(){
        override fun onConnectionStateChange(g:BluetoothGatt,status:Int,newState:Int){
            if(status!=BluetoothGatt.GATT_SUCCESS||newState==BluetoothProfile.STATE_DISCONNECTED){commandCharacteristic=null;mutableSnapshot.value=SmartDeckSnapshot(state=if(status==BluetoothGatt.GATT_SUCCESS)SmartDeckState.DISCONNECTED else SmartDeckState.ERROR,errorCode=if(status==BluetoothGatt.GATT_SUCCESS)null else "GATT_$status");runCatching{g.close()};return}
            if(newState==BluetoothProfile.STATE_CONNECTED){mutableSnapshot.value=mutableSnapshot.value.copy(state=SmartDeckState.CONNECTED,connected=true,deviceId=g.device.address,deviceName=g.device.name);g.discoverServices()}
        }
        override fun onServicesDiscovered(g:BluetoothGatt,status:Int){
            val service=g.getService(UUID.fromString(BuildConfig.SMART_DECK_SERVICE_UUID));commandCharacteristic=service?.getCharacteristic(UUID.fromString(BuildConfig.SMART_DECK_COMMAND_UUID));val event=service?.getCharacteristic(UUID.fromString(BuildConfig.SMART_DECK_EVENT_UUID));if(commandCharacteristic==null||event==null){mutableSnapshot.value=mutableSnapshot.value.copy(state=SmartDeckState.ERROR,errorCode="SERVICE_NOT_FOUND");g.disconnect();return};scope.launch{if(enableNotifications(g,event))send(SmartDeckCommand.GetStatus)else{mutableSnapshot.value=mutableSnapshot.value.copy(state=SmartDeckState.ERROR,errorCode="NOTIFY_SETUP_FAILED");g.disconnect()}}
        }
        @Deprecated("Deprecated in API 33") override fun onCharacteristicChanged(g:BluetoothGatt,c:BluetoothGattCharacteristic){consume(c.value)}
        override fun onCharacteristicChanged(g:BluetoothGatt,c:BluetoothGattCharacteristic,value:ByteArray){consume(value)}
        override fun onCharacteristicWrite(g:BluetoothGatt,c:BluetoothGattCharacteristic,status:Int){writeResult?.complete(status)}
        override fun onDescriptorWrite(g:BluetoothGatt,d:BluetoothGattDescriptor,status:Int){descriptorResult?.complete(status)}
    }

    private fun consume(bytes:ByteArray){SmartDeckCodec.decodeEvent(bytes.toString(Charsets.UTF_8))?.let{event->mutableEvents.tryEmit(event);mutableSnapshot.value=reduce(mutableSnapshot.value,event)}}
    override suspend fun scan():List<SmartDeckCandidate>{
        val scanner=adapter?.bluetoothLeScanner?:run{mutableSnapshot.value=SmartDeckSnapshot(SmartDeckState.ERROR,errorCode="BLE_UNAVAILABLE");return emptyList()}
        mutableSnapshot.value=mutableSnapshot.value.copy(state=SmartDeckState.SCANNING,errorCode=null)
        val found=linkedMapOf<String,SmartDeckCandidate>();val done=CompletableDeferred<Unit>()
        val cb=object:ScanCallback(){override fun onScanResult(type:Int,result:ScanResult){val name=result.device.name?:result.scanRecord?.deviceName?:return;if(name.contains("Smart Deck",true))found[result.device.address]=SmartDeckCandidate(result.device.address,name,result.rssi)};override fun onScanFailed(code:Int){mutableSnapshot.value=mutableSnapshot.value.copy(state=SmartDeckState.ERROR,errorCode="SCAN_$code");done.complete(Unit)}}
        scanner.startScan(listOf(android.bluetooth.le.ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(BuildConfig.SMART_DECK_SERVICE_UUID)).build()),android.bluetooth.le.ScanSettings.Builder().setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY).build(),cb)
        repeat(30){if(done.isCompleted)return@repeat;delay(100)};scanner.stopScan(cb);if(mutableSnapshot.value.state==SmartDeckState.SCANNING)mutableSnapshot.value=mutableSnapshot.value.copy(state=SmartDeckState.DISCONNECTED);return found.values.toList()
    }
    override suspend fun connect(deviceId:String){if(mutableSnapshot.value.connected&&mutableSnapshot.value.deviceId==deviceId)return;val device=runCatching{adapter?.getRemoteDevice(deviceId)}.getOrNull()?:return;mutableSnapshot.value=mutableSnapshot.value.copy(state=SmartDeckState.CONNECTING,errorCode=null);gatt?.close();gatt=device.connectGatt(context,false,callback,BluetoothDevice.TRANSPORT_LE)}
    override suspend fun disconnect(){runCatching{send(SmartDeckCommand.LedOff)};gatt?.disconnect();gatt?.close();gatt=null;commandCharacteristic=null;mutableSnapshot.value=SmartDeckSnapshot()}
    override suspend fun send(command:SmartDeckCommand){if(!write(command)){mutableSnapshot.value=mutableSnapshot.value.copy(state=SmartDeckState.ERROR,errorCode="WRITE_FAILED");throw IllegalStateException("WRITE_FAILED")};mutableSnapshot.value=when(command){is SmartDeckCommand.LedOn->mutableSnapshot.value.copy(state=SmartDeckState.LED_ON,ledOn=true);SmartDeckCommand.LedOff->mutableSnapshot.value.copy(state=SmartDeckState.LED_OFF,ledOn=false);SmartDeckCommand.Arm->mutableSnapshot.value.copy(state=SmartDeckState.ARMED,armed=true);SmartDeckCommand.Disarm->mutableSnapshot.value.copy(state=SmartDeckState.CONNECTED,armed=false);else->mutableSnapshot.value}}
    private suspend fun write(command:SmartDeckCommand)=operationMutex.withLock{val g=gatt?:return@withLock false;val c=commandCharacteristic?:return@withLock false;val bytes=SmartDeckCodec.encode(command).toByteArray();val pending=CompletableDeferred<Int>();writeResult=pending;val started=if(Build.VERSION.SDK_INT>=33)g.writeCharacteristic(c,bytes,BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)==BluetoothStatusCodes.SUCCESS else{@Suppress("DEPRECATION") c.value=bytes;@Suppress("DEPRECATION") g.writeCharacteristic(c)};if(!started){writeResult=null;return@withLock false};val status=withTimeoutOrNull(3000){pending.await()};writeResult=null;status==BluetoothGatt.GATT_SUCCESS}
    private suspend fun enableNotifications(g:BluetoothGatt,c:BluetoothGattCharacteristic)=operationMutex.withLock{if(!g.setCharacteristicNotification(c,true))return@withLock false;val d=c.getDescriptor(UUID.fromString(CLIENT_CONFIG_UUID))?:return@withLock false;val pending=CompletableDeferred<Int>();descriptorResult=pending;val started=if(Build.VERSION.SDK_INT>=33)g.writeDescriptor(d,BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)==BluetoothStatusCodes.SUCCESS else{@Suppress("DEPRECATION") d.value=BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;@Suppress("DEPRECATION") g.writeDescriptor(d)};if(!started){descriptorResult=null;return@withLock false};val status=withTimeoutOrNull(3000){pending.await()};descriptorResult=null;status==BluetoothGatt.GATT_SUCCESS}
    override fun emergencyClose(){runCatching{val g=gatt;val c=commandCharacteristic;if(g!=null&&c!=null){val bytes=SmartDeckCodec.encode(SmartDeckCommand.LedOff).toByteArray();if(Build.VERSION.SDK_INT>=33)g.writeCharacteristic(c,bytes,BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)else{@Suppress("DEPRECATION") c.value=bytes;@Suppress("DEPRECATION") g.writeCharacteristic(c)}}};runCatching{gatt?.disconnect()};runCatching{gatt?.close()};gatt=null;commandCharacteristic=null;mutableSnapshot.value=SmartDeckSnapshot()}
    private fun reduce(s:SmartDeckSnapshot,e:SmartDeckEvent)=when(e){is SmartDeckEvent.DeckOpen->s.copy(state=SmartDeckState.DECK_OPEN,deckOpen=true,battery=e.battery,lastEventAt=System.currentTimeMillis());is SmartDeckEvent.DeckClosed->s.copy(state=SmartDeckState.DECK_CLOSED,deckOpen=false,battery=e.battery,lastEventAt=System.currentTimeMillis());is SmartDeckEvent.LedState->s.copy(state=if(e.enabled)SmartDeckState.LED_ON else SmartDeckState.LED_OFF,ledOn=e.enabled,lastEventAt=System.currentTimeMillis());is SmartDeckEvent.DeviceStatus->s.copy(battery=e.battery,sensorHealthy=e.sensor,lastEventAt=System.currentTimeMillis());is SmartDeckEvent.Error->s.copy(state=SmartDeckState.ERROR,errorCode=e.code,lastEventAt=System.currentTimeMillis())}
    companion object{private const val CLIENT_CONFIG_UUID="00002902-0000-1000-8000-00805f9b34fb"}
}
