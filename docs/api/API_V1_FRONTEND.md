# ToDoLab v1 Frontend API

Last updated: 2026-07-25

이 문서는 모바일/프론트엔드가 실제 연동할 수 있는 현재 백엔드 v1 API 계약이다.

## 1. 공통

환경별 base URL과 CORS 기준은 [`../ops/ENVIRONMENT_INTEGRATION.md`](../ops/ENVIRONMENT_INTEGRATION.md)를 따른다.

Base path:

```text
/api/v1
```

인증이 필요한 요청:

```http
Authorization: Bearer <accessToken>
```

공통 request header:

```http
Content-Type: application/json
Accept: application/json
```

공통 성공 응답:

```ts
type ApiResponse<T> = {
  status: 'success';
  data: T;
  error: null;
  timestamp: string; // LocalDateTime, 예: 2026-07-14T09:30:00
};
```

공통 실패 응답:

```ts
type ApiErrorResponse = {
  status: 'fail';
  data: null;
  error: {
    code: number;
    message: string;
  };
  timestamp: string;
};
```

날짜/시간:

- `LocalDate`: `YYYY-MM-DD`
- `LocalDateTime`: offset 없는 `YYYY-MM-DDTHH:mm:ss`
- 서비스 기준 시간대는 `Asia/Seoul`

오류 코드:

- 상세 오류 코드는 [`API_ERROR_CODES.md`](./API_ERROR_CODES.md)를 원본으로 한다.
- 인증/인가 정책은 [`AUTH_CONTRACT.md`](./AUTH_CONTRACT.md)를 원본으로 한다.
- 401은 토큰 없음/만료/위변조, 403은 인증 후 권한 부족이다.
- 삭제 성공 응답은 `status=success`, `data=null`, `error=null`이다.

## 2. 인증

### 회원가입

```http
POST /api/v1/auth/register
Content-Type: application/json
```

Request:

```ts
type RegisterRequest = {
  email: string;
  password: string; // 8-72자
  displayName: string; // 50자 이하
};
```

Response:

```ts
type UserResponse = {
  id: number;
  email: string;
  displayName: string;
  role: 'USER' | 'ADMIN';
  timeZone: string;
  createdAt: string;
  updatedAt: string | null;
};
```

### 로그인

```http
POST /api/v1/auth/login
Content-Type: application/json
```

Request:

```ts
type LoginRequest = {
  email: string;
  password: string;
};
```

Response:

```ts
type TokenResponse = {
  tokenType: 'Bearer';
  accessToken: string;
  expiresAt: string;
  user: UserResponse;
};
```

### 내 정보

```http
GET /api/v1/auth/me
Authorization: Bearer <accessToken>
```

Response:

```ts
type AuthenticatedUserResponse = {
  id: number;
  email: string;
  role: string;
};
```

### 내 timezone 변경

```http
PATCH /api/v1/users/me/time-zone
Authorization: Bearer <accessToken>
Content-Type: application/json
```

Request:

```ts
type UserTimeZoneRequest = {
  timeZone: string; // IANA timezone ID, 50자 이하
};
```

Response: `UserResponse`

주의:

- `timeZone`은 `Asia/Seoul`, `America/New_York` 같은 유효한 IANA Zone ID여야 한다.
- 저장된 사용자 timezone은 Today/Calendar/알림 후보 조회의 일정 overlap 날짜 경계에 적용된다.
- `targetDate`, D-Day 날짜처럼 날짜만 저장되는 필드는 요청 날짜 값 자체로 비교한다.
- timezone 변경 시 기존 반복 occurrence를 재계산하지는 않는다.

## 3. Task 타입

