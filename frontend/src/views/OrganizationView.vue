<script setup lang="ts">
import { reactive, ref } from 'vue';
import { api, type Employee, type Company, type Department } from '../api';
import { ElMessageBox } from 'element-plus';
const props = defineProps<{ me: Employee; companies: Company[]; departments: Department[] }>();
const emit = defineEmits<{ changed: [] }>();
const dialog = ref(false);
const kind = ref('company');
const form = reactive({
  id: null as number | null,
  name: '',
  companyId: 1,
  active: true,
  version: 0,
});
function edit(type: string, row?: Company | Department) {
  kind.value = type;
  Object.assign(
    form,
    { id: null, name: '', companyId: props.companies[0]?.id, active: true, version: 0 },
    row,
  );
  dialog.value = true;
}
async function save() {
  if (!form.active)
    await ElMessageBox.confirm(
      '停用前须处理有效员工、部门和可用系统关联。确认继续？',
      '组织状态确认',
    );
  await api(kind.value === 'company' ? '/companies' : '/departments', form);
  dialog.value = false;
  emit('changed');
}
</script>
<template>
  <section class="panel">
    <el-alert
      v-if="!me.superAdmin"
      title="部门管理员仅查看本部门组织信息，组织维护由最高管理员执行"
      :closable="false"
      type="info"
    /><el-tabs
      ><el-tab-pane label="公司"
        ><el-button v-if="me.superAdmin" type="primary" @click="edit('company')">新增公司</el-button
        ><el-table :data="companies"
          ><el-table-column prop="name" label="公司名称" /><el-table-column label="状态"
            ><template #default="{ row }">{{
              row.active ? '有效' : '停用'
            }}</template></el-table-column
          ><el-table-column v-if="me.superAdmin" label="操作"
            ><template #default="{ row }"
              ><el-button link type="primary" @click="edit('company', row)"
                >维护</el-button
              ></template
            ></el-table-column
          ></el-table
        ></el-tab-pane
      ><el-tab-pane label="部门"
        ><el-button v-if="me.superAdmin" type="primary" @click="edit('department')"
          >新增部门</el-button
        >
        <p class="hint">组织固定为公司与末级部门两层，部门没有下级。</p>
        <el-table :data="departments"
          ><el-table-column prop="name" label="部门名称" /><el-table-column label="所属公司"
            ><template #default="{ row }">{{
              companies.find((c) => c.id === row.companyId)?.name
            }}</template></el-table-column
          ><el-table-column label="状态"
            ><template #default="{ row }">{{
              row.active ? '有效' : '停用'
            }}</template></el-table-column
          ><el-table-column v-if="me.superAdmin" label="操作"
            ><template #default="{ row }"
              ><el-button link type="primary" @click="edit('department', row)"
                >维护</el-button
              ></template
            ></el-table-column
          ></el-table
        ></el-tab-pane
      ></el-tabs
    >
  </section>
  <el-dialog v-model="dialog" :title="kind === 'company' ? '公司维护' : '部门维护'" width="480px"
    ><el-form label-width="90px"
      ><el-form-item label="名称"><el-input v-model="form.name" maxlength="100" /></el-form-item
      ><el-form-item v-if="kind === 'department'" label="所属公司"
        ><el-select v-model="form.companyId" :disabled="form.id !== null"
          ><el-option
            v-for="company in companies"
            :key="company.id"
            :label="company.name"
            :value="company.id" /></el-select></el-form-item
      ><el-form-item label="有效状态"><el-switch v-model="form.active" /></el-form-item></el-form
    ><template #footer
      ><el-button @click="dialog = false">取消</el-button
      ><el-button type="primary" @click="save">保存</el-button></template
    ></el-dialog
  >
</template>
