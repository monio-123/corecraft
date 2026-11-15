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
import { figView, FIG_LANG } from './kbFig'
import { createCodeFigureView } from './kbCodeFigure'
import { kindOf, mermaidType } from './figureKinds'
import { figStats } from './kbFigureStats'

const LANG = 'mermaid'

/** mermaid 本体：懒加载 */
let mermaidPromise = null
function loadMermaid() {
  if (!mermaidPromise) {
    mermaidPromise = import('mermaid').then((mod) => mod.default)
  }
  return mermaidPromise
}

/**
 * 当前已按哪个主题初始化过（`html[data-theme]` 的取值；本色是空串）。
 * 换主题后要重新 initialize，否则新画的图还是旧配色的。
 */
let initedTheme = null

/**
 * mermaid 的主题变量：**全部从 CSS token 现读**。
 *
 * ## 为什么不用 mermaid 的 `default` 主题
 *
 * 出厂 `default` 是淡紫方块 + 紫边框 + Times 字体 —— 和这个页面（纸色底、
 * 衬线标题、青瓷绿）是两套设计语言，图放在正文里像贴了一张别的网站的截图。
 * 用户 2026-11-09 的原话是"优美质感基本为 0"。
 *
 * ## 为什么读 token 而不是写死三套值
 *
 * 主题一共三套（本色 / 素雅 / 量子，`html[data-theme]`），值都住在
 * `assets/themes.css` 的 CSS 变量里。这里写死就等于把第三份配色表又抄一遍
 * （违反"零冗余"）；读 token 则**主题怎么调，图自动跟着调**。
 *
 * ## 为什么是 `base` 主题
 *
 * mermaid 只有 `base` 会**完全**按 themeVariables 着色（default/forest 等
 * 各自带一套优先于变量的硬编码色）⇒ 定制必须从 `base` 出发。
 */
function themeVars() {
  const cs = getComputedStyle(document.documentElement)
  const v = (name, fallback) => cs.getPropertyValue(name).trim() || fallback
  const surface = v('--cc-surface', '#ffffff')
  const surface2 = v('--cc-surface-2', '#f7f8fa')
  const text = v('--cc-text-1', '#1f2329')
  const text2 = v('--cc-text-2', '#4e5969')
  const line = v('--cc-text-3', '#86909c')
  const primary = v('--cc-primary', '#2b7fff')
  const rgb = hexToRgb(primary) || [43, 127, 255]
  return Object.assign(
    {
      darkMode: false,
      // 透明底：图的"纸"就是正文的纸，不另起一块
      background: 'transparent',
      fontFamily: v('--font-body', 'sans-serif'),
      fontSize: '14px',
      // 节点：纸色底 + 主色淡描边（纯灰边在纸色上几乎看不见，图会"散"在正文里）
      primaryColor: surface,
      primaryTextColor: text,
      primaryBorderColor: tint(rgb, 0.4),
      secondaryColor: surface2,
      secondaryTextColor: text2,
      secondaryBorderColor: tint(rgb, 0.3),
      tertiaryColor: surface2,
      tertiaryTextColor: text2,
      tertiaryBorderColor: tint(rgb, 0.3),
      mainBkg: surface,
      nodeBorder: tint(rgb, 0.4),
      nodeTextColor: text,
      // 连线与全局文字：线降到 text-3，别再抢正文的注意力
      lineColor: line,
      textColor: text,
      titleColor: text,
      edgeLabelBackground: surface,
      // 子图（subgraph）底色：和正文区别一档，不喧宾
      clusterBkg: surface2,
      clusterBorder: tint(rgb, 0.25),
      // 时序图
      actorBkg: surface,
      actorBorder: tint(rgb, 0.4),
      actorTextColor: text,
      actorLineColor: line,
      signalColor: line,
      signalTextColor: text2,
      labelBoxBkgColor: surface,
      labelBoxBorderColor: tint(rgb, 0.3),
      labelTextColor: text,
      noteBkgColor: surface2,
      noteBorderColor: tint(rgb, 0.3),
      noteTextColor: text2,
      // 状态图 / 其它图型共用的标签底
      labelBackground: surface
    },
    // mindmap / timeline / journey 不吃 primaryColor，吃 cScale* 这套色阶 ⇒ 必须单独给
    scaleVars(rgb, text)
  )
}

/** `#rrggbb` → [r,g,b]；读不到（比如主题值将来改成 rgb() 写法）就返回 null 走兜底 */
function hexToRgb(hex) {
  const m = /^#?([0-9a-f]{6})$/i.exec(String(hex).trim())
  if (!m) return null
  const n = parseInt(m[1], 16)
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255]
}

/** 与白混合：ratio=1 原色，0 纯白 */
function tint([r, g, b], ratio) {
  const m = (c) => Math.round(c * ratio + 255 * (1 - ratio))
  return `rgb(${m(r)},${m(g)},${m(b)})`
}

/**
 * 主色 → 12 档同色浅阶（cScale0…11）。
 *
 * <p>**为什么必须单独给**：实测（2026-11-09 三主题截图对比）mindmap 完全不吃
 * `primaryColor` —— 不给这套，思维导图仍是 mermaid 出厂的荧光黄/亮绿/棕，
 * 比换肤前还刺眼。timeline / journey 同理。
 */
function scaleVars(rgb, label) {
  const out = {}
  for (let i = 0; i < 12; i++) {
    const shade = tint(rgb, 0.82 - i * 0.065)
    out['cScale' + i] = shade
    out['cScaleInv' + i] = '#ffffff'
    out['cScaleLabel' + i] = label
    if (i < 8) {
      out['git' + i] = shade
      out['gitBranchLabel' + i] = label
    }
  }
  return out
}

