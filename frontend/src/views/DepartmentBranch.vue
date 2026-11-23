<script setup lang="ts">
import type { DepartmentNode } from '../auth';

withDefaults(defineProps<{ node: DepartmentNode; depth?: number }>(), { depth: 0 });
</script>

<template>
  <li>
    <details v-if="node.children.length" :open="depth === 0">
      <summary>{{ node.name }}（{{ node.id }}）</summary>
      <ul>
        <DepartmentBranch
          v-for="child in node.children"
          :key="child.id"
          :node="child"
          :depth="depth + 1"
        />
      </ul>
    </details>
    <span v-else>{{ node.name }}（{{ node.id }}）</span>
  </li>
</template>
