// 由 AsciiDoc 导出正式 PDF，并依据 PDF 的实际目标页码回填目录。
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { createRequire } from 'node:module';
import { execFileSync } from 'node:child_process';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
execFileSync(process.execPath, [path.join(root, 'scripts/render-design.mjs')], { stdio: 'inherit' });
const require = createRequire(path.join(root, 'frontend/package.json'));
const { chromium } = require('@playwright/test');
const chrome = process.env.PDF_CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const browser = await chromium.launch(fs.existsSync(chrome) ? { executablePath: chrome } : {});
const htmlPath = path.join(root, '.runtime/design/index.html');
const pdfPath = path.join(root, 'docs/pdf/统一权限管理系统需求与详细设计说明书-v2.pdf');
fs.mkdirSync(path.dirname(pdfPath), { recursive: true });

try {
  const page = await browser.newPage();
  await page.goto(pathToFileURL(htmlPath).href, { waitUntil: 'networkidle' });
  await page.evaluate(async () => {
    await document.fonts.ready;
    await Promise.all([...document.images].map(image => image.decode()));
    for (const link of document.querySelectorAll('#toc a')) {
      const label = document.createElement('span');
      label.className = 'toc-label';
      label.textContent = link.textContent;
      const leader = document.createElement('span');
      leader.className = 'toc-leader';
      const number = document.createElement('span');
      number.className = 'toc-page';
      number.textContent = '00';
      link.replaceChildren(label, leader, number);
    }
  });
  const readPages = `
import json, sys
from urllib.parse import unquote
from pypdf import PdfReader
pdf = PdfReader(sys.argv[1])
print(json.dumps({unquote(name.lstrip('/')): pdf.get_destination_page_number(target) + 1
                  for name, target in pdf.named_destinations.items()}, ensure_ascii=False))
`;
  let previous = '';
  let stable = false;
  for (let pass = 0; pass < 4; pass++) {
    await page.pdf({ path: pdfPath, format: 'A4', printBackground: true,
      preferCSSPageSize: true, outline: true, tagged: true });
    const destinations = JSON.parse(execFileSync('python3', ['-c', readPages, pdfPath], { encoding: 'utf8' }));
    const mapping = await page.evaluate(pages => {
      const result = {};
      for (const link of document.querySelectorAll('#toc a')) {
        const id = decodeURIComponent(link.getAttribute('href').slice(1));
        if (!(id in pages)) throw new Error(`目录目标未出现在 PDF 中：${id}`);
        link.querySelector('.toc-page').textContent = pages[id];
        result[id] = pages[id];
      }
      return result;
    }, destinations);
    const current = JSON.stringify(mapping);
    if (current === previous) { stable = true; break; }
    previous = current;
  }
  if (!stable) throw new Error('目录页码四次排版后仍未稳定，请检查分页。');
  // 保留回填后的 HTML；PDF 与浏览器版使用相同内容和样式。
  fs.writeFileSync(htmlPath, await page.content());
  console.log(`已生成 ${pdfPath}；目录页码已收敛并校验`);
} finally {
  await browser.close();
}
