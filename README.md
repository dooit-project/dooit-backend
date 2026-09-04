<p align="center">
  <img src="./docs/assets/dooit-icon.png" width="128" alt="Dooit 아이콘" />
</p>

# Dooit Backend

> 생각난 일은 가볍게 기록하고, 오늘 해야 할 일은 선명하게.

Dooit Backend는 할 일, 일정, D-Day, 반복 계획과 Workspace 협업을 하나의 API 계약으로 제공하는 Spring Boot 백엔드입니다.
모바일 앱이 게스트 시작, 계정 연결, Today 정렬, 반복 일정, 알림과 공유 작업공간을 끊김 없이 다룰 수 있도록 인증, 데이터 모델, 운영 절차를 함께 관리합니다.

프론트엔드가 사용자 경험을 조용하고 명확하게 만드는 동안, 이 서버는 그 경험이 실제 데이터와 production 환경에서도 일관되게 유지되도록 받칩니다.

## 이 서버가 맡는 일

- **계정 흐름** - JWT 인증, refresh token rotation, logout, 비밀번호 재설정
- **게스트 경험** - 게스트 발급, 만료 전 갱신, 회원가입 승격, 기존 계정 병합
- **일정과 할 일** - Today, Inbox, Calendar, 여러 날 일정, 완료와 미룸 상태
- **일일 계획** - focus task, 예상 소요 시간, 하루 마감 summary
- **빠른 등록** - `내일 3시 회의`, `낼모레 오후 3시 반 치과`, `다다음주 화요일 14:30 발표 준비`, `매주 월요일 운동` 같은 규칙 기반 입력
- **반복 계획** - RRULE 기반 반복 series, occurrence 생성, 개별/이후/전체 수정과 삭제
- **D-Day 목표** - 목표 관리, 연결 Task 생성, Today 흐름과의 연결
- **검색과 회고** - 제목, 설명, category, D-Day 제목, 반복 여부 검색과 highlight
- **Workspace 협업** - workspace 생성, 초대, 수락/거절, 멤버 권한, 공유 Task와 D-Day
- **알림** - 로컬 알림 후보, push token, 발송 이력, Expo push client와 scheduler
- **운영 안정성** - Docker Compose production, 실제 도메인 HTTPS, readiness health, release/rollback, backup/restore

## 모바일 경험을 지탱하는 API

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

사람이 읽는 v1 계약은 [docs/api/API_V1_FRONTEND.md](./docs/api/API_V1_FRONTEND.md)를 기준으로 관리합니다.

## 빠르게 실행하기

### 준비물

- JDK 25
- Docker 및 Docker Compose

### 테스트

```bash
./gradlew test
```

### 빌드

```bash
./gradlew clean build
```

### Docker Compose

```bash
cp .env.example .env
docker volume create dooit-mysql-data
docker compose up --build
```

`.env`와 `application-local.yml`은 저장소에 커밋하지 않습니다.

## 기술 스택

| 영역 | 기술 |
| --- | --- |
| Language | Java 25 |
| Framework | Spring Boot 4.0.6, Spring MVC, Virtual Threads |
| Data | Spring Data JPA, QueryDSL, MySQL 8 |
| Security | Spring Security, JWT Resource Server |
| Batch & Mail | Spring Batch, Spring Mail |
| API Docs | OpenAPI, Swagger UI, Scalar |
| Build & Test | Gradle Wrapper, JUnit, JaCoCo |
| Runtime | Docker, Docker Compose |

## 프로젝트 구조

```text
src/main/java/pj/dooit/
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

도메인 기능은 `task`, `dday`, `workspace`, `notification`에 두고, 인증과 사용자 lifecycle은 `auth`와 `user`에서 관리합니다. 공통 응답, 오류, 멱등성, 로깅처럼 API 전반에 걸친 규칙은 `common`에 모읍니다.

## 운영까지 포함한 백엔드

production app port는 `127.0.0.1:8080`에만 바인딩하고, 외부 API는 Cloudflare Tunnel을 통해 `https://dooitapi.hsng.pe.kr`로 공개합니다. Web은 별도 정적 서버의 `127.0.0.1:4173`을 `https://dooit.hsng.pe.kr`에 연결하며, 두 공개 주소 모두 Cloudflare edge에서 HTTPS를 강제합니다. MySQL port는 host에 공개하지 않습니다.

