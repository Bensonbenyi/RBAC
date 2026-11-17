#!/usr/bin/env bash
# Verify a backup in an isolated temporary MySQL instance on the database worker.
set -euo pipefail
backup_file="$1"
restore_name=rbac-v33-restore-check
restore_volume=rbac_v33_restore_check
if docker container inspect "$restore_name" >/dev/null 2>&1 || docker volume inspect "$restore_volume" >/dev/null 2>&1; then
  echo 'Restore check resources already exist; inspect them before retrying.' >&2
  exit 1
fi
cleanup() { docker rm -f "$restore_name" >/dev/null 2>&1 || true; docker volume rm "$restore_volume" >/dev/null 2>&1 || true; }
trap cleanup EXIT
docker run -d --name "$restore_name" --network none --memory 384m \
  -e MYSQL_ALLOW_EMPTY_PASSWORD=yes -v "$restore_volume:/var/lib/mysql" \
  rbac/mysql:8.4.13-amd64 --innodb-buffer-pool-size=64M --max-connections=10 --performance-schema=OFF >/dev/null
ready=0
for attempt in $(seq 1 45); do
  if docker exec "$restore_name" mysqladmin ping --silent >/dev/null 2>&1; then ready=1; break; fi
  sleep 1
done
test "$ready" = 1
# Wait for the final server, rather than the initialization server's socket.
for attempt in $(seq 1 45); do
  if docker logs "$restore_name" 2>&1 | grep -q 'MySQL init process done'; then break; fi
  sleep 1
done
sleep 2
gzip -dc "$backup_file" | docker exec -i "$restore_name" mysql -uroot
live_container="$(docker ps -q --filter label=com.docker.swarm.service.name=rbac_v33_db)"
canonical='--no-create-info --compact --skip-extended-insert --skip-add-locks --set-gtid-purged=OFF --skip-comments --order-by-primary --databases rbac course_project'
source_hash="$(docker exec "$live_container" sh -c 'MYSQL_PWD="$(cat /run/secrets/mysql_root_password)" exec mysqldump -uroot '"$canonical" | sha256sum | cut -d' ' -f1)"
restored_hash="$(docker exec "$restore_name" mysqldump -uroot $canonical | sha256sum | cut -d' ' -f1)"
test "$source_hash" = "$restored_hash"
printf '{"sourceDataSha256":"%s","restoredDataSha256":"%s","result":"passed","network":"none","memoryLimitMiB":384}\n' "$source_hash" "$restored_hash"
