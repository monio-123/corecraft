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
        <el-dropdown @command="handleUserCommand">
          <span class="user-trigger">
            <el-avatar :size="28" :src="currentUser.avatar || undefined" class="user-avatar">
              {{ userAvatarFallback }}
            </el-avatar>
            <span class="user-name">{{ currentUser.nickname || currentUser.username || '未登录' }}</span>
            <el-icon class="user-caret"><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">编辑个人信息</el-dropdown-item>
              <el-dropdown-item command="password">修改密码</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
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

    <el-dialog v-model="profileDialogVisible" title="编辑个人信息" width="560px">
      <el-form :model="profileForm" label-width="90px">
        <el-form-item label="头像">
          <div class="avatar-editor">
            <el-avatar :size="72" :src="profileForm.avatar || undefined">
              {{ userAvatarFallback }}
            </el-avatar>
            <el-upload :show-file-list="false" :http-request="handleAvatarUpload" accept="image/*">
              <el-button>上传头像</el-button>
            </el-upload>
          </div>
        </el-form-item>
        <el-form-item label="用户名">
          <el-input v-model="profileForm.username" disabled />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="profileForm.nickname" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="profileForm.email" />
        </el-form-item>
        <el-form-item label="手机">
          <el-input v-model="profileForm.mobile" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="profileDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="profileSaving" @click="saveProfile">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="passwordDialogVisible" title="修改密码" width="520px">
      <el-form :model="passwordForm" label-width="90px">
        <el-form-item label="旧密码">
          <el-input v-model="passwordForm.oldPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="passwordForm.newPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input v-model="passwordForm.confirmPassword" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="passwordSaving" @click="savePassword">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, watch, reactive, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter, RouterLink } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as Icons from '@element-plus/icons-vue'
import { ArrowDown, ArrowUp, Reading } from '@element-plus/icons-vue'
import { clearAuth, getMenuPaths, getMenuTree } from '../utils/auth'
import { parseMeta, getIconComponent, isPlaceholderPath, shellOf } from '../utils/menu'
import { bootstrapSession, hasAppMenus } from '../utils/session'
import { updateMyPassword, updateMyProfile, uploadMyAvatar } from '../api/user'

const route = useRoute()
const router = useRouter()
const currentUser = reactive({
  username: '',
  nickname: '',
  avatar: '',
  email: '',
  mobile: '',
  roles: []
})
const profileDialogVisible = ref(false)
const passwordDialogVisible = ref(false)
const profileSaving = ref(false)
const passwordSaving = ref(false)
const allowedMenuPaths = ref(getMenuPaths())
const menuTree = ref(getMenuTree())

const profileForm = reactive({
  username: '',
  nickname: '',
  avatar: '',
  email: '',
  mobile: ''
})

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

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
// - GROUP 类型不仅要整体判断可见，还要过滤掉内部不可见的子项（避免 el-sub-menu 渲染出占位子项）
const visibleMenuTree = computed(() => {
  const isVisibleMenu = (node) => {
    const meta = parseMeta(node.meta)
    return meta.path && !isPlaceholderPath(meta.path) && allowedMenuPaths.value.includes(meta.path)
  }
  return menuTree.value
    .filter((item) => shellOf(item.meta) === 'admin')
    .map((item) => {
      if (item.type === 'GROUP') {
        const visibleChildren = (item.children || []).filter(isVisibleMenu)
        return { ...item, children: visibleChildren }
      }
      return isVisibleMenu(item) ? item : null
    })
    .filter(Boolean)
})

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

const userAvatarFallback = computed(() => {
  const source = currentUser.nickname || currentUser.username || 'U'
  return source.slice(0, 1).toUpperCase()
})

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

const handleUserCommand = async (command) => {
  if (command === 'profile') {
    profileForm.username = currentUser.username || ''
    profileForm.nickname = currentUser.nickname || ''
    profileForm.avatar = currentUser.avatar || ''
    profileForm.email = currentUser.email || ''
    profileForm.mobile = currentUser.mobile || ''
    profileDialogVisible.value = true
    return
  }

  if (command === 'password') {
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
    passwordForm.confirmPassword = ''
    passwordDialogVisible.value = true
    return
  }

  if (command === 'logout') {
    await ElMessageBox.confirm('确认退出当前登录状态吗？', '退出登录', {
      type: 'warning',
      confirmButtonText: '退出',
      cancelButtonText: '取消'
    })
    clearAuth()
    tabs.value = [{ name: 'Home', title: '首页', path: '/' }]
    activeTab.value = 'Home'
    ElMessage.success('已退出登录')
    router.push('/login')
  }
}