최신 코드를 기존 production DB volume에 배포하기 전에는 [docs/db/MIGRATION_HISTORY.md](./docs/db/MIGRATION_HISTORY.md)의 미적용 migration을 백업 후 수동 적용해야 합니다. `prod` profile은 `ddl-auto: validate`를 사용하므로 schema가 맞지 않으면 app readiness가 실패합니다.

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

운영 값, secret, access token, DB dump 내용, private URL은 문서와 로그에 남기지 않습니다.

## 문서 둘러보기

문서 전체 목록과 전달 우선순위는 [docs/README.md](./docs/README.md)를 기준으로 확인합니다.

| 문서 | 무엇을 볼 수 있나요? |
| --- | --- |
| [v1 API 계약](./docs/api/API_V1_FRONTEND.md) | 모바일/프론트엔드가 호출하는 endpoint, request/response, 오류 기준 |
| [일일 계획 계약](./docs/api/DAILY_PLANNING_CONTRACT.md) | 오늘 계획, focus task, 예상 소요 시간, 하루 마감 summary |
| [게스트 계정 인수인계](./docs/api/GUEST_ACCOUNT_HANDOFF.md) | 게스트 시작, 승격, 기존 계정 병합, 만료와 rate limit |
| [환경 연동 기준](./docs/ops/ENVIRONMENT_INTEGRATION.md) | local, production 실제 도메인, CORS, 문서 UI 공개 기준 |
| [인증 계약](./docs/api/AUTH_CONTRACT.md) | JWT claim, token TTL, refresh rotation, logout, 비밀번호 재설정 |
| [오류 코드](./docs/api/API_ERROR_CODES.md) | 오류 코드, 사용자 노출 message, 모바일 처리 기준 |
| [데이터 모델 사전](./docs/api/DATA_MODEL_GLOSSARY.md) | Task, D-Day, User 주요 필드와 상태 전이 |
| [시간대 계약](./docs/api/TIMEZONE_CONTRACT.md) | 사용자 timezone과 날짜 경계 계산 기준 |
| [반복 모델](./docs/api/RECURRENCE_MODEL.md) | 반복 series, occurrence, 수정·삭제 정책 |
| [알림 계약](./docs/api/NOTIFICATION_CONTRACT.md) | 로컬 알림 후보와 서버 push 책임 경계 |
| [공유 계약](./docs/api/SHARING_CONTRACT.md) | Workspace 권한, scope, 공유 Task/D-Day 정책 |
| [게스트 운영 적용](./docs/ops/GUEST_ACCOUNT_PRODUCTION_APPLY.md) | 게스트 계약 production 점검 항목 |
| [로컬 production Runbook](./docs/ops/LOCAL_PRODUCTION_RUNBOOK.md) | Docker Compose production, 백업, 복구, release, rollback |
| [로드맵](./docs/project/ROADMAP.md) | 아직 닫히지 않은 제품/운영 작업 |
| [Migration 이력](./docs/db/MIGRATION_HISTORY.md) | Flyway 도입 전 수동 DB migration 적용 이력 |

현재 기준 요약과 아직 닫히지 않은 작업은 [docs/README.md](./docs/README.md)의 “현재 기준 요약”과 [docs/project/ROADMAP.md](./docs/project/ROADMAP.md)를 함께 봅니다.

## 우리가 중요하게 보는 것

- 모바일이 같은 API 계약을 오래 유지할 수 있는가
- 게스트 데이터가 계정 연결 뒤에도 자연스럽게 이어지는가
- Today, 반복 일정, D-Day, Workspace 데이터가 서로 섞이지 않고 정확히 보존되는가
- 실패한 요청을 안전하게 재시도할 수 있는가
- production 장애 상황에서 상태 확인, 백업, 복구, rollback 경로가 분명한가

## 관련 저장소

- [dooit-mobile](https://github.com/dooit-project/dooit-mobile) - Android, iOS, Web 클라이언트

---

<p align="center">
  생각난 일은 가볍게 기록하고, 오늘 해야 할 일은 선명하게.
</p>
