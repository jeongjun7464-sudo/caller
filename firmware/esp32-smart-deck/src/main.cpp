#include <Arduino.h>
#include <ArduinoJson.h>
#include <Adafruit_NeoPixel.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include "config.h"

Adafruit_NeoPixel pixel(PIXEL_COUNT, LED_PIN, NEO_GRB + NEO_KHZ800);
BLECharacteristic *eventCharacteristic = nullptr;
bool connected = false, armed = true, ledEnabled = false;
bool stableOpen = false, lastRawOpen = false;
uint8_t brightness = 80;
uint32_t colorValue = 0x006DFF;
unsigned long rawChangedAt = 0, lastActivityAt = 0, ledOnAt = 0;
unsigned long maxOnMs = DEFAULT_MAX_ON_MS;

int batteryPercent() {
#if ENABLE_BATTERY_ADC
  float voltage = analogReadMilliVolts(BATTERY_ADC_PIN) / 1000.0f * BATTERY_DIVIDER_RATIO;
  return constrain((int)((voltage - BATTERY_EMPTY_V) * 100.0f / (BATTERY_FULL_V - BATTERY_EMPTY_V)), 0, 100);
#else
  return 100;
#endif
}

void ledOff() { pixel.clear(); pixel.show(); ledEnabled = false; }
void ledOn(uint32_t color, int requestedBrightness) {
  int safeBrightness = batteryPercent() < LOW_BATTERY_PERCENT ? min(requestedBrightness, LOW_BATTERY_MAX_BRIGHTNESS) : requestedBrightness;
  brightness = constrain(safeBrightness, 1, 100); colorValue = color;
  pixel.setBrightness(map(brightness, 1, 100, 3, 255)); pixel.setPixelColor(0, colorValue); pixel.show();
  ledEnabled = true; ledOnAt = millis(); lastActivityAt = millis();
}

void notifyEvent(const char *event, bool includeSensor = false) {
  if (!connected || eventCharacteristic == nullptr) return;
  StaticJsonDocument<160> doc; doc["event"] = event; doc["battery"] = batteryPercent();
  if (strcmp(event, "LED_STATE") == 0) doc["enabled"] = ledEnabled;
  if (includeSensor) doc["sensor"] = true;
  String output; serializeJson(doc, output); eventCharacteristic->setValue(output.c_str()); eventCharacteristic->notify();
  Serial.println(output);
}

uint32_t parseColor(const char *hex) { return (hex && hex[0] == '#') ? strtoul(hex + 1, nullptr, 16) : 0x006DFF; }
void runEffect(const char *type, unsigned long durationMs, int flashes) {
  durationMs = constrain(durationMs, 100UL, HARD_MAX_ON_MS);
  if (strcmp(type, "RAINBOW") == 0) {
    for (int i=0;i<40;i++){pixel.setPixelColor(0,pixel.gamma32(pixel.ColorHSV(i*65535/40)));pixel.show();delay(durationMs/40);}
  } else if (strcmp(type, "BLINK") == 0 || strcmp(type, "COURT") == 0) {
    int count=constrain(flashes,1,13);for(int i=0;i<count;i++){ledOn(colorValue,brightness);delay(min(180UL,durationMs/(count*2)));ledOff();delay(min(180UL,durationMs/(count*2)));}
  } else if (strcmp(type, "FADE") == 0) {
    for(int i=0;i<=100;i+=4){pixel.setBrightness(map(i,0,100,0,map(brightness,1,100,3,255)));pixel.setPixelColor(0,colorValue);pixel.show();delay(durationMs/25);}
  }
  ledOn(colorValue,brightness);
}

