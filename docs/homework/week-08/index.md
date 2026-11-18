# 第八周：服务通信与负载均衡

项目：校园二手交易与担保履约平台。继续使用 user-service、product-service、order-service，保留包名 `com.zjgsu.cyy`、原有接口、各服务数据归属和已有业务规则。Nacos 分组为 `PERSONAL_GROUP`。

- 跨服务设计：[docs/service-communication.md](../../service-communication.md)
- 端到端脚本：`scripts/e2e-test.sh`
- 多实例编排：根目录 `compose.yaml`
- OpenFeign 代码：`services/order-service/src/main/java/com/zjgsu/cyy/client/`

## 1. 跨服务流程设计

创建订单流程不变：客户端向 order-service 发送 `POST /api/orders`，只提交 `buyerId` 和 `productId`。订单服务先通过 Feign 调用用户服务 `GET /api/users/{id}` 校验买家，再调用商品服务 `POST /api/products/{id}/reservations` 占用商品，成功后才保存订单。订单金额取商品服务返回的价格，不信任客户端金额。

调用链：

```text
客户端
  → POST /api/orders
    → order-service
      → GET /api/users/{id}                    user-service
      → POST /api/products/{id}/reservations   product-service
      → 保存订单
```

Nacos 只负责注册与发现，业务 HTTP 请求由订单服务的 Feign 客户端发给实例地址；Nacos 不转发请求，也不是网关。

失败位置和结果：

| 失败位置 | 结果 |
| --- | --- |
| 参数不是合法 UUID | 400 |
| 用户不存在 | 404，不调用商品服务、不保存订单 |
| 商品不存在 | 404，不保存订单 |
| 商品已被占用 | 409，不保存订单 |
| 用户/商品服务不可用或超时 | 503，不保存订单 |
| 商品占用成功、订单本地保存失败 | 一致性窗口，见第 4 节 |

### 实现文件和命令

- `services/order-service/src/main/java/com/zjgsu/cyy/client/UserFeignClient.java`
- `services/order-service/src/main/java/com/zjgsu/cyy/client/ProductFeignClient.java`
- `services/order-service/src/main/java/com/zjgsu/cyy/client/TradeClient.java`
- `docs/service-communication.md` 包含 Mermaid 调用链路图

实际结果：正常下单返回 201，商品变为 RESERVED，订单状态 PENDING_PAYMENT；重复购买返回 409；不存在的用户/商品返回 404；两个 user-service 实例都不可用时返回 503。

## 2. 使用 OpenFeign

order-service 使用现有 Spring Cloud BOM，增加：

- `spring-cloud-starter-openfeign`
- `spring-cloud-starter-loadbalancer`

启动类加 `@EnableFeignClients`。两个客户端按服务名调用：

```java
@FeignClient(name = "user-service")
public interface UserFeignClient {
    @GetMapping("/api/users/{id}")
    UserView getUser(@PathVariable("id") String id);
}
```

```java
@FeignClient(name = "product-service")
public interface ProductFeignClient {
    @PostMapping("/api/products/{id}/reservations")
    ProductView reserve(@PathVariable("id") String id);
}
```

`TradeClient` 保留原有响应校验：用户 ID 必须匹配；商品必须是 `RESERVED`、价格大于 0、标题合法。错误处理：

- Feign 404 → `NOT_FOUND`
- Feign 409 → `CONFLICT`
- 连接失败、超时或 5xx → `SERVICE_UNAVAILABLE`
- 无效远端响应 → 502

Feign 配置了连接/读取超时，并使用 `Retryer.NEVER_RETRY`，商品占用写请求不会自动重试。

原来的 `RestTemplate` 配置和手写地址拼接已删除，下单入口走 `TradeClient` → Feign 客户端。

运行测试：

```bash
(cd services/order-service && mvn test)
```

