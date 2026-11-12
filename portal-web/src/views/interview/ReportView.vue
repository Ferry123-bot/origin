<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { ApiResponse } from '@/types/api'
import {
  difficultyMeta,
  fetchInterviewReport,
  formatDateTime,
  formatDuration,
  scoreTone,
  type InterviewReport,
} from '@/api/interview'
import { openInterview, useInterviewStore } from '@/store/interview'

const route = useRoute()
const router = useRouter()
const store = useInterviewStore()
const loading = ref(false)
const starting = ref(false)
const report = ref<InterviewReport | null>(null)
const failed = ref(false)

const circumference = 2 * Math.PI * 65
const tone = computed(() => scoreTone(report.value?.overallScore ?? 0))
const dash = computed(() => circumference * (1 - Math.max(0, Math.min(10, report.value?.overallScore ?? 0)) / 10))
const trend = computed(() => {
  const delta = report.value?.trendDelta
  if (delta === null || delta === undefined) return null
  if (delta > 0) return { text: `提升 ${delta.toFixed(1)}`, tone: 'up' }
  if (delta < 0) return { text: `下降 ${Math.abs(delta).toFixed(1)}`, tone: 'down' }
  return { text: '持平', tone: 'flat' }
})
const coverage = computed(() => {
  const value = report.value?.aiCoverage ?? 0
  return value > 0 ? Math.round(value * 100) : 0
})

onMounted(() => {
  window.scrollTo(0, 0)
  void load()
})

async function load() {
  const id = Number(route.query.id)
  if (!Number.isInteger(id) || id <= 0) {
    failed.value = true
    return
  }
  loading.value = true
  try {
    const response = await fetchInterviewReport(id)
    report.value = response.data.data
  } catch (error) {
    failed.value = true
    ElMessage.error(messageOf(error, '加载面试报告失败'))
  } finally {
    loading.value = false
  }
}

async function again() {
  const current = report.value
  if (!current || starting.value) return
  if (store.draft) {
    try {
      await ElMessageBox.confirm('开始新的面试后，上次未完成的进度会被替换。', '再来一次', {
        confirmButtonText: '开始新面试',
        cancelButtonText: '取消',
      })
    } catch {
      return
    }
  }
  starting.value = true
  try {
    await openInterview(current.subjectId, current.subjectName, current.difficulty, current.questionCount)
    router.push({ name: 'interview-session' })
  } catch (error) {
    ElMessage.error(messageOf(error, '发起面试失败'))
  } finally {
    starting.value = false
  }
}

