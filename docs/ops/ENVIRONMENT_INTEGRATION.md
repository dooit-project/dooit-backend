# ToDoLab Environment Integration

Last updated: 2026-08-11

이 문서는 모바일 real mode가 백엔드에 붙을 때 사용하는 환경별 URL, CORS origin, 문서 UI 공개 기준, API 로그 운영 기준을 정리한다.

이 PC를 단일 production으로 운영하는 실제 명령과 백업·복구 절차는 [`LOCAL_PRODUCTION_RUNBOOK.md`](./LOCAL_PRODUCTION_RUNBOOK.md)를 따른다.

## 1. 환경별 API URL

| 환경 | API URL | 상태 | 비고 |
| --- | --- | --- | --- |
| local | `http://localhost:8080` | 확정 | 백엔드 로컬 실행 기준 |
| local, Android Emulator | `http://10.0.2.2:8080` | 확정 | Android Emulator에서 호스트 머신 접근 시 사용 |
| local, 실제 기기 | `http://<dev-machine-lan-ip>:8080` | 확인 필요 | 같은 네트워크에서 개발 머신 IP로 접근 |
| staging | 사용하지 않음 | 해당 없음 | 이 PC 단일 production 운영에서는 별도 staging을 두지 않는다. |
| production, host 내부 | `http://127.0.0.1:8080` | 확정 | Docker Compose app port는 loopback에만 공개한다. |
| production, Android | Tailscale HTTPS URL 미정 | 확인 필요 | `https://<device>.<tailnet>.ts.net` 확정 후 모바일 API URL로 사용한다. |

모바일 기본 base path는 모든 환경에서 `/api/v1`이다.

## 2. CORS Origin

현재 코드 기준 CORS 설정은 `app.cors.allowed-origins` 값을 사용한다. 운영 프로필에서는 `TODOLAB_ALLOWED_ORIGINS` 환경변수로 주입한다.

| 환경 | Origin | 상태 | 설정 위치 |
| --- | --- | --- | --- |
| local, Expo Web | `http://localhost:8081` | 확인됨 | `application.yml`, `application-local.yml` |
| local, Expo Web 대체 포트 | `http://localhost:8090` | 확인됨 | `application.yml`, `application-local.yml` |
| local, iOS Simulator | CORS origin 없음 | 확인됨 | native 요청은 브라우저 CORS 대상이 아님 |
| local, Android Emulator | CORS origin 없음 | 확인됨 | native 요청은 브라우저 CORS 대상이 아님 |
| local, 실제 기기 | CORS origin 없음 | 확인 필요 | Expo Go/native 요청은 브라우저 CORS 대상이 아님 |
| staging | 사용하지 않음 | 해당 없음 | 이 PC 단일 production 운영에서는 별도 staging을 두지 않는다. |
| production, native app | CORS origin 없음 | 확인 필요 | native 요청은 브라우저 CORS 대상이 아님 |
| production, Expo Web | 미정 | 확인 필요 | Expo Web을 production에 연결할 때만 `TODOLAB_ALLOWED_ORIGINS`에 추가 |

허용 request header:

- `Content-Type`
- `Accept`
- `Authorization`

허용 method:

- `GET`
- `POST`
- `PUT`
- `PATCH`
- `DELETE`
- `OPTIONS`

## 3. 환경변수 운영 방식

운영 프로필은 아래 환경변수로 CORS origin을 주입한다.

```bash
TODOLAB_ALLOWED_ORIGINS=https://app.example.com,https://admin.example.com
TODOLAB_DOCS_PUBLIC_ENABLED=false
```

원칙:

- staging은 현재 운영하지 않는다. production origin은 코드에 하드코딩하지 않는다.
- 여러 origin은 쉼표로 구분한다.
- secret 값은 문서에 기록하지 않는다.
- origin을 추가한 뒤 `Authorization` header가 포함된 preflight를 확인한다.
- iOS Simulator, Android Emulator, 실제 기기 native 앱 요청은 CORS 대상이 아니므로 API URL 접근성만 확인한다.
- Expo Web은 브라우저 CORS 대상이므로 실제 접속 origin을 `TODOLAB_ALLOWED_ORIGINS`에 추가한다.
- production에서는 `TODOLAB_DOCS_PUBLIC_ENABLED=false`를 기본값으로 유지한다.

