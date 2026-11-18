<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { ApiResponse } from '@/types/api'
import { difficultyMeta, finishInterview, gradeInterviewAnswer, scoreTone } from '@/api/interview'
import {
  clock,
  formatElapsed,
  questionMessage,
  useInterviewStore,
  type DraftMessage,
  type InterviewDraft,
} from '@/store/interview'

const TIMEOUT_SECONDS = 5 * 60
const PHASE_TEXT: Record<string, string> = {
  analyzing: 'AI面试官正在分析你的回答...',
  grading: '正在评分...',
  thinking: '思考中',
}

const router = useRouter()
const store = useInterviewStore()
const input = ref('')
const busy = ref(false)
const online = ref(navigator.onLine)
const sideOpen = ref(true)
const listening = ref(false)
const tick = ref(Date.now())
const timeoutFired = ref(false)
const chatRef = ref<HTMLElement | null>(null)
interface BrowserRecognition {
  lang: string
  interimResults: boolean
  continuous: boolean
  onresult: ((event: { results: ArrayLike<ArrayLike<{ transcript: string }>> }) => void) | null
  onerror: (() => void) | null
  onend: (() => void) | null
  start: () => void
  stop: () => void
}

let timer = 0
let enteredAt = Date.now()
let recognition: BrowserRecognition | null = null

const draft = computed(() => store.draft)
const level = computed(() => difficultyMeta(draft.value?.difficulty ?? 2).label)
const answered = computed(() => draft.value?.questions.filter((item) => item.score !== null).length ?? 0)
const average = computed(() => {
  const scored = draft.value?.questions.filter((item) => item.score !== null) ?? []
  if (!scored.length) return null
  const sum = scored.reduce((total, item) => total + (item.score ?? 0), 0)
  return (sum / scored.length).toFixed(1)
})
const elapsedText = computed(() => formatElapsed((draft.value?.elapsedMs ?? 0) + Math.max(0, tick.value - enteredAt)))
const remainText = computed(() => {
  void tick.value
  const start = draft.value?.questionStartedAt ?? Date.now()
  const remain = Math.max(0, TIMEOUT_SECONDS - Math.floor((Date.now() - start) / 1000))
  const minutes = Math.floor(remain / 60)
  const seconds = remain % 60
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
})
const progress = computed(() => {
  const total = draft.value?.questionCount || 1
  return Math.round(answered.value * 100 / total)
})

watch(() => draft.value?.messages.length, async () => {
  await nextTick()
  if (chatRef.value) chatRef.value.scrollTop = chatRef.value.scrollHeight
})

onMounted(() => {
  window.scrollTo(0, 0)
  store.reload()
  const current = store.draft
  if (!current) {
    router.replace({ name: 'interview-config' })
    return
  }
  reconcile(current)
  const opening = current.messages.find((item) => item.role === 'ai' && item.order === current.currentOrder)
  if (opening && current.phase === 'asking') speak(opening.text, true)
  enteredAt = Date.now()
  window.addEventListener('online', onOnline)
  window.addEventListener('offline', onOffline)
  timer = window.setInterval(() => {
    tick.value = Date.now()
    flushElapsed()
    maybeTimeout()
  }, 1000)
})

onUnmounted(() => {
  window.clearInterval(timer)
  window.removeEventListener('online', onOnline)
  window.removeEventListener('offline', onOffline)
  flushElapsed()
  window.speechSynthesis?.cancel()
  recognition?.stop()
})

function onOnline() { online.value = true }
function onOffline() { online.value = false }

function reconcile(current: InterviewDraft) {
  const pending = current.questions.find((item) => item.score === null)
  if (!pending) {
    current.phase = 'done'
    if (!current.messages.some((item) => item.id === 'closing')) {
      pushMessage(current, 'ai', '本次面试题目已全部完成。感谢你的回答，可以查看面试报告了解详细评估。', 'closing')
    }
  } else {
    current.phase = 'asking'
    current.currentOrder = pending.order
    if (!current.messages.some((item) => item.role === 'ai' && item.order === pending.order)) {
      const index = current.questions.findIndex((item) => item.order === pending.order)
      pushMessage(current, 'ai', questionMessage(pending, Math.max(0, index), current.questionCount, current.difficulty), `q-${pending.order}`, pending.order)
    }
  }
  store.persist(current)
}

