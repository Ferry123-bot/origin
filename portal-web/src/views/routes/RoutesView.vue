<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { ApiResponse } from '@/types/api'
import { fetchSubjects, type PortalSubject } from '@/api/subject'
import {
  completeRouteCourse,
  fetchRoute,
  fetchRoutes,
  type PortalRoute,
  type PortalRouteDetail,
} from '@/api/route'

const loading = ref(false)
const detailLoading = ref(false)
const completingId = ref<number | null>(null)
const subjects = ref<PortalSubject[]>([])
const routes = ref<PortalRoute[]>([])
const currentSubjectId = ref<number | null>(null)
const currentRouteId = ref<number | null>(null)
const detail = ref<PortalRouteDetail | null>(null)

const subjectRoutes = computed(() => routes.value.filter((item) => item.subjectId === currentSubjectId.value))
const currentSubject = computed(() => subjects.value.find((item) => item.id === currentSubjectId.value) ?? null)

onMounted(async () => {
  loading.value = true
  try {
    const [subjectRes, routeRes] = await Promise.all([fetchSubjects(), fetchRoutes()])
    subjects.value = subjectRes.data.data
    routes.value = routeRes.data.data
    const first = subjects.value[0]
    if (first) await selectSubject(first.id)
  } catch (error) {
    ElMessage.error(messageOf(error, '加载学习路线失败'))
  } finally {
    loading.value = false
  }
})

function messageOf(error: unknown, fallback: string) {
  const body = error as ApiResponse<unknown>
  return body?.message || fallback
}

function courseCount(subjectId: number) {
  return routes.value.filter((item) => item.subjectId === subjectId).reduce((sum, item) => sum + item.courseCount, 0)
}

function formatMinutes(minutes: number) {
  const total = Math.max(0, minutes || 0)
  const hours = Math.floor(total / 60)
  const mins = total % 60
  if (hours === 0) return `${mins}分钟`
  if (mins === 0) return `${hours}小时`
  return `${hours}小时${mins}分钟`
}

async function selectSubject(id: number) {
  currentSubjectId.value = id
  detail.value = null
  const first = routes.value.find((item) => item.subjectId === id)
  currentRouteId.value = first ? first.id : null
  if (first) await loadDetail(first.id)
}

function switchRoute(value: string | number | boolean | undefined) {
  const id = Number(value)
  if (Number.isInteger(id) && id > 0) loadDetail(id)
}

async function loadDetail(id: number) {
  currentRouteId.value = id
  detailLoading.value = true
  try {
    const response = await fetchRoute(id)
    detail.value = response.data.data
  } catch (error) {
    detail.value = null
    ElMessage.error(messageOf(error, '加载路线失败'))
  } finally {
    detailLoading.value = false
  }
}

async function markComplete(routeCourseId: number, courseId: number) {
  if (!detail.value || completingId.value) return
  const course = detail.value.courses.find((item) => item.routeCourseId === routeCourseId)
  if (!course || course.completed) return
  completingId.value = routeCourseId
  try {
    const response = await completeRouteCourse(routeCourseId, courseId)
    course.completed = true
    detail.value.completedCourseCount = response.data.data.completedCourseCount
    detail.value.courseCount = response.data.data.courseCount
    detail.value.progress = response.data.data.progress
    const summary = routes.value.find((item) => item.id === detail.value?.id)
    if (summary) {
      summary.completedCourseCount = response.data.data.completedCourseCount
      summary.progress = response.data.data.progress
    }
  } catch (error) {
    ElMessage.error(messageOf(error, '标记完成失败'))
  } finally {
    completingId.value = null
  }
}
</script>

