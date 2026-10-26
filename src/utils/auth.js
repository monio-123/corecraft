import { parseMeta } from './menu'

const TOKEN_KEY = 'token'
const TOKEN_TYPE_KEY = 'token_type'
const EXPIRES_IN_KEY = 'expires_in'
const PERMISSIONS_KEY = 'me_permissions'
const MENU_TREE_KEY = 'me_menu_tree'
const MENU_PATHS_KEY = 'me_menu_paths'

/**
 * 登录态变更订阅。
 *
 * localStorage 清得掉，但各模块的**内存缓存**清不掉（utils/session.js 的 snapshot、
 * useCorecraftWebStore 的 topics/tags/categories），它们都是模块级 let/const，
 * 只有整页刷新才会重建。后果是「A 账号退出 → B 账号登录」时 B 继承 A 的资料、菜单和业务数据。
 *
 * 登录态只有两个函数能改：saveAuth（登录成功）和 clearAuth（登出 / 401 / 改完密码），
 * 所以在**这两个源头**发通知，新增缓存只要订阅一次就自动跟着对，不用每条路径各写一遍。
 */
const authListeners = new Set()

export function onAuthChange(listener) {
  authListeners.add(listener)
}

function fireAuthChange() {
  authListeners.forEach((listener) => listener())
}

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function getTokenType() {
  return localStorage.getItem(TOKEN_TYPE_KEY)
}

export function hasToken() {
  return Boolean(getToken())
}

/**
 * 拼 Authorization 头。
 *
 * **收敛点**：axios 拦截器（utils/request.js）和 AI 流式调用（api/ai.js 的 streamChat，
 * 走原生 fetch）都要这行。流式那条不能走 axios —— 全局 `timeout: 5000` 会把流掐断，
 * 于是这里成了第 2 处出现。两处各拼一遍，token 格式一改就只改一处、另一处悄悄失效。
 */
export function authHeaders() {
  const token = getToken()
  const tokenType = getTokenType()
  return token && tokenType ? { Authorization: `${tokenType} ${token}` } : {}
}

export function saveAuth(payload = {}) {
  localStorage.setItem(TOKEN_KEY, payload.access_token || '')
  localStorage.setItem(TOKEN_TYPE_KEY, payload.token_type || 'Bearer')
  localStorage.setItem(EXPIRES_IN_KEY, String(payload.expires_in || ''))
  // 换 token = 换登录态，上一个账号的内存缓存全部作废
  fireAuthChange()
}

/**
 * 缓存权限码（`hasPermission()` 要同步读它，所以放 localStorage）。
 *
 * **只缓存 permissions，不缓存 roles**（2026-10-21 删）：
 * 当前用户的角色在 `/me/profile` 的响应里就有（`SysUserProfileResp.roles`），
 * 走 `utils/session.js` 的 `sessionProfile` 那一路。
 * 原来这里还写一份 `me_roles`，而 `getRoles()` **零调用** ——
 * 同一个数据的第二个副本、从来没被读过，还占了 localStorage 一个键。
 */
export function savePermissions(permissions = []) {
  localStorage.setItem(PERMISSIONS_KEY, JSON.stringify(permissions || []))
}

export function getPermissions() {
  try {
    return JSON.parse(localStorage.getItem(PERMISSIONS_KEY) || '[]')
  } catch {
    return []
  }
}

export function hasPermission(code) {
  return getPermissions().includes(code)
}

export function saveMenuTree(menuTree = []) {
  localStorage.setItem(MENU_TREE_KEY, JSON.stringify(menuTree))
  const paths = []
  const walk = (nodes = []) => {
    nodes.forEach((node) => {
      // meta 怎么解析：**menu.js 那一份是权威**（脏 JSON 当空对象）。
      // 这里原来自己写了一遍 try/catch，规则一样但改一处要记得改另一处。
      const meta = parseMeta(node?.meta)
      if (meta?.path) {
        paths.push(meta.path)
      }
      if (node?.children?.length) {
        walk(node.children)
      }
    })
  }
  walk(menuTree)
  localStorage.setItem(MENU_PATHS_KEY, JSON.stringify(paths))
}

export function getMenuTree() {
  try {
    return JSON.parse(localStorage.getItem(MENU_TREE_KEY) || '[]')
  } catch {
    return []
  }
}

export function getMenuPaths() {
  try {
    return JSON.parse(localStorage.getItem(MENU_PATHS_KEY) || '[]')
  } catch {
    return []
  }
}

export function clearAuth() {
  localStorage.clear()
  // 内存缓存跟着一起丢：见 onAuthChange 的说明
  fireAuthChange()
}
