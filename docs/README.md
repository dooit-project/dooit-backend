# Dooit Backend Docs

Last updated: 2026-10-30

이 디렉터리는 백엔드 API 계약, 모바일 연동, 운영 기준, 내부 관리 문서를 관리한다.

## 현재 기준 요약

문서 원본은 아래 순서로 판단한다. 저장소 문서의 구현 완료 표시가 실행 환경에 배포됐다는 뜻은 아니다.

| 우선순위 | 문서/원본 | 기준 |
| --- | --- | --- |
| 1 | 실행 중인 백엔드 `/v3/api-docs` | endpoint, request/response schema, validation, security 원본 계약 |
| 2 | `docs/api/**` | 사람이 읽는 API 계약, 권한/오류/시간대/반복/알림/공유 정책 |
| 3 | `docs/mobile/**` | 모바일 real-mode 검증 상태와 장애 대응 절차 |
| 4 | `docs/ops/**` | local production 운영, 환경변수, 배포·백업·복구·모니터링 절차 |
| 5 | `docs/project/ROADMAP.md` | 아직 닫히지 않은 제품/운영 작업 |

실제 도메인 기반 production 연결과 public smoke는 완료됐다. 현재 환경은 local 개발/테스트 및 Docker `prd_dooit`/`stg_dooit`다. STG는 loopback 18080과 별도 DB·JWT를 사용한다. [PRD/STG 운영](ops/DOCKER_ENVIRONMENTS.md)을 따른다.

| 항목 | 마지막 검증 기록 기준 | 상세 원본 |
| --- | --- | --- |
| 접근 제어 | Task·D-Day 차단은 10월 30일 작업본 STG 검증 완료. PRD 미반영 | [호환성 정책](api/API_COMPATIBILITY_POLICY.md), [로드맵](project/ROADMAP.md) |
| 목표·계획·실천 여정 | 운영 image `6cdc9e5`, schema 적용 및 공개 HTTPS smoke 완료 | [운영 기록](ops/LOCAL_PRODUCTION_RUNBOOK.md#2026-10-18-운영-image-6cdc9e5-배포-완료), [DB 이력](db/MIGRATION_HISTORY.md) |
| 빠른 기록 선택 유형 | STG 배포·실 API 수용 예시 통과, PRD 미배포 | [서버 회신](api/QUICK_CAPTURE_TYPE_CONTRACT.md) |
| 목표 AI | API 기반 배포, 기본 비활성. 외부 adapter 미구현 | [목표 계약](api/GOAL_JOURNEY_CONTRACT.md) |
| 모니터링 | 10월 30일 target UP·Loki 점검 로그 유입 확인. p95 histogram 부재·alerting 미구현 | [모니터링 운영](ops/MONITORING_RUNBOOK.md) |
| 모바일 실제 기기 | production smoke 미완료 | [백엔드 현황](mobile/MOBILE_API_BACKEND_STATUS.md) |

기능·운영 상태는 각 문서의 검증일 기준이며 이번 코드 검토에서 운영 환경을 재점검하지 않았다. 이후 가동 상태는 readiness와 운영 점검 명령으로 확인한다. 실제 URL/CORS는 [환경 기준](ops/ENVIRONMENT_INTEGRATION.md), 아직 닫히지 않은 제품/운영 작업은 [로드맵](project/ROADMAP.md)에서 관리한다. OpenAPI는 local/test에서 확인하며 production 기본값은 비공개다.

## 프론트/모바일 전달 문서

모바일 또는 프론트엔드 연동을 시작할 때는 아래 문서를 우선 전달한다.

| 문서 | 용도 |
| --- | --- |
| [`api/API_V1_FRONTEND.md`](./api/API_V1_FRONTEND.md) | v1 API endpoint, request/response, 검색, Today 재정렬, D-Day 계약 |
| [`api/DAILY_PLANNING_CONTRACT.md`](./api/DAILY_PLANNING_CONTRACT.md) | 오늘 계획, focus task, 예상 소요 시간, 하루 마감 summary 계약 |
| [`api/GOAL_JOURNEY_CONTRACT.md`](./api/GOAL_JOURNEY_CONTRACT.md) | 목표·반복 계획·회차 실천·직접 기록·여정, 구 개인 시스템 이관과 AI 계약 |
| [`api/QUICK_CAPTURE_TYPE_CONTRACT.md`](./api/QUICK_CAPTURE_TYPE_CONTRACT.md) | 선택 유형 보존 추가 요청, 적용 정책·오류·재시도와 STG/PRD 반영 상태 |
| [`ops/ENVIRONMENT_INTEGRATION.md`](./ops/ENVIRONMENT_INTEGRATION.md) | 환경별 URL, CORS origin, Expo/iOS/Android/실기기 차이 |
| [`api/AUTH_CONTRACT.md`](./api/AUTH_CONTRACT.md) | 게스트 발급·갱신·승격·병합, JWT, refresh rotation, 로그아웃 |
| [`api/API_ERROR_CODES.md`](./api/API_ERROR_CODES.md) | 오류 코드, 사용자 노출 message, 모바일 처리, retry 기준 |
| [`api/DATA_MODEL_GLOSSARY.md`](./api/DATA_MODEL_GLOSSARY.md) | Task, D-Day, User 주요 필드와 상태 전이 |
| [`api/TIMEZONE_CONTRACT.md`](./api/TIMEZONE_CONTRACT.md) | 서비스 시각과 사용자 timezone 조회 경계, 반복 보존 정책 |
| [`api/RECURRENCE_MODEL.md`](./api/RECURRENCE_MODEL.md) | 반복 series와 occurrence 모델 |
| [`api/NOTIFICATION_CONTRACT.md`](./api/NOTIFICATION_CONTRACT.md) | 모바일 로컬 알림과 서버 push 책임 경계 |
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
| [`ops/DOCKER_ENVIRONMENTS.md`](./ops/DOCKER_ENVIRONMENTS.md) | PRD/STG Docker 프로젝트·데이터 분리, STG 운영·검증 절차 |
| [`ops/LOCAL_PRODUCTION_RUNBOOK.md`](./ops/LOCAL_PRODUCTION_RUNBOOK.md) | 로컬 PC production 기동, 실제 도메인 공개, DB 백업·복구 절차 |
| [`ops/MONITORING_RUNBOOK.md`](./ops/MONITORING_RUNBOOK.md) | Prometheus, Grafana, Loki, Alloy 운영 구성과 후속 alerting |
| [`db/MIGRATION_HISTORY.md`](./db/MIGRATION_HISTORY.md) | Flyway 도입 전 수동 production DB migration 적용 이력 |
| [`project/ROADMAP.md`](./project/ROADMAP.md) | 앞으로 닫아야 할 백엔드/운영 작업 |

## 유지 원칙

- OpenAPI JSON과 사람이 읽는 문서의 계약이 다르면 OpenAPI 또는 구현을 먼저 확인한다.
- API 변경은 `API_V1_FRONTEND.md`, 관련 계약 문서, 테스트를 함께 갱신한다.
- 운영 값 자체는 문서에 쓰지 않고 환경변수 이름과 예시만 남긴다.
- 모바일 real-mode 실패는 원인과 검증 결과를 runbook 또는 status 문서에 남긴다.
- 완료 이력만 남은 문서는 별도로 유지하지 않고 현재 계약 문서와 migration 이력만 남긴다. 중복 전달 문서는 원본 계약/절차에 통합하고 내부 링크도 함께 갱신한다.
- 구현 완료·배포 완료·실기기 검증 완료를 구분한다. 배포 이력은 운영 runbook과 migration 이력, 미완료 항목은 로드맵에서 관리한다.
