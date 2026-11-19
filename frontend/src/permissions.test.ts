// @vitest-environment jsdom
import { beforeEach, expect, test, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { auth } from './auth';
import CourseView from './views/CourseView.vue';
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

test('API codes alone never open course menus or send business requests', async () => {
  auth.me!.permissions = ['CV', 'TC', 'CR'];
  const view = mount(CourseView);
  await flushPromises();
  expect(view.text()).not.toContain('学期维护');
  expect(fetch).not.toHaveBeenCalled();
  view.unmount();
});

test('one menu code shows both menus with native disabled and keeps review buttons visible', async () => {
  auth.me!.permissions = ['MCO'];
  const view = mount(CourseView);
  await flushPromises();
  const term = view.findAll('button').find((b) => b.text() === '学期维护')!;
  expect((term.element as HTMLButtonElement).disabled).toBe(true);
  for (const label of ['通过', '驳回']) {
    const button = view.findAll('button').find((b) => b.text() === label)!;
    expect((button.element as HTMLButtonElement).disabled).toBe(true);
    await button.trigger('click');
  }
  expect(vi.mocked(fetch).mock.calls.every((call) => !call[0].toString().includes('/review'))).toBe(
    true,
  );
  auth.me!.permissions = ['MCO', 'MCOR'];
  await flushPromises();
  expect(
    (view.findAll('button').find((b) => b.text() === '通过')!.element as HTMLButtonElement)
      .disabled,
  ).toBe(false);
  auth.me!.permissions = [];
  await flushPromises();
  expect(view.find('table').exists()).toBe(false);
  view.unmount();
});

test('user buttons and submit use independent UI permissions', async () => {
  auth.me!.permissions = ['user.create'];
  const view = mount(UsersView);
  await flushPromises();
  const save = view.findAll('button').find((b) => b.text() === '保存')!;
  expect((save.element as HTMLButtonElement).disabled).toBe(true);
  auth.me!.permissions = ['ui.user.create'];
  await flushPromises();
  expect((save.element as HTMLButtonElement).disabled).toBe(false);
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
      if (url.endsWith('/inherited-roles/20')) {
        linked = init?.method === 'PUT';
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
        ([url, init]) => url === '/api/roles/10/inherited-roles/20' && init?.method === 'PUT',
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
