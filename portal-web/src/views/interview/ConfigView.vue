<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { ApiResponse } from '@/types/api'
import { fetchSubjects, type PortalSubject } from '@/api/subject'
import {
  difficultyMeta,
  fetchInterviewConfig,
  type InterviewConfig,
} from '@/api/interview'
import { formatElapsed, useInterviewStore, openInterview } from '@/store/interview'

const router = useRouter()
const store = useInterviewStore()
const loading = ref(false)
const starting = ref(false)
const config = ref<InterviewConfig | null>(null)
const subjects = ref<PortalSubject[]>([])
const subjectId = ref<number | null>(null)
const difficulty = ref(2)
const count = ref(10)

const resume = computed(() => store.draft)
const selectedSubject = computed(() => config.value?.subjects.find((item) => item.subjectId === subjectId.value) ?? null)
const icons = computed(() => new Map(subjects.value.map((item) => [item.id, item])))
const answered = computed(() => resume.value?.questions.filter((item) => item.score !== null).length ?? 0)
const canStart = computed(() => !!selectedSubject.value && (config.value?.remainingCount ?? 0) > 0 && !starting.value)

onMounted(async () => {
  window.scrollTo(0, 0)
  store.reload()
  await load()
  if (store.draft) await askResume()
})

async function load() {
  loading.value = true
  try {
    const [configRes, subjectRes] = await Promise.all([
      fetchInterviewConfig(),
      fetchSubjects().catch(() => null),
    ])
    config.value = configRes.data.data
    subjects.value = subjectRes?.data.data ?? []
    const options = config.value.subjects
    subjectId.value = options[0]?.subjectId ?? null
    difficulty.value = config.value.difficulties.includes(2) ? 2 : (config.value.difficulties[0] ?? 2)
    count.value = config.value.questionCountOptions.includes(config.value.defaultCount)
      ? config.value.defaultCount
      : (config.value.questionCountOptions[0] ?? 10)
  } catch (error) {
    ElMessage.error(messageOf(error, '加载面试配置失败'))
  } finally {
    loading.value = false
  }
}

async function askResume() {
  const draft = store.draft
  if (!draft) return
  try {
    await ElMessageBox.confirm(
      `已完成 ${draft.questions.filter((item) => item.score !== null).length}/${draft.questionCount} 题 · 用时 ${formatElapsed(draft.elapsedMs)}`,
      '是否继续上次的面试？',
      { confirmButtonText: '继续', cancelButtonText: '重新开始', distinguishCancelAndClose: true },
    )
    router.push({ name: 'interview-session' })
  } catch (action) {
    if (action === 'cancel') store.persist(null)
  }
}

async function start() {
  if (!canStart.value || !selectedSubject.value || !config.value) return
  if (store.draft) {
    try {
      await ElMessageBox.confirm('开始新的面试后，上次未完成的进度会被替换。', '重新开始', {
        confirmButtonText: '开始新面试',
        cancelButtonText: '取消',
      })
    } catch {
      return
    }
  }
  starting.value = true
  try {
    await openInterview(selectedSubject.value.subjectId, selectedSubject.value.subjectName, difficulty.value, count.value)
    router.push({ name: 'interview-session' })
  } catch (error) {
    ElMessage.error(messageOf(error, '发起面试失败'))
  } finally {
    starting.value = false
  }
}

function messageOf(error: unknown, fallback: string) {
  const body = error as ApiResponse<unknown>
  return body?.message || fallback
}
</script>

