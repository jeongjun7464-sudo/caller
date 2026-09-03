# Firebase 설정

Android 앱 `com.cardcaller.magic`을 등록하고 익명 인증, Realtime Database, Cloud Messaging을 활성화합니다. `google-services.json`은 `app/`에 로컬로만 저장하며 Git에 커밋하지 않습니다. `database.rules.json`을 배포하고 공연폰이 참가 UID를 `members`에 등록한 뒤에만 해당 방 접근을 허용합니다. FCM 서버 자격증명은 백엔드 환경변수 또는 Secret Manager에만 둡니다.

