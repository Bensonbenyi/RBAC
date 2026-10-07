<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  api,
  statusLabel,
  query,
  type Employee,
  type Company,
  type Department,
  type System,
  type Page,
  type Role,
  type Effective,
  type Batch,
} from '../api';
const props = defineProps<{
  me: Employee;
  companies: Company[];
  departments: Department[];
  systems: System[];
}>();
const employees = ref<Employee[]>([]);
const total = ref(0);
const page = ref(1);
const search = ref('');
const filter = ref<number>();
const loading = ref(false);
const selected = ref<Employee[]>([]);
const edit = ref(false);
const form = reactive({
  id: null as number | null,
  username: '',
  name: '',
  phone: '',
  companyId: 1,
  departmentId: null as number | null,
  positionName: '',
  version: 0,
});
const availableDepartments = computed(() =>
  props.departments.filter((d) => d.companyId === form.companyId),
);
const detail = ref<Employee>();
const effective = ref<Effective[]>([]);
const detailOpen = ref(false);
const roles = ref<Role[]>([]);
const systemId = ref<number>();
const roleId = ref<number | null>(null);
const transferOpen = ref(false);
const newDepartment = ref<number>();
const assignmentOpen = ref(false);
const batchResult = ref<Batch>();
async function load() {
  loading.value = true;
  try {
    const result = await api<Page<Employee>>(
      '/employees' +
        query({ query: search.value, departmentId: filter.value, page: page.value - 1, size: 20 }),
    );
    employees.value = result.items;
    total.value = result.total;
  } finally {
    loading.value = false;
  }
}
function editing(employee?: Employee) {
  Object.assign(
    form,
    employee
      ? { ...employee }
      : {
          id: null,
          username: '',
          name: '',
          phone: '',
          companyId: props.me.companyId,
          departmentId: props.me.departmentId,
          positionName: '',
          version: 0,
        },
  );
  edit.value = true;
}
async function save() {
  await api('/employees', form);
  edit.value = false;
  ElMessage.success('员工资料已保存');
  await load();
}
async function state(employee: Employee) {
  await ElMessageBox.confirm(
    employee.active
      ? '停用会撤销所有系统角色并结束全部统一会话。确认停用？'
      : '启用不会恢复原有授权，须另行授予。确认启用？',
    '确认状态影响',
  );
  await api(`/employees/${employee.id}/state`, {
    active: !employee.active,
    version: employee.version,
  });
  await load();
}
async function appoint(employee: Employee) {
  detail.value = employee;
  await ElMessageBox.confirm(
    `当前：${employee.superAdmin ? '最高管理员 ' : ''}${employee.departmentAdmin ? '部门管理员' : '无部门管理员身份'}。是否切换部门管理员身份？`,
    '管理员任命',
  );
  await api('/administrators', {
    employeeId: employee.id,
    superAdmin: employee.superAdmin,
    departmentAdmin: !employee.departmentAdmin,
    version: employee.version,
  });
  await load();
}
async function highest(employee: Employee) {
  await ElMessageBox.confirm(
    employee.superAdmin
      ? '取消最高管理员身份后不再具有全局管理范围。确认取消？'
      : '任命最高管理员将授予全部后台管理能力。确认任命？',
    '最高管理员身份',
  );
  await api('/administrators', {
    employeeId: employee.id,
    superAdmin: !employee.superAdmin,
    departmentAdmin: employee.departmentAdmin,
    version: employee.version,
  });
  await load();
}
async function show(employee: Employee) {
  detail.value = employee;
  effective.value = await api<Effective[]>(`/employees/${employee.id}/permissions`);
  detailOpen.value = true;
}
async function assign(employee?: Employee) {
  detail.value = employee;
  systemId.value = props.systems.find((s) => s.active)?.id;
  roleId.value = null;
  assignmentOpen.value = true;
  batchResult.value = undefined;
  await loadRoles();
}
async function loadRoles() {
  if (!systemId.value) return;
  roles.value = (await api<Role[]>('/roles' + query({ systemId: systemId.value }))).filter(
    (r) =>
      !r.template && r.active && (!detail.value || r.departmentId === detail.value.departmentId),
  );
  roleId.value = null;
}
async function applyAssignment() {
  if (!systemId.value) return;
  const targets = detail.value ? [detail.value] : selected.value;
  if (!targets.length) return;
  // 使用预览得到的版本提交，冲突时要求重新确认，绝不自动取新版本覆盖。
  const facts = await Promise.all(
    targets.map((employee) => api<Effective[]>(`/employees/${employee.id}/permissions`)),
  );
  const targetVersions = targets.map((employee, index) => ({
    employeeId: employee.id,
    expectedVersion:
      facts[index].find((f) => f.assignment.systemId === systemId.value)?.assignment.version ?? -1,
  }));
  const previous = facts
    .map(
      (list, index) =>
        `${targets[index].name}：${list.find((f) => f.assignment.systemId === systemId.value)?.role?.name ?? '无角色'}`,
    )
    .join('；');
  await ElMessageBox.confirm(
    `${previous}。${roleId.value === null ? '撤销当前角色' : '新角色将替换旧角色，不叠加'}。确认提交 ${targets.length} 名员工？`,
    '单角色影响确认',
  );
  if (detail.value) {
    await api('/assignments', {
      ...targetVersions[0],
      systemId: systemId.value,
      roleId: roleId.value,
    });
    assignmentOpen.value = false;
    ElMessage.success('变更已提交，最迟 3 秒影响后续请求');
  } else {
    batchResult.value = await api<Batch>('/batches', {
      requestKey: crypto.randomUUID(),
      systemId: systemId.value,
      roleId: roleId.value,
      targets: targetVersions,
    });
  }
}
function transfer(employee: Employee) {
  detail.value = employee;
  newDepartment.value = undefined;
  transferOpen.value = true;
}
async function applyTransfer() {
  if (!detail.value || !newDepartment.value) return;
  await ElMessageBox.confirm(
    '原部门须先手工撤销全部角色并等待 3 秒；调动清除原部门管理员身份，不继承角色。确认继续？',
    '人工调动确认',
  );
  await api(`/employees/${detail.value.id}/transfer`, {
    departmentId: newDepartment.value,
    version: detail.value.version,
  });
  transferOpen.value = false;
  await load();
}
onMounted(load);
</script>
<template>
  <section class="panel">
    <div class="toolbar">
      <el-input
        v-model="search"
        placeholder="姓名、用户名或手机号"
        clearable
        @keyup.enter="
          page = 1;
          load();
        "
      /><el-select v-model="filter" placeholder="全部可见部门" clearable
        ><el-option
          v-for="department in departments"
          :key="department.id"
          :label="department.name"
          :value="department.id" /></el-select
      ><el-button
        @click="
          page = 1;
          load();
        "
        >查询</el-button
      ><el-button type="primary" @click="editing()">新增员工</el-button
      ><el-button :disabled="!selected.length" @click="assign()"
        >批量分配或撤权 ({{ selected.length }})</el-button
      >
    </div>
    <p class="hint">
      未确认部门的员工可统一登录，完成部门确认后才能获得部门角色。每名员工每个系统最多一个角色。
    </p>
    <el-table v-loading="loading" :data="employees" @selection-change="selected = $event">
      <el-table-column type="selection" width="44" />
      <el-table-column prop="username" label="用户名" width="110" />
      <el-table-column prop="name" label="姓名" width="88" />
      <el-table-column label="部门" min-width="125"
        ><template #default="{ row }">{{
          departments.find((d) => d.id === row.departmentId)?.name ?? '待确认部门'
        }}</template></el-table-column
      >
      <el-table-column prop="positionName" label="岗位" min-width="90" />
      <el-table-column label="状态" width="82"
        ><template #default="{ row }"
          ><el-tag :type="row.active ? 'success' : 'info'">{{
            row.active ? '有效' : '停用'
          }}</el-tag></template
        ></el-table-column
      >
      <el-table-column label="后台身份" min-width="125"
        ><template #default="{ row }">{{
          row.superAdmin ? '最高管理员' : row.departmentAdmin ? '部门管理员' : '普通员工'
        }}</template></el-table-column
      >
      <el-table-column label="操作" width="300"
        ><template #default="{ row }">
          <el-button link type="primary" @click="editing(row)">编辑</el-button>
          <el-button link type="primary" @click="show(row)">有效权限</el-button>
          <el-button link type="primary" @click="assign(row)">授权</el-button>
          <el-button link type="warning" @click="state(row)">{{
            row.active ? '停用' : '启用'
          }}</el-button>
          <el-dropdown v-if="me.superAdmin"
            ><el-button link>更多</el-button
            ><template #dropdown
              ><el-dropdown-menu>
                <el-dropdown-item @click="appoint(row)">切换部门管理员</el-dropdown-item>
                <el-dropdown-item @click="highest(row)">切换最高管理员</el-dropdown-item>
                <el-dropdown-item @click="transfer(row)">人工调动</el-dropdown-item>
              </el-dropdown-menu></template
            ></el-dropdown
          >
        </template></el-table-column
      >
    </el-table>
    <el-pagination
      v-model:current-page="page"
      :page-size="20"
      :total="total"
      layout="total, prev, pager, next"
      @current-change="load"
    />
  </section>
  <el-dialog v-model="edit" :title="form.id ? '维护员工资料' : '新增员工'" width="540px"
    ><el-form label-width="100px"
      ><el-form-item label="用户名"
        ><el-input v-model="form.username" maxlength="80" /></el-form-item
      ><el-form-item label="姓名"><el-input v-model="form.name" maxlength="80" /></el-form-item
      ><el-form-item label="手机号"><el-input v-model="form.phone" maxlength="30" /></el-form-item
      ><el-form-item label="公司"
        ><el-select
          v-model="form.companyId"
          :disabled="!me.superAdmin"
          @change="form.departmentId = null"
          ><el-option
            v-for="company in companies"
            :key="company.id"
            :label="company.name"
            :value="company.id" /></el-select></el-form-item
      ><el-form-item label="部门"
        ><el-select
          v-model="form.departmentId"
          :disabled="!!form.id && form.departmentId !== null"
          clearable
          ><el-option
            v-for="department in availableDepartments"
            :key="department.id"
            :label="department.name"
            :value="department.id" /></el-select></el-form-item
      ><el-form-item label="岗位"
        ><el-input v-model="form.positionName" maxlength="120" /></el-form-item></el-form
    ><template #footer
      ><el-button @click="edit = false">取消</el-button
      ><el-button type="primary" @click="save">保存</el-button></template
    ></el-dialog
  >
  <el-dialog v-model="detailOpen" :title="`${detail?.name}的有效角色与权限`" width="850px"
    ><el-table :data="effective"
      ><el-table-column label="系统" min-width="160"
        ><template #default="{ row }">{{
          systems.find((s) => s.id === row.assignment.systemId)?.name
        }}</template></el-table-column
      ><el-table-column label="当前角色" min-width="120"
        ><template #default="{ row }">{{ row.role?.name ?? '未分配' }}</template></el-table-column
      ><el-table-column label="有效性原因" width="170"
        ><template #default="{ row }">{{ statusLabel(row.reason) }}</template></el-table-column
      ><el-table-column label="有效权限"
        ><template #default="{ row }"
          ><el-tag v-for="permission in row.permissions" :key="permission" style="margin: 3px">{{
            permission
          }}</el-tag></template
        ></el-table-column
      ></el-table
    ></el-dialog
  >
  <el-dialog
    v-model="assignmentOpen"
    :title="detail ? `为${detail.name}分配角色` : '批量角色分配与撤销'"
    width="760px"
    ><el-form label-width="100px"
      ><el-form-item label="系统"
        ><el-select v-model="systemId" @change="loadRoles"
          ><el-option
            v-for="system in systems"
            :key="system.id"
            :label="system.name"
            :value="system.id" /></el-select></el-form-item
      ><el-form-item label="目标角色"
        ><el-select v-model="roleId" placeholder="撤销当前角色" clearable @clear="roleId = null"
          ><el-option label="撤销当前角色" :value="null" /><el-option
            v-for="role in roles"
            :key="role.id"
            :label="`${role.name} (${departments.find((d) => d.id === role.departmentId)?.name})`"
            :value="role.id" /></el-select></el-form-item
    ></el-form>
    <p class="hint">
      部门角色须与员工归属一致。跨部门批量选择错误角色会逐项报告失败；越管理范围整批拒绝。
    </p>
    <el-table v-if="batchResult" :data="batchResult.items"
      ><el-table-column prop="employeeId" label="员工编号" /><el-table-column label="结果"
        ><template #default="{ row }">{{ statusLabel(row.status) }}</template></el-table-column
      ><el-table-column prop="message" label="说明" min-width="300" /></el-table
    ><template #footer
      ><el-button @click="assignmentOpen = false">关闭</el-button
      ><el-button type="primary" @click="applyAssignment">预览并确认提交</el-button></template
    ></el-dialog
  >
  <el-dialog v-model="transferOpen" title="人工跨部门调动" width="540px"
    ><p class="hint">先由原部门管理员撤权，最高管理员完成调动，再由新部门管理员重新授权。</p>
    <el-select v-model="newDepartment" placeholder="选择目标部门" style="width: 100%"
      ><el-option
        v-for="department in departments"
        :key="department.id"
        :label="`${companies.find((c) => c.id === department.companyId)?.name}/${department.name}`"
        :value="department.id" /></el-select
    ><template #footer
      ><el-button @click="transferOpen = false">取消</el-button
      ><el-button type="primary" @click="applyTransfer">校验并调动</el-button></template
    ></el-dialog
  >
</template>
