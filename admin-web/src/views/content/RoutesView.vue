<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, ArrowUp, Back, Check, Close, Delete, Edit, Plus, View } from '@element-plus/icons-vue'
import type { CheckboxValueType, FormInstance, FormRules } from 'element-plus'
import type { ApiResponse } from '@/types/api'
import { useUserStore } from '@/store/user'
import {
  addRouteCourses,
  createRoute,
  deleteRoute,
  fetchCourseOptions,
  fetchRouteBoard,
  fetchRouteDetail,
  removeRouteCourse,
  sortRouteCourses,
  updateRoute,
  updateRouteStatus,
  type CourseOption,
  type RouteCard,
  type RouteDetail,
  type SubjectOption,
} from '@/api/route'

interface PickedCourse {
  id: number
  name: string
  bvid: string | null
  duration: string
}

const user = useUserStore()
const route = useRoute()
const router = useRouter()

const loading = ref(false)
const detailLoading = ref(false)
const saving = ref(false)
const sorting = ref(false)
const subjects = ref<SubjectOption[]>([])
const routes = ref<RouteCard[]>([])
const detail = ref<RouteDetail | null>(null)

const editorVisible = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const form = reactive({
  name: '',
  subjectId: undefined as number | undefined,
  status: 1,
  icon: '',
  targetEducation: '',
  description: '',
})
const options = ref<CourseOption[]>([])
const optionsLoading = ref(false)
const checklistKeyword = ref('')
const picked = ref<PickedCourse[]>([])

const previewVisible = ref(false)
const preview = ref<RouteDetail | null>(null)

const addVisible = ref(false)
const addLoading = ref(false)
const addSaving = ref(false)
const addKeyword = ref('')
const addOptions = ref<CourseOption[]>([])
const addSelected = ref<number[]>([])

const rules: FormRules = {
  name: [
    { required: true, message: '请填写路线名称', trigger: 'blur' },
    { max: 100, message: '路线名称不能超过 100 字', trigger: 'blur' },
  ],
  subjectId: [{ required: true, message: '请选择关联学科', trigger: 'change' }],
  targetEducation: [{ max: 100, message: '适合学历不能超过 100 字', trigger: 'blur' }],
  icon: [{ max: 50, message: '路线图标不能超过 50 个字符', trigger: 'blur' }],
  description: [{ max: 5000, message: '路线描述不能超过 5000 字', trigger: 'blur' }],
}

const detailId = computed(() => {
  const raw = route.query.detail
  const value = Array.isArray(raw) ? raw[0] : raw
  const id = Number(value)
  return Number.isInteger(id) && id > 0 ? id : null
})

const enabledCount = computed(() => routes.value.filter((item) => item.status === 1).length)
const arrangedCount = computed(() => routes.value.reduce((sum, item) => sum + item.courseCount, 0))
const pickedIds = computed(() => new Set(picked.value.map((item) => item.id)))
const checklist = computed(() => options.value.filter((item) => item.own && matches(item, checklistKeyword.value)))
const addedIds = computed(() => new Set(detail.value?.courses.map((item) => item.courseId) ?? []))
const addOwn = computed(() => addOptions.value.filter((item) => item.own && matches(item, addKeyword.value)))
const addOther = computed(() => addOptions.value.filter((item) => !item.own && matches(item, addKeyword.value)))

watch(detailId, () => {
  if (detailId.value) {
    loadDetail()
  } else {
    detail.value = null
  }
}, { immediate: true })

onMounted(async () => {
  await load()
  const raw = route.query.create
  const flag = Array.isArray(raw) ? raw[0] : raw
  if (flag === '1' && user.has('route:add') && !detailId.value) {
    openCreate()
    const next = { ...route.query }
    delete next.create
    router.replace({ query: next })
  }
})

function messageOf(error: unknown, fallback: string) {
  const body = error as ApiResponse<unknown>
  return body?.message || fallback
}

function matches(item: CourseOption, keyword: string) {
  const text = keyword.trim().toLowerCase()
  if (!text) return true
  return item.name.toLowerCase().includes(text) || (item.bvid || '').toLowerCase().includes(text)
}

function iconOf(item: { icon: string | null, subjectIcon?: string | null }) {
  return item.icon || item.subjectIcon || '🗺️'
}

