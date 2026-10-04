<template>
  <div class="home">
    <!-- 问候 -->
    <header class="home__hero">
      <h1 class="home__greet">{{ greeting }}，{{ displayName }}</h1>
      <p class="home__date">{{ todayText }}</p>
    </header>

    <!-- 快捷录入：最高频动作，放最显眼 -->
    <section class="quick">
      <el-input
        v-model="draft"
        type="textarea"
        :rows="3"
        resize="none"
        placeholder="记一条…（Ctrl + Enter 保存）"
        @keydown.ctrl.enter="save"
        @keydown.meta.enter="save"
      />
      <div class="quick__actions">
        <span class="quick__hint">随手记，不用想结构，之后再归类</span>
        <el-button type="primary" :loading="saving" :disabled="!draft.trim()" @click="save">
          记下来
        </el-button>
      </div>
    </section>

    <!-- 概览 -->
    <section class="stats">
      <div class="stat">
        <div class="stat__num">{{ topicCount }}</div>
        <div class="stat__label">知识点</div>
      </div>
      <div class="stat">
        <div class="stat__num">{{ categories.length }}</div>
        <div class="stat__label">目录</div>
      </div>
      <div class="stat">
        <div class="stat__num">{{ uncategorizedCount }}</div>
        <div class="stat__label">待归类</div>
      </div>
      <div class="stat">
        <div class="stat__num">{{ todayCount }}</div>
        <div class="stat__label">今日新增</div>
      </div>
    </section>

    <!-- 今日待复习：能力还没做，先把位置和调性占住 -->
    <section class="panel">
      <div class="panel__head">
        <span class="panel__title">今日待复习</span>
        <span class="panel__extra">0 条</span>
      </div>
      <div class="review-empty">
        <el-icon class="review-empty__icon"><Clock /></el-icon>
        <span>复习计划还没开启 —— 目录整理好之后就能按目录抽取知识点安排复习</span>
      </div>
    </section>

    <!-- 最近记录 -->
    <section class="panel">
      <div class="panel__head">
        <span class="panel__title">最近记录</span>
        <RouterLink to="/app/learn" class="panel__more">全部 →</RouterLink>
      </div>
      <ul v-if="recentTopics.length" class="recent">
        <li
          v-for="t in recentTopics"
          :key="t.id"
          class="recent__item"
          @click="goDetail(t)"
        >
          <span class="recent__title">{{ t.title || '未命名' }}</span>
          <span class="recent__cat">{{ getCategoryName(t.categoryId) }}</span>
        </li>
      </ul>
      <p v-else class="recent__empty">还没有记录，从上面记第一条吧</p>
    </section>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, RouterLink } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Clock } from '@element-plus/icons-vue'
import { bootstrapSession } from '../../utils/session'
import {
  ensureTopicsLoaded, ensureCategoriesLoaded, getTopics, getCategories,
  getUncategorizedCount, getCategoryName, createTopic
} from './useCorecraftWebStore'

const router = useRouter()

const draft = ref('')
const saving = ref(false)
const user = ref({})

const topics = computed(() => getTopics())
const categories = computed(() => getCategories())
const topicCount = computed(() => topics.value.length)
const uncategorizedCount = computed(() => getUncategorizedCount())
const recentTopics = computed(() => topics.value.slice(0, 6))

const displayName = computed(() => user.value.nickname || user.value.username || '')

const todayCount = computed(() => {
  const today = new Date().toDateString()
  return topics.value.filter((t) => String(t.createdAt || '').slice(0, 10) === today.slice(0, 10)).length
})

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 11) return '早上好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const todayText = computed(() =>
  new Date().toLocaleDateString('zh-CN', { month: 'long', day: 'numeric', weekday: 'long' })
)

async function save() {
  const content = draft.value.trim()
  if (!content || saving.value) return
  saving.value = true
  try {
    await createTopic({ title: '', content, tags: [] })
    draft.value = ''
    ElMessage.success('已记下')
  } finally {
    saving.value = false
  }
}

function goDetail(t) {
  router.push({ name: 'AppTopicDetail', params: { id: t.id } })
}

onMounted(async () => {
  // 共享 inflight：AppLayout 已经拉过会话时，这里不会再打一次接口
  const [s] = await Promise.all([
    bootstrapSession(),
    ensureTopicsLoaded(),
    ensureCategoriesLoaded()
  ])
  user.value = s.profile
})
</script>

<style scoped>
.home {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.home__hero {
  padding: 8px 0 4px;
}
.home__greet {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: #1f2329;
  letter-spacing: -0.2px;
}
.home__date {
  margin: 6px 0 0;
  font-size: 13px;
  color: #a0a5ad;
}

/* 快捷录入：整块可点，视觉上就是"这里能写" */
.quick {
  background: #fff;
  border: 1px solid #eef0f3;
  border-radius: 12px;
  padding: 14px 16px 12px;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.quick:focus-within {
  border-color: #a8ceff;
  box-shadow: 0 2px 12px rgba(43, 127, 255, 0.08);
}
.quick :deep(.el-textarea__inner) {
  border: none;
  box-shadow: none;
  padding: 0;
  font-size: 15px;
  line-height: 1.7;
  color: #1f2329;
  background: transparent;
}
.quick__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid #f4f6f8;
}
.quick__hint {
  font-size: 12px;
  color: #b6bac1;
}

/* 概览 */
.stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}
.stat {
  background: #fff;
  border: 1px solid #eef0f3;
  border-radius: 12px;
  padding: 16px 18px;
}
.stat__num {
  font-size: 26px;
  font-weight: 600;
  color: #1f2329;
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
}
.stat__label {
  font-size: 12px;
  color: #a0a5ad;
  margin-top: 4px;
}

/* 通用面板 */
.panel {
  background: #fff;
  border: 1px solid #eef0f3;
  border-radius: 12px;
  padding: 14px 18px 6px;
}
.panel__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 6px;
}
.panel__title {
  font-size: 14px;
  font-weight: 600;
  color: #1f2329;
}
.panel__extra {
  font-size: 12px;
  color: #a0a5ad;
}
.panel__more {
  font-size: 12px;
  color: #8a9099;
  text-decoration: none;
}
.panel__more:hover {
  color: #2b7fff;
}

.review-empty {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 0 16px;
  font-size: 13px;
  color: #b6bac1;
}
.review-empty__icon {
  font-size: 15px;
}

.recent {
  list-style: none;
  margin: 0;
  padding: 0;
}
.recent__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid #f7f8fa;
  cursor: pointer;
}
.recent__item:last-child {
  border-bottom: none;
}
.recent__item:hover .recent__title {
  color: #2b7fff;
}
.recent__title {
  font-size: 14px;
  color: #1f2329;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color 0.15s;
}
.recent__cat {
  font-size: 12px;
  color: #b6bac1;
  flex-shrink: 0;
}
.recent__empty {
  margin: 0;
  padding: 12px 0 18px;
  font-size: 13px;
  color: #b6bac1;
}
</style>
