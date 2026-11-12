<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import type { ApiResponse } from '@/types/api'
import { useUserStore } from '@/store/user'
import { formatTime } from '@/utils/time'
import {
  fetchDashboardOverview,
  fetchQuestionDistribution,
  fetchRecentActivities,
  fetchUserGrowth,
  type ActivityItem,
  type DashboardMetric,
  type DashboardOverview,
  type SubjectSlice,
} from '@/api/dashboard'

const router = useRouter()
const user = useUserStore()

const loading = ref(false)
const growthLoading = ref(false)
const failed = ref(false)
const overview = ref<DashboardOverview | null>(null)
const slices = ref<SubjectSlice[]>([])
const activities = ref<ActivityItem[]>([])
const range = ref(7)
const growthDates = ref<string[]>([])
const growthCounts = ref<number[]>([])

const lineRef = ref<HTMLDivElement>()
const pieRef = ref<HTMLDivElement>()
let lineChart: echarts.ECharts | null = null
let pieChart: echarts.ECharts | null = null

const visibleSlices = computed(() => slices.value.filter((item) => item.count > 0))
const showActions = computed(() => user.has('route:view') || user.has('route:add') || user.has('course:view'))

const cards = computed(() => {
  const data = overview.value
  if (!data) return []
  return [
    { key: 'subjects', label: '学科数量', icon: '🎓', tone: 'purple', metric: data.subjects, kind: 'added' as const },
    { key: 'questions', label: '题目数量', icon: '📚', tone: 'green', metric: data.questions, kind: 'added' as const },
    { key: 'students', label: '注册用户', icon: '👥', tone: 'blue', metric: data.students, kind: 'rate' as const },
    { key: 'interviews', label: 'AI面试次数', icon: '🤖', tone: 'orange', metric: data.interviews, kind: 'today' as const },
  ]
})

onMounted(async () => {
  await load()
  window.addEventListener('resize', resizeCharts)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  lineChart?.dispose()
  pieChart?.dispose()
})

watch([growthDates, growthCounts], async () => {
  await nextTick()
  renderLine()
})

watch(visibleSlices, async () => {
  await nextTick()
  renderPie()
})

async function load() {
  loading.value = true
  failed.value = false
  try {
    const [overviewResult, growthResult, distributionResult, activityResult] = await Promise.all([
      fetchDashboardOverview(),
      fetchUserGrowth(range.value),
      fetchQuestionDistribution(),
      fetchRecentActivities(1, 5),
    ])
    overview.value = overviewResult.data.data
    applyGrowth(growthResult.data.data.points)
    slices.value = distributionResult.data.data.items || []
    activities.value = activityResult.data.data.list || []
    await nextTick()
    renderLine()
    renderPie()
  } catch (error) {
    failed.value = true
    ElMessage.error(messageOf(error, '看板加载失败'))
  } finally {
    loading.value = false
  }
}

async function changeRange(next: number) {
  if (range.value === next || growthLoading.value) return
  range.value = next
  growthLoading.value = true
  try {
    const response = await fetchUserGrowth(next)
    applyGrowth(response.data.data.points)
  } catch (error) {
    ElMessage.error(messageOf(error, '用户增长加载失败'))
  } finally {
    growthLoading.value = false
  }
}

function applyGrowth(points: { date: string; count: number }[]) {
  growthDates.value = points.map((item) => item.date.slice(5))
  growthCounts.value = points.map((item) => item.count)
}

function renderLine() {
  if (!lineRef.value) return
  lineChart ??= echarts.init(lineRef.value)
  lineChart.setOption({
    color: ['#667eea'],
    tooltip: { trigger: 'axis' },
    grid: { left: 48, right: 24, top: 24, bottom: range.value === 90 ? 48 : 32 },
    xAxis: {
      type: 'category',
      data: growthDates.value,
      boundaryGap: false,
      axisLabel: { color: '#909399', interval: range.value === 7 ? 0 : 'auto' },
      axisLine: { lineStyle: { color: '#e4e7ed' } },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: { color: '#909399' },
      splitLine: { lineStyle: { color: '#f0f2f5' } },
    },
    series: [{
      name: '新增用户',
      type: 'line',
      smooth: true,
      showSymbol: range.value === 7,
      data: growthCounts.value,
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(102, 126, 234, 0.28)' },
          { offset: 1, color: 'rgba(102, 126, 234, 0.02)' },
        ]),
      },
      lineStyle: { width: 3, color: '#667eea' },
    }],
  }, true)
}

