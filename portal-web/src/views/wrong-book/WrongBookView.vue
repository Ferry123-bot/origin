<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { ApiResponse } from '@/types/api'
import {
  fetchWrongQuestions,
  fetchWrongStats,
  markWrongMastered,
  redoWrongQuestions,
  removeWrongQuestion,
  type WrongItem,
  type WrongStats,
} from '@/api/wrong-book'
import { submitPracticeAnswers, type PracticeQuestion, type PracticeResult } from '@/api/practice'

const TYPE_LABEL: Record<number, string> = { 1: '单选', 2: '多选', 3: '判断', 4: '简答' }

const loading = ref(false)
const listLoading = ref(false)
const mode = ref<'browse' | 'retry'>('browse')
const stats = ref<WrongStats>({ total: 0, toReviewCount: 0, masteredCount: 0, masteryRate: 0, top3: [] })
const items = ref<WrongItem[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const status = ref(0)
const type = ref(0)
const days = ref(0)
const sort = ref<'recent' | 'count'>('recent')
const selected = ref<number[]>([])
const confirmText = ref('')
const confirming = ref(false)
let confirmAction: (() => Promise<void>) | null = null

const practiceId = ref('')
const retryQuestions = ref<PracticeQuestion[]>([])
const retryIndex = ref(0)
const retryAnswers = ref<(string | null)[]>([])
const retryResults = ref<(PracticeResult | null)[]>([])
const retryEmpty = ref(false)
const retryBusy = ref(false)

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))
const currentRetry = computed(() => retryQuestions.value[retryIndex.value] ?? null)
const retryResult = computed(() => retryResults.value[retryIndex.value] ?? null)
const retryHasOptions = computed(() => (currentRetry.value?.options.length ?? 0) > 0)

function messageOf(error: unknown, fallback: string) {
  return (error as ApiResponse<unknown>)?.message || fallback
}

function levelOf(count: number) {
  if (count >= 3) return { key: 'high', label: '高频易错' }
  if (count === 2) return { key: 'medium', label: '多次错误' }
  return { key: 'low', label: '偶尔错误' }
}

function catalog(item: WrongItem) {
  return [item.subjectName, item.stageName].filter(Boolean).join(' · ') || '未分类'
}

function dayOf(value?: string | null) {
  return value ? value.slice(0, 10) : '—'
}

function showAnswer(value: string) {
  if (!value) return '—'
  return /^[A-Za-z正确错误]+([,，][A-Za-z正确错误]+)*$/.test(value) ? value.split(/[,，]/).join('、') : value
}

function progress(count: number) {
  return `${Math.min(100, Math.round((Math.min(count, 3) / 3) * 100))}%`
}

onMounted(() => {
  void loadAll()
})

