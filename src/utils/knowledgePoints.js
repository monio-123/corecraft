/**
 * 知识点正文这一块：**解析 AI 的输出** + **拼喂给 AI 的材料**（都是纯函数）。
 *
 * ## AI 的输出长什么样
 *
 * ```text
 * ## 进程与线程的区别
 * 进程是资源分配的基本单位……
 * ### 对比
 * | 维度 | 进程 | 线程 |
 *
 * ## 进程的状态转换
 * 就绪 → 运行 → 阻塞……
 * ```
 *
 * 每条知识点以 `## 标题` 开头，正文里的**小标题用 `###`**（提示词里写死了，
 * 这样解析器只认一层 `##`，正文的小标题不会把一条知识点切碎）。
 *
 * ## 标题一律用清单里的
 *
 * 模型可能把标题改一个字、加个空格。**落库的标题必须是骨架里那一份**
 * （否则目录里挂着的知识点和骨架对不上）。所以解析出来的段落**按标题**对齐
 * 清单（不是按位置），标题取清单的，不取模型写的。
 *
 * ## 为什么不按位置对齐
 *
 * 按位置对齐（第 n 段配第 n 个标题）看着简单，但模型**换了顺序**就会把 A 的
 * 正文挂到 B 的标题上，**而且不报错** —— 卡片看着有正文，只是讲的是别的东西，
 * 这种错配最难查。总览那条最容易被挪到最后，所以改成**先按标题找**。
 */

// ⚠️ 这里**带扩展名**：项目里其它地方都是不带的（Vite 能解析），
// 但这一份要被 Node 直接 import 跑自测（Node 的 ESM 不认不带扩展名的相对路径）。
import { outlineUnder } from './outlineTree.js'
import { CATEGORY_HREF_PREFIX, topicHref } from './categoryNav.js'

/**
 * 解析 AI 写的知识点正文。
 *
 * @param {string} text AI 的原始输出
 * @param {Array<string>} expectedTitles 骨架给的知识点标题清单（空 = 让模型自己定）
 * @returns {Array<{title: string, content: string}>}
 */
