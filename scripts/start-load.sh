#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p .runtime
if lsof -nP -iTCP:18080 -sTCP:LISTEN >/dev/null; then
  printf '%s\n' '18080 已占用，请先停止演示平台；脚本不自动终止未知进程。'
  exit 1
fi
if [[ ! -f platform/target/platform-1.0.0.jar ]]; then
  printf '%s\n' '请先运行 bash scripts/build.sh'
  exit 1
fi
docker compose up -d --wait mysql
cp platform/target/platform-1.0.0.jar .runtime/platform-load.jar
UPMS_DB=upms_load UPMS_SEED=load UPMS_CACHE=false \
  nohup java -Xms1g -Xmx4g -jar .runtime/platform-load.jar > .runtime/platform-load.log 2>&1 &
printf '%s\n' "$!" > .runtime/platform-load.pid
for task_attempt in {1..60}; do
  if curl -fsS --max-time 2 http://localhost:18080/actuator/health/readiness >/dev/null 2>&1; then break; fi
  sleep 1
done
curl -fsS --max-time 2 http://localhost:18080/actuator/health/readiness >/dev/null
printf '%s\n' 'upms_load 已就绪。运行 bash scripts/load-test.sh all 执行完整正式负载。'
