# Dooit Backend Roadmap

Last updated: 2026-09-05

이 문서는 완료 이력 보관소가 아니라 **앞으로 닫아야 할 백엔드/운영 작업 목록**이다. 이미 구현된 API 계약과 운영 절차의 세부 내용은 각 계약 문서와 runbook을 원본으로 본다.

## 1. 현재 기준

- 모바일 API 기준 경로는 `/api/v1/**`다.
- 인증은 `/api/v1/**` 모바일/웹 클라이언트 API에서 Bearer JWT를 사용한다.
- API 원본 계약은 실행 중인 백엔드의 `/v3/api-docs` OpenAPI JSON이다.
- Java base package는 `pj.dooit`, Gradle project name은 `Dooit`이다.
- local production은 이 Mac의 Docker Compose와 external MySQL volume을 사용한다.
- production app port는 `127.0.0.1:8080`에만 bind하고, 외부 API는 Cloudflare Tunnel의 `https://dooitapi.hsng.pe.kr`로만 연다.
- staging은 현재 운영하지 않는다. 환경은 local 개발 환경과 이 PC의 production 환경으로만 구분한다.
- 실제 secret, access token, DB dump 내용, private production URL은 문서에 기록하지 않는다.

### 2026-09-05 정리

현재 남은 P0는 Android 실기기 production smoke와 운영 복구성 검증이다. 공개 HTTPS 도메인, Docker Compose production 구조, production DB migration 적용, 최신 backend image 배포, readiness/public HTTPS smoke는 완료됐다.

| 구분 | 현재 상태 | 다음에 닫을 일 |
| --- | --- | --- |
| 빠른 등록 | API와 규칙 기반 파싱 구현 완료. 상대 주 표현, 한국어 날짜, 축약 상대일, 시 반/콜론 시간 표현 포함 | 모바일 실제 입력 로그가 생기면 예외 표현 보강 |
| 빠른 등록 템플릿 | personal template CRUD와 template 기반 Task 생성 구현 완료 | 모바일 UI 연동 후 누락 필드가 있으면 계약 보강 |
| 일일 계획 | `daily-plans` resource, focus 1~3개, 계획 확정/마감 상태, 결과 summary 구현 완료 | 모바일 연동 후 하루 마감 UX에서 batch mutation 필요 여부 확인 |
| 예상 소요 시간 | Task nullable 필드와 template 기본값 적용 구현 완료 | 모바일 연동 후 입력 preset/표시 정책 검증 |
| Checklist | 개인/Workspace Task 하위 한 단계 checklist item API 구현 완료 | 모바일 연동 후 담당자/알림 같은 추가 필드 필요 여부 결정 |
| 카테고리 탐색 | 개인 Task category 목록/count API 구현 완료 | 모바일 연동 후 수동 category order 필요 여부 결정 |
| 공유 workspace | 설계와 1차 Task/D-Day API 구현 완료 | 모바일 연동 과정에서 권한/초대 UX 검증 |
| 서버 push | token, 후보, 이력, provider 설정, Expo client, idempotency, invalid token 처리, 개인 owner와 shared workspace scheduler 자동 발송 완료 | 운영 credential 적용 후 실수신 smoke |
| PRD DB schema | production DB migration 적용 완료. 최신 image `63a54d5` readiness/public smoke 통과 | Android 실제 기기 smoke |
| production 접근 | 실제 Web/API 도메인 HTTPS 연결, HTTPS 강제, readiness·CORS public smoke 완료 | Android 실제 기기 smoke |
| 운영 복구성 | launchd, Docker health, readiness recovery check 통과 | 전원 정책 적용과 재부팅/Docker 재시작 실검증 |
| backup | local routine backup 검증 통과 | offsite backup 위치 결정과 restore 연습 |

## 2. 제품 기능 로드맵

제품 기능은 대부분 1차 구현이 닫혔다. 로드맵에는 출시를 막거나 모바일 실사용 로그가 생겨야 판단할 수 있는 후속 작업만 남긴다. 완료된 API의 상세 계약은 `docs/api/**`를 원본으로 본다.

### P0. 프론트 출시 연동 차단 해소

목표: real API 연결과 PRD 출시 검증을 막는 backend gap을 먼저 닫는다.

