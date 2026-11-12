import http from '@/api/http'
import type { ApiResponse } from '@/types/api'

export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  pageSize: number
}

export interface RoleOption {
  id: number
  name: string
  code: string
}

export interface AdminUserItem {
  id: number
  username: string
  mobile: string | null
  email: string | null
  roleId: number
  roleName: string
  status: number
  builtin: boolean
  lastLoginAt: string | null
  createdAt: string | null
}

export interface LogBrief {
  id: number
  action: string
  targetType: string
  targetName: string
  createdAt: string
}

export interface AdminUserDetail extends AdminUserItem {
  recentLogs: LogBrief[]
}

export function fetchRoleOptions() {
  return http.get<ApiResponse<RoleOption[]>>('/users/role-options')
}

export function fetchUsers(params: { keyword?: string; roleId?: number; status?: number; page: number; pageSize: number }) {
  return http.get<ApiResponse<PageResult<AdminUserItem>>>('/users', { params })
}

export function createUser(body: { username: string; mobile: string; email?: string; roleId: number; password: string; status: number }) {
  return http.post('/users', body)
}

export function updateUser(id: number, body: { username: string; mobile: string; email?: string; roleId: number; password?: string; status: number }) {
  return http.put(`/users/${id}`, body)
}

export function batchUserStatus(ids: number[], status: number) {
  return http.put('/users/batch-status', { ids, status })
}

export function deleteUser(id: number) {
  return http.delete(`/users/${id}`)
}

export function batchDeleteUsers(ids: number[]) {
  return http.delete('/users/batch', { data: { ids } })
}

export function resetUserPassword(id: number) {
  return http.post<ApiResponse<{ password: string }>>(`/users/${id}/reset-password`)
}

export function fetchUserDetail(id: number) {
  return http.get<ApiResponse<AdminUserDetail>>(`/users/${id}/detail`)
}
