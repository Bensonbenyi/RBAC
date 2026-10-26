// 渲染课程 AsciiDoc，输出完整 HTML 及实际引用的图片，不生成 PDF。
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const require = createRequire(path.join(root, 'docs/tooling/package.json'));
const asciidoctor = require('@asciidoctor/core');
const source = path.join(root, 'docs/统一权限管理系统需求与详细设计说明书.adoc');
const destination = path.join(root, '.runtime/design');
fs.mkdirSync(path.join(destination, 'assets'), { recursive: true });
const processor = asciidoctor();
const document = processor.loadFile(source, { safe: 'safe', standalone: true });
const chapters = document.getSections();
if (chapters.length !== 7) throw new Error(`主章节应为 7，实际为 ${chapters.length}`);
const images = [...fs.readFileSync(source, 'utf8').matchAll(/image::?([^\[]+)\[/g)];
for (const [, filename] of images) {
  const origin = path.join(root, 'docs/assets', filename);
  if (!fs.existsSync(origin)) throw new Error(`图片不存在：${filename}`);
  // 正式稿使用黑白图形主题；原始 SVG 和 draw.io 图形及其配色保持可编辑。
  const theme = `<style>
    text, foreignObject * { fill: #000 !important; color: #000 !important; }
    rect:not([fill="none"]), ellipse:not([fill="none"]), polygon:not([fill="none"]) {
      fill: #fff !important;
    }
    [stroke]:not([stroke="none"]) { stroke: #000 !important; }
    path:not([fill="none"])[fill] { fill: #fff !important; }
  </style>`;
  const graphic = fs.readFileSync(origin, 'utf8').replace('</svg>', `${theme}</svg>`);
  fs.writeFileSync(path.join(destination, 'assets', filename), graphic);
}
// HTML 独立发布时源码链接指向同一仓库，避免 /design/ 下的相对链接失效。
let html = document.convert().replace(/href="([^"#]+)"/g, (all, target) => {
  if (/^(https?:|mailto:|data:)/.test(target)) return all;
  const resolved = path.resolve(path.dirname(source), decodeURIComponent(target.split('#')[0]));
  const relative = path.relative(root, resolved);
  if (relative.startsWith('..')) throw new Error(`链接超出仓库：${target}`);
  if (!fs.existsSync(resolved)) throw new Error(`链接目标不存在：${target}`);
  const suffix = target.includes('#') ? '#' + target.split('#').slice(1).join('#') : '';
  return `href="https://github.com/Bensonbenyi/RBAC/blob/codex/upms-implementation/${relative.split(path.sep).map(encodeURIComponent).join('/')}${suffix}"`;
});
// 与课程模板一致，仅功能描述、业务流程在目录中再展开一级。
const expandedTitles = new Set(
  String(document.getAttribute('toc-expanded-sections') || '').split(',').map(title => title.trim()));
const expandedIds = new Set(chapters.flatMap(chapter => chapter.getSections())
  .filter(section => expandedTitles.has(section.getTitle())).map(section => section.getId()));
html = html.replace(
  /(<li><a href="#([^"]+)">[^<]+<\/a>)\s*(<ul class="sectlevel3">[\s\S]*?<\/ul>)/g,
  (all, heading, id, children) => expandedIds.has(id) ? `${heading}\n${children}` : heading);
// 封面分成两行；正文只保留模板式编号，表内场景标题使用灰底。
html = html
  .replace('<h1>统一权限管理系统需求与详细设计说明书</h1>',
    '<h1>统一权限管理系统<br>需求与详细设计说明书</h1>')
  .replace(/version (\d+\.\d+),/, '版本 $1')
  .replace('<span id="revdate">2026-10-26</span>',
    '<span id="revdate">2026 年 10 月 26 日</span>')
  .replace(/(<h[234][^>]*>|<a href="#[^"]+">)(\d+(?:\.\d+)*)\. /g, '$1$2 ')
  .replace(/<tr>\s*<td[^>]*>\s*<p class="tableblock"><strong>(主成功场景：|扩展场景：)/g,
    '<tr class="scene-band"><td><p class="tableblock"><strong>$1');
// 逻辑关系标题随第一张横向图换页，避免孤立在上一页末尾。
html = html.replace(
  /(<h4 id="_逻辑关系">[^<]+<\/h4>)\s*(<div class="openblock schema-plate">\s*<div class="content">)/,
  '$2$1');
fs.writeFileSync(path.join(destination, 'index.html'), html);
console.log(`已生成 ${destination}/index.html；${chapters.length} 章，${images.length} 处图片引用`);