- [x] production DB에 미적용 migration을 적용한 뒤 최신 backend image를 배포한다.
- [ ] Android production build에서 `https://dooitapi.hsng.pe.kr` 기준 login/me, Today 조회·생성·완료, guest 발급·병합을 확인한다.
- [ ] smoke 결과에 backend commit SHA 또는 image tag, API URL, 적용 migration 범위, 호환성, 실행 테스트를 함께 남긴다.

### P1. 모바일 실사용 로그 기반 개선

목표: 구현은 끝났지만 실제 입력/사용 데이터가 있어야 정확히 고칠 수 있는 부분만 남긴다.

- [ ] `POST /api/v1/tasks/quick-capture` 실제 입력 로그를 바탕으로 날짜/시간 표현 coverage를 확장한다.
- [ ] 일일 계획 마감 화면에서 단건 mutation 조합이 불편하거나 부분 실패가 확인되면 `POST /api/v1/daily-plans/{date}/apply` batch mutation을 추가한다.
- [ ] 모바일 checklist 사용 중 담당자, 알림, 하위 단계 요구가 확인되면 별도 계약으로 확장한다.
- [ ] category 목록/count API 연동 후 수동 category order가 필요한지 결정한다.

### P1. 서버 push 운영 전환

목표: 구현된 Expo push client와 scheduler를 production credential로 실제 수신까지 검증한다.

- [ ] production Expo push credential을 `.env`에 적용한다.
- [ ] 개인 Task와 workspace Task 알림이 중복 없이 수신되는지 실기기에서 확인한다.
- [ ] provider 실패 응답이 invalid token 비활성화와 history 기록으로 남는지 운영 로그에서 확인한다.

### P2. 제품 정책 후속 결정

- [ ] Workspace template이 필요한지 모바일 UX 기준으로 결정한다.
- [ ] 게스트가 장기 미접속 후에도 복구되어야 하는지 제품 정책을 확정한다.
- [ ] workspace calendar feed를 멤버별 공개 범위로 열지 결정한다.

## 3. 운영 마무리 로드맵

운영 로드맵은 실제 secret, private URL, DB dump 내용을 남기지 않고 통과한 명령과 검증 범위만 기록한다.

### P0. production DB migration 적용

목표: 현재 `main`의 API 계약이 production DB schema와 맞도록 미적용 migration을 백업 후 수동 적용한다.

- [x] production DB backup을 생성하고 gzip 무결성을 확인한다.
- [x] `docs/db/MIGRATION_HISTORY.md`의 미적용 migration을 날짜 순서대로 확인한다.
- [x] 적용 대상 DB에서 table/column/index/constraint 존재 여부를 먼저 확인한다.
- [x] 미적용 migration을 production app 중지 후 수동 적용한다.
- [x] app 재기동 후 `/actuator/health/readiness`와 `/api/v1/system/metadata`를 확인한다.
- [x] 적용 결과를 `docs/db/MIGRATION_HISTORY.md`에 날짜와 범위만 기록한다.

증적:

- `./scripts/backup-db.sh`
- migration 적용 전후 schema 확인 쿼리
- `curl --fail http://127.0.0.1:8080/actuator/health/readiness`
- `GET /api/v1/system/metadata`

### P0. 실제 도메인 production 접근 경로 확정

목표: Android production build와 Web이 실제 HTTPS 도메인으로 production API에 접근한다.

- [x] API 공개 도메인을 `https://dooitapi.hsng.pe.kr`로 확정한다.
- [x] Web 공개 도메인을 `https://dooit.hsng.pe.kr`로 확정한다.
- [x] Cloudflare Tunnel 또는 동등한 reverse proxy로 공개 API 도메인을 `http://127.0.0.1:8080`에 연결한다.
- [x] `.env`의 `DOOIT_PUBLIC_API_URL`, `DOOIT_JWT_ISSUER`를 공개 API origin으로 맞춘다.
- [x] `.env`의 `DOOIT_ALLOWED_ORIGINS`는 공개 Web origin만 허용한다.
- [x] `DOOIT_REQUIRE_PUBLIC_API_URL=true ./scripts/check-production-env.sh`를 통과시킨다.
- [x] `DOOIT_PUBLIC_API_URL=... ./scripts/check-public-production.sh`를 통과시킨다.
- [ ] Android 실제 기기에서 `GET /api/v1/auth/me`까지 실제 도메인 HTTPS로 접근되는지 확인한다.

