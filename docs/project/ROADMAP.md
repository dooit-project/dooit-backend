# Dooit Backend Roadmap

Last updated: 2026-10-30

이 문서는 **백엔드 API 개발·리팩토링과 서버 운영 작업 목록**이다. 프론트 요청의 서버 구현 누락을 먼저 확인하고, 누락이 없으면 기존 API를 유지하는 코드 개선과 정리를 진행한다. 모바일 구현·화면 감사·스크린샷·실기기 UI 검증은 프론트 담당이며 이 저장소의 개발 할 일에 포함하지 않는다. 이미 구현된 API 계약과 운영 절차의 세부 내용은 각 계약 문서와 runbook을 원본으로 본다.

## 1. 현재 기준

- 모바일 API 기준 경로는 `/api/v1/**`다.
- 인증은 `/api/v1/**` 모바일/웹 클라이언트 API에서 Bearer JWT를 사용한다.
- API 원본 계약은 실행 중인 백엔드의 `/v3/api-docs` OpenAPI JSON이다.
- Java base package는 `pj.dooit`, Gradle project name은 `Dooit`이다.
- local production은 이 Mac의 Docker Compose와 external MySQL volume을 사용한다.
- production app port는 `127.0.0.1:8080`에만 bind하고, 외부 API는 Cloudflare Tunnel의 `https://dooitapi.hsng.pe.kr`로만 연다.
- Docker는 prd_dooit와 stg_dooit로 분리하며 STG는 localhost:18080, 별도 DB/키를 사용한다. [운영 절차](../ops/DOCKER_ENVIRONMENTS.md)를 따른다.
- 실제 secret, access token, DB dump 내용, private production URL은 문서에 기록하지 않는다.

### 현재 운영 기준

legacy 접근 차단·requestedType·p95는 10월 30일 PRD에 반영했다. 우선 잔여 작업은 외부 장애 통지 연결, 멱등성 원자성 보강과 운영 복구성 검증이다. 클라이언트 실기기 검증 결과는 외부 연동 참고 사항으로만 관리한다. 공개 HTTPS 도메인, Docker Compose production 구조, production DB migration 적용, 최신 backend image 배포, readiness/public HTTPS smoke는 완료됐다.

| 구분 | 현재 상태 | 다음에 닫을 일 |
| --- | --- | --- |
| 빠른 등록 | 기존 자동 파싱 유지 및 requestedType 선택 유형 보존 STG 반영·실 API 검증. 새 필드는 10월 30일 PRD 반영 | 선택 유형 제품 정책 확인, 실제 입력 사례 기반 예외 표현 검토 |
| 빠른 등록 템플릿 | personal template CRUD와 template 기반 Task 생성 구현 완료 | 추가 필드 요청이 접수되면 계약 검토 |
| 일일 계획 | `daily-plans` resource, focus 1~3개, 계획 확정/마감 상태, 결과 summary 구현 완료 | 부분 실패 사례가 접수되면 batch mutation 필요성 검토 |
| 예상 소요 시간 | Task nullable 필드와 template 기본값 적용 구현 완료 | 추가 입력 요구가 접수되면 validation·계약 검토 |
| Checklist | 개인/Workspace Task 하위 한 단계 checklist item API 구현 완료 | 담당자/알림 추가 요청 시 계약 검토 |
| 카테고리 탐색 | 개인 Task category 목록/count API 구현 완료 | 수동 정렬 요청 시 저장 모델·계약 검토 |
| 공유 workspace | 설계와 1차 Task/D-Day API 구현 완료 | 권한·초대 API 결함 재현 시 서버 회귀 검증 |
| 서버 push | token, 후보, 이력, provider 설정, Expo client, idempotency, invalid token 처리, 개인 owner와 shared workspace scheduler 자동 발송 완료 | 운영 credential 적용 후 실수신 smoke |
| PRD DB schema | production DB migration 적용 완료. 목표 schema 포함 적용 완료. 배포 상세는 운영 runbook 기준 | 새 schema 변경 시 migration/호환성 점검 |
| production 접근 | 실제 Web/API 도메인 HTTPS 연결, HTTPS 강제, readiness·CORS public smoke 완료 | 배포 시 서버 API smoke |
| 운영 복구성 | launchd, Docker health, readiness recovery check 통과 | 전원 정책 적용과 재부팅/Docker 재시작 실검증 |
| backup | local routine backup 검증 통과 | offsite backup 위치 결정과 restore 연습 |
| monitoring | 기본 stack·p95·5xx 패널과 장애 규칙 2개 정상 평가 | 외부 통지 연결과 noise 조정 |
| 목표 중심 계획·실천 여정 | 목표 schema·공개 HTTPS 인증 API smoke 완료. 현재 PRD image는 `1d719d3`. AI는 기본 비활성 | 이관 API 오류 대응, AI provider·보관·삭제 정책 확정 후 실제 adapter 연결 |

