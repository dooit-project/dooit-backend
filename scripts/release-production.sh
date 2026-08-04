#!/bin/bash
set -euo pipefail

image_tag=${TODOLAB_APP_IMAGE_TAG:-$(git rev-parse --short HEAD)}

echo "Releasing ToDoLab backend image tag: ${image_tag}"

./gradlew clean test bootJar

TODOLAB_APP_IMAGE_TAG="$image_tag" docker compose build app
TODOLAB_APP_IMAGE_TAG="$image_tag" docker compose up -d app
TODOLAB_APP_IMAGE_TAG="$image_tag" docker compose ps app

curl --fail http://127.0.0.1:8080/actuator/health/readiness

echo
echo "Release completed: todolab-backend:${image_tag}"
echo "Rollback command: ./scripts/rollback-production.sh <previous-image-tag>"
