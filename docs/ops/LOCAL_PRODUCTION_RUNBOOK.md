# Local PC Production Runbook

Last updated: 2026-10-18

이 문서는 이 Mac의 Docker Compose를 Dooit의 단일 production 서버로 사용하고 실제 HTTPS 도메인으로 Android/Web이 접근하는 절차다.

## 1. 사전 준비

- Docker Desktop을 설치하고 로그인 시 자동 시작을 켠다.
- production DB image는 MySQL 8.4 LTS의 검증된 patch release인 `mysql:8.4.11`로 고정한다.
- 저장소의 `.env.example`을 `.env`로 복사한 뒤 실제 비밀번호와 JWT 값을 저장소 밖에서 관리한다.
- JWT secret은 `openssl rand -base64 48`로 생성한다.
- 최초 1회 external DB volume을 만든다.

```bash
for volume in dooit-mysql-data dooit-app-logs dooit-prometheus-data dooit-loki-data dooit-alloy-data dooit-grafana-data; do
  docker volume create "$volume"
done
```

`.env`와 백업 파일은 Git에 커밋하지 않는다.

기존 `.env`에 production JWT 값이 아직 없다면 실제 API 도메인 origin으로 한 번만 초기화한다. 스크립트는 기존 값을 덮어쓰지 않으며 생성한 secret을 화면에 출력하지 않는다.

```bash
./scripts/configure-production-env.sh https://api.example.com https://api.example.com
```

## 2. build와 기동

```bash
./gradlew clean test bootJar
./scripts/check-production-env.sh
docker compose config
docker compose up -d --build
docker compose ps
./scripts/check-production-host.sh
curl --fail http://127.0.0.1:8080/actuator/health/readiness
./scripts/smoke-production-api.sh
```

MySQL port는 host에 공개하지 않는다. app port도 `127.0.0.1:8080`에만 공개하고 Cloudflare Tunnel 또는 동등한 reverse proxy가 실제 HTTPS 도메인의 진입점이 된다.

로그인 후 Docker Desktop과 Compose stack 자동 복구가 필요하면 LaunchAgent를 설치한다.

```bash
./scripts/install-production-launchd.sh
launchctl bootstrap gui/$(id -u) ~/Library/LaunchAgents/pj.dooit.backend.production.plist
launchctl kickstart -k gui/$(id -u)/pj.dooit.backend.production
launchctl print gui/$(id -u)/pj.dooit.backend.production
./scripts/check-production-recovery.sh
```

이 LaunchAgent는 `RunAtLoad`와 5분 `StartInterval`로 `./scripts/ensure-production-up.sh`를 실행한다. 스크립트는 Docker Desktop을 시작하고 Docker engine 준비를 기다린 뒤 기존 app image tag를 보존해 `docker compose up -d mysql app`을 실행하고 readiness `UP`까지 대기한다.
재부팅 또는 재로그인 뒤에는 `./scripts/check-production-recovery.sh`로 현재 부팅 시각, LaunchAgent 실행 이력, Docker/Compose 상태, readiness를 증적화한다. 실제 도메인 HTTPS 경로는 `DOOIT_PUBLIC_API_URL=https://<api-origin> ./scripts/check-public-production.sh`로 별도 확인한다.

## 3. 실제 도메인 HTTPS

실제 API 도메인은 Cloudflare Tunnel 또는 동등한 reverse proxy를 통해 local app으로 연결한다. app은 host에서 `127.0.0.1:8080`에만 bind한다.

```bash
DOOIT_PUBLIC_API_URL=https://<api-origin> ./scripts/check-public-production.sh
```

모바일 production build의 API base URL은 실제 API 도메인을 사용한다. 공유기 포트포워딩은 사용하지 않는다.

확인 순서:

1. host에서 `DOOIT_PUBLIC_API_URL=https://<api-origin> ./scripts/check-public-production.sh`를 통과시킨다.
2. Android 브라우저에서 `https://<api-origin>/actuator/health/readiness`를 연다.
3. APK에서 로그인, Today 조회·생성·완료를 확인한다.
4. Wi-Fi와 모바일 데이터에서 같은 주소로 확인한다.

