<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { ApiResponse } from '@/types/api'
import { fetchQuestion } from '@/api/question'
import {
  startPractice,
  submitPracticeAnswers,
  type GradeEvent,
  type PracticeQuestion,
  type PracticeResult,
} from '@/api/practice'
import { refreshPortalAccess } from '@/api/http'
import { readPortalToken } from '@/store/user'

const SESSION_KEY = 'yszx_portal_practice'
const TYPE_LABEL: Record<number, string> = { 1: '单选', 2: '多选', 3: '判断', 4: '简答' }

interface GradeCard {
  score: number
  feedback: string
  referencePoints: string[]
  reference: string
  analysis: string
  degraded: boolean
}

interface Session {
  practiceId: string
  title: string
  mode: 1 | 2
  subjectId: number | null
  stageId: number | null
  index: number
  elapsed: number
  questions: PracticeQuestion[]
  answers: (string | null)[]
  statuses: ('correct' | 'wrong' | null)[]
  revealed: boolean[]
  persisted: boolean[]
  grades: (GradeCard | null)[]
  analyses: (string | null)[]
  corrects: (string | null)[]
  durations: number[]
}

const route = useRoute()
const router = useRouter()

const started = ref(false)
const starting = ref(false)
const busy = ref(false)
const picking = ref(false)
const offering = ref(false)
const confirming = ref(false)
const confirmText = ref('')
const shortcuts = ref(false)
const emptyMessage = ref('')
const practiceId = ref('')
const title = ref('刷题练习')
const subjectId = ref<number | null>(null)
const stageId = ref<number | null>(null)
const questions = ref<PracticeQuestion[]>([])
const index = ref(0)
const elapsed = ref(0)
const answers = ref<(string | null)[]>([])
const statuses = ref<('correct' | 'wrong' | null)[]>([])
const revealed = ref<boolean[]>([])
const persisted = ref<boolean[]>([])
const grades = ref<(GradeCard | null)[]>([])
const analyses = ref<(string | null)[]>([])
const corrects = ref<(string | null)[]>([])
const durations = ref<number[]>([])
const mode = ref<1 | 2>(1)

let timer = 0
let shownAt = Date.now()

const current = computed(() => questions.value[index.value] ?? null)
const total = computed(() => questions.value.length)
const progress = computed(() => (total.value ? ((index.value + 1) / total.value) * 100 : 0))
const clock = computed(() => {
  const hours = Math.floor(elapsed.value / 3600).toString().padStart(2, '0')
  const minutes = Math.floor((elapsed.value % 3600) / 60).toString().padStart(2, '0')
  const seconds = (elapsed.value % 60).toString().padStart(2, '0')
  return `${hours}:${minutes}:${seconds}`
})
const isShort = computed(() => current.value?.type === 4)
const currentGrade = computed(() => grades.value[index.value])
const selectedKeys = computed(() => splitKeys(answers.value[index.value]))
const correctKeys = computed(() => splitKeys(corrects.value[index.value]))
const unfinishedCount = computed(() => questions.value.filter((item, i) => item.type !== 4 && !answers.value[i]).length)

function positive(value: unknown) {
  const number = Number(value)
  return Number.isInteger(number) && number > 0 ? number : null
}

function splitKeys(value: string | null | undefined) {
  if (!value) return []
  return value.split(/[,，、]/).map((item) => item.trim()).filter(Boolean)
}

function messageOf(error: unknown, fallback: string) {
  return (error as ApiResponse<unknown>)?.message || (error as { message?: string })?.message || fallback
}

function blankArrays(count: number): Pick<Session, 'answers' | 'statuses' | 'revealed' | 'persisted' | 'grades' | 'analyses' | 'corrects' | 'durations'> {
  return {
    answers: Array(count).fill(null),
    statuses: Array(count).fill(null),
    revealed: Array(count).fill(false),
    persisted: Array(count).fill(false),
    grades: Array(count).fill(null),
    analyses: Array(count).fill(null),
    corrects: Array(count).fill(null),
    durations: Array(count).fill(0),
  }
}

function loadSession(): Session | null {
  try {
    const raw = localStorage.getItem(SESSION_KEY)
    if (!raw) return null
    const data = JSON.parse(raw) as Session
    const count = data?.questions?.length ?? 0
    if (!data?.practiceId || count === 0) return null
    const arrays = [data.answers, data.statuses, data.revealed, data.persisted, data.grades, data.analyses, data.corrects, data.durations]
    if (arrays.some((item) => !Array.isArray(item) || item.length !== count)) return null
    return data
  } catch {
    return null
  }
}

