# ToDoLab Backend Roadmap

Last updated: 2026-08-12

이 문서는 완료 이력 보관소가 아니라 **앞으로 닫아야 할 백엔드/운영 작업 목록**이다. 이미 구현된 API 계약과 운영 절차의 세부 내용은 각 계약 문서와 runbook을 원본으로 본다.

## 1. 현재 기준

- 모바일 API 기준 경로는 `/api/v1/**`다.
- 인증은 모바일 API는 Bearer JWT, 서버 렌더링 화면은 session login을 사용한다.
- API 원본 계약은 실행 중인 백엔드의 `/v3/api-docs` OpenAPI JSON이다.
- local production은 이 Mac의 Docker Compose와 external MySQL volume을 사용한다.
- production app port는 `127.0.0.1:8080`에만 bind하고, Android 접근은 Tailscale HTTPS 경로로만 연다.
- staging은 현재 운영하지 않는다. 환경은 local 개발 환경과 이 PC의 production 환경으로만 구분한다.
- 실제 secret, access token, DB dump 내용, private production URL은 문서에 기록하지 않는다.

## 2. 앞으로 할 일

### P0. production 접근 경로 확정

목표: 공용 인터넷 포트포워딩 없이 Android production build가 Tailscale HTTPS로 production API에 접근한다.

- [ ] Mac에서 `tailscale` CLI를 PATH에 설치하거나 `TODOLAB_TAILSCALE_CLI`로 경로를 지정한다.
- [ ] Mac과 Android 기기를 같은 tailnet에 연결한다.
- [ ] Tailscale MagicDNS 이름을 production API 주소로 확정한다.
- [ ] Tailscale Serve 또는 동등한 reverse proxy로 `https://<device>.<tailnet>.ts.net`을 `http://127.0.0.1:8080`에 연결한다.
- [ ] `.env`에 `TODOLAB_TAILSCALE_API_URL`을 저장하고 `TODOLAB_REQUIRE_TAILSCALE_URL=true ./scripts/check-production-env.sh`를 통과시킨다.
- [ ] `TODOLAB_TAILSCALE_API_URL=... ./scripts/check-tailscale-production.sh`를 통과시킨다.
- [ ] Android 실제 기기에서 `GET /api/v1/auth/me`까지 HTTPS로 접근되는지 확인한다.
- [ ] Tailscale 연결이 끊긴 기기와 허가되지 않은 tailnet 사용자가 API에 접근하지 못하는지 확인한다.

증적:

- `./scripts/check-production-env.sh`
- `./scripts/check-tailscale-production.sh`
- Android production build의 `/api/v1/auth/me` 결과

### P0. Android production smoke

목표: production API URL 확정 후 모바일 핵심 흐름을 실제 기기에서 검증한다.

- [ ] Android production build에 Tailscale HTTPS API URL을 반영한다.
- [ ] `/api/v1/auth/login` 성공과 401 실패 처리를 확인한다.
- [ ] Today 조회, 생성, 완료를 확인한다.
- [ ] 게스트 발급, 게스트 `/auth/me`, 게스트 승격 또는 기존 계정 병합 흐름을 확인한다.
- [ ] 결과를 `docs/mobile/MOBILE_API_BACKEND_STATUS.md`에 날짜와 범위만 기록한다. token, 비밀번호, private URL은 기록하지 않는다.

증적:

- `TODOLAB_SMOKE_BASE_URL=https://<device>.<tailnet>.ts.net ./scripts/smoke-production-api.sh`
- `docs/mobile/MOBILE_INTEGRATION_RUNBOOK.md`의 production 결과 기록 템플릿

### P0. host 상시 가용성 검증

목표: 재부팅, 재로그인, Docker Desktop 재시작 뒤 수동 코드 실행 없이 production API가 복구된다.

- [ ] `TODOLAB_CONFIRM_POWER_POLICY=APPLY ./scripts/apply-production-power-policy.sh`를 관리자 권한으로 실행한다.
- [ ] `TODOLAB_STRICT_POWER=true ./scripts/check-production-host.sh`를 통과시킨다.
- [ ] 실제 재부팅 또는 로그아웃/로그인 후 `./scripts/check-production-recovery.sh`를 통과시킨다.
- [ ] Tailscale URL 확정 후 `TODOLAB_TAILSCALE_API_URL=... ./scripts/check-production-recovery.sh`를 통과시킨다.
- [ ] Docker Desktop 재시작, 네트워크 변경, 절전 복귀 뒤 readiness와 Android 접근을 확인한다.

