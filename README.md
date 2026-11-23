# 안암3교시 🍻

> 수업 끝나고, 3교시는 여기서.
> 고려대학교 SW·AI융합대학원 동기·선후배를 위한 **번개 모임** 서비스 (학생 비공식 프로젝트)

"누구 한잔할 사람?" 하고 먼저 말 꺼내기 민망했던 사람들을 위해 만들었어요.

| 모바일 — 열린 번개 | 모바일 — 성사! | 데스크톱 — 번개 상세 |
| :---: | :---: | :---: |
| <img src="docs/images/mobile-feed.png" width="240" /> | <img src="docs/images/mobile-confirmed.png" width="240" /> | <img src="docs/images/desktop-detail.png" width="420" /> |

## 핵심 규칙: "성사되기 전까지는 아무도 모른다"

| 누가 보나 | 성사 전 (최소 인원 미달) | 성사 후 (최소 인원 달성) |
| --- | --- | --- |
| 참여자 | `김*수 · 25학번` (가려짐) | **실명 + 카카오톡 오픈채팅 링크** |
| 참여 안 한 사람 | `김*수 · 25학번` | `김*수 · 25학번` (계속 가려짐) |
| 운영자 | 실명 | 실명 |

- 마감 시간까지 최소 인원이 안 모이면 **무산**되고, 목록에서 조용히 사라져요 (본인의 "내 번개"에만 남음).
- 이름 가리기는 **서버에서** 처리해요. 화면에서 가리면 개발자 도구로 다 보이니까요. → [`MeetupMapper.java`](backend/src/main/java/com/anam3/meetup/MeetupMapper.java)
- 인원 현황은 지하철 노선도처럼 보여줘요. 자리 하나가 역 하나이고, 최소 인원 지점은 환승역처럼 그려요.

## 기능

- **번개**: 열기(종류·시간·장소·최소/최대 인원·모집 마감·오픈채팅 링크), "나도 콜" 참여/취소, 공유(단톡방 링크 복사), 다가오는/지난 번개
- **공지**: 누구나 작성, 운영자 글은 "운영자" 표시, 상단 고정은 운영자만
- **회원**: 입장 코드로만 가입(외부인 차단), 아이디/비밀번호 로그인, 비밀번호 변경
- **운영자**: 회원 목록(실명), 차단/해제, 임시 비밀번호 발급, 입장 코드 변경, 전체 번개 보기, 통계
- 모바일/데스크톱 반응형, 다크 모드, 홈 화면에 추가 가능

## 기술 스택

| 영역 | 사용 기술 |
| --- | --- |
| 백엔드 | Java 21, Spring Boot 4.1, Spring Security(JWT), Spring Data JPA, Flyway, Maven |
| DB | PostgreSQL(운영, Neon 무료) / H2(로컬·테스트, 설치 불필요) |
| 프론트엔드 | React 19, TypeScript, Vite, Tailwind CSS v4, TanStack Query, React Router |
| 배포 | Render 무료 웹 서비스(Docker 1개에 화면+API) + Neon 무료 PostgreSQL |
| CI | GitHub Actions (백엔드 테스트, 프론트 lint/test/build, Docker 빌드) |

## 로컬에서 실행하기 (5분)

필요한 것: **Java 21**, **Node.js 22 이상**. DB 설치는 필요 없어요(H2 인메모리 + 샘플 데이터 자동 생성).

```bash
# 1) 백엔드 (http://localhost:8080)
cd backend
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run

# 2) 프론트엔드 (http://localhost:5173) — 새 터미널에서
cd frontend
npm install
npm run dev
```

브라우저에서 http://localhost:5173 을 열고 아래 계정으로 로그인하세요.

| 아이디 | 비밀번호 | 역할 |
| --- | --- | --- |
| `admin` | `admin1234` | 운영자 |
| `minsu` / `jiyoung` / `hyunwoo` | `password1234` | 일반 회원 |

새로 가입하려면 입장 코드 `anam3` 를 쓰면 돼요 (로컬 전용).
H2 DB 내용은 http://localhost:8080/h2-console 에서 볼 수 있어요 (JDBC URL: `jdbc:h2:mem:anam3`, 사용자 `sa`).

## 테스트

```bash
cd backend && ./mvnw test           # API 통합 테스트 + 단위 테스트
cd frontend && npm run lint && npm test && npm run build
```

PR을 올리면 GitHub Actions가 위 테스트와 Docker 빌드를 자동으로 돌려요.

## 폴더 구조

