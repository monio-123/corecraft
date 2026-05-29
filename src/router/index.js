import { createRouter, createWebHistory } from 'vue-router'
import { getMenuPaths, hasToken } from '../utils/auth'

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
        component: () => import('../views/Home.vue')
      },
      {
        path: 'roles',
        name: 'Roles',
        component: () => import('../views/Roles.vue')
      },
      {
        path: 'users',
        name: 'Users',
        component: () => import('../views/Users.vue')
      },
      {
        path: 'permissions',
        name: 'Permissions',
        component: () => import('../views/Permissions.vue')
      },
      {
        path: 'dicts',
        name: 'Dictionaries',
        component: () => import('../views/Dictionaries.vue')
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
    next({ name: 'Home' })
  } else if (to.name === 'Login' && token) {
    // 已登录用户访问登录页，重定向到首页
    next({ name: 'Home' })
  } else {
    next()
  }
})

export default router 
