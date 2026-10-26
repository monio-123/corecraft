<template>
  <div class="app-container">
    <!-- 顶栏 -->
    <header class="header">
      <RouterLink to="/" class="brand">
        <span class="brand__logo">CC</span>
        <span class="brand__name">Corecraft</span>
      </RouterLink>
      <div class="nav-menu">
        <el-menu :default-active="activeMenu" mode="horizontal" router class="admin-menu">
          <!-- 首页 -->
          <el-menu-item index="/">
            <el-icon><Icons.House /></el-icon>
            <span>首页</span>
          </el-menu-item>

          <!-- 动态菜单 -->
          <template v-for="group in visibleMenuTree" :key="group.id">
            <!-- GROUP 类型：子菜单 -->
            <el-sub-menu v-if="group.type === 'GROUP' && group.children?.length" :index="group.code">
              <template #title>
                <el-icon v-if="getIconComponent(parseMeta(group.meta).icon)">
                  <component :is="getIconComponent(parseMeta(group.meta).icon)" />
                </el-icon>
                <span>{{ group.name }}</span>
              </template>
              <el-menu-item
                v-for="child in group.children"
                :key="child.id"
                :index="parseMeta(child.meta).path"
              >
                <el-icon v-if="getIconComponent(parseMeta(child.meta).icon)">
                  <component :is="getIconComponent(parseMeta(child.meta).icon)" />
                </el-icon>
                <span>{{ child.name }}</span>
              </el-menu-item>
            </el-sub-menu>

            <!-- MENU 类型：直接菜单项 -->
            <el-menu-item v-else-if="parseMeta(group.meta).path" :index="parseMeta(group.meta).path">
              <el-icon v-if="getIconComponent(parseMeta(group.meta).icon)">
                <component :is="getIconComponent(parseMeta(group.meta).icon)" />
              </el-icon>
              <span>{{ group.name }}</span>
            </el-menu-item>
          </template>
        </el-menu>
      </div>
      <div class="user-info">
        <el-tooltip v-if="hasAppShell" content="进入知识库" placement="bottom">
          <button class="icon-btn" @click="goApp">
            <el-icon><Reading /></el-icon>
          </button>
        </el-tooltip>
        <el-tooltip :content="tabsVisible ? '隐藏标签栏' : '显示标签栏'" placement="bottom">
          <button class="icon-btn" @click="tabsVisible = !tabsVisible">
            <el-icon><component :is="tabsVisible ? ArrowDown : ArrowUp" /></el-icon>
          </button>
        </el-tooltip>
        <!-- 用户菜单：**两个壳共用这一份**（见 components/UserMenu.vue）。
             菜单项 + 个人信息/改密码两个弹窗 + 退出登录（确认框、清登录态、跳登录页）
             都在那边，这里只接"退出之后清什么"（这个壳的页面标签页）。 -->
        <UserMenu
          class="user-menu"
          :user="currentUser"
          :size="28"
          @logout="onSignedOut"
          @password-changed="onSignedOut"
        />
      </div>
    </header>

    <!-- 页面标签页：可拖拽排序 + 关闭 + keep-alive。只剩 1 个时没意义，不显示 -->
    <div class="tabs-row" v-if="tabs.length > 1 && tabsVisible">
      <div ref="tabsContainerRef" class="page-tabs-shell">
        <div class="page-tabs">
          <div
            v-for="tab in tabs"
            :key="tab.name"
            class="page-tab"
            :class="{
              'is-active': activeTab === tab.name,
              'is-placeholder': draggingTabName === tab.name
            }"
            :data-tab-name="tab.name"
            :style="getTabStyle(tab.name)"
            @click="handleTabClick(tab.name)"
            @mousedown.left="handleTabMouseDown($event, tab.name)"
          >
            <span class="page-tab__title">{{ tab.title }}</span>
            <el-icon
              v-if="tab.name !== 'Home'"
              class="page-tab__close"
              @mousedown.stop
              @click.stop="removeTab(tab.name)"
            >
              <Close />
            </el-icon>
          </div>
        </div>
        <div
          v-if="draggingTab"
          class="page-tab page-tab--ghost"
          :class="{ 'is-active': activeTab === draggingTab.name }"
          :style="ghostTabStyle"
        >
          <span class="page-tab__title">{{ draggingTab.title }}</span>
          <el-icon v-if="draggingTab.name !== 'Home'" class="page-tab__close">
            <Close />
          </el-icon>
        </div>
      </div>
    </div>

    <!-- 主内容区 -->
    <main class="main-content">
      <div class="page-wrap">
        <router-view v-slot="{ Component }">
          <!--
            不写 :key：keep-alive 按 component.name（SFC 文件名推断）做缓存 key。
            之前 :key 跟 route.name 强绑定 → 路由一切换 key 就变 → 缓存实例被销毁重建 → 状态全丢。
            当前路由表每个 component name 唯一，没有"同 component 不同 params"复用场景（业务页都 1:1）。
          -->
          <keep-alive :include="cachedTabNames">
            <component :is="Component" />
          </keep-alive>
        </router-view>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, computed, watch, reactive, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter, RouterLink } from 'vue-router'
