/**
 * 目录树的解析与转换（**纯函数**，Node 里也能直接跑 ⇒ 能单独自测）。
 *
 * ## 只认一种格式：markdown 树
 *
 * ```text
 * 操作系统
 * ├── 进程与线程
 * │   ├── 进程与线程的区别
 * │   └── 线程的调度策略
 * └── 内存管理
 *     └── 虚拟内存的工作原理
 * ```
 *
 * **AI 生成的那棵也必须是这个格式**（提示词里写死了）⇒ 一个解析器、两条路共用。
 * 围栏（```）会被跳过，粘贴带不带围栏都行。
 *
 * ## 叶子是知识点，不是目录
 *
 * 树里**没有下级的节点是知识点**（"线程的调度策略"）—— 那是要写正文的东西，
 * 不是目录。**目录 = 有下级的节点**，**知识点 = 叶子**。
 *
 * 所以这份树同时是两份清单：
 * - 目录清单 = 有下级的节点（要建出来）
 * - 知识点清单 = 叶子（要写正文，挂在它**直接的那个目录**下）
 *
 * ## 层数
 *
 * 目录最深 4 层（和 `语言/java/基础/序列化` 一样深）。超了不能建。
 *
 * ## 挂载点
 *
 * 树可以挂在某个**已有目录**下面（"在 语言/java 下补一片"）。挂载点决定了
 * 三件事：查重时跟谁比、这棵树的第一层算第几层、导给后端时建在谁底下。
 *
 * @module utils/outlineTree
 */

/** 一层的缩进宽度（`│   ` 或 4 个空格都算一层） */
const INDENT = 4

/** 目录名最长 50（后端 `kp_category.name` 的列长，也是 DTO 上的 @Size） */
export const MAX_NAME = 50

/** 目录最深几层。**和后端 `MAX_IMPORT_DEPTH` 是同一条约定**，改一处要改另一处。 */
export const MAX_DEPTH = 4

/** 一次最多导多少个目录（和后端 `MAX_IMPORT_NODES` 同一条约定） */
export const MAX_NODES = 300

/**
 * 目录路径：`a/b/c`。
 *
 * <p>**只有这一处拼**。路径是整条链的连接键：`importPlan` 的 `points[].dirPath`
 * → 按目录分组 → 拿目录 id → 拿章节树 → 生成目录链接，**全程靠它当 Map 的键**。
 * 改其中一处的分隔符 ⇒ Map 查不到 ⇒ 知识点挂不上目录、目录链接丢，**而且不报错**。
 *
 * @param {string} parent 上级目录路径（空 = 顶层）
 * @param {string} name 自己的名字
 */
export function joinPath(parent, name) {
  return parent ? `${parent}/${name}` : name
}

/**
 * 「父 id → 名字 → id」索引。
 *
 * <p>两处要的是同一份：判重名（`markOutline`）、把新建的目录对上 id
 * （`CategoryTreeDialog` 的 `resolveDirIds`）。结构一样、只是取名的字段不同，
 * 抄一份的话两边的"同名"口径会漂。
 *
 * @param {Array<{id:number,name:string,parentId:number}>} list
 * @returns {Map<number, Map<string, number>>}
 */
export function indexByParentName(list) {
  const index = new Map()
  for (const c of list || []) {
    const pk = Number(c.parentId || 0)
    if (!index.has(pk)) index.set(pk, new Map())
    index.get(pk).set(c.name, c.id)
  }
  return index
}

/**
 * 一行 → `{ depth, name }`。
 *
 * <p>层数按**名字起始的那一列**算：一层 = 4 个字符（`│   ` 或 4 个空格都算一层）。
 * 用字符位置而不是数竖线，是因为手写的树**两种缩进会混着来**——
 * `│   ├── X` 和 `    ├── X` 都是"往下一层"，数竖线会把后者算浅一层。
 */
function splitLine(line) {
  const raw = line.replace(/\t/g, '    ')
  // 树形符号的位置：名字从它后面开始
  const mark = raw.search(/[├└][─-]/)
  if (mark < 0) {
    // 没有树形符号 = 一棵树的根（也可能是一行废话，那行没下级 ⇒ 会被当知识点，不勾）
    return { depth: 0, name: raw.trim() }
  }
  const head = raw.slice(mark)
  const marker = head.match(/^[├└][─-]+\s*/)[0]
  const name = raw.slice(mark + marker.length).trim()
  // 名字起始的列 = 前缀有多长（含树形符号本身）
  const prefixLen = mark + marker.length
  return { depth: Math.ceil(prefixLen / INDENT), name }
}

/**
 * 解析成一片森林（可以有多棵树）。
 *
 * @param {string} text markdown 树文本
 * @returns {Array<{name: string, children: Array}>}
 */
