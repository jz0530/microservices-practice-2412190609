# 第七周作业系统填写文字

仓库链接：https://github.com/jz0530/microservices-practice-2412190609

## 1. 部署 Nacos 并注册服务（不少于 20 字符）

在根目录 compose.yaml 中加入 nacos/nacos-server:v3.2.4，开启鉴权并用命名卷持久化。控制台映射 18080:8080，服务端 8848、SDK 9848；三个服务统一注册到 public 命名空间、PERSONAL_GROUP 分组，Nacos 控制台可看到 user-service、product-service、order-service 及实例地址。

## 2. 配置隔离并完成服务发现调用（不少于 50 字符）

实际流程是下单：order-service 通过 DiscoveryClient.getInstances("user-service") 和 getInstances("product-service") 获取 Nacos 当前实例，使用实例 URI 分别调用 GET /api/users/{id} 和 POST /api/products/{id}/reservations，最后写本地订单。服务名是 user-service、product-service、order-service，分组 PERSONAL_GROUP；每次请求重新寻址，没有固定下游地址。正常下单返回 201，订单金额 25.50 来自商品服务；重复下单 409，不存在的用户或商品 404。

## 3. 验证健康状态、下线与恢复（不少于 20 字符）

正常时三个 /actuator/health 均为 200/UP，下单 201 且商品变为 RESERVED。停止 user-service 后，再下单返回 503，订单数不增加，商品仍为 AVAILABLE；恢复 user-service 并等待重新注册后，再次下单返回 201。Nacos SDK 连接、实例 healthy 和 Actuator 健康不是同一状态，Actuator DOWN 不会自动摘除实例。

## 4. 验证脚本（不少于 20 字符）

scripts/smoke-test.sh 从仓库根目录执行，检查 Nacos 控制台 http://localhost:18080、三个服务健康接口、正常下单、重复 409、404、停止 user-service 的 503 及恢复后的 201；任一检查失败返回非零退出码。三个服务测试共 21 项通过，测试关闭 Nacos 自动注册并使用 H2 和模拟 DiscoveryClient，不依赖真实 Nacos 或 MySQL。