function headerBackground(subjectId: number) {
  const color = subjects.value.find((subject) => subject.id === subjectId)?.themeColor || '#4F46E5'
  return `linear-gradient(135deg, ${color} 0%, ${darken(color)} 100%)`
}

function darken(hex: string) {
  const raw = hex.replace('#', '')
  const full = raw.length === 3 ? raw.split('').map((char) => char + char).join('') : raw
  const num = Number.parseInt(full, 16)
  if (Number.isNaN(num)) return '#4338CA'
  const channel = (value: number) => Math.max(0, Math.round(value * 0.72)).toString(16).padStart(2, '0')
  return `#${channel((num >> 16) & 255)}${channel((num >> 8) & 255)}${channel(num & 255)}`
}

async function load(quiet = false) {
  if (!quiet) loading.value = true
  try {
    const response = await fetchRouteBoard()
    subjects.value = response.data.data.subjects
    routes.value = response.data.data.routes
  } catch (error) {
    ElMessage.error(messageOf(error, '加载学习路线失败'))
  } finally {
    loading.value = false
  }
}

async function loadDetail(quiet = false) {
  if (!detailId.value) return
  if (!quiet) detailLoading.value = true
  try {
    const response = await fetchRouteDetail(detailId.value)
    detail.value = response.data.data
  } catch (error) {
    ElMessage.error(messageOf(error, '加载路线失败'))
    detail.value = null
    router.replace({ query: {} })
  } finally {
    detailLoading.value = false
  }
}

async function loadOptions(subjectId: number) {
  optionsLoading.value = true
  try {
    const response = await fetchCourseOptions(subjectId)
    options.value = response.data.data
  } catch (error) {
    options.value = []
    ElMessage.error(messageOf(error, '加载课程失败'))
  } finally {
    optionsLoading.value = false
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, { name: '', subjectId: undefined, status: 1, icon: '', targetEducation: '', description: '' })
  picked.value = []
  options.value = []
  checklistKeyword.value = ''
  editorVisible.value = true
}

async function openEdit(id: number) {
  try {
    const response = await fetchRouteDetail(id)
    const item = response.data.data
    editingId.value = item.id
    Object.assign(form, {
      name: item.name,
      subjectId: item.subjectId,
      status: item.status,
      icon: item.icon || '',
      targetEducation: item.targetEducation || '',
      description: item.description || '',
    })
    picked.value = item.courses.map((course) => ({
      id: course.courseId,
      name: course.name,
      bvid: course.bvid,
      duration: course.duration,
    }))
    checklistKeyword.value = ''
    editorVisible.value = true
    await loadOptions(item.subjectId)
  } catch (error) {
    ElMessage.error(messageOf(error, '加载路线失败'))
  }
}

async function onSubjectChange(subjectId: number) {
  checklistKeyword.value = ''
  await loadOptions(subjectId)
}

function togglePick(item: CourseOption, checked: CheckboxValueType) {
  if (checked === true) {
    if (!pickedIds.value.has(item.id)) {
      picked.value.push({ id: item.id, name: item.name, bvid: item.bvid, duration: item.duration })
    }
    return
  }
  picked.value = picked.value.filter((course) => course.id !== item.id)
}

function movePicked(index: number, delta: number) {
  const target = index + delta
  if (target < 0 || target >= picked.value.length) return
  const next = picked.value.slice()
  const [item] = next.splice(index, 1)
  next.splice(target, 0, item)
  picked.value = next
}

async function save() {
  const formEl = formRef.value
  if (!formEl) return
  const valid = await formEl.validate().catch(() => false)
  if (!valid || form.subjectId == null) return
  saving.value = true
  const payload = {
    name: form.name.trim(),
    subjectId: form.subjectId,
    status: form.status,
    icon: form.icon.trim() || null,
    targetEducation: form.targetEducation.trim() || null,
    description: form.description.trim() || null,
    courseIds: picked.value.map((item) => item.id),
  }
  try {
    if (editingId.value) {
      await updateRoute(editingId.value, payload)
      ElMessage.success('已保存')
    } else {
      await createRoute(payload)
      ElMessage.success('已创建')
    }
    editorVisible.value = false
    await load(true)
    if (detailId.value) await loadDetail(true)
  } catch (error) {
    ElMessage.error(messageOf(error, editingId.value ? '保存失败' : '创建失败'))
  } finally {
    saving.value = false
  }
}

