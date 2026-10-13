# Personal System Contract

Last updated: 2026-10-12

이 문서는 "원하는 방향 -> 시스템 하나 -> 오늘 실천 -> 일주일의 증거 -> 시스템 조정" 흐름의 백엔드 구현 계약이다. 2026-10-12 기준 AI 초안/회고/조정과 연결 Task 자동 생성은 아직 구현하지 않았고, 시스템 CRUD, 회차 조회, 실천/휴식, 원본 증거 조회를 먼저 제공한다.

첫 버전은 개인 사용자별 시스템 하나 중심이다. 복수 시스템 운영, Workspace, 점수, 연속 달성, 타이머, 자동 일정 재배치, 신규 서버 push, 문구 CMS는 범위에서 제외한다.

## 백엔드 1차 판단

- 기존 반복 Task occurrence를 직접 확장하기보다 `PERSONAL_SYSTEM`, `PERSONAL_SYSTEM_VERSION`, `PERSONAL_SYSTEM_OCCURRENCE`, `PERSONAL_SYSTEM_PRACTICE_RESULT`를 새 원본 모델로 두고, 필요 시 occurrence마다 개인 Task를 연결한다.
- Task는 Today/Calendar/알림 후보와 기존 완료 UI 재사용을 위한 연결 리소스다. 시스템 회차의 상태와 증거 원본은 새 테이블에 저장한다.
- 반복 Task의 `recurrenceScope`, `SKIPPED`, materialize 정책과 섞지 않는다. 시스템 수정은 `effectiveDate` 이후 회차에 새 version을 적용하고 과거 회차 snapshot은 보존한다.
- 과거 PENDING 회차는 "기록 없음"으로 조회만 하며 실패나 휴식으로 자동 변경하지 않는다.
- 최소 행동은 정상 실천 완료로 인정한다. 예상 시간이나 사용자가 선택한 시작 시간은 실제 소요 시간으로 추정하지 않는다.
- AI 초안/회고/조정은 CRUD, 실천, 원본 증거 조회와 장애 격리한다.

## 상태와 enum

```ts
type PersonalSystemStatus = 'ACTIVE' | 'PAUSED' | 'ARCHIVED';
type PersonalSystemOccurrenceStatus = 'PENDING' | 'PRACTICED' | 'RESTED';
type ActionVariant = 'STANDARD' | 'MINIMUM' | 'UNKNOWN';
type PracticeDifficulty = 'NO_TIME' | 'UNCLEAR_FIRST_ACTION' | 'TOO_HEAVY' | 'FORGOT' | 'OTHER';
```

`UNKNOWN`은 기존 Task 목록에서 직접 완료해 기본/최소 행동 여부를 알 수 없을 때만 사용한다. 백엔드는 기본 행동을 했다고 추정하지 않는다.

## 제안 데이터 모델

### `PERSONAL_SYSTEM`

| 필드 | 의미 |
| --- | --- |
| `ID` | 시스템 ID |
| `OWNER_USER_ID` | 소유 사용자 |
| `CURRENT_VERSION` | 현재 version 번호 |
| `STATUS` | `ACTIVE`, `PAUSED`, `ARCHIVED` |
| `LINKED_DDAY_GOAL_ID` | 선택 D-Day 연결 |
| `CREATED_AT`, `UPDATED_AT`, `ARCHIVED_AT` | 생성, 수정, 종료 시각 |

### `PERSONAL_SYSTEM_VERSION`

| 필드 | 의미 |
| --- | --- |
| `SYSTEM_ID`, `VERSION` | 시스템 version 식별자 |
| `DIRECTION_TEXT`, `MOTIVATION_TEXT` | 기한 없는 바람과 선택 이유 |
| `TRIGGER_TEXT` | "저녁 식사 후" 같은 텍스트 시작 조건 |
| `SCHEDULE_RULE` | 반복 요일, 기준 시작일, IANA timezone, 선택 알림 시각 |
| `STANDARD_ACTION`, `MINIMUM_ACTION` | 행동 설명, 선택 목표 분량, 선택 예상 시간 |
| `NEXT_ACTION` | 다음 실행의 첫 행동 |
| `EFFECTIVE_FROM_DATE` | 이 version이 적용되는 서비스 날짜 |
| `CREATED_AT` | version 생성 시각 |