function renderPie() {
  if (!pieRef.value || visibleSlices.value.length === 0) {
    pieChart?.clear()
    return
  }
  pieChart ??= echarts.init(pieRef.value)
  pieChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}：{c}（{d}%）' },
    legend: { type: 'scroll', bottom: 0, textStyle: { color: '#606266' } },
    series: [{
      type: 'pie',
      radius: ['42%', '68%'],
      center: ['50%', '44%'],
      data: visibleSlices.value.map((item) => ({
        name: item.name,
        value: item.count,
        itemStyle: { color: item.themeColor || undefined },
      })),
      label: { formatter: '{b}\n{c}' },
    }],
  }, true)
}

function resizeCharts() {
  lineChart?.resize()
  pieChart?.resize()
}

function countText(value: number) {
  return value.toLocaleString('zh-CN')
}

function trendOf(metric: DashboardMetric, kind: 'added' | 'rate' | 'today') {
  if (kind === 'added') {
    return metric.today > 0
      ? { text: `↑ ${metric.today} 新增`, tone: 'up' }
      : { text: '今日无新增', tone: 'flat' }
  }
  if (kind === 'today') {
    return metric.today > 0
      ? { text: `↑ ${metric.today} 今日`, tone: 'up' }
      : { text: '今日 0', tone: 'flat' }
  }
  if (metric.changeRate == null) {
    return metric.today > 0
      ? { text: `↑ ${metric.today} 新增`, tone: 'up' }
      : { text: '较昨日持平', tone: 'flat' }
  }
  if (metric.changeRate > 0) return { text: `↑ ${metric.changeRate}% 较昨日`, tone: 'up' }
  if (metric.changeRate < 0) return { text: `↓ ${Math.abs(metric.changeRate)}% 较昨日`, tone: 'down' }
  return { text: '较昨日持平', tone: 'flat' }
}

function initial(name: string) {
  return name ? name.slice(0, 1).toUpperCase() : '?'
}

function avatarColor(name: string) {
  const colors = ['#667eea', '#43e97b', '#fa709a', '#4facfe', '#f6d365', '#a18cd1']
  const code = Array.from(name).reduce((sum, char) => sum + char.charCodeAt(0), 0)
  return colors[code % colors.length]
}

const actionStyles: Record<string, string> = {
  登录: 'login',
  登出: 'logout',
  新增: 'create',
  修改: 'update',
  删除: 'delete',
  禁用启用: 'status',
  导出: 'export',
  审核: 'audit',
}

function actionClass(action: string) {
  if (actionStyles[action]) return actionStyles[action]
  const key = Object.keys(actionStyles).sort((left, right) => right.length - left.length).find((item) => action.includes(item))
  return key ? actionStyles[key] : 'other'
}

function messageOf(error: unknown, fallback: string) {
  const body = error as ApiResponse<unknown>
  return body?.message || fallback
}
</script>

