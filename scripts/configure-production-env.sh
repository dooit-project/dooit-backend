#!/bin/bash
set -euo pipefail

if [ "$#" -lt 1 ] || [ "$#" -gt 2 ]; then
  echo "Usage: $0 <https-api-origin> [https-public-api-origin]" >&2
  exit 2
fi

issuer=${1%/}
public_api_url=${2:-}
public_api_url=${public_api_url%/}
env_file=.env

validate_https_url() {
  local label=$1
  local value=$2
  if [[ ! "$value" =~ ^https://[^[:space:]/]+(:[0-9]+)?$ ]]; then
    echo "$label must be an HTTPS origin without a path" >&2
    exit 2
  fi
}

validate_https_url "Issuer" "$issuer"
if [ -n "$public_api_url" ]; then
  validate_https_url "Public API URL" "$public_api_url"
fi

if [ ! -f "$env_file" ]; then
  cp .env.example "$env_file"
fi

set_env_value() {
  key=$1
  value=$2
  placeholder=$3

  if grep -q "^${key}=" "$env_file"; then
    current_value=$(grep "^${key}=" "$env_file" | tail -n 1 | cut -d= -f2-)
    if [ -n "$current_value" ] && [ "$current_value" != "$placeholder" ]; then
      echo "${key} already has a non-placeholder value; refusing to overwrite .env" >&2
      exit 2
    fi

    tmp_file=$(mktemp)
    awk -v key="$key" -v value="$value" '
      BEGIN { replaced = 0 }
      $0 ~ "^" key "=" {
        if (replaced == 0) {
          print key "=" value
          replaced = 1
        }
        next
      }
      { print }
      END {
        if (replaced == 0) {
          print key "=" value
        }
      }
    ' "$env_file" > "$tmp_file"
    mv "$tmp_file" "$env_file"
    return
  fi

  printf '%s=%s\n' "$key" "$value" >> "$env_file"
}

jwt_secret=$(openssl rand -base64 48)

set_env_value "DOOIT_JWT_ISSUER" "$issuer" "https://api.example.com"
set_env_value "DOOIT_JWT_SECRET" "$jwt_secret" "replace-with-at-least-32-random-bytes"
if [ -n "$public_api_url" ]; then
  set_env_value "DOOIT_PUBLIC_API_URL" "$public_api_url" "https://api.example.com"
fi

if ! grep -q '^DOOIT_JWT_ACCESS_TOKEN_TTL=' "$env_file"; then
  printf 'DOOIT_JWT_ACCESS_TOKEN_TTL=PT24H\n' >> "$env_file"
fi

echo "Production JWT settings added to .env without printing the secret."
