# Dooit Mobile Integration Runbook

Last updated: 2026-10-18

이 문서는 모바일 real mode smoke test를 반복 가능한 절차로 남기기 위한 백엔드 기준 runbook이다.

## 1. 사전 조건

- 백엔드가 검증 대상 환경에서 실행 중이어야 한다. 기본 검증 대상은 local 또는 STG `http://localhost:18080`이다. PRD와 DB·인증이 다르며 [Docker 운영](../ops/DOCKER_ENVIRONMENTS.md)을 따른다.
- 로컬 구현 완료와 배포 완료를 구분한다. 배포 상태는 [백엔드 현황](MOBILE_API_BACKEND_STATUS.md), 운영 image 기록은 [운영 runbook](../ops/LOCAL_PRODUCTION_RUNBOOK.md)을 확인한다.
- 운영 쓰기 smoke는 승인된 범위에서만 실행하고 테스트 데이터의 보존/정리 상태를 기록한다.
- 모바일 앱은 mock mode가 아니라 real mode로 실행해야 한다.
- 모바일 base URL은 `docs/ops/ENVIRONMENT_INTEGRATION.md`의 환경별 API URL을 사용한다.
- 모바일 요청은 인증이 필요한 v1 API에 `Authorization: Bearer <accessToken>`을 포함해야 한다.

## 2. 백엔드 실행

```bash
./gradlew bootRun
```

로컬 기본 API URL:

```text
http://localhost:8080/api/v1
```

문서 확인:

```text
http://localhost:8080/v3/api-docs
http://localhost:8080/swagger-ui
http://localhost:8080/scalar.html
```

production host 내부 확인은 아래 명령을 사용한다.

```bash
./scripts/ensure-production-up.sh
./scripts/smoke-production-api.sh
```

### STG 클라이언트 연결

STG API origin은 `http://localhost:18080`이며 v1 경로는 `/api/v1`이다. 현재 이 Mac의 8080 포트는 PRD이므로 STG 검증에서 기본 local 주소를 그대로 사용하지 않는다.

모바일 저장소에서 Expo Web을 STG로 실행할 때는 기존 web:real 스크립트의 8080 강제 설정을 우회해 다음 값을 명시한다.

```bash
EXPO_PUBLIC_API_MODE_OVERRIDE=real EXPO_PUBLIC_API_URL_OVERRIDE=http://localhost:18080 npx expo start --web --localhost --port 8090
```

이 명령은 클라이언트 실행 예시이며 백엔드 검증 과정에서 실행한 결과가 아니다. STG용 새 게스트/로그인 session을 사용한다. PRD token은 STG JWT 키와 다르므로 재사용하지 않는다. Android Emulator의 API origin은 `http://10.0.2.2:18080`이다.

2026-10-18 백엔드 확인 범위:

- localhost:8081/8090 origin의 quick-capture POST preflight에서 Authorization/Content-Type/Idempotency-Key 허용 확인.
- 실행 중인 STG OpenAPI에서 requestedType이 optional TODO/SCHEDULE/IDEA enum임을 확인.
- 합성 게스트로 선택 유형·계획일·반복 저장·오류·재시도 실 API 검증 통과, 생성 Task 삭제.
- 모바일 소스 확인 결과 `src/types/task.ts`의 요청 필드, `src/features/today/quick-capture.tsx`의 TODO 선택 전달, mock 파싱/저장 정책과 STG 실행 설정 연결이 남아 있다. 이 확인은 클라이언트 구현·화면 검증 완료를 뜻하지 않는다.

## 3. CORS Preflight 확인

Expo Web local origin:

```bash
curl -i -X OPTIONS 'http://localhost:8080/api/v1/tasks/today' \
  -H 'Origin: http://localhost:8081' \
  -H 'Access-Control-Request-Method: GET' \
  -H 'Access-Control-Request-Headers: Authorization'
```

성공 기준:

- HTTP 200 계열 응답
- `Access-Control-Allow-Origin: http://localhost:8081`
- `Access-Control-Allow-Headers`에 `Authorization` 포함

대체 local origin `http://localhost:8090`도 같은 방식으로 확인한다.

production Expo Web origin을 사용할 때만 운영 origin을 `DOOIT_ALLOWED_ORIGINS`에 추가하고 아래처럼 확인한다.

```bash
DOOIT_PUBLIC_API_URL=https://<api-origin> DOOIT_WEB_ORIGIN=https://<web-origin> ./scripts/check-public-production.sh
```

## 4. 인증 Smoke Test

### 회원가입

```http
POST /api/v1/auth/register
Content-Type: application/json
```

```json
{
  "email": "mobile-smoke@example.com",
  "password": "password123",
  "displayName": "모바일 스모크"
}
```

성공 기준:

- HTTP 201
- `status: success`
- `data.email`이 요청 email과 일치
- `data.passwordHash`가 응답에 없음

### 로그인

```http
POST /api/v1/auth/login
Content-Type: application/json
```

```json
{
  "email": "mobile-smoke@example.com",
  "password": "password123"
}
```

성공 기준:

- HTTP 200
- `data.tokenType: Bearer`
- `data.accessToken` 존재
- `data.expiresAt` 존재

### 내 정보

```http
GET /api/v1/auth/me
Authorization: Bearer <accessToken>
```

성공 기준:

- HTTP 200
- `data.email`이 로그인한 사용자와 일치

실패 확인:

- 토큰이 없거나 만료되면 HTTP 401
- 응답은 공통 실패 envelope이며 모바일에는 안전한 `error.message`만 노출

