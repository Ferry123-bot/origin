import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/store/user'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/login', name: 'login', component: () => import('@/views/login/LoginView.vue'), meta: { title: '登录' } },
    {
      path: '/',
      component: () => import('@/layouts/AdminLayout.vue'),
      redirect: '/dashboard',
      children: [
        { path: 'dashboard', name: 'dashboard', component: () => import('@/views/dashboard/DashboardView.vue'), meta: { title: '数据看板', requiresAuth: true } },
        { path: 'subjects', name: 'subjects', component: () => import('@/views/content/SubjectsView.vue'), meta: { title: '学科管理', requiresAuth: true } },
        { path: 'courses', name: 'courses', component: () => import('@/views/content/CoursesView.vue'), meta: { title: 'B站课程库', requiresAuth: true } },
        { path: 'questions', name: 'questions', component: () => import('@/views/content/QuestionsView.vue'), meta: { title: '题库管理', requiresAuth: true } },
        { path: 'interview-bank', name: 'interview-bank', component: () => import('@/views/content/InterviewBankView.vue'), meta: { title: '面试题库管理', requiresAuth: true } },
        { path: 'routes', name: 'routes', component: () => import('@/views/content/RoutesView.vue'), meta: { title: '学习路线', requiresAuth: true } },
        { path: 'students', name: 'students', component: () => import('@/views/student/StudentsView.vue'), meta: { title: '学员管理', requiresAuth: true } },
        { path: 'users', name: 'users', component: () => import('@/views/system/UsersView.vue'), meta: { title: '用户管理', requiresAuth: true } },
        { path: 'roles', name: 'roles', component: () => import('@/views/system/RolesView.vue'), meta: { title: '角色管理', requiresAuth: true } },
        { path: 'logs', name: 'logs', component: () => import('@/views/system/LogsView.vue'), meta: { title: '操作日志', requiresAuth: true } },
        { path: 'settings', name: 'settings', component: () => import('@/views/system/SettingsView.vue'), meta: { title: '系统设置', requiresAuth: true } },
      ],
    },
  ],
})

router.beforeEach((to) => {
  const user = useUserStore()
  if (to.meta.requiresAuth && !user.token) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router
