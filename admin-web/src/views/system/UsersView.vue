<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Check, CircleCheck, CircleClose, Close, Delete, Edit, Key, Plus, RefreshLeft, Search } from '@element-plus/icons-vue'
import type { ApiResponse } from '@/types/api'
import { useUserStore } from '@/store/user'
import { formatTime } from '@/utils/time'
import {
  batchDeleteUsers,
  batchUserStatus,
  createUser,
  deleteUser,
  fetchRoleOptions,
  fetchUserDetail,
  fetchUsers,
  resetUserPassword,
  updateUser,
  type AdminUserDetail,
  type AdminUserItem,
  type RoleOption,
} from '@/api/user'

const user = useUserStore()
const loading = ref(false)
const rows = ref<AdminUserItem[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const roles = ref<RoleOption[]>([])
const selected = ref<AdminUserItem[]>([])
const query = reactive({ keyword: '', roleId: undefined as number | undefined, status: undefined as number | undefined })

const editorVisible = ref(false)
const editing = ref<AdminUserItem | null>(null)
const formRef = ref<FormInstance>()
const form = reactive({ username: '', mobile: '', email: '', roleId: undefined as number | undefined, password: '', status: 1 })
const rules = computed<FormRules>(() => ({
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { pattern: /^\S{1,50}$/, message: '用户名不能包含空格', trigger: 'blur' },
  ],
  mobile: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' },
  ],
  roleId: [{ required: true, message: '请选择角色', trigger: 'change' }],
  password: editing.value
    ? [{ pattern: /^$|^\S{6,20}$/, message: '密码需为 6~20 位且不能包含空格', trigger: 'blur' }]
    : [
        { required: true, message: '请输入密码', trigger: 'blur' },
        { pattern: /^\S{6,20}$/, message: '密码需为 6~20 位且不能包含空格', trigger: 'blur' },
      ],
}))

const detailVisible = ref(false)
const detail = ref<AdminUserDetail | null>(null)

const assignableRoles = computed(() => roles.value.filter((item) => item.code !== 'super_admin'))
const roleLocked = computed(() => !!editing.value?.builtin)

const avatarColors = [
  'linear-gradient(135deg, #667eea, #764ba2)',
  'linear-gradient(135deg, #43e97b, #38f9d7)',
  'linear-gradient(135deg, #fa709a, #fee140)',
  'linear-gradient(135deg, #4facfe, #00f2fe)',
  'linear-gradient(135deg, #f093fb, #f5576c)',
  'linear-gradient(135deg, #4bc0c8, #c779d0)',
]

function avatarColor(id: number) {
  return avatarColors[Math.abs(id) % avatarColors.length]
}

function avatarText(name: string) {
  const char = name?.trim().charAt(0)
  return char ? char.toLocaleUpperCase() : '?'
}

function mobileText(mobile: string | null | undefined) {
  const text = mobile?.trim()
  return text || '—'
}

onMounted(async () => {
  await loadRoles()
  await load()
})

async function loadRoles() {
  const response = await fetchRoleOptions()
  roles.value = response.data.data
}