```ts
type TaskType = 'SCHEDULE' | 'TODO' | 'IDEA';
type TaskStatus = 'INBOX' | 'TODAY' | 'DONE';
type DeferReason = 'TOO_BIG' | 'NOT_NEEDED_NOW' | 'AVOIDING' | 'NO_DEADLINE' | 'WAITING_OTHER' | 'ETC';
type TodayOrderDirection = 'UP' | 'DOWN';
type RecurrenceExceptionType = 'SKIPPED' | 'MOVED' | 'MODIFIED';
type RecurrenceFrequency = 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'YEARLY';

type TaskResponse = {
  id: number;
  type: TaskType;
  title: string;
  description: string | null;
  startAt: string | null;
  endAt: string | null;
  allDay: boolean;
  unscheduled: boolean;
  category: string | null;
  status: TaskStatus;
  plannedDate: string | null;
  targetDate: string | null;
  todayOrder: number | null;
  completedAt: string | null;
  carryOverCount: number;
  staleCarryOver: boolean;
  deferReason: DeferReason | null;
  deferReasonLabel: string | null;
  ddayGoalId: number | null;
  ddayGoalTitle: string | null;
  ddayGoalTargetDate: string | null;
  ddayDaysLeft: number | null;
  recurrenceSeriesId: number | null;
  occurrenceDate: string | null;
  originalOccurrenceDate: string | null;
  recurrenceException: RecurrenceExceptionType | null;
  recurrence: TaskRecurrenceResponse | null;
  createdAt: string | null;
  updatedAt: string | null;
};

type TaskRecurrenceResponse = {
  id: number;
  frequency: RecurrenceFrequency;
  interval: number;
  recurrenceRule: string;
  timeZone: string;
  recurrenceStartAt: string;
  recurrenceUntil: string | null;
  recurrenceCount: number | null;
};

type TaskRequest = {
  title: string; // 30자 이하
  description?: string | null; // 300자 이하
  type?: TaskType | null;
  startAt?: string | null;
  endAt?: string | null;
  category?: string | null; // 30자 이하
  allDay: boolean;
  recurrence?: TaskRecurrenceRequest | null;
};

type TaskRecurrenceRequest = {
  frequency: RecurrenceFrequency;
  interval?: number | null; // 생략하면 1
  recurrenceRule?: string | null; // 직접 지정 RRULE. 보통은 생략하고 byDays/byMonthDays 사용
  timeZone?: string | null; // 생략하면 Asia/Seoul
  recurrenceUntil?: string | null; // YYYY-MM-DD, recurrenceCount와 함께 사용 불가
  recurrenceCount?: number | null; // recurrenceUntil과 함께 사용 불가
  byDays?: string[] | null; // 예: ['TU']
  byMonthDays?: number[] | null; // 예: [15], 월말은 [-1]
};

type TaskNotificationCandidateResponse = {
  notificationKey: string;
  taskId: number;
  scheduledAt: string;
  recurrenceSeriesId: number | null;
  occurrenceDate: string | null;
  suppressLocalNotification: boolean;
  task: TaskResponse;
};

type PushPlatform = 'IOS' | 'ANDROID' | 'EXPO';

type PushDeviceTokenRequest = {
  platform: PushPlatform;
  deviceToken: string; // 512자 이하
  appVersion?: string | null; // 50자 이하
  deviceName?: string | null; // 100자 이하
};

type PushDeviceTokenResponse = {
  id: number;
  platform: PushPlatform;
  tokenSuffix: string;
  appVersion: string | null;
  deviceName: string | null;
  active: boolean;
  lastRegisteredAt: string;
  createdAt: string;
  updatedAt: string | null;
};

type PushNotificationSource = 'SERVER';
type PushNotificationStatus = 'SUCCESS' | 'FAILED';

type PushNotificationHistoryResponse = {
  id: number;
  source: PushNotificationSource;
  provider: 'EXPO';
  status: PushNotificationStatus;
  pushDeviceTokenId: number | null;
  tokenSuffix: string | null;
  taskId: number | null;
  recurrenceSeriesId: number | null;
  occurrenceDate: string | null;
  notificationKey: string;
  idempotencyKey: string;
  providerMessageId: string | null;
  errorCode: string | null;
  errorMessage: string | null;
  attemptedAt: string;
  createdAt: string;
};
```

Task 생성 규칙:

