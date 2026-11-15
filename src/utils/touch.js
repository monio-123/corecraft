/**
 * 指针交互方式探测（2026-11-11）。
 *
 * ## 要回答的问题只有一个
 * **「用户刚才这一下，是用手指点的，还是用鼠标指的？」**
 * tooltip 的关闭策略完全取决于这个答案：
 * - 鼠标 ⇒ 靠 `mouseleave` 关闭（自然、精确）
 * - 手指 ⇒ **没有 mouseleave** ⇒ 必须靠「点一下自动关」或「点别处关」
 *
 * ## ⚠️ 为什么不能用媒体查询判（这是本文件第一版踩的坑）
 * 第一版用 `matchMedia('(hover: none)')` 判「是不是触屏设备」，**在用户真机上失效了**。
 *
 * 用户设备实测（Windows 11 触屏笔记本，截图左上角有触屏板图标）：
 * ```
 * matchMedia('(hover: none)').matches      → false   ← 没命中！
 * matchMedia('(any-hover: hover)').matches → true
 * navigator.maxTouchPoints                → 1      ← 但明明有触屏
 * ```
 * 原因：`(hover: none)` 描述的是「**只能**触屏、压根没有鼠标」的设备。
 * **触屏笔记本 = 触屏 + 鼠标的混合设备** ⇒ 有鼠标 ⇒ `hover` 能力存在 ⇒ 不命中。
 * ⇒ 于是代码以为「这是鼠标设备」⇒ 走 hover 分支 ⇒ **手指点完没有 mouseleave**
 * ⇒ **提示永远不消失**（用户原话：「特别是平板触屏使用的时候一直不消失 看着很烦」）。
 *
 * 同理 `(pointer: coarse)` 也不行：它描述「**主要**输入指针是粗的」，
 * Surface 三合一设备上会报 `fine`，但用户实际在用手指。
 *
 * ## ⇒ 正确判据：**看最近一次交互用的是哪种指针**
 * 浏览器会告诉我们：`PointerEvent.pointerType` 就是 `mouse` / `touch` / `pen`。
 * 监听真实的 pointer 事件 ⇒ 用户用手指点过就切到「触屏模式」，
 * 用鼠标点过就切回「鼠标模式」⇒ **混合设备天然正确**，不需要猜设备类型。
 *
 * 附带好处：**不靠设备类型，靠用户当下的行为** ——
 * 笔记本插着鼠标用，就是鼠标交互；用户改用手指点，就是触屏交互。
 *
 * ## 兜底
 * 完全没有 PointerEvent 的老浏览器 ⇒ 按「鼠标」处理（= 桌面端行为，不会坏）。
 */

/** 最近一次交互用的指针类型（'mouse' / 'touch'） */
let lastPointerType = 'mouse'

/** 指针类型变化时的订阅者 */
const listeners = new Set()

/** 是否已经挂过全局监听（只挂一次） */
let bound = false

function notify() {
  listeners.forEach((fn) => {
    try {
      fn(lastPointerType)
    } catch {
      /* 单个订阅者出错不影响其它 */
    }
  })
}

/**
 * 记录一次指针交互。
 *
 * `pointerType` 的取值：`mouse`（鼠标）/ `touch`（手指）/ `pen`（触控笔）。
 * 触控笔**当作触屏处理** —— 笔尖划过去同样没有 mouseleave，同样会赖着不走。
 */
function recordPointer(type) {
  const normalized = type === 'mouse' ? 'mouse' : 'touch'
  if (normalized === lastPointerType) return
  lastPointerType = normalized
  notify()
}

/**
 * 订阅「指针类型」变化。
 *
 * @param {(type: 'mouse' | 'touch') => void} handler
 * @returns {() => void} 取消订阅
 */
export function onPointerTypeChange(handler) {
  listeners.add(handler)
  ensureBound()
  return () => listeners.delete(handler)
}

/**
 * 当前应当按哪种方式处理提示。
 *
 * @returns {'mouse' | 'touch'}
 */
export function currentPointerType() {
  ensureBound()
  return lastPointerType
}

/**
 * 是否处于「触屏交互」状态（手指 / 触控笔）。
 *
 * @returns {boolean}
 */
export function isTouchInteraction() {
  return currentPointerType() === 'touch'
}

/** 把监听挂到 window 上（幂等，只挂一次） */
function ensureBound() {
  if (bound) return
  if (typeof window === 'undefined') return
  bound = true
  // pointerdown 是最可靠的一次性信号（点击 = 用户明确操作了）
  window.addEventListener('pointerdown', (e) => recordPointer(e.pointerType), true)
  // pointermove 也记一下：用户只是划过（没点）也要切，
  // 否则「鼠标划过 ⇒ 提示该用鼠标逻辑」的判断会滞后
  window.addEventListener('pointermove', (e) => {
    // 只关心 touch/pen，mouse 的 move 太频繁不必每次都记
    if (e.pointerType && e.pointerType !== 'mouse') recordPointer(e.pointerType)
  }, true)
  // 触摸事件兜底（某些环境 pointerdown 不带 pointerType）
  window.addEventListener('touchstart', () => recordPointer('touch'), true)
}