实际结果：order-service 的 `FeignClientTests` 6 项、`OrderServiceTests` 6 项全部通过；测试使用本地 HTTP 服务验证真实 Feign 调用、超时和错误映射，不连接 Nacos 或 MySQL。

## 3. 负载均衡验证

`compose.yaml` 中增加 `user-service-2`：

- 与 `user-service` 使用同一服务名 `user-service`、同一分组、同一个用户数据库；
- 宿主机端口分别为 8101 和 8111，避免冲突；
- `INSTANCE_ID` 分别为 `user-service-1` 和 `user-service-2`；
- 两个实例都注册到 Nacos，order-service 通过 Spring Cloud LoadBalancer 选择实例。

原有访问入口 `http://localhost:8101` 继续可用；8111 只用于第二个实例的健康检查或调试。订单服务使用 Nacos 返回的容器地址 8080，不写死宿主机端口。

Nacos 实际注册结果：

```text
user-service: 172.27.0.9:8080 healthy=True enabled=True
user-service: 172.27.0.7:8080 healthy=True enabled=True
product-service: 172.27.0.8:8080 healthy=True enabled=True
order-service: 172.27.0.6:8080 healthy=True enabled=True
```

端到端脚本连续创建 6 个新买家、6 件新商品并下单，然后从 user-service 日志中筛选本次买家 ID：

```text
USER_LOOKUP instance=user-service-2 buyerId=accb00ec-3522-453b-9f71-d673411c0581
USER_LOOKUP instance=user-service-1 buyerId=7ced02ba-cfc0-49bb-9c5a-b17922d9f364
USER_LOOKUP instance=user-service-2 buyerId=7f925479-1d80-4126-9ecf-1c436b7ac3d2
USER_LOOKUP instance=user-service-1 buyerId=943d6306-b30f-4e0d-a235-735562afae53
USER_LOOKUP instance=user-service-2 buyerId=085f7619-16a7-4876-8a0c-4162f36af2bb
USER_LOOKUP instance=user-service-1 buyerId=1756bab2-56b5-4732-a11c-13078d4e16da
```

这说明订单服务真实下单时，两个实例都收到了买家查询请求，不是只靠 Nacos 注册列表或分别 curl 两个实例。

## 4. 测试与端到端验证

### 单元/集成测试

```bash
(cd services/user-service && mvn test)
(cd services/product-service && mvn test)
(cd services/order-service && mvn test)
```

实际结果：

- user-service：4 项通过
- product-service：5 项通过
- order-service：12 项通过（`FeignClientTests` 6 + `OrderServiceTests` 6）
- 合计 21 项，Failures=0，Errors=0

### 端到端脚本

```bash
bash scripts/e2e-test.sh
```

脚本从仓库根目录执行，检查真实 HTTP 状态码、JSON 内容和业务数据变化；使用新买家、新商品，可重复运行；失败会非零退出，并在退出时恢复两个 user-service 实例。

实际输出见 [e2e-output.txt](e2e-output.txt)，摘要：

```text
PASS  user-service-1 健康接口 200
PASS  user-service-2 健康接口 200
PASS  第 1..6 笔正常下单
PASS  同一轮下单中两个 user-service 实例都处理过买家查询
PASS  已确认两个 user-service 容器都已停止
PASS  两个 user-service 实例停止后下单返回 503
PASS  依赖失败没有新增订单（87 -> 87）
PASS  依赖失败后商品仍为 AVAILABLE
PASS  Nacos 已重新注册两个 healthy=True enabled=True 的 user-service 实例
PASS  恢复后第 1 次下单返回 201
===== PASS =====
```

### 脚本误报防护自测

针对“错误响应被当成订单数组”和“创建失败后仍然 PASS”的风险，额外做了两次故障注入；两次都必须非零退出：

```bash
E2E_ORDER_LIST_PATH=/api/orders-bad bash scripts/e2e-test.sh
# 实际退出码 1：订单列表返回 HTTP 404，脚本停止比较订单数

E2E_FAIL_PRODUCT_AT=3 bash scripts/e2e-test.sh
# 实际退出码 1：第 3 件商品创建失败，主脚本立即退出
```

