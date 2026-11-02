#!/usr/bin/env bash
# Run from the repository root: bash scripts/smoke-test.sh
# Checks the Nacos console, all three Actuator health endpoints, and the real
# cross-service order flow including user-service stop/recovery.
set -euo pipefail

CONSOLE="${NACOS_CONSOLE_URL:-http://localhost:18080}"
USER_BASE="${USER_SERVICE_URL:-http://localhost:8101}"
PRODUCT_BASE="${PRODUCT_SERVICE_URL:-http://localhost:8102}"
ORDER_BASE="${ORDER_SERVICE_URL:-http://localhost:8103}"

check_health() {
  local name="$1" base="$2" body=""
  for attempt in $(seq 1 60); do
    if body=$(curl --connect-timeout 2 --max-time 5 -fsS "$base/actuator/health" 2>/dev/null) \
        && printf '%s' "$body" | grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"'; then
      printf '%s GET %s/actuator/health -> HTTP 200\n%s\n' "$name" "$base" "$body"
      return 0
    fi
    sleep 2
  done
  printf 'FAIL: %s health check at %s\n' "$name" "$base" >&2
  return 1
}

console_code="$(curl -sL --connect-timeout 3 --max-time 8 -o /dev/null -w '%{http_code}' "$CONSOLE/" || true)"
if [ "$console_code" != "200" ]; then
  printf 'FAIL: Nacos console %s returned HTTP %s\n' "$CONSOLE" "$console_code" >&2
  exit 1
fi
printf 'Nacos console %s/ -> HTTP %s\n' "$CONSOLE" "$console_code"

check_health user-service "$USER_BASE"
check_health product-service "$PRODUCT_BASE"
check_health order-service "$ORDER_BASE"

"${PYTHON:-python}" scripts/verify-flow.py --dependency-failure \
  --output docs/homework/week-07/verification.json

printf 'PASS: Nacos console, three health endpoints, order flow, outage and recovery\n'
