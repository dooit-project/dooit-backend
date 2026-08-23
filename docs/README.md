# ToDoLab Backend Docs

Last updated: 2026-08-20

이 디렉터리는 백엔드 API 계약, 모바일 연동, 운영 기준, 내부 관리 문서를 관리한다.

## 현재 기준 요약

2026-08-20 현재 문서 원본은 아래 순서로 판단한다.

| 우선순위 | 문서/원본 | 기준 |
| --- | --- | --- |
| 1 | 실행 중인 백엔드 `/v3/api-docs` | endpoint, request/response schema, validation, security 원본 계약 |
| 2 | `docs/api/**` | 사람이 읽는 API 계약, 권한/오류/시간대/반복/알림/공유 정책 |
| 3 | `docs/mobile/**` | 모바일 real-mode 검증 상태와 장애 대응 절차 |
| 4 | `docs/ops/**` | local production 운영, 환경변수, 배포·백업·복구 절차 |
| 5 | `docs/project/ROADMAP.md` | 아직 닫히지 않은 제품/운영 작업 |

현재 로드맵에서 닫아야 할 최우선 작업은 Android production Tailscale HTTPS 실기기 smoke, host 상시 가용성 검증, offsite backup 확정이다. 제품 기능 쪽은 빠른 등록/템플릿/공유 1차 구현이 닫혔고, 서버 push 실제 발송과 검색·추천 고도화가 다음 후보로 남아 있다.

## 프론트/모바일 전달 문서

모바일 또는 프론트엔드 연동을 시작할 때는 아래 문서를 우선 전달한다.

| 문서 | 용도 |
| --- | --- |
| [`api/API_V1_FRONTEND.md`](./api/API_V1_FRONTEND.md) | v1 API endpoint, request/response, 검색, Today 재정렬, D-Day 계약 |
| [`api/GUEST_ACCOUNT_HANDOFF.md`](./api/GUEST_ACCOUNT_HANDOFF.md) | 게스트 계정 생성, 승격, 병합, 만료, rate limit 모바일 전달 계약 |
| [`ops/ENVIRONMENT_INTEGRATION.md`](./ops/ENVIRONMENT_INTEGRATION.md) | 환경별 URL, CORS origin, Expo/iOS/Android/실기기 차이 |
| [`api/AUTH_CONTRACT.md`](./api/AUTH_CONTRACT.md) | JWT claim, access token TTL, refresh token rotation, 로그아웃, 401/403 |
| [`api/API_ERROR_CODES.md`](./api/API_ERROR_CODES.md) | 오류 코드, 사용자 노출 message, 모바일 처리, retry 기준 |
| [`api/DATA_MODEL_GLOSSARY.md`](./api/DATA_MODEL_GLOSSARY.md) | Task, D-Day, User 주요 필드와 상태 전이 |
| [`api/TIMEZONE_CONTRACT.md`](./api/TIMEZONE_CONTRACT.md) | 현재 `Asia/Seoul` 기준과 사용자 timezone 도입 원칙 |
| [`api/RECURRENCE_MODEL.md`](./api/RECURRENCE_MODEL.md) | 반복 series와 occurrence 모델 |
| [`api/NOTIFICATION_CONTRACT.md`](./api/NOTIFICATION_CONTRACT.md) | 모바일 로컬 알림과 향후 서버 push 책임 경계 |
| [`api/SHARING_CONTRACT.md`](./api/SHARING_CONTRACT.md) | 일정 공유 workspace 권한, scope, migration 설계 |

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
| [`ops/GUEST_ACCOUNT_PRODUCTION_APPLY.md`](./ops/GUEST_ACCOUNT_PRODUCTION_APPLY.md) | 게스트 계정 production DB migration, 배포, smoke test 체크리스트 |
| [`db/MIGRATION_HISTORY.md`](./db/MIGRATION_HISTORY.md) | Flyway 도입 전 수동 production DB migration 적용 이력 |
| [`project/ROADMAP.md`](./project/ROADMAP.md) | 앞으로 닫아야 할 백엔드/운영 작업 |

## 유지 원칙

- OpenAPI JSON과 사람이 읽는 문서의 계약이 다르면 OpenAPI 또는 구현을 먼저 확인한다.
- API 변경은 `API_V1_FRONTEND.md`, 관련 계약 문서, 테스트를 함께 갱신한다.
- 운영 값 자체는 문서에 쓰지 않고 환경변수 이름과 예시만 남긴다.
- 모바일 real-mode 실패는 원인과 검증 결과를 runbook 또는 status 문서에 남긴다.
- 완료 이력만 남은 문서는 별도로 유지하지 않고 현재 계약 문서와 migration 이력만 남긴다.
