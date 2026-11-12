import http from '@/api/http'
import type { ApiResponse } from '@/types/api'
import type { PageResult } from '@/api/user'

export interface OperationLog {
  id: number
  username: string
  action: string
  targetType: string
  targetName: string
  ip: string
  createdAt: string
}

export function fetchLogs(params: { keyword?: string; operationType?: string; startDate?: string; endDate?: string; page: number; pageSize: number }) {
  return http.get<ApiResponse<PageResult<OperationLog>>>('/logs', { params })
}

export function fetchLog(id: number) {
  return http.get<ApiResponse<OperationLog>>(`/logs/${id}`)
}

export function exportLogs(params: { keyword?: string; operationType?: string; startDate?: string; endDate?: string }) {
  return http.get<Blob>('/logs/export', { params, responseType: 'blob' })
}

export function clearLogs() {
  return http.delete('/logs')
}