async function toggleStatus(item: RouteCard | RouteDetail) {
  const next = item.status === 1 ? 0 : 1
  try {
    await updateRouteStatus(item.id, next)
    ElMessage.success(next === 1 ? '已启用' : '已禁用')
    await load(true)
    if (detailId.value === item.id) await loadDetail(true)
  } catch (error) {
    ElMessage.error(messageOf(error, '状态更新失败'))
  }
}

async function openPreview(id: number) {
  try {
    const response = await fetchRouteDetail(id)
    preview.value = response.data.data
    previewVisible.value = true
  } catch (error) {
    ElMessage.error(messageOf(error, '加载预览失败'))
  }
}

async function remove(item: RouteCard) {
  try {
    await ElMessageBox.confirm(`确定删除学习路线「${item.name}」？路线中的课程安排会一并删除，课程库里的课程不会删除。`, '删除路线', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteRoute(item.id)
    ElMessage.success('已删除')
    await load()
  } catch (error) {
    ElMessage.error(messageOf(error, '删除失败'))
  }
}

function openDetail(id: number) {
  router.push({ query: { detail: String(id) } })
}

function backToList() {
  router.push({ query: {} })
  load(true)
}

async function moveCourse(index: number, delta: number) {
  if (!detail.value || sorting.value) return
  const next = detail.value.courses.slice()
  const target = index + delta
  if (target < 0 || target >= next.length) return
  const [item] = next.splice(index, 1)
  next.splice(target, 0, item)
  sorting.value = true
  try {
    await sortRouteCourses(detail.value.id, next.map((course) => course.courseId))
    await loadDetail(true)
    await load(true)
  } catch (error) {
    ElMessage.error(messageOf(error, '排序失败'))
    await loadDetail(true)
  } finally {
    sorting.value = false
  }
}

async function removeCourse(courseId: number, name: string) {
  if (!detail.value) return
  try {
    await ElMessageBox.confirm(`确定从路线中移除「${name}」？`, '移除课程', { type: 'warning' })
  } catch {
    return
  }
  try {
    await removeRouteCourse(detail.value.id, courseId)
    ElMessage.success('已移除')
    await loadDetail(true)
    await load(true)
  } catch (error) {
    ElMessage.error(messageOf(error, '移除失败'))
  }
}

async function openAdd() {
  if (!detail.value) return
  addKeyword.value = ''
  addSelected.value = []
  addVisible.value = true
  addLoading.value = true
  try {
    const response = await fetchCourseOptions(detail.value.subjectId)
    addOptions.value = response.data.data
  } catch (error) {
    addOptions.value = []
    ElMessage.error(messageOf(error, '加载课程库失败'))
  } finally {
    addLoading.value = false
  }
}

function toggleAdd(item: CourseOption, checked: CheckboxValueType) {
  if (addedIds.value.has(item.id)) return
  if (checked === true) {
    if (!addSelected.value.includes(item.id)) addSelected.value.push(item.id)
    return
  }
  addSelected.value = addSelected.value.filter((id) => id !== item.id)
}

async function confirmAdd() {
  if (!detail.value || addSelected.value.length === 0) {
    ElMessage.warning('请选择要添加的课程')
    return
  }
  addSaving.value = true
  try {
    const response = await addRouteCourses(detail.value.id, addSelected.value)
    const added = response.data.data.added
    ElMessage.success(added > 0 ? `已添加 ${added} 门课程` : '所选课程已在路线中')
    addVisible.value = false
    await loadDetail(true)
    await load(true)
  } catch (error) {
    ElMessage.error(messageOf(error, '添加失败'))
  } finally {
    addSaving.value = false
  }
}
</script>

<template>
  <div v-if="detailId" v-loading="detailLoading" class="page">
    <div class="toolbar">
      <div class="title-wrap">
        <el-button :icon="Back" @click="backToList">返回列表</el-button>
        <h2>{{ detail ? `${detail.name} - 课程管理` : '课程管理' }}</h2>
      </div>
      <div class="toolbar-actions">
        <el-button v-if="detail && user.has('route:edit')" :icon="Edit" @click="openEdit(detail.id)">编辑</el-button>
        <el-button v-if="detail && user.has('route:edit')" type="primary" :icon="Plus" @click="openAdd">从课程库添加</el-button>
      </div>
    </div>

    <div v-if="detail" class="info">
      <div><span>关联学科</span><strong>{{ detail.subjectIcon }} {{ detail.subjectName }}</strong></div>
      <div><span>状态</span><strong>{{ detail.status === 1 ? '启用' : '禁用' }}</strong></div>
      <div><span>课程 / 总时长</span><strong>{{ detail.courseCount }} 门 · {{ detail.totalDuration }}</strong></div>
      <div><span>适合学历</span><strong>{{ detail.targetEducation || '未填写' }}</strong></div>
      <p>{{ detail.description || '暂无描述' }}</p>
    </div>

    <div class="panel">
      <el-table :data="detail?.courses ?? []">
        <el-table-column type="index" label="序号" width="70" />
        <el-table-column label="BV号" width="150">
          <template #default="{ row }">{{ row.bvid || '—' }}</template>
        </el-table-column>
        <el-table-column label="课程名称" min-width="240">
          <template #default="{ row }">
            <div class="course-title">{{ row.name }}</div>
            <el-tag v-if="row.crossSubject" size="small" effect="light" type="info">跨学科补充</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="duration" label="时长" width="120" />
        <el-table-column v-if="user.has('route:edit')" label="排序" width="120">
          <template #default="{ $index }">
            <el-button link :icon="ArrowUp" :disabled="sorting || $index === 0" @click="moveCourse($index, -1)" />
            <el-button link :icon="ArrowDown" :disabled="sorting || !detail || $index === detail.courses.length - 1" @click="moveCourse($index, 1)" />
          </template>
        </el-table-column>
        <el-table-column v-if="user.has('route:edit')" label="操作" width="90">
          <template #default="{ row }">
            <el-button link type="danger" @click="removeCourse(row.courseId, row.name)">移除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无课程，点击右上角从课程库添加" />
        </template>
      </el-table>
    </div>
  </div>

  <div v-else v-loading="loading" class="page">
    <div class="stats">
      <div class="stat-card"><div class="stat-icon blue">🗺️</div><div><strong>{{ routes.length }}</strong><p>学习路线</p></div></div>
      <div class="stat-card"><div class="stat-icon green">✅</div><div><strong>{{ enabledCount }}</strong><p>启用中</p></div></div>
      <div class="stat-card"><div class="stat-icon orange">🎬</div><div><strong>{{ arrangedCount }}</strong><p>已编排课程</p></div></div>
    </div>
    <div class="toolbar">
      <h2>学习路线</h2>
      <el-button v-if="user.has('route:add')" type="primary" :icon="Plus" @click="openCreate">新建路线</el-button>
    </div>
    <el-empty v-if="!loading && routes.length === 0" description="暂无学习路线，点击上方新建路线创建一条" />
    <div v-else class="grid">
      <article v-for="item in routes" :key="item.id" class="card">
        <header class="head" :style="{ background: headerBackground(item.subjectId) }">
          <span class="icon">{{ iconOf(item) }}</span>
          <h3>{{ item.name }}</h3>
          <el-tag :type="item.status === 1 ? 'success' : 'info'" effect="light">{{ item.status === 1 ? '启用' : '禁用' }}</el-tag>
        </header>
        <p class="meta">{{ item.subjectIcon }} {{ item.subjectName || '未关联学科' }} · {{ item.targetEducation || '适合学历未填写' }}</p>
        <p class="meta">共 {{ item.courseCount }} 门 B站课程 · 总计 {{ item.totalDuration }}</p>
        <p class="desc">{{ item.description || '暂无描述' }}</p>
        <div class="previews">
          <div v-for="course in item.previews" :key="course.id" class="preview">
            <img v-if="course.coverUrl" :src="course.coverUrl" alt="" referrerpolicy="no-referrer" />
            <span v-else class="cover-fallback">课</span>
            <span>{{ course.name }}</span>
          </div>
          <p v-if="item.previews.length === 0" class="meta">还没有课程</p>
        </div>
        <div class="actions">
          <el-button v-if="user.has('route:edit')" size="small" @click="toggleStatus(item)">{{ item.status === 1 ? '禁用' : '启用' }}</el-button>
          <el-button size="small" :icon="View" @click="openPreview(item.id)">预览</el-button>
          <el-button v-if="user.has('route:edit')" size="small" :icon="Edit" @click="openEdit(item.id)">编辑</el-button>
          <el-button size="small" type="primary" link @click="openDetail(item.id)">课程管理</el-button>
          <el-button v-if="user.has('route:delete')" size="small" type="danger" link :icon="Delete" @click="remove(item)">删除</el-button>
        </div>
      </article>
    </div>
  </div>

  <el-dialog v-model="editorVisible" :title="editingId ? '编辑路线' : '新建路线'" width="780px" destroy-on-close>
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <p class="section">基本信息</p>
      <el-form-item label="路线名称" prop="name">
        <el-input v-model="form.name" maxlength="100" show-word-limit placeholder="例如：Java全栈开发工程师" />
      </el-form-item>
      <div class="form-row">
        <el-form-item label="关联学科" prop="subjectId">
          <el-select v-model="form.subjectId" placeholder="请选择关联学科" style="width: 100%" @change="onSubjectChange">
            <el-option v-for="item in subjects" :key="item.id" :label="`${item.icon || ''} ${item.name}`" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
      </div>
      <el-form-item label="路线图标" prop="icon">
        <el-input v-model="form.icon" maxlength="50" placeholder="留空自动使用学科图标" />
      </el-form-item>
      <el-form-item label="适合学历" prop="targetEducation">
        <el-input v-model="form.targetEducation" type="textarea" :rows="2" maxlength="100" show-word-limit placeholder="例如：大专及以上学历，具备编程基础更佳" />
      </el-form-item>
      <el-form-item label="路线描述" prop="description">
        <el-input v-model="form.description" type="textarea" :rows="2" maxlength="5000" show-word-limit placeholder="请输入路线描述" />
      </el-form-item>

      <p class="section">路线课程 <em>从学科课程中勾选本路线要学习的课程</em></p>
      <el-input v-model="checklistKeyword" clearable placeholder="搜索本学科课程名称或 BV号" :disabled="!form.subjectId" />
      <div v-loading="optionsLoading" class="checklist">
        <p v-if="!form.subjectId" class="hint">请先选择关联学科，加载该学科下的 B站课程</p>
        <p v-else-if="checklist.length === 0" class="hint">该学科下暂无课程</p>
        <el-checkbox
          v-for="item in checklist"
          :key="item.id"
          :model-value="pickedIds.has(item.id)"
          @change="togglePick(item, $event)"
        >
          <span class="check-line"><em>{{ item.bvid || '—' }}</em>{{ item.name }}<i>{{ item.duration }}</i></span>
        </el-checkbox>
      </div>
      <div class="picked">
        <div class="picked-head">
          <span>已选课程 {{ picked.length }} 门</span>
          <em>↑↓ 调整学习顺序</em>
        </div>
        <p v-if="picked.length === 0" class="hint">还未选择课程，请在上方勾选</p>
        <div v-for="(item, index) in picked" :key="item.id" class="picked-item">
          <span class="order">{{ index + 1 }}</span>
          <em>{{ item.bvid || '—' }}</em>
          <span class="picked-name">{{ item.name }}</span>
          <i>{{ item.duration }}</i>
          <el-button link :icon="ArrowUp" :disabled="index === 0" @click="movePicked(index, -1)" />
          <el-button link :icon="ArrowDown" :disabled="index === picked.length - 1" @click="movePicked(index, 1)" />
          <el-button link type="danger" :icon="Close" @click="picked.splice(index, 1)" />
        </div>
      </div>
    </el-form>
    <template #footer>
      <el-button :icon="Close" @click="editorVisible = false">取消</el-button>
      <el-button type="primary" :icon="Check" :loading="saving" @click="save">{{ editingId ? '保存修改' : '创建路线' }}</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="previewVisible" :title="preview ? `${preview.name} · 学员预览` : '预览'" width="720px">
    <template v-if="preview">
      <p class="preview-desc">{{ preview.description || '暂无描述' }}</p>
      <p class="meta">
        {{ preview.subjectIcon }} {{ preview.subjectName }} · {{ preview.status === 1 ? '启用' : '禁用' }} ·
        共 {{ preview.courseCount }} 门课程 · 总计 {{ preview.totalDuration }} · {{ preview.targetEducation || '适合学历未填写' }} · 按列表顺序学习
      </p>
      <el-empty v-if="preview.courses.length === 0" description="课程筹备中，敬请期待" />
      <ol v-else class="preview-list">
        <li v-for="(course, index) in preview.courses" :key="course.courseId">
          <img v-if="course.coverUrl" :src="course.coverUrl" alt="" referrerpolicy="no-referrer" />
          <div>
            <strong>{{ index + 1 }}. {{ course.name }}</strong>
            <p>{{ course.bvid || '—' }} · {{ course.duration }}<template v-if="course.crossSubject"> · 跨学科补充</template></p>
          </div>
          <a v-if="course.link" :href="course.link" target="_blank" rel="noopener noreferrer">去B站学习</a>
        </li>
      </ol>
    </template>
  </el-dialog>

  <el-dialog v-model="addVisible" title="从课程库添加" width="760px">
    <el-input v-model="addKeyword" clearable placeholder="搜索课程名称或 BV号" />
    <p class="meta add-count">已选择 {{ addSelected.length }} 门，已在路线中的课程不可重复添加</p>
    <div v-loading="addLoading" class="add-groups">
      <section>
        <h4>本学科课程</h4>
        <p v-if="!addLoading && addOwn.length === 0" class="hint">没有匹配的课程</p>
        <el-checkbox
          v-for="item in addOwn"
          :key="item.id"
          :model-value="addedIds.has(item.id) || addSelected.includes(item.id)"
          :disabled="addedIds.has(item.id)"
          @change="toggleAdd(item, $event)"
        >
          <span class="check-line">
            <em>{{ item.bvid || '—' }}</em>{{ item.name }}<i>{{ item.duration }}</i>
            <el-tag v-if="addedIds.has(item.id)" size="small" effect="light">已添加</el-tag>
          </span>
        </el-checkbox>
      </section>
      <section>
        <h4>其他学科课程（跨学科补充）</h4>
        <p v-if="!addLoading && addOther.length === 0" class="hint">没有匹配的课程</p>
        <el-checkbox
          v-for="item in addOther"
          :key="item.id"
          :model-value="addedIds.has(item.id) || addSelected.includes(item.id)"
          :disabled="addedIds.has(item.id)"
          @change="toggleAdd(item, $event)"
        >
          <span class="check-line">
            <em>{{ item.bvid || '—' }}</em>{{ item.name }}<i>{{ item.subjectName || '其他学科' }} · {{ item.duration }}</i>
            <el-tag v-if="addedIds.has(item.id)" size="small" effect="light">已添加</el-tag>
          </span>
        </el-checkbox>
      </section>
    </div>
    <template #footer>
      <el-button :icon="Close" @click="addVisible = false">取消</el-button>
      <el-button type="primary" :icon="Check" :loading="addSaving" @click="confirmAdd">完成添加</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.page { min-height: 240px; }
.stats { display: grid; grid-template-columns: repeat(3, 1fr); gap: 20px; margin-bottom: 24px; }
.stat-card { display: flex; align-items: center; gap: 16px; padding: 24px; background: #fff; border-radius: 12px; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06); }
.stat-icon { width: 56px; height: 56px; border-radius: 14px; display: grid; place-items: center; font-size: 28px; }
.stat-icon.blue { background: rgba(64, 158, 255, 0.1); }
.stat-icon.green { background: rgba(103, 194, 58, 0.1); }
.stat-icon.orange { background: rgba(230, 162, 60, 0.1); }
.stat-card strong { display: block; font-size: 28px; line-height: 1.2; }
.stat-card p { margin: 4px 0 0; font-size: 13px; color: #909399; }
.toolbar { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 16px; }
.toolbar h2, .title-wrap h2 { margin: 0; font-size: 18px; }
.title-wrap, .toolbar-actions { display: flex; align-items: center; gap: 12px; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(340px, 1fr)); gap: 16px; }
.card { background: #fff; border-radius: 12px; padding: 18px; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06); display: flex; flex-direction: column; gap: 8px; overflow: hidden; }
.head { display: flex; align-items: center; gap: 8px; margin: -18px -18px 0; padding: 16px 18px; color: #fff; }
.icon { font-size: 26px; line-height: 1.2; }
.head h3 { flex: 1; margin: 0; font-size: 16px; line-height: 1.4; color: #fff; }
.head :deep(.el-tag) { background: rgba(255, 255, 255, 0.22); border-color: transparent; color: #fff; }
.meta { margin: 0; color: #606266; font-size: 13px; }
.desc { margin: 0; color: #606266; font-size: 13px; line-height: 1.6; min-height: 42px; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.previews { display: flex; flex-direction: column; gap: 6px; min-height: 32px; }
.preview { display: flex; align-items: center; gap: 8px; color: #303133; font-size: 13px; }
.preview span:last-child { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.preview img, .cover-fallback { width: 56px; height: 32px; border-radius: 4px; object-fit: cover; background: #f5f7fa; flex: none; }
.cover-fallback { display: grid; place-items: center; color: #c0c4cc; font-size: 12px; }
.actions { display: flex; flex-wrap: nowrap; align-items: center; gap: 2px; }
.actions :deep(.el-button) { margin-left: 0; padding-left: 8px; padding-right: 8px; }
.info { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px 16px; background: #fff; border-radius: 12px; padding: 16px 20px; margin-bottom: 16px; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06); }
.info span { display: block; color: #909399; font-size: 12px; margin-bottom: 4px; }
.info strong { font-size: 14px; font-weight: 600; }
.info p { grid-column: 1 / -1; margin: 0; color: #606266; line-height: 1.6; }
.panel { background: #fff; border-radius: 12px; padding: 12px 16px 16px; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06); }
.course-title { margin-bottom: 4px; }
.section { margin: 8px 0 12px; padding: 8px 14px; border-radius: 8px; background: #f5f7fa; font-weight: 600; }
.section em, .picked-head em, .check-line i { font-style: normal; font-weight: 400; color: #909399; font-size: 12px; }
.form-row { display: grid; grid-template-columns: 1.4fr 0.8fr; gap: 12px; }
.checklist, .add-groups { max-height: 220px; overflow: auto; margin-top: 10px; padding: 8px 12px; border: 1px solid #e4e7ed; border-radius: 8px; }
.add-groups { max-height: 420px; }
.checklist :deep(.el-checkbox), .add-groups :deep(.el-checkbox) { display: flex; margin-right: 0; height: auto; padding: 4px 0; }
.check-line { display: inline-flex; gap: 8px; align-items: center; white-space: normal; }
.check-line em { color: #409eff; font-style: normal; font-family: ui-monospace, monospace; }
.picked { margin-top: 12px; padding: 12px; border-radius: 8px; background: #f0f9eb; }
.picked-head { display: flex; justify-content: space-between; margin-bottom: 8px; font-size: 13px; font-weight: 500; }
.picked-item { display: flex; align-items: center; gap: 8px; padding: 4px 0; }
.order { width: 22px; color: #67c23a; font-weight: 600; }
.picked-item em { color: #409eff; font-style: normal; font-family: ui-monospace, monospace; font-size: 12px; }
.picked-name { flex: 1; }
.hint { margin: 8px 0; color: #909399; text-align: center; font-size: 13px; }
.preview-desc { margin: 0 0 8px; color: #303133; line-height: 1.6; }
.preview-list { list-style: none; margin: 12px 0 0; padding: 0; }
.preview-list li { display: flex; align-items: center; gap: 12px; padding: 10px 0; border-top: 1px solid #ebeef5; }
.preview-list img { width: 72px; height: 40px; object-fit: cover; border-radius: 4px; }
.preview-list strong { display: block; }
.preview-list p { margin: 4px 0 0; color: #909399; font-size: 12px; }
.preview-list a { margin-left: auto; color: #409eff; white-space: nowrap; }
.add-groups h4 { margin: 8px 0; font-size: 13px; }
.add-count { margin: 10px 0 0; }
@media (max-width: 900px) {
  .stats, .info, .form-row { grid-template-columns: 1fr; }
}
</style>
