import fs from 'node:fs';
import crypto from 'node:crypto';
import { setTimeout as delay } from 'node:timers/promises';

// 在专用压测平台持续收到 Gatling 请求时执行真实变更，原始时间和判断逐条保存。
const base = 'http://localhost:18080';
const source = fs.readFileSync('.runtime/load/accounts.csv', 'utf8').trim().split('\n').slice(1)
  .map((line) => line.split(','));
const account = (id) => source.find((row) => Number(row[0]) === id);
const results = [];
const forbidden = [];
class Browser {
  cookies = new Map();
  async request(path, method = 'GET', data, bearer, basic) {
    const url = new URL(path, base);
    if (url.origin !== base) throw new Error('测试不跟随外部回调');
    const headers = { Accept: '*/*', Cookie: [...this.cookies].map(([k, v]) => `${k}=${v}`).join('; ') };
    if (bearer) headers.Authorization = `Bearer ${bearer}`;
    if (basic) headers.Authorization = `Basic ${Buffer.from(basic).toString('base64')}`;
    if (data instanceof URLSearchParams) headers['Content-Type'] = 'application/x-www-form-urlencoded';
    else if (data) headers['Content-Type'] = 'application/json';
    const response = await fetch(url, { method, headers, redirect: 'manual',
      signal: AbortSignal.timeout(5000), body: data instanceof URLSearchParams ? data
        : data ? JSON.stringify(data) : undefined });
    for (const cookie of response.headers.getSetCookie()) {
      const pair = cookie.split(';')[0]; const index = pair.indexOf('=');
      this.cookies.set(pair.slice(0, index), pair.slice(index + 1));
    }
    const text = await response.text();
    let json; try { json = JSON.parse(text); } catch { /* HTML 或空重定向体。 */ }
    return { status: response.status, location: response.headers.get('location'), text, json };
  }
  async login(id) {
    const page = await this.request('/login');
    const csrf = page.text.match(/name="_csrf"[^>]*value="([^"]+)"/)?.[1];
    if (!csrf) throw new Error('登录页没有 CSRF');
    const [, username, phone] = account(id);
    const response = await this.request('/login', 'POST', new URLSearchParams({ username, phone, _csrf: csrf }));
    if (response.status !== 302 || response.location?.includes('error')) throw new Error('登录失败');
    return response;
  }
  async admin(path, data) {
    const csrf = await this.request('/api/csrf');
    const headers = { Cookie: [...this.cookies].map(([k, v]) => `${k}=${v}`).join('; '),
      Accept: 'application/json' };
    if (data) { headers['Content-Type'] = 'application/json'; headers[csrf.json.headerName] = csrf.json.token; }
    const response = await fetch(base + '/api/admin' + path, { method: data ? 'POST' : 'GET',
      headers, body: data ? JSON.stringify(data) : undefined, signal: AbortSignal.timeout(5000) });
    const json = await response.json();
    if (!response.ok) throw new Error(`${response.status}: ${json.detail}`);
    return json;
  }
  async oidc(id) {
    const verifier = crypto.randomBytes(32).toString('base64url');
    const state = crypto.randomUUID(); const nonce = crypto.randomUUID();
    const redirect = 'http://localhost:18081/login/oauth2/code/upms';
    const query = new URLSearchParams({ client_id: 'oa', response_type: 'code', scope: 'openid profile',
      redirect_uri: redirect, state, nonce, code_challenge_method: 'S256',
      code_challenge: crypto.createHash('sha256').update(verifier).digest('base64url') });
    await this.request('/oauth2/authorize?' + query);
    const signedIn = await this.login(id);
    const authorized = await this.request(signedIn.location);
    const callback = new URL(authorized.location);
    if (callback.origin !== 'http://localhost:18081' || callback.searchParams.get('state') !== state)
      throw new Error('回调或 state 不匹配');
    const tokens = await this.request('/oauth2/token', 'POST', new URLSearchParams({
      grant_type: 'authorization_code', code: callback.searchParams.get('code'), redirect_uri: redirect,
      code_verifier: verifier }), undefined, 'oa:oa-demo-secret');
    if (tokens.status !== 200) throw new Error('兑换失败');
    const parts = tokens.json.id_token.split('.');
    const header = JSON.parse(Buffer.from(parts[0], 'base64url')); const claims = JSON.parse(Buffer.from(parts[1], 'base64url'));
    const jwks = (await this.request('/oauth2/jwks')).json;
    const jwk = jwks.keys.find((key) => key.kid === header.kid);
    if (header.alg !== 'RS256' || !crypto.verify('RSA-SHA256', Buffer.from(parts.slice(0, 2).join('.')),
      crypto.createPublicKey({ key: jwk, format: 'jwk' }), Buffer.from(parts[2], 'base64url'))
      || claims.iss !== base || !claims.aud.includes('oa') || claims.nonce !== nonce || claims.sub !== String(id))
      throw new Error('ID Token 校验失败');
    return tokens.json;
  }
}

