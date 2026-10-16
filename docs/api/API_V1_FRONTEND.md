# Dooit v1 Frontend API

Last updated: 2026-10-16

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

생성 API 멱등성 계약이 적용된 endpoint는 아래 header를 추가로 사용할 수 있다. 현재 CORS preflight는 이 header를 허용한다.

```http
Idempotency-Key: <client-generated-key>
```

공통 response/cache header:

```http
Cache-Control: no-store, no-cache, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
```

멱등성 replay 응답을 도입할 때 사용할 `Idempotency-Replayed` response header는 CORS expose header에 포함되어 있다.

생성 요청 멱등성:

- 아래 생성 API는 선택적 `Idempotency-Key` header를 지원한다.
- 기준은 인증 사용자 또는 게스트 생성 client signal, HTTP method, request path, `Idempotency-Key`다.
- 같은 key와 같은 payload는 resource를 다시 만들지 않고 최초 status/body를 반환한다.
- replay 응답은 `Idempotency-Replayed: true` header를 포함한다.
- 같은 key와 다른 payload는 HTTP 409와 `10004`를 반환한다.
- replay 저장 기간은 최소 24시간이다.

대상 endpoint:

```text
POST /api/v1/auth/guest
POST /api/v1/tasks
POST /api/v1/tasks/quick-capture
POST /api/v1/task-templates
POST /api/v1/task-templates/{id}/tasks
POST /api/v1/dday-goals
POST /api/v1/dday-goals/{id}/tasks
POST /api/v1/workspaces
POST /api/v1/workspaces/{workspaceId}/members
POST /api/v1/workspaces/{workspaceId}/tasks
POST /api/v1/workspaces/{workspaceId}/dday-goals
POST /api/v1/personal-systems
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
  refreshToken: string;
  refreshExpiresAt: string;
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
- 만료된 guest token, 병합 완료 guest token, 이미 정리된 guest는 401/`11010`으로 처리한다.
- 정식 계정 token처럼 guest token이 아닌 인증 실패는 401/`11002`로 처리한다.
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
  refreshToken: string | null;
  refreshExpiresAt: string | null;
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

모바일 노출 정책:

- 기존 계정 로그인 병합이 성공했고 `mergeResult`가 있으면 연결 완료 화면에 이전된 전체 개수를 노출한다.
- 전체 개수는 `tasks + schedules + ddayGoals + recurrenceSeries` 합계다.
- 항목별 개수 노출은 선택 사항이며, 0인 항목은 숨길 수 있다.
- `mergeResult=null`이면 일반 로그인 완료로 처리하고 병합 count를 노출하지 않는다.

### 등록 계정 token 갱신

```http
POST /api/v1/auth/refresh
Content-Type: application/json
```

Request:

```ts
type RefreshRequest = {
  refreshToken: string;
};
```

Response는 새 `accessToken`, 새 `refreshToken`, `refreshExpiresAt`을 포함하는 `TokenResponse`다.

주의:

- refresh 성공 시 기존 refresh token은 회전되어 다시 사용할 수 없다.
- 이미 회전되었거나 폐기된 refresh token을 다시 보내면 같은 family의 session이 폐기되고 401/`11009`를 반환한다.
- 만료된 refresh token은 401/`11008`, 존재하지 않거나 올바르지 않은 refresh token은 401/`11007`이다.

### 로그아웃

```http
POST /api/v1/auth/logout
Authorization: Bearer <access-token>
Content-Type: application/json
```

Request:

```ts
type LogoutRequest = {
  refreshToken?: string | null;
};
```

응답은 `data=null`이다. `refreshToken`을 보내면 해당 session만 폐기하고, 생략하면 현재 access token 사용자 기준 활성 refresh session을 모두 폐기한다.

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

### 비밀번호 재설정

```http
POST /api/v1/auth/password-reset/request
POST /api/v1/auth/password-reset/verify
POST /api/v1/auth/password-reset/confirm
```

모두 인증 없이 호출할 수 있다.

Request:

```ts
type PasswordResetRequest = {
  email: string;
};

type PasswordResetVerifyRequest = {
  token: string;
};

type PasswordResetConfirmRequest = {
  token: string;
  newPassword: string; // 8-72자
};
```

Response:

```ts
type PasswordResetRequestResponse = {
  requested: true;
  ttlSeconds: number; // 기본 1800
};

type PasswordResetVerifyResponse = {
  valid: true;
  maskedEmail: string;
};
```

정책:

- request는 가입 여부와 무관하게 HTTP 200과 `requested=true`를 반환한다.
- 등록 계정이면 backend가 reset link를 email로 발송한다.
- reset link 기본 형식은 `dooit://password-reset?token={token}`이다.
- token TTL은 기본 30분이다.
- request rate limit은 normalized email 기준 기본 5건/1시간이며 초과 시 429/`11006`이다.
- token이 없거나 만료되었거나 이미 사용되었으면 verify/confirm은 400/`11005`를 반환한다.
- confirm 성공 응답은 `data=null`이다.
- confirm 성공 후 기존 access token은 즉시 폐기하지 않고 기존 JWT `exp`까지 유효할 수 있다.

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
type DailyPlanStatus = 'DRAFT' | 'CONFIRMED' | 'CLOSED';

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
  estimatedDurationMinutes: number | null;
  notificationEnabled: boolean;
  notifyAt: string | null;
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
  estimatedDurationMinutes?: number | null; // 5-1440
  allDay: boolean;
  notificationEnabled?: boolean | null; // 생략하면 true
  notifyAt?: string | null; // null이면 startAt 기준 알림
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
  defaultDurationMinutes?: number | null; // 5-1440, 생략하면 일정 endAt 계산 시 60분
  recurrenceFrequency?: RecurrenceFrequency | null;
  recurrenceInterval?: number | null; // 생략하면 1
  recurrenceByDays?: string[] | null; // 예: ['MO']
};

type TaskTemplateCreateTaskRequest = {
  targetDate?: string | null; // YYYY-MM-DD, 일정/반복 템플릿에서는 필수
  title?: string | null; // 30자 이하 override
  description?: string | null; // 300자 이하 override
  category?: string | null; // 30자 이하 override
  ddayGoalId?: number | null; // 생성된 Task에 연결할 개인 D-Day 목표 ID
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
  scheduledAt: string; // notifyAt이 있으면 notifyAt, 없으면 startAt
  notifyAt: string | null;
  recurrenceSeriesId: number | null;
  occurrenceDate: string | null;
  suppressLocalNotification: boolean;
  task: TaskResponse;
};

type TaskChecklistItemRequest = {
  title: string; // 30자 이하
};

type TaskChecklistItemOrderRequest = {
  orderedItemIds: number[]; // 현재 Task의 checklist item 전체 ID
};

type TaskChecklistItemResponse = {
  id: number;
  taskId: number;
  title: string;
  done: boolean;
  sortOrder: number;
  completedAt: string | null;
  createdAt: string;
  updatedAt: string | null;
};

type DailyPlanRequest = {
  focusTaskIds: number[]; // 같은 날짜의 미완료 개인 Today Task, 최대 3개
  status: DailyPlanStatus;
};

type DailyPlanResponse = {
  date: string; // YYYY-MM-DD
  status: DailyPlanStatus;
  focusTaskIds: number[];
  confirmedAt: string | null;
  closedAt: string | null;
  updatedAt: string | null;
};