import * as Icons from '@element-plus/icons-vue'
import { ArrowDown, ArrowUp, Reading } from '@element-plus/icons-vue'
import { getMenuPaths, getMenuTree } from '../utils/auth'
import { parseMeta, getIconComponent, buildShellMenuTree } from '../utils/menu'
import { bootstrapSession, hasAppMenus, sessionProfile } from '../utils/session'
import UserMenu from './UserMenu.vue'

const route = useRoute()
const router = useRouter()
// 当前用户资料：**会话里那一份**（utils/session.js，两个壳共用）。
// 以前这个壳自己存一份 reactive 的 currentUser，改完资料还得写回自己那份 ——
// 那是第二份资料，改个人资料这件事也就得写两遍。
const currentUser = sessionProfile
const allowedMenuPaths = ref(getMenuPaths())
const menuTree = ref(getMenuTree())

// 页面标签页数据
const tabs = ref([
  { name: 'Home', title: '首页', path: '/' }
])

// 当前激活的标签页
const activeTab = ref('Home')
// tabs 区可见性：用户可手动隐藏给工作区更多空间。默认 true（保持原有体验）
const tabsVisible = ref(true)
const tabsContainerRef = ref(null)
const draggingTabName = ref('')
const dragPreviewIndex = ref(-1)
const dragGhostLeft = ref(0)
const dragGhostTop = ref(0)
const draggedTabWidth = ref(0)
const draggedTabHeight = ref(0)
const suppressTabClick = ref(false)
const TAB_DRAG_HOLD_DELAY = 120
let dragHoldTimer = null
let pressedTabName = ''
let dragPointerOffsetX = 0
let lastPointerX = 0

// 菜单解析（parseMeta / getIconComponent）与 AppLayout 共用 utils/menu，这里不再各写一份。

// 根据当前路由设置激活的菜单
const activeMenu = computed(() => {
  const path = route.path
  if (path === '/') return path
  // 从 menuTree 中找到匹配的菜单项
  for (const group of menuTree.value) {
    const meta = parseMeta(group.meta)
    if (meta.path && path.startsWith(meta.path)) return group.code
    for (const child of (group.children || [])) {
      const childMeta = parseMeta(child.meta)
      if (childMeta.path && path.startsWith(childMeta.path)) return group.code
    }
  }
  return path
})

// 渲染后的菜单树
// - 只保留 admin 壳的项（读 meta.shell），与 AppLayout 走 utils/menu.js 同一套分流规则。
//   此前这里不过滤 shell，导致业务壳的「知识库」分组也出现在 admin 顶栏，点进去就跳出本壳。
// - 跳过 meta.path 含 ':' 的菜单项（如 /app/topic/:id）：占位路径，用户主动点会跳
//   字面量 URL 触发 #34 的 NaN bug。menuTree 里仍含此项供 menuRouteNameMap 读 tab 标题。
// - GROUP 类型不仅要整体判断可见，还要过滤掉内部不可见的子项（避免 el-sub-menu 渲染出占位子项）。
//   **子项也要按 shell 过滤**：分组层过滤了、子项层没过滤的话，业务壳的菜单项
//   照样会出现在 admin 顶栏的分组里 —— 同一个洞，只补了一半。
// - 可见性判定和按壳过滤**都走 utils/menu.js 那一份**
//   （buildShellMenuTree）：这里原来自己写了一遍，规则一样但改一处要记得改另一处。
//   业务壳那边（AppLayout）本来就是调共享函数的，只剩这侧是副本。
const visibleMenuTree = computed(() =>
  buildShellMenuTree('admin', menuTree.value, allowedMenuPaths.value)
)

