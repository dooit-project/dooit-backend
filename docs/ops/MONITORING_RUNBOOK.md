# Monitoring Runbook

Last updated: 2026-09-05

이 문서는 Dooit local production에 Prometheus, Grafana, Loki, Grafana Alloy 기반 모니터링을 붙이기 위한 목표 구성과 구현 작업을 정리한다. 실제 secret, 관리자 비밀번호, public/private 관리 URL은 문서에 기록하지 않는다.

## 1. 목표

- production API의 JVM, HTTP, DB connection, scheduler 상태를 Prometheus metric으로 수집한다.
- app/mysql/container 로그를 Loki에 보관하고 Grafana Explore에서 request id 기준으로 확인한다.
- Grafana는 host loopback에만 열어 운영자가 로컬에서 확인한다.
- `/actuator/health/**`는 기존 public readiness smoke 용도로 유지하되, `/actuator/prometheus`는 외부 공개 도메인에 노출하지 않는다.
- 대시보드와 datasource는 수동 클릭 설정이 아니라 provisioning 파일로 재현 가능하게 둔다.

## 2. 권장 구성

| 구성요소 | 역할 | 1차 배치 |
| --- | --- | --- |
| Spring Boot Actuator | health와 metric endpoint 제공 | app container |
| Micrometer Prometheus registry | `/actuator/prometheus` scrape 형식 제공 | app dependency |
| Prometheus | app metric scrape와 단기 보관 | Compose service, `127.0.0.1:9090` |
| Loki | 로그 저장과 LogQL 조회 | Compose service, 내부 network 중심 |
| Grafana Alloy | Docker log 또는 app log file 수집 후 Loki 전송 | Compose service |
| Grafana | Prometheus/Loki datasource와 dashboard UI | Compose service, `127.0.0.1:3000` |

1차 운영에서는 Grafana, Prometheus, Loki, Alloy를 외부 공개하지 않는다. Cloudflare Tunnel에 management UI를 연결해야 할 때는 Cloudflare Access 또는 동등한 인증을 먼저 붙인 뒤 별도 작업으로 진행한다.

## 3. 네트워크와 보안 원칙

- app API public endpoint는 기존처럼 Cloudflare Tunnel을 통해 `https://dooitapi.hsng.pe.kr`로만 공개한다.
- Grafana, Prometheus, Loki, Alloy UI는 host loopback bind만 허용한다.
- Prometheus scrape 대상은 Docker Compose 내부 DNS `app:8080`을 사용한다.
- `/actuator/prometheus`는 public internet에 직접 노출하지 않는다.
- Spring Security는 health만 public permit을 유지하고, prometheus endpoint는 내부 scrape만 허용하는 정책을 둔다.
- Grafana admin password는 `.env` 또는 Docker secret에 두고 저장소에 커밋하지 않는다.
- app payload logging은 production 기본값 `DOOIT_API_LOGGING_PAYLOAD_ENABLED=false`를 유지한다.

## 4. 구현 작업

### B0. Spring metric endpoint

- [ ] `build.gradle`에 `io.micrometer:micrometer-registry-prometheus`를 추가한다.
- [ ] `application.yml`의 Actuator exposure에 `prometheus`를 추가하되 production 보안 정책을 함께 반영한다.
- [ ] `application-prod.yml`에서 Prometheus 노출 여부를 `DOOIT_ACTUATOR_PROMETHEUS_ENABLED` 같은 환경변수로 제어할지 결정한다.
- [ ] `SecurityConfig`에서 `/actuator/prometheus` 접근 정책을 health와 분리한다.
- [ ] OpenAPI 문서 공개 정책과 달리 metric endpoint는 API 계약 문서에 포함하지 않는다.

검증:

```bash
./gradlew test
curl --fail http://127.0.0.1:8080/actuator/prometheus
```

### B1. Compose monitoring stack

- [ ] `docker-compose.yml`에 `prometheus`, `loki`, `alloy`, `grafana` service를 추가한다.
- [ ] Prometheus data volume을 추가하고 retention 기간을 15일 또는 30일로 제한한다.
- [ ] Loki data volume을 추가하고 local filesystem 저장소를 사용한다.
- [ ] Grafana data volume을 추가한다.
- [ ] Grafana, Prometheus, Alloy UI port는 `127.0.0.1`에만 bind한다.
- [ ] Loki port는 Grafana/Alloy 내부 통신만 필요하면 host에 bind하지 않는다.

