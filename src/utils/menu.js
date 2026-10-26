import * as Icons from '@element-plus/icons-vue'

/**
 * 菜单解析与"壳"分流。两个壳（AdminLayout / AppLayout）共用这一份规则，
 * 避免各写一套 parseMeta / 可见性判定。
 *
 * 全部是纯函数：menuTree / allowedPaths 由调用方传入。
 * 不直接读 localStorage —— localStorage 不是响应式的，读了也不会触发视图更新。
 */

/** 解析 sys_permission.meta（后端存 TEXT，可能是脏 JSON，解析失败当空对象） */
export function parseMeta(meta) {
  try {
    return meta ? JSON.parse(meta) : {}
  } catch {
    return {}
  }
}

export function getIconComponent(iconName) {
  return iconName && Icons[iconName] ? Icons[iconName] : null
}

/**
 * 占位路径（形如 /app/topic/:id）不能进导航：
 * 用户点了会跳到字面量 URL，触发 id 转数字的 NaN 问题。
 */
export function isPlaceholderPath(path) {
  return typeof path === 'string' && path.includes(':')
}

/**
 * 菜单项归属哪个壳：读 meta.shell。
 * 缺省 'admin' —— 历史菜单没有这个字段，行为保持不变。
 *
 * 由超管在资源管理页配置，决定这一页走管理后台还是业务界面。
 */
export function shellOf(meta) {
  return parseMeta(meta).shell === 'app' ? 'app' : 'admin'
}

/** 菜单项对当前用户是否可见：有 path + 非占位 + 在后端下发的 menuPaths 里 */
export function isVisibleToUser(node, allowedPaths = []) {
  const m = parseMeta(node.meta)
  return Boolean(m.path) && !isPlaceholderPath(m.path) && allowedPaths.includes(m.path)
}

/**
 * 菜单节点 → 归一化后的菜单项（MENU 档）。
 *
 * GROUP 下的子项和顶层菜单项要的是**逐字段相同**的形状（id / code / name /
 * type / meta / path / icon）——原来两处各写一份对象字面量，
 * 改字段要记得改两处；`parseMeta` 也被调了两遍（path 一次、icon 一次）。
 * 收到这里一份，两边都走它。
 */
function toMenuNode(node) {
  const meta = parseMeta(node.meta)
  return {
    id: node.id,
    code: node.code,
    name: node.name,
    type: 'MENU',
    meta: node.meta,
    path: meta.path,
    icon: meta.icon
  }
}

/**
 * 抽出某个壳下、当前用户可见的菜单树。
 * GROUP 保留分组结构（壳自己决定怎么渲染），MENU 拍平成叶子。
 *
 * ⚠️ **空 GROUP 直接丢掉**：分组里一个可见子项都没有 ⇒ 这个分组在界面上
 * 什么都不渲染（el-sub-menu 靠 children 撑开），留着只是白占一行。
 *
 * ⚠️ **输出上带着 `code` 和 `meta`**：admin 壳的 `el-sub-menu` 用 `group.code`
 * 当 index、图标和路径从 `meta` 里读。归一化时把它们抹掉，admin 壳就得自己
 * 再抄一遍解析 —— 那正是这份函数要消除的那件事。
 */
export function buildShellMenuTree(shell, menuTree = [], allowedPaths = []) {
  return menuTree
    .filter((item) => shellOf(item.meta) === shell)
    .map((item) => {
      if (item.type === 'GROUP') {
        const children = (item.children || [])
          .filter((c) => shellOf(c.meta) === shell && isVisibleToUser(c, allowedPaths))
          .map((c) => toMenuNode(c))
        if (!children.length) return null
        const meta = parseMeta(item.meta)
        return {
          id: item.id,
          code: item.code,
          name: item.name,
          meta: item.meta,
          icon: meta.icon,
          type: 'GROUP',
          children
        }
      }
      return isVisibleToUser(item, allowedPaths) ? toMenuNode(item) : null
    })
    .filter(Boolean)
}

/** 菜单树压平成导航项数组（侧边栏用） */
export function flattenShellNav(shell, menuTree = [], allowedPaths = []) {
  return buildShellMenuTree(shell, menuTree, allowedPaths).flatMap((group) =>
    group.type === 'GROUP' ? group.children : [group]
  )
}
