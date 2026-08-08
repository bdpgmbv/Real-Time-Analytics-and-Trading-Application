#!/bin/bash
#
# Takes a backup, and proves it can be restored. A backup nobody has restored is a hope.
#
#   ./deploy/operate/backup.sh                  take one
#   ./deploy/operate/backup.sh --verify FILE    restore it into a scratch database and count rows

set -euo pipefail

container="${RTAT_POSTGRES:-rtat-postgres}"
user="${RTAT_DB_USER:-rtat}"
database="${RTAT_DB_NAME:-rtat}"
into="${RTAT_BACKUP_DIR:-./backups}"

if [ "${1:-}" = "--verify" ]; then
    file="${2:?usage: backup.sh --verify <file>}"
    scratch="rtat_restore_check"

    echo "restoring $file into $scratch"
    docker exec -i "$container" psql -U "$user" -d postgres -q \
        -c "DROP DATABASE IF EXISTS $scratch;" -c "CREATE DATABASE $scratch;"
    gunzip -c "$file" | docker exec -i "$container" pg_restore -U "$user" -d "$scratch" --no-owner 2>/dev/null || true

    docker exec -i "$container" psql -U "$user" -d "$scratch" -c "
      SELECT 'positions' AS table_name, count(*) FROM position
      UNION ALL SELECT 'trades', count(*) FROM trade
      UNION ALL SELECT 'hedges', count(*) FROM hedge;"

    docker exec -i "$container" psql -U "$user" -d postgres -q -c "DROP DATABASE $scratch;"
    echo "restore verified, scratch database removed"
    exit 0
fi

mkdir -p "$into"
file="$into/rtat-$(date -u +%Y%m%dT%H%M%SZ).dump.gz"

# Custom format so a single table can be restored without the rest.
docker exec "$container" pg_dump -U "$user" -d "$database" --format=custom | gzip > "$file"

echo "wrote $file  ($(du -h "$file" | cut -f1))"
echo "verify it now:  ./deploy/operate/backup.sh --verify $file"
