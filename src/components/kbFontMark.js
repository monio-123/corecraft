/**
 * 字号 / 字体：一个自定义 mark，存成行内 HTML。
 *
 * ## 为什么是行内 HTML
 *
 * markdown 没有字号和字体这两个概念（**加粗**有，**字号**没有）。
 * 能存住"这段字是 18px 的黑体"的只有两种：
 *
 * - **自定义语法**（比如 `{: .big}`）—— 那是 pandoc 的方言，别的工具读不了，
 *   平台自己写一套解析器还得定义一套语法规则；
 * - **行内 HTML**（`<span style="font-size:18px">文字</span>`）—— WPS / 语雀 /
 *   微信公众号全都用这个，浏览器直接认，别的编辑器粘过来也不丢。
 *
 * 选后者。**代价是 markdown 里多了一段 html**——但这本来就是"排版信息"，
 * 放正文里总比放数据库的另一个字段里好（那要自己写同步逻辑，两份真相）。
 *
 * ## 链路
 *
 * ```
 * 存：<span style="font-size:18px">文字</span>
 *      ↕ remark 插件把 html 节点对合成一个 fontSpan 节点
 * 读：fontSpan → mark（font）→ <span style="...">文字</span>
 * ```
 *
 * - **解析**：`remarkFontSpan` 插件（remark 侧）把 `<span style=...>…</span>`
 *   拆成的三个节点（开标签 html / 文本 / 闭标签 html）合成一个 `fontSpan`；
 * - **输出**：`remarkStringifyOptionsCtx` 的 `handlers.fontSpan` 把它写回 html；
 * - **编辑**：`font` mark（ProseMirror 侧的 mark），DOM 上就是带 style 的 span。
 *
 * ## 为什么不"造个新语法"
 *
 * 见上面「为什么是行内 HTML」。还有一条更实际的理由：**这份正文会被 AI 读**
 * （问答、出题都喂 `kp_topic.content`）。`<span style="font-size:18px">` 至少
 * 是个标准的、模型见过的东西；自定义语法模型看不懂，出了错都不知道错在哪。
 *
 * ## 档位
 *
 * 字号 5 档、字体 4 档。**故意少**——档位是配置，加一档就是往数组里加一项，
 * 但**语义不能含糊**：字号存 px（绝对值，跨设备一致），字体存字体名。
 *
 * 两套档位的**形状一样**（`key` / `label` / `size` / `family` / `preview`），
 * 所以选择器那边不用写两套映射。
 */

import { remarkStringifyOptionsCtx } from '@milkdown/core'
import { $command, $markSchema, $remark } from '@milkdown/utils'
import { TextSelection } from '@milkdown/prose/state'

/** remark 节点类型名 */
const FONT_SPAN = 'fontSpan'

/**
 * 字号档位。`family: null` = **不动字体那一维**（这一档只管字号）。
 *
 * 为什么要 null 而不是空串：空串是"把字体清回默认"，null 是"这维度我不管"。
 * 这两件事在「只改字号、别把我设的字体也抹了」这个场景里必须分得开。
 */
export const FONT_SIZES = [
  { key: '12px', label: '小', size: '12px', family: null, preview: '12px' },
  { key: '14px', label: '标准', size: '14px', family: null, preview: '14px' },
  { key: '16px', label: '中', size: '16px', family: null, preview: '16px' },
  { key: '20px', label: '大', size: '20px', family: null, preview: '20px' },
  { key: '24px', label: '特大', size: '24px', family: null, preview: '24px' }
]

/** 字体档位。`size: null` = 不动字号那一维。 */
export const FONT_FAMILIES = [
  { key: 'default', label: '默认', size: null, family: '', preview: '' },
  { key: 'serif', label: '衬线', size: null, family: 'serif', preview: '' },
  { key: 'monospace', label: '等宽', size: null, family: 'monospace', preview: '' },
  { key: 'system-ui', label: '圆体', size: null, family: 'system-ui, sans-serif', preview: '' }
]

/** `<span style="...">` —— 开标签 */
const OPEN_SPAN = /^<span\b[^>]*\bstyle\s*=\s*["']([^"']*)["'][^>]*>$/i
/** `</span>` —— 闭标签 */
const CLOSE_SPAN = /^<\/span\s*>$/i

