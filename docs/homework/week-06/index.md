# 第六周：微服务拆分

验证时间：2026-10-29T08:13:52+0800。本周保留 monolith，新增 user-service、product-service、order-service。基础包名均为 com.zjgsu.cyy。

## 一、服务拆分设计

用户资料归用户服务，商品发布、价格和唯一占用归商品服务，下单与订单快照归订单服务。各有独立数据库、账号和数据卷；通过 HTTP 获取远端数据，仅保存远端 ID。完整映射、端口、表和 Mermaid 图见 [设计文档](../../microservices-design.md)。这次新增最小用户模型替代订单自由文本买家，未实现登录认证和担保支付。

## 二、代码拆分

services 下三个工程各有独立 pom.xml、src、Controller、Service、Repository、配置、启动类、测试和 Dockerfile。OrderService 使用 TradeClient 内的 RestTemplate 先校验用户，再占用商品，然后写本地订单。

地址在 application.yml 配置，支持 USER_SERVICE_URL、PRODUCT_SERVICE_URL 环境变量；连接和读取超时为 2 秒、3 秒，均可覆盖。资源不存在返回 404、已占用 409、不可用/超时 503。失败不继续写本地订单。

Windows Git Bash 从仓库根目录执行（JDK 25）：

```bash
(cd services/user-service && ./mvnw.cmd test)
(cd services/product-service && ./mvnw.cmd test)
(cd services/order-service && ./mvnw.cmd test)
```

本机和 Docker 构建均通过：用户 4、商品 5、订单 9，共 18 项测试，Failures=0、Errors=0。商品测试包含并发占用只允许一个成功；订单测试使用真实本地 HTTP 服务验证 RestTemplate、资源缺失、依赖故障及读取超时。

## 三、本地编排

```bash
test -f .env || cp .env.example .env
# 首次配置自己的六个数据库密码，保留已有 .env
docker compose config --quiet
docker compose up -d --build
docker compose ps
bash scripts/smoke-test.sh
```

实际构建成功，三个应用和三个 MySQL 8.4.11 容器启动，健康接口均为 HTTP 200 / UP。宿主机端口 8101、8102、8103；容器内部统一为 8080，调用使用 Compose 服务名。每个应用等待所属数据库健康，分别使用 trade_user、trade_product、trade_order 数据库与账号，独立命名卷保存数据。

旧单体通过 compose.monolith.yaml 启动，旧项目名和数据卷保留。禁止使用 down -v 删除数据卷。

## 四、验证

```bash
bash scripts/smoke-test.sh
python scripts/verify-flow.py --dependency-failure
```

本次真实 HTTP 请求、状态码和响应保存在 [verification.json](verification.json)，10 月 28 日首次验证的历史终端记录见 [验证输出](verification-output.txt)，其中 ID 属于首次运行；本次 10 月 29 日结果以 JSON 与下列截图为准。脚本每次创建新的演示数据，断言订单数量变化，不清空已有数据库。依赖故障验证结束已恢复商品服务。

| 请求/场景 | 状态码 | 数据变化 |
| --- | --- | --- |
| 三个服务 GET /actuator/health | 200 | UP |
| POST :8101/api/users | 201 | 创建买家 |
| POST :8102/api/products | 201 | 创建 AVAILABLE 商品，25.50 |
| POST :8103/api/orders | 201 | 商品 RESERVED，订单 PENDING_PAYMENT |
| GET 商品和订单详情 | 200 | ID 与快照一致 |
| 重复购买该商品 | 409 | 订单数不变 |
| 不存在的用户 | 404 | 新商品仍 AVAILABLE，订单数不变 |
| 不存在的商品 | 404 | 订单数不变 |
| 停止商品服务后下单 | 503 | 订单数不变；恢复后新商品仍 AVAILABLE |

本次用户 ID：`e52f508b-2e34-4307-abd0-61b869d04aa7`；商品 ID：`9c7cc969-b6ab-4677-906b-5f5870fc5de4`；订单 ID：`31d6396e-bd01-4143-aab3-cd4f5c146c58`；失败验证商品 ID：`47dd361d-14ec-444b-b63e-d0b92889b1bf`。

订单数量由 1 变为 2；后续全部失败场景未增加订单。正常商品标题 Week06 HTTP textbook，价格 25.50。本次 ID 独立于第五周记录，不混用。

### 远端成功、本地失败窗口

商品服务先提交占用，再由订单服务保存订单。如果商品占用成功后订单数据库失败，会留下 RESERVED 商品而没有本地订单。读取超时也可能发生在远端提交之后。本周不实现跨服务原子事务、自动补偿、重试或消息机制，需要人工核对该窗口；不能把本次依赖停机测试当成已解决分布式一致性。

## 已归档截图

以下为用户 10 月 29 日实际截图，已核对文件与画面。

| 文件 | 内容 | 对应小题 |
| --- | --- | --- |
| [01-services.png](screenshots/01-services.png) | OrderService 包名、服务路径与校验用户、占用商品、保存订单代码 | 代码拆分 |
| [02-compose.png](screenshots/02-compose.png) | 三个应用 Up，三个 MySQL healthy | 本地编排 |
| [03-health.png](screenshots/03-health.png) | 三个健康接口 200 / UP 与 PASS | 验证 |
| [04-create.png](screenshots/04-create.png) | 创建用户和商品 201 | 代码拆分、验证 |
| [05-success.png](screenshots/05-success.png) | 下单 201、商品 RESERVED、重复购买 409、资源不存在 404 | 代码拆分、验证 |
| [06-failure.png](screenshots/06-failure.png) | 停机 503、订单未新增、服务恢复与 PASS | 验证 |

截图已保存到仓库，作业系统是否提交仍需用户确认。
