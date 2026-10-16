# Goal Journey Contract

Last updated: 2026-10-16

이 문서는 "목표 -> 여러 반복 계획과 일회성 행동 -> 실제 실천 기록 -> 목표별 지나온 길" 흐름의 백엔드 구현 계약이다. 2026-10-12의 개인 시스템 하나 중심 계약은 이 문서로 대체한다. 기존 구현의 `personal-systems` 모델은 폐기하지 않고 목표의 반복 실행 계획 모델로 확장 또는 마이그레이션한다.

첫 전환 목표는 아래 예시를 중복 없이 표현하는 것이다.

- 목표: 토익 900점, 선택 목표일 2026-11-30
- 반복 계획: 매일 단어 30개, 선택한 주말 요일 문제 5개
- 일회성 행동: 오늘 문제 30개 풀기
- 여정 조회: 목표 아래 반복 회차 실천, 일회성 Task 완료, 직접 기록을 원본별로 한 번씩 조회

## 1. 백엔드 1차 판단

- 기존 D-Day는 `targetDate`가 필수이고 목표일 중심 모델이므로 그대로 확장하면 "목표일 미정", "진행/일시정지/달성/보관", "여러 반복 계획"을 무리하게 얹게 된다. 새 `GOAL`을 원본 모델로 추가하고 기존 D-Day는 선택적으로 `GOAL.linkedDdayGoalId` 또는 D-Day의 goal mapping으로 연결한다.
- 기존 D-Day ID, Task 연결, targetDate, 알림/deep link, 완료 기록은 보존한다. 기존 D-Day를 삭제하거나 하나로 병합하지 않는다.
- 기존 `PERSONAL_SYSTEM`은 `GOAL_PLAN` 후보로 본다. 구현은 새 테이블로 분리하는 것을 우선 제안하고, migration에서 기존 personal system을 목표 없는 독립 반복 계획 또는 연결 D-Day가 있는 목표의 계획으로 옮긴다.
- Goal 1:N plan, plan 1:N occurrence를 지원한다. 목표 없는 독립 반복 행동도 기존 personal system 사용 범위와 충돌하지 않게 유지한다.
- 반복 회차의 유효 결과는 하나만 유지한다. 단, 목표에는 여러 일회성 Task와 직접 실천 기록을 추가할 수 있다.
- 계획 변경과 목표 연결 변경은 미래 회차에 적용하고 과거 회차의 goal/plan/action snapshot은 보존한다. 기존 완료 기록의 귀속 수정은 별도 명시적 action으로만 처리한다.
- 주말 중 임의 1회와 토·일 각각은 다른 요구다. 첫 전환은 날짜 기반 선택 요일별 회차를 지원한다. "기간 내 N회" 엔진은 기존 recurrence에 없으므로 이번 범위의 필수 구현에서 제외한다.
- 목표 달성/실패는 기한 도래나 완료 수로 자동 처리하지 않는다. 달성은 사용자 확인으로만 기록한다.
- AI는 목표 구체화, 행동 초안, 조정안, 기록 요약을 보조한다. AI 장애가 있어도 목표/계획 CRUD, 실천 기록, 여정 조회는 동작해야 한다.

## 2. 상태와 enum

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

`UNKNOWN`은 기존 Task 완료나 외부 hook처럼 기본/최소 행동 여부를 알 수 없을 때만 사용한다. 백엔드는 기본 행동을 했다고 추정하지 않는다.

## 3. 제안 데이터 모델

### `GOAL`

| 필드 | 의미 |
| --- | --- |
| `ID` | 목표 ID |
| `OWNER_USER_ID` | 소유 사용자 |
| `TITLE` | 목표 이름 |
| `DESIRED_OUTCOME`, `MOTIVATION_TEXT` | 선택 원하는 결과와 이유 |
| `TARGET_DATE` | 선택 목표일. 미정이면 null |
| `STATUS` | `ACTIVE`, `PAUSED`, `ACHIEVED`, `ARCHIVED` |
| `CURRENT_VERSION` | 목표 설명/날짜 변경 version |
| `LINKED_DDAY_GOAL_ID` | 선택 D-Day 연결 |
| `ACHIEVED_AT`, `ARCHIVED_AT` | 사용자 확인에 의한 달성/보관 시각 |
| `CREATED_AT`, `UPDATED_AT` | 생성/수정 시각 |

목표 이름은 필수다. `targetDate=null`을 허용한다. 기존 D-Day의 `targetDate` 필수 제약은 유지하고, D-Day를 Goal로 직접 대체하지 않는다.

### `GOAL_VERSION`

| 필드 | 의미 |
| --- | --- |
| `GOAL_ID`, `VERSION` | 목표 version 식별자 |
| `TITLE`, `DESIRED_OUTCOME`, `MOTIVATION_TEXT`, `TARGET_DATE` | 당시 목표 snapshot |
| `EFFECTIVE_FROM_DATE` | 이 version이 적용되는 서비스 날짜 |
| `CREATED_AT` | version 생성 시각 |

목표 제목/설명/날짜 수정은 새 version으로 남긴다. 과거 여정 항목의 당시 목표 snapshot은 변경하지 않는다.

### `GOAL_PLAN`

