# Monitoring Runbook

Last updated: 2026-10-30

이 문서는 Dooit local production에 Prometheus, Grafana, Loki, Grafana Alloy 기반 모니터링을 붙이는 구성과 운영 절차를 정리한다. 실제 secret, 관리자 비밀번호, public/private 관리 URL은 문서에 기록하지 않는다.

## 1. 목표

- production API의 JVM, HTTP, DB connection, scheduler 상태를 Prometheus metric으로 수집한다.
- app/mysql/container 로그를 Loki에 보관하고 Grafana Explore에서 request id 기준으로 확인한다.
- Grafana는 host loopback에만 열어 운영자가 로컬에서 확인한다.
- `/actuator/health/**`는 기존 public readiness smoke 용도로 유지하되, `/actuator/prometheus`는 외부 공개 도메인에 노출하지 않는다.
- 대시보드와 datasource는 수동 클릭 설정이 아니라 provisioning 파일로 재현 가능하게 둔다.

## 2. 운영 구성

| 구성요소 | 역할 | 1차 배치 |
| --- | --- | --- |
| Spring Boot Actuator | health와 metric endpoint 제공 | app container |
| Micrometer Prometheus registry | `/actuator/prometheus` scrape 형식 제공 | app dependency |
| Prometheus | app metric scrape와 단기 보관 | Compose service, `127.0.0.1:9090` |
| Loki | 로그 저장과 LogQL 조회 | Compose service, `127.0.0.1:3100` |
| Grafana Alloy | Docker socket으로 container stdout/stderr 수집 후 Loki 전송 | Compose service |
| Grafana | Prometheus/Loki datasource와 dashboard UI | Compose service, `127.0.0.1:3000` |

1차 운영에서는 Grafana, Prometheus, Loki, Alloy를 외부 공개하지 않는다. Cloudflare Tunnel에 management UI를 연결해야 할 때는 Cloudflare Access 또는 동등한 인증을 먼저 붙인 뒤 별도 작업으로 진행한다.

## 현재 상태

2026-09-06 기준 monitoring password file과 Grafana admin password를 local production `.env`에 적용했고, `docker compose --profile monitoring up -d app prometheus loki alloy grafana`와 `./scripts/check-monitoring-stack.sh`가 통과했다. Grafana, Prometheus, Loki, Alloy는 host loopback에만 bind되어 있다.

### 실제 데이터 흐름과 보관

```text
Spring Logback → stdout/stderr → Docker json-file → Alloy → Loki → Grafana (LogQL)
Spring Actuator + Micrometer → /actuator/prometheus ← Prometheus (15초 주기) → Grafana (PromQL)
```

- 로그는 사건별 텍스트이고 메트릭은 요청 횟수·지연시간·메모리 등의 시계열 수치다. Grafana가 두 저장소를 각각 조회한다. 로그를 변환해 현재 JVM/HTTP 메트릭을 만드는 구조는 아니다.
- Alloy는 `DOOIT_COMPOSE_PROJECT_NAME`에 해당하는 PRD 컨테이너만 수집한다. `compose_project`, `compose_service`, `container`, `env`를 라벨로 붙인다. STG는 별도 Docker logs로 확인한다.
- requestId는 로그 본문/MDC에 있다. 현재 Alloy에는 requestId 파싱이나 stack trace multiline 결합 단계가 없으며, Grafana에서 문자열로 검색한다.
- Loki 로그 보관 설정은 168시간(7일), Prometheus 메트릭 보관 기본값은 15일이다.
- Docker json-file은 컨테이너별 10MB × 5개로 회전한다. production Logback 파일은 `/app/logs` 볼륨에도 별도로 기록하며 각 rolling policy는 30일/1GB 상한이다. 이 파일은 현재 Alloy 수집 원본이 아니다.
- Grafana datasource와 8개 패널은 `config/monitoring/grafana`의 provisioning/JSON 파일로 관리한다. 로컬 접속 주소는 `http://127.0.0.1:3000`이다.

### 2026-10-30 배포 전 읽기 전용 점검

