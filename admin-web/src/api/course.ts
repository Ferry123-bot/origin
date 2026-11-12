import http from '@/api/http'
import type { ApiResponse } from '@/types/api'

export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  pageSize: number
}

export interface SubjectOption {
  id: number
  name: string
}

export interface CourseStats {
  courseCount: number
  totalHours: number
  subjectCount: number
  subjects: SubjectOption[]
}

export interface CourseSubject {
  id: number
  name: string
}

export interface CourseItem {
  id: number
  name: string
  coverUrl: string | null
  durationMinutes: number
  duration: string
  source: number
  bvid: string | null
  link: string | null
  subjectId: number | null
  otherSubject: boolean
  subjects: CourseSubject[]
}

export interface CourseForm {
  name: string
  coverUrl: string
  durationMinutes: number
  source: number
  bvid: string
  link: string
  subjectId: number | null
  subjectIds: number[]
}

export interface ImportRow {
  row: number
  name: string
  subjects: string
  source: string
  duration: string
  bvid: string
  status: 'ok' | 'warning' | 'error'
  messages: string[]
}

export interface ImportPreview {
  token: string | null
  total: number
  validCount: number
  warningCount: number
  invalidCount: number
  rows: ImportRow[]
}

export function fetchCourseStats() {
  return http.get<ApiResponse<CourseStats>>('/courses/stats')
}

export function fetchCourses(params: { keyword?: string; subjectId?: number; source?: number; page: number; pageSize: number }) {
  return http.get<ApiResponse<PageResult<CourseItem>>>('/courses', { params })
}

export function createCourse(form: CourseForm) {
  return http.post('/courses', form)
}

export function updateCourse(id: number, form: CourseForm) {
  return http.put(`/courses/${id}`, form)
}

export function deleteCourse(id: number) {
  return http.delete(`/courses/${id}`)
}

export function previewCourseImport(file: File) {
  const form = new FormData()
  form.append('file', file)
  return http.post<ApiResponse<ImportPreview>>('/courses/import/preview', form, { timeout: 60000 })
}

export function confirmCourseImport(token: string) {
  return http.post<ApiResponse<{ imported: number }>>('/courses/import/confirm', { token }, { timeout: 60000 })
}

export async function downloadCourseTemplate() {
  const response = await http.get('/courses/import/template', { responseType: 'blob', timeout: 60000 })
  const url = URL.createObjectURL(response.data as Blob)
  const link = document.createElement('a')
  link.href = url
  link.download = '课程导入模板.xlsx'
  link.click()
  URL.revokeObjectURL(url)
}
