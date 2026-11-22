#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
course_dir=.integrations/course-project-mgt
{
  IFS= read -r course_url
  IFS= read -r course_ref
} < integrations/course-project-mgt/UPSTREAM
if [ ! -d "$course_dir/.git" ]; then
  git clone "$course_url" "$course_dir"
fi
if [ "$(git -C "$course_dir" rev-parse HEAD)" != "$course_ref" ]; then
  if [ -n "$(git -C "$course_dir" status --porcelain)" ]; then
    echo 'Move the previous patched course checkout to a separate directory before preparing a new revision.' >&2
    exit 1
  fi
  git -C "$course_dir" fetch origin "$course_ref"
  git -C "$course_dir" checkout --detach "$course_ref"
fi
patch_file="$PWD/integrations/course-project-mgt/patches/0001-rbac-integration.patch"
if git -C "$course_dir" apply --reverse --check "$patch_file" 2>/dev/null; then exit 0; fi
git -C "$course_dir" apply --check "$patch_file"
git -C "$course_dir" apply "$patch_file"
