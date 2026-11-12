import { defineStore } from 'pinia'
import { ref } from 'vue'
import { difficultyMeta, startInterview, type InterviewQuestion, type InterviewSession } from '@/api/interview'

const STORAGE_KEY = 'yszx_interview_draft'

export interface DraftQuestion {
  order: number
  content: string
  source: string
  score: number | null
  feedback: string
  degraded: boolean
  answer: string
  durationSeconds: number
}

export interface DraftMessage {
  id: string
  role: 'ai' | 'user' | 'status'
  text: string
  time: string
  order?: number
}

export interface InterviewDraft {
  sessionId: string
  finishKey: string
  subjectId: number
  subjectName: string
  difficulty: number
  questionCount: number
  questions: DraftQuestion[]
  messages: DraftMessage[]
  currentOrder: number
  questionStartedAt: number
  elapsedMs: number
  voiceOn: boolean
  phase: 'asking' | 'done'
}

function read(): InterviewDraft | null {
  const raw = localStorage.getItem(STORAGE_KEY)
  if (!raw) return null
  try {
    const data = JSON.parse(raw) as InterviewDraft
    if (!data?.sessionId || !Array.isArray(data.questions) || !Array.isArray(data.messages)) return null
    return data
  } catch {
    return null
  }
}

export function clock() {
  const now = new Date()
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${pad(now.getHours())}:${pad(now.getMinutes())}`
}

export function formatElapsed(ms: number) {
  const total = Math.max(0, Math.floor(ms / 1000))
  const hours = Math.floor(total / 3600)
  const minutes = Math.floor((total % 3600) / 60)
  const seconds = total % 60
  const pad = (value: number) => String(value).padStart(2, '0')
  return hours > 0 ? `${pad(hours)}:${pad(minutes)}:${pad(seconds)}` : `${pad(minutes)}:${pad(seconds)}`
}

export function questionMessage(question: DraftQuestion, index: number, total: number, difficulty: number) {
  const level = difficultyMeta(difficulty).label
  if (index === 0) {
    return `你好，我是本次面试官。我们将进行 ${total} 道${level}题目，每题回答时间 5 分钟。\n\n第 ${question.order} 题：${question.content}`
  }
  return `我们继续。\n\n第 ${question.order} 题：${question.content}`
}

export function createDraft(session: InterviewSession, subjectId: number, subjectName: string, difficulty: number): InterviewDraft {
  const questions: DraftQuestion[] = session.questions.map((item: InterviewQuestion) => ({
    order: item.order,
    content: item.content,
    source: item.source,
    score: null,
    feedback: '',
    degraded: false,
    answer: '',
    durationSeconds: 0,
  }))
  const first = questions[0]
  return {
    sessionId: session.sessionId,
    finishKey: crypto.randomUUID(),
    subjectId,
    subjectName,
    difficulty,
    questionCount: session.questionCount,
    questions,
    messages: first
      ? [{
          id: `q-${first.order}`,
          role: 'ai',
          text: questionMessage(first, 0, session.questionCount, difficulty),
          time: clock(),
          order: first.order,
        }]
      : [],
    currentOrder: first?.order ?? 1,
    questionStartedAt: Date.now(),
    elapsedMs: 0,
    voiceOn: true,
    phase: 'asking',
  }
}

export const useInterviewStore = defineStore('interview', () => {
  const draft = ref<InterviewDraft | null>(read())

  function persist(next: InterviewDraft | null) {
    draft.value = next
    if (next) localStorage.setItem(STORAGE_KEY, JSON.stringify(next))
    else localStorage.removeItem(STORAGE_KEY)
  }

  function reload() {
    draft.value = read()
  }

  return { draft, persist, reload }
})

export async function openInterview(subjectId: number, subjectName: string, difficulty: number, count: number) {
  const response = await startInterview({ subjectId, difficulty, count }, crypto.randomUUID())
  useInterviewStore().persist(createDraft(response.data.data, subjectId, subjectName, difficulty))
}
