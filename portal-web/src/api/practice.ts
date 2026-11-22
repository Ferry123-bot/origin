import http from '@/api/http'
import type { ApiResponse } from '@/types/api'

export interface PracticeOption {
  key: string
  text: string
}

export interface PracticeQuestion {
  id: number
  type: number
  difficulty: number
  content: string
  options: PracticeOption[]
  answer: string
}

export interface PracticePaper {
  practiceId: string
  title: string
  subjectId?: number | null
  stageId?: number | null
  questions: PracticeQuestion[]
}

export interface PracticeStartBody {
  source: 'question' | 'subject' | 'wrong_redo'
  questionId?: number
  subjectId?: number
  stageId?: number
  type?: number
  difficulty?: number
  count?: number
  mode: 1 | 2
  questionIds?: number[]
}

export interface PracticeResult {
  questionId: number
  isCorrect: boolean
  score: number
  answer: string
  analysis?: string | null
  aiFeedback?: string | null
  degraded?: boolean | null
  degradeReason?: string | null
  referencePoints?: string[] | null
}

export interface GradeEvent {
  order: number
  score: number
  feedback: string
  degraded: boolean
  degradeReason?: string | null
  referencePoints: string[]
}

export function startPractice(body: PracticeStartBody) {
  return http.post<ApiResponse<PracticePaper>>('/practice/start', body)
}

export function submitPracticeAnswers(practiceId: string, answers: { questionId: number; answer: string; durationSeconds: number }[]) {
  return http.post<ApiResponse<{ results: PracticeResult[] }>>(`/practice/${practiceId}/submit`, { answers }, {
    timeout: 50000,
  })
}
