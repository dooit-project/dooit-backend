#!/bin/bash
set -euo pipefail

repo_root=$(cd "$(dirname "$0")/.." && pwd)
label=${DOOIT_PRODUCTION_LAUNCHD_LABEL:-pj.dooit.backend.production}
log_dir=${DOOIT_PRODUCTION_LAUNCHD_LOG_DIR:-"$repo_root/logs/launchd"}
interval_seconds=${DOOIT_PRODUCTION_LAUNCHD_INTERVAL_SECONDS:-300}
plist_dir="$HOME/Library/LaunchAgents"
plist_file="${plist_dir}/${label}.plist"

mkdir -p "$plist_dir" "$log_dir"

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
    <string>cd "${repo_root}" &amp;&amp; ./scripts/ensure-production-up.sh</string>
  </array>
  <key>EnvironmentVariables</key>
  <dict>
    <key>PATH</key>
    <string>/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin</string>
  </dict>
  <key>RunAtLoad</key>
  <true/>
  <key>StartInterval</key>
  <integer>${interval_seconds}</integer>
  <key>StandardOutPath</key>
  <string>${log_dir}/production-launchd.out.log</string>
  <key>StandardErrorPath</key>
  <string>${log_dir}/production-launchd.err.log</string>
</dict>
</plist>
EOF

plutil -lint "$plist_file"

echo "LaunchAgent written: $plist_file"
echo "Load command: launchctl bootstrap gui/$(id -u) \"$plist_file\""
echo "Status command: launchctl print gui/$(id -u)/${label}"
