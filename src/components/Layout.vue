<template>
  <div class="app-container">
    <!-- 顶部导航栏 -->
    <div class="header">
      <div class="logo">
        <h2>Cerebrove</h2>
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
          
          <!-- 系统管理下拉菜单 -->
          <el-sub-menu index="system">
            <template #title>
              <el-icon><Setting /></el-icon>
              <span>系统管理</span>
            </template>
            <el-menu-item index="/users">
              <el-icon><User /></el-icon>
              <span>用户管理</span>
            </el-menu-item>
            <el-menu-item index="/roles">
              <el-icon><UserFilled /></el-icon>
              <span>角色管理</span>
            </el-menu-item>
            <el-menu-item index="/permissions">
              <el-icon><Lock /></el-icon>
              <span>权限管理</span>
            </el-menu-item>
          </el-sub-menu>
        </el-menu>
      </div>
      <div class="user-info">
        <el-dropdown>
          <span class="el-dropdown-link">
            <el-icon class="user-icon"><User /></el-icon>
            <span>管理员</span>
            <el-icon class="el-icon--right"><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item>个人中心</el-dropdown-item>
              <el-dropdown-item>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>
    
    <!-- 分隔线 -->
    <div class="separator"></div>
    
    <!-- 页面标签页 -->
    <div class="tabs-container" v-if="tabs.length > 0">
      <el-tabs
        v-model:active-name="activeTab"
        class="page-tabs"
        type="card"
        @tab-remove="removeTab"
        @tab-click="switchTab"
      >
        <el-tab-pane
          v-for="tab in tabs"
          :key="tab.name"
          :label="tab.title"
          :name="tab.name"
          closable
        >
        </el-tab-pane>
      </el-tabs>
    </div>
    
    <!-- 主内容区域 -->
    <div class="main-content">
      <router-view />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { House, Setting, UserFilled, User, Lock, ArrowDown } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

// 页面标签页数据
const tabs = ref([
  { name: 'Home', title: '首页', path: '/' }
])

// 当前激活的标签页
const activeTab = ref('Home')

// 根据当前路由设置激活的菜单
const activeMenu = computed(() => {
  const path = route.path
  if (path === '/') return path
  if (path.startsWith('/roles') || path.startsWith('/users') || path.startsWith('/permissions')) {
    return 'system'
  }
  return path
})

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

// 根据路由名称获取标签页标题
const getTabTitle = (name) => {
  const titleMap = {
    'Home': '首页',
    'Roles': '角色管理',
    'Users': '用户管理',
    'Permissions': '权限管理'
  }
  return titleMap[name] || name
}

// 切换标签页
const switchTab = (tab) => {
  const targetTab = tabs.value.find(t => t.name === tab.paneName)
  if (targetTab) {
    router.push(targetTab.path)
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

.user-icon {
  margin-right: 4px;
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
  height: 100%;
  line-height: 36px;
  border-bottom: none !important;
  margin: 0;
  padding: 0;
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