## 2. 제품 기능 로드맵

요청된 제품 API는 대부분 1차 구현이 닫혔다. 이후에는 서버 구조·정합성·회귀 테스트와 실제 API 사용에서 확인된 결함을 개선한다. 새 기능은 요청 근거와 계약을 확인한 뒤 추가한다. 완료된 API의 상세 계약은 `docs/api/**`를 원본으로 본다.

### P0. 요청 API 누락 점검과 백엔드 코드 정리

2026-10-18 계약·구현·관련 테스트 대조 기준, 전달받은 목표 API와 빠른 기록 선택 유형 요청의 추가 미구현 endpoint는 발견되지 않았다. 당시 빠른 기록은 STG 검증 완료·PRD 미배포였다. 이후 2026-10-30 PRD에 반영했다. 날짜 없는 TODO 및 시각/반복 제한은 구현 누락과 구분해 정책 확인 사항으로 관리한다. 외부 AI adapter는 기존의 별도 미완료 범위다.

1. [x] 프론트 요청과 서버 DTO·서비스·회귀 테스트를 대조한다. 세부 기준은 [연동 상태](../mobile/MOBILE_API_BACKEND_STATUS.md), [목표 계약](../api/GOAL_JOURNEY_CONTRACT.md), [빠른 기록 계약](../api/QUICK_CAPTURE_TYPE_CONTRACT.md)을 따른다.
2. [x] `TaskService`의 일반/Workspace/빠른 기록 생성 책임을 `TaskCreationService`로 분리한다. 반복 시리즈와 Task 저장은 같은 트랜잭션을 유지한다.
3. [x] 날짜·시간·주간 반복의 정규식 해석을 내부 `TaskQuickCaptureTemporalParser`로 분리했다. `TaskQuickCaptureParser`는 유형별 정책·시간 구간·응답 메타데이터를 결정한다. IDEA 해석 생략, 날짜/시각 오류 우선순위, 날짜·반복 동시 입력의 기존 적용 규칙을 회귀 테스트로 보존한다. 표현 지원 범위와 오류 계약은 변경하지 않았다.
4. [ ] 생성 멱등성의 원자성·다중 인스턴스 동시성 개선안을 정하고 장애·경합 회귀 테스트를 추가한다. DB 변경과 기존 키/응답 호환성을 먼저 검토한다.
5. [x] 비활성 메일 실행기, 서비스 검증 없는 스레드 벤치마크, 구 버전 Gradle 주석을 제거했다. 문서의 legacy 공개 정책·MySQL 바인딩·현황 요약을 구현과 맞췄다. 실제 MVC 가상 스레드 검증과 migration 이력은 유지한다.

리팩토링 검증은 `./gradlew test`로 수행한다. 생성 서비스·빠른 기록 파서 분리는 URL·요청/응답 DTO·오류 코드·DB schema 변경이 없고 클라이언트 수정이나 migration이 필요하지 않다. 생성 서비스 분리는 `9038f39`로 커밋했다. 빠른 기록 파서 분리는 `41e02db`로 커밋하고 소유권 수정과 함께 STG에 반영했다. 커밋·배포 여부는 구현 완료와 별도로 기록한다.

### 개인 Task 소유권 회귀 검증 (2026-10-25)