증적:

- `DOOIT_REQUIRE_PUBLIC_API_URL=true ./scripts/check-production-env.sh`
- `DOOIT_PUBLIC_API_URL=https://<api-origin> ./scripts/check-public-production.sh`
- Android production build의 `/api/v1/auth/me` 결과

### P0. Android production smoke

목표: 실제 도메인 production API URL 확정 후 모바일 핵심 흐름을 실제 기기에서 검증한다.

- [ ] Android production build에 실제 도메인 HTTPS API URL을 반영한다.
- [ ] `/api/v1/auth/login` 성공과 401 실패 처리를 확인한다.
- [ ] Today 조회, 생성, 완료를 확인한다.
- [ ] 게스트 발급, 게스트 `/auth/me`, 게스트 승격 또는 기존 계정 병합 흐름을 확인한다.
- [ ] 결과를 `docs/mobile/MOBILE_API_BACKEND_STATUS.md`에 날짜와 범위만 기록한다. token, 비밀번호, private URL은 기록하지 않는다.

증적:

- `DOOIT_SMOKE_BASE_URL=https://<api-origin> ./scripts/smoke-production-api.sh`
- `docs/mobile/MOBILE_INTEGRATION_RUNBOOK.md`의 production 결과 기록 템플릿

### P0. host 상시 가용성 검증

목표: 재부팅, 재로그인, Docker Desktop 재시작 뒤 수동 코드 실행 없이 production API가 복구된다.

- [ ] `DOOIT_CONFIRM_POWER_POLICY=APPLY ./scripts/apply-production-power-policy.sh`를 관리자 권한으로 실행한다.
- [ ] `DOOIT_STRICT_POWER=true ./scripts/check-production-host.sh`를 통과시킨다.
- [x] 현재 부팅 상태에서 `./scripts/check-production-recovery.sh`를 통과시킨다.
- [x] 실제 도메인 URL 확정 후 public production smoke를 통과시킨다.
- [ ] 실제 재부팅 또는 로그아웃/로그인 후 `./scripts/check-production-recovery.sh`를 통과시킨다.
- [ ] Docker Desktop 재시작, 네트워크 변경, 절전 복귀 뒤 readiness와 Android 접근을 확인한다.

증적:

- `./scripts/check-production-host.sh`
- `./scripts/check-production-recovery.sh`
- `./scripts/report-production-status.sh`

### P1. offsite backup 확정

목표: PC 디스크 장애에도 복구 가능한 외부 매체 또는 신뢰할 수 있는 동기화 위치를 운영 절차에 포함한다.

- [x] local backup gzip, backup age, disk 여유 공간, readiness를 `./scripts/check-production-routine.sh`로 확인한다.
- [ ] 외부 디스크, NAS, cloud sync 중 하나를 `DOOIT_OFFSITE_BACKUP_DIR`로 결정한다.
- [ ] `DOOIT_OFFSITE_BACKUP_DIR=... ./scripts/sync-production-backup.sh`를 통과시킨다.
- [ ] `DOOIT_REQUIRE_OFFSITE_BACKUP=true ./scripts/check-production-env.sh`를 통과시킨다.
- [ ] `DOOIT_OFFSITE_BACKUP_DIR=... ./scripts/check-production-routine.sh`를 통과시킨다.
- [ ] offsite 복사본에서 임시 DB restore를 1회 검증한다.

증적:

- 최신 backup gzip 검증
- SHA-256 checksum 일치
- 임시 DB restore 결과

### P1. Expo Web production origin 운영

목표: 공개 Web origin만 production API CORS에 허용하고, origin 변경 시 preflight를 다시 검증한다.

- [x] Expo Web production 배포 여부를 결정한다.
- [x] 실제 Web origin `https://dooit.hsng.pe.kr`만 `DOOIT_ALLOWED_ORIGINS`에 허용한다.
- [x] public API `https://dooitapi.hsng.pe.kr`에 대해 Web CORS preflight를 확인한다.
- [x] origin을 추가하거나 바꾸면 실제 origin만 허용하는 정책을 유지한다.

