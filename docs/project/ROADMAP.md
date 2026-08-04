# ToDoLab Backend Roadmap

Last updated: 2026-08-02

이 문서는 완료 이력보다 **앞으로 백엔드에서 닫아야 할 작업**을 관리한다. 이미 구현된 인증, v1 경로, owner scope, OpenAPI/Swagger/Scalar 문서 UI는 기준 상태로 보고, 아래 항목은 모바일 실사용과 운영 안정성에 필요한 후속 작업이다.

## 1. 현재 기준

- 모바일 API 기준 경로는 `/api/v1/**`다.
- 모바일 API 인증은 `Authorization: Bearer <accessToken>` JWT 방식이다.
- 웹 화면은 세션 기반 인증을 사용한다.
- API 문서 원본은 `/v3/api-docs` OpenAPI JSON이다.
- 개발 확인용 문서 UI는 `/swagger-ui`, 읽기용 문서 UI는 `/scalar.html`을 사용한다.
- 모바일 연동 요약 문서는 [`../api/API_V1_FRONTEND.md`](../api/API_V1_FRONTEND.md)에 둔다.
- 모바일 연동 상태 관리는 [`../mobile/MOBILE_API_BACKEND_STATUS.md`](../mobile/MOBILE_API_BACKEND_STATUS.md)에 둔다.

## 2. 최우선 작업

### 2.1 모바일 real-mode smoke test 보강

목표: 모바일이 실제 백엔드와 붙었을 때 로그인, Today, Calendar, D-Day 흐름이 안정적으로 동작하는지 자동/수동 검증한다.

- [x] Expo Web 인증 요청의 CORS preflight에서 `Authorization` 헤더 허용
- [x] real-mode smoke test 결과를 `MOBILE_API_BACKEND_STATUS.md`에 날짜별로 기록
- [x] 모바일에서 확인한 CORS origin 목록을 local/staging/prod 환경별로 정리
- [x] `/api/v1/auth/register`, `/api/v1/auth/login`, `/api/v1/auth/me` 수동 검증 절차 문서화
- [x] Today, Calendar, D-Day 생성/조회/삭제 real-mode 확인 절차 문서화

관련 문서:

- [`../ops/ENVIRONMENT_INTEGRATION.md`](../ops/ENVIRONMENT_INTEGRATION.md)
- [`../mobile/MOBILE_INTEGRATION_RUNBOOK.md`](../mobile/MOBILE_INTEGRATION_RUNBOOK.md)

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

- [x] 반복 occurrence 계산은 백엔드 책임으로 확정
- [x] 모바일 로컬 알림 예약 후보 범위 정의
- [x] 완료, 미룸, 삭제, 이동 후 알림 후보 갱신 규칙 정의
- [x] 향후 서버 push와 로컬 알림 중복 방지 정책 정리

완료 기준:

- 모바일 알림 구현 전, 백엔드가 내려줄 occurrence/상태/예외 계약이 확정된다.

## 4. 운영과 보안

### 4.1 환경과 CORS

- [x] local, staging, production API URL 문서화
- [x] `TODOLAB_ALLOWED_ORIGINS` 운영 값 관리 방식 정리
- [x] Expo Web, iOS Simulator, Android Emulator, 실제 기기 origin 차이 문서화
- [x] staging/prod에서 Swagger UI와 Scalar 공개 범위 결정

### 4.2 인증 토큰 정책

- [x] access token TTL 운영값 확정
- [x] refresh token 도입 여부 결정
- [x] 토큰 폐기/로그아웃 서버 책임 범위 결정
- [x] 401, 403 오류 message와 code 정리

### 4.3 관측과 장애 대응

- [x] API error code catalog 작성
- [x] 4xx/5xx logging 기준 정리
- [x] 개인정보가 포함될 수 있는 필드 masking 정책 정리
- [x] `/api/**` 공통 request/response logging 필터 도입
- [x] 모바일 연동 장애 대응 runbook 작성

## 5. 백엔드 문서화 과제

우선 작성할 문서:

- [x] API 연동 규격서: v1 endpoint, 인증, envelope, 오류, 날짜 규칙
- [x] 환경별 연동 가이드: local/staging/prod URL, CORS, 실행 순서
- [x] 오류 코드 카탈로그: code, HTTP status, 사용자 노출 message
- [x] 인증/인가 계약서: JWT claim, 만료, 401/403 처리
- [x] 데이터 모델 사전: Task, D-Day, User 주요 필드와 상태 전이
- [x] 릴리스/호환성 정책: v1 유지, deprecation, breaking change 기준
- [x] 모바일 연동 테스트 runbook: smoke test 절차와 기록 방식