// menuTree → routeName → name 映射：让 tab 标题从 sys_permission.name 读（用户诉求"通过数据库配置"）。
// meta.routeName 跟 router/index.js 的 route.name 一一对应（如 'AppTopicDetail'）。
// 加新菜单：只需 sys_permission 加菜单项 + meta.routeName，Layout 不动（继续满足 #45 解耦诉求）。
const menuRouteNameMap = computed(() => {
  const map = new Map()
  const collect = (node) => {
    const meta = parseMeta(node.meta)
    if (node.name && meta.routeName) {
      map.set(meta.routeName, node.name)
    }
    ;(node.children || []).forEach(collect)
  }
  menuTree.value.forEach(collect)
  return map
})

const cachedTabNames = computed(() => tabs.value.map(tab => tab.name))

// 监听路由变化，添加页面标签。
// tab 标题优先级：menuRouteNameMap（sys_permission.name，数据库配置）→ route.meta.title（路由 fallback）→ route.name。
// 加新菜单 = sys_permission 加菜单项（含 meta.routeName）+ router/index.js 加路由（含 meta.title fallback）。
// Layout.vue 不维护任何映射表，完全由数据库 + 路由表驱动。
watch(
  () => route.name,
  () => {
    const name = route.name
    if (name && name !== 'Login') {
      const exists = tabs.value.some(tab => tab.name === name)
      if (!exists) {
        tabs.value.push({
          name,
          title: menuRouteNameMap.value.get(name) || route.meta?.title || name,
          path: route.path
        })
      }
      activeTab.value = name
    }
  },
  { immediate: true }
)

/**
 * 登录态没了（退出登录 / 改完密码）：**这个壳自己的**状态一起清。
 *
 * ⚠️ 退出登录本身（确认框 / 清 token / 提示 / 跳登录页）在 `UserMenu.vue` 里 ——
 * 两个壳共用那一份。各壳只管自己那点收尾（这个壳有页面标签页要清）。
 */
function onSignedOut() {
  tabs.value = [{ name: 'Home', title: '首页', path: '/' }]
  activeTab.value = 'Home'
}

const loadCurrentContext = async (force = false) => {
  // 会话引导（资料/菜单/权限）收敛到 utils/session，AppLayout 走同一份。
  // mount 时【不能】force：子页面（Home.vue）也会调 bootstrapSession，force 会绕开共享的
  // inflight，导致同一轮接口被打两遍。只有改完个人资料需要强刷。
  const { menuTree: tree, menuPaths } = await bootstrapSession({ force })
  menuTree.value = tree
  allowedMenuPaths.value = menuPaths
}

// 有业务菜单（超管往往两边都有）才显示"进入知识库"入口。判据与 AppLayout 共用 hasAppMenus
const hasAppShell = computed(() => hasAppMenus())

function goApp() {
  router.push('/app')
}

const switchTab = (tabName) => {
  const targetTab = tabs.value.find(tab => tab.name === tabName)
  if (targetTab) {
    router.push(targetTab.path)
  }
}

const handleTabClick = (tabName) => {
  if (suppressTabClick.value) {
    return
  }
  switchTab(tabName)
}

const clearDragHoldTimer = () => {
  if (dragHoldTimer) {
    clearTimeout(dragHoldTimer)
    dragHoldTimer = null
  }
}

const cleanupDragging = () => {
  window.removeEventListener('mousemove', handleGlobalPointerMove)
  window.removeEventListener('mouseup', handleGlobalPointerUp)
  draggingTabName.value = ''
  dragPreviewIndex.value = -1
  dragGhostLeft.value = 0
  dragGhostTop.value = 0
  draggedTabWidth.value = 0
  draggedTabHeight.value = 0
  pressedTabName = ''
  dragPointerOffsetX = 0
  clearDragHoldTimer()
}

