<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { allPages, type DepartmentNode } from '../auth';
import DepartmentBranch from './DepartmentBranch.vue';

const roots = ref<DepartmentNode[]>([]);
const error = ref('');

onMounted(async () => {
  try {
    const departments = await allPages<Omit<DepartmentNode, 'children'>>('/api/departments');
    const byId = new Map<string, DepartmentNode>();
    for (const department of departments) {
      byId.set(department.id, { ...department, children: [] });
    }
    for (const node of byId.values()) {
      const parent = node.parentId === null ? undefined : byId.get(node.parentId);
      (parent?.children ?? roots.value).push(node);
    }
  } catch (cause) {
    error.value = (cause as Error).message;
  }
});
</script>

<template>
  <h2>部门目录</h2>
  <p role="alert" class="error">{{ error }}</p>
  <ul>
    <DepartmentBranch v-for="node in roots" :key="node.id" :node="node" />
  </ul>
</template>
