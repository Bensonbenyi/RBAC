<script setup lang="ts">
import { ref, reactive } from 'vue';
import { ElMessageBox } from 'element-plus';
import { api, type Employee, type Department, type System, type Permission } from '../api';
defineProps<{ me: Employee; systems: System[]; departments: Department[] }>();
const emit = defineEmits<{ changed: [] }>();
const dialog = ref(false);
const binding = ref(false);
const directory = ref(false);
const permissions = ref<Permission[]>([]);
const chosen = ref<System>();
const scope = ref<number[]>([]);
const credentials = ref<{
  clientId: string;
  clientSecret: string;
  directoryClientId: string;
  directorySecret: string;
}>();
const showCredentials = ref(false);
const form = reactive({
  id: null as number | null,
  key: '',
  name: '',
  active: true,
  redirectUri: '',
  logoutRedirectUri: '',
  version: 0,
});
function edit(system?: System) {
  Object.assign(
    form,
    system
      ? {
          ...system,
          redirectUri: system.redirectUri ?? '',
          logoutRedirectUri: system.logoutRedirectUri ?? '',
        }
      : {
          id: null,
          key: '',
          name: '',
          active: true,
          redirectUri: '',
          logoutRedirectUri: '',
          version: 0,
        },
  );
  dialog.value = true;
}
async function save() {
  if (!form.active)
    await ElMessageBox.confirm('停用系统使所有部门后续受控操作被拒绝。确认停用？', '系统影响确认');
  await api('/systems', form);
  dialog.value = false;
  emit('changed');
}
function bind(system: System) {
  chosen.value = system;
  scope.value = [...system.departments];
  binding.value = true;
}
async function saveScope() {
  if (!chosen.value) return;
  const removed = chosen.value.departments.filter((d) => !scope.value.includes(d));
  if (removed.length)
    await ElMessageBox.confirm(
      `取消 ${removed.length} 个部门可用范围会使对应授权失效，最迟 3 秒影响后续请求。确认？`,
      '范围影响确认',
    );
  await api(`/systems/${chosen.value.id}/departments`, {
    departments: scope.value,
    version: chosen.value.version,
  });
  binding.value = false;
  emit('changed');
}
async function show(system: System) {
  chosen.value = system;
  permissions.value = await api<Permission[]>(`/systems/${system.id}/permissions`);
  directory.value = true;
}
async function rotate(system: System) {
  await ElMessageBox.confirm(
    '旧登录和目录凭据将失效。新凭据只显示一次，请交给该系统维护人员。确认签发？',
    '接入凭据确认',
  );
  credentials.value = await api(`/systems/${system.id}/credentials`, { version: system.version });
  showCredentials.value = true;
  emit('changed');
}
</script>
<template>
  <section class="panel">
    <div class="toolbar">
      <el-button v-if="me.superAdmin" type="primary" @click="edit()">登记外部系统</el-button>
    </div>
    <p class="hint">
      系统与部门多对多绑定。目录由系统独立凭据上报，失败可修正后用递增版本重报，平台不会生成外部业务账号。
    </p>
    <el-table :data="systems"
      ><el-table-column prop="key" label="系统标识" width="130" /><el-table-column
        prop="name"
        label="系统名称"
        min-width="220"
      /><el-table-column label="状态" width="90"
        ><template #default="{ row }"
          ><el-tag :type="row.active ? 'success' : 'info'">{{
            row.active ? '有效' : '停用'
          }}</el-tag></template
        ></el-table-column
      ><el-table-column prop="directoryRevision" label="目录版本" width="100" /><el-table-column
        label="可用部门"
        width="110"
        ><template #default="{ row }"
          >{{ row.departments.length }} 个部门</template
        ></el-table-column
      ><el-table-column label="操作" width="310"
        ><template #default="{ row }"
          ><el-button link type="primary" @click="show(row)">权限目录</el-button
          ><el-button v-if="me.superAdmin" link type="primary" @click="bind(row)"
            >可用部门</el-button
          ><el-button v-if="me.superAdmin" link type="primary" @click="edit(row)">维护</el-button
          ><el-button v-if="me.superAdmin" link type="primary" @click="rotate(row)"
            >签发凭据</el-button
          ></template
        ></el-table-column
      ></el-table
    >
  </section>
  <el-dialog v-model="dialog" title="系统登记与回调维护" width="600px"
    ><el-form label-width="110px"
      ><el-form-item label="系统标识"
        ><el-input
          v-model="form.key"
          :disabled="!!form.id"
          placeholder="小写字母、数字与连字符" /></el-form-item
      ><el-form-item label="系统名称"><el-input v-model="form.name" /></el-form-item
      ><el-form-item label="登录回调"
        ><el-input
          v-model="form.redirectUri"
          placeholder="HTTPS；本机演示可使用 HTTP" /></el-form-item
      ><el-form-item label="退出回调"><el-input v-model="form.logoutRedirectUri" /></el-form-item
      ><el-form-item label="有效状态"><el-switch v-model="form.active" /></el-form-item></el-form
    ><template #footer
      ><el-button @click="dialog = false">取消</el-button
      ><el-button type="primary" @click="save">保存</el-button></template
    ></el-dialog
  ><el-dialog v-model="binding" :title="`${chosen?.name}的可用部门`" width="600px"
    ><el-select
      v-model="scope"
      multiple
      filterable
      placeholder="未选择表示无部门可用"
      style="width: 100%"
      ><el-option
        v-for="department in departments"
        :key="department.id"
        :label="`${department.companyId}/${department.name}`"
        :value="department.id" /></el-select
    ><template #footer
      ><el-button @click="binding = false">取消</el-button
      ><el-button type="primary" @click="saveScope">确认范围</el-button></template
    ></el-dialog
  ><el-dialog
    v-model="directory"
    :title="`${chosen?.name}权限目录 (版本 ${chosen?.directoryRevision})`"
    width="950px"
    ><el-table :data="permissions"
      ><el-table-column prop="module" label="功能模块" width="140" /><el-table-column
        prop="feature"
        label="功能点"
        min-width="180"
      /><el-table-column prop="action" label="动作" width="100" /><el-table-column
        prop="key"
        label="稳定标识"
        min-width="160"
      /><el-table-column label="状态" width="90"
        ><template #default="{ row }">{{ row.active ? '有效' : '停用' }}</template></el-table-column
      ></el-table
    ></el-dialog
  >
  <el-dialog
    v-model="showCredentials"
    title="接入凭据（仅显示一次）"
    width="640px"
    @closed="credentials = undefined"
  >
    <p>关闭后不能再次查看；需要时重新签发。不要写入前端配置或提交到 Git。</p>
    <el-descriptions v-if="credentials" :column="1" border>
      <el-descriptions-item label="登录客户端">{{ credentials.clientId }}</el-descriptions-item>
      <el-descriptions-item label="登录凭据">{{ credentials.clientSecret }}</el-descriptions-item>
      <el-descriptions-item label="目录客户端">{{
        credentials.directoryClientId
      }}</el-descriptions-item>
      <el-descriptions-item label="目录凭据">{{
        credentials.directorySecret
      }}</el-descriptions-item>
    </el-descriptions>
  </el-dialog>
</template>
