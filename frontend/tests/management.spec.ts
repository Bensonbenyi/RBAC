import { test, expect, type Page } from '@playwright/test';
import { platformUrl, oaUrl, knowledgeUrl, proofPrefix } from './target';

const createdRoles = new Set<number>();

async function highest(page: Page) {
  await page.goto(platformUrl + '/admin');
  await page.getByLabel('用户名', { exact: true }).fill('SG000001');
  await page.getByLabel('手机号', { exact: true }).fill('13976537502');
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page.getByRole('heading', { name: '员工与授权' })).toBeVisible();
}

async function adminApi(page: Page, path: string, body?: unknown) {
  return page.evaluate(
    async ({ path, body }) => {
      const headers: Record<string, string> = { Accept: 'application/json' };
      if (body !== undefined) {
        const csrf = await fetch('/api/csrf').then((r) => r.json());
        headers[csrf.headerName] = csrf.token;
        headers['Content-Type'] = 'application/json';
      }
      const response = await fetch('/api/admin' + path, {
        method: body === undefined ? 'GET' : 'POST',
        headers,
        body: body === undefined ? undefined : JSON.stringify(body),
      });
      const result = await response.json();
      if (!response.ok) throw new Error(result.detail);
      return result;
    },
    { path, body },
  );
}

async function cleanupRole(page: Page, roleId: number, systemId: number) {
  const effective = await adminApi(page, '/employees/20/permissions');
  const record = effective.find(
    (f: { assignment: { systemId: number } }) => f.assignment.systemId === systemId,
  );
  if (record.assignment.roleId === roleId)
    await adminApi(page, '/assignments', {
      employeeId: 20,
      systemId,
      roleId: null,
      expectedVersion: record.assignment.version,
    });
  const latest = (await adminApi(page, '/roles')).find((r: { id: number }) => r.id === roleId);
  if (latest) await adminApi(page, `/roles/${roleId}/delete`, { version: latest.version });
  createdRoles.delete(roleId);
}

// 超时时原页面可能已关闭，独立清理钩子仍只处理本测试创建的角色和关联。
test.afterAll(async ({ browser }) => {
  if (!createdRoles.size) return;
  const context = await browser.newContext();
  try {
    const page = await context.newPage();
    await highest(page);
    const system = (await adminApi(page, '/systems')).find((s: { key: string }) => s.key === 'oa');
    for (const id of [...createdRoles]) await cleanupRole(page, id, system.id);
  } finally {
    await context.close();
  }
});

