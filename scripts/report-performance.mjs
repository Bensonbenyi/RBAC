import fs from 'node:fs';
import path from 'node:path';

// Gatling 3.16 的原始日志为二进制；保留它，并从原生报告表提取便于比较的数据。
const [directory, label] = process.argv.slice(2);
if (!directory || !label) throw new Error('用法：node scripts/report-performance.mjs 报告目录 标签');
const html = fs.readFileSync(path.join(directory, 'index.html'), 'utf8');
const rows = [];
for (const match of html.matchAll(/<tr id="(ROOT|group_[^"]+|req_[^"]+)"[^>]*>([\s\S]*?)<\/tr>/g)) {
  const [, id, content] = match;
  const name = content.match(/class="ellipsed-name">([^<]+)/)?.[1];
  const values = {};
  for (const cell of content.matchAll(/<td class="value [^"]*col-(\d+)">([^<]+)<\/td>/g)) {
    values[Number(cell[1])] = Number(cell[2].replaceAll(',', ''));
  }
  rows.push({ id, name, count: values[2], success: values[3], failure: values[4],
    failurePercent: values[5], meanRps: values[6], minMs: values[7], p50Ms: values[8],
    p75Ms: values[9], p95Ms: values[10], p99Ms: values[11], maxMs: values[12],
    meanMs: values[13], standardDeviationMs: values[14] });
}
if (!rows.length) throw new Error('报告没有找到统计表，不输出伪结果');
const output = path.join('docs', 'verification', 'performance');
fs.mkdirSync(output, { recursive: true });
fs.writeFileSync(path.join(output, `${label}.json`), JSON.stringify({ label,
  gatling: '3.16.0', sourceDirectory: directory, extractedAt: new Date().toISOString(), rows }, null, 2) + '\n');
console.log(rows.map(({ name, count, failure, p95Ms }) => ({ name, count, failure, p95Ms })));
