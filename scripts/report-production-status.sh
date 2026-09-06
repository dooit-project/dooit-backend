#!/bin/bash
set -euo pipefail

backup_dir=${DOOIT_BACKUP_DIR:-/Users/hyunseung/dooit-backups}
base_url=${DOOIT_SMOKE_BASE_URL:-http://127.0.0.1:8080}
public_api_url=${DOOIT_PUBLIC_API_URL:-}
prometheus_url=${DOOIT_PROMETHEUS_URL:-http://127.0.0.1:9090}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1" >&2
    exit 2
  fi
}

latest_backup() {
  find "$backup_dir" -type f -name 'dooit-*.sql.gz' -print0 \
    | xargs -0 ls -t 2>/dev/null \
    | head -1
}

env_status() {
  if ./scripts/check-production-env.sh >/dev/null; then
    echo valid
  else
    echo failed
  fi
}

require_command docker
require_command find
require_command git
require_command jq

commit_sha=$(git rev-parse HEAD)
short_sha=$(git rev-parse --short HEAD)
worktree_status=dirty
if [ -z "$(git status --short)" ]; then
  worktree_status=clean
fi

app_image=missing
if docker inspect dooit-app >/dev/null 2>&1; then
  app_image=$(docker inspect dooit-app --format '{{.Config.Image}}')
fi

backup_file=missing
if [ -d "$backup_dir" ]; then
  backup_file=$(latest_backup)
  if [ -z "$backup_file" ]; then
    backup_file=missing
  fi
fi

readiness=failed
readiness_json=$(mktemp)
trap 'rm -f "$readiness_json"' EXIT
readiness_status=$(curl --silent --show-error --output "$readiness_json" --write-out '%{http_code}' "$base_url/actuator/health/readiness" || true)
if [ "$readiness_status" = "200" ] && [ "$(jq -r '.status' "$readiness_json" 2>/dev/null)" = "UP" ]; then
  readiness=UP
fi

routine=failed
if ./scripts/check-production-routine.sh >/dev/null; then
  routine=passed
fi

recovery=failed
if ./scripts/check-production-recovery.sh >/dev/null; then
  recovery=passed
fi

public_production=skipped
if [ -n "$public_api_url" ]; then
  if DOOIT_PUBLIC_API_URL="$public_api_url" ./scripts/check-public-production.sh >/dev/null; then
    public_production=passed
  else
    public_production=failed
  fi
fi

monitoring=skipped
if docker compose --profile monitoring ps --status running --services 2>/dev/null | grep -qx prometheus; then
  monitoring=failed
  monitoring_query=$(curl --silent --show-error \
    --data-urlencode 'query=up{job="dooit-backend"}' \
    "$prometheus_url/api/v1/query" 2>/dev/null || true)
  if [ -n "$monitoring_query" ] && [ "$(printf '%s' "$monitoring_query" | jq -r '.data.result[0].value[1] // "missing"' 2>/dev/null)" = "1" ]; then
    monitoring=passed
  fi
fi

cat <<EOF
Production status report.
backendCommit=$commit_sha
backendShortSha=$short_sha
worktree=$worktree_status
productionImage=$app_image
env=$(env_status)
latestBackup=$backup_file
readiness=$readiness
routineCheck=$routine
recoveryCheck=$recovery
publicProductionCheck=$public_production
monitoringCheck=$monitoring
guestRefreshApi=supported
mergeResultCounts=supported
EOF
