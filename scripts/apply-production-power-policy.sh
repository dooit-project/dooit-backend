#!/bin/bash
set -euo pipefail

confirm=${TODOLAB_CONFIRM_POWER_POLICY:-}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1" >&2
    exit 2
  fi
}

require_command pmset
require_command sudo

if [ "$confirm" != "APPLY" ]; then
  echo "Refusing to change macOS power policy without TODOLAB_CONFIRM_POWER_POLICY=APPLY" >&2
  echo "Run: TODOLAB_CONFIRM_POWER_POLICY=APPLY ./scripts/apply-production-power-policy.sh" >&2
  exit 2
fi

sudo pmset -c sleep 0 disksleep 0 powernap 0
TODOLAB_STRICT_POWER=true ./scripts/check-production-host.sh
