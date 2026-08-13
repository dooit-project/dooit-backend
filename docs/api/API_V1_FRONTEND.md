# ToDoLab v1 Frontend API

Last updated: 2026-08-10

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
  accountType: 'GUEST' | 'REGISTERED';
  email: string | null;
  displayName: string | null;
  role: 'USER' | 'ADMIN';
  timeZone: string;
  createdAt: string;
  updatedAt: string | null;
};
```

### 게스트 계정 생성

```http
POST /api/v1/auth/guest
```

Request body는 없다. 서버가 새 `GUEST` 사용자와 access token을 발급한다. 성공 HTTP status는 `201 Created`다.

Response:

```ts
type TokenResponse = {
  tokenType: 'Bearer';
  accessToken: string;
  expiresAt: string; // 기본 31일 뒤
  user: {
    id: number;
    accountType: 'GUEST';
    email: null;
    displayName: null;
    role: 'USER';
    timeZone: string;
    createdAt: string;
    updatedAt: string | null;
  };
};
```

주의:

- 서버가 사용자 id를 생성하며 클라이언트가 지정할 수 없다.
- IP나 단말 정보가 같다는 이유로 기존 게스트 계정을 반환하지 않는다.
- 게스트 token에는 `accountType=GUEST` claim이 포함된다.
- 게스트 상태 회원가입 승격은 지원한다.
- 기존 계정 로그인 병합은 지원한다.
- 만료 게스트와 관련 owner 데이터 정리는 운영 스케줄러로 지원한다.
- 게스트 생성 rate limit은 기본 클라이언트 신호별 30건/1시간이며 초과 시 429/`11004`를 반환한다.
- 같은 guest user id를 유지하는 게스트 token 갱신은 만료 전 유효 token으로 지원한다.

### 게스트 token 갱신

```http
POST /api/v1/auth/guest/refresh
Authorization: Bearer <guest-access-token>
```

유효한 게스트 token이면 같은 guest user id를 유지하고 새 게스트 `TokenResponse`를 반환한다. 성공 HTTP status는 `200 OK`다.

주의:

- 갱신 가능 기간은 기존 guest token이 유효한 동안이다.
- 성공 시 `guestExpiresAt`은 새 게스트 token TTL 기준으로 연장된다.
- 만료된 guest token, 병합 완료 guest token, 정식 계정 token, 이미 정리된 guest는 401/`11002`로 처리한다.
- 갱신 실패 시 기존 게스트 row와 owner 데이터는 변경하지 않는다.
- 새로운 guest id 발급은 기존 게스트 데이터 복구 정책으로 사용하지 않는다.

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
  mergeResult: GuestMergeResultResponse | null;
};

type GuestMergeResultResponse = {
  tasks: number;
  schedules: number;
  ddayGoals: number;
  recurrenceSeries: number;
};
```

게스트 데이터 병합 로그인:

```http
POST /api/v1/auth/login
Authorization: Bearer <guest-access-token>
Content-Type: application/json
```

유효한 게스트 token이 있고 이메일/비밀번호 검증이 성공하면 게스트 owner 데이터를 대상 정식 계정으로 병합하고 정식 계정 `TokenResponse`를 반환한다.

병합 대상:

- Task와 일정, Today 순서, 완료 상태, 미룸 사유
- 반복 series, materialized occurrence, recurrence exception
- D-Day 목표와 Task-D-Day 연결 관계
- push device token, push notification history

정책:

- 정식 계정 콘텐츠는 유지하고 게스트 콘텐츠를 추가한다.
- 제목이나 날짜가 같다는 이유로 자동 중복 제거하지 않는다.
- target 계정에 같은 push device token이 이미 있으면 guest token row는 비활성화하고 중복 이전하지 않는다.
- 이메일/비밀번호 검증 실패 시 게스트 데이터는 변경하지 않는다.
- 같은 guest token으로 같은 target 계정 로그인을 재시도하면 중복 이전 없이 정식 token을 다시 반환한다.
- 병합 완료 후 기존 guest token은 `mergedIntoUserId`가 기록된 사용자이므로 owner API와 `/auth/me`에서 401 처리된다.
- 병합 성공 응답은 `mergeResult.tasks`, `mergeResult.schedules`, `mergeResult.ddayGoals`, `mergeResult.recurrenceSeries`를 포함한다.
- 일반 로그인처럼 병합이 없으면 `mergeResult`는 `null`이다.

