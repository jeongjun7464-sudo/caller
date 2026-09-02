# Card Caller

마술사가 고른 카드를 앱 안의 엔터테인먼트용 수신 화면으로 연출하는 Android 앱입니다. 실제 전화 발신, 통화 기록, 연락처 기능을 사용하지 않습니다.

## 기능

- 52장 직접 선택, 4열 카드 그리드
- 화면을 보지 않는 스와이프(무늬) + 탭(숫자) 입력과 햅틱
- 즉시/지연/화면 뒤집기/포그라운드 볼륨 버튼 트리거
- 진동·벨소리를 포함한 앱 내부 전체화면 수신 연출 및 카드 공개
- DataStore 공연 설정
- Firebase 익명 인증 + Realtime Database 기반 두 기기 원격 전송과 중복 소비 방지
- Firebase가 없어도 로컬 기능 정상 동작

## 기술과 구조

Kotlin, Jetpack Compose, Material 3, MVVM, Coroutines, DataStore, Firebase Authentication/Realtime Database를 사용합니다. 최소 API 26, Target/Compile SDK 35입니다. 소스는 작은 프로젝트에서 탐색하기 쉽도록 모델, 저장소, 입력·센서, ViewModel, UI로 나뉘며 요구된 각 책임(`PlayingCard`, `SettingsRepository`, `GestureInputManager`, 센서/원격/수신 제어)은 독립 클래스 또는 ViewModel 동작으로 구현되어 있습니다.

## Android Studio 실행

1. Android Studio에서 이 폴더를 엽니다.
2. SDK Manager에서 Android SDK Platform 35와 Build Tools를 설치합니다.
3. Gradle JDK를 Android Studio 내장 JDK 17 이상으로 지정합니다.
4. Sync 후 `app` 구성을 API 26 이상 기기에서 실행합니다.

`google-services.json`이 없으면 Google Services 플러그인을 적용하지 않습니다. 직접 선택과 제스처 공연은 그대로 사용할 수 있습니다.

## Firebase 연결 (처음 사용자)

1. Firebase Console에서 프로젝트를 만들고 Android 앱 `com.cardcaller.magic`을 추가합니다.
2. 내려받은 `google-services.json`을 `app/google-services.json`에 놓습니다. 이 파일은 `.gitignore`에 포함됩니다.
3. Authentication → Sign-in method에서 **Anonymous** 로그인을 켭니다.
4. Realtime Database를 만들고 원하는 리전을 선택합니다.
5. Database의 Rules 탭에 [`firebase-database-rules.json`](firebase-database-rules.json)을 붙여 넣고 게시합니다.
6. 앱을 다시 빌드합니다. Kotlin 소스에 URL이나 API 키를 직접 넣지 마세요.

예시 규칙은 인증 사용자, 6자리 방, 24시간 이내 방만 읽게 하는 시작점입니다. 실제 배포에서는 참여 토큰을 추가하고 Cloud Functions 또는 서버 작업으로 만료 방을 삭제하는 것을 권장합니다. 현재 클라이언트 생성 시각은 신뢰 경계가 아니므로 높은 보안이 필요한 환경에는 서버 검증이 필요합니다.

## 두 휴대폰 연결

1. 두 기기에 같은 Firebase 연결 빌드를 설치합니다.
2. 공연폰: 원격 카드 전송 → **6자리 방 만들기**.
3. 리모컨폰: 표시된 코드 입력 → **방 연결**.
4. 리모컨폰에서 카드 선택 및 전송.
5. 공연폰이 새 `messageId`를 한 번만 소비하고 설정된 지연 뒤 수신 화면을 엽니다.

## 제스처

- 위 ♠ / 오른쪽 ♥ / 아래 ♣ / 왼쪽 ♦
- 탭 1회=A, 2~10회=2~10, 11=J, 12=Q, 13=K
- 길게 누르면 초기화됩니다. 민감도와 결과 숨김은 공연 설정에서 바꿉니다.

## 테스트와 APK

Windows: `gradlew.bat test`, `gradlew.bat lint`, `gradlew.bat assembleDebug`  
macOS/Linux: `./gradlew test`, `./gradlew lint`, `./gradlew assembleDebug`

APK는 `app/build/outputs/apk/debug/app-debug.apk`에 생성됩니다.

## 알려진 제한사항과 향후 개선

- 사용자 지정 지연 시각/발신자 편집 UI, 사용자 지정 시각 테마 편집기는 다음 버전에서 확장할 수 있습니다.
- 근접 센서 대신 가속도계의 뒤집기 판정을 사용합니다.
- 원격 방의 서버측 강제 만료/삭제는 Cloud Functions가 필요합니다.
- UI 계측 테스트는 에뮬레이터 또는 실제 기기가 필요합니다.
- 향후 암호화된 참여 토큰, 연결 상태 presence, 오디오 선택, 스크린샷 기반 회귀 테스트를 추가할 수 있습니다.
