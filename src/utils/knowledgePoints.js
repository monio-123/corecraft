/**
 * 拼**喂给模型**的材料（纯函数，**只有一份定义**）。
 *
 * <p>2026-11-10 起，这个文件只剩这一件事 —— 原来的「解析 AI 输出」（`parsePoints`）、
 * 「正文过短过滤」、「`[[标题]]` 换链接」、「打标签的材料/解析」、「目录链接校验」
 * 六个函数随**前端本地生成循环**一起删掉了：
 *
 * - 正文解析 / 过短过滤 / 落库都由**服务端任务**做（`KpGenTaskServiceImpl`）
 * - `[[标题]]` 换链接改由**阅读页自动链接**做（`components/kbAutoLink.js`），正文一个字不动
 * - 打标签功能已下线
 * - 目录链接：模型不再被要求写目录链接，`fixCategoryLinks` 没有调用方
 *
 * <p>⚠️ **材料这一份必须留下**：它是"喂给模型的那段话"的唯一权威。
 * 服务端不拼材料（拼了就是第二份实现，必然和这份漂移），弹窗算好随任务提交上去。
 */

import { outlineUnder } from './outlineTree.js'

/**
 * 给模型的材料：主题 / 目录 / 要写什么 / 已经写过什么 / 下级章节。
 *
 * @param {object} o
 * @param {string} o.topic 主题（空 = 没有主题）
 * @param {string} o.dirPath 目录路径
 * @param {Array<string>} o.titles 这次要写的知识点标题
 * @param {string} [o.intent] **这条的定位**（一句话：讲什么、讲到什么程度），
 *        规划阶段产出；**空 = 没有定位**（材料里就不出现这一段）
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
 *        （正文里提到哪一个可以写成 [[标题]]，**落库后由阅读页现算成链接**）
 * @returns {string}
 */
export function buildMaterial({
  topic = '',
  dirPath = '',
  titles = [],
  intent = '',
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
  // **这条的定位**：规划阶段产出的「讲什么、讲到什么程度」。
  // 放在标题后面 —— 模型先知道要写哪条，再知道这条该写多深。
  if (String(intent).trim()) {
    parts.push(`这条要讲什么：${String(intent).trim()}`)
  }
  // **同目录的其它知识点**：一次只写一条时给的。
  // 列出来是为了**别写成同一件事**；但**这次不写它们** —— 标题清单只认上面那一条。
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
  // 阅读页会把它现算成链接（改名、移动、后建都能跟上）。
  if (allPoints.length) {
    parts.push(
      `这次生成结构里的全部知识点（正文里提到清单里的名字时可以写成 [[名字]]，系统会把它变成链接）：\n` +
        allPoints.map((p) => `- ${p.dirPath} / ${p.title}`).join('\n')
    )
  }
  return parts.join('\n\n')
}