const admin = new Browser();
await admin.login(1000);
const highest = await admin.admin('/me');
if (!highest.superAdmin || highest.username !== 'SG001000') throw new Error('只允许专用压测管理员');
const system = (await admin.admin('/systems')).find((item) => item.key === 'oa');
const original = (await admin.admin('/employees/13/permissions')).find((f) => f.assignment.systemId === system.id);
const originalAdministrator = await admin.admin('/employees/11');
const browser = new Browser(); const tokens = await browser.oidc(13);
const otherBrowser = new Browser(); const otherTokens = await otherBrowser.oidc(13);
const permissionKey = 'acceptance-private:查看';
async function directory(active) {
  const client = new Browser();
  const token = await client.request('/oauth2/token', 'POST', new URLSearchParams({
    grant_type: 'client_credentials', scope: 'directory.report' }), undefined, 'oa-directory:oa-directory-secret');
  const current = (await admin.admin('/systems')).find((item) => item.id === system.id);
  const items = (await admin.admin(`/systems/${system.id}/permissions`))
    .filter((item) => item.key !== permissionKey).map(({ key, module, feature, action, active }) =>
      ({ key, module, feature, action, active }));
  items.push({ key: permissionKey, module: '压测专用', feature: '变更验收', action: '查看', active });
  const response = await client.request('/api/directory/report', 'POST',
    { revision: current.directoryRevision + 1, items }, token.json.access_token);
  if (response.status !== 200) throw new Error('目录变更失败：' + response.text);
}
await directory(true);
const permission = (await admin.admin(`/systems/${system.id}/permissions`)).find((p) => p.key === permissionKey);
const role = await admin.admin('/roles', { id: null, systemId: system.id, departmentId: 1,
  name: '变更验收-' + crypto.randomUUID(), template: false, active: true, version: 0, permissions: [permission.id] });
const empty = await admin.admin('/roles', { id: null, systemId: system.id, departmentId: 1,
  name: '空角色验收-' + crypto.randomUUID(), template: false, active: true, version: 0, permissions: [] });
