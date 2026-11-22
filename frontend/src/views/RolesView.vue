<script setup lang="ts">
import { ref, onMounted } from 'vue';
import {
  request,
  refresh,
  auth,
  allowed,
  allPages,
  type Page,
  type Role,
  type Permission,
  type System,
} from '../auth';
const roles = ref<Role[]>([]),
  page = ref(0),
  total = ref(0),
  name = ref(''),
  editing = ref<Role | null>(null),
  selected = ref<Role | null>(null),
  assigned = ref<Permission[]>([]),
  systems = ref<System[]>([]),
  systemId = ref(''),
  permissions = ref<Permission[]>([]),
  permissionId = ref(''),
  inherited = ref<Role[]>([]),
  candidates = ref<Role[]>([]),
  effective = ref<Permission[]>([]),
  hierarchyRole = ref<Role | null>(null),
  juniorId = ref(''),
  error = ref(''),
  busy = ref(false);
async function run(action: () => Promise<void>) {
  if (busy.value) return;
  busy.value = true;
  error.value = '';
  try {
    await action();
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    busy.value = false;
  }
}
async function load() {
  const data = await request<Page<Role>>(`/api/roles?page=${page.value}`);
  roles.value = data.items;
  total.value = data.total;
}
async function save() {
  await run(async () => {
    await request(
      editing.value ? `/api/roles/${editing.value.id}` : '/api/roles',
      editing.value ? 'PATCH' : 'POST',
      { name: name.value },
    );
    if (editing.value) {
      const role = { ...editing.value, name: name.value };
      if (selected.value?.id === role.id) selected.value = role;
      if (hierarchyRole.value?.id === role.id) hierarchyRole.value = role;
      if (auth.me?.roles.includes(editing.value.name)) await refresh();
    }
    editing.value = null;
    name.value = '';
    await load();
  });
}
async function remove(role: Role) {
  if (confirm(`删除角色 ${role.name}？请先处理关联。`))
    await run(async () => {
      await request(`/api/roles/${role.id}`, 'DELETE');
      if (selected.value?.id === role.id) selected.value = null;
      if (hierarchyRole.value?.id === role.id) hierarchyRole.value = null;
      await load();
    });
}
async function select(role: Role) {
  await run(async () => {
    selected.value = role;
    assigned.value = await request<Permission[]>(`/api/roles/${role.id}/permissions`);
    systems.value = await allPages<System>('/api/systems/options');
  });
}
async function loadPermissions() {
  await run(async () => {
    permissionId.value = '';
    permissions.value = systemId.value
      ? await allPages<Permission>(`/api/permissions/options?systemId=${systemId.value}`)
      : [];
  });
}
async function refreshEffective(role: Role) {
  effective.value = await request<Permission[]>(`/api/roles/${role.id}/effective-permissions`);
}

async function changePermission(id: string, method: 'POST' | 'DELETE') {
  await run(async () => {
    const role = selected.value;
    if (!role || !id) return;
    const path = `/api/roles/${role.id}/permissions`;
    if (method === 'POST') {
      await request(path, method, { permissionId: id });
      permissionId.value = '';
    } else {
      await request(`${path}/${id}`, method);
    }
    if (auth.me?.roles.includes(role.name)) await refresh();
    assigned.value = await request<Permission[]>(path);
    if (hierarchyRole.value) await refreshEffective(hierarchyRole.value);
  });
}

async function selectHierarchy(role: Role) {
  await run(async () => {
    hierarchyRole.value = role;
    inherited.value = await request<Role[]>(`/api/roles/${role.id}/inherited-roles`);
    await refreshEffective(role);
    candidates.value = (await allPages<Role>('/api/roles')).filter((item) => item.id !== role.id);
  });
}

