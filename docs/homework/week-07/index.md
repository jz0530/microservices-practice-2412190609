# 第七周：Nacos 服务注册与发现

项目：校园二手交易与担保履约平台。继续使用第六周拆分的 user-service、product-service、order-service，将一条跨服务下单流程从固定地址改为通过 Nacos `DiscoveryClient` 获取实例地址。

- 设计说明：[docs/microservices-design.md](../../microservices-design.md)
- 服务代码：`services/user-service`、`services/product-service`、`services/order-service`
- 编排：根目录 `compose.yaml`
- 验证脚本：`scripts/smoke-test.sh`、`scripts/verify-flow.py`、`scripts/nacos-admin.py`
- 分组：`PERSONAL_GROUP`；命名空间：`public`
- Nacos 控制台：<http://localhost:18080>（容器 8080，服务端 8848，SDK 9848）

## 1. Nacos 部署与初始化

根目录 `compose.yaml` 增加 `nacos/nacos-server:v3.2.4`：

- standalone 模式，开启鉴权、控制台鉴权和管理员鉴权；
- 数据保存在命名卷 `cyy-trade-week07_nacos-data`；
- 控制台映射 `127.0.0.1:18080:8080`，服务端 `127.0.0.1:8848:8848`，SDK gRPC `127.0.0.1:9848:9848`；
- 健康检查使用容器内 `curl -fsS http://localhost:8080/`；
- 三个业务服务通过 `depends_on: condition: service_healthy` 等待 Nacos 健康后再启动，避免注册竞态。

管理员初始化：

```bash
docker compose up -d nacos
python scripts/nacos-admin.py init
```

实际输出：

```text
Nacos administrator initialized; password remains only in local .env.
```

再次执行会输出 `Nacos administrator already initialized; login verified.`，用于确认密码仍与 `.env` 一致。真实密码、Token、Server identity 只在本地 `.env`；`.env.example` 只有占位符。

## 2. 服务注册结果

三个服务都使用 `spring.application.name` 作为注册名，并统一配置：

```yaml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: ${NACOS_SERVER_ADDR:localhost:8848}
        username: ${NACOS_USERNAME:nacos}
        password: ${NACOS_PASSWORD:}
        group: PERSONAL_GROUP
```

验证时 `python scripts/nacos-admin.py` 输出：

```text
Nacos console: http://localhost:18080 | namespace=public | group=PERSONAL_GROUP
user-service: 172.27.0.7:8080 healthy=True enabled=True
product-service: 172.27.0.8:8080 healthy=True enabled=True
order-service: 172.27.0.6:8080 healthy=True enabled=True
```

三个服务注册在 `public` / `PERSONAL_GROUP`，与课堂项目的 `COURSE_GROUP` 隔离。Nacos 看到的是容器内 8080 端口；宿主机 8101、8102、8103 只用于本地请求。容器 IP 每次重建可能变化，以当次控制台为准。

## 3. 服务发现与业务调用

`TradeClient` 注入 `DiscoveryClient`，每次下单时重新查询实例：

```java
var instances = discovery.getInstances(serviceName);
if (instances.isEmpty()) {
    throw new ApiFailure(HttpStatus.SERVICE_UNAVAILABLE,
            "No available instance: " + serviceName);
}
String address = instances.getFirst().getUri().toString();
```

然后把原业务路径拼到实例 URI 上：

- `GET {user-service实例}/api/users/{id}`
- `POST {product-service实例}/api/products/{id}/reservations`

固定配置 `clients.user-url`、`clients.product-url` 以及 Compose 中的 `USER_SERVICE_URL`、`PRODUCT_SERVICE_URL` 已删除。连接超时 2 秒、读取超时 3 秒保留。

实际 order-service 日志：

```text
Nacos discovery: user-service -> http://172.27.0.7:8080
Nacos discovery: product-service -> http://172.27.0.8:8080
```

实际业务结果：

| 请求 | 状态码 | 数据变化 |
| --- | --- | --- |
| `POST :8101/api/users` | 201 | 创建买家 |
| `POST :8102/api/products` | 201 | 创建 AVAILABLE 商品，价格 25.50 |
| `POST :8103/api/orders` | 201 | 商品变 RESERVED，订单金额 25.50 |
| `GET :8103/api/orders/{id}` | 200 | 返回同一订单 |
| 同一商品再次下单 | 409 | 订单数不变 |
| 不存在的买家 | 404 | 不写订单，商品仍 AVAILABLE |
| 不存在的商品 | 404 | 不写订单 |

