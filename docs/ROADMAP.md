# ToDoLab Backend Roadmap

Last updated: 2026-07-22

이 문서는 완료 이력보다 **앞으로 백엔드에서 닫아야 할 작업**을 관리한다. 이미 구현된 인증, v1 경로, owner scope, OpenAPI/Swagger/Scalar 문서 UI는 기준 상태로 보고, 아래 항목은 모바일 실사용과 운영 안정성에 필요한 후속 작업이다.

## 1. 현재 기준

- 모바일 API 기준 경로는 `/api/v1/**`다.
- 모바일 API 인증은 `Authorization: Bearer <accessToken>` JWT 방식이다.
- 웹 화면은 세션 기반 인증을 사용한다.
- API 문서 원본은 `/v3/api-docs` OpenAPI JSON이다.
- 개발 확인용 문서 UI는 `/swagger-ui`, 읽기용 문서 UI는 `/scalar.html`을 사용한다.
- 모바일 연동 요약 문서는 [`API_V1_FRONTEND.md`](./API_V1_FRONTEND.md)에 둔다.
- 모바일 연동 상태 관리는 [`MOBILE_API_BACKEND_STATUS.md`](./MOBILE_API_BACKEND_STATUS.md)에 둔다.

## 2. 최우선 작업

### 2.1 모바일 real-mode smoke test 보강

목표: 모바일이 실제 백엔드와 붙었을 때 로그인, Today, Calendar, D-Day 흐름이 안정적으로 동작하는지 자동/수동 검증한다.

- [x] Expo Web 인증 요청의 CORS preflight에서 `Authorization` 헤더 허용
- [x] real-mode smoke test 결과를 `MOBILE_API_BACKEND_STATUS.md`에 날짜별로 기록
- [x] 모바일에서 확인한 CORS origin 목록을 local/staging/prod 환경별로 정리
- [x] `/api/v1/auth/register`, `/api/v1/auth/login`, `/api/v1/auth/me` 수동 검증 절차 문서화
- [x] Today, Calendar, D-Day 생성/조회/삭제 real-mode 확인 절차 문서화

관련 문서:

- [`ENVIRONMENT_INTEGRATION.md`](./ENVIRONMENT_INTEGRATION.md)
- [`MOBILE_INTEGRATION_RUNBOOK.md`](./MOBILE_INTEGRATION_RUNBOOK.md)

완료 기준:

- 모바일 앱에서 회원가입, 로그인, Today 조회, D-Day Today Task 생성까지 한 흐름으로 검증된다.
- CORS 오류, 401 처리, 안전한 오류 message 노출 여부가 기록된다.

### 2.2 v1 API 계약과 OpenAPI 품질 개선

목표: OpenAPI JSON을 프론트/모바일이 신뢰할 수 있는 원본 계약으로 만든다.

- [x] v1 controller에 operation summary, tag, security, error response schema 보강
- [x] 공통 `ApiResponse<T>` envelope가 OpenAPI에서 읽기 쉽게 보이도록 schema 정리
- [x] enum, 날짜 형식, validation 제약을 request schema에 노출
- [x] Swagger UI에서 Bearer token 입력 후 v1 API 호출 확인
- [x] Scalar에서 모바일 개발자가 읽기 쉬운 tag 순서 확인
- [x] OpenAPI JSON diff를 CI 또는 릴리스 체크에 포함할지 결정

완료 기준:

- `/v3/api-docs`만 보고 모바일 request/response 타입을 재현할 수 있다.
- 문서에 legacy `/api/**`와 v1 `/api/v1/**`가 혼동되지 않는다.

### 2.3 API 계약 불일치 정리

목표: 현재 문서, 모바일 타입, 실제 백엔드 응답의 차이를 없앤다.

- [x] `UserResponse.updatedAt` 응답 필드 반영
- [x] `DeferReason` enum 문서와 실제 응답/요청 계약 일치
- [x] `DdayGoalResponse`의 nullable 필드와 실제 응답 확인
- [x] `TaskResponse`의 nullable 필드, 생성/수정 시 기본값, 날짜 규칙 재확인
- [x] `GET /api/v1/tasks?type=MONTH&date=YYYY-MM` 계약과 실제 binding 동작 검증
- [x] 삭제 응답은 모든 v1 API에서 `data: null`로 통일
- [x] legacy `/api/tasks`, `/api/ddays` 유지/제거 정책 확정

완료 기준:

- `API_V1_FRONTEND.md`, OpenAPI JSON, 모바일 타입이 같은 계약을 설명한다.

## 3. 모바일 실사용 후속 기능

### 3.1 통합 검색 API

문서: `todolab-mobile/docs/API_SEARCH_FILTER.md`

