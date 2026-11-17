import { allowed } from './auth';
export const platformMenus = [
  { path: '/users', label: '用户', code: 'ui.users' },
  { path: '/roles', label: '角色', code: 'ui.roles' },
  { path: '/departments', label: '部门', code: 'ui.departments' },
  { path: '/systems', label: '业务系统', code: 'ui.systems' },
  { path: '/permissions', label: '权限目录', code: 'ui.permissions' },
];
export function canEnter(path: string) {
  const menu = platformMenus.find((item) => item.path === path);
  return !menu || allowed(menu.code);
}
