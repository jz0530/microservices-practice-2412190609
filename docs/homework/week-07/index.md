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

`TradeClient` 注入 `DiscoveryClient`，每次下单时获取 DiscoveryClient 当前已知的实例列表（SDK 可能缓存注册信息）：

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

最近一次验证时间：2026-11-05T08:42:35+0800。买家 `4dbd9882-6486-4fdd-a52f-20d363d6bc55`，商品 `5c6ce416-0fbd-4cce-be64-aaf3d1b80de7`，订单 `21803289-5513-41ef-a150-651211da6b54`。下文 11 月 3 日复核记录为历史验证，11 月 5 日截图对应第 10 节。

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

以下 13 张用户实拍截图已逐项核对。

| 文件 | 内容 |
| --- | --- |
| [01-compose-ps.png](screenshots/01-compose-ps.png) | Nacos、三个应用、三个 MySQL 容器运行 |
| [02-nacos-register.png](screenshots/02-nacos-register.png) | PERSONAL_GROUP 三个服务及健康实例数 |
| [02a-user-instance.png](screenshots/02a-user-instance.png) | user-service 实例地址及健康状态 |
| [02b-product-instance.png](screenshots/02b-product-instance.png) | product-service 实例地址及健康状态 |
| [02c-order-instance.png](screenshots/02c-order-instance.png) | order-service 实例地址及健康状态 |
| [03-discovery-code.png](screenshots/03-discovery-code.png) | DiscoveryClient 获取实例、空列表处理及 HTTP 调用 |
| [03-smoke-test.png](screenshots/03-smoke-test.png) | 完整脚本 PASS |
| [tests.png](screenshots/tests.png) | 11 月 5 日订单服务 12 项测试通过，Failures=0、Errors=0、BUILD SUCCESS |
| [discovery-address.png](screenshots/discovery-address.png) | 实际发现的用户与商品服务地址 |
| [health.png](screenshots/health.png) | 三个健康接口 HTTP 200 / UP |
| [order-success.png](screenshots/order-success.png) | 正常下单 201，商品 RESERVED |
| [outage-503.png](screenshots/outage-503.png) | 服务停止、Nacos 空实例及下单 503 |
| [recovery-success.png](screenshots/recovery-success.png) | 恢复后下单 201，商品 RESERVED |

提交对应：第 1 题使用容器、注册列表和三个实例详情（5 张）；第 2 题使用代码、发现地址和正常下单（3 张）；第 3 题使用健康、下线失败和恢复成功（3 张）；第 4 题使用 03-smoke-test.png 与 tests.png。

## 9. 2026-11-03 复核记录

本次从仓库根目录运行 `bash scripts/smoke-test.sh`，退出码 0。验证脚本新增注册列表检查，等待服务下线及恢复的状态收敛，只记录实例地址与状态，不记录凭据。

| 阶段 | 服务 | Nacos 实例 |
| --- | --- | --- |
| before | user-service | 172.27.0.7:8080 (healthy=True, enabled=True) |
| before | product-service | 172.27.0.8:8080 (healthy=True, enabled=True) |
| before | order-service | 172.27.0.6:8080 (healthy=True, enabled=True) |
| stopped | user-service | 无实例 |
| recovered | user-service | 172.27.0.7:8080 (healthy=True, enabled=True) |

订单数：开始 6，正常下单后 7，依赖失败后保持 7，恢复下单后 8。失败商品 ID 为 `5724e042-1fda-4234-a243-0cf9ff37af06`，失败时 AVAILABLE，恢复下单后 RESERVED。

负向检查：将 `NACOS_CONSOLE_URL` 临时设为 `http://127.0.0.1:1`，脚本报告不可访问并以 1 退出；该临时变量没有写入项目配置。

11 月 3 日复核时尚未归档截图；目前已归档，见第 8 节。

## 10. 2026-11-05 截图对应的最终验证

验证时间 `2026-11-05T08:42:35+0800`，分组 PERSONAL_GROUP，命名空间 public。正常订单 `21803289-5513-41ef-a150-651211da6b54`，商品 `5c6ce416-0fbd-4cce-be64-aaf3d1b80de7`，金额 25.50；失败及恢复测试使用另一商品 `436c5176-2dda-4ed0-8e1b-d325fbcfb6e7`，金额 12.00。两条流程不要混淆。

订单数：开始 8，首次成功后 9，下线失败后仍为 9，恢复成功后 10。原始请求、响应及各阶段注册列表见 [verification.json](verification.json)。

- before / user-service：172.27.0.6:8080。
- before / product-service：172.27.0.7:8080。
- before / order-service：172.27.0.8:8080。
- stopped / user-service：无实例。
- recovered / user-service：172.27.0.6:8080。

11 月 3 日保存的完整终端输出为历史记录（smoke-test-output.txt）；11 月 5 日脚本结果以 verification.json 和 03-smoke-test.png 为准。服务重启后容器 IP 变化属于正常现象。
