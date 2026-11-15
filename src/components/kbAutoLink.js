/**
 * 正文里的**知识点自动链接**（只影响显示，不进正文）。
 *
 * ## 为什么放在渲染层，而不是生成时
 *
 * 生成时让模型写 `[[标题]]` 再换成 id，有两个绕不过去的毛病：
 * ① 重名退化成死文字、目标后建永远缺链、改标题断链；
 * ② 每条正文都要把整棵子树的标题清单喂给模型（几千 token），只为让它知道"哪些名字能标"。
 *
 * 放在渲染层就都解了：**正文一个字不动**，链不链、链到哪，每次打开现算。
 * 新建的知识点自动能被链上，改标题自动跟随，删了自动退成纯文字。
 *
 * ## 为什么用 Decoration，不能自己改 DOM
 *
 * 与 `kbSearchHighlight.js` / `kbHeadingFold.js` 同一条理由（那边踩过死循环）：
 * ProseMirror 的 `MutationObserver` 盯着编辑器 DOM，改 class 它看得见 ⇒
 * 改 DOM → observer → flush → plugin update → 又改 DOM ⇒ 主线程占死、白屏。
 * Decoration 由 PM 自己施加，**幂等**，不会自己触发自己。
 *
 * ## 坐标（这里错过一次，别再错）
 *
 * `doc.descendants` 给**文本节点**的 `pos` **已经是内容起点** —— 文本是叶子节点、
 * 没有自己的开括号 ⇒ 命中坐标是 `pos + at`，**不是 `pos + 1 + at`**
 * （`pos + 1` 只对块级节点成立）。写错的表现是**链接整体右移一个字**。
 *
 * ## 中文的坑：不按"整词"匹配，按"标题串"匹配
 *
 * 中文没有空格分词，"堆"在"堆内存"里算不算一个词没法判定。所以这里的策略是：
 * **只用完整标题串去匹配，且长标题优先** —— "垃圾回收算法"先占位，"垃圾回收"就不会
 * 再把前半截抢走。短于 4 个字的标题、以及"概述/原理/总览"这类通用词**一律不参与**，
 * 否则正文会变成满屏链接。
 */

import { Plugin, PluginKey } from '@milkdown/prose/state'
import { Decoration, DecorationSet } from '@milkdown/prose/view'
import { $prose } from '@milkdown/utils'

/** plugin key（取 state / 派发用） */
export const autoLinkKey = new PluginKey('kbAutoLink')

/** 短于这个长度的标题不参与自动链接（"堆""栈"这种会把正文链成筛子） */
const MIN_TERM = 4

/** 通用词：它们本身就是标题也可能，但链上去毫无意义 */
const GENERIC = new Set([
  '概述', '简介', '定义', '原理', '应用', '总结', '索引', '总览',
  '常见考点', '总览与索引', '知识体系', '基础概念', '核心概念'
])

/** 性能兜底：标题上千条时不做全量匹配（一次几十万次 indexOf 会卡首屏） */
const MAX_TERMS = 800

/**
 * 整理词表：过滤、去重、**按长度降序**（长词优先占位，短词不抢）。
 *
 * @param {Array<{term:string,id:number,href?:string}>} entries
 */
export function normalize(entries) {
  const seen = new Set()
  const out = []
  for (const e of entries || []) {
    const term = String(e?.term || '').trim()
    if (!term || term.length < MIN_TERM) continue
    if (GENERIC.has(term)) continue
    if (!e.id || seen.has(term)) continue
    seen.add(term)
    out.push({ term, href: e.href || `/app/topic/${e.id}` })
  }
  // 长词优先 = 先占位置。否则「垃圾回收」会先把「垃圾回收算法」的前半截吃掉
  out.sort((a, b) => b.term.length - a.term.length)
  return out.slice(0, MAX_TERMS)
}

