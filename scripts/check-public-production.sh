#!/bin/bash
set -euo pipefail

base_url=${TODOLAB_PUBLIC_API_URL:-${TODOLAB_SMOKE_BASE_URL:-}}
web_origin=${TODOLAB_WEB_ORIGIN:-${TODOLAB_EXPO_WEB_ORIGIN:-}}
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
    echo "Public production check failed: ${label} expected HTTP ${expected}, got ${actual}" >&2
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

if [ -z "$base_url" ]; then
  echo "TODOLAB_PUBLIC_API_URL or TODOLAB_SMOKE_BASE_URL is required" >&2
  exit 2
fi
if [[ "$base_url" != https://* ]]; then
  echo "Public production API URL must be HTTPS" >&2
  exit 2
fi
base_url=${base_url%/}

readiness_json="$tmpdir/readiness.json"
metadata_json="$tmpdir/metadata.json"
guest_json="$tmpdir/guest.json"
me_json="$tmpdir/me.json"
preflight_headers="$tmpdir/preflight.headers"

readiness_status=$(request "$readiness_json" "$base_url/actuator/health/readiness")
assert_status "$readiness_status" "200" "readiness" "$readiness_json"
readiness=$(json_value "$readiness_json" '.status')
if [ "$readiness" != "UP" ]; then
  echo "Public production check failed: readiness status is ${readiness}" >&2
  exit 1
fi

metadata_status=$(request "$metadata_json" "$base_url/api/v1/system/metadata")
assert_status "$metadata_status" "200" "metadata" "$metadata_json"
commit_sha=$(json_value "$metadata_json" '.data.commitSha')
image_tag=$(json_value "$metadata_json" '.data.imageTag')
if [ -z "$commit_sha" ] || [ "$commit_sha" = "null" ] || [ -z "$image_tag" ] || [ "$image_tag" = "null" ]; then
  echo "Public production check failed: metadata contract mismatch" >&2
  exit 1
fi

guest_status=$(request "$guest_json" -X POST "$base_url/api/v1/auth/guest")
assert_status "$guest_status" "201" "guest create" "$guest_json"
guest_token=$(json_value "$guest_json" '.data.accessToken')
guest_id=$(json_value "$guest_json" '.data.user.id')
guest_type=$(json_value "$guest_json" '.data.user.accountType')
if [ -z "$guest_token" ] || [ "$guest_token" = "null" ] || [ "$guest_type" != "GUEST" ]; then
  echo "Public production check failed: guest token contract mismatch" >&2
  exit 1
fi

me_status=$(request "$me_json" -H "Authorization: Bearer $guest_token" "$base_url/api/v1/auth/me")
assert_status "$me_status" "200" "guest me" "$me_json"
me_id=$(json_value "$me_json" '.data.id')
me_type=$(json_value "$me_json" '.data.accountType')
if [ "$me_id" != "$guest_id" ] || [ "$me_type" != "GUEST" ]; then
  echo "Public production check failed: /auth/me does not match guest" >&2
  exit 1
fi

preflight_status=skipped
if [ -n "$web_origin" ]; then
  preflight_status=$(curl --silent --show-error --output /dev/null --dump-header "$preflight_headers" --write-out '%{http_code}' \
    -X OPTIONS "$base_url/api/v1/auth/me" \
    -H "Origin: $web_origin" \
    -H 'Access-Control-Request-Method: GET' \
    -H 'Access-Control-Request-Headers: Authorization,Content-Type,Idempotency-Key')
  if [ "$preflight_status" != "200" ]; then
    echo "Public production check failed: CORS preflight expected HTTP 200, got ${preflight_status}" >&2
    cat "$preflight_headers" >&2
    exit 1
  fi
fi

cat <<EOF
Public production check passed.
baseUrl=$base_url
readiness=UP
metadata=ok
backendCommit=$commit_sha
imageTag=$image_tag
guestCreate=201
guestMe=200
corsPreflight=$preflight_status
EOF
