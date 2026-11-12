<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { register, saveSession, sendSms } from '@/api/auth'
import type { ApiResponse } from '@/types/api'
import { useSmsCountdown } from '@/composables/useSmsCountdown'

const form = reactive({ mobile: '', code: '', password: '', confirm: '', agreed: false })
const loading = ref(false)
const sending = ref(false)
const devCode = ref('')
const router = useRouter()
const { seconds, start } = useSmsCountdown()

const strength = computed(() => {
  const password = form.password
  if (!password) return 0
  let score = 0
  if (password.length >= 6) score += 1
  if (/[a-z]/.test(password) && /[A-Z]/.test(password)) score += 1
  if (/[0-9]/.test(password) && /[^a-zA-Z0-9]/.test(password)) score += 1
  return score
})

const strengthText = computed(() => ['', '弱', '中', '强'][strength.value])

async function requestCode() {
  if (!/^1[3-9]\d{9}$/.test(form.mobile.trim())) {
    ElMessage.warning('请输入正确的手机号')
    return
  }
  sending.value = true
  try {
    const response = await sendSms(form.mobile.trim(), 'register')
    start(60)
    devCode.value = response.data.data.debugCode || ''
    ElMessage.success('验证码已发送')
  } catch (error) {
    ElMessage.error((error as ApiResponse<unknown>).message || '验证码发送失败')
  } finally {
    sending.value = false
  }
}

async function submit() {
  if (!/^1[3-9]\d{9}$/.test(form.mobile.trim()) || !/^\d{6}$/.test(form.code.trim())) {
    ElMessage.warning('请输入正确的手机号和 6 位验证码')
    return
  }
  if (!/^\S{6,20}$/.test(form.password)) {
    ElMessage.warning('密码需为 6~20 位且不能包含空格')
    return
  }
  if (form.password !== form.confirm) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  if (!form.agreed) {
    ElMessage.warning('请先阅读并同意用户协议和隐私政策')
    return
  }
  loading.value = true
  try {
    const response = await register(form.mobile.trim(), form.code.trim(), form.password)
    saveSession(response.data.data)
    ElMessage.success('注册成功')
    await router.push('/profile')
  } catch (error) {
    ElMessage.error((error as ApiResponse<unknown>).message || '注册失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth">
    <section class="brand">
      <div class="logo"><span class="brand-mark" aria-hidden="true"></span></div>
      <h1>云枢智学</h1>
      <p>加入我们，开启面试训练之旅</p>
      <ol>
        <li class="on"><b>1</b><span><strong>创建账号</strong><em>填写手机号，设置密码</em></span></li>
        <li><b>2</b><span><strong>完善信息</strong><em>选择技术方向，定制学习计划</em></span></li>
        <li><b>3</b><span><strong>开始训练</strong><em>刷题练习，AI模拟面试</em></span></li>
      </ol>
    </section>
    <section class="panel">
      <div class="box">
        <header>
          <h2>创建新账号</h2>
          <p>注册即可获得AI面试训练资格</p>
        </header>
        <form @submit.prevent="submit">
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
          <label>设置密码</label>
          <input v-model="form.password" type="password" placeholder="请设置密码（6-20位）" autocomplete="new-password" />
          <div class="bars" :class="strength === 1 ? 'weak' : strength === 2 ? 'mid' : strength === 3 ? 'strong' : ''">
            <i></i><i></i><i></i>
            <em v-if="strengthText">{{ strengthText }}</em>
          </div>
          <label>确认密码</label>
          <input v-model="form.confirm" type="password" placeholder="请再次输入密码" autocomplete="new-password" />
          <label class="agree">
            <input v-model="form.agreed" type="checkbox" />
            <span>我已阅读并同意 <a href="#" @click.prevent>《用户协议》</a> 和 <a href="#" @click.prevent>《隐私政策》</a></span>
          </label>
          <button class="submit" type="submit" :disabled="loading">{{ loading ? '注册中...' : '立即注册' }}</button>
        </form>
        <p class="switch">已有账号？<router-link to="/login">立即登录</router-link></p>
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
ol { display: flex; flex-direction: column; gap: 24px; max-width: 300px; margin: 0; padding: 0; list-style: none; }
li { display: flex; align-items: center; gap: 16px; opacity: 0.7; }
li.on { opacity: 1; }
li b { width: 40px; height: 40px; flex: none; display: grid; place-items: center; border-radius: 50%; background: rgba(255, 255, 255, 0.2); }
li.on b { color: #667eea; background: #fff; }
li strong, li em { display: block; }
li em { margin-top: 4px; font-size: 13px; font-style: normal; opacity: 0.8; }
.panel { width: 520px; display: flex; align-items: center; justify-content: center; padding: 40px; background: #fff; }
.box { width: 100%; max-width: 400px; }
header { margin-bottom: 24px; }
header h2 { margin: 0 0 8px; font-size: 28px; }
header p, .switch { color: #909399; }
form label { display: block; margin: 16px 0 8px; color: #606266; font-weight: 500; }
input { width: 100%; height: 42px; padding: 0 14px; border: 1px solid #dcdfe6; border-radius: 8px; font-size: 14px; }
input:focus { outline: none; border-color: #409eff; }
.code { display: flex; gap: 10px; }
.code input { flex: 1; }
.code button, .submit { border: 0; cursor: pointer; }
.code button { padding: 0 16px; border: 1px solid #dcdfe6; border-radius: 8px; color: #409eff; background: #f5f7fa; white-space: nowrap; }
.code button:disabled { color: #c0c4cc; cursor: not-allowed; }
.bars { display: flex; align-items: center; gap: 4px; margin-top: 8px; }
.bars i { flex: 1; height: 4px; border-radius: 2px; background: #e4e7ed; }
.bars em { margin-left: 8px; font-style: normal; font-size: 12px; color: #909399; }
.bars.weak i:first-child { background: #f56c6c; }
.bars.mid i:nth-child(-n + 2) { background: #e6a23c; }
.bars.strong i { background: #67c23a; }
.bars.weak em { color: #f56c6c; }
.bars.mid em { color: #e6a23c; }
.bars.strong em { color: #67c23a; }
.dev { margin: 8px 0 0; color: #e6a23c; font-size: 13px; }
.agree { display: flex; align-items: flex-start; gap: 8px; margin: 20px 0; color: #909399; font-size: 13px; font-weight: 400; }
.agree input { width: 16px; height: 16px; margin-top: 2px; }
.agree a { color: #409eff; }
.submit { width: 100%; height: 46px; border-radius: 8px; color: #fff; font-size: 16px; background: linear-gradient(135deg, #409eff, #337ecc); }
.submit:disabled { opacity: 0.7; cursor: not-allowed; }
.switch { margin: 20px 0 0; text-align: center; }
.switch a { color: #409eff; font-weight: 600; }
@media (max-width: 900px) {
  .brand { display: none; }
  .panel { width: 100%; }
}
</style>
