# Daily Planning Contract

Last updated: 2026-08-31

이 문서는 Dooit 모바일의 `오늘 계획 -> 실행 -> 하루 마감` 흐름을 지원하기 위해 백엔드에 추가할 계약 초안이다. 현재 문서는 구현 완료 계약이 아니라 프론트 요청을 백엔드 구현 단위로 정리한 backlog 원본이다.

구현 전에는 이 문서, `API_V1_FRONTEND.md`, OpenAPI annotation, migration, integration test를 함께 갱신한다.

## 우선순위

| 우선순위 | 항목 | 상태 | 백엔드 판단 |
| --- | --- | --- | --- |
| B0 | 일일 계획 영속화 | 구현 | 오늘의 핵심 1~3개와 계획 확정 상태를 저장 |
| B0 | 예상 소요 시간 | 구현 | 계획 수립과 하루 실행량 조절에 바로 필요 |
| B1 | 계획/마감 batch mutation | 보류 | 단건 mutation으로 MVP 검증 후 부분 실패 문제가 확인되면 추가 |
| B1 | Checklist | 구현 | 한 단계 item 모델로 제한해 Task 하위 실행 단위를 제공 |
| B2 | 일일 결과 summary | 보류 | 여러 조회 조합 비용 또는 기기 간 결과 불일치가 확인될 때 추가 |
| B2 | Category entity | 보류 | 좌측 메뉴/정보구조 확정 후 별도 계약 |

## B0. 일일 계획 영속화

현재 `status=TODAY`, `plannedDate`, `todayOrder`는 오늘 목록과 순서를 표현하지만, 사용자가 오늘의 핵심으로 확정한 1~3개와 계획 완료 상태는 표현하지 못한다.

구현 resource:

```http
GET /api/v1/daily-plans/{date}
PUT /api/v1/daily-plans/{date}
```

요청 예시:

```json
{
  "focusTaskIds": [41, 17, 93],
  "status": "CONFIRMED"
}
```

응답 예시:

```json
{
  "date": "2026-08-30",
  "status": "CONFIRMED",
  "focusTaskIds": [41, 17, 93],
  "confirmedAt": "2026-08-30T08:10:00+09:00",
  "closedAt": null,
  "updatedAt": "2026-08-30T08:10:00+09:00"
}
```

초안 타입:

```ts
type DailyPlanStatus = 'DRAFT' | 'CONFIRMED' | 'CLOSED';

type DailyPlanResponse = {
  date: string;
  status: DailyPlanStatus;
  focusTaskIds: number[];
  confirmedAt: string | null;
  closedAt: string | null;
  updatedAt: string | null;
};

type DailyPlanRequest = {
  focusTaskIds: number[];
  status: DailyPlanStatus;
};
```

계약 조건:

- `focusTaskIds`는 해당 사용자 소유의 같은 날짜 미완료 Today Task만 허용한다.
- 최대 3개이며 배열 순서가 집중 순서다.
- Task 완료, 삭제, Inbox 이동, 다른 날짜 이동 시 focus 목록에서 자동 제거한다.
- 같은 사용자와 날짜에는 하나의 plan만 존재한다.
- `PUT`은 전체 교체 방식이다. 같은 요청을 반복해도 같은 focus 목록과 상태로 수렴한다.
- 날짜 경계는 사용자 timezone을 따른다. 세부 기준은 `TIMEZONE_CONTRACT.md`를 원본으로 한다.
- 1차 구현은 개인 Today Task만 허용하고 workspace focus는 후속 계약으로 분리한다.

## B0. 예상 소요 시간

Task 생성, 수정, 응답에 nullable 필드를 추가했다.

```json
{
  "estimatedDurationMinutes": 30
}
```

계약 조건:

- `null`은 사용자가 시간을 정하지 않은 상태다.
- 허용 범위는 5~1440분이다.
- 5분 단위 입력을 권장하지만 백엔드가 표시 preset을 강제하지 않는다.
- `SCHEDULE`의 `startAt`, `endAt`과 별개다. 일정 길이를 Task 예상 시간에 중복 저장하지 않는다.
- `TaskTemplate.defaultDurationMinutes`는 template 기반 Task 생성 시 `estimatedDurationMinutes`의 기본값으로 적용한다.
- 반복 Task는 series 생성 시 기본 예상 시간을 저장하고, occurrence별 수정 범위는 기존 `recurrenceScope` 정책과 맞춘다.
- Today 응답에는 합계를 별도 필드로 중복 저장하지 않는다. 프론트는 Task 목록의 `estimatedDurationMinutes`를 합산한다.

