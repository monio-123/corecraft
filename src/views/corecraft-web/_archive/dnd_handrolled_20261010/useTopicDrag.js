/**
 * 卡片拖拽：PC 鼠标、平板、手机触摸**共用一套 Pointer Events**。
 *
 * ## 为什么不用 HTML5 Drag and Drop
 *
 * 原来整条链路（TopicCard 的 draggable / dragstart，CategoryTree 的 dragover / drop）
 * 都是 HTML5 DnD。那套 API 是给鼠标设计的，触摸设备上根本不工作：
 * iOS Safari 完全不支持；Android Chrome 支持有限，长按会先触发原生文本选择
 * 和上下文菜单，dragstart 根本轮不到触发。表现就是"平板上长按没反应"。
 * Pointer Events 是 W3C 标准，三端都支持。
 *
 * ## 鼠标和触摸的判定不一样
 *
 * - **鼠标**：按下后位移超过 MOVE_TOLERANCE 就开始拖。鼠标不存在"想滚动"这回事。
 * - **触摸**：先按住不动 LONG_PRESS_MS，才判定为"要拖了"。这期间如果动了，
 *   就是用户想滚动页面 —— 立刻放弃拖拽。这条判定**必须在滚动开始之前完成**，
 *   因为滚动一旦开始（compositor 线程接管），后面再 preventDefault 也抢不回来。
 *
 * ## 接管滚动：touch-action 要在第一个 touchmove 之前改
 *
 * 长按判定成功的那一刻把 touch-action 改成 none，之后的 touchmove 才能
 * preventDefault 掉。浏览器是在**第一次 touchmove 之前**重新读 touch-action 的，
 * 所以长按期间没动过手指 → 来得及切换。事后（拖拽已经开始了）再改就晚了，
 * 这也是为什么"长按 300ms 内不许动"不是偷懒，是机制要求。
 *
 * @see kb.css / base.css 里 .dnd-active 那条（拖拽期间禁选中文本）
 */
import { onBeforeUnmount, ref } from 'vue'
import { dnd, dndStart, dndMove, dndDrop, dndReset } from './useCorecraftWebStore'

/** 触摸：按住不动多久算"要拖了"。
 *
 *  取 250ms 而不是更长：系统的长按文本选择菜单在 ~500ms 之后才弹，
 *  我们必须在那之前就把手势接管掉。CSS 侧的 user-select / -webkit-touch-callout
 *  已经从根源上断了菜单（见 TopicCard.vue），这里只是第二道保险 —— 手势接管
 *  越早，"浮层闪一下就被 pointercancel 掐掉"的窗口越小。 */
const LONG_PRESS_MS = 250
/** 位移超过这个值还没进入拖拽 → 判定用户想滚动 / 移动，不是拖。 */
const MOVE_TOLERANCE = 8

/**
 * @param {() => object} getTopic 延迟取值：拿到的是哪张卡片在拖，pointerdown 那一刻才需要
 * @returns {{ onPointerDown: (e: PointerEvent) => void, dragged: import('vue').Ref<boolean> }}
 *   `dragged`：本次交互是否真的拖过。拖完松手浏览器会补一个 click，调用方用它把
 *   "归类"和"点开详情"区分开。
 */
