import http from '@/api/http'
import type { ApiResponse } from '@/types/api'

export interface PortalRoute {
  id: number
  name: string
  subjectId: number
  subjectName: string | null
  targetEducation: string | null
  description: string | null
  courseCount: number
  completedCourseCount: number
  progress: number
}

export interface PortalRouteCourse {
  routeCourseId: number
  courseId: number
  name: string
  bvid: string | null
  durationMinutes: number
  source: number | null
  coverUrl: string | null
  link: string | null
  completed: boolean
  sortOrder: number
  crossSubject: boolean
}

export interface PortalRouteDetail {
  id: number
  name: string
  subjectId: number
  subjectName: string | null
  icon: string | null
  targetEducation: string | null
  description: string | null
  courseCount: number
  totalMinutes: number
  completedCourseCount: number
  progress: number
  courses: PortalRouteCourse[]
}

export interface RouteProgress {
  completedCourseCount: number
  courseCount: number
  progress: number
}

export function fetchRoutes() {
  return http.get<ApiResponse<PortalRoute[]>>('/routes')
}

export function fetchRoute(id: number) {
  return http.get<ApiResponse<PortalRouteDetail>>(`/routes/${id}`)
}

export function completeRouteCourse(routeCourseId: number, courseId: number) {
  return http.post<ApiResponse<RouteProgress>>(`/route-courses/${routeCourseId}/complete`, { courseId })
}
