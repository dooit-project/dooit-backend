#!/bin/bash
set -euo pipefail

repo_root=$(cd "$(dirname "$0")/.." && pwd)
label=${DOOIT_BACKUP_LAUNCHD_LABEL:-pj.dooit.backend.backup}
backup_dir=${DOOIT_BACKUP_DIR:-"$repo_root/backups"}
backup_hour=${DOOIT_BACKUP_HOUR:-3}
backup_minute=${DOOIT_BACKUP_MINUTE:-15}
plist_dir="$HOME/Library/LaunchAgents"
plist_file="${plist_dir}/${label}.plist"

mkdir -p "$plist_dir" "$backup_dir"

cat > "$plist_file" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN"
  "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key>
  <string>${label}</string>
  <key>ProgramArguments</key>
  <array>
    <string>/bin/bash</string>
    <string>-lc</string>
    <string>cd "${repo_root}" &amp;&amp; DOOIT_BACKUP_DIR="${backup_dir}" ./scripts/backup-db.sh</string>
  </array>
  <key>EnvironmentVariables</key>
  <dict>
    <key>PATH</key>
    <string>/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin</string>
  </dict>
  <key>StartCalendarInterval</key>
  <dict>
    <key>Hour</key>
    <integer>${backup_hour}</integer>
    <key>Minute</key>
    <integer>${backup_minute}</integer>
  </dict>
  <key>StandardOutPath</key>
  <string>${backup_dir}/launchd-backup.out.log</string>
  <key>StandardErrorPath</key>
  <string>${backup_dir}/launchd-backup.err.log</string>
</dict>
</plist>
EOF

plutil -lint "$plist_file"

echo "LaunchAgent written: $plist_file"
echo "Load command: launchctl bootstrap gui/$(id -u) \"$plist_file\""
echo "Status command: launchctl print gui/$(id -u)/${label}"
