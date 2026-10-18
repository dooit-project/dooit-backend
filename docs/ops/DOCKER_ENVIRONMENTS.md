# PRD / STG Docker 운영

Last updated: 2026-10-18

이 Mac에서 PRD와 STG를 별도 Compose 프로젝트로 실행한다. Compose 프로젝트 이름은 소문자만 허용하므로 Docker Desktop 그룹명은 `prd_dooit`, `stg_dooit`다.

| 항목 | PRD | STG |
| --- | --- | --- |
| Compose 파일 | docker-compose.yml | docker-compose.stg.yml |
| 프로젝트 | prd_dooit | stg_dooit |
| 비공개 설정 | .env | .env.stg |
| 앱 URL | http://127.0.0.1:8080 / 기존 공개 HTTPS | http://localhost:18080 |
| 앱 이미지 | 기존 운영 release tag | stg-commit-빌드시각 |
| DB | 기존 dooit-mysql-data 볼륨 | stg_dooit_mysql-data 새 볼륨 |
| DB 계정·JWT 키 | 기존 PRD 값 유지 | init에서 별도 생성 |
| 로그·네트워크 | 기존 PRD 볼륨 / prd_dooit_default | 전용 볼륨 / stg_dooit_default |
| 문서 UI | 기본 비공개 | loopback에서 공개 |
| 실제 push·메일 배치 | 기존 운영 설정 | 비활성 |

STG는 운영 데이터/계정을 복사하지 않은 검증용 환경이다. DB와 JWT가 다르므로 PRD 토큰/계정을 그대로 사용하지 않는다. STG app은 PRD와 같은 prod Spring profile 및 Hibernate validate로 실행한다. 그룹 분리는 같은 Mac의 자원/장애까지 격리하는 것은 아니다. STG 앱/DB 메모리 상한은 각각 768MiB다.

STG는 우선 이 Mac의 loopback에만 열었다. 외부 도메인·실기기 접근은 아직 연결하지 않았다. Android Emulator에서는 호스트 접근 주소 `http://10.0.2.2:18080`을 사용하고 실제 기기는 별도 접근 경로를 정해야 한다. STG의 JWT issuer는 현재 `http://localhost:18080`이며 외부 공개 시 Compose issuer와 CORS 정책도 함께 검토한다.

## STG 기동과 업데이트

```bash
./scripts/staging.sh init
./scripts/staging.sh build
./scripts/staging.sh up
./scripts/staging.sh ps
python3 -B scripts/smoke-staging.py
```

- init은 저장소에 커밋하지 않는 `.env.stg`를 0600으로 만들고, 기존 파일이 있으면 키를 바꾸지 않는다. Docker build context에서도 env/backup/log 파일을 제외한다.
- build는 test/bootJar 후 STG 전용 tag를 빌드한다. 미커밋 코드가 있으면 metadata commit에 `-dirty`를 붙인다. PRD 배포 가능한 확정 커밋이라는 뜻이 아니다.
- up은 빌드된 STG image만 사용하며 readiness를 기다린다. 운영 `.env`는 읽지 않고 STG 파일·프로젝트를 명시한다.
- ps/logs/config로 상태·최근 로그·설정 유효성을 확인한다. config는 비밀이 포함된 전체 설정을 출력하지 않는다.
- stop/down은 STG만 대상으로 하며 down도 데이터 볼륨은 지우지 않는다. 데이터 삭제용 `down -v`는 제공하지 않는다.
- 초기 빈 DB는 최신 `src/main/resources/schema.sql`을 실행한다. 이미 존재하는 STG DB에는 init SQL이 재실행되지 않으므로 후속 schema 변경은 STG에서 필요한 migration만 적용한다.
- smoke-staging.py는 대상 주소를 localhost:18080으로 고정한다. 합성 게스트로 빠른 기록 수용 예시를 확인한 뒤 만든 Task를 삭제한다. 게스트와 멱등성 기록은 STG에 남는다.

Swagger: `http://localhost:18080/swagger-ui`, OpenAPI: `http://localhost:18080/v3/api-docs`.

## PRD 운영

