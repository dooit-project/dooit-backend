#!/bin/bash
set -euo pipefail

backup_dir=${TODOLAB_BACKUP_DIR:-./backups}
retention_days=${TODOLAB_BACKUP_RETENTION_DAYS:-14}
timestamp=$(date '+%Y%m%d-%H%M%S')
backup_file="${backup_dir}/todolab-${timestamp}.sql.gz"

mkdir -p "$backup_dir"

docker compose exec -T mysql sh -c \
  'exec mysqldump --single-transaction --no-tablespaces --routines --triggers -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' \
  | gzip > "$backup_file"

gzip -t "$backup_file"
if ! gzip -dc "$backup_file" | grep -q -- '-- MySQL dump'; then
  echo "Backup validation failed: MySQL dump header is missing" >&2
  rm -f "$backup_file"
  exit 1
fi
find "$backup_dir" -type f -name 'todolab-*.sql.gz' -mtime "+$retention_days" -delete

echo "Database backup created: $backup_file"
