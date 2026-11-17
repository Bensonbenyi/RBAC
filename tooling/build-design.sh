#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 docs/redesign/tools/draw_v33.py
python3 docs/redesign/tools/draw_data_v33.py
python3 docs/redesign/tools/write_v33.py
node docs/redesign/tooling/audit-v33.mjs
node docs/redesign/tooling/export.mjs
python3 docs/redesign/tools/bundle.py