function replay() {
  document.getElementById('replay')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function messageOf(error: unknown, fallback: string) {
  const body = error as ApiResponse<unknown>
  return body?.message || fallback
}
</script>

<template>
  <div v-loading="loading" class="report">
    <el-empty v-if="failed" description="没有找到这场面试报告">
      <el-button type="primary" @click="router.push({ name: 'interview-history' })">返回面试记录</el-button>
    </el-empty>
    <template v-else-if="report">
      <article class="card">
        <header>
          <h1>AI面试评估报告</h1>
          <p>
            {{ report.subjectName }} · {{ difficultyMeta(report.difficulty).label }}难度 · {{ formatDateTime(report.completedAt) }}
            <span v-if="coverage"> · AI 补题占比 {{ coverage }}%</span>
          </p>
        </header>
        <section class="score">
          <div class="ring">
            <svg width="160" height="160" viewBox="0 0 160 160">
              <circle cx="80" cy="80" r="65" fill="none" stroke="#ebeef5" stroke-width="12" />
              <circle
                cx="80" cy="80" r="65" fill="none" stroke-width="12" stroke-linecap="round"
                :class="tone" :stroke-dasharray="circumference" :stroke-dashoffset="dash"
                transform="rotate(-90 80 80)"
              />
            </svg>
            <div>
              <strong>{{ report.overallScore.toFixed(1) }}</strong>
              <span>综合评分</span>
            </div>
          </div>
          <div class="details">
            <div><em>📝</em><span><b>回答准确度</b>{{ report.accuracyRate }}%</span></div>
            <div><em>⏱️</em><span><b>面试时长</b>{{ formatDuration(report.totalDuration) }}</span></div>
            <div><em>📊</em><span><b>答题数量</b>{{ report.questionCount }} 题</span></div>
            <div v-if="trend"><em>📈</em><span><b>相比上次</b><i :class="trend.tone">{{ trend.text }}</i></span></div>
          </div>
        </section>

        <section>
          <h2>能力分析</h2>
          <div class="dims">
            <div v-for="item in report.dimensions" :key="item.name">
              <strong :class="scoreTone(item.score / 10)">{{ item.score }}</strong>
              <span>{{ item.name }}</span>
            </div>
          </div>
        </section>

        <section class="split">
          <div>
            <h2>优点</h2>
            <p v-for="item in report.strengths" :key="item">✅ {{ item }}</p>
            <el-empty v-if="report.strengths.length === 0" description="这次还没有总结出明显优点" />
          </div>
          <div>
            <h2>待提升</h2>
            <div v-for="item in report.weaknesses" :key="item.text" class="weak">
              <p>⚠️ {{ item.text }}</p>
              <small>{{ item.advice }}</small>
            </div>
          </div>
        </section>

        <section>
          <h2>改进建议</h2>
          <ol>
            <li v-for="item in report.suggestions" :key="item">{{ item }}</li>
          </ol>
        </section>

        <section class="cards">
          <article v-for="item in report.improveCards" :key="item.title">
            <h3>{{ item.title }}</h3>
            <p>{{ item.content }}</p>
          </article>
        </section>
      </article>

      <div class="actions">
        <el-button @click="router.push({ name: 'interview-history' })">查看历史记录</el-button>
        <el-button type="primary" :loading="starting" @click="again">再来一次</el-button>
        <el-button @click="replay">查看面试回放</el-button>
      </div>

      <section id="replay" class="card">
        <h2>面试回放</h2>
        <article v-for="item in report.questionReviews" :key="item.order" class="review">
          <header>
            <strong>第 {{ item.order }} 题</strong>
            <em :class="scoreTone(item.score)">{{ item.score.toFixed(1) }} 分</em>
          </header>
          <p>{{ item.questionText }}</p>
          <p class="answer"><b>你的回答</b>{{ item.studentAnswer || '未作答' }}</p>
          <p class="feedback"><b>评语</b>{{ item.feedback || '暂无评语' }}</p>
        </article>
      </section>
    </template>
  </div>
</template>

<style scoped>
.report { display: flex; flex-direction: column; gap: 16px; }
.card, .actions { background: #fff; border-radius: 12px; padding: 24px; }
.card header h1, .card h2 { margin: 0; }
.card header p, .card p { color: #606266; line-height: 1.7; }
.score { display: flex; gap: 28px; align-items: center; margin: 20px 0; flex-wrap: wrap; }
.ring { position: relative; width: 160px; height: 160px; }
.ring div { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; }
.ring strong { font-size: 36px; }
.ring span, .details b { display: block; color: #909399; font-weight: 400; }
.ring circle.high { stroke: #67c23a; }
.ring circle.mid { stroke: #e6a23c; }
.ring circle.low { stroke: #f56c6c; }
.details { display: grid; grid-template-columns: repeat(2, minmax(140px, 1fr)); gap: 12px; flex: 1; }
.details div { display: flex; gap: 10px; align-items: center; }
.details em { font-style: normal; font-size: 22px; }
.details i { font-style: normal; font-weight: 700; }
.up, .high { color: #67c23a; }
.down, .low { color: #f56c6c; }
.flat, .mid { color: #e6a23c; }
.dims, .cards { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin-top: 12px; }
.dims div, .cards article { padding: 16px; border-radius: 10px; background: #f5f7fa; text-align: center; }
.dims strong { display: block; font-size: 24px; }
.dims span, .cards p { color: #909399; }
.cards article { text-align: left; }
.cards h3 { margin: 0 0 8px; }
.split { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-top: 20px; }
.weak { margin-bottom: 10px; }
.weak small { display: block; color: #909399; line-height: 1.6; }
ol { margin: 8px 0 0; padding-left: 20px; color: #606266; line-height: 1.8; }
.actions { display: flex; gap: 12px; flex-wrap: wrap; }
.review { padding: 14px 0; border-top: 1px solid #ebeef5; }
.review header { display: flex; justify-content: space-between; }
.answer, .feedback { margin: 6px 0 0; }
.answer b, .feedback b { margin-right: 8px; color: #909399; font-weight: 400; }
@media (max-width: 800px) {
  .dims, .cards, .split, .details { grid-template-columns: 1fr; }
}
</style>