test('ACC-F-009、010 界面选择权限、预览替换、撤权并导出日志', async ({ page }) => {
  await highest(page);
  const systems = await adminApi(page, '/systems');
  const system = systems.find((s: { key: string }) => s.key === 'oa');
  const template = (await adminApi(page, `/roles?systemId=${system.id}`)).find(
    (r: { template: boolean; name: string }) => r.template && r.name === '普通员工',
  );
  const role = await adminApi(page, '/roles/copy', {
    templateId: template.id,
    departmentId: 1,
    name: `浏览器验收-${Date.now()}`,
  });
  createdRoles.add(role.id);
  try {
    await page.getByRole('menuitem', { name: '模板与部门角色', exact: true }).click();
    const roleRow = page.getByRole('row').filter({ hasText: role.name });
    await roleRow.getByRole('button', { name: '配置', exact: true }).click();
    const roleDialog = page.getByRole('dialog', { name: '角色职责与权限配置' });
    await roleDialog.locator('label.el-checkbox').first().click();
    await roleDialog.getByRole('button', { name: '预览影响并保存', exact: true }).click();
    const roleConfirmation = page.getByRole('dialog', { name: '角色影响确认' });
    await expect(roleConfirmation).toContainText('影响 0 名已分配员工');
    await roleConfirmation.getByRole('button', { name: '确定', exact: true }).click();
    await expect(roleDialog).not.toBeVisible();
    const changedRole = (await adminApi(page, '/roles')).find(
      (r: { id: number }) => r.id === role.id,
    );
    expect(changedRole.permissions.length).not.toBe(role.permissions.length);
    await page.getByRole('menuitem', { name: '员工与授权', exact: true }).click();
    const original = await adminApi(page, '/employees/20/permissions');
    const before = original.find(
      (f: { assignment: { systemId: number } }) => f.assignment.systemId === system.id,
    );
    if (!before.assignment.revoked) throw new Error('测试员工已有授权，禁止覆盖未知状态');
    await page.getByPlaceholder('姓名、用户名或手机号').fill('SG000020');
    await page.getByRole('button', { name: '查询', exact: true }).click();
    const row = page.getByRole('row').filter({ hasText: 'SG000020' });
    await expect(row).toBeVisible();
    await row.getByRole('button', { name: '授权', exact: true }).click();
    const dialog = page.getByRole('dialog', { name: '为何涛分配角色' });
    await dialog.getByRole('combobox').nth(0).press('Enter');
    await page.getByRole('option', { name: system.name, exact: true }).click();
    await dialog.getByRole('combobox').nth(1).press('Enter');
    await page.getByRole('option').filter({ hasText: role.name }).click();
    await dialog.getByRole('button', { name: '预览并确认提交' }).click();
    const confirmation = page.getByRole('dialog', { name: '单角色影响确认' });
    await expect(confirmation).toContainText('新角色将替换旧角色，不叠加');
    await confirmation.getByRole('button', { name: '确定', exact: true }).click();
    await expect(
      page.getByText('变更已提交，最迟 3 秒影响后续请求', { exact: true }),
    ).toBeVisible();
    await row.getByRole('button', { name: '有效权限', exact: true }).click();
    const detail = page.getByRole('dialog', { name: '何涛的有效角色与权限' });
    await expect(detail).toContainText(role.name);
    await expect(detail).toContainText('权限有效');
    await detail.getByRole('button', { name: '关闭此对话框' }).click();
    await row.getByRole('button', { name: '授权', exact: true }).click();
    await dialog.getByRole('combobox').nth(0).press('Enter');
    await page.getByRole('option', { name: system.name, exact: true }).click();
    await dialog.getByRole('button', { name: '预览并确认提交' }).click();
    await expect(confirmation).toContainText('撤销当前角色');
    await confirmation.getByRole('button', { name: '确定', exact: true }).click();
    await expect(dialog).not.toBeVisible();
    await page.getByRole('menuitem', { name: '审计查询与导出', exact: true }).click();
    const download = page.waitForEvent('download');
    await page.getByRole('button', { name: '导出筛选日志', exact: true }).click();
    expect((await download).suggestedFilename()).toBe('upms-audit.csv');
    await page.screenshot({
      path: `../output/playwright/${proofPrefix}admin-management-verified.png`,
      fullPage: true,
    });
  } finally {
    await cleanupRole(page, role.id, system.id);
  }
});

test('ACC-F-012 两个系统权限不同，本人记录校验留在业务系统', async ({ page }) => {
  await page.goto(oaUrl);
  await page.getByRole('link', { name: '集团账号统一登录' }).click();
  await page.getByLabel('用户名', { exact: true }).fill('SG000013');
  await page.getByLabel('手机号', { exact: true }).fill('13554810349');
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await page.getByPlaceholder('演示记录标题').fill('浏览器验收记录');
  await page.getByRole('button', { name: '新增业务记录' }).click();
  await expect(page.getByText('已允许并保存业务记录', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: '校验本人记录' }).click();
  await expect(page.getByText('业务系统拒绝：记录不属于本人', { exact: true })).toBeVisible();
  await page.locator('input[name="id"]').fill('1');
  await page.getByRole('button', { name: '校验本人记录' }).click();
  await expect(page.getByText('本人范围校验通过', { exact: true })).toBeVisible();
  await page.goto(knowledgeUrl);
  await page.getByRole('link', { name: '集团账号统一登录' }).click();
  await page.getByPlaceholder('演示记录标题').fill('知识库应拒绝的写入');
  await page.getByRole('button', { name: '新增业务记录' }).click();
  await expect(page.getByText('权限拒绝：PERMISSION_MISSING', { exact: true })).toBeVisible();
});

