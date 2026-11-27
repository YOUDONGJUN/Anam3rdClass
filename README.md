# 안암3교시

[![CI](https://github.com/YOUDONGJUN/Anam3rdClass/actions/workflows/ci.yml/badge.svg)](https://github.com/YOUDONGJUN/Anam3rdClass/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

> 수업 끝나고, 3교시는 여기서.

IT연합회(고려대·연세대·성균관대·서강대·한양대) 대학원 원우가 수업 끝나고 함께할 번개 모임 **3교시**를 열고 참여하는 서비스예요.
웹, 안드로이드, 아이폰에서 쓸 수 있어요.

| 열린 3교시 | 성사! | 3교시 상세 |
| :---: | :---: | :---: |
| <img src="docs/images/mobile-feed.png" width="240" /> | <img src="docs/images/mobile-confirmed.png" width="240" /> | <img src="docs/images/desktop-detail.png" width="420" /> |

## 주요 기능

- **3교시** — 누구나 열고 참여해요. 최소 인원이 모이면 성사되고, 그때부터 서로 실명이 보여요
- **톡방** — 함께하는 원우끼리 실시간으로 글 · 사진 · KU 호랑이 이모티콘을 주고받고, 읽음 숫자로 확인해요
- **추억 · 정산** — 모였던 3교시에 사진을 남기고, 1인 금액과 송금 버튼으로 나눠 내요
- **우리 학교 · 연합** — 학교 이메일로 인증한 원우만, 우리 학교 또는 다섯 학교 함께
- **알림 · 랭킹 · 신고와 제재** — 새 3교시와 톡 알림, 방장왕 · 출석왕, 교수 · 대학원장의 관리 화면

무엇을 왜 그렇게 정했는지는 [기획 히스토리](docs/planning/README.md)에 남겨요.

## 기술 스택

| 영역 | 사용 |
|---|---|
| 백엔드 | Java 25, Spring Boot 4, Spring Security (JWT), Spring Data JPA, Flyway |
| 프론트엔드 | React 19, TypeScript, Vite, Tailwind CSS v4, TanStack Query |
| 앱 | Capacitor 8 (안드로이드 · 아이폰) |
| DB | PostgreSQL (로컬은 H2 인메모리) |
| 배포 | Docker, Render, Cloudflare R2 |

## 시작하기

필요한 것: **Java 25**, **Node.js 22 이상**. DB · 카카오 키 · 메일 설정 없이 바로 실행되고 샘플 데이터가 들어가요.

```bash
# 백엔드 — http://localhost:8080
cd backend
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run

# 프론트엔드 — http://localhost:5173 (새 터미널)
cd frontend
npm install
npm run dev
```

http://localhost:5173 로그인 화면의 **개발용 로그인**에서 샘플 계정을 골라 들어가요. (로컬 서버에서만 보여요)

### 테스트

```bash
cd backend && ./mvnw test
cd frontend && npm run lint && npm test && npm run build
```

## 프로젝트 구조

```
backend/    Spring Boot API (+ 빌드된 화면을 함께 서빙)
frontend/   React 화면 · android/ · ios/ (Capacitor 앱)
docs/       기획 히스토리 · 배포 · 앱 출시 문서
```

## 문서

- [배포와 환경변수](docs/deployment.md)
- [안드로이드 출시](docs/android-release.md) · [아이폰 설치](docs/ios-sideload.md) · [앱 버전](docs/app-versions.md)
- [기획 히스토리](docs/planning/README.md)
- [기여 가이드](CONTRIBUTING.md)

## 라이선스

[MIT](LICENSE) · 학생들이 만든 비공식 프로젝트로, 어느 학교의 공식 서비스도 아니에요.
