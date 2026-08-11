#!/bin/bash
set -euo pipefail

base_url=${TODOLAB_TAILSCALE_API_URL:-${TODOLAB_SMOKE_BASE_URL:-}}
expo_origin=${TODOLAB_EXPO_WEB_ORIGIN:-}
tailscale_cli=${TODOLAB_TAILSCALE_CLI:-tailscale}
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

require_tailscale_cli() {
  if command -v "$tailscale_cli" >/dev/null 2>&1; then
    return 0
  fi
  if [ -x "$tailscale_cli" ]; then
    return 0
  fi
  if [ -d /Applications/Tailscale.app ]; then
    echo "Tailscale.app is installed, but the tailscale CLI is not available." >&2
    echo "Install or link the CLI, or set TODOLAB_TAILSCALE_CLI to its executable path." >&2
    exit 2
  fi
  echo "Required command not found: $tailscale_cli" >&2
  exit 2
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
    echo "Tailscale production check failed: ${label} expected HTTP ${expected}, got ${actual}" >&2
    jq '{status, error}' "$output_file" >&2 || true
    exit 1
  fi
}

json_value() {
  local output_file=$1
  local expression=$2
  jq -r "$expression" "$output_file"
}

if [ -z "$base_url" ]; then
  echo "TODOLAB_TAILSCALE_API_URL is required, for example https://<device>.<tailnet>.ts.net" >&2
  exit 2
fi
if [[ "$base_url" != https://* ]]; then
  echo "TODOLAB_TAILSCALE_API_URL must be an HTTPS URL" >&2
  exit 2
fi
base_url=${base_url%/}

require_command curl
require_command jq
require_tailscale_cli

if ! "$tailscale_cli" status >/dev/null; then
  echo "Tailscale is not connected" >&2
  exit 1
fi

serve_status="$tmpdir/tailscale-serve.txt"
if ! "$tailscale_cli" serve status >"$serve_status"; then
  echo "Tailscale Serve is not configured" >&2
  exit 1
fi
if ! grep -q '127.0.0.1:8080\|localhost:8080' "$serve_status"; then
  echo "Tailscale Serve does not appear to route to local app port 8080" >&2
  cat "$serve_status" >&2
  exit 1
fi

readiness_json="$tmpdir/readiness.json"
guest_json="$tmpdir/guest.json"
me_json="$tmpdir/me.json"
preflight_headers="$tmpdir/preflight.headers"

readiness_status=$(request "$readiness_json" "$base_url/actuator/health/readiness")
assert_status "$readiness_status" "200" "tailscale readiness" "$readiness_json"
readiness=$(json_value "$readiness_json" '.status')
if [ "$readiness" != "UP" ]; then
  echo "Tailscale production check failed: readiness status is ${readiness}" >&2
  exit 1
fi

guest_status=$(request "$guest_json" -X POST "$base_url/api/v1/auth/guest")
assert_status "$guest_status" "201" "guest create over tailscale" "$guest_json"
guest_token=$(json_value "$guest_json" '.data.accessToken')
guest_id=$(json_value "$guest_json" '.data.user.id')
guest_type=$(json_value "$guest_json" '.data.user.accountType')
if [ -z "$guest_token" ] || [ "$guest_token" = "null" ] || [ "$guest_type" != "GUEST" ]; then
  echo "Tailscale production check failed: guest token contract mismatch" >&2
  exit 1
fi

me_status=$(request "$me_json" -H "Authorization: Bearer $guest_token" "$base_url/api/v1/auth/me")
assert_status "$me_status" "200" "guest me over tailscale" "$me_json"
me_id=$(json_value "$me_json" '.data.id')
me_type=$(json_value "$me_json" '.data.accountType')
if [ "$me_id" != "$guest_id" ] || [ "$me_type" != "GUEST" ]; then
  echo "Tailscale production check failed: /auth/me does not match guest" >&2
  exit 1
fi

preflight_status=skipped
if [ -n "$expo_origin" ]; then
  preflight_status=$(curl --silent --show-error --output /dev/null --dump-header "$preflight_headers" --write-out '%{http_code}' \
    -X OPTIONS "$base_url/api/v1/auth/me" \
    -H "Origin: $expo_origin" \
    -H 'Access-Control-Request-Method: GET' \
    -H 'Access-Control-Request-Headers: Authorization')
  if [ "$preflight_status" != "200" ]; then
    echo "Tailscale production check failed: CORS preflight expected HTTP 200, got ${preflight_status}" >&2
    cat "$preflight_headers" >&2
    exit 1
  fi
fi

cat <<EOF
Tailscale production check passed.
baseUrl=$base_url
tailscaleStatus=connected
tailscaleCli=$tailscale_cli
serveTarget=127.0.0.1:8080
readiness=UP
guestCreate=201
guestMe=200
corsPreflight=$preflight_status
EOF
