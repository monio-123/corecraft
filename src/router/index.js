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
      },
      {
        path: 'knowledge/trees',
        name: 'KnowledgeTrees',
        component: () => import('../views/knowledge_platform/KnowledgeTrees.vue')
      },
      {
        path: 'knowledge/tree/:id',
        name: 'KnowledgeTreeView',
        component: () => import('../views/knowledge_platform/KnowledgeTreeView.vue')
      },
      {
        path: 'knowledge/exams',
        name: 'Exams',
        component: () => import('../views/knowledge_platform/Exams.vue')
      },
      {
        path: 'knowledge/exam/:id',
        name: 'ExamTake',
        component: () => import('../views/knowledge_platform/ExamTake.vue')
      },
      {
        path: 'knowledge/learn',
        name: 'LearnTopic',
        component: () => import('../views/knowledge_platform/LearnTopic.vue')
      },
      {
        path: 'knowledge/history',
        name: 'ExamHistory',
        component: () => import('../views/knowledge_platform/ExamHistory.vue')
      },
      {
        path: 'forbidden',
        name: 'Forbidden',
        component: Forbidden
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
