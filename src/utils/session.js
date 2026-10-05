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
  }).finally(() => {
    // **必须清掉**：不清的话首次失败会把 inflight 永久钉成一个 rejected promise，
    // 而上面第 29 行 `if (inflight && !force) return inflight` 会把每一次后续调用
    // 都导向那个已经失败的 promise —— snapshot 还是 null，永远重试不了，
    // bootstrapSession 在整个页面生命周期里再也不会成功。
    //
    // 症状很难认：session.profile 一直是 {}，界面显示「未登录」，
    // 但 token 明明是好的（建目录、查目录都调得通，那些请求不走这里），
    // 看着像"数据串号"或者"前端没登进去"。
    //
    // 成功路径不需要在这里清：第 28 行 snapshot 已经短路了。
    inflight = null
  })

  return inflight
}

/**
 * 登录后的落点：所有人都落业务首页 `/app`，不分角色、不看有没有 admin 菜单。
 *
 * 不给超管开特例的理由：业务界面是这个产品本体，管理后台是脚手架带来的
 * 用户/角色/字典管理，它是**次级入口**——AppLayout 顶栏的「管理后台」按钮
 * 已经给了超管一键直达（且只对拥有 admin 菜单的人显示）。多这一次点击，
 * 换来"登录后看到的东西永远和这个站是干什么的一致"。
 *
 * 曾经按菜单分流（有 admin 菜单 → 后台首页），代价是"登录落哪"变成三处都要
 * 推理的分支，而且那次改动没留迭代记录，`corecraft-web-context` 和 FEATURES #4
 * 记的还是"无条件落 /app"。策略散落在代码里而不是集中在一处，正是要避免的。
 *
 * 保持成函数而不是让各调用点直接写 `{ name: 'AppHome' }`：它是这条策略的唯一
 * 出处，grep 得到、改一处生效。要改策略就连同上面的理由一起改。
 */
export function landingRoute() {
  return { name: 'AppHome' }
}

/** 用户是否有管理后台（admin 壳）的菜单——决定要不要给"管理后台"入口 */
export function hasAdminMenus() {
  return getMenuPaths().some((p) => !p.startsWith('/app'))
}

/** 用户是否有业务界面（app 壳）的菜单——决定要不要给"进入知识库"入口 */
export function hasAppMenus() {
  return getMenuPaths().some((p) => p.startsWith('/app'))
}
