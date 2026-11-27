# 배포 (Render)

화면과 API를 Docker 이미지 하나로 빌드해요 ([`Dockerfile`](../Dockerfile)).
Render 서비스는 [`render.yaml`](../render.yaml)로 만들어지고, `main`에 합쳐지면 자동으로 배포돼요.

## 환경변수

| 이름 | 내용 |
|---|---|
| `DATABASE_URL` | Neon PostgreSQL 연결 문자열 (`postgresql://…`) |
| `JWT_SECRET` | 로그인 토큰 서명 키 (Render가 자동 생성) |
| `OWNER_EMAIL` | 대학원장(최고 관리자)의 학교 이메일. 이 이메일로 인증한 계정이 대학원장이 돼요 |
| `KAKAO_REST_API_KEY` / `KAKAO_CLIENT_SECRET` | 카카오 디벨로퍼스 > 앱 > 플랫폼 키 > REST API 키. 장소 검색도 같은 키를 써요 (제품 설정 > 카카오맵 사용 설정 ON) |
| `SPRING_MAIL_HOST` / `SPRING_MAIL_PORT` / `SPRING_MAIL_USERNAME` / `SPRING_MAIL_PASSWORD` | 인증 메일 SMTP (예: Gmail `smtp.gmail.com` · `587` · 보내는 주소 · 앱 비밀번호) |
| `MAIL_FROM` | (선택) 보내는 사람 주소. 비우면 `SPRING_MAIL_USERNAME` |
| `SUPPORT_EMAIL` | (선택) 약관·내 정보 화면의 문의 이메일. 비우면 "교수·대학원장에게 문의"로 안내 |
| `KAKAO_APP_ID` | (앱) 카카오 앱 ID(숫자). 안드로이드 앱의 카카오톡 로그인에 필요 |
| `FIREBASE_SERVICE_ACCOUNT` | (앱) Firebase 서비스 계정 키 JSON. 없으면 앱 푸시를 보내지 않고 로그만 남겨요 |
| `REVIEW_EMAIL` / `REVIEW_PASSWORD` | (앱) 스토어 심사용 계정. 둘 다 넣으면 서버가 시작할 때 만들어요 |
| `R2_ACCOUNT_ID` / `R2_ACCESS_KEY_ID` / `R2_SECRET_ACCESS_KEY` / `R2_BUCKET` | 톡방·추억 사진 저장소 (Cloudflare R2). 넷 다 넣어야 써요. 버킷은 비공개, CORS에 사이트 주소의 PUT·GET 허용, 수명 규칙으로 `chat/` 아래를 30일 뒤 삭제 |
| `SCHOOL_TEST_PASSWORD` | (선택) 8자 이상이면 학교마다 시험 계정(`ku@test.anam3` 등)을 만들어요. 이메일 로그인으로 들어가요 |
| `ANDROID_CERT_SHA256` | (앱) 앱 서명 인증서 SHA-256 (쉼표로 여러 개). 공유 링크를 앱으로 열 때 필요 |

Render 무료 플랜은 SMTP 포트(25·465·587)를 막아서, 메일을 보내려면 유료 인스턴스(Starter)가 필요해요.

## 앱

앱(안드로이드·아이폰)은 서버 화면을 그대로 열어서, 웹을 배포하면 앱에도 바로 반영돼요.
앱 기능(네이티브 플러그인)을 바꿨을 때만 앱을 새로 빌드해서 올려요.

- 안드로이드: [android-release.md](android-release.md)
- 아이폰: [ios-sideload.md](ios-sideload.md)