- `startAt`/`endAt`이 모두 없으면 `INBOX`로 저장된다.
- 날짜/시간이 있으면 `TODAY`로 저장되고 `targetDate`는 `startAt` 날짜다.
- `endAt`만 보낼 수 없다.
- `endAt`은 `startAt` 이후여야 한다.
- `allDay=true`이면 `startAt`, `endAt`은 모두 자정이어야 한다.
- 날짜 없는 Task에는 `allDay=true`를 사용할 수 없다.
- `recurrence`가 있으면 `startAt`은 필수다.
- `recurrence.interval`은 생략하면 `1`이다.
- `recurrence.timeZone`은 생략하면 `Asia/Seoul`이다.
- `recurrence.recurrenceUntil`과 `recurrence.recurrenceCount`는 함께 사용할 수 없다.
- `recurrence.recurrenceRule`을 생략하면 `frequency`, `interval`, `byDays`, `byMonthDays`, `recurrenceUntil`, `recurrenceCount`로 RRULE을 생성한다.
- `byDays`를 지정한 `WEEKLY` 반복은 `startAt` 날짜의 요일이 `byDays`에 포함되어야 한다.
- `byMonthDays`를 지정한 `MONTHLY`, `YEARLY` 반복은 `startAt` 날짜가 `byMonthDays`에 포함되어야 한다. 월말은 `-1`이다.
- `BYDAY`는 `WEEKLY`, `BYMONTHDAY`는 `MONTHLY` 또는 `YEARLY`에서만 지원한다.

Task 응답 nullable/default 규칙:

- 생성/조회 응답에서 `id`, `type`, `title`, `allDay`, `unscheduled`, `status`, `carryOverCount`, `staleCarryOver`는 항상 내려온다.
- 저장된 Task의 `createdAt`은 내려온다. 일부 legacy 테스트/생성자 기반 응답에서는 null일 수 있으나 v1 API 응답에서는 non-null로 본다.
- 날짜 없는 Task는 `startAt`, `endAt`, `plannedDate`, `targetDate`, `todayOrder`, `completedAt`이 null이고 `status=INBOX`, `unscheduled=true`, `allDay=false`다.
- 날짜가 있는 Task는 생성 직후 `status=TODAY`, `targetDate=startAt 날짜`, `plannedDate=targetDate`, `unscheduled=false`다.
- `description`, `category`, `deferReason`, `deferReasonLabel`, D-Day 연결 필드, 반복 occurrence 필드는 값이 없으면 null이다.
- `updatedAt`은 생성 직후 null이고 수정 후 값이 생긴다.
- 반복 occurrence Task를 완료하면 해당 occurrence row만 `DONE`으로 바뀌며 `recurrenceSeriesId`, `occurrenceDate`, `originalOccurrenceDate`는 유지된다.

## 4. Task API

모든 `/api/v1/tasks/**` 요청은 현재 로그인 사용자의 Task만 대상으로 한다. 다른 사용자의 Task ID는 `TASK_NOT_FOUND`처럼 처리된다.

### 생성

```http
POST /api/v1/tasks
```

Request: `TaskRequest`

Response: `TaskResponse`

매주 화요일 9시 회의 예시:

```json
{
  "title": "주간 회의",
  "description": "화요일 싱크",
  "type": "SCHEDULE",
  "startAt": "2026-07-07T09:00:00",
  "endAt": "2026-07-07T10:00:00",
  "category": "업무",
  "allDay": false,
  "recurrence": {
    "frequency": "WEEKLY",
    "interval": 1,
    "byDays": ["TU"],
    "recurrenceCount": 10
  }
}
```

반복 생성 응답의 첫 occurrence에는 `recurrenceSeriesId`, `occurrenceDate`, `originalOccurrenceDate`, `recurrence`가 포함된다. 이후 범위 조회, Today 조회, 월간 조회 시 누락된 occurrence가 materialize되어 같은 `recurrenceSeriesId`와 `recurrence`로 반환된다.

### 단건 조회

```http
GET /api/v1/tasks/{id}
```

Response: `TaskResponse`

### 범위 조회

```http
GET /api/v1/tasks?type=DAY|WEEK|MONTH&taskType=TODO|SCHEDULE|IDEA&date=...
```

Query:

- `type`: `DAY`, `WEEK`, `MONTH`
- `taskType`: Task 종류. 생략 시 현재 백엔드 기본값 정책을 따른다.
- `date`:
  - `DAY`, `WEEK`: `YYYY-MM-DD`
  - `MONTH`: `YYYY-MM`. `YYYY-MM-DD`는 HTTP 400이다.

