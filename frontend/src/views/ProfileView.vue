<script setup lang="ts">
import { auth } from '../auth';
</script>

<template>
  <h2>个人资料</h2>
  <dl v-if="auth.me">
    <dt>姓名</dt>
    <dd>{{ auth.me.name }}</dd>
    <dt>用户名</dt>
    <dd>{{ auth.me.no }}</dd>
    <dt>所属部门编号</dt>
    <dd>{{ auth.me.departmentId }}</dd>
  </dl>
</template>