本次验证 ID：买家 `784efa4c-159a-4f38-99f0-cecb591fe426`，商品 `b61983f2-2648-433e-b4eb-81c4724793a0`，订单 `97157b70-8869-4bc8-ae15-01670461390b`。

## 4. 正常、下线与恢复验证

执行：

```bash
bash scripts/smoke-test.sh
```

脚本先检查 Nacos 控制台和三个健康接口，再执行真实下单、重复 409、未知用户/商品 404，然后停止 user-service 验证 503，最后恢复并再次下单。

实际结果：

```text
停止 user-service 后：
POST http://localhost:8103/api/orders -> HTTP 503
{"detail":"No available instance: user-service", ...}
订单数不增加，商品保持 AVAILABLE

恢复 user-service 后：
POST http://localhost:8103/api/orders -> HTTP 201
订单写入，商品变为 RESERVED
```

`docs/homework/week-07/verification.json` 记录：

```text
dependencyFailure: HTTP 503, no local order written, product still AVAILABLE
recovery: HTTP 201 after user-service re-registered (attempt 1); order written and product RESERVED
```

完整脚本输出见 `smoke-test-output.txt`。

## 5. 健康状态的区别

- **SDK 连接存活**：Nacos Client 与 Nacos 服务端的长连接/心跳正常，只说明客户端能通信。
- **实例状态**：Nacos 控制台显示实例 `healthy=True, enabled=True`，表示注册中心当前认为该实例可用。
- **Actuator 应用健康**：`/actuator/health` 返回 `UP`，反映应用自身及数据库等组件状态。

三者不等价。Actuator 返回 DOWN 不会自动让 Nacos 摘除实例；本周未实现自动摘除。服务发现也不消除跨服务写操作的部分成功窗口，不实现分布式事务、自动补偿、重试或熔断。

## 6. 测试与脚本

三个服务测试均使用 H2，关闭 Nacos 自动注册，不连接真实 Nacos 或 MySQL。order-service 使用模拟 `DiscoveryClient` 指向本地 HTTP 测试服务，覆盖：

- 正常发现地址下单、金额取商品服务返回值；
- 用户/商品不存在 404；
- 商品已占用 409；
- 连接失败、读取超时 503；
- user-service / product-service 实例列表为空 503；
- 实例列表变化后重新寻址；
- 失败场景订单数不变。

本机实际测试：user-service 4 项、product-service 5 项、order-service 12 项，共 21 项通过，Failures=0、Errors=0。

命令：

```bash
(cd services/user-service && mvn test)
(cd services/product-service && mvn test)
(cd services/order-service && mvn test)
```

`scripts/smoke-test.sh` 从仓库根目录执行；Nacos 控制台不可访问、任一健康接口失败、业务/下线/恢复检查失败都会返回非零退出码。`scripts/start-week07.sh` 完成 Nacos 启动、管理员初始化、JAR 构建和 Compose 启动。

## 7. 已知限制

- Nacos 使用 standalone + 命名卷，满足本周初始化和持久化要求；没有改为 MySQL 存储。
- 只取实例列表中的第一个实例，不实现轮询、权重、重试、熔断。
- 服务发现只解决“地址从哪来”，不解决商品占用成功而订单保存失败的事务窗口。
- 容器 IP 会随重建变化，截图中的实例 IP 以当次运行为准；不要把它写死到配置或代码。

## 8. 截图清单

截图由用户自行完成，放在 `docs/homework/week-07/screenshots/`。

| 文件名 | 内容 |
| --- | --- |
| 01-compose-ps.png | `docker compose ps`，Nacos、三个应用、三个 MySQL 状态 |
| 02-nacos-register.png | Nacos 控制台 `public` / `PERSONAL_GROUP` 下三个服务及实例 IP:端口 |
| 03-discovery-code.png | `TradeClient` 中 `getInstances` 与使用实例 URI 的 HTTP 调用 |
| 04-order-success.png | 下单 201、商品 RESERVED、订单金额来自商品服务 |
| 05-health.png | 三个 `/actuator/health` 返回 200/UP |
| 06-outage-503.png | 停止 user-service 后下单 503，订单数不增加 |
| 07-recovery-201.png | 恢复 user-service 后重新下单 201 |
| 08-smoke-test.png | `bash scripts/smoke-test.sh` 全流程 PASS |

作业系统填写文字见 [submission.md](submission.md)。