function flushElapsed() {
  const current = store.draft
  if (!current) return
  const now = Date.now()
  current.elapsedMs += Math.max(0, now - enteredAt)
  enteredAt = now
  store.persist(current)
}

function maybeTimeout() {
  const current = store.draft
  if (!current || current.phase !== 'asking' || busy.value || timeoutFired.value) return
  const elapsed = Math.floor((Date.now() - current.questionStartedAt) / 1000)
  if (elapsed < TIMEOUT_SECONDS) return
  timeoutFired.value = true
  const text = input.value.trim() || '（超时未作答）'
  ElMessage.warning('本题已超时，已提交当前回答')
  void send(text)
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    void send(input.value)
  }
}

async function send(raw: string) {
  const current = store.draft
  const text = raw.trim()
  if (!current || current.phase !== 'asking' || busy.value) return
  if (!text) return
  if (!online.value) {
    ElMessage.warning('当前网络不可用，恢复后再发送')
    return
  }
  const question = current.questions.find((item) => item.order === current.currentOrder)
  if (!question || question.score !== null) return
  const duration = Math.min(TIMEOUT_SECONDS, Math.max(0, Math.floor((Date.now() - current.questionStartedAt) / 1000)))
  question.answer = text
  question.durationSeconds = duration
  input.value = ''
  window.speechSynthesis?.cancel()
  recognition?.stop()
  listening.value = false
  pushMessage(current, 'user', text, `u-${question.order}`)
  const status = pushMessage(current, 'status', PHASE_TEXT.analyzing, `s-${question.order}`)
  busy.value = true
  store.persist(current)
  try {
    let preview = ''
    const grade = await gradeInterviewAnswer(current.sessionId, {
      order: question.order,
      studentAnswer: text,
      durationSeconds: duration,
    }, (phase) => {
      if (preview) return
      const found = current.messages.find((item) => item.id === status.id)
      if (found) found.text = PHASE_TEXT[phase] || found.text
    }, (delta) => {
      preview += delta
      const found = current.messages.find((item) => item.id === status.id)
      if (found) found.text = preview
    })
    question.score = grade.score
    question.feedback = grade.feedback
    question.degraded = grade.degraded
    removeMessage(current, status.id)
    const note = grade.degraded ? '\n大模型本次未能评分，已按关键词规则评分。' : ''
    pushMessage(current, 'ai', `本题得分 ${grade.score} 分。${grade.feedback}${note}`, `f-${question.order}`, undefined, true)
    const next = current.questions.find((item) => item.score === null)
    if (next) {
      current.currentOrder = next.order
      current.questionStartedAt = Date.now()
      current.phase = 'asking'
      timeoutFired.value = false
      const index = current.questions.findIndex((item) => item.order === next.order)
      pushMessage(current, 'ai', questionMessage(next, index, current.questionCount, current.difficulty), `q-${next.order}`, next.order, true)
    } else {
      current.phase = 'done'
      pushMessage(current, 'ai', '本次面试题目已全部完成。感谢你的回答，可以查看面试报告了解详细评估。', 'closing', undefined, true)
      store.persist(current)
      await askReport()
      return
    }
    store.persist(current)
  } catch (error) {
    question.answer = ''
    question.durationSeconds = 0
    removeMessage(current, `u-${question.order}`)
    removeMessage(current, status.id)
    input.value = text === '（超时未作答）' ? '' : text
    timeoutFired.value = false
    store.persist(current)
    const code = (error as ApiResponse<unknown>)?.code
    if (code === 20008) {
      ElMessage.error('面试会话已过期，请重新开始')
      store.persist(null)
      router.replace({ name: 'interview-config' })
      return
    }
    ElMessage.error(messageOf(error, '评分失败，请重试'))
  } finally {
    busy.value = false
  }
}

async function askReport() {
  try {
    await ElMessageBox.confirm('本次面试已结束，是否查看面试报告？', '面试完成', {
      confirmButtonText: '查看报告',
      cancelButtonText: '稍后查看',
    })
    await finish()
  } catch {
    // 留在本页，用户可以稍后再打开报告。
  }
}