Expo Web production origin까지 함께 확인해야 하면 아래처럼 origin을 지정해 preflight를 점검한다.

```bash
DOOIT_PUBLIC_API_URL=https://<api-origin> DOOIT_WEB_ORIGIN=https://<web-origin> ./scripts/check-public-production.sh
```

## 4. 백업

기본 백업 위치는 저장소의 `backups/`, 기본 보관 기간은 14일이다. production 자동 백업은 저장소와 Docker volume 삭제에 대비해 `/Users/hyunseung/dooit-backups`를 사용한다. PC 자체 디스크 장애에 대비해 이 폴더를 별도 디스크나 신뢰할 수 있는 동기화 위치에 추가 복사한다.

```bash
./scripts/backup-db.sh
DOOIT_BACKUP_DIR=/absolute/backup/path DOOIT_BACKUP_RETENTION_DAYS=30 ./scripts/backup-db.sh
DOOIT_OFFSITE_BACKUP_DIR=/absolute/offsite/path ./scripts/sync-production-backup.sh
```

릴리스 전과 최소 하루 1회 실행한다. macOS launchd 등 host scheduler 연결은 백업 명령을 수동으로 검증한 뒤 구성한다.
`sync-production-backup.sh`는 최신 production backup을 외부/동기화 경로로 복사한 뒤 원본과 복사본의 gzip 무결성, SHA-256 checksum 일치를 확인한다. 출력되는 checksum은 dump 내용을 노출하지 않는다.

수동 백업을 검증한 뒤 매일 자동 실행이 필요하면 LaunchAgent 파일을 생성한다. 기본 실행 시각은 매일 03:15다.

```bash
DOOIT_BACKUP_DIR=/Users/hyunseung/dooit-backups ./scripts/install-backup-launchd.sh
launchctl bootstrap gui/$(id -u) ~/Library/LaunchAgents/pj.dooit.backend.backup.plist
launchctl print gui/$(id -u)/pj.dooit.backend.backup
```

LaunchAgent는 Docker CLI를 찾을 수 있도록 `/opt/homebrew/bin`, `/usr/local/bin`을 포함한 PATH를 명시한다. 설치 후 `launchctl kickstart -k gui/$(id -u)/pj.dooit.backend.backup`으로 즉시 실행을 검증하고, `last exit code = 0`과 새 backup 파일 생성을 확인한다.

다른 시각이나 백업 경로를 쓰려면 환경변수로 지정한다.

```bash
DOOIT_BACKUP_HOUR=4 DOOIT_BACKUP_MINUTE=0 DOOIT_BACKUP_DIR=/absolute/backup/path ./scripts/install-backup-launchd.sh
```

## 5. 복구 연습

복구는 현재 DB 내용을 교체하므로 먼저 app을 중단한다.

```bash
docker compose stop app
DOOIT_CONFIRM_RESTORE=RESTORE ./scripts/restore-db.sh backups/dooit-YYYYMMDD-HHMMSS.sql.gz
docker compose up -d app
curl --fail http://127.0.0.1:8080/actuator/health/readiness
```

복구 후 Android에서 실제 API 도메인으로 로그인, Today, Calendar의 기존 데이터가 보이는지 확인한다. production 데이터에 처음 시도하지 말고 별도 임시 Compose project/volume에서 최소 1회 복구를 검증한다.

## 6. 업데이트와 rollback

업데이트 전 순서:

1. `./scripts/backup-db.sh`
2. migration SQL과 API 호환성 확인
3. `./scripts/release-production.sh`
4. readiness와 production API smoke test
5. Android smoke test

release script는 app 재기동 후 `/actuator/health/readiness`를 최대 60초 동안 재시도한다. 일시적인 기동 중 응답 실패는 최종 실패로 보지 않고, 제한 시간 안에 readiness가 `UP`이면 release 성공으로 처리한다.

