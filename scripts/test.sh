#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
docker compose up -d --wait mysql
./mvnw verify
npm --prefix frontend run lint
npm --prefix frontend run build
if [[ "${1:-}" == "--e2e" ]]; then
  npm --prefix frontend run test:e2e
fi
