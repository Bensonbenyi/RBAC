<script setup lang="ts">
import type { DepartmentNode } from '../auth';

defineProps<{ node: DepartmentNode }>();
</script>

<template>
  <li>
    <details v-if="node.children.length" open>
      <summary>{{ node.name }}（{{ node.id }}）</summary>
      <ul>
        <DepartmentBranch v-for="child in node.children" :key="child.id" :node="child" />
      </ul>
    </details>
    <span v-else>{{ node.name }}（{{ node.id }}）</span>
  </li>
</template>