release script는 현재 `git rev-parse HEAD` 값을 `DOOIT_APP_COMMIT_SHA`로, short SHA를 기본 `DOOIT_APP_IMAGE_TAG`로 주입한다. 배포 후 `GET /api/v1/system/metadata`로 production backend commit/image tag를 확인한다.
release 후 `./scripts/smoke-production-api.sh`로 게스트 발급, `/auth/me`, 게스트 token 갱신, 회원가입 승격, 기존 계정 병합, 병합 재시도 멱등성을 확인한다. 기본 대상은 `http://127.0.0.1:8080`이며 다른 base URL은 `DOOIT_SMOKE_BASE_URL`로 지정한다. 스크립트는 access token과 비밀번호를 출력하지 않는다.

현재 schema 변경은 자동 migration 도구로 추적되지 않는다. 기존 volume에는 `docker-entrypoint-initdb.d`의 `schema.sql`이 다시 적용되지 않으므로, migration SQL 적용과 백업을 release의 필수 수동 단계로 유지한다. Flyway 도입 전에는 schema 변경이 있는 release를 자동 배포하지 않는다.
수동 적용 이력은 [`../db/MIGRATION_HISTORY.md`](../db/MIGRATION_HISTORY.md)에 기록한다. 현재는 Flyway를 도입하지 않고, schema 변경 빈도나 다중 환경 순서 관리가 필요해질 때 별도 작업으로 전환한다.

기존 production DB가 현재 schema보다 오래된 경우에는 app 업데이트 전에 백업 후 `docs/db/migrations/*.sql` 중 아직 적용되지 않은 파일을 날짜 순서대로 수동 적용한다. 적용 전에는 현재 schema를 확인하고, 이미 존재하는 table, column, index, constraint를 다시 만들지 않는다.

```bash
./scripts/backup-db.sh
docker compose stop app
docker compose exec -T mysql sh -c 'exec mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' < docs/db/migrations/<pending-migration>.sql
docker compose up -d app
curl --fail http://127.0.0.1:8080/actuator/health/readiness
```

`docs/db/migrations/20260803_prepare_local_production.sql`은 local production으로 전환하기 전 legacy DB를 현재 app schema에 맞추는 일회성 수동 migration이다. 이미 같은 table, column, index, constraint가 적용된 DB에는 다시 실행하지 않는다.
개별 migration 적용 여부와 결과는 [`../db/MIGRATION_HISTORY.md`](../db/MIGRATION_HISTORY.md)에 기록한다.

app image tag는 기본적으로 현재 git short SHA를 사용한다. 특정 tag로 release하려면 `DOOIT_APP_IMAGE_TAG`를 지정한다.

```bash
DOOIT_APP_IMAGE_TAG=$(git rev-parse --short HEAD) ./scripts/release-production.sh
docker images dooit-backend
```

새 버전 문제가 확인되면 직전 tag로 되돌린다. rollback은 DB를 변경하지 않고 app container만 기존 image로 재기동한다.

```bash
./scripts/rollback-production.sh <previous-image-tag>
```

Docker stdout/stderr log는 Compose에서 `json-file` driver, `max-size=10m`, `max-file=5`로 제한한다. application log는 `dooit-app-logs` volume의 `/app/logs`에 저장하고 Logback 보관 정책을 따른다.

## 7. 장애 확인 순서

```bash
docker compose ps
docker compose logs --tail=200 app
docker compose logs --tail=200 mysql
curl --fail http://127.0.0.1:8080/actuator/health/readiness
DOOIT_PUBLIC_API_URL=https://<api-origin> ./scripts/check-public-production.sh
```

- local readiness 실패: app/MySQL/schema 상태를 확인한다.
- local readiness 성공, 실제 도메인 실패: Cloudflare Tunnel 또는 reverse proxy 상태와 `DOOIT_PUBLIC_API_URL`을 확인한다.
- 실제 도메인 host smoke는 통과하지만 Android 실패: Android 네트워크, production API URL, 앱 빌드 설정을 확인한다.
- PC 절전·종료 중에는 앱을 사용할 수 없다. 상시 사용하려면 전원 연결 시 절전 정책을 별도로 정한다.

## 8. 모니터링