const buildReorderedTabs = (draggedName, insertIndex) => {
  const draggedTab = tabs.value.find(tab => tab.name === draggedName)
  if (!draggedTab) {
    return tabs.value
  }
  const reordered = tabs.value.filter(tab => tab.name !== draggedName)
  const safeInsertIndex = Math.max(0, Math.min(insertIndex, reordered.length))
  reordered.splice(safeInsertIndex, 0, draggedTab)
  return reordered
}

const getTabIndex = (tabName) => tabs.value.findIndex(tab => tab.name === tabName)
const draggingTab = computed(() => tabs.value.find(tab => tab.name === draggingTabName.value) || null)
const ghostTabStyle = computed(() => ({
  left: `${dragGhostLeft.value}px`,
  top: `${dragGhostTop.value}px`,
  width: `${draggedTabWidth.value}px`,
  height: `${draggedTabHeight.value || 40}px`
}))

const getPreviewInsertIndex = (clientX) => {
  const tabElements = Array.from(tabsContainerRef.value?.querySelectorAll('.page-tabs > .page-tab') || [])
  const otherTabElements = tabElements.filter(element => element.dataset.tabName !== draggingTabName.value)
  for (let index = 0; index < otherTabElements.length; index += 1) {
    const element = otherTabElements[index]
    const rect = element.getBoundingClientRect()
    if (clientX < rect.left + rect.width / 2) {
      return index
    }
  }
  return otherTabElements.length
}

const handleTabMouseDown = (event, tabName) => {
  if (event.button !== 0) {
    return
  }
  event.preventDefault()
  cleanupDragging()
  clearDragHoldTimer()
  pressedTabName = tabName
  lastPointerX = event.clientX
  const rect = event.currentTarget?.getBoundingClientRect?.()
  dragPointerOffsetX = rect ? event.clientX - rect.left : 0
  dragHoldTimer = window.setTimeout(() => {
    if (pressedTabName !== tabName) {
      return
    }
    draggingTabName.value = tabName
    dragPreviewIndex.value = getTabIndex(tabName)
    draggedTabWidth.value = rect?.width || 0
    draggedTabHeight.value = rect?.height || 40
    dragGhostLeft.value = lastPointerX - dragPointerOffsetX
    dragGhostTop.value = rect?.top || 0
  }, TAB_DRAG_HOLD_DELAY)
  window.addEventListener('mousemove', handleGlobalPointerMove)
  window.addEventListener('mouseup', handleGlobalPointerUp)
}

const handleGlobalPointerMove = (event) => {
  lastPointerX = event.clientX
  if (!draggingTabName.value) {
    return
  }
  dragGhostLeft.value = event.clientX - dragPointerOffsetX
  const draggedCenterX = event.clientX - dragPointerOffsetX + draggedTabWidth.value / 2
  dragPreviewIndex.value = getPreviewInsertIndex(draggedCenterX)
}

const handleGlobalPointerUp = () => {
  if (!draggingTabName.value) {
    cleanupDragging()
    return
  }
  tabs.value = buildReorderedTabs(draggingTabName.value, dragPreviewIndex.value)
  suppressTabClick.value = true
  cleanupDragging()
  window.setTimeout(() => {
    suppressTabClick.value = false
  }, TAB_DRAG_HOLD_DELAY)
}

const getTabStyle = (tabName) => {
  if (!draggingTabName.value) {
    return {}
  }
  if (draggingTabName.value === tabName) {
    return {}
  }
  const currentIndex = getTabIndex(tabName)
  const draggedIndex = getTabIndex(draggingTabName.value)
  if (draggedIndex === -1 || dragPreviewIndex.value === -1 || !draggedTabWidth.value) {
    return {}
  }
  if (dragPreviewIndex.value > draggedIndex && currentIndex > draggedIndex && currentIndex <= dragPreviewIndex.value) {
    return {
      transform: `translateX(${-draggedTabWidth.value}px)`
    }
  }
  if (dragPreviewIndex.value < draggedIndex && currentIndex >= dragPreviewIndex.value && currentIndex < draggedIndex) {
    return {
      transform: `translateX(${draggedTabWidth.value}px)`
    }
  }
  return {
    transform: 'translateX(0)'
  }
}

