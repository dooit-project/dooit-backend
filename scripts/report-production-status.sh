#!/bin/bash
set -euo pipefail

backup_dir=${TODOLAB_BACKUP_DIR:-/Users/hyunseung/todolab-backups}
base_url=${TODOLAB_SMOKE_BASE_URL:-http://127.0.0.1:8080}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1" >&2
    exit 2
  fi
}

latest_backup() {
  find "$backup_dir" -type f -name 'todolab-*.sql.gz' -print0 \
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
if docker inspect todolab-app >/dev/null 2>&1; then
  app_image=$(docker inspect todolab-app --format '{{.Config.Image}}')
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

tailscale=skipped
if [ -n "${TODOLAB_TAILSCALE_API_URL:-}" ]; then
  if ./scripts/check-tailscale-production.sh >/dev/null; then
    tailscale=passed
  else
    tailscale=failed
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
tailscaleCheck=$tailscale
guestRefreshApi=supported
mergeResultCounts=supported
EOF
