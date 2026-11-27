# 안드로이드 앱 출시 (Google Play)

앱은 서버 화면(https://anam3.onrender.com)을 그대로 여는 껍데기예요 (Capacitor, `frontend/android`).
웹을 배포하면 앱에도 바로 반영돼요. 앱을 다시 올려야 하는 건 앱 기능(플러그인·권한·아이콘)을 바꿀 때만이에요.

들어간 것: 카카오톡 로그인, 푸시 알림(참여·나감, 성사, 모집 마감 30분 전, 시간·장소 변경·취소, 새 공지, 새 연합 3교시 —
내 정보에서 종류별로 끄기), 공유(안드로이드 공유 창)와 공유 링크로 앱 열기, 뒤로가기, 상단바, 아이콘·시작 화면, 연결 안 될 때 안내 화면.

## 1. 키 준비 (결제 전에 해도 돼요)

| 무엇 | 어디서 | 넣는 곳 |
|---|---|---|
| `google-services.json` | Firebase 콘솔 › 프로젝트 만들기 › Android 앱 추가 (패키지 `kr.anam3.app`) | `frontend/android/app/` (저장소에 안 올라감) |
| Firebase 서비스 계정 키 (JSON) | Firebase › 프로젝트 설정 › 서비스 계정 › 새 비공개 키 | Render 환경변수 `FIREBASE_SERVICE_ACCOUNT` (JSON 내용 그대로) |
| 카카오 네이티브 앱 키 | 카카오 디벨로퍼스 › 앱 › 앱 키 | `frontend/android/local.properties` 에 `kakao.nativeAppKey=…` (저장소에 안 올라감) |
| 카카오 앱 ID (숫자) | 카카오 디벨로퍼스 › 앱 › 앱 키 화면 위쪽 | Render 환경변수 `KAKAO_APP_ID` |
| 카카오 Android 플랫폼 | 카카오 디벨로퍼스 › 앱 › 플랫폼 › Android | 패키지명 `kr.anam3.app`, 키 해시 (아래) |
| 심사용 계정 | 아무 이메일·비밀번호 (8자 이상) | Render 환경변수 `REVIEW_EMAIL`, `REVIEW_PASSWORD` |
| 앱 서명 인증서 SHA-256 | Play Console › 앱 무결성 › 앱 서명 (앱 서명 키 · 업로드 키 둘 다) | Render 환경변수 `ANDROID_CERT_SHA256` (쉼표로 여러 개) |

카카오 키 해시는 서명 키마다 하나씩 넣어요:
- 이 PC 디버그 키 (직접 설치하는 APK): `mJ4R2XhcsfzbWuZcukxdHpA4DDk=` (2026-11-27 새로 만든 `~/.android/debug.keystore`)
  - 카카오 네이티브 앱 키 없이 빌드한 APK 는 카카오 로그인 창(웹)으로 로그인해서 키 해시가 없어도 돼요
- 업로드 키 · Play 앱 서명 키: Play Console › 앱 무결성에 나오는 SHA-1 을 base64 로 바꾼 값
  (`python -c "import base64;print(base64.b64encode(bytes.fromhex('SHA1을 콜론 빼고')).decode())"`)

## 2. 업로드 키 만들기 (한 번만 · 잃어버리지 않게 따로 보관)

```
"C:\Program Files\Java\jdk-21.0.10\bin\keytool" -genkeypair -v -keystore C:\Users\<나>\anam3-keys\anam3-upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
```

`frontend/android/keystore.properties.example` 을 `keystore.properties` 로 복사해서 경로와 비밀번호를 채워요 (저장소에 안 올라감).
Play 앱 서명을 쓰니까 업로드 키를 잃어버려도 Play Console 에서 새 업로드 키로 바꿀 수 있지만 며칠 걸려요.

## 3. 빌드

JDK 21, Android SDK 가 필요해요.

```
cd frontend
npm ci
npx cap sync android
cd android
./gradlew assembleDebug    # 폰에 직접 까는 APK → app/build/outputs/apk/debug/app-debug.apk
./gradlew bundleRelease    # Play Console 에 올리는 파일 → app/build/outputs/bundle/release/app-release.aab
```

올릴 때마다 `frontend/android/app/build.gradle` 의 `versionCode` 를 1씩 올리고, `versionName` 은 아이폰 앱과 같은 버전으로 맞춰요 ([app-versions.md](app-versions.md)).
폰에 직접 까는 APK 는 `anam3-android-v1.2.0.apk` 처럼 버전을 넣은 이름으로 보관해요.

## 4. Play Console (결제 뒤)

1. 개발자 계정 (개인, $25) → 신원 확인
2. 앱 만들기: 안암3교시 · 한국어 · 앱 · 무료
3. 스토어 등록정보: 아이콘 `docs/play-store/icon-512.png`, 그래픽 이미지 `docs/play-store/feature-graphic-1024x500.png`, 휴대전화 스크린샷 2장 이상
4. 앱 콘텐츠
   - 개인정보처리방침: `https://anam3.onrender.com/privacy`
   - 앱 액세스: 로그인 필요 → 심사용 계정 안내 ("첫 화면 › 이메일로 로그인 · 가입 › REVIEW_EMAIL / REVIEW_PASSWORD")
   - 계정 삭제 URL: `https://anam3.onrender.com/privacy#delete-account`
   - 광고 없음 · 타겟 연령 18세 이상 · 콘텐츠 등급 설문 (사용자끼리 소통, 술 언급)
   - 데이터 보안: 이메일 주소·이름(계정 관리, 앱 기능), 사용자 ID(카카오 회원번호), 기기 ID(알림 토큰, 앱 기능), 사진(톡방·추억에 올린 사진, 앱 기능) — 전송 중 암호화, 삭제 요청 가능, 제3자 공유 없음
     - 위치(대략적인 위치·정확한 위치): **수집하지 않음**으로 답한다. 3교시 목록 "가까운 순"에서 폰 안에서만 거리를 계산하고 서버로 보내지 않는다 (Google 기준: 기기 밖으로 나가지 않는 데이터는 수집이 아님). 앱 권한에는 위치가 보이므로, 스토어 설명이나 심사 메모에 "가까운 순 정렬에만 쓰고 서버로 보내지 않아요"를 적어 둔다
     - 3교시 장소의 좌표는 원우가 장소 검색에서 고른 가게 위치(내 위치 아님)라 위치 데이터가 아니다
5. 테스트 › 비공개 테스트 → 테스터 구글 계정 이메일 12명 이상 → AAB 업로드 → 검토 제출
   - 테스터는 참여 링크에서 "테스터 되기" → Play 스토어에서 설치
   - **12명 이상이 14일 동안 계속 참여**해야 정식 출시를 신청할 수 있어요
6. 앱 무결성 › 앱 서명의 SHA-256 → `ANDROID_CERT_SHA256`, SHA-1 → 카카오 키 해시

## 5. 폰에서 확인할 것

에뮬레이터를 못 돌려서(이 PC 는 Windows 하이퍼바이저 플랫폼이 꺼져 있음) 아래는 폰에 설치해서 확인해야 해요.
- 앱이 켜지고 첫 화면이 뜨는지, 연결이 끊기면 "서버에 연결하지 못했어요"가 뜨는지
- 카카오톡으로 로그인 (네이티브 앱 키 · 키 해시 · `KAKAO_APP_ID` 를 넣은 뒤)
- 알림 권한 묻기 → 다른 계정으로 내 3교시에 참여해 보기 → 알림이 오고 누르면 그 3교시가 열리는지 (`google-services.json` · `FIREBASE_SERVICE_ACCOUNT` 를 넣은 뒤)
- 3교시 공유 → 카카오톡에서 링크 누르면 앱으로 열리는지 (`ANDROID_CERT_SHA256` 을 넣은 뒤)
- 뒤로가기 · 내 정보 › 알림 켜고 끄기