## 4. 운영 환경 확정 입력값

production 환경을 확정할 때는 아래 값을 먼저 결정한다. 실제 secret이나 private URL은 이 문서에 기록하지 않고 배포 환경 변수로만 관리한다. staging은 현재 운영하지 않는다.

| 항목 | staging | production | 검증 기준 |
| --- | --- | --- | --- |
| API base URL | 사용하지 않음 | host 내부 `http://127.0.0.1:8080`, Android용 Tailscale HTTPS URL 미정 | 모바일 base URL에서 `/api/v1/auth/me` 접근 가능 |
| Expo Web origin | 사용하지 않음 | 미정 | `Authorization` header 포함 preflight 성공 |
| Native 앱 API 접근 | 사용하지 않음 | Tailscale HTTPS URL 확정 필요 | Android 실제 기기에서 HTTPS API 접근 가능 |
| 문서 UI 공개 여부 | 사용하지 않음 | 비공개 기본 | `/v3/api-docs`, `/swagger-ui`, `/scalar.html` 비공개 정책 확인 |
| CORS 환경변수 | 사용하지 않음 | `TODOLAB_ALLOWED_ORIGINS` | Expo Web 사용 시 쉼표 구분 origin 목록 적용 |

확정 후 반영 순서:

1. `TODOLAB_ALLOWED_ORIGINS`에 Expo Web origin만 추가한다.
2. Native 앱은 CORS 대상이 아니므로 API URL 접근성과 인증 흐름만 확인한다.
3. Expo Web을 production에 붙이면 `OPTIONS /api/v1/auth/me` preflight와 `GET /api/v1/auth/me` 401/200 흐름을 확인한다.
4. production은 문서 UI 비공개 상태를 먼저 확인한 뒤 모바일 smoke test를 진행한다.
5. 확정된 Tailscale HTTPS URL만 `docs/project/ROADMAP.md`와 `docs/mobile/MOBILE_API_BACKEND_STATUS.md`에 반영한다.

반영 전후에는 실제 값을 출력하지 않는 설정 점검을 실행한다.

```bash
./scripts/check-production-env.sh
TODOLAB_REQUIRE_TAILSCALE_URL=true ./scripts/check-production-env.sh
TODOLAB_REQUIRE_OFFSITE_BACKUP=true ./scripts/check-production-env.sh
```

Tailscale URL 확정 직후 host에서는 아래 점검을 실행한다. 이 점검은 Android 실기기 smoke를 대체하지 않고, Serve와 HTTPS API 계약이 host에서 재현되는지만 확인한다.

```bash
TODOLAB_TAILSCALE_API_URL=https://<device>.<tailnet>.ts.net ./scripts/check-tailscale-production.sh
TODOLAB_TAILSCALE_API_URL=https://<device>.<tailnet>.ts.net TODOLAB_EXPO_WEB_ORIGIN=https://<expo-web-origin> ./scripts/check-tailscale-production.sh
```

`TODOLAB_TAILSCALE_API_URL`을 아직 확정값으로 저장하지 않았더라도 `tailscale serve status`에 HTTPS URL이 있으면 `./scripts/check-tailscale-production.sh`는 해당 URL을 자동 감지해 host smoke를 실행한다. 자동 감지 결과는 점검 출력으로만 사용하고 문서에는 실제 URL을 기록하지 않는다.

## 5. Production Health Check

운영 readiness endpoint는 `/actuator/health/readiness`다. 이 endpoint는 인증 없이 접근 가능하지만, Actuator 노출 범위는 `health`로만 제한한다.

확인 항목:

- `readinessState`: Spring Boot application readiness
- `db`: datasource 연결 상태
- `schema`: 운영에 필요한 핵심 table 존재 여부

Docker image와 Compose의 app service health check는 모두 `/actuator/health/readiness`를 호출한다. readiness가 `UP`이 아니면 app container는 healthy 상태가 아니다.

Redis health indicator는 기본 비활성이다. 게스트 생성 rate limit 저장소를 Redis로 운영하고 Redis 접속 상태를 actuator health에 포함하려면 `TODOLAB_REDIS_HEALTH_ENABLED=true`를 함께 설정한다.

상세 점검 endpoint:

- `/actuator/health/liveness`: JVM/application liveness
- `/actuator/health/readiness`: API readiness, DB 연결, schema 상태
- `/actuator/health/db`: datasource 연결 상태
- `/actuator/health/schema`: 핵심 table 존재 여부

