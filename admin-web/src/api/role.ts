import http from '@/api/http'
import type { ApiResponse } from '@/types/api'

export interface RoleCard {
  id: number
  name: string
  code: string
  description: string | null
  userCount: number
  permissionTags: string[]
  protectedRole: boolean
}

export interface PermissionNode {
  id: number
  name: string
  code: string
  checked: boolean
  children: PermissionNode[]
}

export function fetchRoles() {
  return http.get<ApiResponse<RoleCard[]>>('/roles')
}

export function createRole(body: { name: string; code: string; description?: string }) {
  return http.post('/roles', body)
}

export function deleteRole(id: number) {
  return http.delete(`/roles/${id}`)
}

export function fetchPermissionTree(roleId: number) {
  return http.get<ApiResponse<PermissionNode[]>>('/permissions/tree', { params: { roleId } })
}

export function saveRolePermissions(id: number, permissionIds: number[]) {
  return http.put(`/roles/${id}/permissions`, { permissionIds })
}
