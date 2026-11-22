# 部署与验收

当前采用已有三节点 Docker Swarm，辅助节点保存备份并执行压测。实际服务入口、资源、检查结果见 [实施报告](../docs/implementation/实施报告.md)。

## 构建

构建主机需要 Java 21、Docker、Node.js 24.15 和 Maven 3.9。教师仓库由固定提交及补丁准备。

```sh
./tooling/prepare-course.sh
./tooling/test.sh
./mvnw install -DskipTests
mvn -f .integrations/course-project-mgt/backend/pom.xml -s .mvn/settings.xml package -DskipTests
npm ci --prefix .integrations/course-project-mgt/web
VITE_API_BASE_URL=/course-api npm run build --prefix .integrations/course-project-mgt/web -- --base=/course/
./tooling/build-images.sh
```

构建工具读取 `docs/redesign/VERSION`，用同一个版本标记 amd64 镜像 `rbac/backend`、`rbac/course` 和 `rbac/web`。MySQL 使用 `rbac/mysql:8.4.13-amd64`。将前三个镜像通过 `docker save`、文件传输及 `docker load` 载入 Manager，将 MySQL 镜像载入数据库 Worker。当前 Stack 使用本地镜像，更新前需在放置节点载入相同版本。

## 初始化与部署

在 Manager 创建以下外部 Secret。`SECRET_DIR` 指向权限为 700 的凭据目录，其文件权限为 600；文件保存各自的值。

```sh
for name in mysql_root_password rbac_db_password course_db_password private_key public_key; do
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

默认 `BACKEND_STRATEGY=structured`。设置为 `ddd` 后重部署，可同时选择平台与教师应用各自的 DDD 实现。两种实现使用相同接口和数据库。Stack 更新为 stop-first；等待新任务的环境与 health 都符合目标配置，再执行验收。入口 `/platform/`、`/course/`，反向代理将 `/course-api/auth/*` 转到平台认证接口，其余 `/course-api/*` 去掉前缀后交给教师后端。

应用完成 Flyway 迁移后，在数据库 Worker 借助 MySQL 容器导入演示数据。空平台库先执行 `deploy/mysql/demo.sql`。课程库执行 `integrations/course-project-mgt/demo-data.sql`，再跨库执行 `integrations/course-project-mgt/link-demo-teachers.sql`。已有演示库升级时从课程数据和教师关联两步开始。关联脚本将演示工号与平台用户名对齐，确保课程的 `POST /login` 能找到本地教师。

```sh
db="$(docker ps -q --filter label=com.docker.swarm.service.name=rbac_v33_db)"
docker exec -i "$db" sh -c 'MYSQL_PWD="$(cat /run/secrets/mysql_root_password)" exec mysql -uroot --default-character-set=utf8mb4 rbac' < deploy/mysql/demo.sql
docker exec -i "$db" sh -c 'MYSQL_PWD="$(cat /run/secrets/mysql_root_password)" exec mysql -uroot --default-character-set=utf8mb4 course_project' < integrations/course-project-mgt/demo-data.sql
docker exec -i "$db" sh -c 'MYSQL_PWD="$(cat /run/secrets/mysql_root_password)" exec mysql -uroot --default-character-set=utf8mb4' < integrations/course-project-mgt/link-demo-teachers.sql
```

Flyway V5 移除已停用的管理权限码和会话表的目标系统列。请求从已验签的 JWT 读取目标系统，沿用旧会话的 jti、账号、到期时间及撤销状态。

## 手动备份与恢复

在数据库 Worker 执行 `bash deploy/backup.sh` 导出两个数据库。需要恢复时，在选定的 MySQL 实例导入备份，再核对账号、授权关系和两套系统的登录。要继续验证原有会话，应用需使用备份前的签名密钥。

```sh
gzip -dc /绝对路径/所选备份.sql.gz | docker exec -i "$db" sh -c 'MYSQL_PWD="$(cat /run/secrets/mysql_root_password)" exec mysql -uroot --default-character-set=utf8mb4'
```

## 测量

从辅助节点发送固定负载：

```sh
python3 tooling/benchmark.py --base http://入口地址 --strategy structured --concurrency 8 --output structured.json
```

脚本从环境变量 `RBAC_DEMO_PHONE` 读取合成手机号，报告保存指标和状态分布，令牌留在进程内。更换业务策略后等待新任务健康，再在相同数据、资源及日志配置下重复。当前测量使用公网 HTTP、每请求一个客户端连接，单轮 100 次预热及 1,000 次计时请求。