<template>
  <div v-loading="loading" class="config">
    <header class="banner">
      <div class="container banner-inner">
        <div>
          <h1>AI 面试</h1>
          <p>选择方向、难度和题数。回答超时时间固定为 {{ config?.timeoutMinutes ?? 5 }} 分钟。</p>
        </div>
        <nav>
          <router-link to="/">首页</router-link>
          <span>/</span>
          <span>AI面试</span>
        </nav>
      </div>
    </header>

    <div class="container body">
    <section v-if="resume" class="resume">
      <div>
        <strong>上次面试未完成</strong>
        <p>{{ resume.subjectName }} · {{ difficultyMeta(resume.difficulty).label }} · 已完成 {{ answered }}/{{ resume.questionCount }} 题 · 用时 {{ formatElapsed(resume.elapsedMs) }}</p>
      </div>
      <div class="resume-actions">
        <el-button @click="store.persist(null)">重新开始</el-button>
        <el-button type="primary" @click="router.push({ name: 'interview-session' })">继续</el-button>
      </div>
    </section>

    <section v-if="config && config.subjects.length === 0" class="panel">
      <el-empty description="暂无可选的面试方向" />
    </section>

    <template v-else-if="config">
      <section class="panel">
        <h2>技术方向</h2>
        <div class="subjects">
          <button
            v-for="item in config.subjects"
            :key="item.subjectId"
            type="button"
            class="subject"
            :class="{ on: item.subjectId === subjectId }"
            @click="subjectId = item.subjectId"
          >
            <span>{{ icons.get(item.subjectId)?.icon || '🤖' }}</span>
            <strong>{{ item.subjectName }}</strong>
          </button>
        </div>
      </section>

      <section class="panel">
        <h2>面试难度</h2>
        <div class="levels">
          <button
            v-for="item in config.difficulties"
            :key="item"
            type="button"
            class="level"
            :class="{ on: item === difficulty }"
            @click="difficulty = item"
          >
            <strong>{{ difficultyMeta(item).label }}</strong>
            <span>{{ difficultyMeta(item).hint }}</span>
          </button>
        </div>
      </section>

      <section class="panel row">
        <label>
          <span>面试题目数量</span>
          <el-select v-model="count" style="width: 160px">
            <el-option v-for="item in config.questionCountOptions" :key="item" :label="`${item} 题`" :value="item" />
          </el-select>
        </label>
        <label>
          <span>回答超时时间</span>
          <el-input :model-value="`${config.timeoutMinutes} 分钟`" disabled style="width: 160px" />
        </label>
        <div class="quota" :class="{ empty: config.remainingCount <= 0 }">
          今日剩余 {{ config.remainingCount }} 次（每日 5 次，次日 0 点重置）
        </div>
      </section>

      <div class="actions">
        <el-button type="primary" size="large" :disabled="!canStart" :loading="starting" @click="start">开始AI面试</el-button>
        <p v-if="config.remainingCount <= 0">今日次数已用尽，请明天再来。</p>
      </div>
    </template>
    </div>
  </div>
</template>

<style scoped>
.config { min-height: 240px; }
.container { width: min(1120px, calc(100% - 48px)); margin: 0 auto; }
.banner { padding: 40px 0; color: #fff; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); }
.banner-inner { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.banner h1 { margin: 0 0 8px; font-size: 28px; font-weight: 700; }
.banner p { margin: 0; font-size: 14px; opacity: 0.9; }
.banner nav { display: flex; align-items: center; gap: 8px; font-size: 13px; opacity: 0.8; flex: none; }
.banner a { color: #fff; }
.body { display: flex; flex-direction: column; gap: 16px; padding: 24px 0 32px; }
.resume, .panel { background: #fff; border-radius: 12px; padding: 20px; }
.resume { display: flex; justify-content: space-between; gap: 16px; align-items: center; }
.resume p { margin: 6px 0 0; color: #909399; font-size: 13px; }
.resume-actions { display: flex; gap: 8px; }
.panel h2 { margin: 0 0 14px; font-size: 16px; }
.subjects, .levels { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 12px; }
.subject, .level { display: flex; flex-direction: column; align-items: flex-start; gap: 6px; padding: 14px; border: 1px solid #ebeef5; border-radius: 10px; background: #fff; cursor: pointer; text-align: left; }
.subject.on, .level.on { border-color: #409eff; box-shadow: 0 0 0 1px #409eff; }
.subject span { font-size: 22px; }
.level span, .subject strong, .level strong { line-height: 1.5; }
.level span { color: #909399; font-size: 12px; }
.row { display: flex; flex-wrap: wrap; gap: 20px; align-items: flex-end; }
label { display: flex; flex-direction: column; gap: 8px; color: #606266; font-size: 14px; }
.quota { color: #67c23a; font-size: 14px; }
.quota.empty { color: #f56c6c; }
.actions { display: flex; align-items: center; gap: 12px; }
.actions p { margin: 0; color: #f56c6c; }
@media (max-width: 720px) {
  .banner-inner, .resume { flex-direction: column; align-items: flex-start; }
}
</style>
