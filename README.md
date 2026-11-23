# 统一权限管理系统

当前实现版本见 [VERSION](docs/redesign/VERSION)。结构化和 DDD 业务实现共用 HTTP 接口、存储端口、认证、Spring Data JPA 仓储及 MySQL 数据库，通过 `BACKEND_STRATEGY=structured` 或 `ddd` 选择。

## 课程验收

| 入口 | 检查内容 |
| --- | --- |
| [统一权限管理后台](http://113.44.144.119/platform/) | 登录、用户角色分配、角色权限与继承、部门、业务系统和权限目录 |
| [课程项目管理系统](http://113.44.144.119/course/) | 教师和学生登录、我的课程、教师目录与菜单权限 |
| [JaCoCo 测试覆盖率报告](http://113.44.144.119/coverage/) | v3.9.0 平台后端行覆盖率 93.73%，分支覆盖率 70.34% |
| [Apifox 公开文档](https://3wq8lbigke.apifox.cn/) / [线上 Swagger](http://113.44.144.119/api-docs/) / [OpenAPI JSON](http://113.44.144.119/api-docs/openapi.json) | 29 项接口，其中 8 项是老师约定的统一身份接口 |
| [概要设计 AsciiDoc](docs/redesign/统一权限管理系统概要设计说明书.adoc) / [需求与详细设计 AsciiDoc](docs/redesign/统一权限管理系统需求与详细设计说明书.adoc) | 总体架构、数据库表设计、功能、接口及 UML |
| [设计交付目录](docs/redesign/README.md) / [评分办法 V4](docs/OOAD_Grading_2026_V4.pdf) | PDF、StarUML 图稿和课程验收要求 |

### 两个系统怎么测

下表使用课程环境的合成演示账号。权限后台填写「用户名」和「手机号」，课程系统按角色入口填写工号或学号及密码。切换账号前先退出。课程页右上角选择「2026 秋季课程演示」。

| 系统与登录入口 | 用户名、工号或学号 | 手机号或密码 | 操作和预期结果 |
| --- | --- | --- | --- |
| 权限后台 | `admin` | `13800000000` | 点「业务系统」，看到统一权限管理后台和课程管理系统。点「权限目录」，在「所属系统」选课程管理系统，查看课程权限码。再点「用户」「角色」「部门」查看对应列表。 |
| 课程系统 教师登录 | `ooad-load-2026-00001` | `19900000001` | 点「我的课程」和「统一身份联调演示课」，看到「身份联调演示班」。 |
| 课程系统 学生登录 | `ooad-load-2026-00191` | `19900000191` | 在「我的课程」打开同一门课，里程碑页显示「还没有里程碑」。 |
| 课程系统 教师登录 | `reviewer` | `13800000000` | 点「教师」和「添加教师」，展开「规模演示组织」和「总公司」，选「公司领导」。看到成员列表后点「取消」。 |
| 课程系统 教师登录 | `viewer` | `13800000000` | 「教师」「学期」禁用。打开[教师管理页](http://113.44.144.119/course/admin/teachers)，页面显示「无法访问」。 |

两个系统均登记在权限平台。课程管理菜单向权限平台查询界面许可，两个教师账号用相同入口登录时，菜单状态随各自的 RBAC 授权变化。[第二轮演示脚本](docs/implementation/第二轮演示脚本.md)

### 怎么证明 Docker Swarm 已部署

在 Swarm Manager `113.44.144.119` 上执行以下命令。

```sh
docker node ls
docker stack services rbac_v33
docker stack ps rbac_v33 --filter desired-state=running
```

`docker node ls` 显示三个节点都是 `Ready / Active`，其中 `course-manager` 为 `Leader`。服务列表中四项均为 `1/1`，任务列表显示当前任务为 `Running`。

| 服务 | 部署节点 | 内容 |
| --- | --- | --- |
| `rbac_v33_web` | `course-manager` | Nginx 发布 `/platform/` 和 `/course/` 两个前端 |
| `rbac_v33_rbac` | `course-manager` | 统一权限后端 |
| `rbac_v33_course` | `course-manager` | 课程管理后端 |
| `rbac_v33_db` | `course-node1` | 两套应用使用的 MySQL |

两套网页的登录和页面操作可用来验证服务对外可用。[部署与联调记录](docs/implementation/deployment-v3.9.0.json)

### 切换菜单时怎么看 Logback

用 `admin` 登录权限后台。在 Swarm Manager 的另一个终端执行以下命令，保留终端窗口并依次点击网页菜单。每次点击后，在终端看同一时间段及同一请求线程上的日志。

```sh
docker service logs --tail 0 --follow rbac_v33_rbac
```

| 页面操作 | 对应的后端请求 |
| --- | --- |
| 点「用户」 | `GET /api/users` |
| 点「角色」 | `GET /api/roles` |
| 点「部门」 | `GET /api/departments` |
| 点「业务系统」 | `GET /api/systems` |
| 点「权限目录」并选择一个系统 | `GET /api/systems/{systemId}/permissions` |
| 点「刷新权限」 | `GET /auth/me` |

点「用户」后，可在同一请求线程中找到下面四条日志。各层在调用返回时写日志，所以仓储先出现，Controller 最后出现。用 `Ctrl+C` 结束日志跟踪。

```text
[http-nio-8080-exec-4] layer operation=UserJpaRepository.findByDepartmentId(..) outcome=ok elapsedMicros=3932
[http-nio-8080-exec-4] layer operation=JpaManagementStore.users(..)                 outcome=ok elapsedMicros=6207
[http-nio-8080-exec-4] layer operation=StructuredUserService.list(..)             outcome=ok elapsedMicros=9972
[http-nio-8080-exec-4] layer operation=UserController.list(..)                    outcome=ok elapsedMicros=18096
```

## 本地检查

需要 Java 21、Maven 3.9、Node.js 24.15 以上及 Docker。

```sh
./tooling/generate-keys.sh
./tooling/test.sh
./mvnw package -DskipTests
```

测试使用 Testcontainers 启动独立 MySQL，验证两套实现的相同契约。单独验证后端并生成覆盖率时执行 `./mvnw verify`，打开 `backend/target/site/jacoco/index.html`。[线上 JaCoCo 报告](http://113.44.144.119/coverage/) [测试汇总](docs/implementation/test-summary-v3.9.0.json)

## 运行

设置 `DB_URL`、`DB_USER`、`DB_PASSWORD`、`JWT_PRIVATE_KEY`、`JWT_PUBLIC_KEY` 后启动：

```sh
java -jar "backend/target/rbac-backend-$(cat docs/redesign/VERSION).jar"
```

密钥参数使用 Spring Resource 路径，例如 `file:/绝对路径/private.pem`。默认令牌有效期为 30 分钟。Flyway 完成建表后，在空演示库手动导入 [演示 SQL](deploy/mysql/demo.sql)。用户、部门、系统和权限定义由 SQL 维护。后台提供目录查询和完整角色授权。

## 老师系统

```sh
./tooling/prepare-course.sh
./mvnw install -DskipTests
mvn -f .integrations/course-project-mgt/backend/pom.xml -s .mvn/settings.xml package -DskipTests
npm ci --prefix .integrations/course-project-mgt/web
VITE_API_BASE_URL=/course-api VITE_IDENTITY_BASE_URL=http://113.44.144.119 VITE_MOCK_ENABLED=false npm run build --prefix .integrations/course-project-mgt/web -- --base=/course/
```

老师仓库版本和补丁位于 `integrations/course-project-mgt/`。`rbac-integration` profile 接入教师和学生登录、各自的课程、教师目录、学期维护和课程审核。课程后端按本地工号或学号调用平台的 `POST /auth/login`。课程前端按 `VITE_IDENTITY_BASE_URL` 直连平台查询菜单许可与部门目录。

## 部署与设计交付

`deploy/stack.yaml` 使用 Overlay 网络、Docker Secret 和持久卷。部署、演示数据导入及线上测试命令见[部署说明](deploy/README.md)。执行 `./tooling/build-design.sh` 可重建两份 PDF、AsciiDoc、黑白图稿及可编辑 StarUML 模型。 [设计交付目录](docs/redesign/README.md) [v3.9.0 课程联调记录](docs/implementation/整改报告-v3.9.0.md)
