import { createRouter, createWebHistory } from 'vue-router'
import { getMenuPaths, hasToken } from '../utils/auth'
import { bootstrapSession, isSessionReady, landingRoute } from '../utils/session'
import Users from '../views/Users.vue'
import Dictionaries from '../views/Dictionaries.vue'
import Forbidden from '../views/Forbidden.vue'
import NotFound from '../views/NotFound.vue'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: { requiresAuth: false }
  },
  // 业务壳：/app/* 走 AppLayout（沉浸式、左侧栏 + 居中窄栏）
  // 与 admin 壳（/ 下的 Layout.vue：顶部菜单 + 可拖拽页面 tabs）完全独立。
  // 走哪个壳由菜单项的 meta.shell 决定，超管在资源管理页配置。
  {
    path: '/app',
    component: () => import('../components/corecraft-web/AppLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        name: 'AppHome',
        component: () => import('../views/corecraft-web/AppHomeView.vue'),
        meta: { title: '首页' }
      },
      {
        path: 'learn',
        name: 'AppLearn',
        component: () => import('../views/corecraft-web/AppLearnView.vue'),
        meta: { title: '随手记' }
      },
      {
        // 详情页不是菜单项（meta.path 含 ':'，导航里会被过滤掉），只能从卡片点进
        path: 'topic/:id',
        name: 'AppTopicDetail',
        component: () => import('../views/corecraft-web/AppTopicDetailView.vue'),
        meta: { title: '知识点' }
      }
    ]
  },
  {
    path: '/',
    component: () => import('../components/Layout.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        name: 'Home',
        component: () => import('../views/Home.vue'),
        meta: { title: '首页' }
      },
      {
        path: 'roles',
        name: 'Roles',
        component: () => import('../views/Roles.vue'),
        meta: { title: '角色管理' }
      },
      {
        path: 'users',
        name: 'Users',
        component: () => import('../views/Users.vue'),
        meta: { title: '用户管理' }
      },
      {
        path: 'permissions',
        name: 'Permissions',
        component: () => import('../views/Permissions.vue'),
        meta: { title: '资源管理' }
      },
      {
        path: 'dicts',
        name: 'Dictionaries',
        component: () => import('../views/Dictionaries.vue'),
        meta: { title: '字典管理' }
      },
      {
        path: 'forbidden',
        name: 'Forbidden',
        component: Forbidden,
        meta: { title: '无权限' }
      },
      {
        path: '/:pathMatch(.*)*',
        name: 'NotFound',
        component: NotFound
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

const protectedMenuPaths = ['/users', '/roles', '/permissions', '/dicts']

// 路由守卫
router.beforeEach(async (to, from, next) => {
  // 登录页：已登录就按"你拥有哪个壳的菜单"决定落点
  if (to.name === 'Login') {
    if (hasToken()) {
      if (!isSessionReady()) {
        try { await bootstrapSession() } catch { /* 拉不到就按默认落点 */ }
      }
      next(landingRoute())
      return
    }
    next()
    return
  }

  if (!to.meta.requiresAuth) {
    next()
    return
  }

  if (!hasToken()) {
    next({ name: 'Login' })
    return
  }

  // 判路由权限之前必须先把菜单拉回来。
  // 硬刷新 / 直接输地址 / 新标签打开时，两个壳的组件都还没挂载，
  // localStorage 里的菜单是空的 —— 守卫不等会话就查 getMenuPaths()，
  // 会把有权限的用户也弹到 /forbidden。
  if (!isSessionReady()) {
    try {
      await bootstrapSession()
    } catch {
      // 拉不到（token 过期等）就按"没有权限"往下走，
      // request.js 的拦截器会负责清 token 并跳登录
    }
  }

  if (protectedMenuPaths.includes(to.path) && !getMenuPaths().includes(to.path)) {
    next({ name: 'Forbidden' })
    return
  }

  next()
})

export default router 
