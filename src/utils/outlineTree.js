/**
 * 目录树的解析与转换（**纯函数**，Node 里也能直接跑 ⇒ 能单独自测）。
 *
 * ## 认三种格式（markdown 标题 / 缩进列表 / 树形符号）
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
 * **AI 生成的那棵是树形符号那个格式**（提示词里写死的）⇒ 一个解析器、两条路共用。
 * 用户粘贴则认 markdown 标题/列表（从 Typora / Notion / ChatGPT 复制出来就是它），
 * 也认纯缩进和多级编号。围栏（```）会被跳过，粘贴带不带围栏都行。
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
 * 骨架**永远有知识点那层**（名字是结构的一部分）⇒「只要目录」不是这个格式的用法。
 *
 * ## 层数
 *
 * **不限层数**。树想多深就多深 —— 后端也不拦（`kp_category` 是邻接表，
 * 层数只是数据；侧边栏的缩进撑不住是显示问题，不是数据问题）。
 *
 * 层数由**生成骨架时给模型的材料**决定（材料里写「层数上限：N」）。
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

/** markdown 标题：`## 名字`（要求 # 后面有空格，避免把 `#标签` 当标题） */
const MD_HEADING = /^(#{1,6})\s+(.+?)\s*#*$/
/** markdown 列表项：`- 名字` / `* 名字` / `1. 名字`（缩进 2 空格一层） */
const MD_ITEM = /^(\s*)([-*+]|\d+[.)])\s+(.+)$/
/** 多级编号：`1.1 名字` / `2.3.3 名字`（段数-1 = 层级）。单级 `1.` 不算，那种通常还靠缩进 */
const DOTTED = /^(\d+(?:\.\d+)+)[.)、]?\s+(.+)$/

/**
 * 一行 → `{ level, name, kind }`。`level` **只用来判父子**（比大小），不是真实层数。
 *
 * ## 认四种写法（用户粘什么进来都尽量认）
 *
 * | 写法 | 例 | 层级怎么算 |
 * |---|---|---|
 * | markdown 标题 | `## 内存结构` | `#` 的个数 |
 * | markdown 列表 | `  - 程序计数器` | 上级标题 + 1 + 缩进/2 |
 * | 树形符号 | `│   ├── 程序计数器` | 名字起始列 / 4（一层 4 个字符） |
 * | 纯缩进 | `    程序计数器` | 缩进 / 4 |
 * | 多级编号 | `2.3.3 低延迟收集器` | 段数 - 1 |
 *
 * ## 为什么不再只认树形符号
 *
 * 以前只认 `├──/└──`：用户从笔记里复制一份 markdown 大纲粘进来，**整棵树会塌成一层**
 * （每行都被当根），看起来像"粘贴没生效"。而这几个格式里 **markdown 标题/列表才是最通用的**
 * —— 从 Typora / Notion / ChatGPT 复制出来的就是它。树形符号继续认，是为了兼容老数据
 * 和 AI 自己产出的那棵树（提示词里写死的是树形）。
 */
function splitLine(line, headingLevel) {
  const raw = line.replace(/\t/g, '    ')
  const lead = raw.length - raw.trimStart().length
  const body = raw.trim()

  // ① markdown 标题
  const h = body.match(MD_HEADING)
  if (h) return { level: h[1].length, name: h[2].trim(), kind: 'head' }

  // ② markdown 列表：挂在"最近一个标题"下面，再按缩进往下走
  const it = raw.match(MD_ITEM)
  if (it) {
    return {
      level: headingLevel + 1 + Math.floor(it[1].length / 2),
      name: it[3].trim(),
      kind: 'item'
    }
  }

  // ③ 树形符号：名字起始的那一列决定层级
  const mark = raw.search(/[├└][─-]/)
  if (mark >= 0) {
    const marker = raw.slice(mark).match(/^[├└][─-]+\s*/)[0]
    return {
      level: 1 + Math.ceil((mark + marker.length) / INDENT),
      name: raw.slice(mark + marker.length).trim(),
      kind: 'tree'
    }
  }

  // ④ 多级编号（不带缩进时）：`1.1` 是一层，`2.3.3` 是两层
  const d = body.match(DOTTED)
  if (d && lead === 0) return { level: d[1].split('.').length - 1, name: body, kind: 'number' }

  // ⑤ 纯缩进（4 空格 / 一个 tab 一层）
  if (lead > 0) return { level: 1 + Math.ceil(lead / INDENT), name: body, kind: 'plain' }

  return { level: 0, name: body, kind: 'root' }
}

/**
 * 解析成一片森林（可以有多棵树）。
 *
 * @param {string} text markdown 树文本
 * @returns {Array<{name: string, children: Array}>}
 */
export function parseOutlineTree(text) {
  const roots = []
  // 栈：最近见到的各级节点（层级 + 节点），用来认父
  const stack = []
  // 最近一个 markdown 标题的级别：列表项要挂在它下面（`## A` 后面的 `- x` 是 A 的子节点）
  let headingLevel = 0
  for (const line of String(text || '').split('\n')) {
    const t = line.trim()
    if (!t) continue
    if (t.startsWith('```')) continue // 围栏
    const { level, name, kind } = splitLine(line, headingLevel)
    if (kind === 'head') headingLevel = level
    if (!name) continue
    const node = { name, children: [] }
    while (stack.length && stack[stack.length - 1].level >= level) stack.pop()
    if (stack.length) {
      stack[stack.length - 1].node.children.push(node)
    } else {
      roots.push(node)
    }
    stack.push({ node, level })
  }
  return roots
}

