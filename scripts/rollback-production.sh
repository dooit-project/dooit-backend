#!/bin/bash
set -euo pipefail

if [ "$#" -ne 1 ]; then
  echo "Usage: $0 <previous-image-tag>" >&2
  exit 2
fi

image_tag=$1
image_name="todolab-backend:${image_tag}"

docker image inspect "$image_name" >/dev/null

TODOLAB_APP_IMAGE_TAG="$image_tag" docker compose up -d --no-build app
TODOLAB_APP_IMAGE_TAG="$image_tag" docker compose ps app

curl --fail http://127.0.0.1:8080/actuator/health/readiness

echo
echo "Rollback completed: ${image_name}"
