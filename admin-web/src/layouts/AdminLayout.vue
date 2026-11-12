<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { logout } from '@/api/auth'
import { fetchSettings } from '@/api/setting'
import { useUserStore } from '@/store/user'
import { usePermissionStore } from '@/store/permission'

const route = useRoute()
const router = useRouter()
const user = useUserStore()
const permission = usePermissionStore()
const collapsed = ref(false)
const menuOpen = ref(false)
const noticeOpen = ref(false)
const root = ref<HTMLElement | null>(null)
const openGroups = ref<string[]>([])

function groupActive(group: { children?: { path?: string }[] }) {
  return group.children?.some((item) => item.path === route.path) ?? false
}

function toggleGroup(title: string) {
  if (collapsed.value) {
    collapsed.value = false
    if (!openGroups.value.includes(title)) {
      openGroups.value = [...openGroups.value, title]
    }
    return
  }
  openGroups.value = openGroups.value.includes(title)
    ? openGroups.value.filter((item) => item !== title)
    : [...openGroups.value, title]
}

watch(
  () => route.path,
  () => {
    const current = permission.visibleMenus.find((group) => groupActive(group))
    if (current && !openGroups.value.includes(current.title)) {
      openGroups.value = [...openGroups.value, current.title]
    }
  },
  { immediate: true },
)

const avatarText = computed(() => (user.username || '管').slice(0, 1).toUpperCase())
const siteName = ref('云枢智学')
const logoUrl = ref('')

function toggleSidebar() {
  collapsed.value = !collapsed.value
}

function closePopovers() {
  menuOpen.value = false
  noticeOpen.value = false
}

function toggleNotice() {
  noticeOpen.value = !noticeOpen.value
  menuOpen.value = false
}

function toggleUser() {
  menuOpen.value = !menuOpen.value
  noticeOpen.value = false
}

function openPortal() {
  window.location.href = 'http://localhost:5173/'
}

