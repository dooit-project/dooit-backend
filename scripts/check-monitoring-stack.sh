#!/bin/bash
set -euo pipefail

prometheus_url=${DOOIT_PROMETHEUS_URL:-http://127.0.0.1:9090}
loki_url=${DOOIT_LOKI_URL:-http://127.0.0.1:3100}
grafana_url=${DOOIT_GRAFANA_URL:-http://127.0.0.1:3000}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1" >&2
    exit 2
  fi
}

require_command curl
require_command docker
require_command jq

docker compose --profile monitoring ps --status running --services | grep -qx prometheus
docker compose --profile monitoring ps --status running --services | grep -qx loki
docker compose --profile monitoring ps --status running --services | grep -qx alloy
docker compose --profile monitoring ps --status running --services | grep -qx grafana

curl --fail --silent --show-error "$prometheus_url/-/ready" >/dev/null
curl --fail --silent --show-error "$loki_url/ready" >/dev/null
curl --fail --silent --show-error "$grafana_url/api/health" >/dev/null

backend_up=$(curl --fail --silent --show-error \
  --data-urlencode 'query=up{job="dooit-backend"}' \
  "$prometheus_url/api/v1/query" \
  | jq -r '.data.result[0].value[1] // "missing"')

if [ "$backend_up" != "1" ]; then
  echo "Prometheus target dooit-backend is not UP: ${backend_up:-missing}" >&2
  exit 1
fi

cat <<EOF
Monitoring stack check passed.
prometheus=$prometheus_url
loki=$loki_url
grafana=$grafana_url
dooitBackendUp=$backend_up
EOF
