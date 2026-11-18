/**
 * 代码块画图的**共用骨架**（2026-11-11 收敛）。
 *
 * ## 为什么要有这个文件
 *
 * 正文里能画成图的有两种围栏：` ```mermaid `（交给 mermaid 的布局器）和
 * ` ```kbfig `（模型只填 JSON，布局归组件）。两者的**外观与交互完全一样**：
 * 图在上、源码在下（默认收起），右上角一个很淡的「源码」开关，
 * 画不出来时说清为什么并把源码摊开。
 *
 * ⇒ 这些"骨架"以前在 `kbFig.js` 和 `kbMermaid.js` 里**各写了一遍**：
 * DOM 构造、按钮行为、`ignoreMutation`、`update` 逐行相同。
 * 改一处漏一处的后果特别阴：**一个图改了样式、另一个没改**，
 * 而且**界面上没有任何异常**（两处都能正常画图）。
 *
 * ⇒ 现在骨架只有**这一份**，两个文件只提供各自的 `paint`（怎么把源码变成画面）。
 *
 * ## 骨架负责什么、paint 负责什么
 *
 * **骨架**（本文件）：
 * - 造 DOM（容器 / 图区 / 源码区 / 开关按钮）；
 * - 「源码」开关的展开-收起（连带按钮文案）；
 * - **缓存**（源码没变就不重画）与 **作废判定**（连着来了两次画，后一次赢）；
 * - 画失败时的统一表现：图区写清原因 + 把源码摊开。
 *
 * **paint**（各自实现）：
 * - mermaid：`mermaid.render()` 拿到 SVG 塞进图区；
 * - kbfig：解析 JSON、按布局函数造 DOM 塞进图区。
 *
 * paint 的约定：
 * - 成功 ⇒ 把**画面**塞进 `ctx.canvas`，返回 `{ kind, title }`（都可以不给）：
 *   `kind` 是图的类型标签（取自 `figureKinds.js`），`title` 是图名；
 * - 失败 ⇒ 返回 `{ error: '图没画出来：…' }`（**图区里显示的原文**），
 *   骨架负责显示它并摊开源码；
 * - 流式/异步的 paint 拿 `ctx.stale()` 自查：中途又有一次画了，这次就作废
 *   （mermaid 的 `render` 是异步的，两次可能交错结束）。
 *
 * ## 为什么画面单独一层（`canvas`）
 *
 * mermaid 的画法是 `fig.innerHTML = svg` —— **整块覆盖**。
 * 如果类型标签和图名跟画面共用一个容器，每次重画都会把标签一起冲掉
 * （症状很怪：图在、标签只闪一下就没）。所以分成
 *
 * ```
 * fig
 *  ├ head  ← 类型标签 + 图名（骨架管，paint 不碰）
 *  └ canvas ← 画面（paint 随便覆盖）
 * ```
 */

/**
 * 建元素 + 可选类名/文本。
 *
 * <p>**全部走 `textContent`，不拼 HTML** —— 正文是用户写进来的内容，
 * 拼字符串就是注入面。
 */
export function el(tag, cls, text) {
  const n = document.createElement(tag)
  if (cls) n.className = cls
  if (text != null) n.textContent = text
  return n
}

/**
 * 展开 / 收起源码区（按钮文案跟着变）。
 *
 * @param {HTMLElement} dom 代码块容器
 * @param {HTMLElement} btn 开关按钮
 * @param {boolean} open 展开还是收起
 */
export function setSrcOpen(dom, btn, open) {
  dom.classList.toggle('is-src-open', open)
  btn.textContent = open ? '收起' : '源码'
}

/**
 * 建一个「代码块画图」的 nodeView。
 *
 * @param {import('@milkdown/prose/model').Node} node code_block 节点
 * @param {object} opts
 * @param {string} opts.prefix 类名前缀（`kb-mermaid` / `kbfig`）—— 容器、图区、源码区、按钮都按它拼
 * @param {(ctx: {fig: HTMLElement, text: string, stale: () => boolean}) => Promise<{error:string}|null>|{error:string}|null} opts.paint
 *        把源码变成画面。返回 `{error}` 表示画不出来（**错误原文**）；
 * @param {boolean} [opts.keepPrevious] 重画时**先留着上一张图**，等新的画好再换
 *        （mermaid 要：SVG 画一次要上百毫秒，中间留白比"闪一下"难看）。
 *        同步渲染的（kbfig）不用留 —— 直接换。
 * @param {(view: object) => (() => void)|void} [opts.onMount] 建好之后立刻调；
 *        返回值（清理函数）会在销毁时执行
 * @returns {object} ProseMirror 的 nodeView
 */
