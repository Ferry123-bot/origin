<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { resetPassword, sendSms, verifyReset } from '@/api/auth'
import type { ApiResponse } from '@/types/api'
import { useSmsCountdown } from '@/composables/useSmsCountdown'

const form = reactive({ mobile: '', code: '', password: '', confirm: '' })
const step = ref(1)
const resetToken = ref('')
const loading = ref(false)
const sending = ref(false)
const devCode = ref('')
const router = useRouter()
const { seconds, start } = useSmsCountdown()

async function requestCode() {
  sending.value = true
  try {
    const response = await sendSms(form.mobile.trim(), 'reset')
    start(60)
    devCode.value = response.data.data.debugCode || ''
    ElMessage.success('验证码已发送')
  } catch (error) {
    const body = error as ApiResponse<unknown>
    ElMessage.error(body.message || '验证码发送失败')
  } finally {
    sending.value = false
  }
}

async function verify() {
  loading.value = true
  try {
    const response = await verifyReset(form.mobile.trim(), form.code.trim())
    resetToken.value = response.data.data.resetToken
    step.value = 2
  } catch (error) {
    const body = error as ApiResponse<unknown>
    ElMessage.error(body.message || '验证失败')
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (form.password !== form.confirm) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  loading.value = true
  try {
    await resetPassword(resetToken.value, form.password)
    step.value = 3
  } catch (error) {
    const body = error as ApiResponse<unknown>
    ElMessage.error(body.message || '重置失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth">
    <el-card class="card">
      <h2>找回密码</h2>
      <el-form v-if="step === 1" label-position="top" @submit.prevent="verify">
        <el-form-item label="手机号">
          <el-input v-model="form.mobile" maxlength="11" />
        </el-form-item>
        <el-form-item label="验证码">
          <div class="code-row">
            <el-input v-model="form.code" maxlength="6" />
            <el-button :disabled="seconds > 0 || sending" @click="requestCode">
              {{ seconds > 0 ? `${seconds}s` : '获取验证码' }}
            </el-button>
          </div>
        </el-form-item>
        <p v-if="devCode" class="dev-code">开发环境验证码：{{ devCode }}</p>
        <el-button type="primary" native-type="submit" :loading="loading" style="width: 100%">下一步</el-button>
      </el-form>
      <el-form v-else-if="step === 2" label-position="top" @submit.prevent="submit">
        <el-form-item label="新密码">
          <el-input v-model="form.password" type="password" show-password placeholder="6~20 位，不能包含空格" />
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input v-model="form.confirm" type="password" show-password />
        </el-form-item>
        <el-button type="primary" native-type="submit" :loading="loading" style="width: 100%">重置密码</el-button>
      </el-form>
      <div v-else>
        <p>密码已重置，请使用新密码登录。</p>
        <el-button type="primary" style="width: 100%" @click="router.push('/login')">立即登录</el-button>
      </div>
      <router-link v-if="step !== 3" class="back" to="/login">返回登录</router-link>
    </el-card>
  </div>
</template>

<style scoped>
.auth { min-height: 100vh; display: grid; place-items: center; }
.card { width: 420px; }
.code-row { display: flex; gap: 8px; width: 100%; }
.code-row .el-input { flex: 1; }
.dev-code { margin: 0 0 12px; color: #e6a23c; font-size: 13px; }
.back { display: inline-block; margin-top: 16px; }
</style>
