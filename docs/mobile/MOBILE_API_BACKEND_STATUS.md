# Mobile API Backend Status

Last audited: 2026-08-21

이 문서는 `todolab-mobile/docs/API_*.md`와 모바일 로드맵의 백엔드 확인 항목을 `todolab-backend` 현재 코드 기준으로 대조한 관리 문서다.

상태 기준:

- `[x]` 완료 또는 현재 코드로 확인됨
- `[~]` 부분 구현됨. 계약과 차이가 있어 보완 필요
- `[ ]` 미구현 또는 아직 확인 불가

## 0. 현재 요약

2026-08-20 기준 backend 계약과 구현은 모바일 v1 연동에 필요한 핵심 흐름을 대부분 제공한다. 남은 최우선 확인은 backend 구현 문제가 아니라 production 접근 경로와 실제 기기 검증이다.

| 영역 | 상태 | 현재 기준 |
| --- | --- | --- |
| Auth/Guest | [x] | JWT, guest 생성/갱신/승격/병합/cleanup/rate limit 계약 구현 |
| Task/D-Day | [x] | owner scope, Today/Calendar, 검색, D-Day 연결, 반복 occurrence 구현 |
| Quick Capture | [x] | `POST /api/v1/tasks/quick-capture` 구현. 실제 입력 로그 기반 파싱 보강만 남음 |
| Templates | [x] | `TaskTemplate` CRUD와 template 기반 Task 생성 구현 |
| Sharing | [x] | workspace 생성/초대/멤버/Task/D-Day 1차 API 구현 |
| Notifications | [~] | 로컬 알림 후보, push token, 발송 이력 API 구현. 실제 서버 push 발송은 미구현 |
| Production Android | [ ] | Tailscale HTTPS URL로 Android 실제 기기 smoke 필요 |
| Expo Web production | [ ] | 사용 여부와 실제 origin 확정 전 |

## 1. 실제 사용 전 최우선

최근 모바일 연동 테스트 결과:

- [x] Expo Web에서 JWT 요청 preflight 시 `Authorization` header가 CORS 허용 목록에 없어 막히는 문제 수정
- [x] CORS preflight 회귀 테스트 추가
- [x] OpenAPI JSON `/v3/api-docs`, Swagger UI `/swagger-ui`, Scalar `/scalar.html` 노출
- [x] `UserResponse.updatedAt` 문서/모바일 타입/백엔드 DTO 계약 일치
- [x] 2026-07-14 문서 기준: local CORS origin, 인증 smoke test, Today/Calendar/D-Day real-mode 확인 절차를 runbook으로 정리
- [x] 2026-07-14 OpenAPI JSON 기준: v1 Auth/Task/D-Day tag, operation summary, Bearer security, error response schema 노출 검증
- [x] 2026-07-15 OpenAPI JSON 기준: request schema enum, 날짜 형식, validation 제약 노출 검증
- [x] 2026-07-15 문서 UI 기준: Swagger UI/Scalar 노출, Bearer security scheme, v1 tag 순서 검증
- [x] 2026-07-15 정책 기준: OpenAPI JSON snapshot diff는 보류하고 CI의 OpenAPI 문서 회귀 테스트와 릴리스 전 계약 검토로 관리하기로 결정
- [x] 2026-07-22 v1 D-Day 목표 생성 응답 기준: `DdayGoalResponse`의 `id`, `title`, `targetDate`, `daysLeft`, `createdAt`은 모두 non-null
- [x] 2026-07-22 v1 Task 생성 응답 기준: nullable/default/date 규칙을 OpenAPI와 `API_V1_FRONTEND.md`에 반영
- [x] 2026-07-22 v1 MONTH Task 조회 기준: `date=YYYY-MM` 바인딩, `YYYY-MM-DD` 거부, owner scope 검증
- [x] 2026-07-22 v1 리소스 삭제 응답 기준: Task/D-Day 삭제 성공 envelope의 `data`는 `null`
- [x] 2026-07-22 legacy 정책 기준: `/api/tasks/**`, `/api/ddays/**`는 과거 호환 범위로 유지하고 모바일 alias는 추가하지 않음
- [x] 2026-08-09 게스트 계정 기준: 생성, `/auth/me`, 신규 회원가입 승격, 기존 계정 로그인 병합, 만료 정리, 생성 rate limit 계약 구현
- [x] 2026-08-11 production guest smoke 기준: `./scripts/smoke-production-api.sh`로 게스트 발급, `/auth/me`, token refresh, 회원가입 승격, 기존 계정 병합, 병합 재시도 멱등성 확인 가능
- [x] 2026-08-11 production Tailscale smoke 기준: `./scripts/check-tailscale-production.sh`로 HTTPS readiness, guest 발급, `/auth/me`, 선택적 Expo Web CORS preflight 확인 가능
- [x] 2026-08-20 production Tailscale URL 기준: MagicDNS HTTPS URL을 `.env`의 `TODOLAB_TAILSCALE_API_URL`에 저장하고 `TODOLAB_REQUIRE_TAILSCALE_URL=true ./scripts/check-production-env.sh` 통과
- [x] 2026-08-20 production Tailscale host smoke 기준: HTTPS readiness, guest 발급, guest `/auth/me` 확인. 실제 URL, token, guest id는 기록하지 않음
- [x] 2026-08-21 production recovery 기준: launchd, Docker Desktop, app/mysql health, readiness, Tailscale HTTPS recovery check 통과. 실제 URL은 기록하지 않음

