# Goal Journey Contract

Last updated: 2026-10-15

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

2026-10-15 구현: 기간별 회차 GET은 양끝 포함 1~31일을 지원한다. 과거 회차 스냅샷은 보존하며 오늘 이후의 미실천·Task 미연결 회차는 날짜별 적용 version으로 갱신한다. 정지/보관 또는 변경된 선택 요일에 해당하지 않는 미래 미실천 회차는 응답에서 제외하고 DB 원본은 유지한다. 목표 필터는 회차의 당시 목표 ID를 사용한다. `notificationKey`, local `scheduledNotificationAt`, `timeZone`을 함께 반환하며 알림 시각이 없거나 계획이 정지/보관된 경우 후보 시각은 null이다.

목표 상태 요청의 `PAUSE_FUTURE`는 현재 연결된 계획을 일시정지한다. `KEEP_FUTURE`는 계획 상태를 유지한다. 목표 재개/달성 취소 시 계획은 상태 API로 명시적으로 재개한다. 실천/휴식 결과와 회차 Task 자동 연결은 아직 미구현이다.

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
| `CREATED_AT`, `UPDATED_AT`, `CANCELED_AT` | 생성/수정/취소 시각 |

계획하지 않았지만 이미 한 일을 빠르게 남기는 원본이다. 추가 실천을 기존 반복 회차의 기본/최소 결과로 억지 변환하지 않고, 일회성 기록이 주말 회차를 자동 완료하지 않는다.

### Task 확장

기존 `TASK.linkedDdayGoalId`와 D-Day Task 연결 API는 유지한다. 새 목표 연결을 위해 `TASK.linkedGoalId`를 nullable로 추가한다. 목표 상세에서 일회성 Task를 만들면 `linkedGoalId`가 자동 설정되고, 일반 Task 생성/수정에서도 목표를 선택할 수 있다.

Task 완료는 목표 여정의 `TASK_COMPLETION` 원본으로 들어온다. 이미 목표에 연결된 기존 Task를 연결할 때는 새 직접 기록을 복제하지 않는다.

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
    "unknownCount": 3,
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
  "journeyVersion": "goal:100:2026-10-14:2026-11-30:42"
}
```

## 6. 정책

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
- provider, 보관, 삭제 정책은 구현 전 별도 확정한다.

## 7. 기존 구현과 호환성

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
