import axios, { type InternalAxiosRequestConfig } from 'axios'
import type { ApiResponse } from '@/types/api'
import { ADMIN_REFRESH_KEY, ADMIN_TOKEN_KEY, useUserStore } from '@/store/user'

interface RetryConfig extends InternalAxiosRequestConfig {
  _retried?: boolean
}

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE,
  timeout: 15000,
})

let refreshing: Promise<string> | null = null

http.interceptors.request.use((config) => {
  const token = sessionStorage.getItem(ADMIN_TOKEN_KEY) || localStorage.getItem(ADMIN_TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResponse<unknown>
    if (body && typeof body.code === 'number' && body.code !== 0) {
      return Promise.reject(body)
    }
    return response
  },
  async (error) => {
    let body = error.response?.data as ApiResponse<unknown> | undefined
    if (body instanceof Blob) {
      try {
        body = JSON.parse(await body.text()) as ApiResponse<unknown>
      } catch {
        body = undefined
      }
    }
    const config = error.config as RetryConfig | undefined
    if (body && typeof body.code === 'number') {
      if (body.code === 10001 && config && !config._retried && !skipRefresh(config.url)) {
        config._retried = true
        try {
          const accessToken = await refreshAccess()
          config.headers.Authorization = `Bearer ${accessToken}`
          return http(config)
        } catch {
          useUserStore().clear()
        }
      } else if (body.code === 10001 && (config?.url ?? '').includes('/auth/refresh')) {
        useUserStore().clear()
      }
      return Promise.reject(body)
    }
    return Promise.reject({ code: -1, message: error.message || '网络异常', data: null })
  },
)

function skipRefresh(url?: string) {
  if (!url) return true
  return ['/auth/login', '/auth/refresh', '/auth/logout'].some((path) => url.includes(path))
}

function refreshAccess() {
  if (!refreshing) {
    refreshing = doRefresh().finally(() => {
      refreshing = null
    })
  }
  return refreshing
}

async function doRefresh() {
  const refreshToken = sessionStorage.getItem(ADMIN_REFRESH_KEY) || localStorage.getItem(ADMIN_REFRESH_KEY)
  if (!refreshToken) throw new Error('missing refresh')
  const bare = axios.create({ baseURL: import.meta.env.VITE_API_BASE, timeout: 15000 })
  const response = await bare.post<ApiResponse<{ accessToken: string; refreshToken: string }>>('/auth/refresh', null, {
    headers: { 'X-Refresh-Token': refreshToken },
  })
  const body = response.data
  if (!body || body.code !== 0 || !body.data) throw body
  useUserStore().setTokens(body.data.accessToken, body.data.refreshToken)
  return body.data.accessToken
}

export default http
