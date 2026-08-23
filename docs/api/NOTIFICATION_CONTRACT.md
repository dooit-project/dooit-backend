# Notification Contract

Last updated: 2026-08-23

이 문서는 ToDoLab 모바일 알림 구현 전 백엔드와 모바일의 책임 경계를 정리한다. 현재 단계에서는 로컬 알림 예약 후보 API, 서버 push token 등록 API, 개인 owner Task 서버 push 자동 발송을 제공하며, 별도 서버 push 수동 발송 API는 제공하지 않는다.

## 현재 구현 상태

2026-08-24 기준 backend는 아래 범위까지 제공한다.

- 로컬 알림 후보 조회: `GET /api/v1/tasks/notification-candidates`
- push token 등록/조회/비활성화: `POST /api/v1/push-tokens`, `GET /api/v1/push-tokens`, `DELETE /api/v1/push-tokens/{id}`
- 서버 push 전송 이력 조회: `GET /api/v1/push-notification-histories`
- push provider 설정값: `enabled=false`, `provider=EXPO`, Expo endpoint 기본값
- Expo Push Service 단건 발송 client와 push ticket 성공/오류 해석
- `app.notification.push.enabled=true`일 때 개인 owner Task 알림 후보를 scheduler 기반 자동 발송

아직 제공하지 않는 범위:

- shared workspace 알림 후보의 서버 push 자동 발송
- 서버 push 수동 발송 API

## 책임 분리

### 백엔드 책임

- 반복 series의 occurrence 계산 기준은 백엔드가 소유한다.
- Today/Calendar 조회 시 조회 범위의 반복 occurrence를 materialize한다.
- occurrence별 완료, 미룸, 삭제, 이동 상태를 Task row에 저장한다.
- 반복 예외는 `recurrenceException`으로 표현한다.
- 모바일이 동기화할 수 있도록 Task 응답에 `recurrenceSeriesId`, `occurrenceDate`, `originalOccurrenceDate`, `recurrenceException`, `status`, `completedAt`, `startAt`, `endAt`, `targetDate`를 내려준다.
- `GET /api/v1/tasks/notification-candidates`로 지정 범위의 로컬 알림 예약 후보를 내려준다.
- `POST /api/v1/push-tokens`, `DELETE /api/v1/push-tokens/{id}`로 서버 push token 등록/해제 상태를 저장한다.

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
- 응답의 `suppressLocalNotification`은 서버 push 활성화 여부에 따라 내려준다.
- 응답에는 후보 판단 원본인 `task: TaskResponse`를 포함한다.

## 상태 변경 후 갱신 규칙

| 변경 | 백엔드 상태 | 모바일 알림 처리 |
| --- | --- | --- |
| 완료 | `status=DONE`, `completedAt` 기록 | 해당 Task/occurrence 예약 제거 |
| 완료 취소 | `status=TODAY`, 새 `targetDate` 적용 | 새 `startAt` 기준 예약 재평가 |
| Inbox 이동 | `status=INBOX`, 일정 제거 | 예약 제거 |
| Today 이동/이월 | `status=TODAY`, `targetDate`, `startAt`, `endAt` 변경 | 기존 예약 취소 후 새 시간 예약 |
| 반복 occurrence 단건 수정 | `recurrenceException=MODIFIED` | 같은 occurrence 예약 갱신 |
| 반복 occurrence 건너뛰기 | `DELETE /api/v1/tasks/{occurrenceId}?recurrenceScope=THIS`, `recurrenceException=SKIPPED`, 일정 제거 | 예약 제거 |
| 반복 occurrence 이동 | 향후 `recurrenceException=MOVED` | 원래 occurrence 예약 제거 후 이동된 occurrence 예약 |

## 서버 Push 도입 전 정책

- 서버 push 발송 API가 도입되기 전까지 백엔드는 알림 발송 책임을 갖지 않는다.
- 모바일 로컬 알림은 best-effort UX로 취급한다.
- 백엔드는 Task/occurrence 상태를 원본 데이터로 제공하고, 모바일은 동기화 결과를 기준으로 로컬 예약을 재구성한다.
- 서버 push token 등록/해제는 가능하지만, 등록된 token으로 알림을 발송하지는 않는다.

## 서버 Push Token API

```http
POST /api/v1/push-tokens
GET /api/v1/push-tokens
DELETE /api/v1/push-tokens/{id}
```

- `platform`은 `IOS`, `ANDROID`, `EXPO` 중 하나다.
- 같은 사용자와 `deviceToken` 조합을 다시 등록하면 기존 row를 갱신하고 활성화한다.
- 응답은 실제 token 전체를 반환하지 않고 `tokenSuffix`만 반환한다.
- `DELETE`는 물리 삭제가 아니라 비활성화다.

## 서버 Push 전송 이력 API

```http
GET /api/v1/push-notification-histories?limit=50
```

- 백엔드는 서버 push 전송 시도 결과를 `PUSH_NOTIFICATION_HISTORY`에 저장한다.
- 이력은 로그인 사용자 본인 범위로만 조회한다.
- `limit`은 1 이상 100 이하이며 기본값은 50이다.
- 응답은 `SUCCESS`와 `FAILED`를 모두 포함한다.
- 실제 device token 전체는 반환하지 않고 `tokenSuffix`만 반환한다.
- `idempotencyKey`는 성공 이력 중복 방지 기준으로 사용한다.

## 서버 Push 도입 후 중복 방지

