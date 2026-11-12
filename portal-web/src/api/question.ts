import http from '@/api/http'
import type { ApiResponse, PageResult } from '@/types/api'

export interface QuestionItem {
  id: number
  type: number
  difficulty: number
  content: string
  subjectName: string
  stageName: string
  tags: string | null
  doneCount: number
  accuracyRate: number
}

export interface QuestionOption {
  key: string
  text: string
}

export interface QuestionDetail {
  id: number
  type: number
  difficulty: number
  content: string
  subjectName: string
  stageName: string
  tags: string | null
  options: QuestionOption[]
  answer?: string
  analysis?: string
  inWrongBook?: boolean
}

export interface QuestionQuery {
  subjectId?: number
  stageId?: number
  type?: number
  difficulty?: number
  keyword?: string
  page?: number
  pageSize?: number
}

export function fetchQuestions(query: QuestionQuery) {
  return http.get<ApiResponse<PageResult<QuestionItem>>>('/questions', { params: query })
}

export function fetchQuestion(id: number) {
  return http.get<ApiResponse<QuestionDetail>>(`/questions/${id}`)
}
