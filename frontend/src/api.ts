import { ElMessage } from 'element-plus';

export interface Employee {
  id: number;
  username: string;
  name: string;
  phone: string;
  companyId: number;
  departmentId: number | null;
  positionName: string;
  active: boolean;
  superAdmin: boolean;
  departmentAdmin: boolean;
  version: number;
}
export interface Company {
  id: number;
  name: string;
  active: boolean;
  version: number;
}
export interface Department extends Company {
  companyId: number;
}
export interface System {
  id: number;
  key: string;
  name: string;
  active: boolean;
  redirectUri: string | null;
  logoutRedirectUri: string | null;
  directoryRevision: number;
  version: number;
  departments: number[];
}
export interface Role {
  id: number;
  systemId: number;
  departmentId: number | null;
  name: string;
  template: boolean;
  active: boolean;
  version: number;
  permissions: number[];
}
export interface Permission {
  id: number;
  systemId: number;
  key: string;
  module: string;
  feature: string;
  action: string;
  active: boolean;
  version: number;
}
export interface Assignment {
  employeeId: number;
  systemId: number;
  roleId: number | null;
  revoked: boolean;
  version: number;
  changedAt: string | null;
}
export interface Effective {
  assignment: Assignment;
  role: Role | null;
  permissions: string[];
  reason: string;
}
export interface Audit {
  id: number;
  occurredAt: string;
  actorId: number | null;
  departmentId: number | null;
  systemId: number | null;
  eventType: string;
  objectRef: string;
  result: string;
  detail: string;
}
export interface Batch {
  id: number;
  requestKey: string;
  actorId: number;
  systemId: number;
  roleId: number | null;
  status: string;
  createdAt: string;
  items: { employeeId: number; expectedVersion: number; status: string; message: string }[];
}
export interface Page<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
}

let csrf: { token: string; headerName: string } | null = null;

/** 所有写入携带框架提供的 CSRF；身份由 HttpOnly 会话管理，不保存令牌。 */
export async function api<T>(path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = {};
  if (body !== undefined) {
    if (!csrf) csrf = await fetch('/api/csrf').then((response) => response.json());
    if (csrf) headers[csrf.headerName] = csrf.token;
    headers['Content-Type'] = 'application/json';
  }
  const response = await fetch(`/api/admin${path}`, {
    method: body === undefined ? 'GET' : 'POST',
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!response.ok) {
    const result = await response
      .json()
      .catch(() => ({ detail: '请求失败，请检查登录或服务状态' }));
    const message = result.detail || `请求失败 (${response.status})`;
    ElMessage.error(message);
    throw new Error(message);
  }
  return response.json();
}

export function query(values: Record<string, string | number | null | undefined>): string {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(values))
    if (value !== null && value !== undefined && value !== '') params.set(key, String(value));
  return `?${params.toString()}`;
}

export async function logout(): Promise<void> {
  const token = await fetch('/api/csrf').then((response) => response.json());
  const form = document.createElement('form');
  form.method = 'POST';
  form.action = '/logout';
  const field = document.createElement('input');
  field.name = token.parameterName;
  field.value = token.token;
  field.type = 'hidden';
  form.append(field);
  document.body.append(form);
  form.submit();
}
