import { reactive } from 'vue';
export const isCourse = import.meta.env.BASE_URL.includes('course');
const key = isCourse ? 'course-token' : 'rbac-token';
export interface Me {
  subject: string;
  username: string;
  name: string;
  departmentId: string;
  roles: string[];
  permissions: string[];
}
export const auth = reactive({ token: sessionStorage.getItem(key) || '', me: null as Me | null });
export function saveToken(token: string) {
  auth.token = token;
  sessionStorage.setItem(key, token);
}
export function clearToken() {
  saveToken('');
  auth.me = null;
}
export async function request<T>(path: string, method = 'GET', body?: unknown): Promise<T> {
  const response = await fetch(path, {
    method,
    headers: {
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...(auth.token ? { Authorization: `Bearer ${auth.token}` } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!response.ok) {
    if (response.status === 401) clearToken();
    if (response.status === 403 && path !== '/auth/me' && path !== '/course-api/me')
      await refresh().catch(() => {
        auth.me = null;
      });
    const problem = await response.json().catch(() => ({ message: '服务暂不可用' }));
    throw new Error(`${response.status} ${problem.message || '请求失败'}`);
  }
  const text = await response.text();
  return text ? (JSON.parse(text) as T) : (undefined as T);
}
export async function refresh() {
  const identity = await request<Me>('/auth/me');
  if (isCourse) {
    const local = await request<{ permissions: string[] }>('/course-api/me');
    identity.permissions = local.permissions;
  }
  auth.me = identity;
}
export function allowed(code: string) {
  return auth.me?.permissions.includes(code) || false;
}
export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
}
export interface Named {
  id: string;
  name: string;
}
export interface User extends Named {
  username: string;
  phone: string;
  departmentId: string;
}
export interface Role extends Named {
  departmentId: string;
}
export interface System extends Named {
  systemKey: string;
}
export interface Permission extends Named {
  systemId: string;
  code: string;
}

/** 遍历有界分页，授权下拉框展示完整目录。 */
export async function allPages<T>(path: string): Promise<T[]> {
  const items: T[] = [];
  for (let page = 0; ; page++) {
    const data = await request<Page<T>>(
      `${path}${path.includes('?') ? '&' : '?'}page=${page}&size=100`,
    );
    items.push(...data.items);
    if (items.length >= data.total || data.items.length === 0) return items;
  }
}
