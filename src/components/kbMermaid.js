/**
 * mermaid 代码块的渲染：**语言是 mermaid 的代码块画成图**，源码仍然留在下面可编辑。
 *
 * ## 为什么模型写得出图，这里才画得出
 *
 * 模型能写的"图"只有两种：**mermaid 源码**（```mermaid 围栏）和 ASCII 字符画。
 * 渲染层不支持就等于没写 —— 之前提示词里明写着「不要写图片和流程图」，
 * 正是因为渲染层画不了。现在渲染层能画了，那条禁令才该放开（提示词见 sys_config）。
 *
 * ## 为什么自己写，不装 `@milkdown/plugin-diagram`
 *
 * ⚠️ **核过它的源码（2026-10-26）**：那个包**根本不画图** ——
 * 它的 remark 插件只把 ```mermaid 代码块升格成一个 **atom 节点**（`diagram`），
 * 节点的 `toDOM` 输出 `<div data-type="diagram" data-value="源码">`，
 * **里面放的是源码文本**；整个包里 `render` / `svg` / `img` / `nodeView` **一次都没出现**
 * （`mermaid.initialize()` 调了，但没人调 `mermaid.render()`）。
 * ⇒ 它做的是"把 mermaid 块升格成一种独立节点"，**画图这一步得自己补**。
 *
 * 另外三条不选它的理由：
 * - **它停在 7.7.0**（本项目 core 是 7.22.1，而 `@milkdown/kit@7.22.1` 的依赖里**已经没有它**）
 *   ⇒ 同大版本内落后 15 个小版本，**升级 core 也不会把它带回来**。
 * - 它会**自带一份 mermaid 10**（本项目要 12）⇒ 等于装两套 mermaid。
 * - 它把 mermaid 块变成 **atom 节点**（整块当一个不可分割的块）⇒ 与本项目
 *   "所见即所得、源码要能逐字改"的做法不合。
 *
 * 自己写只要一个 nodeView：**别的语言一律返回 null**（走 preset 默认渲染），
 * 不碰 preset 的代码块定义，也就不影响 markdown 的解析和回写。
 *
 * ## 动态 import
 *
 * mermaid 本体不小（gzip 后几百 KB）。**动态 import** 让 Vite 单独切一个 chunk，
 * 只有正文里真的出现 mermaid 围栏时才加载 —— 没有图的用户不付这个成本。
 *
 * ## 源码为什么留着
 *
 * 这是个所见即所得编辑器：读者要的是图，作者要的是**能改**。
 * 图渲染在上面、源码在下面（可点进去编辑），两个都要 —— 只留图就没法改了，
 * 只留源码那就等于没渲染。
 */
import { $prose } from '@milkdown/utils'
import { Plugin } from '@milkdown/prose/state'

const LANG = 'mermaid'

/** mermaid 本体：懒加载 + 只初始化一次 */
let mermaidPromise = null
function loadMermaid() {
  if (!mermaidPromise) {
    mermaidPromise = import('mermaid').then((mod) => {
      const mermaid = mod.default
      mermaid.initialize({
        startOnLoad: false,
        // strict：图里的 HTML 标签不许执行（正文是用户自己写进来的内容）
        securityLevel: 'strict',
        theme: 'default'
      })
      return mermaid
    })
  }
  return mermaidPromise
}

/** 每次渲染一个唯一 id —— mermaid.render 会往 body 里插一个临时元素，id 撞了就报错 */
let seq = 0

/**
 * 一个 mermaid 代码块的视图。
 *
 * @param {import('@milkdown/prose/model').Node} node code_block 节点
 */
function mermaidView(node) {
  const dom = document.createElement('div')
  dom.className = 'kb-mermaid'
  const fig = document.createElement('div')
  fig.className = 'kb-mermaid__fig'
  const pre = document.createElement('pre')
  pre.className = 'kb-mermaid__src'
  const code = document.createElement('code')
  pre.appendChild(code)
  dom.append(fig, pre)

  // 记上一次画的源码：内容没变就不重画（mermaid.render 不便宜）
  let last = null
  const paint = async (text) => {
    if (text === last) return
    last = text
    if (!text.trim()) {
      fig.textContent = ''
      fig.classList.remove('is-error')
      return
    }
    try {
      const mermaid = await loadMermaid()
      const { svg } = await mermaid.render(`kb-mermaid-${++seq}`, text)
      fig.innerHTML = svg
      fig.classList.remove('is-error')
    } catch (e) {
      // **画不出来不许吞掉**：源码留在下面（能改），上面写清楚为什么没画出来。
      // 静默留空 = 用户以为"这知识没有图"。
      fig.textContent = '图没画出来：' + (e?.message || '语法有误')
      fig.classList.add('is-error')
    }
  }
  paint(node.textContent)

  return {
    dom,
    // 内容落在 <code> 里：源码照常可编辑、可选中
    contentDOM: code,
    update: (next) => {
      if (next.type.name !== 'code_block') return false
      node = next
      paint(next.textContent)
      return true
    }
  }
}

/**
 * mermaid 插件。挂在 `.use(commonmark)` 之后即可 —— 它只是**视图层**的 nodeView，
 * 不改 schema、不改 markdown 解析与回写。
 */
export function mermaidPlugin() {
  return $prose(() => new Plugin({
    props: {
      nodeViews: {
        code_block: (node) => {
          const lang = String(node.attrs.language || '').toLowerCase()
          // 不是 mermaid 就返回 null ⇒ ProseMirror 走 preset 的默认渲染
          if (lang !== LANG) return null
          return mermaidView(node)
        }
      }
    }
  }))
}
