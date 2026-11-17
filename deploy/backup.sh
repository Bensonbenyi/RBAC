#!/usr/bin/env bash
# Run on the database worker; write a consistent dump to a protected directory.
set -euo pipefail
backup_dir="${1:-/opt/rbac-v33/backups}"
mkdir -p "$backup_dir"
chmod 700 "$backup_dir"
container_id="$(docker ps -q --filter label=com.docker.swarm.service.name=rbac_v33_db)"
test -n "$container_id"
backup_file="$backup_dir/rbac-$(date +%Y%m%dT%H%M%S).sql.gz"
# Database credentials remain inside the database container.
docker exec "$container_id" sh -c 'MYSQL_PWD="$(cat /run/secrets/mysql_root_password)" exec mysqldump -uroot --single-transaction --routines --triggers --set-gtid-purged=OFF --databases rbac course_project' | gzip > "$backup_file"
chmod 600 "$backup_file"
sha256sum "$backup_file"