| 필드 | 의미 |
| --- | --- |
| `ID` | 실행 계획 ID |
| `OWNER_USER_ID` | 소유 사용자 |
| `GOAL_ID` | 선택 목표 연결. 목표 없는 독립 반복 행동이면 null |
| `CURRENT_VERSION` | 계획 version |
| `STATUS` | `ACTIVE`, `PAUSED`, `ARCHIVED` |
| `CREATED_AT`, `UPDATED_AT`, `ARCHIVED_AT` | 생성/수정/보관 시각 |

단어와 문제풀이처럼 같은 목표 아래 여러 계획을 독립적으로 정지/재개/수정할 수 있어야 한다.

### `GOAL_PLAN_VERSION`

| 필드 | 의미 |
| --- | --- |
| `PLAN_ID`, `VERSION` | 계획 version 식별자 |
| `GOAL_ID` | 이 version의 목표 연결 snapshot |
| `NAME` | 계획 이름 |
| `TRIGGER_TEXT` | 선택 시작 상황 |
| `SCHEDULE_RULE` | 반복 요일, 기준 시작일, IANA timezone, 선택 알림 시각 |
| `STANDARD_ACTION`, `MINIMUM_ACTION` | 기본/최소 행동 설명, 선택 목표 분량, 선택 예상 시간 |
| `NEXT_ACTION` | 선택 다음 행동 |
| `EFFECTIVE_FROM_DATE` | 이 version이 적용되는 서비스 날짜 |
| `CREATED_AT` | version 생성 시각 |

시작 시각 없는 매일/선택 요일 반복을 지원한다. 일반 일정 `startAt`을 강제하지 않는다. 알림은 `notifyAtTime`이 있을 때만 후보로 제공한다.

### `GOAL_PLAN_OCCURRENCE`

| 필드 | 의미 |
| --- | --- |
| `ID` | 회차 ID |
| `OWNER_USER_ID` | 소유 사용자 |
| `GOAL_ID`, `GOAL_VERSION` | 회차 당시 목표 snapshot. 독립 반복이면 null |
| `PLAN_ID`, `PLAN_VERSION` | 적용된 계획 version |
| `SERVICE_DATE` | 사용자 timezone 기준 예정 서비스 날짜 |
| `TIME_ZONE` | 회차 산출 기준 IANA timezone |
| `SNAPSHOT_GOAL_TITLE`, `SNAPSHOT_PLAN_NAME`, `SNAPSHOT_TRIGGER_TEXT` | 회차 당시 목표 제목, 계획 이름과 trigger |
| `SNAPSHOT_STANDARD_ACTION_TEXT`, `SNAPSHOT_MINIMUM_ACTION_TEXT`, `SNAPSHOT_NEXT_ACTION`, `SNAPSHOT_NOTIFY_AT_TIME` | 회차 당시 행동/다음 행동/알림 snapshot |
| `LINKED_TASK_ID` | 선택 연결 Task |
| `STATUS` | `PENDING`, `PRACTICED`, `RESTED` |
| `ENTITY_VERSION` | 낙관적 잠금 version |
| `CREATED_AT`, `UPDATED_AT` | 생성/수정 시각 |

중복 방지는 소유 계획의 `PLAN_ID + SERVICE_DATE` unique key로 보장한다. 조회는 소유자별 계획을 ID 순서로 잠가 동시 materialize를 직렬화한다. 주말 중 임의 1회 같은 기간 N회 계획은 이 key만으로 충분하지 않으므로 별도 엔진 도입 전에는 지원 범위 밖이다.

2026-10-15 구현: 기간별 회차 GET은 양끝 포함 1~31일을 지원한다. 과거 회차 스냅샷은 보존하며 오늘 이후의 미실천 회차와 연결 Task는 날짜별 적용 version으로 갱신한다. 정지/보관 또는 변경된 선택 요일에 해당하지 않는 미래 미실천 회차는 응답에서 제외하고 DB 원본은 유지한다. 목표 필터는 회차의 당시 목표 ID를 사용한다. `notificationKey`, local `scheduledNotificationAt`, `timeZone`을 함께 반환하며 알림 시각이 없거나 계획이 정지/보관된 경우 후보 시각은 null이다.

목표 상태 요청의 `PAUSE_FUTURE`는 현재 연결된 계획을 일시정지한다. `KEEP_FUTURE`는 계획 상태를 유지한다. 목표 재개/달성 취소 시 계획은 상태 API로 명시적으로 재개한다. 실천/휴식 결과와 회차 Task 자동 연결도 구현됐다. 활성 계획의 오늘 이후 미실천 회차에 날짜만 있는 Today Task를 생성하며 알림은 회차 후보만 제공한다. 정지/보관 시 미래 미실천 Task는 Inbox로 이동한다.

### `GOAL_PRACTICE_RESULT`

| 필드 | 의미 |
| --- | --- |
| `OCCURRENCE_ID` | 회차 ID. 회차당 유효 결과 하나 |
| `ACTION_VARIANT` | `STANDARD`, `MINIMUM`, `UNKNOWN` |
| `PRACTICED_AT` | 사용자가 확인한 실천 시각 |
| `PRACTICED_TIME_ZONE` | 실천 시각 해석 timezone |
| `ACCOMPLISHED_NOTE` | 선택 메모 |
| `DIFFICULTIES` | 선택 어려움 목록 |
| `NEXT_ACTION_PATCH_KIND` | 유지, 비우기, 새 값 저장 구분 |
| `NEXT_ACTION_VALUE` | 새 다음 행동 값 |
| `CREATED_AT`, `UPDATED_AT` | 결과 저장/수정 시각 |

