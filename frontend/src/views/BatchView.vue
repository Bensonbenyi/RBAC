<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessageBox } from 'element-plus';
import { api, statusLabel, type System, type Batch, type Effective } from '../api';
defineProps<{ systems: System[] }>();
const jobs = ref<Batch[]>([]);
const chosen = ref<Batch>();
const dialog = ref(false);
async function load() {
  jobs.value = await api<Batch[]>('/batches');
}
async function retry() {
  if (!chosen.value) return;
  const job = chosen.value;
  const failed = job.items.filter((item) => ['FAILED', 'INTERRUPTED'].includes(item.status));
  const facts = await Promise.all(
    failed.map((item) => api<Effective[]>(`/employees/${item.employeeId}/permissions`)),
  );
  const targets = failed.map((item, index) => ({
    employeeId: item.employeeId,
    expectedVersion:
      facts[index].find((f) => f.assignment.systemId === job.systemId)?.assignment.version ?? -1,
  }));
  await ElMessageBox.confirm(
    `将按最新授权版本重试 ${targets.length} 个失败项目，可能替换目前角色。成功项目不重复执行。确认？`,
    '失败重试确认',
  );
  chosen.value = await api<Batch>(`/batches/${job.id}/retry`, targets);
  await load();
}
onMounted(load);
</script>
<template>
  <section class="panel">
    <div class="toolbar"><el-button @click="load">刷新任务</el-button></div>
    <p class="hint">
      每项独立提交并记录结果，中断不会回滚已成功项目。重试前须重新确认当前角色版本。
    </p>
    <el-table :data="jobs"
      ><el-table-column prop="id" label="任务编号" width="90" /><el-table-column
        label="系统"
        min-width="190"
        ><template #default="{ row }">{{
          systems.find((s) => s.id === row.systemId)?.name
        }}</template></el-table-column
      ><el-table-column label="动作" width="110"
        ><template #default="{ row }">{{
          row.roleId === null ? '撤权' : '角色替换'
        }}</template></el-table-column
      ><el-table-column label="总体结果" width="180"
        ><template #default="{ row }">{{ statusLabel(row.status) }}</template></el-table-column
      ><el-table-column label="成功／总数" width="120"
        ><template #default="{ row }"
          >{{ row.items.filter((i: { status: string }) => i.status === 'SUCCESS').length }} /
          {{ row.items.length }}</template
        ></el-table-column
      ><el-table-column label="操作" width="100"
        ><template #default="{ row }"
          ><el-button
            link
            type="primary"
            @click="
              chosen = row;
              dialog = true;
            "
            >逐项结果</el-button
          ></template
        ></el-table-column
      ></el-table
    >
  </section>
  <el-dialog v-model="dialog" :title="`批量任务 ${chosen?.id}`" width="800px"
    ><el-table :data="chosen?.items"
      ><el-table-column prop="employeeId" label="员工编号" width="110" /><el-table-column
        label="结果"
        width="140"
        ><template #default="{ row }">{{ statusLabel(row.status) }}</template></el-table-column
      ><el-table-column prop="message" label="说明" min-width="300" /></el-table
    ><template #footer
      ><el-button @click="dialog = false">关闭</el-button
      ><el-button
        v-if="chosen?.items.some((i) => ['FAILED', 'INTERRUPTED'].includes(i.status))"
        type="primary"
        @click="retry"
        >确认并重试失败项目</el-button
      ></template
    ></el-dialog
  >
</template>