function snapshot(): Session {
  return {
    practiceId: practiceId.value,
    title: title.value,
    mode: mode.value,
    subjectId: subjectId.value,
    stageId: stageId.value,
    index: index.value,
    elapsed: elapsed.value,
    questions: questions.value,
    answers: answers.value,
    statuses: statuses.value,
    revealed: revealed.value,
    persisted: persisted.value,
    grades: grades.value,
    analyses: analyses.value,
    corrects: corrects.value,
    durations: durations.value,
  }
}

function save() {
  if (!started.value || questions.value.length === 0) return
  localStorage.setItem(SESSION_KEY, JSON.stringify(snapshot()))
}

function clearSession() {
  localStorage.removeItem(SESSION_KEY)
}

function applySession(data: Session) {
  mode.value = data.mode === 2 ? 2 : 1
  practiceId.value = data.practiceId
  title.value = data.title || '刷题练习'
  subjectId.value = data.subjectId
  stageId.value = data.stageId
  questions.value = data.questions
  index.value = data.index >= 0 && data.index < data.questions.length ? data.index : 0
  elapsed.value = data.elapsed || 0
  answers.value = data.answers
  statuses.value = data.statuses
  revealed.value = data.revealed
  persisted.value = data.persisted
  grades.value = data.grades
  analyses.value = data.analyses
  corrects.value = data.corrects
  durations.value = data.durations
  started.value = true
  picking.value = false
  offering.value = false
  emptyMessage.value = ''
  shownAt = Date.now()
  startTimer()
}

function startTimer() {
  stopTimer()
  timer = window.setInterval(() => {
    elapsed.value += 1
    if (elapsed.value % 5 === 0) save()
  }, 1000)
}

function stopTimer() {
  if (timer) window.clearInterval(timer)
  timer = 0
}

function bumpDuration() {
  const extra = Math.max(0, Math.floor((Date.now() - shownAt) / 1000))
  durations.value[index.value] = (durations.value[index.value] || 0) + extra
  shownAt = Date.now()
}

function entryMode(): 1 | 2 | 0 {
  if (route.query.mode === 'random') return 2
  if (route.query.mode === 'sequential') return 1
  return 0
}

async function begin(nextMode: 1 | 2) {
  if (starting.value) return
  starting.value = true
  emptyMessage.value = ''
  picking.value = false
  offering.value = false
  try {
    const questionId = positive(route.query.questionId)
    const queryStageId = positive(route.query.stageId)
    const querySubjectId = positive(route.query.subjectId)
    const count = positive(route.query.count)
    const response = await startPractice({
      source: questionId ? 'question' : 'subject',
      mode: nextMode,
      questionId: questionId || undefined,
      stageId: questionId ? undefined : queryStageId || undefined,
      subjectId: questionId ? undefined : querySubjectId || undefined,
      count: questionId ? undefined : count || undefined,
    })
    const paper = response.data.data
    const blanks = blankArrays(paper.questions.length)
    mode.value = nextMode
    practiceId.value = paper.practiceId
    title.value = paper.title || '刷题练习'
    subjectId.value = paper.subjectId ?? querySubjectId
    stageId.value = paper.stageId ?? queryStageId
    questions.value = paper.questions
    index.value = 0
    elapsed.value = 0
    answers.value = blanks.answers
    statuses.value = blanks.statuses
    revealed.value = blanks.revealed
    persisted.value = blanks.persisted
    grades.value = blanks.grades
    analyses.value = blanks.analyses
    corrects.value = blanks.corrects
    durations.value = blanks.durations
    started.value = true
    shownAt = Date.now()
    startTimer()
    save()
  } catch (error) {
    emptyMessage.value = messageOf(error, '暂时没有可练习的题目')
    picking.value = entryMode() === 0
    ElMessage.error(emptyMessage.value)
  } finally {
    starting.value = false
  }
}

function resume() {
  const saved = loadSession()
  if (!saved) {
    offering.value = false
    openPickerOrStart()
    return
  }
  applySession(saved)
}

function restart() {
  clearSession()
  stopTimer()
  started.value = false
  offering.value = false
  confirming.value = false
  openPickerOrStart()
}

function openPickerOrStart() {
  const mode = entryMode()
  if (mode === 1 || mode === 2) {
    void begin(mode)
    return
  }
  picking.value = true
}