Prometheus, Grafana, Loki, Grafana Alloy 기반 monitoring stack은 `monitoring` Compose profile로 실행한다. 실제 password는 저장소 밖 파일에 저장하고, `.env`의 `DOOIT_MONITORING_PASSWORD_FILE`에 절대 경로만 둔다.

최초 준비:

```bash
mkdir -p /Users/hyunseung/dooit-secrets
openssl rand -base64 48 > /Users/hyunseung/dooit-secrets/monitoring-password
chmod 600 /Users/hyunseung/dooit-secrets/monitoring-password
```

`.env`에서 아래 값을 설정한다.

```bash
DOOIT_MONITORING_ENABLED=true
DOOIT_MONITORING_USERNAME=dooit-prometheus
DOOIT_MONITORING_PASSWORD_FILE=/Users/hyunseung/dooit-secrets/monitoring-password
DOOIT_GRAFANA_ADMIN_PASSWORD=<저장소 밖에서 관리하는 값>
```

기동과 점검:

```bash
docker compose --profile monitoring up -d app prometheus loki alloy grafana
docker compose --profile monitoring ps
./scripts/check-monitoring-stack.sh
```

2026-09-06 기준 위 monitoring stack 기동과 점검은 local production에서 통과했다.

local UI:

- Grafana: `http://127.0.0.1:3000`
- Prometheus: `http://127.0.0.1:9090`
- Loki: `http://127.0.0.1:3100`
- Alloy: `http://127.0.0.1:12345`

Grafana, Prometheus, Loki, Alloy는 외부 public domain에 연결하지 않는다. 원격에서 봐야 하면 Cloudflare Access 또는 동등한 인증 앞단을 별도 작업으로 구성한다.

### 로그 인코딩 깨짐

`responseBody` 로그에서 `서버 오류가 발생했습니다.`가 `ìë² ì¤ë¥...`처럼 보이면 UTF-8 바이트를 Latin-1 계열 문자셋으로 잘못 읽은 상태다. `db52d6c` 이후 app은 API response logging 전에 response character encoding을 UTF-8로 고정한다.

확인 순서:

1. production app image tag가 `db52d6c` 이후 commit인지 확인한다.
2. 수정 이전에 찍힌 오래된 Docker log인지 확인한다.
3. 새 로그도 깨지면 Docker log viewer, terminal locale, 로그 수집기의 문자셋이 UTF-8인지 확인한다.
4. 같은 request id로 app exception과 response envelope를 함께 확인하되 access token, 비밀번호, DB 비밀번호는 공유하지 않는다.

## 9. 월간 운영 점검

월 1회 또는 release 후 아래 점검을 실행한다.

```bash
./scripts/check-production-env.sh
./scripts/check-production-host.sh
./scripts/ensure-production-up.sh
./scripts/check-production-recovery.sh
./scripts/check-production-routine.sh
./scripts/report-production-status.sh
./scripts/check-monitoring-stack.sh
DOOIT_OFFSITE_BACKUP_DIR=/absolute/offsite/path ./scripts/check-production-routine.sh
DOOIT_MIN_FREE_GB=20 DOOIT_MAX_BACKUP_AGE_HOURS=30 ./scripts/check-production-routine.sh
```

점검 항목:

- production `.env` 필수값, placeholder 제거, JWT issuer/secret 형식
- 최신 backup gzip 무결성
- 최신 backup age
- backup 경로의 디스크 여유 공간
- 외부/동기화 백업 복사본과 checksum
- Docker Desktop running 상태
- app/mysql restart policy
- production LaunchAgent와 Compose stack 복구 스크립트
- 재부팅/재로그인 후 LaunchAgent 실행 이력과 readiness 복구 상태
- AC 전원 sleep/disksleep/powernap 설정
- app/mysql container running 상태
- `/actuator/health/readiness` `UP`
- monitoring stack running 상태와 Prometheus target `UP`
- 완료 보고용 backend commit, image tag, backup, readiness, routine/recovery 상태

임시 DB restore 연습은 schema 변경 release나 월간 점검 때 별도 임시 MySQL container/volume에서 수행한다. 현재 production DB를 직접 덮어쓰는 restore는 장애 복구 상황에서만 `DOOIT_CONFIRM_RESTORE=RESTORE`와 함께 실행한다.

