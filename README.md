# 统一权限管理系统

当前实现版本见 [VERSION](docs/redesign/VERSION)。结构化和 DDD 业务实现共用 HTTP 接口、存储端口、认证、Spring Data JPA 仓储及 MySQL 数据库，通过 `BACKEND_STRATEGY=structured` 或 `ddd` 选择。

## 本地检查

需要 Java 21、Maven 3.9、Node.js 24.15 以上及 Docker。

```sh
./tooling/generate-keys.sh
./tooling/test.sh
./mvnw package -DskipTests
```

测试使用 Testcontainers 启动独立 MySQL，验证两套实现的相同契约。权限前端产物由 `frontend/` 构建。课程入口使用老师仓库原版 `web/`，两套产物由 Nginx 分别发布。

## 运行

设置 `DB_URL`、`DB_USER`、`DB_PASSWORD`、`JWT_PRIVATE_KEY`、`JWT_PUBLIC_KEY` 后启动：

```sh
java -jar "backend/target/rbac-backend-$(cat docs/redesign/VERSION).jar"
```

密钥参数使用 Spring Resource 路径，例如 `file:/绝对路径/private.pem`。默认令牌有效期为 30 分钟。Flyway 完成建表后，在空演示库手动导入 [演示 SQL](deploy/mysql/demo.sql)。用户资料和部门由 SQL 维护，角色与授权由平台维护。

## 老师系统

```sh
./tooling/prepare-course.sh
./mvnw install -DskipTests
mvn -f .integrations/course-project-mgt/backend/pom.xml -s .mvn/settings.xml package -DskipTests
npm ci --prefix .integrations/course-project-mgt/web
VITE_API_BASE_URL=/course-api VITE_IDENTITY_BASE_URL=/course-api npm run build --prefix .integrations/course-project-mgt/web -- --base=/course/
```

老师仓库版本和补丁位于 `integrations/course-project-mgt/`。该集成启用 `rbac-integration` profile，覆盖教师登录、学期维护和课程审核。老师前端调用课程后端的 `POST /login`。课程后端查询本地工号后，调用平台的 `POST /auth/login`。

设计文档、图及导出工具见 `docs/redesign/`，实施与测试记录见 `docs/implementation/`。

## 部署与设计交付

`deploy/stack.yaml` 将入口及两套应用部署在现有 Swarm Manager，MySQL 放在 Worker 1。使用独立 Overlay、Secret 和数据卷。手动导入和恢复步骤见 [部署说明](deploy/README.md)。

当前文档入口为 [设计交付目录](docs/redesign/README.md)，本轮改动和验证见 [v3.7.0 整改记录](docs/implementation/整改报告-v3.7.0.md)，原部署记录见 [实施报告](docs/implementation/实施报告.md)。`tooling/build-design.sh` 重建两份 PDF、AsciiDoc、黑白图稿及 StarUML 模型。


## 第二轮验收

本版实现 RBAC1 同部门角色继承，支持多级、多路径及防环，已有令牌实时读取有效权限。数据库为八张业务表和一张技术会话表。

- [权限后台](http://113.44.144.119/platform/) 与 [老师课程入口](http://113.44.144.119/course/)
- [平台 API 文档](http://113.44.144.119/api-docs/) 与 [JaCoCo 报告](http://113.44.144.119/coverage/)
- [第二轮演示脚本](docs/implementation/第二轮演示脚本.md)

API 文档由 `python3 tooling/build-api.py` 构建，并核对全部 34 个 Controller 操作。覆盖率由 `./mvnw verify` 生成。真实部署测试使用 `tooling/acceptance.py`，通过环境变量读取演示手机号，使用 `--version` 记录被验收部署的版本，结束时清理合成角色、关联及学期。
