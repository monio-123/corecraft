/**
 * 两份正文（ProseMirror doc）的**块级对比**。
 *
 * ## 为什么用 Milkdown 自己的 doc，不用 markdown-it
 *
 * 正文的渲染管线是 Milkdown（remark → ProseMirror → DOM）。**再引一个 markdown 渲染器
 * 就会出现两套渲染**：标题层级、软换行、字号 mark、图片节点、附件链接的样式都会对不上，
 * 用户看到的就不是"知识点渲染后的格式"了。
 * ⇒ 这里直接用编辑器的解析器产出 doc，再用 ProseMirror 官方的 `DOMSerializer` 序列化
 * —— **和编辑器里渲染的是同一套 toDOM**。
 *
 * ## 粒度是「块」，不是「行」
 *
 * Markdown 的自然单位就是块（标题 / 段落 / 列表 / 表格 / 代码块），而**渲染之后行边界就没了**
 * （源码里三行的一个段落，渲染出来是一个 `<p>`）—— 这是渲染的必然结果。
 *
 * 块与块之间怎么配对，靠**指纹**（类型 + 文本 + 标题层级）：指纹一样 ⇒ 同一个块。
 *
 * ## 指纹为什么不含 attrs
 *
 * Milkdown 给标题生成 `attrs.id`（slug / 随机），**同一段文字两次解析可能拿到不同的 id**
 * ⇒ 拿它当指纹会把"没变的标题"判成"改了" ⇒ 整块红+绿，误报一片。
 * 指纹只取**类型 + 层级 + 文本**：内容变了指纹就变（这正是要看的），格式微调不误报。
 *
 * ## 输出是「行」，和 git 的 side-by-side 一样
 *
 * 每行 = 左右各一个格子。删除的旧块和新增的新块**放在同一行**（左边红、右边绿），
 * 用户一眼看到"这里被换成了什么"；没变的块左右都有、不着色。
 * ⇒ 左右**逐行对齐** ⇒ 界面用 grid 两列排，行高自动取两边较大值 ⇒
 * **一个滚动容器就够，两边天然一起滚**（不需要两套滚动 + 同步逻辑）。
 */
import { DOMSerializer, Fragment } from '@milkdown/prose/model'

/**
 * 块的指纹：类型 + 标题层级 + 文本。
 *
 * **刻意不含 attrs**（见文件头：标题 id 每次解析可能不同）。
 */
export function fingerprint(node) {
  const level = node.attrs?.level ?? ''
  return `${node.type.name}|${level}|${node.textContent}`
}

/**
 * 一个顶层节点 → HTML 字符串。
 *
 * 用 ProseMirror 官方的 `DOMSerializer`：**它和编辑器渲染用的是同一套 `toDOM`**，
 * 所以自定义节点（图片、字号 mark）和内置节点的标记完全一致。
 */
function nodeToHtml(node) {
  const fragment = DOMSerializer.fromSchema(node.type.schema)
    .serializeFragment(Fragment.from(node))
  const box = document.createElement('div')
  box.appendChild(fragment)
  return box.innerHTML
}

/**
 * doc → 顶层块数组。
 *
 * **顶层节点 = 块**：ProseMirror 的 doc 内容就是若干顶层块（段落 / 标题 / 列表 / 表格…），
 * 它们和 Markdown 的块一一对应 ⇒ 对比和渲染都以它为单位。
 *
 * @param {object|null} doc ProseMirror doc
 * @returns {Array<{key: string, html: string}>} key = 指纹（配对用）；html = 渲染后的这一块
 */
export function blocksOf(doc) {
  if (!doc) return []
  const out = []
  doc.content.forEach((node) => {
    out.push({ key: fingerprint(node), html: nodeToHtml(node) })
  })
  return out
}

/** DP 规模上限：超过就不做 LCS（退化成按序对位），免得长文卡住界面 */
const MAX_LCS_CELLS = 1_000_000

/**
 * 中段的对齐：**按文档顺序**产出片段。
 *
 * 片段两种：
 * - `{kind:'eq', i, j}` 两边是同一个块（指纹一样）
 * - `{kind:'gap', i0, i1, j0, j1}` 旧文 [i0,i1) 与新文 [j0,j1) 对不上（改动区）
 *
 * ⚠️ **必须按文档顺序**：早先的实现把"没变的"全堆在前面、"改动的"全放在最后 ——
 * 那样第 3 段被改写，界面上却显示在文章末尾，**位置全错**。
 * git 的 diff 是按行号排的，这里也一样。
 */
