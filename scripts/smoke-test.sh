#!/usr/bin/env bash
set -euo pipefail
# Run from the repository root: bash scripts/smoke-test.sh
check() {
  local name="$1" base="$2" body=""
  for attempt in {1..30}; do
    if body=$(curl --connect-timeout 2 --max-time 5 -fsS "$base/actuator/health" 2>/dev/null)         && printf '%s' "$body" | grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"'; then
      printf '%s GET %s/actuator/health -> HTTP 200\n%s\n' "$name" "$base" "$body"
      return 0
    fi
    sleep 2
  done
  printf 'FAIL: %s health check at %s\n' "$name" "$base" >&2
  return 1
}
check user-service "${USER_SERVICE_URL:-http://localhost:8101}"
check product-service "${PRODUCT_SERVICE_URL:-http://localhost:8102}"
check order-service "${ORDER_SERVICE_URL:-http://localhost:8103}"
printf 'PASS: all three services are healthy\n'
