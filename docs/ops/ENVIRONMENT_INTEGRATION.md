# Dooit Environment Integration

Last updated: 2026-10-18

이 문서는 모바일 real mode가 백엔드에 붙을 때 사용하는 환경별 URL, CORS origin, 문서 UI 공개 기준, API 로그 운영 기준을 정리한다.

이 PC의 production 명령과 백업·복구 절차는 [`LOCAL_PRODUCTION_RUNBOOK.md`](./LOCAL_PRODUCTION_RUNBOOK.md)를 따른다.
Prometheus, Grafana, Loki, Grafana Alloy 기반 모니터링 운영 구성은 [`MONITORING_RUNBOOK.md`](./MONITORING_RUNBOOK.md)를 따른다.

## 현재 운영 입력 상태

local 개발 URL, host 내부 production URL, Docker Compose loopback bind, 실제 도메인 기반 public smoke가 확인됐다. 공개 Web은 `https://dooit.hsng.pe.kr`, 공개 API는 `https://dooitapi.hsng.pe.kr`을 사용한다. `.env`의 `DOOIT_PUBLIC_API_URL`과 `DOOIT_JWT_ISSUER`는 공개 API origin으로, `DOOIT_ALLOWED_ORIGINS`는 공개 Web origin으로 설정한다.

