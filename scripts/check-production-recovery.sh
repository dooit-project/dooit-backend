#!/bin/bash
set -euo pipefail

label=${DOOIT_PRODUCTION_LAUNCHD_LABEL:-pj.dooit.backend.production}
base_url=${DOOIT_SMOKE_BASE_URL:-http://127.0.0.1:8080}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1" >&2
    exit 2
  fi
}

launchd_field() {
  local pattern=$1
  launchctl print "gui/$(id -u)/${label}" 2>/dev/null | awk -F '= ' -v pattern="$pattern" '$1 ~ pattern { print $2; exit }'
}

require_command curl
require_command docker
require_command jq
require_command launchctl
require_command sysctl

boot_time=$(sysctl -n kern.boottime | awk -F'[{}, ]+' '{ print $4 }')
now_epoch=$(date '+%s')
uptime_seconds=$((now_epoch - boot_time))
launchd_runs=$(launchd_field 'runs')
launchd_last_exit=$(launchd_field 'last exit code')

if [ -z "$launchd_runs" ]; then
  echo "Production LaunchAgent is not loaded: ${label}" >&2
  exit 1
fi
if [ "$launchd_last_exit" != "0" ]; then
  echo "Production LaunchAgent last exit code is not 0: ${launchd_last_exit:-unknown}" >&2
  exit 1
fi

docker_status=$(docker desktop status 2>/dev/null || true)
if ! echo "$docker_status" | grep -qi 'running'; then
  echo "Docker Desktop is not running" >&2
  echo "$docker_status" >&2
  exit 1
fi

docker compose ps --status running --services | grep -qx mysql
docker compose ps --status running --services | grep -qx app

app_health=$(docker inspect dooit-app --format '{{.State.Health.Status}}')
mysql_health=$(docker inspect dooit-mysql --format '{{.State.Health.Status}}')
if [ "$app_health" != "healthy" ] || [ "$mysql_health" != "healthy" ]; then
  echo "Unexpected container health: app=${app_health}, mysql=${mysql_health}" >&2
  exit 1
fi

readiness_json=$(mktemp)
trap 'rm -f "$readiness_json"' EXIT
readiness_status=$(curl --silent --show-error --output "$readiness_json" --write-out '%{http_code}' "$base_url/actuator/health/readiness")
if [ "$readiness_status" != "200" ] || [ "$(jq -r '.status' "$readiness_json")" != "UP" ]; then
  echo "Readiness check failed" >&2
  jq '{status, components}' "$readiness_json" >&2 || true
  exit 1
fi

cat <<EOF
Production recovery check passed.
bootTimeEpoch=$boot_time
uptimeSeconds=$uptime_seconds
launchdLabel=$label
launchdRuns=$launchd_runs
launchdLastExitCode=$launchd_last_exit
dockerDesktop=running
appHealth=$app_health
mysqlHealth=$mysql_health
readiness=UP
EOF
