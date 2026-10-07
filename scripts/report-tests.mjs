import fs from 'node:fs';
import path from 'node:path';

// 汇总真实 Surefire 结果；有失败或缺少测试报告时不输出“通过”。
const suites = [];
for (const module of ['platform', 'demo-client']) {
  const directory = path.join(module, 'target', 'surefire-reports');
  const files = fs.readdirSync(directory).filter((name) => name.startsWith('TEST-') && name.endsWith('.xml'));
  if (!files.length) throw new Error(module + ' 没有测试报告');
  for (const file of files) {
    const xml = fs.readFileSync(path.join(directory, file), 'utf8');
    const header = xml.match(/<testsuite\s+([^>]+)>/)?.[1];
    if (!header) throw new Error('无效报告：' + file);
    const attributes = Object.fromEntries([...header.matchAll(/([\w]+)="([^"]*)"/g)].map((m) => [m[1], m[2]]));
    suites.push({ module, name: attributes.name, tests: Number(attributes.tests),
      failures: Number(attributes.failures), errors: Number(attributes.errors),
      skipped: Number(attributes.skipped), seconds: Number(attributes.time) });
  }
}
const total = suites.reduce((sum, suite) => ({ tests: sum.tests + suite.tests,
  failures: sum.failures + suite.failures, errors: sum.errors + suite.errors,
  skipped: sum.skipped + suite.skipped }), { tests: 0, failures: 0, errors: 0, skipped: 0 });
const result = { measuredAt: new Date().toISOString(), database: 'upms_test', total, suites,
  passed: total.failures === 0 && total.errors === 0 && total.skipped === 0 };
fs.mkdirSync('docs/verification', { recursive: true });
fs.writeFileSync('docs/verification/backend-tests.json', JSON.stringify(result, null, 2) + '\n');
console.log(result);
if (!result.passed) process.exitCode = 1;
