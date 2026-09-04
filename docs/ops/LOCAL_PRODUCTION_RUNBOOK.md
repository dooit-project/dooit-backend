# Local PC Production Runbook

Last updated: 2026-09-04

이 문서는 이 Mac의 Docker Compose를 Dooit의 단일 production 서버로 사용하고 실제 HTTPS 도메인으로 Android/Web이 접근하는 절차다.

## 1. 사전 준비

- Docker Desktop을 설치하고 로그인 시 자동 시작을 켠다.
- production DB image는 MySQL 8.4 LTS의 검증된 patch release인 `mysql:8.4.11`로 고정한다.
- 저장소의 `.env.example`을 `.env`로 복사한 뒤 실제 비밀번호와 JWT 값을 저장소 밖에서 관리한다.
- JWT secret은 `openssl rand -base64 48`로 생성한다.
- 최초 1회 external DB volume을 만든다.

```bash
docker volume create dooit-mysql-data
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

### 로그 인코딩 깨짐

`responseBody` 로그에서 `서버 오류가 발생했습니다.`가 `ìë² ì¤ë¥...`처럼 보이면 UTF-8 바이트를 Latin-1 계열 문자셋으로 잘못 읽은 상태다. `db52d6c` 이후 app은 API response logging 전에 response character encoding을 UTF-8로 고정한다.

확인 순서:

1. production app image tag가 `db52d6c` 이후 commit인지 확인한다.
2. 수정 이전에 찍힌 오래된 Docker log인지 확인한다.
3. 새 로그도 깨지면 Docker log viewer, terminal locale, 로그 수집기의 문자셋이 UTF-8인지 확인한다.
4. 같은 request id로 app exception과 response envelope를 함께 확인하되 access token, 비밀번호, DB 비밀번호는 공유하지 않는다.

## 8. 월간 운영 점검

월 1회 또는 release 후 아래 점검을 실행한다.

```bash
./scripts/check-production-env.sh
./scripts/check-production-host.sh
./scripts/ensure-production-up.sh
./scripts/check-production-recovery.sh
./scripts/check-production-routine.sh
./scripts/report-production-status.sh
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
- 완료 보고용 backend commit, image tag, backup, readiness, routine/recovery 상태

임시 DB restore 연습은 schema 변경 release나 월간 점검 때 별도 임시 MySQL container/volume에서 수행한다. 현재 production DB를 직접 덮어쓰는 restore는 장애 복구 상황에서만 `DOOIT_CONFIRM_RESTORE=RESTORE`와 함께 실행한다.

## 9. 장애 리허설

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

## 10. 전원 정책

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