반영 상태:

- `TASK.estimated_duration_minutes` nullable column 추가
- `TaskTemplate.defaultDurationMinutes`는 template 기반 Task의 `estimatedDurationMinutes` 기본값으로 적용
- `TaskRequest`, `TaskResponse`, 검색 응답의 nested `TaskResponse` 계약 갱신
- validation, OpenAPI schema, integration test 추가

## B1. 계획/마감 Batch Mutation

프론트 MVP는 기존 단건 mutation으로 먼저 검증한다. 부분 성공 문제가 실제 흐름에서 확인되면 atomic endpoint를 추가한다.

```http
POST /api/v1/daily-plans/{date}/apply
```

요청 예시:

```json
{
  "operations": [
    { "taskId": 41, "action": "MOVE_TO_DATE", "date": "2026-08-31" },
    { "taskId": 17, "action": "MOVE_TO_INBOX" },
    { "taskId": 93, "action": "SET_DEFER_REASON", "reason": "TOO_BIG" }
  ],
  "planStatus": "CLOSED"
}
```

계약 조건:

- 모든 operation이 성공하거나 전체가 rollback되는 atomic 처리를 우선한다.
- 중복 Task ID는 400으로 거부한다.
- 권한 없음은 403, 리소스 없음은 404, 이미 완료/삭제/상태 충돌은 409로 구분한다.
- `Idempotency-Key`를 지원한다.
- 응답에는 갱신된 Task와 plan을 포함해 추가 refetch 없이 cache를 맞출 수 있게 한다.

초안 action:

- `MOVE_TO_DATE`
- `MOVE_TO_INBOX`
- `SET_DEFER_REASON`
- `COMPLETE`
- `REOPEN`

## B1. Checklist

깊은 계층형 subtask 대신 Task 아래 한 단계 checklist를 우선한다.

구현 계약:

- 별도 checklist endpoint를 제공한다.
- item 생성, 제목 수정, 완료, 재개, 삭제를 제공한다.
- 한 Task 안에서 item 전체 ID 목록 기반 정렬을 지원한다.
- 부모 Task 완료 시 미완료 item은 같은 완료 시각으로 함께 완료한다.
- 반복 Task occurrence의 checklist는 occurrence Task row별로 독립 관리한다.

권장 제한:

- item 제목 최대 길이와 Task당 item 최대 개수를 명시한다.
- checklist item에는 별도 날짜, 알림, 담당자, 재귀 checklist를 두지 않는다.
- 1차 범위에서 Workspace Task checklist는 제외한다. 적용할 경우 OWNER, EDITOR, VIEWER 권한을 기존 Task 계약과 일치시킨다.

## B2. 일일 결과 Summary

당일 조회를 여러 번 조합하는 비용이나 기기 간 결과 불일치가 실제로 확인될 때만 추가한다.

```http
GET /api/v1/daily-plans/{date}/summary
```

최소 후보:

- 계획 시점의 focus 수
- 완료 수
- 다른 날짜로 이동한 수
- Inbox로 이동한 수
- 아직 결정하지 않은 수

생산성 점수, 연속 달성, 비교 ranking은 범위에 포함하지 않는다.

## B2. Category

카테고리 entity, 정렬, 변경, 삭제, Task 수 집계는 오늘 계획과 예상 시간보다 후순위다. 좌측 상단 메뉴 정보구조를 확정한 뒤 별도 계약으로 분리한다.

현재 백엔드가 제공하는 범위:

- Task의 nullable `category` 문자열 필드
- 검색 API의 `category` exact match
- 빈 검색 결과의 `suggestedCategories`

추가 계약 후보:

- `GET /api/v1/categories`
- `POST /api/v1/categories`
- `PATCH /api/v1/categories/{id}`
- `DELETE /api/v1/categories/{id}`
- category별 active Today/Inbox/전체 count
- 사용자별 정렬, 숨김, 기본 category 정책

## 구현 요청 순서

1. 일일 계획 resource와 예상 소요 시간 OpenAPI 초안을 합의한다.
2. migration, integration test, backend source를 구현한다.
3. `API_V1_FRONTEND.md`와 OpenAPI JSON 기준으로 mock/real response fixture와 frontend type을 연결한다.
4. production 배포 metadata를 확인한다.
5. Android, iOS, Web의 같은 계정에서 계획과 예상 시간 동기화 smoke를 진행한다.
6. 부분 실패 근거가 확인되면 batch mutation을 추가한다.
7. checklist, summary, category 순으로 확장한다.
