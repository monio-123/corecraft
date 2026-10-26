import { createRouter, createWebHistory } from 'vue-router'
import { getMenuPaths, hasToken } from '../utils/auth'
import { bootstrapSession, hasAdminMenus, isSessionReady, landingRoute } from '../utils/session'
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
  // 编辑器实验台：**免登录**。编辑器的问题几乎全是「当场看着对、存下来再打开就不对」，
  // 那个问题只能在「编辑 → 看存下去的 markdown → 重新打开」这条链上才看得见，
  // 而这条链要求能反复跑、不落库、不依赖后端 —— 所以单独开一页，不进任何壳。
  // 详见 views/EditorLab.vue。
  {
    path: '/editor-lab',
    name: 'EditorLab',
    component: () => import('../views/EditorLab.vue'),
    meta: { requiresAuth: false }
  },
  // 考试（demo）：**独立窗口**，不进任何壳。
  //
  // 入口在知识点详情页顶栏（挨着「AI 问答」那个按钮）→ `window.open` 弹另一个标签页。
  // 为什么单独一个路由而不挂在 /app 下面：考试是沉浸式的——侧边栏、顶栏、
  // "这条知识在哪个目录"这些东西在考试里全是干扰。窗口关掉就关掉，
  // 不影响原来那个页面。
  // **题目和答题记录都不保存** —— 卷子只活在内存里，刷新就没了。
  //
  // ⚠️ **考哪一批知识走查询参数 `ids`**（`/exam?ids=1,2,3`），不走路径参数：
  //   详情页给一条、列表页给当前筛选后可见的那些，两条入口共用同一个路由。
  //   正文由服务端按 id 自己捞（AiChatReq.topicIds），前端不传正文。
  {
    path: '/exam',
    name: 'AppExam',
    component: () => import('../views/corecraft-web/AppExamView.vue'),
    meta: { requiresAuth: true, title: '考试' }
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
        // 侧边栏显示的名字来自 sys_permission.name（本条 code=corecraft-web:learn），
        // 两边要同步改：菜单名在管理后台改（附 SQL 见 resources/db/_archive），
        // 这里改的是顶栏标题 / tab 标题，只靠前端这一处匹配不到。
        meta: { title: '知识点' }
      },
      {
        // 详情页不是菜单项（meta.path 含 ':'，导航里会被过滤掉），只能从卡片点进
        path: 'topic/:id',
        name: 'AppTopicDetail',
        component: () => import('../views/corecraft-web/AppTopicDetailView.vue'),
        meta: { title: '知识点' }
      },
      {
        // 也不进导航列表：入口在侧边栏底栏那个齿轮的菜单里。低频操作不该占导航的一行。
        path: 'settings/ai',
        name: 'AppAiSettings',
        component: () => import('../views/corecraft-web/AiSettingsView.vue'),
        // 标题跟页面自己的大标题、菜单项用同一个词（页面里写的是「AI 能力」）。
        // 同一个东西三个地方三个名字 = 用户要自己认一遍。
        meta: { title: 'AI 能力' }
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
        // 系统配置：系统默认的界面偏好 + 各用户自己的覆盖值。
        // 菜单项在 sys_permission 里（code=config:manage，path 必须和这里对上）。
        path: 'configs',
        name: 'Configs',
        component: () => import('../views/Configs.vue'),
        meta: { title: '系统配置' }
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

const protectedMenuPaths = ['/users', '/roles', '/permissions', '/dicts', '/configs']

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

  // 根路径不是"有 token 就能进的后台首页"。
  // `/` 是管理后台的真实首页（AppLayout 顶栏「管理后台」按钮的 goAdmin() 就 push 这里），
  // 所以它不能变成纯分发口——那会让超管点齿轮 → `/` → 弹回 `/app` → 再点齿轮，死循环。
  // 因此这里只把**没有 admin 菜单的人**请走，他们本来就不该看见这个壳，
  // 落点用 landingRoute()（= 业务首页），和登录后落点保持同一个出处。
  // ⚠️ 修法不是把 '/' 塞进 protectedMenuPaths：普通用户的落点本来就有（业务首页），
  // 弹 Forbidden 是错的答案；而且 /forbidden 本身就是 / 的子路由，塞进去会让
  // 无权用户看到"自己的无权页套在管理后台壳里"。
  if (to.name === 'Home' && !hasAdminMenus()) {
    next(landingRoute())
    return
  }

  if (protectedMenuPaths.includes(to.path) && !getMenuPaths().includes(to.path)) {
    next({ name: 'Forbidden' })
    return
  }

  next()
})

export default router 