async function loadAll() {
  loading.value = true
  try {
    await Promise.all([loadStats(), loadList()])
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  const response = await fetchWrongStats()
  stats.value = response.data.data
}

async function loadList() {
  listLoading.value = true
  try {
    const response = await fetchWrongQuestions({
      status: status.value || undefined,
      type: type.value || undefined,
      days: days.value || undefined,
      sort: sort.value,
      page: page.value,
      pageSize,
    })
    items.value = response.data.data.list
    total.value = response.data.data.total
    selected.value = []
  } catch (error) {
    ElMessage.error(messageOf(error, '加载错题失败'))
  } finally {
    listLoading.value = false
  }
}

function pickStatus(value: number) {
  status.value = value
  page.value = 1
  void loadList()
}

function pickType(value: number) {
  type.value = value
  page.value = 1
  void loadList()
}

function pickDays(value: number) {
  days.value = value
  page.value = 1
  void loadList()
}

function pickSort(value: 'recent' | 'count') {
  sort.value = value
  page.value = 1
  void loadList()
}

function changePage(next: number) {
  if (next < 1 || next > totalPages.value || next === page.value) return
  page.value = next
  void loadList()
}

function toggle(id: number, checked: boolean) {
  selected.value = checked ? [...selected.value, id] : selected.value.filter((item) => item !== id)
}

function ask(text: string, action: () => Promise<void>) {
  confirmText.value = text
  confirmAction = action
  confirming.value = true
}

async function runConfirm() {
  const action = confirmAction
  confirming.value = false
  confirmAction = null
  if (!action) return
  try {
    await action()
  } catch (error) {
    ElMessage.error(messageOf(error, '操作失败'))
  }
}

function markOne(item: WrongItem) {
  ask('确定将这道题标记为已掌握吗？', async () => {
    await markWrongMastered(item.id)
    ElMessage.success('已标记为已掌握')
    await loadAll()
  })
}

function removeOne(item: WrongItem) {
  ask('确定从错题本移除这道题吗？', async () => {
    await removeWrongQuestion(item.id)
    ElMessage.success('已移除')
    if (items.value.length === 1 && page.value > 1) page.value -= 1
    await loadAll()
  })
}

function batchMaster() {
  const ids = items.value.filter((item) => selected.value.includes(item.id) && item.status === 1).map((item) => item.id)
  if (ids.length === 0) {
    ElMessage.warning('所选错题已掌握')
    return
  }
  ask(`确定将所选 ${ids.length} 道题标记为已掌握吗？`, async () => {
    for (const id of ids) await markWrongMastered(id)
    ElMessage.success('已标记为已掌握')
    await loadAll()
  })
}

function batchRemove() {
  const ids = [...selected.value]
  ask(`确定移除所选 ${ids.length} 道题吗？`, async () => {
    for (const id of ids) await removeWrongQuestion(id)
    ElMessage.success('已移除')
    page.value = 1
    await loadAll()
  })
}

async function openRedo(ids: number[]) {
  retryBusy.value = true
  retryEmpty.value = false
  try {
    const response = await redoWrongQuestions(ids)
    const paper = response.data.data
    practiceId.value = paper.practiceId
    retryQuestions.value = paper.questions
    retryIndex.value = 0
    retryAnswers.value = Array(paper.questions.length).fill(null)
    retryResults.value = Array(paper.questions.length).fill(null)
    mode.value = 'retry'
    selected.value = []
  } catch (error) {
    ElMessage.error(messageOf(error, '无法开始重做'))
  } finally {
    retryBusy.value = false
  }
}

async function enterRetry() {
  selected.value = []
  retryBusy.value = true
  try {
    const response = await fetchWrongQuestions({ status: 1, sort: 'recent', page: 1, pageSize: 100 })
    const ids = response.data.data.list.map((item) => item.id)
    if (ids.length === 0) {
      retryEmpty.value = true
      retryQuestions.value = []
      mode.value = 'retry'
      return
    }
    await openRedo(ids)
  } catch (error) {
    retryEmpty.value = true
    ElMessage.error(messageOf(error, '无法开始重做'))
  } finally {
    retryBusy.value = false
  }
}

function exitRetry() {
  mode.value = 'browse'
  retryQuestions.value = []
  retryEmpty.value = false
  void loadAll()
}

function chooseRetry(key: string) {
  const question = currentRetry.value
  if (!question || retryResult.value || !retryHasOptions.value) return
  if (question.type === 2) {
    const keys = new Set(split(retryAnswers.value[retryIndex.value]))
    if (keys.has(key)) keys.delete(key)
    else keys.add(key)
    const next = [...keys].sort()
    retryAnswers.value[retryIndex.value] = next.length ? next.join(',') : null
  } else {
    retryAnswers.value[retryIndex.value] = key
  }
}

function split(value: string | null | undefined) {
  return value ? value.split(',').filter(Boolean) : []
}

async function submitRetry() {
  const question = currentRetry.value
  const answer = retryAnswers.value[retryIndex.value]
  if (!question || retryResult.value) return
  if (!answer) {
    ElMessage.warning('请先选择一个答案')
    return
  }
  retryBusy.value = true
  try {
    const response = await submitPracticeAnswers(practiceId.value, [{
      questionId: question.id,
      answer,
      durationSeconds: 0,
    }])
    retryResults.value[retryIndex.value] = response.data.data.results[0] ?? null
    await loadStats()
  } catch (error) {
    ElMessage.error(messageOf(error, '提交失败'))
  } finally {
    retryBusy.value = false
  }
}

function moveRetry(step: number) {
  const question = currentRetry.value
  const next = retryIndex.value + step
  if (next < 0 || next >= retryQuestions.value.length) return
  if (step > 0 && question && retryHasOptions.value && !retryResult.value) {
    ElMessage.warning('请先提交答案')
    return
  }
  retryIndex.value = next
}

function optionClass(key: string) {
  const chosen = split(retryAnswers.value[retryIndex.value]).includes(key)
  const result = retryResult.value
  const right = result ? split(result.answer).includes(key) : false
  return {
    selected: chosen && !result,
    correct: !!result && right,
    wrong: !!result && chosen && !right,
  }
}
</script>

<template>
  <div v-loading="loading" class="book">
    <header class="banner">
      <div class="inner">
        <div>
          <h1>我的错题本</h1>
          <p>记录错题，针对性复习，攻克薄弱点</p>
        </div>
        <nav><router-link to="/">首页</router-link><span>/</span><span>错题本</span></nav>
      </div>
    </header>

    <div class="inner body">
      <div class="modes">
        <button type="button" :class="{ on: mode === 'browse' }" @click="mode === 'retry' ? exitRetry() : undefined">浏览模式</button>
        <button type="button" :class="{ on: mode === 'retry' }" @click="enterRetry">重做模式</button>
      </div>

      <template v-if="mode === 'browse'">
        <section class="stats">
          <article><strong>{{ stats.total }}</strong><span>错题总数</span></article>
          <article><strong>{{ stats.toReviewCount }}</strong><span>待复习</span></article>
          <article><strong>{{ stats.masteredCount }}</strong><span>已掌握</span></article>
          <article><strong>{{ stats.masteryRate }}%</strong><span>掌握率</span></article>
          <article class="top">
            <span>高频错题</span>
            <p v-if="stats.top3.length === 0">暂无高频错题</p>
            <button v-for="(item, index) in stats.top3" :key="item.id" type="button" @click="openRedo([item.id])">
              <em>{{ index + 1 }}. {{ item.content }}</em>
              <b>{{ item.errorCount }}次</b>
            </button>
          </article>
        </section>

        <div class="filters">
          <div>
            <button type="button" :class="{ on: status === 0 }" @click="pickStatus(0)">全部</button>
            <button type="button" :class="{ on: status === 1 }" @click="pickStatus(1)">待复习</button>
            <button type="button" :class="{ on: status === 2 }" @click="pickStatus(2)">已掌握</button>
          </div>
          <div>
            <button type="button" :class="{ on: type === 0 }" @click="pickType(0)">全部题型</button>
            <button v-for="(label, value) in TYPE_LABEL" :key="value" type="button" :class="{ on: type === Number(value) }" @click="pickType(Number(value))">{{ label }}</button>
          </div>
          <div>
            <button type="button" :class="{ on: days === 0 }" @click="pickDays(0)">全部时间</button>
            <button type="button" :class="{ on: days === 7 }" @click="pickDays(7)">近 7 天</button>
            <button type="button" :class="{ on: days === 30 }" @click="pickDays(30)">近 30 天</button>
          </div>
          <div>
            <button type="button" :class="{ on: sort === 'recent' }" @click="pickSort('recent')">最近答错</button>
            <button type="button" :class="{ on: sort === 'count' }" @click="pickSort('count')">错误次数</button>
          </div>
        </div>

        <div v-loading="listLoading">
          <div v-if="!listLoading && items.length === 0" class="empty">
            <span>🎉</span>
            <p>暂无错题</p>
          </div>
          <article v-for="item in items" :key="item.id" class="card" :class="item.status === 2 ? 'mastered' : levelOf(item.errorCount).key">
            <input type="checkbox" :checked="selected.includes(item.id)" @change="toggle(item.id, ($event.target as HTMLInputElement).checked)">
            <div>
              <header class="progress">
                <em :class="levelOf(item.errorCount).key">{{ item.errorCount }}次 · {{ levelOf(item.errorCount).label }}</em>
                <i><b :style="{ width: progress(item.correctedCount) }" /></i>
                <span>已纠正 {{ Math.min(item.correctedCount, 3) }}/3 次</span>
              </header>
              <h2>{{ item.status === 2 ? '✅ ' : '' }}{{ item.content }}</h2>
              <p class="meta">
                <span>{{ TYPE_LABEL[item.type] || '题目' }}</span>
                <span>{{ catalog(item) }}</span>
                <span>{{ dayOf(item.lastWrongAt) }}</span>
              </p>
              <div class="answers">
                <div><span>你的答案</span><p>{{ showAnswer(item.userAnswer) }}</p></div>
                <div><span>正确答案</span><p>{{ showAnswer(item.correctAnswer) }}</p></div>
              </div>
              <div class="actions">
                <button v-if="item.status === 1" type="button" class="primary" @click="openRedo([item.id])">重新做题</button>
                <button v-if="item.status === 1" type="button" @click="markOne(item)">标记已掌握</button>
                <button type="button" @click="removeOne(item)">移除错题</button>
              </div>
            </div>
          </article>
        </div>

        <footer v-if="total > 0" class="pager">
          <span>共 {{ total }} 道</span>
          <div>
            <button type="button" :disabled="page === 1" @click="changePage(page - 1)">‹</button>
            <button v-for="number in totalPages" :key="number" type="button" :class="{ on: number === page }" @click="changePage(number)">{{ number }}</button>
            <button type="button" :disabled="page === totalPages" @click="changePage(page + 1)">›</button>
          </div>
        </footer>
      </template>

      <section v-else v-loading="retryBusy" class="retry">
        <div v-if="retryEmpty" class="empty">
          <span>🎉</span>
          <p>没有待复习的错题</p>
          <button type="button" class="primary" @click="exitRetry">返回浏览</button>
        </div>
        <template v-else-if="currentRetry">
          <header>
            <strong>错题重做练习</strong>
            <span>第 {{ retryIndex + 1 }}/{{ retryQuestions.length }} 题</span>
          </header>
          <h2>{{ currentRetry.content }}</h2>
          <div v-if="!retryHasOptions" class="plain">
            <p>此题暂无可选的答题选项，请自行复习</p>
            <div><span>正确答案</span><p>{{ showAnswer(currentRetry.answer) }}</p></div>
          </div>
          <div v-else class="options">
            <button
              v-for="option in currentRetry.options"
              :key="option.key"
              type="button"
              :class="optionClass(option.key)"
              @click="chooseRetry(option.key)"
            >
              <b>{{ option.key }}</b>
              <span>{{ option.text }}</span>
            </button>
          </div>
          <div v-if="retryResult" class="feedback" :class="retryResult.isCorrect ? 'ok' : 'bad'">
            <strong>{{ retryResult.isCorrect ? '回答正确' : '回答错误' }}</strong>
            <p v-if="retryResult.analysis">{{ retryResult.analysis }}</p>
          </div>
          <footer>
            <button type="button" @click="exitRetry">退出练习</button>
            <div>
              <button type="button" :disabled="retryIndex === 0" @click="moveRetry(-1)">上一题</button>
              <button v-if="retryHasOptions && !retryResult" type="button" class="primary" :disabled="retryBusy" @click="submitRetry">提交答案</button>
              <button type="button" :disabled="retryIndex === retryQuestions.length - 1" @click="moveRetry(1)">下一题</button>
            </div>
          </footer>
        </template>
      </section>
    </div>

    <div v-if="selected.length && mode === 'browse'" class="batch">
      <span>已选择 {{ selected.length }} 项</span>
      <button type="button" class="ok" @click="batchMaster">批量标记已掌握</button>
      <button type="button" class="danger" @click="batchRemove">批量移除</button>
      <button type="button" @click="selected = []">取消选择</button>
    </div>

    <div v-if="confirming" class="mask">
      <div class="dialog" role="dialog">
        <h3>{{ confirmText }}</h3>
        <div>
          <button type="button" @click="confirming = false">取消</button>
          <button type="button" class="primary" @click="runConfirm">确定</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.book { color: #303133; }
.banner { padding: 36px 0; color: #fff; background: linear-gradient(135deg, #409eff, #337ecc); }
.inner { width: min(1120px, calc(100% - 48px)); margin: 0 auto; }
.banner .inner { display: flex; align-items: flex-end; justify-content: space-between; gap: 16px; }
.banner h1 { margin: 0; font-size: 28px; }
.banner p { margin: 8px 0 0; opacity: 0.9; }
.banner nav { display: flex; gap: 8px; color: rgba(255, 255, 255, 0.85); }
.body { padding: 28px 0 48px; }
.modes { display: inline-flex; margin-bottom: 20px; padding: 3px; border: 1px solid #dcdfe6; border-radius: 99px; background: #f5f7fa; }
.modes button, .filters button, .actions button, .retry footer button, .batch button, .dialog button, .top button, .options button {
  border: 0; background: transparent; cursor: pointer; font: inherit;
}
.modes button { height: 32px; padding: 0 18px; border-radius: 99px; color: #909399; }
.modes button.on { color: #409eff; background: #fff; box-shadow: 0 2px 6px rgba(0, 0, 0, 0.08); }
.stats { display: grid; grid-template-columns: repeat(4, 1fr) 1.4fr; gap: 12px; margin-bottom: 20px; }
.stats article, .card, .retry, .empty { border-radius: 12px; background: #fff; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06); }
.stats article { padding: 18px 12px; text-align: center; }
.stats strong { display: block; font-size: 28px; line-height: 1.2; }
.stats span, .meta, .progress span, .answers span { color: #909399; font-size: 13px; }
.top { text-align: left; padding: 16px; }
.top > span { font-weight: 600; color: #303133; }
.top p { margin: 10px 0 0; color: #c0c4cc; font-size: 13px; }
.top button { width: 100%; display: flex; gap: 8px; align-items: center; padding: 6px 0; border-bottom: 1px solid #ebeef5; text-align: left; }
.top em { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-style: normal; color: #606266; font-size: 13px; }
.top b { flex: none; color: #f56c6c; font-size: 12px; }
.filters { display: flex; flex-wrap: wrap; gap: 10px 16px; margin-bottom: 16px; }
.filters div { display: flex; flex-wrap: wrap; gap: 8px; }
.filters button { height: 32px; padding: 0 14px; border: 1px solid #dcdfe6; border-radius: 99px; background: #fff; color: #606266; font-size: 13px; }
.filters button.on { color: #fff; border-color: #409eff; background: #409eff; }
.card { display: flex; gap: 14px; margin-bottom: 14px; padding: 22px; border-left: 4px solid #f56c6c; }
.card.high { border-left-color: #f56c6c; }
.card.medium { border-left-color: #e6a23c; }
.card.low { border-left-color: #409eff; }
.card.mastered { border-left-color: #c0c4cc; background: #fafafa; color: #909399; }
.card input { margin-top: 4px; }
.card h2 { margin: 12px 0 0; font-size: 16px; font-weight: 500; line-height: 1.7; white-space: pre-wrap; }
.progress { display: flex; align-items: center; gap: 10px; }
.progress em { padding: 2px 8px; border-radius: 99px; font-style: normal; font-size: 12px; }
.progress em.high { color: #f56c6c; background: rgba(245, 108, 108, 0.12); }
.progress em.medium { color: #e6a23c; background: rgba(230, 162, 60, 0.15); }
.progress em.low { color: #409eff; background: rgba(64, 158, 255, 0.12); }
.progress i { flex: 1; height: 6px; border-radius: 99px; background: #ebeef5; overflow: hidden; }
.progress b { display: block; height: 100%; background: #67c23a; }
.meta { display: flex; gap: 16px; margin: 10px 0 0; }
.answers { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-top: 14px; }
.answers div { padding: 12px; border-radius: 8px; background: #f5f7fa; }
.answers p { margin: 6px 0 0; white-space: pre-wrap; line-height: 1.7; }
.actions, .retry footer, .dialog div { display: flex; gap: 8px; }
.actions { margin-top: 14px; }
.actions button, .retry footer button, .batch button, .dialog button { height: 32px; padding: 0 12px; border: 1px solid #dcdfe6; border-radius: 8px; background: #fff; color: #606266; }
.primary { color: #fff !important; border-color: #409eff !important; background: #409eff !important; }
.empty { padding: 64px 24px; text-align: center; color: #909399; }
.empty span { display: block; margin-bottom: 12px; font-size: 40px; }
.empty .primary { margin-top: 16px; }
.pager { display: flex; align-items: center; justify-content: space-between; margin-top: 8px; color: #909399; }
.pager div { display: flex; gap: 8px; }
.pager button { min-width: 36px; height: 36px; border: 1px solid #dcdfe6; border-radius: 4px; background: #fff; cursor: pointer; }
.pager button.on { color: #fff; border-color: #409eff; background: #409eff; }
.pager button:disabled { color: #c0c4cc; cursor: not-allowed; }
.retry { padding: 24px; }
.retry header { display: flex; justify-content: space-between; color: #606266; }
.retry h2 { margin: 16px 0; font-size: 18px; font-weight: 500; line-height: 1.7; white-space: pre-wrap; }
.plain, .feedback { padding: 16px; border-radius: 8px; background: #f5f7fa; }
.plain p, .feedback p { margin: 8px 0 0; white-space: pre-wrap; line-height: 1.7; }
.options { display: flex; flex-direction: column; gap: 10px; }
.options button { display: flex; gap: 10px; align-items: flex-start; padding: 12px 14px; border: 1px solid #dcdfe6; border-radius: 8px; text-align: left; }
.options b { flex: none; min-width: 28px; height: 28px; display: inline-flex; align-items: center; justify-content: center; border-radius: 99px; background: #f5f7fa; color: #409eff; }
.options .selected { border-color: #409eff; }
.options .correct { border-color: #67c23a; background: rgba(103, 194, 58, 0.08); }
.options .wrong { border-color: #f56c6c; background: rgba(245, 108, 108, 0.08); }
.feedback { margin-top: 16px; }
.feedback.ok { background: rgba(103, 194, 58, 0.1); }
.feedback.bad { background: rgba(245, 108, 108, 0.1); }
.retry footer { justify-content: space-between; margin-top: 20px; }
.retry footer div { display: flex; gap: 8px; }
.batch { position: fixed; left: 50%; bottom: 24px; transform: translateX(-50%); display: flex; align-items: center; gap: 10px; padding: 12px 16px; border-radius: 12px; background: #fff; box-shadow: 0 8px 24px rgba(0, 0, 0, 0.16); }
.batch .ok { color: #67c23a; border-color: #67c23a; }
.batch .danger { color: #f56c6c; border-color: #f56c6c; }
.mask { position: fixed; inset: 0; z-index: 2000; display: grid; place-items: center; background: rgba(0, 0, 0, 0.45); }
.dialog { width: min(420px, calc(100% - 32px)); padding: 24px; border-radius: 12px; background: #fff; }
.dialog h3 { margin: 0 0 20px; font-size: 16px; font-weight: 500; line-height: 1.6; }
.dialog div { justify-content: flex-end; }
@media (max-width: 900px) {
  .stats, .answers, .banner .inner { display: grid; grid-template-columns: 1fr 1fr; }
  .top { grid-column: 1 / -1; }
  .banner .inner { grid-template-columns: 1fr; }
}
</style>
