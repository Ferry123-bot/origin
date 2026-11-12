<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Download, Search } from '@element-plus/icons-vue'
import type { ApiResponse } from '@/types/api'
import { formatTime } from '@/utils/time'
import { clearLogs, exportLogs, fetchLog, fetchLogs, type OperationLog } from '@/api/log'

const types = ['登录', '登出', '新增', '修改', '删除', '禁用启用', '导出', '其他']
const loading = ref(false)
const rows = ref<OperationLog[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const query = reactive({ keyword: '', operationType: '', dates: [] as string[] })
const detailVisible = ref(false)
const detail = ref<OperationLog | null>(null)

onMounted(load)

async function load() {
  loading.value = true
  try {
    const response = await fetchLogs({
      keyword: query.keyword || undefined,
      operationType: query.operationType || undefined,
      startDate: query.dates?.[0],
      endDate: query.dates?.[1],
      page: page.value,
      pageSize: pageSize.value,
    })
    rows.value = response.data.data.list
    total.value = response.data.data.total
  } catch (error) {
    ElMessage.error((error as ApiResponse<unknown>).message || '加载失败')
  } finally {
    loading.value = false
  }
}

const actionStyles: Record<string, string> = {
  登录: 'login',
  登出: 'logout',
  新增: 'create',
  创建: 'create',
  上传: 'upload',
  修改: 'update',
  更新: 'update',
  编辑: 'update',
  删除: 'delete',
  禁用启用: 'status',
  禁用: 'status',
  启用: 'status',
  导入: 'import',
  导出: 'export',
}

function actionClass(action: string) {
  if (actionStyles[action]) return actionStyles[action]
  const key = Object.keys(actionStyles)
    .sort((left, right) => right.length - left.length)
    .find((item) => action.includes(item))
  return key ? actionStyles[key] : 'other'
}

function search() {
  page.value = 1
  load()
}

async function openDetail(row: OperationLog) {
  try {
    const response = await fetchLog(row.id)
    detail.value = response.data.data
    detailVisible.value = true
  } catch (error) {
    ElMessage.error((error as ApiResponse<unknown>).message || '加载详情失败')
  }
}

async function download() {
  try {
    const response = await exportLogs({
      keyword: query.keyword || undefined,
      operationType: query.operationType || undefined,
      startDate: query.dates?.[0],
      endDate: query.dates?.[1],
    })
    const blob = new Blob([response.data], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = 'operation-logs.csv'
    link.click()
    URL.revokeObjectURL(url)
    ElMessage.success('已开始下载')
    await load()
  } catch (error) {
    ElMessage.error((error as ApiResponse<unknown>).message || '导出失败')
  }
}

async function clearAll() {
  try {
    await ElMessageBox.confirm('清空后日志无法恢复，确定继续？', '清空日志', { type: 'warning' })
  } catch {
    return
  }
  try {
    await clearLogs()
    ElMessage.success('日志已清空')
    page.value = 1
    await load()
  } catch (error) {
    ElMessage.error((error as ApiResponse<unknown>).message || '清空失败')
  }
}
</script>

<template>
  <section class="panel" v-loading="loading">
    <div class="filters">
      <el-input v-model="query.keyword" placeholder="用户 / 操作 / 目标" clearable style="width: 220px" @keyup.enter="search" />
      <el-select v-model="query.operationType" placeholder="全部类型" clearable style="width: 140px">
        <el-option v-for="item in types" :key="item" :label="item" :value="item" />
      </el-select>
      <el-date-picker v-model="query.dates" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" />
      <el-button type="primary" :icon="Search" @click="search">查询</el-button>
      <el-button :icon="Download" @click="download">导出日志</el-button>
      <el-button type="danger" plain :icon="Delete" @click="clearAll">清空日志</el-button>
    </div>
    <el-table :data="rows" @row-click="openDetail">
      <el-table-column prop="username" label="用户" width="140" />
      <el-table-column label="操作" width="130">
        <template #default="{ row }">
          <span class="action-tag" :class="actionClass(row.action)">{{ row.action }}</span>
        </template>
      </el-table-column>
      <el-table-column label="目标" min-width="280" class-name="target-col">
        <template #default="{ row }"><span class="target">{{ row.targetType }} / {{ row.targetName }}</span></template>
      </el-table-column>
      <el-table-column prop="ip" label="IP地址" width="150" />
      <el-table-column label="时间" width="170">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无匹配的日志记录" />
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

    <el-drawer v-model="detailVisible" title="日志详情" size="420px">
      <template v-if="detail">
        <p>操作用户：{{ detail.username }}</p>
        <p>操作类型：<span class="action-tag" :class="actionClass(detail.action)">{{ detail.action }}</span></p>
        <p class="target">操作对象：{{ detail.targetType }} / {{ detail.targetName }}</p>
        <p>IP地址：{{ detail.ip }}</p>
        <p>操作时间：{{ formatTime(detail.createdAt) }}</p>
        <p>日志ID：{{ detail.id }}</p>
      </template>
    </el-drawer>
  </section>
</template>

<style scoped>
.panel { background: #fff; border-radius: 12px; padding: 20px; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06); }
.filters { display: flex; gap: 12px; align-items: center; margin-bottom: 16px; flex-wrap: wrap; }
.pager { margin-top: 16px; justify-content: flex-end; }
.target { white-space: normal; word-break: break-all; line-height: 1.6; }
:deep(td.target-col .cell) { white-space: normal; }
.action-tag { display: inline-block; padding: 4px 10px; border-radius: 12px; font-size: 12px; line-height: 1.4; }
.action-tag.login { background: rgba(64, 158, 255, 0.1); color: #409eff; }
.action-tag.logout { background: rgba(98, 106, 239, 0.1); color: #626aef; }
.action-tag.create { background: rgba(103, 194, 58, 0.1); color: #67c23a; }
.action-tag.update { background: rgba(230, 162, 60, 0.1); color: #e6a23c; }
.action-tag.delete { background: rgba(245, 108, 108, 0.1); color: #f56c6c; }
.action-tag.status { background: rgba(250, 173, 20, 0.12); color: #d48806; }
.action-tag.import { background: rgba(114, 69, 209, 0.1); color: #7245d1; }
.action-tag.export { background: rgba(0, 180, 180, 0.1); color: #00b4b4; }
.action-tag.upload { background: rgba(250, 173, 20, 0.1); color: #faad14; }
.action-tag.other { background: rgba(144, 147, 153, 0.1); color: #909399; }
</style>
