#!/usr/bin/env bash
# Week 08 end-to-end verification. Run from the repository root:
#   bash scripts/e2e-test.sh
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || exit 1

USER1="http://localhost:${USER_PORT:-8101}"
USER2="http://localhost:${USER2_PORT:-8111}"
PRODUCT="http://localhost:${PRODUCT_PORT:-8102}"
ORDER="http://localhost:${ORDER_PORT:-8103}"
ORDER_LIST_PATH="${E2E_ORDER_LIST_PATH:-/api/orders}"
FAIL_PRODUCT_AT="${E2E_FAIL_PRODUCT_AT:-0}"

TMP_DIR="$(mktemp -d)"
FAIL=0
CODE=""
LAST_COUNT=""
CREATED_ID=""
PRODUCT_SEQ=0
BODY_FILE="$TMP_DIR/body.json"
BUYERS=()
PRODUCTS=()

pass() { printf 'PASS  %s\n' "$1"; }
fail() { FAIL=$((FAIL + 1)); printf 'FAIL  %s\n' "$1" >&2; }

cleanup() {
  # Always restore the two user-service instances, even if a check failed.
  docker compose start user-service user-service-2 >/dev/null 2>&1 || true
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

request() {
  local method="$1" url="$2" data="${3-}"
  local args=(-sS -o "$BODY_FILE" -w '%{http_code}' -X "$method")
  if [ -n "$data" ]; then
    args+=(-H 'Content-Type: application/json' -d "$data")
  fi
  CODE="$(curl "${args[@]}" "$url" 2>"$TMP_DIR/curl.err")" || CODE="000"
  if [ -z "$CODE" ] || [ "$CODE" = "000" ]; then
    if [ "$method" = "GET" ]; then
      # 只对只读 GET 做一次传输层重试。
      sleep 1
      CODE="$(curl "${args[@]}" "$url" 2>"$TMP_DIR/curl.err")" || CODE="000"
    else
      if [ "${E2E_EXPECT_POST_DISCONNECT:-0}" = "1" ]; then
        printf 'EXPECTED  %s %s 连接中断：本次写入结果未知，不自动重发\n' "$method" "$url" >&2
      else
        fail "$method $url 传输失败：本次写入结果未知，不自动重发"
      fi
      exit 1
    fi
  fi
  if [ -z "$CODE" ]; then CODE="000"; fi
  if [ -s "$TMP_DIR/curl.err" ]; then
    printf 'curl: %s\n' "$(cat "$TMP_DIR/curl.err")" >&2
  fi
}

json_field() {
  python -c "import json,sys; print(json.load(open(sys.argv[1], encoding='utf-8')).get(sys.argv[2], ''))" \
    "$BODY_FILE" "$1" 2>/dev/null
}

# Require HTTP 200 and a JSON array; never treat an error object as an order list.
order_count() {
  request GET "$ORDER$ORDER_LIST_PATH"
  if [ "$CODE" != "200" ]; then
    fail "订单列表接口返回 HTTP $CODE，不能继续比较订单数"
    return 1
  fi
  LAST_COUNT="$(python -c "import json,sys; d=json.load(open(sys.argv[1], encoding='utf-8')); assert isinstance(d,list); print(len(d))" "$BODY_FILE" 2>/dev/null)" || {
    fail "订单列表响应不是 JSON 数组"
    return 1
  }
  if ! [[ "$LAST_COUNT" =~ ^[0-9]+$ ]]; then
    fail "订单列表数量不是整数：$LAST_COUNT"
    return 1
  fi
}

# Verify the failed order did not appear for this exact buyer/product pair.
no_order_for() {
  local buyer="$1" product="$2" matches
  request GET "$ORDER$ORDER_LIST_PATH"
  if [ "$CODE" != "200" ]; then
    fail "核对失败订单时订单列表返回 HTTP $CODE"
    return 1
  fi
  matches="$(python -c "import json,sys; d=json.load(open(sys.argv[1], encoding='utf-8')); assert isinstance(d,list); print(sum(1 for o in d if o.get('buyerId')==sys.argv[2] and o.get('productId')==sys.argv[3]))" \
    "$BODY_FILE" "$buyer" "$product" 2>/dev/null)" || {
    fail "核对失败订单时响应不是 JSON 数组"
    return 1
  }
  if [ "$matches" != "0" ]; then
    fail "失败场景后仍存在匹配订单（buyer=$buyer product=$product count=$matches）"
    return 1
  fi
}

expect_code() {
  if [ "$CODE" = "$1" ]; then
    pass "$2"
  else
    fail "$2（期望 HTTP $1，实际 $CODE：$(cat "$BODY_FILE")）"
  fi
}

wait_health() {
  local name="$1" base="$2" i
  for i in $(seq 1 60); do
    if [ "$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "$base/actuator/health" 2>/dev/null)" = "200" ]; then
      pass "$name 健康接口 200"
      return 0
    fi
    sleep 2
  done
  fail "$name 未在 120 秒内健康"
  return 1
}

create_user() {
  request POST "$USER1/api/users" "{\"name\":\"$1\"}"
  if [ "$CODE" != "201" ]; then
    fail "创建用户失败：$CODE $(cat "$BODY_FILE")"
    return 1
  fi
  CREATED_ID="$(json_field id)"
  if [ -z "$CREATED_ID" ]; then
    fail "创建用户响应没有 id"
    return 1
  fi
}

create_product() {
  PRODUCT_SEQ=$((PRODUCT_SEQ + 1))
  if [ "$FAIL_PRODUCT_AT" != "0" ] && [ "$PRODUCT_SEQ" = "$FAIL_PRODUCT_AT" ]; then
    fail "故障注入：第 $PRODUCT_SEQ 件商品创建失败"
    return 1
  fi
  request POST "$PRODUCT/api/products" "{\"title\":\"$1\",\"description\":\"week08 e2e\",\"price\":25.50}"
  if [ "$CODE" != "201" ]; then
    fail "创建商品失败（第 $PRODUCT_SEQ 件）：$CODE $(cat "$BODY_FILE")"
    return 1
  fi
  CREATED_ID="$(json_field id)"
  if [ -z "$CREATED_ID" ]; then
    fail "创建商品响应没有 id"
    return 1
  fi
}

create_order() {
  request POST "$ORDER/api/orders" "{\"buyerId\":\"$1\",\"productId\":\"$2\"}"
}

# 仅用于验证脚本本身：模拟“服务端收到 POST 后断开连接”，
# 要求脚本不自动重发 POST，且服务端只收到 1 次请求。
if [ "${E2E_SELF_TEST:-}" = "post-disconnect" ]; then
  PORT=$((20000 + RANDOM % 10000))
  COUNT_FILE="$TMP_DIR/post-count"
  cat > "$TMP_DIR/close_server.py" <<'PY'
import http.server
import socketserver
import socket
import sys


class Handler(http.server.BaseHTTPRequestHandler):
    def do_POST(self):
        length = int(self.headers.get('Content-Length') or 0)
        self.rfile.read(length)
        with open(sys.argv[1], 'a', encoding='utf-8') as stream:
            stream.write('POST\n')
        try:
            self.connection.shutdown(socket.SHUT_RDWR)
        except OSError:
            pass
        self.connection.close()

    def log_message(self, *args):
        pass


with socketserver.TCPServer(('127.0.0.1', int(sys.argv[2])), Handler) as server:
    server.serve_forever()
PY
  python "$TMP_DIR/close_server.py" "$COUNT_FILE" "$PORT" &
  SERVER_PID=$!
  for attempt in $(seq 1 30); do
    probe="$(curl -s -o /dev/null -w '%{http_code}' --max-time 1 "http://127.0.0.1:$PORT/" 2>/dev/null || true)"
    if [ -n "$probe" ] && [ "$probe" != "000" ]; then break; fi
    sleep 0.2
  done
  ( export E2E_EXPECT_POST_DISCONNECT=1; request POST "http://127.0.0.1:$PORT/api/orders" '{"buyerId":"self-test","productId":"self-test"}' )
  REQUEST_RC=$?
  kill "$SERVER_PID" >/dev/null 2>&1 || true
  wait "$SERVER_PID" 2>/dev/null || true
  COUNT="$(wc -l < "$COUNT_FILE" 2>/dev/null | tr -d ' ' || echo 0)"
  if [ "$REQUEST_RC" -ne 0 ] && [ "$COUNT" = "1" ]; then
    echo "PASS  POST 断连时脚本非零退出，服务端只收到 1 次 POST"
    exit 0
  fi
  echo "FAIL  POST 断连自测：exit=$REQUEST_RC count=$COUNT" >&2
  exit 1
fi

echo "===== 1. 两个实例健康检查 ====="
if ! wait_health "user-service-1" "$USER1"; then exit 1; fi
if ! wait_health "user-service-2" "$USER2"; then exit 1; fi
if ! wait_health "product-service" "$PRODUCT"; then exit 1; fi
if ! wait_health "order-service" "$ORDER"; then exit 1; fi

echo "===== 2. 正常下单，并收集买家 ID ====="
STAMP="$(date +%s)"
for i in 1 2 3 4 5 6; do
  if ! create_user "Week08 Buyer $STAMP-$i"; then exit 1; fi
  buyer="$CREATED_ID"
  if ! create_product "Week08 textbook $STAMP-$i"; then exit 1; fi
  product="$CREATED_ID"

  create_order "$buyer" "$product"
  expect_code 201 "第 $i 笔正常下单"
  if [ "$CODE" != "201" ]; then exit 1; fi

  order_buyer="$(json_field buyerId)"
  order_product="$(json_field productId)"
  order_amount="$(json_field amount)"
  order_status="$(json_field status)"
  if [ "$order_buyer" != "$buyer" ] || [ "$order_product" != "$product" ] \
      || [ "$order_status" != "PENDING_PAYMENT" ] \
      || { [ "$order_amount" != "25.5" ] && [ "$order_amount" != "25.50" ]; }; then
    fail "第 $i 笔订单响应字段不正确：buyer=$order_buyer product=$order_product amount=$order_amount status=$order_status"
    exit 1
  fi
  pass "第 $i 笔订单 buyerId/productId/金额/状态正确"

  request GET "$PRODUCT/api/products/$product"
  expect_code 200 "第 $i 件商品查询"
  if [ "$CODE" != "200" ]; then exit 1; fi
  status="$(json_field status)"
  if [ "$status" != "RESERVED" ]; then
    fail "第 $i 件商品状态应为 RESERVED，实际 $status"
    exit 1
  fi
  pass "第 $i 件商品状态为 RESERVED"

  BUYERS+=("$buyer")
  PRODUCTS+=("$product")
done

if [ "${#BUYERS[@]}" -ne 6 ]; then
  fail "正常流程只完成 ${#BUYERS[@]} 笔，要求恰好 6 笔"
  exit 1
fi

echo "===== 3. 从 user-service 日志核对两个实例标识 ====="
sleep 2
LOG_FILE="$TMP_DIR/user-service.log"
docker compose logs --no-color user-service user-service-2 >"$LOG_FILE" 2>/dev/null || true
SAW_ONE=0
SAW_TWO=0
for buyer in "${BUYERS[@]}"; do
  if grep -Fq "USER_LOOKUP instance=user-service-1 buyerId=$buyer" "$LOG_FILE"; then
    SAW_ONE=1
    grep -F "USER_LOOKUP instance=user-service-1 buyerId=$buyer" "$LOG_FILE" | head -n 1
  fi
  if grep -Fq "USER_LOOKUP instance=user-service-2 buyerId=$buyer" "$LOG_FILE"; then
    SAW_TWO=1
    grep -F "USER_LOOKUP instance=user-service-2 buyerId=$buyer" "$LOG_FILE" | head -n 1
  fi
done
if [ "$SAW_ONE" -eq 1 ] && [ "$SAW_TWO" -eq 1 ]; then
  pass "同一轮下单中两个 user-service 实例都处理过买家查询"
else
  fail "未同时观察到 user-service-1 和 user-service-2 的 USER_LOOKUP 日志"
  echo "--- 本次买家 ID ---"
  printf '%s\n' "${BUYERS[@]}"
  echo "--- 最近的 USER_LOOKUP 日志 ---"
  grep -F "USER_LOOKUP" "$LOG_FILE" | tail -n 20 || true
  exit 1
fi

echo "===== 4. 两个 user-service 实例都不可用 ====="
if ! create_product "Week08 failure guard $STAMP"; then exit 1; fi
failure_product="$CREATED_ID"
if ! order_count; then exit 1; fi
before="$LAST_COUNT"

if ! docker compose stop user-service user-service-2 >/dev/null 2>&1; then
  fail "停止两个 user-service 实例失败"
  exit 1
fi
sleep 2
if ! running="$(docker compose ps --status running -q user-service user-service-2)"; then
  fail "无法查询用户服务容器状态，停止验证"
  exit 1
fi
if [ -n "$running" ]; then
  fail "stop 命令后仍有 user-service 容器在运行：$running"
  exit 1
fi
pass "已确认两个 user-service 容器都已停止"
create_order "${BUYERS[0]}" "$failure_product"
expect_code 503 "两个 user-service 实例停止后下单返回 503"
if [ "$CODE" != "503" ]; then exit 1; fi
if printf '%s' "$(cat "$BODY_FILE")" | grep -Eq 'unavailable|timed out|No available instance'; then
  pass "503 响应明确说明依赖不可用"
else
  fail "503 响应缺少明确的依赖不可用说明"
  exit 1
fi
if ! order_count; then exit 1; fi
after="$LAST_COUNT"
if [ "$before" = "$after" ]; then
  pass "依赖失败没有新增订单（$before -> $after）"
else
  fail "依赖失败后订单数变化（$before -> $after）"
  exit 1
fi
if ! no_order_for "${BUYERS[0]}" "$failure_product"; then exit 1; fi
pass "失败场景没有与本次买家/商品匹配的订单"

request GET "$PRODUCT/api/products/$failure_product"
if [ "$CODE" = "200" ] && [ "$(json_field status)" = "AVAILABLE" ]; then
  pass "依赖失败后商品仍为 AVAILABLE"
else
  fail "依赖失败后商品状态异常：$CODE $(cat "$BODY_FILE")"
  exit 1
fi

echo "===== 5. 恢复两个实例，只读确认就绪后只下一次单 ====="
if ! docker compose start user-service user-service-2 >/dev/null 2>&1; then
  fail "恢复两个 user-service 实例失败"
  exit 1
fi
if ! wait_health "user-service-1" "$USER1"; then exit 1; fi
if ! wait_health "user-service-2" "$USER2"; then exit 1; fi

REGISTERED=0
for attempt in $(seq 1 30); do
  registry="$(python scripts/nacos-admin.py 2>/dev/null | tr -d '\r' || true)"
  count="$(printf '%s\n' "$registry" | grep -cE '^user-service: .*healthy=True enabled=True$' || true)"
  if [ "$count" -ge 2 ]; then
    REGISTERED=1
    break
  fi
  sleep 2
done
if [ "$REGISTERED" -ne 1 ]; then
  fail "两个 user-service 实例未在 Nacos 以 healthy=True enabled=True 重新注册"
  exit 1
fi
pass "Nacos 已重新注册两个 healthy=True enabled=True 的 user-service 实例"

for base in "$USER1" "$USER2"; do
  code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$base/api/users/${BUYERS[0]}" 2>/dev/null || true)"
  if [ "$code" = "200" ]; then
    pass "只读买家查询确认 $base 就绪"
  else
    fail "只读买家查询 $base 返回 $code"
    exit 1
  fi
done

if ! create_product "Week08 recovery guard $STAMP"; then exit 1; fi
recovery_product="$CREATED_ID"
create_order "${BUYERS[0]}" "$recovery_product"
expect_code 201 "恢复后下单返回 201"
if [ "$CODE" != "201" ]; then exit 1; fi
request GET "$PRODUCT/api/products/$recovery_product"
if [ "$CODE" = "200" ] && [ "$(json_field status)" = "RESERVED" ]; then
  pass "恢复后商品为 RESERVED"
else
  fail "恢复后商品状态异常：$CODE $(cat "$BODY_FILE")"
  exit 1
fi

echo
if [ "$FAIL" -eq 0 ]; then
  echo "===== PASS：跨服务下单、双实例分发、依赖失败和恢复全部通过 ====="
  exit 0
fi
echo "===== FAIL：$FAIL 项检查失败 ====="
exit 1
