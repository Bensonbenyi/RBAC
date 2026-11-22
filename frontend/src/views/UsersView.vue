<script setup lang="ts">
import { ref, onMounted } from 'vue';
import {
  request,
  allowed,
  allPages,
  refresh,
  auth,
  type Page,
  type User,
  type Role,
} from '../auth';
const users = ref<User[]>([]),
  page = ref(0),
  total = ref(0),
  error = ref(''),
  busy = ref(false);
const selected = ref<User | null>(null),
  assigned = ref<Role[]>([]),
  roles = ref<Role[]>([]),
  roleId = ref('');
async function run(action: () => Promise<void>) {
  error.value = '';
  try {
    await action();
  } catch (e) {
    error.value = (e as Error).message;
  }
}
async function load() {
  const data = await request<Page<User>>(`/api/users?page=${page.value}&size=20`);
  users.value = data.items;
  total.value = data.total;
}
async function showRoles(user: User) {
  await run(async () => {
    selected.value = user;
    assigned.value = await request<Role[]>(`/api/users/${user.id}/roles`);
    roles.value = await allPages<Role>('/api/roles/options');
  });
}
async function changeRole(id: string, method: 'POST' | 'DELETE') {
  const user = selected.value;
  if (!user || busy.value || !id) return;
  busy.value = true;
  try {
    await run(async () => {
      const path = `/api/users/${user.id}/roles`;
      if (method === 'POST') {
        await request(path, method, { roleId: id });
        roleId.value = '';
      } else {
        await request(`${path}/${id}`, method);
      }
      if (user.id === auth.me?.subject) await refresh();
      assigned.value = await request<Role[]>(path);
    });
  } finally {
    busy.value = false;
  }
}
onMounted(() => run(load));
</script>
<template>
  <h2>本部门用户</h2>
  <p role="alert" class="error">{{ error }}</p>
  <table>
    <thead>
      <tr>
        <th>用户名</th>
        <th>姓名</th>
        <th>手机号</th>
        <th>操作</th>
      </tr>
    </thead>
    <tbody>
      <tr v-for="user in users" :key="user.id">
        <td>{{ user.username }}</td>
        <td>{{ user.name }}</td>
        <td>{{ user.phone }}</td>
        <td>
          <button :disabled="busy || !allowed('ui.user.roles')" @click="showRoles(user)">
            分配角色
          </button>
        </td>
      </tr>
    </tbody>
  </table>
  <div class="pager">
    <button
      :disabled="page === 0"
      @click="
        page--;
        run(load);
      "
    >
      上一页</button
    ><span>第 {{ page + 1 }} 页 共 {{ total }} 人</span
    ><button
      :disabled="(page + 1) * 20 >= total"
      @click="
        page++;
        run(load);
      "
    >
      下一页
    </button>
  </div>
  <section v-if="selected">
    <h3>{{ selected.name }} 的角色</h3>
    <ul>
      <li v-for="role in assigned" :key="role.id">
        {{ role.name }}
        <button
          :disabled="busy || !allowed('ui.user.roles')"
          @click="changeRole(role.id, 'DELETE')"
        >
          撤销
        </button>
      </li>
    </ul>
    <form @submit.prevent="changeRole(roleId, 'POST')">
      <label
        >角色<select v-model="roleId" required>
          <option value="">请选择</option>
          <option v-for="role in roles" :key="role.id" :value="role.id">{{ role.name }}</option>
        </select></label
      ><button :disabled="busy || !allowed('ui.user.roles')">分配</button
      ><button type="button" :disabled="busy" @click="selected = null">关闭</button>
    </form>
  </section>
</template>
