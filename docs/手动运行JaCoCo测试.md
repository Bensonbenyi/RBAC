# 手动运行 JaCoCo 测试

在项目根目录执行。需要 Java 21、Python 3 和已经启动的 Docker Desktop；首次运行还需要下载 Maven 依赖和 MySQL 镜像。

## 1. 运行测试和覆盖率检查

```bash
bash scripts/coverage.sh
```

脚本会启动项目的 MySQL，清理旧构建，运行 `platform`、`demo-client` 的后端测试并生成报告。两个模块的行覆盖率和分支覆盖率都必须达到 100%，命令才算通过。测试使用独立的 `upms_test` 数据库，不需要先启动后台、OA 或知识库。

看到 Maven 的 `BUILD SUCCESS`，且脚本最后显示“覆盖率验收通过”，才表示本次运行通过。100% 表示测试执行过统计范围内的代码和分支，不代表没有缺陷，仍需检查测试断言。

## 2. 打开报告

macOS 执行：

```bash
open coverage-report/target/site/jacoco-aggregate/index.html
```

进入 `platform` 或 `demo-client`，逐层点开包、类和方法，可看到覆盖情况。绿色表示已覆盖，红色表示未覆盖，黄色表示该行的分支只覆盖了一部分。

各模块报告也在 `platform/target/site/jacoco/index.html` 和 `demo-client/target/site/jacoco/index.html`。本次结果及代码版本记录在 `.runtime/coverage/verification.json`，报告副本在同目录。

## 3. 没通过时

- Docker 连接失败：打开 Docker Desktop，等它就绪后重跑。
- 3307 端口被占用或 MySQL 未就绪：执行 `docker compose ps`、`docker compose logs --tail=80 mysql` 检查项目容器，不要删除数据卷。
- 测试失败：查看终端提示和两个模块的 `target/surefire-reports/`。
- 覆盖率不足：根据 JaCoCo 的红色代码或黄色分支补充有断言的测试，再运行完整命令。

只想测量尚未达标的覆盖率时，可以运行 `bash scripts/coverage.sh --baseline`。此模式跳过 100% 门禁，即使命令成功也不能作为验收通过结果。正式展示前必须重新运行不带参数的命令；不要把旧报告当作本次结果。
