# ToDoLab Backend Roadmap

Last updated: 2026-08-20

이 문서는 완료 이력 보관소가 아니라 **앞으로 닫아야 할 백엔드/운영 작업 목록**이다. 이미 구현된 API 계약과 운영 절차의 세부 내용은 각 계약 문서와 runbook을 원본으로 본다.

## 1. 현재 기준

- 모바일 API 기준 경로는 `/api/v1/**`다.
- 인증은 `/api/v1/**` 모바일/웹 클라이언트 API에서 Bearer JWT를 사용한다.
- API 원본 계약은 실행 중인 백엔드의 `/v3/api-docs` OpenAPI JSON이다.
- local production은 이 Mac의 Docker Compose와 external MySQL volume을 사용한다.
- production app port는 `127.0.0.1:8080`에만 bind하고, Android 접근은 Tailscale HTTPS 경로로만 연다.
- staging은 현재 운영하지 않는다. 환경은 local 개발 환경과 이 PC의 production 환경으로만 구분한다.
- 실제 secret, access token, DB dump 내용, private production URL은 문서에 기록하지 않는다.

### 2026-08-20 정리

현재 남은 P0는 기능 구현보다 production 접근성과 운영 복구성 검증에 몰려 있다.

| 구분 | 현재 상태 | 다음에 닫을 일 |
| --- | --- | --- |
| 빠른 등록 | API와 규칙 기반 파싱 구현 완료 | 모바일 실제 입력 로그 기반 날짜/시간 표현 보강 |
| 빠른 등록 템플릿 | personal template CRUD와 template 기반 Task 생성 구현 완료 | 모바일 UI 연동 후 누락 필드가 있으면 계약 보강 |
| 공유 workspace | 설계와 1차 Task/D-Day API 구현 완료 | 모바일 연동 과정에서 권한/초대 UX 검증 |
| 서버 push | token, 후보, 이력, provider 설정 계약 완료 | Expo 발송 client, scheduler, idempotency, invalid token 처리 구현 |
| production 접근 | host 내부와 Tailscale host smoke 가능, `.env` Tailscale URL 반영 완료 | Android 실제 기기 HTTPS smoke |
| 운영 복구성 | launchd, Docker health, readiness, Tailscale recovery check 통과 | 전원 정책 적용과 재부팅/Docker 재시작 실검증 |
| backup | local routine backup 검증 통과 | offsite backup 위치 결정과 restore 연습 |

## 2. 제품 기능 로드맵

운영 접근 경로가 막혀 있더라도 backend 설계와 API 구현을 병행할 수 있는 제품 기능이다. 모바일 UX 영향, 데이터 모델 변경 범위, 공유/권한 모델의 blast radius를 기준으로 순서를 둔다.

### P0. 일정 빠른 등록 API

목표: 모바일과 서버 화면에서 같은 backend API로 빠르게 일정을 입력한다. 첫 버전은 실패해도 안전하게 Inbox Task로 저장되는 보수적 파싱을 기준으로 한다.

상태: API와 회귀 테스트는 닫혔다. 남은 작업은 실제 모바일 입력 로그를 반영한 파싱 coverage 확장이다.

- [x] `POST /api/v1/tasks/quick-capture` 계약을 추가한다.
- [x] request는 원문 `text`, 기준 날짜, 사용자 timezone, 선택적 기본 category를 받는다.
- [x] “내일 3시 회의”, “매주 월요일 운동” 같은 입력에서 title/date/time/recurrence 후보를 파싱한다.
- [x] 파싱 확신도가 낮으면 원문 title의 Inbox `TODO`로 저장한다.
- [x] 생성 결과에 `parsed` 여부와 적용된 date/time/type/recurrence 요약을 반환한다.
- [x] 모바일이 확인 화면을 띄울 수 있도록 원문과 서버 해석 결과를 함께 내려준다.
- [x] 파싱 실패가 데이터 손실이나 5xx로 이어지지 않도록 회귀 테스트를 추가한다.
- [x] “금요일 병원”처럼 단독 요일 표현을 날짜로 해석하는 규칙을 추가한다.
- [ ] 모바일 실제 입력 로그를 바탕으로 날짜/시간 표현 coverage를 확장한다.

선행 조건:

- 사용자 timezone 기준 날짜 경계는 현재 구현된 `User.timeZone`을 사용한다.
- 자연어 파서는 처음부터 완전한 한국어 NLP로 만들지 않고, 규칙 기반으로 시작한다.

### P0. 빠른 등록 템플릿

목표: 자주 쓰는 일정과 할 일을 한 번의 선택으로 생성할 수 있게 한다. 자연어 파싱보다 예측 가능하고 모바일 반복 사용성에 바로 도움을 준다.

