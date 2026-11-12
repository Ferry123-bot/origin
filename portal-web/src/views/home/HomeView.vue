<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  fetchHotStages,
  fetchLearningOverview,
  fetchPlatformStats,
  finishGuide,
  type HotStage,
  type LearningOverview,
  type PlatformStats,
  type TrendPoint,
} from '@/api/home'
import type { ApiResponse } from '@/types/api'
import { useUserStore } from '@/store/user'

const GUEST_GUIDE_KEY = 'yszx_portal_guide_dismissed'
const palette = ['#667eea', '#f093fb', '#4facfe', '#43e97b', '#fa709a', '#fee140', '#a8edea', '#d299c2']

const router = useRouter()
const user = useUserStore()
const stages = ref<HotStage[]>([])
const stagesReady = ref(false)
const stagesFailed = ref(false)
const overview = ref<LearningOverview | null>(null)
const guideVisible = ref(false)
const shown = reactive({ questionCount: 0, studentCount: 0, interviewCount: 0, goodRate: 0 })
let frame = 0

const week = computed(() => overview.value?.weekTrend ?? emptyWeek())
const todayCount = computed(() => overview.value?.todayCount ?? 0)
const learning = computed(() => overview.value?.learningStats ?? {
  consecutiveDays: 0,
  accuracyRate: 0,
  totalPracticeCount: 0,
})
const maxCount = computed(() => Math.max(1, ...week.value.map((item) => item.count)))

onMounted(() => {
  void load()
})

onUnmounted(() => {
  cancelAnimationFrame(frame)
  document.body.style.overflow = ''
})

watch(guideVisible, (open) => {
  document.body.style.overflow = open ? 'hidden' : ''
})

async function load() {
  const tasks = [
    fetchPlatformStats()
      .then((response) => animate(response.data.data))
      .catch(() => undefined),
    fetchHotStages()
      .then((response) => {
        stages.value = response.data.data
      })
      .catch(() => {
        stages.value = []
        stagesFailed.value = true
      })
      .finally(() => {
        stagesReady.value = true
      }),
  ]
  if (user.token) {
    tasks.push(
      fetchLearningOverview()
        .then((response) => {
          overview.value = response.data.data
          guideVisible.value = !response.data.data.guideFinished
        })
        .catch(() => undefined),
    )
  } else {
    guideVisible.value = localStorage.getItem(GUEST_GUIDE_KEY) !== '1'
  }
  await Promise.all(tasks)
}

function animate(target: PlatformStats) {
  const from = { ...shown }
  const start = performance.now()
  cancelAnimationFrame(frame)
  const step = (now: number) => {
    const progress = Math.min((now - start) / 1200, 1)
    const eased = 1 - (1 - progress) ** 3
    shown.questionCount = Math.round(from.questionCount + (target.questionCount - from.questionCount) * eased)
    shown.studentCount = Math.round(from.studentCount + (target.studentCount - from.studentCount) * eased)
    shown.interviewCount = Math.round(from.interviewCount + (target.interviewCount - from.interviewCount) * eased)
    shown.goodRate = Math.round(from.goodRate + (target.goodRate - from.goodRate) * eased)
    if (progress < 1) frame = requestAnimationFrame(step)
  }
  frame = requestAnimationFrame(step)
}

function emptyWeek(): TrendPoint[] {
  const today = new Date()
  return Array.from({ length: 7 }, (_, index) => {
    const date = new Date(today)
    date.setDate(today.getDate() - (6 - index))
    const month = String(date.getMonth() + 1).padStart(2, '0')
    const day = String(date.getDate()).padStart(2, '0')
    return { date: `${date.getFullYear()}-${month}-${day}`, count: 0 }
  })
}

function formatCount(value: number) {
  return Math.round(value).toLocaleString('zh-CN')
}

function dayLabel(date: string) {
  const parts = date.split('-')
  return parts.length === 3 ? `${parts[1]}-${parts[2]}` : date
}

function barHeight(count: number) {
  return `${Math.max((count / maxCount.value) * 100, 6)}%`
}

function stageBackground(index: number) {
  const color = palette[index % palette.length]
  return { background: `linear-gradient(135deg, ${color}20 0%, ${color}10 100%)` }
}

function openStage(stage: HotStage) {
  router.push({ name: 'questions', query: { subjectId: stage.subjectId, stageId: stage.stageId } })
}

