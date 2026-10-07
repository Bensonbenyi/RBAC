import { test, expect, type Page } from '@playwright/test';

async function login(page: Page, username: string, phone: string) {
  await page.getByLabel('用户名', { exact: true }).fill(username);
  await page.getByLabel('手机号', { exact: true }).fill(phone);
  await page.getByRole('button', { name: '登录', exact: true }).click();
}

test('ACC-F-003、005 标准跳转登录、跨系统复用与统一退出', async ({ page, context }) => {
  await page.goto('http://localhost:18081');
  await page.getByRole('link', { name: '集团账号统一登录' }).click();
  await expect(page).toHaveURL(/localhost:18080\/login/);
  await login(page, 'SG000013', '13554810349');
  await expect(page).toHaveURL('http://localhost:18081/');
  await page.getByRole('button', { name: '查看业务记录', exact: true }).click();
  await expect(page.getByText('已允许查看业务记录', { exact: true })).toBeVisible();
  const other = await context.newPage();
  await other.goto('http://localhost:18082');
  await other.getByRole('link', { name: '集团账号统一登录' }).click();
  await expect(other).toHaveURL('http://localhost:18082/');
  await other.getByRole('button', { name: '查看业务记录', exact: true }).click();
  await expect(other.getByText('已允许查看业务记录', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: '统一退出当前会话', exact: true }).click();
  await expect(page).toHaveURL('http://localhost:18081/');
  await expect(page.getByRole('link', { name: '集团账号统一登录' })).toBeVisible();
  await other.getByRole('button', { name: '查看业务记录', exact: true }).click();
  await expect(other.getByText(/操作拒绝/)).toBeVisible();
});

test('ACC-F-002 普通员工不能使用后台但可以统一确认身份', async ({ page }) => {
  await page.goto('http://localhost:18080/admin');
  await login(page, 'SG000020', '18921085097');
  await expect(page.getByRole('heading', { name: '无法进入管理后台' })).toBeVisible();
});

test('后台中文功能导航及管理员身份', async ({ page }) => {
  await page.goto('http://localhost:18080/admin');
  await login(page, 'SG000001', '13976537502');
  await expect(page.getByRole('heading', { name: '员工与授权' })).toBeVisible();
  for (const title of [
    '组织管理',
    '系统接入与目录',
    '模板与部门角色',
    '批量任务结果',
    '审计查询与导出',
  ]) {
    await page.getByRole('menuitem', { name: title, exact: true }).click();
    await expect(page.getByRole('heading', { name: title })).toBeVisible();
  }
  await page.screenshot({ path: '../output/playwright/admin-audit.png', fullPage: true });
});