```
backend/                       Spring Boot API
  src/main/java/com/anam3/
    auth/      로그인·가입·JWT·보안 설정
    member/    회원, 내 정보(/api/me)
    meetup/    번개 ★ 공개 규칙은 MeetupMapper, 상태 계산은 Meetup.statusAt
    notice/    공지
    admin/     운영자 API (/api/admin/**)
    setting/   입장 코드 등 운영 설정
    bootstrap/ 운영자 계정 자동 생성, 로컬 샘플 데이터
    common/    에러 처리, 이름 마스킹, SPA 서빙, DB URL 변환
  src/main/resources/db/migration/   DB 스키마 (Flyway)
frontend/                      React 화면
  src/api/        API 호출 + react-query 훅
  src/pages/      화면 단위
  src/components/ 공통 컴포넌트 (LineGauge = 노선도 게이지)
  src/lib/        시간(KST) 처리, 번개 종류/상태 표시 규칙
  src/index.css   디자인 토큰 (색은 여기서만 정의)
Dockerfile        화면+API 한 컨테이너로 빌드
render.yaml       Render 배포 설정
```

## 배포 (무료)

구성: **Render**(앱 1개, 무료) + **Neon**(PostgreSQL, 무료) + **cron-job.org**(잠들지 않게 깨우기, 무료)

> 무료 플랜 조건은 바뀔 수 있어요 (2026년 11월 기준: Render 무료 750시간/월·15분 무요청 시 잠듦, Neon 무료 1GB·월 100 CU-시간).

### 1. Neon에서 DB 만들기
1. https://neon.com 가입 → **New Project** → Region은 **AWS Asia Pacific (Singapore)** 선택
2. 대시보드의 **Connect** 에서 연결 문자열 복사 (`postgresql://...neon.tech/neondb?sslmode=require` 형태, *Pooled connection* 권장)

### 2. Render에 앱 올리기
1. https://render.com 에 GitHub 계정으로 가입
2. **New → Blueprint** → 이 저장소 선택 (`render.yaml` 을 자동으로 읽어요)
3. 환경변수 입력:

| 이름 | 값 |
| --- | --- |
| `DATABASE_URL` | 1번에서 복사한 Neon 연결 문자열 그대로 |
| `INVITE_CODE` | 가입용 입장 코드 (단톡방에 공지할 값) |
| `ADMIN_LOGIN_ID` | 운영자 아이디 |
| `ADMIN_PASSWORD` | 운영자 비밀번호 (8자 이상) |
| `JWT_SECRET` | 자동 생성됨 (손대지 않기) |

4. **Apply** → 첫 빌드 5~10분 → `https://anam3-xxxx.onrender.com` 주소가 생겨요.
5. 그 주소로 들어가 운영자 아이디로 로그인 → 운영 페이지에서 입장 코드 확인

### 3. 잠들지 않게 하기 (선택이지만 추천)
Render 무료 앱은 15분간 요청이 없으면 잠들고, 깨는 데 최대 1분 걸려요.
https://cron-job.org 에서 **10분마다** `https://(내 주소)/api/health` 를 호출하도록 등록하면 항상 깨어 있어요.
(헬스체크는 DB를 건드리지 않아서 Neon 무료 사용량은 아껴져요.)

GitHub Actions로 하려면 저장소 **Settings → Secrets and variables → Actions → Variables** 에 `APP_URL` 을 넣으면 [`keepalive.yml`](.github/workflows/keepalive.yml) 이 10분마다 깨워요. 다만 GitHub 예약 작업은 실행이 늦어질 수 있어서 cron-job.org를 더 추천해요.

### 환경변수 전체

| 이름 | 필수 | 설명 |
| --- | --- | --- |
| `DATABASE_URL` | ✅ | `postgresql://user:pass@host/db?sslmode=require` 또는 `jdbc:postgresql://...` |
| `JWT_SECRET` | ✅ | 32자 이상 무작위 문자열 |
| `INVITE_CODE` | ✅ | 최초 입장 코드 (이후엔 운영 페이지에서 변경) |
| `ADMIN_LOGIN_ID` / `ADMIN_PASSWORD` | 권장 | 서버 시작 시 운영자 계정 생성. 이미 가입한 아이디면 운영자로 승격 |
| `ADMIN_NAME` | | 운영자 표시 이름 (기본 "운영자") |
| `CORS_ALLOWED_ORIGINS` | | 프론트를 다른 도메인(예: Vercel)에 따로 올릴 때만 |
| `VITE_API_BASE_URL` | | (프론트 빌드용) API가 다른 도메인일 때만 |

## 함께 만들기

기능 제안, 버그 제보, 코드 기여 모두 환영해요. → [CONTRIBUTING.md](CONTRIBUTING.md)

학생들이 만든 비공식 커뮤니티로, 고려대학교의 공식 서비스가 아닙니다.