2026-10-15 구현: 결과 PUT은 기본/최소/미분류 실천과 timezone/메모/어려움/다음 행동 조정을 저장한다. 회차당 유효 결과 하나를 유지하며 수정도 `ENTITY_VERSION`을 올린다. 결과 응답과 회차 GET의 `result`에 최신 `occurrenceVersion`을 제공한다. `REPLACE`만 비어 있지 않은 다음 행동 값이 필수이며 `KEEP`/`CLEAR`에는 값을 보내지 않는다. 이 조정은 결과에만 저장하며 계획 자체를 수정하지 않는다.

결과/휴식 전환은 기존 기록을 먼저 명시적으로 취소한다. 실천 결과 저장은 연결 Task 완료와, 결과 취소는 Task 재개와 같은 트랜잭션이다. 휴식은 Task를 Inbox로 옮기고 취소 시 재개한다. 정지된 계획의 미래 Task는 취소 후에도 Inbox에 유지한다. Task에서 직접 완료하면 `UNKNOWN` 결과를 만들며 기존 명시적 결과는 보존한다. Task 완료 취소는 결과를 취소한다. 휴식 중 Task 완료/재개 및 연결 Task hard delete는 상태 충돌로 거절한다.

회차 변경은 비관적 잠금으로 직렬화하고 선택 `occurrenceVersion`으로 오래된 요청을 409/`70004`로 거절한다. 결과 없음에 대한 결과 취소와 휴식 없음에 대한 휴식 취소는 기존 상태를 보존한다. 자세한 body/응답/오류 계약은 [`API_V1_FRONTEND.md`](./API_V1_FRONTEND.md)의 회차 실천·휴식 계약을 따른다.

### `GOAL_DIRECT_LOG`

| 필드 | 의미 |
| --- | --- |
| `ID` | 직접 실천 기록 ID |
| `OWNER_USER_ID` | 소유 사용자 |
| `GOAL_ID`, `GOAL_VERSION` | 목표 snapshot |
| `PRACTICED_DATE` | 실제 실천 날짜 |
| `PRACTICED_TIME_ZONE` | 날짜 해석 timezone |
| `TITLE` | 행동 제목 |
| `NOTE` | 선택 메모 |
| `IDEMPOTENCY_KEY` | 재시도 중복 방지 key |
| `CREATE_REQUEST_HASH` | 최초 정규화 생성 본문의 SHA-256. 기록 수정/취소 후에도 유지 |
| `CREATED_AT`, `UPDATED_AT`, `CANCELED_AT` | 생성/수정/취소 시각 |

계획하지 않았지만 이미 한 일을 빠르게 남기는 원본이다. 추가 실천을 기존 반복 회차의 기본/최소 결과로 억지 변환하지 않고, 일회성 기록이 주말 회차를 자동 완료하지 않는다.

2026-10-15 구현: 생성은 1~160자 `Idempotency-Key` 헤더가 필수다. key는 앞뒤 공백을 제거하고 대소문자를 구분하며 사용자+목표 범위로 관리한다. 목표 row 잠금과 unique key로 동시 생성을 직렬화한다. 같은 key/정규화 생성 본문은 기존 ID의 현재 상태를 반환하고 다른 본문은 409/`10004`다. 최초 생성 지문을 별도로 유지하므로 기록 수정이나 취소 후에도 생성 재시도가 기록을 복제하거나 되살리지 않는다.

PATCH는 날짜/zone/제목/메모를 교체하고 원래 목표 snapshot과 key/지문을 유지한다. DELETE는 최초 `canceledAt`을 유지하는 soft cancel이다. 취소된 기록 수정은 409/`70007`, owner scope 밖 기록은 404/`70006`이다. 직접 기록은 기존 반복 회차 결과와 독립이다. 배포 전 `20261015_add_goal_direct_log_request_hash.sql` 적용이 필요하다.

### Task 확장

기존 `TASK.linkedDdayGoalId`와 D-Day Task 연결 API는 유지한다. 새 목표 연결을 위해 `TASK.linkedGoalId`를 nullable로 추가한다. 목표 상세에서 일회성 Task를 만들면 `linkedGoalId`가 자동 설정되고, 일반 Task 생성/수정에서도 목표를 선택할 수 있다.

Task 완료는 목표 여정의 `TASK_COMPLETION` 원본으로 들어온다. 이미 목표에 연결된 기존 Task를 연결할 때는 새 직접 기록을 복제하지 않는다.

2026-10-15 구현: 목표별 Task 생성은 `title`(1~30자), `date` 필수 body로 Today Task를 만든다. 별도 목표 연결 PUT/DELETE와 일반 Task 생성/수정의 선택 `linkedGoalId`를 지원한다. 수정 시 목표 ID 생략/null은 기존 연결을 유지하고 별도 DELETE로 해제한다. 응답 `linkedGoalId`는 nullable이다. 완료 상태/시각과 D-Day 연결은 보존한다. 일반 반복/목표 계획/기존 개인 시스템 회차 Task의 목표는 개별 Task에서 변경할 수 없다. workspace Task는 개인 목표 연결 대상이 아니다. 여정 통합/중복 제거는 후속 단계에서 구현한다.

## 4. API 초안

