# 统一权限管理系统

当前实现版本为 3.3.0。结构化和 DDD 业务实现共用 HTTP 接口、存储端口、认证及 MySQL 数据库，通过 `BACKEND_STRATEGY=structured` 或 `ddd` 选择。

## 本地检查

需要 Java 21、Maven 3.9、Node.js 22 以上及 Docker。

```sh
./tooling/generate-keys.sh
./tooling/test.sh
./mvnw package -DskipTests
```

测试使用 Testcontainers 启动独立 MySQL，验证两套实现的相同契约。前端构建生成 `/platform/` 和 `/course/` 两个入口。

## 运行

设置 `DB_URL`、`DB_USER`、`DB_PASSWORD`、`JWT_PRIVATE_KEY`、`JWT_PUBLIC_KEY` 后启动：

```sh
java -jar backend/target/rbac-backend-3.3.0.jar
```

密钥参数使用 Spring Resource 路径，例如 `file:/绝对路径/private.pem`。默认令牌有效期为 30 分钟。空演示库可显式设置 `app.demo.enabled=true` 和 `app.demo.phone` 初始化合成账号；已有用户时跳过初始化。

## 老师系统

```sh
./tooling/prepare-course.sh
./mvnw install -DskipTests
mvn -f .integrations/course-project-mgt/backend/pom.xml -s .mvn/settings.xml package -DskipTests
```

老师仓库版本和补丁位于 `integrations/course-project-mgt/`。该集成启用 `rbac-integration` profile，提供学期维护与课程审核链路；登录请求直接访问 RBAC。前端使用相同技术栈，页面通过真实 API 读取和提交数据。

设计文档、图及导出工具见 `docs/redesign/`，实施与测试记录见 `docs/implementation/`。
