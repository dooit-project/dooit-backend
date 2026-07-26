# Auth Contract

Last updated: 2026-07-25

이 문서는 ToDoLab 백엔드의 모바일 JWT 인증과 웹 세션 인증 책임을 정리한다.

## 인증 방식

- 모바일 API는 `/api/v1/**` 경로에서 `Authorization: Bearer <accessToken>` JWT를 사용한다.
- 웹 화면은 세션 기반 form login을 사용한다.
- 회원가입과 로그인은 인증 없이 호출할 수 있다.
- 사용자 데이터 소유권 격리는 모바일 JWT와 웹 세션 모두 동일하게 적용한다.

## Access Token

운영 access token TTL은 1시간이다.

| 환경 | 설정 |
| --- | --- |
| local/test 기본값 | `PT1H` |
| production 기본값 | `TODOLAB_JWT_ACCESS_TOKEN_TTL` 미설정 시 `PT1H` |
| production override | `TODOLAB_JWT_ACCESS_TOKEN_TTL=PT1H` 형식의 ISO-8601 duration |

JWT claim:

| Claim | 값 |
| --- | --- |
| `iss` | `app.auth.jwt.issuer` |
| `sub` | 사용자 id 문자열 |
| `email` | 사용자 email |
| `role` | 사용자 role |
| `iat` | 발급 시각 |
| `exp` | 만료 시각 |

`TokenResponse.expiresAt`은 access token 만료 시각을 `LocalDateTime`으로 내려준다.

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
