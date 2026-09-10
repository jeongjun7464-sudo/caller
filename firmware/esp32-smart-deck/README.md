# ESP32 Smart Deck firmware

PlatformIO 프로젝트입니다. 핀과 BLE UUID는 `include/config.h`에서만 변경합니다.

```bash
pio run
pio run --target upload
pio device monitor
```

기본 배선: WS2812B DATA→GPIO18(220Ω 직렬 저항), 홀/리드 센서→GPIO33와 GND, 테스트 버튼→GPIO32와 GND, 모든 GND 공통. WS2812B 전원에는 제조사 권장 디커플링을 사용하세요. 배터리를 ESP32 또는 LED 핀에 직접 연결하지 말고 보호·충전·정전압 회로를 사용해야 합니다.
