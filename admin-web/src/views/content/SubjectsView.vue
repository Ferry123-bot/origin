<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, ArrowUp, Check, Close, Delete, Edit, Plus } from '@element-plus/icons-vue'
import type { ApiResponse } from '@/types/api'
import { useUserStore } from '@/store/user'
import {
  createStage,
  createSubject,
  deleteStage,
  deleteSubject,
  fetchSubjectStats,
  fetchSubjects,
  sortStage,
  sortSubject,
  updateStage,
  updateStageStatus,
  updateSubject,
  type AdminStage,
  type AdminSubject,
  type SubjectStats,
} from '@/api/subject'

const colors = ['#4F46E5', '#7C3AED', '#0891B2', '#0F766E', '#D97706', '#DC2626', '#DB2777', '#EA580C']

function hexToRgba(hex: string, alpha: number) {
  const raw = hex.replace('#', '')
  const full = raw.length === 3 ? raw.split('').map((char) => char + char).join('') : raw
  const num = Number.parseInt(full, 16)
  const red = (num >> 16) & 255
  const green = (num >> 8) & 255
  const blue = num & 255
  return `rgba(${red}, ${green}, ${blue}, ${alpha})`
}

function darken(hex: string) {
  const raw = hex.replace('#', '')
  const full = raw.length === 3 ? raw.split('').map((char) => char + char).join('') : raw
  const num = Number.parseInt(full, 16)
  const channel = (value: number) => Math.max(0, value - 32).toString(16).padStart(2, '0')
  return `#${channel((num >> 16) & 255)}${channel((num >> 8) & 255)}${channel(num & 255)}`
}
const user = useUserStore()
const loading = ref(false)
const stats = ref<SubjectStats>({ subjectCount: 0, subjectsWithStages: 0, stageCount: 0, questionCount: 0 })
const subjects = ref<AdminSubject[]>([])

const subjectVisible = ref(false)
const subjectEditing = ref<AdminSubject | null>(null)
const subjectForm = reactive({ name: '', icon: '', description: '', sortOrder: 1, themeColor: colors[0] })

const stageVisible = ref(false)
const stageSubjectId = ref(0)
const stageEditing = ref<AdminStage | null>(null)
const stageForm = reactive({ name: '', icon: '', sortOrder: 1 })

onMounted(load)

async function load() {
  loading.value = true
  try {
    const [statsRes, listRes] = await Promise.all([fetchSubjectStats(), fetchSubjects()])
    stats.value = statsRes.data.data
    subjects.value = listRes.data.data
  } catch (error) {
    ElMessage.error((error as ApiResponse<unknown>).message || '加载失败')
  } finally {
    loading.value = false
  }
}

function openCreateSubject() {
  subjectEditing.value = null
  Object.assign(subjectForm, { name: '', icon: '', description: '', sortOrder: subjects.value.length + 1, themeColor: colors[0] })
  subjectVisible.value = true
}

function openEditSubject(item: AdminSubject) {
  subjectEditing.value = item
  Object.assign(subjectForm, {
    name: item.name,
    icon: item.icon,
    description: item.description,
    sortOrder: item.sortOrder,
    themeColor: item.themeColor,
  })
  subjectVisible.value = true
}

async function saveSubject() {
  if (!subjectForm.name.trim()) {
    ElMessage.warning('请输入学科名称')
    return
  }
  if (!subjectForm.icon.trim()) {
    ElMessage.warning('请输入学科图标')
    return
  }
  if (!subjectForm.description.trim()) {
    ElMessage.warning('请输入学科描述')
    return
  }
  try {
    if (subjectEditing.value) {
      await updateSubject(subjectEditing.value.id, { ...subjectForm })
    } else {
      await createSubject({ ...subjectForm })
    }
    subjectVisible.value = false
    ElMessage.success('已保存')
    await load()
  } catch (error) {
    ElMessage.error((error as ApiResponse<unknown>).message || '保存失败')
  }
}

async function moveSubject(item: AdminSubject, move: 'up' | 'down') {
  try {
    await sortSubject(item.id, move)
    await load()
  } catch (error) {
    ElMessage.error((error as ApiResponse<unknown>).message || '排序失败')
  }
}

async function removeSubject(item: AdminSubject) {
  try {
    await ElMessageBox.confirm(`确定删除学科「${item.name}」？`, '删除学科')
  } catch {
    return
  }
  try {
    await deleteSubject(item.id)
    ElMessage.success('已删除')
    await load()
  } catch (error) {
    ElMessage.error((error as ApiResponse<unknown>).message || '删除失败')
  }
}

