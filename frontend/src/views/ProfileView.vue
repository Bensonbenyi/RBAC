<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { auth, refresh } from '../auth';

const error = ref('');
onMounted(async () => {
  try {
    await refresh();
  } catch (cause) {
    error.value = (cause as Error).message;
  }
});
</script>

<template>
  <h2>个人资料</h2>
  <p v-if="error" role="alert" class="error">{{ error }}</p>
  <dl v-if="auth.me">
    <dt>姓名</dt>
    <dd>{{ auth.me.name }}</dd>
    <dt>用户名</dt>
    <dd>{{ auth.me.no }}</dd>
    <dt>所属部门编号</dt>
    <dd>{{ auth.me.departmentId }}</dd>
  </dl>
</template>
