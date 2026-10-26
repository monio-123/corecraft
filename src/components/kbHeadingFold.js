/**
 * 标题折叠 + 文档大纲。
 *
 * ## 折叠状态存在哪：编辑器视图侧，不进正文
 *
 * markdown 没有"折叠"这个概念。**真存进正文**（比如写成 `<!--fold-->` 或
 * `:::collapsed`）会带来三个问题：
 *
 * 1. 正文要为一件事多一套语法，而这份正文**会被 AI 读**（问答、出题都喂
 *    `kp_topic.content`）——模型看到一堆折叠标记只会当成噪音；
 * 2. 折叠是**看的人临时决定的**，写进内容就变成"所有人都折叠"；
 * 3. 存进 attr 就会被序列化器写回 markdown，正文每次保存都脏一点。
 *
 * 所以折叠状态放在 **ProseMirror plugin state** 里，刷新即展开。
 * **这是有意的**：折叠是视图状态，不是内容。
 *
 * ## 折叠怎么画：必须用 Decoration，不能自己改 DOM
 *
 * 踩过的坑（实测出来的，不是猜的）：**在 `view.update` 里直接给 DOM 加 class，
 * 会把编辑器打进死循环**。
 *
 * ProseMirror 的 `MutationObserver` 盯着编辑器 DOM，**class 属性的变化它看得见**
 * （`registerMutation` 只忽略 root 节点和 `contenteditable` 这几个属性）。
 * 于是：
 *
 * ```
 * 我们改 DOM class → MutationObserver 触发 → flush() → updateState()
 *        → plugin 的 update() → 我们又改 DOM class → …
 * ```
 *
 * 浏览器主线程被这个循环占死，页面直接卡住（实测：带标题的文档一打开就白屏卡死）。
 *
 * 正解是 **Decoration** —— ProseMirror 自己的"只影响显示、不影响内容"的机制：
 * 装饰由 PM 施加，PM 自己知道这些 class 是它加的，**幂等**，不会自己触发自己。
 * 所以折叠做成 `props.decorations`，一行 DOM 都不自己碰。
 *
 * ## 装饰的定位：必须正好是「整个节点」
 *
 * PM 判断一个装饰属于哪个节点，看的是**这个节点在父节点里的起始位置**
 * （`updateChildren` 里：`deco.from <= offset && deco.to > offset`）。
 * 所以范围是 **[节点起始位置, 节点起始位置 + nodeSize]**。
 *
 * 差一位的后果不是报错，而是**装饰悄悄落到别的节点上**（或者谁都不落）——
 * 所以这里统一用 `pos + node.nodeSize`，`pos` 取自 `doc.descendants`。
 *
 * ## 折叠范围：markdown 的 heading 是平级块
 *
 * heading 的 content 是 `inline*`，装不下子块，所以"折叠子内容"不是嵌套，
 * 而是**把后面那段兄弟块藏起来**：
 *
 * - 范围 = 同一父容器里，该 heading **后面的兄弟块**，到**下一个同级或更高级的
 *   heading** 之前为止
 * - 范围内的每个块各加一个 `kb-folded-hidden` 装饰，CSS `display: none`
 *
 * ## 折叠状态的 key：heading 的 id
 *
 * 用 `attrs.id` 而不是位置：位置会随编辑漂移，id 不会。
 * `syncHeadingIdPlugin` 保证 id 唯一（同名标题会自动加 `-#2` 后缀），
 * 所以两个同名标题不会一起折叠。
 */

import { Plugin, PluginKey, TextSelection } from '@milkdown/prose/state'
import { Decoration, DecorationSet } from '@milkdown/prose/view'
import { $prose } from '@milkdown/utils'

const foldPluginKey = new PluginKey('KB_HEADING_FOLD')

const HEADING_SELECTOR = 'h1,h2,h3,h4,h5,h6'

