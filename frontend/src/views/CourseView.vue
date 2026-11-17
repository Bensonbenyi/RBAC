<script setup lang="ts">
import { ref, onMounted, watch, computed } from 'vue';
import { allowed, request, auth } from '../auth';
const statusNames: Record<number, string> = { 1: '申请', 2: '待审核', 3: '已开课' };
interface Term {
  id: number;
  name: string;
  startDate: string;
  endDate: string;
  current: boolean;
}
interface Course {
  description?: string;
  id: number;
  name: string;
  code: string;
  courseStatus: number;
}
const detail = ref<Course | null>(null);
async function showDetail(course: Course) {
  await run(async () => {
    detail.value = await request<Course>(`/course-api/courses/${course.id}`);
  });
}
const tab = ref(''),
  terms = ref<Term[]>([]),
  courses = ref<Course[]>([]),
  editing = ref<Term | null>(null),
  name = ref(''),
  startDate = ref(''),
  endDate = ref(''),
  error = ref(''),
  comment = ref(''),
  page = ref(0),
  total = ref(0);
async function run(action: () => Promise<void>) {
  error.value = '';
  try {
    await action();
  } catch (e) {
    error.value = (e as Error).message;
  }
}
async function loadTerms() {
  terms.value = (await request<{ items: Term[] }>('/course-api/terms')).items;
}
async function loadCourses() {
  const data = await request<{ items: Course[]; total: number }>(
    `/course-api/admin/courses?page=${page.value + 1}&pageSize=20`,
  );
  courses.value = data.items;
  total.value = data.total;
}
function edit(term: Term) {
  editing.value = term;
  name.value = term.name;
  startDate.value = term.startDate;
  endDate.value = term.endDate;
}
function reset() {
  editing.value = null;
  name.value = '';
  startDate.value = '';
  endDate.value = '';
}
async function save() {
  if (!allowed(editing.value ? 'MTRE' : 'MTRC')) return;
  await run(async () => {
    await request(
      editing.value ? `/course-api/terms/${editing.value.id}` : '/course-api/terms',
      editing.value ? 'PATCH' : 'POST',
      { name: name.value, startDate: startDate.value, endDate: endDate.value },
    );
    reset();
    await loadTerms();
  });
}
async function remove(term: Term) {
  if (!allowed('MTRD')) return;
  if (confirm(`删除学期 ${term.name}？`))
    await run(async () => {
      await request(`/course-api/terms/${term.id}`, 'DELETE');
      await loadTerms();
    });
}
async function review(course: Course, decision: string) {
  if (!allowed('MCOR')) return;
  await run(async () => {
    await request(`/course-api/courses/${course.id}/review`, 'POST', {
      decision,
      comment: comment.value || null,
    });
    comment.value = '';
    await loadCourses();
  });
}
const showManagement = computed(() => allowed('MCO') || allowed('MTR'));
function choose(next: string) {
  if (!allowed(next === 'terms' ? 'MTR' : 'MCO')) return;
  tab.value = next;
  void run(next === 'terms' ? loadTerms : loadCourses);
}
watch(
  () => auth.me?.permissions.slice(),
  () => {
    if (tab.value && !allowed(tab.value === 'terms' ? 'MTR' : 'MCO')) tab.value = '';
  },
);
onMounted(() => {
  if (allowed('MTR')) choose('terms');
  else if (allowed('MCO')) choose('courses');
});
</script>
<template>
  <nav>
    <template v-if="showManagement"
      ><button :disabled="!allowed('MTR')" @click="choose('terms')">学期维护</button
      ><button :disabled="!allowed('MCO')" @click="choose('courses')">课程审核</button></template
    ><a href="/platform/" target="_blank">权限管理后台</a>
  </nav>
  <p v-if="!tab">请选择可用的管理菜单。</p>
  <p role="alert" class="error">{{ error }}</p>
  <template v-if="tab === 'terms'"
    ><h2>学期维护</h2>
    <table>
      <thead>
        <tr>
          <th>学期</th>
          <th>开始日期</th>
          <th>结束日期</th>
          <th>当前学期</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="term in terms" :key="term.id">
          <td>{{ term.name }}</td>
          <td>{{ term.startDate }}</td>
          <td>{{ term.endDate }}</td>
          <td>{{ term.current ? '是' : '否' }}</td>
          <td>
            <button :disabled="term.current || !allowed('MTRE')" @click="edit(term)">修改</button
            ><button :disabled="term.current || !allowed('MTRD')" @click="remove(term)">
              删除
            </button>
          </td>
        </tr>
      </tbody>
    </table>
    <section>
      <h3>{{ editing ? '修改学期' : '新建学期' }}</h3>
      <form @submit.prevent="save">
        <label>名称<input v-model.trim="name" required maxlength="100" /></label
        ><label>开始日期<input v-model="startDate" type="date" required /></label
        ><label>结束日期<input v-model="endDate" type="date" required :min="startDate" /></label
        ><button :disabled="!allowed(editing ? 'MTRE' : 'MTRC')">保存</button
        ><button type="button" @click="reset">取消</button>
      </form>
    </section></template
  >
  <template v-else-if="tab === 'courses'"
    ><h2>课程审核</h2>
    <label>审核意见<input v-model="comment" maxlength="500" placeholder="驳回时必填" /></label>
    <table>
      <thead>
        <tr>
          <th>代码</th>
          <th>课程名称</th>
          <th>状态</th>
          <th>审核</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="course in courses" :key="course.id">
          <td>{{ course.code }}</td>
          <td>
            <button :disabled="!allowed('MCO')" @click="showDetail(course)">
              {{ course.name }}
            </button>
          </td>
          <td>{{ statusNames[course.courseStatus] }}</td>
          <td>
            <template v-if="course.courseStatus === 2"
              ><button :disabled="!allowed('MCOR')" @click="review(course, 'approved')">通过</button
              ><button
                :disabled="!allowed('MCOR') || !comment.trim()"
                @click="review(course, 'rejected')"
              >
                驳回
              </button></template
            >
          </td>
        </tr>
      </tbody>
    </table>
    <div class="pager">
      <button
        :disabled="page === 0"
        @click="
          page--;
          run(loadCourses);
        "
      >
        上一页</button
      ><span>第 {{ page + 1 }} 页 共 {{ total }} 门</span
      ><button
        :disabled="(page + 1) * 20 >= total"
        @click="
          page++;
          run(loadCourses);
        "
      >
        下一页
      </button>
    </div></template
  >
  <section v-if="detail && tab === 'courses'">
    <h3>课程详情</h3>
    <dl>
      <dt>代码</dt>
      <dd>{{ detail.code }}</dd>
      <dt>名称</dt>
      <dd>{{ detail.name }}</dd>
      <dt>说明</dt>
      <dd>{{ detail.description || '无' }}</dd>
    </dl>
    <button @click="detail = null">关闭</button>
  </section>
</template>