function cancelPicker() {
  backToBank()
}

function backQuery() {
  const query: Record<string, string> = {}
  const subject = subjectId.value || positive(route.query.subjectId)
  const stage = stageId.value || positive(route.query.stageId)
  if (subject) query.subjectId = String(subject)
  if (stage) query.stageId = String(stage)
  return query
}

function backToBank() {
  stopTimer()
  router.push({ name: 'questions', query: backQuery() })
}

function choose(key: string) {
  const question = current.value
  if (!question || question.type === 4 || persisted.value[index.value] || busy.value) return
  if (question.type === 2) {
    const keys = new Set(selectedKeys.value)
    if (keys.has(key)) keys.delete(key)
    else keys.add(key)
    const next = [...keys].sort()
    answers.value[index.value] = next.length ? next.join(',') : null
  } else {
    answers.value[index.value] = key
  }
  save()
}

function onDraft(event: Event) {
  const value = (event.target as HTMLTextAreaElement).value
  answers.value[index.value] = value
  save()
}

async function submitCurrent(answer: string) {
  bumpDuration()
  const response = await submitPracticeAnswers(practiceId.value, [{
    questionId: questions.value[index.value].id,
    answer,
    durationSeconds: durations.value[index.value] || 0,
  }])
  applyResult(index.value, response.data.data.results[0])
  save()
}

function applyResult(position: number, result: PracticeResult | undefined) {
  if (!result) return
  statuses.value[position] = result.isCorrect ? 'correct' : 'wrong'
  persisted.value[position] = true
  if (questions.value[position]?.type === 4) {
    grades.value[position] = {
      score: result.score,
      feedback: result.aiFeedback || '',
      referencePoints: result.referencePoints || [],
      reference: result.answer || '',
      analysis: result.analysis || '',
      degraded: !!result.degraded,
    }
    return
  }
  corrects.value[position] = result.answer || ''
  analyses.value[position] = result.analysis || ''
  revealed.value[position] = true
}

async function gradeFetch(questionId: number, answer: string) {
  const token = readPortalToken()
  const controller = new AbortController()
  const timeout = window.setTimeout(() => controller.abort(), 40000)
  try {
    return await fetch(`${import.meta.env.VITE_API_BASE}/practice/${practiceId.value}/grade`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: token ? `Bearer ${token}` : '',
      },
      body: JSON.stringify({ questionId, answer }),
      signal: controller.signal,
    })
  } finally {
    window.clearTimeout(timeout)
  }
}

async function gradeShort(questionId: number, answer: string): Promise<GradeEvent | null> {
  let response = await gradeFetch(questionId, answer)
  if (response.status === 401) {
    const body = await response.clone().json().catch(() => null) as ApiResponse<unknown> | null
    if (body?.code === 10001) {
      await refreshPortalAccess()
      response = await gradeFetch(questionId, answer)
    }
  }
  const contentType = response.headers.get('content-type') || ''
  if (!contentType.includes('text/event-stream')) {
    throw await response.json()
  }
  const reader = response.body?.getReader()
  if (!reader) return null
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
      const data = JSON.parse(dataLine) as GradeEvent & { code?: number; message?: string }
      if (name === 'error') throw data
      if (name === 'grade') grade = data
    }
  }
  return grade
}

async function submitShort() {
  const question = current.value
  if (!question || question.type !== 4 || persisted.value[index.value] || busy.value) return
  const answer = (answers.value[index.value] || '').trim()
  if (!answer) {
    ElMessage.warning('请输入你的回答')
    return
  }
  answers.value[index.value] = answer
  busy.value = true
  try {
    try {
      await gradeShort(question.id, answer)
    } catch (error) {
      const code = (error as { code?: number }).code
      if (code && code !== 20010) throw error
    }
    await submitCurrent(answer)
  } catch (error) {
    const code = (error as ApiResponse<unknown>)?.code
    if (code === 10003) {
      ElMessage.error('练习已过期，请重新开始')
      restart()
      return
    }
    ElMessage.error(messageOf(error, '评分失败'))
  } finally {
    busy.value = false
  }
}