상태: backend 1차 구현은 닫혔다. 모바일 화면 연동 중 추가 필드가 필요해질 때 계약을 보강한다.

- [x] `TASK_TEMPLATE` 모델을 추가한다.
- [x] `GET /api/v1/task-templates`, `POST /api/v1/task-templates`, `PUT /api/v1/task-templates/{id}`, `DELETE /api/v1/task-templates/{id}`를 추가한다.
- [x] template에는 title, type, category, allDay, default time, recurrence preset을 둔다.
- [x] `POST /api/v1/task-templates/{id}/tasks`로 template 기반 Task를 생성한다.
- [x] owner scope와 guest 승격/병합/cleanup 대상에 template을 포함한다.
- [x] D-Day 연결 템플릿 정책을 결정한다.
- [x] 공유 workspace 템플릿 정책을 공유 설계 단계에서 결정한다.

선행 조건:

- 빠른 등록 API와 request/response 형태를 맞춰 모바일 입력 UI가 둘을 함께 사용할 수 있어야 한다.

### P1. 일정 공유 설계

목표: 개인 owner 모델을 깨지 않고 공유 캘린더/공유 Task를 도입할 수 있는 권한 모델을 먼저 확정한다.

상태: 설계와 invariant 테스트 기준은 닫혔다. 구현 세부 계약은 `docs/api/SHARING_CONTRACT.md`를 원본으로 본다.

- [x] 공유 단위 결정: 1차 범위는 workspace 안의 Task/D-Day로 제한한다.
- [x] 권한 모델 결정: OWNER, EDITOR, VIEWER와 PENDING/ACTIVE/REMOVED membership을 사용한다.
- [x] 초대 방식 결정: 1차 범위는 email 초대이며 invite link는 제외한다.
- [x] 공유된 Task의 반복 series, D-Day 연결, push token, 알림 이력 소유권 정책을 문서화한다.
- [x] 기존 owner scope API와 공유 조회 API를 분리한다.
- [x] migration과 rollback 전략을 먼저 문서화한다.
- [x] `docs/api/SHARING_CONTRACT.md` 기준으로 구현 전후 invariant 테스트를 추가한다.

선행 조건:

- 개인 데이터 격리 invariant를 깨지 않는 repository query 정책을 테스트로 고정한다.
- 공유 기능은 바로 구현하지 않고 설계 문서와 테스트 전략부터 커밋한다.

### P1. 일정 공유 1차 구현

목표: 가장 작은 공유 단위를 실제 API로 연다. 1차 범위는 “공유 workspace 안의 Task/D-Day를 멤버가 조회”로 제한한다.

상태: backend 1차 구현은 닫혔다. 모바일 연동 단계에서는 개인 API와 workspace API가 섞이지 않는지, VIEWER/EDITOR 권한 UX가 맞는지 검증한다.

- [x] `SHARED_WORKSPACE`, `WORKSPACE_MEMBER` 모델과 migration을 추가한다.
- [x] workspace 생성, 멤버 초대, 수락, 제거 API를 추가한다.
- [x] Task/D-Day/반복 series에 `PERSONAL`/`WORKSPACE` scope 컬럼을 추가하고 개인 API 격리 테스트를 추가한다.
- [x] shared workspace Task 생성/조회 API를 개인 Task API와 명확히 구분한다.
- [x] viewer/editor 권한에 따른 Task 생성/조회 제한을 적용한다.
- [x] shared workspace D-Day 생성/조회 API를 개인 D-Day API와 명확히 구분한다.
- [x] shared workspace Task 수정/삭제 API를 추가한다.
- [x] shared workspace D-Day 삭제와 Task 연결 API를 추가한다.
- [x] workspace 반복 Task materialize를 구현한다.
- [x] workspace 알림 후보 API를 추가한다.
- [x] 게스트 계정은 공유 workspace 초대 대상에서 제외하거나 제한 정책을 둔다.
- [x] 감사 로그 또는 최소한 생성/수정자 필드를 남긴다.

선행 조건:

- P1 일정 공유 설계가 닫혀야 한다.

### P1. 서버 push 실제 발송

목표: 현재 구현된 push token, 알림 후보, 발송 이력 기반 위에 실제 provider 전송을 붙인다.

상태: provider 설정과 이력 저장 계약은 준비되어 있지만, 실제 Expo/APNs/FCM 호출 scheduler는 아직 구현하지 않는다.

- [ ] Expo/APNs/FCM 중 production provider와 credential 보관 방식을 확정한다.
- [ ] 발송 스케줄러 실행 시점과 look-ahead window를 정한다.
- [ ] idempotency key 기준으로 중복 발송을 막는다.
- [ ] provider 실패 응답에 따라 token 비활성화 정책을 적용한다.
- [x] 모바일 로컬 알림과 중복되지 않도록 `suppressLocalNotification` 정책을 검증한다.