// 移除标签页
const removeTab = (name) => {
  const index = tabs.value.findIndex(tab => tab.name === name)
  if (index !== -1) {
    tabs.value.splice(index, 1)

    // 如果移除的是当前激活的标签页，切换到前一个或首页
    if (name === activeTab.value) {
      const newActive = tabs.value[index - 1] || tabs.value[0]
      if (newActive) {
        router.push(newActive.path)
      }
    }
  }
}

onMounted(async () => {
  // admin 壳**没有任何界面偏好开关**（2026-10-07 那个百分比 chip 整个删掉了），
  // 所以启动时什么都不用写：base.css 的 :root 就是这个壳的全部外观。
  try {
    await loadCurrentContext()
  } catch (e) {
    // 提示归 request.js（bootstrapSession 走 axios 实例，唯一提示出口）。
    // 这里原来再弹一条「加载用户信息失败：<e.message>」，一次失败出两条、
    // 而且两条内容还不一样 —— 挂在界面顶上的那条反而是最没有信息量的。
  }
})

onBeforeUnmount(() => {
  cleanupDragging()
})
</script>

<style scoped>
.app-container {
  display: flex;
  flex-direction: column;
  /* 100% 而不是 100vh：全高靠 App.vue 那条 html → body → #app 的百分比链，壳取同一档 */
  height: 100%;
  overflow: hidden;
}

/* ---------- 顶栏 ---------- */
.header {
  display: flex;
  align-items: center;
  gap: var(--sp-13);
  height: 56px;
  flex-shrink: 0;
  padding: 0 var(--sp-12) 0 var(--sp-13);
  background: var(--cc-surface);
  border-bottom: 1px solid var(--cc-border);
}

.brand {
  display: flex;
  align-items: center;
  gap: var(--sp-9);
  text-decoration: none;
  flex-shrink: 0;
}
.brand__logo {
  width: 26px;
  height: 26px;
  border-radius: var(--radius);
  background: var(--cc-logo-gradient);
  color: var(--cc-surface);
  font-size: var(--text-2xs);
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}
.brand__name {
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--cc-text-1);
  letter-spacing: var(--ls-tight);
}

/* 导航：浅色菜单。原来的 background-color/text-color/active-text-color
   是 el-menu 为深色底设计的 props，才逼出一堆 :deep 补丁；改浅色后一律不需要。 */
.nav-menu {
  flex: 1;
  min-width: 0;
  height: 100%;
}

:deep(.admin-menu) {
  height: 100%;
  border-bottom: none;
  background: transparent;
}

:deep(.admin-menu.el-menu--horizontal > .el-menu-item),
:deep(.admin-menu.el-menu--horizontal > .el-sub-menu > .el-sub-menu__title) {
  height: 56px;
  line-height: 56px;
  margin: 0;
  padding: 0 var(--sp-10);
  font-size: var(--text-md);
  color: var(--cc-text-2);
  background: transparent;
  transition: var(--cc-transition);
}

:deep(.admin-menu .el-sub-menu__title:hover),
:deep(.admin-menu > .el-menu-item:hover) {
  color: var(--cc-text-1);
  background: var(--cc-surface-2);
}

/* 选中态：主色文字 + 底部 2px 主色指示条（替代原来那个 #ffd04b 黄字） */
:deep(.admin-menu > .el-menu-item.is-active) {
  color: var(--cc-primary);
  font-weight: 500;
  box-shadow: inset 0 -2px 0 var(--cc-primary);
}
:deep(.admin-menu > .el-sub-menu.is-active > .el-sub-menu__title) {
  color: var(--cc-primary);
  font-weight: 500;
}

/* 子菜单浮层：Element Plus 默认白底浮层即可，不要再染深灰 */
:deep(.admin-menu .el-menu--horizontal .el-menu) {
  top: 56px;
  border-top: none;
  border-radius: var(--cc-radius);
  box-shadow: 0 4px 16px rgba(31, 35, 41, 0.1);
}

:deep(.admin-menu .el-menu-item) {
  font-size: var(--text-md);
  color: var(--cc-text-2);
  transition: var(--cc-transition);
}
:deep(.admin-menu .el-menu-item:hover) {
  color: var(--cc-text-1);
  background: var(--cc-surface-2);
}
:deep(.admin-menu .el-menu-item.is-active) {
  color: var(--cc-primary);
}

