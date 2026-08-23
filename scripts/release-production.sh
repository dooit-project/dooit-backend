#!/bin/bash
set -euo pipefail

commit_sha=$(git rev-parse HEAD)
image_tag=${TODOLAB_APP_IMAGE_TAG:-$(git rev-parse --short HEAD)}

echo "Releasing ToDoLab backend image tag: ${image_tag}"

./gradlew clean test bootJar

TODOLAB_APP_COMMIT_SHA="$commit_sha" TODOLAB_APP_IMAGE_TAG="$image_tag" docker compose build app
TODOLAB_APP_COMMIT_SHA="$commit_sha" TODOLAB_APP_IMAGE_TAG="$image_tag" docker compose up -d app
TODOLAB_APP_COMMIT_SHA="$commit_sha" TODOLAB_APP_IMAGE_TAG="$image_tag" docker compose ps app

for attempt in {1..30}; do
  if curl --fail --silent --show-error http://127.0.0.1:8080/actuator/health/readiness; then
    break
  fi
  if [ "$attempt" -eq 30 ]; then
    echo "Readiness check failed after ${attempt} attempts" >&2
    exit 1
  fi
  sleep 2
done

echo
echo "Release completed: todolab-backend:${image_tag}"
echo "Rollback command: ./scripts/rollback-production.sh <previous-image-tag>"
