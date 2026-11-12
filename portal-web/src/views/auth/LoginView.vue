<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { loginByPassword, loginBySms, saveSession, sendSms } from '@/api/auth'
import type { ApiResponse } from '@/types/api'
import { useSmsCountdown } from '@/composables/useSmsCountdown'

const form = reactive({ account: '', password: '', mobile: '', code: '', remember: true })
const loading = ref(false)
const sending = ref(false)
const devCode = ref('')
const tab = ref<'password' | 'sms'>('password')
const router = useRouter()
const route = useRoute()
const { seconds, start } = useSmsCountdown()

function redirectAfter() {
  const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
  return router.push(redirect)
}

async function submitPassword() {
  if (!form.account.trim() || !form.password) {
    ElMessage.warning('请输入手机号/邮箱和密码')
    return
  }
  loading.value = true
  try {
    const response = await loginByPassword(form.account.trim(), form.password)
    saveSession(response.data.data, form.remember)
    await redirectAfter()
  } catch (error) {
    ElMessage.error((error as ApiResponse<unknown>).message || '登录失败')
  } finally {
    loading.value = false
  }
}

async function submitSms() {
  if (!/^1[3-9]\d{9}$/.test(form.mobile.trim()) || !/^\d{6}$/.test(form.code.trim())) {
    ElMessage.warning('请输入正确的手机号和 6 位验证码')
    return
  }
  loading.value = true
  try {
    const response = await loginBySms(form.mobile.trim(), form.code.trim())
    saveSession(response.data.data, true)
    await redirectAfter()
  } catch (error) {
    ElMessage.error((error as ApiResponse<unknown>).message || '登录失败')
  } finally {
    loading.value = false
  }
}

async function requestCode() {
  if (!/^1[3-9]\d{9}$/.test(form.mobile.trim())) {
    ElMessage.warning('请输入正确的手机号')
    return
  }
  sending.value = true
  try {
    const response = await sendSms(form.mobile.trim(), 'login')
    start(60)
    devCode.value = response.data.data.debugCode || ''
    ElMessage.success('验证码已发送')
  } catch (error) {
    ElMessage.error((error as ApiResponse<unknown>).message || '验证码发送失败')
  } finally {
    sending.value = false
  }
}

function social() {
  ElMessage.info('第三方登录暂未开放')
}
</script>

<template>
  <div class="auth">
    <section class="brand">
      <div class="logo"><span class="brand-mark" aria-hidden="true"></span></div>
      <h1>云枢智学</h1>
      <p>AI赋能的智能面试训练平台</p>
      <div class="features">
        <div><span>📚</span><em>海量题库</em></div>
        <div><span>🤖</span><em>AI面试官</em></div>
        <div><span>📊</span><em>智能分析</em></div>
        <div><span>🎯</span><em>精准推荐</em></div>
      </div>
    </section>
    <section class="panel">
      <div class="box">
        <header>
          <h2>欢迎回来</h2>
          <p>登录你的账户继续学习</p>
        </header>
        <div class="tabs">
          <button type="button" :class="{ on: tab === 'password' }" @click="tab = 'password'">账号密码登录</button>
          <button type="button" :class="{ on: tab === 'sms' }" @click="tab = 'sms'">手机验证码登录</button>
        </div>
        <form v-if="tab === 'password'" @submit.prevent="submitPassword">
          <label>手机号/邮箱</label>
          <input v-model="form.account" placeholder="请输入手机号或邮箱" autocomplete="username" />
          <label>密码</label>
          <input v-model="form.password" type="password" placeholder="请输入密码" autocomplete="current-password" />
          <div class="options">
            <label class="remember"><input v-model="form.remember" type="checkbox" /> 记住我</label>
            <router-link to="/forgot-password">忘记密码？</router-link>
          </div>
          <button class="submit" type="submit" :disabled="loading">{{ loading ? '登录中...' : '登 录' }}</button>
        </form>
        <form v-else @submit.prevent="submitSms">
          <label>手机号</label>
          <input v-model="form.mobile" maxlength="11" placeholder="请输入手机号" autocomplete="tel" />
          <label>验证码</label>
          <div class="code">
            <input v-model="form.code" maxlength="6" placeholder="请输入验证码" />
            <button type="button" :disabled="seconds > 0 || sending" @click="requestCode">
              {{ seconds > 0 ? `${seconds}s后重试` : '获取验证码' }}
            </button>
          </div>
          <p v-if="devCode" class="dev">开发环境验证码：{{ devCode }}</p>
          <button class="submit" type="submit" :disabled="loading">{{ loading ? '登录中...' : '登 录' }}</button>
        </form>
        <div class="divider"><span>其他登录方式</span></div>
        <div class="social">
          <button type="button" title="微信登录" @click="social">💬</button>
          <button type="button" title="QQ登录" @click="social">🐧</button>
          <button type="button" title="GitHub登录" @click="social">🐱</button>
        </div>
        <p class="switch">还没有账号？<router-link to="/register">立即注册</router-link></p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.auth, .auth * { box-sizing: border-box; }