Response: `TaskResponse[]`

### Today 조회

```http
GET /api/v1/tasks/today?date=YYYY-MM-DD
```

Response: `TaskResponse[]`

현재 동작:

- `targetDate`가 요청 날짜인 `TODAY` Task를 반환한다.
- 요청 날짜의 사용자 timezone 경계와 겹치는 `SCHEDULE`도 반환한다.
- 여러 날 일정은 날짜별 복제 row가 아니라 원본 `id`로 한 번 반환한다.
- `endAt`은 exclusive 경계로 처리한다. 예: `endAt=2026-07-14T00:00:00`이면 7월 14일에는 점유하지 않는다.

### Inbox 조회

```http
GET /api/v1/tasks/inbox
```

Response: `TaskResponse[]`

### 미정 일정 조회

```http
GET /api/v1/tasks/unscheduled
```

Response: `TaskResponse[]`

### Today 추천

```http
GET /api/v1/tasks/today/recommendations?date=YYYY-MM-DD
```

Response:

```ts
type TaskRecommendationResponse = {
  task: TaskResponse;
  reason: string;
};
```

### 로컬 알림 후보

```http
GET /api/v1/tasks/notification-candidates?from=YYYY-MM-DD&to=YYYY-MM-DD
```

Response: `TaskNotificationCandidateResponse[]`

조건:

- `from`, `to`는 필수이며 양끝 날짜를 포함한다.
- 조회 범위는 최대 31일이다.
- 사용자 timezone 기준 범위 안의 반복 occurrence는 백엔드가 materialize한 뒤 반환한다.
- `status=TODAY`, `startAt != null`, `completedAt == null`, `recurrenceException != SKIPPED`인 Task만 반환한다.
- `notificationKey`는 단건 Task면 `task:{taskId}`, 반복 occurrence면 `recurrence:{recurrenceSeriesId}:{occurrenceDate}` 형식이다.
- `suppressLocalNotification=true`면 서버 push가 활성화된 상태이므로 모바일은 해당 후보의 로컬 알림 예약을 건너뛴다.
- 실제 알림 예약/취소는 모바일 로컬 알림 책임이다.

### Push Token 등록/조회/해제

```http
POST /api/v1/push-tokens
GET /api/v1/push-tokens
DELETE /api/v1/push-tokens/{id}
```

Request: `PushDeviceTokenRequest`

Response:

- `POST`: `PushDeviceTokenResponse`
- `GET`: `PushDeviceTokenResponse[]`
- `DELETE`: `data: null`

규칙:

- 같은 사용자와 `deviceToken` 조합을 다시 등록하면 기존 row를 갱신하고 활성화한다.
- 응답은 실제 `deviceToken` 전체를 반환하지 않고 `tokenSuffix`만 반환한다.
- `DELETE`는 물리 삭제가 아니라 비활성화다.
- 이 API는 서버 push 발송을 수행하지 않는다. 발송과 실패 토큰 정리는 별도 계약이다.

### Push 알림 전송 이력 조회

```http
GET /api/v1/push-notification-histories?limit=50
```

Response: `PushNotificationHistoryResponse[]`

규칙:

- `limit`은 선택이며 1 이상 100 이하이다. 기본값은 50이다.
- 로그인 사용자 본인의 서버 push 전송 이력만 최신순으로 반환한다.
- 응답은 실제 `deviceToken` 전체를 반환하지 않고 `tokenSuffix`만 반환한다.
- `idempotencyKey`는 서버 push 중복 발송 방지 기준이며, 단건 Task는 `SERVER:{taskId}`, 반복 occurrence는 `SERVER:{recurrenceSeriesId}:{occurrenceDate}` 형식을 사용한다.

### 지난 미완료

```http
GET /api/v1/tasks/stale?date=YYYY-MM-DD
GET /api/v1/tasks/overdue?date=YYYY-MM-DD
```

Response: `TaskResponse[]`

`stale`의 `date`는 생략 가능하다. 생략 시 서버 현재 날짜 기준이다.

### 완료 조회

```http
GET /api/v1/tasks/done?date=YYYY-MM-DD
```

