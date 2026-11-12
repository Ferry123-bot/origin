<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Check, Close, Delete, Download, Edit, Plus, RefreshRight, Upload, View } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type UploadFile, type UploadInstance } from 'element-plus'
import type { CheckboxValueType } from 'element-plus'
import type { ApiResponse } from '@/types/api'
import { useUserStore } from '@/store/user'
import {
  auditQuestion,
  batchDeleteQuestions,
  batchPassQuestions,
  batchQuestionStatus,
  changeQuestionStatus,
  confirmQuestionImport,
  createQuestion,
  deleteQuestion,
  downloadQuestionTemplate,
  exportQuestions,
  fetchQuestion,
  fetchQuestionStages,
  fetchQuestionStats,
  fetchQuestions,
  previewQuestionImport,
  resubmitQuestion,
  updateQuestion,
  type ImportPreview,
  type QuestionDetail,
  type QuestionForm,
  type QuestionItem,
  type QuestionOption,
  type QuestionStats,
  type StageOption,
} from '@/api/question'

const LETTERS = ['A', 'B', 'C', 'D', 'E', 'F']
const user = useUserStore()
const loading = ref(false)
const tableLoading = ref(false)
const saving = ref(false)
const exporting = ref(false)
const editorVisible = ref(false)
const detailVisible = ref(false)
const importVisible = ref(false)
const previewing = ref(false)
const importing = ref(false)
const editingId = ref<number | null>(null)
const editingStatus = ref(3)
const correctKey = ref('A')
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const rows = ref<QuestionItem[]>([])
const selected = ref<QuestionItem[]>([])
const stages = ref<StageOption[]>([])
const detail = ref<QuestionDetail | null>(null)
const preview = ref<ImportPreview | null>(null)
const importKey = ref(0)
const formRef = ref<FormInstance>()
const uploadRef = ref<UploadInstance>()
const stats = reactive<QuestionStats>({ total: 0, published: 0, pending: 0, single: 0, multiple: 0, judge: 0, shortAnswer: 0 })
const query = reactive({ keyword: '', stageId: undefined as number | undefined, type: undefined as number | undefined, difficulty: undefined as number | undefined, status: undefined as number | undefined })
const form = reactive<QuestionForm>(emptyForm())

const stageGroups = computed(() => {
  const groups = new Map<string, StageOption[]>()
  for (const stage of stages.value) {
    const list = groups.get(stage.subjectName) || []
    list.push(stage)
    groups.set(stage.subjectName, list)
  }
  return [...groups.entries()]
})

const statusText = computed(() => (editingId.value ? statusName(editingStatus.value) : '待审核'))

let keywordTimer = 0

onMounted(async () => {
  await Promise.all([loadStats(), loadStages(), load()])
})

function emptyForm(): QuestionForm {
  return {
    content: '',
    stageId: null,
    type: 1,
    difficulty: 2,
    tags: '',
    analysis: '',
    answer: '',
    options: defaultOptions(),
  }
}

function defaultOptions(): QuestionOption[] {
  return LETTERS.slice(0, 4).map((key) => ({ key, text: '', correct: key === 'A' }))
}

async function loadStats() {
  const response = await fetchQuestionStats()
  Object.assign(stats, response.data.data)
}

async function loadStages() {
  const response = await fetchQuestionStages()
  stages.value = response.data.data
}

async function load() {
  tableLoading.value = true
  try {
    const response = await fetchQuestions({
      keyword: query.keyword.trim() || undefined,
      stageId: query.stageId,
      type: query.type,
      difficulty: query.difficulty,
      status: query.status,
      page: page.value,
      pageSize: pageSize.value,
    })
    rows.value = response.data.data.list
    total.value = response.data.data.total
    selected.value = []
  } catch (error) {
    ElMessage.error(textOf(error, '题目加载失败'))
  } finally {
    tableLoading.value = false
    loading.value = false
  }
}

function onKeywordInput() {
  window.clearTimeout(keywordTimer)
  keywordTimer = window.setTimeout(() => {
    page.value = 1
    load()
  }, 300)
}

function onFilter() {
  page.value = 1
  load()
}

function onSelect(items: QuestionItem[]) {
  selected.value = items
}