경로명은 아직 최종 확정 전이다. 기존 `/api/v1/personal-systems/**`는 호환 기간 동안 유지하고 새 goal API로 단계적 전환한다.

| 작업 | 경로 |
| --- | --- |
| 목표 생성, 목록 | `POST /api/v1/goals`, `GET /api/v1/goals` |
| 목표 상세, 수정 | `GET /api/v1/goals/{goalId}`, `PATCH /api/v1/goals/{goalId}` |
| 목표 상태 변경 | `PATCH /api/v1/goals/{goalId}/status` |
| 목표 달성 확인 | `POST /api/v1/goals/{goalId}/achievement` |
| 목표 달성 취소 | `DELETE /api/v1/goals/{goalId}/achievement` |
| 계획 생성, 목록 | `POST /api/v1/goals/{goalId}/plans`, `GET /api/v1/goals/{goalId}/plans` |
| 독립 반복 계획 생성, 목록 | `POST /api/v1/goal-plans`, `GET /api/v1/goal-plans?goalId=...` |
| 계획 상세, 수정, 상태 변경 | `GET/PATCH /api/v1/goal-plans/{planId}`, `PATCH /api/v1/goal-plans/{planId}/status` |
| 기간별 회차 | `GET /api/v1/goal-plan-occurrences?from=YYYY-MM-DD&to=YYYY-MM-DD&goalId=...` |
| 회차 결과 저장, 수정, 취소 | `PUT /api/v1/goal-plan-occurrences/{occurrenceId}/result`, `DELETE /api/v1/goal-plan-occurrences/{occurrenceId}/result` |
| 회차 휴식 선택, 취소 | `PUT /api/v1/goal-plan-occurrences/{occurrenceId}/rest`, `DELETE /api/v1/goal-plan-occurrences/{occurrenceId}/rest` |
| 목표 일회성 Task 생성 | `POST /api/v1/goals/{goalId}/tasks` |
| 기존 Task 목표 연결 | `PUT /api/v1/tasks/{taskId}/goal-link`, `DELETE /api/v1/tasks/{taskId}/goal-link` |
| 이미 한 일 직접 기록 | `POST /api/v1/goals/{goalId}/direct-logs` |
| 직접 기록 수정, 취소 | `PATCH /api/v1/goal-direct-logs/{logId}`, `DELETE /api/v1/goal-direct-logs/{logId}` |
| 목표 여정 조회 | `GET /api/v1/goals/{goalId}/journey?from=YYYY-MM-DD&to=YYYY-MM-DD&timeZone=Asia/Seoul&cursor=...` |
| AI 목표/계획 초안 | `POST /api/v1/goals/draft`, `POST /api/v1/goals/{goalId}/plan-draft` |
| AI 조정/요약 | `POST /api/v1/goals/{goalId}/adjustment-draft`, `POST /api/v1/goals/{goalId}/journey-summary` |

목표 여정 조회는 AI 호출 없이 원본과 집계를 반환한다. AI 요약은 별도 endpoint에서 생성하고 입력 원본 version으로 캐시 무효화를 판단한다.

## 5. DTO 예시

목표 생성:

```json
{
  "title": "토익 900점",
  "desiredOutcome": "LC/RC 합산 900점 이상",
  "motivationText": "이직 지원 자격을 맞추기 위해",
  "targetDate": "2026-11-30",
  "linkedDdayGoalId": 12
}
```

계획 생성:

```json
{
  "name": "매일 단어 30개",
  "triggerText": "출근 전",
  "schedule": {
    "startDate": "2026-10-14",
    "days": ["MO", "TU", "WE", "TH", "FR", "SA", "SU"],
    "timeZone": "Asia/Seoul",
    "notifyAtTime": null
  },
  "standardAction": {
    "text": "단어 30개 외우기",
    "targetAmount": "30개",
    "estimatedMinutes": null
  },
  "minimumAction": {
    "text": "단어 5개 보기",
    "targetAmount": "5개",
    "estimatedMinutes": null
  },
  "nextAction": "Day 1 단어장 열기"
}
```

이미 한 일 기록:

```json
{
  "practicedDate": "2026-10-14",
  "practicedTimeZone": "Asia/Seoul",
  "title": "문제 30개 풀기",
  "note": "Part 5 오답이 많았음"
}
```

목표 여정 응답 골격:

```json
{
  "goalId": 100,
  "range": {
    "from": "2026-10-14",
    "to": "2026-11-30",
    "timeZone": "Asia/Seoul"
  },
  "summary": {
    "practiceOccurrenceCount": 12,
    "oneOffCompletionCount": 3,
    "directLogCount": 2,
    "practiceDateCount": 14,
    "standardCount": 8,
    "minimumCount": 4,
    "unknownCount": 5,
    "restCount": 1,
    "noRecordOccurrenceCount": 5
  },
  "items": [
    {
      "sourceType": "PLAN_OCCURRENCE",
      "sourceId": 501,
      "practicedDate": "2026-10-14",
      "goalId": 100,
      "planId": 201,
      "title": "매일 단어 30개",
      "actionText": "단어 30개 외우기",
      "actionVariant": "STANDARD",
      "note": "아침에 완료",
      "taskId": 9001
    },
    {
      "sourceType": "TASK_COMPLETION",
      "sourceId": 9100,
      "practicedDate": "2026-10-14",
      "goalId": 100,
      "planId": null,
      "title": "문제 30개 풀기",
      "actionVariant": "UNKNOWN",
      "note": null,
      "taskId": 9100
    },
    {
      "sourceType": "DIRECT_LOG",
      "sourceId": 77,
      "practicedDate": "2026-10-14",
      "goalId": 100,
      "planId": null,
      "title": "오답노트 정리",
      "actionVariant": "UNKNOWN",
      "note": "Part 5"
    }
  ],
  "nextCursor": null,
  "journeyVersion": "goal:100:<sha256>"
}
```