/* ---------- 顶栏右侧 ---------- */
.user-info {
  display: flex;
  align-items: center;
  gap: var(--sp-4);
  flex-shrink: 0;
}

.icon-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border: none;
  border-radius: var(--cc-radius-sm);
  background: transparent;
  color: var(--cc-icon);
  cursor: pointer;
  transition: var(--cc-transition);
}
.icon-btn:hover {
  color: var(--cc-primary);
  background: var(--cc-primary-soft);
}

/* 用户菜单的样式（触发区、名字、尖角）搬进 components/UserMenu.vue ——
   两个壳共用那一份组件，样式也该跟着走一份。 */

/* ---------- 页面标签页 ---------- */
/* 下划线式：原来那套「浏览器文件页」造型（1px 边框 + -1px 重叠 + 灰底 + 激活变白）
   是 admin 壳显得旧的主因，去掉。 */
.tabs-row {
  display: flex;
  align-items: stretch;
  height: var(--ctl-lg);
  flex-shrink: 0;
  padding: 0 var(--sp-13);
  background: var(--cc-surface);
  border-bottom: 1px solid var(--cc-border);
}

.page-tabs-shell {
  display: flex;
  flex: 1;
  min-width: 0;
}

.page-tabs {
  display: flex;
  align-items: stretch;
  flex: 1;
  min-width: 0;
  overflow-x: auto;
  overflow-y: hidden;
}

.page-tab {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: var(--sp-6);
  height: var(--ctl-lg);
  padding: 0 var(--sp-11);
  flex-shrink: 0;
  white-space: nowrap;
  font-size: var(--text-sm);
  color: var(--cc-text-3);
  cursor: pointer;
  user-select: none;
  transition: var(--cc-transition);
}
.page-tab::after {
  content: '';
  position: absolute;
  left: var(--sp-11);
  right: var(--sp-11);
  bottom: 0;
  height: 2px;
  border-radius: var(--radius-xs);
  background: transparent;
  transition: background 0.15s;
}
.page-tab:hover {
  color: var(--cc-text-1);
  background: var(--cc-surface-2);
}
.page-tab.is-active {
  color: var(--cc-primary);
  font-weight: 500;
}
.page-tab.is-active::after {
  background: var(--cc-primary);
}
.page-tab.is-placeholder {
  visibility: hidden;
}

.page-tab--ghost {
  position: fixed;
  opacity: 0.98;
  cursor: grabbing;
  box-shadow: 0 6px 16px rgba(31, 35, 41, 0.16);
  border-radius: var(--cc-radius-sm);
  background: var(--cc-surface);
  transition: none;
  pointer-events: none;
  z-index: 1000;
}

.page-tab__title {
  white-space: nowrap;
}

.page-tab__close {
  font-size: var(--text-xs);
  padding: var(--sp-2);
  border-radius: 50%;
  color: var(--cc-text-4);
  opacity: 0;
  transition: opacity 0.15s, background 0.15s, color 0.15s;
}
.page-tab:hover .page-tab__close {
  opacity: 1;
}
.page-tab__close:hover {
  color: var(--cc-text-1);
  background: var(--cc-surface-2);
}

/* ---------- 主内容区 ---------- */
.main-content {
  flex: 1;
  min-height: 0;
  background: var(--cc-bg);
  padding: var(--sp-14);
  overflow-y: auto;
  /* 和业务壳 .app-content 同一个毛病、同一个修法：下面的 .page-wrap 是
     `margin: 0 auto` 居中的，滚动条却长在**本元素**里——它一出现容器就窄一条，
     居中起点跟着横跳（切筛选 / 翻页时整块内容平移几像素）。
     留槽之后有没有滚动条都一样宽。 */
  scrollbar-gutter: stable;
}

/* 内容宽度上限：原来满宽铺到 2K 屏边。admin 表格页比业务壳宽，但不该无限宽。
   写死 1440，不跟业务壳的 --w-content 绑在一起：两个壳的内容宽度是各自定的，
   只是碰巧现在同值（业务壳 1440 / admin 1440），绑在一起以后改一边就会连累另一边。 */
.page-wrap {
  max-width: 1440px;
  margin: 0 auto;
}
</style>