async function finish() {
  const current = store.draft
  if (!current || busy.value) return
  busy.value = true
  try {
    if (!current.finishKey) current.finishKey = crypto.randomUUID()
    const pending = current.questions.find((item) => item.order === current.currentOrder && item.score === null)
    if (pending && current.phase === 'asking' && input.value.trim()) {
      pending.answer = input.value.trim()
      pending.durationSeconds = Math.min(TIMEOUT_SECONDS, Math.max(0, Math.floor((Date.now() - current.questionStartedAt) / 1000)))
    }
    const response = await finishInterview(current.sessionId, current.questions.map((item) => ({
      order: item.order,
      studentAnswer: item.answer || null,
      durationSeconds: item.durationSeconds || 0,
    })), current.finishKey)
    const reportId = response.data.data.reportId
    store.persist(null)
    router.push({ name: 'interview-report', query: { id: String(reportId) } })
  } catch (error) {
    const code = (error as ApiResponse<unknown>)?.code
    if (code === 20008) {
      ElMessage.error('面试会话已过期，请重新开始')
      store.persist(null)
      router.replace({ name: 'interview-config' })
      return
    }
    ElMessage.error(messageOf(error, '生成报告失败'))
  } finally {
    busy.value = false
  }
}

async function endInterview() {
  if (busy.value) return
  try {
    await ElMessageBox.confirm('结束面试后将根据已作答内容生成报告，未作答题目记 0 分。', '结束面试', {
      confirmButtonText: '结束并生成报告',
      cancelButtonText: '继续面试',
      type: 'warning',
    })
  } catch {
    return
  }
  await finish()
}

async function exitInterview() {
  try {
    await ElMessageBox.confirm('退出后本次进度会保留在本机，下次可以继续。', '退出面试', {
      confirmButtonText: '退出',
      cancelButtonText: '留下',
    })
  } catch {
    return
  }
  flushElapsed()
  router.push({ name: 'interview-config' })
}

