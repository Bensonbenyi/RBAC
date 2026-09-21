---
name: markdown-pdf-export
description: Export Markdown to PDF in this repository when the user explicitly requests PDF export, using the project's fixed preview-stix style with readable Chinese, English, and math.
---

# Markdown PDF Export

Use this skill only on explicit request, such as “使用 markdown-pdf-export skill 转 PDF”.

Goal: turn Markdown into a clean PDF while preserving the feeling of VS Code Markdown preview: white page, clear heading hierarchy, readable Chinese/English text, and properly rendered math formulas.

## Fixed style

Always use `preview-stix`. Do not ask the user to choose a style.

Use a screen-reading layout close to VS Code Markdown preview. Chinese tends toward `PingFang SC`; English and math can use `STIX Two Text` / `STIX Two Math`. If fonts or tools are unavailable, choose close local alternatives and say what changed.

## Workflow

Prefer flexible tools over a fixed command. Good routes include:

- `pandoc + xelatex` for reliable Chinese and rendered math.
- Browser print / Playwright when the exact HTML preview look matters.
- WeasyPrint only if math is already rendered into static HTML/SVG, because it may not run KaTeX scripts.

Default output path: put PDFs near the source or under `output/pdf/`. Name files clearly, for example:

- `原文件名-preview-stix.pdf`

## Checks

Before finishing:

- Confirm Chinese text is not missing glyphs.
- Confirm formulas are rendered, not raw LaTeX like `\mathrm{dp}`.
- Confirm page size and page count look reasonable.
- Render or inspect at least the first page when possible.
- If one route fails, try another route instead of forcing the same method.
