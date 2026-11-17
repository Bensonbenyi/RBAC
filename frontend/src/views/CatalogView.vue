<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { request, allowed, allPages, type Page, type Named, type System } from '../auth';
const props = defineProps<{ kind: 'departments' | 'systems' | 'permissions' }>();
interface Entry extends Named {
  parentId?: string;
  systemKey?: string;
  code?: string;
}
const titles = { departments: '部门', systems: '业务系统', permissions: '权限目录' };
const rows = ref<Entry[]>([]),
  page = ref(0),
  total = ref(0),
  systems = ref<System[]>([]),
  systemId = ref(''),
  name = ref(''),
  code = ref(''),
  parentId = ref(''),
  editing = ref<Entry | null>(null),
  error = ref('');
function can(action: string) {
  return allowed(`ui.${props.kind.slice(0, -1)}.${action}`);
}
function endpoint() {
  return props.kind === 'permissions'
    ? `/api/systems/${systemId.value}/permissions`
    : `/api/${props.kind}`;
}
async function run(action: () => Promise<void>) {
  error.value = '';
  try {
    await action();
  } catch (e) {
    error.value = (e as Error).message;
  }
}
async function load() {
  if (props.kind === 'permissions' && !systemId.value) {
    rows.value = [];
    return;
  }
  const data = await request<Page<Entry>>(`${endpoint()}?page=${page.value}`);
  rows.value = data.items;
  total.value = data.total;
}
function reset() {
  editing.value = null;
  name.value = '';
  code.value = '';
  parentId.value = '';
}
async function save() {
  await run(async () => {
    const body = editing.value
      ? { name: name.value }
      : props.kind === 'departments'
        ? { name: name.value, parentId: parentId.value || null }
        : props.kind === 'systems'
          ? { name: name.value, systemKey: code.value }
          : { name: name.value, code: code.value };
    await request(
      editing.value ? `/api/${props.kind}/${editing.value.id}` : endpoint(),
      editing.value ? 'PATCH' : 'POST',
      body,
    );
    reset();
    await load();
  });
}
async function remove(row: Entry) {
  if (confirm(`删除 ${row.name}？存在关联时需先处理关联。`))
    await run(async () => {
      await request(`/api/${props.kind}/${row.id}`, 'DELETE');
      await load();
    });
}
onMounted(() =>
  run(async () => {
    if (props.kind === 'permissions') {
      systems.value = await allPages<System>('/api/systems/options');
      systemId.value = systems.value[0]?.id || '';
    }
    await load();
  }),
);
</script>
<template>
  <h2>{{ titles[kind] }}</h2>
  <p role="alert" class="error">{{ error }}</p>
  <label v-if="kind === 'permissions'"
    >所属系统<select
      v-model="systemId"
      @change="
        page = 0;
        reset();
        run(load);
      "
    >
      <option v-for="system in systems" :key="system.id" :value="system.id">
        {{ system.name }}
      </option>
    </select></label
  >
  <table>
    <thead>
      <tr>
        <th>编号</th>
        <th>名称</th>
        <th>{{ kind === 'departments' ? '父部门编号' : '标识或编码' }}</th>
        <th>操作</th>
      </tr>
    </thead>
    <tbody>
      <tr v-for="row in rows" :key="row.id">
        <td>{{ row.id }}</td>
        <td>{{ row.name }}</td>
        <td>{{ row.parentId || row.systemKey || row.code || '根部门' }}</td>
        <td>
          <button
            :disabled="!can('edit')"
            @click="
              editing = row;
              name = row.name;
            "
          >
            重命名</button
          ><button :disabled="!can('delete')" @click="remove(row)">删除</button>
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
    ><span>第 {{ page + 1 }} 页 共 {{ total }} 条</span
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
  <h3>{{ editing ? '重命名' : '新建' }}</h3>
  <form @submit.prevent="save">
    <label>名称<input v-model.trim="name" required maxlength="100" /></label
    ><label v-if="!editing && kind === 'departments'"
      >父部门编号<input
        v-model="parentId"
        inputmode="numeric"
        pattern="[1-9][0-9]*"
        placeholder="留空创建根部门" /></label
    ><label v-if="!editing && kind !== 'departments'"
      >{{ kind === 'systems' ? '系统标识' : '权限编码'
      }}<input v-model.trim="code" required :maxlength="kind === 'systems' ? 64 : 100" /></label
    ><button :disabled="!can(editing ? 'edit' : 'create') || (kind === 'permissions' && !systemId)">
      保存</button
    ><button type="button" @click="reset">取消</button>
  </form>
</template>
