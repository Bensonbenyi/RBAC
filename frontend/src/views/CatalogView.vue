<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { request, allPages, type Page, type Named, type System } from '../auth';
const props = defineProps<{ kind: 'systems' | 'permissions' }>();
interface Entry extends Named {
  systemKey?: string;
  code?: string;
}
const titles = { systems: '业务系统', permissions: '权限目录' };
const rows = ref<Entry[]>([]),
  page = ref(0),
  total = ref(0),
  systems = ref<System[]>([]),
  systemId = ref(''),
  error = ref(''),
  busy = ref(false);
function endpoint() {
  return props.kind === 'permissions'
    ? `/api/systems/${systemId.value}/permissions`
    : `/api/${props.kind}`;
}
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
  if (props.kind === 'permissions' && !systemId.value) {
    rows.value = [];
    return;
  }
  const data = await request<Page<Entry>>(`${endpoint()}?page=${page.value}`);
  rows.value = data.items;
  total.value = data.total;
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
      :disabled="busy"
      @change="
        page = 0;
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
        <th>标识或编码</th>
      </tr>
    </thead>
    <tbody>
      <tr v-for="row in rows" :key="row.id">
        <td>{{ row.id }}</td>
        <td>{{ row.name }}</td>
        <td>{{ row.systemKey || row.code }}</td>
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
    ><span>第 {{ page + 1 }} 页 共 {{ total }} 条</span
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
</template>
