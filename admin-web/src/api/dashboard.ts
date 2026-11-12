import http from '@/api/http'
import type { PageResult } from '@/api/user'
import type { ApiResponse } from '@/types/api'

export interface DashboardMetric {
  total: number
  today: number
  yesterday: number
  changeRate: number | null
}

export interface DashboardOverview {
  subjects: DashboardMetric
  questions: DashboardMetric
  students: DashboardMetric
  interviews: DashboardMetric
}

export interface GrowthPoint {
  date: string
  count: number
}

export interface UserGrowth {
  range: number
  points: GrowthPoint[]
}

export interface SubjectSlice {
  subjectId: number | null
  name: string
  themeColor: string | null
  count: number
}

export interface ActivityItem {
  id: number
  username: string
  action: string
  detail: string
  createdAt: string
}

export function fetchDashboardOverview() {
  return http.get<ApiResponse<DashboardOverview>>('/dashboard/overview')
}

export function fetchUserGrowth(range: number) {
  return http.get<ApiResponse<UserGrowth>>('/dashboard/user-growth', { params: { range } })
}

export function fetchQuestionDistribution() {
  return http.get<ApiResponse<{ items: SubjectSlice[] }>>('/dashboard/question-distribution')
}

export function fetchRecentActivities(page: number, pageSize: number) {
  return http.get<ApiResponse<PageResult<ActivityItem>>>('/dashboard/recent-activities', {
    params: { page, pageSize },
  })
}
