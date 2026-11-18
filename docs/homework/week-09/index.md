# 第九周：API 网关与认证授权

项目：校园二手交易与担保履约平台。本周实现范围是交易服务的统一入口和认证授权，未声称完成支付、退款或分布式事务。

## API Gateway

独立工程 `services/gateway-service/` 使用 Spring Cloud Gateway WebFlux、Nacos 和 LoadBalancer。根 `compose.yaml` 配置网关 8080 与三条服务名路由：用户/认证、商品、订单；`GET /actuator/health` 是公开检查入口。下游取消宿主机端口，保留用户双实例及原数据库卷。

## 认证服务

`services/user-service/` 新增 Account、AccountRepository、AuthService、JwtService 和登录控制器。POST `/api/auth/login` 校验 BCrypt 密码哈希后签发 HS512 JWT。buyer/buyer2 为 BUYER，admin 为 ADMIN。密钥和初始密码仅由本地 .env 提供，不提交实际值。

## 网关鉴权

未登录和无效 Token 返回 401；低权限创建商品返回 403；管理员可创建商品。网关覆盖身份请求头，订单服务校验订单归属。商品占用接口禁止经网关访问，保留内部 Feign。认证流程和完整权限表见 [security.md](../../security.md)。

## 测试脚本

从仓库根目录执行：

```bash
# 首次使用：复制 .env.example 到 .env，填写自己的数据库及 Nacos 凭据
python scripts/init-week09-env.py
bash scripts/start-week09.sh
bash scripts/auth-test.sh
```

需 Java 25、Maven、Docker Compose、Python 3（标准库）和 Bash。MVN/PYTHON 环境变量可指定本机路径。若课堂工程占用 8080/18080/8848/9848，请先停止该工程，保留其数据卷。

脚本用真实 HTTP 验证无 Token、登录、携带 Token、低角色越权、伪造身份头、本人订单、内部占用接口和正常下单；每轮新建商品，不清空历史数据。失败立即非零退出，写请求不自动重发。单元测试覆盖密码错误、JWT 声明、拒绝冒用买家且不调用下游，以及订单读取归属。

实际运行记录见本目录 `auth-output.txt`、`test-output.txt`、`docker-ports.txt`。截图在 `screenshots/`，具体结果以这些实际输出为准。

## 截图索引

- `01-gateway-code.png`：三条路由和鉴权关键代码（真实源码节选）。
- `02-auth-code.png`：密码哈希、登录及 JWT 签发（真实源码节选）。
- `03-auth-results.png`：真实运行日志中的 401、200、403、201。
- `04-ownership-results.png`：内部接口、伪造身份、订单归属和正常流程。
- `05-tests-ports.png`：测试汇总与端口隔离。

截图为原文件/日志节选展示，标注来源，不包含真实密码或完整 Token。