## 10. 장애 리허설

비파괴 리허설은 아래 명령으로 실행한다.

```bash
./scripts/drill-production-incident.sh
```

확인 항목:

- readiness `UP`
- 인증 없는 `/api/v1/auth/me` 401/`11002`
- 존재하지 않는 API path 404/`10003`

DB outage 리허설은 production API가 일시적으로 내려갈 수 있으므로 점검 시간에만 명시적으로 실행한다.

```bash
DOOIT_CONFIRM_DB_OUTAGE=STOP_MYSQL ./scripts/drill-production-incident.sh
```

이 모드는 MySQL container를 중지해 readiness가 내려가는지 확인한 뒤 MySQL/app을 다시 올리고 readiness `UP`까지 대기한다. 실행 전 모바일 사용자가 없는지 확인한다. Android production build smoke는 실제 기기에서 별도로 검증한다.

## 11. 전원 정책

이 PC를 production 서버로 사용할 때 AC 전원 연결 중 시스템 절전을 허용하면 API가 내려갈 수 있다. production 정책은 AC 전원에서 system sleep, disk sleep, power nap을 끄는 것이다.

적용 명령:

```bash
DOOIT_CONFIRM_POWER_POLICY=APPLY ./scripts/apply-production-power-policy.sh
```

확인 명령:

```bash
pmset -g custom
DOOIT_STRICT_POWER=true ./scripts/check-production-host.sh
```

현재 권한 없는 shell에서는 `pmset` 변경이 실패할 수 있다. 이 경우 관리자 권한으로 위 명령을 한 번 적용한 뒤 strict check를 통과시킨다. display sleep은 API availability에 직접 영향을 주지 않으므로 별도 제품/운영 선호에 따라 조정한다.

## 개인 시스템·목표 schema readiness (2026-10-17)

새 release의 schema health는 기존 기본 테이블 10개 외에 개인 시스템 4개/목표 여정 7개 테이블과 주요 추가 컬럼 5개를 검사한다. 목록과 적용 순서는 `../db/MIGRATION_HISTORY.md`의 신규 migration 절을 따른다. 현재 연결 DB의 catalog/schema에서 정확한 테이블·컬럼 이름을 확인하므로 다른 schema의 동명 테이블은 대체하지 못한다. metadata 조회 실패나 DB namespace 확인 불가도 DOWN으로 처리하며 연결 오류 원문/credential은 health detail에 담지 않는다.

readiness가 DOWN이면 내부에서 schema component의 missingTables/missingColumns를 확인한다. 공개 health의 detail 비공개 정책은 유지한다. 테이블만 있고 직접 기록 지문/이관 mapping 컬럼이 없는 부분 적용 상태도 DOWN이다. 기본 API DTO/URL은 바뀌지 않지만 schema 누락 환경의 readiness는 이제 503이 될 수 있으므로 기존 배포 점검과 자동 복구 절차에 영향을 준다.

백업/복구 확인 후 개인 시스템(10-12), 목표 여정(10-14), 직접 기록 지문(10-15), 이관 mapping(10-16)의 실제 적용 상태를 점검하고 미적용분만 순서대로 수동 적용한다. metadata 오류만으로 DDL을 중복 실행하지 않는다. 2026-09-05 배포 성공 이력은 이 신규 schema 적용을 의미하지 않는다. unique/FK/index, type/collation과 데이터 검증은 별도 migration 점검을 계속 수행한다.

readiness 코드 보강 단계에서는 운영 DB를 바꾸지 않았다. 이후 2026-10-17 23:24 KST 명시적 승인에 따라 네 DDL 적용과 기존 image 재기동을 완료했다. 아래 적용 결과를 따른다.

## 목표 migration 읽기 전용 배포 사전 점검 (2026-10-17)

```bash
python3 -B scripts/check-goal-schema.py
```