Docker 환경은 `prd_dooit`와 `stg_dooit`로 분리했다. STG는 localhost:18080과 별도 DB/JWT를 사용한다. [분리·기동 절차](DOCKER_ENVIRONMENTS.md)를 따른다. 운영 image·API별 배포 여부는 [현재 기준 요약](../README.md#현재-기준-요약)과 [운영 기록](LOCAL_PRODUCTION_RUNBOOK.md)을 확인한다. 프로젝트 분리 후 PRD readiness/복구와 STG 합성 API 검증을 수행했다.

`./scripts/check-production-recovery.sh`는 host 내부 readiness 복구를 확인하고, public readiness와 Web CORS preflight는 `./scripts/check-public-production.sh`로 확인한다. Android 실제 기기 smoke와 offsite backup 경로는 아직 확정 결과가 없다.

전원 정책은 아직 strict production 기준이 아니다. `DOOIT_CONFIRM_POWER_POLICY=APPLY ./scripts/apply-production-power-policy.sh`는 macOS 관리자 비밀번호 입력이 필요하므로 운영자 터미널에서 실행한다.

남은 값은 `.env`와 운영 환경에만 저장하고, 문서에는 검증 범위와 명령만 기록한다.

## 1. 환경별 API URL

| 환경 | API URL | 상태 | 비고 |
| --- | --- | --- | --- |
| local | `http://localhost:8080` | 확정 | 백엔드 로컬 실행 기준 |
| local, Android Emulator | `http://10.0.2.2:8080` | 확정 | Android Emulator에서 호스트 머신 접근 시 사용 |
| local, 실제 기기 | `http://<dev-machine-lan-ip>:8080` | 확인 필요 | 같은 네트워크에서 개발 머신 IP로 접근 |
| staging | `http://localhost:18080` | host 검증 완료 | 별도 DB/JWT, loopback 전용. localhost 운영 확정 |
| production, host 내부 | `http://127.0.0.1:8080` | 확정 | Docker Compose app port는 loopback에만 공개한다. |
| production, Android | `.env`의 `DOOIT_PUBLIC_API_URL` | host public smoke 확인됨, 실제 기기 확인 필요 | 실제 API 도메인을 사용한다. |
| production, 실제 도메인 | `https://dooitapi.hsng.pe.kr` | 확인됨 | Cloudflare Tunnel, TLS, readiness public smoke 통과 |

모바일 기본 base path는 모든 환경에서 `/api/v1`이다.

## 2. CORS Origin

현재 코드 기준 CORS 설정은 `app.cors.allowed-origins` 값을 사용한다. 운영 프로필에서는 `DOOIT_ALLOWED_ORIGINS` 환경변수로 주입한다.

| 환경 | Origin | 상태 | 설정 위치 |
| --- | --- | --- | --- |
| local, Expo Web | `http://localhost:8081` | 확인됨 | `application.yml`, `application-local.yml` |
| local, Expo Web 대체 포트 | `http://localhost:8090` | 확인됨 | `application.yml`, `application-local.yml` |
| local, iOS Simulator | CORS origin 없음 | 확인됨 | native 요청은 브라우저 CORS 대상이 아님 |
| local, Android Emulator | CORS origin 없음 | 확인됨 | native 요청은 브라우저 CORS 대상이 아님 |
| local, 실제 기기 | CORS origin 없음 | 확인 필요 | Expo Go/native 요청은 브라우저 CORS 대상이 아님 |
| staging, Expo Web | `http://localhost:8081`, `http://localhost:8090` | 설정됨 | `docker-compose.stg.yml` |
| production, native app | CORS origin 없음 | 확인 필요 | native 요청은 브라우저 CORS 대상이 아님 |
| production, Expo Web | `https://dooit.hsng.pe.kr` | 확인됨 | `DOOIT_ALLOWED_ORIGINS`에 이 origin만 허용 |

허용 request header:

- `Content-Type`
- `Accept`
- `Authorization`
- `Idempotency-Key`

노출 response header:

- `Idempotency-Replayed`

허용 method:

- `GET`
- `POST`
- `PUT`
- `PATCH`
- `DELETE`
- `OPTIONS`

## 3. 환경변수 운영 방식

운영 프로필은 아래 환경변수로 CORS origin을 주입한다. 실제 도메인을 API URL로 쓰면 `DOOIT_PUBLIC_API_URL`과 `DOOIT_JWT_ISSUER`는 같은 HTTPS origin을 사용한다.

```bash
DOOIT_PUBLIC_API_URL=https://dooitapi.hsng.pe.kr
DOOIT_JWT_ISSUER=https://dooitapi.hsng.pe.kr
DOOIT_ALLOWED_ORIGINS=https://dooit.hsng.pe.kr
DOOIT_DOCS_PUBLIC_ENABLED=false
```

원칙:

- STG와 PRD origin/인증 키는 분리한다. production origin은 코드에 하드코딩하지 않는다.
- 여러 origin은 쉼표로 구분한다.
- secret 값은 문서에 기록하지 않는다.
- origin을 추가한 뒤 `Authorization`, `Content-Type`, `Idempotency-Key` header가 포함된 preflight를 확인한다.
- iOS Simulator, Android Emulator, 실제 기기 native 앱 요청은 CORS 대상이 아니므로 API URL 접근성만 확인한다.
- production Expo Web은 브라우저 CORS 대상이므로 `https://dooit.hsng.pe.kr`만 `DOOIT_ALLOWED_ORIGINS`에 추가한다.
- production에서는 `DOOIT_DOCS_PUBLIC_ENABLED=false`를 기본값으로 유지한다.

## 4. 운영 환경 확정 입력값

production 환경을 확정할 때는 아래 값을 먼저 결정한다. 실제 secret이나 private URL은 이 문서에 기록하지 않고 배포 환경 변수로만 관리한다. STG 값은 별도 `.env.stg`와 Compose 파일을 사용한다.

| 항목 | staging | production | 검증 기준 |
| --- | --- | --- | --- |
| API base URL | `http://localhost:18080` | host 내부 `http://127.0.0.1:8080`, 외부 공개용 HTTPS URL은 `.env`의 `DOOIT_PUBLIC_API_URL` | 모바일 base URL에서 `/api/v1/auth/me` 접근 가능 |
| Web origin | localhost:8081/8090 | `https://dooit.hsng.pe.kr` | Web에서 API CORS preflight와 인증 요청 확인 |
| Native 앱 API 접근 | Emulator: 10.0.2.2:18080, 실기기 경로 미정 | `https://dooitapi.hsng.pe.kr` | Android 실제 기기에서 HTTPS API 접근 가능 |
| 문서 UI 공개 여부 | loopback에서 공개 | 비공개 기본 | `/v3/api-docs`, `/swagger-ui`, `/scalar.html` 비공개 정책 확인 |
| CORS 환경변수 | STG Compose의 로컬 Web origin | `https://dooit.hsng.pe.kr` | 공개 Web origin만 허용 |
| backend metadata | `GET /api/v1/system/metadata` | `GET /api/v1/system/metadata` | `commitSha`, `imageTag`, `version` 확인 |

확정 후 반영 순서:

1. `DOOIT_ALLOWED_ORIGINS`에 Expo Web origin만 추가한다.
2. Native 앱은 CORS 대상이 아니므로 API URL 접근성과 인증 흐름만 확인한다.
3. Expo Web을 production에 붙이면 `OPTIONS /api/v1/auth/me` preflight와 `GET /api/v1/auth/me` 401/200 흐름을 확인한다.
4. production은 문서 UI 비공개 상태를 먼저 확인한 뒤 모바일 smoke test를 진행한다.
5. 확정된 production HTTPS URL 자체는 문서에 남기지 않고, 통과한 점검 범위만 `docs/project/ROADMAP.md`와 `docs/mobile/MOBILE_API_BACKEND_STATUS.md`에 반영한다.

반영 전후에는 실제 값을 출력하지 않는 설정 점검을 실행한다.

```bash
./scripts/check-production-env.sh
DOOIT_REQUIRE_PUBLIC_API_URL=true ./scripts/check-production-env.sh
DOOIT_REQUIRE_OFFSITE_BACKUP=true ./scripts/check-production-env.sh
```

실제 도메인의 DNS, TLS, reverse proxy가 준비되면 아래 점검을 실행한다.

```bash
DOOIT_PUBLIC_API_URL=https://api.example.com ./scripts/check-public-production.sh
DOOIT_PUBLIC_API_URL=https://api.example.com DOOIT_WEB_ORIGIN=https://app.example.com ./scripts/check-public-production.sh
```

## 5. Production Health Check

운영 readiness endpoint는 `/actuator/health/readiness`다. 이 endpoint는 인증 없이 접근 가능하지만, Actuator 노출 범위는 `health`로만 제한한다.

확인 항목:

- `readinessState`: Spring Boot application readiness
- `db`: datasource 연결 상태
- `schema`: 운영에 필요한 핵심 table 존재 여부

Docker image와 Compose의 app service health check는 모두 `/actuator/health/readiness`를 호출한다. readiness가 `UP`이 아니면 app container는 healthy 상태가 아니다.

Redis health indicator는 기본 비활성이다. 게스트 생성 rate limit 저장소를 Redis로 운영하고 Redis 접속 상태를 actuator health에 포함하려면 `DOOIT_REDIS_HEALTH_ENABLED=true`를 함께 설정한다.

상세 점검 endpoint:

- `/actuator/health/liveness`: JVM/application liveness
- `/actuator/health/readiness`: API readiness, DB 연결, schema 상태
- `/actuator/health/db`: datasource 연결 상태
- `/actuator/health/schema`: 핵심 table 존재 여부

Prometheus metric endpoint는 아직 production에 적용하지 않았다. 도입 시 `/actuator/prometheus`는 public API 도메인에 직접 노출하지 않고 Docker Compose 내부 scrape 또는 별도 인증 정책으로 제한한다.

## 6. Production Metadata

운영 backend 식별 endpoint는 `GET /api/v1/system/metadata`다. 이 endpoint는 인증 없이 접근 가능하다.

응답 필드:

- `version`: 애플리케이션 버전. 기본값은 `1.0-SNAPSHOT`이다.
- `commitSha`: 배포된 backend commit SHA. release script는 현재 `git rev-parse HEAD` 값을 `DOOIT_APP_COMMIT_SHA`로 주입한다.
- `imageTag`: 배포된 Docker image tag. 기본 release는 현재 short SHA를 사용한다.

반환하지 않는 값:

- secret, access token, DB URL/password
- private 관리 URL
- CORS origin 원문

운영 확인 예:

```bash
curl --fail http://127.0.0.1:8080/api/v1/system/metadata
```

## 7. 문서 UI 공개 기준

| 환경 | Swagger UI `/swagger-ui` | Scalar `/scalar.html` | 비고 |
| --- | --- | --- | --- |
| local | 공개 | 공개 | 개발 확인용 |
| staging | loopback 공개 | loopback 공개 | STG 전용 설정, localhost 운영 |
| production | 비공개 기본 | 비공개 기본 | `DOOIT_DOCS_PUBLIC_ENABLED=false` 기본값 유지 |

현재 백엔드는 `app.docs.public-enabled`로 문서 UI와 `/v3/api-docs` 공개 여부를 제어한다. local/test 기본값은 `true`, prod 기본값은 `false`다. prod 설정의 `DOOIT_DOCS_PUBLIC_ENABLED`, `DOOIT_SPRINGDOC_API_DOCS_ENABLED`, `DOOIT_SPRINGDOC_SWAGGER_UI_ENABLED` 기본값은 테스트로 검증한다.

## 8. API Cache 정책

`/api/**` 응답은 성공, validation 오류, 인증 실패, 권한 오류 모두 아래 cache header를 반환한다.

```http
Cache-Control: no-store, no-cache, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
```

API 오류와 인증 실패는 HTML redirect 없이 JSON `ApiResponse` envelope와 HTTP status/error code를 그대로 반환한다.

## 9. API 로그 운영 기준

백엔드는 `/api/**` 요청에 공통 API 로그 필터를 적용한다. Actuator health와 OpenAPI/Swagger/Scalar 문서 endpoint는 이 필터 대상이 아니다.

기본 기록 항목:

- request id: `X-Request-Id` 요청 헤더가 있으면 재사용하고, 없으면 서버가 생성해 응답 헤더로 내려준다.
- request: method, path, query, remote IP, user agent, headers
- response: status, elapsed time, response headers
- payload: `app.api-logging.payload-enabled=true`일 때 요청/응답 body를 기록한다.
- payload 제외: `/api/auth`, `/api/v1/auth`는 전문 로깅을 켜도 요청/응답 body를 남기지 않는다.
- 목표/계획/실천 보호 경로는 로컬의 빈 마스킹 설정에도 Authorization/Cookie/Set-Cookie/Proxy-Authorization/X-API-Key를 마스킹한다. JSON 파싱 오류 메시지의 입력 인용도 로그에 남기지 않는다.
- 목표/계획/실천 보호: `/api/v1/goals`, `/api/v1/goal-plans`, `/api/v1/goal-plan-occurrences`, `/api/v1/goal-direct-logs`, `/api/v1/personal-systems`, `/api/v1/personal-system-occurrences`와 각 하위 경로는 본문 및 query 전체를 로그에서 제외한다. `payload-excluded-paths`를 교체해도 이 보호는 유지되고 context path에도 적용된다. 제외 경로에는 본문 로깅용 캐싱 wrapper를 만들지 않는다. 경로/상태/처리 시간/request ID와 기존 마스킹을 적용한 헤더는 계속 기록한다. AI provider 호출 및 데이터 보관 정책은 별도 확정 대상이다.

운영 환경변수:

```bash
DOOIT_API_LOGGING_ENABLED=true
DOOIT_API_LOGGING_PAYLOAD_ENABLED=false
DOOIT_API_LOGGING_MAX_PAYLOAD_LENGTH=4096
DOOIT_LOG_PATH=./logs
DOOIT_LOG_MAX_HISTORY=30
DOOIT_LOG_TOTAL_SIZE_CAP=1GB
DOOIT_LOG_MAX_FILE_SIZE=100MB
DOOIT_SPRINGDOC_API_DOCS_ENABLED=false
DOOIT_SPRINGDOC_SWAGGER_UI_ENABLED=false
DOOIT_PUSH_ENABLED=false
DOOIT_PUSH_PROVIDER=EXPO
DOOIT_PUSH_ENDPOINT=https://exp.host/--/api/v2/push/send
DOOIT_PUSH_ACCESS_TOKEN=
DOOIT_PUSH_SCHEDULER_FIXED_DELAY=PT1M
DOOIT_PUSH_LOOK_AHEAD_WINDOW=PT10M
DOOIT_REDIS_HEALTH_ENABLED=false
```

운영 원칙:

- local 프로필은 프론트 연동 디버깅을 위해 `payload-enabled=true`이고, header/query/payload 마스킹과 payload 제외 path를 비워 요청/응답 전문을 그대로 남긴다.
- production의 전문 로깅은 장애 재현이나 제한된 점검 시간에만 켠다.
- `Authorization`, `Cookie`, `Set-Cookie`, `email`, `name`, `password`, `token`, `secret`, `jwt`, `title`, `category`, `description`, `content`, `memo`, `note`, `query`, `keyword`, `subject`, `body`, `to`, `from` 계열 값은 로그에 `[MASKED]`로 남긴다.
- header는 `Authorization`, `Cookie`, `Set-Cookie`, `Proxy-Authorization`, `X-Api-Key`만 마스킹해 `Content-Type`, `Content-Length` 같은 운영 진단용 값은 보존한다.
- query string은 `q`, `query`, `keyword`, `search`, 인증/개인정보성 파라미터를 마스킹한다.
- payload는 `DOOIT_API_LOGGING_MAX_PAYLOAD_LENGTH` 길이를 넘으면 잘라서 남긴다.
- JSON, text, form payload만 기록하고 multipart 또는 binary payload는 전문을 남기지 않는다.
- 로그 공유 시 request id, 발생 시각, method/path, status, elapsed time을 우선 전달한다.
- production에서는 `${DOOIT_LOG_PATH}/dooit-backend.log`와 `${DOOIT_LOG_PATH}/dooit-backend-error.log`를 파일로 남긴다.
- archive 파일은 일 단위와 크기 단위로 끊고 `${DOOIT_LOG_PATH}/archive/*.log.gz`로 압축한다.
- Logback 기본 기능은 압축 시점을 롤오버 시점으로 처리한다. 정확히 3일 지난 파일만 지연 압축해야 하면 운영 환경의 `logrotate` 또는 cron 정책을 추가한다.
- 서버 push 1차 provider는 `EXPO`이며, `DOOIT_PUSH_ENABLED=false`가 기본값이다. enabled 값을 켜면 개인 owner와 ACTIVE workspace 멤버의 알림 후보를 scheduler가 자동 발송한다.
- Android/iOS push credentials는 EAS/Expo project에 보관하고, 백엔드는 Expo enhanced push security를 켠 경우에만 `DOOIT_PUSH_ACCESS_TOKEN`을 환경변수로 받는다.

## 목표 AI 공통 호출 제한 (2026-10-17)

외부 제공자 adapter는 아직 없고 `DOOIT_GOAL_AI_ENABLED=false`가 기본이다. 활성화만으로 외부 호출이 생기지 않는다. 실제 adapter 연결 전 provider·보관·삭제 정책을 별도 확정한다.

| 환경변수 | 기본값 | 허용 범위/의미 |
| --- | --- | --- |
| `DOOIT_GOAL_AI_CALL_TIMEOUT` | `PT10S` | 1ms~60초, 제출 후 제공자 결과 대기 시간 |
| `DOOIT_GOAL_AI_MAX_CONCURRENT_CALLS` | `8` | 1~64, 프로세스 전체 동시 작업 수 |
| `DOOIT_GOAL_AI_MAX_REQUESTS_PER_WINDOW` | `20` | 1~10000, 사용자 ID당 창 내 승인 호출 시도 수 |
| `DOOIT_GOAL_AI_RATE_LIMIT_WINDOW` | `PT1H` | 1초~24시간, 첫 승인 호출 기준 고정 창 |
| `DOOIT_GOAL_AI_MAX_TRACKED_USERS` | `10000` | 1~100000, 프로세스 내 quota 추적 사용자 수 |

설정이 유효하지 않으면 시작을 거부하며 제한 변경은 재시작 시 반영된다. 네 AI API가 같은 quota를 공유한다. 제공자 호출 전 검증/소유권/연결/version 검사를 유지한다. 미설정/비활성/무호출 빈 여정과 제출 전 용량 거절은 횟수를 소비하지 않는다. 승인 후 실패/timeout/출력 오류는 횟수를 소비한다. 한도 초과는 429/70010 + Retry-After(초), 동시 작업 또는 추적 사용자 용량 부족/timeout/제공자 실패는 503/70009다.

작업 큐 없이 별도 virtual worker에서 제공자를 호출한다. timeout 시 취소 interrupt를 요청하지만 interrupt를 무시하는 작업은 실제 종료까지 용량을 차지한다. 애플리케이션 종료 시에도 interrupt를 요청하며 외부 작업 강제 종료나 과금 취소를 보장하지 않는다. adapter의 HTTP 연결/응답 timeout, 응답 크기 제한과 비용 정책은 별도로 필요하다. 요청 전체/DB 조회 deadline은 이 제한 범위가 아니다.

사용자 ID·창 시작·시도 수만 메모리에 저장하고 입력/출력 원문은 보관하지 않는다. 만료는 재접근 또는 용량 정리 시 확인한다. 활성 창을 강제로 밀어내지 않으며 추적 용량이 차면 새 사용자 호출은 503으로 거절한다. 재시작은 quota를 초기화하고 다중 인스턴스·새 사용자 ID 사이에서는 합산하지 않는다. 다중 서버 운영 전 공유 quota와 실제 비용 상한을 별도 설계한다. 이 작업에는 DB migration이나 production credential 적용이 없다.
