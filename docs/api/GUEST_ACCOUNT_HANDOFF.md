# Guest Account Handoff

Last updated: 2026-08-10

이 문서는 모바일 게스트 계정 bootstrap, 정식 계정 승격, 기존 계정 병합 연동에 필요한 백엔드 확정 계약을 정리한다.

## 1. Endpoint

모든 응답은 기존 `ApiResponse` envelope을 사용한다.

### 게스트 계정 생성

```http
POST /api/v1/auth/guest
```

Request body는 없다.

성공 HTTP status는 `201 Created`이고 응답은 `TokenResponse`다.

```json
{
  "status": "success",
  "data": {
    "tokenType": "Bearer",
    "accessToken": "<guest-access-token>",
    "expiresAt": "2026-09-09T00:00:00",
    "user": {
      "id": 123,
      "accountType": "GUEST",
      "email": null,
      "displayName": null,
      "role": "USER",
      "timeZone": "Asia/Seoul",
      "createdAt": "2026-08-09T00:00:00",
      "updatedAt": null
    }
  },
  "timestamp": "2026-08-09T00:00:00"
}
```

### 현재 사용자 조회

```http
GET /api/v1/auth/me
Authorization: Bearer <access-token>
```

`data.accountType`은 `GUEST` 또는 `REGISTERED`다. 게스트는 `email`, `displayName`이 `null`이다.

### 게스트 상태 신규 회원가입

```http
POST /api/v1/auth/register
Authorization: Bearer <guest-access-token>
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "password123",
  "displayName": "사용자"
}
```

유효한 게스트 token이면 같은 user id를 유지한 채 `REGISTERED`로 승격하고 정식 `TokenResponse`를 반환한다. 성공 HTTP status는 `201 Created`다.

### 기존 계정 로그인 및 게스트 병합

```http
POST /api/v1/auth/login
Authorization: Bearer <guest-access-token>
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "password123"
}
```

이메일/비밀번호 검증 성공 후 게스트 owner 데이터를 대상 정식 계정으로 병합하고 정식 `TokenResponse`를 반환한다. Authorization이 없으면 기존 로그인과 동일하게 동작한다.

## 2. DB Migration

적용 파일:

- `docs/db/migrations/20260809_add_guest_account_columns.sql`

주요 변경:

- `APP_USER.EMAIL`, `PASSWORD_HASH`, `DISPLAY_NAME` nullable 전환
- `ACCOUNT_TYPE`, `MERGED_INTO_USER_ID`, `MERGED_AT`, `LAST_ACTIVE_AT`, `GUEST_EXPIRES_AT` 추가
- 기존 사용자는 `REGISTERED`로 유지

Rollback은 운영 DB 상태에 따라 수동으로 결정한다. 게스트 row가 남아 있으면 email/password/displayName `NOT NULL` 복구가 불가능하므로, rollback 전 게스트 승격/삭제 또는 별도 백업이 필요하다.

## 3. Token Claim

| Claim | 게스트 | 정식 |
| --- | --- | --- |
| `sub` | 서버 user id | 서버 user id |
| `accountType` | `GUEST` | `REGISTERED` |
| `email` | 없음 | email |
| `displayName` | 없음 | 표시 이름 |
| `role` | `USER` | `USER` |

유효기간:

- 정식 access token: 기본 `PT1H`
- 게스트 access token: 기본 `P31D`
- refresh token: 미지원

## 4. 병합 대상과 충돌 정책

병합 대상:

- Task와 일정, Today 순서, 완료 상태, 미룸 사유
- 반복 series, materialized occurrence, recurrence exception
- D-Day 목표와 Task-D-Day 연결 관계
- push device token, push notification history

정책:

