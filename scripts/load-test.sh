#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
task_phase="${1:-all}"
case "$task_phase" in all|login|authz|admin|mixed|changes) ;; *) printf '%s\n' '可选：all login authz admin mixed changes'; exit 1;; esac
mkdir -p .runtime/load docs/verification/performance/raw
# 压测不操作演示库；必须由调用者先启动 UPMS_DB=upms_load、UPMS_SEED=load 平台。
curl -fsS --max-time 3 http://localhost:18080/actuator/health/readiness >/dev/null
if [[ ! -f .runtime/load/accounts.csv || ! -f .runtime/load/admins.csv ]]; then
  printf '%s\n' '请先启动专用压测平台，生成压测输入。'
  exit 1
fi
task_caffeinate=""
if command -v caffeinate >/dev/null; then
  caffeinate -i -w "$$" &
  task_caffeinate="$!"
fi
trap 'if [[ -n "$task_caffeinate" ]]; then kill "$task_caffeinate" 2>/dev/null || true; fi' EXIT

run_simulation() {
  local task_class="$1" task_label="$2" task_log=".runtime/load-$2.log" task_exit=0
  ./mvnw -q -pl load-test gatling:test -Dgatling.simulationClass="cn.edu.upms.load.$task_class" > "$task_log" 2>&1 || task_exit="$?"
  # 失败测量也保存，不能只保留达标轮次。归档中没有 CSV 令牌或虚拟手机号。
  local task_report
  task_report=$(sed -n 's@.*file://\(.*\)/index.html@\1@p' "$task_log" | tail -n 1)
  if [[ -n "$task_report" && -f "$task_report/index.html" ]]; then
    node scripts/report-performance.mjs "$task_report" "$task_label"
    tar -czf "docs/verification/performance/raw/$task_label.tar.gz" -C "$(dirname "$task_report")" "$(basename "$task_report")"
    cp "$task_log" "docs/verification/performance/raw/$task_label-console.txt"
  fi
  return "$task_exit"
}

if [[ "$task_phase" == all || "$task_phase" == login ]]; then
  run_simulation LoginSimulation login-final
fi
if [[ "$task_phase" != login ]]; then
  # 不允许用 9,999 个令牌冒充 10,000 个已登录账号；登录失败后须重新生成完整输入。
  node --input-type=module -e 'import fs from "node:fs"; const rows=fs.readFileSync(".runtime/load/tokens.csv","utf8").trim().split("\n").slice(1); if(rows.length!==10000||new Set(rows.map(r=>r.split(",")[0])).size!==10000) throw Error("需要 10,000 个不同账号的有效令牌；先完整运行 login");'
fi
if [[ "$task_phase" == all || "$task_phase" == authz ]]; then run_simulation AuthorizationSimulation authz-final; fi
if [[ "$task_phase" == all || "$task_phase" == admin ]]; then run_simulation AdminSimulation admin-final; fi
if [[ "$task_phase" == all || "$task_phase" == mixed ]]; then run_simulation MixedSimulation mixed-final; fi
if [[ "$task_phase" == all || "$task_phase" == changes ]]; then
  # 第 1 部门整个范围会被取消，不能把同部门其他员工放入恒定允许的背景断言。
  node --input-type=module -e 'import fs from "node:fs"; const lines=fs.readFileSync(".runtime/load/tokens.csv","utf8").trim().split("\n"); fs.writeFileSync(".runtime/load/change-tokens.csv",lines.filter((r,i)=>i===0||Number(r.split(",")[0])>28).join("\n")+"\n");'
  run_simulation ChangeSimulation changes-background &
  task_background="$!"
  # 等到 Gatling 真正发起请求，排除 JVM 编译和启动时间。
  for task_attempt in {1..60}; do
    if rg -q 'active:|Requests' .runtime/load-changes-background.log 2>/dev/null; then break; fi
    if ! kill -0 "$task_background" 2>/dev/null; then wait "$task_background"; exit 1; fi
    sleep 1
  done
  task_changes_exit=0
  node scripts/verify-changes.mjs > .runtime/change-deadlines-console.txt 2>&1 || task_changes_exit="$?"
  wait "$task_background"
  cp .runtime/change-deadlines-console.txt docs/verification/performance/raw/change-deadlines-console.txt
  if [[ "$task_changes_exit" != 0 ]]; then cat .runtime/change-deadlines-console.txt; exit "$task_changes_exit"; fi
fi
printf '%s\n' '压测及原始结果已保存至 docs/verification/performance。'