function openCreateStage(item: AdminSubject) {
  stageSubjectId.value = item.id
  stageEditing.value = null
  Object.assign(stageForm, { name: '', icon: '', sortOrder: item.stages.length + 1 })
  stageVisible.value = true
}

function openEditStage(subjectId: number, stage: AdminStage) {
  stageSubjectId.value = subjectId
  stageEditing.value = stage
  Object.assign(stageForm, { name: stage.name, icon: stage.icon || '', sortOrder: stage.sortOrder })
  stageVisible.value = true
}

async function saveStage() {
  if (!stageForm.name.trim()) {
    ElMessage.warning('请输入阶段名称')
    return
  }
  const payload = { name: stageForm.name.trim(), icon: stageForm.icon.trim(), sortOrder: stageForm.sortOrder }
  try {
    if (stageEditing.value) {
      await updateStage(stageSubjectId.value, stageEditing.value.id, payload)
    } else {
      await createStage(stageSubjectId.value, payload)
    }
    stageVisible.value = false
    ElMessage.success('已保存')
    await load()
  } catch (error) {
    ElMessage.error((error as ApiResponse<unknown>).message || '保存失败')
  }
}

async function moveStage(subjectId: number, stage: AdminStage, move: 'up' | 'down') {
  try {
    await sortStage(subjectId, stage.id, move)
    await load()
  } catch (error) {
    ElMessage.error((error as ApiResponse<unknown>).message || '排序失败')
  }
}

async function toggleStage(subjectId: number, stage: AdminStage) {
  try {
    await updateStageStatus(subjectId, stage.id, stage.status === 1 ? 0 : 1)
    await load()
  } catch (error) {
    ElMessage.error((error as ApiResponse<unknown>).message || '操作失败')
  }
}

async function removeStage(subjectId: number, stage: AdminStage) {
  try {
    await ElMessageBox.confirm(`确定删除阶段「${stage.name}」？`, '删除阶段')
  } catch {
    return
  }
  try {
    await deleteStage(subjectId, stage.id)
    ElMessage.success('已删除')
    await load()
  } catch (error) {
    ElMessage.error((error as ApiResponse<unknown>).message || '删除失败')
  }
}
</script>