export function createCodeFigureView(node, { prefix, paint }) {
  const dom = el('div', prefix)
  const fig = el('div', prefix + '__fig')
  // 图上的一行：类型标签 + 图名。**paint 不碰这两个**（见文件头「为什么画面单独一层」）
  const head = el('div', prefix + '__head is-empty')
  const kind = el('span', prefix + '__kind')
  const title = el('div', prefix + '__title')
  head.append(kind, title)
  // 画面层：mermaid 会整块 innerHTML 覆盖，所以它必须独立成层。
  // ⚠️ 类名不能叫 `__body` —— `kbfig__body` 已经是流程图里的**步骤卡片**（白底 + 主色描边），
  // 重名会让每张步骤卡片都被套上画布的背景/内距，而且不报错、只是变丑。
  const canvas = el('div', prefix + '__canvas')
  fig.append(head, canvas)
  const pre = el('pre', prefix + '__src')
  const code = el('code')
  pre.append(code)

  /**
   * 清空「图之上那一行」。
   *
   * <p>没有类型也没有图名时整行不占高度 —— 不然每张没名字的图都会多出 8px 空白，
   * 看起来像排版错位。
   */
  const clearHead = () => {
    kind.textContent = ''
    title.textContent = ''
    head.classList.add('is-empty')
  }

  /** 把一个图块的 head 写成 paint 报上来的内容（空就说空） */
  const setHead = (res) => {
    kind.textContent = (res && res.kind) || ''
    title.textContent = (res && res.title) || ''
    kind.classList.toggle('is-empty', !kind.textContent)
    head.classList.toggle('is-empty', !kind.textContent && !title.textContent)
  }

  // 「源码」开关：**默认把源码收起来**。
  // 读者要的是图，那串源码摆在图下面只是噪声；
  // 但作者改图必须能看到它 ⇒ 留一个按钮，点开照旧可编辑。
  const toggle = el('button', prefix + '__srcbtn', '源码')
  toggle.type = 'button'
  // mousedown 就拦掉：不拦的话按钮会抢走编辑器焦点，点一下光标就跑进代码块
  toggle.addEventListener('mousedown', (e) => e.preventDefault())
  toggle.addEventListener('click', (e) => {
    e.preventDefault()
    e.stopPropagation()
    setSrcOpen(dom, toggle, !dom.classList.contains('is-src-open'))
  })

  dom.append(fig, toggle, pre)

  // 记上一次画的源码：内容没变就不重画
  let last = null

  /** 画一次。 */
  const runPaint = (text) => {
    if (text === last) return
    last = text
    if (!text.trim()) {
      canvas.textContent = ''
      canvas.classList.remove('is-error')
      clearHead()
      return
    }
    canvas.textContent = ''
    canvas.classList.remove('is-error')

    const res = paint({ canvas, text })
    if (res && res.error) {
      // **画不出来不许吞掉**：源码留在下面（能改），上面写清楚为什么没画出来。
      // 静默留空 = 用户以为"这知识没有图"。
      canvas.textContent = res.error
      canvas.classList.add('is-error')
      // 画不出来就没有「这是哪种图」可说 —— 别硬标一个
      clearHead()
      // 画不出来时摊开源码 —— 这时用户唯一的出路就是改源码
      setSrcOpen(dom, toggle, true)
    } else {
      // 画成功了 ⇒ 错误态要撤掉（上一轮失败留下的红色不能染到这一轮的图上）
      canvas.classList.remove('is-error')
      setHead(res)
    }
  }
  runPaint(node.textContent)

  const view = {
    dom,
    // 内容落在 <code> 里：源码照常可编辑、可选中
    contentDOM: code,
    /**
     * ⚠️ **没有这一条，图永远画不出来，而且整页会上下抖**（2026-10-28 定位）。
     *
     * ProseMirror 盯着编辑器里的 DOM 变动。我们把 SVG 塞进**图区**（`fig`，
     * 它不在 contentDOM 里）⇒ 它认为"这个节点的内容被人从外面改了"
     * ⇒ 把整个 code_block 判成 dirty ⇒ **nodeView 整个重建**
     * ⇒ 重建时又画一次图 ⇒ 图一变又触发重建 ⇒ **无限循环**（实测每 50ms 一轮）。
     *
     * 用户看到的就是这两件事：图区永远是空的（刚画好就被换掉了）、
     * 页面上下抖动（正文高度每 50ms 在"图高 0 ⇄ 图高"之间翻一次）。
     *
     * 所以：**只有 contentDOM 里的变动才算"用户改了内容"**；
     * 图区是我们自己画上去的，一律不认。
     */
    ignoreMutation: (mutation) => !code.contains(mutation.target),
    update: (next) => {
      if (next.type.name !== 'code_block') return false
      node = next
      runPaint(next.textContent)
      return true
    }
  }

  return view
}
