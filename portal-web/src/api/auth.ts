import http from '@/api/http'
import type { ApiResponse } from '@/types/api'
import { useUserStore } from '@/store/user'

export interface PortalSession {
  accessToken: string
  refreshToken: string
  expiresIn: number
  nickname: string
}

export interface SmsResult {
  expireSeconds: number
  debugCode?: string | null
}

export function sendSms(mobile: string, type: 'register' | 'login' | 'reset') {
  return http.post<ApiResponse<SmsResult>>('/auth/sms-code', { mobile, type })
}

export function register(mobile: string, code: string, password: string) {
  return http.post<ApiResponse<PortalSession>>('/auth/register', { mobile, code, password })
}

export function loginByPassword(account: string, password: string) {
  return http.post<ApiResponse<PortalSession>>('/auth/login/password', { account, password })
}

export function loginBySms(mobile: string, code: string) {
  return http.post<ApiResponse<PortalSession>>('/auth/login/sms', { mobile, code })
}

export function verifyReset(mobile: string, code: string) {
  return http.post<ApiResponse<{ resetToken: string }>>('/auth/reset/verify', { mobile, code })
}

export function resetPassword(resetToken: string, newPassword: string) {
  return http.post<ApiResponse<null>>('/auth/reset', { resetToken, newPassword })
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

export function saveSession(data: PortalSession, remember = true) {
  useUserStore().setSession(data.accessToken, data.refreshToken, data.nickname, remember)
}