export function parseOutlineTree(text) {
  const roots = []
  // 栈：最近见到的各级节点（深度 + 节点），用来认父
  const stack = []
  for (const line of String(text || '').split('\n')) {
    const t = line.trim()
    if (!t) continue
    if (t.startsWith('```')) continue // 围栏
    const { depth, name } = splitLine(line)
    if (!name) continue
    const node = { name, children: [] }
    while (stack.length && stack[stack.length - 1].depth >= depth) stack.pop()
    if (stack.length) {
      stack[stack.length - 1].node.children.push(node)
    } else {
      roots.push(node)
    }
    stack.push({ node, depth })
  }
  return roots
}

/**
 * 给树打标记：哪些是知识点（叶子）、哪些已经存在、哪些超过层数、默认勾不勾。
 *
 * @param {Array} forest `parseOutlineTree` 的结果
 * @param {Array} categories 现有目录 `[{ id, name, parentId }]`
 * @param {{id: number, depth: number}} mount 挂载点：`id` = 挂在谁底下（0 = 挂根），
 *        `depth` = 那个目录自己占几层（0 = 挂根）
 * @returns {Array} 每个节点多了 `exists` / `leaf` / `tooDeep` / `checked` / `depth`
 */
export function markOutline(forest, categories, mount = { id: 0, depth: 0 }) {
  // 现有目录按「父 id → 名字 → id」索引
  const index = indexByParentName(categories)
  const mountId = Number(mount.id || 0)
  const mountDepth = Number(mount.depth || 0)
  const walk = (nodes, parentKey, depth) =>
    (nodes || []).map((n) => {
      // 父目录在已有目录里（parentKey 是个 id）⇒ 判重名要看那个 id 底下的
      const hit = parentKey == null ? undefined : (index.get(parentKey) || new Map()).get(n.name)
      const exists = hit !== undefined
      // 已存在 ⇒ 它的下级挂到**已有那个目录**底下（合并）；不存在 ⇒ 下级全是新的，不会有重名
      const children = walk(n.children, exists ? hit : null, depth + 1)
      const leaf = children.length === 0
      // **层数只管目录**：知识点挂在目录下，不占目录的层数
      // （"线程的调度策略"挂在第 4 层的目录下，它自己不是第 5 层目录）。
      const tooDeep = !leaf && depth > MAX_DEPTH
      return {
        ...n,
        children,
        depth,
        exists,
        leaf,
        tooDeep,
        // **默认全勾**：目录要建、知识点要写，两个都是"这批要的东西"。
        // 超深的例外 —— 它建不了（叶子挂不到第 5 层以下的目录上）。
        checked: !tooDeep
      }
    })
  return walk(forest, mountId, mountDepth + 1)
}

/** 树里所有节点（打平） */
export function allNodes(marked) {
  const out = []
  const walk = (nodes) => {
    for (const n of nodes || []) {
      out.push(n)
      walk(n.children)
    }
  }
  walk(marked)
  return out
}

/**
 * 导入计划：**要建的目录 + 要写的知识点**。
 *
 * <p>这是**唯一的口径** —— 界面上"建什么 / 写什么"的统计、按钮能不能点、
 * 发给后端的目录结构、发给模型的知识点清单，全从这里出。
 *
 * <p>**只算真正会落库的那些**：没勾的节点不进计划，它们的超长/超深**不拦**任何人。
 * 没勾的目录 ⇒ 整支（它的子目录 + 它的知识点）都不处理 —— 父目录不建，
 * 知识点挂哪儿去。
 *
 * @param {Array} marked `markOutline` 的结果
 * @param {number} baseDepth 挂载点自己占几层（0 = 挂根）
 * @returns {{
 *   dirs: Array, points: Array, orphan: Array, dirNodes: Map,
 *   dirCount: number, pointCount: number, depth: number,
 *   tooLong: Array, tooDeep: Array, skippedDeep: Array, tooMany: number
 * }}
 */
