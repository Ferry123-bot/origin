import http from '@/api/http'
import type { ApiResponse } from '@/types/api'

export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  pageSize: number
}

export interface QuestionStats {
  total: number
  published: number
  pending: number
  single: number
  multiple: number
  judge: number
  shortAnswer: number
}

export interface QuestionItem {
  id: number
  content: string
  stageId: number
  stageName: string
  subjectName: string
  type: number
  difficulty: number
  status: number
  createdAt: string
  doneCount: number
  accuracyRate: number | null
}

export interface QuestionOption {
  key: string
  text: string
  correct: boolean
}

export interface QuestionDetail extends QuestionItem {
  answer: string | null
  analysis: string | null
  tags: string | null
  rejectReason: string | null
  options: QuestionOption[]
  correctCount: number
}

export interface StageOption {
  id: number
  name: string
  subjectId: number
  subjectName: string
}

export interface QuestionForm {
  content: string
  stageId: number | null
  type: number
  difficulty: number
  tags: string
  analysis: string
  answer: string
  options: QuestionOption[]
}

export interface ImportRow {
  sheet: string
  row: number
  content: string
  type: string
  stage: string
  status: string
  messages: string[]
}

export interface ImportPreview {
  token: string | null
  total: number
  valid: number
  invalid: number
  rows: ImportRow[]
}

export interface QuestionQuery {
  keyword?: string
  subjectId?: number
  stageId?: number
  type?: number
  difficulty?: number
  status?: number
  page: number
  pageSize: number
}

export function fetchQuestionStats() {
  return http.get<ApiResponse<QuestionStats>>('/questions/stats')
}

export function fetchQuestions(params: QuestionQuery) {
  return http.get<ApiResponse<PageResult<QuestionItem>>>('/questions', { params })
}

export function fetchQuestion(id: number) {
  return http.get<ApiResponse<QuestionDetail>>(`/questions/${id}`)
}

export function fetchQuestionStages() {
  return http.get<ApiResponse<StageOption[]>>('/questions/stages')
}

export function createQuestion(form: QuestionForm) {
  return http.post('/questions', form)
}

export function updateQuestion(id: number, form: QuestionForm) {
  return http.put(`/questions/${id}`, form)
}

export function deleteQuestion(id: number) {
  return http.delete(`/questions/${id}`)
}

export function auditQuestion(id: number, pass: boolean, rejectReason?: string) {
  return http.post(`/questions/${id}/audit`, { pass, rejectReason })
}

export function changeQuestionStatus(id: number, action: 'on' | 'off') {
  return http.post(`/questions/${id}/status`, { action })
}

export function resubmitQuestion(id: number) {
  return http.post(`/questions/${id}/resubmit`)
}

export function batchQuestionStatus(ids: number[], action: 'on' | 'off') {
  return http.post<ApiResponse<{ affected: number; skipped: number }>>('/questions/batch-status', { ids, action })
}

export function batchDeleteQuestions(ids: number[]) {
  return http.post<ApiResponse<{ affected: number; skipped: number }>>('/questions/batch-delete', { ids })
}

export function batchPassQuestions(ids: number[]) {
  return http.post<ApiResponse<{ affected: number; skipped: number }>>('/questions/batch-pass', { ids })
}

export function previewQuestionImport(file: File) {
  const form = new FormData()
  form.append('file', file)
  return http.post<ApiResponse<ImportPreview>>('/questions/import/preview', form, { timeout: 60000 })
}

export function confirmQuestionImport(token: string) {
  return http.post<ApiResponse<{ imported: number }>>('/questions/import/confirm', { token }, { timeout: 60000 })
}

async function saveBlob(path: string, filename: string, params?: Record<string, unknown>) {
  const response = await http.get(path, { params, responseType: 'blob', timeout: 60000 })
  const blob = response.data as Blob
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}

export function downloadQuestionTemplate() {
  return saveBlob('/questions/import/template', '题库导入模板.xlsx')
}

export function exportQuestions(params: Omit<QuestionQuery, 'page' | 'pageSize'>) {
  return saveBlob('/questions/export', '题库导出.xlsx', params)
}