## 6. 정책

### 2026-10-16 여정 구현 계약

- 여정 GET은 저장된 `PRACTICED` 회차 결과, 현재 목표에 연결된 개인 일회성 Task 완료, 취소되지 않은 직접 기록을 owner scope로 통합한다. 목표 계획/기존 개인 시스템 회차의 연결 Task는 일회성 완료 원본에서 제외한다.
- 필수 `from`/`to`는 양끝 포함이며 31일 제한이 없다. `timeZone` 기본값은 `Asia/Seoul`, `limit` 기본값은 50이며 1~100을 지원한다. 항목은 실제 실천 날짜 내림차순, 타입 이름 오름차순, 원본 ID 내림차순이다.
- 시각 있는 회차 결과/Task 완료는 요청 timezone으로 날짜를 산출한다. 직접 기록은 저장된 날짜/zone을 보존한다. 회차의 `serviceDate`와 실제 `practicedDate`가 다르면 실제 실천 날짜로 항목을 조회한다.
- summary는 페이지 일부가 아니라 전체 범위 실천 원본, 행동 종류, 날짜 합집합을 집계한다. 일회성 Task와 직접 기록은 `UNKNOWN`에 포함한다. 휴식/미기록 수는 서비스 날짜 범위의 저장된 회차를 기준으로 하며 미생성 날짜를 추정하지 않는다. 오늘 이후 숨겨진 정지/보관/요일·목표 변경 회차는 미기록에서 제외한다. 조회는 회차/Task를 생성하지 않는다.
- 현재 원본을 한 read-only repeatable-read transaction에서 조회한다. `journeyVersion`은 전체 범위 입력/집계 지문이며 cursor는 소유자/목표/기간/timezone과 이 지문에 연결된다. 원본 변경 후 기존 cursor는 409/`70008`로 거절한다. 클라이언트는 cursor 없이 다시 시작한다.
- 완료/실천 취소, Task 목표 연결 해제/변경, 직접 기록 수정/취소 후 항목·집계·지문이 함께 갱신된다. 과거 회차와 직접 기록의 목표 version snapshot은 보존한다. 상세 필드/오류는 [`API_V1_FRONTEND.md`](./API_V1_FRONTEND.md)의 목표 여정 조회 계약을 따른다.

### 날짜와 조회

- 목표일, 회차일, 직접 기록일은 사용자 timezone 기준 `LocalDate`로 저장한다.
- 목표 여정 조회는 `goalId`, 명시 기간, `timeZone`, cursor를 받는다. 주간뿐 아니라 목표 시작 이후 전체 범위도 조회할 수 있어야 한다.
- cursor pagination을 사용해도 summary는 페이지 일부가 아니라 전체 조회 범위 기준으로 계산한다.
- 정렬 기본값은 `practicedDate desc, sourceType, sourceId desc`다. 같은 날짜 안의 세부 순서는 안정적이어야 한다.

### 상태 변경

- 목표 `ACHIEVED`와 `ARCHIVED` 전환은 사용자 확인 action이다.
- 상태 변경 요청에는 `futurePlanPolicy`를 받을 수 있다. 기본 제안은 `PAUSE_FUTURE`이며 과거 실천은 보존한다.
- `KEEP_FUTURE`를 선택하면 목표가 달성/보관되어도 미래 계획 생성과 알림 후보를 유지한다. 프론트가 명시 선택 없이 암묵 처리하지 않는다.
- 계획 보관/정지는 해당 계획의 미래 회차와 알림 후보만 멈춘다. 목표의 다른 계획은 영향을 받지 않는다.

### 중복 방지

- 반복 회차와 연결 Task가 같은 원본이면 여정에 한 번만 나온다. `PLAN_OCCURRENCE`가 우선이고 연결 Task 완료는 그 회차 결과로 수렴한다.
- 목표 연결 Task 중 회차에 연결되지 않은 일회성 Task 완료만 `TASK_COMPLETION`으로 집계한다.
- `GOAL_DIRECT_LOG`는 idempotency key로 생성 재시도 중복을 막는다.
- 완료 취소, Task 목표 연결 해제, 직접 기록 취소 후 여정 원본과 summary, AI 요약 입력 version은 일치해야 한다.

### 계정, 권한, 삭제

- 모든 목표, 계획, 회차, 직접 기록, Task 연결은 owner scope를 가진다. 타 사용자 리소스는 조회/수정할 수 없다.
- 게스트 계정도 목표/계획/여정 CRUD를 사용할 수 있다. 계정 병합 시 기존 Task/D-Day 병합 정책과 같은 owner 이전 원칙을 따른다.
- 삭제 기본값은 기록 보존을 위해 archive/cancel이다. 실제 hard delete가 필요하면 여정과 집계에서 어떤 원본이 사라지는지 별도 migration 계획을 둔다.

### 알림

