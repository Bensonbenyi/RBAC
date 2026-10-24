#!/usr/bin/env bash
# 先停止本项目释放内存，再恢复原有两个 Swarm 服务，不删除任何数据卷。
set -euo pipefail
case "${1:-}" in
  manager)
    docker stop upms-proxy upms-platform
    echo '还需在 Node1 执行本脚本 node，再在 Manager 执行 services。'
    ;;
  node) docker stop upms-oa upms-knowledge upms-mysql ;;
  services) docker service scale lab_mysql=1 lab_productdemojpa=1 ;;
  *) echo '用法：restore-lab.sh manager|node|services' >&2; exit 2 ;;
esac
