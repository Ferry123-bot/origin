<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules, UploadFile, UploadInstance } from 'element-plus'
import { Check, Close, Delete, Download, Edit, Plus, Upload } from '@element-plus/icons-vue'
import type { ApiResponse } from '@/types/api'
import { useUserStore } from '@/store/user'
import {
  confirmCourseImport,
  createCourse,
  deleteCourse,
  downloadCourseTemplate,
  fetchCourseStats,
  fetchCourses,
  previewCourseImport,
  updateCourse,
  type CourseItem,
  type CourseStats,
  type ImportPreview,
  type SubjectOption,
} from '@/api/course'

const user = useUserStore()
const loading = ref(false)
const tableLoading = ref(false)
const stats = ref<CourseStats>({ courseCount: 0, totalHours: 0, subjectCount: 0, subjects: [] })
const rows = ref<CourseItem[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const query = reactive({ keyword: '', subjectId: null as number | null, source: null as number | null })
const brokenCovers = reactive<Record<number, boolean>>({})

const editorVisible = ref(false)
const editing = ref<CourseItem | null>(null)
const saving = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  name: '',
  coverUrl: '',
  duration: '',
  subjectId: undefined as number | undefined,
  extraIds: [] as number[],
  source: 1,
  bvid: '',
  link: '',
})

const importVisible = ref(false)
const importKey = ref(0)
const previewing = ref(false)
const importing = ref(false)
const preview = ref<ImportPreview | null>(null)
const uploadRef = ref<UploadInstance>()

const subjects = computed(() => stats.value.subjects)
const extraOptions = computed(() => subjects.value.filter((item) => item.id !== form.subjectId))
const importable = computed(() => (preview.value ? preview.value.validCount + preview.value.warningCount : 0))

const rules: FormRules = {
  name: [{ required: true, message: '请输入课程名称', trigger: 'blur' }],
  duration: [{
    validator: (_rule, value: string, callback) => {
      if (!value || !value.trim()) callback(new Error('请输入时长'))
      else if (parseDuration(value) == null) callback(new Error('时长格式为 小时:分钟，例如 4:53'))
      else callback()
    },
    trigger: 'blur',
  }],
  subjectId: [{ required: true, message: '请选择所属学科', trigger: 'change' }],
  source: [{ required: true, message: '请选择来源', trigger: 'change' }],
  bvid: [{
    validator: (_rule, value: string, callback) => {
      const text = (value || '').trim()
      if (form.source === 1 && !text) callback(new Error('来源为 B站时请填写 BV号'))
      else if (text && !/^BV[0-9A-Za-z]{10}$/.test(text)) callback(new Error('BV号格式不正确，形如 BV1xx411c7mD'))
      else callback()
    },
    trigger: 'blur',
  }],
  coverUrl: [{ validator: (_rule, value: string, callback) => urlCallback(value, '封面地址不正确', callback), trigger: 'blur' }],
  link: [{ validator: (_rule, value: string, callback) => urlCallback(value, '课程链接不正确', callback), trigger: 'blur' }],
}

let keywordTimer = 0

onMounted(load)

function parseDuration(value: string) {
  const match = value.trim().match(/^(\d{1,5}):([0-5]?\d)$/)
  if (!match) return null
  const minute = Number(match[2])
  if (minute > 59) return null
  const total = Number(match[1]) * 60 + minute
  return total >= 1 && total <= 600000 ? total : null
}

function urlCallback(value: string, message: string, callback: (error?: Error) => void) {
  const text = (value || '').trim()
  if (!text || /^https?:\/\/\S+$/i.test(text)) callback()
  else callback(new Error(message))
}

function hoursText(value: number) {
  const rounded = Math.round(value * 10) / 10
  return Number.isInteger(rounded) ? String(rounded) : rounded.toFixed(1)
}

function rowIndex(index: number) {
  return (page.value - 1) * pageSize.value + index + 1
}

