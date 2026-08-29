#!/bin/bash
set -euo pipefail

base_url=${DOOIT_SMOKE_BASE_URL:-http://127.0.0.1:8080}
run_db_outage=${DOOIT_CONFIRM_DB_OUTAGE:-}
tmpdir=$(mktemp -d)

cleanup() {
  rm -rf "$tmpdir"
}
trap cleanup EXIT

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1" >&2
    exit 2
  fi
}

request() {
  local output_file=$1
  shift
  curl --silent --show-error --output "$output_file" --write-out '%{http_code}' "$@"
}

wait_readiness_up() {
  local output_file=$1
  for attempt in {1..30}; do
    local status
    status=$(request "$output_file" "$base_url/actuator/health/readiness" || true)
    if [ "$status" = "200" ] && [ "$(jq -r '.status // ""' "$output_file")" = "UP" ]; then
      return 0
    fi
    sleep 2
  done
  echo "Readiness did not return UP within 60 seconds" >&2
  jq '{status, components}' "$output_file" >&2 || true
  return 1
}

compose_up_with_current_app_image() {
  local current_app_image=$1
  if [[ "$current_app_image" == dooit-backend:* ]]; then
    DOOIT_APP_IMAGE_TAG="${current_app_image#dooit-backend:}" docker compose up -d mysql app >/dev/null
    return
  fi
  docker compose up -d mysql app >/dev/null
}

require_command curl
require_command docker
require_command jq

readiness_json="$tmpdir/readiness.json"
guest_json="$tmpdir/guest.json"
unauthorized_json="$tmpdir/unauthorized.json"
not_found_json="$tmpdir/not-found.json"
db_down_json="$tmpdir/db-down.json"
recovered_json="$tmpdir/recovered.json"

wait_readiness_up "$readiness_json"

unauthorized_status=$(request "$unauthorized_json" "$base_url/api/v1/auth/me")
unauthorized_code=$(jq -r '.error.code // ""' "$unauthorized_json")
if [ "$unauthorized_status" != "401" ] || [ "$unauthorized_code" != "11002" ]; then
  echo "Unauthorized drill failed: status=${unauthorized_status}, code=${unauthorized_code}" >&2
  exit 1
fi

guest_status=$(request "$guest_json" -X POST "$base_url/api/v1/auth/guest")
if [ "$guest_status" != "201" ]; then
  echo "Guest token setup for not found drill failed: status=${guest_status}" >&2
  jq '{status, error}' "$guest_json" >&2 || true
  exit 1
fi
guest_token=$(jq -r '.data.accessToken' "$guest_json")

not_found_status=$(request "$not_found_json" -H "Authorization: Bearer $guest_token" "$base_url/api/v1/__incident-drill-not-found")
not_found_code=$(jq -r '.error.code // ""' "$not_found_json")
if [ "$not_found_status" != "404" ] || [ "$not_found_code" != "10003" ]; then
  echo "Not found drill failed: status=${not_found_status}, code=${not_found_code}" >&2
  exit 1
fi

db_outage_result="skipped"
if [ "$run_db_outage" = "STOP_MYSQL" ]; then
  current_app_image=$(docker inspect dooit-app --format '{{.Config.Image}}')
  docker compose stop mysql >/dev/null
  set +e
  db_down_status=$(request "$db_down_json" "$base_url/actuator/health/readiness")
  set -e
  if [ "$db_down_status" = "200" ] && [ "$(jq -r '.status // ""' "$db_down_json")" = "UP" ]; then
    echo "DB outage drill failed: readiness stayed UP while mysql was stopped" >&2
    compose_up_with_current_app_image "$current_app_image"
    wait_readiness_up "$recovered_json"
    exit 1
  fi
  compose_up_with_current_app_image "$current_app_image"
  wait_readiness_up "$recovered_json"
  db_outage_result="recovered"
elif [ -n "$run_db_outage" ]; then
  echo "Invalid DOOIT_CONFIRM_DB_OUTAGE value. Use STOP_MYSQL to run the DB outage drill." >&2
  exit 2
fi

cat <<EOF
Production incident drill passed.
baseUrl=$base_url
readiness=UP
unauthorized=401 code=11002
notFound=404 code=10003
dbOutage=$db_outage_result
EOF