async function assignment(roleId) {
  const facts = await admin.admin('/employees/13/permissions');
  const version = facts.find((f) => f.assignment.systemId === system.id).assignment.version;
  return admin.admin('/assignments', { employeeId: 13, systemId: system.id, roleId, expectedVersion: version });
}
async function check(token = tokens.access_token) {
  const response = await browser.request('/api/runtime/check', 'POST', { system: 'oa', permission: permissionKey }, token);
  return { status: response.status, allowed: response.json?.allowed === true, reason: response.json?.reason ?? response.json?.code };
}
async function observe(name, mutation, expected, token = tokens.access_token,
  read = () => check(token), denialStatus = 401) {
  // 先证明旧状态相反，防止用已退出令牌验证停用而得到没有意义的成功。
  const before = await read();
  if (before.status !== 200 || before.allowed === expected) throw new Error(name + ' 的旧状态不成立');
  await mutation(); const committed = performance.now();
  let firstCorrect; let incorrectAfterDeadline = 0; const samples = [];
  while (performance.now() - committed < 3500) {
    const result = await read(); const elapsed = performance.now() - committed;
    const semanticResult = result.status === 200 || (!expected && result.status === denialStatus);
    samples.push({ elapsedMs: Math.round(elapsed), ...result });
    if (semanticResult && result.allowed === expected && firstCorrect === undefined) firstCorrect = elapsed;
    if (elapsed >= 3000 && (!semanticResult || result.allowed !== expected)) incorrectAfterDeadline++;
    await delay(50);
  }
  const passed = firstCorrect !== undefined && firstCorrect <= 3000 && incorrectAfterDeadline === 0;
  results.push({ name, expected, firstCorrectMs: Math.round(firstCorrect ?? -1), incorrectAfterDeadline, passed, samples });
  if (!passed) forbidden.push(name);
}
try {
  await observe('单个授权', () => assignment(role.id), true);
  await observe('换角替换旧权限', () => assignment(empty.id), false);
  await assignment(role.id);
  await observe('撤权', () => assignment(null), false);
  if ((await browser.request('/userinfo', 'GET', undefined, tokens.access_token)).status !== 200)
    throw new Error('撤权错误地结束了身份会话');
  await assignment(role.id);
  await observe('角色权限修改', async () => { const current = (await admin.admin('/roles')).find((r) => r.id === role.id);
    await admin.admin('/roles', { ...current, permissions: [] }); }, false);
  const current = (await admin.admin('/roles')).find((r) => r.id === role.id);
  await admin.admin('/roles', { ...current, permissions: [permission.id] });
  await observe('目录明确停用', () => directory(false), false);
  await directory(true);
  if ((await check()).allowed) throw new Error('重新启用错误地恢复了旧角色选择');
  const resetRole = (await admin.admin('/roles')).find((r) => r.id === role.id);
  await admin.admin('/roles', { ...resetRole, permissions: [permission.id] });
  const currentSystem = (await admin.admin('/systems')).find((s) => s.id === system.id);
  await observe('部门可用系统范围取消', () => admin.admin(`/systems/${system.id}/departments`,
    { version: currentSystem.version, departments: currentSystem.departments.filter((d) => d !== 1) }), false);
  const unavailable = (await admin.admin('/systems')).find((s) => s.id === system.id);
  await admin.admin(`/systems/${system.id}/departments`, { version: unavailable.version, departments: currentSystem.departments });
  const deptAdmin = await admin.admin('/employees/11');
  await admin.admin('/administrators', { employeeId: 11, superAdmin: false, departmentAdmin: true, version: deptAdmin.version });
  const departmentBrowser = new Browser(); await departmentBrowser.login(11);
  const beforeCancel = await admin.admin('/employees/11');
  await observe('管理员身份取消', () => admin.admin('/administrators',
    { employeeId: 11, superAdmin: false, departmentAdmin: false, version: beforeCancel.version }),
  false, undefined, async () => {
    const response = await departmentBrowser.request('/api/admin/employees');
    return { status: response.status, allowed: response.status === 200, reason: response.json?.code };
  }, 403);
  await observe('统一退出', async () => {
    const form = await browser.request('/api/csrf');
    const result = await browser.request('/logout', 'POST', new URLSearchParams({ _csrf: form.json.token }));
    if (result.status !== 302) throw new Error('退出未确认');
  }, false);
  const independent = await check(otherTokens.access_token);
  results.push({ name: '独立浏览器会话保留', passed: independent.allowed });
  if (!independent.allowed) forbidden.push('独立浏览器会话保留');
  const employee = await admin.admin('/employees/13');
  await observe('员工停用撤销全部会话', () => admin.admin('/employees/13/state', { active: false, version: employee.version }), false, otherTokens.access_token);
  if ((await check(otherTokens.access_token)).allowed) forbidden.push('其他会话未失效');
} catch (error) {
  forbidden.push(String(error));
  throw error;
} finally {
  const currentSystem = (await admin.admin('/systems')).find((s) => s.id === system.id);
  if (!currentSystem.departments.includes(1)) await admin.admin(`/systems/${system.id}/departments`,
    { version: currentSystem.version, departments: [...currentSystem.departments, 1] });
  const employee = await admin.admin('/employees/13');
  if (!employee.active) await admin.admin('/employees/13/state', { active: true, version: employee.version });
  await assignment(original.assignment.roleId);
  const restoreAdministrator = await admin.admin('/employees/11');
  await admin.admin('/administrators', { employeeId: 11, superAdmin: originalAdministrator.superAdmin,
    departmentAdmin: originalAdministrator.departmentAdmin, version: restoreAdministrator.version });
  for (const testRole of [role, empty]) {
    const latest = (await admin.admin('/roles')).find((r) => r.id === testRole.id);
    if (latest) await admin.admin(`/roles/${testRole.id}/delete`, { version: latest.version });
  }
  fs.mkdirSync('docs/verification/performance', { recursive: true });
  fs.writeFileSync('docs/verification/performance/change-deadlines.json', JSON.stringify({
    measuredAt: new Date().toISOString(), database: 'upms_load', results, failures: forbidden,
    passed: forbidden.length === 0 }, null, 2) + '\n');
}
console.log(results.map(({ name, passed, firstCorrectMs }) => ({ name, passed, firstCorrectMs })));
if (forbidden.length) process.exitCode = 1;
