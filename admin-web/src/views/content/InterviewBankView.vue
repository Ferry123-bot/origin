<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadInstance, UploadRawFile } from 'element-plus'
import { Check, Close, Delete, Document, Edit, Plus, RefreshLeft, Search, Upload, View } from '@element-plus/icons-vue'
import type { CheckboxValueType } from 'element-plus'
import type { ApiResponse } from '@/types/api'
import { useUserStore } from '@/store/user'
import {
  confirmInterviewImport,
  createInterviewQuestion,
  deleteInterviewQuestion,
  fetchInterviewQuestion,
  fetchInterviewQuestions,
  fetchInterviewSubjects,
  fetchInterviewTopics,
  previewInterviewImport,
  updateInterviewQuestion,
  type InterviewDetail,
  type InterviewImportPreview,
  type InterviewQuestion,
  type InterviewSample,
  type InterviewSubject,
  type InterviewTopic,
} from '@/api/interview'

const user = useUserStore()
const tableLoading = ref(false)
const rows = ref<InterviewQuestion[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const subjects = ref<InterviewSubject[]>([])
const topics = ref<InterviewTopic[]>([])
const query = reactive({
  keyword: '',
  subjectId: undefined as number | undefined,
  difficulty: undefined as number | undefined,
})
const topicKey = ref('')
const unclassified = ref(false)

const detailVisible = ref(false)
const detail = ref<InterviewDetail | null>(null)
const editVisible = ref(false)
const editing = ref<InterviewQuestion | null>(null)
const editTopic = ref('')
const editDifficulty = ref(2)
const saving = ref(false)

const createVisible = ref(false)
const createForm = reactive({ subjectId: undefined as number | undefined, content: '', referenceAnswer: '', topic: '', difficulty: 2, tags: '' })

const importVisible = ref(false)
const importUploadRef = ref<UploadInstance>()
const importStep = ref(1)
const importSubjectId = ref<number | undefined>()
const importFile = ref<File | null>(null)
const importPreview = ref<InterviewImportPreview | null>(null)
const importTopic = ref('')
const importDifficulty = ref(2)
const importTags = ref('')
const importTopics = ref<InterviewTopic[]>([])
const importing = ref(false)

const emptyText = computed(() => (unclassified.value ? '没有未分类的题目 —— 题库里的题都标好主题了' : '没有符合条件的题目'))
const vocabularyTopics = computed(() => topics.value.filter((item) => item.inVocabulary))

onMounted(async () => {
  await Promise.all([loadSubjects(), loadTopics(), load()])
})

async function loadSubjects() {
  const response = await fetchInterviewSubjects()
  subjects.value = response.data.data
}

async function loadTopics() {
  const response = await fetchInterviewTopics(query.subjectId)
  topics.value = response.data.data
}

async function load() {
  tableLoading.value = true
  try {
    const picked = currentTopic()
    const response = await fetchInterviewQuestions({
      keyword: query.keyword.trim() || undefined,
      subjectId: query.subjectId,
      topicId: !unclassified.value && picked?.id ? picked.id : undefined,
      topic: !unclassified.value && picked && picked.id == null ? picked.name : undefined,
      topicSubjectId: !unclassified.value && picked && picked.id == null ? picked.subjectId ?? undefined : undefined,
      difficulty: query.difficulty,
      unclassified: unclassified.value || undefined,
      page: page.value,
      pageSize: pageSize.value,
    })
    rows.value = response.data.data.list
    total.value = response.data.data.total
  } catch (error) {
    ElMessage.error(textOf(error, '题目加载失败'))
  } finally {
    tableLoading.value = false
  }
}

function currentTopic() {
  return topics.value.find((item) => optionKey(item) === topicKey.value)
}

function optionKey(item: InterviewTopic) {
  return item.id != null ? `id:${item.id}` : `out:${item.subjectId ?? 0}:${item.name}`
}

function topicLabel(item: InterviewTopic) {
  const count = `${item.name}（${item.questionCount} 题）`
  const outside = item.inVocabulary ? '' : ' · 词表外'
  const scope = !query.subjectId && item.subjectName ? ` · ${item.subjectName}` : ''
  return count + outside + scope
}

async function onDirectionChange() {
  topicKey.value = ''
  page.value = 1
  await loadTopics()
  await load()
}

function onTopicChange() {
  if (topicKey.value) unclassified.value = false
  page.value = 1
  load()
}

function onUntagged(value: CheckboxValueType) {
  unclassified.value = value === true
  if (unclassified.value) topicKey.value = ''
  page.value = 1
  load()
}

function onDifficultyChange() {
  page.value = 1
  load()
}

function searchByKeyword() {
  page.value = 1
  load()
}

async function resetQuery() {
  query.keyword = ''
  query.subjectId = undefined
  query.difficulty = undefined
  topicKey.value = ''
  unclassified.value = false
  page.value = 1
  await loadTopics()
  await load()
}

function onPageChange(next: number) {
  page.value = next
  load()
}

function onSizeChange(next: number) {
  pageSize.value = next
  page.value = 1
  load()
}

async function openDetail(row: InterviewQuestion) {
  try {
    const response = await fetchInterviewQuestion(row.id)
    detail.value = response.data.data
    detailVisible.value = true
  } catch (error) {
    ElMessage.error(textOf(error, '题目加载失败'))
  }
}

function openEdit(row: InterviewQuestion) {
  editing.value = row
  editTopic.value = row.topic || ''
  editDifficulty.value = row.difficulty
  editVisible.value = true
}

function editTopics() {
  const row = editing.value
  const list = vocabularyTopics.value.filter((item) => !row || item.subjectId == null || item.subjectId === row.subjectId)
  if (row?.topic && !list.some((item) => item.name === row.topic)) {
    return [...list, { id: null, name: row.topic, subjectId: row.subjectId, subjectName: row.subjectName, questionCount: 0, inVocabulary: false, sortOrder: 0 }]
  }
  return list
}

async function saveEdit() {
  if (!editing.value) return
  saving.value = true
  try {
    await updateInterviewQuestion(editing.value.id, { topic: editTopic.value, difficulty: editDifficulty.value })
    ElMessage.success('已保存')
    editVisible.value = false
    await Promise.all([loadTopics(), load()])
  } catch (error) {
    ElMessage.error(textOf(error, '保存失败'))
  } finally {
    saving.value = false
  }
}

function openCreate() {
  createForm.subjectId = query.subjectId
  createForm.content = ''
  createForm.referenceAnswer = ''
  createForm.topic = ''
  createForm.difficulty = 2
  createForm.tags = ''
  createVisible.value = true
}

async function saveCreate() {
  if (!createForm.subjectId) {
    ElMessage.warning('请选择所属学科')
    return
  }
  if (!createForm.content.trim()) {
    ElMessage.warning('题干不能为空')
    return
  }
  saving.value = true
  try {
    await createInterviewQuestion({
      subjectId: createForm.subjectId,
      content: createForm.content.trim(),
      referenceAnswer: createForm.referenceAnswer.trim(),
      topic: createForm.topic.trim(),
      difficulty: createForm.difficulty,
      tags: createForm.tags.trim(),
    })
    ElMessage.success('已录入题目')
    createVisible.value = false
    page.value = 1
    await Promise.all([loadTopics(), load()])
  } catch (error) {
    ElMessage.error(textOf(error, '录入失败'))
  } finally {
    saving.value = false
  }
}

function openImport() {
  importStep.value = 1
  importSubjectId.value = query.subjectId
  importFile.value = null
  importPreview.value = null
  importTopic.value = ''
  importDifficulty.value = 2
  importTags.value = ''
  importVisible.value = true
  nextTick(() => importUploadRef.value?.clearFiles())
}

function onImportFile(upload: { raw?: File }) {
  const file = upload.raw
  if (!file) return
  if (!file.name.toLowerCase().endsWith('.md')) {
    ElMessage.warning('只支持 Markdown（.md）文件')
    importFile.value = null
    importUploadRef.value?.clearFiles()
    return
  }
  importFile.value = file
}

function replaceImportFile(files: File[]) {
  const file = files[0]
  importUploadRef.value?.clearFiles()
  if (file) importUploadRef.value?.handleStart(file as UploadRawFile)
}

async function toImportPreview() {
  if (!importSubjectId.value) {
    ElMessage.warning('请选择所属学科')
    return
  }
  if (!importFile.value) {
    ElMessage.warning('请先选择 Markdown 文件')
    return
  }
  importing.value = true
  try {
    const response = await previewInterviewImport(importFile.value)
    importPreview.value = response.data.data
    const topicsResponse = await fetchInterviewTopics(importSubjectId.value)
    importTopics.value = topicsResponse.data.data.filter((item) => item.inVocabulary)
    importStep.value = 2
  } catch (error) {
    ElMessage.error(textOf(error, '解析失败'))
  } finally {
    importing.value = false
  }
}

async function confirmImport() {
  if (!importPreview.value || !importSubjectId.value) return
  const fileName = importPreview.value.fileName
  importing.value = true
  try {
    const response = await confirmInterviewImport({
      token: importPreview.value.token,
      subjectId: importSubjectId.value,
      topic: importTopic.value,
      difficulty: importDifficulty.value,
      tags: importTags.value.trim(),
    })
    ElMessage.success({
      message: `导入成功，已导入「${fileName}」，共 ${response.data.data.imported} 道题`,
      duration: 4000,
      showClose: true,
    })
    importVisible.value = false
    page.value = 1
    await Promise.all([loadTopics(), load()])
  } catch (error) {
    ElMessage.error(textOf(error, '导入失败'))
  } finally {
    importing.value = false
  }
}

async function removeQuestion(row: InterviewQuestion) {
  try {
    await ElMessageBox.confirm(deleteTip(row), '删除题目', { type: 'warning' })
    await deleteInterviewQuestion(row.id)
    ElMessage.success('已删除')
    await Promise.all([loadTopics(), load()])
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(textOf(error, '删除失败'))
  }
}

function deleteTip(row: InterviewQuestion) {
  if (row.sourceType === 2) return '确定要删除这道题吗？这是手工录入的题，系统里没有源文档，删掉就找不回来了。'
  return '确定要删除这道题吗？上传的 Markdown 系统不留存，删掉之后没法重新解析，只能再上传一次源文件。'
}

function editTip(sourceType: number) {
  if (sourceType === 2) return '题干、参考答案与标签是手工录入的，这一页改不了 —— 要改内容只能删掉重新录入。'
  return '题干、参考答案与标签是上传 Markdown 时解析出来的，这一页改不了 —— 要改内容请改好源文件重新上传导入。'
}

function sourceText(row: { sourceType: number; sourceDocName: string | null; sourceSeq: number | null }) {
  if (row.sourceType === 2) return '手工录入（无来源文档）'
  const name = row.sourceDocName || '未记录文档名'
  return `${name} 第 ${row.sourceSeq || 1} 题`
}

function difficultyName(value: number) {
  return value === 1 ? '简单' : value === 3 ? '困难' : '中等'
}

function difficultyTag(value: number) {
  return value === 1 ? 'success' : value === 3 ? 'danger' : 'warning'
}

function tagList(tags: string | null) {
  return (tags || '').split(',').map((item) => item.trim()).filter(Boolean)
}

function fileSize(bytes: number) {
  if (!bytes) return '0KB'
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))}KB`
  return `${(bytes / 1024 / 1024).toFixed(1)}MB`
}

function sampleAnswer(sample: InterviewSample) {
  return sample.referenceAnswer || '（标题下面没有正文，这道题没有参考答案）'
}

function textOf(error: unknown, fallback: string) {
  const body = error as ApiResponse<unknown>
  return body?.message || fallback
}
</script>

<template>
  <div>
    <section class="panel">
      <div class="toolbar">
        <div class="filters">
          <el-input v-model="query.keyword" clearable placeholder="搜索题干" style="width: 220px" @keyup.enter="searchByKeyword" />
          <el-select v-model="query.subjectId" clearable placeholder="全部学科" style="width: 220px" @change="onDirectionChange">
            <el-option v-for="item in subjects" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
          <el-select v-model="topicKey" clearable placeholder="全部主题" style="width: 240px" :disabled="unclassified" @change="onTopicChange">
            <el-option v-for="item in topics" :key="optionKey(item)" :label="topicLabel(item)" :value="optionKey(item)" />
          </el-select>
          <el-select v-model="query.difficulty" clearable placeholder="全部难度" style="width: 120px" @change="onDifficultyChange">
            <el-option label="简单" :value="1" />
            <el-option label="中等" :value="2" />
            <el-option label="困难" :value="3" />
          </el-select>
          <el-checkbox :model-value="unclassified" :disabled="!!topicKey" @change="onUntagged">只看未分类</el-checkbox>
        </div>
        <div class="toolbar-actions">
          <el-button type="primary" :icon="Search" @click="searchByKeyword">查询</el-button>
          <el-button :icon="RefreshLeft" @click="resetQuery">重置</el-button>
          <el-button v-if="user.has('interviewbank:edit')" :icon="Upload" @click="openImport">上传 Markdown</el-button>
          <el-button v-if="user.has('interviewbank:edit')" type="primary" :icon="Plus" @click="openCreate">录入题目</el-button>
        </div>
      </div>

      <div class="tip">
        这一页是所有学科的面试题总览。题目有两个来路：上传 Markdown 文档解析入库（主）、手工录入。
        不管哪一路进来，题干与参考答案在这一页都只能看不能改，这里只改主题与难度。
        主题下拉里的题数包含 0 题的词表主题；标「· 词表外」的主题不在配置的词表内。
      </div>

      <el-table v-loading="tableLoading" :data="rows" row-key="id">
        <el-table-column label="题目" min-width="360">
          <template #default="{ row }">
            <div class="stem-line">
              <el-tag v-if="row.sourceType === 2" size="small" type="success" effect="light">手工录入</el-tag>
              <button type="button" class="stem" @click="openDetail(row)">{{ row.content }}</button>
            </div>
            <div v-if="tagList(row.tags).length" class="tag-row">
              <span v-for="tag in tagList(row.tags)" :key="tag" class="plain-tag">{{ tag }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="学科" min-width="200" prop="subjectName" />
        <el-table-column label="来源" min-width="220" class-name="source-col">
          <template #default="{ row }">
            <div class="source-name">{{ row.sourceType === 2 ? '手工录入' : (row.sourceDocName || '—') }}</div>
            <div class="sub">{{ row.sourceType === 2 ? '无来源文档' : `第 ${row.sourceSeq || 1} 题` }}</div>
          </template>
        </el-table-column>
        <el-table-column label="难度" width="88">
          <template #default="{ row }">
            <el-tag :type="difficultyTag(row.difficulty)" effect="light">{{ difficultyName(row.difficulty) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="主题" width="140">
          <template #default="{ row }">
            <el-tag v-if="row.topic" effect="light">{{ row.topic }}</el-tag>
            <el-tag v-else type="warning" effect="light">未分类</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="228">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button link type="primary" :icon="View" @click="openDetail(row)">详情</el-button>
              <el-button v-if="user.has('interviewbank:edit')" link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
              <el-button v-if="user.has('interviewbank:delete')" link type="danger" :icon="Delete" @click="removeQuestion(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty :description="emptyText" />
        </template>
      </el-table>

      <div class="pager">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="total"
          :current-page="page"
          :page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          @current-change="onPageChange"
          @size-change="onSizeChange"
        />
      </div>
    </section>

    <el-dialog v-model="detailVisible" title="题目详情" width="680px">
      <template v-if="detail">
        <div class="meta">
          <el-tag v-if="detail.topic" effect="light">{{ detail.topic }}</el-tag>
          <el-tag v-else type="warning" effect="light">未分类</el-tag>
          <el-tag :type="difficultyTag(detail.difficulty)" effect="light">{{ difficultyName(detail.difficulty) }}</el-tag>
          <span v-for="tag in tagList(detail.tags)" :key="tag" class="plain-tag">{{ tag }}</span>
        </div>
        <div class="block"><div class="label">题干</div><div class="text">{{ detail.content }}</div></div>
        <div class="block">
          <div class="label">参考答案</div>
          <div v-if="detail.referenceAnswer" class="text">{{ detail.referenceAnswer }}</div>
          <div v-else class="muted">{{ detail.sourceType === 2 ? '（录入时没有填参考答案）' : '（标题下面没有正文，这道题没有参考答案）' }}</div>
        </div>
        <div class="block"><div class="label">来源</div><div class="text">{{ detail.subjectName }} / {{ sourceText(detail) }}</div></div>
        <div class="muted">创建于 {{ detail.createdAt }} · 最近修改于 {{ detail.updatedAt }}</div>
      </template>
      <template #footer>
        <el-button :icon="Close" @click="detailVisible = false">关闭</el-button>
        <el-button v-if="detail && user.has('interviewbank:edit')" type="primary" :icon="Edit" @click="detailVisible = false; openEdit(detail)">编辑主题与难度</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editVisible" title="编辑主题与难度" width="520px">
      <el-form label-position="top">
        <el-form-item label="主题">
          <el-select v-model="editTopic" style="width: 100%">
            <el-option label="未分类（清空主题）" value="" />
            <el-option v-for="item in editTopics()" :key="item.name + String(item.subjectId)" :label="item.name" :value="item.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="难度">
          <el-select v-model="editDifficulty" style="width: 100%">
            <el-option label="简单" :value="1" />
            <el-option label="中等" :value="2" />
            <el-option label="困难" :value="3" />
          </el-select>
        </el-form-item>
      </el-form>
      <div class="note">可选主题来自配置的主题词表，要增删主题请去「系统设置」。<br>{{ editing ? editTip(editing.sourceType) : '' }}</div>
      <template #footer>
        <el-button :icon="Close" @click="editVisible = false">取消</el-button>
        <el-button type="primary" :icon="Check" :loading="saving" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="createVisible" title="录入题目" width="640px">
      <el-form label-position="top">
        <el-form-item label="所属学科" required>
          <el-select v-model="createForm.subjectId" placeholder="请选择学科" style="width: 100%">
            <el-option v-for="item in subjects" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="题干" required>
          <el-input v-model="createForm.content" type="textarea" :rows="3" maxlength="20000" placeholder="题干会直接拿去提问，别写「1.」这类题号前缀" />
        </el-form-item>
        <el-form-item label="参考答案">
          <el-input v-model="createForm.referenceAnswer" type="textarea" :rows="4" maxlength="20000" placeholder="可以留空" />
        </el-form-item>
        <div class="pair">
          <el-form-item label="主题">
            <el-input v-model="createForm.topic" maxlength="50" placeholder="可留空，留空即为未分类" style="width: 220px" />
          </el-form-item>
          <el-form-item label="难度">
            <el-select v-model="createForm.difficulty" style="width: 160px">
              <el-option label="简单" :value="1" />
              <el-option label="中等" :value="2" />
              <el-option label="困难" :value="3" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="标签">
          <el-input v-model="createForm.tags" maxlength="255" placeholder="多个标签用逗号分隔，可留空" />
        </el-form-item>
      </el-form>
      <div class="note">手工录入的题没有源文档，列表会标「手工录入」。录进来之后题干和参考答案就不能再改，录错了只能删掉重录。</div>
      <template #footer>
        <el-button :icon="Close" @click="createVisible = false">取消</el-button>
        <el-button type="primary" :icon="Check" :loading="saving" @click="saveCreate">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="importVisible" :title="importStep === 1 ? '上传 Markdown 导入题目' : '上传 Markdown 导入题目 · 解析预览'" width="680px">
      <template v-if="importStep === 1">
        <el-form label-position="top">
          <el-form-item label="所属学科" required>
            <el-select v-model="importSubjectId" placeholder="请选择学科" style="width: 100%">
              <el-option v-for="item in subjects" :key="item.id" :label="item.name" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="Markdown 题库文档" required>
            <el-upload
              ref="importUploadRef"
              :auto-upload="false"
              :limit="1"
              :show-file-list="false"
              accept=".md,text/markdown"
              :on-change="onImportFile"
              :on-exceed="replaceImportFile"
            >
              <el-button :icon="Document">选择 .md 文件</el-button>
            </el-upload>
            <div v-if="importFile" class="picked-file">{{ importFile.name }}</div>
          </el-form-item>
        </el-form>
        <div class="note">按 Markdown 标题逐题切分：标题当题干，标题下面的正文当参考答案，原文照抄。<br>标题只认 # 到 ###，单次最多 500 道。上传的文件不留存，解析入库之后只保留文档名。</div>
      </template>
      <template v-else-if="importPreview">
        <div class="preview">
          <div>文件：<strong>{{ importPreview.fileName }}</strong>（{{ fileSize(importPreview.fileSize) }}）</div>
          <div>解析出 <strong>{{ importPreview.total }}</strong> 道题。</div>
          <div class="sample" v-for="sample in importPreview.samples" :key="sample.lineNo">
            <div>{{ sample.content }}</div>
            <div class="sub">{{ sampleAnswer(sample) }} · 源文件第 {{ sample.lineNo }} 行</div>
          </div>
        </div>
        <div class="pair">
          <el-form-item label="统一主题">
            <el-select v-model="importTopic" style="width: 220px">
              <el-option label="未分类（留空）" value="" />
              <el-option v-for="item in importTopics" :key="optionKey(item)" :label="item.name" :value="item.name" />
            </el-select>
          </el-form-item>
          <el-form-item label="统一难度">
            <el-select v-model="importDifficulty" style="width: 160px">
              <el-option label="简单" :value="1" />
              <el-option label="中等" :value="2" />
              <el-option label="困难" :value="3" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="统一标签">
          <el-input v-model="importTags" maxlength="255" placeholder="多个标签用逗号分隔，可留空" />
        </el-form-item>
        <div class="note">主题、难度和标签整份文档一次性标注，导入后可以在列表里逐题改主题和难度。</div>
      </template>
      <template #footer>
        <el-button :icon="Close" @click="importVisible = false">取消</el-button>
        <el-button v-if="importStep === 1" type="primary" :icon="View" :loading="importing" @click="toImportPreview">解析预览</el-button>
        <el-button v-else type="primary" :icon="Upload" :loading="importing" @click="confirmImport">开始导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.panel { background: #fff; border-radius: 12px; padding: 20px; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06); }
.toolbar { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; flex-wrap: wrap; }
.filters, .toolbar-actions, .row-actions, .meta, .pair { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.toolbar-actions { justify-content: flex-end; }
.tip { margin: 16px 0; padding: 12px 16px; background: rgba(64, 158, 255, 0.05); border-radius: 8px; color: #606266; font-size: 13px; line-height: 1.7; }
.stem-line { display: flex; align-items: flex-start; gap: 8px; }
.stem { display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; flex: 1; padding: 0; border: 0; background: transparent; color: #303133; text-align: left; line-height: 1.6; cursor: pointer; }
.tag-row { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px; }
.plain-tag { font-size: 12px; padding: 1px 8px; border-radius: 4px; background: #f4f4f5; color: #909399; border: 1px solid #e9e9eb; }
.sub, .muted { color: #909399; font-size: 12px; }
.row-actions { flex-wrap: nowrap; }
.row-actions :deep(.el-button) { margin-left: 0; padding-left: 6px; padding-right: 6px; }
.pager { display: flex; justify-content: flex-end; margin-top: 16px; }
.block { margin-top: 16px; }
.label { margin-bottom: 6px; color: #909399; font-size: 13px; }
.text { white-space: pre-wrap; line-height: 1.7; color: #303133; }
.note { margin-top: 8px; padding: 12px 16px; background: #f5f7fa; border-radius: 8px; color: #909399; font-size: 12px; line-height: 1.8; }
.preview { margin-bottom: 12px; line-height: 1.7; word-break: break-all; }
.picked-file { flex: 1 0 100%; margin-top: 8px; color: #606266; line-height: 1.6; word-break: break-all; }
:deep(td.source-col .cell) { white-space: normal; word-break: break-all; text-overflow: clip; line-height: 1.5; }
.sample { margin-top: 8px; padding: 10px 12px; background: #f5f7fa; border-radius: 8px; }
</style>
