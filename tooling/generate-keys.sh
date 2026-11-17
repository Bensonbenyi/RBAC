#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p .runtime
if [ -f .runtime/private.pem ]; then echo 'Existing signing key retained'; exit 0; fi
umask 077
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out .runtime/private.pem
openssl pkey -in .runtime/private.pem -pubout -out .runtime/public.pem