상세 목록과 우선순위는 [`BACKEND_DOCUMENTATION_PLAN.md`](./BACKEND_DOCUMENTATION_PLAN.md)에서 관리한다.

## 6. 다음 백로그

기존 2-5번 로드맵 항목은 현재 기준으로 닫힌 상태다. 앞으로의 작업은 아래 백로그에서 하나의 커밋 단위로 고른다.

### 6.1 사용자 timezone 날짜 경계 적용

현재 상태:

- [x] User profile timezone 저장
- [x] `PATCH /api/v1/users/me/time-zone`
- [x] 사용자 timezone 기준 Today 조회
- [x] 사용자 timezone 기준 Calendar 조회
- [x] 사용자 timezone 기준 알림 후보 조회
- [x] 사용자 timezone 변경 시 기존 반복 occurrence 재계산/보존 정책 확정
- [x] timezone별 날짜 경계 회귀 테스트

착수 조건:

- 모바일이 사용자 timezone 설정을 실제로 노출할지 결정한다.
- 기존 `Asia/Seoul` 기준 데이터와 사용자 timezone 기준 조회의 호환 정책을 먼저 확정한다.

### 6.2 서버 push 발송

현재 상태:

- [x] push token 등록/조회/해제
- [x] 로컬 알림 후보 API
- [x] push provider 선택 및 설정 방식 확정
- [x] 알림 발송 요청/스케줄러 설계
- [x] 전송 실패 token 비활성화 정책
- [x] 로컬 알림과 서버 push 중복 방지 플래그
- [x] 알림 전송 이력 저장/조회

착수 조건:

- APNs/FCM/Expo 중 사용할 provider와 운영 credential 관리 방식을 확정한다.
- 서버가 어떤 시점에 어떤 Task/occurrence를 발송할지 제품 정책을 확정한다.

### 6.3 반복 rule 수정

현재 상태:

- [x] 반복 생성
- [x] 반복 occurrence 일반 필드 scope 수정/삭제
- [x] 기존 rule 수정 요청 400 방어
- [x] 기존 series rule 변경 API
- [x] rule 변경 시 과거 완료 occurrence 보존 정책
- [x] rule 변경 시 미래 materialized occurrence 재생성/정리 정책
- [x] 모바일 rule 편집 UI와 migration 안내

착수 조건:

- 기존 occurrence 중 완료/수정/삭제된 항목을 rule 변경 후 어떻게 보존할지 정책을 확정한다.

### 6.4 운영 환경 확정

현재 상태:

- [x] local 환경 문서화
- [x] CORS 운영 값 관리 방식 정리
- [ ] staging API URL 확정
- [ ] production API URL 확정
- [ ] staging/production CORS origin 확정
- [x] Swagger UI/Scalar 공개 설정 운영 검증

착수 조건:

- 실제 staging/production 도메인과 모바일 배포 origin을 확정한다.

### 6.5 로컬 PC 단일 production 운영

목표: 별도 서버 없이 이 PC의 Docker Compose를 ToDoLab의 유일한 production 환경으로 사용하고, Android 앱이 Tailscale을 통해 안전하게 접근할 수 있게 한다. 이 환경에서는 staging을 따로 만들지 않고 `local 개발 환경`과 `이 PC의 production 환경`만 구분한다.

현재 확인된 상태:

- [x] MySQL 데이터는 external named volume `todolab-mysql-data`에 저장한다.
- [x] app과 MySQL 컨테이너에 `restart: unless-stopped`가 설정되어 있다.
- [x] MySQL health check와 app의 MySQL healthy 대기가 설정되어 있다.
- [x] production profile은 DB, JWT, 문서 비공개, payload logging 비활성화 설정을 환경변수로 받는다.
- [x] app 컨테이너 자체 health check와 production readiness endpoint를 추가했다.
- [x] MySQL host port를 제거하고 app은 `127.0.0.1:8080`에만 공개한다.
- [ ] DB 백업·복구 스크립트와 보관 주기는 마련했지만 자동 실행과 실제 복구 연습이 남아 있다.
- [x] `schema.sql`은 새 volume 최초 초기화에만 적용되므로 기존 production DB의 migration 실행·검증 절차가 필요하다.
- [ ] Dockerfile은 빌드된 JAR을 전제로 하므로 clean checkout에서 JAR build부터 Compose 기동까지 재현하는 release 절차가 필요하다.
- [ ] PC 재부팅, Docker Desktop 재시작, 절전과 네트워크 변경 뒤 자동 복구를 검증하지 않았다.

#### A. production 접근 경로와 노출 범위

