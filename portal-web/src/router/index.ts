import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/store/user'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      component: () => import('@/layouts/MainLayout.vue'),
      children: [
        { path: '', name: 'home', component: () => import('@/views/home/HomeView.vue'), meta: { title: '首页' } },
        { path: 'routes', name: 'routes', component: () => import('@/views/routes/RoutesView.vue'), meta: { title: '学习路线', requiresAuth: true } },
        { path: 'questions', name: 'questions', component: () => import('@/views/questions/QuestionsView.vue'), meta: { title: '题库中心' } },
        { path: 'practice', name: 'practice', component: () => import('@/views/practice/PracticeView.vue'), meta: { title: '刷题练习', requiresAuth: true } },
        { path: 'wrong-book', name: 'wrong-book', component: () => import('@/views/wrong-book/WrongBookView.vue'), meta: { title: '错题本', requiresAuth: true } },
        { path: 'interview/config', name: 'interview-config', component: () => import('@/views/interview/ConfigView.vue'), meta: { title: 'AI 面试', requiresAuth: true } },
        { path: 'interview/session', name: 'interview-session', component: () => import('@/views/interview/InterviewView.vue'), meta: { title: '模拟面试', requiresAuth: true } },
        { path: 'interview/report', name: 'interview-report', component: () => import('@/views/interview/ReportView.vue'), meta: { title: '面试报告', requiresAuth: true } },
        { path: 'interview/history', name: 'interview-history', component: () => import('@/views/interview/HistoryView.vue'), meta: { title: '面试记录', requiresAuth: true } },
        { path: 'profile', name: 'profile', component: () => import('@/views/profile/ProfileView.vue'), meta: { title: '个人中心', requiresAuth: true } },
      ],
    },
    { path: '/login', name: 'login', component: () => import('@/views/auth/LoginView.vue'), meta: { title: '登录' } },
    { path: '/register', name: 'register', component: () => import('@/views/auth/RegisterView.vue'), meta: { title: '注册' } },
    { path: '/forgot-password', name: 'forgot-password', component: () => import('@/views/auth/ForgotPasswordView.vue'), meta: { title: '找回密码' } },
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