<template>
  <div v-loading="loading" class="routes">
    <section class="subjects">
      <button
        v-for="item in subjects"
        :key="item.id"
        type="button"
        class="subject"
        :class="{ on: item.id === currentSubjectId }"
        :style="{ '--theme': item.themeColor || '#409eff' }"
        @click="selectSubject(item.id)"
      >
        <span class="emoji">{{ item.icon || '📘' }}</span>
        <strong>{{ item.name }}</strong>
        <em>{{ courseCount(item.id) }} 门课程</em>
      </button>
    </section>

    <section v-if="!currentSubject" class="panel">
      <el-empty description="请选择一个学科方向" />
    </section>
    <section v-else-if="subjectRoutes.length === 0" class="panel">
      <el-empty description="该学科暂无学习路线" />
    </section>
    <section v-else v-loading="detailLoading" class="panel">
      <el-radio-group v-if="subjectRoutes.length > 1" :model-value="currentRouteId" class="switcher" @change="switchRoute">
        <el-radio-button v-for="item in subjectRoutes" :key="item.id" :value="item.id">{{ item.name }}</el-radio-button>
      </el-radio-group>
      <template v-if="detail">
        <header class="head">
          <h2>{{ detail.icon || currentSubject.icon }} {{ detail.name }}</h2>
          <p>{{ detail.targetEducation || '适合学历未填写' }}</p>
          <p class="desc">{{ detail.description || '暂无描述' }}</p>
          <p class="meta">共 {{ detail.courseCount }} 门课程 · 总计 {{ formatMinutes(detail.totalMinutes) }} · 按列表顺序学习 · 已完成 {{ detail.completedCourseCount }}/{{ detail.courseCount }}</p>
          <el-progress :percentage="detail.progress" :stroke-width="8" />
        </header>
        <el-empty v-if="detail.courses.length === 0" description="课程筹备中，敬请期待" />
        <ol v-else class="courses">
          <li v-for="(course, index) in detail.courses" :key="course.routeCourseId">
            <span class="index">{{ index + 1 }}</span>
            <img v-if="course.coverUrl" :src="course.coverUrl" alt="" referrerpolicy="no-referrer" />
            <span v-else class="cover">课</span>
            <div class="body">
              <strong>{{ course.name }}</strong>
              <p>
                {{ course.bvid || '—' }} · {{ formatMinutes(course.durationMinutes) }}
                <el-tag v-if="course.crossSubject" size="small" effect="light" type="info">跨学科补充</el-tag>
              </p>
            </div>
            <a v-if="course.link" class="go" :href="course.link" target="_blank" rel="noopener noreferrer">去B站学习</a>
            <el-checkbox
              :model-value="course.completed"
              :disabled="course.completed || completingId === course.routeCourseId"
              @change="markComplete(course.routeCourseId, course.courseId)"
            >
              {{ course.completed ? '已完成' : '标记已完成' }}
            </el-checkbox>
          </li>
        </ol>
      </template>
    </section>
  </div>
</template>

<style scoped>
.routes { display: flex; flex-direction: column; gap: 16px; }
.subjects { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 12px; }
.subject { display: flex; flex-direction: column; align-items: flex-start; gap: 6px; padding: 14px; border: 1px solid #ebeef5; border-radius: 10px; background: #fff; cursor: pointer; text-align: left; }
.subject.on { border-color: var(--theme); box-shadow: 0 0 0 1px var(--theme); }
.emoji { font-size: 24px; }
.subject strong { color: #303133; font-size: 14px; line-height: 1.4; }
.subject em { color: #909399; font-style: normal; font-size: 12px; }
.panel { background: #fff; border-radius: 12px; padding: 20px; min-height: 280px; }
.switcher { margin-bottom: 16px; }
.head h2 { margin: 0; font-size: 20px; }
.head p { margin: 8px 0 0; color: #606266; }
.desc { line-height: 1.6; }
.meta { color: #909399; font-size: 13px; }
.courses { list-style: none; margin: 16px 0 0; padding: 0; }
.courses li { display: flex; align-items: center; gap: 12px; padding: 12px 0; border-top: 1px solid #ebeef5; }
.index { width: 24px; color: #409eff; font-weight: 600; }
.courses img, .cover { width: 96px; height: 54px; border-radius: 6px; object-fit: cover; background: #f5f7fa; flex: none; }
.cover { display: grid; place-items: center; color: #c0c4cc; }
.body { flex: 1; min-width: 0; }
.body strong { display: block; }
.body p { margin: 4px 0 0; color: #909399; font-size: 12px; }
.go { color: #409eff; white-space: nowrap; }
@media (max-width: 720px) {
  .courses li { flex-wrap: wrap; }
}
</style>
