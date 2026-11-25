# 함께 만들기 가이드

안암3교시에 관심 가져 줘서 고마워요! 개발을 처음 해 보는 동기도 참여할 수 있게 최대한 쉽게 정리했어요.

## 어떻게 참여하나요?

- **아이디어/버그만 알려 주기** → [Issues](../../issues/new/choose) 에서 템플릿을 골라 작성
- **코드로 기여하기** → 아래 순서대로

## 코드 기여 순서

1. **이슈 먼저**: 큰 작업이면 이슈에 "이거 제가 해 볼게요" 남겨 주세요. 겹치는 작업을 막을 수 있어요.
2. **저장소 가져오기**
   - 관리자가 협업자(Collaborator)로 초대했다면 → 바로 `git clone`
   - 아니라면 → 오른쪽 위 **Fork** 후 내 저장소를 clone
3. **브랜치 만들기** (main 에 바로 올리지 않아요)
   ```bash
   git checkout -b feat/meetup-comment     # 새 기능
   git checkout -b fix/deadline-timezone   # 버그 수정
   ```
4. **로컬 실행 & 수정** — [README의 "로컬에서 실행하기"](README.md#로컬에서-실행하기)
5. **테스트 통과 확인**
   ```bash
   cd backend && ./mvnw test
   cd frontend && npm run lint && npm test && npm run build
   ```
6. **커밋 & 푸시 → Pull Request**
   - PR 템플릿을 채워 주세요. 화면을 바꿨다면 스크린샷(모바일+데스크톱)을 꼭 넣어 주세요.
   - 기능을 바꾸는 PR 이면 [docs/planning](docs/planning/README.md)에 기획 파일(정한 것 · 버린 대안과 이유)을 남기고 PR 에 링크해 주세요.
   - GitHub Actions(CI)가 초록색이 되고 관리자 승인을 받으면 합쳐져요.
   - `main` 에 합쳐지면 자동으로 배포돼요.

## 커밋 메시지

`종류: 무엇을` 형식으로 한국어/영어 자유롭게.

```
feat: 3교시에 댓글 기능 추가
fix: 자정 넘는 3교시가 '오늘'로 보이던 문제 수정
docs: README 보완
refactor / test / chore / style
```

## 꼭 지켜 주세요

### 개인정보 · 공개 규칙
- **실명은 서버에서 가린다.** 화면(JS)에서 가리면 개발자 도구로 다 보여요.
  공개 규칙은 [`MeetupMapper.java`](backend/src/main/java/com/anam3/meetup/MeetupMapper.java) 한 곳에만 있어요. 바꾸려면 이슈에서 먼저 이야기해요.
- 새 API에서 다른 회원 정보를 내려줄 때는 `PersonView`/`AuthorView` 처럼 **가려진 이름**을 기본으로.
- 카카오 키·클라이언트 시크릿, SMTP 비밀번호, 토큰, DB 주소를 코드나 커밋에 넣지 마세요. 전부 환경변수예요.
- 샘플·테스트 데이터에는 실제 있을 법한 이름 대신 `김고대`처럼 없을 만한 이름을 써요.

### DB 변경
- 테이블/컬럼을 바꿀 땐 **다음 번호로 새 Flyway 파일**을 추가해요: `backend/src/main/resources/db/migration/V8__설명.sql`
- 이미 배포된 `V1__init.sql` 같은 파일은 **절대 수정하지 않기** (운영 DB가 깨져요).
- PostgreSQL과 H2(PostgreSQL 모드) 양쪽에서 돌아가는 SQL로 써 주세요.

### 백엔드
- 패키지는 기능별(`meetup`, `notice` …)로 나눠요. 컨트롤러 → 서비스 → 레포지토리.
- 사용자에게 보여줄 에러는 `ApiException` 으로, 메시지는 **친근한 해요체 한국어**로.
- 현재 시각은 `Clock` 빈에서. 그래야 테스트에서 시간을 돌려 볼 수 있어요.
- 기능을 추가하면 `src/test` 에 API 테스트도 함께 (`ApiTestSupport` 를 상속하면 쉬워요).

### 프론트엔드
- API 호출은 `src/api/queries.ts` 의 훅으로만. 화면에서 `fetch` 직접 쓰지 않기.
- 색은 `src/index.css` 의 토큰(`bg-board`, `text-ink-2`, `bg-accent` …)만 써요. `#ff0000` 같은 값을 직접 쓰지 않기. 다크 모드가 자동으로 맞춰져요.
- 시간 표시는 `src/lib/time.ts` 를 써요 (항상 한국 시간 기준).
- 모바일(390px)과 데스크톱(1440px) 둘 다 확인해 주세요.
- 디자인은 안암역 6호선 안내판이 모티브예요. 기존 화면의 색·간격·말투에 맞춰 주세요.

궁금한 건 이슈나 단톡방에 편하게 물어봐 주세요. 🍻