게스트 상태 회원가입:

```http
POST /api/v1/auth/register
Authorization: Bearer <guest-access-token>
Content-Type: application/json
```

유효한 게스트 token이 있으면 새 user row를 만들지 않고 기존 게스트 user id를 유지한 채 `REGISTERED`로 승격한다. 성공 HTTP status는 `201 Created`이고 응답은 추가 로그인이 필요 없도록 `TokenResponse`다.

```ts
type GuestPromotionRegisterResponse = TokenResponse & {
  user: UserResponse & {
    accountType: 'REGISTERED';
  };
};
```

검증 실패 또는 이메일 중복이면 게스트 상태와 데이터는 유지된다. 승격 성공 후 기존 guest token은 DB의 현재 `accountType`과 token claim이 불일치하므로 owner API와 `/auth/me`에서 401 처리된다.

### 내 정보

```http
GET /api/v1/auth/me
Authorization: Bearer <accessToken>
```

Response:

```ts
type AuthenticatedUserResponse = {
  id: number;
  accountType: 'GUEST' | 'REGISTERED';
  email: string | null;
  displayName: string | null;
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

type TaskQuickCaptureRequest = {
  text: string; // 100자 이하
  referenceDate?: string | null; // YYYY-MM-DD, 생략하면 사용자 timezone의 오늘
  timeZone?: string | null; // IANA timezone, 생략하면 사용자 timezone
  defaultCategory?: string | null; // 30자 이하
};

type TaskQuickCaptureResponse = {
  task: TaskResponse;
  parsed: boolean;
  originalText: string;
  parsedDate: string | null; // YYYY-MM-DD
  parsedTime: string | null; // HH:mm:ss
  parsedType: TaskType;
  parsedRecurrenceFrequency: RecurrenceFrequency | null;
  parsedByDays: string[];
  timeZone: string;
};

type TaskTemplateRequest = {
  title: string; // 30자 이하
  description?: string | null; // 300자 이하
  type?: TaskType | null; // 생략하면 TODO
  category?: string | null; // 30자 이하
  allDay: boolean;
  defaultStartTime?: string | null; // HH:mm:ss
  defaultDurationMinutes?: number | null; // 1-1440, 생략하면 Task 생성 시 60분
  recurrenceFrequency?: RecurrenceFrequency | null;
  recurrenceInterval?: number | null; // 생략하면 1
  recurrenceByDays?: string[] | null; // 예: ['MO']
};

type TaskTemplateCreateTaskRequest = {
  targetDate?: string | null; // YYYY-MM-DD, 일정/반복 템플릿에서는 필수
  title?: string | null; // 30자 이하 override
  description?: string | null; // 300자 이하 override
  category?: string | null; // 30자 이하 override
};

type TaskTemplateResponse = {
  id: number;
  title: string;
  description: string | null;
  type: TaskType;
  category: string | null;
  allDay: boolean;
  defaultStartTime: string | null;
  defaultDurationMinutes: number | null;
  recurrenceFrequency: RecurrenceFrequency | null;
  recurrenceInterval: number;
  recurrenceByDays: string[];
  createdAt: string;
  updatedAt: string | null;
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

### 빠른 등록

```http
POST /api/v1/tasks/quick-capture
```

Request: `TaskQuickCaptureRequest`

Response: `TaskQuickCaptureResponse`

규칙:

- 현재 지원 범위는 `오늘`, `내일`, `모레`, `YYYY-MM-DD`, `M/D`, `오전/오후 N시`, `N시`, `매주 요일` 기반 규칙 파싱이다.
- 날짜 또는 시간이 파싱되면 `SCHEDULE`로 저장하고, 시간만 있으면 `referenceDate`를 날짜로 사용한다.
- 오전/오후가 없는 `1시`부터 `7시`까지는 모바일 확인 화면에서 조정하기 쉬운 낮 시간으로 해석한다. 예: `3시`는 `15:00:00`.
- 날짜만 있거나 시간 없는 반복은 종일 일정으로 저장한다.
- `매주 월요일 운동`처럼 반복 요일이 있으면 기준 날짜 이후 가장 가까운 해당 요일을 시작일로 사용하고 `WEEKLY` 반복을 생성한다.
- 파싱 가능한 날짜/시간/반복이 없으면 원문을 제목으로 하는 Inbox `TODO`로 저장하며 `parsed=false`를 반환한다.
- 제목이 30자를 넘으면 제목은 30자로 줄이고 원문은 description에 보존한다.
- 잘못된 날짜, 시간, timezone은 HTTP 400으로 응답한다.

예시:

```json
{
  "text": "내일 3시 출시 회의",
  "referenceDate": "2026-08-13",
  "timeZone": "Asia/Seoul",
  "defaultCategory": "업무"
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

- `recurrence`는 생성 전용 필드다. 기존 Task의 반복 규칙 자체를 바꾸는 요청은 이 API에서 HTTP 400이다.
- 기존 series rule 자체 변경은 `PUT /api/v1/tasks/{id}/recurrence-rule`을 사용한다.
- `recurrenceScope`를 생략하면 `THIS`다.
- 반복 Task가 아니면 `recurrenceScope`는 무시된다.
- `THIS`는 해당 occurrence row만 수정하고 `recurrenceException=MODIFIED`로 표시한다.
- `THIS_AND_FUTURE`는 현재 occurrence 날짜 이상으로 materialize된 같은 series occurrence를 수정한다.
- `ALL`은 materialize된 같은 series occurrence 전체를 수정한다.
- `THIS_AND_FUTURE`, `ALL`은 occurrence 날짜별로 요청 `startAt`/`endAt`의 시간을 유지해 날짜를 이동한다. 따라서 `startAt`이 필요하다.
- `THIS_AND_FUTURE`, `ALL` 범위에 이미 완료된 occurrence가 포함되면 제목/시간 등 일반 필드는 수정되지만 `status=DONE`, `completedAt`은 유지된다.

### 반복 Rule 변경

```http
PUT /api/v1/tasks/{id}/recurrence-rule
```

Request: `TaskRecurrenceRequest`

Response: `TaskResponse`

규칙:

- `{id}`는 변경 기준이 되는 반복 occurrence Task ID다.
- 선택 occurrence의 `occurrenceDate`를 `effectiveDate`로 사용한다.
- 선택 occurrence 이전 완료/예외 occurrence는 보존된다.
- 선택 occurrence 이후 일반 materialized occurrence는 새 rule 기준 재생성을 위해 정리된다.
- 완료 또는 `SKIPPED`, `MOVED`, `MODIFIED` 예외 occurrence는 임의 삭제하지 않는다.
- `PUT /api/v1/tasks/{id}`에 `recurrence`를 포함한 기존 요청은 계속 HTTP 400이다.

### 삭제

```http
DELETE /api/v1/tasks/{id}
DELETE /api/v1/tasks/{id}?recurrenceScope=THIS|THIS_AND_FUTURE|ALL
```

Response: `data: null`

반복 Task 삭제 범위:

- 반복 occurrence 건너뛰기는 별도 `skip` endpoint 없이 `DELETE /api/v1/tasks/{occurrenceId}?recurrenceScope=THIS`를 사용한다.
- `recurrenceScope`를 생략하면 `THIS`다.
- 반복 Task가 아니면 기존처럼 해당 Task를 삭제한다.
- 반복 occurrence 삭제는 같은 occurrence가 다시 materialize되지 않도록 row를 물리 삭제하지 않고 `recurrenceException=SKIPPED` marker로 남긴다.
- DELETE 응답 body에는 `SKIPPED` row를 내려주지 않는다. 응답은 공통 envelope의 `data: null`이다.
- 건너뛰기 후 Today/Calendar 조회와 `GET /api/v1/tasks/notification-candidates`에서는 해당 occurrence가 제외된다.
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

## 5. Task 템플릿 API

모든 `/api/v1/task-templates/**` 요청은 현재 로그인 사용자의 템플릿만 대상으로 한다. 다른 사용자의 템플릿 ID는 `TASK_TEMPLATE_NOT_FOUND`처럼 처리된다.

### 템플릿 생성

```http
POST /api/v1/task-templates
```

Request: `TaskTemplateRequest`

Response: `TaskTemplateResponse`

규칙:

- `type`을 생략하면 `TODO` 템플릿으로 저장한다.
- `SCHEDULE` 또는 반복 템플릿으로 Task를 생성하려면 `targetDate`가 필요하다.
- `allDay=true`이면 `defaultStartTime`을 함께 보낼 수 없다.
- `defaultDurationMinutes`는 1분 이상 1440분 이하이며, Task 생성 시 생략되어 있으면 60분을 사용한다.
- `recurrenceByDays`는 `MO`, `TU`, `WE`, `TH`, `FR`, `SA`, `SU`만 허용한다.
- 템플릿은 guest 승격 시 같은 사용자 ID로 유지되고, 기존 계정 로그인 병합과 만료 guest cleanup 대상에 포함된다.
- D-Day 연결과 공유 workspace 템플릿 정책은 아직 지원하지 않는다.

### 템플릿 목록

```http
GET /api/v1/task-templates
```

Response: `TaskTemplateResponse[]`

### 템플릿 단건

```http
GET /api/v1/task-templates/{id}
```

Response: `TaskTemplateResponse`

### 템플릿 수정

```http
PUT /api/v1/task-templates/{id}
```

Request: `TaskTemplateRequest`

Response: `TaskTemplateResponse`

### 템플릿 삭제

```http
DELETE /api/v1/task-templates/{id}
```

Response: `null`

### 템플릿 기반 Task 생성

```http
POST /api/v1/task-templates/{id}/tasks
```

Request: `TaskTemplateCreateTaskRequest`

Response: `TaskResponse`

예시:

```json
{
  "targetDate": "2026-08-17",
  "title": "월요일 운동"
}
```

응답은 기존 Task 생성과 동일한 `TaskResponse`다. 반복 템플릿이면 첫 occurrence에 `recurrenceSeriesId`, `occurrenceDate`, `originalOccurrenceDate`, `recurrence`가 포함된다.

## 6. Workspace API

모든 `/api/v1/workspaces/**` 요청은 현재 로그인 사용자의 ACTIVE membership을 기준으로 한다. 게스트 계정은 workspace 생성, 초대, 수락, 조회 대상에서 제외한다.

```ts
type WorkspaceRole = 'OWNER' | 'EDITOR' | 'VIEWER';
type WorkspaceMemberStatus = 'PENDING' | 'ACTIVE' | 'REMOVED';

type WorkspaceRequest = {
  name: string; // 50자 이하
  description?: string | null; // 300자 이하
};

type WorkspaceInviteRequest = {
  email: string;
  role?: WorkspaceRole | null; // OWNER는 초대 API에서 부여할 수 없음
};

type WorkspaceMemberUpdateRequest = {
  role?: WorkspaceRole | null;
  status?: WorkspaceMemberStatus | null;
};

type WorkspaceResponse = {
  id: number;
  name: string;
  description: string | null;
  createdByUserId: number;
  createdAt: string;
  updatedAt: string | null;
};

type WorkspaceMemberResponse = {
  id: number;
  workspaceId: number;
  userId: number;
  email: string;
  displayName: string;
  role: WorkspaceRole;
  status: WorkspaceMemberStatus;
  createdAt: string;
  updatedAt: string | null;
};
```

### Workspace 생성

```http
POST /api/v1/workspaces
```

Request: `WorkspaceRequest`

Response: `WorkspaceResponse`

생성자는 자동으로 `OWNER`/`ACTIVE` 멤버가 된다.

### Workspace 목록

```http
GET /api/v1/workspaces
```

Response: `WorkspaceResponse[]`

### Workspace 단건

```http
GET /api/v1/workspaces/{workspaceId}
```

Response: `WorkspaceResponse`

### Workspace 수정/삭제

```http
PUT /api/v1/workspaces/{workspaceId}
DELETE /api/v1/workspaces/{workspaceId}
```

수정/삭제는 `OWNER`만 가능하다.

### 멤버 초대

```http
POST /api/v1/workspaces/{workspaceId}/members
```

Request: `WorkspaceInviteRequest`

Response: `WorkspaceMemberResponse`

규칙:

- 등록 사용자 email만 초대할 수 있다.
- `OWNER` role은 초대 API에서 부여할 수 없다.
- 초대된 멤버는 `PENDING` 상태이며 workspace 데이터 조회 권한이 없다.

### 멤버 목록

```http
GET /api/v1/workspaces/{workspaceId}/members
```

Response: `WorkspaceMemberResponse[]`

현재는 ACTIVE 멤버만 반환한다.

### 멤버 수정/초대 수락

```http
PATCH /api/v1/workspaces/{workspaceId}/members/{memberId}
```

Request: `WorkspaceMemberUpdateRequest`

Response: `WorkspaceMemberResponse`

초대받은 사용자는 자기 `PENDING` membership에 `{"status":"ACTIVE"}`를 보내 초대를 수락한다. 그 외 role/status 변경은 `OWNER`만 가능하다.

### 멤버 제거

```http
DELETE /api/v1/workspaces/{workspaceId}/members/{memberId}
```

Response: `null`

`OWNER`가 멤버를 제거하거나 멤버가 본인 membership에서 나갈 수 있다. workspace에는 최소 1명의 ACTIVE OWNER가 필요하다.

### Workspace Task 생성

```http
POST /api/v1/workspaces/{workspaceId}/tasks
```

Request: `TaskRequest`

Response: `TaskResponse`

규칙:

- `OWNER`, `EDITOR`만 생성할 수 있다.
- 생성된 Task는 `WORKSPACE` scope로 저장되며 기존 개인 Task API에는 반환되지 않는다.
- `recurrence`가 있는 workspace Task 생성은 아직 HTTP 400이다.
- D-Day 연결은 workspace D-Day API가 열리기 전까지 지원하지 않는다.

### Workspace Task 범위 조회

```http
GET /api/v1/workspaces/{workspaceId}/tasks?type=DAY|WEEK|MONTH&taskType=SCHEDULE&date=YYYY-MM-DD
```

Response: `TaskResponse[]`

`ACTIVE` 멤버가 조회할 수 있다. query 규칙은 개인 Task 범위 조회와 동일하다.

### Workspace Task 단건 조회

```http
GET /api/v1/workspaces/{workspaceId}/tasks/{taskId}
```

Response: `TaskResponse`

`ACTIVE` 멤버가 조회할 수 있다. 다른 workspace의 Task ID나 workspace scope 밖 Task ID는 `TASK_NOT_FOUND`처럼 처리된다.

아직 제공하지 않는 workspace 하위 API:

- workspace Task 수정/삭제
- workspace 반복 Task materialize
- workspace D-Day 생성/조회/삭제
- workspace 알림 후보

기존 개인 API인 `/api/v1/tasks/**`, `/api/v1/dday-goals/**`는 `PERSONAL` scope 데이터만 반환한다. workspace scope 데이터는 workspace 하위 API가 열리기 전까지 프론트에서 조회하거나 수정할 수 없다.

## 7. D-Day API

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

## 8. Task 통합 검색

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

## 9. 아직 프론트에서 의존하면 안 되는 계약

아래는 모바일 문서에 요구사항이 있으나 현재 백엔드 v1에는 없다.

- refresh token API
- 서버 push 알림 발송 API

## 10. 모바일 전환 체크리스트

- [ ] 로그인 성공 시 `accessToken` 저장
- [ ] 모든 v1 요청에 `Authorization: Bearer <accessToken>` 추가
- [ ] Task path를 `/api/tasks`에서 `/api/v1/tasks`로 전환
- [ ] D-Day path를 `/api/ddays`에서 `/api/v1/dday-goals`로 전환
- [ ] legacy `/api/ddays/**` alias 추가를 기다리지 않고 v1 D-Day 계약으로 전환
- [ ] D-Day Today Task 생성은 3단계 workflow 대신 `POST /api/v1/dday-goals/{id}/tasks` 사용
- [ ] 검색 UI는 `GET /api/v1/tasks/search` 사용
- [ ] Today drag-and-drop 저장은 `PUT /api/v1/tasks/today-order` 사용
- [ ] 반복 생성 UI는 `POST /api/v1/tasks`의 `recurrence` 하위 객체 사용
- [ ] 반복 기존 rule 수정 UI는 `PUT /api/v1/tasks/{id}/recurrence-rule`로 분리
- [ ] 로컬 알림 예약은 `GET /api/v1/tasks/notification-candidates` 응답만 기준으로 구성
- [ ] 401 응답 시 로그인 화면으로 이동하거나 세션 만료 안내
- [ ] 403 응답 시 재로그인 반복 대신 권한 오류 표시
- [ ] 서버 push 알림 UI는 push API 구현 전까지 실제 저장 기능처럼 열지 않음

## 11. Legacy API 정책

- 모바일 신규 연동 기준은 `/api/v1/**`다.
- legacy `/api/tasks/**`, `/api/ddays/**`는 웹 화면과 과거 호환 범위로 유지한다.
- 모바일 호환을 위해 legacy `/api/ddays/{id}`, `/api/ddays/{id}/tasks` alias를 새로 추가하지 않는다.
- D-Day 단건 조회는 `GET /api/v1/dday-goals/{id}`, D-Day 기반 Today Task 생성은 `POST /api/v1/dday-goals/{id}/tasks`를 사용한다.
