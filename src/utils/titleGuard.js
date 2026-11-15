/**
 * 触屏模式下隐藏原生 title 提示（2026-11-13，用户：「触屏的时候这些按钮就不弹提示」）。
 *
 * ## 为什么需要它
 * 原生 title 是浏览器自己画的 hover 提示，CSS 管不到它；Windows 触屏（Chrome/Edge）
 * 点一下会触发 hover 模拟 ⇒ title 提示弹出来还赖着不走。
 * el-tooltip 那一路（KbTooltip）在组件里禁了；这里管剩下散在全站的原生 title。
 *
 * ## 机制（按需清扫，不挂常驻观察器）
 * - pointerdown（capture）发现这一下不是鼠标 ⇒ 把当前 DOM 里所有 [title]
 *   摘下来记进 Map（键 = 元素，值 = 原文字）；
 * - 鼠标 pointerdown ⇒ 从 Map 恢复回去（触屏笔记本换回鼠标点一下就还原）。
 * - **每次触摸点按都重扫**：触摸期间新渲染出来的按钮（弹窗、菜单）也在覆盖范围内。
 *   扫描发生在 pointerdown 捕获阶段 —— 早于浏览器补发的 mouseover ⇒
 *   这一下点按的原生提示来不及出现。
 *
 * ## 性能（用户点名要求不能卡）
 * - 鼠标用户：监听器读一个 pointerType 字段就返回，**零额外开销**；
 * - 触屏用户：每次点按一次 `querySelectorAll('[title]')`（毫秒级，不在任何帧路径上）；
 * - **不用 MutationObserver**：childList 观察会挂在每一次 DOM 插入上（编辑器打字也在
 *   其中），换「点按即扫」就没有任何常驻成本。
 *
 * ## 已知边界（与 utils/touch.js / KbTooltip 同一口径）
 * - 混合设备「触摸 → 换鼠标只 hover 不点」：title 要等鼠标**点一下**才恢复
 *   （touch.js 只记 pointerdown 的鼠标；pointermove 太频繁故意不记）。
 * - 判据是**这一下用什么点的**（PointerEvent.pointerType），不是设备类型 ——
 *   ⚠️ 别拿 `@media (hover: none)` / `(pointer: coarse)` 判（见 touch.js 头注释，
 *   2026-11-11 在 Windows 11 触屏笔记本上实测不命中）。
 */

/** 被摘下来的 title：元素 → 原文字。恢复时原样写回；元素已离场（isConnected false）就跳过。 */
const stash = new Map()

function stripTitles() {
  for (const el of document.querySelectorAll('[title]')) {
    if (!stash.has(el)) stash.set(el, el.getAttribute('title'))
    el.removeAttribute('title')
  }
}

function restoreTitles() {
  for (const [el, title] of stash) {
    if (el.isConnected) el.setAttribute('title', title)
  }
  stash.clear()
}

/**
 * 挂全局监听（幂等，应用启动时调一次）。
 * 生产入口（main.js）和 demo 入口（mockup/main-demo.js）都走 bootstrap.js，只需要在那里调。
 */
export function initTitleGuard() {
  if (typeof window === 'undefined') return
  window.addEventListener(
    'pointerdown',
    (e) => {
      if (e.pointerType === 'mouse') {
        if (stash.size) restoreTitles()
      } else if (e.pointerType) {
        // touch / pen 都算触屏（笔尖同样没有 mouseleave）
        stripTitles()
      }
      // pointerType 为空（老浏览器）按鼠标处理，不动 —— 与 touch.js 的兜底口径一致
    },
    true
  )
  // 兜底：个别环境 pointerdown 不带 pointerType，touchstart 一定是指 ⇒ 重扫一遍（幂等）
  window.addEventListener('touchstart', stripTitles, true)
}
