import { $command } from '@milkdown/utils'
import { findWrapping } from '@milkdown/prose/transform'

/**
 * 让块级命令的粒度对齐「视觉行」。
 *
 * ## 它补的是什么缺口
 *
 * 正文以 markdown 存储，而 markdown 里**只有 hardbreak 能精确保留空行**
 * （段内连续两个 `\n\`\n` = 一个空行；空段落会被 CommonMark 折叠，存不住）。
 * 代价是整篇贴进来的文本**是一个大段落** —— 而标题 / 列表 / 代码块
 * 都是**段落粒度**的命令，于是在里面选一行点 H2，那 31 行会一起变标题。
 *
 * ## 分工：三件事各归各的，不重叠
 *
 * | 需求 | 由谁负责 |
 * |---|---|
 * | 空行要保留 | 粘贴策略（段内 hardbreak）—— **本文件不碰** |
 * | 段落之间的视觉间距 | CSS（`.ProseMirror > p` 的 margin-bottom）—— **本文件不碰** |
 * | 给某一行单独加标题 | **本文件** |
 *
 * 所以它不是"把外来数据掰成段落"，而是**在用户主动点标题的那一刻**才切开，
 * 切出来的行就是货真价实的独立块。
 *
 * ## 为什么切分点只取三处
 *
 *   ① 选区内部的换行（选中的每一行都要能单独成块）
 *   ② 选区起点往前的第一个换行（起点不在行首 → 往前扩到行首）
 *   ③ 选区终点往后的第一个换行（终点不在行尾 → 往后扩到行尾）
 *
 * ② ③ 缺一不可：只切 ① 的话，选中段落中间几个字时选区仍然落在段落内部，
 * `setBlockType` 照样把整段换掉。
 *
 * ## 编辑态的已知代价
 *
 * 切完之后那几行才成为独立段落；**没点过标题的行仍然是原段落的一部分**，
 * 按退格 / 想在其中插一行的手感与手打的不完全一样。这是 markdown 存不了
 * "段落内的行"这个结构的必然结果 —— 要消掉它只能放弃精确保留空行。
 */
function lineCuts(doc, range) {
  const { $from, $to } = range
  const cuts = []

  const add = (pos) => {
    const $p = doc.resolve(pos)
    const inParagraph = $p.depth === 1 && $p.parent.type.name === 'paragraph'
    // parentOffset 落在两端说明这一行没有"后面"，切了也只是多一个空段落
    if (inParagraph && $p.parentOffset > 0 && $p.parentOffset < $p.parent.content.size) {
      cuts.push(pos)
    }
  }

  // ① 选区内部的换行
  doc.nodesBetween($from.pos, $to.pos, (node, pos) => {
    if (node.type.name === 'hardbreak') add(pos + 1)
  })

  // ② 起点不在行首 → 往前找最近的换行，切一刀让选区从行首开始
  if ($from.parentOffset > 0) {
    let cut = null
    doc.nodesBetween($from.start(), $from.pos, (node, pos) => {
      if (node.type.name === 'hardbreak') cut = pos + 1
    })
    if (cut !== null) add(cut)
  }

  // ③ 终点不在行尾 → 往后找最近的换行，切一刀让选区到行尾结束
  if ($to.parentOffset < $to.parent.content.size) {
    let cut = null
    doc.nodesBetween($to.pos, $to.end(), (node, pos) => {
      if (cut === null && node.type.name === 'hardbreak') cut = pos + 1
    })
    if (cut !== null) add(cut)
  }

  return [...new Set(cuts)].sort((a, b) => a - b)
}

/**
 * 选区先"落到行边界上"。
 *
 * ⚠️ **终点落在换行符之后必须收回一格**：那是**下一行的行首**，用户并没有选中那一行。
 * 不收回的话，`setBlockType(from, to)` 会把终点所在的那个段落也换掉 ——
 * 症状是**凭空多出一个空标题**（真实拖选到行尾时就会命中）。
 */
function visualRange(state) {
  const { $from, $to } = state.selection
  let { pos: to } = $to
  if (to > 0) {
    const $t = state.doc.resolve(to)
    if ($t.depth === 1 && $t.parentOffset > 0 && $t.nodeBefore?.type.name === 'hardbreak') {
      to -= 1
    }
  }
  return { from: $from.pos, to, $from, $to: state.doc.resolve(to) }
}

