<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { ApiResponse } from '@/types/api'
import { fetchQuestion, fetchQuestions, type QuestionDetail, type QuestionItem } from '@/api/question'
import { fetchStages, fetchSubjects, type PortalStage, type PortalSubject } from '@/api/subject'
import { useUserStore } from '@/store/user'

const route = useRoute()
const router = useRouter()
const user = useUserStore()

const loading = ref(false)
const listLoading = ref(false)
const subjects = ref<PortalSubject[]>([])
const stages = ref<PortalStage[]>([])
const currentId = ref<number | null>(null)
const focusStageId = ref<number | null>(null)
const stageId = ref<number | null>(null)
const questions = ref<QuestionItem[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const type = ref(0)
const difficulty = ref(0)
const keyword = ref('')
const detail = ref<QuestionDetail | null>(null)
const detailLoading = ref(false)
let keywordTimer = 0

const current = computed(() => subjects.value.find((item) => item.id === currentId.value) ?? null)
const currentStage = computed(() => stages.value.find((item) => item.id === stageId.value) ?? null)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))
const filtered = computed(() => type.value !== 0 || difficulty.value !== 0 || keyword.value.trim() !== '')

const typeOptions = [
  { value: 0, label: '全部' },
  { value: 1, label: '单选' },
  { value: 2, label: '多选' },
  { value: 3, label: '判断' },
  { value: 4, label: '简答' },
]
const difficultyOptions = [
  { value: 1, label: '简单' },
  { value: 2, label: '中等' },
  { value: 3, label: '困难' },
]

onMounted(async () => {
  loading.value = true
  try {
    const response = await fetchSubjects()
    subjects.value = response.data.data
    const subjectId = Number(route.query.subjectId)
    const queryStageId = Number(route.query.stageId)
    focusStageId.value = Number.isInteger(queryStageId) && queryStageId > 0 ? queryStageId : null
    const preferred = subjects.value.some((item) => item.id === subjectId) ? subjectId : subjects.value[0]?.id
    if (preferred) await selectSubject(preferred)
    if (focusStageId.value) {
      await nextTick()
      document.getElementById(`stage-${focusStageId.value}`)?.scrollIntoView({ block: 'center' })
    }
  } catch (error) {
    ElMessage.error(messageOf(error, '加载学科失败'))
  } finally {
    loading.value = false
  }
})

function messageOf(error: unknown, fallback: string) {
  return (error as ApiResponse<unknown>)?.message || fallback
}

function typeLabel(value: number) {
  return typeOptions.find((item) => item.value === value)?.label || '题目'
}

function difficultyLabel(value: number) {
  return difficultyOptions.find((item) => item.value === value)?.label || ''
}

function difficultyClass(value: number) {
  if (value === 1) return 'easy'
  if (value === 3) return 'hard'
  return 'medium'
}

function tagsOf(value: string | null) {
  if (!value) return []
  return value.split(/[,，]/).map((item) => item.trim()).filter(Boolean)
}

function questionNo(index: number) {
  return (page.value - 1) * pageSize + index + 1
}

async function selectSubject(id: number) {
  currentId.value = id
  stageId.value = null
  questions.value = []
  total.value = 0
  resetFilters()
  try {
    const response = await fetchStages(id)
    stages.value = response.data.data
  } catch (error) {
    stages.value = []
    ElMessage.error(messageOf(error, '加载阶段失败'))
  }
}

function resetFilters() {
  type.value = 0
  difficulty.value = 0
  keyword.value = ''
  page.value = 1
}

async function openStage(id: number) {
  stageId.value = id
  focusStageId.value = id
  resetFilters()
  await loadQuestions()
}

function backToStages() {
  stageId.value = null
  questions.value = []
  total.value = 0
}

async function loadQuestions() {
  if (!currentId.value || !stageId.value) return
  listLoading.value = true
  try {
    const response = await fetchQuestions({
      subjectId: currentId.value,
      stageId: stageId.value,
      type: type.value || undefined,
      difficulty: difficulty.value || undefined,
      keyword: keyword.value.trim() || undefined,
      page: page.value,
      pageSize,
    })
    questions.value = response.data.data.list
    total.value = response.data.data.total
  } catch (error) {
    questions.value = []
    total.value = 0
    ElMessage.error(messageOf(error, '加载题目失败'))
  } finally {
    listLoading.value = false
  }
}

function pickType(value: number) {
  if (type.value === value) return
  type.value = value
  page.value = 1
  loadQuestions()
}

function pickDifficulty(value: number) {
  difficulty.value = difficulty.value === value ? 0 : value
  page.value = 1
  loadQuestions()
}

function onKeyword() {
  window.clearTimeout(keywordTimer)
  keywordTimer = window.setTimeout(() => {
    page.value = 1
    loadQuestions()
  }, 400)
}