/**
 * 扫一遍文档，拿到所有标题：id / 层级 / 位置 / 折叠范围。
 *
 * **全文件唯一算标题范围的地方** —— 正文要藏哪些块、大纲要藏哪些条目，
 * 规则只有这一份（两处各算一遍的话，改一边另一边不动，
 * 折叠起来正文和大纲就对不上了）。
 *
 * ## id 怎么算
 *
 * 规则和 milkdown 的 `syncHeadingIdPlugin` 一致：默认是标题文字小写、空格转 `-`，
 * **重名的依次加 `-#2`、`-#3`**。
 *
 * 为什么自己算而不直接用 `node.attrs.id`：
 * markdown 解析出来的标题 `attrs.id` 是**空的**，要等 `syncHeadingIdPlugin`
 * 在下一次 update 才补上。那之前拿到的 id 是空串 —— 大纲点了跳不过去，
 * 折叠也认错人。而 DOM 上写着的 id（`toDOM` 里 `id: node.attrs.id || getId(node)`）
 * **从一开始就是对的**，所以这边也按同一套规则算一份，两边就永远对得上。
 *
 * ## 为什么要扫两遍
 *
 * 一个标题的范围（它罩着哪些下级）里，**后代的 id 必须在所有标题的 id 都算完之后才知道**
 * —— 后代在文档里排在后面，第一遍遍历到它时还没编号。
 * 所以：第一遍编号建表，第二遍按范围取后代（查表 O(1)）。
 */
function scanHeadings(doc) {
  // ---------- 第一遍：标题表（id / 层级 / 位置 / 父容器里的序号） ----------
  const list = []
  const used = new Map()
  doc.descendants((node, pos, parent) => {
    if (node.type.name !== 'heading') return true
    // 空标题不参与去重（syncHeadingIdPlugin 也是直接跳过它）
    let id = ''
    if (node.textContent.trim()) {
      const base = node.textContent.toLowerCase().trim().replace(/\s+/g, '-')
      const n = (used.get(base) || 0) + 1
      used.set(base, n)
      id = n === 1 ? base : `${base}-#${n}`
    }
    const $pos = doc.resolve(pos)
    // $pos 现在停在父容器里、这个节点的前面 → depth 是父的深度，index 是它在父里的序号
    const index = $pos.index($pos.depth)
    list.push({ node, pos, id, level: node.attrs.level || 1, parent, index })
    return true
  })
  // 位置 → 标题：第二遍取后代标题的 id 时用它查（不用 find，O(n²)）
  const byPos = new Map(list.map((h) => [h.pos, h]))

  // ---------- 第二遍：每个标题的范围 ----------
  for (const h of list) {
    // 折叠范围 = 同一父容器里，这个标题**后面的兄弟块**，到下一个同级或更高级的标题之前。
    // `blocks` 是这些块的位置（正文靠它加隐藏装饰），
    // `descendants` 是其中属于标题的那些（大纲靠它藏下级条目）——
    // **两者是同一个范围**，所以正文藏的和大纲藏的永远一致。
    h.blocks = []
    h.descendants = []
    let siblingPos = h.pos + h.node.nodeSize
    for (let i = h.index + 1; i < h.parent.childCount; i++) {
      const sib = h.parent.child(i)
      if (sib.type.name === 'heading' && (sib.attrs.level || 1) <= h.level) break
      h.blocks.push(siblingPos)
      if (sib.type.name === 'heading') {
        const kid = byPos.get(siblingPos)
        if (kid) h.descendants.push(kid.id)
      }
      siblingPos += sib.nodeSize
    }
  }
  return list
}