검증:

```bash
docker compose config
docker compose up -d prometheus loki alloy grafana
docker compose ps
```

### B2. Prometheus scrape config

- [ ] `config/monitoring/prometheus/prometheus.yml`을 추가한다.
- [ ] scrape target은 `app:8080`, metrics path는 `/actuator/prometheus`로 둔다.
- [ ] scrape interval은 15초 또는 30초로 시작한다.
- [ ] Prometheus 자체 metric도 함께 scrape한다.

핵심 확인 metric:

- `up{job="dooit-backend"}`
- `http_server_requests_seconds_count`
- `jvm_memory_used_bytes`
- `hikaricp_connections_active`
- `process_uptime_seconds`

### B3. Loki와 Alloy log pipeline

- [ ] 새 구성에서는 Promtail 대신 Grafana Alloy를 사용한다.
- [ ] 1차는 Docker socket 기반 container log 수집으로 시작한다.
- [ ] label은 `compose_project`, `compose_service`, `container`, `env` 정도만 둔다.
- [ ] app log의 `requestId`는 line parsing으로 추출할 수 있으면 label이 아니라 parsed field로 유지한다.
- [ ] noisy health check 로그가 과도하면 Alloy process stage에서 drop 여부를 결정한다.

기본 조회:

```logql
{compose_service="app"}
{compose_service="mysql"}
{compose_service="app"} |= "ERROR"
{compose_service="app"} |= "requestId"
```

### B4. Grafana provisioning

- [ ] `config/monitoring/grafana/provisioning/datasources`에 Prometheus와 Loki datasource를 추가한다.
- [ ] Prometheus URL은 `http://prometheus:9090`, Loki URL은 `http://loki:3100`을 사용한다.
- [ ] dashboard provisioning 디렉터리를 추가한다.
- [ ] 1차 dashboard는 JVM/HTTP/DB/Container health 중심으로 만든다.
- [ ] Grafana anonymous access는 production 기본 비활성으로 둔다.

1차 dashboard panel:

- API request rate, latency p95, 5xx count
- JVM heap/non-heap memory, GC pause
- HikariCP active/idle/pending connection
- process CPU, uptime
- app container restart count 또는 Docker health 상태
- recent ERROR logs

### B5. 운영 스크립트와 문서

- [ ] `scripts/check-monitoring-stack.sh`를 추가해 Prometheus, Loki, Grafana datasource health를 점검한다.
- [ ] `scripts/report-production-status.sh`에 monitoring endpoint 요약을 포함할지 결정한다.
- [ ] `docs/ops/LOCAL_PRODUCTION_RUNBOOK.md`에 monitoring 기동과 장애 확인 순서를 추가한다.
- [ ] `docs/project/ROADMAP.md`에서 monitoring 구축 작업을 완료 상태로 갱신한다.

## 5. 운영 확인 순서

```bash
docker compose ps
curl --fail http://127.0.0.1:8080/actuator/health/readiness
curl --fail http://127.0.0.1:9090/-/ready
curl --fail http://127.0.0.1:3000/api/health
```

Grafana에서 확인한다.

- Prometheus datasource Save & test 통과
- Loki datasource Save & test 통과
- `up{job="dooit-backend"}`가 `1`
- `{compose_service="app"}` 로그가 최신 시각까지 유입
- app ERROR 로그가 request id와 함께 조회

## 6. 알림은 후속 작업

Alertmanager, Grafana contact point, Slack/Discord/email 알림은 1차 dashboard와 log 수집이 안정된 뒤 붙인다. 1차 alert 후보는 아래로 제한한다.

- readiness down 2분 이상
- 5xx rate 급증
- DB connection pending 증가
- app container restart
- backup age 기준 초과
- disk free space 부족

## 7. 참고 원본

- Spring Boot Actuator Prometheus endpoint: https://docs.spring.io/spring-boot/reference/actuator/metrics.html
- Spring Boot Actuator endpoint exposure: https://docs.spring.io/spring-boot/reference/actuator/endpoints.html
- Grafana Loki stack and Alloy: https://grafana.com/docs/enterprise-logs/latest/get-started/quick-start/tutorial/
- Grafana datasource provisioning: https://grafana.com/docs/grafana/latest/administration/provisioning/