/** 从 style 串里取出我们关心的两条 */
function readStyle(style) {
  const size = /font-size\s*:\s*([^;]+)/i.exec(style)?.[1]?.trim() || ''
  const family = /font-family\s*:\s*([^;]+)/i.exec(style)?.[1]?.trim() || ''
  return { size, family }
}

/** 把 size / family 组成 style 串（空的不写） */
function writeStyle(size, family) {
  const parts = []
  if (size) parts.push(`font-size:${size}`)
  if (family) parts.push(`font-family:${family}`)
  return parts.join(';')
}

/**
 * remark 插件：把 `<span style=...>…</span>` 合成一个 fontSpan 节点。
 *
 * remark 解析行内 html 时**不会**把 span 当成容器——开标签、文本、闭标签
 * 是三个平级的兄弟节点。所以得自己把它们合起来。
 *
 * 合并只在**开标签能取出字号/字体**时做：认不出的 span 原样留着
 * （那是别人的 html，不该我们插手）。
 */
export const remarkFontSpan = $remark('remarkFontSpan', () => () => (tree) => {
  const walk = (node) => {
    const children = node.children
    if (!children) return
    const out = []
    for (let i = 0; i < children.length; i++) {
      const child = children[i]
      const open = child.type === 'html' && typeof child.value === 'string'
        ? OPEN_SPAN.exec(child.value)
        : null
      if (!open) {
        out.push(child)
        continue
      }
      const { size, family } = readStyle(open[1])
      if (!size && !family) {
        out.push(child)
        continue
      }
      // 找到配对的闭标签
      let depth = 1
      let end = i + 1
      const inner = []
      for (; end < children.length; end++) {
        const c = children[end]
        if (c.type === 'html' && typeof c.value === 'string') {
          if (OPEN_SPAN.test(c.value)) depth++
          else if (CLOSE_SPAN.test(c.value)) {
            depth--
            if (depth === 0) break
          }
        }
        inner.push(c)
      }
      if (depth !== 0) {
        // 没有配对的闭标签：不动它
        out.push(child)
        continue
      }
      out.push({ type: FONT_SPAN, size, family, children: inner })
      i = end
    }
    node.children = out
    // 递归走**合并之后**的子节点：合成的 fontSpan 里面还可能有嵌套的 span，
    // 走旧数组的话那层永远合不掉
    out.forEach((c) => walk(c))
  }
  walk(tree)
})

/**
 * mark schema。
 *
 * `toMarkdown` 发的是**自定义 remark 节点类型**（`fontSpan`），
 * 对应的输出 handler 在 `remarkFontSpanStringify` 里注册。
 */
export const fontSchema = $markSchema('font', () => ({
  attrs: {
    size: { default: '', validate: 'string' },
    family: { default: '', validate: 'string' }
  },
  parseDOM: [
    {
      tag: 'span[style]',
      getAttrs: (dom) => {
        const style = dom.getAttribute('style') || ''
        const { size, family } = readStyle(style)
        // 认不出的 span 交回默认解析（不当成字体标记）
        if (!size && !family) return false
        return { size, family }
      }
    }
  ],
  toDOM: (mark) => ['span', { style: writeStyle(mark.attrs.size, mark.attrs.family) }, 0],
  parseMarkdown: {
    match: (node) => node.type === FONT_SPAN,
    runner: (state, node, markType) => {
      state.openMark(markType, { size: node.size || '', family: node.family || '' })
      state.next(node.children)
      state.closeMark(markType)
    }
  },
  toMarkdown: {
    match: (mark) => mark.type.name === 'font',
    runner: (state, mark) => {
      state.withMark(mark, FONT_SPAN, undefined, {
        size: mark.attrs.size,
        family: mark.attrs.family
      })
    }
  }
}))

/**
 * remark-stringify 的输出 handler：fontSpan → `<span style="...">…</span>`。
 *
 * **必须在 init 插件读走 stringify options 之前写进去**，所以这里是一个
 * 普通的 MilkdownPlugin（同步部分在 `use()` 时就跑），不走 `$remark`
 * （那个要等 InitReady，那时已经晚了）。
 */
