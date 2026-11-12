import { defineStore } from 'pinia'
import { computed } from 'vue'
import { useUserStore } from '@/store/user'

export interface MenuItem {
  title: string
  path?: string
  permission?: string
  icon?: string
  children?: MenuItem[]
}

const menus: MenuItem[] = [
  {
    title: '概览',
    icon: '📊',
    children: [{ title: '数据看板', path: '/dashboard', permission: 'dashboard:view', icon: '📊' }],
  },
  {
    title: '内容管理',
    icon: '📁',
    children: [
      { title: '学科管理', path: '/subjects', permission: 'subject:view', icon: '🎓' },
      { title: 'B站课程库', path: '/courses', permission: 'course:view', icon: '🎬' },
      { title: '题库管理', path: '/questions', permission: 'question:view', icon: '📚' },
      { title: '面试题库管理', path: '/interview-bank', permission: 'interviewbank:view', icon: '🗂️' },
      { title: '学习路线', path: '/routes', permission: 'route:view', icon: '🗺️' },
    ],
  },
  {
    title: '学员管理',
    icon: '🎓',
    children: [{ title: '学员管理', path: '/students', permission: 'student:view', icon: '🎓' }],
  },
  {
    title: '系统管理',
    icon: '⚙️',
    children: [
      { title: '用户管理', path: '/users', permission: 'user:view', icon: '👥' },
      { title: '角色管理', path: '/roles', icon: '🔐' },
      { title: '操作日志', path: '/logs', permission: 'system:log_view', icon: '📝' },
      { title: '系统设置', path: '/settings', permission: 'system:setting', icon: '⚙️' },
    ],
  },
]

export const usePermissionStore = defineStore('permission', () => {
  const user = useUserStore()
  const visibleMenus = computed(() => filterMenus(menus, user.permissions, user.roleCode))
  return { menus, visibleMenus }
})

function filterMenus(items: MenuItem[], permissions: string[], roleCode: string): MenuItem[] {
  return items.flatMap((item) => {
    if (item.children) {
      const children = filterMenus(item.children, permissions, roleCode)
      return children.length ? [{ ...item, children }] : []
    }
    if (!item.permission) {
      return roleCode === 'super_admin' ? [item] : []
    }
    return permissions.includes(item.permission) ? [item] : []
  })
}