/** 这些块里的文字不参与自动链接（代码、标题、公式） */
const SKIP_BLOCKS = new Set(['code_block', 'fence', 'heading', 'math_block', 'math_inline'])

/**
 * 扫文档，给每个标题词的**首次出现**加装饰。
 *
 * @returns {DecorationSet}
 */
function buildDeco(doc, entries) {
  if (!entries.length) return DecorationSet.empty
  const linked = new Set()
  const decos = []

  doc.descendants((node, pos, parent) => {
    // 块级：整支跳过（代码块 / 标题里的文字不该变链接）
    const blockName = node.isBlock ? node.type.name : null
    if (blockName && SKIP_BLOCKS.has(blockName)) return false
    if (!node.isText || !node.text) return true
    // 已经有链接的地方不重复链（作者显式写的那种优先）
    if (node.marks.some((m) => m.type.name === 'link')) return true

    const text = node.text
    const taken = [] // 本节点内已占用的区间，防重叠
    for (const e of entries) {
      if (linked.has(e.term)) continue
      let at = -1
      let from = 0
      for (;;) {
        const found = text.indexOf(e.term, from)
        if (found < 0) break
        const end = found + e.term.length
        const clash = taken.some(([s, t]) => found < t && end > s)
        if (!clash) {
          at = found
          break
        }
        from = found + 1
      }
      if (at < 0) continue
      const end = at + e.term.length
      taken.push([at, end])
      linked.add(e.term)
      decos.push(
        Decoration.inline(pos + at, pos + end, {
          class: 'kb-autolink',
          'data-href': e.href,
          title: `跳到「${e.term}」`
        })
      )
    }
    return true
  })

  return decos.length ? DecorationSet.create(doc, decos) : DecorationSet.empty
}

class AutoLinkState {
  constructor(entries, deco) {
    this.entries = entries
    this.deco = deco
  }

  apply(tr) {
    const meta = tr.getMeta(autoLinkKey)
    if (meta) {
      const entries = normalize(meta.entries)
      return new AutoLinkState(entries, buildDeco(tr.doc, entries))
    }
    if (tr.docChanged) {
      // 文档变了（换了一条知识 / 折叠改了结构）⇒ 位置全变，重算
      return new AutoLinkState(this.entries, buildDeco(tr.doc, this.entries))
    }
    return this
  }
}

/**
 * 自动链接插件（**纯装饰，不改内容**）。
 *
 * <p>⚠️ **点击不在这里处理**：装饰出来的 `.kb-autolink` 不是 link mark，
 * 编辑器那套"单击链接 ⇒ 弹链接工具条"的判据（`closest('a')` + `marks` 里有 link）
 * 对它一条都不成立。所以点击接在 `MilkdownEditorBody` 的
 * `editorViewOptionsCtx` 那个 click 里（同一处，紧挨着真链接的分支），
 * 保证两种链接**交互一致**：单击弹同一条工具条、Ctrl/⌘+单击新标签打开。
 * 早先在插件里自己派 `kb:autolink` 事件、由详情页 router.push，是第二套逻辑 ——
 * 结果是"点了直接跳"，跟站内真链接"先弹工具条再决定"的行为对不上，已撤。
 */
export const autoLinkPlugin = $prose(() => new Plugin({
  key: autoLinkKey,
  state: {
    init: () => new AutoLinkState([], DecorationSet.empty),
    apply: (tr, prev) => prev.apply(tr)
  },
  props: {
    decorations: (state) => autoLinkKey.getState(state).deco
  }
}))

/**
 * 设置自动链接词表（走 transaction meta ⇒ 插件自己重算装饰）。
 *
 * @param {import('@milkdown/prose/view').EditorView} view
 * @param {Array<{term:string,id:number,href?:string}>} entries
 */
export function setAutoLinks(view, entries) {
  if (!view) return
  view.dispatch(view.state.tr.setMeta(autoLinkKey, { entries: entries || [] }))
}