async function showAnalysis() {
  const question = current.value
  if (!question || busy.value) return
  if (question.type === 4) {
    if (!persisted.value[index.value]) ElMessage.warning('请先提交答案')
    return
  }
  const answer = answers.value[index.value]
  if (!answer) {
    if (!revealed.value[index.value]) {
      busy.value = true
      try {
        const response = await fetchQuestion(question.id)
        corrects.value[index.value] = response.data.data.answer || question.answer || ''
        analyses.value[index.value] = response.data.data.analysis || ''
        revealed.value[index.value] = true
        save()
      } catch (error) {
        ElMessage.error(messageOf(error, '加载解析失败'))
      } finally {
        busy.value = false
      }
    }
    return
  }
  if (!persisted.value[index.value]) {
    busy.value = true
    try {
      await submitCurrent(answer)
    } catch (error) {
      const code = (error as ApiResponse<unknown>)?.code
      if (code === 10003) {
        ElMessage.error('练习已过期，请重新开始')
        restart()
        return
      }
      ElMessage.error(messageOf(error, '提交失败'))
      return
    } finally {
      busy.value = false
    }
  }
  revealed.value[index.value] = true
  save()
}

function go(next: number) {
  if (next < 0 || next >= questions.value.length || next === index.value || busy.value) return
  const question = current.value
  if (question?.type === 4 && !persisted.value[index.value] && next > index.value) {
    ElMessage.warning('请先提交答案')
    return
  }
  bumpDuration()
  index.value = next
  shownAt = Date.now()
  save()
}

function nextQuestion() {
  if (index.value >= questions.value.length - 1) {
    requestFinish()
    return
  }
  go(index.value + 1)
}

function requestFinish() {
  if (!started.value || busy.value) return
  const pendingShort = questions.value.findIndex((item, i) => item.type === 4 && !persisted.value[i])
  if (pendingShort >= 0) {
    ElMessage.warning('还有简答题未作答，请先完成')
    bumpDuration()
    index.value = pendingShort
    shownAt = Date.now()
    save()
    return
  }
  confirmText.value = unfinishedCount.value > 0
    ? `还有 ${unfinishedCount.value} 道题未完成，确定要结束练习吗？`
    : '确定要结束练习吗？'
  confirming.value = true
}

async function confirmFinish() {
  confirming.value = false
  busy.value = true
  try {
    bumpDuration()
    const pending = questions.value.flatMap((item, i) => {
      if (item.type === 4 || persisted.value[i] || !answers.value[i]) return []
      return [{ questionId: item.id, answer: answers.value[i] as string, durationSeconds: durations.value[i] || 0, position: i }]
    })
    if (pending.length) {
      const response = await submitPracticeAnswers(practiceId.value, pending.map(({ questionId, answer, durationSeconds }) => ({
        questionId, answer, durationSeconds,
      })))
      response.data.data.results.forEach((result, order) => applyResult(pending[order].position, result))
    }
  } catch (error) {
    ElMessage.error(messageOf(error, '部分作答未能保存'))
  } finally {
    busy.value = false
    stopTimer()
    clearSession()
    started.value = false
    backToBank()
  }
}

function optionClass(key: string) {
  const revealedNow = revealed.value[index.value]
  const chosen = selectedKeys.value.includes(key)
  const right = correctKeys.value.includes(key)
  return {
    selected: chosen && !persisted.value[index.value],
    correct: revealedNow && right,
    wrong: revealedNow && persisted.value[index.value] && chosen && !right,
    locked: persisted.value[index.value],
  }
}

function navClass(position: number) {
  const status = statuses.value[position]
  const answered = !!answers.value[position]
  return {
    current: position === index.value,
    wrong: status === 'wrong',
    correct: status === 'correct' || (answered && !status),
  }
}

function displayAnswer(value: string | null | undefined) {
  if (!value) return '暂无'
  return value.includes(',') ? value.split(',').join('、') : value
}

function analysisBadge() {
  if (!answers.value[index.value]) return { kind: 'empty', text: '未作答' }
  if (statuses.value[index.value] === 'correct') return { kind: 'correct', text: '回答正确' }
  return { kind: 'wrong', text: '回答错误' }
}

function scoreClass(score: number) {
  if (score >= 8) return 'high'
  if (score >= 6) return 'medium'
  return 'low'
}

