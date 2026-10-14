# 数据库设计：校园二手交易平台

## 本周流程
发布商品 → 查询商品 → 买家下单 → 创建待付款订单并占用商品。
本周不实现账号认证、支付、订单取消、退款或重新上架。buyerName 为演示输入，不是已认证身份。

## 表和关系

`products` 商品表：

| 字段 | 类型 | 约束/含义 |
| --- | --- | --- |
| id | varchar(36) | UUID 主键 |
| title | varchar(120) | 非空，商品名称 |
| description | varchar(2000) | 非空，可为空字符串 |
| price | decimal(12,2) | 非空，Service 校验大于 0 且最多两位小数 |
| status | varchar(24) | 非空，AVAILABLE 或 RESERVED |

`trade_orders` 订单表：

| 字段 | 类型 | 约束/含义 |
| --- | --- | --- |
| id | varchar(36) | UUID 主键 |
| product_id | varchar(36) | 非空外键，引用 products.id；唯一约束 |
| buyer_name | varchar(80) | 非空，演示买家名称 |
| amount | decimal(12,2) | 非空，下单时商品价格快照 |
| status | varchar(24) | 非空，本周为 PENDING_PAYMENT |
| created_at | timestamp(6) | 非空，创建时间 |

一笔订单关联一个商品。本周没有取消/重新销售流程，因此每件商品最多对应一笔订单，以 product_id 唯一约束防止重复订单；后续支持历史订单时需要调整此约束。

## 事务与并发
OrderService.create 使用 @Transactional，查询商品时获取数据库行写锁，检查 AVAILABLE 后占用商品并保存订单。两次写入一起提交或回滚。重复购买返回 409，不存在返回 404，输入错误返回 400。

## 环境和数据准备
实际运行使用 MySQL 8.4，测试使用 H2。测试代码每次创建独立测试数据，测试事务回滚；不依赖本机 MySQL 历史数据。
课堂开发使用 ddl-auto: update 自动建立表，不能代替生产环境迁移工具。密码通过 DB_PASSWORD 环境变量传入，不写入 Git。
