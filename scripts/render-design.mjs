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
  fs.copyFileSync(origin, path.join(destination, 'assets', filename));
}
// HTML 独立发布时源码链接指向同一仓库，避免 /design/ 下的相对链接失效。
const html = document.convert().replace(/href="([^"#]+)"/g, (all, target) => {
  if (/^(https?:|mailto:|data:)/.test(target)) return all;
  const resolved = path.resolve(path.dirname(source), decodeURIComponent(target.split('#')[0]));
  const relative = path.relative(root, resolved);
  if (relative.startsWith('..')) throw new Error(`链接超出仓库：${target}`);
  if (!fs.existsSync(resolved)) throw new Error(`链接目标不存在：${target}`);
  const suffix = target.includes('#') ? '#' + target.split('#').slice(1).join('#') : '';
  return `href="https://github.com/Bensonbenyi/RBAC/blob/codex/upms-implementation/${relative.split(path.sep).map(encodeURIComponent).join('/')}${suffix}"`;
});
fs.writeFileSync(path.join(destination, 'index.html'), html);
console.log(`已生成 ${destination}/index.html；${chapters.length} 章，${images.length} 处图片引用`);