Response: `TaskResponse[]`

### 수정

```http
PUT /api/v1/tasks/{id}
PUT /api/v1/tasks/{id}?recurrenceScope=THIS|THIS_AND_FUTURE|ALL
```

Request: `TaskRequest`

Response: `TaskResponse`

반복 Task 수정 범위:

- `recurrence`는 생성 전용 필드다. 기존 Task의 반복 규칙 자체를 바꾸는 요청은 현재 HTTP 400이다.
- `recurrenceScope`를 생략하면 `THIS`다.
- 반복 Task가 아니면 `recurrenceScope`는 무시된다.
- `THIS`는 해당 occurrence row만 수정하고 `recurrenceException=MODIFIED`로 표시한다.
- `THIS_AND_FUTURE`는 현재 occurrence 날짜 이상으로 materialize된 같은 series occurrence를 수정한다.
- `ALL`은 materialize된 같은 series occurrence 전체를 수정한다.
- `THIS_AND_FUTURE`, `ALL`은 occurrence 날짜별로 요청 `startAt`/`endAt`의 시간을 유지해 날짜를 이동한다. 따라서 `startAt`이 필요하다.
- `THIS_AND_FUTURE`, `ALL` 범위에 이미 완료된 occurrence가 포함되면 제목/시간 등 일반 필드는 수정되지만 `status=DONE`, `completedAt`은 유지된다.

### 삭제

```http
DELETE /api/v1/tasks/{id}
DELETE /api/v1/tasks/{id}?recurrenceScope=THIS|THIS_AND_FUTURE|ALL
```

Response: `data: null`

반복 Task 삭제 범위:

- `recurrenceScope`를 생략하면 `THIS`다.
- 반복 Task가 아니면 기존처럼 해당 Task를 삭제한다.
- 반복 occurrence 삭제는 같은 occurrence가 다시 materialize되지 않도록 row를 물리 삭제하지 않고 `recurrenceException=SKIPPED` marker로 남긴다.
- `THIS_AND_FUTURE`는 현재 occurrence 날짜 이상, `ALL`은 같은 series 전체를 `SKIPPED`로 표시한다.
- `THIS_AND_FUTURE`, `ALL` 삭제는 아직 materialize되지 않은 미래 occurrence가 다시 생성되지 않도록 series 종료일도 함께 줄인다.

### Today 이동

```http
PATCH /api/v1/tasks/{id}/today?date=YYYY-MM-DD
```

Response: `TaskResponse`

### Inbox 이동

```http
PATCH /api/v1/tasks/{id}/inbox
```

Response: `TaskResponse`

### 완료

```http
PATCH /api/v1/tasks/{id}/done
PATCH /api/v1/tasks/{id}/done?completedAt=YYYY-MM-DDTHH:mm:ss
```

Response: `TaskResponse`

### 완료 취소

```http
PATCH /api/v1/tasks/{id}/done/cancel?date=YYYY-MM-DD
```

Response: `TaskResponse`

### 이월

```http
PATCH /api/v1/tasks/{id}/carry-over?date=YYYY-MM-DD
```

Response: `TaskResponse`

### Today 한 칸 재정렬

```http
PATCH /api/v1/tasks/{id}/today-order?date=YYYY-MM-DD&direction=UP|DOWN
```

Response: `TaskResponse`

주의:

- 현재 API는 한 칸씩 이동하는 호환 API다.

### Today 일괄 재정렬

```http
PUT /api/v1/tasks/today-order
```

Request: `TodayOrderRequest`

```json
{
  "date": "2026-07-15",
  "orderedTaskIds": [3, 1, 2]
}
```

Response: `TaskResponse[]`

동작:

- 요청 날짜의 일정이 아닌 Today Task 전체 순서를 한 번에 저장한다.
- `orderedTaskIds`는 현재 재정렬 대상 Task ID 전체를 중복 없이 포함해야 한다.
- 저장은 하나의 transaction에서 처리한다.
- 성공 응답 순서는 저장된 `todayOrder` 순서와 같다.
- 저장 직후 `GET /api/v1/tasks/today?date=YYYY-MM-DD`에서 일정이 아닌 Today Task는 같은 순서로 반환된다.
- `SCHEDULE`은 Today 조회에는 포함될 수 있지만 drag-and-drop 실행 순서 저장 대상에서는 제외한다.

