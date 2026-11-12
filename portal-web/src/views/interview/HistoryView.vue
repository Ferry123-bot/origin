<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { ApiResponse } from '@/types/api'
import {
  difficultyMeta,
  fetchInterviewRecords,
  fetchInterviewStats,
  formatDateTime,
  formatDuration,
  scoreTone,
  type InterviewRecord,
  type InterviewStats,
} from '@/api/interview'
import { openInterview, useInterviewStore } from '@/store/interview'

const router = useRouter()
const store = useInterviewStore()
const loading = ref(false)
const startingId = ref<number | null>(null)
const stats = ref<InterviewStats>({ interviewTotal: 0, avgScore: 0, maxScore: 0, totalDuration: 0 })
const records = ref<InterviewRecord[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 10

onMounted(() => {
  window.scrollTo(0, 0)
  void load()
})

async function load() {
  loading.value = true
  try {
    const [statRes, listRes] = await Promise.all([
      fetchInterviewStats(),
      fetchInterviewRecords(page.value, pageSize),
    ])
    stats.value = statRes.data.data
    records.value = listRes.data.data.list
    total.value = listRes.data.data.total
  } catch (error) {
    ElMessage.error(messageOf(error, '加载面试记录失败'))
  } finally {
    loading.value = false
  }
}

function changePage(next: number) {
  page.value = next
  void load()
}

function openReport(record: InterviewRecord) {
  if (!record.hasReport) return
  router.push({ name: 'interview-report', query: { id: String(record.recordId) } })
}

async function again(record: InterviewRecord) {
  if (startingId.value) return
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
  startingId.value = record.recordId
  try {
    await openInterview(record.subjectId, record.subjectName, record.difficulty, record.questionCount)
    router.push({ name: 'interview-session' })
  } catch (error) {
    ElMessage.error(messageOf(error, '发起面试失败'))
  } finally {
    startingId.value = null
  }
}

function messageOf(error: unknown, fallback: string) {
  const body = error as ApiResponse<unknown>
  return body?.message || fallback
}
</script>

<template>
  <div v-loading="loading" class="history">
    <header>
      <h1>面试记录</h1>
      <el-button type="primary" @click="router.push({ name: 'interview-config' })">开始AI面试</el-button>
    </header>
    <section class="stats">
      <article><strong>{{ stats.interviewTotal }}</strong><span>面试总次数</span></article>
      <article><strong>{{ stats.avgScore.toFixed(1) }}</strong><span>平均分数</span></article>
      <article><strong>{{ stats.maxScore.toFixed(1) }}</strong><span>最高分数</span></article>
      <article><strong>{{ formatDuration(stats.totalDuration) }}</strong><span>累计时长</span></article>
    </section>
    <el-empty v-if="!loading && records.length === 0" description="还没有面试记录">
      <el-button type="primary" @click="router.push({ name: 'interview-config' })">去面试</el-button>
    </el-empty>
    <section v-else class="list">
      <article v-for="item in records" :key="item.recordId">
        <div class="mark" :class="scoreTone(item.overallScore)">{{ item.overallScore.toFixed(1) }}</div>
        <div class="body">
          <strong>{{ item.subjectName }}</strong>
          <p>{{ formatDateTime(item.completedAt) }} · {{ formatDuration(item.totalDuration) }} · {{ item.questionCount }} 题 · {{ difficultyMeta(item.difficulty).label }}</p>
        </div>
        <div class="ops">
          <el-button :disabled="!item.hasReport" @click="openReport(item)">查看报告</el-button>
          <el-button type="primary" plain :loading="startingId === item.recordId" @click="again(item)">再来一次</el-button>
        </div>
      </article>
    </section>
    <el-pagination
      v-if="total > pageSize"
      layout="prev, pager, next"
      :total="total"
      :page-size="pageSize"
      :current-page="page"
      @current-change="changePage"
    />
  </div>
</template>

<style scoped>
.history { display: flex; flex-direction: column; gap: 16px; }
header { display: flex; justify-content: space-between; align-items: center; }
header h1 { margin: 0; font-size: 28px; }
.stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }
.stats article, .list article { background: #fff; border-radius: 12px; }
.stats article { padding: 18px; }
.stats strong { display: block; font-size: 26px; }
.stats span, .body p { color: #909399; }
.list { display: flex; flex-direction: column; gap: 12px; }
.list article { display: flex; align-items: center; gap: 16px; padding: 16px 18px; }
.mark { width: 64px; height: 64px; border-radius: 50%; display: grid; place-items: center; font-weight: 700; background: #f5f7fa; flex: none; }
.mark.high { color: #67c23a; background: rgba(103, 194, 58, 0.12); }
.mark.mid { color: #e6a23c; background: rgba(230, 162, 60, 0.12); }
.mark.low { color: #f56c6c; background: rgba(245, 108, 108, 0.12); }
.body { flex: 1; min-width: 0; }
.body strong { display: block; }
.body p { margin: 6px 0 0; font-size: 13px; }
.ops { display: flex; gap: 8px; }
@media (max-width: 800px) {
  .stats { grid-template-columns: 1fr 1fr; }
  .list article { flex-wrap: wrap; }
}
</style>