export function useTopicDrag(getTopic) {
  const dragged = ref(false)
  let originX = 0
  let originY = 0
  let dragging = false
  let holdTimer = null
  // 本次交互是鼠标还是触摸：onMove 里要用它分道（鼠标超阈值=起拖，触摸超阈值=想滚动）
  let pointerType = 'mouse'
  // pointerdown 时就抓住卡片元素。**不能等到 setTimeout 回调里再读 e.currentTarget**：
  // 事件派发一结束 currentTarget 就会被置成 null（DOM 规范如此），回调里读必然报错。
  let cardEl = null

  function clearHold() {
    if (holdTimer) {
      clearTimeout(holdTimer)
      holdTimer = null
    }
  }

  /** 清理所有拖拽副作用：定时器、touch-action、body 上的禁选类、store 状态。 */
  function abort(committed) {
    clearHold()
    if (dragging) {
      if (committed) dndDrop()
      else dndReset()
      // 拖完浏览器会补一个 click，延到下一轮宏任务再放开，让那次 click 被吃掉
      dragged.value = true
      setTimeout(() => { dragged.value = false }, 0)
    }
    document.body.classList.remove('dnd-active')
    // 还原 touch-action：它是写在行内样式上的，不清这张卡片就再也滚不动
    if (cardEl) cardEl.style.touchAction = ''
    cardEl = null
    dragging = false
    originX = originY = 0
  }

  function beginDrag(x, y) {
    dragging = true
    dndStart(getTopic()?.id, x, y)
    document.body.classList.add('dnd-active')
    // 此刻才把 touch-action 切到 none —— 见文件头「接管滚动」那段。
    // 鼠标本来就不受 touch-action 管，设为 none 只为触摸路径。
    if (cardEl) cardEl.style.touchAction = 'none'
  }

  function onMove(e) {
    if (!dragging) {
      if (Math.hypot(e.clientX - originX, e.clientY - originY) <= MOVE_TOLERANCE) return
      // 鼠标：位移超阈值 = 用户要拖，从这里起拖。
      // 触摸：超阈值 = 用户要滚页面，长按判定作废。两者在这里分道扬镳。
      if (pointerType === 'mouse') beginDrag(e.clientX, e.clientY)
      else abort(false)
      return
    }
    // 已经在拖了还要 preventDefault：浏览器只有在这里还能阻止后续的滚动 / 手势接管。
    // 一旦某次 touchmove 没拦住，滚动被 compositor 拿走，后面就再也抢不回来了。
    e.preventDefault()
    dndMove(e.clientX, e.clientY)
  }

  function onPointerDown(e) {
    // 只认主键 / 单指：右键菜单、多指手势（缩放）不参与拖拽
    if (e.pointerType === 'mouse' && e.button !== 0) return
    if (!e.isPrimary) return
    pointerType = e.pointerType
    cardEl = e.currentTarget
    originX = e.clientX
    originY = e.clientY    // 触摸：按住不动 300ms 才起拖，期间移动则放弃（onMove 里判）
    // 鼠标：不设定时器，位移超阈值即起拖
    if (e.pointerType !== 'mouse') {
      holdTimer = setTimeout(() => {
        holdTimer = null
        beginDrag(originX, originY)
      }, LONG_PRESS_MS)
    }
    // 三个出口都要有：pointerup（正常松手）、pointercancel（系统手势 / 来电 /
    // 切后台打断）、blur（**鼠标拖出浏览器窗口再松开**）。
    // 少了 blur 会出现"浮层一直挂着、非得再点一下页面才消失"。
    window.addEventListener('pointermove', onMove)
    window.addEventListener('pointerup', onUp)
    window.addEventListener('pointercancel', onCancel)
    window.addEventListener('blur', onCancel)
  }

  function detach() {
    clearHold()
    window.removeEventListener('pointermove', onMove)
    window.removeEventListener('pointerup', onUp)
    window.removeEventListener('pointercancel', onCancel)
    window.removeEventListener('blur', onCancel)
  }

  function onUp() {
    detach()
    abort(dragging)
  }

  function onCancel() {
    // 系统手势 / 来电 / 切后台打断：直接取消，不归类。
    // blur 也在列：鼠标拖到浏览器窗口外再松开，pointerup 收不到（事件没落在文档里），
    // 浮层就会一直挂着，非得再点一下页面才消失。
    //
    // ⚠️ 触摸端的长按文本选择菜单也会走到这里（系统接管手势 → 浏览器发 pointercancel）。
    // 菜单本身已经在 TopicCard 的 user-select / -webkit-touch-callout 上被断掉了，
    // 这里只是不让"菜单抢先"把拖拽掐断。
    detach()
    abort(false)
  }

  onBeforeUnmount(() => {
    detach()
    abort(false)
  })

  return { onPointerDown, dragged }
}
