<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { logout } from '@/api/auth'
import { fetchProfile } from '@/api/profile'
import { useUserStore } from '@/store/user'

const route = useRoute()
const router = useRouter()
const user = useUserStore()
const avatarBroken = ref(false)
const isHome = computed(() => route.name === 'home')
const wide = computed(() => route.name === 'home' || route.name === 'profile' || route.name === 'questions' || route.name === 'practice' || route.name === 'wrong-book' || route.name === 'interview-config' || route.name === 'interview-session')
const showFooter = computed(() => route.name !== 'home' && route.name !== 'practice' && route.name !== 'interview-session')
const initial = computed(() => {
  const name = user.nickname.trim()
  return name ? name.slice(0, 1) : '学'
})
const showAvatar = computed(() => Boolean(user.avatar) && !avatarBroken.value)

watch(() => user.avatar, () => {
  avatarBroken.value = false
})

watch(() => user.token, (token, previous) => {
  if (token && !previous) void refreshAccount()
}, { immediate: true })

async function refreshAccount() {
  try {
    const response = await fetchProfile()
    user.setNickname(response.data.data.nickname)
    user.setAvatar(response.data.data.avatar || '')
  } catch {
    // 顶栏保留本地已有的昵称和头像
  }
}

async function onLogout() {
  await logout()
  router.push({ name: 'home' })
}

function openAdmin() {
  window.location.href = 'http://localhost:5174/admin/'
}
</script>

<template>
  <div class="layout">
    <header class="nav">
      <div class="nav-inner">
        <router-link class="brand" to="/">
          <span class="brand-mark" aria-hidden="true"></span>
          <span>云枢智学</span>
        </router-link>
        <nav>
          <router-link to="/" :class="{ on: route.name === 'home' }">首页</router-link>
          <router-link to="/routes" :class="{ on: route.name === 'routes' }">学习路线</router-link>
          <router-link to="/questions" :class="{ on: route.name === 'questions' }">题库</router-link>
          <router-link to="/interview/config" :class="{ on: String(route.name).startsWith('interview') }">AI面试</router-link>
        </nav>
        <div class="account">
          <el-dropdown v-if="user.token" trigger="hover">
            <span class="user">
              <span class="avatar">
                <img v-if="showAvatar" :src="user.avatar" alt="" @error="avatarBroken = true" />
                <template v-else>{{ initial }}</template>
              </span>
              <span class="user-name">{{ user.nickname || '学员' }}</span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="router.push({ name: 'profile' })">个人中心</el-dropdown-item>
                <el-dropdown-item @click="router.push({ name: 'wrong-book' })">我的错题</el-dropdown-item>
                <el-dropdown-item @click="router.push({ name: 'interview-history' })">面试记录</el-dropdown-item>
                <el-dropdown-item v-if="user.role === 'admin'" @click="openAdmin">管理后台</el-dropdown-item>
                <el-dropdown-item divided @click="onLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <router-link v-else class="login" to="/login">登录</router-link>
        </div>
      </div>
    </header>
    <main class="main" :class="{ 'main-home': wide }">
      <router-view />
    </main>
    <footer v-if="showFooter" class="footer">
      <span>云枢智学</span>
      <span v-if="route.name" class="path">{{ route.meta.title }}</span>
    </footer>
  </div>
</template>

<style scoped>
.layout { min-height: 100vh; display: flex; flex-direction: column; }
.nav {
  position: sticky;
  top: 0;
  z-index: 1000;
  height: 64px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}
.nav-inner {
  height: 100%;
  width: min(1200px, calc(100% - 40px));
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: none;
  font-size: 20px;
  font-weight: 700;
  white-space: nowrap;
  color: #303133;
}
.brand .brand-mark { font-size: 28px; }
nav { display: flex; gap: 8px; }
nav a {
  padding: 8px 16px;
  border-radius: 8px;
  color: #606266;
  font-weight: 500;
}
nav a.on { color: #409eff; background: rgba(64, 158, 255, 0.1); }
.account { display: flex; align-items: center; }
.user {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 12px;
  border-radius: 20px;
  cursor: pointer;
  outline: none;
}
.user:hover { background: #f5f7fa; }
.avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 600;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.avatar img { width: 100%; height: 100%; object-fit: cover; }
.user-name { font-weight: 500; }
.login { color: #409eff; font-weight: 600; }
.main { flex: 1; width: min(1120px, calc(100% - 48px)); margin: 24px auto; }
.main-home { width: 100%; margin: 0; }
.footer { height: 56px; display: flex; align-items: center; gap: 20px; padding: 0 24px; color: #909399; background: #fff; border-top: 1px solid #ebeef5; }
.path { margin-left: auto; }
@media (max-width: 768px) {
  .nav-inner { width: 100%; padding: 0 12px; gap: 8px; overflow-x: auto; }
  .brand { font-size: 16px; }
  .brand .brand-mark { font-size: 22px; }
  nav { flex: none; gap: 2px; }
  nav a, .login, .user { flex: none; white-space: nowrap; }
  nav a { padding: 6px 8px; font-size: 13px; }
  .user-name { display: none; }
}
</style>
