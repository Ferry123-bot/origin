<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CircleCheck, CircleClose, Delete, View } from '@element-plus/icons-vue'
import type { TableInstance } from 'element-plus'
import type { ApiResponse } from '@/types/api'
import { useUserStore } from '@/store/user'
import { formatTime } from '@/utils/time'
import {
  batchDeleteStudents,
  batchStudentStatus,
  changeStudentStatus,
  deleteStudent,
  fetchStudentDetail,
  fetchStudents,
  type StudentDetail,
  type StudentItem,
} from '@/api/student'

const user = useUserStore()
const loading = ref(false)
const rows = ref<StudentItem[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const query = reactive({ keyword: '', status: undefined as number | undefined })
const selected = ref<StudentItem[]>([])
const tableRef = ref<TableInstance>()
const detailVisible = ref(false)
const detail = ref<StudentDetail | null>(null)
const brokenAvatars = ref<number[]>([])

const avatarColors = [
  'linear-gradient(135deg, #667eea, #764ba2)',
  'linear-gradient(135deg, #43e97b, #38f9d7)',
  'linear-gradient(135deg, #fa709a, #fee140)',
  'linear-gradient(135deg, #4facfe, #00f2fe)',
  'linear-gradient(135deg, #f093fb, #f5576c)',
  'linear-gradient(135deg, #4bc0c8, #c779d0)',
]

onMounted(load)

async function load() {
  loading.value = true
  try {
    const response = await fetchStudents({
      keyword: query.keyword.trim() || undefined,
      status: query.status,
      page: page.value,
      pageSize: pageSize.value,
    })
    rows.value = response.data.data.list
    total.value = response.data.data.total
  } catch (error) {
    ElMessage.error(textOf(error, '加载失败'))
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function avatarColor(id: number) {
  return avatarColors[id % avatarColors.length]
}

function avatarText(name: string) {
  return name ? name.charAt(0) : '?'
}

function showAvatar(row: { id: number; avatar: string | null }) {
  return !!row.avatar && !brokenAvatars.value.includes(row.id)
}

function hideAvatar(id: number) {
  if (!brokenAvatars.value.includes(id)) brokenAvatars.value = [...brokenAvatars.value, id]
}

function selectedNames() {
  const names = selected.value.map((item) => item.nickname)
  if (names.length <= 8) return names.join('、')
  return `${names.slice(0, 8).join('、')} 等 ${names.length} 个`
}

function clearSelection() {
  tableRef.value?.clearSelection()
  selected.value = []
}

async function openDetail(row: StudentItem) {
  try {
    const response = await fetchStudentDetail(row.id)
    detail.value = response.data.data
    detailVisible.value = true
  } catch (error) {
    ElMessage.error(textOf(error, '加载详情失败'))
  }
}

async function toggleStatus(row: StudentItem) {
  const enabling = row.status !== 1
  const label = enabling ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(`确认${label}学员「${row.nickname}」吗？`, `${label}学员`, { type: 'warning' })
  } catch {
    return
  }
  try {
    await changeStudentStatus(row.id, enabling ? 1 : 0)
    ElMessage.success(`学员已${label}`)
    await load()
    if (detail.value?.id === row.id) await openDetail(row)
  } catch (error) {
    ElMessage.error(textOf(error, `${label}失败`))
  }
}

async function remove(row: StudentItem) {
  try {
    await ElMessageBox.confirm(`确认删除学员「${row.nickname}」吗？此操作不可恢复。`, '删除学员', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteStudent(row.id)
    ElMessage.success('学员已删除')
    if (detail.value?.id === row.id) detailVisible.value = false
    clearSelection()
    if (rows.value.length === 1 && page.value > 1) page.value -= 1
    await load()
  } catch (error) {
    ElMessage.error(textOf(error, '删除失败'))
  }
}

async function runBatchStatus(status: number) {
  if (!selected.value.length) {
    ElMessage.warning('请先选择学员')
    return
  }
  const label = status === 1 ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(`确认${label}以下学员吗？${selectedNames()}`, `批量${label}`, { type: 'warning' })
  } catch {
    return
  }
  try {
    await batchStudentStatus(selected.value.map((item) => item.id), status)
    ElMessage.success(`已${label} ${selected.value.length} 个学员`)
    clearSelection()
    await load()
  } catch (error) {
    ElMessage.error(textOf(error, `批量${label}失败`))
  }
}

async function runBatchDelete() {
  if (!selected.value.length) {
    ElMessage.warning('请先选择学员')
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除以下学员吗？${selectedNames()}。此操作不可恢复。`, '批量删除', { type: 'warning' })
  } catch {
    return
  }
  const count = selected.value.length
  try {
    await batchDeleteStudents(selected.value.map((item) => item.id))
    ElMessage.success(`已删除 ${count} 个学员`)
    if (detail.value && selected.value.some((item) => item.id === detail.value?.id)) detailVisible.value = false
    clearSelection()
    if (rows.value.length === count && page.value > 1) page.value -= 1
    await load()
  } catch (error) {
    ElMessage.error(textOf(error, '批量删除失败'))
  }
}

function textOf(error: unknown, fallback: string) {
  return (error as ApiResponse<unknown>)?.message || fallback
}
</script>

<template>
  <section class="panel" v-loading="loading">
    <div class="toolbar">
      <div class="filters">
        <el-input v-model="query.keyword" clearable placeholder="搜索昵称/手机号..." style="width: 220px" @keyup.enter="search" @clear="search" />
        <el-select v-model="query.status" clearable placeholder="全部状态" style="width: 140px" @change="search">
          <el-option label="已启用" :value="1" />
          <el-option label="已禁用" :value="0" />
        </el-select>
      </div>
      <div class="toolbar-actions">
        <el-button v-if="user.has('student:edit')" :icon="CircleCheck" @click="runBatchStatus(1)">批量启用</el-button>
        <el-button v-if="user.has('student:edit')" :icon="CircleClose" @click="runBatchStatus(0)">批量禁用</el-button>
        <el-button v-if="user.has('student:delete')" type="danger" :icon="Delete" @click="runBatchDelete">批量删除</el-button>
      </div>
    </div>

    <div v-if="selected.length" class="batch">
      <span>已选择 <strong>{{ selected.length }}</strong> 项</span>
      <el-button size="small" @click="clearSelection">取消选择</el-button>
    </div>

    <el-table ref="tableRef" :data="rows" row-key="id" @selection-change="selected = $event">
      <el-table-column type="selection" width="48" reserve-selection />
      <el-table-column label="学员" min-width="180">
        <template #default="{ row }">
          <button type="button" class="user-cell" @click="openDetail(row)">
            <img v-if="showAvatar(row)" class="user-avatar photo" :src="row.avatar" alt="" referrerpolicy="no-referrer" @error="hideAvatar(row.id)" />
            <span v-else class="user-avatar" :style="{ background: avatarColor(row.id) }">{{ avatarText(row.nickname) }}</span>
            <span class="user-name">{{ row.nickname }}</span>
          </button>
        </template>
      </el-table-column>
      <el-table-column prop="mobile" label="手机号" width="140" />
      <el-table-column label="注册时间" width="170">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="学习天数" width="110">
        <template #default="{ row }">{{ row.studyDays }}天</template>
      </el-table-column>
      <el-table-column label="刷题/面试" width="140">
        <template #default="{ row }">{{ row.practiceCount }}题 / {{ row.interviewCount }}次</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" effect="light">{{ row.status === 1 ? '已启用' : '已禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="250">
        <template #default="{ row }">
          <div class="ops">
            <el-button link type="primary" :icon="View" @click="openDetail(row)">查看详情</el-button>
            <el-button
              v-if="user.has('student:edit')"
              link
              :type="row.status === 1 ? 'danger' : 'success'"
              :icon="row.status === 1 ? CircleClose : CircleCheck"
              @click="toggleStatus(row)"
            >{{ row.status === 1 ? '禁用' : '启用' }}</el-button>
            <el-button v-if="user.has('student:delete')" link type="danger" :icon="Delete" @click="remove(row)">删除</el-button>
          </div>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="没有找到匹配的学员" />
      </template>
    </el-table>

    <el-pagination
      v-if="total > 0"
      class="pager"
      background
      layout="total, prev, pager, next"
      :total="total"
      :current-page="page"
      :page-size="pageSize"
      @current-change="(value: number) => { page = value; load() }"
    />

    <el-dialog v-model="detailVisible" title="学员详情" width="760px">
      <div v-if="detail" class="detail">
        <div class="profile">
          <img v-if="showAvatar(detail)" class="avatar photo" :src="detail.avatar || ''" alt="" referrerpolicy="no-referrer" @error="hideAvatar(detail.id)" />
          <span v-else class="avatar" :style="{ background: avatarColor(detail.id) }">{{ avatarText(detail.nickname) }}</span>
          <h3>{{ detail.nickname }}</h3>
          <el-tag :type="detail.status === 1 ? 'success' : 'info'" effect="light">{{ detail.status === 1 ? '已启用' : '已禁用' }}</el-tag>
          <ul>
            <li><span>手机号</span><strong>{{ detail.mobile }}</strong></li>
            <li><span>邮箱</span><strong>{{ detail.email || '—' }}</strong></li>
            <li><span>注册时间</span><strong>{{ formatTime(detail.createdAt) }}</strong></li>
            <li><span>最后登录</span><strong>{{ formatTime(detail.lastLoginAt) }}</strong></li>
          </ul>
        </div>
        <div class="stats">
          <h4>学习统计</h4>
          <div class="stat-grid">
            <div><strong class="blue">{{ detail.studyDays }}</strong><span>学习天数</span></div>
            <div><strong class="green">{{ detail.courseCount }}</strong><span>已学课程</span></div>
            <div><strong class="orange">{{ detail.practiceCount }}</strong><span>刷题总数</span></div>
            <div><strong class="purple">{{ detail.accuracy }}%</strong><span>正确率</span></div>
          </div>
          <h4>面试记录</h4>
          <div class="interview">
            <strong>{{ detail.interviewCount }}</strong>
            <span>累计面试次数</span>
          </div>
        </div>
      </div>
    </el-dialog>
  </section>
</template>

<style scoped>
.panel { background: #fff; border-radius: 12px; padding: 20px; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06); }
.toolbar { display: flex; align-items: center; justify-content: space-between; gap: 16px; flex-wrap: wrap; }
.filters, .toolbar-actions, .ops { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.ops { flex-wrap: nowrap; }
.ops :deep(.el-button) { margin-left: 0; }
.batch { display: flex; align-items: center; gap: 12px; margin-top: 16px; padding: 12px 16px; background: #f0f9ff; border-radius: 8px; color: #606266; }
.batch strong { color: #409eff; }
.user-cell { display: inline-flex; align-items: center; gap: 12px; padding: 0; border: 0; background: transparent; cursor: pointer; }
.user-avatar, .avatar { width: 36px; height: 36px; border-radius: 50%; display: inline-flex; align-items: center; justify-content: center; color: #fff; font-weight: 600; font-size: 12px; flex-shrink: 0; object-fit: cover; }
.user-name { font-size: 14px; font-weight: 500; color: #303133; }
.user-cell:hover .user-name { color: #409eff; }
.pager { margin-top: 16px; justify-content: flex-end; }
.detail { display: grid; grid-template-columns: 260px 1fr; gap: 24px; }
.profile { text-align: center; padding-right: 24px; border-right: 1px solid #f0f2f5; }
.profile .avatar { width: 64px; height: 64px; font-size: 24px; margin: 0 auto 12px; }
.profile h3 { margin: 0 0 8px; font-size: 18px; }
.profile ul { list-style: none; padding: 16px 0 0; margin: 0; text-align: left; }
.profile li { display: flex; justify-content: space-between; gap: 12px; padding: 8px 0; border-bottom: 1px solid #f5f7fa; font-size: 13px; }
.profile li span { color: #909399; }
.stats h4 { margin: 0 0 12px; font-size: 15px; }
.stat-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 24px; }
.stat-grid div, .interview { text-align: center; padding: 12px; background: #f5f7fa; border-radius: 8px; }
.stat-grid strong, .interview strong { display: block; font-size: 20px; }
.interview strong { font-size: 28px; color: #409eff; }
.stat-grid span, .interview span { display: block; margin-top: 4px; font-size: 12px; color: #909399; }
.blue { color: #409eff; }
.green { color: #67c23a; }
.orange { color: #e6a23c; }
.purple { color: #9b59b6; }
</style>