- [x] 개인 Task 조회·수정·삭제·완료와 체크리스트 7개 연산의 JWT owner·부모/item 범위 검사 경로를 확인했다.
- [x] 소유권 없는 legacy `/api/tasks/**` 우회를 보안 필터에서 차단했다. 기존 경로 이용자는 인증된 v1 API로 이관해야 한다. 상세 영향은 [API 계약](../api/API_V1_FRONTEND.md)의 Task 보안 점검을 따른다.
- [x] A Task를 B로 요청한 뒤 원본 재조회, 본인 부모에 타인 item ID 혼합, 미인증 요청과 legacy 경로 차단 회귀 테스트를 추가했다.
- [x] `b9495f0` image의 STG 반영과 실제 거부 요청 38건/원본 보존 및 기존 빠른 기록 smoke를 통과했다. 전체 테스트는 692개 통과다. 상세 증적은 [Docker 환경](../ops/DOCKER_ENVIRONMENTS.md)을 따른다.
- [x] 보안 수정은 10월 30일 PRD 1d719d3에 반영하고 legacy 차단·원본 보존을 검증했다.

### CI 완료 시각 정밀도 수정 (2026-10-30)

- [x] CI에서 완료 직후 나노초 응답과 DB 재조회 마이크로초 반올림의 불일치로 실패한 회귀 테스트를 수정했다. Task 완료 시각은 운영 MySQL DATETIME 정밀도에 맞춰 초 단위로 저장·응답하고 연결 실천·체크리스트에 동일한 시각을 전달한다.
- [x] 소수 초 입력을 포함한 완료·목표 연결/변경/해제의 시간 보존을 검증한다. v1 `completedAt` 입력의 소수 초는 버리므로 기존 소수 초 의존 클라이언트에 표시 정밀도 변경이 있다. URL·오류 코드는 동일하며 DB migration은 필요하지 않다.

### 운영 관점 코드 검토 (2026-10-29)

이번 검토는 인증/인가, 멱등성, 로깅, 주요 서비스 책임, 빌드·Compose와 문서 대조에 집중했다. 모든 비즈니스 분기나 실제 운영 부하를 검증한 것은 아니다.

- [x] legacy D-Day의 전체 조회·연결 Task 조회·삭제 우회를 차단하고 인증 유무별 회귀 테스트를 추가했다. API 기본 접근 정책도 명시 허용 외에는 거부하도록 변경했다.
- [x] 멱등성 잠금 제거 시 대기자와 새 요청의 잠금이 갈라지는 경합을 고정 크기 잠금으로 수정했다. 실패 요청 이후 대기/신규 요청의 직렬화를 검증한다.
- [x] JSON 파싱에 실패한 payload 원문이 로그에 남는 문제를 수정했다. 잘린 JSON·일반 텍스트는 `[UNPARSEABLE_PAYLOAD]`로 기록한다. 운영 payload 로깅 기본 비활성은 유지한다.
- [x] 2026-10-30 미커밋 작업본을 STG에 반영하고 거부 요청 46건·원본 보존·빠른 등록 smoke를 통과했다. [STG 기록](../ops/DOCKER_ENVIRONMENTS.md)을 따른다.
- [x] 사용자 승인 후 커밋·푸시하고 확정 image 1d719d3를 PRD에 반영했다. 백업·702개 테스트·공개 API 검증을 통과했다.
- [ ] 생성 업무와 멱등성 기록의 원자성, 다중 인스턴스 DB 잠금, 만료 레코드 정리 및 실패 후 재시도 정책을 함께 설계한다. 현재 `expiresAt`만 기록하며 자동 삭제하지 않는다.
- [ ] `TaskService`(약 920줄)의 검색 매칭·추천 계산을 순수 계산 객체로 분리한다. `TaskTxService`(약 690줄)는 반복 수정과 일반 상태 변경의 트랜잭션 경계를 보존하며 나눈다. 이미 분리한 생성·날짜 파서는 유지한다.
- [ ] monitoring image의 `latest` 태그를 검증된 버전/digest로 고정한다. 실제 stack 호환성 검증 후 반영하며 이번에는 임의 버전으로 바꾸지 않았다.

가독성은 도메인별 controller/service/repository와 DTO 분리가 일관적이지만 위 대형 서비스에 책임이 집중돼 있다. 문서 개수 자체보다 정책 모순과 구현/배포 상태 혼동을 먼저 정리했다. 계약·runbook·migration 이력은 각각 참조와 운영 용도가 있어 삭제하지 않았다.

### P1. 빠른 기록 선택 유형 보존 (2026-10-18 별도 요청)

