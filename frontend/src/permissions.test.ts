// @vitest-environment jsdom
import { beforeEach, expect, test, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { auth } from './auth';
import CourseView from './views/CourseView.vue';
import UsersView from './views/UsersView.vue';

beforeEach(() => {
  auth.token = 'test';
  auth.me = {
    subject: '1',
    username: 'test',
    name: '测试',
    departmentId: '1',
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
