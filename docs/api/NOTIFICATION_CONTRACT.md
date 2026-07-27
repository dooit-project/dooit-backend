# Notification Contract

Last updated: 2026-07-25

이 문서는 ToDoLab 모바일 알림 구현 전 백엔드와 모바일의 책임 경계를 정리한다. 현재 단계에서는 로컬 알림 예약 후보 API를 제공하며, 서버 push API는 제공하지 않는다.

## 책임 분리

### 백엔드 책임

- 반복 series의 occurrence 계산 기준은 백엔드가 소유한다.
- Today/Calendar 조회 시 조회 범위의 반복 occurrence를 materialize한다.
- occurrence별 완료, 미룸, 삭제, 이동 상태를 Task row에 저장한다.
- 반복 예외는 `recurrenceException`으로 표현한다.
- 모바일이 동기화할 수 있도록 Task 응답에 `recurrenceSeriesId`, `occurrenceDate`, `originalOccurrenceDate`, `recurrenceException`, `status`, `completedAt`, `startAt`, `endAt`, `targetDate`를 내려준다.
- `GET /api/v1/tasks/notification-candidates`로 지정 범위의 로컬 알림 예약 후보를 내려준다.

### 모바일 책임

- 현재는 백엔드 알림 후보 API에 포함된 가까운 미래 Task/occurrence만 로컬 알림 후보로 사용한다.
- 로컬 알림 예약 범위는 모바일이 정하되, 서버에서 조회한 범위 밖 occurrence를 자체 생성하지 않는다.
- 로컬 알림 예약 key는 백엔드가 내려준 `notificationKey`를 사용한다.
- 같은 `recurrenceSeriesId + occurrenceDate` 조합이 다시 내려오면 기존 예약을 갱신한다.
- `DONE`, `SKIPPED`, 삭제/미노출된 occurrence는 로컬 예약에서 제거한다.

## 로컬 알림 후보 기준

모바일은 아래 조건을 모두 만족하는 Task만 로컬 알림 후보로 삼는다.

- `status=TODAY`
- `startAt`이 null이 아님
- `completedAt`이 null
- `recurrenceException`이 null 또는 `MOVED`, `MODIFIED`
- `recurrenceException=SKIPPED`가 아님
- 모바일이 정한 로컬 예약 윈도우 안에 있음

날짜 없는 `INBOX` Task와 완료된 `DONE` Task는 로컬 알림 후보가 아니다.

## 로컬 알림 후보 API

```http
GET /api/v1/tasks/notification-candidates?from=YYYY-MM-DD&to=YYYY-MM-DD
```

- `from`, `to`는 필수이며 양끝 날짜를 포함한다.
- 조회 범위는 최대 31일이다.
- 백엔드는 조회 범위의 반복 occurrence를 materialize한 뒤 후보를 산출한다.
- 응답의 `notificationKey`는 단건 Task면 `task:{taskId}`, 반복 occurrence면 `recurrence:{recurrenceSeriesId}:{occurrenceDate}` 형식이다.
- 응답에는 후보 판단 원본인 `task: TaskResponse`를 포함한다.

## 상태 변경 후 갱신 규칙

| 변경 | 백엔드 상태 | 모바일 알림 처리 |
| --- | --- | --- |
| 완료 | `status=DONE`, `completedAt` 기록 | 해당 Task/occurrence 예약 제거 |
| 완료 취소 | `status=TODAY`, 새 `targetDate` 적용 | 새 `startAt` 기준 예약 재평가 |
| Inbox 이동 | `status=INBOX`, 일정 제거 | 예약 제거 |
| Today 이동/이월 | `status=TODAY`, `targetDate`, `startAt`, `endAt` 변경 | 기존 예약 취소 후 새 시간 예약 |
| 반복 occurrence 단건 수정 | `recurrenceException=MODIFIED` | 같은 occurrence 예약 갱신 |
| 반복 occurrence 삭제 | `recurrenceException=SKIPPED`, 일정 제거 | 예약 제거 |
| 반복 occurrence 이동 | 향후 `recurrenceException=MOVED` | 원래 occurrence 예약 제거 후 이동된 occurrence 예약 |

## 서버 Push 도입 전 정책

- 서버 push가 도입되기 전까지 백엔드는 알림 발송 책임을 갖지 않는다.
- 모바일 로컬 알림은 best-effort UX로 취급한다.
- 백엔드는 Task/occurrence 상태를 원본 데이터로 제공하고, 모바일은 동기화 결과를 기준으로 로컬 예약을 재구성한다.

## 서버 Push 도입 후 중복 방지

향후 서버 push를 도입할 때는 아래 정책을 먼저 구현한다.

- 알림 source를 `LOCAL` 또는 `SERVER`로 구분한다.
- 동일 `task.id` 또는 `recurrenceSeriesId + occurrenceDate`에 대해 source별 중복 발송을 막는다.
- 서버 push가 활성화된 사용자는 모바일 로컬 알림 예약을 끄거나, 서버가 내려주는 suppress flag를 따른다.
- 서버 push 전환은 앱 버전별로 점진 적용한다.

## 아직 제공하지 않는 API

- 서버 push 등록/해제 API
- 알림 전송 이력 API