선행 조건:

- production credential 관리 방식이 확정되어야 한다.

### P1. 검색/추천 고도화

목표: 이미 있는 검색과 Today 추천을 실제 사용 패턴에 맞게 개선한다.

- [ ] 최근 완료/이월/미룸 패턴 기반 Today 추천 점수를 추가한다.
- [x] category와 D-Day 제목을 `q` 검색 매칭 범위에 포함한다.
- [x] title, category, D-Day 제목, description 순서로 검색 매칭 ranking을 적용한다.
- [x] 검색 결과에 `matchedFields`와 `highlight`를 반환한다.
- [x] 반복 여부 필터와 반복 매칭 ranking을 추가한다.
- [ ] 모바일에서 빈 검색 결과일 때 추천 query/category를 줄 수 있는지 검토한다.

### P2. 장기 게스트 복구

목표: 31일 이상 미접속한 게스트의 데이터 복구가 제품 요구로 확정될 때 별도 인증 수단으로 해결한다.

- [ ] refresh token, device-bound proof, recovery code 중 하나를 선택한다.
- [ ] token 탈취 시 피해 범위와 회수 정책을 정의한다.
- [ ] 이미 cleanup된 guest의 오류 코드와 UX 문구를 정한다.
- [ ] 새 guest id 발급을 복구 정책으로 사용하지 않는 원칙을 유지한다.

## 3. 운영 마무리 로드맵

운영 로드맵은 실제 secret, private URL, DB dump 내용을 남기지 않고 통과한 명령과 검증 범위만 기록한다.

### P0. production 접근 경로 확정

목표: 공용 인터넷 포트포워딩 없이 Android production build가 Tailscale HTTPS로 production API에 접근한다.

- [x] Mac에서 `tailscale` CLI를 PATH에 설치하거나 `Tailscale.app` 실행 파일을 자동 감지한다.
- [ ] Mac과 Android 기기를 같은 tailnet에 연결한다.
- [x] Tailscale MagicDNS 이름을 production API 주소로 확정한다.
- [x] Tailscale Serve 또는 동등한 reverse proxy로 `https://<device>.<tailnet>.ts.net`을 `http://127.0.0.1:8080`에 연결한다.
- [x] `.env`에 `TODOLAB_TAILSCALE_API_URL`을 저장하고 `TODOLAB_REQUIRE_TAILSCALE_URL=true ./scripts/check-production-env.sh`를 통과시킨다.
- [x] `TODOLAB_TAILSCALE_API_URL=... ./scripts/check-tailscale-production.sh`를 통과시킨다.
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
- [x] 현재 부팅 상태에서 `./scripts/check-production-recovery.sh`를 통과시킨다.
- [x] Tailscale URL 확정 후 `TODOLAB_TAILSCALE_API_URL=... ./scripts/check-production-recovery.sh`를 통과시킨다.
- [ ] 실제 재부팅 또는 로그아웃/로그인 후 `./scripts/check-production-recovery.sh`를 통과시킨다.
- [ ] Docker Desktop 재시작, 네트워크 변경, 절전 복귀 뒤 readiness와 Android 접근을 확인한다.

증적:

- `./scripts/check-production-host.sh`
- `./scripts/check-production-recovery.sh`
- `./scripts/report-production-status.sh`

### P1. offsite backup 확정

목표: PC 디스크 장애에도 복구 가능한 외부 매체 또는 신뢰할 수 있는 동기화 위치를 운영 절차에 포함한다.

- [x] local backup gzip, backup age, disk 여유 공간, readiness를 `./scripts/check-production-routine.sh`로 확인한다.
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

Tailscale URL과 offsite backup 경로가 확정된 뒤에는 strict 검증을 추가한다.

```bash
TODOLAB_REQUIRE_TAILSCALE_URL=true ./scripts/check-production-env.sh
TODOLAB_TAILSCALE_API_URL=https://<device>.<tailnet>.ts.net ./scripts/check-tailscale-production.sh
TODOLAB_REQUIRE_OFFSITE_BACKUP=true ./scripts/check-production-env.sh
TODOLAB_OFFSITE_BACKUP_DIR=/absolute/offsite/path ./scripts/check-production-routine.sh
```

## 6. 문서 유지 원칙

- 완료 이력은 로드맵에 길게 누적하지 않고 관련 계약 문서 또는 migration 이력으로 이동한다.
- 미정인 실제 값은 추측하지 않고 `미정` 또는 `확정 필요`로 둔다.
- secret, access token, DB dump 내용, private production URL은 문서와 로그에 남기지 않는다.
- API 계약 변경은 OpenAPI JSON, `API_V1_FRONTEND.md`, 관련 테스트를 함께 갱신한다.
