<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { changePassword, fetchProfile, updateProfile, uploadAvatar, type Profile } from '@/api/profile'
import AvatarCropper from '@/components/AvatarCropper.vue'
import type { ApiResponse } from '@/types/api'
import { useUserStore } from '@/store/user'

const router = useRouter()
const user = useUserStore()
const loading = ref(false)
const saving = ref(false)
const uploading = ref(false)
const changing = ref(false)
const profile = ref<Profile | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const cropUrl = ref('')
const cropMime = ref('image/jpeg')
const form = reactive({ nickname: '', email: '', gender: 0, avatar: '' })
const password = reactive({ oldPassword: '', nextPassword: '', confirm: '' })

const initial = computed(() => {
  const name = form.nickname.trim() || profile.value?.nickname || '学'
  return name.slice(0, 1)
})

onMounted(load)

async function load() {
  loading.value = true
  try {
    const response = await fetchProfile()
    profile.value = response.data.data
    form.nickname = profile.value.nickname
    form.email = profile.value.email || ''
    form.gender = profile.value.gender ?? 0
    form.avatar = profile.value.avatar || ''
    user.setNickname(profile.value.nickname)
    user.setAvatar(form.avatar)
  } catch (error) {
    ElMessage.error(messageOf(error, '加载个人资料失败'))
  } finally {
    loading.value = false
  }
}

async function save() {
  const nickname = form.nickname.trim()
  if (!nickname || nickname.length > 20) {
    ElMessage.warning('用户名需为 1~20 个字符')
    return
  }
  if (form.email.trim() && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) {
    ElMessage.warning('邮箱格式不正确')
    return
  }
  saving.value = true
  try {
    await updateProfile({
      nickname,
      email: form.email.trim(),
      gender: form.gender,
      avatar: form.avatar || null,
    })
    user.setNickname(nickname)
    ElMessage.success('个人信息保存成功')
    await load()
  } catch (error) {
    ElMessage.error(messageOf(error, '保存失败'))
  } finally {
    saving.value = false
  }
}

function closeCrop() {
  if (cropUrl.value) URL.revokeObjectURL(cropUrl.value)
  cropUrl.value = ''
}

function onFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (!['image/jpeg', 'image/png'].includes(file.type)) {
    ElMessage.warning('只支持 JPG 或 PNG')
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.warning('图片不能超过 10MB')
    return
  }
  closeCrop()
  cropMime.value = file.type
  cropUrl.value = URL.createObjectURL(file)
}

async function confirmCrop(file: File) {
  uploading.value = true
  try {
    const response = await uploadAvatar(file)
    form.avatar = response.data.data.url
    closeCrop()
    ElMessage.success('头像已裁剪，保存修改后生效')
  } catch (error) {
    ElMessage.error(messageOf(error, '头像上传失败'))
  } finally {
    uploading.value = false
  }
}

async function savePassword() {
  if (!password.oldPassword || !/^\S{6,20}$/.test(password.nextPassword)) {
    ElMessage.warning('新密码需为 6~20 位且不能包含空格')
    return
  }
  if (password.nextPassword !== password.confirm) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  changing.value = true
  try {
    await changePassword(password.oldPassword, password.nextPassword)
    user.clear()
    ElMessage.success('密码已更新，请重新登录')
    await router.push('/login')
  } catch (error) {
    ElMessage.error(messageOf(error, '修改密码失败'))
  } finally {
    changing.value = false
  }
}

function messageOf(error: unknown, fallback: string) {
  return (error as ApiResponse<unknown>)?.message || fallback
}
</script>