function openCreate() {
  editingId.value = null
  editingStatus.value = 3
  Object.assign(form, emptyForm())
  correctKey.value = 'A'
  editorVisible.value = true
}

async function openEdit(row: QuestionItem) {
  try {
    const response = await fetchQuestion(row.id)
    fillForm(response.data.data)
    editorVisible.value = true
  } catch (error) {
    ElMessage.error(textOf(error, '题目加载失败'))
  }
}

function fillForm(item: QuestionDetail) {
  editingId.value = item.id
  editingStatus.value = item.status
  form.content = item.content
  form.stageId = item.stageId
  form.type = item.type
  form.difficulty = item.difficulty
  form.tags = item.tags || ''
  form.analysis = item.analysis || ''
  form.answer = item.answer || ''
  form.options = item.options.length ? item.options.map((option) => ({ ...option })) : defaultOptions()
  correctKey.value = item.options.find((option) => option.correct)?.key || 'A'
}

function onTypeChange() {
  if (form.type === 1 || form.type === 2) {
    if (form.options.length < 2) form.options = defaultOptions()
    if (form.type === 1) correctKey.value = form.options.find((option) => option.correct)?.key || form.options[0].key
    return
  }
  if (form.type === 3 && form.answer !== '正确' && form.answer !== '错误') form.answer = '正确'
}

function addOption() {
  if (form.options.length >= 6) return
  form.options.push({ key: LETTERS[form.options.length], text: '', correct: false })
}

function removeOption(index: number) {
  if (form.options.length <= 2) return
  form.options.splice(index, 1)
  form.options.forEach((option, position) => {
    option.key = LETTERS[position]
  })
  if (!form.options.some((option) => option.key === correctKey.value)) correctKey.value = form.options[0].key
}

function markOption(option: QuestionOption, value: CheckboxValueType) {
  option.correct = value === true
}

async function save() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid || form.stageId == null) return
  const payload = buildPayload()
  if (!payload) return
  saving.value = true
  try {
    if (editingId.value) await updateQuestion(editingId.value, payload)
    else await createQuestion(payload)
    ElMessage.success(editingId.value ? '已保存' : '已提交审核')
    editorVisible.value = false
    await Promise.all([loadStats(), load()])
    if (detail.value && editingId.value === detail.value.id) await openDetail(detail.value)
  } catch (error) {
    ElMessage.error(textOf(error, '保存失败'))
  } finally {
    saving.value = false
  }
}

function buildPayload(): QuestionForm | null {
  if (form.type === 1 || form.type === 2) {
    if (form.options.some((option) => !option.text.trim())) {
      ElMessage.warning('请填写每个选项的内容')
      return null
    }
    const options = form.options.map((option) => ({
      key: option.key,
      text: option.text.trim(),
      correct: form.type === 1 ? option.key === correctKey.value : option.correct,
    }))
    if (!options.some((option) => option.correct)) {
      ElMessage.warning('请至少指定一个正确答案')
      return null
    }
    return { ...form, content: form.content.trim(), tags: form.tags.trim(), analysis: form.analysis.trim(), answer: '', options }
  }
  if (form.type === 3 && form.answer !== '正确' && form.answer !== '错误') {
    ElMessage.warning('请选择判断题答案')
    return null
  }
  if (form.type === 4 && !form.answer.trim()) {
    ElMessage.warning('请填写参考答案')
    return null
  }
  return { ...form, content: form.content.trim(), tags: form.tags.trim(), analysis: form.analysis.trim(), answer: form.answer.trim(), options: [] }
}

async function openDetail(row: QuestionItem) {
  try {
    const response = await fetchQuestion(row.id)
    detail.value = response.data.data
    detailVisible.value = true
  } catch (error) {
    ElMessage.error(textOf(error, '题目加载失败'))
  }
}

