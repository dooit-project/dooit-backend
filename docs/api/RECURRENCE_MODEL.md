# Recurrence Model

Last updated: 2026-07-23

이 문서는 반복 Task/일정 저장과 v1 API 계약 기준이다.

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
- 월말(`BYMONTHDAY=-1`), 윤년 2월 29일, DST가 있는 time zone 경계에서 occurrence 산출을 테스트한다.

## API 상태

- `POST /api/v1/tasks`는 `recurrence` 하위 객체로 반복 생성을 지원한다.
- 반복 Task 응답은 기존 occurrence 필드와 함께 `recurrence` 상세 객체를 포함한다.
- 반복 생성 요청은 `startAt`이 필요하다.
- `recurrence.timeZone`은 생략하면 `Asia/Seoul`이다.
- `recurrence.interval`은 생략하면 `1`이다.
- `recurrence.recurrenceRule`을 직접 보내거나, `frequency`, `interval`, `byDays`, `byMonthDays`, `recurrenceUntil`, `recurrenceCount`로 RRULE을 생성할 수 있다.
- `BYDAY`를 지정한 `WEEKLY` 반복은 시작일 요일이 `BYDAY`에 포함되어야 한다.
- `BYMONTHDAY`를 지정한 `MONTHLY`, `YEARLY` 반복은 시작일 일자가 `BYMONTHDAY`에 포함되어야 한다.
- `BYDAY`는 `WEEKLY`, `BYMONTHDAY`는 `MONTHLY` 또는 `YEARLY`에서만 지원한다.
- 반복 수정/삭제 API는 기존 `PUT /api/v1/tasks/{id}`, `DELETE /api/v1/tasks/{id}`의 `recurrenceScope` query parameter를 사용한다.
- 기존 series의 반복 규칙 자체를 바꾸는 API는 아직 제공하지 않는다. `PUT /api/v1/tasks/{id}`에 `recurrence`를 보내면 HTTP 400이다.
- Today/Calendar owner 조회는 연결된 반복 series와 template Task를 기준으로 조회 범위의 누락 occurrence Task를 materialize한다.
- materialize는 `DAILY`, `WEEKLY`, `MONTHLY`, `YEARLY`와 검증된 `BYDAY`, `BYMONTHDAY`, `COUNT`, `UNTIL` 범위를 사용한다.
- 현재 materialize 기준 template은 같은 series에 연결된 가장 이른 non-exception Task다.
- occurrence별 완료 상태는 materialize된 `TASK` row의 `status=DONE`, `completedAt`으로 저장한다.
- 완료된 occurrence도 `recurrenceSeriesId`, `occurrenceDate`, `originalOccurrenceDate`를 유지하므로 같은 series의 다른 occurrence와 독립적으로 구분된다.
- 수정/삭제 scope는 `THIS`, `THIS_AND_FUTURE`, `ALL`이다.
- `THIS` 수정은 해당 occurrence만 `MODIFIED`로 표시한다.
- `THIS_AND_FUTURE`, `ALL` 수정은 materialize된 occurrence row 범위에 적용한다.
- `THIS_AND_FUTURE`, `ALL` 수정은 완료된 occurrence의 `status=DONE`, `completedAt`을 보존한다.
- 반복 occurrence 삭제는 재생성을 막기 위해 row를 보존하고 `SKIPPED` marker로 표시한다.
- `THIS_AND_FUTURE`, `ALL` 삭제는 아직 materialize되지 않은 미래 occurrence 재생성을 막기 위해 series 종료일을 함께 줄인다.
- 사용자 timezone 변경은 기존 `RecurrenceSeries.timeZone`과 이미 materialize된 occurrence를 자동 재계산하지 않는다. timezone 변경 정책은 [`TIMEZONE_CONTRACT.md`](./TIMEZONE_CONTRACT.md)를 따른다.
- 모바일은 반복 생성 UI를 열 수 있다. 다만 반복 rule 편집 UI는 현재 지원 필드(`frequency`, `interval`, `byDays`, `byMonthDays`, `recurrenceUntil`, `recurrenceCount`) 범위 안에서만 노출한다.

## 반복 Rule 변경 정책

기존 series rule 변경 API는 아직 제공하지 않는다. 구현 시 아래 정책을 따른다.

- rule 변경은 `effectiveDate` 기준으로 적용한다. `THIS_AND_FUTURE` 편집에서 선택한 occurrence의 `occurrenceDate`를 기본 `effectiveDate`로 사용한다.
- `effectiveDate` 이전 occurrence row는 삭제하거나 새 rule로 재계산하지 않는다.
- `effectiveDate` 이전 완료 occurrence는 `status=DONE`, `completedAt`, `recurrenceSeriesId`, `occurrenceDate`, `originalOccurrenceDate`를 그대로 보존한다.
- `effectiveDate` 이전 `SKIPPED`, `MOVED`, `MODIFIED` 예외 row도 사용자가 명시적으로 만든 이력으로 보고 그대로 보존한다.
- `effectiveDate` 이후 일반 materialized occurrence row는 새 rule 기준 재생성을 위해 정리할 수 있다.
- `effectiveDate` 이후 이미 완료된 occurrence는 완료 이력을 보존한다. 새 rule에 포함되지 않더라도 임의 삭제하지 않는다.
- `effectiveDate` 이후 `SKIPPED`, `MOVED`, `MODIFIED` 예외 row는 사용자가 명시적으로 변경한 데이터이므로 기본적으로 보존한다.
- 새 rule materialize 과정은 같은 `recurrenceSeriesId + occurrenceDate` row가 있으면 중복 생성하지 않는다.
- rule 변경 후 새 rule 범위 밖이 된 미래 일반 occurrence는 삭제 대상이다. 완료 또는 예외 row는 삭제 대상에서 제외한다.
- `COUNT` 기반 rule을 변경하면 `effectiveDate` 이후 새 occurrence 산출 개수는 변경된 `COUNT`와 보존 row를 함께 고려해 별도 테스트로 검증한다.
- 모바일은 API가 제공되기 전까지 기존 series rule 편집 UI를 노출하지 않는다.

## 생성 예시

매주 화요일 9시 회의:

```json
{
  "title": "주간 회의",
  "description": "화요일 싱크",
  "type": "SCHEDULE",
  "startAt": "2026-07-07T09:00:00",
  "endAt": "2026-07-07T10:00:00",
  "category": "업무",
  "allDay": false,
  "recurrence": {
    "frequency": "WEEKLY",
    "interval": 1,
    "byDays": ["TU"],
    "recurrenceCount": 10
  }
}
```
