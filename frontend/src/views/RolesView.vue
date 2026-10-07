<script setup lang="ts">
import { onMounted, reactive, ref, computed } from 'vue';
import { ElMessageBox } from 'element-plus';
import {
  api,
  query,
  type Employee,
  type Department,
  type System,
  type Role,
  type Permission,
} from '../api';
const props = defineProps<{ me: Employee; systems: System[]; departments: Department[] }>();
const systemId = ref<number>();
const departmentId = ref<number>();
const roles = ref<Role[]>([]);
const permissions = ref<Permission[]>([]);
const dialog = ref(false);
const copying = ref(false);
const templateId = ref<number>();
const copyDepartment = ref<number>();
const copyName = ref('');
const form = reactive({
  id: null as number | null,
  systemId: 0,
  departmentId: null as number | null,
  name: '',
  template: false,
  active: true,
  version: 0,
  permissions: [] as number[],
});
const allowedDepartments = computed(() =>
  props.departments.filter((d) =>
    props.systems.find((s) => s.id === form.systemId)?.departments.includes(d.id),
  ),
);
async function load() {
  roles.value = await api<Role[]>(
    '/roles' + query({ systemId: systemId.value, departmentId: departmentId.value }),
  );
}
async function edit(role?: Role) {
  Object.assign(
    form,
    role
      ? { ...role, permissions: [...role.permissions] }
      : {
          id: null,
          systemId: systemId.value ?? props.systems[0]?.id,
          departmentId: props.me.departmentId,
          name: '',
          template: false,
          active: true,
          version: 0,
          permissions: [],
        },
  );
  await directory();
  dialog.value = true;
}
async function directory() {
  if (!form.systemId) return;
  permissions.value = await api<Permission[]>(`/systems/${form.systemId}/permissions`);
}
async function save() {
  if (form.template) form.departmentId = null;
  const affected = form.id ? await api<number[]>(`/roles/${form.id}/affected`) : [];
  await ElMessageBox.confirm(
    `本次显式保存 ${form.permissions.length} 项权限，影响 ${affected.length} 名已分配员工。${form.active ? '权限变更最迟 3 秒影响后续鉴权。' : '停用会使该角色后续操作被拒绝。'}模板修改不传播到复制角色。确认保存？`,
    '角色影响确认',
  );
  await api('/roles', form);
  dialog.value = false;
  await load();
}
function copy(role: Role) {
  templateId.value = role.id;
  copyDepartment.value = props.me.departmentId ?? props.departments[0]?.id;
  copyName.value = `${role.name}-部门角色`;
  copying.value = true;
}
async function remove(role: Role) {
  const affected = await api<number[]>(`/roles/${role.id}/affected`);
  if (affected.length) {
    await ElMessageBox.alert(`仍有 ${affected.length} 名员工分配，须先撤权。`, '不能删除');
    return;
  }
  await ElMessageBox.confirm('确认删除角色？历史审计保留，角色名称不再复用。', '删除角色');
  await api(`/roles/${role.id}/delete`, { version: role.version });
  await load();
}
async function applyCopy() {
  await api('/roles/copy', {
    templateId: templateId.value,
    departmentId: copyDepartment.value,
    name: copyName.value,
  });
  copying.value = false;
  await load();
}
onMounted(load);
</script>
<template>
  <section class="panel">
    <div class="toolbar">
      <el-select v-model="systemId" clearable placeholder="全部可见系统"
        ><el-option
          v-for="system in systems"
          :key="system.id"
          :label="system.name"
          :value="system.id" /></el-select
      ><el-select v-model="departmentId" clearable placeholder="全部可见范围"
        ><el-option
          v-for="department in departments"
          :key="department.id"
          :label="department.name"
          :value="department.id" /></el-select
      ><el-button @click="load">查询</el-button
      ><el-button type="primary" @click="edit()">新增角色</el-button>
    </div>
    <p class="hint">
      模板须先复制为部门角色才能分配。新增权限默认不授予；明确停用项从选择中撤下，重新启用须再次配置。
    </p>
    <el-table :data="roles"
      ><el-table-column prop="name" label="角色名称" min-width="150" /><el-table-column
        label="系统"
        min-width="180"
        ><template #default="{ row }">{{
          systems.find((s) => s.id === row.systemId)?.name
        }}</template></el-table-column
      ><el-table-column label="范围" min-width="160"
        ><template #default="{ row }">{{
          row.template ? '全局模板' : departments.find((d) => d.id === row.departmentId)?.name
        }}</template></el-table-column
      ><el-table-column label="配置权限" width="95"
        ><template #default="{ row }">{{ row.permissions.length }} 项</template></el-table-column
      ><el-table-column label="状态" width="90"
        ><template #default="{ row }">{{ row.active ? '有效' : '停用' }}</template></el-table-column
      ><el-table-column label="操作" width="230"
        ><template #default="{ row }"
          ><el-button v-if="row.template" link type="primary" @click="copy(row)"
            >复制到部门</el-button
          ><el-button v-if="me.superAdmin || !row.template" link type="primary" @click="edit(row)"
            >配置</el-button
          ><el-button v-if="me.superAdmin || !row.template" link type="danger" @click="remove(row)"
            >删除</el-button
          ></template
        ></el-table-column
      ></el-table
    >
  </section>
  <el-dialog v-model="dialog" title="角色职责与权限配置" width="880px"
    ><el-form label-width="105px"
      ><el-form-item label="角色名称"><el-input v-model="form.name" maxlength="120" /></el-form-item
      ><el-form-item label="所属系统"
        ><el-select
          v-model="form.systemId"
          :disabled="!!form.id"
          @change="
            form.permissions = [];
            directory();
          "
          ><el-option
            v-for="system in systems"
            :key="system.id"
            :label="system.name"
            :value="system.id" /></el-select></el-form-item
      ><el-form-item v-if="me.superAdmin" label="全局模板"
        ><el-switch v-model="form.template" :disabled="!!form.id" /></el-form-item
      ><el-form-item v-if="!form.template" label="所属部门"
        ><el-select v-model="form.departmentId" :disabled="!!form.id"
          ><el-option
            v-for="department in allowedDepartments"
            :key="department.id"
            :label="department.name"
            :value="department.id" /></el-select></el-form-item
      ><el-form-item label="有效状态"><el-switch v-model="form.active" /></el-form-item></el-form
    ><el-checkbox-group v-model="form.permissions"
      ><el-table :data="permissions" height="370"
        ><el-table-column label="选择" width="70"
          ><template #default="{ row }"
            ><el-checkbox
              :value="row.id"
              :disabled="!row.active && !form.permissions.includes(row.id)"
              :aria-label="row.key" /></template></el-table-column
        ><el-table-column prop="module" label="模块" width="130" /><el-table-column
          prop="feature"
          label="功能点"
          min-width="190"
        /><el-table-column prop="action" label="操作动作" width="100" /><el-table-column
          label="状态"
          width="90"
          ><template #default="{ row }">{{
            row.active ? '有效' : '已停用'
          }}</template></el-table-column
        ></el-table
      ></el-checkbox-group
    ><template #footer
      ><el-button @click="dialog = false">取消</el-button
      ><el-button type="primary" @click="save">预览影响并保存</el-button></template
    ></el-dialog
  ><el-dialog v-model="copying" title="复制全局模板" width="500px"
    ><el-form label-width="100px"
      ><el-form-item label="部门"
        ><el-select v-model="copyDepartment"
          ><el-option
            v-for="department in departments"
            :key="department.id"
            :label="department.name"
            :value="department.id" /></el-select></el-form-item
      ><el-form-item label="角色名称"><el-input v-model="copyName" /></el-form-item></el-form
    ><template #footer
      ><el-button @click="copying = false">取消</el-button
      ><el-button type="primary" @click="applyCopy">独立复制</el-button></template
    ></el-dialog
  >
</template>
