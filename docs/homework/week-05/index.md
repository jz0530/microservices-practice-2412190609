# 第 05 周：Docker 容器化

项目：校园二手交易与担保履约平台。沿用 `monolith/` 的商品与订单实现，基础包名 `com.zjgsu.cyy`。
验证时间：2026-10-20T17:25:15.871507（本机时间）。

## Dockerfile 与镜像构建

`monolith/Dockerfile` 构建阶段使用 `maven:3.9.16-eclipse-temurin-25`，执行 `mvn -B -ntp clean package`；运行阶段使用 `eclipse-temurin:25.0.4.1_1-jre`，只复制生成的可运行 JAR。构建不依赖本机 Java 或 Maven。
`.dockerignore` 排除 target、IDE 配置、环境密码文件、日志和私钥文件。真实 `.env` 位于构建上下文之外，并由根目录 `.gitignore` 排除。

仓库根目录执行：

```bash
docker build -t campus-trade:week05 ./monolith
docker image ls campus-trade:week05
```

实际结果：镜像构建成功，容器内测试结果 `Tests run: 14, Failures: 0, Errors: 0, Skipped: 0`，Maven `BUILD SUCCESS`。精简原始日志见 [build-result.txt](build-result.txt)。

## Compose 编排与启动

根目录 `compose.yaml` 包含 app 和 mysql，构建上下文为 `./monolith`，未使用 version 字段。
MySQL 固定为 8.4.11；两个服务加入 trade-network。应用通过服务名 mysql 和容器内部 3306 端口访问数据库，连接地址、用户名和密码通过环境变量传入。
MySQL 使用 SQL `SELECT 1` 健康检查；app 依赖 `service_healthy`。

```bash
# 首次运行且还没有 .env 时复制，然后修改示例密码
cp .env.example .env
docker compose config --quiet
docker compose up -d --build
docker compose ps
docker compose logs --tail=30 app
```

本机 `.env` 已准备，后续不再覆盖。应用映射 `127.0.0.1:8084:8080`，MySQL 不映射宿主机端口。
实际结果：app 运行，mysql 运行且 healthy；见 [container-status.txt](container-status.txt)。
启动后等待健康接口返回 UP，再调用业务接口。

## 个人业务 API 验证

```bash
curl -i http://localhost:8084/actuator/health
curl -i -X POST http://localhost:8084/api/products -H 'Content-Type: application/json' -d '{"title":"Week05 Docker textbook","description":"Named volume persistence verification","price":25.50}'
curl -i http://localhost:8084/api/products
```

实际结果：健康检查 HTTP 200、status UP；创建商品 HTTP 201；列表和详情查询 HTTP 200。
本次验证商品 ID：`0f7b9679-90c2-495f-b7a6-2cf75b8411ea`，价格 25.50，状态 AVAILABLE。
每次再次创建会生成新的 ID，不应把不同商品的 ID 用于持久化前后对比。

## 数据持久化验证

使用创建接口生成上述商品，重建前保存查询结果；执行 down / up 后重新查询同一 ID：

```bash
curl -i http://localhost:8084/api/products/0f7b9679-90c2-495f-b7a6-2cf75b8411ea
docker compose down
docker compose up -d
docker compose ps
# 等待应用健康接口返回 UP 后再查询
curl -i http://localhost:8084/api/products/0f7b9679-90c2-495f-b7a6-2cf75b8411ea
```

验证结果：两个容器 ID 均发生变化；商品 ID、标题、描述、价格和状态全部一致。
原始响应分别保存在 [before-recreate.json](before-recreate.json) 和 [after-recreate.json](after-recreate.json)；
包含重建前后容器 ID 的机器可读证据见 [verification.json](verification.json)。

MySQL 数据保存在命名卷 `cyy-campus-trade_mysql-data`；down 删除容器和网络但保留卷，重建后 MySQL 挂载同一个卷，因此数据仍在。
**本次没有使用、验证时也不要使用 `docker compose down -v`。**

## 截图清单

以下截图已保存于本目录 `screenshots/`，与 `compose.yaml`、`monolith/Dockerfile` 和 `monolith/.dockerignore` 一并提交 GitHub：

| 文件名 | 内容 | 作业栏目 |
| --- | --- | --- |
| screenshots/01-build.png | `docker build` 成功，容器内 Tests run: 14，镜像导出 | Dockerfile 与镜像构建 |
| screenshots/02-image.png | `docker image ls campus-trade:week05`，镜像 ID 与大小 | Dockerfile 与镜像构建 |
| screenshots/03-compose.png | `docker compose ps`：app Up、mysql Up (healthy) 及端口映射 | Compose 编排与启动 |
| screenshots/04-health.png | `curl -i /actuator/health`，HTTP 200、status UP | 个人业务 API 验证 |
| screenshots/05-create.png | `POST /api/products`，HTTP 201、Location 与返回商品 | 个人业务 API 验证 |
| screenshots/06-before.png | 重建前 `GET /api/products/{id}`，HTTP 200 与完整字段 | API 验证及持久化验证 |
| screenshots/07-recreate.png | `docker compose down` 后 `up -d`，容器与网络重建 | 数据持久化验证 |
| screenshots/08-after.png | 重建后查询同一商品，ID、标题、价格、状态均不变 | 数据持久化验证 |

截图记录的商品 ID 为 `20259707-54a8-44ad-80c5-f8713903d6f4`；[before-recreate.json](before-recreate.json)、[after-recreate.json](after-recreate.json) 与 [verification.json](verification.json) 是另一次同流程复核，商品 ID 为 `0f7b9679-90c2-495f-b7a6-2cf75b8411ea`，两次均验证重建前后数据一致。
作业系统填写文字见 [submission.md](submission.md)。
