# Timezone Contract

Last updated: 2026-07-26

이 문서는 사용자별 timezone 도입 전후의 백엔드 날짜/시간 계약을 정리한다.

## 현재 기준

- 서비스 기준 timezone은 `Asia/Seoul`이다.
- 코드 기준 상수는 `Constant.ZONE_ID`, `Constant.ZONE`이다.
- API의 `LocalDate`는 `YYYY-MM-DD` 형식이다.
- API의 `LocalDateTime`은 offset 없는 `YYYY-MM-DDTHH:mm:ss` 형식이다.
- offset 없는 `LocalDateTime`은 사용자 timezone 도입 전까지 `Asia/Seoul` 기준 wall-clock time으로 해석한다.
- 사용자 profile에는 IANA timezone ID를 저장할 수 있으며 기본값은 `Asia/Seoul`이다.

## 현재 백엔드 책임

- 오늘 날짜, 기본 완료 시각, D-Day 남은 일수, 응답 timestamp는 `Constant.ZONE` 기준으로 계산한다.
- Today, Calendar, 알림 후보 조회의 일정 overlap 경계는 저장된 사용자 timezone 기준 날짜를 서비스 기준 `Asia/Seoul` `LocalDateTime` 범위로 변환해 계산한다.
- 모바일은 서버 응답의 날짜/시간 값을 임의 timezone으로 재해석하지 않는다.
- 날짜만 저장되는 `targetDate`, D-Day 날짜는 요청 날짜 값 자체로 비교한다.
- `PATCH /api/v1/users/me/time-zone`은 사용자 timezone 설정값을 저장한다.

## 사용자 Timezone 도입 조건

사용자 timezone별 조회 경계는 제공하지만, timezone 변경에 따른 기존 반복 occurrence 재계산은 아직 도입하지 않았다. 재계산을 도입하려면 아래 변경을 하나의 계약 변경으로 처리한다.

- 반복 series의 `recurrenceTimeZone`과 사용자 timezone 변경 정책 분리
- 기존 offset 없는 `LocalDateTime` 응답의 호환성 정책 확정
- 모바일 앱 버전별 마이그레이션 계획 확정

## 변경 전 호환성 원칙

- 기존 `/api/v1/**` 응답 형식은 유지한다.
- timezone 필드를 추가하더라도 기존 클라이언트가 무시할 수 있는 optional 필드로 시작한다.
- `Asia/Seoul` 기준 데이터는 자동 변환하지 않는다.
- 사용자 timezone 변경은 과거 완료 기록을 재계산하지 않는 것을 기본값으로 한다.

## 아직 제공하지 않는 기능

- timezone 변경 migration API
- offset 또는 timezone 포함 `ZonedDateTime` 응답
