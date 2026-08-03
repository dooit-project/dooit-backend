# ToDoLab Backend Docs

Last updated: 2026-08-03

이 디렉터리는 백엔드 API 계약, 모바일 연동, 운영 기준, 내부 관리 문서를 관리한다.

## 프론트/모바일 전달 문서

모바일 또는 프론트엔드 연동을 시작할 때는 아래 문서를 우선 전달한다.

| 문서 | 용도 |
| --- | --- |
| [`api/API_V1_FRONTEND.md`](./api/API_V1_FRONTEND.md) | v1 API endpoint, request/response, 검색, Today 재정렬, D-Day 계약 |
| [`ops/ENVIRONMENT_INTEGRATION.md`](./ops/ENVIRONMENT_INTEGRATION.md) | 환경별 URL, CORS origin, Expo/iOS/Android/실기기 차이 |
| [`api/AUTH_CONTRACT.md`](./api/AUTH_CONTRACT.md) | JWT claim, access token TTL, refresh token 미도입, 로그아웃, 401/403 |
| [`api/API_ERROR_CODES.md`](./api/API_ERROR_CODES.md) | 오류 코드, 사용자 노출 message, 모바일 처리, retry 기준 |
| [`api/DATA_MODEL_GLOSSARY.md`](./api/DATA_MODEL_GLOSSARY.md) | Task, D-Day, User 주요 필드와 상태 전이 |
| [`api/TIMEZONE_CONTRACT.md`](./api/TIMEZONE_CONTRACT.md) | 현재 `Asia/Seoul` 기준과 사용자 timezone 도입 원칙 |
| [`api/RECURRENCE_MODEL.md`](./api/RECURRENCE_MODEL.md) | 반복 series와 occurrence 모델 |
| [`api/NOTIFICATION_CONTRACT.md`](./api/NOTIFICATION_CONTRACT.md) | 모바일 로컬 알림과 향후 서버 push 책임 경계 |

실제 API 원본 계약은 실행 중인 백엔드의 `/v3/api-docs` OpenAPI JSON이다.

## 모바일 검증 문서

| 문서 | 용도 |
| --- | --- |
| [`mobile/MOBILE_INTEGRATION_RUNBOOK.md`](./mobile/MOBILE_INTEGRATION_RUNBOOK.md) | real-mode smoke test 절차 |
| [`mobile/MOBILE_API_BACKEND_STATUS.md`](./mobile/MOBILE_API_BACKEND_STATUS.md) | 모바일 요구사항 대비 백엔드 구현/검증 상태 |
| [`mobile/MOBILE_INCIDENT_RUNBOOK.md`](./mobile/MOBILE_INCIDENT_RUNBOOK.md) | 모바일 연동 장애 접수와 백엔드 확인 순서 |

## 운영/정책 문서

| 문서 | 용도 |
| --- | --- |
| [`api/API_COMPATIBILITY_POLICY.md`](./api/API_COMPATIBILITY_POLICY.md) | v1 호환성, breaking change, deprecation 기준 |
| [`ops/LOCAL_PRODUCTION_RUNBOOK.md`](./ops/LOCAL_PRODUCTION_RUNBOOK.md) | 로컬 PC production 기동, Tailscale HTTPS, DB 백업·복구 절차 |
| [`project/BACKEND_DOCUMENTATION_PLAN.md`](./project/BACKEND_DOCUMENTATION_PLAN.md) | 문서 소유권과 유지 기준 |
| [`project/ROADMAP.md`](./project/ROADMAP.md) | 완료된 기준 상태와 다음 백엔드 우선순위 |

## 서버 화면/이력 문서

아래 문서는 백엔드가 제공하는 서버 렌더링 화면 또는 과거 도메인 전환 이력을 다룬다. 모바일 API 전달 묶음에는 기본 포함하지 않는다.

| 문서 | 용도 |
| --- | --- |
| [`history/TASK_DATE_MIGRATION.md`](./history/TASK_DATE_MIGRATION.md) | Task 날짜 도메인 전환 이력 |
| [`history/UI_UX_BACKLOG.md`](./history/UI_UX_BACKLOG.md) | 서버 렌더링 화면 UI/UX 개선 이력 |
| [`history/UI_UX_RESPONSIVE_CHECKLIST.md`](./history/UI_UX_RESPONSIVE_CHECKLIST.md) | 서버 렌더링 화면 반응형 점검 기준 |

## 유지 원칙

- OpenAPI JSON과 사람이 읽는 문서의 계약이 다르면 OpenAPI 또는 구현을 먼저 확인한다.
- API 변경은 `API_V1_FRONTEND.md`, 관련 계약 문서, 테스트를 함께 갱신한다.
- 운영 값 자체는 문서에 쓰지 않고 환경변수 이름과 예시만 남긴다.
- 모바일 real-mode 실패는 원인과 검증 결과를 runbook 또는 status 문서에 남긴다.
