# 正式说明书导出

在仓库根目录执行：

```bash
npm --prefix docs/tooling ci
npm --prefix frontend ci
python3 -m pip install pypdf
npm --prefix docs/tooling run pdf
```

需要 Node.js、Python 3 和本机 Google Chrome。其他安装位置可通过 `PDF_CHROME_PATH` 指定 Chrome 可执行文件。

输入为 `docs/统一权限管理系统需求与详细设计说明书.adoc`，版式为本目录 `spec-v2.css`。输出为 `docs/pdf/统一权限管理系统需求与详细设计说明书-v2.pdf`；带目录页码的 HTML 在 `.runtime/design/index.html`。只生成 HTML 可执行 `npm --prefix docs/tooling run build`。

正文使用 Times New Roman 和 Songti SC，标题使用 Arial 和 Heiti SC。参照模板的 10.5／22／16 磅字号、黑色标题、页眉横线、居中页码及蓝灰用例表；目录中功能描述、业务流程展开至第三级，其余保留两级。图形的黑白主题仅作用于导出副本。两页逻辑 ER 使用横向 A4，其余为纵向 A4。导出脚本按实际 PDF 目标页回填目录并检查页码收敛。
