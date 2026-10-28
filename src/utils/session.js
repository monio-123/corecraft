import { getMyMenuTree, getMyProfile } from '../api/user'
import { ref } from 'vue'
import request from './request'
import { getMenuPaths, getMenuTree, getToken, onAuthChange, savePermissions, saveMenuTree } from './auth'
import { createLoadSlot, loadOnce, resetSlot } from './lazyLoad'
import { loadRemotePrefs } from './uiPrefs'

/**
 * 会话引导的加载状态。
 *
 * **两个壳共用这一份**：它们都要回答同一件事 ——"用户资料还没到的时候，
 * 那一格该显示什么"。各写一份就是两份真相，改一边另一边还照旧撒谎。
 */
const sessionSlot = createLoadSlot()
export const sessionState = sessionSlot.state

/**
 * 当前会话的用户资料（**响应式，两个壳共用这一份**）。
 *
 * **为什么提到模块级**：改完个人资料要写回会话 —— 各壳自己存一份的话，
 * 写回这件事就得写两遍（而且两边可能不同步）。
 * 提上来之后，"资料"这件事全站只有一份，写回只有一个出口。
 */
export const sessionProfile = ref({})

/** 把改完的资料合并进当前会话（改个人资料、传头像都走这里） */
export function applyProfile(patch) {
  sessionProfile.value = { ...sessionProfile.value, ...patch }
}

/**
 * 那一格显示的用户名。**两个壳共用。**
 *
 * **没有资料时不能说「未登录」**：真没登录时 401 拦截器已经 clearAuth + 弹到登录页了，
 * 能站在这里就说明 token 是好的 —— 那一格实际含义是"资料还没加载出来"。
 * 加载中就留空（只剩头像），失败就说"加载失败"。
 */
export function sessionUserName(profile) {
  return profile?.nickname || profile?.username
    || (sessionState.value === 'failed' ? '加载失败' : '')
}

/**
 * 会话引导：拉当前用户的资料 / 菜单树 / 权限，并写入本地缓存。
 *
 * 两个壳（Layout.vue / AppLayout.vue）启动时都要这套数据，抽出来只留一份。
 * 同一时刻并发调用共享同一个在途请求——两个壳同时挂载也不会打两次接口。
 * 路由守卫也会在判权限前调它，保证硬刷新进来时菜单已经就位。
 *
 * ⚠️ 这份快照是模块级内存，clearAuth() 只清得到 localStorage。
 * 换账号时不订阅 onAuthChange，B 会直接继承 A 的资料和菜单权限（严重度见 FEATURES.md）。
 */
onAuthChange(() => {
  resetSlot(sessionSlot)
  // 资料也清：换了账号还留着上一个人的昵称/头像，
  // 站在页面上看着像是"登录没换成功"（会话引导本身的注释也记着这个坑）。
  sessionProfile.value = {}
})

export function isSessionReady() {
  return sessionSlot.state.value === 'ready'
}

export function bootstrapSession({ force = false } = {}) {
  return loadOnce(sessionSlot, force, async (isCurrent) => {
    // 记下发起时的登录态：请求在途中换了账号，这批资料就属于旧账号，不能写进缓存
    const token = getToken()
    const [menuRes, profileRes, permissionsRes] = await Promise.all([
      getMyMenuTree(),
      getMyProfile(),
      request.get('/sys/user/me/permissions')
    ])
    // 换账号了：这批资料作废（返回 undefined = "这次没拿到"，状态留在 failed，下次能重试）
    if (getToken() !== token) return undefined
    // **只有最新发起的那次才落缓存**（见 lazyLoad 的「版本号」）：旧的请求晚回来时，
    // 它带的是更早的菜单/权限 ⇒ 落进去等于把新的那份覆盖掉。
    if (!isCurrent()) {
      return {
        menuTree: getMenuTree(),
        menuPaths: getMenuPaths(),
        profile: sessionProfile.value
      }
    }
    saveMenuTree(menuRes?.data || [])
    // 只缓存权限码。角色在下面那行 sessionProfile 里（/me/profile 的响应带 roles），
    // 不在这里另存一份 —— 详见 utils/auth.js 的 savePermissions 注释。
    savePermissions(permissionsRes?.data?.permissions || [])
    sessionProfile.value = profileRes?.data || {}
    // 界面偏好（侧边栏宽度、正文字号……）：**后端才是真相**，本地只留缓存。
    // 放在会话引导里，是因为它也得等登录态才拿得到（每个用户自己那份），
    // 而且引导是在守卫里 await 的 ⇒ 页面出来时偏好已经就位，不会先闪一下默认值。
    // 拉不到不牵连引导：界面偏好在后端挂了不该拦住用户干活（loadRemotePrefs 自己吞掉）。
    loadRemotePrefs()
    return {
      menuTree: getMenuTree(),
      menuPaths: getMenuPaths(),
      profile: sessionProfile.value
    }
  })
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
