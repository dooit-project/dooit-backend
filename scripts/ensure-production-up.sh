#!/bin/bash
set -euo pipefail

repo_root=$(cd "$(dirname "$0")/.." && pwd)
base_url=${TODOLAB_SMOKE_BASE_URL:-http://127.0.0.1:8080}
image_tag=${TODOLAB_APP_IMAGE_TAG:-}

wait_for_docker() {
  for attempt in {1..60}; do
    if docker info >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  echo "Docker engine did not become ready within 120 seconds" >&2
  return 1
}

wait_for_readiness() {
  for attempt in {1..60}; do
    if curl --fail --silent --show-error "$base_url/actuator/health/readiness" >/dev/null; then
      return 0
    fi
    sleep 2
  done
  echo "Readiness did not become UP within 120 seconds" >&2
  return 1
}

if command -v docker >/dev/null 2>&1; then
  docker desktop start >/dev/null 2>&1 || true
else
  echo "Docker CLI is not available" >&2
  exit 2
fi

wait_for_docker

cd "$repo_root"

if [ -z "$image_tag" ] && docker inspect todolab-app >/dev/null 2>&1; then
  current_image=$(docker inspect todolab-app --format '{{.Config.Image}}')
  if [[ "$current_image" == todolab-backend:* ]]; then
    image_tag=${current_image#todolab-backend:}
  fi
fi
if [ -z "$image_tag" ]; then
  image_tag=$(git rev-parse --short HEAD)
fi

TODOLAB_APP_IMAGE_TAG="$image_tag" docker compose up -d mysql app
wait_for_readiness

echo "Production stack is up: todolab-backend:${image_tag}"
