<script setup lang="ts">
import {ref,onMounted} from 'vue';import {request,allowed,type Page,type User,type Role} from '../auth';
const users=ref<User[]>([]),page=ref(0),total=ref(0),error=ref(''),editing=ref<User|null>(null),form=ref({username:'',name:'',phone:''});
const selected=ref<User|null>(null),assigned=ref<Role[]>([]),roles=ref<Role[]>([]),roleId=ref('');
async function run(action:()=>Promise<void>){error.value='';try{await action()}catch(e){error.value=(e as Error).message}}
async function load(){const data=await request<Page<User>>(`/api/users?page=${page.value}&size=20`);users.value=data.items;total.value=data.total}
function edit(user:User){editing.value=user;form.value={username:user.username,name:user.name,phone:user.phone}}
function reset(){editing.value=null;form.value={username:'',name:'',phone:''}}
async function save(){await run(async()=>{await request(editing.value?`/api/users/${editing.value.id}`:'/api/users',editing.value?'PATCH':'POST',editing.value?{name:form.value.name,phone:form.value.phone}:form.value);reset();await load()})}
async function remove(user:User){if(confirm(`删除用户 ${user.name}？`))await run(async()=>{await request(`/api/users/${user.id}`,'DELETE');await load()})}
async function showRoles(user:User){await run(async()=>{selected.value=user;assigned.value=await request<Role[]>(`/api/users/${user.id}/roles`);roles.value=(await request<Page<Role>>('/api/roles?size=100')).items})}
async function assign(){await run(async()=>{if(!selected.value||!roleId.value)return;await request(`/api/users/${selected.value.id}/roles`,'POST',{roleId:roleId.value});await showRoles(selected.value)})}
async function revoke(role:Role){await run(async()=>{if(!selected.value)return;await request(`/api/users/${selected.value.id}/roles/${role.id}`,'DELETE');await showRoles(selected.value)})}
onMounted(()=>run(load));
</script>
<template><h2>本部门用户</h2><p role="alert" class="error">{{error}}</p>
 <table><thead><tr><th>用户名</th><th>姓名</th><th>手机号</th><th>操作</th></tr></thead><tbody><tr v-for="user in users" :key="user.id"><td>{{user.username}}</td><td>{{user.name}}</td><td>{{user.phone}}</td><td><button v-if="allowed('user.edit')" @click="edit(user)">修改资料</button><button v-if="allowed('user.role.assign')" @click="showRoles(user)">分配角色</button><button v-if="allowed('user.delete')" @click="remove(user)">删除</button></td></tr></tbody></table>
 <div class="pager"><button :disabled="page===0" @click="page--;run(load)">上一页</button><span>第 {{page+1}} 页 共 {{total}} 人</span><button :disabled="(page+1)*20>=total" @click="page++;run(load)">下一页</button></div>
 <section v-if="editing?allowed('user.edit'):allowed('user.create')"><h3>{{editing?'修改资料':'新建用户'}}</h3><form @submit.prevent="save"><label>用户名<input v-model.trim="form.username" :disabled="!!editing" required maxlength="64"></label><label>姓名<input v-model.trim="form.name" required maxlength="100"></label><label>手机号<input v-model="form.phone" required pattern="1[3-9][0-9]{9}"></label><button>保存</button><button type="button" @click="reset">取消</button></form></section>
 <section v-if="selected"><h3>{{selected.name}} 的角色</h3><ul><li v-for="role in assigned" :key="role.id">{{role.name}} <button @click="revoke(role)">撤销</button></li></ul><form @submit.prevent="assign"><label>角色<select v-model="roleId" required><option value="">请选择</option><option v-for="role in roles" :key="role.id" :value="role.id">{{role.name}}</option></select></label><button>分配</button><button type="button" @click="selected=null">关闭</button></form></section>
</template>
