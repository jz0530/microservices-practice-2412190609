# 第五周作业系统填写文字

仓库链接：https://github.com/jz0530/microservices-practice-2412190609

## 1. Dockerfile 与镜像构建
在原有 monolith 工程中编写多阶段 Dockerfile，使用 Maven 与 Java 25 镜像完成编译和测试，再将可运行 JAR 复制到 JRE 镜像。执行 docker build -t campus-trade:week05 ./monolith 构建成功，减少运行镜像中的构建工具和源码，无需本机 Maven 或 Java 参与。

## 2. Compose 编排与启动
使用根目录 compose.yaml 一键启动 app 与 MySQL 8.4.11。应用通过共享网络中的 mysql 服务名连接数据库，使用环境变量传入连接配置和密码，并等待数据库健康后启动。app 正常运行，mysql 显示 healthy，本机通过 8084 端口访问应用。

## 3. 个人业务 API 验证
通过容器映射端口验证健康检查、商品创建和商品查询。健康接口返回 HTTP 200 和 UP；POST /api/products 创建教材商品返回 HTTP 201；GET 商品列表与详情返回 HTTP 200，标题、价格和商品状态均符合预期。

## 4. 数据持久化验证
创建商品并记录其 ID 和关键字段后，执行 docker compose down 和 docker compose up -d 重建容器。应用恢复后查询同一商品，ID、标题、价格和状态保持一致，证明 MySQL 命名数据卷保留了业务数据。验证过程中未使用删除数据卷的 -v 参数。
