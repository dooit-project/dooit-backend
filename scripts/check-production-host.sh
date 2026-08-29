#!/bin/bash
set -euo pipefail

strict_power=${DOOIT_STRICT_POWER:-false}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1" >&2
    exit 2
  fi
}

pmset_value() {
  local key=$1
  pmset -g custom | awk -v key="$key" '$1 == key { print $2; exit }'
}

warn_or_fail_power() {
  local message=$1
  if [ "$strict_power" = "true" ]; then
    echo "$message" >&2
    exit 1
  fi
  echo "WARN: $message"
}

require_command docker
require_command pmset

docker_status=$(docker desktop status 2>/dev/null || true)
if ! echo "$docker_status" | grep -qi 'running'; then
  echo "Docker Desktop is not running" >&2
  echo "$docker_status" >&2
  exit 1
fi

app_restart=$(docker inspect dooit-app --format '{{.HostConfig.RestartPolicy.Name}}')
mysql_restart=$(docker inspect dooit-mysql --format '{{.HostConfig.RestartPolicy.Name}}')
if [ "$app_restart" != "unless-stopped" ] || [ "$mysql_restart" != "unless-stopped" ]; then
  echo "Unexpected restart policy: app=${app_restart}, mysql=${mysql_restart}" >&2
  exit 1
fi

sleep_value=$(pmset_value sleep)
disk_sleep_value=$(pmset_value disksleep)
powernap_value=$(pmset_value powernap)

if [ "$sleep_value" != "0" ]; then
  warn_or_fail_power "AC power sleep is ${sleep_value}; production policy requires sleep=0"
fi
if [ "$disk_sleep_value" != "0" ]; then
  warn_or_fail_power "AC power disksleep is ${disk_sleep_value}; production policy requires disksleep=0"
fi
if [ "$powernap_value" != "0" ]; then
  warn_or_fail_power "AC power powernap is ${powernap_value}; production policy requires powernap=0"
fi

cat <<EOF
Production host check passed.
dockerDesktop=running
appRestartPolicy=$app_restart
mysqlRestartPolicy=$mysql_restart
acSleep=$sleep_value
acDiskSleep=$disk_sleep_value
acPowerNap=$powernap_value
strictPower=$strict_power
EOF
