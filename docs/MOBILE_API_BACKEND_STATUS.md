# Mobile API Backend Status

Last audited: 2026-07-25

이 문서는 `todolab-mobile/docs/API_*.md`와 모바일 로드맵의 백엔드 확인 항목을 `todolab-backend` 현재 코드 기준으로 대조한 관리 문서다.

상태 기준:

- `[x]` 완료 또는 현재 코드로 확인됨
- `[~]` 부분 구현됨. 계약과 차이가 있어 보완 필요
- `[ ]` 미구현 또는 아직 확인 불가

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
- [x] 2026-07-22 legacy 정책 기준: `/api/tasks/**`, `/api/ddays/**`는 웹/과거 호환 범위로 유지하고 모바일 alias는 추가하지 않음

1. [~] 인증 사용자 소유권
   - 완료: `Task`, `DdayGoal` owner 필드와 owner-aware repository/service path 추가
   - 완료: `/api/v1/tasks`, `/api/v1/dday-goals`의 주요 조회/수정/삭제 endpoint를 owner-aware service path로 확장
   - 완료: 기존 `/api/tasks`, `/api/ddays`는 웹/과거 호환 범위로 유지하고 모바일 alias는 추가하지 않는 정책 확정
   - 완료: access token TTL 1시간, refresh token 미도입, 모바일 로그아웃 클라이언트 책임, 401/403 오류 계약 확정
   - 문서: `docs/AUTH_CONTRACT.md`

2. [~] Today / Calendar 여러 날 일정 범위 조회
   - 완료: `GET /api/tasks/today?date=...`는 요청 날짜와 겹치는 `SCHEDULE`을 포함한다.
   - `DAY/WEEK/MONTH` 범위 조회는 `startAt/endAt` overlap 조건을 사용하므로 Calendar 쪽 기반은 있음.
   - 남음: 여러 날 일정이 실행 순서와 `todayOrder`에 섞이지 않게 API 응답/모바일 표시 기준을 더 명확히 분리.

3. [~] 통합 검색 API
   - 완료: `GET /api/v1/tasks/search` 구현.
   - 완료: 검색어, 필터, relevantDate/dateSource, cursor/limit, owner scope 검증.
   - 남음: offset cursor의 중복/누락 방지 강화 여부 결정.

4. [~] 기존 D-Day 500 이슈 확인
   - 현재 `GET /api/ddays/{id}` endpoint 자체가 없음.
   - 현재 `POST /api/ddays/{id}/tasks` endpoint 자체가 없음.
   - 현재 Task와 D-Day 연결은 `PATCH /api/tasks/{id}/dday-goal?ddayGoalId=...`로 제공됨.
   - 완료: v1 기준 `GET /api/v1/dday-goals/{id}`와 `POST /api/v1/dday-goals/{id}/tasks` 추가.
   - 완료: legacy `/api/ddays/**` alias는 추가하지 않고 모바일을 v1 계약으로 전환하기로 결정.

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
- [~] Today 응답에서 실행 TODO 정렬과 일정 정렬 기준 분리
- [ ] 여러 날 일정이 `todayOrder` 재정렬 대상에서 제외되는 테스트 추가
- [ ] Today/Calendar가 같은 overlap 기준을 쓰는 통합 테스트 추가

## 3. 통합 검색 API

문서: `todolab-mobile/docs/API_SEARCH_FILTER.md`

현재 상태: [~] 부분 구현

필요 작업:

- [x] `GET /api/v1/tasks/search`
- [x] `q` 제목/설명 검색
- [x] `statuses`, `taskTypes`, `category`, `ddayGoalId`, `hasDday`, `allDay`
- [x] `dateField`, `dateFrom`, `dateTo`
- [x] `sort`, `cursor`, `limit`
- [ ] cursor pagination 중복/누락 방지
- [x] 한글 검색, 영문 대소문자 검색 일관성
- [x] `relevantDate`, `dateSource` 반환
- [x] 잘못된 enum, 날짜 범위, cursor는 HTTP 400
- [x] 모바일에 노출 가능한 안전한 오류 message
- [x] 인증 사용자 owner 조건 적용

## 4. 반복 Task / 반복 일정

문서: `todolab-mobile/docs/API_RECURRENCE.md`

현재 상태: [~] 모델 설계

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