- 정식 계정 콘텐츠는 유지하고 게스트 콘텐츠를 추가한다.
- 제목이나 날짜가 같다는 이유로 자동 중복 제거하지 않는다.
- target 계정에 같은 push device token이 이미 있으면 guest token row는 비활성화하고 중복 이전하지 않는다.
- 잘못된 비밀번호, 이메일 중복 등 검증 실패 시 게스트 상태와 데이터는 유지된다.
- 같은 guest token으로 같은 target 계정 로그인을 재시도하면 중복 이전 없이 정식 token을 반환하는 멱등 성공으로 처리한다.

## 5. 오류 코드

| 상황 | HTTP | ErrorCode | 모바일 처리 |
| --- | --- | --- | --- |
| 이메일/비밀번호 불일치 | 401 | `11001` | 게스트 token 유지, 로그인 오류 표시 |
| 잘못되거나 만료된 token | 401 | `11002` | 새 게스트 생성 가능 여부 판단 |
| 병합 완료 guest token | 401 | `11002` | 로그인 결과 또는 `/auth/me` 재확인 |
| 이메일 중복 회원가입 | 409 | `40001` | 게스트 token 유지, 기존 계정 로그인 안내 |
| 게스트 생성 rate limit 초과 | 429 | `11004` | 대기 후 재시도 |
| 서버 오류 | 5xx | `99999` | 게스트 token 유지, 안전하게 재시도 |

## 6. Idempotency와 동시성

- 회원가입 승격과 로그인 병합은 transaction 안에서 처리한다.
- 대상 사용자와 게스트 사용자는 pessimistic write lock으로 조회한다.
- 병합 완료 guest는 `mergedIntoUserId`, `mergedAt`이 기록된다.
- 병합 완료 guest token은 owner API와 `/auth/me`에서 401 처리된다.
- 동일 guest token으로 같은 target 계정 로그인을 재시도하면 중복 이전 없이 성공 응답을 반환한다.

## 7. 만료와 Rate Limit

게스트 만료:

- 기본 만료 기간은 게스트 access token TTL과 같은 31일이다.
- `TODOLAB_GUEST_CLEANUP_ENABLED=true`이면 `TODOLAB_GUEST_CLEANUP_CRON` 기준으로 만료 게스트와 관련 owner 데이터를 삭제한다.
- 기본 cron은 `0 30 3 * * *`다.
- IP나 단말 식별 정보만으로 게스트 계정을 복구하지 않는다.

게스트 생성 제한:

- `TODOLAB_GUEST_RATE_LIMIT_ENABLED=true`
- `TODOLAB_GUEST_RATE_LIMIT_STORE=memory`
- `TODOLAB_GUEST_RATE_LIMIT_MAX_REQUESTS=30`
- `TODOLAB_GUEST_RATE_LIMIT_WINDOW=PT1H`
- 단일 서버는 `memory`, 다중 서버는 `redis`를 사용한다.
- Redis 저장소는 Spring Redis 접속 설정과 함께 `TODOLAB_GUEST_RATE_LIMIT_STORE=redis`로 활성화한다.

## 8. 테스트

백엔드 검증:

```bash
./gradlew test
```

관련 테스트:

- `AuthSecurityIntegrationTest`
- `AuthControllerTest`
- `AuthServiceTest`
- `CurrentUserServiceTest`
- `GuestAccountCleanupServiceIntegrationTest`
- `GuestAccountRateLimiterTest`

현재 미지원/후속 계약:

- 같은 guest user id를 유지하는 게스트 token 갱신 또는 재발급 API
- 병합 결과 개수의 API 응답 포함

병합 결과 개수는 현재 내부 audit 로그에만 기록한다. 응답 확장 전까지 모바일은 일반적인 계정 연결 완료 문구를 사용한다.

## 9. 모바일 전달 기준

모바일 연동 기준 문서:

- `docs/api/GUEST_ACCOUNT_HANDOFF.md`
- `docs/api/API_V1_FRONTEND.md`
- `docs/api/AUTH_CONTRACT.md`
- `docs/api/API_ERROR_CODES.md`

backend commit은 이 문서가 포함된 최신 `main` 커밋을 사용한다.
