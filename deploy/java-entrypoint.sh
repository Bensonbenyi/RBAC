#!/usr/bin/env bash
set -euo pipefail
export DB_PASSWORD="$(cat /run/secrets/db_password)"
if [ -f /run/secrets/demo_phone ]; then export APP_DEMO_PHONE="$(cat /run/secrets/demo_phone)"; fi
exec java -jar /app/application.jar
