import http from '@/api/http'
import type { ApiResponse } from '@/types/api'

export interface SubjectOption {
  id: number
  name: string
  icon: string | null
  themeColor: string | null
}

export interface CoursePreview {
  id: number
  name: string
  coverUrl: string | null
  bvid: string | null
  durationMinutes: number
}

export interface RouteCard {
  id: number
  name: string
  status: number
  subjectId: number
  subjectName: string | null
  subjectIcon: string | null
  icon: string | null
  targetEducation: string | null
  description: string | null
  courseCount: number
  totalMinutes: number
  totalDuration: string
  previews: CoursePreview[]
}

export interface RouteBoard {
  subjects: SubjectOption[]
  routes: RouteCard[]
}

export interface RouteCourseItem {
  routeCourseId: number
  courseId: number
  name: string
  bvid: string | null
  durationMinutes: number
  duration: string
  coverUrl: string | null
  link: string | null
  source: number | null
  subjectId: number | null
  subjectName: string | null
  crossSubject: boolean
  sortOrder: number
}

export interface RouteDetail {
  id: number
  name: string
  subjectId: number
  subjectName: string | null
  subjectIcon: string | null
  status: number
  icon: string | null
  targetEducation: string | null
  description: string | null
  courseCount: number
  totalMinutes: number
  totalDuration: string
  courses: RouteCourseItem[]
  ownCourses: RouteCourseItem[]
  otherCourses: RouteCourseItem[]
}

export interface CourseOption {
  id: number
  name: string
  bvid: string | null
  durationMinutes: number
  duration: string
  coverUrl: string | null
  subjectId: number | null
  subjectName: string | null
  own: boolean
}

export interface RouteSavePayload {
  name: string
  subjectId: number
  targetEducation?: string | null
  icon?: string | null
  description?: string | null
  status?: number
  courseIds?: number[]
}

export function fetchRouteBoard() {
  return http.get<ApiResponse<RouteBoard>>('/routes')
}

export function fetchRouteDetail(id: number) {
  return http.get<ApiResponse<RouteDetail>>(`/routes/${id}/detail`)
}

export function fetchCourseOptions(subjectId: number) {
  return http.get<ApiResponse<CourseOption[]>>('/routes/course-options', { params: { subjectId } })
}

export function createRoute(payload: RouteSavePayload) {
  return http.post<ApiResponse<null>>('/routes', payload)
}

export function updateRoute(id: number, payload: RouteSavePayload) {
  return http.put<ApiResponse<null>>(`/routes/${id}`, payload)
}

export function updateRouteStatus(id: number, status: number) {
  return http.post<ApiResponse<null>>(`/routes/${id}/status`, { status })
}

export function deleteRoute(id: number) {
  return http.delete<ApiResponse<null>>(`/routes/${id}`)
}

export function addRouteCourses(id: number, courseIds: number[]) {
  return http.post<ApiResponse<{ added: number }>>(`/routes/${id}/courses`, { courseIds })
}

export function removeRouteCourse(id: number, courseId: number) {
  return http.delete<ApiResponse<null>>(`/routes/${id}/courses/${courseId}`)
}

export function sortRouteCourses(id: number, courseIds: number[]) {
  return http.put<ApiResponse<null>>(`/routes/${id}/courses/sort`, { courseIds })
}