오류:

- 중복 ID, 빈 목록, null ID는 HTTP 400.
- 누락 ID, 다른 날짜 ID, 완료 ID, 일정 ID, 다른 사용자 ID처럼 현재 재정렬 대상과 요청 ID 집합이 다르면 HTTP 409, error code `20002`.

### 미룬 이유

```http
PATCH /api/v1/tasks/{id}/defer-reason?reason=TOO_BIG|NOT_NEEDED_NOW|AVOIDING|NO_DEADLINE|WAITING_OTHER|ETC
DELETE /api/v1/tasks/{id}/defer-reason
```

Response: `TaskResponse`

### D-Day 연결

```http
PATCH /api/v1/tasks/{id}/dday-goal?ddayGoalId={goalId}
DELETE /api/v1/tasks/{id}/dday-goal
```

Response: `TaskResponse`

## 5. D-Day API

모든 `/api/v1/dday-goals/**` 요청은 현재 로그인 사용자의 D-Day 목표만 대상으로 한다.

```ts
type DdayGoalRequest = {
  title: string; // 50자 이하
  targetDate: string; // YYYY-MM-DD
};

type DdayGoalResponse = {
  id: number;
  title: string;
  targetDate: string;
  daysLeft: number;
  createdAt: string;
};

type DdayGoalTaskRequest = {
  title: string; // 30자 이하
  date: string; // YYYY-MM-DD
};
```

### 목표 생성

```http
POST /api/v1/dday-goals
```

Request: `DdayGoalRequest`

Response: `DdayGoalResponse`

### 목표 목록

```http
GET /api/v1/dday-goals
```

Response: `DdayGoalResponse[]`

### 목표 단건

```http
GET /api/v1/dday-goals/{id}
```

Response: `DdayGoalResponse`

### 목표 연결 Task 목록

```http
GET /api/v1/dday-goals/{id}/tasks
```

Response: `TaskResponse[]`

### 목표 기반 Today Task 생성

```http
POST /api/v1/dday-goals/{id}/tasks
```

Request: `DdayGoalTaskRequest`

Response: `TaskResponse`

동작:

- 지정 D-Day 목표에 연결된 `TODO` Task를 만든다.
- `date` 기준으로 Today에 추가한다.
- 생성, 목표 연결, Today 이동, `todayOrder` 배정을 하나의 트랜잭션으로 처리한다.

### 목표 삭제

```http
DELETE /api/v1/dday-goals/{id}
```

Response: `data: null`

동작:

- 목표는 삭제한다.
- 연결된 Task는 삭제하지 않고 D-Day 연결만 해제한다.

리소스 삭제 응답 규칙:

- v1 리소스 삭제 endpoint인 `DELETE /api/v1/tasks/{id}`, `DELETE /api/v1/dday-goals/{id}`는 성공 시 공통 envelope의 `data`를 `null`로 반환한다.
- `DELETE /api/v1/tasks/{id}/defer-reason`, `DELETE /api/v1/tasks/{id}/dday-goal`은 Task 리소스 삭제가 아니라 Task 수정이므로 `TaskResponse`를 반환한다.

## 6. Task 통합 검색

```http
GET /api/v1/tasks/search
```

Query parameters:

| 이름 | 값 |
| --- | --- |
| `q` | 제목/설명 부분 검색어. 한글 검색과 영문 대소문자 무시 검색을 지원한다. |
| `statuses` | `INBOX`, `TODAY`, `DONE`. 반복 파라미터 또는 콤마 구분을 지원한다. |
| `taskTypes` | `TODO`, `SCHEDULE`, `IDEA`. 반복 파라미터 또는 콤마 구분을 지원한다. |
| `category` | 카테고리명 exact match. |
| `ddayGoalId` | 연결된 D-Day 목표 ID. |
| `hasDday` | D-Day 목표 연결 여부. |
| `allDay` | 종일 일정 여부. |
| `dateField` | `PLANNED`, `START`, `TARGET`, `COMPLETED`, `CREATED`, `UPDATED`. 기본값은 `PLANNED`. |
| `dateFrom`, `dateTo` | `dateField` 기준 날짜 범위. `YYYY-MM-DD`, 양 끝 포함. |
| `sort` | `RELEVANT_DATE_ASC`, `RELEVANT_DATE_DESC`, `CREATED_AT_ASC`, `CREATED_AT_DESC`, `UPDATED_AT_ASC`, `UPDATED_AT_DESC`. 기본값은 `RELEVANT_DATE_ASC`. |
| `cursor` | 이전 응답의 `nextCursor`. |
| `limit` | 1 이상 100 이하. 기본값은 50. |

