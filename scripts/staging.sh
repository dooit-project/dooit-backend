#!/bin/bash
set -euo pipefail
repo_root=$(cd "$(dirname "$0")/.." && pwd)
cd "$repo_root"
export COMPOSE_DISABLE_ENV_FILE=1
unset COMPOSE_FILE COMPOSE_PROJECT_NAME
stg_env="$repo_root/.env.stg"
compose() {
  docker compose --env-file "$stg_env" -p stg_dooit -f "$repo_root/docker-compose.stg.yml" "$@"
}
case "${1:-}" in
  init)
    if [ -e "$stg_env" ]; then
      echo 'STG environment already exists; secrets were not replaced.'
      exit 0
    fi
    umask 077
    python3 - "$stg_env" <<'PY'
import os, secrets, sys
values = {
    'STG_MYSQL_ROOT_PASSWORD': secrets.token_hex(32),
    'STG_DB_PASSWORD': secrets.token_hex(32),
    'STG_JWT_SECRET': secrets.token_hex(48),
    'STG_APP_IMAGE_TAG': 'stg-local',
    'STG_APP_COMMIT_SHA': 'local-dirty',
}
with open(os.open(sys.argv[1], os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600), 'w') as f:
    f.write(''.join(f'{k}={v}\n' for k, v in values.items()))
PY
    echo 'Created private .env.stg with independent STG credentials.'
    ;;
  build)
    [ -f "$stg_env" ] || { echo 'Run scripts/staging.sh init first.' >&2; exit 2; }
    commit_sha=$(git rev-parse HEAD)
    if [ -n "$(git status --porcelain)" ]; then commit_sha="${commit_sha}-dirty"; fi
    image_tag="stg-$(git rev-parse --short HEAD)-$(date '+%Y%m%d%H%M%S')"
    ./gradlew test bootJar
    STG_APP_IMAGE_TAG="$image_tag" STG_APP_COMMIT_SHA="$commit_sha" compose build app
    python3 - "$stg_env" "$image_tag" "$commit_sha" <<'PY'
from pathlib import Path
import sys
p = Path(sys.argv[1])
values = dict(line.split('=', 1) for line in p.read_text().splitlines() if '=' in line and not line.startswith('#'))
values.update(STG_APP_IMAGE_TAG=sys.argv[2], STG_APP_COMMIT_SHA=sys.argv[3])
p.write_text(''.join(f'{k}={v}\n' for k, v in values.items()))
PY
    echo "STG image built: dooit-backend:$image_tag"
    ;;
  up)
    compose up -d --no-build --wait --wait-timeout 180
    ;;
  ps) compose ps ;;
  logs) compose logs --tail=100 app ;;
  stop) compose stop ;;
  down) compose down ;;
  config) compose config --quiet ;;
  *) echo 'Usage: scripts/staging.sh {init|build|up|ps|logs|stop|down|config}' >&2; exit 2 ;;
esac
