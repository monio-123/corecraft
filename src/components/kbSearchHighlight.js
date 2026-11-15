/**
 * 正文里的**关键词高亮 + 定位**（详情页的检索框用它）。
 *
 * ## 为什么用 Decoration，不能自己改 DOM
 *
 * 与 `kbHeadingFold.js` 同一条理由（那边踩过死循环）：ProseMirror 的
 * `MutationObserver` 盯着编辑器 DOM，**class 属性的变化它看得见** ⇒
 * 我们改 DOM class → observer 触发 → flush → updateState → plugin 的 update
 * → 我们又改 DOM class → … 浏览器主线程被占死，页面直接白屏。
 *
 * 正解是 **Decoration** —— ProseMirror 自己的「只影响显示、不影响内容」机制：
 * 装饰由 PM 施加，PM 自己知道这些 class 是它加的，**幂等**，不会自己触发自己。
 * ⇒ 一行 DOM 都不自己碰。
 *
 * ## 高亮只影响显示，不进正文
 *
 * 正文要喂给 AI（问答 / 出题都读 `kp_topic.content`）。高亮如果真写进内容，
 * 正文就会脏一点；而「现在搜到第几处」是**看的人临时决定的**，本来就该是视图状态。
 * ⇒ 和折叠一样：状态住 plugin state，刷新即失效。
 *
 * ## 命中怎么找
 *
 * **只扫文本节点**（inline content），不扫代码块 / 数学公式之类的特殊节点：
 * 那些节点里的文本不是一个连续的可见字符串，按文本匹配会在块边界处
 * 切出半个命中（装饰的 from/to 落在节点中间 ⇒ PM 报 "Invalid decoration"）。
 *
 * 命中位置记成 `{from, to}`（**文档绝对位置**）—— 装饰要的就是这两个数。
 */

import { Plugin, PluginKey } from '@milkdown/prose/state'
import { Decoration, DecorationSet } from '@milkdown/prose/view'
import { $prose } from '@milkdown/utils'

/** plugin key（取 state 用） */
export const searchHighlightKey = new PluginKey('kbSearchHighlight')

/**
 * 当前搜索词 + 当前命中的第几个（0 = 还没定位）。
 *
 * 这两个值放在 **plugin state** 里（不是组件的 ref）⇒ 装饰和状态同源，
 * 不会出现「装饰画出来了但计数是旧的」这种分裂。
 */
class SearchState {
  constructor(keyword, activeIndex, deco, matches) {
    this.keyword = keyword
    this.activeIndex = activeIndex
    this.deco = deco
    /** 全部命中的位置（文档绝对位置），供上下跳转用 */
    this.matches = matches
  }

  apply(tr) {
    const meta = tr.getMeta(searchHighlightKey)
    if (meta) {
      // 换了词 ⇒ 命中重算，游标回到 0（还没定位）。
      // ⚠️ **换了词不能沿用旧游标**：新词的命中数可能完全不同，
      // 沿用旧值会停在一个不存在的第 n 处（界面上是"高亮跳到了别处"）。
      const keyword = meta.keyword ?? ''
      const matches = keyword ? findMatches(tr.doc, keyword) : []
      // activeIndex 显式带过来（上下跳转用）；没带就是 0（还没定位）。
      const wanted = meta.activeIndex ?? 0
      const activeIndex = wanted > 0 ? Math.min(wanted, matches.length - 1) : 0
      return new SearchState(keyword, activeIndex, buildDeco(tr.doc, matches, activeIndex), matches)
    }
    if (tr.docChanged) {
      // 文档改了（打字 / 折叠 / 换内容）⇒ 位置全变了，重算
      const matches = this.keyword ? findMatches(tr.doc, this.keyword) : []
      const activeIndex = Math.min(this.activeIndex, matches.length - 1)
      return new SearchState(this.keyword, activeIndex, buildDeco(tr.doc, matches, activeIndex), matches)
    }
    return this
  }
}

/**
 * 扫文本节点找命中。
 *
 * @returns {Array<{from:number,to:number}>} 命中位置（文档绝对位置，按出现顺序）
 */
export function findMatches(doc, keyword) {
  const kw = String(keyword || '').trim()
  if (!kw) return []
  const needle = kw.toLowerCase()
  const hits = []
  doc.descendants((node, pos) => {
    // 只看文本节点：块级节点（段落/标题/列表项）自己没 textContent 可搜，
    // 而它们的 textContent 是**子节点拼起来的**——按它搜会算出跨块的位置，
    // 那个位置落在节点边界上，装饰会失效。
    if (!node.isText || !node.text) return true
    const hay = node.text.toLowerCase()
    let from = 0
    for (;;) {
      const at = hay.indexOf(needle, from)
      if (at < 0) break
      // ⚠️ **这里的坐标是 `pos + at`，不是 `pos + 1 + at`**（2026-11-09 定位错位修出来的）：
      // `doc.descendants` 给的 `pos` **已经是这段文本内容的起点** —— 文本节点是**叶子节点**，
      // 它**没有自己的开括号**（`pos + 1` 那种算法只对**块级节点**成立：
      // 块级节点的 `pos` 指向它的开括号，内容从 `pos + 1` 开始）。
      //
      // 实证（node + 项目真实的 prosemirror-model）：
      //   doc = paragraph[text "邓昌镒电渠研发云："]
      //   descendants 给 text 节点的 pos = 1，而 textBetween(1, 3) = "邓昌" ✓
      //   → 公式 `pos + at` 落在「邓昌」；`pos + 1 + at` 落在「昌镒」（**整体右移一个字**）。
      // 症状：用户搜「邓昌」，高亮却盖在「昌镒」上（用户原话：「我搜索的邓昌 但是高亮的是昌益」）。
      hits.push({ from: pos + at, to: pos + at + needle.length })
      from = at + needle.length
    }
    return true
  })
  return hits
}