- 첫 버전은 기존 로컬 알림 후보 모델을 재사용한다. 신규 서버 push 구현은 이 계약 범위가 아니다.
- 회차별 `notificationKey`는 `goal-plan:{planId}:{serviceDate}` 형식을 제안한다.
- 후보 응답에는 예정 시각, timezone, 취소/변경 판별용 version 또는 hash를 포함한다.
- 목표 달성/보관, 계획 일시정지/보관, 주기 변경 시 모바일이 기존 예약을 정리할 수 있도록 후보에서 제외하거나 cancel marker를 제공한다.

### AI

- AI 초안은 저장하거나 회차를 만들지 않는다. 사용자가 확인한 요청만 CRUD에 반영한다.
- AI 요약은 서버 계산 집계와 사용자가 남긴 메모만 근거로 삼는다. 기록에 없는 성과, 점수 상승, 실제 학습량/시간을 만들지 않는다.
- 개인 메모, 목표 이유, 어려움, 다음 행동은 운영 로그에 원문을 남기지 않는다.
- 2026-10-16 로그 보호 기반: `/api/v1/goals`, `/goal-plans`, `/goal-plan-occurrences`, `/goal-direct-logs`, `/personal-systems`, `/personal-system-occurrences`의 전체 하위 경로는 API 전문 로깅을 켜도 요청/응답 본문과 query 전체를 기록하지 않는다(각 경로는 `/api/v1` 기준). 필드명 추가나 로깅 제외 설정 교체로 해제되지 않으며 context path 배포에도 적용한다. 본문 캐싱 wrapper도 만들지 않는다. HTTP 상태, 경로, 처리 시간, request ID와 기존 마스킹 규칙을 적용한 헤더는 유지한다. 향후 위 경로에 추가될 AI 초안/요약도 같은 보호를 받는다.
- provider, 보관, 삭제 정책은 구현 전 별도 확정한다.
- 로그 보호는 외부 전송/보관 정책 확정을 대신하지 않으며, 기존 Goal/Plan/실천 데이터의 DB 저장·조회·삭제 계약은 바꾸지 않는다.
- 2026-10-16 목표 초안 기반: 인증된 `POST /api/v1/goals/draft`에 `description`(공백 불가, 최대 2000자)과 선택 `targetDate`를 보낸다. 성공 응답은 `title`(1~120자), 선택 `desiredOutcome`/`motivationText`(각 최대 500자), 요청 그대로의 `targetDate`이며 ID는 없다. 목표/계획/회차/Task를 저장하지 않는다. 사용자 확인 후 별도 `POST /api/v1/goals`로 저장하며 D-Day는 그때 사용자가 선택한다.
- 기본 `app.goal-ai.enabled=false`(`DOOIT_GOAL_AI_ENABLED`)이며 현재 외부 제공자 adapter가 없다. 비활성, 제공자 없음, 잘못된 제공자 출력, 제공자 예외는 503/`70009`로 통일하고 CRUD에는 영향을 주지 않는다. 입력 검증 실패는 400/`10001`, 미인증은 401이다. 활성 설정만으로 외부 전송은 발생하지 않는다. 외부 adapter는 provider·보관·삭제 정책 및 timeout/사용량 제한을 확정한 후 별도 구현한다.
- 초안과 제공자 응답은 DB/서버 캐시에 보관하지 않는다. 요청 처리 동안 메모리에서 사용하고 응답에는 `Cache-Control: no-store`를 적용한다. 이는 JVM 메모리의 즉시 삭제/덮어쓰기나 외부 provider의 보관 정책을 보장하지 않는다. 제공자 예외의 원문/원인은 로그에 남기지 않으며 JSON 파싱 오류도 입력을 인용할 수 있는 예외 메시지를 기록하지 않는다. 날짜/ID를 제공자가 추론하지 않도록 인터페이스 입력은 정규화한 설명만 전달한다.
- 이 단계는 새 endpoint/DTO/오류 코드 추가이며 기존 API와 호환된다. DB migration과 기존 클라이언트 수정은 필요 없다. 여정 요약은 아직 미구현이다.
- 2026-10-16 계획 초안 기반: `POST /api/v1/goals/{goalId}/plan-draft`는 본인 소유 목표와 `description`(공백 불가, 최대 2000자), 필수 `schedule`을 입력으로 받는다. 일정은 계획 생성 DTO와 같고 `startDate`/1~7개 요일(`MO/TU/WE/TH/FR/SA/SU`)이 필수다. 요일 null 원소는 400이며 중복은 최초 순서를 유지해 제거한다. timeZone은 공백 제거 후 ZoneId 검증, 누락/공백은 `Asia/Seoul`이다. 시작일/요일/zone/알림 시각은 제공자가 결정하지 않는다.
- 제공자에는 해당 목표 snapshot의 title/desiredOutcome/motivationText와 정규화한 description만 전달한다. 사용자·목표 ID, 날짜, D-Day 연결, 무관한 실천 기록은 전달하지 않는다. 소유권 조회 트랜잭션이 종료된 뒤 호출한다. 이 조회는 비활성이어도 실행되어 타인/없는 목표는 404/`70001`이고, 제공자 없음·실패·유효하지 않은 출력은 503/`70009`다. 제공자 인터페이스는 목표 초안과 별도이며 같은 활성 설정을 공유한다. 외부 adapter는 아직 없다.
- 응답은 `goalId`, 읽은 snapshot의 `goalVersion`, `name`(필수 1~120자), `triggerText`(선택 최대 200자), 사용자가 선택한 정규화 `schedule`, 필수 `standardAction`/`minimumAction`, `nextAction`(선택 최대 300자)다. 행동은 계획 생성과 동일하게 text(필수 최대 500자), 선택 targetAmount(최대 100자), 선택 estimatedMinutes(1~1440)를 검증한다. 텍스트 앞뒤 공백 제거 및 빈 선택 텍스트 null 처리를 적용한다. ID나 새 plan version은 만들지 않으며 목표·계획·회차·Task를 저장하지 않는다. 응답은 no-store다.
- goalVersion은 초안에 사용한 목표 버전을 알리는 값이며 저장을 예약하거나 목표를 잠그는 값이 아니다. 사용자는 결과를 검토하고 `goalVersion`을 제외한 생성 필드로 `POST /api/v1/goals/{goalId}/plans`를 호출한다. 저장 시 최신 소유권/목표 존재 여부를 다시 검증하며 기존 계획 생성 정책을 따른다. 일시정지/달성/보관 목표도 읽기 전용 초안 생성은 허용한다. 추가 API로 기존 계약과 호환되며 DB migration은 없다.
- 2026-10-16 조정안 기반: `POST /api/v1/goals/{goalId}/adjustment-draft`는 필수 양수 `planId`와 `description`(공백 불가, 최대 2000자)을 받는다. REPEATABLE_READ 읽기 트랜잭션에서 본인 소유 목표와 계획의 현재 snapshot을 함께 읽고 해당 계획이 URL 목표에 연결되어 있는지 검사한다. 타인/없는 목표는 404/`70001`, 타인/없는 계획·다른 목표 연결·독립 계획은 404/`70002`다. 유효한 입력이면 비활성이어도 소유권/연결을 먼저 검사한다.
- 제공자 호출은 snapshot 트랜잭션 종료 후 실행한다. 현재 목표 title/desiredOutcome/motivationText, 현재 계획 name/triggerText/기본·최소 행동/nextAction과 정규화한 사용자 설명만 제공한다. 사용자·목표·계획 ID, 일정/날짜, 실천 이력과 집계는 보내지 않는다. 실천 기록에 근거한 자동 진단이나 실패 판정은 이 단계에 포함하지 않는다. 요청할 때 선택한 한 계획의 텍스트 조정만 지원한다.
- 응답은 `goalId/goalVersion/planId/planVersion`, 제안 `name/triggerText/standardAction/minimumAction/nextAction`과 변경하지 않은 현재 `schedule`이다. 제안 텍스트와 행동은 계획 초안과 같은 검증/정규화 규칙을 사용한다. 목표 연결·일정·상태·적용일은 제공자가 바꾸지 못한다. 목표/계획/회차/Task/버전을 저장하지 않으며 기본 비활성, adapter 없음, 장애·잘못된 출력은 503/`70009`다. 개인정보 로그 제외 및 no-store 정책도 동일하다.
- 버전은 사용한 snapshot의 정보이며 수정 예약이나 조건부 쓰기 토큰이 아니다. 반영 전 목표/계획을 다시 조회해 버전을 비교하고 달라졌다면 초안을 재검토한다. 사용자가 확인한 수정 필드만 `PATCH /api/v1/goal-plans/{planId}`로 보내며 goalVersion/planId/planVersion은 제외한다. effectiveFromDate는 사용자가 별도 선택하거나 기존 기본값을 따른다. 기존 수정 API에 버전 비교 기능을 추가하지 않으며 보관 계획 수정 거부 등 현재 CRUD 정책을 유지한다. 읽기 전용 초안은 일시정지/달성/보관 상태에도 허용한다. 추가 API/DTO이며 DB migration과 기존 클라이언트 수정은 필요 없다.

