# Data Model Glossary

Last updated: 2026-07-25

이 문서는 ToDoLab 백엔드의 주요 도메인 모델과 모바일 API 응답 필드 의미를 정리한다.

## User

| 필드 | 의미 |
| --- | --- |
| `id` | 사용자 식별자 |
| `email` | 로그인 email. 저장 시 trim 후 소문자로 정규화한다. |
| `passwordHash` | BCrypt password hash. API 응답과 로그에 노출하지 않는다. |
| `displayName` | 표시 이름. 50자 이하 |
| `role` | `USER`, `ADMIN` |
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

`TaskRequest.type`이 null이면 `TODO`로 저장한다.

## Task 날짜 필드

| 필드 | 의미 |
| --- | --- |
| `startAt` | 일정 시작 시각. 없으면 날짜 없는 Task |
| `endAt` | 일정 종료 시각. 기간 일정에서만 사용하며 exclusive 경계다. |
| `allDay` | 종일 여부. true이면 `startAt`/`endAt`은 00:00이어야 한다. |
| `plannedDate` | 응답용 계획 날짜. `targetDate`가 있으면 우선 사용하고, 없으면 `startAt` 날짜를 사용한다. |
| `targetDate` | Today 실행 기준 날짜 |
| `todayOrder` | Today 실행 TODO 정렬 순서. `SCHEDULE`은 일괄 재정렬 대상이 아니다. |
| `completedAt` | 완료 시각. `DONE`일 때 기록한다. |
| `carryOverCount` | 이월 횟수 |
| `staleCarryOver` | `carryOverCount >= 3`이면 true |

날짜 규칙:

- `startAt`과 `endAt`이 모두 null이면 날짜 없는 Task이며 `status=INBOX`, `unscheduled=true`, `allDay=false`다.
- `endAt`만 보낼 수 없다.
- `endAt`은 `startAt` 이후여야 한다.
- 여러 날 일정 overlap은 `[startAt, endAt)` 기준이다.
- 서비스 기준 시간대는 사용자 timezone 도입 전까지 `Asia/Seoul`이다.

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

## Owner Scope

- v1 모바일 API는 항상 현재 인증 사용자의 User를 owner 기준으로 사용한다.
- 다른 사용자의 Task/D-Day id는 존재하지 않는 리소스처럼 처리한다.
- legacy `/api/**`는 웹/과거 호환 범위이며 모바일 신규 계약은 `/api/v1/**`다.