证据：[fault-order-list.txt](fault-order-list.txt)、[fault-create-third.txt](fault-create-third.txt)。

### POST 连接中断自测

验证“服务端已经收到 POST，但响应途中断开”时脚本不会自动重发写请求：

```bash
E2E_SELF_TEST=post-disconnect bash scripts/e2e-test.sh
# EXPECTED  POST ... 连接中断：本次写入结果未知，不自动重发
# PASS  POST 断连时脚本非零退出，服务端只收到 1 次 POST
```

证据：[post-disconnect.txt](post-disconnect.txt)。本地测试服务记录到服务端实际只收到 **1 次 POST**；脚本对 POST 传输失败直接非零退出，不再重试。

脚本处理逻辑：

- `order_count` 先要求 HTTP 200，再确认响应是 JSON 数组，否则失败；
- 创建买家/商品失败时在主脚本中直接 `exit 1`，不依赖命令替换子进程的失败计数；
- 每笔成功订单都核对返回的 `buyerId`、`productId`、金额和 `PENDING_PAYMENT`，并要求恰好 6 笔；
- 失败场景除比较订单总数，还核对没有与本次买家和商品匹配的订单；
- 只有 GET 允许一次传输层重试；POST 遇到连接中断立即失败并提示“本次写入结果未知”；
- 停机命令失败直接退出；停机后确认两个 user-service 容器都已停止；
- 恢复阶段统计 `healthy=True enabled=True` 的两个实例，对两个实例各做一次只读买家查询，然后只下一次单，不对 503 重试。

### 写操作超时的边界

商品占用是远程写操作。如果调用发生读取超时，远端可能已经完成占用；本地报错只能说明本地没有写订单，不能断言远端一定回滚。本项目没有分布式事务、自动补偿或自动重试，因此不宣称跨服务写操作具有原子性。服务发现和 Feign 都不能消除这个窗口。

回归测试覆盖读取超时：`FeignClientTests.readTimeoutMapsTo503AndReservationIsAttemptedOnlyOnce` 使用 300ms 读取超时和延迟 1200ms 的本地商品服务，断言返回 503 且占用请求只发送一次；`OrderServiceTests.productReadTimeoutDoesNotSaveAndIsCalledOnce` 断言本地订单不保存。

## 5. 截图清单

截图由用户自行截取，放在 `docs/homework/week-08/screenshots/`。

| 文件名 | 内容 |
| --- | --- |
| 01-openfeign-code.png | `UserFeignClient` / `ProductFeignClient` 的 `@FeignClient` 和实际调用位置 |
| 02-nacos-two-instances.png | Nacos 中两个 user-service 实例 |
| 03-e2e-two-instances.png | e2e 输出中 user-service-1 / user-service-2 的 `USER_LOOKUP` 日志 |
| 04-e2e-failure-recovery.png | 两个实例停止后 503、订单不增加、商品 AVAILABLE、恢复后 201 |
| 05-tests.png | 三个服务测试通过结果（21 项） |

## 6. 实际验证记录

- 验证时间：2026-11-11
- Nacos：`PERSONAL_GROUP`，控制台 18080
- 两个 user-service 实例：`172.27.0.7:8080`、`172.27.0.9:8080`
- 端到端脚本：通过，退出码 0
- 失败场景：两个 user-service 都停止后，下单 503；订单数 87 -> 87；商品仍 AVAILABLE
- 恢复场景：两个实例恢复并重新注册后，再次下单 201
- 证据文件：[e2e-output.txt](e2e-output.txt)、[nacos-instances.txt](nacos-instances.txt)、[test-output.txt](test-output.txt)、[fault-order-list.txt](fault-order-list.txt)、[fault-create-third.txt](fault-create-third.txt)、[post-disconnect.txt](post-disconnect.txt)
