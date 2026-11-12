import http from '@/api/http'
import type { ApiResponse } from '@/types/api'

export interface SiteSettings {
  siteName: string
  siteDescription: string
  logoUrl: string
  defaultAvatar: string
}

export function fetchSettings() {
  return http.get<ApiResponse<SiteSettings>>('/settings')
}

export function saveSettings(body: SiteSettings) {
  return http.put('/settings', body)
}

export function resetSettings() {
  return http.post('/settings/reset')
}

export function uploadImage(file: File, kind: 'logo' | 'avatar') {
  const form = new FormData()
  form.append('file', file)
  form.append('kind', kind)
  return http.post<ApiResponse<{ url: string }>>('/files/image', form)
}