/** 按当前 `data-theme` 初始化（主题没变就不重复 initialize） */
function initForTheme(mermaid) {
  const theme = document.documentElement.dataset.theme || ''
  if (theme === initedTheme) return
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
    theme: 'base',
    themeVariables: themeVars(),
    /*
     * 连线走 basis（平滑曲线）：直角折线在节点一多时全是十字路口，
     * 曲线能让人一眼跟着"流向"走。flowchart 之外（时序/状态）本就画曲线，不受影响。
     */
    flowchart: { curve: 'basis', padding: 12 },
    // 时序图的消息别挤在一起
    sequence: { actorMargin: 42, messageMargin: 32 }
  })
  initedTheme = theme
}

/** 所有还活着的图（主题切换后逐个重画；图是 SVG，不会自己跟着 CSS 变量变） */
const repaints = new Set()

/** 监听 `html[data-theme]`：切主题后让所有图按新 token 重画一遍 */
let themeObserver = null
function watchTheme() {
  if (themeObserver) return
  themeObserver = new MutationObserver(() => {
    for (const repaint of repaints) repaint()
  })
  themeObserver.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ['data-theme']
  })
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
 * <p>**骨架是共用的**（`kbCodeFigure.js`）：DOM 结构、「源码」开关、
 * 缓存与作废判定、画失败的表现都在那里一份（与 kbfig 同一份）
 * ⇒ 这里只提供"怎么把 mermaid 源码画成图"。
 *
 * @param {import('@milkdown/prose/model').Node} node code_block 节点
 */
function mermaidView(node) {
  return createCodeFigureView(node, {
    prefix: 'kb-mermaid',
    // SVG 画一次要上百毫秒 ⇒ 重画时先留着上一张，等新的画好再换（不闪白）
    keepPrevious: true,
    paint: async ({ canvas, text, stale }) => {
      // id 每次都不一样：mermaid 靠它认自己的临时节点，出错时我们也要靠它清理
      const id = `kb-mermaid-${++seq}`
      try {
        const mermaid = await loadMermaid()
        initForTheme(mermaid)
        // 类型标签要在**画之前**算：detectType 只看首行和结构，不排版，很便宜。
        // 放在 render 之后算也行，但那时画失败就该整个返回 error，标签没意义。
        // **一次判定，标签和计数共用**：`types` 要的是原始类型键（flowchart-v2），标签要中文
        const type = mermaidType(mermaid, text)
        const kind = kindOf(type, text)
        const { svg } = await withTimeout(mermaid.render(id, text), RENDER_TIMEOUT, '画图超时')
        // 已经有更新的一次画完了，这次作废
        if (stale()) return null
        canvas.innerHTML = svg
        // ⚠️ **mermaid 的类型必须也计进 `types`**：只计 kbfig 的话，「mermaid 里 77% 都是
        // flowchart」这种事**系统自己发现不了**（只能靠人去数库）—— 而选型退化的第一信号就是它。
        // 前缀 `mermaid:` 是为了和 kbfig 的 shapes 分开（两边都有 stages 之类的词）。
        if (type) {
          const k = 'mermaid:' + type
          figStats.types[k] = (figStats.types[k] || 0) + 1
        }
        return { kind }
      } catch (e) {
        if (stale()) return null
        // 清掉 mermaid 留在页面上的临时节点（中途抛错时它自己不会删）
        cleanupMermaidLeftovers(id)
        // **画不出来不许吞掉**：源码留在下面（能改），上面写清楚为什么没画出来。
        // 静默留空 = 用户以为"这知识没有图"。
        // 抛错和超时都落这里：**不计数就永远不知道"图没画出来"发生过多少次**
        figStats.mermaidFail++
        return { error: '图没画出来：' + (e?.message || '语法有误') }
      }
    },
    onMount: (view) => {
      // 切主题（html[data-theme] 变化）后按新 token 重画：SVG 里的颜色是写死的，
      // 它不会跟着 CSS 变量自己变 —— 不重画，图就还是上一个主题的配色。
      const repaint = () => view.repaint()
      watchTheme()
      repaints.add(repaint)
      // 节点被删掉/整页卸载时注销，别让 repaints 攒一堆已销毁的视图
      return () => repaints.delete(repaint)
    }
  })
}

/**
 * mermaid 插件。挂在 `.use(commonmark)` 之后即可 —— 它只是**视图层**的 nodeView，
 * 不改 schema、不改 markdown 解析与回写。
 *
 * ⚠️ **这里是「代码块画图」的唯一注册点**，mermaid 和结构化图（kbfig）都在这一处
 * 分派。原因：ProseMirror 合并多个插件的 `nodeViews` 时，同一个 key（`code_block`）
 * 是**后注册的整张表覆盖先注册的**，各注册一个插件会有一个静默失效
 * （mermaid 或 kbfig 其中之一永远画不出来，而且没有任何报错）。
 */
export function mermaidPlugin() {
  return $prose(() => new Plugin({
    props: {
      nodeViews: {
        code_block: (node) => {
          const lang = String(node.attrs.language || '').toLowerCase()
          // 结构化图：模型只填 JSON，布局归组件（见 kbFig.js）
          if (lang === FIG_LANG) return figView(node)
          // 不是 mermaid 就返回 null ⇒ ProseMirror 走 preset 的默认渲染
          if (lang !== LANG) return null
          return mermaidView(node)
        }
      }
    }
  }))
}
