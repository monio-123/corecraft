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
        // **不许把错误图画到页面上**（2026-10-28 加）。
        // 语法错的图，mermaid 默认会往 body 里塞一张巨大的「Syntax error in text」图
        // —— 那张图跟正文没关系，却会显示在页面底部；而且它自己会带一堆 svg 动画，
        // 足以把渲染进程拖死（实测：一张坏图就把浏览器整个卡住，截图都超时）。
        // 我们自己有错误提示（"图没画出来：…"），不需要它那张。
        suppressErrorRendering: true,
        theme: 'default'
      })
      return mermaid
    })
  }
  return mermaidPromise
}

/**
 * 给一个 promise 加超时。
 *
 * <p>**为什么需要**：mermaid 渲染卡住时既不成功也不报错（实测 classDiagram 走 elk 布局时
 * 出现过一直不返回）⇒ 图区就那么空着，用户看不出是"图没画出来"还是"还没画完"。
 * ⇒ 超时后自己判成失败，至少给一句话。
 *
 * @param {Promise<any>} p 要等的 promise
 * @param {number} ms 超时毫秒
 * @param {string} what 超时后抛出的错误里写什么
 */
function withTimeout(p, ms, what) {
  return Promise.race([
    p,
    new Promise((_, reject) =>
      setTimeout(() => reject(new Error(`${what}（超过 ${Math.round(ms / 1000)} 秒）`)), ms)
    )
  ])
}

/**
 * 清掉 mermaid 留在页面上的临时节点。
 *
 * <p>mermaid 渲染时会在 body 里放一个 `d<id>` 的临时容器（正常渲染完会自己删）。
 * **中途抛错时它不会删** ⇒ 页面上会留下一个空壳（或者半张图）。
 *
 * @param {string} id 传给 mermaid.render 的那个 id
 */
function cleanupMermaidLeftovers(id) {
  for (const sel of [`#${id}`, `#d${id}`]) {
    document.querySelector(sel)?.remove()
  }
}

/** 渲染多久算超时（首次渲染要顺带下 mermaid 本体，elk 布局引擎最大） */
const RENDER_TIMEOUT = 15000

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
  // 画图是异步的，两次 paint 可能交错结束 ⇒ 用序号认领"最后一次"
  let ticket = 0
  const paint = async (text) => {
    if (text === last) return
    last = text
    const mine = ++ticket
    if (!text.trim()) {
      fig.textContent = ''
      fig.classList.remove('is-error')
      return
    }
    // id 每次都不一样：mermaid 靠它认自己的临时节点，出错时我们也要靠它清理
    const id = `kb-mermaid-${++seq}`
    try {
      const mermaid = await loadMermaid()
      const { svg } = await withTimeout(mermaid.render(id, text), RENDER_TIMEOUT, '画图超时')
      if (mine !== ticket) return   // 已经有更新的一次画完了，这次作废
      fig.innerHTML = svg
      fig.classList.remove('is-error')
    } catch (e) {
      if (mine !== ticket) return
      // **画不出来不许吞掉**：源码留在下面（能改），上面写清楚为什么没画出来。
      // 静默留空 = 用户以为"这知识没有图"。
      cleanupMermaidLeftovers(id)
      fig.textContent = '图没画出来：' + (e?.message || '语法有误')
      fig.classList.add('is-error')
    }
  }
  paint(node.textContent)

  return {
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
