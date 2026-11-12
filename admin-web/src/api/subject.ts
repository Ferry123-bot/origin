import http from '@/api/http'
import type { ApiResponse } from '@/types/api'

export interface SubjectStats {
  subjectCount: number
  subjectsWithStages: number
  stageCount: number
  questionCount: number
}

export interface AdminStage {
  id: number
  name: string
  icon: string | null
  sortOrder: number
  status: number
  questionCount: number
}

export interface AdminSubject {
  id: number
  name: string
  icon: string
  description: string
  themeColor: string
  sortOrder: number
  courseCount: number
  routeCount: number
  questionCount: number
  stages: AdminStage[]
}

export interface SubjectForm {
  name: string
  icon: string
  description: string
  sortOrder: number
  themeColor: string
}

export interface StageForm {
  name: string
  icon: string
  sortOrder: number
}

export function fetchSubjectStats() {
  return http.get<ApiResponse<SubjectStats>>('/subjects/stats')
}

export function fetchSubjects() {
  return http.get<ApiResponse<AdminSubject[]>>('/subjects')
}

export function createSubject(form: SubjectForm) {
  return http.post('/subjects', form)
}

export function updateSubject(id: number, form: SubjectForm) {
  return http.put(`/subjects/${id}`, form)
}

export function sortSubject(id: number, move: 'up' | 'down') {
  return http.put('/subjects/sort', { id, move })
}

export function deleteSubject(id: number) {
  return http.delete(`/subjects/${id}`)
}

export function createStage(subjectId: number, form: StageForm) {
  return http.post(`/subjects/${subjectId}/stages`, form)
}

export function updateStage(subjectId: number, id: number, form: StageForm) {
  return http.put(`/subjects/${subjectId}/stages/${id}`, form)
}

export function sortStage(subjectId: number, id: number, move: 'up' | 'down') {
  return http.put(`/subjects/${subjectId}/stages/sort`, { id, move })
}

export function updateStageStatus(subjectId: number, id: number, status: number) {
  return http.put(`/subjects/${subjectId}/stages/${id}/status`, { status })
}

export function deleteStage(subjectId: number, id: number) {
  return http.delete(`/subjects/${subjectId}/stages/${id}`)
}