/**
 * 命中集合 → 装饰集合。
 *
 * 当前那一个（activeIndex）用高亮色，其余用淡色 ⇒ 一眼看出"现在在第几处"。
 */
function buildDeco(doc, matches, activeIndex) {
  if (!matches.length) return DecorationSet.empty
  const decos = matches.map((m, i) =>
    Decoration.inline(m.from, m.to, { class: i === activeIndex ? 'kb-hit kb-hit--cur' : 'kb-hit' })
  )
  return DecorationSet.create(doc, decos)
}

/**
 * 折叠 / 滚动用的插件（**只读装饰，不改内容**）。
 */
export const searchHighlightPlugin = $prose(() => new Plugin({
  key: searchHighlightKey,
  state: {
    init: (_config, state) => new SearchState('', 0, DecorationSet.empty, []),
    apply: (tr, prev) => prev.apply(tr)
  },
  props: {
    decorations: (state) => searchHighlightKey.getState(state).deco
  }
}))

/**
 * 设置搜索词（走 transaction meta ⇒ 插件自己重算装饰）。
 *
 * @param {import('@milkdown/prose/view').EditorView} view
 * @param {string} keyword
 */
export function setSearchKeyword(view, keyword) {
  if (!view) return
  view.dispatch(view.state.tr.setMeta(searchHighlightKey, { keyword }))
}

/**
 * 跳到第 n 个命中（0 起）。超出范围则不动。
 *
 * @returns {boolean} 有没有跳成功
 */
export function gotoMatch(view, index) {
  if (!view) return false
  const st = searchHighlightKey.getState(view.state)
  if (!st || !st.matches.length) return false
  if (index < 0 || index >= st.matches.length) return false
  // 换词之外还要**换游标** ⇒ activeIndex 显式带过去（setMeta 的 apply 认它）。
  // ⚠️ 顺序不能反：先 dispatch（装饰重画）再滚动 —— 滚的时候高亮元素已经在了。
  view.dispatch(view.state.tr.setMeta(searchHighlightKey, {
    keyword: st.keyword,
    activeIndex: index
  }))
  scrollToMatch(view, index)
  return true
}

/**
 * 滚动到第 n 个命中并把它滚进视野。
 */
export function scrollToMatch(view, index) {
  const st = searchHighlightKey.getState(view.state)
  if (!st || !st.matches.length) return
  const hit = st.matches[index]
  if (!hit) return
  // 用 DOM 找元素：装饰已经画上去了，命中处有 .kb-hit 类。
  // ⚠️ **不自己算坐标**：PM 的坐标和屏幕坐标不是一回事（编辑器有滚动容器），
  // 让浏览器自己 scrollIntoView 最省事也最准。
  const el = hitElementAt(view, hit.from) || view.dom.querySelector('.kb-hit--cur')
  if (el && el.scrollIntoView) el.scrollIntoView({ block: 'center', behavior: 'smooth' })
}

/**
 * 命中位置（文档绝对位置）对应的**那个被装饰包住的 DOM 元素**。
 *
 * <p>⚠️ **按位置反查，不按 class 猜**（2026-11-09 定位错位修出来的）：
 * `querySelector('.kb-hit--cur')` 拿的是**文档里第一个** `.kb-hit--cur` ——
 * 命中多的时候，"当前那一个"和"第一个"是两回事（跳到第 5 处却滚到第 1 处）。
 * 这里用 `view.domAtPos(pos)` 精确落到那一段文字上，再往上找带 `.kb-hit` 的祖先
 * （PM 把 inline 装饰渲染成包住文字的 <span>）。
 *
 * @param {import('@milkdown/prose/view').EditorView} view
 * @param {number} pos 命中起点（文档绝对位置）
 * @returns {HTMLElement|null}
 */
function hitElementAt(view, pos) {
  try {
    // side = -1：偏向**位置左边**那一侧。命中起点正好是文本节点边界时，
    // side=0 可能落到前一段（未命中的那段）上 ⇒ 滚到"上一处"去了。
    const info = view.domAtPos(pos, -1)
    let el = info.node
    // 文本节点 ⇒ 往上找它的父元素（装饰的 <span>）
    if (el && el.nodeType === 3) el = el.parentElement
    while (el && el !== view.dom) {
      if (el.classList && el.classList.contains('kb-hit')) return el
      el = el.parentElement
    }
  } catch {
    // 位置不在当前 doc 里（刚换内容，坐标过期）⇒ 放弃，退回 class 查找
  }
  return null
}