async function dismissGuide() {
  guideVisible.value = false
  if (!user.token) {
    localStorage.setItem(GUEST_GUIDE_KEY, '1')
    return
  }
  try {
    await finishGuide()
  } catch (error) {
    ElMessage.error((error as ApiResponse<unknown>).message || '引导状态保存失败')
  }
}

function openAdmin() {
  window.location.href = 'http://localhost:5174/admin/'
}
</script>

<template>
  <div class="home">
    <section class="hero">
      <div class="hero-content container">
        <h1>AI赋能的智能面试训练平台</h1>
        <p>海量题库 × AI面试官 × 智能分析，助你轻松拿Offer</p>
        <div class="hero-actions">
          <router-link class="hero-btn ghost" to="/questions">海量题库</router-link>
          <router-link class="hero-btn solid" :to="{ name: 'interview-config' }">开始AI面试</router-link>
        </div>
        <div class="hero-stats">
          <div>
            <strong>{{ formatCount(shown.questionCount) }}</strong>
            <span>精选面试题</span>
          </div>
          <div>
            <strong>{{ formatCount(shown.studentCount) }}</strong>
            <span>注册学员</span>
          </div>
          <div>
            <strong>{{ formatCount(shown.interviewCount) }}</strong>
            <span>AI面试次数</span>
          </div>
          <div>
            <strong>{{ formatCount(shown.goodRate) }}%</strong>
            <span>好评率</span>
          </div>
        </div>
      </div>
    </section>

    <section class="quick">
      <div class="container bar">
        <router-link class="qs random" :to="{ name: 'practice', query: { mode: 'random', count: '10' } }">
          <span class="qs-icon">🎲</span>
          <span>
            <strong>随机练习10题</strong>
            <em>随机抽取题目，检验知识储备</em>
          </span>
        </router-link>
        <router-link class="qs ai" :to="{ name: 'interview-config' }">
          <span class="qs-icon">🤖</span>
          <span>
            <strong>开始AI面试</strong>
            <em>AI面试官模拟真实面试场景</em>
          </span>
        </router-link>
        <router-link class="qs resume" :to="{ name: 'practice', query: { continue: '1' } }">
          <span class="qs-icon">▶️</span>
          <span>
            <strong>继续上次学习</strong>
            <em>回到上次练习的位置继续刷题</em>
          </span>
        </router-link>
        <router-link class="qs route" :to="{ name: 'routes' }">
          <span class="qs-icon">🗺️</span>
          <span>
            <strong>规划学习路线</strong>
            <em>根据方向定制专属学习路径</em>
          </span>
        </router-link>
      </div>
    </section>

    <section class="block">
      <div class="container">
        <header class="title">
          <h2>核心功能</h2>
          <p>全方位助力你的面试准备</p>
        </header>
        <div class="features">
          <router-link class="feature" to="/questions">
            <span class="feature-icon">📚</span>
            <h3>海量题库</h3>
            <p>覆盖Java、Python、前端、数据库等主流技术栈，持续更新</p>
          </router-link>
          <router-link class="feature" :to="{ name: 'interview-config' }">
            <span class="feature-icon">🤖</span>
            <h3>AI模拟面试</h3>
            <p>真实模拟面试场景，AI面试官实时评分点评</p>
          </router-link>
          <router-link class="feature" :to="{ name: 'practice' }">
            <span class="feature-icon">✏️</span>
            <h3>刷题练习</h3>
            <p>顺序刷题、随机刷题、模拟考试，多种模式</p>
          </router-link>
          <router-link class="feature" :to="{ name: 'routes' }">
            <span class="feature-icon">🗺️</span>
            <h3>学习路线</h3>
            <p>9大学科方向，精选B站核心课程，从入门到就业体系化学习</p>
          </router-link>
        </div>
      </div>
    </section>

    <section class="continue">
      <div class="container">
        <header class="title">
          <h2>继续学习</h2>
          <p>坚持每日练习，持续进步</p>
        </header>
        <div class="continue-grid">
          <article class="card">
            <div class="card-head">
              <h3>📊 学习统计</h3>
              <router-link v-if="user.token" class="card-link" :to="{ name: 'profile' }">查看全部 →</router-link>
              <router-link v-else class="card-link" to="/login">登录后查看 →</router-link>
            </div>
            <div class="badges">
              <div>
                <span>🔥</span>
                <strong class="streak">{{ learning.consecutiveDays }}</strong>
                <em>天连续学习</em>
              </div>
              <div>
                <span>🎯</span>
                <strong class="accuracy">{{ learning.accuracyRate }}</strong>
                <em>% 正确率</em>
              </div>
              <div>
                <span>📝</span>
                <strong class="total">{{ learning.totalPracticeCount }}</strong>
                <em>总练习数</em>
              </div>
            </div>
          </article>
          <article class="card">
            <div class="card-head">
              <h3>📈 近7天练习</h3>
              <span class="today">今日已刷 <b>{{ todayCount }}</b> 题</span>
            </div>
            <div class="bars">
              <div v-for="(item, index) in week" :key="item.date" class="bar-item" :class="{ today: index === week.length - 1 }">
                <span class="bar-value">{{ item.count }}</span>
                <i :style="{ height: barHeight(item.count) }"></i>
                <span class="bar-label">{{ dayLabel(item.date) }}</span>
              </div>
            </div>
          </article>
        </div>
      </div>
    </section>

    <section class="block stages">
      <div class="container">
        <header class="title">
          <h2>热门阶段</h2>
          <p>选择你的技术方向，开始学习</p>
        </header>
        <p v-if="stagesReady && stagesFailed" class="empty">热门阶段暂时无法加载</p>
        <p v-else-if="stagesReady && stages.length === 0" class="empty">暂无阶段，题目正在录入中</p>
        <div v-else class="categories">
          <button v-for="(stage, index) in stages" :key="stage.stageId" type="button" class="category" @click="openStage(stage)">
            <span class="category-icon" :style="stageBackground(index)">{{ stage.icon || '📘' }}</span>
            <span>
              <strong>{{ stage.stageName }}</strong>
              <em>{{ stage.questionCount }} 道题目</em>
            </span>
          </button>
        </div>
      </div>
    </section>

    <section class="ai-section">
      <div class="container ai-content">
        <div class="ai-info">
          <span class="ai-badge">🤖 AI驱动</span>
          <h2>AI模拟面试官</h2>
          <p>基于RAG知识库和大语言模型，打造真实的面试体验。AI面试官会根据你的回答实时调整问题难度，并给出专业的评分和建议。</p>
          <div class="ai-features">
            <span><i>✓</i>智能出题，千人千面</span>
            <span><i>✓</i>实时评分，即时反馈</span>
            <span><i>✓</i>面试报告，精准分析</span>
            <span><i>✓</i>海量知识，持续更新</span>
          </div>
          <router-link class="hero-btn solid" :to="{ name: 'interview-config' }">立即体验</router-link>
        </div>
        <div class="demo">
          <div class="demo-head">
            <span class="demo-avatar">🤖</span>
            <span>
              <strong>AI面试官</strong>
              <em>正在面试中...</em>
            </span>
          </div>
          <div class="chat">
            <p class="ai">你好，我是你的AI面试官。今天我们来进行一场AI应用开发的面试，请问你有多少年的AI应用开发经验？</p>
            <p class="user">我有2年的AI应用开发经验，主要做RAG检索增强和Agent智能体项目。</p>
            <p class="ai">很好。那请问你对Spring的IoC和DI有什么理解？能举例说明吗？</p>
          </div>
          <form class="demo-input" @submit.prevent="router.push({ name: 'interview-config' })">
            <input type="text" readonly placeholder="输入你的回答..." />
            <button type="submit" aria-label="前往AI面试">➤</button>
          </form>
        </div>
      </div>
    </section>

    <footer class="footer">
      <div class="container">
        <div class="footer-grid">
          <div class="brand-col">
            <h3><span class="brand-mark" aria-hidden="true"></span>云枢智学</h3>
            <p>AI赋能的智能面试训练平台，助力每一位开发者拿到心仪的Offer。</p>
          </div>
          <div>
            <h4>产品</h4>
            <router-link to="/routes">学习路线</router-link>
            <router-link to="/questions">题库</router-link>
            <router-link :to="{ name: 'interview-config' }">AI面试</router-link>
          </div>
          <div>
            <h4>支持</h4>
            <a href="#" @click.prevent>帮助中心</a>
            <a href="#" @click.prevent>意见反馈</a>
            <a href="#" @click.prevent>联系我们</a>
          </div>
          <div>
            <h4>关于</h4>
            <a href="#" @click.prevent>关于我们</a>
            <a href="#" @click.prevent>用户协议</a>
            <a href="#" @click.prevent>隐私政策</a>
          </div>
        </div>
        <div class="footer-bottom">
          <p>&copy; 2026 云枢智学 版权所有 | 京ICP备XXXXXXXX号</p>
          <button type="button" class="admin-entry" @click="openAdmin">⚙ 管理后台</button>
        </div>
      </div>
    </footer>

    <div v-if="guideVisible" class="overlay">
      <div class="modal" role="dialog" aria-labelledby="guide-title">
        <div class="welcome">🚀</div>
        <h2 id="guide-title">欢迎来到云枢智学</h2>
        <p class="subtitle">三步开启你的面试备战之旅</p>
        <div class="dots" aria-hidden="true"><i class="active"></i><i></i><i></i></div>
        <ol>
          <li>
            <b>1</b>
            <span class="step-icon">📂</span>
            <span>
              <strong>选择技术方向</strong>
              <em>在热门阶段中选择你要准备的技术栈，Java、前端、Python等任你挑选</em>
            </span>
          </li>
          <li>
            <b>2</b>
            <span class="step-icon">✏️</span>
            <span>
              <strong>开始刷题练习</strong>
              <em>顺序刷题、随机刷题、模拟考试多种模式，AI智能推荐提升效率</em>
            </span>
          </li>
          <li>
            <b>3</b>
            <span class="step-icon">🤖</span>
            <span>
              <strong>AI面试检验</strong>
              <em>AI面试官模拟真实面试，智能评分点评，精准定位知识薄弱点</em>
            </span>
          </li>
        </ol>
        <button type="button" class="start" @click="dismissGuide">开始学习</button>
        <button type="button" class="skip" @click="dismissGuide">跳过引导</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.home, .home * { box-sizing: border-box; }
