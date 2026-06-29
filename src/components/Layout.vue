<template>
  <div class="app-container">
    <!-- 顶部导航栏 -->
    <div class="header">
      <div class="logo">
        <h2>Corecraft Web</h2>
      </div>
      <div class="nav-menu">
        <el-menu
          :default-active="activeMenu"
          class="el-menu-demo"
          mode="horizontal"
          router
          background-color="#545c64"
          text-color="#fff"
          active-text-color="#ffd04b"
        >
          <!-- 首页 -->
          <el-menu-item index="/">
            <el-icon><House /></el-icon>
            <span>首页</span>
          </el-menu-item>

          <el-sub-menu v-if="visibleSystemMenus.length" index="system">
            <template #title>
              <el-icon><Setting /></el-icon>
              <span>系统管理</span>
            </template>
            <el-menu-item v-for="item in visibleSystemMenus" :key="item.path" :index="item.path">
              <el-icon><component :is="item.icon" /></el-icon>
              <span>{{ item.title }}</span>
            </el-menu-item>
          </el-sub-menu>

          <el-sub-menu index="knowledge">
            <template #title>
              <el-icon><Reading /></el-icon>
              <span>自学平台</span>
            </template>
            <el-menu-item index="/knowledge/learn">
              <el-icon><MagicStick /></el-icon>
              <span>学习主题</span>
            </el-menu-item>
            <el-menu-item index="/knowledge/trees">
              <el-icon><CollectionTag /></el-icon>
              <span>知识树</span>
            </el-menu-item>
            <el-menu-item index="/knowledge/exams">
              <el-icon><Document /></el-icon>
              <span>试卷中心</span>
            </el-menu-item>
            <el-menu-item index="/knowledge/history">
              <el-icon><Timer /></el-icon>
              <span>考试历史</span>
            </el-menu-item>
          </el-sub-menu>
        </el-menu>
      </div>
      <div class="user-info">
        <el-dropdown @command="handleUserCommand">
          <span class="el-dropdown-link">
            <el-avatar :size="32" :src="currentUser.avatar || undefined" class="user-avatar">
              {{ userAvatarFallback }}
            </el-avatar>
            <span class="user-name">{{ currentUser.nickname || currentUser.username || '未登录' }}</span>
            <el-icon class="el-icon--right"><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">编辑个人信息</el-dropdown-item>
              <el-dropdown-item command="password">修改密码</el-dropdown-item>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>
    
    <!-- 分隔线 -->
    <div class="separator"></div>
    
    <!-- 页面标签页 -->
    <div class="tabs-container" v-if="tabs.length > 0">
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
    
    <!-- 主内容区域 -->
    <div class="main-content">
      <router-view v-slot="{ Component, route: currentViewRoute }">
        <keep-alive :include="cachedTabNames">
          <component :is="Component" :key="currentViewRoute.name || currentViewRoute.path" />
        </keep-alive>
      </router-view>
    </div>

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
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { House, Setting, UserFilled, User, Lock, ArrowDown, CollectionTag, Close, Reading, Document, Timer, List, MagicStick } from '@element-plus/icons-vue'
import { clearAuth, getMenuPaths, saveAuthorizationProfile, saveMenuTree } from '../utils/auth'
import { getMyMenuTree, getMyProfile, updateMyPassword, updateMyProfile, uploadMyAvatar } from '../api/user'
import request from '../utils/request'

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
const systemMenus = [
  { path: '/users', title: '用户管理', icon: User },
  { path: '/roles', title: '角色管理', icon: UserFilled },
  { path: '/permissions', title: '资源管理', icon: Lock },
  { path: '/dicts', title: '字典管理', icon: CollectionTag }
]

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

// 根据当前路由设置激活的菜单
const activeMenu = computed(() => {
  const path = route.path
  if (path === '/') return path
  if (
    path.startsWith('/roles') ||
    path.startsWith('/users') ||
    path.startsWith('/permissions') ||
    path.startsWith('/dicts')
  ) {
    return 'system'
  }
  if (path.startsWith('/knowledge')) {
    return 'knowledge'
  }
  return path
})

const visibleSystemMenus = computed(() =>
  systemMenus.filter(item =>
    allowedMenuPaths.value.includes(item.path)
  )
)

const cachedTabNames = computed(() => tabs.value.map(tab => tab.name))

const userAvatarFallback = computed(() => {
  const source = currentUser.nickname || currentUser.username || 'U'
  return source.slice(0, 1).toUpperCase()
})

// 根据路由名称获取标签页标题
const getTabTitle = (name) => {
  const titleMap = {
    'Home': '首页',
    'Roles': '角色管理',
    'Users': '用户管理',
    'Permissions': '资源管理',
    'Dictionaries': '字典管理',
    'KnowledgeTrees': '知识树',
    'KnowledgeTreeView': '知识树详情',
    'LearnTopic': '学习主题',
    'Exams': '试卷中心',
    'ExamTake': '考试作答',
    'ExamHistory': '考试历史'
  }
  return titleMap[name] || name
}

// 监听路由变化，添加页面标签
watch(
  () => route.name,
  (newName) => {
    if (newName && newName !== 'Login') {
      const exists = tabs.value.some(tab => tab.name === newName)
      if (!exists) {
        tabs.value.push({
          name: newName,
          title: getTabTitle(newName),
          path: route.path
        })
      }
      activeTab.value = newName
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

const loadCurrentContext = async () => {
  const [profileRes, menuRes, permissionsRes] = await Promise.all([
    getMyProfile(),
    getMyMenuTree(),
    request.get('/sys/user/me/permissions')
  ])
  Object.assign(currentUser, profileRes?.data || {})
  saveMenuTree(menuRes?.data || [])
  saveAuthorizationProfile(permissionsRes?.data || {})
  allowedMenuPaths.value = getMenuPaths()
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
    await loadCurrentContext()
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
  height: `${draggedTabHeight.value || 36}px`
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
    draggedTabHeight.value = rect?.height || 36
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
    ElMessage.error('加载用户信息失败')
  }
})

onBeforeUnmount(() => {
  cleanupDragging()
})
</script>

<style scoped>
/* Main layout container */
.app-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden;
  width: 100%;
}