const loadCurrentContext = async (force = false) => {
  // 会话引导（资料/菜单/权限）收敛到 utils/session，AppLayout 走同一份。
  // mount 时【不能】force：子页面（Home.vue）也会调 bootstrapSession，force 会绕开共享的
  // inflight，导致同一轮接口被打两遍。只有改完个人资料需要强刷。
  const { profile, menuTree: tree, menuPaths } = await bootstrapSession({ force })
  Object.assign(currentUser, profile)
  menuTree.value = tree
  allowedMenuPaths.value = menuPaths
}

// 有业务菜单（超管往往两边都有）才显示"进入知识库"入口。判据与 AppLayout 共用 hasAppMenus
const hasAppShell = computed(() => hasAppMenus())

function goApp() {
  router.push('/app')
}

const saveProfile = async () => {
  profileSaving.value = true
  try {
    await updateMyProfile({
      nickname: profileForm.nickname,
      avatar: profileForm.avatar,
      email: profileForm.email,
      mobile: profileForm.mobile
    })
    await loadCurrentContext(true)
    profileDialogVisible.value = false
    ElMessage.success('个人信息已更新')
  } finally {
    profileSaving.value = false
  }
}

const savePassword = async () => {
  if (!passwordForm.oldPassword || !passwordForm.newPassword || !passwordForm.confirmPassword) {
    ElMessage.error('请完整填写密码信息')
    return
  }
  passwordSaving.value = true
  try {
    await updateMyPassword(passwordForm)
    passwordDialogVisible.value = false
    ElMessage.success('密码已修改，请重新登录')
    clearAuth()
    router.push('/login')
  } finally {
    passwordSaving.value = false
  }
}

const handleAvatarUpload = async ({ file }) => {
  const res = await uploadMyAvatar(file)
  profileForm.avatar = res?.data?.avatar || ''
  currentUser.avatar = profileForm.avatar
  ElMessage.success('头像上传成功')
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
  try {
    await loadCurrentContext()
  } catch (e) {
    ElMessage.error(`加载用户信息失败：${e?.message || e}`)
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
  height: 100vh;
  overflow: hidden;
}

/* ---------- 顶栏 ---------- */
.header {
  display: flex;
  align-items: center;
  gap: 20px;
  height: 56px;
  flex-shrink: 0;
  padding: 0 16px 0 20px;
  background: var(--cc-surface);
  border-bottom: 1px solid var(--cc-border);
}

.brand {
  display: flex;
  align-items: center;
  gap: 9px;
  text-decoration: none;
  flex-shrink: 0;
}
.brand__logo {
  width: 26px;
  height: 26px;
  border-radius: 7px;
  background: var(--cc-logo-gradient);
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}
.brand__name {
  font-size: 15px;
  font-weight: 600;
  color: var(--cc-text-1);
  letter-spacing: 0.3px;
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
  padding: 0 12px;
  font-size: 14px;
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
  font-size: 14px;
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
  gap: 4px;
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

.user-trigger {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px 4px 4px;
  border-radius: 16px;
  cursor: pointer;
  transition: var(--cc-transition);
}
.user-trigger:hover {
  background: var(--cc-surface-2);
}
.user-name {
  font-size: 14px;
  color: var(--cc-text-2);
}
.user-caret {
  font-size: 12px;
  color: var(--cc-text-4);
}

/* ---------- 页面标签页 ---------- */
/* 下划线式：原来那套「浏览器文件页」造型（1px 边框 + -1px 重叠 + 灰底 + 激活变白）
   是 admin 壳显得旧的主因，去掉。 */
.tabs-row {
  display: flex;
  align-items: stretch;
  height: 40px;
  flex-shrink: 0;
  padding: 0 20px;
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
  gap: 6px;
  height: 40px;
  padding: 0 14px;
  flex-shrink: 0;
  white-space: nowrap;
  font-size: 13px;
  color: var(--cc-text-3);
  cursor: pointer;
  user-select: none;
  transition: var(--cc-transition);
}
.page-tab::after {
  content: '';
  position: absolute;
  left: 14px;
  right: 14px;
  bottom: 0;
  height: 2px;
  border-radius: 1px;
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
  font-size: 12px;
  padding: 2px;
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
  padding: 24px;
  overflow-y: auto;
}

/* 内容宽度上限：原来满宽铺到 2K 屏边。admin 表格页比业务壳宽，但不该无限宽。 */
.page-wrap {
  max-width: 1440px;
  margin: 0 auto;
}

.avatar-editor {
  display: flex;
  align-items: center;
  gap: 16px;
}
</style>