## 7. 기존 구현과 호환성

### 2026-10-16 명시적 개인 시스템 이관 구현

- 기존 API를 자동 전환하지 않는다. 본인 소유 `POST /api/v1/personal-systems/{systemId}/goal-migration` 요청으로 시스템 하나를 새 Goal/Plan으로 이관한다. DB mapping 컬럼으로 원본과 새 ID를 연결하고 재시도는 같은 ID를 반환한다.
- 버전/적용일, 회차 snapshot, 기본·최소·미분류 실천, 휴식, 메모/어려움/다음 행동과 실제 실천 시각/zone을 보존한다. 기존 연결 Task/D-Day ID도 유지한다. 새 목표 제목/계획 이름은 방향 문구 앞 120자를 쓰고 전체 방향은 목표 `desiredOutcome`에 보존한다. 새 엔티티 생성 시각은 이관 시각이며 원본 감사 시각은 구 테이블에 남긴다.
- 이관을 선택한 원본 시스템은 보관하고 원본 이력은 읽기 전용으로 유지한다. 구 DTO에 새 Goal/Plan/회차 ID를 제공한다. 이관된 원본의 수정·재개·실천/휴식 변경은 409/`60004`로 안내하고 새 ID/API를 사용한다. 이관하지 않은 시스템의 기존 계약은 유지한다. 이 동작 변경은 명시적 이관을 선택한 리소스에만 적용된다.
- 이후 회차와 Task 완료/재개는 새 모델만 갱신한다. Task는 복제하지 않으며 구 Task 알림은 끄고 새 회차 후보를 사용한다. 클라이언트는 이관 성공 후 `personal-system:{systemId}:...` 예약을 취소하고 새 Goal/Plan API를 조회한다.
- 게스트 병합은 원본/새 모델의 owner를 같은 트랜잭션에서 옮기고 ID와 mapping을 유지한다. 만료 정리는 연결 FK의 자식부터 제거한다. 운영 DB 적용은 별도 migration 후 진행하며 이 작업 중 production 데이터를 자동 이관하지 않는다.
- 구현 migration은 `20261016_add_personal_system_goal_mapping.sql`이다. `PersonalSystemResponse.migratedGoalId/migratedPlanId`, 구 회차의 `migratedOccurrenceId`는 nullable 대응 ID다. 재시도는 같은 ID와 원본 이관 건수를 반환하며 원본 변경은 허용하지 않는다. 새 계획의 목표를 나중에 바꿔도 최초 mapping은 보존한다.
- 구 회차 조회/증거 조회는 이관 시점 이력이며 canonical 변경을 다시 복제하지 않는다. 이후 live 상태는 새 API를 조회한다. source Task 완료/재개 callback은 이관된 구 회차를 건너뛰고 새 회차만 갱신한다. source 알림 시각은 null로 반환한다.
- 이관 후 새 데이터가 생성된 경우 mapping 미지원 backend로의 단순 배포 rollback은 지원하지 않는다. 쓰기를 중지하고 canonical 변경을 조정/복구하는 별도 계획이 먼저 필요하다. 자세한 클라이언트 절차/오류/response는 `API_V1_FRONTEND.md`의 이관 계약을 따른다.

