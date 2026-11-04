/**
 * 指针拖拽的公共骨架：PC / 平板 / 手机**三端同一套逻辑**。
 * 卡片拖到目录、目录拖到目录，走的都是它——拖法只在一处。
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
 * `-webkit-touch-callout` 管的（都挂在 .card 上），跟拖拽逻辑无关。
 */
import { onBeforeUnmount, ref } from 'vue'
import { dnd, dndStart, dndMove, dndDrop, dndReset } from './useCorecraftWebStore'

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
 * 下一次 pointerdown 时复位（那是新的一次手势）。
 *
 * <p>⚠️ **模块级**：一次只可能有一个拖拽在进行中，而"拖完了别再当成点击"
 * 这条规则**所有拖拽源都要用** ⇒ 每个源各存一份的话，拖目录的标记和
 * 拖知识点的标记会各说各话（点那一行的时候读的是哪一份？）。
 */
export const gestureWasDrag = ref(false)

/**
 * @param {(e: PointerEvent) => object|null} getSource 认出"按下的是哪个拖拽源"。
 *   传入 pointerdown 事件是为了让**多行共用这一个 handler** 时能认出是哪一行
 *   （目录树那十几行就靠 event.currentTarget 上挂的 data-cat-id 区分）。
 *   返回 `{ what, label }`：`what` 是 `{ topicId }` 或 `{ catId }`（二者只传其一，
 *   交给 dndStart 决定这次拖的是知识点还是目录），`label` 是幽灵上显示的一行字。
 * @returns {{ onPointerDown: (e: PointerEvent) => void }}
 */
export function useTopicDrag(getSource) {
  let originX = 0
  let originY = 0
  let dragging = false
  // pointerdown 那一刻解析出来、一直带到起拖。**不能拖起来了才去问"是哪一行"**——
  // 那时的 event 是 pointermove，currentTarget 已经是 window 了，拿不到行上的 data-cat-id。
  let source = null

  /** 收尾：归类 / 移动 / 取消 + 清掉 body 上的禁选类 */
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
    dndStart(source.what, originX, originY, source.label)
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
