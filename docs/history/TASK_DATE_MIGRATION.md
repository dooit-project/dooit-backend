# Task 날짜 도메인 마이그레이션

## 목표

사용자에게 노출되는 Task 날짜를 `plannedDate` 하나로 통일한다.
시간은 계획 날짜에 딸린 선택 정보로 취급한다.

최종 모델:

- `plannedDate`: 할 날짜. 기록함 Task는 `null`
- `startTime`, `endTime`: 시간이 있는 일정에서만 사용
- `allDay`: 시간이 없는 계획 날짜를 Calendar에 표시하는 방식
- 여러 날 일정은 별도 기간 모델이 필요할 때 확장

## 현재 제약

현재 저장소는 같은 날짜를 두 형태로 보관한다.

- `TARGET_DATE`: Today와 지난 미완료 조회 기준
- `START_AT`, `END_AT`: Calendar 범위와 시간 표현

두 값을 즉시 하나로 합치면 Calendar 범위 조회, 배치 메일, 기존 API와 데이터 마이그레이션이
동시에 변경된다. 따라서 호환 계약부터 전환한다.

## 전환 단계

1. API 응답에 `plannedDate`를 추가한다.
   - 현재는 `TARGET_DATE`를 우선 사용하고 없으면 `START_AT` 날짜를 사용한다.
   - 기존 `targetDate`는 호환을 위해 유지한다.
2. 화면과 신규 코드는 `plannedDate`를 우선 사용한다.
   - 구버전 응답을 위해 `targetDate`, `startAt` fallback을 유지한다.
3. 요청 DTO를 `plannedDate + 선택적 시간` 기준으로 변경한다.
   - 서버 내부에서 기존 `START_AT`, `END_AT` 표현으로 변환하는 호환 계층을 둔다.
4. Calendar와 배치 조회를 새 계획 날짜 계약으로 전환한다.
5. `TARGET_DATE`를 물리적 `plannedDate` 컬럼으로 유지하고 추후 컬럼명만 변경한다.
6. `START_AT`, `END_AT`은 선택 시간과 여러 날 범위의 호환 투영으로 유지한다.
7. 자동 생성 여부에 의존하는 코드가 없으므로 `SCHEDULE_SOURCE`를 제거한다.

## 결정

- `plannedDate`가 사용자와 API가 사용하는 대표 날짜다.
- `targetDate`는 마이그레이션 기간의 레거시 호환 필드다.
- `TARGET_DATE`는 최종적으로 `PLANNED_DATE`로 이름을 변경한다.
- `START_AT`, `END_AT`은 시간 및 여러 날 범위를 위해 유지하되 날짜 불변식을 강제한다.
- `scheduleSource`는 동작 분기에 필요하지 않아 제거한다.
- `SCHEDULE_SOURCE` 컬럼은 `20260616_drop_schedule_source.sql`로 삭제한다.
