<template>
  <div class="home">
    <div class="welcome">
      <span class="welcome__eyebrow">管理后台</span>
      <h1 class="welcome__title">欢迎回来{{ userName ? `，${userName}` : '' }}</h1>
      <p class="welcome__sub">在这里配置菜单、角色与用户</p>
    </div>

    <!-- 入口由当前用户的菜单配置生成，不在前端硬编码菜单清单 -->
    <div v-if="entries.length" class="entries">
      <RouterLink v-for="e in entries" :key="e.id" :to="e.path" class="entry">
        <span class="entry__icon">
          <el-icon><component :is="getIconComponent(e.icon) || Setting" /></el-icon>
        </span>
        <span class="entry__name">{{ e.name }}</span>
        <el-icon class="entry__arrow"><ArrowRight /></el-icon>
      </RouterLink>
    </div>
    <p v-else class="empty">当前账号没有可用的后台菜单</p>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { RouterLink } from 'vue-router'
import { ArrowRight, Setting } from '@element-plus/icons-vue'
import { flattenShellNav, getIconComponent } from '../utils/menu'
import { bootstrapSession } from '../utils/session'

const session = ref({ menuTree: [], menuPaths: [], profile: {} })

const userName = computed(() => session.value.profile?.nickname || session.value.profile?.username || '')

// 去掉首页自身（path '/'），其余后台菜单平铺成入口卡片
const entries = computed(() =>
  flattenShellNav('admin', session.value.menuTree, session.value.menuPaths)
    .filter((m) => m.path !== '/')
)

onMounted(async () => {
  // Layout 已在加载会话；这里复用同一份快照（bootstrapSession 内部共享），不会再打一次接口
  session.value = await bootstrapSession()
})
</script>

<style scoped>
.home {
  display: flex;
  flex-direction: column;
  gap: 32px;
  max-width: 880px;
}

.welcome__eyebrow {
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 1px;
  color: var(--cc-text-3);
}
.welcome__title {
  margin: 8px 0 0;
  font-size: 26px;
  font-weight: 600;
  color: var(--cc-text-1);
  letter-spacing: 0.2px;
}
.welcome__sub {
  margin: 6px 0 0;
  font-size: 14px;
  color: var(--cc-text-2);
}

.entries {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 12px;
}

.entry {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: var(--cc-surface);
  border: 1px solid var(--cc-border);
  border-radius: var(--cc-radius);
  text-decoration: none;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.entry:hover {
  border-color: var(--cc-primary);
  box-shadow: 0 2px 10px rgba(43, 127, 255, 0.1);
}
.entry__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  flex-shrink: 0;
  border-radius: var(--cc-radius-sm);
  background: var(--cc-primary-soft);
  color: var(--cc-primary);
  font-size: 16px;
}
.entry__name {
  flex: 1;
  min-width: 0;
  font-size: 14px;
  font-weight: 500;
  color: var(--cc-text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.entry__arrow {
  flex-shrink: 0;
  color: var(--cc-text-4);
  transition: color 0.15s, transform 0.15s;
}
.entry:hover .entry__arrow {
  color: var(--cc-primary);
  transform: translateX(2px);
}

.empty {
  margin: 0;
  font-size: 14px;
  color: var(--cc-text-3);
}
</style>
