# Recurrence Model

Last updated: 2026-07-23

이 문서는 반복 Task/일정 저장을 위한 백엔드 내부 모델 기준이다. 현재 단계에서는 모델과 저장 구조만 확정하며, 모바일에서 호출할 반복 생성/수정 API는 아직 제공하지 않는다.

## 목표

- 반복 규칙은 `RECURRENCE_SERIES`에 한 번 저장한다.
- 실제로 표시, 완료, 이동, 수정되는 각 occurrence는 기존 `TASK` row로 표현한다.
- 반복 예외는 occurrence Task에 표시해 `SKIPPED`, `MOVED`, `MODIFIED`를 구분한다.
- owner scope는 series와 occurrence Task 양쪽에 유지한다.

## 테이블

### `RECURRENCE_SERIES`

| 컬럼 | 의미 |
| --- | --- |
| `ID` | 반복 series ID |
| `OWNER_USER_ID` | 소유 사용자 |
| `FREQUENCY` | `DAILY`, `WEEKLY`, `MONTHLY`, `YEARLY` |
| `INTERVAL_VALUE` | 반복 간격. 1 이상 |
| `RRULE` | 원본 RRULE 문자열 |
| `TIME_ZONE` | occurrence 계산 기준 time zone. 현재 기본 기준은 `Asia/Seoul` |
| `RECURRENCE_START_AT` | 반복 시작 일시 |
| `RECURRENCE_UNTIL` | 반복 종료일. 없을 수 있음 |
| `RECURRENCE_COUNT` | 반복 횟수. 없을 수 있음 |
| `CREATED_AT`, `UPDATED_AT` | 생성/수정 시각 |

### `TASK` 반복 필드

| 컬럼 | 의미 |
| --- | --- |
| `RECURRENCE_SERIES_ID` | 연결된 반복 series |
| `OCCURRENCE_DATE` | 이 Task가 나타내는 occurrence 날짜 |
| `ORIGINAL_OCCURRENCE_DATE` | 이동/수정 전 원래 occurrence 날짜 |
| `RECURRENCE_EXCEPTION` | `SKIPPED`, `MOVED`, `MODIFIED` |

## 검증 기준

- `frequency`, `rrule`, `timeZone`, `recurrenceStartAt`은 필수다.
- `interval`은 1 이상이다.
- `recurrenceCount`가 있으면 1 이상이다.
- `recurrenceUntil`은 `recurrenceStartAt`의 날짜보다 빠를 수 없다.
- `timeZone`은 유효한 IANA Zone ID여야 한다.
- RRULE은 `FREQ`, `INTERVAL`, `COUNT`, `UNTIL`, `BYDAY`, `BYMONTHDAY`만 지원한다.
- RRULE `FREQ`는 `frequency`와 일치해야 한다.
- RRULE `INTERVAL`은 `interval`과 일치해야 한다. 생략하면 `1`로 본다.
- RRULE `COUNT`는 `recurrenceCount`와 함께 지정하며 값이 일치해야 한다.
- RRULE `UNTIL`은 `recurrenceUntil`과 함께 지정하며 `YYYYMMDD` 형식과 값이 일치해야 한다.
- RRULE `COUNT`와 `UNTIL`은 함께 사용할 수 없다.
- RRULE `BYDAY`는 `MO`, `TU`, `WE`, `TH`, `FR`, `SA`, `SU`를 콤마로 나열한다.
- RRULE `BYMONTHDAY`는 `-31`부터 `31`까지 허용하되 `0`은 허용하지 않는다. `-1`은 월말 표현으로 예약한다.
- 월말/윤년 occurrence 산출과 time zone 경계 처리는 다음 단계에서 별도로 테스트한다.

## API 상태

- 반복 생성/수정/삭제 API는 아직 없다.
- 기존 Task 생성/수정 API는 반복 필드를 받지 않는다.
- Today/Calendar owner 조회는 연결된 반복 series와 template Task를 기준으로 조회 범위의 누락 occurrence Task를 materialize한다.
- materialize는 `DAILY`, `WEEKLY`, `MONTHLY`, `YEARLY`와 검증된 `BYDAY`, `BYMONTHDAY`, `COUNT`, `UNTIL` 범위를 사용한다.
- 현재 materialize 기준 template은 같은 series에 연결된 가장 이른 non-exception Task다.
- occurrence별 완료 상태는 materialize된 `TASK` row의 `status=DONE`, `completedAt`으로 저장한다.
- 완료된 occurrence도 `recurrenceSeriesId`, `occurrenceDate`, `originalOccurrenceDate`를 유지하므로 같은 series의 다른 occurrence와 독립적으로 구분된다.
- 모바일은 반복 UI를 실제 저장 기능처럼 열면 안 된다.