async function load() {
  loading.value = true
  try {
    const [statsRes, listRes] = await Promise.all([fetchCourseStats(), requestList()])
    stats.value = statsRes.data.data
    applyList(listRes.data.data)
  } catch (error) {
    ElMessage.error((error as ApiResponse<unknown>).message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function loadList() {
  tableLoading.value = true
  try {
    const response = await requestList()
    applyList(response.data.data)
  } catch (error) {
    ElMessage.error((error as ApiResponse<unknown>).message || '加载失败')
  } finally {
    tableLoading.value = false
  }
}

function requestList() {
  return fetchCourses({
    keyword: query.keyword.trim() || undefined,
    subjectId: query.subjectId === null ? undefined : query.subjectId,
    source: query.source === null ? undefined : query.source,
    page: page.value,
    pageSize: pageSize.value,
  })
}

function applyList(data: { list: CourseItem[]; total: number }) {
  rows.value = data.list
  total.value = data.total
  for (const key of Object.keys(brokenCovers)) delete brokenCovers[Number(key)]
}

function onKeywordInput() {
  window.clearTimeout(keywordTimer)
  keywordTimer = window.setTimeout(() => {
    page.value = 1
    loadList()
  }, 300)
}

function onFilter() {
  page.value = 1
  loadList()
}

function onPageSize(value: number) {
  pageSize.value = value
  page.value = 1
  loadList()
}

async function openCreate() {
  editing.value = null
  Object.assign(form, { name: '', coverUrl: '', duration: '', subjectId: undefined, extraIds: [], source: 1, bvid: '', link: '' })
  editorVisible.value = true
  await nextTick()
  formRef.value?.clearValidate()
}

async function openEdit(row: CourseItem) {
  editing.value = row
  Object.assign(form, {
    name: row.name,
    coverUrl: row.coverUrl || '',
    duration: row.duration,
    subjectId: row.otherSubject ? 0 : row.subjectId ?? undefined,
    extraIds: row.subjects.filter((item) => item.id !== row.subjectId).map((item) => item.id),
    source: row.source,
    bvid: row.bvid || '',
    link: row.link || '',
  })
  editorVisible.value = true
  await nextTick()
  formRef.value?.clearValidate()
}

function onPrimaryChange(value: number) {
  if (value === 0) form.extraIds = []
  else form.extraIds = form.extraIds.filter((id) => id !== value)
}

async function save() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  const durationMinutes = parseDuration(form.duration)
  if (durationMinutes == null || form.subjectId === undefined) return
  saving.value = true
  const payload = {
    name: form.name.trim(),
    coverUrl: form.coverUrl.trim(),
    durationMinutes,
    source: form.source,
    bvid: form.bvid.trim(),
    link: form.link.trim(),
    subjectId: form.subjectId === 0 ? null : form.subjectId,
    subjectIds: form.subjectId === 0 ? [] : [...form.extraIds],
  }
  try {
    if (editing.value) await updateCourse(editing.value.id, payload)
    else await createCourse(payload)
    editorVisible.value = false
    ElMessage.success('已保存')
    await load()
  } catch (error) {
    ElMessage.error((error as ApiResponse<unknown>).message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function remove(row: CourseItem) {
  try {
    await ElMessageBox.confirm(`确定删除课程「${row.name}」？`, '删除课程', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteCourse(row.id)
    ElMessage.success('已删除')
    if (rows.value.length === 1 && page.value > 1) page.value -= 1
    await load()
  } catch (error) {
    ElMessage.error((error as ApiResponse<unknown>).message || '删除失败')
  }
}

function openImport() {
  preview.value = null
  importKey.value += 1
  importVisible.value = true
}

async function onImportFile(file: UploadFile) {
  if (file.status !== 'ready' || !file.raw) return
  previewing.value = true
  preview.value = null
  try {
    const response = await previewCourseImport(file.raw)
    preview.value = response.data.data
  } catch (error) {
    uploadRef.value?.clearFiles()
    ElMessage.error((error as ApiResponse<unknown>).message || '解析失败')
  } finally {
    previewing.value = false
  }
}

function clearPreview() {
  preview.value = null
}

async function confirmImport() {
  if (!preview.value?.token) return
  importing.value = true
  try {
    const response = await confirmCourseImport(preview.value.token)
    ElMessage.success(`已导入 ${response.data.data.imported} 门课程`)
    importVisible.value = false
    page.value = 1
    await load()
  } catch (error) {
    ElMessage.error((error as ApiResponse<unknown>).message || '导入失败')
  } finally {
    importing.value = false
  }
}

async function downloadTemplate() {
  try {
    await downloadCourseTemplate()
  } catch (error) {
    ElMessage.error((error as ApiResponse<unknown>).message || '模板下载失败')
  }
}

function statusText(status: ImportPreview['rows'][number]['status']) {
  if (status === 'ok') return '有效'
  if (status === 'warning') return '警告'
  return '无效'
}

function subjectLabel(item: SubjectOption) {
  return item.name
}
</script>

<template>
  <div v-loading="loading">
    <div class="stats">
      <div class="stat-card">
        <div class="stat-icon blue">🎬</div>
        <div><strong>{{ stats.courseCount }}</strong><p>课程总数</p></div>
      </div>
      <div class="stat-card">
        <div class="stat-icon green">⏱️</div>
        <div><strong>{{ hoursText(stats.totalHours) }}</strong><p>总时长（小时）</p></div>
      </div>
      <div class="stat-card">
        <div class="stat-icon orange">📚</div>
        <div><strong>{{ stats.subjectCount }}</strong><p>关联学科数</p></div>
      </div>
    </div>

    <section class="panel">
      <div class="toolbar">
        <div class="filters">
          <el-input v-model="query.keyword" clearable placeholder="搜索课程名称" style="width: 220px" @input="onKeywordInput" @clear="onKeywordInput" />
          <el-select v-model="query.subjectId" clearable placeholder="全部学科" style="width: 200px" @change="onFilter">
            <el-option label="其他学科" :value="0" />
            <el-option v-for="item in subjects" :key="item.id" :label="subjectLabel(item)" :value="item.id" />
          </el-select>
          <el-select v-model="query.source" clearable placeholder="全部来源" style="width: 140px" @change="onFilter">
            <el-option label="B站" :value="1" />
            <el-option label="其他" :value="2" />
          </el-select>
        </div>
        <div class="toolbar-actions">
          <el-button v-if="user.has('course:add')" :icon="Upload" @click="openImport">批量导入</el-button>
          <el-button v-if="user.has('course:add')" type="primary" :icon="Plus" @click="openCreate">新增课程</el-button>
        </div>
      </div>

      <el-table v-loading="tableLoading" :data="rows" row-key="id">
        <el-table-column label="序号" width="70" :index="rowIndex" type="index" />
        <el-table-column label="封面" width="132">
          <template #default="{ row }">
            <img v-if="row.coverUrl && !brokenCovers[row.id]" class="thumb" :src="row.coverUrl" alt="" referrerpolicy="no-referrer" @error="brokenCovers[row.id] = true" />
            <span v-else class="thumb placeholder">🎬</span>
          </template>
        </el-table-column>
        <el-table-column label="课程名称" min-width="240">
          <template #default="{ row }">
            <a v-if="row.link" class="course-name" :href="row.link" target="_blank" rel="noopener noreferrer">{{ row.name }}</a>
            <span v-else class="course-name">{{ row.name }}</span>
            <div v-if="row.bvid" class="bvid">{{ row.bvid }}</div>
          </template>
        </el-table-column>
        <el-table-column label="时长" width="100">
          <template #default="{ row }"><span class="duration">{{ row.duration }}</span></template>
        </el-table-column>
        <el-table-column label="学科" min-width="180">
          <template #default="{ row }">
            <template v-if="row.otherSubject"><span class="tag other">其他学科</span></template>
            <template v-else>
              <span v-for="item in row.subjects" :key="item.id" class="tag">{{ item.name }}</span>
            </template>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="90">
          <template #default="{ row }">
            <el-tag :type="row.source === 1 ? 'primary' : 'info'" effect="light">{{ row.source === 1 ? 'B站' : '其他' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-if="user.has('course:edit')" link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="user.has('course:delete')" link type="danger" :icon="Delete" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无匹配的课程，试试调整筛选条件" />
        </template>
      </el-table>
      <el-pagination
        v-if="total > 0"
        class="pager"
        background
        layout="total, sizes, prev, pager, next"
        :total="total"
        :page-sizes="[10, 20, 50]"
        :current-page="page"
        :page-size="pageSize"
        @current-change="(value: number) => { page = value; loadList() }"
        @size-change="onPageSize"
      />
    </section>

    <el-dialog v-model="editorVisible" :title="editing ? '编辑课程' : '新增课程'" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="课程名称" prop="name">
          <el-input v-model="form.name" maxlength="100" show-word-limit placeholder="请输入课程名称" />
        </el-form-item>
        <el-form-item label="封面图片 URL" prop="coverUrl">
          <el-input v-model="form.coverUrl" maxlength="500" placeholder="选填，http 或 https 地址" />
        </el-form-item>
        <el-form-item label="时长" prop="duration">
          <el-input v-model="form.duration" maxlength="8" placeholder="小时:分钟，例如 4:53" />
          <p class="hint">按 小时:分钟 填写，例如 293 分钟写成 4:53</p>
        </el-form-item>
        <el-form-item label="所属学科" prop="subjectId">
          <el-select v-model="form.subjectId" placeholder="请选择所属学科" style="width: 100%" @change="onPrimaryChange">
            <el-option label="其他学科" :value="0" />
            <el-option v-for="item in subjects" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
          <p class="hint">不属于已有学科时选择「其他学科」。关联学科数不统计这一项。</p>
        </el-form-item>
        <el-form-item v-if="form.subjectId" label="其他关联学科">
          <el-select v-model="form.extraIds" multiple collapse-tags collapse-tags-tooltip placeholder="可选，一门课可以属于多个学科" style="width: 100%">
            <el-option v-for="item in extraOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源" prop="source">
          <el-radio-group v-model="form.source">
            <el-radio :value="1">B站</el-radio>
            <el-radio :value="2">其他</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="BV号" prop="bvid">
          <el-input v-model="form.bvid" maxlength="12" placeholder="来源为 B站时必填，例如 BV1xx411c7mD" />
        </el-form-item>
        <el-form-item label="课程链接" prop="link">
          <el-input v-model="form.link" maxlength="500" placeholder="选填。B站课程留空时按 BV号自动生成" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :icon="Close" @click="editorVisible = false">取消</el-button>
        <el-button type="primary" :icon="Check" :loading="saving" @click="save">{{ editing ? '保存修改' : '确认新增' }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="importVisible" title="批量导入课程" width="920px" destroy-on-close>
      <div class="format">
        <strong>Excel 列顺序</strong>
        <p>课程名称、所属学科、来源、时长（分钟）、BV号、封面、播放链接。请使用第一行表头，从第二行填写数据，单次最多 500 条。</p>
        <p>所属学科填已有学科名称，多个用顿号或逗号分隔。不属于已有学科时填「其他学科」。来源只能填「B站」或「其他」。时长单位是分钟，可以带小数。</p>
        <el-button link type="primary" :icon="Download" @click="downloadTemplate">下载导入模板</el-button>
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
        <div class="upload-text">将 Excel 拖到这里，或点击选择文件</div>
        <div class="upload-hint">支持 .xlsx / .xls</div>
      </el-upload>

      <div v-loading="previewing">
        <div v-if="preview" class="summary">
          <span>共 {{ preview.total }} 条</span>
          <span class="ok">有效 {{ preview.validCount }}</span>
          <span class="warn">警告 {{ preview.warningCount }}</span>
          <span class="bad">无效 {{ preview.invalidCount }}</span>
          <em v-if="importable > 0">确认后写入有效行和警告行，无效行会跳过</em>
          <em v-else>没有可导入的数据</em>
        </div>
        <el-table v-if="preview && preview.rows.length > 0" :data="preview.rows" max-height="360" size="small">
          <el-table-column prop="row" label="行号" width="70" />
          <el-table-column prop="name" label="课程名称" min-width="180" show-overflow-tooltip />
          <el-table-column prop="duration" label="时长" width="80" />
          <el-table-column prop="subjects" label="学科" min-width="140" show-overflow-tooltip />
          <el-table-column prop="source" label="来源" width="70" />
          <el-table-column prop="bvid" label="BV号" width="130" />
          <el-table-column label="状态" min-width="180">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ok' ? 'success' : row.status === 'warning' ? 'warning' : 'danger'" effect="light">{{ statusText(row.status) }}</el-tag>
              <span v-if="row.messages.length" class="reason">{{ row.messages.join('；') }}</span>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else-if="preview" description="未解析到课程数据，请从第 2 行开始填写" />
      </div>
      <template #footer>
        <el-button :icon="Close" @click="importVisible = false">取消</el-button>
        <el-button type="primary" :icon="Check" :disabled="!preview?.token" :loading="importing" @click="confirmImport">确认导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.stats { display: grid; grid-template-columns: repeat(3, 1fr); gap: 20px; margin-bottom: 24px; }
.stat-card { display: flex; align-items: center; gap: 16px; padding: 24px; background: #fff; border-radius: 12px; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06); }
.stat-icon { width: 56px; height: 56px; border-radius: 14px; display: grid; place-items: center; font-size: 28px; }
.stat-icon.blue { background: rgba(64, 158, 255, 0.1); }
.stat-icon.green { background: rgba(103, 194, 58, 0.1); }
.stat-icon.orange { background: rgba(230, 162, 60, 0.1); }
.stat-card strong { display: block; font-size: 28px; line-height: 1.2; }
.stat-card p { margin: 4px 0 0; font-size: 13px; color: #909399; }
.panel { background: #fff; border-radius: 12px; padding: 20px; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06); }
.toolbar { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 16px; flex-wrap: wrap; }
.filters, .toolbar-actions { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
.thumb { width: 108px; height: 60px; border-radius: 6px; object-fit: cover; background: #f5f7fa; display: inline-flex; }
.thumb.placeholder { align-items: center; justify-content: center; color: #c0c4cc; font-size: 20px; }
.course-name { display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; color: #303133; font-weight: 500; line-height: 1.5; text-decoration: none; }
a.course-name { color: #409eff; }
a.course-name:hover { text-decoration: underline; }
.bvid { margin-top: 4px; color: #409eff; font-family: ui-monospace, monospace; font-size: 12px; }
.duration { font-family: ui-monospace, monospace; color: #606266; }
.tag { display: inline-block; margin: 2px 4px 2px 0; padding: 2px 8px; border-radius: 4px; background: rgba(103, 194, 58, 0.12); color: #67c23a; font-size: 12px; }
.tag.other { background: rgba(144, 147, 153, 0.12); color: #909399; }
.pager { margin-top: 16px; justify-content: flex-end; }
.hint { margin: 4px 0 0; color: #909399; font-size: 12px; }
.format { margin-bottom: 16px; padding: 12px 14px; border-radius: 8px; background: #f5f7fa; color: #606266; font-size: 13px; line-height: 1.6; }
.format strong { color: #303133; }
.format p { margin: 6px 0; }
.upload-text { color: #303133; font-size: 14px; }
.upload-hint { margin-top: 4px; color: #909399; font-size: 12px; }
.summary { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; margin: 16px 0 12px; padding: 10px 14px; border-radius: 8px; background: #f0f9eb; color: #303133; font-size: 14px; }
.summary .ok { color: #67c23a; font-weight: 600; }
.summary .warn { color: #e6a23c; font-weight: 600; }
.summary .bad { color: #f56c6c; font-weight: 600; }
.summary em { color: #909399; font-style: normal; }
.reason { margin-left: 8px; color: #909399; font-size: 12px; }
@media (max-width: 900px) { .stats { grid-template-columns: 1fr; } }
</style>
