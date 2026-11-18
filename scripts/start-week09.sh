#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export PYTHONIOENCODING=utf-8
PYTHON="${PYTHON:-python}"
MVN="${MVN:-mvn}"
"$PYTHON" scripts/init-week09-env.py
docker compose up -d nacos user-mysql product-mysql order-mysql
"$PYTHON" scripts/nacos-admin.py init
for service in gateway-service user-service product-service order-service; do
  "$MVN" -B -ntp -f "services/$service/pom.xml" package
done
docker compose build gateway-service user-service product-service order-service
# Seed schema/accounts on instance 1 before starting instance 2 with the shared DB.
docker compose up -d user-service product-service order-service gateway-service
ready=0
for attempt in $(seq 1 90); do
  if "$PYTHON" scripts/wait-auth-ready.py; then ready=1; break; fi
  sleep 2
done
if [ "$ready" != 1 ]; then echo "user-service not ready; check docker compose logs" >&2; exit 1; fi
docker compose up -d user-service-2
echo "Gateway: http://localhost:8080 ; next: bash scripts/auth-test.sh"
