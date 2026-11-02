#!/usr/bin/env bash
# One-click week 07 startup: Nacos, administrator init, service jars, Compose.
# Run from the repository root as: bash scripts/start-week07.sh
set -euo pipefail

if [ ! -f .env ]; then
  echo "缺少 .env。请先执行 cp .env.example .env，并填写数据库与 Nacos 的本地密码/Token。" >&2
  exit 1
fi

MVN="${MVN:-mvn}"

echo "1/4 启动 Nacos 并等待健康 ..."
docker compose up -d nacos
python scripts/nacos-admin.py init

echo "2/4 构建三个服务 JAR ..."
for service in user-service product-service order-service; do
  (cd "services/$service" && "$MVN" -B -ntp -DskipTests package)
done

echo "3/4 构建并启动业务服务 ..."
docker compose up -d --build

echo "4/4 等待三个健康接口 ..."
for attempt in $(seq 1 90); do
  user_code="$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8101/actuator/health || true)"
  product_code="$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8102/actuator/health || true)"
  order_code="$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8103/actuator/health || true)"
  if [ "$user_code" = "200" ] && [ "$product_code" = "200" ] && [ "$order_code" = "200" ]; then
    echo "三个服务已健康。Nacos 控制台: http://localhost:18080"
    echo "下一步: bash scripts/smoke-test.sh"
    exit 0
  fi
  sleep 2
done

echo "服务未在 180 秒内全部健康，请查看: docker compose logs --tail=100" >&2
exit 1
