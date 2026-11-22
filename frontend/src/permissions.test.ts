// @vitest-environment jsdom
import { beforeEach, expect, test, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { auth } from './auth';
import DepartmentView from './views/DepartmentView.vue';
import UsersView from './views/UsersView.vue';
import RolesView from './views/RolesView.vue';

beforeEach(() => {
  auth.token = 'test';
  auth.me = {
    subject: '1',
    no: 'test',
    name: '测试',
    departmentId: 1,
    roles: [],
    permissions: [],
  };
  vi.stubGlobal(
    'fetch',
    vi.fn(
      async (url: string) =>
        new Response(
          JSON.stringify(
            url.includes('admin/courses')
              ? { items: [{ id: 1, name: '课程', code: 'C1', courseStatus: 2 }], total: 1 }
              : { items: [], total: 0 },
          ),
          { status: 200 },
        ),
    ),
  );
});

test('role assignment requires the independent UI code', async () => {
  auth.me!.permissions = ['user.role.assign'];
  vi.stubGlobal(
    'fetch',
    vi.fn(
      async () =>
        new Response(
          JSON.stringify({
            items: [{ id: '1', name: '测试用户', username: 'test', departmentId: '1' }],
            page: 0,
            size: 20,
            total: 1,
          }),
        ),
    ),
  );
  const view = mount(UsersView);
  await flushPromises();
  const button = view.findAll('button').find((b) => b.text() === '分配角色')!;
  expect((button.element as HTMLButtonElement).disabled).toBe(true);
  auth.me!.permissions = ['ui.user.roles'];
  await flushPromises();
  expect((button.element as HTMLButtonElement).disabled).toBe(false);
  view.unmount();
});

test('department tree shows child departments under their parent', async () => {
  vi.stubGlobal(
    'fetch',
    vi.fn(
      async () =>
        new Response(
          JSON.stringify({
            items: [
              { id: '1', name: '教学部', parentId: null },
              { id: '2', name: '教务部', parentId: '1' },
            ],
            page: 0,
            size: 100,
            total: 2,
          }),
        ),
    ),
  );
  const view = mount(DepartmentView);
  await flushPromises();
  expect(view.find('details summary').text()).toContain('教学部');
  expect(view.find('details ul li').text()).toContain('教务部');
  expect(view.find('form').exists()).toBe(false);
  view.unmount();
});

test('inheritance controls require their own UI code and refresh effective permissions after a write', async () => {
  const roles = [
    { id: '10', name: '高级角色', departmentId: '1' },
    { id: '20', name: '基础角色', departmentId: '1' },
  ];
  let linked = false;
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, init?: RequestInit) => {
      if (url.endsWith('/inherited-roles') && init?.method === 'POST') {
        linked = true;
        return new Response(null, { status: 201 });
      }
      if (url.endsWith('/inherited-roles/20') && init?.method === 'DELETE') {
        linked = false;
        return new Response(null, { status: 204 });
      }
      const data =
        url === '/auth/me'
          ? auth.me
          : url.endsWith('/inherited-roles')
            ? linked
              ? [roles[1]]
              : []
            : url.endsWith('/effective-permissions')
              ? linked
                ? [{ id: '30', systemId: '2', code: 'CR', name: '审核课程' }]
                : []
              : { items: roles, total: roles.length, page: 0, size: 20 };
      return new Response(JSON.stringify(data), { status: 200 });
    }),
  );
  auth.me!.permissions = ['role.manage'];
  const view = mount(RolesView);
  await flushPromises();
  const configure = view.findAll('button').find((b) => b.text() === '配置继承')!;
  expect((configure.element as HTMLButtonElement).disabled).toBe(true);
  await configure.trigger('click');
  expect(view.find('section').exists()).toBe(false);
  auth.me!.permissions = ['ui.role.inheritance'];
  await flushPromises();
  await configure.trigger('click');
  await flushPromises();
  expect(
    view
      .find('select')
      .findAll('option')
      .map((o) => o.text()),
  ).toEqual(['请选择基础角色', '基础角色']);
  await view.find('select').setValue('20');
  await view.find('section form').trigger('submit');
  await flushPromises();
  expect(view.text()).toContain('审核课程');
  expect(
    vi
      .mocked(fetch)
      .mock.calls.some(
        ([url, init]) =>
          url === '/api/roles/10/inherited-roles' &&
          init?.method === 'POST' &&
          init?.body === JSON.stringify({ juniorId: '20' }),
      ),
  ).toBe(true);
  await view
    .findAll('button')
    .find((b) => b.text() === '解除继承')!
    .trigger('click');
  await flushPromises();
  expect(view.text()).not.toContain('审核课程');
  view.unmount();
});
