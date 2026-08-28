# ToDoLab Backend

ToDoLab Backend는 일정, 할 일, D-Day, 반복 일정, 공유 workspace, 알림을 하나의 모바일/웹 API로 제공하는 Spring Boot 백엔드입니다.

단순한 CRUD 서버보다 “실제 앱을 오래 쓰는 데 필요한 계약”에 초점을 둡니다. 게스트로 바로 시작하고, 계정으로 승격하거나 기존 계정에 병합하고, 반복 일정과 Today 정렬을 보존하며, production에서는 Tailscale HTTPS와 백업/복구 절차까지 함께 검증합니다.

## 지금 되는 것

| 영역 | 제공 기능 |
| --- | --- |
| Auth | JWT 로그인, refresh token rotation, logout, 비밀번호 재설정 |
| Guest | 게스트 발급, 만료 전 갱신, 회원가입 승격, 기존 계정 병합, cleanup, rate limit |
| Task | 할 일/일정 CRUD, Today/Calendar 조회, 여러 날 일정 overlap, Today 재정렬 |
| Quick Capture | `내일 3시 회의`, `이번 주 금요일 병원`, `8월 15일 행사`, `매주 월요일 운동` 같은 규칙 기반 빠른 등록 |
| D-Day | 목표 생성/조회/삭제, D-Day 기반 Task 생성과 연결 |
| Recurrence | RRULE 기반 반복 series, occurrence materialize, 개별/이후/전체 수정·삭제 |
| Search | 제목, 설명, category, D-Day 제목, 반복 여부 검색과 highlight |
| Sharing | workspace 생성, 초대/수락/거절, 멤버 권한, workspace Task/D-Day |
| Notification | 로컬 알림 후보, push token, 발송 이력, Expo push client, scheduler |
| Ops | Docker Compose production, readiness health, release/rollback, backup/restore, Tailscale smoke |

## API 첫 확인

기본 v1 경로는 `/api/v1`입니다.

```http
GET /api/v1/auth/me
Authorization: Bearer <accessToken>
```

실행 중인 백엔드의 OpenAPI JSON이 기계 판독 가능한 원본 계약입니다.

| 용도 | URL |
| --- | --- |
| OpenAPI JSON | `/v3/api-docs` |
| Swagger UI | `/swagger-ui` |
| Scalar Reference | `/scalar.html` |

사람이 읽는 v1 계약은 [docs/api/API_V1_FRONTEND.md](./docs/api/API_V1_FRONTEND.md)를 봅니다.

## 빠르게 실행

요구 사항:

- JDK 25
- Docker 및 Docker Compose

테스트:

```bash
./gradlew test
```

로컬 빌드:

```bash
./gradlew clean build
```

Docker Compose production 형태로 실행:

```bash
cp .env.example .env
docker volume create todolab-mysql-data
docker compose up --build
```

`.env`와 `application-local.yml`은 저장소에 커밋하지 않습니다.

## 기술 스택

| 영역 | 기술 |
| --- | --- |
| Language | Java 25 |
| Framework | Spring Boot 4.0.6, Spring MVC, Virtual Threads |
| Data | Spring Data JPA, QueryDSL, MySQL 8 |
| Batch & Mail | Spring Batch, Spring Mail |
| Security | Spring Security, JWT Resource Server |
| API Docs | OpenAPI, Swagger UI, Scalar |
| Build & Test | Gradle Wrapper, JUnit, JaCoCo |
| Runtime | Docker, Docker Compose |

## 코드 구조

```text
src/main/java/com/todolab/
├── auth/          # 회원, 게스트, JWT, refresh, 비밀번호 재설정
├── task/          # Task, quick capture, template, recurrence
├── dday/          # D-Day 목표와 연결 Task
├── workspace/     # 공유 workspace, 멤버, 권한
├── notification/  # 알림 후보, push token, 발송 이력
├── calendar/      # iCalendar 읽기 전용 feed
├── batch/         # 일일 일정 메일 배치
├── common/        # 공통 응답, 오류, 멱등성, 로깅
├── config/        # security, CORS, OpenAPI, health, metadata
└── mail/          # 메일 발송
```

## 운영 기준

production app port는 `127.0.0.1:8080`에만 바인딩하고, 외부 접근은 Tailscale HTTPS 또는 실제 도메인의 reverse proxy HTTPS 경로로만 엽니다. MySQL port는 host에 공개하지 않습니다.

대표 점검 명령:

```bash
./scripts/check-production-env.sh
./scripts/check-production-host.sh
./scripts/check-production-routine.sh
./scripts/check-production-recovery.sh
./scripts/report-production-status.sh
```

릴리스와 rollback:

```bash
./scripts/release-production.sh
./scripts/rollback-production.sh <previous-image-tag>
```

## 문서 지도

문서 전체 목록과 전달 우선순위는 [docs/README.md](./docs/README.md)를 기준으로 확인합니다.

프론트엔드/모바일 전달용 핵심 문서:

- [docs/api/API_V1_FRONTEND.md](./docs/api/API_V1_FRONTEND.md)
- [docs/api/GUEST_ACCOUNT_HANDOFF.md](./docs/api/GUEST_ACCOUNT_HANDOFF.md)
- [docs/ops/ENVIRONMENT_INTEGRATION.md](./docs/ops/ENVIRONMENT_INTEGRATION.md)
- [docs/api/AUTH_CONTRACT.md](./docs/api/AUTH_CONTRACT.md)
- [docs/api/API_ERROR_CODES.md](./docs/api/API_ERROR_CODES.md)
- [docs/api/DATA_MODEL_GLOSSARY.md](./docs/api/DATA_MODEL_GLOSSARY.md)
- [docs/api/TIMEZONE_CONTRACT.md](./docs/api/TIMEZONE_CONTRACT.md)
- [docs/api/RECURRENCE_MODEL.md](./docs/api/RECURRENCE_MODEL.md)
- [docs/api/NOTIFICATION_CONTRACT.md](./docs/api/NOTIFICATION_CONTRACT.md)
- [docs/api/SHARING_CONTRACT.md](./docs/api/SHARING_CONTRACT.md)
- [docs/ops/GUEST_ACCOUNT_PRODUCTION_APPLY.md](./docs/ops/GUEST_ACCOUNT_PRODUCTION_APPLY.md)
- [docs/ops/LOCAL_PRODUCTION_RUNBOOK.md](./docs/ops/LOCAL_PRODUCTION_RUNBOOK.md)
- [docs/project/ROADMAP.md](./docs/project/ROADMAP.md)
- [docs/db/MIGRATION_HISTORY.md](./docs/db/MIGRATION_HISTORY.md)

현재 기준 요약과 아직 닫히지 않은 작업은 [docs/README.md](./docs/README.md)의 “현재 기준 요약”과 [docs/project/ROADMAP.md](./docs/project/ROADMAP.md)를 함께 봅니다.

## 설계 원칙

- API 계약 변경은 OpenAPI, 문서, 테스트를 함께 갱신합니다.
- 모바일이 안전하게 재시도할 수 있도록 주요 생성 API에 `Idempotency-Key`를 지원합니다.
- 개인 API와 workspace API는 scope를 명확히 분리합니다.
- production secret, access token, DB dump 내용, private URL은 문서와 로그에 남기지 않습니다.
- 자동 migration 도구 도입 전까지 schema 변경은 SQL 파일과 [docs/db/MIGRATION_HISTORY.md](./docs/db/MIGRATION_HISTORY.md)로 수동 추적합니다.
