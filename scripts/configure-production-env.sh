#!/bin/bash
set -euo pipefail

if [ "$#" -ne 1 ]; then
  echo "Usage: $0 <https-tailscale-origin>" >&2
  exit 2
fi

issuer=${1%/}
env_file=.env

case "$issuer" in
  https://*.ts.net) ;;
  *)
    echo "Issuer must be a Tailscale HTTPS origin ending in .ts.net" >&2
    exit 2
    ;;
esac

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

set_env_value "TODOLAB_JWT_ISSUER" "$issuer" "https://your-device.your-tailnet.ts.net"
set_env_value "TODOLAB_JWT_SECRET" "$jwt_secret" "replace-with-at-least-32-random-bytes"

if ! grep -q '^TODOLAB_JWT_ACCESS_TOKEN_TTL=' "$env_file"; then
  printf 'TODOLAB_JWT_ACCESS_TOKEN_TTL=PT24H\n' >> "$env_file"
fi

echo "Production JWT settings added to .env without printing the secret."
