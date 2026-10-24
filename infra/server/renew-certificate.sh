#!/usr/bin/env bash
# IP 证书约六天有效，每六小时检查续期；失败保留日志并返回非零。
set -euo pipefail
cd /opt/upms
docker run --rm --network host -v "$PWD/letsencrypt:/etc/letsencrypt" \
  -v "$PWD/acme:/var/www/acme" certbot/certbot:v5.8.0 renew --quiet
docker exec upms-proxy nginx -t
docker exec upms-proxy nginx -s reload
