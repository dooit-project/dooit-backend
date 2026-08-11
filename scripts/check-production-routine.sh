#!/bin/bash
set -euo pipefail

backup_dir=${TODOLAB_BACKUP_DIR:-/Users/hyunseung/todolab-backups}
offsite_dir=${TODOLAB_OFFSITE_BACKUP_DIR:-}
max_backup_age_hours=${TODOLAB_MAX_BACKUP_AGE_HOURS:-30}
min_free_gb=${TODOLAB_MIN_FREE_GB:-10}
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

backup_age_hours() {
  local backup_file=$1
  local backup_epoch
  local now_epoch
  backup_epoch=$(stat -f '%m' "$backup_file")
  now_epoch=$(date '+%s')
  echo $(( (now_epoch - backup_epoch) / 3600 ))
}

free_gb_for_path() {
  local path=$1
  df -g "$path" | awk 'NR == 2 { print $4 }'
}

require_command curl
require_command docker
require_command find
require_command gzip
require_command jq
require_command shasum
require_command stat

if [ ! -d "$backup_dir" ]; then
  echo "Backup directory not found: $backup_dir" >&2
  exit 1
fi

backup_file=$(latest_backup)
if [ -z "$backup_file" ]; then
  echo "No backup file found in: $backup_dir" >&2
  exit 1
fi

gzip -t "$backup_file"
age_hours=$(backup_age_hours "$backup_file")
if [ "$age_hours" -gt "$max_backup_age_hours" ]; then
  echo "Latest backup is too old: ${age_hours}h > ${max_backup_age_hours}h ($backup_file)" >&2
  exit 1
fi

free_gb=$(free_gb_for_path "$backup_dir")
if [ "$free_gb" -lt "$min_free_gb" ]; then
  echo "Free disk space is too low: ${free_gb}GiB < ${min_free_gb}GiB" >&2
  exit 1
fi

offsite_status=skipped
if [ -n "$offsite_dir" ]; then
  offsite_file="${offsite_dir}/$(basename "$backup_file")"
  if [ ! -f "$offsite_file" ]; then
    echo "Offsite backup copy not found: $offsite_file" >&2
    exit 1
  fi
  gzip -t "$offsite_file"
  source_sha=$(shasum -a 256 "$backup_file" | awk '{ print $1 }')
  offsite_sha=$(shasum -a 256 "$offsite_file" | awk '{ print $1 }')
  if [ "$source_sha" != "$offsite_sha" ]; then
    echo "Offsite backup checksum mismatch: $offsite_file" >&2
    exit 1
  fi
  offsite_status=verified
fi

docker compose ps --status running --services | grep -qx mysql
docker compose ps --status running --services | grep -qx app

readiness_json=$(mktemp)
trap 'rm -f "$readiness_json"' EXIT
readiness_status=$(curl --silent --show-error --output "$readiness_json" --write-out '%{http_code}' "$base_url/actuator/health/readiness")
if [ "$readiness_status" != "200" ]; then
  echo "Readiness endpoint returned HTTP ${readiness_status}" >&2
  jq '{status, components}' "$readiness_json" >&2 || true
  exit 1
fi
if [ "$(jq -r '.status' "$readiness_json")" != "UP" ]; then
  echo "Readiness status is not UP" >&2
  jq '{status, components}' "$readiness_json" >&2 || true
  exit 1
fi

cat <<EOF
Production routine check passed.
baseUrl=$base_url
backup=$backup_file
backupAgeHours=$age_hours
freeDiskGiB=$free_gb
offsiteBackup=$offsite_status
readiness=UP
EOF