- 현재 구현된 `/api/v1/personal-systems/**`는 목표 없는 독립 반복 행동 API로 당분간 유지할 수 있다.
- 새 구현에서는 사용자당 active system 1개 제한을 제거한다. 목표에는 여러 plan이 붙을 수 있고, 독립 반복 계획도 여러 개 존재할 수 있다.
- 기존 `linkedDdayGoalId`는 새 `GOAL.linkedDdayGoalId`로 옮기는 것을 우선한다. Task의 기존 D-Day 연결 필드는 유지한다.
- API 계약 변경은 외부 클라이언트에 영향을 준다. 새 `/goals/**` API를 먼저 추가하고 기존 `/personal-systems/**`는 호환 경로로 유지한 뒤 deprecation 계획을 별도 공지한다.

## 8. 구현 순서

1. 계약 확정: DTO, enum, validation, 오류 코드, OpenAPI 예시, D-Day/Task 연결 정책을 확정한다.
2. DB migration: `GOAL`, `GOAL_VERSION`, `GOAL_PLAN`, `GOAL_PLAN_VERSION`, `GOAL_PLAN_OCCURRENCE`, `GOAL_PRACTICE_RESULT`, `GOAL_DIRECT_LOG`, `TASK.linked_goal_id`를 추가한다.
3. Goal CRUD: 목표 생성, 목록/상세, 수정, 일시정지, 달성, 보관과 future plan policy를 구현한다.
4. Plan CRUD: 목표 하위/독립 반복 계획 생성, 수정, 정지, 재개, 보관을 구현한다.
5. 회차 materialize: 선택 요일 기반 기간 조회, 중복 방지, snapshot, pause/archive 제외, 알림 후보 정보를 구현한다.
6. 실천/휴식: 회차 결과 저장/수정/취소와 연결 Task 완료/재개 transaction을 구현한다.
7. 일회성 Task/직접 기록: 목표 연결 Task 생성/연결/해제, 직접 기록 생성/수정/취소와 idempotency를 구현한다.
8. 여정 조회: 회차, Task 완료, 직접 기록을 중복 없이 통합하고 전체 범위 summary를 반환한다.
9. 기존 personal system migration/호환: 기존 데이터를 새 goal plan으로 이동하거나 호환 adapter를 붙인다.
10. AI 초안/요약/조정: CRUD와 장애 격리해 순차 구현한다.

## 9. 테스트 수용 기준

- 목표 하나 아래 독립 반복 계획 둘을 생성할 수 있다.
- 각 계획 회차에 기본/최소 실천을 각각 기록할 수 있다.
- 목표 연결 일회성 Task 완료가 목표 여정에 한 번만 등장한다.
- 계획하지 않았지만 이미 한 일을 직접 기록하면 목표 여정에 별도 원본으로 등장한다.
- 연결 Task와 반복 회차는 같은 원본으로 중복 집계되지 않는다.
- 완료 취소, Task 변경, 목표 연결 수정 후 여정 원본과 summary가 일치한다.
- 계획/목표일 변경 후 과거 회차 snapshot과 과거 기록 귀속이 보존된다.
- 목표 달성 확인 시 `futurePlanPolicy`에 따라 미래 반복과 알림 후보가 멈추거나 유지된다.
- 기간 일부 page만 받아도 summary는 전체 조회 범위 기준으로 계산된다.
- 최소 행동, 휴식, 기록 없음, 종류 미확인은 서로 구분된다.
- 기한 도래, 행동 완료 수, 예상 분량으로 목표 달성률이나 실패를 자동 추정하지 않는다.
- AI 장애 시에도 목표/계획 CRUD, 실천, 휴식, 직접 기록, 여정 조회가 가능하다.
- 타 사용자 목표, 계획, 회차, 연결 Task, 직접 기록, 여정은 조회하거나 변경할 수 없다.
