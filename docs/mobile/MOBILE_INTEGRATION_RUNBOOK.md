# ToDoLab Mobile Integration Runbook

Last updated: 2026-07-14

이 문서는 모바일 real mode smoke test를 반복 가능한 절차로 남기기 위한 백엔드 기준 runbook이다.

## 1. 사전 조건

- 백엔드가 local 환경에서 실행 중이어야 한다.
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

## 6. 결과 기록 템플릿

`docs/mobile/MOBILE_API_BACKEND_STATUS.md`의 "최근 모바일 연동 테스트 결과"에 아래 형식으로 기록한다.

```text
- [x] 2026-07-14 local real mode: register/login/me, Today, Calendar, D-Day Today Task 생성 확인
  - Origin: http://localhost:8081
  - API URL: http://localhost:8080/api/v1
  - CORS: Authorization preflight 성공
  - Auth: 401 envelope 및 안전한 error.message 확인
  - 비고: <특이사항>
```