필드 길이와 단위는 Task 제목 30자, 설명 300자, 예상 시간 5~1440분 제약을 그대로 쓰지 않는다. 1분 행동과 시간 없는 행동을 모두 표현할 수 있게 별도 validation을 확정한다.

### `PERSONAL_SYSTEM_OCCURRENCE`

| 필드 | 의미 |
| --- | --- |
| `ID` | 회차 ID |
| `SYSTEM_ID`, `SYSTEM_VERSION` | 적용된 시스템 version |
| `OWNER_USER_ID` | 소유 사용자 |
| `SERVICE_DATE` | 사용자 timezone 기준 예정 서비스 날짜 |
| `TIME_ZONE` | 회차 산출 기준 IANA timezone |
| `SNAPSHOT_JSON` | 회차 당시 행동, 최소 행동, trigger, 알림 시각 snapshot |
| `LINKED_TASK_ID` | 선택 연결 Task |
| `STATUS` | `PENDING`, `PRACTICED`, `RESTED` |
| `VERSION` | 낙관적 잠금 version |
| `CREATED_AT`, `UPDATED_AT` | 생성, 수정 시각 |

중복 방지는 `OWNER_USER_ID + SYSTEM_ID + SERVICE_DATE` 또는 rule 변경 정책을 반영한 동등 unique key로 처리한다.

### `PERSONAL_SYSTEM_PRACTICE_RESULT`

| 필드 | 의미 |
| --- | --- |
| `OCCURRENCE_ID` | 회차 ID. 첫 버전은 회차당 유효 결과 하나 |
| `ACTION_VARIANT` | `STANDARD`, `MINIMUM`, `UNKNOWN` |
| `PRACTICED_AT` | 사용자가 확인한 실천 시각 |
| `PRACTICED_TIME_ZONE` | 실천 시각 해석 timezone |
| `ACCOMPLISHED_NOTE` | 선택 메모 |
| `DIFFICULTIES` | 선택 어려움 목록 |
| `NEXT_ACTION_PATCH_KIND` | 유지, 비우기, 새 값 저장 구분 |
| `NEXT_ACTION_VALUE` | 새 다음 행동 값 |
| `CREATED_AT`, `UPDATED_AT` | 결과 저장, 수정 시각 |

`nextAction`을 입력하지 않음, 명시적 비우기, 새 값 저장은 서로 다른 요청 값으로 표현한다. 과거 기록 수정은 더 최신 회차나 시스템 수정으로 저장된 `nextAction`을 덮어쓰지 않는다.

## API

| 작업 | 경로 |
| --- | --- |
| 시스템 생성, 목록 | `POST /api/v1/personal-systems`, `GET /api/v1/personal-systems` |
| 시스템 상세, 수정, 상태 변경 | `GET /api/v1/personal-systems/{systemId}`, `PATCH /api/v1/personal-systems/{systemId}` |
| 기간별 예정 회차 | `GET /api/v1/personal-system-occurrences?from=YYYY-MM-DD&to=YYYY-MM-DD` |
| 회차 결과 저장, 수정, 취소 | `PUT /api/v1/personal-system-occurrences/{occurrenceId}/result`, `DELETE /api/v1/personal-system-occurrences/{occurrenceId}/result` |
| 회차 휴식 선택, 취소 | `PUT /api/v1/personal-system-occurrences/{occurrenceId}/rest`, `DELETE /api/v1/personal-system-occurrences/{occurrenceId}/rest` |
| 시스템 증거 | `GET /api/v1/personal-systems/{systemId}/evidence?from=YYYY-MM-DD&to=YYYY-MM-DD&timeZone=Asia/Seoul` |
| AI 시스템 초안 | `POST /api/v1/personal-systems/draft` |
| AI 조정 초안 | `POST /api/v1/personal-systems/{systemId}/adjustment-draft` |
| AI 회고 | `POST /api/v1/personal-systems/{systemId}/reflection` |

`GET evidence`는 AI 호출 없이 원본 기록과 집계만 반환한다. AI 회고는 별도 endpoint에서 생성하고 입력 version으로 캐시 무효화를 판단한다.

구현 상태:

- 구현: 시스템 생성/목록/상세/수정/상태 변경, 기간별 회차 조회, 실천 결과 저장/취소, 휴식/취소, 증거 조회
- 미구현: AI 시스템 초안, AI 조정 초안, AI 회고, 연결 Task 자동 생성과 Task 직접 완료 hook

백엔드 기본 정책:

- 첫 버전은 사용자당 `ACTIVE` 시스템 1개만 허용한다.
- result 취소는 `DELETE /api/v1/personal-system-occurrences/{occurrenceId}/result`를 사용한다.
- 다음 행동 patch 값은 `KEEP`, `CLEAR`, `REPLACE`로 표현한다.
- 증거 조회 기간은 양끝 포함 최대 31일이다.
- 최소 행동의 예상 시간은 1분부터 허용하고, 시간 없는 행동은 `estimatedMinutes=null`로 표현한다.

## 회차와 Task 연결 정책

- 회차 생성은 사용자 timezone과 schedule rule 기준으로 산출한다.
- 조회 범위의 회차가 없으면 중복 없이 materialize한다. 단순 조회가 과거 PENDING 상태를 바꾸지는 않는다.
- 지난 미실행 회차는 Today나 오래 미룬 일에 자동 누적하지 않는다.
- 이번 회차 휴식과 시스템 전체 일시정지는 별도 상태다. 재개 시 누락 회차를 몰아서 만들지 않는다.
- 연결 Task 자동 생성은 아직 구현하지 않았다. 구현 시 Task 제목/설명은 회차 snapshot 기반으로 만들고, `linkedTaskId`를 회차에 저장한다.
- Task 직접 완료 hook은 아직 구현하지 않았다. 구현 시 행동 종류를 알 수 없으면 회차 결과는 `ActionVariant.UNKNOWN`으로 저장한다.
- 연결 Task 완료/재개와 회차 결과 저장/취소는 하나의 transaction으로 맞춘다.
- 연결 Task 삭제는 회차 증거를 삭제하지 않는다. 회차는 `linkedTaskId` 단절 상태와 원본 snapshot으로 조회 가능해야 한다.

## 멱등성과 충돌

- `POST /api/v1/personal-systems`는 기존 `Idempotency-Key` replay 정책을 사용한다.
- 결과 저장 `PUT`은 같은 `occurrenceId`와 같은 payload에 대해 멱등으로 수렴한다.
- 다른 payload가 이미 저장된 PRACTICED/RESTED 결과를 바꾸려면 요청에 회차 `version` 또는 동등한 충돌 검출 값을 포함한다.
- stale version은 409로 거부하고 프론트는 회차 상세 또는 기간 조회를 새로고침한 뒤 재시도한다.
- 동시에 PRACTICED와 RESTED가 들어오면 먼저 commit된 상태가 우선이고 나중 요청은 version 충돌로 처리한다.

## 주간 증거 조회

`GET evidence`는 명시적 `from`, `to`, `timeZone`을 받는다. 날짜 범위는 양끝 포함이며 1차 상한은 31일로 제안한다.

응답에는 아래 값을 포함한다.

- 조회 범위와 집계 기준 timezone
- 일자별 원본 회차와 실행 기록
- 실천 회차 수, 실천 날짜 수
- 기본, 최소, 종류 미확인 실천 수
- 사용자 선택 휴식 수
- 기록 없음 회차와 조회 실패 구분
- 메모, 시스템 version/snapshot, 원본 occurrence/result ID
- 집계 입력 version 또는 `evidenceVersion`

완료 비율, 점수, 연속 달성은 첫 버전에서 제공하지 않는다.

## AI 기능 정책

- AI 시스템 초안은 사용자 입력 바람과 일상 조건을 구조화할 뿐 저장하거나 회차를 만들지 않는다.
- AI 회고는 서버 계산 집계와 사용자가 남긴 메모만 근거로 삼는다. 기록에 없는 성과, 감정, 실제 시간을 만들지 않는다.
- AI 조정 초안은 현재 시스템과 사용자가 고른 어려움을 입력으로 삼고, 사용자 확인 전 시스템을 자동 변경하지 않는다.
- AI provider key는 서버 환경변수로만 관리한다.
- 구조화 응답 검증, timeout, 사용자별 요청 제한, 안정적인 오류 코드와 재시도 정책을 구현한다.
- AI 장애가 있어도 시스템 CRUD, 실천, 휴식, 원본 증거 조회는 동작해야 한다.
- 바람, 이유, 메모, 어려움, 다음 행동은 개인정보가 포함될 수 있으므로 운영 로그에 원문을 남기지 않는다.
- provider, 보관, 삭제 정책은 구현 전 별도 확정한다.

## 알림

첫 버전은 기존 로컬 알림 후보 모델을 재사용한다. 서버 push 신규 구현은 범위 밖이다.

