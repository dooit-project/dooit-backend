# Data Model Glossary

Last updated: 2026-10-18

이 문서는 Dooit 백엔드의 주요 도메인 모델과 모바일 API 응답 필드 의미를 정리한다.

## User

| 필드 | 의미 |
| --- | --- |
| `id` | 사용자 식별자 |
| `email` | 로그인 email. 게스트는 null이며 저장 시 trim 후 소문자로 정규화한다. |
| `accountType` | `GUEST` 또는 `REGISTERED`. 수명 주기는 [인증 계약](AUTH_CONTRACT.md) 기준 |
| `passwordHash` | BCrypt password hash. API 응답과 로그에 노출하지 않는다. |
| `displayName` | 표시 이름. 50자 이하 |
| `role` | `USER`, `ADMIN` |
| `timeZone` | 사용자 IANA timezone 설정값. 기본값은 `Asia/Seoul` |
| `createdAt` | 사용자 생성 시각 |
| `updatedAt` | 사용자 수정 시각. 생성 직후 null일 수 있다. |

## Task 기본 분류

| 필드 | 값 | 의미 |
| --- | --- | --- |
| `type` | `TODO` | 실행할 일. 기본 Task type |
| `type` | `SCHEDULE` | 시간 또는 기간이 있는 일정 |
| `type` | `IDEA` | 실행 날짜가 아직 없는 아이디어 |
| `status` | `INBOX` | 날짜가 없거나 Inbox로 이동된 Task |
| `status` | `TODAY` | 특정 날짜의 실행 대상 Task |
| `status` | `DONE` | 완료된 Task |

`TaskRequest.type`이 null이면 startAt/endAt이 모두 없을 때 TODO, 시각 구간이 있으면 SCHEDULE로 해석한다. 빠른 기록의 requestedType은 [별도 계약](QUICK_CAPTURE_TYPE_CONTRACT.md)을 따른다.

## Task 날짜 필드

| 필드 | 의미 |
| --- | --- |
| `startAt` | 일정 시작 시각. 계획일만 있는 TODO는 null일 수 있다. |
| `endAt` | 일정 종료 시각. 기간 일정에서만 사용하며 exclusive 경계다. |
| `allDay` | 종일 여부. true이면 `startAt`/`endAt`은 00:00이어야 한다. |
| `plannedDate` | 응답용 계획 날짜. `targetDate`가 있으면 우선 사용하고, 없으면 `startAt` 날짜를 사용한다. |
| `targetDate` | Today 계획 기준 날짜. 미래 날짜도 가능하다. |
| `todayOrder` | Today 실행 TODO 정렬 순서. `SCHEDULE`은 일괄 재정렬 대상이 아니다. |
| `completedAt` | 완료 시각. `DONE`일 때 기록한다. |
| `carryOverCount` | 이월 횟수 |
| `staleCarryOver` | `carryOverCount >= 3`이면 true |

날짜 규칙:

- `startAt`과 `endAt`이 모두 null이면 `unscheduled=true`, `allDay=false`다. 일반 생성은 INBOX이며 빠른 기록 TODO 날짜 입력은 시각 구간 없이 `targetDate/plannedDate`와 TODAY 상태를 저장한다. 상태 이름이나 unscheduled만으로 오늘 날짜/계획일 유무를 판단하지 않는다.
- `endAt`만 보낼 수 없다.
- `endAt`은 `startAt` 이후여야 한다.
- 여러 날 일정 overlap은 `[startAt, endAt)` 기준이다.
- 서비스 기준 시각과 사용자 timezone 조회 경계는 [시간대 계약](TIMEZONE_CONTRACT.md)을 따른다.

## Task 상태 전이

| 동작 | 결과 |
| --- | --- |
| 생성, 날짜 없음 | `status=INBOX`, 일정 필드 없음 |
| 생성, 날짜 있음 | `status=TODAY`, `targetDate=startAt 날짜` |
| Today 이동 | 일정이 없으면 종일 일정으로 만들고 `status=TODAY`, `targetDate` 설정 |
| Inbox 이동 | 일정 필드를 지우고 `status=INBOX`, `todayOrder=null`, `completedAt=null` |
| 완료 | `status=DONE`, `completedAt` 기록 |
| 완료 취소 | `status=TODAY`, 요청 날짜 기준 일정 재배치 |
| 이월 | `status=TODAY`, 요청 날짜로 일정 이동, `carryOverCount` 증가 |

## D-Day

| 필드 | 의미 |
| --- | --- |
| `id` | D-Day 목표 식별자 |
| `title` | 목표 제목. 50자 이하 |
| `targetDate` | 목표 날짜 |
| `daysLeft` | 서비스 기준 날짜에서 목표 날짜까지 남은 일수 |
| `createdAt` | 목표 생성 시각 |
| `owner` | 목표 소유 사용자 |

D-Day와 연결된 Task는 `ddayGoalId`, `ddayGoalTitle`, `ddayGoalTargetDate`, `ddayDaysLeft`를 응답에 포함한다. D-Day 삭제 시 연결 Task는 삭제하지 않고 연결만 해제한다.

## 반복 모델

반복 series와 occurrence 세부 계약은 [`RECURRENCE_MODEL.md`](./RECURRENCE_MODEL.md)를 원본으로 한다.

| 필드 | 의미 |
| --- | --- |
| `recurrenceSeriesId` | 반복 series 식별자 |
| `occurrenceDate` | materialize된 occurrence 날짜 |
| `originalOccurrenceDate` | 이동/수정/삭제 전 원래 occurrence 날짜 |
| `recurrenceException` | `SKIPPED`, `MOVED`, `MODIFIED` |

반복 occurrence의 완료, 수정, 삭제 상태는 Task row에 저장한다.

## 목표·계획·실천 여정

D-Day와 별도로 Goal(선택 목표일), GoalPlan(반복 계획), GoalPlanOccurrence(날짜별 회차), GoalPracticeResult(실천/휴식), GoalDirectLog(직접 기록)을 제공한다. Task의 nullable linkedGoalId로 일회성 행동을 연결한다. 구 personal system은 호환 API와 명시적 이관 mapping을 유지한다. 상세 필드·상태·이관 제약은 [목표 여정 계약](GOAL_JOURNEY_CONTRACT.md)을 원본으로 본다.

## Owner Scope

- v1 모바일 API는 항상 현재 인증 사용자의 User를 owner 기준으로 사용한다.
- 다른 사용자의 Task/D-Day id는 존재하지 않는 리소스처럼 처리한다.
- legacy Task·D-Day 경로는 보안상 HTTP 접근을 차단한다. 모바일/웹 계약은 `/api/v1/**`이며 이관 기준은 [호환성 정책](API_COMPATIBILITY_POLICY.md#6-legacy-api-정책)을 따른다.

## Resource Scope

공유 workspace 도입을 위해 Task, D-Day, 반복 series는 `scope`를 가진다.

| 값 | 의미 |
| --- | --- |
| `PERSONAL` | 기존 개인 데이터. `/api/v1/tasks/**`, `/api/v1/dday-goals/**`에서 조회/수정하는 범위 |
| `WORKSPACE` | 공유 workspace 데이터. workspace membership 기준으로 접근한다 |

기존 개인 API는 `PERSONAL` scope만 반환한다. `WORKSPACE` scope의 Task/D-Day/반복 series는 workspace 하위 API에서만 다룬다.