- 반복 UI는 이 계약 확정 전까지 실제 저장 기능처럼 열면 안 된다.
- 반복 series 저장 모델은 `docs/RECURRENCE_MODEL.md`에 정리했지만, 반복 생성/수정 API는 아직 제공하지 않는다.

## 5. 반복 일정과 알림 책임

문서: `todolab-mobile/docs/API_NOTIFICATIONS.md`

현재 상태: [x] 계약 확정

확정해야 할 백엔드 책임:

- [x] 반복 occurrence 계산은 백엔드 책임
- [x] 모바일은 가까운 미래 occurrence만 로컬 알림 예약
- [x] 완료·미룸·삭제 후 같은 occurrence가 다음 동기화에서 제외/변경되는지
- [x] `SKIPPED`, `MOVED`, `MODIFIED` 예외 처리
- [x] time zone 변경 시 과거/미래 occurrence 재계산 방식
- [x] 향후 서버 push 알림과 로컬 알림 중복 방지 방식

세부 계약은 `docs/NOTIFICATION_CONTRACT.md`에서 관리한다.

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
| 모바일이 서울 기준으로 해석 가능 | [~] | 값 형식은 맞지만 전체 코드가 `Constant.ZONE_ID`를 일관되게 쓰는 구조는 아님 |
| 향후 사용자 time zone 계약 | [ ] | 미정 |

필요 작업:

- [ ] `LocalDate.now()` 직접 사용 지점은 `Constant.ZONE_ID` 기준 clock으로 정리
- [ ] 사용자 time zone 도입 전까지 API 문서에 “서버/서비스 기준은 Asia/Seoul” 명시 유지

## 8. 기존 백엔드 이슈와 운영 확인

문서: `todolab-mobile/docs/ROADMAP.md`

| 항목 | 상태 | 메모 |
| --- | --- | --- |
| 개발 / 스테이징 / 운영 API URL | [x] | `ENVIRONMENT_INTEGRATION.md`에 local 기준, staging/production 미정 상태, CORS 운영 값 관리, 문서 UI 공개 제어 방식 정리 |
| 인증 방식과 토큰 계약 | [x] | `/api/v1/auth/register`, `/api/v1/auth/login`, `/api/v1/auth/me` 있음. access token TTL, refresh token 미도입, 로그아웃 책임, 401/403 계약은 `AUTH_CONTRACT.md` 기준 |
| OpenAPI 명세 | [x] | `/v3/api-docs`, `/swagger-ui`, `/scalar.html` 제공. v1 주요 controller tag/summary/security/error schema와 tag 순서 검증 추가 |
| 오류 코드와 장애 대응 | [x] | `API_ERROR_CODES.md`와 `MOBILE_INCIDENT_RUNBOOK.md`에 오류 코드, retry, logging/masking, 장애 확인 순서 정리 |
| `GET /api/tasks` 범위 조회 계약 | [~] | `DAY/WEEK/MONTH`, `taskType` 지원. v1/owner 기준 계약 문서화 필요 |
| `GET /api/ddays/{id}` HTTP 500 | [x] | legacy alias는 추가하지 않음. 모바일은 v1 `GET /api/v1/dday-goals/{id}` 사용 |
| `POST /api/ddays/{id}/tasks` HTTP 500 | [x] | legacy alias는 추가하지 않음. 모바일은 v1 `POST /api/v1/dday-goals/{id}/tasks` 사용 |
| D-Day 연결 Task Today 이동 후 HTTP 500 | [~] | 관련 회귀 테스트 필요. 현재 `PATCH /api/tasks/{id}/today`와 D-Day fetch join 응답 구조는 있음 |

## 9. 추천 구현 순서

1. [x] `/api/v1/tasks` 조회/수정/삭제를 owner-aware service path로 확장
2. [x] `/api/v1/dday-goals` 조회/삭제/연결 Task 조회를 owner-aware service path로 확장
3. [x] Today 조회에 여러 날 schedule overlap 포함
4. [x] OpenAPI/Swagger/Scalar 문서 UI 추가
5. [x] Expo Web Authorization CORS preflight 허용
6. [~] D-Day legacy 500 이슈 재현 테스트 또는 endpoint 계약 정리
7. [ ] `GET /api/v1/tasks/search` 구현
8. [ ] Today 일괄 재정렬 API 구현
9. [ ] 반복/알림 계약 설계 확정 후 recurrence 모델링
