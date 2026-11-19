<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { auth, request, refresh } from '../auth';
const name = ref(auth.me?.name || ''),
  phone = ref(''),
  message = ref('');
onMounted(async () => {
  try {
    await refresh();
    name.value = auth.me?.name || '';
  } catch (e) {
    message.value = (e as Error).message;
  }
});
async function save() {
  message.value = '';
  try {
    await request('/auth/me', 'PATCH', {
      name: name.value,
      ...(phone.value ? { phone: phone.value } : {}),
    });
    await refresh();
    phone.value = '';
    message.value = '已保存';
  } catch (e) {
    message.value = (e as Error).message;
  }
}
</script>
<template>
  <h2>个人资料</h2>
  <p>用户名 {{ auth.me?.no }}　所属部门编号 {{ auth.me?.departmentId }}</p>
  <form @submit.prevent="save">
    <label>姓名<input v-model.trim="name" required maxlength="100" /></label
    ><label
      >新手机号<input
        v-model="phone"
        pattern="1[3-9][0-9]{9}"
        placeholder="留空保留当前手机号" /></label
    ><button>保存</button>
  </form>
  <p role="status">{{ message }}</p>
</template>