<template>
  <div v-loading="loading">
    <div class="stats">
      <div class="stat-card">
        <div class="stat-icon blue">🎓</div>
        <div><strong>{{ stats.subjectCount }}</strong><p>总学科数</p></div>
      </div>
      <div class="stat-card">
        <div class="stat-icon green">📂</div>
        <div><strong>{{ stats.subjectsWithStages }}</strong><p>有阶段的学科</p></div>
      </div>
      <div class="stat-card">
        <div class="stat-icon orange">📁</div>
        <div><strong>{{ stats.stageCount }}</strong><p>阶段总数</p></div>
      </div>
      <div class="stat-card">
        <div class="stat-icon purple">📝</div>
        <div><strong>{{ stats.questionCount.toLocaleString() }}</strong><p>题目总数</p></div>
      </div>
    </div>

    <div class="grid">
      <article v-for="(item, index) in subjects" :key="item.id" class="subject-card">
        <div class="accent" :style="{ background: `linear-gradient(135deg, ${item.themeColor}, ${darken(item.themeColor)})` }" />
        <header class="card-head">
          <div class="card-info">
            <div class="subject-icon" :style="{ background: hexToRgba(item.themeColor, 0.12) }">{{ item.icon }}</div>
            <div>
              <h3>{{ item.name }} <span class="sort-badge" title="排序号">第{{ item.sortOrder }}位</span></h3>
              <p>{{ item.description }}</p>
            </div>
          </div>
          <div class="icon-actions">
            <button v-if="user.has('subject:edit')" type="button" class="icon-btn" title="上移" :disabled="index === 0" @click="moveSubject(item, 'up')"><el-icon><ArrowUp /></el-icon></button>
            <button v-if="user.has('subject:edit')" type="button" class="icon-btn" title="下移" :disabled="index === subjects.length - 1" @click="moveSubject(item, 'down')"><el-icon><ArrowDown /></el-icon></button>
            <button v-if="user.has('subject:edit')" type="button" class="icon-btn" title="编辑学科" @click="openEditSubject(item)"><el-icon><Edit /></el-icon></button>
            <button v-if="user.has('subject:delete')" type="button" class="icon-btn danger" title="删除学科" @click="removeSubject(item)"><el-icon><Delete /></el-icon></button>
          </div>
        </header>
        <div class="card-stats">
          <span>🎬 课程 <b>{{ item.courseCount }}</b></span>
          <span>🗺️ 路线 <b>{{ item.routeCount }}</b></span>
          <span>📝 题目 <b>{{ item.questionCount.toLocaleString() }}</b></span>
        </div>
        <div class="card-body">
          <div class="section-title"><span>阶段</span><span class="count">{{ item.stages.length }}</span></div>
          <p v-if="item.stages.length === 0" class="empty-stages">暂无阶段，点击下方按钮添加</p>
          <div v-else class="stage-list">
            <div v-for="(stage, stageIndex) in item.stages" :key="stage.id" class="stage">
              <span class="stage-icon">{{ stage.icon || '📁' }}</span>
              <div class="stage-info">
                <div>{{ stage.name }} <span class="sort-badge sm">{{ stage.sortOrder }}</span></div>
                <p>{{ stage.questionCount }} 道题目</p>
              </div>
              <div class="stage-actions">
                <button v-if="user.has('subject:edit')" type="button" class="mini-btn" title="上移" :disabled="stageIndex === 0" @click="moveStage(item.id, stage, 'up')"><el-icon><ArrowUp /></el-icon></button>
                <button v-if="user.has('subject:edit')" type="button" class="mini-btn" title="下移" :disabled="stageIndex === item.stages.length - 1" @click="moveStage(item.id, stage, 'down')"><el-icon><ArrowDown /></el-icon></button>
                <el-switch
                  v-if="user.has('subject:edit')"
                  :model-value="stage.status === 1"
                  size="small"
                  @change="toggleStage(item.id, stage)"
                />
                <button v-if="user.has('subject:edit')" type="button" class="mini-btn" title="编辑" @click="openEditStage(item.id, stage)"><el-icon><Edit /></el-icon></button>
                <button v-if="user.has('subject:delete')" type="button" class="mini-btn danger" title="删除" @click="removeStage(item.id, stage)"><el-icon><Delete /></el-icon></button>
              </div>
            </div>
          </div>
          <button v-if="user.has('subject:add')" type="button" class="add-stage" @click="openCreateStage(item)"><el-icon><Plus /></el-icon>添加阶段</button>
        </div>
      </article>
      <button v-if="user.has('subject:add')" type="button" class="add-card" @click="openCreateSubject">
        <span class="plus">＋</span>
        <span>新增学科</span>
        <small>点击创建新学科</small>
      </button>
    </div>

    <el-dialog v-model="subjectVisible" :title="subjectEditing ? '编辑学科' : '新增学科'" width="480px">
      <el-form label-position="top">
        <el-form-item label="学科名称" required><el-input v-model="subjectForm.name" maxlength="50" placeholder="请输入学科名称" /></el-form-item>
        <el-form-item label="学科图标（Emoji）" required>
          <el-input v-model="subjectForm.icon" maxlength="8" placeholder="例如：☕、🌐、🐍" />
          <p class="hint">请输入一个 Emoji 作为学科图标</p>
        </el-form-item>
        <el-form-item label="学科描述" required><el-input v-model="subjectForm.description" type="textarea" maxlength="255" placeholder="请输入学科描述" /></el-form-item>
        <el-form-item label="排序号">
          <el-input-number v-model="subjectForm.sortOrder" :min="1" :max="9999" />
          <p class="hint">数字越小排序越靠前</p>
        </el-form-item>
        <el-form-item label="主题颜色">
          <button v-for="color in colors" :key="color" type="button" class="swatch" :style="{ background: color }" :class="{ on: subjectForm.themeColor === color }" @click="subjectForm.themeColor = color" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :icon="Close" @click="subjectVisible = false">取消</el-button>
        <el-button type="primary" :icon="Check" @click="saveSubject">{{ subjectEditing ? '保存修改' : '确认新增' }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="stageVisible" :title="stageEditing ? '编辑阶段' : '新增阶段'" width="420px">
      <el-form label-position="top">
        <el-form-item label="阶段名称" required><el-input v-model="stageForm.name" maxlength="50" placeholder="请输入阶段名称" /></el-form-item>
        <el-form-item label="图标"><el-input v-model="stageForm.icon" maxlength="8" placeholder="留空使用默认图标" /></el-form-item>
        <el-form-item label="排序号"><el-input-number v-model="stageForm.sortOrder" :min="0" :max="9999" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button :icon="Close" @click="stageVisible = false">取消</el-button>
        <el-button type="primary" :icon="Check" @click="saveStage">{{ stageEditing ? '保存修改' : '确认新增' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: 20px; margin-bottom: 24px; }
.stat-card { display: flex; align-items: center; gap: 16px; padding: 24px; background: #fff; border-radius: 12px; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06); }
.stat-icon { width: 56px; height: 56px; border-radius: 14px; display: grid; place-items: center; font-size: 28px; }
.stat-icon.blue { background: rgba(64, 158, 255, 0.1); }
.stat-icon.green { background: rgba(103, 194, 58, 0.1); }
.stat-icon.orange { background: rgba(230, 162, 60, 0.1); }
.stat-icon.purple { background: rgba(114, 69, 209, 0.1); }
.stat-card strong { display: block; font-size: 28px; line-height: 1.2; }
.stat-card p { margin: 4px 0 0; font-size: 13px; color: #909399; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(380px, 1fr)); gap: 24px; }
.subject-card { display: flex; flex-direction: column; background: #fff; border-radius: 14px; box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06); overflow: hidden; }
.subject-card:hover { box-shadow: 0 6px 20px rgba(0, 0, 0, 0.1); transform: translateY(-2px); }
.accent { height: 6px; }
.card-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 8px; padding: 20px 24px 0; }
.card-info { display: flex; gap: 14px; min-width: 0; }
.subject-icon { width: 52px; height: 52px; border-radius: 14px; display: grid; place-items: center; font-size: 26px; flex: none; }
.card-info h3 { margin: 0 0 4px; font-size: 18px; }
.card-info p, .stage-info p { margin: 0; color: #909399; font-size: 13px; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.sort-badge { display: inline-block; margin-left: 6px; padding: 1px 6px; border-radius: 4px; background: #f4f4f5; color: #909399; font-size: 11px; font-weight: 500; vertical-align: middle; }
.sort-badge.sm { margin-left: 4px; padding: 0 4px; font-size: 10px; }
.icon-actions, .stage-actions { display: flex; align-items: center; gap: 6px; flex: none; }
.icon-btn, .mini-btn { display: grid; place-items: center; border: 0; border-radius: 8px; background: #f5f7fa; color: #909399; cursor: pointer; }
.icon-btn { width: 32px; height: 32px; }
.mini-btn { width: 28px; height: 28px; background: transparent; }
.icon-btn:hover, .mini-btn:hover { background: #e8eaed; color: #303133; }
.icon-btn.danger:hover, .mini-btn.danger:hover { background: rgba(245, 108, 108, 0.1); color: #f56c6c; }
.icon-btn:disabled, .mini-btn:disabled { opacity: 0.35; cursor: not-allowed; }
.card-stats { display: flex; gap: 24px; padding: 16px 24px; color: #909399; font-size: 13px; }
.card-stats b { color: #606266; }
.card-body { padding: 0 24px 20px; }
.section-title { display: flex; align-items: center; justify-content: space-between; padding: 12px 0 8px; border-top: 1px solid #f0f2f5; color: #909399; font-size: 13px; font-weight: 600; }
.count { padding: 2px 8px; border-radius: 10px; background: #f0f2f5; font-size: 12px; font-weight: 500; }
.empty-stages { margin: 8px 0 4px; text-align: center; color: #c0c4cc; font-size: 13px; }
.stage-list { display: flex; flex-direction: column; gap: 8px; }
.stage { display: flex; align-items: center; gap: 10px; padding: 10px 12px; border-radius: 10px; background: #f8f9fb; }
.stage-icon { font-size: 18px; }
.stage-info { flex: 1; min-width: 0; color: #303133; font-size: 14px; font-weight: 500; }
.add-stage { display: flex; align-items: center; justify-content: center; gap: 6px; width: 100%; margin-top: 8px; padding: 10px; border: 1.5px dashed #dcdfe6; border-radius: 10px; background: transparent; color: #909399; cursor: pointer; }
.add-stage:hover { border-color: #409eff; color: #409eff; background: rgba(64, 158, 255, 0.04); }
.add-card { min-height: 220px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px; border: 2px dashed #dcdfe6; border-radius: 14px; background: transparent; color: #909399; cursor: pointer; }
.add-card:hover { border-color: #409eff; color: #409eff; background: rgba(64, 158, 255, 0.04); }
.plus { width: 52px; height: 52px; border-radius: 50%; display: grid; place-items: center; background: #f5f7fa; font-size: 26px; }
.add-card span { font-size: 15px; font-weight: 600; }
.add-card small { color: #c0c4cc; font-size: 12px; }
.hint { margin: 4px 0 0; color: #909399; font-size: 12px; }
.swatch { width: 32px; height: 32px; border: 2px solid transparent; border-radius: 8px; margin-right: 8px; cursor: pointer; }
.swatch.on { border-color: #303133; }
@media (max-width: 1200px) { .stats { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 768px) { .stats, .grid { grid-template-columns: 1fr; } }
</style>
