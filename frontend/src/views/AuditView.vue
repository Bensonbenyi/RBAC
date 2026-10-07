<script setup lang="ts">
import { onMounted, ref } from 'vue';
import {
  api,
  query,
  statusLabel,
  auditEventTypes,
  type Employee,
  type Department,
  type Audit,
  type Page,
} from '../api';
defineProps<{ me: Employee; departments: Department[] }>();
const departmentId = ref<number>();
const eventType = ref('');
const result = ref('');
const search = ref('');
const range = ref<string[]>([]);
const page = ref(1);
const total = ref(0);
const logs = ref<Audit[]>([]);
const detail = ref<Audit>();
const dialog = ref(false);
function filters() {
  return {
    departmentId: departmentId.value,
    eventType: eventType.value,
    result: result.value,
    query: search.value,
    from: range.value[0] ? new Date(range.value[0]).toISOString() : undefined,
    to: range.value[1] ? new Date(range.value[1]).toISOString() : undefined,
  };
}
async function load() {
  const response = await api<Page<Audit>>(
    '/audit' + query({ ...filters(), page: page.value - 1, size: 20 }),
  );
  logs.value = response.items;
  total.value = response.total;
}
function exportCsv() {
  window.location.assign('/api/admin/audit/export' + query(filters()));
}
onMounted(load);
</script>
<template>
  <section class="panel">
    <div class="toolbar">
      <el-select v-if="me.superAdmin" v-model="departmentId" clearable placeholder="全局审计范围"
        ><el-option
          v-for="department in departments"
          :key="department.id"
          :label="department.name"
          :value="department.id" /></el-select
      ><el-select v-model="eventType" placeholder="全部事件类型" clearable filterable>
        <el-option
          v-for="value in auditEventTypes"
          :key="value"
          :label="statusLabel(value)"
          :value="value"
        /> </el-select
      ><el-input v-model="search" placeholder="对象编号或标识" clearable /><el-select
        v-model="result"
        clearable
        placeholder="全部结果"
        ><el-option
          v-for="value in ['SUCCESS', 'DENY', 'FAILURE', 'RECOVERED']"
          :key="value"
          :label="statusLabel(value)"
          :value="value" /></el-select
      ><el-date-picker
        v-model="range"
        type="datetimerange"
        value-format="YYYY-MM-DDTHH:mm:ss"
        start-placeholder="开始时间"
        end-placeholder="结束时间"
      /><el-button
        @click="
          page = 1;
          load();
        "
        >筛选</el-button
      ><el-button type="primary" @click="exportCsv">导出筛选日志</el-button>
    </div>
    <p class="hint">
      成功管理变更与审计一致提交。允许鉴权只统计吞吐量；拒绝与异常留痕。审计故障期间暂停受控请求，恢复事件说明可能缺口。
    </p>
    <el-table :data="logs"
      ><el-table-column label="时间" width="190"
        ><template #default="{ row }">{{
          new Date(row.occurredAt).toLocaleString('zh-CN')
        }}</template></el-table-column
      ><el-table-column prop="actorId" label="操作者" width="90" /><el-table-column
        label="事件类型"
        min-width="190"
        ><template #default="{ row }">{{ statusLabel(row.eventType) }}</template></el-table-column
      ><el-table-column prop="objectRef" label="对象" min-width="180" /><el-table-column
        label="结果"
        width="110"
        ><template #default="{ row }">{{ statusLabel(row.result) }}</template></el-table-column
      ><el-table-column label="操作" width="80"
        ><template #default="{ row }"
          ><el-button
            link
            type="primary"
            @click="
              detail = row;
              dialog = true;
            "
            >详情</el-button
          ></template
        ></el-table-column
      ></el-table
    ><el-pagination
      v-model:current-page="page"
      :page-size="20"
      :total="total"
      layout="total, prev, pager, next"
      @current-change="load"
    />
  </section>
  <el-dialog v-model="dialog" title="审计事件详情" width="650px"
    ><el-descriptions v-if="detail" :column="1" border
      ><el-descriptions-item label="编号">{{ detail.id }}</el-descriptions-item
      ><el-descriptions-item label="事件"
        >{{ statusLabel(detail.eventType) }} ({{ detail.eventType }})</el-descriptions-item
      ><el-descriptions-item label="对象">{{ detail.objectRef }}</el-descriptions-item
      ><el-descriptions-item label="结果">{{ statusLabel(detail.result) }}</el-descriptions-item
      ><el-descriptions-item label="说明">{{
        detail.detail
      }}</el-descriptions-item></el-descriptions
    ></el-dialog
  >
</template>