<template>
  <section v-loading="loading" class="dashboard">
    <div v-if="failed && !overview" class="panel empty-panel">
      <el-empty description="看板加载失败">
        <el-button type="primary" @click="load">重新加载</el-button>
      </el-empty>
    </div>

    <template v-else>
      <div class="stats">
        <article v-for="card in cards" :key="card.key" class="stat">
          <span class="stat-icon" :class="card.tone">{{ card.icon }}</span>
          <div>
            <strong>{{ countText(card.metric.total) }}</strong>
            <p>{{ card.label }}</p>
            <span class="trend" :class="trendOf(card.metric, card.kind).tone">{{ trendOf(card.metric, card.kind).text }}</span>
          </div>
        </article>
      </div>

      <div v-if="showActions" class="actions">
        <el-button v-if="user.has('route:view')" type="primary" @click="router.push({ name: 'routes' })">管理学习路线</el-button>
        <el-button v-if="user.has('route:add')" @click="router.push({ name: 'routes', query: { create: '1' } })">创建新路线</el-button>
        <el-button v-if="user.has('course:view')" @click="router.push({ name: 'courses' })">B站课程库</el-button>
      </div>

      <div class="charts">
        <article class="panel">
          <header>
            <h2>用户增长趋势</h2>
            <div class="ranges">
              <button type="button" :class="{ on: range === 7 }" :disabled="growthLoading" @click="changeRange(7)">7天</button>
              <button type="button" :class="{ on: range === 30 }" :disabled="growthLoading" @click="changeRange(30)">30天</button>
              <button type="button" :class="{ on: range === 90 }" :disabled="growthLoading" @click="changeRange(90)">90天</button>
            </div>
          </header>
          <div ref="lineRef" class="chart" />
        </article>
        <article class="panel">
          <header><h2>题目学科分布</h2></header>
          <div v-if="visibleSlices.length" ref="pieRef" class="chart" />
          <el-empty v-else description="暂无题目" />
        </article>
      </div>

      <article class="panel">
        <header>
          <h2>最近活动</h2>
          <el-button v-if="user.has('system:log_view')" @click="router.push({ name: 'logs' })">查看全部</el-button>
        </header>
        <el-table :data="activities" empty-text="暂无活动">
          <el-table-column label="用户" min-width="160">
            <template #default="{ row }">
              <span class="person">
                <span class="avatar" :style="{ background: avatarColor(row.username) }">{{ initial(row.username) }}</span>
                {{ row.username || '—' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140">
            <template #default="{ row }">
              <span class="action-tag" :class="actionClass(row.action)">{{ row.action || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="detail" label="详情" min-width="240" show-overflow-tooltip />
          <el-table-column label="时间" width="170">
            <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
          </el-table-column>
        </el-table>
      </article>
    </template>
  </section>
</template>

<style scoped>
.dashboard { display: flex; flex-direction: column; gap: 16px; min-height: 240px; }
.panel { background: #fff; border-radius: 12px; padding: 20px; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06); }
.panel header { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
.panel h2 { margin: 0; font-size: 16px; font-weight: 600; color: #303133; }
.stats { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 16px; }
.stat { display: flex; gap: 14px; align-items: center; background: #fff; border-radius: 12px; padding: 18px 20px; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06); }
.stat strong { display: block; font-size: 26px; line-height: 1.2; color: #303133; }
.stat p { margin: 4px 0; color: #909399; font-size: 13px; }
.stat-icon { width: 48px; height: 48px; border-radius: 14px; display: inline-flex; align-items: center; justify-content: center; font-size: 22px; flex-shrink: 0; }
.stat-icon.purple { background: rgba(102, 126, 234, 0.12); }
.stat-icon.green { background: rgba(67, 233, 123, 0.16); }
.stat-icon.blue { background: rgba(79, 172, 254, 0.14); }
.stat-icon.orange { background: rgba(250, 173, 20, 0.16); }
.trend { font-size: 13px; }
.trend.up { color: #67c23a; }
.trend.down { color: #f56c6c; }
.trend.flat { color: #909399; }
.actions { display: flex; gap: 12px; flex-wrap: wrap; }
.charts { display: grid; grid-template-columns: 1.4fr 1fr; gap: 16px; }
.chart { height: 320px; }
.ranges { display: flex; gap: 8px; }
.ranges button { height: 30px; padding: 0 12px; border: 1px solid #dcdfe6; border-radius: 6px; background: #fff; color: #606266; cursor: pointer; }
.ranges button.on { border-color: #667eea; background: #667eea; color: #fff; }
.ranges button:disabled { cursor: not-allowed; opacity: 0.7; }
.person { display: inline-flex; align-items: center; gap: 10px; }
.avatar { width: 32px; height: 32px; border-radius: 50%; color: #fff; display: inline-flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 600; }
.action-tag { display: inline-block; padding: 4px 10px; border-radius: 12px; font-size: 12px; line-height: 1.4; }
.action-tag.login { background: rgba(64, 158, 255, 0.1); color: #409eff; }
.action-tag.logout { background: rgba(98, 106, 239, 0.1); color: #626aef; }
.action-tag.create { background: rgba(103, 194, 58, 0.1); color: #67c23a; }
.action-tag.update { background: rgba(230, 162, 60, 0.1); color: #e6a23c; }
.action-tag.delete { background: rgba(245, 108, 108, 0.1); color: #f56c6c; }
.action-tag.status { background: rgba(250, 173, 20, 0.12); color: #d48806; }
.action-tag.export { background: rgba(0, 180, 180, 0.1); color: #00b4b4; }
.action-tag.audit { background: rgba(114, 69, 209, 0.1); color: #7245d1; }
.action-tag.other { background: rgba(144, 147, 153, 0.1); color: #909399; }
.empty-panel { min-height: 320px; display: flex; align-items: center; justify-content: center; }
@media (max-width: 1100px) {
  .stats, .charts { grid-template-columns: 1fr; }
}
</style>
