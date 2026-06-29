const TOKEN_KEY = 'token'
const TOKEN_TYPE_KEY = 'token_type'
const EXPIRES_IN_KEY = 'expires_in'
const PERMISSIONS_KEY = 'me_permissions'
const ROLES_KEY = 'me_roles'
const MENU_TREE_KEY = 'me_menu_tree'
const MENU_PATHS_KEY = 'me_menu_paths'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function getTokenType() {
  return localStorage.getItem(TOKEN_TYPE_KEY)
}

export function hasToken() {
  return Boolean(getToken())
}

export function saveAuth(payload = {}) {
  localStorage.setItem(TOKEN_KEY, payload.access_token || '')
  localStorage.setItem(TOKEN_TYPE_KEY, payload.token_type || 'Bearer')
  localStorage.setItem(EXPIRES_IN_KEY, String(payload.expires_in || ''))
}

export function saveAuthorizationProfile(profile = {}) {
  localStorage.setItem(PERMISSIONS_KEY, JSON.stringify(profile.permissions || []))
  localStorage.setItem(ROLES_KEY, JSON.stringify(profile.roles || []))
}

export function getPermissions() {
  try {
    return JSON.parse(localStorage.getItem(PERMISSIONS_KEY) || '[]')
  } catch {
    return []
  }
}

export function getRoles() {
  try {
    return JSON.parse(localStorage.getItem(ROLES_KEY) || '[]')
  } catch {
    return []
  }
}

export function hasPermission(code) {
  return getPermissions().includes(code)
}

const ADMIN_ROLES = ['ROLE_ADMIN', 'ADMIN']

export function isAdmin() {
  return getRoles().some((r) => ADMIN_ROLES.includes(r))
}

export function isAdminRoleCode(code) {
  return ADMIN_ROLES.includes(code)
}

export function saveMenuTree(menuTree = []) {
  localStorage.setItem(MENU_TREE_KEY, JSON.stringify(menuTree))
  const paths = []
  const walk = (nodes = []) => {
    nodes.forEach((node) => {
      let meta = {}
      try {
        meta = node?.meta ? JSON.parse(node.meta) : {}
      } catch {
        meta = {}
      }
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
}