- `check-monitoring-stack.sh` 통과, Prometheus `up{job="dooit-backend"}=1` 확인.
- 점검용 metadata 읽기 요청에 requestId를 부여하고 Loki에서 해당 문자열의 로그 유입을 확인했다. 초기 빈 조회만으로 수집 장애라고 판단하지 않고 실제 요청→수집까지 검증했다.
- HTTP count 메트릭은 존재하지만 `http_server_requests_seconds_bucket`은 없었다. p95는 histogram 설정을 추가하고 앱 배포 후 bucket과 차트를 검증해야 한다.
- 이 스크립트는 서비스 endpoint/target 상태를 확인한다. Grafana datasource 인증 health나 실제 Loki 로그 유입까지 자동 검증하지는 않는다.
- 이번에는 PRD 앱·모니터링 설정을 변경하지 않았다. 알림 연락처/규칙도 아직 설정하지 않았다.

### p95 수집 보완과 배포 전 STG 검증 (2026-10-30)

- 공통 `application.yml`에 `management.metrics.distribution.percentiles-histogram.http.server.requests=true`를 추가했다. local/test/prod에서 같은 분포 설정을 사용하며 metric endpoint의 기존 인증·활성화 정책은 유지한다.
- HTTP 요청별 시간 구간의 누적 bucket을 내보내고 Prometheus에서 `histogram_quantile`로 p95를 추정한다. [Spring Boot 4.0 metrics 문서](https://docs.spring.io/spring-boot/4.0/reference/actuator/metrics.html)를 기준으로 구성했다.
- 대시보드 요청량·p95의 쿼리를 backend job의 `/api/**`로 한정했다. route template을 사용하는 `uri` tag로 집계하며 requestId나 사용자 ID 라벨을 추가하지 않는다.
- HTTP metadata 요청 후 인증된 Prometheus endpoint에서 유한 bucket과 `+Inf` bucket의 실제 증가를 통합 테스트로 검증한다.
- STG image `stg-4a14f3b-20261030000822`에서 일회성 모니터링 인증을 활성화해 69개 bucket series를 확인했다. STG 네트워크의 임시 Prometheus에서 대시보드 원본 쿼리로 요청량·p95가 유한한 양수를 반환하는 것을 검증했다. 소량 합성 요청 결과이며 운영 성능 수치로 해석하지 않는다.
- 임시 Prometheus는 삭제했고 STG 모니터링은 비활성으로 복구했다. PRD 앱 image는 변경하지 않았다. 대시보드 JSON은 기존 provisioning bind mount에 따라 갱신될 수 있지만 PRD histogram 수집은 새 앱 배포가 필요하다.
- histogram은 요청 tag 조합마다 시계열을 늘린다. 배포 뒤 Prometheus series·메모리 증가를 확인한다. p95는 최근 5분 요청과 최소 두 번의 scrape가 필요하며, 요청이 없는 구간의 no data/NaN을 0ms로 치환하지 않는다.

배포 후 확인할 쿼리:

```promql
count(http_server_requests_seconds_bucket{job="dooit-backend",uri=~"/api/.*"})
histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{job="dooit-backend",uri=~"/api/.*"}[5m])) by (le))
```

## 3. 네트워크와 보안 원칙

- app API public endpoint는 기존처럼 Cloudflare Tunnel을 통해 `https://dooitapi.hsng.pe.kr`로만 공개한다.
- Grafana, Prometheus, Loki, Alloy UI는 host loopback bind만 허용한다.
- Prometheus scrape 대상은 Docker Compose 내부 DNS `app:8080`을 사용한다.
- `/actuator/prometheus`는 HTTP Basic 인증을 요구한다.
- Prometheus password는 저장소 밖 password file을 app과 Prometheus container에 함께 mount한다. `.env` 값은 host 파일 경로이고, app 내부 환경변수는 `/run/secrets/dooit-monitoring-password`로 고정한다.
- Spring Security는 health만 public permit을 유지하고, prometheus endpoint는 monitoring credential로 분리한다.
- Grafana admin password는 `.env` 또는 Docker secret에 두고 저장소에 커밋하지 않는다.
- app payload logging은 production 기본값 `DOOIT_API_LOGGING_PAYLOAD_ENABLED=false`를 유지한다.

## 4. 구현 작업

### B0. Spring metric endpoint

- [x] `build.gradle`에 `io.micrometer:micrometer-registry-prometheus`를 추가한다.
- [x] `application.yml`의 Actuator exposure에 `prometheus`를 추가하고 production 보안 정책을 함께 반영한다.
- [x] `application-prod.yml`에서 Prometheus 인증 여부를 `DOOIT_MONITORING_ENABLED`로 제어한다.
- [x] `SecurityConfig`에서 `/actuator/prometheus` 접근 정책을 health와 분리한다.
- [x] OpenAPI 문서 공개 정책과 달리 metric endpoint는 API 계약 문서에 포함하지 않는다.

검증:

```bash
./gradlew test
curl --fail --user "$DOOIT_MONITORING_USERNAME:$(cat "$DOOIT_MONITORING_PASSWORD_FILE")" \
  http://127.0.0.1:8080/actuator/prometheus
```

### B1. Compose monitoring stack

- [x] `docker-compose.yml`에 `prometheus`, `loki`, `alloy`, `grafana` service를 추가한다.
- [x] Prometheus data volume을 추가하고 retention 기간을 기본 15일로 제한한다.
- [x] Loki data volume을 추가하고 local filesystem 저장소를 사용한다.
- [x] Grafana data volume을 추가한다.
- [x] Grafana, Prometheus, Loki, Alloy UI port는 `127.0.0.1`에만 bind한다.
- [x] monitoring service는 `monitoring` Compose profile로 분리한다.

검증:

```bash
docker compose config --quiet
docker compose --profile monitoring up -d app prometheus loki alloy grafana
docker compose ps
```

### B2. Prometheus scrape config

- [x] `config/monitoring/prometheus/prometheus.yml`을 추가한다.
- [x] scrape target은 `app:8080`, metrics path는 `/actuator/prometheus`로 둔다.
- [x] scrape interval은 15초로 시작한다.
- [x] Prometheus 자체 metric도 함께 scrape한다.

핵심 확인 metric:

- `up{job="dooit-backend"}`
- `http_server_requests_seconds_count`
- `jvm_memory_used_bytes`
- `hikaricp_connections_active`
- `process_uptime_seconds`

### B3. Loki와 Alloy log pipeline

- [x] 새 구성에서는 Promtail 대신 Grafana Alloy를 사용한다.
- [x] 1차는 Docker socket 기반 container log 수집으로 시작한다.
- [x] label은 `compose_project`, `compose_service`, `container`, `env` 정도만 둔다.
- [x] app log의 `requestId`는 line parsing으로 추출할 수 있으면 label이 아니라 parsed field로 유지한다.
- [ ] noisy health check 로그가 과도하면 Alloy process stage에서 drop 여부를 결정한다.

기본 조회:

```logql
{compose_service="app"}
{compose_service="mysql"}
{compose_service="app"} |= "ERROR"
{compose_service="app"} |= "requestId"
```

### B4. Grafana provisioning

- [x] `config/monitoring/grafana/provisioning/datasources`에 Prometheus와 Loki datasource를 추가한다.
- [x] Prometheus URL은 `http://prometheus:9090`, Loki URL은 `http://loki:3100`을 사용한다.
- [x] dashboard provisioning 디렉터리를 추가한다.
- [x] 1차 dashboard는 backend scrape 상태/HTTP/JVM/DB/오류 로그 중심으로 만든다.
- [x] Grafana anonymous access는 production 기본 비활성으로 둔다.

현재 `Dooit Production Overview`의 실제 패널은 8개다.

- Backend Up: Prometheus scrape 성공 여부. readiness나 Docker health와는 다르다.
- API Request Rate: `job="dooit-backend"`, `uri=~"/api/.*"`의 초당 요청 증가율. Actuator·문서 UI 요청은 제외한다.
- API Latency p95: 같은 API 범위의 histogram bucket을 합산해 계산한다. 2026-10-30 PRD 1d719d3 배포 후 bucket과 실제 p95 계산을 확인했다.
- JVM Memory Used: heap/non-heap 메모리 사용량.
- HikariCP Connections: active/idle/pending 연결 수.
- API 5xx Error Ratio: 최근 5분 API 요청 중 5xx 비율. 무요청은 no data/NaN으로 둔다.
- API 5xx Count (5m): 최근 5분 5xx 증가량. 경계 보간으로 소수가 나올 수 있다.
- Recent App Errors: Loki app 로그 중 `ERROR` 문자열 검색.

GC pause, CPU, uptime, container restart/health 패널은 아직 없다. 특히 Docker 상태를 수집할 exporter도 현재 구성에 없다.

### B5. 운영 스크립트와 문서

- [x] `scripts/check-monitoring-stack.sh`를 추가해 Prometheus, Loki, Grafana datasource health를 점검한다.
- [x] `scripts/report-production-status.sh`에 monitoring endpoint 요약을 포함한다.
- [x] `docs/ops/LOCAL_PRODUCTION_RUNBOOK.md`에 monitoring 기동과 장애 확인 순서를 추가한다.
- [x] `docs/project/ROADMAP.md`에서 기본 monitoring 구축 완료와 후속 alerting 작업을 구분한다.

## 5. 운영 확인 순서

```bash
docker compose ps
curl --fail http://127.0.0.1:8080/actuator/health/readiness
curl --fail http://127.0.0.1:9090/-/ready
curl --fail http://127.0.0.1:3100/ready
curl --fail http://127.0.0.1:3000/api/health
./scripts/check-monitoring-stack.sh
```

Grafana에서 확인한다.

- Prometheus datasource Save & test 통과
- Loki datasource Save & test 통과
- `up{job="dooit-backend"}`가 `1`
- `{compose_service="app"}` 로그가 최신 시각까지 유입
- app ERROR 로그가 request id와 함께 조회

## 6. 장애 규칙과 수신처

2026-10-30 Grafana 파일 provisioning으로 두 규칙을 활성화했다. 1분마다 평가한다.

| 규칙 | 조건 | 유지 시간 |
| --- | --- | --- |
| Backend metrics unavailable | backend up=0 또는 target 부재 | 2분 |
| API high 5xx | 최근 5분 API 5xx 비율 5% 초과 및 추정 5xx 건수 5건 이상 | 5분 |

- 수집 중단은 readiness 장애와 동일하지 않다. 앱·인증·Prometheus 경로를 함께 확인한다. 호스트가 꺼지면 같은 호스트의 Grafana도 멈추므로 외부 감시가 별도로 필요하다.
- 오류율은 `/api/**`만 집계한다. 무요청과 성공 응답만 있는 상황은 정상이다. 적은 요청의 일시적 오류로 경보가 과도하게 발생하지 않도록 건수 조건을 둔다.
- 쿼리 실행 실패와 수집 중단 규칙의 NoData는 Alerting이다. 5xx NoData는 정상으로 처리하며 수집 가용성은 별도 규칙이 담당한다.
- 현재 contact point는 없고 root receiver는 기존 `empty`다. Grafana 내부 평가만 동작하며 이메일·Slack·Discord 전송은 미연결이다. 수신처와 자격증명 확정 후 시험/복구 통지 수신을 검증해야 한다.
- 원본은 `config/monitoring/grafana/provisioning/alerting/dooit-rules.json`이다. [Grafana 공식 절차](https://grafana.com/docs/grafana/latest/alerting/set-up/provision-alerting-resources/file-provisioning/)에 따라 admin API `POST /api/admin/provisioning/alerting/reload` 또는 Grafana 재시작으로 적용한다. 비밀번호는 셸 인자·로그에 남기지 않는다.
- `python3 -B scripts/test-monitoring-rules.py`는 실행 중인 Prometheus image ID로 네트워크 없는 임시 promtool 컨테이너를 띄워 실제 쿼리 9개 시나리오를 검증한다. 정상/수집 중단/target 부재/무요청/5% 경계/건수 부족/오류 증가/Actuator 제외를 포함한다.
- 운영에 실제 5xx나 서버 중단을 유발하지 않았다. 두 규칙의 `inactive`, `health=ok` 평가를 확인했다. 소량 점검 트래픽의 p95는 부하 테스트 결과가 아니다.

후속: 외부 수신처 연결, 임계값 조정, DB pending·backup age·disk 여유·외부 가용성 감시 확장.

## 7. 참고 원본

- Spring Boot Actuator Prometheus endpoint: https://docs.spring.io/spring-boot/reference/actuator/metrics.html
- Spring Boot Actuator endpoint exposure: https://docs.spring.io/spring-boot/reference/actuator/endpoints.html
- Grafana Loki stack and Alloy: https://grafana.com/docs/enterprise-logs/latest/get-started/quick-start/tutorial/
- Grafana datasource provisioning: https://grafana.com/docs/grafana/latest/administration/provisioning/

## 2026-10-30 PRD 반영 기록

- 1d719d3 배포 후 API histogram 621개와 실제 p95 값 반환을 확인했다. 시계열 수는 route/status 조합에 따라 변하므로 고정 목표가 아니다.
- Grafana live dashboard에서 5xx 비율/건수 포함 8개 패널과 두 장애 규칙의 정상 평가를 확인했다. 외부 통지는 미연결이다.
- 앞의 읽기 전용/STG 검증 기록은 배포 전 이력이다. 현재 상태는 이 절을 따른다.