type DailyPlanSummaryResponse = {
  date: string; // YYYY-MM-DD
  status: DailyPlanStatus;
  plannedFocusCount: number;
  completedCount: number;
  movedToOtherDateCount: number;
  movedToInboxCount: number;
  undecidedCount: number;
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

type TaskCategorySummaryResponse = {
  category: string | null; // null이면 미분류
  displayName: string;
  taskCount: number;
  inboxCount: number;
  todayCount: number;
  doneCount: number;
};
```

## 3-1. 일일 계획

프론트의 `오늘 계획 -> 실행 -> 하루 마감` 흐름을 위해 사용자가 확정한 focus Task와 계획 상태를 저장한다.

제공 API:

- `GET /api/v1/daily-plans/{date}`
- `PUT /api/v1/daily-plans/{date}`
- `GET /api/v1/daily-plans/{date}/summary`

규칙:

- 저장 전 조회는 빈 `DRAFT` 계획을 반환한다.
- `PUT`은 전체 교체 방식이다. 같은 요청을 반복해도 같은 focus 목록과 상태로 수렴한다.
- `focusTaskIds`는 로그인 사용자 소유의 개인 Task만 허용한다.
- `focusTaskIds`는 같은 날짜의 미완료 Today Task만 허용한다.
- `focusTaskIds`는 최대 3개이며 배열 순서가 집중 순서다.
- Task 완료, 삭제, Inbox 이동, 다른 날짜 이동 시 focus 목록에서 자동 제거된다.
- 1차 범위에서 workspace Task는 focus에 포함하지 않는다.
- summary는 계획 확정 시점의 focus Task snapshot을 기준으로 현재 상태를 집계한다.
- `completedCount`는 `DONE`, `movedToInboxCount`는 `INBOX`, `movedToOtherDateCount`는 계획일이 아닌 다른 날짜의 `TODAY`, `undecidedCount`는 아직 완료/이동/Inbox 처리되지 않은 항목이다.

후속 후보:

- B1: `POST /api/v1/daily-plans/{date}/apply` batch mutation
- B2: category entity/count/order 계약

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
- `estimatedDurationMinutes`는 nullable이며 값이 있으면 5분 이상 1440분 이하여야 한다.
- `estimatedDurationMinutes`는 `SCHEDULE`의 `startAt`/`endAt` 길이와 별개로 저장된다.

Task 응답 nullable/default 규칙:

- 생성/조회 응답에서 `id`, `type`, `title`, `allDay`, `unscheduled`, `status`, `carryOverCount`, `staleCarryOver`는 항상 내려온다.
- 저장된 Task의 `createdAt`은 내려온다. 일부 legacy 테스트/생성자 기반 응답에서는 null일 수 있으나 v1 API 응답에서는 non-null로 본다.
- 날짜 없는 Task는 `startAt`, `endAt`, `plannedDate`, `targetDate`, `todayOrder`, `completedAt`이 null이고 `status=INBOX`, `unscheduled=true`, `allDay=false`다.
- 날짜가 있는 Task는 생성 직후 `status=TODAY`, `targetDate=startAt 날짜`, `plannedDate=targetDate`, `unscheduled=false`다.
- `description`, `category`, `estimatedDurationMinutes`, `deferReason`, `deferReasonLabel`, D-Day 연결 필드, 반복 occurrence 필드는 값이 없으면 null이다.
- `updatedAt`은 생성 직후 null이고 수정 후 값이 생긴다.
- 반복 occurrence Task를 완료하면 해당 occurrence row만 `DONE`으로 바뀌며 `recurrenceSeriesId`, `occurrenceDate`, `originalOccurrenceDate`는 유지된다.

## 3-2. 목표 중심 계획·실천 여정

사용자의 "목표 -> 여러 반복 계획과 일회성 행동 -> 실제 실천 기록 -> 목표별 지나온 길" 흐름을 지원한다. 상세 정책은 [`PERSONAL_SYSTEM_CONTRACT.md`](./PERSONAL_SYSTEM_CONTRACT.md)를 원본으로 본다.

현재 구현된 `/api/v1/personal-systems/**`는 2026-10-12의 개인 시스템 1개 모델이다. 2026-10-14 기준 새 계약은 이 모델을 목표 중심 API로 전환하는 요구사항이며, 신규 `/api/v1/goals/**` 경로명은 아직 최종 확정 전이다.

구현 상태:

- 구현됨: 목표 생성/목록/상세/수정, 상태 변경, 사용자 확인 달성/달성 취소와 futurePlanPolicy 적용, 목표 하위/독립 반복 계획 CRUD, 날짜 기반 회차 생성/조회·스냅샷·알림 후보, 회차 실천/휴식 결과와 연결 Task 완료/재개, Goal/Plan DB schema, D-Day 소유권 검증
- 추가 구현됨: 목표 연결 일회성 Task 생성/연결/해제, 직접 실천 기록 생성/수정/취소와 생성 idempotency, 목표 여정 통합 조회·전체 기간 summary·cursor
- 추가 구현됨: 기존 개인 시스템의 명시적 이관, 원본 이력·새 ID mapping 보존, 게스트 계획 데이터 병합·정리
- AI 목표/계획 초안·조정안 API 기반은 기본 비활성으로 제공한다. 실제 외부 제공자 연결과 여정 요약은 아직 미구현이다.

제안 API:

- `POST /api/v1/goals`, `GET /api/v1/goals` 구현됨
- `GET /api/v1/goals/{goalId}`, `PATCH /api/v1/goals/{goalId}` 구현됨
- `PATCH /api/v1/goals/{goalId}/status` 구현됨
- `POST /api/v1/goals/{goalId}/achievement`, `DELETE /api/v1/goals/{goalId}/achievement` 구현됨
- `POST /api/v1/goals/{goalId}/plans`, `GET /api/v1/goals/{goalId}/plans` 구현됨
- `POST /api/v1/goal-plans`, `GET /api/v1/goal-plans?goalId=...` 구현됨
- `GET`/`PATCH /api/v1/goal-plans/{planId}`, `PATCH /api/v1/goal-plans/{planId}/status` 구현됨
- `GET /api/v1/goal-plan-occurrences?from=YYYY-MM-DD&to=YYYY-MM-DD&goalId=...` 구현됨
- `PUT /api/v1/goal-plan-occurrences/{occurrenceId}/result`, `DELETE /api/v1/goal-plan-occurrences/{occurrenceId}/result` 구현됨
- `PUT /api/v1/goal-plan-occurrences/{occurrenceId}/rest`, `DELETE /api/v1/goal-plan-occurrences/{occurrenceId}/rest` 구현됨
- `POST /api/v1/goals/{goalId}/tasks` 구현됨
- `PUT /api/v1/tasks/{taskId}/goal-link`, `DELETE /api/v1/tasks/{taskId}/goal-link` 구현됨
- `POST /api/v1/goals/{goalId}/direct-logs` 구현됨
- `PATCH /api/v1/goal-direct-logs/{logId}`, `DELETE /api/v1/goal-direct-logs/{logId}` 구현됨
- `GET /api/v1/goals/{goalId}/journey?from=YYYY-MM-DD&to=YYYY-MM-DD&timeZone=Asia/Seoul&cursor=...` 구현됨

회차 조회 계약:

- `from`, `to`는 필수이며 양끝 포함 1~31일이다. 선택 `goalId`는 현재 계획 연결 대신 회차 스냅샷의 목표 ID로 필터링한다. 전체 조회는 독립 반복 계획도 포함한다.
- 조회 시 소유자의 계획 회차를 저장한다. 날짜별 적용 계획 version과 시작일/선택 요일을 사용하며 같은 계획/날짜 재조회 및 동시 조회에서 ID가 유지된다.
- 응답은 `serviceDate`, `id` 오름차순이다. `id`, `goalId`, `goalVersion`, `planId`, `planVersion`, `serviceDate`, `timeZone`, `status`, `linkedTaskId`, `occurrenceVersion`과 목표 제목/계획 이름/trigger/기본·최소 행동/다음 행동 스냅샷을 반환한다.
- 과거 스냅샷은 유지한다. 계획 timezone의 오늘 이후 `PENDING` 회차는 적용 version으로 스냅샷과 연결 Task를 갱신한다. 요일에서 제외되거나 계획이 정지/보관되면 해당 미래 회차를 응답에서 제외하되 DB 원본은 유지한다.
- `notificationKey`는 `goal-plan:{planId}:{serviceDate}`다. `scheduledNotificationAt`은 활성 계획의 `PENDING` 회차에 알림 시각이 있을 때만 반환하며 `timeZone` 기준 local datetime이다. 알림이 없거나 정지/보관이면 null이다. 클라이언트는 새 조회 결과에서 빠진 미래 후보의 기존 로컬 알림을 취소한다.
- 목표 달성/정지/보관 요청의 `PAUSE_FUTURE`(기본값)는 연결 계획을 일시정지하고 `KEEP_FUTURE`는 계획 상태를 유지한다. 달성 취소/목표 재개는 계획을 자동 재개하지 않으며 계획 상태 API에서 명시적으로 재개한다.
- 회차 조회는 활성 계획의 오늘 이후 미실천 회차에 날짜만 있는 Today Task를 생성·연결한다. 과거 회차에는 Task를 생성하지 않는다. 연결 Task는 알림을 비활성화하고 회차 후보만 사용해 중복 알림을 피한다.
- 잘못된 기간은 400/`10001`, 타 사용자 또는 없는 `goalId`는 404/`70001`이다.

호환성: 신규 회차 결과/휴식 API와 응답의 nullable `result` 필드 추가이며 기존 Task/D-Day/개인 시스템 API 경로는 유지한다. 회차 GET은 오늘 이후 Today Task를 생성한다. 회차 연결 Task 삭제는 409/`70004`로 거절하므로 해당 Task에는 삭제 대신 회차 휴식/계획 보관 UI를 사용한다. 기존 목표 API의 `futurePlanPolicy`가 연결 계획에 실제 적용된다. 목표 schema의 선행 migration은 `20261014_add_goal_journey.sql`이며, 직접 기록 기능 배포 전에는 아래 추가 migration도 필요하다. 기존 `PERSONAL_SYSTEM*` 데이터는 아래 명시적 이관 계약을 따른다.

회차 실천·휴식 계약:

- 결과 PUT body의 필수 값은 `actionVariant`(`STANDARD`/`MINIMUM`/`UNKNOWN`), `practicedAt`(local datetime)이다. `practicedTimeZone` 생략 시 회차 timezone을 사용한다.
- 선택 필드는 `accomplishedNote`(최대 1000자), `difficulties`(최대 5개, 중복 제거), `nextActionPatchKind`(기본 `KEEP`), `nextAction`, `occurrenceVersion`이다. `REPLACE`만 비어 있지 않은 `nextAction`이 필수이고 `KEEP`/`CLEAR`에는 값을 보내지 않는다. 다음 행동 조정은 결과에 저장하며 계획 자체와 과거 스냅샷은 변경하지 않는다.
- 결과 PUT은 유효 결과 하나를 생성·수정하고 `PRACTICED`로 변경한다. 응답은 결과 필드와 `createdAt`, `updatedAt`, 현재 `occurrenceVersion`을 반환한다. 결과 DELETE는 `PRACTICED`를 `PENDING`으로 되돌리고 Task를 서비스 날짜로 재개한다. 이미 결과가 없으면 상태를 유지한다.
- 휴식 PUT/DELETE는 선택 query `occurrenceVersion`을 받는다. PUT은 `RESTED` 회차 응답을 반환하고 Task를 Inbox로 옮긴다. DELETE는 휴식만 취소하며 Task를 재개한다. 실천 -> 휴식, 휴식 -> 실천 전환은 기존 기록을 먼저 명시적으로 취소해야 한다.
- 결과/휴식 DELETE는 공통 envelope의 `data: null`을 반환한다. 재시도 시 같은 상태를 유지하며 최신 version을 보낸다. 오래된 version은 작업 종류와 관계없이 409/`70004`다.
- 연결 Task 완료는 같은 트랜잭션에서 `UNKNOWN` 실천 결과를 만든다. 이미 `STANDARD`/`MINIMUM` 결과가 있으면 해당 결과와 실천 시각을 유지한다. Task 완료 취소는 실천 결과를 취소한다. 휴식 중 Task 완료/재개는 409로 거절하며 Task 변경도 롤백된다.
- 실천 timezone의 `practicedAt`을 Task 저장 기준 `Asia/Seoul`로 변환한다. Task 완료 시각은 반대로 회차 timezone으로 변환해 결과에 저장한다. 연결 Task 알림은 사용하지 않는다.
- 계획 정지/보관 및 목표 `PAUSE_FUTURE`는 미래 미실천 연결 Task를 Inbox로 옮긴다. 계획 재개 후 회차를 조회하면 Today로 복원한다. 정지 계획의 결과/휴식 취소는 미래 Task를 Inbox에 유지한다.
- 없는 회차/다른 사용자 회차는 404/`70003`, 상태/버전 충돌과 연결 Task 삭제는 409/`70004`, 잘못된 timezone/필드/다음 행동 조정은 400/`10001`이다.

결과 PUT 예시:

```json
{
  "actionVariant": "MINIMUM",
  "practicedAt": "2026-10-15T09:00:00",
  "practicedTimeZone": "Asia/Seoul",
  "accomplishedNote": "최소 5개를 확인했다",
  "difficulties": ["NO_TIME"],
  "nextActionPatchKind": "REPLACE",
  "nextAction": "다음 단어장 열기",
  "occurrenceVersion": 1
}
```

목표 일회성 Task 계약:

- `POST /goals/{goalId}/tasks` body는 `{ "title": "문제 풀기", "date": "2026-10-15" }`다. 제목은 1~30자이고 날짜는 필수다. 지정 날짜 Today Task를 생성하고 `linkedGoalId`를 설정하며 201/`TaskResponse`를 반환한다. 선택 `Idempotency-Key`는 기존 Task 생성과 같은 공통 응답 replay 정책을 따른다.
- `PUT /tasks/{taskId}/goal-link` body는 `{ "goalId": 100 }`이며 200/`TaskResponse`를 반환한다. 이미 완료된 Task도 연결할 수 있고 완료 상태/시각과 D-Day 연결은 유지한다. `DELETE`는 이 목표 연결만 해제하며 같은 응답 타입을 반환한다. 같은 연결/해제 요청은 다시 실행해도 같은 연결 상태를 유지한다.
- 일반 `POST /tasks`, `PUT /tasks/{id}`에도 선택 양수 `linkedGoalId`를 추가한다. 수정 시 생략/null은 기존 연결을 유지하고 해제는 별도 DELETE를 사용한다. 모든 `TaskResponse`에 nullable `linkedGoalId`가 추가된다.
- 개인 일회성 Task만 지원한다. workspace Task는 개인 목표 연결 입력을 400/`10001`로 거절한다. 일반 반복/목표 계획/기존 개인 시스템의 회차 Task 개별 연결·해제는 409/`70005`다. 새 반복 생성 요청에 목표 연결을 함께 보내면 400/`10001`이다.
- 본인 소유 Task/목표만 사용할 수 있다. 범위 밖 Task는 404/`20001`, 목표는 404/`70001`이다. 연결 시 직접 실천 기록을 복제하지 않으며 여정 집계는 후속 여정 API에서 구현한다.

목표 직접 실천 기록 계약:

- `POST /goals/{goalId}/direct-logs`는 `Idempotency-Key` 헤더(앞뒤 공백 제거 후 1~160자, 대소문자 구분)가 필수다. body는 `practicedDate`(필수 LocalDate), `practicedTimeZone`(기본 `Asia/Seoul`), `title`(필수 1~120자), `note`(선택 최대 1000자)다. IANA timezone을 검증하며 제목/메모/zone 앞뒤 공백과 빈 선택 값을 정규화한다.
- 응답은 201이며 `id`, `goalId`, `goalVersion`, 날짜/zone/제목/메모, `createdAt`, `updatedAt`, `canceledAt`을 반환한다. 목표 version은 실천 날짜에 적용되는 version이며 생성 전 날짜는 최초 목표 version을 사용한다.
- key는 사용자+목표 범위다. 같은 key와 정규화한 생성 본문은 같은 ID의 **현재 기록 상태**를 반환하고 `Idempotency-Replayed: true`를 붙인다. 다른 생성 본문은 409/`10004`다. 원본 최초 요청 지문을 별도로 보관하므로 수정·취소나 공통 응답 캐시 만료 후에도 중복 생성과 취소 기록 부활을 막는다. 기록이 존재하는 동안 key를 유지하며 새 기록에는 새 key를 사용한다.
- `PATCH /goal-direct-logs/{logId}`는 같은 body 네 필드를 전체 교체한다. 날짜/제목은 필수이며 note 생략/null은 비운다. 원래 목표 ID/version과 최초 요청 지문은 유지한다. 200/기록 응답을 반환한다.
- `DELETE /goal-direct-logs/{logId}`는 `canceledAt`을 남기고 200/`data: null`을 반환한다. 재시도는 최초 취소 시각을 유지한다. 취소 후 수정은 409/`70007`이며 같은 생성 요청 재시도는 취소된 현재 기록을 반환한다.
- 타 사용자 또는 없는 기록은 404/`70006`, 타 사용자 또는 없는 목표는 404/`70001`이다. 잘못된 필드/zone/key 또는 필수 헤더 누락은 400/`10001`이다. 직접 기록은 Task나 반복 회차를 자동 완료하지 않는다.

배포·클라이언트 영향: endpoint 및 nullable Task 필드 추가이므로 기존 요청 형식은 호환된다. 직접 기록 배포 전 `20261015_add_goal_direct_log_request_hash.sql`을 선행 목표 migration 이후 적용해야 한다. 운영 적용 여부는 [`../db/MIGRATION_HISTORY.md`](../db/MIGRATION_HISTORY.md)에 기록한다. 지문 없는 기존 행의 동일 key 재시도는 최초 본문을 확인할 수 없어 409/`10004`로 거절한다. 프론트는 직접 기록 생성 key를 성공 후 재시도에도 유지하고, 목표 해제에는 별도 DELETE를 사용한다.

목표 여정 조회 계약:

- `from`, `to`는 필수 LocalDate이며 양끝 포함이다. `from <= to`여야 하고 회차 생성 API의 31일 제한을 적용하지 않아 목표 시작 이후 장기간도 조회할 수 있다. `timeZone`은 기본 `Asia/Seoul`, `limit`은 기본 50, 허용 범위 1~100이다.
- 응답은 `goalId`, `range: { from, to, timeZone }`, 전체 기간 `summary`, 이번 페이지 `items`, `nextCursor`, `journeyVersion`이다. 빈 결과와 마지막 페이지의 `nextCursor`는 null이다.
- `items`는 유효 실천만 포함한다. `PLAN_OCCURRENCE`는 실제 결과가 있는 `PRACTICED` 회차, `TASK_COMPLETION`은 현재 해당 목표에 연결된 완료 개인 일회성 Task, `DIRECT_LOG`는 취소하지 않은 직접 기록이다. 휴식과 미기록은 summary에만 포함한다.
- 목표 계획/기존 개인 시스템 회차에 연결된 Task는 `TASK_COMPLETION`에서 제외한다. 회차의 현재 계획 목표 연결이 달라져도 저장된 과거 목표 ID/계획/행동 snapshot을 사용한다. 일회성 Task는 현재 `linkedGoalId`를 기준으로 하므로 연결 해제/변경 시 해당 목표 여정에서도 이동한다.
- 시각이 있는 실천 결과는 `practicedAt`+`practicedTimeZone`, Task 완료는 저장 기준 `Asia/Seoul`을 요청 timezone으로 변환해 `practicedDate`와 기간 포함 여부를 판단한다. 직접 기록은 시각 없는 날짜 원본이므로 저장된 `practicedDate`와 zone을 보존한다. 휴식/미기록 집계도 서비스 날짜 원본을 사용한다.
- 정렬은 `practicedDate desc`, `sourceType asc`, `sourceId desc`다. 같은 날짜의 타입 순서는 `DIRECT_LOG`, `PLAN_OCCURRENCE`, `TASK_COMPLETION`이다. ID는 원본별 ID이므로 클라이언트 항목 key는 `sourceType + sourceId`로 만든다.
- 회차 항목은 당시 `goalVersion`, `planId`, `planVersion`, 계획 이름 `title`, 선택한 기본/최소 `actionText`, 메모, 연결 `taskId`, `serviceDate`, 원래 실천 시각/zone, `snapshotGoalTitle`, 다음 행동, 어려움 목록을 반환한다. 미분류 실천의 `actionText`는 null이다. 직접 기록의 목표 제목은 저장된 goal version으로 조회한다. 일회성 Task에는 goal version snapshot이 없어 `goalVersion`/`snapshotGoalTitle`이 null이며 `note`에는 Task 설명을 반환한다.
- summary는 cursor/limit과 무관하게 전체 조회 기간을 집계한다. `practiceOccurrenceCount`, `oneOffCompletionCount`, `directLogCount`는 원본별 실천 수이며 `practiceDateCount`는 이 세 원본의 날짜 합집합 크기다. `standardCount`, `minimumCount`, `unknownCount`는 모든 실천 항목의 행동 종류별 수이며 일회성 Task와 직접 기록은 `UNKNOWN`이다.
- `restCount`는 저장된 `RESTED` 회차 수, `noRecordOccurrenceCount`는 저장된 유효 `PENDING` 회차 수다. 오늘 이후 정지/보관 또는 선택 요일·목표 연결 변경으로 숨겨진 미실천 회차는 제외하고 과거 미기록은 보존한다. **미생성 회차를 추정해 집계하지 않으며 회차/Task를 새로 생성하지 않는다.** 미기록을 실패나 목표 달성률로 해석하지 않는다.
- `journeyVersion`은 목표 메타데이터, 조회 범위, 전체 항목/집계와 원본 변경 정보를 반영하는 불투명 지문이다. 클라이언트는 동등성 비교/캐시 무효화에 사용하고 숫자 순서로 해석하지 않는다. 다음 페이지는 같은 목표/기간/timezone에 서버가 준 `nextCursor`를 그대로 보낸다. 페이지 사이 기록 변경은 409/`70008`이며 이전 페이지를 버리고 cursor 없이 재조회한다.
- 범위/zone/limit 오류와 손상되거나 다른 조회에 쓰인 cursor는 400/`10001`, 타 사용자 또는 없는 목표는 404/`70001`이다. 모든 원본은 owner scope로 조회하며 AI 호출 없이 동작한다.

호환성·배포: 신규 GET과 응답 DTO/오류 코드 추가이며 기존 API 계약은 유지한다. 여정 조회 자체의 추가 DB migration은 없고, 선행 목표 schema 및 직접 기록 지문 migration은 필요하다. AI 요약은 후속 범위다.

기존 개인 시스템 명시적 이관:

- `POST /api/v1/personal-systems/{systemId}/goal-migration`은 본인 소유 시스템 하나를 새 Goal/Plan으로 이관한다. body와 Idempotency-Key는 필요하지 않다. 200 응답의 `systemId`, `goalId`, `planId`, `versionCount`, `occurrenceCount`, `resultCount`, `alreadyMigrated`를 반환한다. 최초 성공은 false, 재시도는 같은 ID와 원본 이관 건수 및 true를 반환한다.
- 버전/적용일, 회차 snapshot, 실천 종류/메모/어려움/다음 행동/실천 시각과 zone, 휴식 상태를 옮긴다. 기존 연결 Task/D-Day ID는 유지한다. 방향 문구 앞 최대 120자를 새 목표 제목/계획 이름으로 쓰고 전체 방향은 목표 `desiredOutcome`과 보존된 원본에서 확인한다. 새 엔티티 감사 시각은 이관 시각이며 원래 감사 시각은 구 테이블에 남긴다.
- 구 시스템은 보관되어 읽기 전용 원본 이력으로 남는다. `PersonalSystemResponse`에 nullable `migratedGoalId`, `migratedPlanId`, 구 회차 응답에 nullable `migratedOccurrenceId`가 추가된다. 이 ID는 이관 당시 대응 관계이며 나중에 새 계획의 목표를 변경해도 mapping 자체는 유지한다.
- 이관된 구 시스템의 수정/상태 변경과 구 회차 실천/휴식 변경은 409/`60004`다. 이후 실천·Task 완료/재개·미래 회차는 새 API만 갱신한다. 구 이력은 이관 시점 snapshot이므로 새 상태를 보려면 새 ID/API를 조회한다. 이관하지 않은 시스템의 기존 API 동작은 유지된다.
- 클라이언트는 성공 후 구 `personal-system:{systemId}:...` 로컬 예약을 취소하고 새 Goal/Plan/회차 API를 조회한다. 구 회차의 `scheduledNotificationAt`은 null이 되고 연결 Task 알림도 비활성화해 새 회차 후보만 사용한다. Task는 복제하지 않으며 실천/휴식 결과에 맞춰 기존 Task 완료/Inbox 상태를 정리한다.
- 없는/다른 사용자 시스템은 404/`60001`이다. version 이력, 결과/상태 또는 owner/D-Day 연결이 일치하지 않으면 409/`60003`이며 새 Goal/Plan을 포함한 전체 이관을 롤백한다. 사용자/시스템/회차/결과 잠금과 unique mapping으로 동시 재시도를 보호한다.
- 게스트 로그인 병합은 구 시스템, 새 Goal/Plan/회차/직접 기록의 owner도 같은 트랜잭션에서 이전한다. 기존 응답의 `mergeResult` 네 count 필드는 그대로이며 새 모델의 별도 count 필드는 추가하지 않는다. 만료 정리는 mapping과 자식 FK를 먼저 해제한 뒤 관련 데이터를 삭제한다.

배포·호환성: `20261016_add_personal_system_goal_mapping.sql`을 개인 시스템·목표 선행 migration 이후 적용해야 한다. DDL은 데이터 이관을 실행하지 않는다. **명시적 이관을 선택한 리소스에만 구 변경 API의 409 전환이 적용된다.** 클라이언트의 새 API 전환이 준비된 뒤 이관을 호출한다. 이관 후 새 기록이 생긴 계정은 mapping을 이해하지 못하는 구 backend로 단순 rollback하지 않으며, 쓰기 중지와 새 기록 조정/복구 계획이 먼저 필요하다. 이번 작업에서는 운영 DB 적용이나 계정 데이터 이관을 실행하지 않았다.

핵심 타입:

```ts
type GoalStatus = 'ACTIVE' | 'PAUSED' | 'ACHIEVED' | 'ARCHIVED';
type GoalPlanStatus = 'ACTIVE' | 'PAUSED' | 'ARCHIVED';
type GoalOccurrenceStatus = 'PENDING' | 'PRACTICED' | 'RESTED';
type GoalJourneySourceType = 'PLAN_OCCURRENCE' | 'TASK_COMPLETION' | 'DIRECT_LOG';
type ActionVariant = 'STANDARD' | 'MINIMUM' | 'UNKNOWN';
type NextActionPatchKind = 'KEEP' | 'CLEAR' | 'REPLACE';
type ScheduleDay = 'MO' | 'TU' | 'WE' | 'TH' | 'FR' | 'SA' | 'SU';
type GoalFuturePlanPolicy = 'PAUSE_FUTURE' | 'KEEP_FUTURE';
type PracticeDifficulty = 'NO_TIME' | 'UNCLEAR_FIRST_ACTION' | 'TOO_HEAVY' | 'FORGOT' | 'OTHER';
```

규칙:

- D-Day는 `targetDate` 필수 모델로 유지하고, 새 Goal이 선택적으로 D-Day에 연결된다.
- 목표일은 nullable이다. 기한 도래, 행동 완료 수, 예상 분량으로 달성/실패를 자동 처리하지 않는다.
- 사용자당 active 목표/계획 1개 제한을 두지 않는다.
- Goal 1:N plan, plan 1:N occurrence를 지원하고 목표 없는 독립 반복 계획도 허용한다.
- 계획 변경과 목표 연결 변경은 미래 회차에 적용하며 과거 snapshot은 보존한다.
- 연결 Task와 반복 회차가 같은 원본이면 여정과 집계에 한 번만 등장한다.
- 일회성 Task 완료와 직접 기록은 반복 회차를 자동 완료하지 않는다.
- 여정 summary는 cursor page 일부가 아니라 전체 조회 범위 기준이다.
- `MINIMUM`, `RESTED`, 기록 없음, `UNKNOWN`은 서로 구분한다.
- 목표 달성/보관 시 미래 계획은 `futurePlanPolicy`로 멈춤/유지를 명시한다.
- AI 초안/요약/조정 장애가 있어도 목표/계획 CRUD, 실천, 직접 기록, 여정 조회는 동작해야 한다.

기존 personal system API는 새 goal API가 구현될 때까지 호환 경로로 유지한다. 외부 클라이언트에는 `/personal-systems/**` deprecation과 `/goals/**` 전환 일정을 별도 고지한다.

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
  "estimatedDurationMinutes": 45,
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

- 현재 지원 범위는 `오늘`, `내일`, `낼`, `모레`, `내일모레`, `낼모레`, `YYYY-MM-DD`, `YYYY년 M월 D일`, `M월 D일`, `M/D`, `이번 주/다음 주/담주/다다음주/다담주 + 요일`, 단독 요일(`금요일 병원`), `오전/오후 N시`, `N시`, `N시 반`, `HH:mm`, `매주 요일` 기반 규칙 파싱이다.
- 날짜 또는 시간이 파싱되면 `SCHEDULE`로 저장하고, 시간만 있으면 `referenceDate`를 날짜로 사용한다.
- 오전/오후가 없는 `1시`부터 `7시`까지는 모바일 확인 화면에서 조정하기 쉬운 낮 시간으로 해석한다. 예: `3시`는 `15:00:00`.
- 날짜만 있거나 시간 없는 반복은 종일 일정으로 저장한다.
- `8월 15일`, `2027년 1월 3일`처럼 한국어 날짜 표현은 연도가 없으면 `referenceDate`의 연도를 사용한다.
- `이번 주 금요일 병원`, `다음주 월요일 오전 10시 치과`, `다다음주 화요일 14:30 발표 준비`처럼 주 단위 상대 요일은 기준 날짜가 속한 주, 다음 주, 다다음 주의 해당 요일로 생성한다.
- `금요일 병원`처럼 반복 표현이 없는 단독 요일은 기준 날짜 이후 가장 가까운 해당 요일의 종일 일정으로 생성한다.
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

추천 기준:

- 지난 Today 미완료, 이월 누적, D-Day 임박, 오래된 Inbox 기록을 우선 고려한다.
- 같은 우선순위 안에서는 최근 14일 완료한 category, 이월 횟수, 미룸 사유를 점수화해 더 관련 있는 후보를 먼저 반환한다.
- 응답은 최대 5개이며, `reason`은 모바일 표시용 짧은 추천 사유다.

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
- `notificationEnabled=false`인 Task는 반환하지 않는다.
- `notifyAt`이 있으면 `scheduledAt=notifyAt`이며, 없으면 `scheduledAt=startAt`이다.
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
- 이 API 자체는 서버 push 발송을 수행하지 않는다. 실제 발송은 scheduler가 담당한다.
- 서버 push 전송 이력 기록 중 `DeviceNotRegistered`, `InvalidPushToken`, `InvalidDeviceToken` 같은 영구 token 실패가 확인되면 해당 token은 비활성화되고 활성 목록에서 제외된다.

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
- 같은 수신자 owner와 idempotency key의 `SUCCESS` 이력이 있으면 서버 push scheduler는 다시 발송하지 않는다. `FAILED` 이력만 있으면 다음 scheduler cycle에서 재시도할 수 있다.
- shared workspace push는 ACTIVE 멤버 중 활성 push token이 있는 사용자를 수신자로 하며 payload에 `workspaceId`를 포함한다. PENDING/REMOVED 멤버는 제외된다.

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

## 4-1. Task Checklist API

모든 `/api/v1/tasks/{taskId}/checklist-items/**` 요청은 현재 로그인 사용자의 개인 Task 또는 ACTIVE 멤버인 workspace Task를 대상으로 한다. 다른 사용자의 개인 Task ID와 멤버가 아닌 workspace Task ID는 `TASK_NOT_FOUND`, 다른 item ID는 `TASK_CHECKLIST_ITEM_NOT_FOUND`처럼 처리된다.

제공 API:

```http
GET /api/v1/tasks/{taskId}/checklist-items
POST /api/v1/tasks/{taskId}/checklist-items
PUT /api/v1/tasks/{taskId}/checklist-items/{itemId}
PATCH /api/v1/tasks/{taskId}/checklist-items/{itemId}/done
PATCH /api/v1/tasks/{taskId}/checklist-items/{itemId}/done/cancel
DELETE /api/v1/tasks/{taskId}/checklist-items/{itemId}
PUT /api/v1/tasks/{taskId}/checklist-items/order
```

규칙:

- checklist item은 Task 아래 한 단계만 제공한다.
- item 제목은 30자 이하이며 Task당 최대 100개다.
- item에는 별도 날짜, 알림, 담당자, 하위 checklist를 두지 않는다.
- 생성 응답은 `201 Created`와 `TaskChecklistItemResponse`다.
- `done` 처리 시 `completedAt`을 생략하면 서버 현재 시각을 사용한다.
- Task 완료 시 미완료 checklist item은 같은 완료 시각으로 함께 완료된다.
- Task 삭제 시 checklist item도 함께 삭제된다.
- 정렬은 `orderedItemIds`에 현재 Task의 item 전체 ID를 중복 없이 보내는 전체 교체 방식이다.
- workspace Task checklist는 기존 workspace Task 권한과 동일하게 ACTIVE 멤버가 조회하고, OWNER/EDITOR만 생성, 수정, 완료, 재개, 삭제, 정렬할 수 있다. VIEWER의 변경 요청은 `FORBIDDEN`이다.

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
- `defaultDurationMinutes`는 5분 이상 1440분 이하이며, 일정 템플릿의 `endAt` 계산과 생성 Task의 `estimatedDurationMinutes` 기본값으로 사용한다. 일정 템플릿에서 생략되어 있으면 `endAt` 계산에는 60분을 사용하고 생성 Task의 `estimatedDurationMinutes`는 null이다.
- `recurrenceByDays`는 `MO`, `TU`, `WE`, `TH`, `FR`, `SA`, `SU`만 허용한다.
- 템플릿은 guest 승격 시 같은 사용자 ID로 유지되고, 기존 계정 로그인 병합과 만료 guest cleanup 대상에 포함된다.
- 템플릿 자체에는 D-Day를 저장하지 않는다. 템플릿 기반 Task 생성 시 `ddayGoalId`를 보내면 생성된 개인 Task에 같은 owner의 개인 D-Day를 연결한다.
- 1차 정책에서 템플릿은 personal-only 리소스다. 공유 workspace 템플릿과 개인 템플릿 기반 workspace Task 생성은 제공하지 않는다.

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
  "title": "월요일 운동",
  "ddayGoalId": 1
}
```

응답은 기존 Task 생성과 동일한 `TaskResponse`다. 반복 템플릿이면 첫 occurrence에 `recurrenceSeriesId`, `occurrenceDate`, `originalOccurrenceDate`, `recurrence`가 포함된다. `ddayGoalId`는 같은 owner의 개인 D-Day만 허용하며 다른 owner 또는 workspace D-Day는 `DDAY_GOAL_NOT_FOUND`처럼 처리된다.

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

type WorkspaceInvitationResponse = {
  workspace: WorkspaceResponse;
  membership: WorkspaceMemberResponse; // status=PENDING
  invitedAt: string;
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

ACTIVE membership의 workspace만 반환한다.

### 현재 사용자 초대 목록

```http
GET /api/v1/workspace-invitations
```

Response: `WorkspaceInvitationResponse[]`

현재 로그인 사용자의 `PENDING` membership만 반환한다. 수락 후에는 이 목록에서 제거되고 `GET /api/v1/workspaces`에 나타난다. `REMOVED` membership은 반환하지 않는다. 게스트 계정은 HTTP 403 `FORBIDDEN`이다.

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

수정/삭제는 `OWNER`만 가능하다. `DELETE /api/v1/workspaces/{workspaceId}`는 workspace 하위 `TASK`, `DDAY_GOAL`, `RECURRENCE_SERIES`, `WORKSPACE_MEMBER`를 같은 트랜잭션에서 함께 삭제하고 성공 시 `data=null`을 반환한다. 내용이 있는 workspace 삭제도 HTTP 500을 반환하지 않는다.

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
- `recurrence`가 있는 workspace Task는 workspace scope 반복 series로 저장된다.
- D-Day 연결은 같은 workspace의 D-Day만 지원한다.

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

### Workspace 로컬 알림 후보

```http
GET /api/v1/workspaces/{workspaceId}/tasks/notification-candidates?from=YYYY-MM-DD&to=YYYY-MM-DD
```

Response: `TaskNotificationCandidateResponse[]`

`ACTIVE` 멤버가 조회할 수 있다. 조건과 응답 형식은 개인 로컬 알림 후보 API와 동일하지만, 같은 workspace의 `WORKSPACE` scope Task/occurrence만 반환한다. 개인 알림 후보 API에는 workspace Task가 포함되지 않는다.

### Workspace Task 수정

```http
PUT /api/v1/workspaces/{workspaceId}/tasks/{taskId}
PUT /api/v1/workspaces/{workspaceId}/tasks/{taskId}?recurrenceScope=THIS|THIS_AND_FUTURE|ALL
```

Request: `TaskRequest`

Response: `TaskResponse`

`OWNER`, `EDITOR`만 수정할 수 있다. 다른 workspace의 Task ID나 workspace scope 밖 Task ID는 `TASK_NOT_FOUND`처럼 처리된다. `recurrence`가 있는 요청은 반복 규칙 수정 요청으로 간주해 HTTP 400 `INVALID_INPUT`이다.

반복 Task 수정 범위는 개인 Task API와 동일하다. `recurrenceScope`를 생략하면 `THIS`이며, 반복 Task가 아니면 무시된다. `THIS_AND_FUTURE`, `ALL`은 같은 workspace의 materialize된 occurrence만 대상으로 하며, occurrence 날짜별로 요청 `startAt`/`endAt`의 시간을 유지해 날짜를 이동하므로 `startAt`이 필요하다.

### Workspace Task 삭제

```http
DELETE /api/v1/workspaces/{workspaceId}/tasks/{taskId}
DELETE /api/v1/workspaces/{workspaceId}/tasks/{taskId}?recurrenceScope=THIS|THIS_AND_FUTURE|ALL
```

Response: `null`

`OWNER`, `EDITOR`만 삭제할 수 있다. 다른 workspace의 Task ID나 workspace scope 밖 Task ID는 `TASK_NOT_FOUND`처럼 처리된다.

반복 Task 삭제 범위는 개인 Task API와 동일하다. `recurrenceScope`를 생략하면 `THIS`이며, 반복 Task가 아니면 기존처럼 해당 Task를 삭제한다. 반복 occurrence 삭제는 `recurrenceException=SKIPPED` marker로 남기고 조회/알림 후보에서는 제외된다. `THIS_AND_FUTURE`, `ALL`은 아직 materialize되지 않은 미래 occurrence가 다시 생성되지 않도록 series 종료일도 함께 줄인다.

### Workspace Task D-Day 연결

```http
PATCH /api/v1/workspaces/{workspaceId}/tasks/{taskId}/dday-goal?ddayGoalId={goalId}
```

Response: `TaskResponse`

`OWNER`, `EDITOR`만 연결할 수 있다. Task와 D-Day는 같은 workspace의 `WORKSPACE` scope여야 한다. 다른 workspace의 Task ID나 workspace scope 밖 Task ID는 `TASK_NOT_FOUND`, 다른 workspace의 D-Day ID나 workspace scope 밖 D-Day ID는 `DDAY_GOAL_NOT_FOUND`처럼 처리된다.

### Workspace Task D-Day 연결 해제

```http
DELETE /api/v1/workspaces/{workspaceId}/tasks/{taskId}/dday-goal
```

Response: `TaskResponse`

`OWNER`, `EDITOR`만 연결 해제할 수 있다.

### Workspace D-Day 생성

```http
POST /api/v1/workspaces/{workspaceId}/dday-goals
```

Request: `DdayGoalRequest`

Response: `DdayGoalResponse`

`OWNER`, `EDITOR`만 생성할 수 있다. 생성된 D-Day는 `WORKSPACE` scope로 저장되며 기존 개인 D-Day API에는 반환되지 않는다.

### Workspace D-Day 목록

```http
GET /api/v1/workspaces/{workspaceId}/dday-goals
```

Response: `DdayGoalResponse[]`

`ACTIVE` 멤버가 조회할 수 있다.

### Workspace D-Day 단건

```http
GET /api/v1/workspaces/{workspaceId}/dday-goals/{goalId}
```

Response: `DdayGoalResponse`

`ACTIVE` 멤버가 조회할 수 있다. 다른 workspace의 D-Day ID나 workspace scope 밖 D-Day ID는 `DDAY_GOAL_NOT_FOUND`처럼 처리된다.

### Workspace D-Day 연결 Task 목록

```http
GET /api/v1/workspaces/{workspaceId}/dday-goals/{goalId}/tasks
```

Response: `TaskResponse[]`

`ACTIVE` 멤버가 조회할 수 있다. 같은 workspace에서 해당 D-Day에 연결된 Task만 반환한다.

### Workspace D-Day 삭제

```http
DELETE /api/v1/workspaces/{workspaceId}/dday-goals/{goalId}
```

Response: `null`

`OWNER`, `EDITOR`만 삭제할 수 있다. 삭제 시 같은 workspace에서 연결된 Task의 D-Day 연결을 해제한다.

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
| `q` | 제목/설명/category/D-Day 제목 부분 검색어. 한글 검색과 영문 대소문자 무시 검색을 지원한다. |
| `statuses` | `INBOX`, `TODAY`, `DONE`. 반복 파라미터 또는 콤마 구분을 지원한다. |
| `taskTypes` | `TODO`, `SCHEDULE`, `IDEA`. 반복 파라미터 또는 콤마 구분을 지원한다. |
| `category` | 카테고리명 exact match. |
| `ddayGoalId` | 연결된 D-Day 목표 ID. |
| `hasDday` | D-Day 목표 연결 여부. |
| `hasRecurrence` | 반복 series 연결 여부. |
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
      "dateSource": "TARGET_DATE",
      "matchedFields": ["TITLE"],
      "highlight": "출시 회의"
    }
  ],
  "nextCursor": "50",
  "limit": 50,
  "suggestedQueries": [],
  "suggestedCategories": []
}
```

`dateSource` 값은 `TARGET_DATE`, `START_AT`, `COMPLETED_AT`, `CREATED_AT`, `UPDATED_AT`, `NONE` 중 하나다. `matchedFields` 값은 `TITLE`, `DESCRIPTION`, `CATEGORY`, `DDAY_GOAL_TITLE`, `RECURRENCE` 중 하나이며, `q`가 없으면 빈 배열이다. `highlight`는 검색 결과 표시용 짧은 매칭 원문이며 `q`가 없으면 `null`이다. 잘못된 enum, `dateFrom > dateTo`, 잘못된 cursor, 범위를 벗어난 `limit`은 HTTP 400으로 응답한다.

빈 검색 결과 추천:

- `q`가 있고 첫 페이지 결과가 0건이면 `suggestedQueries`와 `suggestedCategories`에 현재 필터와 날짜 조건 안의 기존 category를 빈도순으로 최대 5개 반환한다.
- `suggestedQueries`는 모바일이 같은 검색창에 다시 넣을 수 있는 category 기반 query이고, `suggestedCategories`는 category 필터 chip으로 제안할 수 있는 값이다.
- 결과가 있거나 cursor로 다음 페이지를 조회하는 요청이면 두 배열은 빈 배열이다.

정렬 주의:

- `q`가 있으면 같은 sort 안에서 제목, category, D-Day 제목, 반복, 설명 매칭 순서로 먼저 정렬한다.
- 그 다음 `sort` 값의 날짜/생성/수정 시각 기준과 `task.id` tie-breaker를 적용한다.

Cursor 기준:

- 현재 cursor는 마지막으로 받은 항목의 `task.id` 문자열이다.
- `nextCursor=null`이면 다음 페이지가 없다.
- 다음 페이지 요청은 이전 응답의 `nextCursor`를 그대로 보낸다.
- cursor Task가 더 이상 검색 조건에 포함되지 않으면 HTTP 400이다.
- 중간에 Task가 생성/수정/삭제되어도 이전 페이지 마지막 항목 이후부터 이어서 조회하므로 offset shift 중복/누락을 피한다.

## 8-1. Task 카테고리 요약

TickTick식 좌측 메뉴처럼 Inbox, Today, Calendar와 category를 한 화면에서 탐색할 때 사용할 수 있는 개인 Task category 목록이다.

```http
GET /api/v1/tasks/categories
```

Response: `TaskCategorySummaryResponse[]`

정렬과 범위:

- 로그인 사용자의 개인 Task만 집계한다. Workspace Task는 workspace 메뉴와 혼합하지 않는다.
- `category`가 없는 Task는 `category: null`, `displayName: "미분류"`로 내려간다.
- 정렬은 category 이름 오름차순이며 미분류는 마지막이다.
- 별도 category entity나 사용자 지정 order는 아직 두지 않는다. 모바일에서 수동 category order가 필요해지면 별도 계약으로 추가한다.

## 9. Calendar Feed API

외부 캘린더 앱에서 개인 일정을 읽기 전용으로 구독하기 위한 iCalendar feed다. feed URL은 bearer token 없이 접근 가능하므로 원본 token을 비밀번호처럼 취급한다.

```http
POST /api/v1/calendar-feed/token
DELETE /api/v1/calendar-feed/token
POST /api/v1/workspaces/{workspaceId}/calendar-feed/token
DELETE /api/v1/workspaces/{workspaceId}/calendar-feed/token
GET /api/v1/calendar-feeds/{token}.ics
```

규칙:

- `POST /api/v1/calendar-feed/token`은 인증 필요. 기존 활성 feed token을 폐기하고 새 token을 발급한다.
- `DELETE /api/v1/calendar-feed/token`은 인증 필요. 로그인 사용자의 활성 feed token을 모두 폐기한다.
- `POST /api/v1/workspaces/{workspaceId}/calendar-feed/token`은 인증 필요. ACTIVE 멤버가 자신이 속한 workspace의 feed token을 발급한다. 같은 멤버가 같은 workspace에 발급한 기존 활성 token은 폐기된다.
- `DELETE /api/v1/workspaces/{workspaceId}/calendar-feed/token`은 인증 필요. ACTIVE 멤버가 자신이 발급한 해당 workspace feed token을 모두 폐기한다.
- workspace 멤버가 REMOVED 상태가 되면 해당 멤버가 발급한 workspace feed token은 폐기된다.
- `GET /api/v1/calendar-feeds/{token}.ics`는 인증 없이 `text/calendar`를 반환한다.
- token 원본은 저장하지 않고 SHA-256 hash만 DB에 저장한다.
- 개인 feed는 개인 scope의 날짜 있는 미완료 Task만 포함한다.
- workspace feed는 해당 workspace scope의 날짜 있는 미완료 Task만 포함한다. 발급된 URL은 bearer token 없이 접근 가능하므로 workspace 멤버가 제거되거나 URL이 노출되면 token을 폐기해야 한다.
- Task 설명, category, D-Day 제목, 멤버 이름, access token은 feed에 넣지 않는다.
- 반복 Task는 feed 조회 범위의 occurrence를 materialize한 뒤 각 occurrence를 `VEVENT`로 반환한다.
- token이 없거나 폐기됐으면 HTTP 404다.

Response:

```ts
type CalendarFeedTokenResponse = {
  token: string;
  feedPath: string; // /api/v1/calendar-feeds/{token}.ics
  createdAt: string;
  active: boolean;
};
```

## 10. System API

### 백엔드 metadata 조회

```http
GET /api/v1/system/metadata
```

인증 없이 호출할 수 있다.

Response:

```ts
type AppMetadataResponse = {
  version: string;
  commitSha: string;
  imageTag: string;
};
```

이 endpoint는 production build가 실제 연결한 backend commit/image를 확인하기 위한 읽기 전용 공개 API다. secret, private URL, 환경변수 원문은 반환하지 않는다.

## 11. 아직 프론트에서 의존하면 안 되는 계약

아래는 모바일 문서에 요구사항이 있으나 현재 백엔드 v1에는 없다.

- 서버 push 수동 발송 API

## 12. 모바일 전환 체크리스트

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
- [ ] 서버 push 수동 발송 UI는 별도 API가 생기기 전까지 실제 저장 기능처럼 열지 않음

## 13. Legacy API 정책

- 모바일 신규 연동 기준은 `/api/v1/**`다.
- legacy `/api/tasks/**`, `/api/ddays/**`는 과거 호환 범위로만 유지한다.
- 모바일 호환을 위해 legacy `/api/ddays/{id}`, `/api/ddays/{id}/tasks` alias를 새로 추가하지 않는다.
- D-Day 단건 조회는 `GET /api/v1/dday-goals/{id}`, D-Day 기반 Today Task 생성은 `POST /api/v1/dday-goals/{id}/tasks`를 사용한다.

## 목표 초안 API 기반 (2026-10-16)

`POST /api/v1/goals/draft`는 Bearer 인증이 필요하다. 현재 외부 AI 제공자는 연결하지 않았으며 기본 비활성 상태다. 활성 설정만 바꿔도 제공자가 없으면 503/`70009`를 반환한다.

요청:

```json
{"description":"이직 준비를 위한 영어 점수를 정하고 싶어요", "targetDate":"2026-11-30"}
```

- `description`: 공백 불가, 최대 2000자. 앞뒤 공백을 제거해 제공자에 전달한다.
- `targetDate`: 선택 ISO 날짜이며 제공자에 보내지 않고 응답에서 요청값을 유지한다. 생략/null은 null이다.
- 제공자 연결 후 성공 시 공통 envelope의 `data`는 `title`, `desiredOutcome`, `motivationText`, `targetDate`다. title은 1~120자이고 나머지 텍스트는 최대 500자, 빈 선택 텍스트는 null이다. ID는 없다.
- 초안은 목표/계획/회차/Task를 만들지 않는다. 확인한 필드만 별도 `POST /api/v1/goals`로 보내 저장한다. D-Day 연결은 저장 요청에서 별도 선택한다.
- 성공/AI 사용 불가 응답은 `Cache-Control: no-store`를 포함한다. 서버는 초안을 DB나 캐시에 보관하지 않는다.
- 입력 오류는 400/`10001`, 인증 오류는 기존 인증 계약, 비활성/제공자 미설정/실패/잘못된 출력은 503/`70009`다. 입력을 보존하고 직접 작성 흐름을 제공한다. 자동 재시도를 반복하지 않는다. 기존 CRUD는 AI와 독립적으로 동작한다.
- 기존 DTO/URL/오류 코드는 유지하는 추가 API이며 DB migration은 없다. 여정 요약과 외부 provider·보관·삭제 정책은 후속 작업이다.

## 목표별 계획 초안 API 기반 (2026-10-16)

`POST /api/v1/goals/{goalId}/plan-draft`는 Bearer 인증과 본인 소유 목표가 필요하다. 목표 초안과 같은 `app.goal-ai.enabled` 설정을 공유하지만 제공자 인터페이스는 별도다. 기본 비활성 상태이고 현재 외부 adapter는 없다.

```json
{
  "description": "퇴근 후 부담 없이 할 수 있는 영어 학습 행동을 추천해주세요",
  "schedule": {
    "startDate": "2026-11-01",
    "days": ["MO", "WE", "FR"],
    "timeZone": "Asia/Seoul",
    "notifyAtTime": "20:30:00"
  }
}
```

- description은 공백 불가, 최대 2000자다. schedule은 필수이며 startDate와 1~7개 요일이 필요하다. 요일 enum은 `MO/TU/WE/TH/FR/SA/SU`이며 null은 허용하지 않고 중복은 제거한다. timeZone은 누락/공백이면 Asia/Seoul, 잘못된 zone은 400이다. notifyAtTime은 선택이다. AI가 시작일·요일·zone·알림 시각을 바꾸지 않는다.
- 제공자 연결 후 성공 시 `data`는 `goalId`, `goalVersion`, `name`, `triggerText`, `schedule`, `standardAction`, `minimumAction`, `nextAction`이다. name은 필수 최대 120자, triggerText는 선택 최대 200자, nextAction은 선택 최대 300자다. 두 action은 필수이며 text(공백 불가, 최대 500자), 선택 targetAmount(최대 100자), 선택 estimatedMinutes(1~1440)를 포함한다. 텍스트 앞뒤 공백은 제거하고 빈 선택 텍스트는 null이다.
- `goalVersion`은 사용한 목표 snapshot 버전이다. 제공자 호출 동안 목표를 잠그지 않으므로 응답 시점의 최신 버전과 다를 수 있다. 사용자 검토 후 goalVersion을 제외한 생성 필드로 `POST /api/v1/goals/{goalId}/plans`를 호출한다. 저장 시 최신 소유권/존재 여부를 재검증한다.
- 초안은 계획/버전/회차/Task를 저장하지 않고 `Cache-Control: no-store`를 포함한다. 일시정지/달성/보관 목표도 읽기 전용 초안을 만들 수 있다.
- 검증 오류는 400/10001, 타인/없는 목표는 404/70001, 비활성·제공자 미설정·실패·잘못된 출력은 503/70009다. 유효한 요청에서는 비활성이어도 소유권을 먼저 검사하며 AI 실패 시 기존 CRUD는 유지한다. 입력을 유지하고 직접 작성 흐름을 제공한다.
- 추가 endpoint/DTO이며 기존 API 변경과 DB migration은 없다. 외부 provider·보관·삭제 정책 및 timeout/사용량 제한 확정 후 실제 adapter를 연결한다.

## 목표 계획 조정안 API 기반 (2026-10-16)

`POST /api/v1/goals/{goalId}/adjustment-draft`는 Bearer 인증이 필요하다. 기본 비활성 상태이며 외부 AI adapter는 아직 없다.

```json
{"planId": 12, "description": "퇴근이 늦어져서 기본 행동을 더 작게 조정하고 싶어요"}
```

- planId는 필수 양수, description은 공백 불가 최대 2000자다. 유효한 요청에서는 비활성이어도 소유권·연결을 먼저 검사한다. 타인/없는 목표는 404/70001, 타인/없는 계획·다른 목표에 연결된 계획·독립 계획은 404/70002다.
- 목표와 계획의 현재 snapshot을 같은 읽기 트랜잭션에서 가져온 뒤 트랜잭션 밖에서 제공자를 호출한다. 현재 목표 설명과 계획의 텍스트/행동, 사용자 설명만 입력으로 사용하며 실천 이력이나 집계는 자동으로 첨부하지 않는다. 기록에 기반한 실패 판정이나 자동 진단은 제공하지 않는다.
- 성공 data는 goalId, goalVersion, planId, planVersion, name, triggerText, schedule, standardAction, minimumAction, nextAction이다. 텍스트·행동 검증/정규화는 계획 초안과 동일하다. schedule은 현재 계획의 일정 그대로이며 제공자가 일정·연결 목표·상태·적용일을 변경하지 않는다.
- 초안은 계획/버전/회차/Task를 저장하지 않고 no-store 응답을 반환한다. 비활성·제공자 미설정·실패·잘못된 출력은 503/70009, 입력 오류는 400/10001이다. 일시정지/달성/보관 상태도 읽기 전용 초안은 허용한다. 실제 수정은 기존 API 정책을 따른다.
- goalVersion/planVersion은 읽은 snapshot 버전이며 조건부 쓰기 토큰이 아니다. 반영 전 목표·계획을 다시 조회해 버전을 비교하고 변경되었다면 초안을 재검토한다. 사용자 확인 후 goalVersion/planId/planVersion을 제외한 수정 필드로 `PATCH /api/v1/goal-plans/{planId}`를 호출한다. effectiveFromDate는 별도 선택한다. 기존 PATCH에 버전 비교 기능은 추가하지 않는다.
- 추가 endpoint/DTO이며 기존 API와 오류 코드를 유지한다. DB migration은 없다. 외부 provider·보관·삭제 정책과 timeout/사용량 제한 확정, 실제 adapter 연결, 여정 요약 API는 후속 작업이다.
