# 아이폰 앱 — Mac 없이 빌드하고 윈도우에서 설치

아이폰 앱은 Mac(Xcode)에서만 빌드돼요. 그래서 **GitHub Actions 의 Mac 서버**에서 서명 없이 빌드하고,
윈도우 PC 의 **Impactor**(오픈소스, MIT)로 내 Apple ID 서명을 붙여 USB 로 설치해요.

앱은 안드로이드처럼 서버 화면(`https://anam3.onrender.com`)을 그대로 열어요 → 웹을 배포하면 아이폰 앱에도 바로 반영돼요.

## 1. 빌드 (.ipa 받기)

- `.github/workflows/ios.yml` 이 `frontend/ios`, `capacitor.config.ts`, `package-lock.json` 이 바뀌면 자동으로 돌아요.
  손으로 돌리려면: GitHub › Actions › **iOS 앱** › Run workflow
- 끝나면 그 실행 화면 아래 Artifacts 의 `anam3-ios-ipa` 를 받아 압축을 풀면 `anam3-ios.ipa`
  - 명령줄: `gh run download <실행 번호> -n anam3-ios-ipa -D <폴더>`
- 공개 저장소라 Mac 서버 사용료는 없어요.

## 2. 윈도우 준비 (한 번만)

- iTunes (웹 설치판, `winget install Apple.iTunes`) — 아이폰 USB 드라이버
- Impactor (`winget install CLARATION.Impactor.GUI`)

## 3. 설치

1. 아이폰을 USB 로 PC 에 연결 → 아이폰에서 **이 컴퓨터 신뢰**
2. Impactor 실행 → Apple ID 로그인 (Apple 에 직접 로그인해요. 앱 전용 Apple ID 를 따로 만들어 써도 돼요)
3. `anam3-ios.ipa` 를 Impactor 에 끌어다 놓고 설치
4. 아이폰: **설정 › 개인정보 보호 및 보안 › 개발자 모드** 켜기 → 재시동 (iOS 16 이상, 처음 한 번)
5. 아이폰: **설정 › 일반 › VPN 및 기기 관리** › 내 Apple ID › 신뢰

## 무료 Apple ID 라서 생기는 제약

| 제약 | 내용 |
|---|---|
| 7일마다 다시 서명 | 7일이 지나면 앱이 안 열려요. Impactor 로 다시 설치하거나 자동 갱신을 켜요 (데이터는 남아요) |
| 앱 3개까지 | 무료 Apple ID 로 동시에 깔 수 있는 앱 수 |
| 푸시 알림 없음 | 아이폰 푸시(APNs)는 Apple 개발자 유료 가입(연 129,000원)이 있어야 해요 |
| 카카오 로그인 | 카카오톡 앱 대신 앱 안의 카카오 로그인 창(웹)으로 해요. 이메일 로그인도 돼요 |

나중에 앱스토어에 내려면 Apple 개발자 유료 가입 → TestFlight 로 바꾸면 돼요 (빌드는 같은 Mac 서버에서 서명만 붙여요).