async function changeInheritance(id: string, method: 'POST' | 'DELETE') {
  await run(async () => {
    const role = hierarchyRole.value;
    if (!role || !id) return;
    const path = `/api/roles/${role.id}/inherited-roles`;
    if (method === 'POST') {
      await request(path, method, { juniorId: id });
      juniorId.value = '';
    } else {
      await request(`${path}/${id}`, method);
    }
    if (auth.me?.roles.includes(role.name)) await refresh();
    inherited.value = await request<Role[]>(path);
    await refreshEffective(role);
  });
}
onMounted(() => run(load));
</script>
<template>
  <h2>本部门角色</h2>
  <p role="alert" class="error">{{ error }}</p>
  <table>
    <thead>
      <tr>
        <th>角色名称</th>
        <th>操作</th>
      </tr>
    </thead>
    <tbody>
      <tr v-for="role in roles" :key="role.id">
        <td>{{ role.name }}</td>
        <td>
          <button
            :disabled="busy || !allowed('ui.role.edit')"
            @click="
              editing = role;
              name = role.name;
            "
          >
            重命名</button
          ><button :disabled="busy || !allowed('ui.role.permissions')" @click="select(role)">
            配置权限</button
          ><button
            :disabled="busy || !allowed('ui.role.inheritance')"
            @click="selectHierarchy(role)"
          >
            配置继承</button
          ><button :disabled="busy || !allowed('ui.role.delete')" @click="remove(role)">
            删除
          </button>
        </td>
      </tr>
    </tbody>
  </table>
  <div class="pager">
    <button
      :disabled="busy || page === 0"
      @click="
        page--;
        run(load);
      "
    >
      上一页</button
    ><span>第 {{ page + 1 }} 页 共 {{ total }} 个</span
    ><button
      :disabled="busy || (page + 1) * 20 >= total"
      @click="
        page++;
        run(load);
      "
    >
      下一页
    </button>
  </div>
  <form @submit.prevent="save">
    <label
      >{{ editing ? '重命名角色' : '新建角色'
      }}<input v-model.trim="name" required maxlength="100" /></label
    ><button :disabled="busy || !allowed(editing ? 'ui.role.edit' : 'ui.role.create')">保存</button
    ><button
      type="button"
      @click="
        editing = null;
        name = '';
      "
    >
      取消
    </button>
  </form>
  <section v-if="hierarchyRole">
    <h3>{{ hierarchyRole.name }} 的角色继承</h3>
    <p>当前角色获得下列基础角色的全部权限，包含多级继承。角色均属于本部门。</p>
    <ul>
      <li v-for="role in inherited" :key="role.id">
        {{ role.name }}
        <button
          :disabled="busy || !allowed('ui.role.inheritance')"
          @click="changeInheritance(role.id, 'DELETE')"
        >
          解除继承
        </button>
      </li>
    </ul>
    <form @submit.prevent="changeInheritance(juniorId, 'POST')">
      <label
        >基础角色<select v-model="juniorId" required>
          <option value="">请选择基础角色</option>
          <option v-for="role in candidates" :key="role.id" :value="role.id">
            {{ role.name }}
          </option>
        </select></label
      >
      <button :disabled="busy || !allowed('ui.role.inheritance')">添加继承</button>
      <button type="button" :disabled="busy" @click="hierarchyRole = null">关闭</button>
    </form>
    <h4>有效权限 直接授权与继承的并集</h4>
    <table>
      <thead>
        <tr>
          <th>系统编号</th>
          <th>权限编码</th>
          <th>名称</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="permission in effective" :key="permission.id">
          <td>{{ permission.systemId }}</td>
          <td>{{ permission.code }}</td>
          <td>{{ permission.name }}</td>
        </tr>
      </tbody>
    </table>
  </section>
  <section v-if="selected">
    <h3>{{ selected.name }} 的权限</h3>
    <table>
      <thead>
        <tr>
          <th>系统编号</th>
          <th>权限编码</th>
          <th>名称</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="permission in assigned" :key="permission.id">
          <td>{{ permission.systemId }}</td>
          <td>{{ permission.code }}</td>
          <td>{{ permission.name }}</td>
          <td>
            <button
              :disabled="busy || !allowed('ui.role.permissions')"
              @click="changePermission(permission.id, 'DELETE')"
            >
              撤销
            </button>
          </td>
        </tr>
      </tbody>
    </table>
    <form @submit.prevent="changePermission(permissionId, 'POST')">
      <label
        >系统<select v-model="systemId" required @change="loadPermissions">
          <option value="">请选择</option>
          <option v-for="system in systems" :key="system.id" :value="system.id">
            {{ system.name }}
          </option>
        </select></label
      ><label
        >权限<select v-model="permissionId" required>
          <option value="">请选择</option>
          <option v-for="permission in permissions" :key="permission.id" :value="permission.id">
            {{ permission.code }} {{ permission.name }}
          </option>
        </select></label
      ><button :disabled="busy || !allowed('ui.role.permissions')">授予</button
      ><button type="button" :disabled="busy" @click="selected = null">关闭</button>
    </form>
  </section>
</template>