저장소 루트에서 Docker Compose의 mysql service와 기존 컨테이너 MYSQL_USER/MYSQL_PASSWORD/MYSQL_DATABASE를 사용한다. host에서 .env를 실행하거나 credential을 인자로 출력하지 않는다. Python 3와 Docker Compose, MySQL service 및 해당 DB의 metadata 조회 권한이 필요하다. 쿼리는 information_schema SELECT만 사용하고 application row를 읽지 않는다. raw metadata/접속 오류 원문을 파일이나 보고서로 저장하지 않는다.

- 종료 0: 선행 조건과 명시한 모든 점검 통과. migration 실행 이력이나 전체 schema/data 정확성을 증명하지 않는다.
- 종료 1: MISSING 또는 PARTIAL_OR_DRIFT. 출력된 파일/객체/차이를 수동 점검한다. index의 이름만 아니라 열 순서/unique, FK 열/대상/삭제 규칙/동일 DB 참조, 지문 길이와 key collation도 비교한다.
- 종료 2: 연결/metadata 판독 실패. DB/컨테이너 가용성·접속/metadata 권한을 확인한다. 민감한 원문 오류는 출력하지 않는다. DB 조회 대기는 최대 30초다.

READY는 명시한 항목만 통과했다는 의미다. 전체 baseline column/type/default, FK update rule, 실제 데이터·중복·고아 행 등은 별도 검증해야 한다. 동등한 schema라도 다른 index/constraint 이름이면 drift로 표시한다. 권한 때문에 안 보이는 객체도 MISSING일 수 있으므로 출력만 보고 DDL을 재실행하지 않는다. 운영 파일은 자동 적용하지 않는다.

release-production.sh는 이 점검을 `./gradlew clean test bootJar`, image build, app 교체보다 먼저 실행하며 실패 시 기존 app을 그대로 둔다. 점검부터 배포까지 schema가 바뀌지 않는다는 보장은 없으므로 배포 중 schema 변경을 조율하고 기존 readiness도 계속 확인한다. 점검 우회 옵션은 제공하지 않는다.

2026-10-17 최초 읽기 전용 점검에서는 선행 4/4 통과, 10-12/14/15/16 대상 객체 전체 MISSING이었다. 이후 승인된 적용을 완료했으며 현재는 전부 READY다. 다음 실행 순서는 백업/복구 확인 → 권한/실제 객체 확인 → 미적용 DDL 순서별 수동 적용 → 사전 점검 재실행 → release/readiness다. MySQL DDL의 implicit commit 때문에 파일의 START TRANSACTION/COMMIT으로 전체 rollback을 기대하지 않는다. 부분 적용 상태는 누락 객체 단위로 검토한다.

DB 없이 fixture를 점검하려면 `python3 -B scripts/check-goal-schema.py --metadata-file <metadata.jsonl>`을 사용한다. 테스트용 JSON-lines 형식이며 운영 raw metadata를 공유할 필요는 없다. 이 옵션은 release 경로에서 사용하지 않는다. 회귀 테스트는 `./gradlew test`에 포함된 Python unittest 8개로 실행한다.


### 2026-10-17 백업·격리 복구 리허설 결과

비공개 새 백업의 gzip 무결성·덤프 완료 표식·0600 권한을 확인하고, network none 및 별도 일회용 volume의 MySQL 8.4.11에 복구했다. 원본 schema metadata 일치 후 네 DDL을 순서대로 적용했으며 전체 사전 점검이 READY였다. 임시 DB 컨테이너와 volume은 제거했다. 백업과 검증 JSON은 git 제외 backups 디렉터리에만 보존하며 dump 내용/접속 정보는 로그나 문서에 기록하지 않는다.

리허설에서 발견한 InnoDB NO ACTION/RESTRICT 동등성 오판을 수정했다. InnoDB engine을 확인한 경우에만 정규화하며 CASCADE/SET NULL·잘못된 참조는 허용하지 않는다. Python/DDL/release 파일 변경도 Gradle test 입력에 포함해 검사 결과를 잘못 재사용하지 않도록 했다.

### 2026-10-17 23:24 KST 운영 DDL 적용·기존 image 재기동

