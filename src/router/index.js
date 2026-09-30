import { createRouter, createWebHistory } from 'vue-router'
import { getMenuPaths, hasToken } from '../utils/auth'
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
        path: 'corecraft-web/learn',
        name: 'CorecraftWebLearnTopic',
        component: () => import('../views/corecraft-web/LearnTopicView.vue'),
        meta: { title: '随手记' }
      },
      {
        // 知识点详情：从列表卡片点进 / 关联跳转都用 router.push，
        // 进入 Layout 顶部 tabs 作为可关闭、可切换的标签页（不需要菜单项）。
        path: 'corecraft-web/topic/:id',
        name: 'CorecraftWebTopicDetail',
        component: () => import('../views/corecraft-web/TopicDetailView.vue'),
        meta: { title: '知识点详情' }
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
router.beforeEach((to, from, next) => {
  const token = hasToken()
  const menuPaths = getMenuPaths()
  
  if (to.meta.requiresAuth && !token) {
    next({ name: 'Login' })
  } else if (
    to.meta.requiresAuth &&
    protectedMenuPaths.includes(to.path) &&
    !menuPaths.includes(to.path)
  ) {
    next({ name: 'Forbidden' })
  } else if (to.name === 'Login' && token) {
    // 已登录用户访问登录页，重定向到首页
    next({ name: 'Home' })
  } else {
    next()
  }
})

export default router 