function locate(order: number) {
  const current = store.draft
  const question = current?.questions.find((item) => item.order === order)
  if (!question || (question.score === null && order !== current?.currentOrder)) {
    ElMessage.info('请先完成当前题目')
    return
  }
  document.getElementById(`q-${order}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

function listen() {
  if (listening.value) {
    recognition?.stop()
    listening.value = false
    return
  }
  const Ctor = recognitionCtor()
  if (!Ctor) {
    ElMessage.warning('当前浏览器不支持语音识别，已填入模拟文本，请修改后发送')
    input.value = `${input.value} 这是一段模拟识别的回答，请改成你自己的话。`.trim()
    return
  }
  recognition = new Ctor()
  recognition.lang = 'zh-CN'
  recognition.interimResults = false
  recognition.continuous = false
  recognition.onresult = (event) => {
    const text = event.results[event.results.length - 1]?.[0]?.transcript ?? ''
    if (text) input.value = `${input.value} ${text}`.trim()
  }
  recognition.onerror = () => {
    listening.value = false
    ElMessage.warning('语音识别失败，请改为文字输入')
  }
  recognition.onend = () => {
    listening.value = false
  }
  recognition.start()
  listening.value = true
}

function toggleVoice() {
  const current = store.draft
  if (!current) return
  current.voiceOn = !current.voiceOn
  if (!current.voiceOn) window.speechSynthesis?.cancel()
  store.persist(current)
}

function replay(text: string) {
  speak(text, true)
}

function speak(text: string, interrupt: boolean) {
  if (!store.draft?.voiceOn || !window.speechSynthesis) return
  if (interrupt) window.speechSynthesis.cancel()
  const utter = new SpeechSynthesisUtterance(text.replace(/\s+/g, ' '))
  utter.lang = 'zh-CN'
  window.speechSynthesis.speak(utter)
}

function pushMessage(current: InterviewDraft, role: DraftMessage['role'], text: string, id: string, order?: number, audible = false) {
  const message: DraftMessage = { id, role, text, time: clock(), order }
  current.messages.push(message)
  if (audible && role === 'ai') speak(text, false)
  return message
}

function removeMessage(current: InterviewDraft, id: string) {
  const index = current.messages.findIndex((item) => item.id === id)
  if (index >= 0) current.messages.splice(index, 1)
}

function shortText(content: string) {
  const compact = content.replace(/\s+/g, '')
  return compact.length > 16 ? `${compact.slice(0, 16)}…` : compact
}

function messageOf(error: unknown, fallback: string) {
  if (error instanceof DOMException && error.name === 'AbortError') return '评分超时，请重试'
  const body = error as ApiResponse<unknown>
  return body?.message || fallback
}

function recognitionCtor() {
  const host = window as Window & {
    SpeechRecognition?: new () => BrowserRecognition
    webkitSpeechRecognition?: new () => BrowserRecognition
  }
  return host.SpeechRecognition || host.webkitSpeechRecognition || null
}
</script>

<template>
  <div v-if="draft" class="room">
    <section class="main">
      <header class="top">
        <div class="identity">
          <button type="button" class="text" @click="exitInterview">退出面试</button>
          <div>
            <h1>模拟面试</h1>
            <p>{{ draft.subjectName }} · {{ level }}</p>
          </div>
        </div>
        <div class="tools">
          <span class="badge" :class="online ? 'on' : 'off'"><i></i>{{ online ? '在线' : '离线' }}</span>
          <span class="chip">总用时 {{ elapsedText }}</span>
          <span class="chip">本题剩余 {{ remainText }}</span>
          <span class="chip">题目 {{ Math.min(draft.currentOrder, draft.questionCount) }}/{{ draft.questionCount }}</span>
          <button type="button" class="text" @click="toggleVoice">语音播报 {{ draft.voiceOn ? '开' : '关' }}</button>
          <button v-if="draft.phase !== 'done'" type="button" class="end" :disabled="busy" @click="endInterview">结束面试</button>
          <button v-else type="button" class="end go" :disabled="busy" @click="finish">查看报告</button>
        </div>
        <div class="progress">
          <span>答题进度 {{ answered }}/{{ draft.questionCount }}</span>
          <span class="bar"><i :style="{ width: `${progress}%` }"></i></span>
          <span>平均分 {{ average ?? '—' }}</span>
          <span>已用时间 {{ elapsedText }}</span>
          <em>{{ level }}</em>
        </div>
      </header>

      <div ref="chatRef" class="chat">
        <article v-for="item in draft.messages" :id="item.id" :key="item.id" class="bubble" :class="item.role">
          <p>{{ item.text }}</p>
          <footer>
            <time>{{ item.time }}</time>
            <button v-if="item.role === 'ai'" type="button" @click="replay(item.text)">重播</button>
          </footer>
          <i v-if="item.role === 'status'" class="dots"></i>
        </article>
      </div>

      <form v-if="draft.phase === 'asking'" class="composer" @submit.prevent="send(input)">
        <textarea
          v-model="input"
          rows="3"
          placeholder="输入回答，Enter 发送，Shift+Enter 换行"
          :disabled="busy"
          @keydown="onKeydown"
        ></textarea>
        <div>
          <button type="button" class="ghost" :class="{ listening }" @click="listen">{{ listening ? '停止识别' : '语音输入' }}</button>
          <button type="submit" class="send" :disabled="busy || !input.trim()">发送</button>
        </div>
      </form>
    </section>

    <aside class="side" :class="{ shut: !sideOpen }">
      <button type="button" class="fold" @click="sideOpen = !sideOpen">{{ sideOpen ? '收起' : '展开题目' }}</button>
      <template v-if="sideOpen">
        <h2>面试题目</h2>
        <button
          v-for="item in draft.questions"
          :key="item.order"
          type="button"
          class="q"
          :class="{ current: item.order === draft.currentOrder && draft.phase !== 'done' }"
          @click="locate(item.order)"
        >
          <span>{{ item.order }}. {{ shortText(item.content) }}</span>
          <em v-if="item.score !== null" :class="scoreTone(item.score)">{{ item.score }}</em>
        </button>
        <h2>面试统计</h2>
        <ul>
          <li><span>已回答</span><strong>{{ answered }}</strong></li>
          <li><span>总题数</span><strong>{{ draft.questionCount }}</strong></li>
          <li><span>平均分</span><strong>{{ average ?? '—' }}</strong></li>
          <li><span>用时</span><strong>{{ elapsedText }}</strong></li>
        </ul>
      </template>
    </aside>
  </div>
</template>

<style scoped>
.room { min-height: calc(100vh - 64px); display: grid; grid-template-columns: minmax(0, 1fr) 280px; gap: 16px; padding: 20px; background: linear-gradient(135deg, #1a1a2e 0%, #16213e 100%); color: #fff; }
.main, .side { min-width: 0; }
.top, .side, .composer { background: rgba(255, 255, 255, 0.08); border-radius: 16px; }
.top { padding: 16px 20px; }
.identity, .tools, .progress { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.identity h1 { margin: 0; font-size: 16px; }
.identity p, .progress { margin: 4px 0 0; color: rgba(255, 255, 255, 0.7); font-size: 13px; }
.tools { margin-left: auto; }
.badge, .chip, .text, .end, .ghost, .send, .fold, .q { border: 0; border-radius: 999px; }
.badge, .chip { display: inline-flex; align-items: center; gap: 6px; padding: 6px 12px; background: rgba(255, 255, 255, 0.1); }
.badge i { width: 8px; height: 8px; border-radius: 50%; background: #f56c6c; }
.badge.on i { background: #67c23a; }
.text, .fold { background: transparent; color: rgba(255, 255, 255, 0.8); cursor: pointer; }
.end { padding: 8px 16px; color: #f56c6c; background: rgba(245, 108, 108, 0.18); cursor: pointer; }
.end.go { color: #fff; background: #409eff; }
.progress { margin-top: 12px; padding-top: 12px; border-top: 1px solid rgba(255, 255, 255, 0.08); }
.bar { width: 160px; height: 8px; border-radius: 99px; background: rgba(255, 255, 255, 0.12); overflow: hidden; }
.bar i { display: block; height: 100%; background: #409eff; }
.progress em { font-style: normal; padding: 2px 8px; border-radius: 99px; background: rgba(64, 158, 255, 0.2); }
.chat { height: calc(100vh - 360px); min-height: 280px; margin: 16px 0; overflow: auto; display: flex; flex-direction: column; gap: 12px; }
.bubble { max-width: min(720px, 100%); padding: 12px 14px; border-radius: 14px; background: rgba(255, 255, 255, 0.08); }
.bubble.user { align-self: flex-end; background: #409eff; }
.bubble.status { color: #e6a23c; }
.bubble p { margin: 0; white-space: pre-wrap; line-height: 1.7; }
.bubble footer { display: flex; gap: 10px; margin-top: 6px; color: rgba(255, 255, 255, 0.65); font-size: 12px; }
.bubble button { border: 0; background: transparent; color: inherit; cursor: pointer; }
.dots { display: inline-block; width: 8px; height: 8px; margin-left: 6px; border-radius: 50%; background: #e6a23c; animation: blink 1s infinite; }
.composer { display: flex; gap: 12px; padding: 12px; }
textarea { flex: 1; resize: vertical; border: 0; border-radius: 10px; padding: 10px 12px; background: rgba(0, 0, 0, 0.25); color: #fff; }
.composer div { display: flex; flex-direction: column; gap: 8px; }
.ghost, .send { padding: 8px 14px; cursor: pointer; }
.ghost { color: #fff; background: rgba(255, 255, 255, 0.12); }
.ghost.listening { background: #e6a23c; color: #1a1a2e; }
.send { color: #fff; background: #409eff; }
.send:disabled, .end:disabled { opacity: 0.5; cursor: not-allowed; }
.side { padding: 16px; align-self: start; }
.side.shut { padding-bottom: 12px; }
.side h2 { margin: 16px 0 8px; font-size: 14px; }
.q { width: 100%; display: flex; justify-content: space-between; gap: 8px; margin-bottom: 8px; padding: 8px 10px; text-align: left; color: #fff; background: rgba(255, 255, 255, 0.06); cursor: pointer; border-radius: 10px; }
.q.current { background: rgba(64, 158, 255, 0.28); }
.q em { font-style: normal; font-weight: 700; }
.q em.high, .high { color: #67c23a; }
.q em.mid { color: #e6a23c; }
.q em.low { color: #f56c6c; }
.side ul { list-style: none; margin: 0; padding: 0; }
.side li { display: flex; justify-content: space-between; padding: 6px 0; color: rgba(255, 255, 255, 0.75); }
@keyframes blink { 50% { opacity: 0.2; } }
@media (max-width: 900px) {
  .room { grid-template-columns: 1fr; }
  .tools { margin-left: 0; }
  .chat { height: auto; max-height: 60vh; }
  .composer { flex-direction: column; }
}
</style>
