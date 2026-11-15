/**
 * 指针拖拽的公共骨架：PC / 平板 / 手机**三端同一套逻辑**。
 * **只挪目录**（目录拖到另一个目录下 = 换父级）——拖法只在一处。
 *
 * ## 为什么不自己从头写，也不直接用 SortableJS
 *
 * 两版都试过，各差一点：
 *
 * - **纯手写（Pointer Events）**：PC 完全可用，触摸端三次没成。
 * - **SortableJS**：触摸判定成熟，但 fallback 幽灵是 `cloneNode` 整张卡片，
 *   且库在 `_appendGhost()` 里给幽灵写了**行内** `width` / `height`
 *   （= 源卡片尺寸），每次移动都重写。CSS 改不动（行内样式 !important 也压不过），
 *   要改就得每帧跟库对着干。本项目要的幽灵是"一行标题"，跟库的模型对不上。
 *
 * 所以：**取两边的长处** —— 手写骨架（幽灵、浮层、命中全归我们），
 * 触摸判定照抄 SortableJS 的做法（下面这段就是它的逻辑）。
 *
 * ## 触摸判定的关键：不是"长按"，是"位移超阈值就起拖 + 每帧 preventDefault"
 *
 * SortableJS 的 `_onTouchMove` 里没有长按计时器，只有：
 *
 *     if (!dragging && Math.max(|dx|, |dy|) < TOLERANCE) return;  // 位移不够就不动
 *     beginDrag();                                                 // 一超阈值立刻起拖
 *     e.preventDefault();                                          // 每一帧都拦
 *
 * 之前手写版错在**只在起拖之后才 preventDefault**：长按判定期间没拦，
 * 手指一动浏览器就开始滚，滚动被 compositor 接管之后再 preventDefault 也抢不回来，
 * 于是"浮层闪一下就被掐掉"。每帧都拦才能在第一帧就把滚动按死。
 *
 * 至于长按会弹出浏览器的「复制 / 查找」菜单——那是 `user-select` 和
 * `-webkit-touch-callout` 管的（都挂在拖拽源那一行上），跟拖拽逻辑无关。
 */
import { onBeforeUnmount, ref } from 'vue'
import { dndStart, dndMove, dndDrop, dndReset } from './useCategoryDnd'

/** 位移超过这个像素才算起拖（横竖取最大值，跟库一致）。 */
const TOLERANCE = 8

/**
 * 这一次手势**是不是"拖了一下"**（而不是点了一下）。
 *
 * <p>为什么需要它：拖完之后浏览器**照样会发 click**（指针按下和抬起在同一个元素上，
 * 只是中间移动过）⇒ 拖着一个目录丢到另一行上，那一行会**同时**被"点"一下
 * ⇒ 顺手就跳进了那个目录的卡片视图。用户看到的是"我拖了一下，页面自己跳了"。
 *
 * <p>所以：**起拖了就把这个标记置真**，点击的处理方先问它一句。
 *
 * <p>⚠️ **模块级**：一次只可能有一个拖拽在进行中，而"拖完了别再当成点击"
 * 这条规则**所有拖拽源都要用** ⇒ 每个源各存一份的话，拖目录的标记和
 * 拖知识点的标记会各说各话（点那一行的时候读的是哪一份？）。
 */
export const gestureWasDrag = ref(false)

/**
 * ⚠️ **全局复位：新的一次 pointerdown 就把上次"拖过"的标记作废**。
 *
 * <p>**为什么必须全局**：标记是模块级、而复位原来只发生在**目录行**的
 * `onPointerDown` 里 ⇒ 知识点行（它**不是**拖拽源，不绑 pointerdown）没有复位的地方。
 * 于是：**拖过一次目录（或触屏 tap 位移超阈值被误判成拖拽）之后，
 * 标记一直黏着 true ⇒ 后面点知识点被 {@link consumeDragGesture} 静默吞掉 ⇒
 * "点了没反应，刷新一下才正常"**（2026-11-10 用户报的就是这个）。
 *
 * <p>⇒ 复位放在**模块级、capture 阶段**的 window pointerdown 上：
 * **任何一次新手势都作废上次那个标记**（落在知识点行、落在章卡、落在空白处都行）。
 *
 * <p>⚠️ **capture 阶段**是必须的：bubble 阶段的话，
 * 目录行自己的 `onPointerDown`（冒泡）会**先于**我们这个模块级监听器执行 ——
 * 它把标记置 false，我们再置 false，等于白干一次倒还打乱了顺序。
 * capture 阶段我们**先**跑 ⇒ 目录行的 handler 随后再设值，顺序才对。
 */
