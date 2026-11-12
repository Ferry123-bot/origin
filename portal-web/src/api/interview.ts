import http, { refreshPortalAccess } from '@/api/http'
import { readPortalToken } from '@/store/user'
import type { ApiResponse, PageResult } from '@/types/api'

export interface InterviewSubject {
  subjectId: number
  subjectName: string
}

export interface InterviewConfig {
  subjects: InterviewSubject[]
  difficulties: number[]
  questionCountOptions: number[]
  defaultCount: number
  remainingCount: number
  timeoutMinutes: number
}

export interface InterviewQuestion {
  order: number
  subjectId: number
  difficulty: number
  content: string
  source: 'bank' | 'ai' | string
}

export interface InterviewSession {
  sessionId: string
  questions: InterviewQuestion[]
  questionCount: number
}

export interface GradeEvent {
  order: number
  score: number
  feedback: string
  degraded: boolean
  referencePoints: string[]
}

export interface FinishResult {
  reportId: number
  overallScore: number
}

export interface ReportDimension {
  name: string
  score: number
}

export interface ReportWeakness {
  text: string
  advice: string
}

export interface ImproveCard {
  title: string
  content: string
}

export interface QuestionReview {
  order: number
  questionText: string
  studentAnswer: string | null
  score: number
  feedback: string | null
}

export interface InterviewReport {
  reportId: number
  recordId: number
  subjectId: number
  subjectName: string
  difficulty: number
  questionCount: number
  totalDuration: number
  completedAt: string
  overallScore: number
  accuracyRate: number
  trendDelta: number | null
  aiCoverage: number
  dimensions: ReportDimension[]
  strengths: string[]
  weaknesses: ReportWeakness[]
  suggestions: string[]
  improveCards: ImproveCard[]
  questionReviews: QuestionReview[]
}

export interface InterviewRecord {
  recordId: number
  subjectId: number
  subjectName: string
  difficulty: number
  questionCount: number
  overallScore: number
  totalDuration: number
  completedAt: string
  hasReport: boolean
}

export interface InterviewStats {
  interviewTotal: number
  avgScore: number
  maxScore: number
  totalDuration: number
}

export interface FinishAnswer {
  order: number
  studentAnswer: string | null
  durationSeconds: number
}

const DIFFICULTY: Record<number, { label: string; hint: string }> = {
  1: { label: '简单', hint: '基础面试，适合刚入门，考察核心概念。' },
  2: { label: '中等', hint: '进阶面试，适合有练习基础，考察场景理解。' },
  3: { label: '困难', hint: '高级面试，适合准备高阶岗位，考察取舍与边界。' },
}

export function difficultyMeta(value: number) {
  return DIFFICULTY[value] ?? DIFFICULTY[2]
}

export function formatDuration(seconds: number) {
  const total = Math.max(0, Math.round(seconds || 0))
  const minutes = Math.floor(total / 60)
  const rest = total % 60
  if (minutes === 0) return `${rest}秒`
  if (rest === 0) return `${minutes}分钟`
  return `${minutes}分${rest}秒`
}

export function formatDateTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

export function scoreTone(score: number) {
  if (score >= 8) return 'high'
  if (score >= 6) return 'mid'
  return 'low'
}

export function fetchInterviewConfig() {
  return http.get<ApiResponse<InterviewConfig>>('/interview/config')
}

export function startInterview(body: { subjectId: number; difficulty: number; count: number }, idempotencyKey: string) {
  return http.post<ApiResponse<InterviewSession>>('/interview/sessions', body, {
    headers: { 'Idempotency-Key': idempotencyKey },
  })
}

export function finishInterview(sessionId: string, answers: FinishAnswer[], idempotencyKey: string) {
  return http.post<ApiResponse<FinishResult>>(`/interview/sessions/${sessionId}/finish`, { answers }, {
    headers: { 'Idempotency-Key': idempotencyKey },
    timeout: 50000,
  })
}

export function fetchInterviewReport(id: number) {
  return http.get<ApiResponse<InterviewReport>>(`/interview/reports/${id}`)
}

export function fetchInterviewRecords(page: number, pageSize: number) {
  return http.get<ApiResponse<PageResult<InterviewRecord>>>('/interview/records', { params: { page, pageSize } })
}

export function fetchInterviewStats() {
  return http.get<ApiResponse<InterviewStats>>('/interview/records/stats')
}

export async function gradeInterviewAnswer(
  sessionId: string,
  body: { order: number; studentAnswer: string; durationSeconds: number },
  onPhase?: (phase: string) => void,
): Promise<GradeEvent> {
  let response = await gradeFetch(sessionId, body)
  if (response.status === 401) {
    const payload = await response.clone().json().catch(() => null) as ApiResponse<unknown> | null
    if (payload?.code === 10001) {
      await refreshPortalAccess()
      response = await gradeFetch(sessionId, body)
    }
  }
  const contentType = response.headers.get('content-type') || ''
  if (!contentType.includes('text/event-stream')) {
    throw await response.json()
  }
  const reader = response.body?.getReader()
  if (!reader) throw { code: -1, message: '评分连接中断', data: null }
  const decoder = new TextDecoder()
  let buffer = ''
  let grade: GradeEvent | null = null
  while (true) {
    const chunk = await reader.read()
    if (chunk.done) break
    buffer += decoder.decode(chunk.value, { stream: true })
    const frames = buffer.split('\n\n')
    buffer = frames.pop() || ''
    for (const frame of frames) {
      const name = frame.match(/^event:\s*(.*)$/m)?.[1]?.trim()
      const dataLine = frame.split('\n').filter((line) => line.startsWith('data:')).map((line) => line.slice(5).trim()).join('\n')
      if (!dataLine) continue
      const data = JSON.parse(dataLine) as GradeEvent & { phase?: string; code?: number; message?: string }
      if (name === 'error') throw data
      if (name === 'status' && data.phase) onPhase?.(data.phase)
      if (name === 'grade') grade = data
    }
  }
  if (!grade) throw { code: 20010, message: '未收到评分结果', data: null }
  return grade
}

function gradeFetch(sessionId: string, body: { order: number; studentAnswer: string; durationSeconds: number }) {
  const token = readPortalToken()
  const controller = new AbortController()
  const timeout = window.setTimeout(() => controller.abort(), 50000)
  return fetch(`${import.meta.env.VITE_API_BASE}/interview/sessions/${sessionId}/answers`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: token ? `Bearer ${token}` : '',
    },
    body: JSON.stringify(body),
    signal: controller.signal,
  }).finally(() => window.clearTimeout(timeout))
}
