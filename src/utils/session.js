import { getMyMenuTree, getMyProfile } from '../api/user'
import request from './request'
import { getMenuPaths, getMenuTree, getToken, onAuthChange, saveAuthorizationProfile, saveMenuTree } from './auth'

/**
 * 会话引导：拉当前用户的资料 / 菜单树 / 权限，并写入本地缓存。
 *
 * 两个壳（Layout.vue / AppLayout.vue）启动时都要这套数据，抽出来只留一份。
 * 同一时刻并发调用共享同一个 Promise——两个壳同时挂载也不会打两次接口。
 * 路由守卫也会在判权限前调它，保证硬刷新进来时菜单已经就位。
 *
 * ⚠️ snapshot 是模块级内存，clearAuth() 只清得到 localStorage。
 * 换账号时不订阅 onAuthChange，B 会直接继承 A 的资料和菜单权限（严重度见 FEATURES.md）。
 */
let inflight = null
let snapshot = null

onAuthChange(() => {
  snapshot = null
  inflight = null
})

export function isSessionReady() {
  return snapshot != null
}

export function bootstrapSession({ force = false } = {}) {
  if (snapshot && !force) return Promise.resolve(snapshot)
  if (inflight && !force) return inflight

  // 记下发起时的登录态：请求在途中换了账号，这批资料就属于旧账号，不能写进缓存
  const token = getToken()

  inflight = Promise.all([
    getMyMenuTree(),
    getMyProfile(),
    request.get('/sys/user/me/permissions')
  ]).then(([menuRes, profileRes, permissionsRes]) => {
    if (getToken() !== token) return { menuTree: [], menuPaths: [], profile: {} }
    saveMenuTree(menuRes?.data || [])
    saveAuthorizationProfile(permissionsRes?.data || {})
    snapshot = {
      menuTree: getMenuTree(),
      menuPaths: getMenuPaths(),
      profile: profileRes?.data || {}
    }
    return snapshot
  })

  return inflight
}

/**
 * 登录后的落点：按"你拥有哪个壳的菜单"决定，而不是按角色。
 *   - 有管理后台菜单 → 后台首页，他的工作面是配置系统
 *   - 只有业务菜单 → 业务首页
 * 一个都没有也落业务首页：业务界面是产品本体，进去至少能看到"没权限"的提示。
 */
export function landingRoute() {
  return hasAdminMenus() ? { name: 'Home' } : { name: 'AppHome' }
}

/** 用户是否有管理后台（admin 壳）的菜单——决定要不要给"管理后台"入口 */
export function hasAdminMenus() {
  return getMenuPaths().some((p) => !p.startsWith('/app'))
}

/** 用户是否有业务界面（app 壳）的菜单——决定要不要给"进入知识库"入口 */
export function hasAppMenus() {
  return getMenuPaths().some((p) => p.startsWith('/app'))
}
