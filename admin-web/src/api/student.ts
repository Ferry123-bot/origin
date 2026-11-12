import http from '@/api/http'
import type { ApiResponse } from '@/types/api'

export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  pageSize: number
}

export interface StudentItem {
  id: number
  nickname: string
  avatar: string | null
  mobile: string
  createdAt: string
  studyDays: number
  practiceCount: number
  interviewCount: number
  status: number
}

export interface StudentDetail {
  id: number
  nickname: string
  avatar: string | null
  mobile: string
  email: string | null
  status: number
  createdAt: string
  lastLoginAt: string | null
  studyDays: number
  courseCount: number
  practiceCount: number
  accuracy: number
  interviewCount: number
}

export function fetchStudents(params: { keyword?: string; status?: number; page: number; pageSize: number }) {
  return http.get<ApiResponse<PageResult<StudentItem>>>('/students', { params })
}

export function fetchStudentDetail(id: number) {
  return http.get<ApiResponse<StudentDetail>>(`/students/${id}/detail`)
}

export function changeStudentStatus(id: number, status: number) {
  return http.put(`/students/${id}/status`, { status })
}

export function batchStudentStatus(ids: number[], status: number) {
  return http.put('/students/batch-status', { ids, status })
}

export function deleteStudent(id: number) {
  return http.delete(`/students/${id}`)
}

export function batchDeleteStudents(ids: number[]) {
  return http.delete('/students/batch', { data: { ids } })
}