async function remove(row: QuestionItem) {
  try {
    await ElMessageBox.confirm('删除后不可恢复。已被练习引用的题目不能删除。', '删除题目', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteQuestion(row.id)
    ElMessage.success('已删除')
    if (detail.value?.id === row.id) detailVisible.value = false
    if (rows.value.length === 1 && page.value > 1) page.value -= 1
    await Promise.all([loadStats(), load()])
  } catch (error) {
    ElMessage.error(textOf(error, '删除失败'))
  }
}

async function pass(row: QuestionItem) {
  try {
    await auditQuestion(row.id, true)
    ElMessage.success('已通过并上架')
    await refreshAfter(row.id)
  } catch (error) {
    ElMessage.error(textOf(error, '审核失败'))
  }
}

async function reject(row: QuestionItem) {
  let reason = ''
  try {
    const result = await ElMessageBox.prompt('请填写驳回原因', '审核不通过', {
      inputValidator: (value) => (value && value.trim() && value.trim().length <= 255 ? true : '请填写 1~255 字的驳回原因'),
    })
    reason = result.value.trim()
  } catch {
    return
  }
  try {
    await auditQuestion(row.id, false, reason)
    ElMessage.success('已驳回')
    await refreshAfter(row.id)
  } catch (error) {
    ElMessage.error(textOf(error, '审核失败'))
  }
}

async function toggleStatus(row: QuestionItem) {
  try {
    await changeQuestionStatus(row.id, row.status === 1 ? 'off' : 'on')
    ElMessage.success(row.status === 1 ? '已下架' : '已重新提交审核')
    await refreshAfter(row.id)
  } catch (error) {
    ElMessage.error(textOf(error, '状态更新失败'))
  }
}

async function resubmit(row: QuestionItem) {
  try {
    await resubmitQuestion(row.id)
    ElMessage.success('已重新提交审核')
    await refreshAfter(row.id)
  } catch (error) {
    ElMessage.error(textOf(error, '提交失败'))
  }
}

async function refreshAfter(id: number) {
  await Promise.all([loadStats(), load()])
  if (detail.value?.id === id && detailVisible.value) {
    const current = rows.value.find((item) => item.id === id)
    if (current) await openDetail(current)
    else {
      try {
        const response = await fetchQuestion(id)
        detail.value = response.data.data
      } catch {
        detailVisible.value = false
      }
    }
  }
}

async function runBatchStatus(action: 'on' | 'off') {
  const ids = selected.value.map((item) => item.id)
  try {
    const response = await batchQuestionStatus(ids, action)
    const result = response.data.data
    if (!result.affected) {
      ElMessage.warning(action === 'off' ? '所选题目都不是已上架，不能下架' : '所选题目都不是已下架，不能重新上架')
      return
    }
    ElMessage.success(result.skipped ? `已处理 ${result.affected} 道，跳过 ${result.skipped} 道` : `已处理 ${result.affected} 道`)
    await Promise.all([loadStats(), load()])
  } catch (error) {
    ElMessage.error(textOf(error, '批量操作失败'))
  }
}

async function runBatchPass() {
  try {
    const response = await batchPassQuestions(selected.value.map((item) => item.id))
    const result = response.data.data
    if (!result.affected) {
      ElMessage.warning('所选题目都不是待审核，不能通过')
      return
    }
    ElMessage.success(result.skipped ? `已通过 ${result.affected} 道并上架，跳过 ${result.skipped} 道` : `已通过 ${result.affected} 道并上架`)
    await Promise.all([loadStats(), load()])
  } catch (error) {
    ElMessage.error(textOf(error, '批量通过失败'))
  }
}

async function runBatchDelete() {
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${selected.value.length} 道题目？已被练习引用的会跳过。`, '批量删除', { type: 'warning' })
  } catch {
    return
  }
  try {
    const response = await batchDeleteQuestions(selected.value.map((item) => item.id))
    const result = response.data.data
    ElMessage.success(result.skipped ? `已删除 ${result.affected} 道，跳过 ${result.skipped} 道` : `已删除 ${result.affected} 道`)
    if (rows.value.length === selected.value.length && page.value > 1) page.value -= 1
    await Promise.all([loadStats(), load()])
  } catch (error) {
    ElMessage.error(textOf(error, '批量删除失败'))
  }
}

function openImport() {
  preview.value = null
  importKey.value += 1
  importVisible.value = true
}

function clearPreview() {
  preview.value = null
}

async function onImportFile(file: UploadFile) {
  if (file.status !== 'ready' || !file.raw) return
  previewing.value = true
  preview.value = null
  try {
    const response = await previewQuestionImport(file.raw)
    preview.value = response.data.data
  } catch (error) {
    uploadRef.value?.clearFiles()
    ElMessage.error(textOf(error, '解析失败'))
  } finally {
    previewing.value = false
  }
}

async function confirmImport() {
  if (!preview.value?.token) return
  importing.value = true
  try {
    const response = await confirmQuestionImport(preview.value.token)
    ElMessage.success(`已导入 ${response.data.data.imported} 道题目，状态为待审核`)
    importVisible.value = false
    page.value = 1
    await Promise.all([loadStats(), load()])
  } catch (error) {
    ElMessage.error(textOf(error, '导入失败'))
  } finally {
    importing.value = false
  }
}

async function downloadTemplate() {
  try {
    await downloadQuestionTemplate()
  } catch (error) {
    ElMessage.error(textOf(error, '模板下载失败'))
  }
}

async function exportFile() {
  exporting.value = true
  try {
    await exportQuestions({
      keyword: query.keyword.trim() || undefined,
      stageId: query.stageId,
      type: query.type,
      difficulty: query.difficulty,
      status: query.status,
    })
  } catch (error) {
    ElMessage.error(textOf(error, '导出失败'))
  } finally {
    exporting.value = false
  }
}

function statusName(status: number) {
  return ({ 1: '已上架', 2: '已下架', 3: '待审核', 5: '已驳回' } as Record<number, string>)[status] || '—'
}

function statusType(status: number) {
  if (status === 1) return 'success'
  if (status === 3) return 'warning'
  if (status === 5) return 'danger'
  return 'info'
}

function typeName(type: number) {
  return ({ 1: '单选', 2: '多选', 3: '判断', 4: '简答' } as Record<number, string>)[type] || '—'
}

function typeTag(type: number) {
  return ({ 1: 'primary', 2: 'success', 3: 'warning', 4: 'info' } as Record<number, 'primary' | 'success' | 'warning' | 'info'>)[type] || 'info'
}

function difficultyName(difficulty: number) {
  return ({ 1: '简单', 2: '中等', 3: '困难' } as Record<number, string>)[difficulty] || '—'
}

function difficultyTag(difficulty: number) {
  return ({ 1: 'success', 2: 'warning', 3: 'danger' } as Record<number, 'success' | 'warning' | 'danger'>)[difficulty] || 'info'
}

function accuracyText(value: number | null) {
  return value == null ? '—' : `${value}%`
}

function textOf(error: unknown, fallback: string) {
  const body = error as ApiResponse<unknown>
  return body?.message || fallback
}
</script>

<template>
  <div v-loading="loading">
    <div class="stats">
      <div class="stat-card"><div class="stat-icon blue">题</div><div><strong>{{ stats.total }}</strong><p>题目总数</p></div></div>
      <div class="stat-card"><div class="stat-icon green">架</div><div><strong>{{ stats.published }}</strong><p>已上架</p></div></div>
      <div class="stat-card"><div class="stat-icon orange">审</div><div><strong>{{ stats.pending }}</strong><p>待审核</p></div></div>
      <div class="stat-card"><div class="stat-icon blue">单</div><div><strong>{{ stats.single }}</strong><p>单选题</p></div></div>
      <div class="stat-card"><div class="stat-icon green">多</div><div><strong>{{ stats.multiple }}</strong><p>多选题</p></div></div>
      <div class="stat-card"><div class="stat-icon orange">判</div><div><strong>{{ stats.judge }}</strong><p>判断题</p></div></div>
      <div class="stat-card"><div class="stat-icon blue">简</div><div><strong>{{ stats.shortAnswer }}</strong><p>简答题</p></div></div>
    </div>

    <section class="panel">
      <div class="toolbar">
        <div class="filters">
          <el-input v-model="query.keyword" clearable placeholder="搜索题干" style="width: 220px" @input="onKeywordInput" @clear="onKeywordInput" />
          <el-select v-model="query.stageId" clearable placeholder="全部阶段" style="width: 240px" @change="onFilter">
            <el-option-group v-for="[subject, items] in stageGroups" :key="subject" :label="subject">
              <el-option v-for="item in items" :key="item.id" :label="item.name" :value="item.id" />
            </el-option-group>
          </el-select>
          <el-select v-model="query.type" clearable placeholder="全部题型" style="width: 130px" @change="onFilter">
            <el-option label="单选" :value="1" />
            <el-option label="多选" :value="2" />
            <el-option label="判断" :value="3" />
            <el-option label="简答" :value="4" />
          </el-select>
          <el-select v-model="query.difficulty" clearable placeholder="全部难度" style="width: 130px" @change="onFilter">
            <el-option label="简单" :value="1" />
            <el-option label="中等" :value="2" />
            <el-option label="困难" :value="3" />
          </el-select>
          <el-select v-model="query.status" clearable placeholder="全部状态" style="width: 130px" @change="onFilter">
            <el-option label="已上架" :value="1" />
            <el-option label="已下架" :value="2" />
            <el-option label="待审核" :value="3" />
            <el-option label="已驳回" :value="5" />
          </el-select>
        </div>
        <div class="toolbar-actions">
          <el-button v-if="user.has('question:export')" :icon="Download" :loading="exporting" @click="exportFile">导出</el-button>
          <el-button v-if="user.has('question:import')" :icon="Upload" @click="openImport">导入</el-button>
          <el-button v-if="user.has('question:add')" type="primary" :icon="Plus" @click="openCreate">新增题目</el-button>
        </div>
      </div>

      <div v-if="selected.length" class="batch">
        <span>已选 {{ selected.length }} 道</span>
        <el-button v-if="user.has('question:audit')" size="small" type="success" :icon="Check" @click="runBatchPass">批量通过</el-button>
        <el-button v-if="user.has('question:edit')" size="small" @click="runBatchStatus('on')">批量上架</el-button>
        <el-button v-if="user.has('question:edit')" size="small" @click="runBatchStatus('off')">批量下架</el-button>
        <el-button v-if="user.has('question:delete')" size="small" type="danger" @click="runBatchDelete">批量删除</el-button>
      </div>

      <el-table v-loading="tableLoading" :data="rows" row-key="id" @selection-change="onSelect">
        <el-table-column type="selection" width="46" />
        <el-table-column prop="id" label="ID" width="72" />
        <el-table-column label="题目" width="400">
          <template #default="{ row }">
            <button type="button" class="stem" @click="openDetail(row)">{{ row.content }}</button>
          </template>
        </el-table-column>
        <el-table-column label="阶段" min-width="180">
          <template #default="{ row }">
            <div>{{ row.stageName || '—' }}</div>
            <div class="sub">{{ row.subjectName }}</div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="88">
          <template #default="{ row }">
            <el-tag :type="typeTag(row.type)" effect="light">{{ typeName(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="难度" width="88">
          <template #default="{ row }">
            <el-tag :type="difficultyTag(row.difficulty)" effect="light">{{ difficultyName(row.difficulty) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" effect="light">{{ statusName(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="150" />
        <el-table-column label="操作" width="292" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button v-if="row.status === 3 && user.has('question:audit')" link type="success" :icon="Check" @click="pass(row)">通过</el-button>
              <el-button v-if="row.status === 3 && user.has('question:audit')" link type="danger" :icon="Close" @click="reject(row)">不通过</el-button>
              <el-button v-if="row.status === 1 && user.has('question:edit')" link type="primary" @click="toggleStatus(row)">下架</el-button>
              <el-button v-if="row.status === 2 && user.has('question:edit')" link type="primary" @click="toggleStatus(row)">上架</el-button>
              <el-button v-if="row.status === 5 && user.has('question:edit')" link type="primary" :icon="RefreshRight" @click="resubmit(row)">重新提交</el-button>
              <el-button v-if="user.has('question:edit')" link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
              <el-button v-if="user.has('question:delete')" link type="danger" :icon="Delete" @click="remove(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无匹配的题目，试试调整筛选条件" />
        </template>
      </el-table>
      <el-pagination
        v-if="total > 0"
        v-model:current-page="page"
        v-model:page-size="pageSize"
        class="pager"
        background
        layout="total, prev, pager, next"
        :total="total"
        @current-change="load"
      />
    </section>

    <el-dialog v-model="editorVisible" :title="editingId ? '编辑题目' : '新增题目'" width="760px" destroy-on-close>
      <el-form ref="formRef" :model="form" label-width="88px">
        <el-form-item label="题目内容" prop="content" :rules="[{ required: true, message: '请填写题目内容', trigger: 'blur' }]">
          <el-input v-model="form.content" type="textarea" :rows="5" maxlength="20000" show-word-limit placeholder="题干，可以包含代码" />
        </el-form-item>
        <el-form-item label="阶段" prop="stageId" :rules="[{ required: true, message: '请选择阶段', trigger: 'change' }]">
          <el-select v-model="form.stageId" placeholder="请选择阶段" filterable style="width: 100%">
            <el-option-group v-for="[subject, items] in stageGroups" :key="subject" :label="subject">
              <el-option v-for="item in items" :key="item.id" :label="item.name" :value="item.id" />
            </el-option-group>
          </el-select>
        </el-form-item>
        <div class="pair">
          <el-form-item label="类型" prop="type" :rules="[{ required: true, message: '请选择题型', trigger: 'change' }]">
            <el-select v-model="form.type" style="width: 180px" @change="onTypeChange">
              <el-option label="单选" :value="1" />
              <el-option label="多选" :value="2" />
              <el-option label="判断" :value="3" />
              <el-option label="简答" :value="4" />
            </el-select>
          </el-form-item>
          <el-form-item label="难度" prop="difficulty" :rules="[{ required: true, message: '请选择难度', trigger: 'change' }]">
            <el-select v-model="form.difficulty" style="width: 180px">
              <el-option label="简单" :value="1" />
              <el-option label="中等" :value="2" />
              <el-option label="困难" :value="3" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="标签">
          <el-input v-model="form.tags" maxlength="255" placeholder="多个标签用逗号分隔" />
        </el-form-item>
        <el-form-item v-if="form.type === 1 || form.type === 2" label="选项管理">
          <div class="options">
            <div v-for="(option, index) in form.options" :key="option.key" class="option-row">
              <span class="option-idx">{{ option.key.toLowerCase() }}</span>
              <el-input v-model="option.text" maxlength="500" :placeholder="`选项 ${option.key.toLowerCase()}`" />
              <el-radio v-if="form.type === 1" v-model="correctKey" :value="option.key">正确</el-radio>
              <el-checkbox v-else :model-value="option.correct" @change="markOption(option, $event)">正确</el-checkbox>
              <button type="button" class="option-remove" :class="{ hidden: form.options.length <= 2 }" @click="removeOption(index)">×</button>
            </div>
            <button type="button" class="option-add" :disabled="form.options.length >= 6" @click="addOption">+ 添加选项</button>
          </div>
        </el-form-item>
        <el-form-item v-else-if="form.type === 3" label="正确答案">
          <el-radio-group v-model="form.answer">
            <el-radio value="正确">正确</el-radio>
            <el-radio value="错误">错误</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-else label="参考答案">
          <el-input v-model="form.answer" type="textarea" :rows="4" maxlength="20000" placeholder="简答参考答案" />
        </el-form-item>
        <el-form-item label="解析">
          <el-input v-model="form.analysis" type="textarea" :rows="3" maxlength="20000" placeholder="选填" />
        </el-form-item>
        <el-form-item label="状态">
          <el-input :model-value="statusText" disabled />
          <p v-if="editingStatus === 5" class="hint">保存后会重新进入待审核。</p>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="题目详情" size="560px">
      <template v-if="detail">
        <div class="meta">
          <el-tag effect="light">{{ detail.subjectName }} / {{ detail.stageName }}</el-tag>
          <el-tag effect="light">{{ typeName(detail.type) }}</el-tag>
          <el-tag effect="light">{{ difficultyName(detail.difficulty) }}</el-tag>
          <el-tag :type="statusType(detail.status)" effect="light">{{ statusName(detail.status) }}</el-tag>
        </div>
        <p v-if="detail.rejectReason" class="reject">驳回原因：{{ detail.rejectReason }}</p>
        <pre class="content">{{ detail.content }}</pre>
        <ul v-if="detail.options.length" class="detail-options">
          <li v-for="option in detail.options" :key="option.key" :class="{ correct: option.correct }">
            <strong>{{ option.key }}.</strong> {{ option.text }}
            <span v-if="option.correct">正确答案</span>
          </li>
        </ul>
        <p v-else-if="detail.answer" class="block"><strong>参考答案</strong><pre>{{ detail.answer }}</pre></p>
        <p v-if="detail.tags" class="block"><strong>标签</strong>{{ detail.tags }}</p>
        <p v-if="detail.analysis" class="block"><strong>解析</strong><pre>{{ detail.analysis }}</pre></p>
        <div class="practice">
          <span>答题次数 {{ detail.doneCount }}</span>
          <span>正确次数 {{ detail.correctCount }}</span>
          <span>正确率 {{ accuracyText(detail.accuracyRate) }}</span>
        </div>
        <div class="actions detail-actions">
          <el-button v-if="detail.status === 3 && user.has('question:audit')" type="success" :icon="Check" @click="pass(detail)">通过</el-button>
          <el-button v-if="detail.status === 3 && user.has('question:audit')" type="danger" :icon="Close" @click="reject(detail)">不通过</el-button>
          <el-button v-if="detail.status === 1 && user.has('question:edit')" @click="toggleStatus(detail)">下架</el-button>
          <el-button v-if="detail.status === 2 && user.has('question:edit')" @click="toggleStatus(detail)">上架</el-button>
          <el-button v-if="detail.status === 5 && user.has('question:edit')" :icon="RefreshRight" @click="resubmit(detail)">重新提交</el-button>
          <el-button v-if="user.has('question:edit')" :icon="Edit" @click="openEdit(detail)">编辑</el-button>
          <el-button :icon="View" @click="detailVisible = false">关闭</el-button>
        </div>
      </template>
    </el-drawer>

    <el-dialog v-model="importVisible" title="导入题目" width="920px" destroy-on-close>
      <div class="format">
        <p>支持 .xlsx / .xls，单次最多 500 道。模板含三张表，表头与现有题库表格一致：</p>
        <p><strong>单选题 / 多选题：</strong>题干、阶段、题型、难度、选项A~F、正确答案、解析、标签</p>
        <p><strong>简答题：</strong>题干、阶段、题型、难度、正确答案、解析、标签</p>
        <p>题型可填「单选 / 多选 / 简答」。阶段名称须与学科管理中的阶段完全一致。只导入校验通过的行，入库后为待审核。</p>
        <el-button link type="primary" @click="downloadTemplate">下载模板</el-button>
      </div>
      <el-upload
        :key="importKey"
        ref="uploadRef"
        drag
        :auto-upload="false"
        :limit="1"
        accept=".xlsx,.xls"
        :on-change="onImportFile"
        :on-remove="clearPreview"
      >
        <div class="upload-text">将文件拖到这里，或点击选择</div>
        <div class="upload-hint">表头在第一行，从第二行开始是题目</div>
      </el-upload>
      <div v-if="preview" v-loading="previewing" class="summary">
        <span>共 {{ preview.total }} 行</span>
        <span class="ok">通过 {{ preview.valid }}</span>
        <span class="bad">错误 {{ preview.invalid }}</span>
        <em>确认后只写入通过的题目</em>
      </div>
      <el-table v-if="preview" :data="preview.rows" max-height="360" size="small">
        <el-table-column prop="sheet" label="工作表" width="100" />
        <el-table-column prop="row" label="行" width="60" />
        <el-table-column prop="content" label="题干" min-width="220" show-overflow-tooltip />
        <el-table-column prop="type" label="题型" width="80" />
        <el-table-column prop="stage" label="阶段" width="160" show-overflow-tooltip />
        <el-table-column label="结果" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ok' ? 'success' : 'danger'" effect="light">{{ row.status === 'ok' ? '通过' : '错误' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="说明" min-width="180">
          <template #default="{ row }">{{ row.messages.join('；') || '—' }}</template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!preview?.token" :loading="importing" @click="confirmImport">确认导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.stats { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: 12px; margin-bottom: 16px; }
.stat-card { display: flex; align-items: center; gap: 12px; padding: 16px; background: #fff; border-radius: 12px; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06); }
.stat-icon { width: 40px; height: 40px; border-radius: 10px; display: grid; place-items: center; font-weight: 700; }
.stat-icon.blue { background: rgba(64, 158, 255, 0.1); color: #409eff; }
.stat-icon.green { background: rgba(103, 194, 58, 0.1); color: #67c23a; }
.stat-icon.orange { background: rgba(230, 162, 60, 0.1); color: #e6a23c; }
.stat-card strong { display: block; font-size: 24px; line-height: 1.2; }
.stat-card p { margin: 4px 0 0; font-size: 13px; color: #909399; }
.panel { background: #fff; border-radius: 12px; padding: 20px; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06); }
.toolbar { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 16px; flex-wrap: wrap; }
.filters, .toolbar-actions, .actions, .batch { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.batch { margin-bottom: 12px; }
.stem { display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; width: 100%; padding: 0; border: 0; background: transparent; color: #409eff; text-align: left; line-height: 1.5; cursor: pointer; white-space: pre-wrap; }
.sub { margin-top: 2px; color: #909399; font-size: 12px; }
.row-actions { display: flex; flex-wrap: nowrap; align-items: center; }
.row-actions :deep(.el-button) { margin-left: 0; padding-left: 6px; padding-right: 6px; }
.actions :deep(.el-button) { margin-left: 0; }
.pager { margin-top: 16px; justify-content: flex-end; }
.pair { display: flex; gap: 12px; }
.options { width: 100%; }
.option-row { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.option-row .el-input { flex: 1; }
.option-idx { width: 28px; height: 28px; border-radius: 50%; background: #f5f7fa; display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 600; color: #606266; flex-shrink: 0; }
.option-row :deep(.el-radio),
.option-row :deep(.el-checkbox) { margin-right: 0; height: auto; flex-shrink: 0; }
.option-row :deep(.el-radio__label),
.option-row :deep(.el-checkbox__label) { padding-left: 6px; color: #909399; }
.option-remove { width: 28px; height: 28px; border: 0; background: transparent; color: #f56c6c; cursor: pointer; font-size: 18px; line-height: 1; border-radius: 4px; flex-shrink: 0; }
.option-remove:hover { background: rgba(245, 108, 108, 0.1); }
.option-remove.hidden { visibility: hidden; pointer-events: none; }
.option-add { padding: 6px 14px; border: 1px dashed #dcdfe6; border-radius: 6px; background: #fff; color: #909399; cursor: pointer; font-size: 13px; }
.option-add:hover:not(:disabled) { border-color: #409eff; color: #409eff; }
.option-add:disabled { cursor: not-allowed; opacity: 0.5; }
.hint { margin: 6px 0 0; color: #909399; font-size: 12px; }
.meta { display: flex; flex-wrap: wrap; gap: 8px; }
.reject { margin: 12px 0 0; color: #f56c6c; }
.content, .block pre { margin: 16px 0 0; white-space: pre-wrap; word-break: break-word; font-family: inherit; line-height: 1.6; }
.detail-options { margin: 16px 0 0; padding: 0; list-style: none; }
.detail-options li { margin-bottom: 8px; padding: 8px 10px; border-radius: 8px; background: #f5f7fa; }
.detail-options li.correct { background: rgba(103, 194, 58, 0.12); }
.detail-options span { margin-left: 8px; color: #67c23a; font-size: 12px; }
.block { margin: 16px 0 0; }
.block strong { display: block; margin-bottom: 6px; }
.practice { display: flex; gap: 16px; margin-top: 16px; color: #606266; }
.detail-actions { margin-top: 20px; }
.format { margin-bottom: 16px; padding: 12px 14px; border-radius: 8px; background: #f5f7fa; color: #606266; font-size: 13px; line-height: 1.7; }
.format p { margin: 0 0 4px; }
.upload-text { color: #303133; }
.upload-hint { margin-top: 4px; color: #909399; font-size: 12px; }
.summary { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; margin: 16px 0 12px; padding: 10px 14px; border-radius: 8px; background: #f0f9eb; }
.summary .ok { color: #67c23a; font-weight: 600; }
.summary .bad { color: #f56c6c; font-weight: 600; }
.summary em { color: #909399; font-style: normal; }
@media (max-width: 1200px) { .stats { grid-template-columns: repeat(4, minmax(0, 1fr)); } }
@media (max-width: 800px) { .stats { grid-template-columns: 1fr 1fr; } }
</style>
