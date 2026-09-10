# Smart Deck 사전 분석

## 저장소와 기술 스택

- 단일 저장소에 Android 앱(`app`), FastAPI 서버(`backend`, `server`), React/TypeScript 공개 웹(`reveal-web`), Firebase 및 인프라 설정이 공존한다.
- Android 앱은 Kotlin, Jetpack Compose, Material 3, MVVM(`CardCallerViewModel`), Coroutines, Preferences DataStore, Room을 사용한다. Flutter와 Java 앱 소스는 없다.
- 최소 Android API는 26, target/compile SDK는 35이며 Java/Kotlin 타깃은 17이다.

## 기존 카드·공연 구조

- `PlayingCard`가 52장과 빨강/검정 조커를 정의하고 `id`, `shortCode`, 표시 형식을 통일한다.
- 직접 선택, 제스처, 볼륨 조합, Firebase 원격 입력은 `CardCallerViewModel.select`로 모이며 마지막 선택 카드를 공유한다.
- 앱 내부 모의 수신 화면은 `ThemedIncoming`, 단계형 통화는 `AiCallScreen`, 공개는 `BlueFanReveal`, 결과는 `PerformanceResult`가 담당한다. 실제 전화/연락처 API는 사용하지 않는다.
- 공연 흐름은 `PerformanceStateMachine`이 생성부터 완료·취소·실패까지 검사한다.
- 설정은 `AppSettings`와 `SettingsRepository`가 Preferences DataStore에 저장한다.

## 변경 전 상태와 제약

- Smart Deck 메뉴, BLE 권한, GATT 통신, 장치 상태 및 설정 모델은 존재하지 않는다.
- 현재 `MainActivity`의 볼륨 키는 비밀 카드 입력 전용이므로 Smart Deck 비밀 리모컨과의 우선순위 규칙이 필요하다.
- Android 12 이상 BLE 런타임 권한은 Smart Deck 화면 진입 시에만 요청해야 한다.
- 하드웨어 없이 CI에서 검증할 수 있도록 실제 BLE 구현과 동일 인터페이스의 Fake 장치가 필요하다.
- 실제 ESP32 및 배터리 분압 회로는 이 환경에 연결되어 있지 않으므로 앱/펌웨어 컴파일과 Fake 흐름은 자동 검증하고 실제 전압·센서 방향·전파 범위는 현장에서 검증해야 한다.

## 구현 방침

1. 순수 Kotlin 프로토콜·상태 머신·카드 효과 매퍼·디바운서·명령 큐를 먼저 작성한다.
2. `SmartDeckDevice` 인터페이스 아래 Fake와 Android BLE GATT 구현을 둔다.
3. DataStore에 마지막 장치, 자동 연결, 색상/밝기/효과/지연/자동 종료/센서 반전/비밀 조작 설정을 저장한다.
4. 대시보드·설정·리허설 화면을 기존 Compose 디자인에 추가하고 공연 모드에서는 기술 오류를 일반 상태 문구로 치환한다.
5. 카드 공개 완료 이벤트를 피날레 큐와 연결한다.
6. PlatformIO 기반 독립 펌웨어와 중앙화된 `config.h`를 제공한다.
7. 단위/UI 테스트, Android 빌드, PlatformIO 정적 검증 및 문서 정합성 검토를 수행한다.
