#!/usr/bin/env bash
# MySQL 只绑定内网；两个客户端仅供 Manager 反向代理访问。
set -euo pipefail
cd /opt/upms
docker volume create upms-course-data
docker run -d --name upms-mysql --restart unless-stopped --memory 512m --cpus 1 \
  --env-file mysql.env -p 172.31.12.231:3307:3306 \
  -v upms-course-data:/var/lib/mysql mysql:8.4 \
  --character-set-server=utf8mb4 --collation-server=utf8mb4_0900_as_cs \
  --innodb-buffer-pool-size=96M --performance-schema=OFF --max-connections=40
for system in oa knowledge; do
  docker run -d --name "upms-$system" --restart unless-stopped --network host \
    --memory 384m --cpus 0.5 --env-file "$system.env" \
    -v "$PWD/demo-client.jar:/app/application.jar:ro" -w /app \
    eclipse-temurin:21.0.11_10-jre-jammy java -Xms32m -Xmx128m \
    -XX:MaxMetaspaceSize=128m -XX:+ExitOnOutOfMemoryError -jar /app/application.jar
done
