#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
docker compose up -d --wait mysql
npm --prefix frontend ci
npm --prefix frontend run lint
npm --prefix frontend run build
./mvnw spotless:check verify
mkdir -p platform/target/classes/static
cp -R frontend/dist/. platform/target/classes/static/
./mvnw -pl platform,demo-client -DskipTests package
