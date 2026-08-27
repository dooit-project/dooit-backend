# Auth Contract

Last updated: 2026-08-28

이 문서는 ToDoLab 백엔드의 모바일/웹 클라이언트 JWT 인증 책임을 정리한다.

## 인증 방식

- 클라이언트 API는 `/api/v1/**` 경로에서 `Authorization: Bearer <accessToken>` JWT를 사용한다.
- 백엔드는 Thymeleaf 서버 렌더링 화면과 세션 기반 form login을 제공하지 않는다.
- 회원가입과 로그인은 인증 없이 호출할 수 있다.
- 게스트 시작은 `POST /api/v1/auth/guest`로 인증 없이 호출할 수 있다.
- 비밀번호 재설정 request/verify/confirm은 인증 없이 호출할 수 있다.
- 사용자 데이터 소유권 격리는 JWT 인증 사용자 기준으로 적용한다.

## 계정 유형

사용자 계정 유형은 `accountType`으로 구분한다.

| accountType | 설명 |
| --- | --- |
| `GUEST` | 서버가 발급한 임시 사용자. 이메일, 비밀번호, 표시 이름이 없을 수 있다. |
| `REGISTERED` | 이메일과 비밀번호로 로그인하는 정식 사용자 |

게스트도 인증된 사용자로 취급하며 Task, 일정, D-Day owner scope는 기존 사용자와 동일하게 적용한다.

## Access Token

이 PC의 Docker Compose production access token TTL 기본값은 24시간이다. 애플리케이션 prod profile 자체의 fallback은 `PT1H`이지만, local production은 Compose가 `TODOLAB_JWT_ACCESS_TOKEN_TTL:-PT24H`를 주입한다.

게스트 access token TTL은 31일이다.

| 환경 | 설정 |
| --- | --- |
| local/test 기본값 | `PT1H` |
| application prod fallback | `TODOLAB_JWT_ACCESS_TOKEN_TTL` 미설정 시 `PT1H` |
| local production Compose 기본값 | `TODOLAB_JWT_ACCESS_TOKEN_TTL` 미설정 시 `PT24H` |
| production override | `TODOLAB_JWT_ACCESS_TOKEN_TTL=PT24H` 형식의 ISO-8601 duration |
| guest local/prod 기본값 | `P31D` |
| guest production override | `TODOLAB_GUEST_JWT_ACCESS_TOKEN_TTL=P31D` 형식의 ISO-8601 duration |

JWT claim:

| Claim | 값 |
| --- | --- |
| `iss` | `app.auth.jwt.issuer` |
| `sub` | 사용자 id 문자열 |
| `accountType` | `GUEST` 또는 `REGISTERED` |
| `email` | 정식 사용자 email. 게스트 token에는 없을 수 있음 |
| `displayName` | 정식 사용자 표시 이름. 게스트 token에는 없을 수 있음 |
| `role` | 사용자 role |
| `iat` | 발급 시각 |
| `exp` | 만료 시각 |

`TokenResponse.expiresAt`은 access token 만료 시각을 `LocalDateTime`으로 내려준다.
`TokenResponse.refreshToken`과 `TokenResponse.refreshExpiresAt`은 refresh token을 발급하는 endpoint에서만 내려주며, refresh token 원본은 DB에 저장하지 않는다.

## 게스트 수명 주기

현재 제공:

- `POST /api/v1/auth/guest`: 새 게스트 사용자와 게스트 access token 발급
- `POST /api/v1/auth/guest/refresh`: 유효한 게스트 Bearer token으로 같은 guest user id의 게스트 access token 재발급
- `GET /api/v1/auth/me`: `accountType`, nullable `email`, nullable `displayName` 반환
- 게스트 token의 `accountType` claim은 `GUEST`
- 게스트 계정 row에는 `guestExpiresAt`, `lastActiveAt`을 저장한다.
- `POST /api/v1/auth/register`에 유효한 게스트 Bearer token을 보내면 같은 user id를 유지하고 `REGISTERED`로 승격한다.
- 승격 성공 후 기존 guest token은 DB의 현재 `accountType`과 token claim이 불일치하므로 401로 거부한다.
- `POST /api/v1/auth/login`에 유효한 게스트 Bearer token을 보내고 정식 계정 이메일/비밀번호 검증이 성공하면 게스트 owner 데이터를 정식 계정으로 병합한다.
- 병합 완료 guest token은 `mergedIntoUserId`가 기록된 사용자이므로 401/`11010`으로 거부한다.
- `app.auth.guest.cleanup.enabled=true`이면 매일 03:30에 `guestExpiresAt`이 지난 미병합 게스트와 관련 owner 데이터를 삭제한다.
- 만료된 guest token은 owner API와 `/auth/me`에서 401/`11010`으로 거부한다.
- 게스트 token 갱신은 만료 전 유효한 guest token으로만 가능하며, 성공 시 같은 guest user id를 유지하고 `guestExpiresAt`을 새 게스트 token TTL 기준으로 연장한다.
- `POST /api/v1/auth/guest`는 클라이언트 신호별 기본 30건/1시간 rate limit을 적용하고 초과 시 429/`11004`를 반환한다.