/**
 * 算文档里所有 heading 的信息：id / 层级 / 文字 / 有没有可折的内容 / 下级条目。
 *
 * **不返回位置**：位置会随正文编辑漂移（标题前面插一段，后面所有标题的位置都变了），
 * 而大纲的"点了跳过去"要的是**稳定的目标**。所以只给 id，
 * 跳转时再按 id 去当前文档里找位置（见 `findHeadingPos`）。
 *
 * `descendants` = 这个标题罩着的**所有下级标题 id**（不只直接下级）：
 * 大纲折叠一项时要把这一整支都藏掉，所以给全的。
 * **由这里算，不让大纲自己按 level 推** —— 同一套层级规则只存在一份。
 *
 * @param {import('@milkdown/prose/model').Node} doc
 * @returns {Array<{id:string,level:number,text:string,descendants:string[]}>}
 */
export function getOutline(doc) {
  return scanHeadings(doc).map((h) => ({
    id: h.id,
    level: h.level,
    text: h.node.textContent,
    descendants: h.descendants
  }))
}

/** 按 id 找 heading 在当前文档里的位置（找不到返回 -1） */
function findHeadingPos(doc, id) {
  const hit = scanHeadings(doc).find((h) => h.id === id)
  return hit ? hit.pos : -1
}

/**
 * 折叠箭头。
 *
 * **是真实的 DOM 元素，不是 `::before` 伪元素** —— 理由有两个：
 *
 * 1. 伪元素点不中。真要判定"点的是不是箭头"，只能拿 clientX 去比坐标，
 *    而箭头的位置受 heading 的 padding / 字号影响，写死阈值必然对不齐；
 *    真实元素则是事件目标本身，直接绑事件就行。
 * 2. `::before` 的 content 会被读成标题文字的一部分，标题一多就出现
 *    "一级标题▾"这种脏数据。
 *
 * 用 **ProseMirror widget** 承载：widget 是"只影响显示、不进内容"的机制，
 * 既不会混进 markdown（正文要喂给 AI），也不会和 PM 的 DOM 同步打架。
 *
 * 这个函数是**模块级稳定的**：widget 的 DOM 只在「同一个标题的折叠状态没变」
 * 时才复用（PM 靠 `toDOM` 引用相等 + `spec.key` 判断），所以它每次都新建函数的话，
 * 每改一次正文就要重建一次箭头 DOM，白白多一轮 DOM 变动。
 */
function arrowDOM() {
  const dom = document.createElement('span')
  dom.className = 'kb-fold-arrow'
  // 不让光标落进来（否则点箭头会先把光标塞进箭头里）
  dom.contentEditable = 'false'
  return dom
}

/**
 * 按当前折叠状态算出装饰集合。
 *
 * - 有下文的标题：前面插一个折叠箭头（widget）+ `kb-is-collapsed`（折叠中）
 * - 折起来的标题下面那些块：`kb-folded-hidden`（CSS 里 `display: none`）
 */
function buildDeco(doc, collapsed) {
  const decos = []
  // 被隐藏的块按位置去重：**一个块可能同时落在几个标题的折叠范围里**
  // （一级标题的范围罩着二级标题的范围）。不去重就会给同一个节点挂两个
  // 范围完全相同的装饰 —— 重复的装饰是脏数据（PM 会按装饰数比对内容）。
  const hidden = new Set()
  for (const h of scanHeadings(doc)) {
    // 没有 id 就没法当折叠的 key；没有下文就没东西可折
    if (!h.id || !h.blocks.length) continue
    const isCollapsed = collapsed.has(h.id)
    // 箭头插在标题内容的**最前面**（pos + 1 = 标题内容起点）
    decos.push(Decoration.widget(h.pos + 1, arrowDOM, { side: -1, key: `kb-arrow-${h.id}` }))
    if (!isCollapsed) continue
    decos.push(Decoration.node(h.pos, h.pos + h.node.nodeSize, { class: 'kb-is-collapsed' }))
    h.blocks.forEach((pos) => hidden.add(pos))
  }
  hidden.forEach((pos) => {
    const node = doc.nodeAt(pos)
    if (!node) return
    decos.push(Decoration.node(pos, pos + node.nodeSize, { class: 'kb-folded-hidden' }))
  })
  return DecorationSet.create(doc, decos)
}

