#!/usr/bin/env bash
set -euo pipefail
export DB_PASSWORD="$(cat /run/secrets/db_password)"
exec java -jar /app/application.jar
