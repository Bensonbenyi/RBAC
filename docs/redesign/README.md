# 统一权限管理系统 重做设计 v3.1.0

本版包含七章正文、10 个用例和 78 张图。34 个 HTTP 接口均有对应顺序图，20 项业务写操作分别提供结构化与 DDD 设计。

| 文件 | 内容 |
| --- | --- |
| [PDF](统一权限管理系统需求与详细设计说明书-v3.1.0.pdf) | 完整设计说明书 |
| [AsciiDoc](统一权限管理系统需求与详细设计说明书.adoc) | 正文源文件，与 PDF 内容一致 |
| [完整设计包](统一权限管理系统设计-v3.1.0.zip) | PDF、正文、模型、图片、SQL 与生成工具 |
| [StarUML 模型](assets/spec/rbac-spec.mdj) | 76 张 UML 图，含 58 张顺序图、13 张类图、3 张架构包图及 2 张活动图 |
| [流程覆盖清单](流程配图覆盖清单.md) | 34 个接口与具体图稿的对应关系 |
| [绘图规范与参考](绘图规范与参考.md) | OMG 标准条款与老师示例的采用范围 |
| [陈氏 ER 模型](assets/rbac-chen-v1.mdj) | 可编辑的基本图元模型 |
| [数据库关系模型](assets/rbac-schema-v1.mdj) | 七张表及外键关系，可编辑的基本图元模型 |
| [数据库 DDL](sql/schema.sql) | MySQL 8.4，七张表、八条外键 |
| [课件图例与验收](课件图例与绘图验收.md) | 教学示例页码与绘图核对依据 |

UML 模型保留原生类、接口、操作、关系、生命线与消息。打开一个 mdj 文件，在 Model Explorer 中选择图；配色提示选择 Keep Colors。SVG 和 PNG 使用显式坐标导出。StarUML 按本机字体设置呈现标签。

说明书统一 A4 纵向，页边距、正文和表格字体参照 V2。用例保留蓝底级别行、灰底场景行，调用说明采用“实际方法／调用说明 URL／功能”三列表。全部图形为黑白。

授权遵循用户、角色、权限两组多对多关系，角色归属一个部门。用户名与手机号登录，password 字段承载手机号，使用 Session 和 Cookie。两套实现共用 API 和数据库，通过 app.backend-strategy 在启动时选择。对象拷贝使用老师的 CloneFactory，运行日志使用 SLF4J 和 Logback。

用户暂按至多一个部门、管理员暂按本部门直接成员设计。这两项随后续业务确认同步修订。

## 重新生成与核对

在仓库根目录执行。依赖 Node.js、Python 3、pypdf、Pillow、rsvg-convert 与 Google Chrome；中文排版使用本机宋体和黑体。

```sh
npm --prefix docs/redesign/tooling ci
python3 docs/redesign/tools/draw_models.py
python3 docs/redesign/tools/draw_complete.py
python3 docs/redesign/tools/write_spec.py
python3 docs/redesign/tools/audit_design.py
npm --prefix docs/redesign/tooling run audit:diagrams
npm --prefix docs/redesign/tooling run pdf
python3 docs/redesign/tools/bundle.py
```

正文由 write_spec.py 和 spec_plates.py 生成。flow_catalog.py 管理接口与流程清单；draw_complete.py 绘制当前 UML，draw_spec.py 提供底层模型与绘图函数。陈氏图和表关系图由 draw_models.py 生成。在 StarUML 编辑后应同步生成源及导出图。

核对工具检查模型引用、调用签名及参数数量、接口与图的覆盖、图中文字边界与重叠、图片导出数量，以及实际目录页码。生成后仍需渲染 PDF，逐页检查图表和分页。检查产物位于 build/，中间文件不进入 Git。

Chrome 默认路径为 /Applications/Google Chrome.app/Contents/MacOS/Google Chrome，可用 PDF_CHROME_PATH 覆盖。VERSION 是三段版本号的唯一来源：兼容的设计补充升次版本，排版与勘误升修订号；发布记录见 CHANGELOG.md。

assets/design 为早期淘汰图稿，当前说明书使用 assets/spec 和两张 ER、表关系图。前期讨论稿与 v3.0.0 发布文件保留历史；当前评审使用 v3.1.0。两套后端实现按说明书验收条件开展。