test('ACC-F-014 无授权拒绝、三秒撤权与停用结束现有会话', async ({ browser }) => {
  const adminContext = await browser.newContext();
  const employeeContext = await browser.newContext();
  const admin = await adminContext.newPage();
  let roleId: number | undefined;
  let systemId: number | undefined;
  try {
    await highest(admin);
    const employee = await adminApi(admin, '/employees/20');
    if (!employee.active) throw new Error('测试员工已停用，禁止覆盖未知状态');
    const system = (await adminApi(admin, '/systems')).find((s: { key: string }) => s.key === 'oa');
    systemId = system.id;
    const initial = (await adminApi(admin, '/employees/20/permissions')).find(
      (f: { assignment: { systemId: number } }) => f.assignment.systemId === system.id,
    );
    if (!initial.assignment.revoked) throw new Error('测试员工已有授权，禁止覆盖未知状态');
    const template = (await adminApi(admin, `/roles?systemId=${system.id}`)).find(
      (r: { template: boolean; name: string }) => r.template && r.name === '普通员工',
    );
    const role = await adminApi(admin, '/roles/copy', {
      templateId: template.id,
      departmentId: 1,
      name: `生命周期验收-${Date.now()}`,
    });
    roleId = role.id;
    createdRoles.add(role.id);
    const page = await employeeContext.newPage();
    await page.goto(oaUrl);
    await page.getByRole('link', { name: '集团账号统一登录' }).click();
    await page.getByLabel('用户名', { exact: true }).fill('SG000020');
    await page.getByLabel('手机号', { exact: true }).fill('18921085097');
    await page.getByRole('button', { name: '登录', exact: true }).click();
    const read = page.getByRole('button', { name: '查看业务记录', exact: true });
    await read.click();
    await expect(page.getByText(/^权限拒绝：/)).toBeVisible();
    const granted = await adminApi(admin, '/assignments', {
      employeeId: 20,
      systemId,
      roleId,
      expectedVersion: initial.assignment.version,
    });
    await read.click();
    await expect(page.getByText('已允许查看业务记录', { exact: true })).toBeVisible();
    const revoked = await adminApi(admin, '/assignments', {
      employeeId: 20,
      systemId,
      roleId: null,
      expectedVersion: granted.version,
    });
    const started = Date.now();
    await read.click();
    await expect(page.getByText(/^权限拒绝：/)).toBeVisible();
    expect(Date.now() - started).toBeLessThanOrEqual(3000);
    await adminApi(admin, '/assignments', {
      employeeId: 20,
      systemId,
      roleId,
      expectedVersion: revoked.version,
    });
    await read.click();
    await expect(page.getByText('已允许查看业务记录', { exact: true })).toBeVisible();
    const current = await adminApi(admin, '/employees/20');
    await adminApi(admin, '/employees/20/state', { active: false, version: current.version });
    await read.click();
    await expect(page.getByText(/操作拒绝/)).toBeVisible();
    const disabled = await adminApi(admin, '/employees/20');
    await adminApi(admin, '/employees/20/state', { active: true, version: disabled.version });
    // 重新启用不能恢复已结束的统一会话，也不能恢复旧角色。
    await read.click();
    await expect(page.getByText(/操作拒绝/)).toBeVisible();
    await page.screenshot({
      path: `../output/playwright/${proofPrefix}employee-disabled.png`,
      fullPage: true,
    });
  } finally {
    if (roleId !== undefined && systemId !== undefined) {
      const employee = await adminApi(admin, '/employees/20');
      if (!employee.active)
        await adminApi(admin, '/employees/20/state', { active: true, version: employee.version });
      await cleanupRole(admin, roleId, systemId);
    }
    await adminContext.close();
    await employeeContext.close();
  }
});