/**
 * **走一遍树**（通用遍历）。
 *
 * <p>⚠️ **只有这一份遍历范式**：骨架树要在好几个地方走（拆意图 / 收集定位 /
 * 建目录对 id），各写一份递归的话改一边另一边就漂 —— 而且漂了不报错
 * （表现是"这一批的定位丢了"，界面上什么异常都看不到）。
 *
 * <p>**路径按 `joinPath` 拼**（`a/b/c`）⇒ 与 `importPlan` 的 `points[].dirPath` 同形。
 *
 * @param {Array} nodes 子节点数组（`parseOutlineTree` / `markOutline` 的结果）
 * @param {(node:object, path:string, depth:number, parentPath:string) => (void|false)} visit
 *        访问每个节点；**返回 false 表示不再往下走这一支**（剪枝）。
 *        `parentPath` = 父节点的路径（顶层是空串）—— 要顺着父链查东西的用它。
 * @param {string} [path] 父路径（递归用，外部不用传）
 * @param {number} [depth] 当前层（递归用，外部不用传）
 */
export function walkTree(nodes, visit, path = '', depth = 1) {
  for (const n of nodes || []) {
    const p = path ? joinPath(path, n.name) : n.name
    if (visit(n, p, depth, path) === false) continue
    walkTree(n.children, visit, p, depth + 1)
  }
}

/**
 * 给树打标记：哪些是知识点（叶子）、哪些已经存在、默认勾不勾。
 *
 * @param {Array} forest `parseOutlineTree` 的结果
 * @param {Array} categories 现有目录 `[{ id, name, parentId }]`
 * @param {{id: number}} mount 挂载点：`id` = 挂在谁底下（0 = 挂根）
 * @returns {Array} 每个节点多了 `exists` / `leaf` / `checked` / `depth`
 */
export function markOutline(forest, categories, mount = { id: 0 }) {
  // 现有目录按「父 id → 名字 → id」索引
  const index = indexByParentName(categories)
  const mountId = Number(mount.id || 0)
  const walk = (nodes, parentKey, depth) =>
    (nodes || []).map((n) => {
      // 父目录在已有目录里（parentKey 是个 id）⇒ 判重名要看那个 id 底下的
      const hit = parentKey == null ? undefined : (index.get(parentKey) || new Map()).get(n.name)
      const exists = hit !== undefined
      // 已存在 ⇒ 它的下级挂到**已有那个目录**底下（合并）；不存在 ⇒ 下级全是新的，不会有重名
      const children = walk(n.children, exists ? hit : null, depth + 1)
      const leaf = children.length === 0
      return {
        ...n,
        children,
        depth,
        exists,
        leaf,
        // **默认全勾**：目录要建、知识点要写，两个都是"这批要的东西"。
        checked: true
      }
    })
  return walk(forest, mountId, 1)
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
 * <p>**只算真正会落库的那些**：没勾的节点不进计划，它们的超长**不拦**任何人。
 * 没勾的目录 ⇒ 整支（它的子目录 + 它的知识点）都不处理 —— 父目录不建，
 * 知识点挂哪儿去。
 *
 * @param {Array} marked `markOutline` 的结果
 * @returns {{
 *   dirs: Array, points: Array, orphan: Array,
 *   dirCount: number, pointCount: number,
 *   tooLong: Array, tooMany: number
 * }}
 */
export function importPlan(marked) {
  const points = []
  const orphan = []
  const build = (nodes, path) => {
    const out = []
    for (const n of nodes || []) {
      const p = joinPath(path, n.name)
      // **是不是知识点**：判据在 `markOutline` 那儿定好了（`leaf`）——
      // 这边只读，不再自己算一遍"没有下级就是知识点"。
      if (n.leaf) {
        // 叶子 = 知识点：挂在 path 那个目录下。
        // 没有任何目录祖先 ⇒ 无处可挂（orphan），界面要说出来，不能静默丢。
        if (n.checked) {
          if (path) points.push({ dirPath: path, title: n.name })
          else orphan.push(n.name)
        }
        continue
      }
      // 目录没勾 ⇒ 整支不要（它的子目录和它的知识点都不处理）。
      if (!n.checked) continue
      out.push({ name: n.name, children: build(n.children, p) })
    }
    return out
  }
  const dirs = build(marked, '')
  let dirCount = 0
  const dirPaths = []
  const walkDir = (list, path) => {
    for (const n of list) {
      const p = joinPath(path, n.name)
      dirCount += 1
      dirPaths.push({ name: n.name, path: p })
      walkDir(n.children, p)
    }
  }
  walkDir(dirs, '')
  return {
    dirs,
    points,
    orphan,
    dirCount,
    pointCount: points.length,
    // 目录名超长（列长 50）—— 知识点标题不受这个限（它落在 title 列，更长）
    tooLong: dirPaths.filter((d) => d.name.length > MAX_NAME),
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
