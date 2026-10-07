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

/** 显示业务结果含义；稳定英文代码留在接口与日志中，不要求演示者记忆内部枚举。 */
export function statusLabel(code: string): string {
  const names: Record<string, string> = {
    EFFECTIVE: '权限有效',
    EMPLOYEE_DISABLED: '员工已停用',
    DEPARTMENT_UNAVAILABLE: '部门未确认或停用',
    SYSTEM_UNAVAILABLE: '系统未获准或停用',
    NO_ROLE: '未分配角色',
    ROLE_UNAVAILABLE: '角色已停用或归属不符',
    ROLE_UNKNOWN: '角色不存在',
    SYSTEM_UNKNOWN: '系统不存在',
    PERMISSION_MISSING: '不具备该操作权限',
    RUNNING: '执行中',
    COMPLETED: '全部成功',
    PARTIAL_FAILURE: '部分失败',
    PENDING: '待执行',
    INTERRUPTED: '已中断',
    FAILED: '失败',
    SUCCESS: '成功',
    DENY: '拒绝',
    FAILURE: '失败',
    RECOVERED: '已恢复',
    LOGIN_SUCCEEDED: '登录成功',
    LOGIN_FAILED: '登录失败',
    LOGOUT: '统一退出',
    ROLE_ASSIGNED: '角色分配或替换',
    ROLE_REVOKED: '撤销角色',
    ROLE_CHANGED: '角色配置',
    ROLE_DELETED: '删除角色',
    DIRECTORY_ACCEPTED: '目录接收',
    DIRECTORY_FAILED: '目录失败',
    IDENTITY_REJECTED: '身份拒绝',
    AUTHORIZATION_DENIED: '鉴权拒绝',
    EMPLOYEE_CHANGED: '员工资料变更',
    EMPLOYEE_STATE: '员工启停',
    EMPLOYEE_TRANSFER: '员工调动',
    ADMIN_CHANGED: '管理员身份变更',
    COMPANY_CHANGED: '公司变更',
    DEPARTMENT_CHANGED: '部门变更',
    SYSTEM_CHANGED: '系统变更',
    SYSTEM_SCOPE: '可用范围变更',
    SYSTEM_CREDENTIALS_ROTATED: '重新签发系统凭据',
    AUDIT_EXPORTED: '审计导出',
    AUDIT_HEALTH: '审计存储探测',
    AUDIT_RECOVERED: '审计故障恢复',
    BATCH_CREATED: '建立批量任务',
    BATCH_RETRY: '重试批量任务',
    DEMO_CONFIGURED: '演示配置',
    LOAD_CONFIGURED: '压测配置',
    BASELINE_IMPORTED: '基础资料导入',
  };
  return names[code] ?? code;
}

/** 查询菜单覆盖全部关键审计事件，筛选和详情共用中文名称。 */
export const auditEventTypes = [
  'LOGIN_SUCCEEDED',
  'LOGIN_FAILED',
  'LOGOUT',
  'COMPANY_CHANGED',
  'DEPARTMENT_CHANGED',
  'EMPLOYEE_CHANGED',
  'EMPLOYEE_STATE',
  'EMPLOYEE_TRANSFER',
  'ADMIN_CHANGED',
  'SYSTEM_CHANGED',
  'SYSTEM_SCOPE',
  'SYSTEM_CREDENTIALS_ROTATED',
  'DIRECTORY_ACCEPTED',
  'DIRECTORY_FAILED',
  'ROLE_CHANGED',
  'ROLE_DELETED',
  'ROLE_ASSIGNED',
  'ROLE_REVOKED',
  'BATCH_CREATED',
  'BATCH_RETRY',
  'AUTHORIZATION_DENIED',
  'IDENTITY_REJECTED',
  'AUDIT_EXPORTED',
  'AUDIT_HEALTH',
  'AUDIT_RECOVERED',
  'BASELINE_IMPORTED',
  'DEMO_CONFIGURED',
  'LOAD_CONFIGURED',
] as const;

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
