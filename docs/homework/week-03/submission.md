# 第三周作业系统填写文本

## 仓库链接
https://github.com/jz0530/microservices-practice-2412190609

## 1. 选题与本周范围
沿用第二周确定的校园二手交易与担保履约平台，面向学生买家、卖家及平台管理员，优先规划卖家发布商品、买家浏览并下单的场景，核心模型为商品和订单。本周仅完成 Spring Boot 工程初始化、基础配置、问候接口、健康检查和启动测试，不实现业务模型、数据库或完整交易功能。

## 2. 工程创建与运行结果
使用 Java 25、Spring Boot 4.0.8 和 Maven Wrapper 创建工程，包名为 com.zjgsu.cyy，配置使用 application.yml，端口为 8080。工程已成功启动，GET /api/hello 返回项目问候语，/actuator/health 的 status 为 UP。

## 3. 测试命令与结果
在 monolith 目录运行 ./mvnw.cmd test（Windows；Linux 对应 ./mvnw test），执行使用 @SpringBootTest 的 contextLoads 启动测试。2026 年 10 月 9 日实测结果为 Tests run: 1，Failures: 0，Errors: 0，Skipped: 0，BUILD SUCCESS，应用上下文成功加载。

## 4. 运行步骤与验证
准备 JDK 25 后进入仓库 monolith 目录，通过 Maven Wrapper 执行测试，再执行 spring-boot:run 启动应用。浏览器访问 localhost:8080/api/hello 获得项目问候语，访问 localhost:8080/actuator/health 获得 UP 状态。README 已补充环境要求、启动测试命令、接口地址和本周尚未实现的业务范围。

提交前：审核文本，上传原仓库，并在作业系统分别附上运行和测试证据。当前只准备本地材料，不代表已经在作业系统提交。