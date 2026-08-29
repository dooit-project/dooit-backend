#!/bin/bash
set -euo pipefail

if [ "$#" -ne 1 ]; then
  echo "Usage: $0 <backup.sql.gz>" >&2
  exit 2
fi

backup_file=$1

if [ ! -f "$backup_file" ]; then
  echo "Backup file not found: $backup_file" >&2
  exit 2
fi

gzip -t "$backup_file"

if [ "${DOOIT_CONFIRM_RESTORE:-}" != "RESTORE" ]; then
  echo "Restore replaces the current Dooit database." >&2
  echo "Re-run with DOOIT_CONFIRM_RESTORE=RESTORE after stopping the app service." >&2
  exit 2
fi

if docker compose ps --status running --services | grep -qx app; then
  echo "Stop the app first: docker compose stop app" >&2
  exit 2
fi

gzip -dc "$backup_file" \
  | docker compose exec -T mysql sh -c \
    'exec mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"'

echo "Database restore completed from: $backup_file"