- launchd 자동 복구를 잠시 중지하고 app을 내린 뒤 최종 백업을 만들었다. gzip 무결성·덤프 완료 표식과 비공개 보관을 확인했다.
- 10-12 → 10-14 → 10-15 → 10-16 네 파일을 순서대로 적용하고 각 단계 및 최종 사전 점검 READY를 확인했다. 기존 테이블 행 수는 DDL 전후 동일했다.
- 기존 app 컨테이너를 다시 시작하고 launchd를 복원했다. 전후 image digest는 같고 운영 image tag는 3bda00c다. 새 backend image 배포와 사용자 데이터 이관 API는 실행하지 않았다.
- 로컬/공개 HTTPS curl의 readiness는 200/UP, metadata는 200, 미인증 auth/me는 401이었다. 공개 Web origin CORS는 200과 allow-origin 일치로 확인했다. Python urllib의 공개 요청은 403이었으나 curl 검증은 통과했다. 인증 후 기능 smoke는 포함하지 않았다.
- 백업·검증 JSON과 SHA-256은 git 제외 backups 디렉터리에만 남겼다. 운영 DDL을 재실행하지 않는다. 상세 파일/결과는 MIGRATION_HISTORY.md의 23:24 적용 기록을 따른다.
- `./gradlew test bootJar`가 성공했다. 테스트는 이전 성공 결과를 재사용했고 다음 배포용 `build/libs/Dooit-1.0-SNAPSHOT.jar`를 생성했다. 이 단계에서는 기존 image를 유지했으며, 이후 2026-10-18 아래의 새 image 배포와 목표 API smoke를 완료했다.

### 2026-10-17 후보 image 6cdc9e5 검증

- 운영 적용 기록을 commit `6cdc9e5`로 남긴 뒤 `./gradlew test bootJar` 전체 테스트 618개를 재실행해 통과했다. `DOOIT_APP_COMMIT_SHA=<full commit> DOOIT_APP_IMAGE_TAG=6cdc9e5 docker compose build app`로 후보 이미지를 만들었다. app 교체는 수행하지 않았다.
- 별도 internal Docker network/임시 volume에 MySQL 8.4.11을 띄우고 운영 DB에서 schema만 복제했다. 운영 데이터 행이나 credential은 복사하지 않았다. 별도 임시 JWT secret과 production profile로 후보를 기동해 Hibernate validate 및 readiness UP을 확인했다. host 포트 공개 없이 컨테이너 내부 curl로 점검했다.
- 임시 게스트 계정으로 목표/계획 생성, 날짜별 회차 생성, 최소 실천, 직접 기록과 동일 key 재시도, 여정 집계, 다른 계정의 404, 네 AI API 기본 비활성 503을 검증했다. 테스트 직접 기록을 취소하고 목표를 보관한 뒤 임시 컨테이너·DB volume·network를 제거했다. 모든 fixture는 임시 DB와 함께 제거됐으며 운영 smoke 계정은 생성하지 않았다. 사용자 개인 시스템 이관 API는 호출하지 않았다.
- 비공개 `backups/candidate-validation-6cdc9e5.json`에 검증 결과를 보존한다. 이 파일의 fixture 보관 설명은 격리 DB 안의 API 처리 상태이며, 최종 컨테이너/volume 제거 이후 fixture가 운영에 남았다는 뜻은 아니다.
- 후보 검증은 일회성 격리 환경에서 수행했으며 상시 STG는 구축하지 않았다. 이후 운영 image 교체 결과는 아래 배포 완료 기록을 따른다.


### 2026-10-18 운영 image 6cdc9e5 배포 완료

사용자가 운영 image 교체·자동 복구 일시정지/복원·실패 시 롤백·테스트 계정 API smoke를 명시적으로 승인한 뒤 `3bda00c`에서 `6cdc9e5`로 app만 교체했다. DB DDL은 재실행하지 않았다. 새 image readiness가 통과해 롤백은 실행하지 않았다. 이전 image는 보존했다.