1. [x] 인증 사용자 소유권
   - 완료: `Task`, `DdayGoal` owner 필드와 owner-aware repository/service path 추가
   - 완료: `/api/v1/tasks`, `/api/v1/dday-goals`의 주요 조회/수정/삭제 endpoint를 owner-aware service path로 확장
   - 완료: 기존 `/api/tasks`, `/api/ddays`는 웹/과거 호환 범위로 유지하고 모바일 alias는 추가하지 않는 정책 확정
   - 완료: access token TTL 1시간, refresh token 미도입, 모바일 로그아웃 클라이언트 책임, 401/403 오류 계약 확정
   - 문서: `docs/api/AUTH_CONTRACT.md`

2. [x] Today / Calendar 여러 날 일정 범위 조회
   - 완료: `GET /api/tasks/today?date=...`는 요청 날짜와 겹치는 `SCHEDULE`을 포함한다.
   - 완료: `DAY/WEEK/MONTH` 범위 조회는 `startAt/endAt` overlap 조건을 사용한다.
   - 완료: 여러 날 일정은 Today 조회에 포함되지만 일괄 재정렬 대상에서는 제외한다.

3. [x] 통합 검색 API
   - 완료: `GET /api/v1/tasks/search` 구현.
   - 완료: 검색어, 필터, relevantDate/dateSource, cursor/limit, owner scope 검증.
   - 완료: cursor를 offset에서 마지막 Task id anchor로 변경해 offset shift 중복/누락을 방지.

4. [x] 기존 D-Day 500 이슈 확인
   - 현재 `GET /api/ddays/{id}` endpoint 자체가 없음.
   - 현재 `POST /api/ddays/{id}/tasks` endpoint 자체가 없음.
   - 현재 Task와 D-Day 연결은 `PATCH /api/tasks/{id}/dday-goal?ddayGoalId=...`로 제공됨.
   - 완료: v1 기준 `GET /api/v1/dday-goals/{id}`와 `POST /api/v1/dday-goals/{id}/tasks` 추가.
   - 완료: legacy `/api/ddays/**` alias는 추가하지 않고 모바일을 v1 계약으로 전환하기로 결정.
   - 완료: v1 D-Day 연결 Task Today 이동 회귀 테스트 추가.

5. [x] 게스트 계정 및 정식 계정 연동
   - 완료: `POST /api/v1/auth/guest` 서버 발급형 게스트 사용자와 token 발급
   - 완료: `/api/v1/auth/me`와 token claim에 `accountType=GUEST|REGISTERED` 반영
   - 완료: 게스트 상태 `POST /api/v1/auth/register`는 같은 user id를 유지하고 `REGISTERED`로 승격
   - 완료: 기존 계정 `POST /api/v1/auth/login`에 guest Bearer token을 보내면 Task/D-Day/반복/알림 owner 데이터를 정식 계정으로 병합
   - 완료: 병합 완료 guest token, 만료 guest token은 owner API와 `/auth/me`에서 401 처리
   - 완료: 만료 게스트 정리 스케줄러와 게스트 생성 rate limit 429/`11004` 구현
   - 완료: `POST /api/v1/auth/guest/refresh`는 같은 guest user id를 유지하며 만료 전 token을 갱신
   - 완료: 기존 계정 로그인 병합 성공 응답에 `mergeResult.tasks`, `schedules`, `ddayGoals`, `recurrenceSeries` 포함
   - 문서: `docs/api/GUEST_ACCOUNT_HANDOFF.md`