export function importPlan(marked, baseDepth = 0) {
  const points = []
  const orphan = []
  /** 目录路径 → 目录节点。写总览知识点时要知道这个目录下面有哪些章节。 */
  const dirNodes = new Map()
  const build = (nodes, path) => {
    const out = []
    for (const n of nodes || []) {
      const p = joinPath(path, n.name)
      if (!n.children.length) {
        // 叶子 = 知识点：挂在 path 那个目录下。
        // 没有任何目录祖先 ⇒ 无处可挂（orphan），界面要说出来，不能静默丢。
        if (n.checked) {
          if (path) points.push({ dirPath: path, title: n.name })
          else orphan.push(n.name)
        }
        continue
      }
      // 目录没勾 ⇒ 整支不要（它的子目录和它的知识点都不处理）；
      // 超深 ⇒ 建不了，也不进计划（跟"没勾"一个待遇：计划里没有它）。
      if (!n.checked || n.tooDeep) continue
      dirNodes.set(p, n)
      out.push({ name: n.name, children: build(n.children, p) })
    }
    return out
  }
  const dirs = build(marked, '')
  let dirCount = 0
  let depth = baseDepth
  const dirPaths = []
  const walkDir = (list, d, path) => {
    for (const n of list) {
      const p = joinPath(path, n.name)
      dirCount += 1
      if (d > depth) depth = d
      dirPaths.push({ name: n.name, path: p })
      walkDir(n.children, d + 1, p)
    }
  }
  walkDir(dirs, baseDepth + 1, '')
  // **超深目录默认不勾**（建不了），所以要分成两种：
  //   - 勾上了 ⇒ 要拦（按钮禁用：它建不出来）
  //   - 没勾 ⇒ 跳过，但要告诉用户跳了几支（不许静默丢）
  const deep = allNodes(marked).filter((n) => n.tooDeep)
  return {
    dirs,
    points,
    orphan,
    dirNodes,
    dirCount,
    pointCount: points.length,
    depth,
    // 目录名超长（列长 50）—— 知识点标题不受这个限（它落在 title 列，更长）
    tooLong: dirPaths.filter((d) => d.name.length > MAX_NAME),
    tooDeep: deep.filter((n) => n.checked),
    skippedDeep: deep.filter((n) => !n.checked),
    // **一次最多建多少个目录**：和后端 `MAX_IMPORT_NODES` 是同一条约定。
    // 后端会拒，但那已经是"点完创建"之后的事了 ⇒ 前端先拦一道，
    // 按钮禁用 + 说清楚超了多少（0 = 没超）。
    tooMany: dirCount > MAX_NODES ? dirCount : 0
  }
}

/**
 * 一个目录下面的章节和它们各自的知识点（**总览知识点照着它写索引**）。
 *
 * <p>目录节点的 children 里**两样都有**：叶子是挂在这个目录下的知识点、
 * 有下级的是章节。这里**从章节开始列**，章节底下的知识点逐个列出来 ——
 * 「章节 → 它的知识点」这两层都要，缺一层总览就索引不全。
 *
 * <p>章节层自己**没有**下级章节 ⇒ 返回空 ⇒ 那层不给这段材料（它也不需要）。
 *
 * @param {Object} node 目录节点（`markOutline` 的结果）
 * @param {object} [opt]
 * @param {string} [opt.basePath] 这个目录自己的路径（章节路径要从它往下拼）
 * @param {(path: string) => string|null} [opt.linkOf] 目录路径 → 链接地址；
 *        给了就在章节后面附上地址（总览知识点照着它写 markdown 链接）
 * @returns {Array<string>} 缩进的结构清单
 */
export function outlineUnder(node, { basePath = '', linkOf = null } = {}) {
  if (!node) return []
  const lines = []
  const walk = (n, indent, path) => {
    const pad = '  '.repeat(indent)
    const p = joinPath(path, n.name)
    const href = linkOf ? linkOf(p) : null
    lines.push(`${pad}- 章节：${n.name}${href ? ` → ${href}` : ''}`)
    for (const c of n.children || []) {
      if (c.children.length) walk(c, indent + 1, p)
      else lines.push(`${pad}  - 知识点：${c.name}`)
    }
  }
  for (const c of node.children || []) {
    if (c.children.length) walk(c, 0, basePath)
  }
  return lines
}

/**
 * 现有的目录树 → **同一种 markdown 树格式**。
 *
 * <p>发给 AI 用：它看着你的体系长，就不会另起一套同义的名字。
 * 格式和 `parseOutlineTree` 认的**完全一样** ⇒ 模型照着写，我们照着读。
 *
 * <p>渲染约定和用户手写的一致：`├── ` / `└── `，中间的层用 `│   ` 占位、
 * 同一支的最后一行用 4 个空格占位。
 *
 * @param {Array} categories 现有目录 `[{ id, name, parentId }]`
 * @returns {string} markdown 树文本（没有目录时返回空串）
 */
export function renderOutlineTree(categories) {
  const byParent = new Map()
  for (const c of categories || []) {
    const pk = Number(c.parentId || 0)
    if (!byParent.has(pk)) byParent.set(pk, [])
    byParent.get(pk).push(c)
  }
  const lines = []
  const walk = (nodes, prefix, isRoot) => {
    nodes.forEach((n, i) => {
      const last = i === nodes.length - 1
      // **根没有树形符号**：和提示词里教的格式一致（顶层是光秃秃一行），
      // 别让模型照着"└── xxx"起头。
      lines.push(isRoot ? n.name : prefix + (last ? '└── ' : '├── ') + n.name)
      walk(byParent.get(n.id) || [], isRoot ? '' : prefix + (last ? '    ' : '│   '), false)
    })
  }
  walk(byParent.get(0) || [], '', true)
  return lines.join('\n')
}
