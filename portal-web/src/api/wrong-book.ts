import http from '@/api/http'
import type { ApiResponse, PageResult } from '@/types/api'
import type { PracticePaper } from '@/api/practice'

export interface WrongOption {
  key: string
  text: string
}

export interface WrongItem {
  id: number
  questionId: number
  type: number
  content: string
  options: WrongOption[]
  correctAnswer: string
  userAnswer: string
  analysis?: string | null
  errorCount: number
  correctedCount: number
  status: number
  lastWrongAt?: string | null
  subjectName?: string | null
  stageName?: string | null
}

export interface WrongTop {
  id: number
  questionId: number
  content: string
  errorCount: number
}

export interface WrongStats {
  total: number
  toReviewCount: number
  masteredCount: number
  masteryRate: number
  top3: WrongTop[]
}

export interface WrongQuery {
  status?: number
  type?: number
  days?: number
  sort?: 'recent' | 'count'
  page?: number
  pageSize?: number
}

export function fetchWrongStats() {
  return http.get<ApiResponse<WrongStats>>('/wrong-questions/stats')
}

export function fetchWrongQuestions(query: WrongQuery) {
  return http.get<ApiResponse<PageResult<WrongItem>>>('/wrong-questions', { params: query })
}

export function redoWrongQuestions(ids: number[]) {
  return http.post<ApiResponse<PracticePaper>>('/wrong-questions/redo-start', { ids })
}

export function markWrongMastered(id: number) {
  return http.post<ApiResponse<null>>(`/wrong-questions/${id}/mastered`)
}

export function removeWrongQuestion(id: number) {
  return http.delete<ApiResponse<null>>(`/wrong-questions/${id}`)
}