async function onLogout() {
  closePopovers()
  try {
    await ElMessageBox.confirm('确定退出登录？', '退出登录', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  await logout()
  router.push({ name: 'login' })
}

function onDocumentClick(event: MouseEvent) {
  if (!root.value?.contains(event.target as Node)) {
    return
  }
  const target = event.target as HTMLElement
  if (!target.closest('.popover')) {
    closePopovers()
  }
}

onMounted(async () => {
  document.addEventListener('click', onDocumentClick)
  try {
    const response = await fetchSettings()
    siteName.value = response.data.data.siteName || '云枢智学'
    logoUrl.value = response.data.data.logoUrl || ''
  } catch {
    siteName.value = '云枢智学'
  }
})
onBeforeUnmount(() => document.removeEventListener('click', onDocumentClick))

watch([() => route.meta.title, siteName], () => {
  document.title = `${route.meta.title || '管理后台'} - ${siteName.value}`
}, { immediate: true })
</script>

<template>
  <div ref="root" class="admin" :class="{ collapsed }">
    <aside class="sidebar">
      <div class="sidebar-header">
        <router-link class="logo" to="/dashboard">
          <img v-if="logoUrl" class="logo-image" :src="logoUrl" alt="" />
          <span v-else class="logo-icon brand-mark" aria-hidden="true" />
          <span class="logo-text">{{ siteName }}</span>
        </router-link>
      </div>
      <nav class="sidebar-nav">
        <div v-for="group in permission.visibleMenus" :key="group.title" class="nav-group">
          <button
            type="button"
            class="nav-parent"
            :class="{ open: openGroups.includes(group.title), active: groupActive(group) }"
            :title="group.title"
            @click="toggleGroup(group.title)"
          >
            <span class="icon">{{ group.icon }}</span>
            <span class="label">{{ group.title }}</span>
            <span class="chevron" :class="{ open: openGroups.includes(group.title) }">›</span>
          </button>
          <div v-show="openGroups.includes(group.title)" class="nav-children">
            <router-link
              v-for="item in group.children"
              :key="item.path"
              class="nav-item"
              :class="{ active: route.path === item.path }"
              :to="item.path || '/dashboard'"
              :title="item.title"
            >
              <span class="icon">{{ item.icon }}</span>
              <span class="label">{{ item.title }}</span>
            </router-link>
          </div>
        </div>
      </nav>
    </aside>

    <div class="main">
      <header class="header">
        <div class="header-left">
          <button class="menu-toggle" type="button" aria-label="折叠菜单" @click="toggleSidebar">☰</button>
          <h1 class="page-title">{{ route.meta.title }}</h1>
        </div>
        <div class="header-right">
          <div class="popover">
            <button class="notice-btn" type="button" aria-label="通知" @click.stop="toggleNotice">
              <span>🔔</span>
            </button>
            <div v-if="noticeOpen" class="dropdown notice">暂无通知</div>
          </div>
          <div class="popover">
            <button class="user" type="button" @click.stop="toggleUser">
              <span class="avatar">{{ avatarText }}</span>
              <span class="name">{{ user.username || '管理员' }}</span>
              <span class="arrow" :class="{ open: menuOpen }">▾</span>
            </button>
            <div v-if="menuOpen" class="dropdown">
              <button type="button" @click="openPortal">🏠 返回前台</button>
              <div class="divider" />
              <button class="danger" type="button" @click="onLogout">🚪 退出登录</button>
            </div>
          </div>
        </div>
      </header>
      <div class="content">
        <router-view />
      </div>
    </div>
  </div>
</template>

<style scoped>
.admin {
  min-height: 100vh;
  background: #f5f7fa;
}

.sidebar {
  width: 240px;
  background: #1d1e2c;
  color: #fff;
  display: flex;
  flex-direction: column;
  position: fixed;
  top: 0;
  left: 0;
  bottom: 0;
  z-index: 100;
  transition: width 0.3s ease;
}

.sidebar-header {
  padding: 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #fff;
  text-decoration: none;
}

.logo-icon {
  font-size: 24px;
}

.logo-image {
  width: 28px;
  height: 28px;
  object-fit: cover;
  border-radius: 6px;
}

.logo-text {
  font-size: 18px;
  font-weight: 700;
  white-space: nowrap;
}

.sidebar-nav {
  flex: 1;
  padding: 16px 12px;
  overflow-y: auto;
}

.nav-group {
  margin-bottom: 8px;
}

.nav-parent {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 12px 16px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: rgba(255, 255, 255, 0.85);
  font-size: 14px;
  cursor: pointer;
}

.nav-parent:hover,
.nav-parent.active {
  background: rgba(255, 255, 255, 0.08);
  color: #fff;
}

.chevron {
  margin-left: auto;
  font-size: 10px;
  color: rgba(255, 255, 255, 0.45);
  transition: transform 0.2s ease;
}

.chevron.open {
  transform: rotate(90deg);
}

.nav-children {
  margin: 4px 0 8px;
  padding-left: 16px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  color: rgba(255, 255, 255, 0.7);
  text-decoration: none;
  border-radius: 8px;
  margin-bottom: 4px;
  transition: background 0.2s ease, color 0.2s ease;
}

.nav-item:hover {
  background: rgba(255, 255, 255, 0.1);
  color: #fff;
}

.nav-item.active {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
}

.icon {
  width: 24px;
  text-align: center;
  font-size: 18px;
  flex: none;
}

.label {
  white-space: nowrap;
}

.main {
  margin-left: 240px;
  min-height: 100vh;
  transition: margin-left 0.3s ease;
}

.header {
  height: 64px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  position: sticky;
  top: 0;
  z-index: 50;
}

.header-left,
.header-right,
.user {
  display: flex;
  align-items: center;
}

.header-left {
  gap: 16px;
}

.header-right {
  gap: 20px;
}

.menu-toggle,
.notice-btn,
.user {
  border: 0;
  background: transparent;
  cursor: pointer;
}

.menu-toggle {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  font-size: 20px;
}

.menu-toggle:hover,
.notice-btn:hover {
  background: #f5f7fa;
}

.page-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.notice-btn {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  font-size: 18px;
}

.user {
  gap: 10px;
  color: #303133;
  font-size: 14px;
}

.avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  color: #fff;
  font-weight: 600;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.arrow {
  font-size: 10px;
  color: #909399;
  transition: transform 0.2s;
}

.arrow.open {
  transform: rotate(180deg);
}

.popover {
  position: relative;
}

.dropdown {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  width: 180px;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 10px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  z-index: 200;
  overflow: hidden;
}

.dropdown button {
  width: 100%;
  border: 0;
  background: transparent;
  text-align: left;
  padding: 12px 16px;
  font-size: 14px;
  color: #606266;
  cursor: pointer;
}

.dropdown button:hover {
  background: #f5f7fa;
}

.dropdown .danger {
  color: #f56c6c;
}

.notice {
  width: 220px;
  padding: 16px;
  color: #909399;
  font-size: 14px;
}

.divider {
  height: 1px;
  background: #e4e7ed;
}

.content {
  padding: 24px;
}

.collapsed .sidebar {
  width: 64px;
}

.collapsed .main {
  margin-left: 64px;
}

.collapsed .logo-text,
.collapsed .label,
.collapsed .chevron,
.collapsed .nav-children {
  display: none;
}

.collapsed .sidebar-header {
  padding: 20px 0;
  display: flex;
  justify-content: center;
}

.collapsed .nav-parent,
.collapsed .nav-item {
  justify-content: center;
  padding: 12px 0;
}
</style>