class ServerCallbacks:public BLEServerCallbacks{void onConnect(BLEServer*) override{connected=true;lastActivityAt=millis();}void onDisconnect(BLEServer *server) override{connected=false;armed=true;ledOff();server->getAdvertising()->start();}};
class CommandCallbacks:public BLECharacteristicCallbacks{
  void onWrite(BLECharacteristic *characteristic) override {
    String input=String(characteristic->getValue().c_str());StaticJsonDocument<256> doc;
    if(deserializeJson(doc,input)){notifyError();return;}const char *command=doc["command"]|"";lastActivityAt=millis();
    if(strcmp(command,"LED_ON")==0){ledOn(parseColor(doc["color"]|"#006DFF"),doc["brightness"]|80);notifyEvent("LED_STATE");}
    else if(strcmp(command,"LED_OFF")==0){ledOff();notifyEvent("LED_STATE");}
    else if(strcmp(command,"EFFECT")==0){runEffect(doc["type"]|"FADE",doc["durationMs"]|1500,doc["flashes"]|1);notifyEvent("LED_STATE");}
    else if(strcmp(command,"ARM")==0)armed=true;else if(strcmp(command,"DISARM")==0){armed=false;ledOff();}
    else if(strcmp(command,"SET_MAX_ON")==0)maxOnMs=constrain((unsigned long)(doc["seconds"]|30)*1000UL,1000UL,HARD_MAX_ON_MS);
    else if(strcmp(command,"GET_STATUS")==0)notifyEvent("DEVICE_STATUS",true);else notifyError();
  }
  void notifyError(){if(!connected)return;StaticJsonDocument<100> doc;doc["event"]="ERROR";doc["code"]="BAD_COMMAND";String out;serializeJson(doc,out);eventCharacteristic->setValue(out.c_str());eventCharacteristic->notify();}
};

void setupBle(){BLEDevice::init(DEVICE_NAME);BLEServer *server=BLEDevice::createServer();server->setCallbacks(new ServerCallbacks());BLEService *service=server->createService(SERVICE_UUID);auto *command=service->createCharacteristic(COMMAND_UUID,BLECharacteristic::PROPERTY_WRITE);command->setCallbacks(new CommandCallbacks());eventCharacteristic=service->createCharacteristic(EVENT_UUID,BLECharacteristic::PROPERTY_NOTIFY|BLECharacteristic::PROPERTY_READ);eventCharacteristic->addDescriptor(new BLE2902());service->start();BLEAdvertising *advertising=server->getAdvertising();advertising->addServiceUUID(SERVICE_UUID);advertising->start();}

void setup(){Serial.begin(115200);pinMode(SENSOR_PIN,INPUT_PULLUP);pinMode(TEST_BUTTON_PIN,INPUT_PULLUP);pixel.begin();ledOff();lastRawOpen=digitalRead(SENSOR_PIN)==SENSOR_OPEN_LEVEL;stableOpen=lastRawOpen;lastActivityAt=millis();setupBle();Serial.println("Smart Deck ready");}

void loop(){
  bool rawOpen=digitalRead(SENSOR_PIN)==SENSOR_OPEN_LEVEL;if(rawOpen!=lastRawOpen){lastRawOpen=rawOpen;rawChangedAt=millis();}
  if(rawOpen!=stableOpen&&millis()-rawChangedAt>=SENSOR_DEBOUNCE_MS){stableOpen=rawOpen;lastActivityAt=millis();if(stableOpen){notifyEvent("DECK_OPEN");if(armed&&!connected)ledOn(0x006DFF,60);}else{notifyEvent("DECK_CLOSED");ledOff();}}
  if(digitalRead(TEST_BUTTON_PIN)==LOW){ledOn(0x006DFF,60);delay(250);ledOff();lastActivityAt=millis();}
  if(ledEnabled&&millis()-ledOnAt>=min(maxOnMs,HARD_MAX_ON_MS))ledOff();
  if(!connected&&!stableOpen&&millis()-lastActivityAt>=IDLE_SLEEP_MS){ledOff();Serial.println("Deep sleep");Serial.flush();esp_sleep_enable_ext0_wakeup((gpio_num_t)SENSOR_PIN,SENSOR_OPEN_LEVEL);esp_deep_sleep_start();}
  delay(10);
}
