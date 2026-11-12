import http from '@/api/http'
import type { ApiResponse } from '@/types/api'
import { useUserStore } from '@/store/user'

export interface AdminSession {
  accessToken: string
  refreshToken: string
  expiresIn: number
  username: string
  roleCode: string
  permissions: string[]
}

export function login(account: string, password: string) {
  return http.post<ApiResponse<AdminSession>>('/auth/login', { account, password })
}

export function saveSession(data: AdminSession, remember = true) {
  useUserStore().setSession(data.accessToken, data.refreshToken, data.username, data.roleCode, data.permissions ?? [], remember)
}

export async function logout() {
  const user = useUserStore()
  try {
    await http.post('/auth/logout', null, {
      headers: { 'X-Refresh-Token': user.refreshToken },
    })
  } catch {
    // 接口失败时仍清除本地登录态
  } finally {
    user.clear()
  }
}