- [x] `GET /api/v1/tasks/search`
- [x] `q` 제목/설명 검색
- [x] `statuses`, `taskTypes`, `category`, `ddayGoalId`, `hasDday`, `allDay`
- [x] `dateField`, `dateFrom`, `dateTo`
- [x] `sort`, `cursor`, `limit`
- [x] `relevantDate`, `dateSource` 반환
- [x] 한글 검색, 영문 대소문자 검색 일관성
- [x] 잘못된 enum, 날짜 범위, cursor는 HTTP 400
- [x] owner scope 적용

완료 기준:

- 모바일 real mode에서 검색 화면을 준비 중 상태가 아니라 실제 검색으로 열 수 있다.

### 3.2 Today 일괄 재정렬 API

문서: `todolab-mobile/docs/API_TODAY_REORDER.md`

- [x] `PUT /api/v1/tasks/today-order`
- [x] request `{ date, orderedTaskIds }`
- [x] 전체 순서를 transaction으로 저장
- [x] 중복/누락/다른 날짜/완료/일정 Task ID 거부
- [x] 동시 변경 시 HTTP 409
- [x] 저장 직후 Today 조회 순서와 응답 순서 일치

완료 기준:

- 모바일 drag-and-drop 재정렬이 한 번의 요청으로 안정적으로 저장된다.

### 3.3 반복 Task / 반복 일정

문서: `todolab-mobile/docs/API_RECURRENCE.md`

- [x] recurrence series 모델 설계
- [x] RRULE validation 범위 확정
- [x] Today/Calendar 조회 시 occurrence materialize
- [x] occurrence별 완료 상태 저장
- [x] `THIS`, `THIS_AND_FUTURE`, `ALL` 수정/삭제 scope
- [x] 반복 전체 수정 후 기존 완료 기록 보존
- [x] 월말, 윤년, 타임존 경계 테스트

완료 기준:

- 모바일이 반복 UI를 실제 저장 기능처럼 열 수 있다.

### 3.4 알림 책임 계약

문서: `todolab-mobile/docs/API_NOTIFICATIONS.md`

- [ ] 반복 occurrence 계산은 백엔드 책임으로 확정
- [ ] 모바일 로컬 알림 예약 후보 범위 정의
- [ ] 완료, 미룸, 삭제, 이동 후 알림 후보 갱신 규칙 정의
- [ ] 향후 서버 push와 로컬 알림 중복 방지 정책 정리

완료 기준:

- 모바일 알림 구현 전, 백엔드가 내려줄 occurrence/상태/예외 계약이 확정된다.

## 4. 운영과 보안

### 4.1 환경과 CORS

- [ ] local, staging, production API URL 문서화
- [ ] `TODOLAB_ALLOWED_ORIGINS` 운영 값 관리 방식 정리
- [ ] Expo Web, iOS Simulator, Android Emulator, 실제 기기 origin 차이 문서화
- [ ] staging/prod에서 Swagger UI와 Scalar 공개 범위 결정

### 4.2 인증 토큰 정책

- [ ] access token TTL 운영값 확정
- [ ] refresh token 도입 여부 결정
- [ ] 토큰 폐기/로그아웃 서버 책임 범위 결정
- [ ] 401, 403 오류 message와 code 정리

### 4.3 관측과 장애 대응

- [ ] API error code catalog 작성
- [ ] 4xx/5xx logging 기준 정리
- [ ] 개인정보가 포함될 수 있는 필드 masking 정책 정리
- [ ] 모바일 연동 장애 대응 runbook 작성

## 5. 백엔드 문서화 과제

우선 작성할 문서:

- [ ] API 연동 규격서: v1 endpoint, 인증, envelope, 오류, 날짜 규칙
- [x] 환경별 연동 가이드: local/staging/prod URL, CORS, 실행 순서
- [ ] 오류 코드 카탈로그: code, HTTP status, 사용자 노출 message
- [ ] 인증/인가 계약서: JWT claim, 만료, 401/403 처리
- [ ] 데이터 모델 사전: Task, D-Day, User 주요 필드와 상태 전이
- [x] 릴리스/호환성 정책: v1 유지, deprecation, breaking change 기준
- [x] 모바일 연동 테스트 runbook: smoke test 절차와 기록 방식

상세 목록과 우선순위는 [`BACKEND_DOCUMENTATION_PLAN.md`](./BACKEND_DOCUMENTATION_PLAN.md)에서 관리한다.

## 6. 개발 원칙

- 모바일과 웹의 인증 방식은 분리하되 사용자 데이터 격리는 동일하게 유지한다.
- 새 모바일 API는 `/api/v1/**`에 추가한다.
- API 계약 변경은 OpenAPI JSON, `API_V1_FRONTEND.md`, 테스트를 함께 갱신한다.
- 날짜/시간은 사용자 time zone 도입 전까지 `Asia/Seoul` 기준을 유지한다.
- 반복/알림처럼 계약이 확정되지 않은 기능은 실제 저장 기능처럼 열지 않는다.