function goPage(next: number) {
  if (next < 1 || next > totalPages.value || next === page.value) return
  page.value = next
  loadQuestions()
}

async function openDetail(id: number) {
  detailLoading.value = true
  detail.value = null
  try {
    const response = await fetchQuestion(id)
    detail.value = response.data.data
  } catch (error) {
    ElMessage.error(messageOf(error, '加载解析失败'))
  } finally {
    detailLoading.value = false
  }
}

function closeDetail() {
  detail.value = null
}

function startStage(id: number) {
  router.push({ name: 'practice', query: { stageId: String(id), subjectId: currentId.value ? String(currentId.value) : undefined } })
}

function startQuestion(id: number) {
  router.push({
    name: 'practice',
    query: {
      questionId: String(id),
      subjectId: currentId.value ? String(currentId.value) : undefined,
      stageId: stageId.value ? String(stageId.value) : undefined,
    },
  })
}

function goLogin() {
  router.push({ name: 'login', query: { redirect: route.fullPath } })
}
</script>

<template>
  <div v-loading="loading" class="bank">
    <header class="banner">
      <div class="container banner-inner">
        <div>
          <h1>题库中心</h1>
          <p>海量精选面试题，助你轻松备战</p>
        </div>
        <nav>
          <router-link to="/">首页</router-link>
          <span>/</span>
          <span>题库</span>
        </nav>
      </div>
    </header>
    <div class="container layout">
      <aside>
        <div class="side-card">
          <h3>学科</h3>
          <button
            v-for="item in subjects"
            :key="item.id"
            type="button"
            :class="{ on: item.id === currentId }"
            @click="selectSubject(item.id)"
          >
            <span class="icon">{{ item.icon || '📘' }}</span>
            <span class="name">{{ item.name }}</span>
            <em>{{ item.stageCount }}</em>
          </button>
        </div>
      </aside>
      <section v-if="current && !stageId">
        <header class="view-head">
          <div class="view-left">
            <span class="view-icon">{{ current.icon || '📘' }}</span>
            <div>
              <h2>{{ current.name }}</h2>
              <p>{{ current.description }}</p>
            </div>
          </div>
          <span class="count">共 {{ stages.length }} 个阶段</span>
        </header>
        <div v-if="stages.length === 0" class="empty">
          <span>📭</span>
          <p>该学科暂无阶段，题目正在录入中</p>
        </div>
        <div v-else class="stages">
          <article
            v-for="stage in stages"
            :id="`stage-${stage.id}`"
            :key="stage.id"
            class="stage"
            :class="{ focus: stage.id === focusStageId }"
          >
            <div class="stage-info">
              <span class="stage-icon">{{ stage.icon || '📘' }}</span>
              <div>
                <strong>{{ stage.name }}</strong>
                <p>共 {{ stage.questionCount }} 题</p>
              </div>
            </div>
            <div class="actions">
              <button type="button" class="ghost" @click="openStage(stage.id)">查看题目</button>
              <button type="button" class="primary" @click="startStage(stage.id)">开始练习</button>
            </div>
          </article>
        </div>
      </section>
      <section v-else-if="current && currentStage" v-loading="listLoading">
        <div class="catalog">
          <button type="button" class="back" @click="backToStages">‹ 返回阶段列表</button>
          <span>{{ current.name }} / <strong>{{ currentStage.name }}</strong></span>
        </div>
        <div class="toolbar">
          <div class="filters">
            <button
              v-for="item in typeOptions"
              :key="item.value"
              type="button"
              :class="{ on: type === item.value }"
              @click="pickType(item.value)"
            >{{ item.label }}</button>
          </div>
          <div class="filters right">
            <button
              v-for="item in difficultyOptions"
              :key="item.value"
              type="button"
              :class="{ on: difficulty === item.value }"
              @click="pickDifficulty(item.value)"
            >{{ item.label }}</button>
            <label class="search">
              <span>🔍</span>
              <input v-model="keyword" placeholder="搜索题目关键词..." @input="onKeyword" />
            </label>
          </div>
        </div>
        <div v-if="!listLoading && questions.length === 0" class="empty">
          <span>📝</span>
          <p>{{ filtered ? '没有符合条件的题目' : '该阶段暂无题目，题目正在录入中' }}</p>
        </div>
        <div v-else class="cards">
          <article v-for="(item, index) in questions" :key="item.id" class="card">
            <header>
              <div>
                <p class="no">第 {{ questionNo(index) }} 题</p>
                <h3>{{ item.content }}</h3>
              </div>
              <div class="badges">
                <span class="badge type">{{ typeLabel(item.type) }}</span>
                <span class="badge" :class="difficultyClass(item.difficulty)">{{ difficultyLabel(item.difficulty) }}</span>
              </div>
            </header>
            <div class="meta">
              <span>📁 {{ item.stageName }}</span>
              <span>👥 {{ item.doneCount }}人做过</span>
              <span>✓ 正确率 {{ item.accuracyRate }}%</span>
              <span class="tags">
                <em v-for="tag in tagsOf(item.tags)" :key="tag">{{ tag }}</em>
              </span>
            </div>
            <div class="actions">
              <button type="button" class="ghost" @click="openDetail(item.id)">查看解析</button>
              <button type="button" class="primary" @click="startQuestion(item.id)">开始练习</button>
            </div>
          </article>
        </div>
        <footer v-if="total > 0" class="pager">
          <span>共 {{ total }} 道题目</span>
          <div>
            <button type="button" :disabled="page <= 1" @click="goPage(page - 1)">‹</button>
            <button type="button" class="on">{{ page }}</button>
            <button type="button" :disabled="page >= totalPages" @click="goPage(page + 1)">›</button>
          </div>
        </footer>
      </section>
    </div>

    <div v-if="detail || detailLoading" class="mask" @click.self="closeDetail">
      <div v-loading="detailLoading" class="dialog">
        <template v-if="detail">
          <header>
            <div>
              <p>{{ detail.subjectName }} / {{ detail.stageName }}</p>
              <h3>{{ detail.content }}</h3>
            </div>
            <button type="button" class="close" @click="closeDetail">✕</button>
          </header>
          <div class="badges">
            <span class="badge type">{{ typeLabel(detail.type) }}</span>
            <span class="badge" :class="difficultyClass(detail.difficulty)">{{ difficultyLabel(detail.difficulty) }}</span>
            <span v-if="detail.inWrongBook" class="badge wrong">已在错题本</span>
          </div>
          <ul v-if="detail.options.length" class="options">
            <li v-for="option in detail.options" :key="option.key"><b>{{ option.key }}</b>{{ option.text }}</li>
          </ul>
          <div v-if="user.token" class="analysis">
            <p><strong>正确答案</strong>{{ detail.answer || '暂无' }}</p>
            <p><strong>解析</strong>{{ detail.analysis || '暂无解析' }}</p>
          </div>
          <p v-else class="login-tip">登录后可查看答案和解析。<button type="button" @click="goLogin">去登录</button></p>
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped>
.bank, .bank * { box-sizing: border-box; }
.container { width: min(1200px, calc(100% - 40px)); margin: 0 auto; }
.banner { padding: 36px 0; color: #fff; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); }
.banner-inner { display: flex; align-items: center; justify-content: space-between; }
.banner h1 { margin: 0 0 8px; font-size: 28px; }
.banner p { margin: 0; opacity: 0.9; }
.banner nav { display: flex; gap: 8px; font-size: 13px; opacity: 0.85; }
.banner a { color: #fff; }
.layout { display: flex; gap: 24px; padding: 24px 0 32px; }
aside { width: 260px; flex: none; }
.side-card { position: sticky; top: 88px; padding: 20px; border-radius: 12px; background: #fff; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08); }
.side-card h3 { margin: 0 0 16px; padding-bottom: 12px; border-bottom: 1px solid #e4e7ed; font-size: 16px; }
.side-card button, .filters button, .pager button, .ghost, .primary, .back, .close, .login-tip button {
  border: 0; background: transparent; cursor: pointer; font: inherit;
}
.side-card button { width: 100%; display: flex; align-items: center; gap: 10px; padding: 12px 16px; border-radius: 8px; color: #606266; text-align: left; }
.side-card button:hover { color: #409eff; background: #f5f7fa; }
.side-card button.on { color: #409eff; font-weight: 500; background: rgba(64, 158, 255, 0.1); }
.side-card .name { flex: 1; min-width: 0; }
.side-card em { padding: 2px 8px; border-radius: 10px; color: #909399; font-style: normal; font-size: 12px; background: #f5f7fa; }
section { flex: 1; min-width: 0; }
.view-head, .stage, .card, .empty { border-radius: 12px; background: #fff; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08); }
.view-head { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 16px; padding: 20px 24px; }
.view-left, .stage-info { display: flex; align-items: center; gap: 14px; min-width: 0; }
.view-icon, .stage-icon { width: 48px; height: 48px; display: grid; place-items: center; flex: none; border-radius: 8px; font-size: 24px; background: #f5f7fa; }
.stage-icon { width: 44px; height: 44px; font-size: 22px; }
.view-head h2, .card h3, .dialog h3 { margin: 0; font-size: 17px; font-weight: 600; }
.view-head p, .count, .stage p { margin: 4px 0 0; color: #909399; font-size: 13px; }
.stages, .cards { display: flex; flex-direction: column; gap: 12px; }
.cards { gap: 16px; }
.stage { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 18px 24px; }
.stage.focus { outline: 2px solid #409eff; }
.stage strong { font-size: 15px; font-weight: 500; }
.actions { display: flex; gap: 8px; flex: none; }
.ghost, .primary { height: 32px; padding: 0 12px; border-radius: 8px; font-size: 12px; }
.ghost, .back { color: #606266; border: 1px solid #dcdfe6; background: #fff; }
.ghost:hover, .back:hover { color: #409eff; border-color: #409eff; }
.primary { color: #fff; background: linear-gradient(135deg, #409eff, #337ecc); }
.empty { padding: 64px 24px; text-align: center; color: #909399; }
.empty span { display: block; margin-bottom: 12px; font-size: 40px; }
.catalog { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; color: #909399; font-size: 14px; }
.catalog strong { color: #303133; font-weight: 500; }
.back { height: 36px; padding: 0 14px; border-radius: 4px; }
.toolbar { display: flex; justify-content: space-between; gap: 16px; flex-wrap: wrap; margin-bottom: 20px; }
.filters { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }
.filters button { height: 34px; padding: 0 16px; border: 1px solid #dcdfe6; border-radius: 20px; background: #fff; color: #606266; font-size: 13px; }
.filters button.on { color: #fff; border-color: #409eff; background: #409eff; }
.search { position: relative; }
.search span { position: absolute; left: 14px; top: 50%; transform: translateY(-50%); }
.search input { width: 240px; height: 38px; padding: 0 16px 0 40px; border: 1px solid #dcdfe6; border-radius: 20px; font-size: 14px; }
.search input:focus { outline: none; border-color: #409eff; }
.card { padding: 24px; }
.card header, .dialog header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.no { margin: 0 0 8px; color: #909399; font-size: 13px; }
.card h3, .dialog h3 { font-size: 16px; font-weight: 500; line-height: 1.6; white-space: pre-wrap; }
.badges { display: flex; align-items: center; gap: 8px; flex: none; align-self: flex-start; }
.badge { display: inline-flex; align-items: center; height: 24px; padding: 0 10px; border-radius: 20px; font-size: 12px; line-height: 1; white-space: nowrap; }
.badge.type { color: #409eff; background: rgba(64, 158, 255, 0.1); }
.badge.easy { color: #67c23a; background: rgba(103, 194, 58, 0.15); }
.badge.medium { color: #e6a23c; background: rgba(230, 162, 60, 0.15); }
.badge.hard { color: #f56c6c; background: rgba(245, 108, 108, 0.15); }
.badge.wrong { color: #f56c6c; background: rgba(245, 108, 108, 0.1); }
.meta { display: flex; align-items: center; gap: 20px; margin-top: 16px; padding-top: 16px; border-top: 1px solid #e4e7ed; color: #909399; font-size: 13px; }
.tags { display: flex; gap: 8px; flex-wrap: wrap; margin-left: auto; }
.tags em { padding: 4px 10px; border-radius: 20px; font-style: normal; font-size: 12px; background: #f5f7fa; }
.card .actions { margin-top: 12px; }
.pager { display: flex; align-items: center; justify-content: space-between; margin-top: 24px; color: #909399; font-size: 14px; }
.pager div { display: flex; gap: 8px; }
.pager button { min-width: 36px; height: 36px; border: 1px solid #dcdfe6; border-radius: 4px; background: #fff; }
.pager button.on { color: #fff; border-color: #409eff; background: #409eff; }
.pager button:disabled { color: #c0c4cc; cursor: not-allowed; }
.mask { position: fixed; inset: 0; z-index: 2000; display: grid; place-items: center; padding: 24px; background: rgba(0, 0, 0, 0.45); }
.dialog { width: min(720px, 100%); max-height: calc(100vh - 48px); overflow: auto; padding: 24px; border-radius: 12px; background: #fff; }
.dialog header p { margin: 0 0 8px; color: #909399; font-size: 13px; }
.close { width: 32px; height: 32px; color: #909399; }
.options { margin: 16px 0 0; padding: 0; list-style: none; }
.options li { display: flex; gap: 10px; margin-top: 10px; padding: 12px 14px; border-radius: 8px; background: #f5f7fa; }
.analysis { margin-top: 16px; padding: 16px; border-radius: 8px; background: #f5f7fa; line-height: 1.7; }
.analysis p { margin: 0 0 8px; white-space: pre-wrap; }
.analysis strong { margin-right: 8px; }
.login-tip { margin: 16px 0 0; color: #909399; }
.login-tip button { margin-left: 8px; color: #409eff; }
@media (max-width: 900px) {
  .layout, .banner-inner, .stage, .toolbar { display: grid; }
  .layout, .stage, .banner-inner { gap: 12px; }
  aside, .search input { width: 100%; }
  .side-card { position: static; }
}
</style>
