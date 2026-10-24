#!/usr/bin/env bash
# 在 Manager 上启动单节点平台；私有环境文件不进入版本控制。
set -euo pipefail
cd /opt/upms
docker run -d --name upms-platform --restart unless-stopped --network host \
  --memory 1024m --cpus 1.5 --env-file platform.env \
  -v "$PWD/platform.jar:/app/application.jar:ro" -v "$PWD/source:/app/source:ro" \
  -w /app eclipse-temurin:21.0.11_10-jre-jammy \
  java -Xms128m -Xmx512m -XX:MaxMetaspaceSize=192m -XX:+ExitOnOutOfMemoryError \
  -jar /app/application.jar
if [[ "${1:-}" == "--platform-only" ]]; then
  exit 0
fi
docker run -d --name upms-proxy --restart unless-stopped --network host \
  --memory 64m --cpus 0.25 -v "$PWD/nginx.conf:/etc/nginx/nginx.conf:ro" \
  -v "$PWD/acme:/var/www/acme:ro" -v "$PWD/letsencrypt:/etc/letsencrypt:ro" \
  -v "$PWD/public:/srv/upms:ro" nginx:1.28.0-alpine