## 게스트 만료 정리 정책

운영 기본값:

| 항목 | 값 |
| --- | --- |
| 게스트 계정 미사용 만료 기간 | 게스트 access token TTL과 같은 31일 |
| 정리 스케줄 활성화 | `TODOLAB_GUEST_CLEANUP_ENABLED=false` 기본값 |
| 정리 cron | `TODOLAB_GUEST_CLEANUP_CRON=0 30 3 * * *` |

정리 대상:

- `accountType=GUEST`
- `guestExpiresAt < now`
- `mergedIntoUserId IS NULL`

삭제 대상:

- Task와 일정, Today 순서, 완료 상태, 미룸 사유
- 반복 series, materialized occurrence, recurrence exception
- D-Day 목표와 Task-D-Day 연결 관계
- push device token, push notification history
- 게스트 user row

정책:

- 정식 계정으로 승격되었거나 병합 완료된 데이터는 삭제하지 않는다.
- IP나 단말 식별 정보로 만료 게스트를 복구하지 않는다.
- 정리 결과 count는 내부 audit 로그로 기록하되 token, 비밀번호, 원본 식별 신호는 기록하지 않는다.

장기 복구 수단:

- 게스트가 31일 이상 미접속한 뒤에도 기존 데이터를 복구해야 하는 제품 요구가 확정되면 `recovery code` 방식만 사용한다.
- recovery code는 만료 전 게스트가 명시적으로 발급받은 1회성 비밀값으로 한정한다.
- refresh token은 세션 연장/회전 수단이므로 cleanup 이후 데이터 복구 수단으로 사용하지 않는다.
- device-bound proof는 앱 재설치, 단말 교체, OS 백업 복원에서 사용자 복구 실패 가능성이 커 1차 복구 수단에서 제외한다.
- recovery code가 없고 cleanup이 완료된 게스트 token은 401/`11010`으로 종료하며, 새 guest id를 기존 데이터 복구에 재사용하지 않는다.
- recovery code 탈취 시 cleanup 전 guest owner 데이터 전체가 이전될 수 있으므로, 실제 구현 시 code 원본 미저장, 1회 사용, 짧은 만료, 폐기 API, rate limit, 시도 로그를 함께 설계한다.

## 게스트 생성 Rate Limit

운영 기본값:

| 항목 | 값 |
| --- | --- |
| 활성화 | `TODOLAB_GUEST_RATE_LIMIT_ENABLED=true` |
| 저장소 | `TODOLAB_GUEST_RATE_LIMIT_STORE=memory` 기본값. 다중 서버는 `redis` |
| 한도 | `TODOLAB_GUEST_RATE_LIMIT_MAX_REQUESTS=30` |
| window | `TODOLAB_GUEST_RATE_LIMIT_WINDOW=PT1H` |
| 추적 key 최대 수 | `TODOLAB_GUEST_RATE_LIMIT_MAX_TRACKED_KEYS=10000` |

정책:

- 대상 endpoint는 `POST /api/v1/auth/guest`다.
- `X-Forwarded-For` 첫 IP가 있으면 우선 사용하고, 없으면 `remoteAddr`를 사용한다.
- 클라이언트 신호는 사용자 식별자나 owner scope로 사용하지 않고 abuse 방지용 해시 key로만 메모리에 저장한다.
- `memory` 저장소는 단일 서버 인스턴스 기준 인메모리 fixed window다.
- `redis` 저장소는 `StringRedisTemplate` 기반 atomic increment와 TTL을 사용해 다중 서버에서 공유 window를 적용한다.
- 초과 시 HTTP 429와 `GUEST_CREATION_RATE_LIMIT_EXCEEDED(11004)`를 반환한다.

## 게스트 병합 정책

