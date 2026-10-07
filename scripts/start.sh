#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p .runtime
task_mode="${1:-demo}"
case "$task_mode" in
  demo) export UPMS_DB=upms UPMS_SEED=demo ;;
  baseline) export UPMS_DB=upms_baseline UPMS_SEED=baseline ;;
  *) printf '%s\n' '可选启动模式：demo baseline'; exit 1 ;;
esac
docker compose up -d --wait mysql
# 旧卷同样可以补建独立基础库；此脚本只有 CREATE IF NOT EXISTS 与 GRANT，无删除。
docker compose exec -T mysql sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" < /docker-entrypoint-initdb.d/01-databases.sql' 2>/dev/null
if [[ ! -f platform/target/platform-1.0.0.jar || ! -f demo-client/target/demo-client-1.0.0.jar ]]; then
  printf '%s\n' '请先运行 bash scripts/build.sh'
  exit 1
fi
for task_port in 18080 18081 18082; do
  if lsof -nP -iTCP:"$task_port" -sTCP:LISTEN >/dev/null; then
    printf '端口 %s 已占用，请检查已有项目进程。\n' "$task_port"
    exit 1
  fi
done
# 使用独立运行副本，后续 Maven 重新打包不会覆盖正在读取的 JAR。
cp platform/target/platform-1.0.0.jar .runtime/platform.jar
cp demo-client/target/demo-client-1.0.0.jar .runtime/demo-client.jar
nohup java -Xms512m -Xmx2g -jar .runtime/platform.jar > .runtime/platform.log 2>&1 &
printf '%s\n' "$!" > .runtime/platform.pid
for task_attempt in {1..60}; do
  if curl -fsS --max-time 2 http://localhost:18080/actuator/health/readiness >/dev/null 2>&1; then break; fi
  sleep 1
done
curl -fsS --max-time 2 http://localhost:18080/actuator/health/readiness >/dev/null
nohup java -Xms128m -Xmx512m -jar .runtime/demo-client.jar > .runtime/oa.log 2>&1 &
printf '%s\n' "$!" > .runtime/oa.pid
DEMO_PORT=18082 DEMO_COOKIE=KNOWLEDGE_SESSION DEMO_SYSTEM=knowledge \
  DEMO_SECRET=knowledge-demo-secret DEMO_TITLE='企业知识库演示' \
  DEMO_READ='f007:查看' DEMO_WRITE='f002:编辑' \
  nohup java -Xms128m -Xmx512m -jar .runtime/demo-client.jar > .runtime/knowledge.log 2>&1 &
printf '%s\n' "$!" > .runtime/knowledge.pid
for task_port in 18081 18082; do
  for task_attempt in {1..30}; do
    if curl -fsS --max-time 2 "http://localhost:$task_port/" >/dev/null 2>&1; then break; fi
    sleep 1
  done
  curl -fsS --max-time 2 "http://localhost:$task_port/" >/dev/null
done
printf '%s\n' '后台 http://localhost:18080/admin' 'OA http://localhost:18081' '知识库 http://localhost:18082'
if [[ "$task_mode" == baseline ]]; then
  printf '%s\n' '纯基础模式没有管理员或业务授权；演示请停止后改用 demo 模式。'
fi
if [[ "${2:-}" == --foreground ]]; then
  # 在进程托管或 CI 环境保留父进程，避免会话结束时后台子进程被托管工具清理。
  trap 'bash scripts/stop.sh' INT TERM
  wait
fi