export function parsePoints(text, expectedTitles = []) {
  const wanted = (expectedTitles || []).map((t) => String(t).trim()).filter(Boolean)
  const segments = []
  let cur = null
  const flush = () => {
    if (!cur) return
    const content = cur.lines.join('\n').trim()
    if (content) segments.push({ title: cur.title, content })
    cur = null
  }
  for (const line of String(text || '').split('\n')) {
    const m = line.match(/^##\s+(.+?)\s*$/)
    const title = m ? m[1].trim() : ''
    if (title) {
      flush()
      cur = { title, lines: [] }
      continue
    }
    if (cur) cur.lines.push(line)
  }
  flush()
  if (!wanted.length) return segments
  // **按标题对齐，不按位置**：模型可能把两条的顺序换了（总览那条常被挪到最后），
  // 按位置对齐会把 A 的正文挂到 B 的标题上，**且不报错**。
  const used = new Array(segments.length).fill(false)
  const hit = new Array(wanted.length).fill(-1)
  for (let i = 0; i < wanted.length; i++) {
    let k = segments.findIndex((s, j) => !used[j] && s.title === wanted[i])
    if (k < 0) k = segments.findIndex((s, j) => !used[j] && normTitle(s.title) === normTitle(wanted[i]))
    if (k >= 0) {
      used[k] = true
      hit[i] = k
    }
  }
  // 标题对不上但**数量对得上** ⇒ 模型只是改了标题写法 ⇒ 剩下按它写的顺序补位。
  // 数量也对不上 ⇒ 不补（宁可缺一条，也不把别的内容塞到错的标题下），
  // 缺的那些由界面报"这次没生成"。
  const rest = segments.map((_, k) => k).filter((k) => !used[k])
  const missing = hit.map((h, i) => (h < 0 ? i : -1)).filter((i) => i >= 0)
  if (missing.length && rest.length === missing.length) {
    missing.forEach((i, n) => { hit[i] = rest[n] })
  }
  const out = []
  for (let i = 0; i < wanted.length; i++) {
    const k = hit[i]
    if (k < 0) continue
    out.push({ title: wanted[i], content: segments[k].content })
  }
  return out
}

/**
 * 标题归一化：去空白、去 markdown 修饰，让 `**进程定义**`、`进程 定义`
 * 和 `进程定义` 认成同一个标题。
 */
function normTitle(s) {
  return String(s || '')
    .replace(/\s+/g, '')
    .replace(/[*_`]/g, '')
    .toLowerCase()
}

/**
 * 正文太短的门槛：**低于这个字数不落库**。
 *
 * <p>模型有时候不写正文，直接把"材料里没写"当成正文写进卡片。这种卡片进了库，
 * 后面检索、抽题都会拿到它 —— 比没有还糟。所以**短的直接不落库**，并在结果行里
 * 报出来，让用户知道哪一节没写出来（可以单独重写那个目录）。
 *
 * <p>**200 是底线**：提示词里也写着「至少 200 字」——两边同一个数。
 * 提示词那边是**给模型看的下限**（它自己就不该写太短），这边是**落库的门槛**
 * （模型不听话时的兜底）。改这个数记得两处一起改。
 */
export const MIN_CONTENT = 200

/**
 * 把解析出来的知识点拆成「该落库的」和「正文过短的」。
 *
 * <p>**一处定义**：弹窗落库前用它拦一道，验证脚本用同一个函数 —— 规则只写一份，
 * 脚本才能证明界面这条路走得通。
 *
 * @param {Array<{title: string, content: string}>} points 解析出来的知识点
 * @returns {{keep: Array, short: Array}} keep = 该落库的；short = 过短的（带长度）
 */
export function splitShortContent(points = []) {
  const keep = []
  const short = []
  for (const p of points || []) {
    const len = String(p?.content || '').trim().length
    if (len < MIN_CONTENT) short.push({ ...p, length: len })
    else keep.push(p)
  }
  return { keep, short }
}

/**
 * 给模型的材料：主题 / 目录 / 要写什么 / 已经写过什么 / 下级章节。
 *
 * <p>**只有一份定义** —— 弹窗和验证脚本都用它。脚本要能复现"界面上喂给模型的
 * 那段话"，不然验证的就不是同一件事（材料一漂，验证结果就作废）。
 *
 * @param {object} o
 * @param {string} o.topic 主题（空 = 没有主题）
 * @param {string} o.dirPath 目录路径
 * @param {Array<string>} o.titles 这次要写的知识点标题
 * @param {Array<string>} [o.siblings] 同目录的**其它**知识点（**不写它们**，只用来避免内容重复）
 * @param {Array<string>} o.written 已经写过的知识点标题（防重复）
 * @param {Array<string>} o.kids 这个目录**现有的**子目录名（别写它们管的领域）
 * @param {string} [o.extra] **补充说明**：用户自己写的一句话（"面向开发"、"面向考试"……）。
 *        模型照它调整写法和深度；**空 = 没有补充**
 * @param {string} [o.pref] **生成偏好**：整个系统通用的默认口味（系统配置里的那一项）。
 *        和「补充说明」是同一类东西的两种粒度 —— **补充说明是这次说的，偏好是一直这样**；
 *        **空 = 没配偏好**（材料里就不出现这一段）
 * @param {Object} o.node 这个目录在骨架里的节点（用来列下级章节和它们的知识点）
 * @param {(path: string) => string|null} [o.linkOf] 目录路径 → 链接地址；
 *        给了就在章节后面附上地址（总览知识点照着它写 markdown 链接）
 * @param {Array<{dirPath: string, title: string}>} [o.allPoints] **这次生成结构里的全部知识点**
 *        （正文里提到哪一个可以写成 [[标题]]，系统会把它变成链接）
 * @returns {string}
 */
export function buildMaterial({
  topic = '',
  dirPath = '',
  titles = [],
  siblings = [],
  written = [],
  kids = [],
  extra = '',
  pref = '',
  node = null,
  linkOf = null,
  allPoints = []
} = {}) {
  const parts = []
  if (String(topic).trim()) parts.push(`主题：${String(topic).trim()}`)
  // **补充说明**紧跟主题：它是对"这次要写什么"的整体限定
  // （面向开发 / 面向考试 / 只要原理不要例子……），放在最前面模型才拿它当总纲。
  if (String(extra).trim()) parts.push(`补充说明：${String(extra).trim()}`)
  // **生成偏好**：整个系统通用的默认口味（系统配置里的「生成偏好」那一项）。
  // 和补充说明是同一类东西的两种粒度 —— **补充说明是这次说的，偏好是一直这样**；
  // 两者都在最前面，模型才拿它们当总纲。
  if (String(pref).trim()) parts.push(`生成偏好（按它取舍）：\n${String(pref).trim()}`)
  parts.push(`目录：${dirPath}`)
  if (titles.length) {
    parts.push(`这次要写的知识点：\n${titles.map((t) => `- ${t}`).join('\n')}`)
  }
  // **同目录的其它知识点**：一次只写一条时给的（见 CategoryTreeDialog 的 runGroups）。
  // 列出来是为了**别写成同一件事**（原来一次问整个目录，模型看得见兄弟标题）；
  // 但**这次不写它们** —— 标题清单只认上面那一条。
  if (siblings.length) {
    parts.push(
      `同目录的其它知识点（**这次不要写它们**，列出来是为了避免内容重复）：\n${siblings.map((t) => `- ${t}`).join('\n')}`
    )
  }
  if (written.length) {
    parts.push(`这个主题下已经写过的知识点（不要重复）：\n${written.map((t) => `- ${t}`).join('\n')}`)
  }
  // 这个目录**现有的子目录**：模型要知道哪些领域已经有人管了，
  // 别把该属于子目录的内容写在这里（"集合"下面已有 List/Set/Map）。
  if (kids.length) {
    parts.push(`这个目录现有的子目录（不要写这些领域的内容）：\n${kids.map((k) => `- ${k}`).join('\n')}`)
  }
  // 下级章节 + 它们各自的知识点（**总览知识点要用**：正文写成索引）
  const structure = outlineUnder(node, { basePath: dirPath, linkOf })
  if (structure.length) {
    parts.push(
      `这个目录下面的章节和它们各自的知识点（总览知识点要把它们列出来）：\n${structure.join('\n')}`
    )
  }
  // **这次生成结构里的全部知识点**：正文里提到哪一个，可以写成 [[标题]]，
  // 落库后系统把它换成指向那条知识的链接。
  // ⇒ 模型得先知道**有哪些名字可标**，否则它只会写普通文字。
  if (allPoints.length) {
    parts.push(
      `这次生成结构里的全部知识点（正文里提到清单里的名字时可以写成 [[名字]]，系统会把它变成链接）：\n` +
        allPoints.map((p) => `- ${p.dirPath} / ${p.title}`).join('\n')
    )
  }
  return parts.join('\n\n')
}

/**
 * 正文里的知识点标记：`[[知识点标题]]` → `[知识点标题](/app/topic/<id>)`。
 *
 * <p>**为什么是标记后处理，而不是让模型直接写地址**：知识点的 id 是**落库那一刻**
 * 才有的，而正文是在落库之前写的 —— 模型写正文时根本不知道目标那条知识的 id。
 * ⇒ 模型只写名字（它知道名字），地址由这里在整批落库后统一补上。
 *
 * <p>**认不出来的退成普通文字**（跟目录链接 `fixCategoryLinks` 同一个规矩）：
 * 不在这次生成结构里、或者重名（不知道指哪一个）⇒ 只留文字。
 * 宁可少一个链接，也不要一个点进去是空的。
 *
 * @param {string} content 正文
 * @param {Map<string, number>} idByTitle 标题 → 知识点 id
 *        （**只含这次生成结构里、且标题不重名的**那些）
 * @returns {string}
 */
export function linkTopicMarks(content, idByTitle) {
  return String(content || '').replace(/\[\[([^\[\]\n]+)\]\]/g, (m, raw) => {
    const title = raw.trim()
    const id = idByTitle?.get(title)
    return id == null ? title : `[${title}](${topicHref(id)})`
  })
}

/**
 * 给「打标签」那一次调用的材料：这条知识自己。
 *
 * <p>**判据和「现有标签清单」不在这里**：那条提示词
 * （`ai.prompt.question.pick_tags:tags`）由后端 `appendInstruction` 取出来，
 * 顺带把用户现有的标签名附上（**统一叫法**就靠它）⇒ 这里只给"这条知识是什么"。
 *
 * <p>为什么**单独一次调用**、不塞进正文那次：标签是**跨知识点**的决策
 * （「索引」和「索引优化」只能留一个叫法），单看一条知识没有全局视野；
 * 而正文那次调用刚把输出上限的问题解决掉，再往里塞东西等于往超载的调用上加。
 *
 * @param {object} o
 * @param {string} o.title 这条知识的标题
 * @param {string} o.content 这条知识的正文
 * @param {string} [o.dirPath] 它挂在哪个目录（**主归属**，标签只装"主归属之外的那些"）
 * @returns {string}
 */
export function buildTagMaterial({ title, content, dirPath = '' } = {}) {
  const parts = []
  parts.push(`标题：${String(title || '').trim()}`)
  if (String(dirPath).trim()) parts.push(`所在目录：${String(dirPath).trim()}`)
  parts.push(`正文：\n${String(content || '')}`)
  return parts.join('\n\n')
}

/**
 * 解析标签：模型**每行写一个标签名**。
 *
 * <p>清洗三件事（模型偶尔会加）：
 * ① 列表符号 / 编号（`- xxx` / `1. xxx`）
 * ② 成对引号、括号（「xxx」「(xxx)」「xxx」）
 * ③ 「标签：」这种前缀
 *
 * <p>**「无」不算标签**：没有合适的标签时模型要写一行「无」
 * （提示词 `ai.prompt.knowledge.tags:tags` 的【格式】里定的）⇒
 * 这里把它滤掉，返回空数组 —— **"没有标签"和"调用失败"必须能分开**。
 *
 * <p>**长度上限 30**：标签是概念名（判据里"粒度要说清是哪种"），
 * 一行几十个字的多半是**正文**被抄进来了，不是标签 ⇒ 直接丢掉。
 *
 * <p>**最多 4 个**：判据里写的就是「数量 0 到 4 个，宁缺毋滥」⇒ 这里兜住，
 * 免得模型给 8 个。
 *
 * @param {string} text 模型的输出
 * @param {number} [max] 最多几个
 * @returns {string[]}
 */
export function parseTagNames(text, max = 4) {
  const out = []
  const seen = new Set()
  for (const raw of String(text || '').split('\n')) {
    let s = String(raw).trim()
    if (!s) continue
    s = s.replace(/^[-*•]\s*/, '')          // 列表符号
    s = s.replace(/^\d+[.、)]\s*/, '')        // 编号
    s = s.replace(/^标签\s*[:：]\s*/, '')       // 「标签：xxx」
    s = s.replace(/^[「『"'（(【[]+/, '')       // 前面的引号/括号
    s = s.replace(/[」』"'）)】\]]+$/, '')       // 后面的引号/括号
    s = s.trim()
    if (!s || s.length > 30) continue
    // 「无 / 无标签 / 没有 / none」= 没有合适的标签（不是标签名本身）
    if (/^(无|无标签|没有|没有合适的标签|none|n\/a|-)$/i.test(s)) continue
    if (seen.has(s)) continue
    seen.add(s)
    out.push(s)
    if (out.length >= max) break
  }
  return out
}

/**
 * 正文里的目录链接：**只认这次的目录**。
 *
 * <p>AI 可能把地址里的 id 写错 ⇒ 点进去是个空列表（不报错，只是没用）。
 * 认不出来的链接**退成普通文字**（文字还在，链接没了）—— 宁可少一个链接，
 * 也不要一个指错的链接。
 *
 * @param {string} content 正文
 * @param {Array<string>} validHrefs 这次允许的链接地址
 * @returns {string}
 */
export function fixCategoryLinks(content, validHrefs = []) {
  const valid = new Set(validHrefs)
  const re = new RegExp(`\\[([^\\]]*)\\]\\(${escapeRe(CATEGORY_HREF_PREFIX)}(\\d+)\\)`, 'g')
  return String(content || '').replace(re, (m, text, id) =>
    valid.has(CATEGORY_HREF_PREFIX + id) ? m : text
  )
}

/** 正则里的元字符要转义（地址里有 `?`） */
function escapeRe(s) {
  return String(s).replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}