## 2. 여러 날 일정 / Calendar 범위 조회

문서: `todolab-mobile/docs/API_SCHEDULE_RANGE.md`

| 항목 | 상태 | 백엔드 현재 상태 |
| --- | --- | --- |
| `GET /api/tasks/today?date=YYYY-MM-DD`가 오늘과 겹치는 `SCHEDULE`도 반환 | [x] | `TaskRepository.findTodayTasks`가 `targetDate` 일치 또는 `SCHEDULE` overlap 조건 사용 |
| Calendar `DAY/WEEK/MONTH`가 범위와 겹치는 일정 반환 | [x] | `TaskRepositoryImpl.findByDateRangeAndType`가 `startAt < end && endAt > start` overlap 사용 |
| 원본 ID로 한 번만 반환 | [x] | 날짜별 row materialize 없이 `Task` row를 그대로 반환 |
| 여러 날 일정이 날짜마다 중복 row로 내려오지 않음 | [x] | Calendar 범위 조회는 원본 row 조회 방식 |
| 자정 종료, 종일 일정 종료일 exclusive 처리 | [x] | `endAt.isAfter(startAt)` 검증, range query는 `[start,end)` overlap. `endAt == rangeStart`는 미포함 |
| `endAt < startAt` 요청 validation | [x] | `TaskRequest.validate`, `Task.validatePeriodSchedule` 모두 거부 |
| 서비스 기준 시간대 `Asia/Seoul` | [x] | `Constant.ZONE_ID = "Asia/Seoul"` |

필요 작업:

- [x] Today 조회에 schedule overlap 포함
- [x] Today 응답에서 실행 TODO 정렬과 일정 정렬 기준 분리
- [x] 여러 날 일정이 `todayOrder` 재정렬 대상에서 제외되는 테스트 추가
- [x] Today/Calendar가 같은 overlap 기준을 쓰는 통합 테스트 추가

## 3. 통합 검색 API

문서: `todolab-mobile/docs/API_SEARCH_FILTER.md`

현재 상태: [x] 구현

필요 작업:

- [x] `GET /api/v1/tasks/search`
- [x] `q` 제목/설명 검색
- [x] `statuses`, `taskTypes`, `category`, `ddayGoalId`, `hasDday`, `allDay`
- [x] `dateField`, `dateFrom`, `dateTo`
- [x] `sort`, `cursor`, `limit`
- [x] cursor pagination 중복/누락 방지
- [x] 한글 검색, 영문 대소문자 검색 일관성
- [x] `relevantDate`, `dateSource` 반환
- [x] 잘못된 enum, 날짜 범위, cursor는 HTTP 400
- [x] 모바일에 노출 가능한 안전한 오류 message
- [x] 인증 사용자 owner 조건 적용

## 4. 반복 Task / 반복 일정

문서: `todolab-mobile/docs/API_RECURRENCE.md`

현재 상태: [x] 모델/계약 확정

백엔드 모델 반영 상태와 남은 endpoint 작업은 아래와 같다.

- [x] `recurrenceSeriesId`
- [x] `recurrenceRule` RRULE
- [x] `recurrenceTimeZone`
- [x] `recurrenceStartAt`
- [x] `recurrenceUntil` / `recurrenceCount`
- [x] `occurrenceDate`
- [x] `originalOccurrenceDate`
- [x] `recurrenceException`
- [x] RRULE validation 범위 확정
- [x] Today / Calendar 조회 시 occurrence materialize
- [x] occurrence별 완료 상태 저장
- [x] `THIS` / `THIS_AND_FUTURE` / `ALL` 수정·삭제 scope
- [x] 반복 전체 수정 후 기존 완료 기록 보존
- [x] 월말, 윤년, time zone 경계 테스트

제품 주의:

