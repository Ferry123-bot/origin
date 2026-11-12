<script setup lang="ts">
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, Close, Delete, Edit, Plus, View } from '@element-plus/icons-vue'
import type { ApiResponse } from '@/types/api'
import {
  createRole,
  deleteRole,
  fetchPermissionTree,
  fetchRoles,
  saveRolePermissions,
  type PermissionNode,
  type RoleCard,
} from '@/api/role'

const loading = ref(false)
const roles = ref<RoleCard[]>([])
const createVisible = ref(false)
const form = reactive({ name: '', code: '', description: '' })

const treeVisible = ref(false)
const current = ref<RoleCard | null>(null)
const tree = ref<PermissionNode[]>([])
const treeRef = ref<{ getCheckedKeys: (leafOnly?: boolean) => number[] }>()
const checked = ref<number[]>([])

onMounted(load)

async function load() {
  loading.value = true
  try {
    const response = await fetchRoles()
    roles.value = response.data.data
  } catch (error) {
    ElMessage.error((error as ApiResponse<unknown>).message || '加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, { name: '', code: '', description: '' })
  createVisible.value = true
}

async function saveRole() {
  try {
    await createRole({ name: form.name.trim(), code: form.code.trim(), description: form.description.trim() })
    createVisible.value = false
    ElMessage.success('已新增角色')
    await load()
  } catch (error) {
    ElMessage.error((error as ApiResponse<unknown>).message || '保存失败')
  }
}

async function openTree(role: RoleCard) {
  current.value = role
  const response = await fetchPermissionTree(role.id)
  tree.value = response.data.data
  checked.value = leaves(response.data.data).filter((item) => item.checked).map((item) => item.id)
  treeVisible.value = true
  await nextTick()
  treeRef.value?.getCheckedKeys
}

function leaves(nodes: PermissionNode[]): PermissionNode[] {
  return nodes.flatMap((node) => (node.children?.length ? leaves(node.children) : [node]))
}

async function saveTree() {
  if (!current.value || current.value.protectedRole) return
  const ids = treeRef.value?.getCheckedKeys(true) ?? []
  try {
    await saveRolePermissions(current.value.id, ids)
    treeVisible.value = false
    ElMessage.success('权限已保存')
    await load()
  } catch (error) {
    ElMessage.error((error as ApiResponse<unknown>).message || '保存失败')
  }
}

async function remove(role: RoleCard) {
  if (role.protectedRole) return
  if (role.userCount > 0) {
    ElMessage.warning(`该角色下有 ${role.userCount} 个用户，不能删除`)
    return
  }
  try {
    await ElMessageBox.confirm(`确定删除角色「${role.name}」？`, '删除角色', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteRole(role.id)
    ElMessage.success('已删除')
    await load()
  } catch (error) {
    ElMessage.error((error as ApiResponse<unknown>).message || '删除失败')
  }
}
</script>

<template>
  <section v-loading="loading">
    <div class="toolbar">
      <el-button type="primary" :icon="Plus" @click="openCreate">新增角色</el-button>
    </div>
    <el-empty v-if="!loading && roles.length === 0" description="暂无角色数据，点击右上角新增角色" />
    <div class="grid">
      <el-card v-for="role in roles" :key="role.id">
        <div class="head">
          <div>
            <h3>{{ role.name }}</h3>
            <p>{{ role.code }} · {{ role.userCount }} 个用户</p>
          </div>
          <el-tag v-if="role.protectedRole" type="warning">内置</el-tag>
        </div>
        <p class="desc">{{ role.description || '暂无描述' }}</p>
        <div class="tags">
          <el-tag v-for="tag in role.permissionTags.slice(0, 8)" :key="tag" size="small">{{ tag }}</el-tag>
          <span v-if="role.permissionTags.length > 8">等 {{ role.permissionTags.length }} 项</span>
        </div>
        <div class="ops">
          <el-button link type="primary" :icon="role.protectedRole ? View : Edit" @click="openTree(role)">{{ role.protectedRole ? '查看权限' : '编辑权限' }}</el-button>
          <el-button link type="danger" :icon="Delete" :disabled="role.protectedRole || role.userCount > 0" @click="remove(role)">删除</el-button>
        </div>
      </el-card>
    </div>

    <el-dialog v-model="createVisible" title="新增角色" width="440px">
      <el-form label-position="top">
        <el-form-item label="角色名称"><el-input v-model="form.name" maxlength="50" /></el-form-item>
        <el-form-item label="角色编码"><el-input v-model="form.code" maxlength="50" placeholder="小写字母、数字或下划线" /></el-form-item>
        <el-form-item label="角色描述"><el-input v-model="form.description" type="textarea" maxlength="255" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button :icon="Close" @click="createVisible = false">取消</el-button>
        <el-button type="primary" :icon="Check" @click="saveRole">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="treeVisible" :title="`编辑权限 - ${current?.name || ''}`" width="520px">
      <el-tree
        :key="current?.id"
        ref="treeRef"
        :data="tree"
        node-key="id"
        show-checkbox
        default-expand-all
        :props="{ label: 'name', children: 'children', disabled: () => !!current?.protectedRole }"
        :default-checked-keys="checked"
      />
      <template #footer>
        <el-button :icon="Close" @click="treeVisible = false">关闭</el-button>
        <el-button v-if="!current?.protectedRole" type="primary" :icon="Check" @click="saveTree">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.toolbar { display: flex; justify-content: flex-end; margin-bottom: 16px; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 12px; }
.head { display: flex; justify-content: space-between; gap: 12px; }
.head h3 { margin: 0; }
.head p, .desc { color: #909399; }
.tags { display: flex; flex-wrap: wrap; gap: 6px; min-height: 28px; }
.ops { margin-top: 12px; }
</style>