if (typeof window !== 'undefined') {
  window.addEventListener('pointerdown', () => { gestureWasDrag.value = false }, true)
}

/**
 * **消费**这个标记：这次点击是不是"拖完浏览器补发的那一下"。
 *
 * <p>⚠️ **一次性消费**（读到 true 顺手清成 false）——
 * 原来只有"读"没有"清"，标记会一直黏着 ⇒ 后面**每一次**点击都被吞。
 * 改成消费语义后：**只有紧跟着那次拖拽的 click 被拦一次**，之后的点击照常。
 *
 * <p>⚠️ 为什么不在 `finish()` 里清：那会儿还不知道浏览器会不会补发 click ——
 * 补发的话这次 click 必须被拦；不补发的话清不清都无所谓（下一个 pointerdown 会清）。
 * **在这里消费**才是准确的那一刀：**拦掉该拦的那一次，不多拦**。
 *
 * @returns {boolean} true = 这次是"拖完补发的 click"，调用方应当**不动作**
 */
export function consumeDragGesture() {
  if (!gestureWasDrag.value) return false
  gestureWasDrag.value = false
  return true
}

/**
 * @param {(e: PointerEvent) => object|null} getSource 认出"按下的是哪个拖拽源"。
 *   传入 pointerdown 事件是为了让**多行共用这一个 handler** 时能认出是哪一行
 *   （目录树那十几行就靠 event.currentTarget 上挂的 data-cat-id 区分）。
 *   返回 `{ catId, label }`：`catId` 是被挪的目录，`label` 是幽灵上显示的一行字。
 * @returns {{ onPointerDown: (e: PointerEvent) => void }}
 */
export function useCategoryDrag(getSource) {
  let originX = 0
  let originY = 0
  let dragging = false
  // pointerdown 那一刻解析出来、一直带到起拖。**不能拖起来了才去问"是哪一行"**——
  // 那时的 event 是 pointermove，currentTarget 已经是 window 了，拿不到行上的 data-cat-id。
  let source = null

  /** 收尾：挪目录 / 取消 + 清掉 body 上的禁选类 */
  function finish(committed) {
    if (dragging) {
      if (committed) dndDrop()
      else dndReset()
    }
    document.body.classList.remove('dnd-active')
    dragging = false
    source = null
  }

  function onMove(e) {
    if (dragging) {
      // 已在拖：**每一帧**都要 preventDefault，这是触摸端唯一的窗口。
      // 漏掉任何一帧，滚动就归 compositor 了，后面再也抢不回来。
      if (e.cancelable) e.preventDefault()
      dndMove(e.clientX, e.clientY)
      return
    }
    // 还没起拖：位移不够就当没动（用户可能只是在点、或者刚开始按）
    const dx = Math.abs(e.clientX - originX)
    const dy = Math.abs(e.clientY - originY)
    if (Math.max(dx, dy) < TOLERANCE) return

    // 一超阈值立刻起拖——不分鼠标 / 触摸，PC 的手感本来也是"挪动才拖"
    if (!source) return
    dragging = true
    gestureWasDrag.value = true
    dndStart(source.catId, originX, originY, source.label)
    document.body.classList.add('dnd-active')
    if (e.cancelable) e.preventDefault()
    dndMove(e.clientX, e.clientY)
  }

  function onUp() {
    detach()
    finish(dragging)
  }

  function onCancel() {
    // 系统手势 / 来电 / 切后台 / 窗口失焦：直接取消，不归类。
    // blur 必须在列——鼠标拖出浏览器窗口外再松开时 pointerup 收不到
    // （事件没落在文档里），浮层会一直挂着，非得再点一下页面才消失。
    detach()
    finish(false)
  }

  function detach() {
    window.removeEventListener('pointermove', onMove)
    window.removeEventListener('pointerup', onUp)
    window.removeEventListener('pointercancel', onCancel)
    window.removeEventListener('blur', onCancel)
  }

  function onPointerDown(e) {
    // 只认主键 / 单指：右键菜单、多指手势（缩放）不参与拖拽
    if (e.pointerType === 'mouse' && e.button !== 0) return
    if (!e.isPrimary) return
    // 新的一次手势 ⇒ 上一次"拖过"的标记作废（不然下一次点击会被当成拖）
    gestureWasDrag.value = false
    source = getSource(e)
    originX = e.clientX
    originY = e.clientY
    window.addEventListener('pointermove', onMove)
    window.addEventListener('pointerup', onUp)
    window.addEventListener('pointercancel', onCancel)
    window.addEventListener('blur', onCancel)
  }

  onBeforeUnmount(() => {
    detach()
    finish(false)
  })

  return { onPointerDown }
}