/**
 * payload: `{ name: 节点名, attrs?: 属性, mode?: 'blockType' | 'wrap' }`
 *
 * mode 默认 `blockType`（heading / code_block 这类"换块类型"的）；
 * 列表是 `wrap`（套一层容器，不是换类型）。
 *
 * 切不动时**不吞掉操作**：没有切点就按原样对原选区做，
 * 和 milkdown 原生命令行为完全一致；空选区返回 false 交回原生命令。
 *
 * 导出成纯函数是为了能脱离编辑器直接跑 —— 这段逻辑的正确性全在位置计算上，
 * **必须用真实文档 + 真实选区验过**才算数。
 */
export function applyToVisualLines(state, dispatch, payload) {
  const { name, attrs = null, mode = 'blockType' } = payload ?? {}
  if (!name || !dispatch) return false

  const nodeType = state.schema.nodes[name]
  if (!nodeType) return false
  if (state.selection.empty) return false

  const range = visualRange(state)
  const tr = state.tr
  for (const p of lineCuts(state.doc, range).reverse()) {
    tr.split(p, 1)
    // ⚠️ **切完必须把切点左边那个换行符删掉。**
    // 它是"这一行到此结束"的标记，而**段落本身就提供了分隔** —— 留着就凭空多一个
    // `<br>`，于是"本来一行空行都没有"的正文，设一次标题就上下各冒出一格空白
    // （2026-10-19 用户实机截图：整段没有空行，点一次 H2 就多了空隙）。
    //
    // 删掉它切分前后的视觉完全一致，两种情况都对：
    //   `A<br>B`      切分 → `A` + `B`        渲染 A / B      （原来 A / B）
    //   `A<br><br>B`  切分 → `A<br>` + `B`    渲染 A / 空 / B （原来 A / 空 / B）
    // 删的是**紧贴切点的那个**，也就是这一行自己的行尾标记；
    // 更前面的 br 是空行本身，必须留着。
    //
    // ⚠️ **split 之后切点仍落在前一个段落里**（实测 parentOffset 不是 0、nodeBefore 是 hardbreak），
    // 不是新段落的开头 —— 这一点想错了整个删除就是死代码。实测证据见 FEATURES #191。
    //
    // **这里不写 try/catch**：`lineCuts` 的 `add()` 已经保证切点在**顶层段落的内容中间**，
    // 而 paragraph 收得下两个段落，split 在那里不可能失败。真要是失败了说明 schema 变了，
    // **让它崩比静默吞掉好查** —— 吞掉的话症状是「点了没反应」，最难定位的一种。
    if (tr.doc.resolve(p).nodeBefore?.type.name === 'hardbreak') {
      tr.delete(p - 1, p)
    }
  }

  // 切完之后原选区位置失效，要过一遍 mapping。
  // `assoc` 两个方向必须相反：起点用 1 落在**新**段落里（选中的第一行不被漏掉），
  // 终点用 -1 落在**旧**段落里（不会把下一行拖进来）。
  // `Transaction` 自己的 `tr.selection` 不会跟着走。
  const selFrom = tr.steps.length ? tr.mapping.map(range.from, 1) : range.from
  const selTo = tr.steps.length ? tr.mapping.map(range.to, -1) : range.to

  if (mode === 'wrap') {
    const blockRange = tr.doc.resolve(selFrom).blockRange(tr.doc.resolve(selTo))
    const wrapping = blockRange && findWrapping(blockRange, nodeType, attrs)
    if (!wrapping) return false
    tr.wrap(blockRange, wrapping)
  } else {
    tr.setBlockType(selFrom, selTo, nodeType, attrs)
  }

  dispatch(tr.scrollIntoView())
  return true
}

/**
 * ⚠️ **形状必须是 `(payload) => (state, dispatch)`** ——
 * `CommandManager.call` 是两步调用（`@milkdown/core/lib/index.js:205-210`）：
 * 先 `(payload)` 拿到真正的命令，再 `(view.state, view.dispatch, view)` 执行。
 * 直接把 `(state, dispatch, payload)` 塞进去的话，第一次会把 payload 当成 state、
 * `dispatch` 拿到 undefined → 返回 false → 第二次 `false(...)` 抛
 * `TypeError: command is not a function`，按钮看上去就是「一点反应都没有」。
 */
const applyToVisualLinesCommand = () => (payload) => (state, dispatch) =>
  applyToVisualLines(state, dispatch, payload)

export const $applyToVisualLines = $command('ApplyToVisualLines', applyToVisualLinesCommand)