- 반복 생성 UI는 실제 저장 기능으로 열 수 있다.
- 기존 반복 series의 rule 자체 수정은 `PUT /api/v1/tasks/{id}/recurrence-rule`로 일반 필드 수정과 분리해서 요청한다.
- 기존 반복 series rule 변경은 `effectiveDate` 이전 완료/예외 occurrence를 보존하고, 이후 일반 materialized occurrence만 새 rule 기준으로 정리한다.

## 5. 반복 일정과 알림 책임

문서: `todolab-mobile/docs/API_NOTIFICATIONS.md`

현재 상태: [x] 로컬 알림 후보 API 구현

확정해야 할 백엔드 책임:

- [x] 반복 occurrence 계산은 백엔드 책임
- [x] 모바일은 가까운 미래 occurrence만 로컬 알림 예약
- [x] 완료·미룸·삭제 후 같은 occurrence가 다음 동기화에서 제외/변경되는지
- [x] `SKIPPED`, `MOVED`, `MODIFIED` 예외 처리
- [x] time zone 변경 시 과거/미래 occurrence 재계산 방식
- [x] 향후 서버 push 알림과 로컬 알림 중복 방지 방식
- [x] 서버 push 활성화 시 로컬 알림 억제 플래그
- [x] `GET /api/v1/tasks/notification-candidates`
- [x] `POST /api/v1/push-tokens`
- [x] `GET /api/v1/push-tokens`
- [x] `DELETE /api/v1/push-tokens/{id}`
- [x] `GET /api/v1/push-notification-histories`

세부 계약은 `docs/api/NOTIFICATION_CONTRACT.md`에서 관리한다.

## 6. Today 재정렬 API

문서: `todolab-mobile/docs/API_TODAY_REORDER.md`

현재 상태: [x] 구현

현재 제공:

- [x] `PATCH /api/v1/tasks/{taskId}/today-order?date=YYYY-MM-DD&direction=UP|DOWN`
- [x] 단일 Task를 위/아래 한 칸 이동
- [x] `PUT /api/v1/tasks/today-order`
- [x] request `{ date, orderedTaskIds }`
- [x] 전체 순서를 transaction으로 저장
- [x] 중복 ID는 HTTP 400
- [x] 누락/다른 날짜/완료/일정 Task ID 또는 stale 목록은 HTTP 409
- [x] 저장 직후 Today 조회 순서와 응답 순서 일치
- [x] OpenAPI request/response/error schema 등록
- [x] 인증 사용자 owner 조건 적용

주의: `SCHEDULE`은 Today 조회에는 포함될 수 있지만 drag-and-drop 실행 순서 저장 대상에서는 제외한다.

## 7. 날짜·시간 기준

문서: `todolab-mobile/docs/API_DATE_TIME.md`

| 항목 | 상태 | 백엔드 현재 상태 |
| --- | --- | --- |
| 서비스 기준 시간대 `Asia/Seoul` | [x] | `Constant.ZONE_ID`에 정의 |
| `LocalDate`는 `YYYY-MM-DD` | [x] | Spring `LocalDate` binding/JSON 기본 형식 |
| `LocalDateTime`은 offset 없는 `YYYY-MM-DDTHH:mm:ss` | [x] | Java `LocalDateTime` 사용 |
| 모바일이 서울 기준으로 해석 가능 | [x] | 현재 날짜/시간 계산은 `Constant.ZONE` 기준으로 통일 |
| 사용자 time zone 저장 API | [x] | `PATCH /api/v1/users/me/time-zone`으로 IANA timezone 설정값 저장 |
| 사용자 time zone별 날짜 경계 계산 | [x] | Today/Calendar/알림 후보 일정 overlap 조회에 사용자 timezone 경계 적용 |

필요 작업:

- [x] `LocalDate.now()` 직접 사용 지점은 `Constant.ZONE_ID` 기준 clock으로 정리
- [x] 사용자 time zone 도입 전까지 API 문서에 “서버/서비스 기준은 Asia/Seoul” 명시 유지
- [x] timezone 변경 시 기존 반복 occurrence 재계산/보존 정책 확정
  - 정책: 사용자 timezone 변경은 조회 경계에만 영향을 주며, 기존 반복 series/occurrence는 자동 재계산하지 않는다.
  - 문서: `docs/api/TIMEZONE_CONTRACT.md`