function onKey(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    if (confirming.value) {
      confirming.value = false
      return
    }
    if (shortcuts.value) {
      shortcuts.value = false
      return
    }
    if (picking.value) cancelPicker()
    return
  }
  if (picking.value || offering.value || confirming.value || !started.value) return
  const tag = (event.target as HTMLElement | null)?.tagName
  const typing = tag === 'TEXTAREA' || tag === 'INPUT'
  if (event.key === '?' || (event.key === '/' && event.shiftKey)) {
    if (typing) return
    event.preventDefault()
    shortcuts.value = !shortcuts.value
    return
  }
  if (typing) return
  if (event.key === 'ArrowLeft') {
    event.preventDefault()
    go(index.value - 1)
  } else if (event.key === 'ArrowRight') {
    event.preventDefault()
    nextQuestion()
  } else if (event.key === 'Enter') {
    event.preventDefault()
    if (isShort.value && !persisted.value[index.value]) void submitShort()
    else if (!isShort.value && answers.value[index.value] && !revealed.value[index.value]) void showAnalysis()
    else nextQuestion()
  }
}

function onTextKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    event.stopPropagation()
    void submitShort()
  }
}

onMounted(() => {
  const saved = loadSession()
  if (saved) offering.value = true
  else if (route.query.continue === '1') {
    ElMessage.info('没有未完成的练习')
    picking.value = true
  } else openPickerOrStart()
  window.addEventListener('keydown', onKey)
  window.addEventListener('beforeunload', save)
})

onUnmounted(() => {
  stopTimer()
  window.removeEventListener('keydown', onKey)
  window.removeEventListener('beforeunload', save)
})
</script>

