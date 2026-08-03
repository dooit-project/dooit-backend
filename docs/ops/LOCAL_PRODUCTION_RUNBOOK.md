# Local PC Production Runbook

Last updated: 2026-08-03

이 문서는 이 Mac의 Docker Compose를 ToDoLab의 단일 production 서버로 사용하고 Android APK에서 Tailscale HTTPS로 접근하는 절차다.

## 1. 사전 준비

- Docker Desktop을 설치하고 로그인 시 자동 시작을 켠다.
- Mac과 Android에 Tailscale을 설치하고 같은 tailnet에 로그인한다.
- 저장소의 `.env.example`을 `.env`로 복사한 뒤 실제 비밀번호와 JWT 값을 저장소 밖에서 관리한다.
- JWT secret은 `openssl rand -base64 48`로 생성한다.
- 최초 1회 external DB volume을 만든다.

```bash
docker volume create todolab-mysql-data
```

`.env`와 백업 파일은 Git에 커밋하지 않는다.

기존 `.env`에 production JWT 값이 아직 없다면 Tailscale HTTPS origin으로 한 번만 초기화한다. 스크립트는 기존 값을 덮어쓰지 않으며 생성한 secret을 화면에 출력하지 않는다.

```bash
./scripts/configure-production-env.sh https://<device>.<tailnet>.ts.net
```

## 2. build와 기동

```bash
./gradlew clean test bootJar
docker compose config
docker compose up -d --build
docker compose ps
curl --fail http://127.0.0.1:8080/actuator/health/readiness
```

MySQL port는 host에 공개하지 않는다. app port도 `127.0.0.1:8080`에만 공개하고 Tailscale Serve가 HTTPS 진입점이 된다.

## 3. Tailscale HTTPS

Tailscale의 HTTPS와 MagicDNS를 활성화한 뒤 Mac에서 다음과 같이 local app을 공개한다.

```bash
tailscale serve --bg http://127.0.0.1:8080
tailscale serve status
```

명령이 출력한 `https://<device>.<tailnet>.ts.net` 주소를 모바일 `EXPO_PUBLIC_API_URL`로 사용한다. 공유기 포트포워딩은 사용하지 않는다.

확인 순서:

1. Android Tailscale을 켠다.
2. Android 브라우저에서 `https://<device>.<tailnet>.ts.net/actuator/health/readiness`를 연다.
3. APK에서 로그인, Today 조회·생성·완료를 확인한다.
4. Wi-Fi를 끄고 모바일 데이터에서도 같은 주소로 확인한다.

## 4. 백업

기본 백업 위치는 저장소의 `backups/`, 기본 보관 기간은 14일이다. PC 자체 디스크 장애에 대비해 이 폴더를 별도 디스크나 신뢰할 수 있는 동기화 위치에 추가 복사한다.

```bash
./scripts/backup-db.sh
TODOLAB_BACKUP_DIR=/absolute/backup/path TODOLAB_BACKUP_RETENTION_DAYS=30 ./scripts/backup-db.sh
```

릴리스 전과 최소 하루 1회 실행한다. macOS launchd 등 host scheduler 연결은 백업 명령을 수동으로 검증한 뒤 구성한다.

## 5. 복구 연습

복구는 현재 DB 내용을 교체하므로 먼저 app을 중단한다.

```bash
docker compose stop app
TODOLAB_CONFIRM_RESTORE=RESTORE ./scripts/restore-db.sh backups/todolab-YYYYMMDD-HHMMSS.sql.gz
docker compose up -d app
curl --fail http://127.0.0.1:8080/actuator/health/readiness
```

복구 후 Android에서 로그인, Today, Calendar의 기존 데이터가 보이는지 확인한다. production 데이터에 처음 시도하지 말고 별도 임시 Compose project/volume에서 최소 1회 복구를 검증한다.

## 6. 업데이트와 rollback

업데이트 전 순서:

1. `./scripts/backup-db.sh`
2. migration SQL과 API 호환성 확인
3. `./gradlew clean test bootJar`
4. `docker compose up -d --build app`
5. readiness와 Android smoke test

현재 schema 변경은 자동 migration 도구로 추적되지 않는다. 기존 volume에는 `docker-entrypoint-initdb.d`의 `schema.sql`이 다시 적용되지 않으므로, migration SQL 적용과 백업을 release의 필수 수동 단계로 유지한다. Flyway 도입 전에는 schema 변경이 있는 release를 자동 배포하지 않는다.

기존 production DB가 현재 schema보다 오래된 경우에는 app 업데이트 전에 백업 후 필요한 migration SQL을 수동 적용한다.

```bash
./scripts/backup-db.sh
docker compose stop app
docker compose exec -T mysql sh -c 'exec mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' < docs/db/migrations/20260803_prepare_local_production.sql
docker compose up -d app
curl --fail http://127.0.0.1:8080/actuator/health/readiness
```

`docs/db/migrations/20260803_prepare_local_production.sql`은 local production으로 전환하기 전 legacy DB를 현재 app schema에 맞추는 일회성 수동 migration이다. 이미 같은 table, column, index, constraint가 적용된 DB에는 다시 실행하지 않는다.

## 7. 장애 확인 순서

```bash
docker compose ps
docker compose logs --tail=200 app
docker compose logs --tail=200 mysql
curl --fail http://127.0.0.1:8080/actuator/health/readiness
tailscale status
tailscale serve status
```

- local readiness 실패: app/MySQL/schema 상태를 확인한다.
- local readiness 성공, Android 실패: Android Tailscale 연결과 production API URL을 확인한다.
- PC 절전·종료 중에는 앱을 사용할 수 없다. 상시 사용하려면 전원 연결 시 절전 정책을 별도로 정한다.