- [ ] PC와 Android 기기에 Tailscale을 설치하고 동일 tailnet에서 연결한다.
- [ ] Tailscale MagicDNS 이름을 production API 주소로 확정한다.
- [ ] Tailscale Serve 또는 동등한 reverse proxy로 `https://<device>.<tailnet>.ts.net`을 app `8080`에 연결한다.
- [ ] Android 앱에서 `/api/v1/auth/me`까지 HTTPS로 접근되는지 확인한다.
- [x] Tailscale 경로만 사용하도록 app port를 `127.0.0.1:8080:8080`으로 제한한다.
- [x] MySQL은 host port 공개를 제거하고 Compose 내부 network에서만 접근한다.
- [ ] Tailscale 연결이 끊긴 기기와 허가되지 않은 tailnet 사용자가 API에 접근하지 못하는지 확인한다.

완료 기준:

- 공용 인터넷 포트포워딩 없이 Android 앱에서 production API에 접근한다.
- DB port는 LAN 또는 tailnet에 불필요하게 노출되지 않는다.
- 모바일 production API URL이 재부팅이나 LAN IP 변경에도 바뀌지 않는다.

#### B. Compose와 release 재현성

- [x] production 전용 `.env.example`을 만들고 필수 환경변수, 생성 방법, 선택값을 설명한다.
- [x] `TODOLAB_JWT_SECRET`은 32바이트 이상의 무작위 값으로 생성하고 저장소 밖에 보관한다.
- [x] 사용하지 않는 mail/batch 설정 때문에 app 기동이 막히지 않도록 필수·선택 환경변수를 정리한다.
- [x] `./gradlew clean test bootJar`부터 `docker compose up -d --build`까지 release 명령을 문서화한다.
- [x] app image에 version 또는 git commit tag를 남기고 이전 정상 image로 rollback하는 절차를 정한다.
- [x] app health check를 추가하고 MySQL 연결, schema, API readiness를 구분해 확인한다.
- [x] log volume/path와 Docker log rotation을 확정해 디스크 무한 증가를 방지한다.

완료 기준:

- clean checkout과 production 환경 파일만으로 같은 PC에서 배포를 재현할 수 있다.
- 새 버전 실패 시 DB를 손상시키지 않고 직전 app image로 되돌릴 수 있다.

#### C. 데이터 보존과 복구

- [ ] `mysqldump` 백업 명령은 구현했으며 검증 후 host scheduler에 연결한다.
- [x] 백업 파일은 DB volume과 다른 경로에 저장하고 기본 보관 기간을 14일로 정한다.
- [ ] 최소 1회 빈 임시 DB에 백업을 복원해 로그인, Today, Calendar 데이터를 확인한다.
- [x] release 전 schema migration과 DB backup을 선행하는 순서를 문서화한다.
- [ ] migration 파일의 적용 이력을 관리할 도구(Flyway 등) 도입 여부를 결정한다.
- [ ] Docker volume 삭제·재생성, PC 디스크 장애 시 복구 가능한 외부 백업 위치를 결정한다.

완료 기준:

- 컨테이너와 PC가 재시작되어도 데이터가 유지된다.
- 실수로 volume을 잃어도 마지막 정상 백업에서 복원하는 절차가 검증되어 있다.

#### D. 상시 운영 검증

- [ ] Docker Desktop 로그인 시 자동 시작과 Compose 자동 복구를 확인한다.
- [ ] PC 절전 중에는 앱을 사용할 수 없다는 점을 전제로 절전 정책을 확정한다.
- [ ] 재부팅 후 MySQL → app → Tailscale HTTPS 경로가 자동으로 복구되는지 확인한다.
- [ ] `/api/v1/auth/login`, Today 조회·생성·완료를 Android production build에서 smoke test한다.
- [ ] 401, 5xx, DB 중단, Tailscale 연결 끊김을 재현하고 로그와 복구 절차를 확인한다.
- [ ] 월 1회 backup restore와 디스크 여유 공간을 확인하는 운영 루틴을 정한다.

완료 기준:

- PC 재부팅 후 수동 코드 실행 없이 Android 앱이 다시 접속된다.
- 장애 시 request id, container 상태, app/MySQL log, Tailscale 상태 순서로 원인을 확인할 수 있다.

## 7. 개발 원칙

- 모바일과 웹의 인증 방식은 분리하되 사용자 데이터 격리는 동일하게 유지한다.
- 새 모바일 API는 `/api/v1/**`에 추가한다.
- API 계약 변경은 OpenAPI JSON, `API_V1_FRONTEND.md`, 테스트를 함께 갱신한다.
- 날짜/시간은 사용자 time zone 도입 전까지 `Asia/Seoul` 기준을 유지한다.
- 알림처럼 계약이 확정되지 않은 기능은 실제 저장 기능처럼 열지 않는다.