<template>
  <div v-loading="starting" class="practice" :class="{ idle: !started }">
    <aside v-if="started" class="sidebar">
      <button class="back" type="button" @click="requestFinish">返回题库</button>
      <h2>{{ title }}</h2>
      <div class="progress-line"><span :style="{ width: `${progress}%` }" /></div>
      <p class="progress-text">{{ index + 1 }}/{{ total }}</p>
      <p class="timer">{{ clock }}</p>
      <div class="sheet">
        <button
          v-for="(item, position) in questions"
          :key="item.id"
          type="button"
          class="nav-item"
          :class="navClass(position)"
          @click="go(position)"
        >{{ position + 1 }}</button>
      </div>
      <button class="end" type="button" :disabled="busy" @click="requestFinish">结束练习</button>
    </aside>

    <section class="board">
      <div v-if="!started" class="idle-panel">
        <h2>刷题练习</h2>
        <p v-if="emptyMessage">{{ emptyMessage }}</p>
        <p v-else>选择练习模式后开始答题。</p>
        <button v-if="emptyMessage" type="button" class="primary" @click="backToBank">返回题库</button>
      </div>

      <template v-else-if="current">
        <article class="question">
          <span class="type">{{ TYPE_LABEL[current.type] || '题目' }}</span>
          <p class="stem">{{ current.content }}</p>

          <div v-if="current.type !== 4" class="options">
            <button
              v-for="option in current.options"
              :key="option.key"
              type="button"
              class="option"
              :class="optionClass(option.key)"
              @click="choose(option.key)"
            >
              <span class="key">{{ option.key }}</span>
              <span>{{ option.text }}</span>
            </button>
          </div>

          <div v-else class="short">
            <textarea
              :value="answers[index] || ''"
              :disabled="persisted[index] || busy"
              placeholder="请输入你的回答，Enter 提交，Shift+Enter 换行"
              @input="onDraft"
              @keydown="onTextKeydown"
            />
            <button v-if="!persisted[index]" type="button" class="primary" :disabled="busy" @click="submitShort">
              {{ busy ? '评分中...' : '提交答案' }}
            </button>
            <div v-if="busy && !currentGrade" class="loading"><i /><span>AI 正在评分中</span></div>
            <div v-if="currentGrade" class="grade">
              <header>
                <span class="ai">AI</span>
                <strong>评分结果</strong>
                <em :class="scoreClass(currentGrade.score)">{{ currentGrade.score }}/10分</em>
              </header>
              <section>
                <h3>评语</h3>
                <p>{{ currentGrade.feedback }}</p>
              </section>
              <section v-if="currentGrade.referencePoints.length">
                <h3>关键点</h3>
                <div class="points">
                  <span v-for="point in currentGrade.referencePoints" :key="point">{{ point }}</span>
                </div>
              </section>
              <section>
                <h3>参考答案</h3>
                <p class="pre">{{ currentGrade.reference || '暂无参考答案' }}</p>
              </section>
              <p v-if="currentGrade.degraded" class="degraded">大模型本次未能评分，已按关键词规则评分。</p>
            </div>
          </div>

          <div v-if="current.type !== 4 && revealed[index]" class="analysis">
            <header>
              <span class="badge" :class="analysisBadge().kind">{{ analysisBadge().text }}</span>
              <strong>答案解析</strong>
            </header>
            <p>正确答案：{{ displayAnswer(corrects[index]) }}</p>
            <p class="pre">{{ analyses[index] || '暂无解析' }}</p>
          </div>
        </article>

        <footer>
          <button type="button" :disabled="index === 0 || busy" @click="go(index - 1)">上一题</button>
          <button v-if="!isShort || persisted[index]" type="button" :disabled="busy" @click="showAnalysis">查看解析</button>
          <button type="button" class="primary" :disabled="busy" @click="nextQuestion">{{ index === total - 1 ? '完成' : '下一题' }}</button>
        </footer>
      </template>
    </section>

    <div v-if="picking" class="mask" @click.self="cancelPicker">
      <div class="dialog" role="dialog" aria-label="选择练习模式">
        <header>
          <h3>选择练习模式</h3>
          <p>选定后开始本次练习</p>
          <button type="button" class="close" @click="cancelPicker">✕</button>
        </header>
        <div class="modes">
          <button type="button" @click="begin(1)">
            <span class="mode-icon" aria-hidden="true">📝</span>
            <strong>顺序练习</strong>
            <span>按题库顺序逐题作答</span>
          </button>
          <button type="button" @click="begin(2)">
            <span class="mode-icon" aria-hidden="true">🔀</span>
            <strong>随机练习</strong>
            <span>打乱题目顺序作答</span>
          </button>
        </div>
      </div>
    </div>

    <div v-if="offering" class="mask">
      <div class="dialog confirm" role="dialog" aria-label="继续练习">
        <div class="mark info" aria-hidden="true">
          <svg viewBox="0 0 24 24"><path d="M8 6.5v11l10-5.5z" /></svg>
        </div>
        <header><h3>检测到未完成的练习，是否继续？</h3></header>
        <div class="actions">
          <button type="button" @click="restart">
            <svg class="ico" viewBox="0 0 24 24"><path d="M4 12a8 8 0 1 0 2.3-5.6" /><path d="M4 4v5h5" /></svg>
            重新开始
          </button>
          <button type="button" class="primary" @click="resume">
            <svg class="ico solid" viewBox="0 0 24 24"><path d="M8 6.5v11l10-5.5z" /></svg>
            继续
          </button>
        </div>
      </div>
    </div>

    <div v-if="confirming" class="mask">
      <div class="dialog confirm" role="dialog" aria-label="结束练习">
        <div class="mark warn" aria-hidden="true">
          <svg viewBox="0 0 24 24"><path d="M12 6.5v7" /><circle cx="12" cy="17.5" r="1.15" fill="currentColor" stroke="none" /></svg>
        </div>
        <header><h3>{{ confirmText }}</h3></header>
        <div class="actions">
          <button type="button" @click="confirming = false">
            <svg class="ico solid" viewBox="0 0 24 24"><path d="M8 6.5v11l10-5.5z" /></svg>
            继续练习
          </button>
          <button type="button" class="primary" @click="confirmFinish">
            <svg class="ico" viewBox="0 0 24 24"><path d="M5 12.5 9.2 17 19 7.5" /></svg>
            结束练习
          </button>
        </div>
      </div>
    </div>

    <button v-if="started" type="button" class="help" @click="shortcuts = !shortcuts">?</button>
    <aside v-if="shortcuts" class="keys">
      <h3>快捷键</h3>
      <p><kbd>←</kbd> 上一题</p>
      <p><kbd>→</kbd> 下一题</p>
      <p><kbd>Enter</kbd> 提交答案 / 下一题</p>
      <p><kbd>?</kbd> 打开或关闭本面板</p>
      <p><kbd>Esc</kbd> 关闭弹窗</p>
    </aside>
  </div>
</template>