Response: `TaskSearchResponse`

```json
{
  "items": [
    {
      "task": { "id": 1, "title": "출시 회의" },
      "relevantDate": "2026-07-22",
      "dateSource": "TARGET_DATE"
    }
  ],
  "nextCursor": "50",
  "limit": 50
}
```

`dateSource` 값은 `TARGET_DATE`, `START_AT`, `COMPLETED_AT`, `CREATED_AT`, `UPDATED_AT`, `NONE` 중 하나다. 잘못된 enum, `dateFrom > dateTo`, 잘못된 cursor, 범위를 벗어난 `limit`은 HTTP 400으로 응답한다.

Cursor 기준:

- 현재 cursor는 마지막으로 받은 항목의 `task.id` 문자열이다.
- `nextCursor=null`이면 다음 페이지가 없다.
- 다음 페이지 요청은 이전 응답의 `nextCursor`를 그대로 보낸다.
- cursor Task가 더 이상 검색 조건에 포함되지 않으면 HTTP 400이다.
- 중간에 Task가 생성/수정/삭제되어도 이전 페이지 마지막 항목 이후부터 이어서 조회하므로 offset shift 중복/누락을 피한다.

## 7. 아직 프론트에서 의존하면 안 되는 계약

아래는 모바일 문서에 요구사항이 있으나 현재 백엔드 v1에는 없다.

- refresh token API
- 서버 push 알림 발송 API
- 알림 전송 이력 API

## 8. 모바일 전환 체크리스트

- [ ] 로그인 성공 시 `accessToken` 저장
- [ ] 모든 v1 요청에 `Authorization: Bearer <accessToken>` 추가
- [ ] Task path를 `/api/tasks`에서 `/api/v1/tasks`로 전환
- [ ] D-Day path를 `/api/ddays`에서 `/api/v1/dday-goals`로 전환
- [ ] legacy `/api/ddays/**` alias 추가를 기다리지 않고 v1 D-Day 계약으로 전환
- [ ] D-Day Today Task 생성은 3단계 workflow 대신 `POST /api/v1/dday-goals/{id}/tasks` 사용
- [ ] 검색 UI는 `GET /api/v1/tasks/search` 사용
- [ ] Today drag-and-drop 저장은 `PUT /api/v1/tasks/today-order` 사용
- [ ] 반복 생성 UI는 `POST /api/v1/tasks`의 `recurrence` 하위 객체 사용
- [ ] 반복 기존 rule 수정 UI는 숨기고 일반 필드 수정 scope만 노출
- [ ] 로컬 알림 예약은 `GET /api/v1/tasks/notification-candidates` 응답만 기준으로 구성
- [ ] 401 응답 시 로그인 화면으로 이동하거나 세션 만료 안내
- [ ] 403 응답 시 재로그인 반복 대신 권한 오류 표시
- [ ] 서버 push 알림 UI는 push API 구현 전까지 실제 저장 기능처럼 열지 않음

## 9. Legacy API 정책

- 모바일 신규 연동 기준은 `/api/v1/**`다.
- legacy `/api/tasks/**`, `/api/ddays/**`는 웹 화면과 과거 호환 범위로 유지한다.
- 모바일 호환을 위해 legacy `/api/ddays/{id}`, `/api/ddays/{id}/tasks` alias를 새로 추가하지 않는다.
- D-Day 단건 조회는 `GET /api/v1/dday-goals/{id}`, D-Day 기반 Today Task 생성은 `POST /api/v1/dday-goals/{id}/tasks`를 사용한다.