export const remarkFontSpanStringify = (ctx) => {
  ctx.update(remarkStringifyOptionsCtx, (prev) => ({
    ...prev,
    handlers: {
      ...prev.handlers,
      [FONT_SPAN]: (node, _parent, state, info) => {
        const value = state.containerPhrasing(node, { before: '\n', after: '\n', ...info })
        return `<span style="${writeStyle(node.size, node.family)}">${value}</span>`
      }
    }
  }))
}

/**
 * 设置字号 / 字体。
 *
 * ## 一次命令管两个维度，而不是两个命令
 *
 * 原来分成 `SetFontSize` / `SetFontFamily` 两个命令，payload 里各带一半——
 * 于是「设字号」那个命令必须**把 family 也传上去**，否则选字号会把已设的字体抹掉；
 * 而「不传」和「传空串」在命令里还得分三种情况（不改 / 清空 / 没这个维度）。
 *
 * 合成一个命令之后，语义就干净了：
 * - payload 里 **null / 不给** = 那一维**不动**
 * - payload 里 **空串** = 那一维**清掉**（字体档位里的「默认」）
 *
 * 另一条：**不是 toggle**。字号是"这一段是 18px"这种**设定**语义，
 * 选中一段 14px 的字改成 18px 之后，再点一次 14px 应该变回 14px，
 * 而不是"取消标记"（那样就退回默认字号，而不是 14px）。
 */
function setFontMark(markType, attrs) {
  return (state, dispatch) => {
    const { from, to, empty } = state.selection
    // 选区上已有的 font mark：用来保留"这次没要改"的那一维
    const existing = (state.storedMarks || state.selection.$from.marks())
      .find((m) => m.type === markType)
    const next = {
      size: attrs.size == null ? (existing?.attrs.size ?? '') : attrs.size,
      family: attrs.family == null ? (existing?.attrs.family ?? '') : attrs.family
    }
    if (dispatch) {
      let tr = state.tr
      const mark = markType.create(next)
      if (empty) {
        tr = tr.addStoredMark(mark)
      } else {
        tr = tr.removeMark(from, to, markType).addMark(from, to, mark)
      }
      dispatch(tr)
    }
    return true
  }
}

/**
 * 设置字号 / 字体。payload: `{ size?: string|null, family?: string|null }`
 * —— 给 null 或不给 = 那一维不动；给空串 = 那一维清掉。
 */
export const setFontCommand = $command('SetFont', (ctx) => (payload = {}) =>
  setFontMark(fontSchema.type(ctx), payload)
)

/**
 * 清除格式：把选区里的**行内标记**全去掉，标题还原成正文段落。
 *
 * ## 边界
 *
 * - **列表结构保留**：`- 项目` 里的"项目"是正文，但那个圆点表示的是
 *   "这条属于一组"，那是结构不是格式，清掉就丢了信息。所以只清标记，
 *   列表项仍是列表项（要变成普通段落是另一件事，不在"清除格式"里做）。
 * - **代码块不清**：代码块里的内容是代码，不该被"清除格式"改写。
 */
export const clearFormattingCommand = $command('ClearFormatting', (ctx) => () => (state, dispatch) => {
  const { from, to, empty } = state.selection
  const paragraph = state.schema.nodes.paragraph
  if (!paragraph) return false

  if (dispatch) {
    let tr = state.tr
    if (empty) {
      // 光标处：清掉光标上的所有标记
      const marks = state.storedMarks || state.selection.$from.marks()
      marks.forEach((m) => {
        tr = tr.removeStoredMark(m.type)
      })
      tr = tr.setSelection(TextSelection.create(tr.doc, from, to))
    } else {
      tr = tr.removeMark(from, to)
    }
    // 选区里**每个**标题块 → 正文段落。
    // 逐块找而不是看公共祖先：`$from.blockRange($to)` 给的是选区**共同**的块，
    // 选区跨了"标题 + 正文"时它返回的是 doc，标题就漏掉了。
    const headings = []
    state.doc.nodesBetween(from, to, (node, pos) => {
      if (node.type.name === 'heading') headings.push([pos, pos + node.nodeSize])
    })
    headings.forEach(([f, t]) => {
      tr = tr.setBlockType(f, t, paragraph)
    })
    dispatch(tr)
  }
  return true
})
