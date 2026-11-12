import http from '@/api/http'
import type { ApiResponse } from '@/types/api'
import { useUserStore } from '@/store/user'

export interface ProfileStats {
  practiceCount: number
  interviewCount: number
  wrongQuestionCount: number
}

export interface StudyStats {
  totalDays: number
  accuracyRate: number
}

export interface Profile {
  id: number
  nickname: string
  mobile: string
  email: string | null
  gender: number
  preferredSubjectId: number | null
  avatar: string | null
  guideFinished: boolean
  stats: ProfileStats
  study: StudyStats
}

export interface ProfileUpdate {
  nickname: string
  email: string
  gender: number
  avatar: string | null
}

export function fetchProfile() {
  return http.get<ApiResponse<Profile>>('/profile')
}

export function updateProfile(body: ProfileUpdate) {
  return http.put<ApiResponse<null>>('/profile', body)
}

export function changePassword(oldPassword: string, newPassword: string) {
  return http.put<ApiResponse<null>>('/profile/password', { oldPassword, newPassword }, {
    headers: { 'X-Refresh-Token': useUserStore().refreshToken },
  })
}

export function uploadAvatar(file: File) {
  const data = new FormData()
  data.append('file', file)
  return http.post<ApiResponse<{ url: string }>>('/files/avatar', data)
}