<template>
  <div v-loading="loading" class="profile">
    <header class="banner">
      <div class="container banner-inner">
        <div>
          <h1>个人中心</h1>
          <p>管理你的个人信息和账户设置</p>
        </div>
        <nav>
          <router-link to="/">首页</router-link>
          <span>/</span>
          <span>个人中心</span>
        </nav>
      </div>
    </header>
    <div v-if="profile" class="container body">
      <aside class="card user">
        <img v-if="form.avatar" :src="form.avatar" alt="" />
        <div v-else class="letter">{{ initial }}</div>
        <h2>{{ form.nickname || profile.nickname }}</h2>
        <em>普通学员</em>
        <div class="stats">
          <div><strong>{{ profile.stats.practiceCount }}</strong><span>做题数</span></div>
          <div><strong>{{ profile.stats.interviewCount }}</strong><span>面试数</span></div>
          <div><strong>{{ profile.stats.wrongQuestionCount }}</strong><span>错题数</span></div>
        </div>
      </aside>
      <div class="main">
        <section class="card">
          <h3>基本信息</h3>
          <div class="avatar-row">
            <img v-if="form.avatar" :src="form.avatar" alt="" />
            <div v-else class="letter">{{ initial }}</div>
            <div>
              <strong>修改头像</strong>
              <p>支持 JPG、PNG，选择后可裁剪再保存</p>
              <button type="button" :disabled="uploading" @click="fileInput?.click()">{{ uploading ? '上传中...' : '上传头像' }}</button>
              <input ref="fileInput" type="file" accept="image/jpeg,image/png" hidden @change="onFile" />
            </div>
          </div>
          <div class="grid">
            <label>用户名<input v-model="form.nickname" maxlength="20" /></label>
            <label>手机号<input :value="profile.mobile" readonly /></label>
            <label>邮箱<input v-model="form.email" type="email" placeholder="选填" /></label>
            <label>性别
              <select v-model.number="form.gender">
                <option :value="1">男</option>
                <option :value="2">女</option>
                <option :value="0">保密</option>
              </select>
            </label>
          </div>
          <div class="actions"><button type="button" class="primary" :disabled="saving" @click="save">{{ saving ? '保存中...' : '保存修改' }}</button></div>
        </section>
        <section class="card">
          <h3>学习数据</h3>
          <div class="learn">
            <div><span>📚</span><strong>{{ profile.stats.practiceCount }}</strong><em>已完成题目</em></div>
            <div><span>✅</span><strong>{{ profile.study.accuracyRate }}%</strong><em>正确率</em></div>
            <div><span>🤖</span><strong>{{ profile.stats.interviewCount }}</strong><em>AI面试次数</em></div>
          </div>
        </section>
        <section class="card">
          <h3>修改密码</h3>
          <div class="grid password">
            <label>原密码<input v-model="password.oldPassword" type="password" autocomplete="current-password" /></label>
            <label>新密码<input v-model="password.nextPassword" type="password" placeholder="6~20 位，不能包含空格" autocomplete="new-password" /></label>
            <label>确认新密码<input v-model="password.confirm" type="password" autocomplete="new-password" /></label>
          </div>
          <div class="actions"><button type="button" class="primary" :disabled="changing" @click="savePassword">{{ changing ? '提交中...' : '确认修改' }}</button></div>
        </section>
      </div>
    </div>
    <AvatarCropper v-if="cropUrl" :src="cropUrl" :mime="cropMime" :busy="uploading" @cancel="closeCrop" @confirm="confirmCrop" />
  </div>
</template>

<style scoped>
.profile, .profile * { box-sizing: border-box; }
.container { width: min(1200px, calc(100% - 40px)); margin: 0 auto; }
.banner { padding: 36px 0; color: #fff; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); }
.banner-inner { display: flex; align-items: center; justify-content: space-between; }
.banner h1 { margin: 0 0 8px; font-size: 28px; }
.banner p { margin: 0; opacity: 0.9; }
.banner nav { display: flex; gap: 8px; font-size: 13px; opacity: 0.85; }
.banner a { color: #fff; }
.body { display: grid; grid-template-columns: 280px 1fr; gap: 20px; margin-top: -28px; padding-bottom: 32px; }
.card { padding: 24px; border-radius: 12px; background: #fff; box-shadow: 0 8px 24px rgba(31, 45, 61, 0.06); }
.user { text-align: center; }
.user img, .letter { width: 88px; height: 88px; border-radius: 50%; object-fit: cover; }
.letter { display: grid; place-items: center; margin: 0 auto; color: #fff; font-size: 32px; font-weight: 700; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); }
.user h2 { margin: 16px 0 8px; font-size: 20px; }
.user em { display: inline-block; padding: 2px 10px; border-radius: 12px; color: #409eff; font-style: normal; font-size: 12px; background: #ecf5ff; }
.stats, .learn { display: grid; grid-template-columns: repeat(3, 1fr); }
.stats { margin-top: 20px; }
.stats strong, .learn strong { display: block; font-size: 22px; }
.stats span, .learn em { color: #909399; font-size: 12px; font-style: normal; }
.main { display: flex; flex-direction: column; gap: 16px; }
.main h3 { margin: 0 0 20px; font-size: 16px; }
.avatar-row { display: flex; align-items: center; gap: 16px; margin-bottom: 20px; }
.avatar-row img, .avatar-row .letter { width: 72px; height: 72px; font-size: 28px; }
.avatar-row p { margin: 6px 0 10px; color: #909399; font-size: 13px; }
.avatar-row button, .primary { height: 34px; padding: 0 14px; border: 1px solid #dcdfe6; border-radius: 6px; background: #fff; cursor: pointer; }
.primary { border: 0; color: #fff; background: linear-gradient(135deg, #409eff, #337ecc); }
.primary:disabled, .avatar-row button:disabled { opacity: 0.7; cursor: not-allowed; }
.grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
label { display: flex; flex-direction: column; gap: 8px; color: #606266; font-size: 14px; }
input, select { height: 40px; padding: 0 12px; border: 1px solid #dcdfe6; border-radius: 8px; font-size: 14px; }
input:focus, select:focus { outline: none; border-color: #409eff; }
input[readonly] { color: #909399; background: #f5f7fa; }
.actions { margin-top: 20px; text-align: right; }
.learn { gap: 12px; }
.learn div { padding: 20px 8px; text-align: center; border-radius: 10px; background: #f5f7fa; }
.learn span { font-size: 24px; }
.password { grid-template-columns: 1fr; }
@media (max-width: 900px) {
  .body, .grid, .banner-inner { grid-template-columns: 1fr; display: grid; }
  .banner-inner { gap: 12px; }
}
</style>
