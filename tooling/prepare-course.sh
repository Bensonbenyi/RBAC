#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
course_dir=.integrations/course-project-mgt
course_ref=c96efa2ee7f760d126a4476ce5e4772ff64ebe11
if [ ! -d "$course_dir/.git" ]; then
 git clone https://gitee.com/mingqcn/course-project-mgt.git "$course_dir"
 git -C "$course_dir" checkout --detach "$course_ref"
fi
[ "$(git -C "$course_dir" rev-parse HEAD)" = "$course_ref" ] || { echo 'Course upstream revision mismatch' >&2; exit 1; }
patch_file="$PWD/integrations/course-project-mgt/patches/0001-rbac-integration.patch"
if git -C "$course_dir" apply --reverse --check "$patch_file" 2>/dev/null; then exit 0; fi
git -C "$course_dir" apply --check "$patch_file"
git -C "$course_dir" apply "$patch_file"