.auth { min-height: 100vh; display: flex; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); }
.brand { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 40px; color: #fff; }
.logo { width: 120px; height: 120px; display: grid; place-items: center; margin-bottom: 24px; border-radius: 24px; background: #fff; box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2); }
.logo .brand-mark { font-size: 64px; }
.brand h1 { margin: 0 0 12px; font-size: 36px; }
.brand > p { margin: 0 0 40px; font-size: 18px; opacity: 0.9; }
.features { display: flex; gap: 32px; }
.features div { text-align: center; }
.features span { width: 60px; height: 60px; display: flex; align-items: center; justify-content: center; margin: 0 auto 12px; border-radius: 16px; font-size: 28px; background: rgba(255, 255, 255, 0.2); }
.features em { font-style: normal; font-size: 14px; opacity: 0.9; }
.panel { width: 480px; display: flex; align-items: center; justify-content: center; padding: 40px; background: #fff; }
.box { width: 100%; max-width: 360px; }
header { margin-bottom: 32px; }
header h2 { margin: 0 0 8px; font-size: 28px; }
header p, .switch { color: #909399; }
.tabs { display: flex; margin-bottom: 28px; border-bottom: 2px solid #e4e7ed; }
.tabs button { flex: 1; padding: 12px 0; border: 0; color: #909399; font-size: 16px; background: transparent; cursor: pointer; }
.tabs button.on { position: relative; color: #409eff; font-weight: 600; }
.tabs button.on::after { content: ""; position: absolute; left: 0; right: 0; bottom: -2px; height: 2px; background: #409eff; }
form label { display: block; margin: 16px 0 8px; color: #606266; font-weight: 500; }
input { width: 100%; height: 42px; padding: 0 14px; border: 1px solid #dcdfe6; border-radius: 8px; font-size: 14px; }
input:focus { outline: none; border-color: #409eff; }
.options { display: flex; justify-content: space-between; align-items: center; margin: 16px 0 20px; font-size: 14px; }
.remember { display: flex; align-items: center; gap: 6px; margin: 0; color: #606266; font-weight: 400; }
.remember input { width: 16px; height: 16px; }
.options a { color: #409eff; }
.code { display: flex; gap: 10px; }
.code input { flex: 1; }
.code button, .submit, .social button { border: 0; cursor: pointer; }
.code button { padding: 0 16px; border: 1px solid #dcdfe6; border-radius: 8px; color: #409eff; background: #f5f7fa; white-space: nowrap; }
.code button:disabled { color: #c0c4cc; cursor: not-allowed; }
.submit { width: 100%; height: 46px; margin-top: 20px; border-radius: 8px; color: #fff; font-size: 16px; background: linear-gradient(135deg, #409eff, #337ecc); }
.submit:disabled { opacity: 0.7; cursor: not-allowed; }
.dev { margin: 8px 0 0; color: #e6a23c; font-size: 13px; }
.divider { display: flex; align-items: center; margin: 24px 0; color: #909399; font-size: 13px; }
.divider::before, .divider::after { content: ""; flex: 1; height: 1px; background: #e4e7ed; }
.divider span { padding: 0 16px; }
.social { display: flex; justify-content: center; gap: 20px; }
.social button { width: 48px; height: 48px; border: 1px solid #dcdfe6; border-radius: 50%; font-size: 22px; background: #fff; }
.social button:hover { border-color: #409eff; background: rgba(64, 158, 255, 0.05); }
.switch { margin: 24px 0 0; text-align: center; }
.switch a { color: #409eff; font-weight: 600; }
@media (max-width: 900px) {
  .brand { display: none; }
  .panel { width: 100%; }
}
</style>