공개 HTTPS에서 테스트 게스트로 목표/계획 생성, 회차 생성·MINIMUM 실천, 직접 기록 같은 key 재시도와 동일 ID, 여정 집계, 다른 owner 접근 404, 네 AI API 비활성 503을 확인했다. 테스트 목표는 보관하고 직접 기록은 취소했지만 두 게스트와 실천 이력은 남아 있다. 기존 사용자 데이터나 개인 시스템 이관 API는 건드리지 않았다. Android 실제 기기/실제 사용자 이관 검증은 별도다.

배포 후 launchd 복구 스크립트가 commit env를 전달하지 않아 metadata를 local로 덮고 같은 image를 재생성하는 문제를 발견했다. `ensure-production-up.sh`가 같은 running image의 기존 commit을 보존하고, 누락/local이면 image tag를 git commit으로 해석하도록 보정했다. explicit image가 다르면 이전 컨테이너 commit을 상속하지 않고 explicit commit이 있으면 우선한다. git에서 찾을 수 없는 custom tag는 tag 자체를 fallback으로 사용한다. Docker inspect env는 commit 필드만 추출하고 다른 env는 출력하지 않는다.

host 복구 스크립트 보정 후 같은 `6cdc9e5` image에 full commit SHA를 전달해 재기동했다. 반복 복구 실행에서 컨테이너 ID가 유지됐고 로컬/공개 readiness UP, metadata의 tag/full SHA 일치, CORS 및 launchd 로드 상태를 재확인했다. 이는 host 스크립트 수정이며 app image를 다시 빌드한 것은 아니다. 회귀 테스트 네 개를 포함해 전체 622개가 통과했다.

최종 비공개 보고서는 `backups/production-release-6cdc9e5-20261018-011714.json`이며 token/credential은 저장하지 않았다. 이번 배포의 API 변경은 기존 계약에 새 목표 API·nullable 연결 필드·AI 오류 응답을 추가한 것이며, 구 personal system의 60004 동작은 명시적 이관을 선택한 경우에만 적용된다. 클라이언트는 기존 예약 정리와 새 ID/API 전환을 이관 계약에 따라 처리한다. AI 외부 adapter는 여전히 없으며 다음 작업은 모바일 실제 기기 연동 확인과 provider·보관·삭제 정책 확정이다.

## 게스트 schema와 배포 점검

게스트 컬럼 migration `../db/migrations/20260809_add_guest_account_columns.sql`은 이미 적용됐다. 적용 이력은 [DB 이력](../db/MIGRATION_HISTORY.md), 실제 컬럼/nullable/index 정의는 SQL을 원본으로 본다. 재구축 또는 drift 복구 때는 APP_USER의 ACCOUNT_TYPE/MERGED_INTO_USER_ID/MERGED_AT/LAST_ACTIVE_AT/GUEST_EXPIRES_AT와 IDX_APP_USER_ACCOUNT_TYPE_EXPIRES를 확인하고 누락된 변경만 검토한다. 적용된 파일을 통째로 재실행하지 않는다.

게스트 row가 있으면 EMAIL/PASSWORD_HASH/DISPLAY_NAME을 NOT NULL로 되돌릴 수 없으므로 단순 역방향 DDL을 rollback으로 사용하지 않는다. 백업/복구와 데이터 처리 계획을 먼저 확인한다. 배포는 이 문서의 공통 release 절차를 따르고, 게스트 발급·갱신·승격·병합 검증은 [모바일 연동 runbook](../mobile/MOBILE_INTEGRATION_RUNBOOK.md#게스트-발급갱신승격병합)을 따른다. 완료 보고에는 commit/image, migration 적용 여부, readiness, 테스트 범위, 테스트 데이터 잔존 여부를 적는다.

## PRD/STG 프로젝트 분리 (2026-10-18)

기존 운영 image/볼륨을 보존해 프로젝트를 prd_dooit로 전환했고, 별도 STG를 localhost:18080에 기동했다. 현재 `.env`의 DOOIT_COMPOSE_PROJECT_NAME이 PRD 그룹을 선택한다. 전환 백업·검증과 STG 명령은 [PRD/STG 운영](DOCKER_ENVIRONMENTS.md)을 따른다. 이 전환은 PRD에 빠른 기록 새 기능을 배포한 것이 아니다.
