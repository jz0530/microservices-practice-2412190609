# 作业系统填写内容

仓库：https://github.com/jz0530/microservices-practice-2412190609

## 1. API Gateway
新增独立 gateway-service，使用 Nacos 服务名配置用户、商品和订单三条路由，统一从 8080 访问，保留健康检查入口。通过网关请求实际验证路由转发，下游服务不再映射宿主机端口。
配图：01-gateway-code.png、03-auth-results.png、05-tests-ports.png。

## 2. 认证服务
用户服务实现登录和 HS512 JWT 签发，提供 BUYER、ADMIN 两种角色，数据库仅保存 BCrypt 密码哈希。使用本地演示账户验证登录成功、错误密码被拒绝以及过期 Token 无法访问。
配图：02-auth-code.png、03-auth-results.png。

## 3. 网关鉴权
受保护接口无 Token 返回 401，有效 Token 可访问，买家调用管理员发布商品接口返回 403。网关覆盖客户端身份头，订单服务校验本人归属，内部商品占用接口禁止通过网关访问。
配图：01-gateway-code.png、03-auth-results.png、04-ownership-results.png。

## 4. 测试脚本
从仓库根目录执行 bash scripts/auth-test.sh，自动验证未登录、登录成功、授权访问、角色越权、伪造身份、本人订单及跨服务下单。断言失败以非零状态退出，写请求不自动重试，避免将网络失败当成功。
配图：03-auth-results.png、04-ownership-results.png、05-tests-ports.png。