향후 서버 push를 도입할 때는 아래 정책을 먼저 구현한다.

- 알림 source를 `LOCAL` 또는 `SERVER`로 구분한다.
- 동일 `task.id` 또는 `recurrenceSeriesId + occurrenceDate`에 대해 source별 중복 발송을 막는다.
- 서버 push가 활성화되면 백엔드는 알림 후보 응답의 `suppressLocalNotification=true`를 내려준다.
- 모바일은 `suppressLocalNotification=true`인 후보를 로컬 알림으로 예약하지 않는다.
- 서버 push 전환은 앱 버전별로 점진 적용한다.

## 서버 Push Provider와 Credential 정책

- 1차 provider는 `EXPO`로 확정한다.
- 모바일은 Expo Push Service용 `ExpoPushToken`을 등록한다. FCM/APNs native token은 1차 범위에서 사용하지 않는다.
- Android/iOS push credentials는 EAS/Expo project에 보관하고 백엔드 저장소나 DB에 저장하지 않는다.
- Expo enhanced push security를 켜면 백엔드는 Expo access token을 `TODOLAB_PUSH_ACCESS_TOKEN` 환경변수로만 주입받는다.
- `TODOLAB_PUSH_ACCESS_TOKEN` 값은 문서, git, 로그, 전송 이력에 남기지 않는다.
- access token이 비어 있으면 백엔드는 Authorization header 없이 Expo Push API를 호출하는 설정으로 간주한다.
- 운영 설정 prefix는 `app.notification.push`다.
- production 환경변수는 `TODOLAB_PUSH_ENABLED`, `TODOLAB_PUSH_PROVIDER`, `TODOLAB_PUSH_ENDPOINT`, `TODOLAB_PUSH_ACCESS_TOKEN`, `TODOLAB_PUSH_SCHEDULER_FIXED_DELAY`, `TODOLAB_PUSH_LOOK_AHEAD_WINDOW`를 사용한다.
- 기본값은 `enabled=false`, `provider=EXPO`, `endpoint=https://exp.host/--/api/v2/push/send`다.
- provider 설정은 발송 준비 계약이며, `enabled=true`만으로 발송 스케줄러가 동작하지는 않는다.

## 서버 Push 발송 스케줄러 설계

서버 push 발송은 아래 단계로 구현한다.

- Expo provider 호출은 `https://exp.host/--/api/v2/push/send` 단건 메시지 전송 client를 사용한다.
- Expo ticket의 `status=ok`와 `id`는 성공 이력의 `providerMessageId`로 저장할 값이다.
- Expo ticket의 `details.error`는 실패 이력의 `errorCode`와 token 비활성화 판단에 사용한다.
- scheduler는 `app.notification.push.enabled=true`일 때만 동작한다.
- scheduler는 `TODOLAB_PUSH_SCHEDULER_FIXED_DELAY=PT1M` 기본값에 따라 1분 주기로 실행한다.
- 발송 후보 window는 `TODOLAB_PUSH_LOOK_AHEAD_WINDOW=PT10M` 기본값에 따라 현재 시각부터 10분 뒤까지다.
- 후보 산출은 `GET /api/v1/tasks/notification-candidates`와 같은 기준을 사용한다.
- 서버 push는 활성 push token이 있는 사용자만 대상으로 한다.
- 1차 자동 발송 범위는 개인 owner Task 알림 후보로 제한한다. shared workspace 후보는 수신자/멤버별 중복 정책을 별도로 확정한 뒤 추가한다.
- idempotency key는 `SERVER:{task.id}` 또는 `SERVER:{recurrenceSeriesId}:{occurrenceDate}` 형식이다.
- 같은 owner와 idempotency key의 `SUCCESS` 이력이 있으면 다시 발송하지 않는다.
- 이력 기록 command가 idempotency key를 비워 보내면 백엔드가 Task/occurrence 필드로 같은 key를 생성한다.
- `FAILED` 이력만 있는 key는 다음 scheduler cycle의 재시도 대상이 될 수 있다.
- 발송 결과는 성공/실패 모두 `PUSH_NOTIFICATION_HISTORY` 전송 이력으로 저장한다.
- 실패한 token이 provider에서 영구 invalid로 판정되면 해당 push token을 비활성화한다.
- 외부 provider 호출은 짧은 timeout과 제한된 retry만 허용한다.

## 전송 실패 Token 비활성화 정책

Expo provider 기준 실패 처리는 아래처럼 구분한다.

- `DeviceNotRegistered`는 영구 실패로 보고 해당 `PUSH_DEVICE_TOKEN.active=false`로 비활성화한다.
- `InvalidPushToken`, `InvalidDeviceToken`처럼 provider가 명시한 invalid token 오류는 영구 실패로 보고 비활성화한다.
- rate limit, timeout, 5xx, 네트워크 오류는 일시 실패로 보고 token을 유지한다.
- credential 오류와 payload 오류는 token 자체 문제로 보지 않고 token을 유지한다.
- 일시 실패는 전송 이력에 `FAILED`로 저장하되 같은 scheduler cycle에서 즉시 무한 재시도하지 않는다.
- 영구 실패로 비활성화된 token은 `GET /api/v1/push-tokens` 활성 목록에서 제외된다.
- 사용자가 같은 device token을 다시 등록하면 기존 row를 갱신하고 `active=true`로 되살린다.

## 아직 제공하지 않는 API

- 서버 push 발송 API