/**
 * plugin state：折叠的 heading id 集合 + 据此算出的装饰。
 *
 * 装饰跟着文档走（文档改了位置就变了），折叠集合跟着 id 走（位置漂移不影响它）。
 */
class FoldState {
  constructor(collapsed, deco) {
    this.collapsed = collapsed
    this.deco = deco
  }

  apply(tr) {
    const next = tr.getMeta(foldPluginKey)
    if (next) {
      const collapsed = new Set(next)
      return new FoldState(collapsed, buildDeco(tr.doc, collapsed))
    }
    if (tr.docChanged) {
      return new FoldState(this.collapsed, buildDeco(tr.doc, this.collapsed))
    }
    return this
  }
}

/**
 * 折叠 plugin。
 *
 * ⚠️ **必须用 `$prose` 包**：milkdown 的 `editor.use()` 收的是**函数式**插件
 * （`MilkdownPlugin = (ctx) => ...`），直接把 ProseMirror 的 `Plugin` **对象**
 * 丢进去会在 create 时炸 `plugin is not a function`。
 *
 * ⚠️ `$prose` 的工厂**只接一个参数**（没有 id）。
 */
export const foldPlugin = $prose(() => new Plugin({
  key: foldPluginKey,
  state: {
    init: (_config, state) => new FoldState(new Set(), buildDeco(state.doc, new Set())),
    apply: (tr, prev) => prev.apply(tr)
  },
  props: {
    /**
     * 装饰由 PM 施加 —— **这是不碰 DOM 的关键**。
     * 自己改 DOM class 会和 PM 的 MutationObserver 打成死循环（见文件头说明）。
     */
    decorations: (state) => foldPluginKey.getState(state).deco,
    handleDOMEvents: {
      mousedown: (view, event) => {
        const el = event.target
        // 点的是折叠箭头（真实元素，见 arrowDOM 的说明）
        const arrow = el?.closest?.('.kb-fold-arrow')
        if (!arrow || !view.dom.contains(arrow)) return false
        // 箭头在标题里面 → 顺着它找到那个标题，拿它的 id
        const headingEl = arrow.closest(HEADING_SELECTOR)
        if (!headingEl) return false
        const id = headingEl.id
        if (!id) return false
        toggleFold(view, id)
        // 拦住默认行为：不然光标会被塞进箭头里
        return true
      }
    }
  }
}))

/** 折叠 / 展开某个 heading */
function toggleFold(view, id) {
  const collapsed = new Set(foldPluginKey.getState(view.state).collapsed)
  if (collapsed.has(id)) collapsed.delete(id)
  else collapsed.add(id)
  setCollapsed(view, collapsed)
}

/**
 * **覆盖**式设置折叠集合。
 *
 * 和 toggleFold 走同一个出口（构造 meta + dispatch）—— 折叠状态怎么写进 plugin，
 * 只有这一处。
 *
 * @param {import('@milkdown/prose/view').EditorView} view
 * @param {Iterable<string>} ids 要折起来的标题 id（空集合 = 全展开）
 */
function setCollapsed(view, ids) {
  view.dispatch(view.state.tr.setMeta(foldPluginKey, new Set(ids)))
}

/**
 * 滚到某个标题并把光标放进去。
 *
 * 按 **id** 定位而不是位置：位置会随编辑漂移，id 不会（见 getOutline 的说明）。
 * 用 `TextSelection.near` 而不是 `TextSelection.create`：pos 是 heading 的**节点起点**，
 * `create` 要求 pos 落在 textblock **内部**，差一位就会抛。
 */
export function scrollToHeading(view, id) {
  const { doc } = view.state
  const pos = findHeadingPos(doc, id)
  if (pos < 0) return false
  const $pos = doc.resolve(pos)
  const selection = TextSelection.near($pos, 1)
  view.dispatch(view.state.tr.setSelection(selection).scrollIntoView())
  view.focus()
  return true
}
