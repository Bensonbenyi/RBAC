<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { api, logout, type Employee, type Company, type Department, type System } from './api';
import EmployeesView from './views/EmployeesView.vue';
import OrganizationView from './views/OrganizationView.vue';
import SystemsView from './views/SystemsView.vue';
import RolesView from './views/RolesView.vue';
import AuditView from './views/AuditView.vue';
import BatchView from './views/BatchView.vue';

const me = ref<Employee>();
const companies = ref<Company[]>([]);
const departments = ref<Department[]>([]);
const systems = ref<System[]>([]);
const tab = ref('employees');
const error = ref('');
const tabs = [
  { key: 'employees', title: '员工与授权' },
  { key: 'organization', title: '组织管理' },
  { key: 'systems', title: '系统接入与目录' },
  { key: 'roles', title: '模板与部门角色' },
  { key: 'batches', title: '批量任务结果' },
  { key: 'audit', title: '审计查询与导出' },
];
const title = computed(() => tabs.find((item) => item.key === tab.value)?.title);
async function refresh() {
  [companies.value, departments.value, systems.value] = await Promise.all([
    api<Company[]>('/companies'),
    api<Department[]>('/departments'),
    api<System[]>('/systems'),
  ]);
}
onMounted(async () => {
  try {
    me.value = await api<Employee>('/me');
    await refresh();
  } catch (failure) {
    error.value = failure instanceof Error ? failure.message : '无法读取后台身份';
  }
});
</script>
<template>
  <div v-if="error" class="empty">
    <h1>无法进入管理后台</h1>
    <p>{{ error }}</p>
    <p>普通员工请通过业务系统统一登录。</p>
    <el-button @click="logout">退出当前身份</el-button><a href="/login">重新登录</a>
  </div>
  <div v-else-if="me" class="shell">
    <aside class="sidebar">
      <h1>集团统一权限<br />管理平台</h1>
      <el-menu :default-active="tab" @select="tab = $event"
        ><el-menu-item v-for="item in tabs" :key="item.key" :index="item.key">{{
          item.title
        }}</el-menu-item></el-menu
      >
      <p class="hint" style="color: #c9d8ec; padding: 12px">
        课程 Demo · RBAC0<br />每人每系统一个角色
      </p>
    </aside>
    <main class="main">
      <header class="topbar">
        <h2>{{ title }}</h2>
        <div class="user">
          <span>{{ me.name }}</span
          ><el-tag>{{ me.superAdmin ? '最高管理员' : '部门管理员' }}</el-tag
          ><el-button @click="logout">统一退出</el-button>
        </div>
      </header>
      <EmployeesView
        v-if="tab === 'employees'"
        :me="me"
        :companies="companies"
        :departments="departments"
        :systems="systems"
      />
      <OrganizationView
        v-if="tab === 'organization'"
        :me="me"
        :companies="companies"
        :departments="departments"
        @changed="refresh"
      />
      <SystemsView
        v-if="tab === 'systems'"
        :me="me"
        :systems="systems"
        :departments="departments"
        @changed="refresh"
      />
      <RolesView v-if="tab === 'roles'" :me="me" :systems="systems" :departments="departments" />
      <BatchView v-if="tab === 'batches'" :systems="systems" />
      <AuditView v-if="tab === 'audit'" :me="me" :departments="departments" />
    </main>
  </div>
  <div v-else class="empty">正在校验管理身份...</div>
</template>
