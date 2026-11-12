import http from '@/api/http'
import type { ApiResponse } from '@/types/api'
import type { PageResult } from '@/api/question'

export interface InterviewSubject {
  id: number
  name: string
}

export interface InterviewQuestion {
  id: number
  content: string
  subjectId: number
  subjectName: string
  difficulty: number
  topic: string | null
  tags: string | null
  sourceType: number
  sourceDocName: string | null
  sourceSeq: number | null
  createdAt: string
}

export interface InterviewDetail extends InterviewQuestion {
  referenceAnswer: string | null
  updatedAt: string
}

export interface InterviewSample {
  content: string
  referenceAnswer: string
  lineNo: number
}

export interface InterviewImportPreview {
  token: string
  fileName: string
  fileSize: number
  total: number
  samples: InterviewSample[]
}

export interface InterviewUploadStatus {
  status: 'parsing' | 'done' | 'failed'
  parsedCount: number
  samples: InterviewSample[]
  errorMessage?: string | null
}

export interface InterviewSearchHit {
  id: number
  content: string
  subjectName: string
  sourceType: number
  sourceDocName: string | null
  sourceSeq: number | null
  topic: string | null
  difficulty: number
}

export interface InterviewTopic {
  id: number | null
  name: string
  subjectId: number | null
  subjectName: string | null
  questionCount: number
  inVocabulary: boolean
  sortOrder: number
}

export interface InterviewQuery {
  keyword?: string
  subjectId?: number
  topicId?: number
  topic?: string
  topicSubjectId?: number
  difficulty?: number
  unclassified?: boolean
  page: number
  pageSize: number
}

export function fetchInterviewSubjects() {
  return http.get<ApiResponse<InterviewSubject[]>>('/interview-questions/subjects')
}

export function fetchInterviewQuestions(params: InterviewQuery) {
  return http.get<ApiResponse<PageResult<InterviewQuestion>>>('/interview-questions', { params })
}

export function fetchInterviewQuestion(id: number) {
  return http.get<ApiResponse<InterviewDetail>>(`/interview-questions/${id}`)
}

export function createInterviewQuestion(body: {
  subjectId: number
  content: string
  referenceAnswer: string
  topic: string
  difficulty: number
  tags: string
}) {
  return http.post('/interview-questions', body)
}

export function updateInterviewQuestion(id: number, body: { topic: string; difficulty: number }) {
  return http.put(`/interview-questions/${id}`, body)
}

export function deleteInterviewQuestion(id: number) {
  return http.delete(`/interview-questions/${id}`)
}

export function previewInterviewImport(file: File) {
  const form = new FormData()
  form.append('file', file)
  return http.post<ApiResponse<InterviewImportPreview>>('/interview-questions/import/preview', form, { timeout: 60000 })
}

export function confirmInterviewImport(body: { token: string; subjectId: number; topic: string; difficulty: number; tags: string }) {
  return http.post<ApiResponse<{ imported: number }>>('/interview-questions/import/confirm', body, { timeout: 60000 })
}

export function uploadInterviewDocument(file: File, subjectId: number) {
  const form = new FormData()
  form.append('file', file)
  form.append('subjectId', String(subjectId))
  return http.post<ApiResponse<{ taskId: string }>>('/interview-questions/upload', form, { timeout: 60000 })
}

export function fetchInterviewUpload(taskId: string) {
  return http.get<ApiResponse<InterviewUploadStatus>>(`/interview-questions/upload/${taskId}`)
}

export function searchInterviewQuestions(keyword: string) {
  return http.post<ApiResponse<InterviewSearchHit[]>>('/interview-questions/search-test', { keyword })
}

export function fetchInterviewTopics(subjectId?: number) {
  return http.get<ApiResponse<InterviewTopic[]>>('/interview-topics', { params: { subjectId } })
}

export function createInterviewTopic(body: { name: string; subjectId: number | null; sortOrder: number }) {
  return http.post('/interview-topics', body)
}

export function updateInterviewTopic(id: number, body: { name: string; subjectId: number | null; sortOrder: number }) {
  return http.put(`/interview-topics/${id}`, body)
}

export function deleteInterviewTopic(id: number) {
  return http.delete(`/interview-topics/${id}`)
}
