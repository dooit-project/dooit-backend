# Backend Documentation Plan

Last updated: 2026-07-27

이 문서는 ToDoLab 백엔드 문서의 소유권과 유지 기준을 정리한다. 문서 목록과 독자별 전달 우선순위는 [`../README.md`](../README.md)를 기준으로 본다.

## 문서 그룹

### API 계약

| 문서 | 갱신 조건 |
| --- | --- |
| [`../api/API_V1_FRONTEND.md`](../api/API_V1_FRONTEND.md) | v1 endpoint, request/response, pagination, 삭제 응답, 오류 계약 변경 |
| [`../api/GUEST_ACCOUNT_HANDOFF.md`](../api/GUEST_ACCOUNT_HANDOFF.md) | 게스트 계정 bootstrap, 승격, 병합, 만료, rate limit 전달 계약 변경 |
| [`../api/AUTH_CONTRACT.md`](../api/AUTH_CONTRACT.md) | JWT claim, TTL, refresh token, 로그아웃, 401/403 정책 변경 |
| [`../api/API_ERROR_CODES.md`](../api/API_ERROR_CODES.md) | `ErrorCode` 추가/수정/삭제, retry 또는 사용자 노출 message 변경 |
| [`../api/DATA_MODEL_GLOSSARY.md`](../api/DATA_MODEL_GLOSSARY.md) | Task, D-Day, User 주요 필드나 상태 전이 변경 |
| [`../api/RECURRENCE_MODEL.md`](../api/RECURRENCE_MODEL.md) | 반복 series, occurrence, 예외 처리, 수정/삭제 scope 변경 |
| [`../api/NOTIFICATION_CONTRACT.md`](../api/NOTIFICATION_CONTRACT.md) | 로컬 알림 후보, 서버 push, 반복 occurrence 알림 책임 변경 |
| [`../api/TIMEZONE_CONTRACT.md`](../api/TIMEZONE_CONTRACT.md) | 사용자 timezone 저장/조회 도입 또는 날짜 경계 기준 변경 |

### 연동과 운영

| 문서 | 갱신 조건 |
| --- | --- |
| [`../ops/ENVIRONMENT_INTEGRATION.md`](../ops/ENVIRONMENT_INTEGRATION.md) | local/staging/prod URL, CORS origin, 문서 UI 공개 설정 변경 |
| [`../mobile/MOBILE_INTEGRATION_RUNBOOK.md`](../mobile/MOBILE_INTEGRATION_RUNBOOK.md) | 모바일 real-mode smoke test 절차 변경 또는 검증 결과 누적 |
| [`../mobile/MOBILE_INCIDENT_RUNBOOK.md`](../mobile/MOBILE_INCIDENT_RUNBOOK.md) | 장애 대응 절차, 분류 기준, 기록 양식 변경 |
| [`../api/API_COMPATIBILITY_POLICY.md`](../api/API_COMPATIBILITY_POLICY.md) | v1 유지 정책, breaking change, deprecation 기준 변경 |

### 관리와 이력

| 문서 | 갱신 조건 |
| --- | --- |
| [`ROADMAP.md`](./ROADMAP.md) | 백엔드 우선순위 추가/완료/범위 변경 |
| [`../mobile/MOBILE_API_BACKEND_STATUS.md`](../mobile/MOBILE_API_BACKEND_STATUS.md) | 모바일 요구사항 대비 구현/검증 상태 변경 |
| [`../history/TASK_DATE_MIGRATION.md`](../history/TASK_DATE_MIGRATION.md) | Task 날짜 도메인 전환 이력 보강 |
| [`../history/UI_UX_BACKLOG.md`](../history/UI_UX_BACKLOG.md) | 서버 렌더링 화면 UI/UX 개선 항목 변경 |
| [`../history/UI_UX_RESPONSIVE_CHECKLIST.md`](../history/UI_UX_RESPONSIVE_CHECKLIST.md) | 서버 렌더링 화면 반응형 점검 기준 변경 |

## 유지 원칙

- API 구현 변경은 OpenAPI JSON, 사람이 읽는 문서, 테스트를 함께 갱신한다.
- `/v3/api-docs`는 기계 판독 가능한 원본 계약이다.
- 사람이 읽는 문서는 OpenAPI를 보완하되 별도 계약을 만들지 않는다.
- 문서의 미정 값은 실제 값을 추측하지 않고 `미정`, `확인 필요`로 남긴다.
- 운영 secret, 실제 token, password, private URL은 문서에 기록하지 않는다.
- 모바일 연동 실패가 발생하면 원인, 수정, 검증 결과를 runbook 또는 status 문서에 남긴다.

## 커밋 전 점검

- API endpoint 또는 DTO 변경: `API_V1_FRONTEND.md`, OpenAPI 테스트, 관련 controller/service 테스트 확인
- 인증/인가 변경: `AUTH_CONTRACT.md`, 401/403 응답 테스트 확인
- 오류 코드 변경: `API_ERROR_CODES.md`, `ErrorCodeTest` 확인
- 날짜/시간 변경: `TIMEZONE_CONTRACT.md`, `DateTimeContractTest` 확인
- 반복/알림 변경: `RECURRENCE_MODEL.md`, `NOTIFICATION_CONTRACT.md`, occurrence 테스트 확인
- 운영 설정 변경: `ENVIRONMENT_INTEGRATION.md`, 관련 config 테스트 확인