STG 실 API 검증 및 10월 30일 PRD 반영 완료. 계약/서버 회신: [`../api/QUICK_CAPTURE_TYPE_CONTRACT.md`](../api/QUICK_CAPTURE_TYPE_CONTRACT.md).

- [ ] TODO 시각/반복 오류 및 날짜 없는 TODO 기록함 정책을 모바일/제품과 확인한다.
- [x] PRD 1d719d3 배포와 합성 IDEA 선택 유형 저장 검증을 완료했다. STG 전체 수용 예시와 구분한다.
- 멱등성 개선은 위 백엔드 코드 정리 4번에서 관리한다.

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

### P1. 목표 중심 계획·실천 여정 1차

목표와 계획, 실천, 여정 1차 API는 운영 배포됐으며 외부 AI adapter는 남아 있다. 계약은 [`../api/GOAL_JOURNEY_CONTRACT.md`](../api/GOAL_JOURNEY_CONTRACT.md)를 원본으로 둔다.

- [ ] AI provider·보관·삭제 정책을 확정한 뒤 실제 provider adapter의 HTTP timeout·응답 크기 제한·출력 검증을 연결하고, 다중 인스턴스 운영 시 공유 quota/비용 제한을 설계한다.

### P2. 제품 정책 후속 결정

- [ ] Workspace template이 필요한지 모바일 UX 기준으로 결정한다.
- [ ] 게스트가 장기 미접속 후에도 복구되어야 하는지 제품 정책을 확정한다.
- [ ] workspace calendar feed를 멤버별 공개 범위로 열지 결정한다.

## 3. 운영 마무리 로드맵

운영 로드맵은 실제 secret, private URL, DB dump 내용을 남기지 않고 통과한 명령과 검증 범위만 기록한다.

### 적용 완료한 production DB migration 기준

목표: 현재 `main`의 API 계약이 production DB schema와 맞도록 미적용 migration을 백업 후 수동 적용한다.

목표 schema까지 2026-10-17 적용 완료했다. 적용 파일과 검증 범위는 [migration 이력](../db/MIGRATION_HISTORY.md)을 따른다. 이미 적용된 DDL은 재실행하지 않는다.

증적:

- `./scripts/backup-db.sh`
- migration 적용 전후 schema 확인 쿼리
- `curl --fail http://127.0.0.1:8080/actuator/health/readiness`
- `GET /api/v1/system/metadata`

### P1. API 배포와 연동 지원

공개 HTTPS, Web origin CORS와 localhost STG API 검증은 완료했다. 백엔드는 API 계약·배포 상태와 서버 측 인증/생성/조회 smoke를 관리한다.

- [x] PRD image/commit·readiness·IDEA 저장 검증을 운영 runbook에 기록했다. 전체 유형 수용 예시는 STG 기록을 따른다.
- 클라이언트에서 재현된 API 오류는 요청·응답·서버 로그를 근거로 백엔드 결함 여부를 확인한다.
- Android build 설정·실기기 UI·화면 감사·스크린샷 갱신은 프론트 담당이다. 기존 검증 미완료 이력은 [연동 상태](../mobile/MOBILE_API_BACKEND_STATUS.md)에 참고 기록으로 보존한다.

### P0. host 상시 가용성 검증

목표: 재부팅, 재로그인, Docker Desktop 재시작 뒤 수동 코드 실행 없이 production API가 복구된다.

- [ ] `DOOIT_CONFIRM_POWER_POLICY=APPLY ./scripts/apply-production-power-policy.sh`를 관리자 권한으로 실행한다.
- [ ] `DOOIT_STRICT_POWER=true ./scripts/check-production-host.sh`를 통과시킨다.
- [ ] 실제 재부팅 또는 로그아웃/로그인 후 `./scripts/check-production-recovery.sh`를 통과시킨다.
- [ ] Docker Desktop 재시작, 네트워크 변경, 절전 복귀 뒤 readiness와 공개 API 접근을 확인한다.

증적:

- `./scripts/check-production-host.sh`
- `./scripts/check-production-recovery.sh`
- `./scripts/report-production-status.sh`

### P1. offsite backup 확정

목표: PC 디스크 장애에도 복구 가능한 외부 매체 또는 신뢰할 수 있는 동기화 위치를 운영 절차에 포함한다.

