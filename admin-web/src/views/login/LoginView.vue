<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Back, Key, Right } from '@element-plus/icons-vue'
import { login, saveSession } from '@/api/auth'
import type { ApiResponse } from '@/types/api'

const form = reactive({ account: '', password: '', remember: true })
const loading = ref(false)
const error = ref('')
const router = useRouter()
const route = useRoute()

async function submit() {
  error.value = ''
  if (!form.account.trim()) {
    error.value = '请输入管理员账号'
    return
  }
  if (!form.password) {
    error.value = '请输入登录密码'
    return
  }
  loading.value = true
  try {
    const response = await login(form.account.trim(), form.password)
    saveSession(response.data.data, form.remember)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard'
    await router.push(redirect)
  } catch (err) {
    const body = err as ApiResponse<unknown>
    error.value = body.message || '账号或密码错误，请重试'
  } finally {
    loading.value = false
  }
}

function forgot() {
  ElMessage.info('请联系超级管理员重置密码')
}
</script>

<template>
  <div class="login">
    <section class="brand">
      <div class="brand-content">
        <div class="brand-logo"><span class="brand-mark" aria-hidden="true" /></div>
        <h1>云枢智学</h1>
        <p>AI版 · 管理后台</p>
        <div class="features">
          <div><span>📚</span>题库管理 · 面试题库 · 学科管理</div>
          <div><span>👥</span>用户管理 · 角色权限 · 操作日志</div>
          <div><span>📊</span>数据看板 · 系统配置 · AI模型管理</div>
        </div>
      </div>
    </section>
    <section class="form-section">
      <form class="form" @submit.prevent="submit">
        <header>
          <h2>管理员登录</h2>
          <p>请输入管理员账号和密码登录后台</p>
        </header>
        <div v-if="error" class="error">⚠️ {{ error }}</div>
        <label>
          管理员账号
          <span class="field">
            <i>👤</i>
            <input v-model="form.account" autocomplete="username" placeholder="请输入管理员账号" />
          </span>
        </label>
        <label>
          登录密码
          <span class="field">
            <i>🔒</i>
            <input v-model="form.password" type="password" autocomplete="current-password" placeholder="请输入密码" />
          </span>
        </label>
        <div class="options">
          <label class="remember"><input v-model="form.remember" type="checkbox" />记住登录状态</label>
          <button type="button" class="link" @click="forgot"><Key />忘记密码？</button>
        </div>
        <button class="submit" type="submit" :disabled="loading"><Right v-if="!loading" />{{ loading ? '登录中…' : '登 录' }}</button>
        <a class="back" href="http://localhost:5173/"><Back />返回学员前台</a>
      </form>
    </section>
  </div>
</template>

<style scoped>
.login {
  min-height: 100vh;
  display: flex;
  background: #1d1e2c;
}

.brand {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 60px;
  background: linear-gradient(135deg, #1d1e2c 0%, #2d2e4c 100%);
  position: relative;
  overflow: hidden;
}

.brand::before {
  content: '';
  position: absolute;
  top: -50%;
  left: -50%;
  width: 200%;
  height: 200%;
  background: radial-gradient(circle, rgba(102, 126, 234, 0.08) 0%, transparent 60%);
}

.brand-content {
  position: relative;
  z-index: 1;
  text-align: center;
  color: #fff;
}

.brand-logo {
  width: 100px;
  height: 100px;
  margin: 0 auto 24px;
  border-radius: 24px;
  display: grid;
  place-items: center;
  font-size: 48px;
  background: linear-gradient(135deg, #667eea, #764ba2);
  box-shadow: 0 8px 32px rgba(102, 126, 234, 0.3);
}

.brand-content h1 {
  margin: 0 0 8px;
  font-size: 28px;
}

.brand-content > p {
  margin: 0 0 40px;
  opacity: 0.7;
}

.features {
  display: flex;
  flex-direction: column;
  gap: 16px;
  text-align: left;
  max-width: 280px;
  margin: 0 auto;
}

.features div {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
  opacity: 0.8;
}

.features span {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  background: rgba(255, 255, 255, 0.1);
  flex: none;
}

.form-section {
  width: 460px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
  background: #fff;
}

.form {
  width: 100%;
  max-width: 360px;
}

.form header {
  margin-bottom: 32px;
}

.form h2 {
  margin: 0 0 8px;
  font-size: 24px;
}

.form header p,
.back {
  color: #909399;
  font-size: 14px;
}

.form label {
  display: block;
  margin-bottom: 20px;
  font-size: 14px;
  font-weight: 500;
}

.field {
  margin-top: 8px;
  display: block;
  position: relative;
}

.field i {
  position: absolute;
  left: 14px;
  top: 50%;
  transform: translateY(-50%);
  font-style: normal;
}

.field input {
  width: 100%;
  height: 44px;
  padding: 0 16px 0 44px;
  border: 1px solid #dcdfe6;
  border-radius: 10px;
  background: #f5f7fa;
  font-size: 14px;
  box-sizing: border-box;
}

.field input:focus {
  outline: none;
  border-color: #667eea;
  background: #fff;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
}

.options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}

.remember {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-size: 13px;
  font-weight: 400;
  color: #606266;
}

.remember input {
  accent-color: #667eea;
}

.link,
.back,
.submit {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.link :deep(svg),
.back :deep(svg),
.submit :deep(svg) {
  width: 14px;
  height: 14px;
}

.link,
.back {
  border: 0;
  background: transparent;
  color: #667eea;
  cursor: pointer;
  font-size: 13px;
  text-decoration: none;
}

.back {
  display: flex;
  margin-top: 20px;
  color: #909399;
}

.back:hover,
.link:hover {
  color: #667eea;
}

.submit {
  width: 100%;
  height: 44px;
  border: 0;
  border-radius: 10px;
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  background: linear-gradient(135deg, #667eea, #764ba2);
}

.submit:hover {
  box-shadow: 0 6px 20px rgba(102, 126, 234, 0.4);
}

.submit:disabled {
  cursor: wait;
  opacity: 0.7;
}

.error {
  margin-bottom: 16px;
  padding: 12px 16px;
  border-radius: 8px;
  color: #f56c6c;
  background: rgba(245, 108, 108, 0.1);
  border: 1px solid rgba(245, 108, 108, 0.3);
  font-size: 13px;
}

@media (max-width: 900px) {
  .brand { display: none; }
  .form-section { width: 100%; }
}
</style>
