#!/usr/bin/env bash
# 使用真实 MySQL 重跑后端覆盖率；--baseline 仅用于测量，明确跳过 100% 门禁。
set -euo pipefail
cd "$(dirname "$0")/.."
skip_check=false
if [[ "${1:-}" == "--baseline" ]]; then
  skip_check=true
elif [[ $# -gt 0 ]]; then
  echo '用法：scripts/coverage.sh [--baseline]' >&2
  exit 2
fi
mkdir -p .runtime/coverage
date -u +%Y-%m-%dT%H:%M:%SZ > .runtime/coverage/started-at.txt
git rev-parse HEAD > .runtime/coverage/source-commit.txt
git diff HEAD -- pom.xml platform demo-client coverage-report scripts/coverage.sh \
  | shasum -a 256 > .runtime/coverage/source-diff-sha256.txt
finish() {
  result=$?
  trap - EXIT
  python3 scripts/report-coverage.py "$result" "$skip_check"
  exit "$result"
}
trap finish EXIT
docker compose up -d --wait mysql
./mvnw -B -pl coverage-report -am clean
./mvnw -B -pl coverage-report -am verify -Dcoverage.skip-check="$skip_check"
