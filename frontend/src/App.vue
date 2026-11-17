<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, watch } from 'vue';
import { auth, isCourse, request, saveToken, clearToken, refresh, allowed } from './auth';
import { platformMenus, canEnter } from './permissions';
import { useRouter, useRoute } from 'vue-router';
const router = useRouter(),
  route = useRoute();
const showManagement = computed(() => platformMenus.some((item) => allowed(item.code)));
async function syncPermissions() {
  if (auth.token)
    try {
      await refresh();
    } catch (e) {
      error.value = (e as Error).message;
    }
}
watch(
  () => auth.me,
  () => {
    if (!isCourse && !canEnter(route.path)) void router.replace('/profile');
  },
);
onMounted(() => window.addEventListener('focus', syncPermissions));
onUnmounted(() => window.removeEventListener('focus', syncPermissions));
const username = ref(''),
  password = ref(''),
  error = ref(''),
  busy = ref(false);
async function login() {
  error.value = '';
  busy.value = true;
  try {
    const result = await request<{ accessToken: string }>('/auth/login', 'POST', {
      username: username.value,
      password: password.value,
      systems: [isCourse ? 'course-project-management' : 'rbac'],
    });
    saveToken(result.accessToken);
    password.value = '';
    await refresh();
  } catch (e) {
    clearToken();
    error.value = (e as Error).message;
  } finally {
    busy.value = false;
  }
}
async function logout() {
  try {
    await request('/auth/logout', 'POST');
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    clearToken();
  }
}
onMounted(async () => {
  if (auth.token)
    try {
      await refresh();
    } catch (e) {
      error.value = (e as Error).message;
      clearToken();
    }
});
</script>
<template>
  <header>
    <h1>{{ isCourse ? '课程管理系统' : '统一权限管理系统' }}</h1>
    <div v-if="auth.me">{{ auth.me.name }} <button @click="logout">退出</button></div>
  </header>
  <main v-if="!auth.me" class="login">
    <h2>登录</h2>
    <p>使用用户名和手机号登录。</p>
    <form @submit.prevent="login">
      <label
        >用户名<input
          v-model.trim="username"
          required
          maxlength="64"
          autocomplete="username" /></label
      ><label
        >手机号<input
          v-model="password"
          type="password"
          required
          pattern="1[3-9][0-9]{9}"
          autocomplete="current-password" /></label
      ><button :disabled="busy">{{ busy ? '登录中' : '登录' }}</button>
    </form>
    <p role="alert" class="error">{{ error }}</p>
  </main>
  <template v-else
    ><nav v-if="!isCourse">
      <RouterLink to="/profile">个人资料</RouterLink
      ><template v-if="showManagement"
        ><button
          v-for="menu in platformMenus"
          :key="menu.path"
          :disabled="!allowed(menu.code)"
          @click="router.push(menu.path)"
        >
          {{ menu.label }}
        </button></template
      ><button @click="syncPermissions">刷新权限</button
      ><a href="/course/" target="_blank">课程系统</a>
    </nav>
    <main><RouterView :key="$route.path" /></main
  ></template>
</template>
