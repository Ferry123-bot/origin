<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, RefreshLeft, Upload } from '@element-plus/icons-vue'
import type { UploadRequestOptions } from 'element-plus'
import type { ApiResponse } from '@/types/api'
import { useUserStore } from '@/store/user'
import { fetchSettings, resetSettings, saveSettings, uploadImage } from '@/api/setting'

const user = useUserStore()
const loading = ref(false)
const form = reactive({ siteName: '', siteDescription: '', logoUrl: '', defaultAvatar: '' })
const canEdit = user.has('system:setting')

onMounted(load)

async function load() {
  loading.value = true
  try {
    const response = await fetchSettings()
    Object.assign(form, response.data.data)
  } catch (error) {
    ElMessage.error((error as ApiResponse<unknown>).message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function upload(kind: 'logo' | 'avatar', options: UploadRequestOptions) {
  try {
    const response = await uploadImage(options.file as File, kind)
    if (kind === 'logo') form.logoUrl = response.data.data.url
    else form.defaultAvatar = response.data.data.url
    ElMessage.success('图片已上传')
  } catch (error) {
    ElMessage.error((error as ApiResponse<unknown>).message || '上传失败')
  }
}

async function save() {
  if (!form.siteName.trim()) {
    ElMessage.warning('请输入平台名称')
    return
  }
  try {
    await saveSettings({ ...form, siteName: form.siteName.trim() })
    ElMessage.success('设置已保存')
    document.title = `${form.siteName}管理后台`
  } catch (error) {
    ElMessage.error((error as ApiResponse<unknown>).message || '保存失败')
  }
}

async function reset() {
  try {
    await ElMessageBox.confirm('确定恢复默认设置？', '重置设置', { type: 'warning' })
  } catch {
    return
  }
  try {
    await resetSettings()
    ElMessage.success('已恢复默认')
    await load()
  } catch (error) {
    ElMessage.error((error as ApiResponse<unknown>).message || '重置失败')
  }
}
</script>

<template>
  <section class="panel" v-loading="loading">
    <h3>基本信息设置</h3>
    <el-form label-position="top" :disabled="!canEdit">
      <el-form-item label="平台名称" required>
        <el-input v-model="form.siteName" maxlength="50" />
      </el-form-item>
      <el-form-item label="平台描述">
        <el-input v-model="form.siteDescription" type="textarea" maxlength="500" :rows="4" />
      </el-form-item>
      <el-form-item label="Logo（256×256，JPG/PNG）">
        <div class="upload">
          <img v-if="form.logoUrl" :src="form.logoUrl" alt="Logo" />
          <el-upload v-if="canEdit" :show-file-list="false" accept="image/png,image/jpeg" :http-request="(options: UploadRequestOptions) => upload('logo', options)">
            <el-button :icon="Upload">上传 Logo</el-button>
          </el-upload>
        </div>
      </el-form-item>
      <el-form-item label="默认头像（JPG/PNG）">
        <div class="upload">
          <img v-if="form.defaultAvatar" :src="form.defaultAvatar" alt="默认头像" />
          <el-upload v-if="canEdit" :show-file-list="false" accept="image/png,image/jpeg" :http-request="(options: UploadRequestOptions) => upload('avatar', options)">
            <el-button :icon="Upload">上传头像</el-button>
          </el-upload>
        </div>
      </el-form-item>
    </el-form>
    <div v-if="canEdit" class="ops">
      <el-button :icon="RefreshLeft" @click="reset">重置</el-button>
      <el-button type="primary" :icon="Check" @click="save">保存设置</el-button>
    </div>
  </section>
</template>

<style scoped>
.panel { background: #fff; border-radius: 12px; padding: 20px; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06); max-width: 720px; }
h3 { margin-top: 0; }
.upload { display: flex; align-items: center; gap: 16px; }
img { width: 64px; height: 64px; object-fit: cover; border-radius: 8px; border: 1px solid #e4e7ed; }
.ops { display: flex; justify-content: flex-end; gap: 12px; }
</style>
