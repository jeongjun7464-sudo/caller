# Card Caller

마술사가 고른 카드를 앱 안의 엔터테인먼트용 수신 화면으로 연출하는 Android 앱입니다. 실제 전화 발신, 통화 기록, 연락처 기능을 사용하지 않습니다.

## 기능

- 52장 직접 선택, 4열 카드 그리드
- 화면을 보지 않는 스와이프(무늬) + 탭(숫자) 입력과 햅틱
- 즉시/지연/화면 뒤집기/포그라운드 볼륨 버튼 트리거
- 진동·벨소리를 포함한 앱 내부 전체화면 수신 연출 및 카드 공개
- DataStore 공연 설정
- Firebase 익명 인증 + Realtime Database 기반 두 기기 원격 전송과 중복 소비 방지
- FastAPI 서버를 통한 실제 Instagram Story/피드 카드 공개(공식 Content Publishing API)
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

## Instagram 공개 서버

Android 앱은 서버에 JSON 본문 `{ "cardId": "HEARTS_ACE", "publishType": "STORY" }`만 전송합니다. 재시도 중복 방지를 위한 UUID는 `Idempotency-Key` 헤더로 보내며 Instagram 토큰, Meta 앱 비밀키, 저장소 키는 앱에 포함하지 않습니다.

서버 처리 순서는 다음과 같습니다.

1. `requestId`를 SQLite에 원자적으로 등록합니다.
2. Story는 1080×1920, 피드는 1080×1350 JPEG 이미지를 만듭니다.
3. 운영 모드에서는 S3 호환 저장소에 업로드하여 공개 HTTPS `image_url`을 만듭니다.
4. `/{ig-user-id}/media`로 컨테이너를 생성합니다. Story는 `media_type=STORIES`를 지정합니다.
5. `/{container-id}?fields=status_code,status`를 `FINISHED`까지 확인합니다.
6. `/{ig-user-id}/media_publish`를 호출하고 게시 미디어의 `permalink,timestamp`를 조회합니다.
7. `mediaId`, `permalink`, `createdAt`을 SQLite에 저장합니다.

Meta 설정 전에는 기본 `INSTAGRAM_MODE=mock`이 실제와 같은 전체 흐름을 실행합니다.

### FastAPI 실행

Python 3.11 이상에서:

```bash
cd server
python -m venv .venv
# Windows: .venv\Scripts\activate
# macOS/Linux: source .venv/bin/activate
pip install -r requirements.txt
copy .env.example .env  # macOS/Linux: cp .env.example .env
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
pytest -q
```

에뮬레이터의 기본 서버 주소는 `http://10.0.2.2:8000`입니다. 다른 주소는 빌드할 때 지정합니다.

```bash
./gradlew assembleDebug -PCARD_CALLER_API_BASE_URL=https://api.example.com
```

운영 APK와 서버 사이에는 HTTPS를 사용하세요. Manifest의 cleartext 허용은 로컬 에뮬레이터 개발용이며 배포 변형에서는 비활성화하는 것을 권장합니다.

### Meta 및 Instagram 준비

이 프로젝트는 비밀번호 입력, Instagram 화면 자동 조작, 크롤링 또는 비공식 로그인 API를 사용하지 않습니다. Meta의 공식 **Instagram API with Instagram Login**과 Content Publishing API만 사용합니다.

1. Instagram 앱에서 계정을 Professional(비즈니스 또는 크리에이터) 계정으로 전환합니다.
2. [Meta for Developers](https://developers.facebook.com/)에서 Business 유형 앱을 만들고 Instagram 제품/API를 추가합니다.
3. Instagram Login 설정에 OAuth Redirect URI, Deauthorize callback, Data deletion callback을 등록합니다.
4. 앱 개발 중에는 대상 Instagram 계정을 앱 역할/테스터로 추가하고 계정에서 초대를 수락합니다.
5. Instagram Login OAuth에서 `instagram_business_basic`과 `instagram_business_content_publish` 권한을 요청합니다. 기존 Facebook Login 방식에서는 앱 구성에 따라 `instagram_basic`, `instagram_content_publish`, Page 관련 권한 및 연결된 Facebook Page가 필요할 수 있으므로 한 로그인 방식을 섞지 마세요.
6. 외부 일반 계정에 배포하려면 Meta App Review에서 필요한 권한의 Advanced Access를 승인받고 앱을 Live 모드로 전환합니다.
7. 서버에서 OAuth callback을 구현하거나 Meta가 제공하는 테스트 도구로 장기 토큰을 발급한 뒤 서버의 비밀 저장소에만 보관합니다. Android 앱이나 Git 저장소에 토큰을 넣지 마세요.
8. 공개 읽기가 가능한 S3/Cloudflare R2 등 HTTPS 저장소를 준비합니다. Meta 서버가 인증 쿠키 없이 이미지 URL을 다운로드할 수 있어야 합니다.
9. `.env.example`을 복사해 아래 운영 값을 설정합니다.

```dotenv
INSTAGRAM_MODE=live
INSTAGRAM_ACCESS_TOKEN=...
INSTAGRAM_USER_ID=...
INSTAGRAM_USERNAME=your_professional_account
INSTAGRAM_PROFILE_URL=https://www.instagram.com/your_professional_account/
META_GRAPH_VERSION=v23.0
APP_SECRET=...
PUBLIC_MEDIA_BASE_URL=https://cdn.example.com/card-caller
S3_BUCKET=...
S3_REGION=...
S3_ENDPOINT_URL=https://...   # AWS S3는 비워도 됨
S3_ACCESS_KEY_ID=...
S3_SECRET_ACCESS_KEY=...
```

토큰 수명과 권한은 Meta Access Token Debugger로 확인하고, 토큰 갱신·폐기 정책을 운영 환경에 추가하세요. API 버전은 Meta 지원 일정에 맞춰 검증 후 올려야 합니다.

### 모의 API 확인

`.env`에서 `INSTAGRAM_MODE=mock`을 유지하면 Meta 계정이나 S3 없이 테스트할 수 있습니다.

```bash
curl -X POST http://localhost:8000/api/v1/publish \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: demo-request-1" \
  -d '{"cardId":"HEARTS_ACE","publishType":"STORY"}'
```

같은 멱등 키를 다시 보내면 기존 결과를 반환하여 중복 게시하지 않습니다.

## 알려진 제한사항과 향후 개선

- 사용자 지정 지연 시각/발신자 편집 UI, 사용자 지정 시각 테마 편집기는 다음 버전에서 확장할 수 있습니다.
- 근접 센서 대신 가속도계의 뒤집기 판정을 사용합니다.
- 원격 방의 서버측 강제 만료/삭제는 Cloud Functions가 필요합니다.
- UI 계측 테스트는 에뮬레이터 또는 실제 기기가 필요합니다.
- Story 게시물은 일반 게시물처럼 영구 permalink가 제공되지 않을 수 있으므로 앱의 `Instagram에서 확인`은 연결된 프로필을 엽니다.
- 서버의 로컬 미디어 제공은 모의 개발 전용입니다. 실제 Meta 게시에는 공개 HTTPS 객체 저장소가 필요합니다.
- 향후 암호화된 참여 토큰, 연결 상태 presence, 오디오 선택, 스크린샷 기반 회귀 테스트를 추가할 수 있습니다.
