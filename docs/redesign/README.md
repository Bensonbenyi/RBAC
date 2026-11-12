# 统一权限管理系统 重做设计 v3.0.0

当前说明书包含七章正文、10 个用例、13 张图和 18 项实现验收场景。

| 文件 | 内容 |
| --- | --- |
| [PDF](统一权限管理系统需求与详细设计说明书-v3.0.0.pdf) | 排版后的完整说明书 |
| [AsciiDoc](统一权限管理系统需求与详细设计说明书.adoc) | 正文源文件，与 PDF 使用同一份内容 |
| [StarUML 模型](assets/spec/rbac-spec.mdj) | 11 张 UML 图，包含包图、类图、顺序图和活动图 |
| [陈氏 ER 模型](assets/rbac-chen-v1.mdj) | 可编辑的基本图元模型 |
| [数据库关系模型](assets/rbac-schema-v1.mdj) | 七张表及外键，可编辑的基本图元模型 |
| [数据库 DDL](sql/schema.sql) | MySQL 8.4 建表语句，七张表、八条外键 |
| [课件图例与绘图验收](课件图例与绘图验收.md) | 教学示例对应页码与绘图检查依据 |

UML 模型使用原生类、接口、操作、关系、生命线和消息。打开一个 `.mdj` 后，在 Model Explorer 中选择图；出现配色提示时选择 Keep Colors。SVG 与 PNG 采用固定坐标导出，StarUML 会根据自身字体设置呈现标签。

全文统一使用 A4 纵向纸张，页边距、正文与表格字体参照 v2。用例保留蓝底级别行、灰底场景行，调用说明采用“实际方法／调用说明 URL／功能”三列表。图形使用黑白符号。

设计基线为用户经角色获得权限，角色归属一个部门。用户名与手机号登录，`password` 字段承载手机号；会话采用 Session 和 Cookie。结构化版与 DDD 版共用接口及数据库，使用 `app.backend-strategy` 在启动时选择实现。对象转换采用老师的 CloneFactory，各执行层使用 SLF4J 和 Logback。

用户暂按至多一个部门设计，管理员范围暂限本部门直接成员。这两项按后续确认结果同步修改模型、接口和数据定义。

## 修改与重新导出

在仓库根目录执行。需要 Node.js、Python 3、Python 包 `pypdf` 和 `Pillow`、命令 `rsvg-convert`，以及 Google Chrome。PDF 使用本机宋体和黑体字体；更换机器后需核对字体及分页。

```sh
npm --prefix docs/redesign/tooling ci
python3 docs/redesign/tools/write_spec.py
python3 docs/redesign/tools/draw_models.py
python3 docs/redesign/tools/draw_spec.py
npm --prefix docs/redesign/tooling run pdf
```

正文由 `tools/write_spec.py` 生成，修改正文时先修改该脚本。UML 图由 `tools/draw_spec.py` 生成，陈氏图及表关系图由 `tools/draw_models.py` 生成；在 StarUML 手工修改模型后，应同步图源及导出图，防止重新生成覆盖修改。

Chrome 路径默认是 `/Applications/Google Chrome.app/Contents/MacOS/Google Chrome`，可通过环境变量 `PDF_CHROME_PATH` 调整。导出脚本检查七章结构、图片加载和目录页码收敛，过程文件放在 `build/`。最终 PDF 仍需逐页查看表格、文字、连线和分页。

`assets/design/` 是已淘汰的早期图稿；当前说明书引用 `assets/spec/` 及两张 ER 和数据库图。`第一版设计.md`、`数据库表设计.md` 保留前期讨论，评审以 v3 说明书为准。

本次交付为设计文档与模型。两套后端实现及业务、性能测试按说明书中的验收条件开展。

版本号采用 `主版本.次版本.修订号`，由 `VERSION` 统一维护。兼容当前需求的设计补充升次版本，文案、排版和勘误升修订号；每次发布记录在 `CHANGELOG.md`。
