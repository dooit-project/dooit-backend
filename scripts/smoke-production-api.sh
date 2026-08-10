#!/bin/bash
set -euo pipefail

base_url=${TODOLAB_SMOKE_BASE_URL:-http://127.0.0.1:8080}
suffix=${TODOLAB_SMOKE_SUFFIX:-$(date '+%Y%m%d%H%M%S')}
password="SmokePass-${suffix}!"
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

assert_status() {
  local actual=$1
  local expected=$2
  local label=$3
  local output_file=$4
  if [ "$actual" != "$expected" ]; then
    echo "Smoke failed: ${label} expected HTTP ${expected}, got ${actual}" >&2
    jq '{status, error}' "$output_file" >&2 || true
    exit 1
  fi
}

json_value() {
  local output_file=$1
  local expression=$2
  jq -r "$expression" "$output_file"
}

require_command curl
require_command jq

readiness_json="$tmpdir/readiness.json"
guest_json="$tmpdir/guest.json"
me_json="$tmpdir/me.json"
refresh_json="$tmpdir/refresh.json"
promote_json="$tmpdir/promote.json"
target_json="$tmpdir/target.json"
merge_guest_json="$tmpdir/merge-guest.json"
merge_task_json="$tmpdir/merge-task.json"
merge_dday_json="$tmpdir/merge-dday.json"
merge_login_json="$tmpdir/merge-login.json"
retry_target_json="$tmpdir/retry-target.json"
retry_guest_json="$tmpdir/retry-guest.json"
retry_task_json="$tmpdir/retry-task.json"
retry_login1_json="$tmpdir/retry-login1.json"
retry_login2_json="$tmpdir/retry-login2.json"

readiness_status=$(request "$readiness_json" "$base_url/actuator/health/readiness")
assert_status "$readiness_status" "200" "readiness" "$readiness_json"
readiness=$(json_value "$readiness_json" '.status')
if [ "$readiness" != "UP" ]; then
  echo "Smoke failed: readiness status is ${readiness}" >&2
  exit 1
fi

guest_status=$(request "$guest_json" -X POST "$base_url/api/v1/auth/guest")
assert_status "$guest_status" "201" "guest create" "$guest_json"
guest_token=$(json_value "$guest_json" '.data.accessToken')
guest_id=$(json_value "$guest_json" '.data.user.id')
guest_type=$(json_value "$guest_json" '.data.user.accountType')
guest_email=$(json_value "$guest_json" '.data.user.email // "null"')
guest_display_name=$(json_value "$guest_json" '.data.user.displayName // "null"')

if [ "$guest_type" != "GUEST" ] || [ "$guest_email" != "null" ] || [ "$guest_display_name" != "null" ]; then
  echo "Smoke failed: guest create contract mismatch" >&2
  exit 1
fi

me_status=$(request "$me_json" -H "Authorization: Bearer $guest_token" "$base_url/api/v1/auth/me")
assert_status "$me_status" "200" "guest me" "$me_json"
me_id=$(json_value "$me_json" '.data.id')
me_type=$(json_value "$me_json" '.data.accountType')
if [ "$me_id" != "$guest_id" ] || [ "$me_type" != "GUEST" ]; then
  echo "Smoke failed: guest me does not match created guest" >&2
  exit 1
fi

refresh_status=$(request "$refresh_json" -X POST -H "Authorization: Bearer $guest_token" "$base_url/api/v1/auth/guest/refresh")
assert_status "$refresh_status" "200" "guest refresh" "$refresh_json"
refresh_id=$(json_value "$refresh_json" '.data.user.id')
refresh_type=$(json_value "$refresh_json" '.data.user.accountType')
if [ "$refresh_id" != "$guest_id" ] || [ "$refresh_type" != "GUEST" ]; then
  echo "Smoke failed: guest refresh does not preserve guest id" >&2
  exit 1
fi

promote_status=$(request "$promote_json" \
  -X POST "$base_url/api/v1/auth/register" \
  -H "Authorization: Bearer $guest_token" \
  -H 'Content-Type: application/json' \
  --data "{\"email\":\"smoke-promote-${suffix}@example.com\",\"password\":\"$password\",\"displayName\":\"Smoke Promote\"}")
assert_status "$promote_status" "201" "guest promote" "$promote_json"
promote_id=$(json_value "$promote_json" '.data.user.id')
promote_type=$(json_value "$promote_json" '.data.user.accountType')
if [ "$promote_id" != "$guest_id" ] || [ "$promote_type" != "REGISTERED" ]; then
  echo "Smoke failed: guest promote does not preserve guest id" >&2
  exit 1
fi

target_status=$(request "$target_json" \
  -X POST "$base_url/api/v1/auth/register" \
  -H 'Content-Type: application/json' \
  --data "{\"email\":\"smoke-target-${suffix}@example.com\",\"password\":\"$password\",\"displayName\":\"Smoke Target\"}")
assert_status "$target_status" "201" "merge target register" "$target_json"

merge_guest_status=$(request "$merge_guest_json" -X POST "$base_url/api/v1/auth/guest")
assert_status "$merge_guest_status" "201" "merge guest create" "$merge_guest_json"
merge_guest_token=$(json_value "$merge_guest_json" '.data.accessToken')

task_status=$(request "$merge_task_json" \
  -X POST "$base_url/api/v1/tasks" \
  -H "Authorization: Bearer $merge_guest_token" \
  -H 'Content-Type: application/json' \
  --data "{\"title\":\"Smoke task ${suffix}\",\"type\":\"TODO\",\"allDay\":false}")
assert_status "$task_status" "201" "guest task create" "$merge_task_json"

dday_status=$(request "$merge_dday_json" \
  -X POST "$base_url/api/v1/dday-goals" \
  -H "Authorization: Bearer $merge_guest_token" \
  -H 'Content-Type: application/json' \
  --data "{\"title\":\"Smoke dday ${suffix}\",\"targetDate\":\"2026-12-31\"}")
assert_status "$dday_status" "201" "guest dday create" "$merge_dday_json"

merge_status=$(request "$merge_login_json" \
  -X POST "$base_url/api/v1/auth/login" \
  -H "Authorization: Bearer $merge_guest_token" \
  -H 'Content-Type: application/json' \
  --data "{\"email\":\"smoke-target-${suffix}@example.com\",\"password\":\"$password\"}")
assert_status "$merge_status" "200" "guest merge login" "$merge_login_json"
merge_type=$(json_value "$merge_login_json" '.data.user.accountType')
merge_tasks=$(json_value "$merge_login_json" '.data.mergeResult.tasks')
merge_ddays=$(json_value "$merge_login_json" '.data.mergeResult.ddayGoals')
if [ "$merge_type" != "REGISTERED" ] || [ "$merge_tasks" != "1" ] || [ "$merge_ddays" != "1" ]; then
  echo "Smoke failed: guest merge result mismatch" >&2
  exit 1
fi

retry_target_status=$(request "$retry_target_json" \
  -X POST "$base_url/api/v1/auth/register" \
  -H 'Content-Type: application/json' \
  --data "{\"email\":\"smoke-retry-${suffix}@example.com\",\"password\":\"$password\",\"displayName\":\"Smoke Retry\"}")
assert_status "$retry_target_status" "201" "retry target register" "$retry_target_json"

retry_guest_status=$(request "$retry_guest_json" -X POST "$base_url/api/v1/auth/guest")
assert_status "$retry_guest_status" "201" "retry guest create" "$retry_guest_json"
retry_guest_token=$(json_value "$retry_guest_json" '.data.accessToken')

retry_task_status=$(request "$retry_task_json" \
  -X POST "$base_url/api/v1/tasks" \
  -H "Authorization: Bearer $retry_guest_token" \
  -H 'Content-Type: application/json' \
  --data "{\"title\":\"Smoke retry ${suffix}\",\"type\":\"TODO\",\"allDay\":false}")
assert_status "$retry_task_status" "201" "retry guest task create" "$retry_task_json"

retry_login1_status=$(request "$retry_login1_json" \
  -X POST "$base_url/api/v1/auth/login" \
  -H "Authorization: Bearer $retry_guest_token" \
  -H 'Content-Type: application/json' \
  --data "{\"email\":\"smoke-retry-${suffix}@example.com\",\"password\":\"$password\"}")
assert_status "$retry_login1_status" "200" "first retry merge login" "$retry_login1_json"

retry_login2_status=$(request "$retry_login2_json" \
  -X POST "$base_url/api/v1/auth/login" \
  -H "Authorization: Bearer $retry_guest_token" \
  -H 'Content-Type: application/json' \
  --data "{\"email\":\"smoke-retry-${suffix}@example.com\",\"password\":\"$password\"}")
assert_status "$retry_login2_status" "200" "second retry merge login" "$retry_login2_json"
retry_first_tasks=$(json_value "$retry_login1_json" '.data.mergeResult.tasks')
retry_second_tasks=$(json_value "$retry_login2_json" '.data.mergeResult.tasks')
if [ "$retry_first_tasks" != "1" ] || [ "$retry_second_tasks" != "0" ]; then
  echo "Smoke failed: merge retry idempotency mismatch" >&2
  exit 1
fi

cat <<EOF
Production smoke passed.
baseUrl=$base_url
guestCreate=201
guestMe=200
guestRefresh=200
guestPromote=201
guestMerge=200 tasks=$merge_tasks ddayGoals=$merge_ddays
mergeRetry=200 retryTasks=$retry_second_tasks
EOF