기존 release/rollback/backup/recovery 스크립트를 그대로 사용한다. `.env`의 `DOOIT_COMPOSE_PROJECT_NAME=prd_dooit`가 프로젝트를 선택하며 STG wrapper는 이 설정을 사용하지 않는다. 고정 PRD 컨테이너명 dooit-app/mysql 및 모니터링 이름과 기존 볼륨 이름은 보존해 점검·복구 스크립트의 참조를 유지한다. 기존 볼륨은 external로 명시해 구 프로젝트 소유 label과 충돌하지 않고 Compose down으로 삭제되지 않게 한다. 신규 설치 시 운영 runbook의 볼륨 생성 절차를 먼저 실행한다.

```bash
docker compose --profile monitoring ps
./scripts/check-production-recovery.sh
./scripts/check-monitoring-stack.sh
```

Alloy의 프로젝트 필터도 같은 환경변수를 받아 PRD 로그만 수집한다. STG 로그는 STG 전용 볼륨과 Docker logs로 확인한다. PRD 이미지와 STG 이미지를 자동 승격하거나 서로의 DB migration을 자동 적용하지 않는다.

## 2026-10-18 분리 적용 기록

- 기존 dooit 프로젝트의 app image `6cdc9e5`, 모든 서비스 image ID, 환경 값과 named volume을 사전 대조했다. Alloy의 프로젝트 필터 전달 외에는 환경 차이가 없었다.
- backend launchd 자동 복구를 일시 중지하고 app 쓰기를 멈춘 상태에서 MySQL 백업을 생성했다. 덤프 완료 표식과 gzip 왕복 검증 후 볼륨 삭제 없이 기존 컨테이너를 내렸다.
- 동일 이미지/볼륨으로 prd_dooit 프로젝트를 기동하고 healthy 및 이미지 ID/볼륨 일치를 확인한 뒤 launchd를 다시 등록했다. PRD DB DDL과 새 백엔드 image 배포는 하지 않았다.
- 비공개 복구 자료는 git 제외 `backups/project-split-20261018-123614/`에 0700 디렉터리/0600 파일로 보관했다. DB 백업, 이전 env, rollback Compose 설정에는 운영 비밀/데이터가 있으므로 공유하지 않는다.
- PRD/STG readiness UP, PRD 자동 복구 반복 실행 시 app ID 유지, Prometheus target UP 및 Loki/Grafana 점검을 확인했다. 재기동 직후 Loki 준비 과정의 503은 초기화 완료 후 해소됐다.
- 최종 STG image는 `stg-a27fcd2-20261018124000`이며 미커밋 작업본이다. 전체 백엔드 테스트 648개와 실제 실행 환경의 비밀 값/네트워크/볼륨 분리 검증을 통과했다.
- STG는 새 빈 MySQL에 초기 schema와 누락됐던 10-15/10-16 변경을 적용했다. 이후 초기 schema도 보완하고 별도 일회성 STG DB에서 전체 SQL 실행·최신 컬럼·key collation을 검증한 후 일회성 DB를 삭제했다.
- STG 실 API에서 TODO 계획일/Today 분리, IDEA 문구 보존, SCHEDULE 시각, 실제 주간 반복 저장, 기존 자동 해석, 미지원 입력 오류와 멱등 재시도를 통과했다. 생성 Task는 삭제했다.

일회성 전환 도구 `python3 -B scripts/split-production-project.py`는 기본 읽기 전용이다. 최초 적용 때만 `--apply`를 사용했으며 실패 시 원래 dooit 그룹으로 복구하도록 구성했다. 이미 prd_dooit이면 변경하지 않는다. 운영 볼륨과 현재 설정을 확인하지 않은 다른 PC에 이 도구를 그대로 적용하지 않는다.

## 남은 일

- STG 외부 도메인/실기기 접근 경로 확정.
- STG에서 제품 정책과 모바일 연동 확인 후 커밋된 release를 별도로 PRD에 배포.
- host 전원 정책·재부팅 후 복구·offsite backup은 [로드맵](../project/ROADMAP.md) 기준으로 계속 검증.
