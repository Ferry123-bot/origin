import http from '@/api/http'
import type { ApiResponse } from '@/types/api'

export interface PlatformStats {
  studentCount: number
  questionCount: number
  interviewCount: number
  goodRate: number
}

export interface HotStage {
  stageId: number
  subjectId: number
  subjectName: string
  stageName: string
  icon: string | null
  questionCount: number
}

export interface LearningStats {
  consecutiveDays: number
  accuracyRate: number
  totalPracticeCount: number
}

export interface TrendPoint {
  date: string
  count: number
}

export interface LearningOverview {
  learningStats: LearningStats
  weekTrend: TrendPoint[]
  todayCount: number
  guideFinished: boolean
}

export function fetchPlatformStats() {
  return http.get<ApiResponse<PlatformStats>>('/home/platform-stats')
}

export function fetchHotStages() {
  return http.get<ApiResponse<HotStage[]>>('/home/hot-stages')
}

export function fetchLearningOverview() {
  return http.get<ApiResponse<LearningOverview>>('/home/learning-overview')
}

export function finishGuide() {
  return http.post<ApiResponse<null>>('/profile/guide')
}