.container { width: min(1200px, calc(100% - 40px)); margin: 0 auto; }
.title { text-align: center; margin-bottom: 48px; }
.title h2 { margin: 0 0 12px; font-size: 32px; }
.title p, .empty { margin: 0; color: #909399; font-size: 16px; }
.empty { text-align: center; }

.hero {
  position: relative;
  overflow: hidden;
  padding: 80px 0;
  text-align: center;
  color: #fff;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.hero::before {
  content: "";
  position: absolute;
  inset: -50%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.1) 0%, transparent 60%);
  animation: pulse 8s ease-in-out infinite;
}
.hero-content { position: relative; z-index: 1; }
.hero h1 { margin: 0 0 16px; font-size: 48px; animation: rise 0.8s ease; }
.hero-content > p { margin: 0 0 40px; font-size: 20px; opacity: 0.9; }
.hero-actions, .hero-stats { display: flex; justify-content: center; }
.hero-actions { gap: 16px; }
.hero-btn {
  padding: 16px 32px;
  border-radius: 30px;
  font-size: 16px;
  font-weight: 600;
}
.hero-btn.solid { background: #fff; color: #667eea; }
.hero-btn.ghost { color: #fff; border: 2px solid #fff; background: rgba(255, 255, 255, 0.2); }
.hero-btn.solid:hover { transform: translateY(-3px); box-shadow: 0 8px 24px rgba(0, 0, 0, 0.2); }
.hero-stats { gap: 60px; margin-top: 60px; }
.hero-stats strong { display: block; font-size: 42px; }
.hero-stats span { font-size: 14px; opacity: 0.8; }

.quick { padding: 40px 0; background: #fff; border-bottom: 1px solid #e4e7ed; }
.bar { display: flex; gap: 20px; }
.qs {
  position: relative;
  overflow: hidden;
  flex: 1;
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 28px 24px;
  border-radius: 16px;
  color: #fff;
}
.qs::after {
  content: "";
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, rgba(255, 255, 255, 0.15), transparent);
  transition: left 0.5s ease;
}
.qs:hover { transform: translateY(-4px); box-shadow: 0 12px 32px rgba(0, 0, 0, 0.18); }
.qs:hover::after { left: 100%; }
.qs strong, .qs em { display: block; }
.qs strong { font-size: 17px; }
.qs em { margin-top: 4px; font-style: normal; font-size: 13px; opacity: 0.85; }
.qs-icon {
  width: 56px;
  height: 56px;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 14px;
  font-size: 28px;
  background: rgba(255, 255, 255, 0.2);
}
.random { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); }
.ai { background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%); }
.resume { background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%); }
.route { background: linear-gradient(135deg, #43e97b 0%, #38f9d7 100%); }

.block { padding: 80px 0; }
.features, .categories { display: grid; grid-template-columns: repeat(4, 1fr); gap: 24px; }
.categories { gap: 20px; }
.feature, .category, .card {
  background: #fff;
  border-radius: 16px;
  transition: 0.3s ease;
}
.feature { display: block; padding: 32px 24px; text-align: center; color: inherit; }
.feature:hover, .category:hover { transform: translateY(-8px); box-shadow: 0 12px 32px rgba(0, 0, 0, 0.1); }
.category:hover { transform: translateX(8px); }
.feature-icon, .category-icon {
  display: flex;
  align-items: center;
  justify-content: center;
}
.feature-icon {
  width: 72px;
  height: 72px;
  margin: 0 auto 20px;
  border-radius: 20px;
  font-size: 36px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.feature h3 { margin: 0 0 10px; font-size: 18px; }
.feature p { margin: 0; color: #909399; font-size: 14px; line-height: 1.6; }

.continue { padding: 60px 0; background: #f5f7fa; }
.continue-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 24px; }
.card { padding: 28px; }
.card:hover { box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1); }
.card-head, .badges { display: flex; }
.card-head { align-items: center; justify-content: space-between; margin-bottom: 20px; }
.card-head h3 { margin: 0; font-size: 18px; }
.card-link { color: #409eff; font-size: 13px; }
.today { color: #909399; font-size: 13px; }
.today b { color: #409eff; }
.badges { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
.badges > div { padding: 20px 12px; text-align: center; border-radius: 12px; background: #f5f7fa; }
.badges span { display: block; font-size: 28px; }
.badges strong { display: block; font-size: 24px; }
.badges em { display: block; margin-top: 4px; color: #909399; font-size: 12px; font-style: normal; }
.streak { color: #f5576c; }
.accuracy { color: #67c23a; }
.total { color: #409eff; }
.bars {
  height: 140px;
  display: flex;
  align-items: flex-end;
  gap: 12px;
  padding-bottom: 28px;
  border-bottom: 1px solid #e4e7ed;
}
.bar-item { position: relative; flex: 1; height: 100%; display: flex; flex-direction: column; align-items: center; justify-content: flex-end; }
.bar-item i {
  width: 100%;
  max-width: 36px;
  display: block;
  border-radius: 6px 6px 0 0;
  background: linear-gradient(180deg, #667eea 0%, #764ba2 100%);
}
.bar-item.today i { background: linear-gradient(180deg, #f093fb 0%, #f5576c 100%); }
.bar-value, .bar-label { color: #909399; font-size: 11px; font-style: normal; }
.bar-item.today .bar-value { color: #f5576c; font-weight: 600; }
.bar-value { margin-bottom: 6px; font-weight: 600; }
.bar-label { position: absolute; bottom: -24px; white-space: nowrap; }

.stages { background: #f8f9fe; }
.category {
  display: flex;
  align-items: center;
  gap: 16px;
  width: 100%;
  padding: 24px;
  border: 0;
  text-align: left;
  cursor: pointer;
  border-radius: 12px;
}
.category-icon { width: 56px; height: 56px; flex: none; border-radius: 14px; font-size: 28px; }
.category strong, .category em { display: block; }
.category strong { font-size: 16px; }
.category em { margin-top: 4px; color: #909399; font-size: 13px; font-style: normal; }

.ai-section { padding: 80px 0; color: #fff; background: linear-gradient(135deg, #1a1a2e 0%, #16213e 100%); }
.ai-content { display: flex; align-items: center; gap: 60px; }
.ai-info, .demo { flex: 1; }
.ai-badge {
  display: inline-block;
  margin-bottom: 20px;
  padding: 6px 16px;
  border-radius: 20px;
  font-size: 13px;
  background: rgba(255, 255, 255, 0.1);
}
.ai-info h2 { margin: 0 0 16px; font-size: 36px; }
.ai-info > p { margin: 0 0 32px; font-size: 16px; line-height: 1.8; opacity: 0.8; }
.ai-features { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 32px; }
.ai-features span { display: flex; align-items: center; gap: 10px; font-size: 14px; }
.ai-features i {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  font-style: normal;
  background: rgba(103, 194, 58, 0.2);
}
.demo { padding: 32px; border-radius: 20px; border: 1px solid rgba(255, 255, 255, 0.1); background: rgba(255, 255, 255, 0.05); }
.demo-head, .demo-input { display: flex; align-items: center; }
.demo-head { gap: 12px; margin-bottom: 24px; }
.demo-avatar {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  font-size: 24px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.demo-head strong, .demo-head em { display: block; }
.demo-head em { font-size: 12px; font-style: normal; opacity: 0.7; }
.chat { display: flex; flex-direction: column; gap: 16px; }
.chat p { max-width: 80%; margin: 0; padding: 12px 16px; border-radius: 16px; font-size: 14px; line-height: 1.6; }
.chat .ai { background: rgba(103, 194, 58, 0.2); border-bottom-left-radius: 4px; }
.chat .user { align-self: flex-end; background: rgba(64, 158, 255, 0.3); border-bottom-right-radius: 4px; }
.demo-input { gap: 12px; margin-top: 20px; }
.demo-input input {
  flex: 1;
  padding: 12px 16px;
  border: 0;
  border-radius: 24px;
  color: #fff;
  background: rgba(255, 255, 255, 0.1);
}
.demo-input input::placeholder { color: rgba(255, 255, 255, 0.5); }
.demo-input button {
  width: 44px;
  height: 44px;
  border: 0;
  border-radius: 50%;
  color: #fff;
  background: #409eff;
  cursor: pointer;
}

.footer { padding: 60px 0 30px; color: rgba(255, 255, 255, 0.7); background: #1a1a2e; }
.footer-grid { display: grid; grid-template-columns: 2fr 1fr 1fr 1fr; gap: 40px; margin-bottom: 40px; }
.brand-col h3 { display: flex; align-items: center; gap: 10px; margin: 0 0 16px; color: #fff; font-size: 24px; }
.brand-col .brand-mark { font-size: 24px; }
.brand-col p { margin: 0; font-size: 14px; line-height: 1.8; }
.footer h4 { margin: 0 0 20px; color: #fff; font-size: 16px; }
.footer-grid a { display: block; margin-bottom: 12px; color: rgba(255, 255, 255, 0.6); font-size: 14px; }
.footer-grid a:hover { color: #fff; }
.footer-bottom { padding-top: 30px; border-top: 1px solid rgba(255, 255, 255, 0.1); text-align: center; font-size: 13px; }
.footer-bottom p { margin: 0; }
.admin-entry {
  margin-top: 6px;
  border: 0;
  color: #909399;
  background: transparent;
  opacity: 0.5;
  cursor: pointer;
}
.admin-entry:hover { opacity: 1; }

.overlay {
  position: fixed;
  inset: 0;
  z-index: 9999;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.65);
}
.modal { width: min(520px, 90%); max-height: calc(100vh - 48px); overflow: auto; padding: 40px 36px; border-radius: 20px; text-align: center; background: #fff; }
.welcome { font-size: 56px; }
.modal h2 { margin: 16px 0 8px; font-size: 24px; }
.subtitle { margin: 0 0 24px; color: #909399; }
.dots { display: flex; justify-content: center; gap: 8px; margin-bottom: 24px; }
.dots i { width: 8px; height: 8px; border-radius: 50%; background: #dcdfe6; }
.dots .active { width: 24px; border-radius: 4px; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); }
.modal ol { display: flex; flex-direction: column; gap: 16px; margin: 0 0 32px; padding: 0; list-style: none; text-align: left; }
.modal li { display: flex; align-items: flex-start; gap: 16px; padding: 16px 20px; border-radius: 12px; background: #f5f7fa; }
.modal b {
  width: 32px;
  height: 32px;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: #fff;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.step-icon { font-size: 28px; }
.modal li strong, .modal li em { display: block; }
.modal li em { margin-top: 2px; color: #909399; font-size: 13px; font-style: normal; }
.start {
  padding: 14px 48px;
  border: 0;
  border-radius: 30px;
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.start:hover { transform: translateY(-3px); box-shadow: 0 8px 24px rgba(102, 126, 234, 0.4); }
.skip { display: block; margin: 16px auto 0; border: 0; color: #909399; text-decoration: underline; background: transparent; cursor: pointer; }

@keyframes pulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.1); }
}
@keyframes rise {
  from { opacity: 0; transform: translateY(30px); }
  to { opacity: 1; transform: translateY(0); }
}

@media (max-width: 1024px) {
  .features, .categories, .footer-grid { grid-template-columns: 1fr 1fr; }
  .continue-grid, .ai-content, .bar { grid-template-columns: 1fr; flex-direction: column; }
}
@media (max-width: 768px) {
  .hero h1 { font-size: 32px; }
  .hero-stats { flex-direction: column; gap: 24px; }
  .features, .categories, .footer-grid { grid-template-columns: 1fr; }
  .modal { padding: 28px 20px; }
}
</style>
