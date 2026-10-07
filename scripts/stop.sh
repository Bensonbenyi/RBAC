#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
for task_name in knowledge oa platform platform-load; do
  task_file=".runtime/$task_name.pid"
  if [[ ! -f "$task_file" ]]; then continue; fi
  read -r task_pid < "$task_file"
  if [[ ! "$task_pid" =~ ^[0-9]+$ ]]; then continue; fi
  task_command=$(ps -p "$task_pid" -o command= || true)
  if [[ "$task_command" == *'java '*'-jar platform/target/platform-1.0.0.jar'* || \
        "$task_command" == *'java '*'-jar demo-client/target/demo-client-1.0.0.jar'* || \
        "$task_command" == *'java '*'-jar .runtime/platform.jar'* || \
        "$task_command" == *'java '*'-jar .runtime/platform-load.jar'* || \
        "$task_command" == *'java '*'-jar .runtime/demo-client.jar'* ]]; then
    kill "$task_pid"
    for task_attempt in {1..20}; do
      if ! kill -0 "$task_pid" 2>/dev/null; then break; fi
      sleep 1
    done
  fi
done
# 只停止本项目 Compose 服务，不删除数据库卷，也不停止其他项目。
docker compose stop mysql
