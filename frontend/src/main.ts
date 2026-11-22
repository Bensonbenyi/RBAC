import { createApp } from 'vue';
import { createRouter, createWebHistory } from 'vue-router';
import App from './App.vue';
import UsersView from './views/UsersView.vue';
import RolesView from './views/RolesView.vue';
import CatalogView from './views/CatalogView.vue';
import ProfileView from './views/ProfileView.vue';
import DepartmentView from './views/DepartmentView.vue';
import { auth, refresh } from './auth';
import './style.css';
import { canEnter } from './permissions';
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', redirect: '/profile' },
    { path: '/users', component: UsersView },
    { path: '/roles', component: RolesView },
    { path: '/departments', component: DepartmentView },
    { path: '/systems', component: CatalogView, props: { kind: 'systems' } },
    { path: '/permissions', component: CatalogView, props: { kind: 'permissions' } },
    { path: '/profile', component: ProfileView },
  ],
});
router.beforeEach(async (to) => {
  if (auth.token && !auth.me) await refresh().catch(() => {});
  if (!canEnter(to.path)) return '/profile';
  return true;
});
createApp(App).use(router).mount('#app');
