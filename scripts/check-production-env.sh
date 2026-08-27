#!/bin/bash
set -euo pipefail

env_file=${TODOLAB_ENV_FILE:-.env}
require_tailscale_url=${TODOLAB_REQUIRE_TAILSCALE_URL:-false}
require_public_api_url=${TODOLAB_REQUIRE_PUBLIC_API_URL:-false}
require_offsite_backup=${TODOLAB_REQUIRE_OFFSITE_BACKUP:-false}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1" >&2
    exit 2
  fi
}

env_value() {
  local key=$1
  grep -E "^${key}=" "$env_file" 2>/dev/null | tail -n 1 | cut -d= -f2- || true
}

require_env_value() {
  local key=$1
  local value
  value=$(env_value "$key")
  if [ -z "$value" ]; then
    echo "Missing required production env value: $key" >&2
    exit 1
  fi
  case "$value" in
    replace-with-*|https://your-device.your-tailnet.ts.net)
      echo "Production env value still uses placeholder: $key" >&2
      exit 1
      ;;
  esac
}

validate_https_url() {
  local key=$1
  local value=$2
  if [[ ! "$value" =~ ^https://[^[:space:]/]+(:[0-9]+)?$ ]]; then
    echo "$key must be an HTTPS origin without a path" >&2
    exit 1
  fi
}

validate_https_origin_list() {
  local key=$1
  local value=$2
  local item
  if [ -z "$value" ]; then
    return
  fi
  IFS=',' read -r -a origins <<< "$value"
  for item in "${origins[@]}"; do
    item=$(echo "$item" | xargs)
    case "$item" in
      https://*|http://localhost:*|http://127.0.0.1:*) ;;
      *)
        echo "$key contains a non-HTTPS origin: $item" >&2
        exit 1
        ;;
    esac
  done
}

require_command grep
require_command cut
require_command xargs

if [ ! -f "$env_file" ]; then
  echo "Production env file not found: $env_file" >&2
  exit 1
fi

for key in \
  TODOLAB_MYSQL_ROOT_PASSWORD \
  TODOLAB_MYSQL_DATABASE \
  TODOLAB_DB_URL \
  TODOLAB_DB_USERNAME \
  TODOLAB_DB_PASSWORD \
  TODOLAB_JWT_ISSUER \
  TODOLAB_JWT_SECRET \
  TODOLAB_JWT_ACCESS_TOKEN_TTL
do
  require_env_value "$key"
done

jwt_issuer=$(env_value TODOLAB_JWT_ISSUER)
validate_https_url TODOLAB_JWT_ISSUER "$jwt_issuer"

jwt_secret=$(env_value TODOLAB_JWT_SECRET)
jwt_secret_bytes=$(printf '%s' "$jwt_secret" | wc -c | xargs)
if [ "$jwt_secret_bytes" -lt 32 ]; then
  echo "TODOLAB_JWT_SECRET must be at least 32 bytes" >&2
  exit 1
fi

allowed_origins=$(env_value TODOLAB_ALLOWED_ORIGINS)
validate_https_origin_list TODOLAB_ALLOWED_ORIGINS "$allowed_origins"
if [ -z "$allowed_origins" ]; then
  allowed_origins_status=empty
else
  allowed_origins_status=configured
fi

guest_jwt_ttl=$(env_value TODOLAB_GUEST_JWT_ACCESS_TOKEN_TTL)
if [ -z "$guest_jwt_ttl" ]; then
  guest_jwt_ttl_status=defaultP31D
else
  guest_jwt_ttl_status=configured
fi

tailscale_url=$(env_value TODOLAB_TAILSCALE_API_URL)
if [ -z "$tailscale_url" ]; then
  if [ "$require_tailscale_url" = "true" ]; then
    echo "TODOLAB_TAILSCALE_API_URL is required when TODOLAB_REQUIRE_TAILSCALE_URL=true" >&2
    exit 1
  fi
  tailscale_url_status=missing
else
  validate_https_url TODOLAB_TAILSCALE_API_URL "$tailscale_url"
  case "$tailscale_url" in
    https://*.ts.net) tailscale_url_status=configured ;;
    *) tailscale_url_status=configuredNonTailscale ;;
  esac
fi

public_api_url=$(env_value TODOLAB_PUBLIC_API_URL)
if [ -z "$public_api_url" ]; then
  if [ "$require_public_api_url" = "true" ]; then
    echo "TODOLAB_PUBLIC_API_URL is required when TODOLAB_REQUIRE_PUBLIC_API_URL=true" >&2
    exit 1
  fi
  public_api_url_status=missing
else
  validate_https_url TODOLAB_PUBLIC_API_URL "$public_api_url"
  public_api_url_status=configured
fi

offsite_dir=$(env_value TODOLAB_OFFSITE_BACKUP_DIR)
if [ -z "$offsite_dir" ]; then
  if [ "$require_offsite_backup" = "true" ]; then
    echo "TODOLAB_OFFSITE_BACKUP_DIR is required when TODOLAB_REQUIRE_OFFSITE_BACKUP=true" >&2
    exit 1
  fi
  offsite_backup_status=missing
elif [ -d "$offsite_dir" ]; then
  offsite_backup_status=directoryExists
else
  offsite_backup_status=configuredDirectoryMissing
fi

cat <<EOF
Production env check passed.
envFile=$env_file
dbConfig=present
jwtIssuer=httpsOrigin
jwtSecretBytes=valid
guestJwtTtl=$guest_jwt_ttl_status
allowedOrigins=$allowed_origins_status
tailscaleApiUrl=$tailscale_url_status
publicApiUrl=$public_api_url_status
offsiteBackupDir=$offsite_backup_status
EOF