### 게스트 발급·갱신·승격·병합

상세 계약은 [인증 계약](../api/AUTH_CONTRACT.md)을 따른다. 각 흐름은 별도 테스트 게스트로 검증한다.

| 검증 | 요청 | 확인 기준 |
| --- | --- | --- |
| 발급 | POST /api/v1/auth/guest | 201, GUEST, nullable email/displayName, access/refresh token과 만료 시각 |
| 본인 조회 | GET /api/v1/auth/me | 발급 때와 같은 user id, GUEST |
| 만료 전 access 갱신 | POST /api/v1/auth/guest/refresh + guest Bearer | 200, 같은 user id, 새 만료 시각 |
| refresh rotation | POST /api/v1/auth/refresh | 새 access/refresh token, 이전 refresh 재사용 거부 |
| 회원가입 승격 | POST /api/v1/auth/register + guest Bearer | 201, 같은 user id, REGISTERED, 작성 데이터 유지 |
| 기존 계정 병합 | POST /api/v1/auth/login + guest Bearer | 대상 계정 기존 데이터 유지, guest 데이터 이전, mergeResult 반환 |
| 병합 재시도 | 같은 guest token과 같은 대상 로그인 | 중복 이전 없이 성공, 기존 guest의 owner API 접근은 401/11010 |
| 검증 실패 | 잘못된 비밀번호 또는 중복 이메일 | 게스트 데이터 유지, 부분 이전 없음 |

자동 host smoke의 현재 검증 범위는 `scripts/smoke-production-api.sh`를 확인한다. 승격/병합·refresh 전체 검증을 자동 스크립트 한 번의 성공으로 대체하지 않는다. token과 비밀번호는 결과 기록에 포함하지 않는다.

## 5. Today / Calendar / D-Day Smoke Test

### Today 조회

```http
GET /api/v1/tasks/today?date=2026-07-14
Authorization: Bearer <accessToken>
```

성공 기준:

- HTTP 200
- `data`는 배열
- 다른 사용자의 Task가 섞이지 않음

### Calendar 범위 조회

```http
GET /api/v1/tasks?type=MONTH&date=2026-07&taskType=SCHEDULE
Authorization: Bearer <accessToken>
```

성공 기준:

- HTTP 200
- `data`는 배열
- 여러 날 일정은 원본 Task ID로 한 번만 반환

### D-Day 목표 생성

```http
POST /api/v1/dday-goals
Authorization: Bearer <accessToken>
Content-Type: application/json
```

```json
{
  "title": "출시",
  "targetDate": "2026-08-01"
}
```

성공 기준:

- HTTP 201
- `data.id` 존재
- `data.daysLeft` 존재

### D-Day 기반 Today Task 생성

```http
POST /api/v1/dday-goals/{id}/tasks
Authorization: Bearer <accessToken>
Content-Type: application/json
```

```json
{
  "title": "출시 준비",
  "date": "2026-07-14"
}
```

성공 기준:

- HTTP 201
- `data.status: TODAY`
- `data.targetDate: 2026-07-14`
- `data.ddayGoalId`가 요청한 D-Day 목표 ID와 일치

### 삭제 확인

```http
DELETE /api/v1/tasks/{id}
DELETE /api/v1/dday-goals/{id}
```

성공 기준:

- HTTP 200
- v1 문서 기준 삭제 성공 envelope의 `data`는 `null`

## 6. Production Public HTTPS Smoke Test

실제 API 도메인이 production app `127.0.0.1:8080`으로 연결되고 TLS가 준비된 뒤 실행한다.

```bash
DOOIT_PUBLIC_API_URL=https://<api-origin> ./scripts/check-public-production.sh
DOOIT_PUBLIC_API_URL=https://<api-origin> DOOIT_WEB_ORIGIN=https://<web-origin> ./scripts/check-public-production.sh
```

성공 기준:

- HTTPS `/actuator/health/readiness`가 `UP`
- HTTPS `POST /api/v1/auth/guest`가 HTTP `201`
- HTTPS `GET /api/v1/auth/me`가 같은 guest user id로 HTTP `200`
- Expo Web origin을 지정한 경우 CORS preflight가 성공

이 host smoke가 통과한 뒤 Android production build에서 같은 API origin을 사용해 로그인, Today 조회·생성·완료를 확인한다. 이 단계는 실제 기기 네트워크와 앱 빌드를 검증하므로 host smoke로 대체하지 않는다.

## 7. 결과 기록 템플릿

`docs/mobile/MOBILE_API_BACKEND_STATUS.md`의 "현재 보존할 검증 기준" 또는 운영 연결 확인 목록에 아래 형식으로 기록한다.

```text
- [x] 2026-07-14 local real mode: register/login/me, Today, Calendar, D-Day Today Task 생성 확인
  - Origin: http://localhost:8081
  - API URL: http://localhost:8080/api/v1
  - CORS: Authorization preflight 성공
  - Auth: 401 envelope 및 안전한 error.message 확인
  - 비고: <특이사항>
```

production 결과는 실제 secret, access token, 비밀번호를 기록하지 않고 아래 형식으로 남긴다.

```text
- [x] YYYY-MM-DD production real device: login/me, Today 조회·생성·완료 확인
  - API URL: https://<api-origin>/api/v1
  - Host smoke: check-public-production 통과
  - Android: auth/me 200 확인
  - CORS: Expo Web 사용 시 preflight 성공, native-only면 해당 없음
  - 비고: <특이사항>
```
