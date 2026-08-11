#!/bin/bash
set -euo pipefail

backup_dir=${TODOLAB_BACKUP_DIR:-/Users/hyunseung/todolab-backups}
offsite_dir=${TODOLAB_OFFSITE_BACKUP_DIR:-}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1" >&2
    exit 2
  fi
}

latest_backup() {
  find "$backup_dir" -type f -name 'todolab-*.sql.gz' -print0 \
    | xargs -0 ls -t 2>/dev/null \
    | head -1
}

require_command find
require_command gzip
require_command shasum

if [ -z "$offsite_dir" ]; then
  echo "TODOLAB_OFFSITE_BACKUP_DIR is required" >&2
  exit 2
fi
if [ ! -d "$backup_dir" ]; then
  echo "Backup directory not found: $backup_dir" >&2
  exit 1
fi

backup_file=$(latest_backup)
if [ -z "$backup_file" ]; then
  echo "No backup file found in: $backup_dir" >&2
  exit 1
fi

mkdir -p "$offsite_dir"
gzip -t "$backup_file"

dest_file="${offsite_dir}/$(basename "$backup_file")"
if command -v rsync >/dev/null 2>&1; then
  rsync -a "$backup_file" "$dest_file"
else
  cp -p "$backup_file" "$dest_file"
fi

gzip -t "$dest_file"
source_sha=$(shasum -a 256 "$backup_file" | awk '{ print $1 }')
dest_sha=$(shasum -a 256 "$dest_file" | awk '{ print $1 }')
if [ "$source_sha" != "$dest_sha" ]; then
  echo "Offsite backup checksum mismatch" >&2
  exit 1
fi

cat > "${dest_file}.sha256" <<EOF
${dest_sha}  $(basename "$dest_file")
EOF

cat <<EOF
Production backup synced.
source=$backup_file
destination=$dest_file
sha256=$dest_sha
EOF
