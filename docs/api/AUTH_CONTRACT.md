# Auth Contract

Last updated: 2026-08-09

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
- `GET /api/v1/auth/me`: `accountType`, nullable `email`, nullable `displayName` 반환
- 게스트 token의 `accountType` claim은 `GUEST`
- 게스트 계정 row에는 `guestExpiresAt`, `lastActiveAt`을 저장한다.
- `POST /api/v1/auth/register`에 유효한 게스트 Bearer token을 보내면 같은 user id를 유지하고 `REGISTERED`로 승격한다.
- 승격 성공 후 기존 guest token은 DB의 현재 `accountType`과 token claim이 불일치하므로 401로 거부한다.

후속 작업:

- 기존 정식 계정 로그인 시 게스트 데이터 병합
- 병합 완료 guest token revoke
- 만료 게스트 정리
- 게스트 생성 rate limit

## Refresh Token

현재 refresh token은 도입하지 않는다.

- access token 만료 시 모바일은 다시 로그인한다.
- silent refresh endpoint는 제공하지 않는다.
- refresh token 저장소, rotation, reuse detection은 현재 백엔드 책임이 아니다.

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