function alignMiddle(a, b) {
  const n = a.length
  const m = b.length
  if (!n || !m) {
    // 一边空 ⇒ 整段是改动区（纯删除 / 纯新增）
    return [{ kind: 'gap', i0: 0, i1: n, j0: 0, j1: m }]
  }

  // dp[i][j] = a[i..] 与 b[j..] 的 LCS 长度
  const useLcs = n * m <= MAX_LCS_CELLS
  let dp = null
  if (useLcs) {
    dp = new Array(n + 1)
    for (let i = 0; i <= n; i++) dp[i] = new Uint32Array(m + 1)
    for (let i = n - 1; i >= 0; i--) {
      for (let j = m - 1; j >= 0; j--) {
        dp[i][j] = a[i] === b[j] ? dp[i + 1][j + 1] + 1 : Math.max(dp[i + 1][j], dp[i][j + 1])
      }
    }
  }

  // ⚠️ **走的方向必须和 dp 的方向一致**。
  // dp 是**后缀**方向（dp[i][j] = a[i..] 与 b[j..] 的 LCS 长度）⇒ 回溯也得**从前往后**走。
  // 早先写成"从后往前回溯"（配前缀 DP 的比较式 dp[i-1][j] >= dp[i][j-1]），
  // 两边方向一对不上 ⇒ 明明只改了两块，却整段被判成一个改动区，
  // 改动位置全错（界面显示成"从头到尾都变了"）。
  const ops = []
  let i = 0
  let j = 0
  while (i < n && j < m) {
    if (a[i] === b[j]) {
      ops.push({ op: 'eq', i, j })
      i++
      j++
    } else if (!useLcs || dp[i + 1][j] >= dp[i][j + 1]) {
      // 这一块旧文没有对应的 ⇒ 删
      ops.push({ op: 'del', i })
      i++
    } else {
      // 新文多出来的一块 ⇒ 增
      ops.push({ op: 'ins', j })
      j++
    }
  }
  while (i < n) { ops.push({ op: 'del', i }); i++ }
  while (j < m) { ops.push({ op: 'ins', j }); j++ }

  // 连续的 del/ins 合成一个改动区（**位置连续**：对齐脚本里删/插只消耗自己那一边的下标）
  const segs = []
  let k = 0
  while (k < ops.length) {
    if (ops[k].op === 'eq') {
      segs.push({ kind: 'eq', i: ops[k].i, j: ops[k].j })
      k++
      continue
    }
    const dels = []
    const inss = []
    while (k < ops.length && ops[k].op !== 'eq') {
      if (ops[k].op === 'del') dels.push(ops[k].i)
      else inss.push(ops[k].j)
      k++
    }
    segs.push({
      kind: 'gap',
      i0: dels.length ? dels[0] : 0,
      i1: dels.length ? dels[dels.length - 1] + 1 : 0,
      j0: inss.length ? inss[0] : 0,
      j1: inss.length ? inss[inss.length - 1] + 1 : 0
    })
  }
  return segs
}

/**
 * 两段块序列的对齐。
 *
 * **先剥公共前后缀再算 LCS**：同一篇文章改几行时，剥完只剩几行 ⇒ DP 规模从
 * 「块数²」掉到「改动行数²」，流式期间每 250ms 算一次也不卡。
 * （剥前后缀是安全的：a[0]===b[0] 时一定存在一个最优 LCS 包含这一对，前后缀同理。）
 */
function align(oldKeys, newKeys) {
  const n = oldKeys.length
  const m = newKeys.length
  let head = 0
  while (head < n && head < m && oldKeys[head] === newKeys[head]) head++
  let tail = 0
  while (tail < n - head && tail < m - head
    && oldKeys[n - 1 - tail] === newKeys[m - 1 - tail]) tail++

  const segs = []
  for (let i = 0; i < head; i++) segs.push({ kind: 'eq', i, j: i })

  const mid = alignMiddle(oldKeys.slice(head, n - tail), newKeys.slice(head, m - tail))
  for (const s of mid) {
    if (s.kind === 'eq') segs.push({ kind: 'eq', i: head + s.i, j: head + s.j })
    else {
      segs.push({
        kind: 'gap',
        i0: head + s.i0, i1: head + s.i1,
        j0: head + s.j0, j1: head + s.j1
      })
    }
  }

  for (let i = 0; i < tail; i++) segs.push({ kind: 'eq', i: n - tail + i, j: m - tail + i })
  return segs
}

/**
 * 两个块序列 → **行序列**（左右逐行对齐，和 git 的 side-by-side 一个形状）。
 *
 * 行的四种：
 * - `equal`  —— 两边都有同一个块，不着色
 * - `changed` —— 左边是旧块、右边是新块（**同一处改动的两个版本**）⇒ 左红右绿
 * - `removed` —— 左边有、右边空（纯删除）
 * - `added`   —— 左边空、右边有（纯新增）
 *
 * 一个改动区里剩下的旧块与新块**按顺序两两配对**成 `changed` 行（`git diff` 也是这么把
 * "删掉的"和"加上的"并排放在同一行的）；多出来的单独成行。
 *
 * **行的顺序 = 文档顺序**（改动出现在它该在的位置，不是全堆在末尾）。
 *
 * @param {Array} oldBlocks 旧文块（`blocksOf` 的结果）
 * @param {Array} newBlocks 新文块
 * @returns {Array<{type: string, left: object|null, right: object|null}>}
 */
export function diffRows(oldBlocks, newBlocks) {
  const oldList = oldBlocks || []
  const newList = newBlocks || []
  const segs = align(oldList.map((b) => b.key), newList.map((b) => b.key))

  const rows = []
  for (const s of segs) {
    if (s.kind === 'eq') {
      rows.push({ type: 'equal', left: oldList[s.i], right: newList[s.j] })
      continue
    }
    const dels = oldList.slice(s.i0, s.i1)
    const inss = newList.slice(s.j0, s.j1)
    const k = Math.min(dels.length, inss.length)
    for (let n = 0; n < k; n++) {
      rows.push({ type: 'changed', left: dels[n], right: inss[n] })
    }
    for (let n = k; n < dels.length; n++) {
      rows.push({ type: 'removed', left: dels[n], right: null })
    }
    for (let n = k; n < inss.length; n++) {
      rows.push({ type: 'added', left: null, right: inss[n] })
    }
  }
  return rows
}
