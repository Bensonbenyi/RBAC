# 统一权限管理系统

当前实现版本为 3.5.0。结构化和 DDD 业务实现共用 HTTP 接口、存储端口、认证及 MySQL 数据库，通过 `BACKEND_STRATEGY=structured` 或 `ddd` 选择。

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
java -jar backend/target/rbac-backend-3.5.0.jar
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

## 部署与设计交付

`deploy/stack.yaml` 将入口及两套应用部署在现有 Swarm Manager，MySQL 放在 Worker 1。使用独立 Overlay、Secret 和数据卷。`deploy/backup.sh` 在数据库节点生成一致性备份；恢复验证使用独立临时数据库。

当前文档入口为 [设计交付目录](docs/redesign/README.md)，本轮核查和验证见 [v3.5.0 整改报告](docs/implementation/整改报告-v3.5.0.md)，原部署记录见 [实施报告](docs/implementation/实施报告.md)。`tooling/build-design.sh` 重建两份 PDF、AsciiDoc、151 张黑白图稿及 StarUML 模型。


## 第二轮验收

本版实现 RBAC1 同部门角色继承，支持多级、多路径及防环，已有令牌实时读取有效权限。数据库为八张业务表和一张技术会话表。

- [权限后台](http://113.44.144.119/platform/) 与 [老师课程入口](http://113.44.144.119/course/)
- [平台 API 文档](http://113.44.144.119/api-docs/) 与 [JaCoCo 报告](http://113.44.144.119/coverage/)
- [第二轮演示脚本](docs/implementation/第二轮演示脚本.md)

API 文档由 `python3 tooling/build-api.py` 构建，并核对全部 42 个 Controller 操作。覆盖率由 `./mvnw verify` 生成。真实部署测试使用 `tooling/acceptance.py`，通过环境变量读取演示手机号，结束时清理合成角色、关联及学期。
