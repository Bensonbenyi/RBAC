# 正式说明书导出

在仓库根目录执行：

```bash
npm --prefix docs/tooling ci
npm --prefix frontend ci
python3 -m pip install pypdf
npm --prefix docs/tooling run pdf
```

需要 Node.js、Python 3 和本机 Google Chrome。其他安装位置可通过 `PDF_CHROME_PATH` 指定 Chrome 可执行文件。

输入为 `docs/统一权限管理系统需求与详细设计说明书.adoc`，版式为本目录 `spec-v2.css`。输出为 `docs/统一权限管理系统需求与详细设计说明书-v2.pdf`；带目录页码的 HTML 在 `.runtime/design/index.html`。只生成 HTML 可执行 `npm --prefix docs/tooling run build`。

正文使用 Times New Roman 和 Songti SC，标题使用 Arial 和 Heiti SC。参照模板的 10.5／22／16 磅字号、黑色标题、页眉横线、居中页码及蓝灰用例表；目录中功能描述、业务流程展开至第三级，其余保留两级。全部页面统一为竖向 A4，图形统一使用黑白导出主题，数据库表关系图的 SVG 和 draw.io 源文件也采用黑白配色。导出脚本按实际 PDF 目标页回填目录、检查页码收敛，并核验所有页面尺寸。

`docs/assets/spec-database-relations.drawio` 含三页可编辑表关系图，依次为 `spec-database-schema.svg`、`spec-database-catalog.svg` 和 `spec-database-lifecycle.svg`。每表按“中文含义、字段名、类型、约束”四列展示；后两页采用并排表框压缩篇幅。修改源图后应重新导出相应页面，并保留 SVG 中的中文字体栈。
