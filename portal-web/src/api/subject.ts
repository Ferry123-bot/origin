import http from '@/api/http'
import type { ApiResponse } from '@/types/api'

export interface PortalSubject {
  id: number
  name: string
  icon: string
  description: string
  themeColor: string
  stageCount: number
  sortOrder: number
}

export interface PortalStage {
  id: number
  name: string
  icon: string | null
  questionCount: number
  sortOrder: number
}

export function fetchSubjects() {
  return http.get<ApiResponse<PortalSubject[]>>('/subjects')
}

export function fetchStages(subjectId: number) {
  return http.get<ApiResponse<PortalStage[]>>(`/subjects/${subjectId}/stages`)
}
