# 统一权限管理系统设计 v3.7.0

本版采用 v3.2.2 的字体、表格和图幅，按当前 RBAC1 需求组织概要和详细设计。

| 交付文件 | 内容 |
| --- | --- |
| [概要设计说明书](统一权限管理系统概要设计说明书-v3.7.0.pdf) | 九章总体设计、四个架构故事、ATAM、设计模型、接口、数据、部署与追溯 |
| [需求与详细设计说明书](统一权限管理系统需求与详细设计说明书-v3.7.0.pdf) | 八章需求与详细设计，功能下附调用说明及交互图 |
| 两份同名 `.adoc` | 可编辑正文 |
| [UML 模型](assets/v33/rbac-spec.mdj) | 架构、包、类、活动、状态和时序图 |
| [陈氏 ER 图](assets/v33/chen.mdj) | 业务实体与关系 |
| [数据库表关系图](assets/v33/schema.mdj) | 八张业务表及技术会话表、主外键关系 |
| [设计源文件包](统一权限管理系统设计-v3.7.0.zip) | PDF、AsciiDoc、模型、SVG、PNG、SQL 与生成工具 |

用户和角色均归属单个部门，授权采用用户角色、角色权限两组多对多关系，并用角色继承表表达同部门无环层次。平台自身和老师课程系统作为两个业务系统接入。登录请求沿用老师的 username、password、systems 约定，password 承载手机号；成功后使用 Bearer JWT 和持久化技术会话。

在 StarUML 使用浅色主题查看黑白模型。遇到颜色转换提示时选择 Keep Colors。

结构化与 DDD 共用输入层、Service Port 和 Infrastructure，通过 `BACKEND_STRATEGY` 启动切换。持久化适配器统一通过 Spring Data JPA 仓储执行数据库访问。源码与验证记录位于仓库根目录和 `docs/implementation/`。

重建需要 Python 3、rsvg-convert、Node.js、Chrome 及 pypdf。在仓库根目录运行：

```sh
npm ci --prefix docs/redesign/tooling
./tooling/build-design.sh
```

`tools/v33_catalog.py` 维护 34 个平台 HTTP 操作；`draw_v33.py` 与 `draw_data_v33.py` 使用人工坐标生成黑白图稿和 StarUML 模型；`write_v33.py` 写入两份文档；`tooling/export.mjs` 导出 A4 纵向 PDF 并校验目录页码。当前入口是 `build-design.sh`。历史 PDF、设计包及旧流程图可从对应 Git 提交查阅。

如需课程提交文件名，执行 `python3 docs/redesign/tools/bundle.py --group 实际组号`。

v3.3.0 的实测基线随图源保存在 `assets/v33/performance-baseline.json`，用于重建性能结果表；完整原始结果与部署证据见 `docs/implementation/实施报告.md`。

本次修订逐项对照 v3.2.2，恢复完整用例表、三列调用说明、字段字典、两套完整包图及持久化交互。详见 [对照修订记录](与v3.2.2对照修订.md)。

v3.7.0 保留完整角色管理，目录服务由两套策略共用，员工数据和部门由 SQL 维护。课程入口使用老师原版前端。验证记录见 [v3.7.0 整改报告](../implementation/整改报告-v3.7.0.md)。