## 8. 기존 백엔드 이슈와 운영 확인

문서: `todolab-mobile/docs/ROADMAP.md`

| 항목 | 상태 | 메모 |
| --- | --- | --- |
| 개발 / 스테이징 / 운영 API URL | [~] | local과 host 내부 production은 확정. Android production Tailscale HTTPS URL과 Expo Web production origin은 실제 기기/배포 origin 확정 필요 |
| 인증 방식과 토큰 계약 | [x] | `/api/v1/auth/register`, `/api/v1/auth/login`, `/api/v1/auth/me` 있음. access token TTL, refresh token 미도입, 로그아웃 책임, 401/403 계약은 `docs/api/AUTH_CONTRACT.md` 기준 |
| 게스트 계정 bootstrap과 정식 계정 연동 | [x] | `POST /api/v1/auth/guest`, 게스트 회원가입 승격, 기존 계정 로그인 병합, 만료 정리, 생성 rate limit 계약은 `docs/api/GUEST_ACCOUNT_HANDOFF.md` 기준 |
| OpenAPI 명세 | [x] | `/v3/api-docs`, `/swagger-ui`, `/scalar.html` 제공. v1 주요 controller tag/summary/security/error schema와 tag 순서 검증 추가 |
| 오류 코드와 장애 대응 | [x] | `docs/api/API_ERROR_CODES.md`와 `docs/mobile/MOBILE_INCIDENT_RUNBOOK.md`에 오류 코드, retry, logging/masking, 장애 확인 순서 정리 |
| 데이터 모델 사전 | [x] | `docs/api/DATA_MODEL_GLOSSARY.md`에 Task, D-Day, User 주요 필드, 상태 전이, owner scope 정리 |
| `GET /api/tasks` 범위 조회 계약 | [x] | `DAY/WEEK/MONTH`, `taskType` 지원. v1/owner 기준은 `docs/api/API_V1_FRONTEND.md`와 OpenAPI에 문서화 |
| `GET /api/ddays/{id}` HTTP 500 | [x] | legacy alias는 추가하지 않음. 모바일은 v1 `GET /api/v1/dday-goals/{id}` 사용 |
| `POST /api/ddays/{id}/tasks` HTTP 500 | [x] | legacy alias는 추가하지 않음. 모바일은 v1 `POST /api/v1/dday-goals/{id}/tasks` 사용 |
| D-Day 연결 Task Today 이동 후 HTTP 500 | [x] | v1 `PATCH /api/v1/tasks/{id}/today`에서 D-Day 연결 필드를 유지하는 회귀 테스트 추가 |

운영 연결 확인:

- [x] host 내부 production API smoke: `./scripts/smoke-production-api.sh`
- [x] Tailscale HTTPS host smoke 스크립트: `TODOLAB_TAILSCALE_API_URL=https://<device>.<tailnet>.ts.net ./scripts/check-tailscale-production.sh`
- [x] Tailscale HTTPS URL `.env` 반영: `TODOLAB_REQUIRE_TAILSCALE_URL=true ./scripts/check-production-env.sh`
- [x] Tailscale HTTPS recovery check: `TODOLAB_TAILSCALE_API_URL=https://<device>.<tailnet>.ts.net ./scripts/check-production-recovery.sh`
- [ ] Android 실제 기기 production smoke: Tailscale HTTPS URL로 `/api/v1/auth/me`, login, Today 조회·생성·완료 확인
- [ ] Expo Web production origin: 필요 시 `TODOLAB_ALLOWED_ORIGINS` 반영 후 preflight 확인

## 9. 추천 구현 순서

1. [x] `/api/v1/tasks` 조회/수정/삭제를 owner-aware service path로 확장
2. [x] `/api/v1/dday-goals` 조회/삭제/연결 Task 조회를 owner-aware service path로 확장
3. [x] Today 조회에 여러 날 schedule overlap 포함
4. [x] OpenAPI/Swagger/Scalar 문서 UI 추가
5. [x] Expo Web Authorization CORS preflight 허용
6. [x] D-Day legacy 500 이슈 재현 테스트 또는 endpoint 계약 정리
7. [x] `GET /api/v1/tasks/search` 구현
8. [x] Today 일괄 재정렬 API 구현
9. [x] 반복/알림 계약 설계 확정 후 recurrence 모델링