- [ ] 외부 디스크, NAS, cloud sync 중 하나를 `DOOIT_OFFSITE_BACKUP_DIR`로 결정한다.
- [ ] `DOOIT_OFFSITE_BACKUP_DIR=... ./scripts/sync-production-backup.sh`를 통과시킨다.
- [ ] `DOOIT_REQUIRE_OFFSITE_BACKUP=true ./scripts/check-production-env.sh`를 통과시킨다.
- [ ] `DOOIT_OFFSITE_BACKUP_DIR=... ./scripts/check-production-routine.sh`를 통과시킨다.
- [ ] offsite 복사본에서 임시 DB restore를 1회 검증한다.

증적:

- 최신 backup gzip 검증
- SHA-256 checksum 일치
- 임시 DB restore 결과

### P1. production 모니터링 구축

Prometheus, Grafana, Loki, Grafana Alloy 기본 stack 실기동과 점검은 완료했다. 구성과 명령은 [모니터링 runbook](../ops/MONITORING_RUNBOOK.md)을 따른다.

- [x] HTTP histogram 수집 설정과 API 전용 쿼리를 추가했다. 2026-10-30 STG에서 bucket과 별도 Prometheus의 실제 p95 계산을 검증했다.
- [x] p95 image를 PRD에 반영하고 bucket·실제 p95 값을 확인했다.
- [ ] 트래픽 증가에 따른 histogram 시계열 수와 Prometheus 메모리를 관찰한다.
- [x] 5xx 비율·건수 패널을 추가했다. 현재 패널은 8개다.
- [ ] GC·CPU 패널과 container health/restart exporter 필요성을 검토한다.
- [x] 수집 중단·5xx 증가 규칙을 적용하고 정상 평가 및 PromQL 9개 시나리오를 검증했다.
- [ ] 외부 수신처를 확정하고 시험/복구 통지 수신을 검증한다. 현재 Grafana 내부 표시만 동작한다.

증적:

- [`../ops/MONITORING_RUNBOOK.md`](../ops/MONITORING_RUNBOOK.md)
- `curl --fail --user "$DOOIT_MONITORING_USERNAME:$(cat "$DOOIT_MONITORING_PASSWORD_FILE")" http://127.0.0.1:8080/actuator/prometheus`
- `up{job="dooit-backend"}`
- Grafana Prometheus/Loki datasource health

### P1. Expo Web production origin 운영

목표: 공개 Web origin만 production API CORS에 허용하고, origin 변경 시 preflight를 다시 검증한다.

증적:

- `DOOIT_ALLOWED_ORIGINS` 설정 여부: `https://dooit.hsng.pe.kr`
- `OPTIONS /api/v1/auth/me` preflight 결과: public API와 Web origin 기준 통과

## 4. 현재 완료된 기준

아래 항목은 현재 코드와 문서 기준으로 닫힌 상태다. 상세 계약은 관련 문서에서 관리한다.

- v1 Auth, Task, D-Day, 검색, Today 재정렬, 반복, timezone, 알림 후보 API 계약
- owner scope와 JWT 인증/인가 계약
- 게스트 계정 생성, refresh, 승격, 기존 계정 병합, 병합 결과 count, rate limit, 만료 정리
- production DB migration 적용 이력과 수동 migration 관리 방식
- local production Docker Compose, readiness, rollback, backup/restore, routine/recovery/status report 스크립트
- API logging masking, UTF-8 response logging, 운영 문서 UI 비공개 기준
- Prometheus, Grafana, Loki, Alloy 기본 monitoring 실기동과 운영 점검 절차

관련 문서:

- [`../api/API_V1_FRONTEND.md`](../api/API_V1_FRONTEND.md)
- [`../api/AUTH_CONTRACT.md`](../api/AUTH_CONTRACT.md)
- [`../api/GOAL_JOURNEY_CONTRACT.md`](../api/GOAL_JOURNEY_CONTRACT.md)
- [`../ops/LOCAL_PRODUCTION_RUNBOOK.md`](../ops/LOCAL_PRODUCTION_RUNBOOK.md)
- [`../ops/ENVIRONMENT_INTEGRATION.md`](../ops/ENVIRONMENT_INTEGRATION.md)
- [`../ops/MONITORING_RUNBOOK.md`](../ops/MONITORING_RUNBOOK.md)
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
