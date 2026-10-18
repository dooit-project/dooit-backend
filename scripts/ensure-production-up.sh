#!/bin/bash
set -euo pipefail

repo_root=$(cd "$(dirname "$0")/.." && pwd)
base_url=${DOOIT_SMOKE_BASE_URL:-http://127.0.0.1:8080}
image_tag=${DOOIT_APP_IMAGE_TAG:-}
commit_sha=${DOOIT_APP_COMMIT_SHA:-}

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

if [ -z "$image_tag" ] && docker inspect dooit-app >/dev/null 2>&1; then
  current_image=$(docker inspect dooit-app --format '{{.Config.Image}}')
  if [[ "$current_image" == dooit-backend:* ]]; then
    image_tag=${current_image#dooit-backend:}
  fi
fi
if [ -z "$image_tag" ]; then
  image_tag=$(git rev-parse --short HEAD)
fi

if [ -z "$commit_sha" ] && docker inspect dooit-app >/dev/null 2>&1; then
  current_image=$(docker inspect dooit-app --format '{{.Config.Image}}')
  if [ "$current_image" = "dooit-backend:${image_tag}" ]; then
    commit_sha=$(docker inspect dooit-app --format '{{range .Config.Env}}{{println .}}{{end}}' \
      | awk -F= '$1 == "DOOIT_APP_COMMIT_SHA" { print substr($0, index($0, "=") + 1); exit }')
  fi
fi
if [ -z "$commit_sha" ] || [ "$commit_sha" = "local" ]; then
  commit_sha=$(git rev-parse --verify "${image_tag}^{commit}" 2>/dev/null || true)
fi
if [ -z "$commit_sha" ]; then
  commit_sha=$image_tag
fi

DOOIT_APP_COMMIT_SHA="$commit_sha" DOOIT_APP_IMAGE_TAG="$image_tag" docker compose up -d mysql app
wait_for_readiness

echo "Production stack is up: dooit-backend:${image_tag}"
