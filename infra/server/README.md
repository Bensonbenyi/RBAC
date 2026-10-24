# 课程服务器运行说明

当前采用 Manager + Node1，平台仍为单实例。结构见[部署图](../../docs/assets/server-deployment.svg)，验收及入口见[口试交付报告](../../docs/口试交付验收报告.md)。公网只开放 80、443、8443、9443；数据库 3307 和应用 18080 至 18082 不开放公网。

| 主机 | 进程和限额 |
| --- | --- |
| Manager 113.44.144.119，内网 172.31.7.102 | platform 1024 MiB / Xmx512m；Nginx 64 MiB |
| Node1 119.3.221.88，内网 172.31.12.231 | MySQL 512 MiB / buffer96m；OA、知识库各 384 MiB / Xmx128m |

两台均为 2 CPU、约 1800 MiB。镜像为 Temurin 21.0.11_10-jre-jammy、MySQL 8.4、Nginx 1.28.0-alpine、Certbot 5.8.0。主机只有 Docker，不要求 Compose。数据库使用独立 `upms-course-data` 卷；原课程卷保留。

## 首次准备

1. 本机运行 `bash scripts/build.sh`。将两个 JAR 分别放入 Manager 的 `/opt/upms/platform.jar` 和 Node1 的 `/opt/upms/demo-client.jar`。将三份来源 Excel 原样放入 Manager 的 `/opt/upms/source/`。
2. 将本目录的启动、恢复和续期脚本放入各机 `/opt/upms/`。Manager 同时放入 `nginx.conf`；首次 HTTP 验证可先用 `nginx-bootstrap.conf`。
3. `/opt/upms` 权限 700，环境文件权限 600。密码和客户端密钥仅写入服务器环境文件，不提交 Git，也不放入 Apifox 公共文档。
4. 先启动 Node1 的数据库，确认私网 3307 可达；再启动平台；平台就绪后启动两个 OAuth 客户端。首次 `start-node.sh` 会一起启动三个容器，客户端在平台未就绪时可能退出，可待平台就绪后重新启动。

私有环境文件的配置约定：

| 文件 | 必需配置 |
| --- | --- |
| mysql.env | MYSQL_ROOT_PASSWORD、MYSQL_DATABASE=upms、MYSQL_USER=upms、MYSQL_PASSWORD |
| platform.env | SPRING_PROFILES_ACTIVE=server；SERVER_ADDRESS=127.0.0.1；UPMS_JDBC_URL 指向私网 Node1:3307/upms；MYSQL_PASSWORD 与数据库一致；UPMS_ISSUER=https://113.44.144.119；UPMS_SEED=demo；UPMS_CACHE=false |
| platform.env | UPMS_DEMO_OA_URL=https://113.44.144.119:8443；UPMS_DEMO_KNOWLEDGE_URL=https://113.44.144.119:9443；UPMS_BOOTSTRAP_CLIENT_SUFFIX 和 UPMS_BOOTSTRAP_DIRECTORY_SUFFIX 为独立随机值 |
| oa.env | SPRING_PROFILES_ACTIVE=server；SERVER_ADDRESS=172.31.12.231；UPMS_ISSUER 同平台；DEMO_PORT=18081；DEMO_SYSTEM=oa；DEMO_SECRET=oa-加客户端后缀；DEMO_COOKIE=OA_SESSION；DEMO_TITLE=办公自动化演示；DEMO_READ=f001:查看；DEMO_WRITE=f002:新增 |
| knowledge.env | 同客户端配置；DEMO_PORT=18082；DEMO_SYSTEM=knowledge；DEMO_SECRET=knowledge-加客户端后缀；DEMO_COOKIE=KNOWLEDGE_SESSION；DEMO_TITLE=企业知识库演示；DEMO_READ=f007:查看；DEMO_WRITE=f002:编辑 |

引导后缀只在首次导入时生成数据库哈希，修改环境文件不会重置已登记凭据；需要轮换时通过最高管理员的凭据能力更新，并同步修改对应客户端私有配置。回调和退出地址必须与公网 origin 完全一致。

## HTTPS 与续期

已申请可信 IP shortlived 证书，三个入口共用该 IP 证书。证书及 ACME 状态位于 `/opt/upms/letsencrypt/`，验证文件位于 `/opt/upms/acme/`；私钥不归档到仓库。80 端口保留 HTTP-01 验证路径，其余 HTTP 重定向到 HTTPS。

Manager 的 `/etc/cron.d/upms-certificate` 每六小时执行：

```bash
/opt/upms/renew-certificate.sh >> /opt/upms/certificate-renewal.log 2>&1
```

短期证书依赖及时续期，检查失败日志和到期时间。2026-10-24 已通过 `certbot renew --dry-run`。手动模拟方法：

```bash
cd /opt/upms
docker run --rm --network host \
  -v "$PWD/letsencrypt:/etc/letsencrypt" \
  -v "$PWD/acme:/var/www/acme" certbot/certbot:v5.8.0 renew --dry-run
```

Nginx 覆盖外部转发头；应用限制在环回或内网地址，主机防火墙限制客户端端口来源。Cookie 设置 Secure 与 HttpOnly。静态资源启用 gzip；没有关闭 OAuth 回调或证书校验。

## 更新、停止与恢复

更新前保留旧 JAR，先完整上传到 `.next.jar` 并核对 SHA-256，再停止对应容器、替换文件并启动；不能覆盖运行中映射的 JAR。平台启动后检查 `/actuator/health/readiness`，再启动或验证客户端。平台重启清空内存会话、令牌索引及签名密钥，旧会话必须重新登录；业务资料保留。两个客户端的演示业务记录也不持久化。

```bash
# Manager
cd /opt/upms
docker stop upms-platform
cp platform.jar platform.previous.jar
mv platform.next.jar platform.jar
docker start upms-platform
# Node1：确认 demo-client.next.jar 已完整上传后执行
cd /opt/upms
docker stop upms-oa upms-knowledge
cp demo-client.jar demo-client.previous.jar
mv demo-client.next.jar demo-client.jar
docker start upms-oa upms-knowledge
```

单纯停止本项目使用 `docker stop`，不删除卷。恢复原课程业务须按顺序执行：

```bash
# Manager
bash /opt/upms/restore-lab.sh manager
# Node1
bash /opt/upms/restore-lab.sh node
# 最后回到 Manager
bash /opt/upms/restore-lab.sh services
```

原 Swarm 的 `lab_mysql`、`lab_productdemojpa` 已缩为 0，服务定义快照在 Manager `/opt/upms/previous-lab/`。监控未停止。不要同时启动两套数据库和业务服务挤占这两台主机内存。

## 发布静态交付

Nginx 将 `/opt/upms/public/design/` 发布为 `/design/`，将 `public/reports/` 发布为 `/reports/`。本机执行 `npm --prefix docs/tooling ci && npm --prefix docs/tooling run build` 后上传 `.runtime/design/` 内容。覆盖率的 HTML、XML、CSV 来自 `bash scripts/coverage.sh`，原始包及计数存于 `docs/verification/coverage/`；公开 HTML 是报告副本，不代表在服务器重新测过覆盖率或容量。