- 회차별 `notificationKey`는 `personal-system:{systemId}:{serviceDate}` 형식을 제안한다.
- 후보 응답에는 예정 시각, timezone, 취소/변경 판별용 version 또는 hash를 포함한다.
- 휴식, 일시정지, 주기 변경, 종료 시 모바일이 기존 예약을 정리할 수 있도록 회차 후보에서 제외하거나 cancel marker를 제공한다.

## 프론트 전달 기준

프론트 mock/API client는 아래 전제로 먼저 진행할 수 있다.

- `GET evidence`와 AI 회고는 분리한다. 증거 조회는 AI 없이 원본과 집계를 반환한다.
- 회차 상태는 `PENDING`, `PRACTICED`, `RESTED`이며, 지난 PENDING은 실패가 아니라 기록 없음이다.
- 실천 `actionVariant`는 `STANDARD`, `MINIMUM`, `UNKNOWN`을 허용한다.
- Task 직접 완료에서 행동 종류를 모르면 `UNKNOWN`으로 저장하며 기본 행동으로 추정하지 않는다.
- 최소 행동도 정상 완료다.
- 시스템 수정은 과거 회차 snapshot을 바꾸지 않고 적용일 이후 새 version으로 반영한다.
- 일시정지/재개는 누락 회차를 몰아서 생성하지 않는다.
- 로컬 알림은 서버 push가 아니라 회차별 안정 key와 예정 시각 후보를 내려주는 방식으로 맞춘다.

프론트 대기 없이 백엔드 기본값으로 확정한 항목은 아래다.

- 첫 화면 기본 기간은 프론트가 정하되 API는 명시적 `from/to`만 받는다.
- active 시스템은 첫 버전에서 사용자당 1개만 허용한다.
- 텍스트 필드는 direction/motivation/action 500자, trigger 200자, nextAction 300자, note 1000자로 시작한다.
- 예상 시간은 nullable이며 1~1440분을 허용한다.
- 다음 행동 patch 표현은 `KEEP`, `CLEAR`, `REPLACE`다.
- Task 직접 완료 hook을 구현할 때 행동 종류를 모르면 `UNKNOWN`으로 저장한다.
- result 취소는 `DELETE /result`다.

후속 구현 전 확정이 필요한 항목은 아래다.

- 연결 Task 자동 생성 시 Task 제목/설명 문구와 Today 노출 정책
- AI provider, 전송 항목, 보관/삭제 정책, 사용자 고지 문구

## 구현 순서

1. 계약 확정: DTO, enum, validation, 오류 코드, OpenAPI 예시, Task 연결 정책을 확정한다.
2. DB migration: 시스템, version, occurrence, result 테이블과 unique key, owner index를 추가한다.
3. CRUD: 시스템 생성, 목록/상세, 수정, 일시정지, 재개, 종료를 구현한다.
4. 회차 materialize: 기간 조회, 중복 방지, version snapshot, pause/archive 제외를 구현한다.
5. 실천/휴식: 결과 저장, 수정, 취소와 연결 Task 완료/재개 transaction을 구현한다.
6. 증거 조회: 원본 회차와 집계를 반환하고 수정/취소 후 집계 일치를 테스트한다.
7. 알림 후보: 회차별 로컬 알림 key와 변경 감지 정보를 제공한다.
8. AI 초안: 시스템 초안부터 분리 구현한다.
9. AI 회고/조정: 입력 version 검증, 근거 ID 연결, timeout/rate limit을 붙인다.

## 테스트 수용 기준

- 같은 생성/실천 요청을 재시도하거나 병렬 처리해도 시스템, 회차, 실천 결과가 중복되지 않는다.
- 최소 행동 실천은 PRACTICED로 집계되며 실제 시간과 성과를 추정하지 않는다.
- Task 직접 완료/취소와 회차 상태, 주간 집계가 transaction 기준으로 일치한다.
- 휴식, 일시정지, 재개, 주기 변경 후 과거 누락이 Today에 누적되지 않는다.
- 시스템 수정 후 과거 회차 snapshot이 보존되고 stale version 변경은 409로 거부된다.
- 주간 회고 입력은 원본 occurrence/result ID로 검증 가능하다.
- AI 장애 시에도 직접 작성, 실천, 휴식, 원본 증거 조회가 가능하다.
- 타 사용자 시스템, 회차, 연결 Task, 증거는 조회하거나 변경할 수 없다.