증적:

- `./scripts/check-production-host.sh`
- `./scripts/check-production-recovery.sh`
- `./scripts/report-production-status.sh`

### P1. offsite backup 확정

목표: PC 디스크 장애에도 복구 가능한 외부 매체 또는 신뢰할 수 있는 동기화 위치를 운영 절차에 포함한다.

- [ ] 외부 디스크, NAS, cloud sync 중 하나를 `TODOLAB_OFFSITE_BACKUP_DIR`로 결정한다.
- [ ] `TODOLAB_OFFSITE_BACKUP_DIR=... ./scripts/sync-production-backup.sh`를 통과시킨다.
- [ ] `TODOLAB_REQUIRE_OFFSITE_BACKUP=true ./scripts/check-production-env.sh`를 통과시킨다.
- [ ] `TODOLAB_OFFSITE_BACKUP_DIR=... ./scripts/check-production-routine.sh`를 통과시킨다.
- [ ] offsite 복사본에서 임시 DB restore를 1회 검증한다.

증적:

- 최신 backup gzip 검증
- SHA-256 checksum 일치
- 임시 DB restore 결과

### P1. Expo Web production origin 결정

목표: Expo Web을 production API에 연결할 필요가 있는지 결정하고, 필요할 때만 CORS origin을 연다.

- [ ] Expo Web production 배포 여부를 결정한다.
- [ ] 사용하지 않으면 `TODOLAB_ALLOWED_ORIGINS`는 비워 둔다.
- [ ] 사용하면 실제 origin만 `TODOLAB_ALLOWED_ORIGINS`에 추가한다.
- [ ] `TODOLAB_EXPO_WEB_ORIGIN=... ./scripts/check-tailscale-production.sh`로 preflight를 확인한다.

증적:

- `TODOLAB_ALLOWED_ORIGINS` 설정 여부
- `OPTIONS /api/v1/auth/me` preflight 결과

### P2. 제품 정책 후속 결정

- [ ] 모바일 연결 완료 화면에서 병합 결과 count를 구체적으로 노출할지 결정한다.
- [ ] 게스트가 31일 이상 미접속한 뒤 기존 데이터를 복구해야 하는지 결정한다.
- [ ] 장기 게스트 복구가 필요하면 refresh token, device-bound proof, recovery code 중 별도 인증 수단을 먼저 설계한다.
- [ ] 서버 push를 실제 발송하려면 Expo/APNs/FCM credential 관리 방식과 발송 시점을 확정한다.

## 3. 현재 완료된 기준

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

## 4. 기본 검증 명령

```bash
./gradlew test
./scripts/check-production-env.sh
./scripts/check-production-routine.sh
./scripts/check-production-recovery.sh
./scripts/report-production-status.sh
```

Tailscale URL과 offsite backup 경로가 확정된 뒤에는 strict 검증을 추가한다.

```bash
TODOLAB_REQUIRE_TAILSCALE_URL=true ./scripts/check-production-env.sh
TODOLAB_TAILSCALE_API_URL=https://<device>.<tailnet>.ts.net ./scripts/check-tailscale-production.sh
TODOLAB_REQUIRE_OFFSITE_BACKUP=true ./scripts/check-production-env.sh
TODOLAB_OFFSITE_BACKUP_DIR=/absolute/offsite/path ./scripts/check-production-routine.sh
```

## 5. 문서 유지 원칙

- 완료 이력은 로드맵에 길게 누적하지 않고 관련 계약 문서 또는 migration 이력으로 이동한다.
- 미정인 실제 값은 추측하지 않고 `미정` 또는 `확정 필요`로 둔다.
- secret, access token, DB dump 내용, private production URL은 문서와 로그에 남기지 않는다.
- API 계약 변경은 OpenAPI JSON, `API_V1_FRONTEND.md`, 관련 테스트를 함께 갱신한다.
