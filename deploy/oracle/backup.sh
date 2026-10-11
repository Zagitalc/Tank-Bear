#!/usr/bin/env bash
# Consistent copy of the database (price history cannot be rebuilt). Keeps the last 7. Run by tankbear-backup.timer.
set -euo pipefail
src="/var/lib/tankbear/tankbear.sqlite"
dir="/var/lib/tankbear/backups"
[ -f "${src}" ] || exit 0
out="${dir}/tankbear-$(date -u +%Y%m%dT%H%M%SZ).sqlite"
sqlite3 "${src}" ".backup '${out}'"
ls -1t "${dir}"/tankbear-*.sqlite | tail -n +8 | xargs -r rm -f
echo "backup written: ${out}"
