#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
version="$(cat docs/redesign/VERSION)"
npm ci --prefix docs/api
python3 tooling/build-api.py
test -f backend/target/site/jacoco/index.html
for service in backend course frontend; do
  image="$service"
  if [ "$service" = frontend ]; then image=web; fi
  tag="rbac/$image:$version"
  build_args=(--platform linux/amd64)
  if [ "$service" = backend ]; then build_args+=(--build-arg "RBAC_VERSION=$version"); fi
  docker build "${build_args[@]}" -t "$tag" -f "deploy/Dockerfile.$service" .
  test "$(docker image inspect "$tag" --format '{{.Architecture}}')" = amd64
done
# Pin the amd64 manifest, including when the builder itself runs on Apple Silicon.
mysql_image='docker.m.daocloud.io/library/mysql@sha256:72c368d898861f3b3a07af0e6d690400f44f048799ed46822783d9224cc2c86d'
docker pull "$mysql_image"
docker tag "$mysql_image" rbac/mysql:8.4.13-amd64
test "$(docker image inspect rbac/mysql:8.4.13-amd64 --format '{{.Architecture}}')" = amd64
