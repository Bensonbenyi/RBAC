# 部署与验收

当前采用已有三节点 Docker Swarm，辅助节点保存备份并执行压测。实际服务入口、资源、检查结果见 [实施报告](../docs/implementation/实施报告.md)。

## 构建

构建主机需要 Java 21、Docker、Node.js 和 Maven Wrapper。教师仓库由固定提交及补丁准备。

```sh
./tooling/prepare-course.sh
./tooling/test.sh
./mvnw install -DskipTests
mvn -f .integrations/course-project-mgt/backend/pom.xml -s .mvn/settings.xml package -DskipTests
npm ci --prefix frontend
npm run build --prefix frontend
./tooling/build-images.sh
```

构建工具读取 `docs/redesign/VERSION`，用同一个版本标记 amd64 镜像 `rbac/backend`、`rbac/course` 和 `rbac/web`。MySQL 使用 `rbac/mysql:8.4.13-amd64`。将前三个镜像通过 `docker save`、文件传输及 `docker load` 载入 Manager，将 MySQL 镜像载入数据库 Worker。当前 Stack 使用本地镜像，更新前需在放置节点载入相同版本。

## 初始化与部署

在 Manager 创建以下外部 Secret。`SECRET_DIR` 指向权限为 700 的凭据目录，其文件权限为 600；文件保存各自的值。

```sh
for name in mysql_root_password rbac_db_password course_db_password private_key public_key demo_phone; do
  docker secret create "rbac_v33_$name" "$SECRET_DIR/$name"
done
```

`private_key`、`public_key` 是同一组 RSA PEM，生成入口为 `tooling/generate-keys.sh`。`demo_phone` 是合成演示账号手机号。MySQL 首次启动脚本分别创建 `rbac`、`course_project` 数据库及专用账号，数据库迁移由应用启动执行，随后 Hibernate 校验 Spring Data JPA 的实体映射。已有卷沿用原数据库和密码；Secret 变更需要同步数据库账号。

给数据库节点标记 `rbac.db=true`，然后在 Manager 的仓库根目录部署。

```sh
docker node update --label-add rbac.db=true course-node1
RBAC_VERSION="$(cat docs/redesign/VERSION)" docker stack deploy --resolve-image never -c deploy/stack.yaml rbac_v33
docker stack services rbac_v33
```

默认 `BACKEND_STRATEGY=structured`。设置为 `ddd` 后重部署，可同时选择平台与教师应用各自的 DDD 实现。两种实现使用相同接口和数据库。Stack 更新为 stop-first；等待新任务的环境与 health 都符合目标配置，再执行验收。入口 `/platform/`、`/course/`，反向代理将 `/course-api/` 剥离前缀交给教师后端。

演示课程种子为 `integrations/course-project-mgt/demo-data.sql`，在课程数据库执行。脚本只添加缺失的合成课程，保留已有审核结果。平台空库在显式启用演示初始化后创建合成角色与账号，已有用户时跳过初始化。

两个应用完成初始化后，使用具备跨库读取权限的初始化账号执行 `integrations/course-project-mgt/link-demo-teachers.sql`，将 admin、reviewer、viewer 的实际平台编号关联至课程 `teacher.user_id`。脚本保留已有教师关联。课程 `GET /me` 需要本地教师或学生资料，未关联时返回 403；正式账号使用对应的真实资料。通过数据库命令行执行中文种子时设置 `--default-character-set=utf8mb4`。

Flyway V2 在既有平台库补齐独立界面码，此后两类权限可以分别撤销。已部署版本和验收记录见 [v3.5.0 部署记录](../docs/implementation/整改报告-v3.5.0.md)。

## 备份与恢复校验

在数据库 Worker 执行：

```sh
bash /opt/rbac-v33/backup.sh /opt/rbac-v33/backups
bash /opt/rbac-v33/restore-check.sh /opt/rbac-v33/backups/指定备份.sql.gz
```

备份脚本在容器内读取 Secret。恢复检查使用本地 amd64 MySQL 镜像，新建 `rbac-v33-restore-check` 容器和 `rbac_v33_restore_check` 卷；名称已有占用时退出。隔离容器无网络和发布端口，导入后比较源库与恢复库的规范化数据摘要，并清理本次创建的临时资源。源库在备份与比较期间保持无写入，以便比较同一份数据。需要正式恢复时，以验证后的转储恢复数据库，同时提供原 JWT 签名密钥。

## 测量

从辅助节点发送固定负载：

```sh
python3 tooling/benchmark.py --base http://入口地址 --strategy structured --concurrency 8 --output structured.json
```

脚本从环境变量 `RBAC_DEMO_PHONE` 读取合成手机号，报告保存指标和状态分布，令牌留在进程内。更换业务策略后等待新任务健康，再在相同数据、资源及日志配置下重复。当前测量使用公网 HTTP、每请求一个客户端连接，单轮 100 次预热及 1,000 次计时请求。