/* Header with fixed height */
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background-color: #545c64;
  color: white;
  padding: 0 20px;
  height: 60px;
  flex-shrink: 0;
}

/* Logo styling */
.logo {
  flex: 0 0 auto;
}

.logo h2 {
  margin: 0;
  color: white;
  font-size: 18px;
  line-height: 60px;
}

/* Navigation menu container */
.nav-menu {
  flex: 1;
  margin: 0 40px;
  height: 100%;
}

/* Menu styles */
:deep(.el-menu) {
  margin: 0;
  padding: 0;
  border: none;
}

:deep(.el-menu--horizontal) {
  border-bottom: none !important;
  height: 100% !important;
  line-height: 60px !important;
  background-color: transparent;
}

:deep(.el-menu--horizontal .el-menu-item),
:deep(.el-menu--horizontal .el-sub-menu__title) {
  height: 100% !important;
  line-height: 60px !important;
  margin: 0 !important;
  padding: 0 12px !important;
  border-bottom: none !important;
}

:deep(.el-menu--horizontal .el-sub-menu) {
  height: 100%;
  line-height: 60px;
}

:deep(.el-menu--horizontal .el-sub-menu .el-menu) {
  top: 60px !important;
  border-top: none !important;
  z-index: 200 !important;
  background-color: #545c64;
}

/* User info section */
.user-info {
  flex: 0 0 auto;
}

.el-dropdown-link {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #fff;
  cursor: pointer;
  background: rgba(255, 255, 255, 0.12);
  border-radius: 18px;
  padding: 6px 12px;
}

.user-avatar {
  flex-shrink: 0;
}

.user-name {
  color: #fff;
  font-size: 14px;
}

.user-icon {
  margin-right: 4px;
}

.avatar-editor {
  display: flex;
  align-items: center;
  gap: 16px;
}

/* Separator between header and tabs */
.separator {
  height: 1px;
  background-color: #e4e7ed;
  flex-shrink: 0;
}

/* Tabs container */
.tabs-container {
  background-color: #fff;
  padding: 0 20px;
  height: 36px;
  line-height: 36px;
  flex-shrink: 0;
  border-bottom: 1px solid #e4e7ed;
  margin-top: 20px;
}

.page-tabs {
  display: flex;
  align-items: center;
  height: 100%;
  gap: 0;
  overflow-x: auto;
  overflow-y: hidden;
}

.page-tab {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  padding: 0 14px;
  border: 1px solid #dcdfe6;
  border-bottom: none;
  background-color: #f5f7fa;
  color: #606266;
  cursor: pointer;
  user-select: none;
  flex-shrink: 0;
  transition: transform 0.12s ease, background-color 0.12s ease, color 0.12s ease;
}

.page-tab + .page-tab {
  margin-left: -1px;
}

.page-tab.is-active {
  background-color: #fff;
  color: #409eff;
}

.page-tab.is-placeholder {
  visibility: hidden;
}

.page-tab--ghost {
  position: fixed;
  opacity: 0.98;
  cursor: grabbing;
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.12);
  transition: none;
  pointer-events: none;
  z-index: 1000;
}

.page-tab__title {
  white-space: nowrap;
}

.page-tab__close {
  color: #909399;
  border-radius: 50%;
  font-size: 12px;
  padding: 2px;
}

.page-tab__close:hover {
  background-color: rgba(0, 0, 0, 0.08);
  color: #606266;
}

/* Main content area */
.main-content {
  flex: 1;
  background-color: #f5f7fa;
  padding: 20px;
  overflow-y: auto;
  width: 100%;
  margin-top: 10px;
}

/* Dropdown menu styling */
:deep(.el-sub-menu__title) {
  color: #fff !important;
}

:deep(.el-sub-menu:hover > .el-sub-menu__title) {
  background-color: rgba(255, 255, 255, 0.1);
}

:deep(.el-dropdown-menu) {
  background-color: #545c64;
  border: none;
}

:deep(.el-dropdown-item) {
  color: #fff;
}

:deep(.el-dropdown-item:hover) {
  background-color: rgba(255, 255, 255, 0.1);
}

/* 响应式设计 */
@media (max-width: 1024px) {
  .nav-menu {
    margin: 0 20px;
  }
}

@media (max-width: 768px) {
  .header {
    padding: 0 10px;
  }
  
  .logo h2 {
    font-size: 16px;
  }
  
  .nav-menu {
    margin: 0 10px;
  }
  
  .main-content {
    padding: 10px;
  }
  
  .tabs-container {
    padding: 0 10px;
  }
}

@media (max-width: 600px) {
  .el-menu-item span,
  .el-sub-menu__title span {
    display: none;
  }
  
  .user-info span:nth-of-type(2) {
    display: none;
  }
}

@media (max-width: 480px) {
  .header {
    flex-wrap: wrap;
    height: auto;
    min-height: 60px;
  }
  
  .nav-menu {
    order: 3;
    width: 100%;
    margin: 0;
    background-color: #545c64;
  }
  
  .user-info {
    margin-left: auto;
  }
}
</style>