## 6. 문서 UI 공개 기준

| 환경 | Swagger UI `/swagger-ui` | Scalar `/scalar.html` | 비고 |
| --- | --- | --- | --- |
| local | 공개 | 공개 | 개발 확인용 |
| staging | 사용하지 않음 | 사용하지 않음 | 이 PC 단일 production 운영에서는 별도 staging을 두지 않는다. |
| production | 비공개 기본 | 비공개 기본 | `TODOLAB_DOCS_PUBLIC_ENABLED=false` 기본값 유지 |

현재 백엔드는 `app.docs.public-enabled`로 문서 UI와 `/v3/api-docs` 공개 여부를 제어한다. local/test 기본값은 `true`, prod 기본값은 `false`다. prod 설정의 `TODOLAB_DOCS_PUBLIC_ENABLED`, `TODOLAB_SPRINGDOC_API_DOCS_ENABLED`, `TODOLAB_SPRINGDOC_SWAGGER_UI_ENABLED` 기본값은 테스트로 검증한다.

## 7. API 로그 운영 기준

백엔드는 `/api/**` 요청에 공통 API 로그 필터를 적용한다. 서버 렌더링 화면과 정적 리소스는 이 필터 대상이 아니다.

기본 기록 항목:

- request id: `X-Request-Id` 요청 헤더가 있으면 재사용하고, 없으면 서버가 생성해 응답 헤더로 내려준다.
- request: method, path, query, remote IP, user agent, headers
- response: status, elapsed time, response headers
- payload: `app.api-logging.payload-enabled=true`일 때 요청/응답 body를 기록한다.
- payload 제외: `/api/auth`, `/api/v1/auth`는 전문 로깅을 켜도 요청/응답 body를 남기지 않는다.

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
TODOLAB_PUSH_ENABLED=false
TODOLAB_PUSH_PROVIDER=EXPO
TODOLAB_PUSH_ENDPOINT=https://exp.host/--/api/v2/push/send
TODOLAB_REDIS_HEALTH_ENABLED=false
```

운영 원칙:

- local 프로필은 프론트 연동 디버깅을 위해 `payload-enabled=true`이고, header/query/payload 마스킹과 payload 제외 path를 비워 요청/응답 전문을 그대로 남긴다.
- production의 전문 로깅은 장애 재현이나 제한된 점검 시간에만 켠다.
- `Authorization`, `Cookie`, `Set-Cookie`, `email`, `name`, `password`, `token`, `secret`, `jwt`, `title`, `category`, `description`, `content`, `memo`, `note`, `query`, `keyword`, `subject`, `body`, `to`, `from` 계열 값은 로그에 `[MASKED]`로 남긴다.
- header는 `Authorization`, `Cookie`, `Set-Cookie`, `Proxy-Authorization`, `X-Api-Key`만 마스킹해 `Content-Type`, `Content-Length` 같은 운영 진단용 값은 보존한다.
- query string은 `q`, `query`, `keyword`, `search`, 인증/개인정보성 파라미터를 마스킹한다.
- payload는 `TODOLAB_API_LOGGING_MAX_PAYLOAD_LENGTH` 길이를 넘으면 잘라서 남긴다.
- JSON, text, form payload만 기록하고 multipart 또는 binary payload는 전문을 남기지 않는다.
- 로그 공유 시 request id, 발생 시각, method/path, status, elapsed time을 우선 전달한다.
- production에서는 `${TODOLAB_LOG_PATH}/todolab-backend.log`와 `${TODOLAB_LOG_PATH}/todolab-backend-error.log`를 파일로 남긴다.
- archive 파일은 일 단위와 크기 단위로 끊고 `${TODOLAB_LOG_PATH}/archive/*.log.gz`로 압축한다.
- Logback 기본 기능은 압축 시점을 롤오버 시점으로 처리한다. 정확히 3일 지난 파일만 지연 압축해야 하면 운영 환경의 `logrotate` 또는 cron 정책을 추가한다.
- 서버 push 1차 provider는 `EXPO`이며, `TODOLAB_PUSH_ENABLED=false`가 기본값이다. 발송 스케줄러가 구현되기 전에는 enabled 값을 켜도 실제 발송하지 않는다.