증적:

- `DOOIT_ALLOWED_ORIGINS` 설정 여부: `https://dooit.hsng.pe.kr`
- `OPTIONS /api/v1/auth/me` preflight 결과: public API와 Web origin 기준 통과

### P2. 제품 정책 후속 결정

- [x] 모바일 연결 완료 화면에서 병합 결과 count를 구체적으로 노출할지 결정한다.
- [x] TickTick처럼 좌측 상단 메뉴에서 Inbox, Today, Calendar, category를 한 번에 탐색하는 UX를 채택할 때 쓸 category 목록/count API를 추가한다.
- [x] 채택 시 기존 검색 `category` 필터와 추천 category만으로 충분한지, 별도 category 목록/count/order API가 필요한지 계약한다.
- [ ] 게스트가 31일 이상 미접속한 뒤 기존 데이터를 복구해야 하는지 결정한다.
- [x] 장기 게스트 복구가 필요하면 refresh token, device-bound proof, recovery code 중 별도 인증 수단을 먼저 설계한다.
- [ ] 서버 push 운영 credential을 실제 production에 적용할지 결정한다.

## 4. 현재 완료된 기준

아래 항목은 현재 코드와 문서 기준으로 닫힌 상태다. 상세 계약은 관련 문서에서 관리한다.

- v1 Auth, Task, D-Day, 검색, Today 재정렬, 반복, timezone, 알림 후보 API 계약
- owner scope와 JWT 인증/인가 계약
- 게스트 계정 생성, refresh, 승격, 기존 계정 병합, 병합 결과 count, rate limit, 만료 정리
- production DB migration 적용 이력과 수동 migration 관리 방식
- local production Docker Compose, readiness, rollback, backup/restore, routine/recovery/status report 스크립트
- API logging masking, UTF-8 response logging, 운영 문서 UI 비공개 기준

관련 문서:

- [`../api/API_V1_FRONTEND.md`](../api/API_V1_FRONTEND.md)
- [`../api/AUTH_CONTRACT.md`](../api/AUTH_CONTRACT.md)
- [`../api/GUEST_ACCOUNT_HANDOFF.md`](../api/GUEST_ACCOUNT_HANDOFF.md)
- [`../ops/LOCAL_PRODUCTION_RUNBOOK.md`](../ops/LOCAL_PRODUCTION_RUNBOOK.md)
- [`../ops/ENVIRONMENT_INTEGRATION.md`](../ops/ENVIRONMENT_INTEGRATION.md)
- [`../mobile/MOBILE_API_BACKEND_STATUS.md`](../mobile/MOBILE_API_BACKEND_STATUS.md)
- [`../db/MIGRATION_HISTORY.md`](../db/MIGRATION_HISTORY.md)

## 5. 기본 검증 명령

```bash
./gradlew test
./scripts/check-production-env.sh
./scripts/check-production-routine.sh
./scripts/check-production-recovery.sh
./scripts/report-production-status.sh
```

실제 도메인 URL과 offsite backup 경로가 확정된 뒤에는 strict 검증을 추가한다.

```bash
DOOIT_REQUIRE_PUBLIC_API_URL=true ./scripts/check-production-env.sh
DOOIT_PUBLIC_API_URL=https://<api-origin> ./scripts/check-public-production.sh
DOOIT_REQUIRE_OFFSITE_BACKUP=true ./scripts/check-production-env.sh
DOOIT_OFFSITE_BACKUP_DIR=/absolute/offsite/path ./scripts/check-production-routine.sh
```

## 6. 문서 유지 원칙

- 완료 이력은 로드맵에 길게 누적하지 않고 관련 계약 문서 또는 migration 이력으로 이동한다.
- 미정인 실제 값은 추측하지 않고 `미정` 또는 `확정 필요`로 둔다.
- secret, access token, DB dump 내용, private production URL은 문서와 로그에 남기지 않는다.
- API 계약 변경은 OpenAPI JSON, `API_V1_FRONTEND.md`, 관련 테스트를 함께 갱신한다.