기존 정식 계정 로그인 시 `Authorization: Bearer <guest-access-token>`이 있으면 자격 증명 검증 후 하나의 트랜잭션에서 병합한다.

병합 대상:

- Task와 일정, Today 순서, 완료 상태, 미룸 사유
- 반복 series, materialized occurrence, recurrence exception
- D-Day 목표와 Task-D-Day 연결 관계
- push device token, push notification history

병합 정책:

- 정식 계정 콘텐츠는 유지하고 게스트 콘텐츠를 추가한다.
- 제목이나 날짜가 같다는 이유로 자동 중복 제거하지 않는다.
- target 계정에 같은 push device token이 이미 있으면 guest token row는 비활성화하고 중복 이전하지 않는다.
- 이메일/비밀번호 검증 실패 시 게스트 데이터는 변경하지 않는다.
- 같은 guest token으로 같은 target 계정 로그인을 재시도하면 중복 이전 없이 정식 token을 다시 반환하는 멱등 성공으로 처리한다.
- 병합 완료 후 기존 guest token은 owner API와 `/auth/me`에서 401 처리된다.
- 병합 결과 count는 응답의 `mergeResult`와 내부 audit 로그로 기록하되 token, 비밀번호, 원본 식별 신호는 기록하지 않는다.
- `POST /api/v1/auth/login`에 유효한 게스트 token을 함께 보내 병합이 수행되면 `mergeResult.tasks`, `mergeResult.schedules`, `mergeResult.ddayGoals`, `mergeResult.recurrenceSeries`를 반환한다.
- 일반 로그인처럼 병합이 없으면 `mergeResult`는 `null`이다.

## Refresh Token

현재 제공:

- `POST /api/v1/auth/login`: 등록 계정 access token과 refresh token을 함께 발급한다.
- `POST /api/v1/auth/register`: 등록 계정 생성 또는 게스트 승격 성공 시 access token과 refresh token을 함께 발급한다.
- `POST /api/v1/auth/guest`: 게스트 access token과 refresh token을 함께 발급한다.
- `POST /api/v1/auth/refresh`: refresh token을 회전하고 새 access token과 refresh token을 반환한다.
- `POST /api/v1/auth/logout`: 현재 사용자 refresh session을 폐기한다.
- 게스트 access token은 `POST /api/v1/auth/guest/refresh`로 같은 guest user id를 유지하며 재발급할 수 있다.

저장 모델:

- `REFRESH_TOKEN_SESSION`에 `USER_ID`, `TOKEN_HASH`, `FAMILY_ID`, `EXPIRES_AT`, `ABSOLUTE_EXPIRES_AT`, `ROTATED_AT`, `REVOKED_AT`, `CREATED_AT`, `UPDATED_AT`을 저장한다.
- refresh token 원본은 저장하지 않고 SHA-256 hash만 저장한다.
- refresh token은 URL-safe opaque string이다.
- `app.auth.refresh-token.idle-ttl` 기본값은 30일이고, `app.auth.refresh-token.absolute-ttl` 기본값은 90일이다.

Rotation/reuse detection 정책:

- refresh 성공 시 기존 session에 `ROTATED_AT`을 기록하고 같은 `FAMILY_ID`의 새 session을 발급한다.
- 이미 회전되었거나 폐기된 refresh token이 다시 사용되면 reuse로 판단해 같은 `FAMILY_ID`의 모든 session을 폐기하고 401/`11009`를 반환한다.
- `EXPIRES_AT` 또는 `ABSOLUTE_EXPIRES_AT`이 지난 refresh token은 401/`11008`이다.
- 존재하지 않거나 형식이 잘못된 refresh token, 병합 완료 guest, cleanup된 guest, token 사용자 상태와 맞지 않는 요청은 401/`11007`이다.
- 비밀번호 재설정 confirm 성공, 게스트 승격, 게스트 병합, 게스트 cleanup은 영향을 받는 사용자의 refresh session을 폐기한다.

Logout 정책:

- 요청 body에 `refreshToken`이 있으면 해당 token session만 폐기한다.
- 요청 body가 없거나 `refreshToken`이 비어 있으면 access token 사용자 기준 활성 refresh session을 모두 폐기한다.
- 로그아웃은 access token blocklist를 만들지 않는다. 이미 발급된 access token은 기존 정책처럼 `exp`까지 유효할 수 있다.

게스트 token 갱신 정책:

- 갱신 가능 기간은 기존 guest token이 유효한 동안이다.
- 만료 전 갱신 성공 시 HTTP 200과 새 `TokenResponse`를 반환한다.
- 만료된 guest token, 병합 완료 guest token, 이미 정리된 guest는 401/`11010`으로 처리한다.
- 정식 계정 token처럼 guest token이 아닌 인증 실패는 401/`11002`로 처리한다.
- 갱신 실패 시 기존 게스트 row와 owner 데이터는 변경하지 않는다.

새로운 guest id를 발급하는 방식은 기존 게스트 데이터 복구 정책으로 사용하지 않는다.

## 비밀번호 재설정

현재 제공:

- `POST /api/v1/auth/password-reset/request`: 가입 여부 노출 없이 요청을 접수한다.
- `POST /api/v1/auth/password-reset/verify`: 재설정 token이 사용 가능한지 확인한다.
- `POST /api/v1/auth/password-reset/confirm`: 유효한 token으로 새 비밀번호를 저장한다.

저장 모델:

- `PASSWORD_RESET_TOKEN`에 `USER_ID`, normalized `EMAIL`, `TOKEN_HASH`, `EXPIRES_AT`, `USED_AT`, `CREATED_AT`을 저장한다.
- 원본 token은 저장하지 않고 SHA-256 hash만 저장한다.
- token은 URL-safe opaque string이다.
- reset link 기본 형식은 `todolab://password-reset?token={token}`이며, production에서는 `TODOLAB_PASSWORD_RESET_LINK_TEMPLATE`로 변경할 수 있다.

정책:

- request는 등록 계정이 없어도 HTTP 200과 `requested=true`를 반환해 계정 존재 여부를 노출하지 않는다.
- 등록 계정이 있으면 TTL 30분 token을 생성하고 reset link를 email로 발송한다.
- 게스트 계정은 email/password가 없으므로 재설정 대상이 아니다.
- request rate limit은 normalized email 기준 기본 5건/1시간이며 초과 시 429/`11006`이다.
- verify/confirm에서 token이 없거나 만료되었거나 이미 사용되었으면 400/`11005`다.
- confirm 성공 시 token은 `USED_AT`을 기록해 재사용할 수 없다.
- confirm은 기존 access token blocklist를 만들지 않는다. 이미 발급된 access token은 기존 Auth 정책처럼 `exp`까지 유효할 수 있다.

## 로그아웃과 토큰 폐기

현재 제공:

- `POST /api/v1/auth/logout`: 인증된 사용자의 refresh session을 폐기한다.
- 모바일은 로그아웃 성공 또는 실패 후 로컬에 저장한 access token과 refresh token을 삭제한다.
- 백엔드는 access token blocklist를 운영하지 않는다.
- 이미 발급된 access token은 `exp`까지 유효할 수 있다.

서버 주도 토큰 폐기가 필요해지면 blocklist 또는 token version 정책을 새 계약으로 추가한다.

## 오류 계약

| 상황 | HTTP | ErrorCode | Message | 모바일 처리 |
| --- | --- | --- | --- | --- |
| 이메일/비밀번호 불일치 | 401 | `11001` | `이메일 또는 비밀번호가 올바르지 않습니다.` | 로그인 화면에 안전한 실패 문구 노출 |
| 토큰 없음, 만료, 위변조 | 401 | `11002` | `인증이 필요합니다.` | 저장 토큰 삭제 후 로그인 유도 |
| 인증은 됐지만 권한 부족 | 403 | `11003` | `접근 권한이 없습니다.` | 재시도하지 않고 권한 오류 표시 |
| 비밀번호 재설정 token 없음, 만료, 사용됨 | 400 | `11005` | `비밀번호 재설정 링크가 만료되었거나 올바르지 않습니다.` | 재설정 링크 재요청 안내 |
| 비밀번호 재설정 rate limit 초과 | 429 | `11006` | `비밀번호 재설정 요청이 너무 많습니다.` | 대기 후 재시도 안내 |
| refresh token 없음, 위변조, 폐기됨 | 401 | `11007` | `refresh token이 올바르지 않습니다.` | 저장 토큰 삭제 후 로그인 유도 |
| refresh token 만료 | 401 | `11008` | `refresh token이 만료되었습니다.` | 저장 토큰 삭제 후 로그인 유도 |
| refresh token 재사용 감지 | 401 | `11009` | `refresh token 재사용이 감지되었습니다.` | 저장 토큰 삭제 후 로그인 유도 |

401과 403 응답은 공통 `ApiResponse` envelope를 사용한다.
