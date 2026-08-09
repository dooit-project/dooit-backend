# ToDoLab Backend

ToDoLab의 일정·할 일 도메인, 서버 렌더링 화면, 배치 작업을 담당하는 백엔드 애플리케이션입니다.

명확한 도메인 모델과 검증 가능한 구조를 우선하며, Spring MVC와 Virtual Threads를 기반으로 읽기 쉬운 명령형 코드와 동시 처리 성능을 함께 확보하는 것을 목표로 합니다.

## 주요 기능

- 일정과 할 일 생성·조회·수정·삭제
- 오늘·주간·월간 일정 조회와 정렬
- D-Day 목표 관리
- 일일 일정 메일 발송 배치
- Thymeleaf 기반 서버 렌더링 화면
- 일관된 API 응답과 예외 처리

## 기술 스택

| 영역 | 기술 |
| --- | --- |
| Language | Java 25 |
| Framework | Spring Boot 4.0.6, Spring MVC, Virtual Threads |
| Data | Spring Data JPA, QueryDSL, MySQL 8 |
| Batch & Mail | Spring Batch, Spring Mail |
| View | Thymeleaf |
| API Docs | OpenAPI, Swagger UI, Scalar |
| Build & Test | Gradle Wrapper, JUnit, JaCoCo |
| Runtime | Docker, Docker Compose |

## 프로젝트 구조

```text
src/main/java/com/todolab/
├── common/   # 공통 API 응답과 예외 처리
├── config/   # 애플리케이션 설정
├── task/     # 일정·할 일 도메인
├── dday/     # D-Day 도메인
├── batch/    # 일일 일정 배치
├── mail/     # 메일 발송
└── view/     # 서버 렌더링 화면
```

## 로컬 개발

### 요구 사항

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

### API 문서

애플리케이션 실행 후 다음 endpoint에서 API 계약을 확인합니다.

| 용도 | URL |
| --- | --- |
| OpenAPI JSON | `/v3/api-docs` |
| Swagger UI | `/swagger-ui` |
| Scalar Reference | `/scalar.html` |

프론트엔드와 모바일 연동의 기계 판독 가능한 원본 계약은 OpenAPI JSON입니다. 사람이 읽기 좋은 현재 v1 요약은 [`docs/api/API_V1_FRONTEND.md`](./docs/api/API_V1_FRONTEND.md)에서 함께 관리합니다.

문서 전체 목록과 전달 우선순위는 [`docs/README.md`](./docs/README.md)를 기준으로 확인합니다.

프론트엔드/모바일 전달용 핵심 문서:

- [`docs/api/API_V1_FRONTEND.md`](./docs/api/API_V1_FRONTEND.md)
- [`docs/api/GUEST_ACCOUNT_HANDOFF.md`](./docs/api/GUEST_ACCOUNT_HANDOFF.md)
- [`docs/ops/ENVIRONMENT_INTEGRATION.md`](./docs/ops/ENVIRONMENT_INTEGRATION.md)
- [`docs/api/AUTH_CONTRACT.md`](./docs/api/AUTH_CONTRACT.md)
- [`docs/api/API_ERROR_CODES.md`](./docs/api/API_ERROR_CODES.md)
- [`docs/api/DATA_MODEL_GLOSSARY.md`](./docs/api/DATA_MODEL_GLOSSARY.md)
- [`docs/api/TIMEZONE_CONTRACT.md`](./docs/api/TIMEZONE_CONTRACT.md)
- [`docs/api/RECURRENCE_MODEL.md`](./docs/api/RECURRENCE_MODEL.md)
- [`docs/api/NOTIFICATION_CONTRACT.md`](./docs/api/NOTIFICATION_CONTRACT.md)

### API 로그

`/api/**` 요청은 공통 필터에서 request id, method, path, query, headers, status, elapsed time을 기록합니다. 요청/응답 전문은 `app.api-logging.payload-enabled`가 `true`일 때만 남기며, 운영 기본값은 비활성입니다. 인증 API body는 전문 로깅을 켜도 기록하지 않습니다.

운영 환경변수:

```bash
TODOLAB_API_LOGGING_ENABLED=true
TODOLAB_API_LOGGING_PAYLOAD_ENABLED=false
TODOLAB_API_LOGGING_MAX_PAYLOAD_LENGTH=4096
TODOLAB_LOG_PATH=./logs
TODOLAB_LOG_MAX_HISTORY=30
TODOLAB_LOG_TOTAL_SIZE_CAP=1GB
TODOLAB_LOG_MAX_FILE_SIZE=100MB
TODOLAB_SPRINGDOC_API_DOCS_ENABLED=false
TODOLAB_SPRINGDOC_SWAGGER_UI_ENABLED=false
```

### Docker Compose

`.env.example`을 `.env`로 복사한 뒤 실제 로컬 값을 입력합니다.

```bash
cp .env.example .env
docker volume create todolab-mysql-data
docker compose up --build
```

> `.env`와 `application-local.yml`은 저장소에 커밋하지 않습니다.

## 기술적 결정

- 복잡한 리액티브 흐름 대신 Spring MVC와 Virtual Threads를 사용해 코드 가독성과 동시 처리 성능의 균형을 맞춥니다.
- JPA와 QueryDSL을 사용해 도메인 중심 모델과 명시적인 조회 조건을 구성합니다.
- 핵심 도메인, 서비스, 배치 동작은 자동화된 테스트로 검증합니다.
- API 계약 변경은 호환성과 마이그레이션 계획을 함께 관리합니다.