<style scoped>
.practice { height: calc(100vh - 64px); display: flex; background: #f5f7fa; position: relative; }
.practice.idle { align-items: center; justify-content: center; }
.sidebar {
  width: 280px; flex: none; display: flex; flex-direction: column; gap: 12px;
  padding: 20px; background: #fff; border-right: 1px solid #ebeef5; overflow: auto;
}
.back, .end, footer button, .primary, .modes button, .actions button, .help, .close {
  font: inherit; cursor: pointer;
}
.back { align-self: flex-start; border: none; background: transparent; color: #409eff; padding: 0; }
.sidebar h2 { margin: 0; font-size: 18px; line-height: 1.4; }
.progress-line { height: 8px; border-radius: 99px; background: #ebeef5; overflow: hidden; }
.progress-line span { display: block; height: 100%; background: #409eff; }
.progress-text, .timer { margin: 0; color: #606266; }
.timer { font-variant-numeric: tabular-nums; font-size: 20px; font-weight: 700; color: #303133; }
.sheet { display: grid; grid-template-columns: repeat(5, 1fr); gap: 8px; }
.nav-item {
  height: 36px; border: 1px solid #dcdfe6; border-radius: 8px; background: #fff; color: #606266;
}
.nav-item.correct { background: #67c23a; border-color: #67c23a; color: #fff; }
.nav-item.wrong { background: #f56c6c; border-color: #f56c6c; color: #fff; }
.nav-item.current { box-shadow: 0 0 0 2px #409eff; }
.nav-item.current:not(.correct):not(.wrong) { background: #409eff; border-color: #409eff; color: #fff; }
.end { margin-top: auto; height: 42px; border: none; border-radius: 8px; background: #f56c6c; color: #fff; }
.end:disabled { opacity: 0.6; cursor: default; }
.board { flex: 1; min-width: 0; overflow: auto; display: flex; flex-direction: column; }
.idle-panel, .question { width: min(800px, calc(100% - 48px)); margin: 28px auto; }
.idle-panel { text-align: center; color: #606266; }
.question { background: #fff; border-radius: 12px; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04); padding: 28px; }
.type { display: inline-flex; height: 24px; align-items: center; padding: 0 10px; border-radius: 99px; background: rgba(64, 158, 255, 0.12); color: #409eff; font-size: 13px; }
.stem, .pre { white-space: pre-wrap; line-height: 1.8; }
.stem { margin: 16px 0 0; font-size: 16px; }
.options { display: flex; flex-direction: column; gap: 12px; margin-top: 20px; }
.option {
  display: flex; gap: 12px; align-items: flex-start; text-align: left; padding: 14px 16px;
  border: 1px solid #dcdfe6; border-radius: 8px; background: #fff; font: inherit; color: inherit;
}
.option .key {
  flex: none; min-width: 28px; height: 28px; padding: 0 6px; border-radius: 99px; display: inline-flex;
  align-items: center; justify-content: center; background: #f5f7fa; color: #409eff; font-weight: 700;
}
.option.selected { border-color: #409eff; background: rgba(64, 158, 255, 0.06); }
.option.correct { border-color: #67c23a; background: rgba(103, 194, 58, 0.08); }
.option.wrong { border-color: #f56c6c; background: rgba(245, 108, 108, 0.08); }
.option.locked { cursor: default; }
.short { display: flex; flex-direction: column; gap: 12px; margin-top: 20px; }
textarea {
  min-height: 160px; padding: 16px; border: 2px solid #ebeef5; border-radius: 8px; font: inherit; line-height: 1.8; resize: vertical;
}
textarea:focus { outline: none; border-color: #409eff; }
.primary { align-self: flex-end; height: 40px; padding: 0 18px; border: none; border-radius: 8px; background: #409eff; color: #fff; }
.primary:disabled { opacity: 0.6; cursor: default; }
.loading { display: flex; align-items: center; gap: 10px; color: #606266; }
.loading i { width: 22px; height: 22px; border: 3px solid #ebeef5; border-top-color: #409eff; border-radius: 50%; animation: spin 0.8s linear infinite; }
.grade, .analysis { margin-top: 8px; padding: 20px; border-radius: 12px; background: #fff; }
.grade { border: 2px solid #dcdfe6; }
.grade header, .analysis header { display: flex; align-items: center; gap: 10px; }
.grade header em { margin-left: auto; font-style: normal; font-size: 20px; font-weight: 700; }
.grade header em.high { color: #67c23a; }
.grade header em.medium { color: #e6a23c; }
.grade header em.low { color: #f56c6c; }
.ai { width: 32px; height: 32px; border-radius: 50%; display: inline-flex; align-items: center; justify-content: center; color: #fff; background: linear-gradient(135deg, #409eff, #8b5cf6); }
.grade section { margin-top: 14px; padding: 12px 14px; border-radius: 8px; background: #f5f7fa; }
.grade h3 { margin: 0 0 6px; font-size: 13px; color: #909399; }
.grade p, .analysis p { margin: 0; line-height: 1.8; }
.points { display: flex; flex-wrap: wrap; gap: 8px; }
.points span { padding: 4px 12px; border-radius: 99px; background: rgba(64, 158, 255, 0.1); color: #409eff; font-size: 13px; }
.degraded { margin-top: 10px !important; color: #909399; font-size: 13px; }
.analysis { border-left: 4px solid #67c23a; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04); }
.badge { padding: 4px 10px; border-radius: 99px; font-size: 13px; }
.badge.correct { background: rgba(103, 194, 58, 0.12); color: #67c23a; }
.badge.wrong { background: rgba(245, 108, 108, 0.12); color: #f56c6c; }
.badge.empty { background: #f5f7fa; color: #909399; }
footer { display: flex; justify-content: flex-end; gap: 12px; width: min(800px, calc(100% - 48px)); margin: 0 auto 28px; }
footer button { height: 40px; padding: 0 16px; border: 1px solid #dcdfe6; border-radius: 8px; background: #fff; }
footer button:disabled { opacity: 0.5; cursor: default; }
.mask { position: fixed; inset: 0; z-index: 2000; display: flex; align-items: center; justify-content: center; background: rgba(0, 0, 0, 0.55); }
.dialog { width: min(520px, calc(100% - 32px)); background: #fff; border-radius: 12px; box-shadow: 0 12px 32px rgba(0, 0, 0, 0.18); }
.dialog header { position: relative; padding: 24px 24px 12px; text-align: center; }
.dialog h3 { margin: 0; font-size: 20px; }
.dialog header p { margin: 6px 0 0; color: #909399; font-size: 13px; }
.close { position: absolute; top: 12px; right: 12px; width: 28px; height: 28px; border: none; border-radius: 50%; background: transparent; color: #909399; }
.modes { display: flex; gap: 16px; padding: 8px 24px 24px; }
.modes button { flex: 1; display: flex; flex-direction: column; gap: 8px; align-items: center; padding: 24px 12px; border: 2px solid #dcdfe6; border-radius: 8px; background: #fff; }
.modes button:hover { border-color: #409eff; background: rgba(64, 158, 255, 0.05); }
.modes span:not(.mode-icon), .actions { color: #909399; font-size: 13px; }
.mode-icon { font-size: 32px; line-height: 1; }
.confirm { width: min(420px, calc(100% - 32px)); }
.confirm header { padding: 16px 28px 8px; }
.mark {
  width: 64px; height: 64px; margin: 28px auto 0; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
}
.mark svg { width: 30px; height: 30px; fill: none; stroke: currentColor; stroke-width: 2; stroke-linecap: round; stroke-linejoin: round; }
.mark.info svg { fill: currentColor; stroke: none; }
.mark.warn { background: #fdf6ec; color: #e6a23c; }
.mark.info { background: #ecf5ff; color: #409eff; }
.actions { display: flex; justify-content: center; gap: 12px; padding: 8px 24px 28px; }
.actions button {
  height: 36px; padding: 0 14px; border: 1px solid #dcdfe6; border-radius: 8px; background: #fff; color: #606266;
  display: inline-flex; align-items: center; gap: 6px;
}
.actions .ico { width: 16px; height: 16px; fill: none; stroke: currentColor; stroke-width: 2; stroke-linecap: round; stroke-linejoin: round; }
.actions .ico.solid { fill: currentColor; stroke: none; }
.help { position: fixed; right: 24px; bottom: 24px; width: 36px; height: 36px; border: none; border-radius: 50%; background: #fff; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12); color: #409eff; font-weight: 700; }
.keys { position: fixed; right: 24px; bottom: 72px; width: 220px; padding: 16px; border-radius: 12px; background: #fff; box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12); }
.keys h3 { margin: 0 0 8px; }
.keys p { margin: 6px 0; color: #606266; }
kbd { display: inline-block; min-width: 1.6em; padding: 0 6px; border: 1px solid #dcdfe6; border-radius: 4px; background: #f5f7fa; text-align: center; }
@keyframes spin { to { transform: rotate(360deg); } }
@media (max-width: 768px) {
  .practice { height: auto; min-height: calc(100vh - 64px); flex-direction: column; }
  .sidebar { width: auto; border-right: none; border-bottom: 1px solid #ebeef5; }
  .modes { flex-direction: column; }
}
</style>