async function load() {
  loading.value = true
  try {
    const response = await fetchUsers({
      keyword: query.keyword || undefined,
      roleId: query.roleId,
      status: query.status,
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

function search() {
  page.value = 1
  load()
}

function resetQuery() {
  query.keyword = ''
  query.roleId = undefined
  query.status = undefined
  search()
}

async function resetFormValidation() {
  await nextTick()
  formRef.value?.clearValidate()
}

function openCreate() {
  editing.value = null
  Object.assign(form, { username: '', mobile: '', email: '', roleId: undefined, password: '', status: 1 })
  editorVisible.value = true
  resetFormValidation()
}

function openEdit(row: AdminUserItem) {
  editing.value = row
  Object.assign(form, {
    username: row.username,
    mobile: row.mobile || '',
    email: row.email || '',
    roleId: row.roleId,
    password: '',
    status: row.status,
  })
  editorVisible.value = true
  resetFormValidation()
}

async function save() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid || !form.roleId) return
  const payload = {
    username: form.username.trim(),
    mobile: form.mobile.trim(),
    email: form.email.trim(),
    roleId: form.roleId,
    password: form.password,
    status: form.status,
  }
  try {
    if (editing.value) {
      await updateUser(editing.value.id, payload)
    } else {
      await createUser(payload)
    }
    editorVisible.value = false
    ElMessage.success('已保存')
    await load()
  } catch (error) {
    ElMessage.error((error as ApiResponse<unknown>).message || '保存失败')
  }
}

async function changeStatus(row: AdminUserItem) {
  const next = row.status === 1 ? 0 : 1
  const text = next === 1 ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(`确定${text}用户「${row.username}」？`, `${text}用户`)
  } catch {
    return
  }
  try {
    await batchUserStatus([row.id], next)
    ElMessage.success(`已${text}`)
    await load()
  } catch (error) {
    ElMessage.error((error as ApiResponse<unknown>).message || '操作失败')
  }
}

async function resetPassword(row: AdminUserItem) {
  try {
    await ElMessageBox.confirm(`确定将「${row.username}」的密码重置为默认密码？`, '重置密码')
  } catch {
    return
  }
  try {
    const response = await resetUserPassword(row.id)
    await ElMessageBox.alert(`新密码：${response.data.data.password}`, '密码已重置，请妥善告知用户')
  } catch (error) {
    ElMessage.error((error as ApiResponse<unknown>).message || '重置失败')
  }
}

async function remove(row: AdminUserItem) {
  try {
    await ElMessageBox.confirm(`确定删除用户「${row.username}」？`, '删除用户', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteUser(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (error) {
    ElMessage.error((error as ApiResponse<unknown>).message || '删除失败')
  }
}

async function batchStatus(status: number) {
  if (selected.value.length === 0) {
    ElMessage.warning('请先勾选用户')
    return
  }
  const text = status === 1 ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(`确定批量${text} ${selected.value.length} 个用户？`, `批量${text}`)
  } catch {
    return
  }
  try {
    await batchUserStatus(selected.value.map((item) => item.id), status)
    ElMessage.success(`已批量${text}`)
    selected.value = []
    await load()
  } catch (error) {
    ElMessage.error((error as ApiResponse<unknown>).message || '操作失败')
  }
}

async function batchRemove() {
  if (selected.value.length === 0) {
    ElMessage.warning('请先勾选用户')
    return
  }
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${selected.value.length} 个用户？`, '批量删除', { type: 'warning' })
  } catch {
    return
  }
  try {
    await batchDeleteUsers(selected.value.map((item) => item.id))
    ElMessage.success('已删除')
    selected.value = []
    await load()
  } catch (error) {
    ElMessage.error((error as ApiResponse<unknown>).message || '删除失败')
  }
}

async function openDetail(row: AdminUserItem) {
  try {
    const response = await fetchUserDetail(row.id)
    detail.value = response.data.data
    detailVisible.value = true
  } catch (error) {
    ElMessage.error((error as ApiResponse<unknown>).message || '加载详情失败')
  }
}
</script>

<template>
  <section class="panel" v-loading="loading">
    <div class="toolbar">
      <div class="filters">
        <el-input v-model="query.keyword" placeholder="搜索用户名/手机号..." clearable style="width: 200px" @keyup.enter="search" />
        <el-select v-model="query.roleId" placeholder="全部角色" clearable style="width: 140px">
          <el-option v-for="item in roles" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 140px">
          <el-option label="已启用" :value="1" />
          <el-option label="已禁用" :value="0" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        <el-button :icon="RefreshLeft" @click="resetQuery">重置</el-button>
      </div>
      <div class="toolbar-actions">
        <el-button v-if="user.has('user:edit')" :icon="CircleCheck" @click="batchStatus(1)">批量启用</el-button>
        <el-button v-if="user.has('user:edit')" :icon="CircleClose" @click="batchStatus(0)">批量禁用</el-button>
        <el-button v-if="user.has('user:disable')" type="danger" :icon="Delete" @click="batchRemove">批量删除</el-button>
        <el-button v-if="user.has('user:add')" type="primary" :icon="Plus" @click="openCreate">新增用户</el-button>
      </div>
    </div>
    <el-table :data="rows" row-key="id" @selection-change="selected = $event">
      <el-table-column type="selection" width="48" reserve-selection />
      <el-table-column label="用户" min-width="160">
        <template #default="{ row }">
          <button type="button" class="user-cell" @click="openDetail(row)">
            <span class="user-avatar" :style="{ background: avatarColor(row.id) }">{{ avatarText(row.username) }}</span>
            <span class="user-name">{{ row.username }}</span>
          </button>
        </template>
      </el-table-column>
      <el-table-column label="手机号" min-width="130">
        <template #default="{ row }">{{ mobileText(row.mobile) }}</template>
      </el-table-column>
      <el-table-column prop="roleName" label="角色" min-width="120" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '已启用' : '已禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="最后登录" min-width="160">
        <template #default="{ row }">{{ formatTime(row.lastLoginAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" min-width="340">
        <template #default="{ row }">
          <div class="ops">
            <el-button v-if="user.has('user:edit')" link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
            <el-button
              v-if="user.has('user:edit')"
              link
              :type="row.status === 1 ? 'danger' : 'success'"
              :icon="row.status === 1 ? CircleClose : CircleCheck"
              :disabled="row.builtin"
              @click="changeStatus(row)"
            >{{ row.status === 1 ? '禁用' : '启用' }}</el-button>
            <el-button v-if="user.has('user:edit')" link type="primary" :icon="Key" @click="resetPassword(row)">重置密码</el-button>
            <el-button v-if="user.has('user:disable')" link type="danger" :icon="Delete" :disabled="row.builtin" @click="remove(row)">删除</el-button>
          </div>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="没有找到匹配的用户" />
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

    <el-dialog v-model="editorVisible" :title="editing ? '编辑用户' : '新增用户'" width="480px">
      <el-form ref="formRef" class="user-form" :model="form" :rules="rules" label-position="top" hide-required-asterisk>
        <el-form-item prop="username">
          <template #label><span class="req">*</span>用户名</template>
          <el-input v-model="form.username" maxlength="50" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item prop="mobile">
          <template #label><span class="req">*</span>手机号</template>
          <el-input v-model="form.mobile" maxlength="11" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" maxlength="100" placeholder="请输入邮箱（选填）" />
        </el-form-item>
        <el-form-item prop="roleId">
          <template #label><span class="req">*</span>角色</template>
          <el-select v-model="form.roleId" :disabled="roleLocked" placeholder="请选择角色" style="width: 100%">
            <el-option v-for="item in (roleLocked ? roles : assignableRoles)" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item prop="password">
          <template #label><span v-if="!editing" class="req">*</span>{{ editing ? '新密码' : '密码' }}</template>
          <el-input
            v-model="form.password"
            type="password"
            show-password
            maxlength="20"
            :placeholder="editing ? '留空则不修改密码' : '请输入密码'"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch
            v-model="form.status"
            :active-value="1"
            :inactive-value="0"
            active-text="已启用"
            inactive-text="已禁用"
            :disabled="roleLocked"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :icon="Close" @click="editorVisible = false">取消</el-button>
        <el-button type="primary" :icon="Check" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="用户详情" size="420px">
      <template v-if="detail">
        <div class="user-cell detail-user">
          <span class="user-avatar" :style="{ background: avatarColor(detail.id) }">{{ avatarText(detail.username) }}</span>
          <strong>{{ detail.username }}</strong>
        </div>
        <p>角色：{{ detail.roleName }}</p>
        <p>状态：{{ detail.status === 1 ? '已启用' : '已禁用' }}</p>
        <p>手机号：{{ mobileText(detail.mobile) }}</p>
        <p>邮箱：{{ detail.email || '—' }}</p>
        <p>创建时间：{{ formatTime(detail.createdAt) }}</p>
        <p>最后登录：{{ formatTime(detail.lastLoginAt) }}</p>
        <h4>最近操作</h4>
        <el-empty v-if="detail.recentLogs.length === 0" description="暂无操作记录" />
        <div v-for="item in detail.recentLogs" :key="item.id" class="log">
          <span>{{ item.action }} · {{ item.targetType }} / {{ item.targetName }}</span>
          <em>{{ formatTime(item.createdAt) }}</em>
        </div>
      </template>
    </el-drawer>
  </section>
</template>

<style scoped>
.panel { background: #fff; border-radius: 12px; padding: 20px; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06); }
.toolbar { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 16px; flex-wrap: wrap; }
.filters, .toolbar-actions { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
.pager { margin-top: 16px; justify-content: flex-end; }
.user-cell { display: inline-flex; align-items: center; gap: 12px; padding: 0; border: 0; background: transparent; cursor: pointer; }
.user-avatar { width: 36px; height: 36px; border-radius: 50%; display: inline-flex; align-items: center; justify-content: center; color: #fff; font-weight: 600; font-size: 12px; flex-shrink: 0; }
.user-name { font-size: 14px; font-weight: 500; color: #303133; }
.user-cell:hover .user-name { color: #409eff; }
.detail-user { cursor: default; margin-bottom: 12px; }
.ops { display: flex; align-items: center; flex-wrap: nowrap; }
.ops :deep(.el-button--danger.is-link.is-disabled),
.ops :deep(.el-button--danger.is-link.is-disabled:hover) {
  --el-button-disabled-text-color: var(--el-color-danger-light-5);
  color: var(--el-color-danger-light-5);
  cursor: not-allowed;
}
.req { color: #f56c6c; margin-right: 4px; }
.log { display: flex; justify-content: space-between; gap: 12px; padding: 8px 0; border-top: 1px solid #ebeef5; }
.log em { color: #909399; font-style: normal; }
</style>
