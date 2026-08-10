# Auth Contract

Last updated: 2026-08-10

이 문서는 ToDoLab 백엔드의 모바일 JWT 인증과 웹 세션 인증 책임을 정리한다.

## 인증 방식

- 모바일 API는 `/api/v1/**` 경로에서 `Authorization: Bearer <accessToken>` JWT를 사용한다.
- 웹 화면은 세션 기반 form login을 사용한다.
- 회원가입과 로그인은 인증 없이 호출할 수 있다.
- 게스트 시작은 `POST /api/v1/auth/guest`로 인증 없이 호출할 수 있다.
- 사용자 데이터 소유권 격리는 모바일 JWT와 웹 세션 모두 동일하게 적용한다.

## 계정 유형

사용자 계정 유형은 `accountType`으로 구분한다.

| accountType | 설명 |
| --- | --- |
| `GUEST` | 서버가 발급한 임시 사용자. 이메일, 비밀번호, 표시 이름이 없을 수 있다. |
| `REGISTERED` | 이메일과 비밀번호로 로그인하는 정식 사용자 |

게스트도 인증된 사용자로 취급하며 Task, 일정, D-Day owner scope는 기존 사용자와 동일하게 적용한다.

## Access Token

운영 access token TTL은 1시간이다.

게스트 access token TTL은 31일이다.

| 환경 | 설정 |
| --- | --- |
| local/test 기본값 | `PT1H` |
| production 기본값 | `TODOLAB_JWT_ACCESS_TOKEN_TTL` 미설정 시 `PT1H` |
| production override | `TODOLAB_JWT_ACCESS_TOKEN_TTL=PT1H` 형식의 ISO-8601 duration |
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
- 병합 완료 guest token은 `mergedIntoUserId`가 기록된 사용자이므로 401로 거부한다.
- `app.auth.guest.cleanup.enabled=true`이면 매일 03:30에 `guestExpiresAt`이 지난 미병합 게스트와 관련 owner 데이터를 삭제한다.
- 만료된 guest token은 owner API와 `/auth/me`에서 401로 거부한다.
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

현재 refresh token은 도입하지 않는다.

- access token 만료 시 모바일은 다시 로그인한다.
- silent refresh endpoint는 제공하지 않는다.
- refresh token 저장소, rotation, reuse detection은 현재 백엔드 책임이 아니다.
- 게스트 access token은 `POST /api/v1/auth/guest/refresh`로 같은 guest user id를 유지하며 재발급할 수 있다.

게스트 token 갱신 정책:

- 갱신 가능 기간은 기존 guest token이 유효한 동안이다.
- 만료 전 갱신 성공 시 HTTP 200과 새 `TokenResponse`를 반환한다.
- 만료된 guest token, 병합 완료 guest token, 정식 계정 token, 이미 정리된 guest는 401/`11002`로 처리한다.
- 갱신 실패 시 기존 게스트 row와 owner 데이터는 변경하지 않는다.
- refresh token이나 단말 인증 수단은 아직 도입하지 않았으므로, 만료 후 재발급은 지원하지 않는다.

새로운 guest id를 발급하는 방식은 기존 게스트 데이터 복구 정책으로 사용하지 않는다.

refresh token을 도입할 때는 별도 endpoint, 저장소, 폐기 정책, 모바일 secure storage 정책을 먼저 확정한다.

## 로그아웃과 토큰 폐기

현재 모바일 로그아웃은 클라이언트 책임이다.

- 백엔드는 access token blocklist를 운영하지 않는다.
- 모바일은 로그아웃 시 로컬에 저장한 access token을 삭제한다.
- 이미 발급된 access token은 `exp`까지 유효할 수 있다.

서버 주도 토큰 폐기가 필요해지면 blocklist 또는 token version 정책을 새 계약으로 추가한다.

## 오류 계약

| 상황 | HTTP | ErrorCode | Message | 모바일 처리 |
| --- | --- | --- | --- | --- |
| 이메일/비밀번호 불일치 | 401 | `11001` | `이메일 또는 비밀번호가 올바르지 않습니다.` | 로그인 화면에 안전한 실패 문구 노출 |
| 토큰 없음, 만료, 위변조 | 401 | `11002` | `인증이 필요합니다.` | 저장 토큰 삭제 후 로그인 유도 |
| 인증은 됐지만 권한 부족 | 403 | `11003` | `접근 권한이 없습니다.` | 재시도하지 않고 권한 오류 표시 |

401과 403 응답은 공통 `ApiResponse` envelope를 사용한다.
