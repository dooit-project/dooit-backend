# Guest Account Production Apply Runbook

Last updated: 2026-08-11

이 문서는 모바일 게스트 계정 연동을 production에 적용하기 전후의 필수 확인 절차를 정리한다. 실제 secret, access token, DB 비밀번호, 백업 파일 내용은 문서와 공유 로그에 남기지 않는다.

## 1. 적용 범위

production에는 최소 다음 backend commit 이후의 깨끗한 작업 트리에서 만든 image를 배포한다.

- `f28c938` 게스트 계정 발급 계약 추가
- `cc0b8e7` 게스트 회원가입 승격 처리
- `b8247f7` 게스트 로그인 데이터 병합 처리
- `da97c15` 게스트 병합 재시도 검증 추가

Redis 기반 게스트 생성 rate-limit 변경이 미커밋 상태라면 production image에 포함하지 않는다. 포함하려면 `./gradlew test` 통과 후 별도 커밋으로 확정한다.

## 2. DB Migration

적용 파일:

- `docs/db/migrations/20260809_add_guest_account_columns.sql`

변경 내용:

- `APP_USER.EMAIL`, `PASSWORD_HASH`, `DISPLAY_NAME` nullable 전환
- `APP_USER.ACCOUNT_TYPE`
- `APP_USER.MERGED_INTO_USER_ID`
- `APP_USER.MERGED_AT`
- `APP_USER.LAST_ACTIVE_AT`
- `APP_USER.GUEST_EXPIRES_AT`
- `IDX_APP_USER_ACCOUNT_TYPE_EXPIRES`

적용 전 schema 확인:

```sql
SHOW COLUMNS FROM APP_USER LIKE 'ACCOUNT_TYPE';
SHOW COLUMNS FROM APP_USER LIKE 'MERGED_INTO_USER_ID';
SHOW COLUMNS FROM APP_USER LIKE 'MERGED_AT';
SHOW COLUMNS FROM APP_USER LIKE 'LAST_ACTIVE_AT';
SHOW COLUMNS FROM APP_USER LIKE 'GUEST_EXPIRES_AT';
SHOW INDEX FROM APP_USER WHERE Key_name = 'IDX_APP_USER_ACCOUNT_TYPE_EXPIRES';
```

컬럼 또는 index가 이미 있으면 migration을 그대로 재실행하지 않는다. 현재 schema와 migration 차이를 확인한 뒤 누락된 변경만 수동 적용한다.

## 3. 적용 순서

1. production DB 백업을 생성한다.
2. 백업 파일이 생성됐는지 확인한다.
3. 별도 임시 DB 또는 검증용 volume에서 복구 가능 여부를 확인한다.
4. production app container를 중지한다.
5. `20260809_add_guest_account_columns.sql`을 적용한다.
6. 변경된 column, nullable, index를 확인한다.
7. 깨끗한 git 작업 트리에서 최신 backend image를 빌드하고 배포한다.
8. `/actuator/health/readiness`가 `UP`인지 확인한다.
9. 게스트 API smoke test를 실행한다.

기존 local production 명령과 백업/복구 절차는 [`LOCAL_PRODUCTION_RUNBOOK.md`](./LOCAL_PRODUCTION_RUNBOOK.md)를 따른다.

## 4. Smoke Test

로컬 production API smoke는 아래 스크립트로 실행한다. 스크립트는 access token과 비밀번호를 출력하지 않는다.

```bash
./scripts/smoke-production-api.sh
TODOLAB_SMOKE_BASE_URL=https://<device>.<tailnet>.ts.net ./scripts/smoke-production-api.sh
```

### 게스트 발급

```http
POST /api/v1/auth/guest
```

확인 항목:

- HTTP `201 Created`
- `data.tokenType: Bearer`
- `data.accessToken` 존재
- `data.expiresAt` 존재
- `data.user.accountType: GUEST`
- `data.user.email: null`
- `data.user.displayName: null`

### 게스트 사용자 조회

```http
GET /api/v1/auth/me
Authorization: Bearer <guest-access-token>
```

확인 항목:

- 게스트 발급 응답과 같은 `data.id`
- `data.accountType: GUEST`
- `data.email: null`
- `data.displayName: null`

### 회원가입 승격

```http
POST /api/v1/auth/register
Authorization: Bearer <guest-access-token>
Content-Type: application/json
```

확인 항목:

- HTTP `201 Created`
- 기존 guest user id 유지
- `data.user.accountType: REGISTERED`
- 정식 access token 반환
- 게스트 상태에서 작성한 Task, 일정, D-Day 유지
- 이메일 중복 등 실패 시 게스트 상태와 데이터 유지

### 기존 계정 로그인 병합

```http
POST /api/v1/auth/login
Authorization: Bearer <guest-access-token>
Content-Type: application/json
```

확인 항목:

- 게스트 Task, 일정, 완료 기록, 반복, D-Day 관계 병합
- 정식 계정의 기존 데이터 유지
- 실패 시 전체 rollback
- 실패 후 기존 게스트 데이터 접근 가능
- 같은 guest token으로 재시도해도 중복 이전 없음
- 병합 성공 후 정식 access token 반환

## 5. 추가 API 계약 현황

게스트 token은 `POST /api/v1/auth/guest/refresh`로 만료 전 갱신할 수 있다. refresh는 같은 guest user id를 유지하고, 성공하면 새 access token과 갱신된 만료 시각을 반환한다.

만료 후 복구용 재발급은 지원하지 않는다. token이 만료됐거나 이미 정리된 게스트는 인증 실패로 처리하고, 새 guest id 발급을 기존 데이터 복구 정책으로 사용하지 않는다.

병합 결과 개수는 `POST /api/v1/auth/login` 병합 성공 응답의 `mergeResult`에 포함한다. 일반 로그인처럼 병합이 없으면 `mergeResult`는 `null`이다.

## 6. 완료 보고 항목

완료 후 다음 항목만 공유한다. secret, access token, 비밀번호, 원본 DB dump 내용은 공유하지 않는다.

- 적용한 backend commit SHA
- production image tag
- migration 적용 시간
- DB 백업 위치와 복구 확인 여부
- readiness 결과
- 전체 테스트 결과
- 게스트 발급, 승격, 병합 smoke 결과
- 게스트 token 갱신 API 지원 여부
- 병합 결과 개수 응답 지원 여부

아래 명령은 공유 가능한 완료 보고 초안을 출력한다. 출력에는 secret, access token, 비밀번호, DB dump 내용이 포함되지 않는다.

```bash
./scripts/report-production-status.sh
```
