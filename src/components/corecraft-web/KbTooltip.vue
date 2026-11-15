<template>
  <!-- 全站统一的 tooltip 壳（2026-11-11）。
       原来各处直接写 <el-tooltip>，参数全是 EP 默认值，于是有两种毛病：
       ① **桌面端赖着不走**：EP 默认 enterable=true，鼠标从按钮滑进提示框，
          tooltip 不算"离开" ⇒ 提示挂在屏幕上不走（用户原话：「显示的时间太久了」）。
       ② **触屏永远不消失**：触屏 tap 会让浏览器补一次 mouseenter，
          但**没有 mouseleave** ⇒ 提示弹出后再没人叫它关 ⇒ 一直挂在那儿
          （用户原话：「特别是平板触屏使用的时候一直不消失 看着很烦」）。

       ⇒ 这里一次性定死一套行为，全站只此一份（别处不要再裸写 el-tooltip）：
          · **鼠标**：hover 触发，**移开立刻消失**（hideAfter=0），提示框不可进入
          · **手指/触控笔**：**不弹**（2026-11-13 用户拍板「触屏的时候这些按钮就不弹提示」，
            取代 2026-11-11 的「弹出来 1.2 秒自动走」）；正在显示的那一下仍会立刻收掉

       ⚠️ **判"是不是手指点的"不能靠媒体查询**（2026-11-11 用户真机打脸）：
          第一版用 `(hover: none)` 判"触屏设备"，在用户的 Windows 11 触屏笔记本上
          **不命中**（那台机器有鼠标 ⇒ hover 能力存在）⇒ 代码以为"这是鼠标设备"
          ⇒ 走 hover 分支 ⇒ 手指点完没有 mouseleave ⇒ **还是永不消失**。
          ⇒ 改用 `utils/touch.js`：**看最近一次交互用的是哪种指针**
          （`PointerEvent.pointerType`）⇒ 混合设备天然正确。
       -->
  <el-tooltip
    ref="tipRef"
    v-bind="$attrs"
    :content="content"
    :placement="placement"
    :trigger="trigger"
    :show-after="showAfter"
    :hide-after="hideAfter"
    :enterable="enterable"
    :disabled="tipDisabled"
    @show="onShow"
    @hide="onHide"
  >
    <slot />
  </el-tooltip>
</template>

<script setup>
/**
 * 全站 tooltip 统一壳。
 *
 * 为什么单独一个组件（而不是给每个 el-tooltip 加几个属性）：
 * "指针是手指还是鼠标"这个判断是**有状态的**（用户随时在鼠标/手指之间切换）；
 * 每个使用点各写一遍 = 同一份判断散在 N 处，改一次改 N 处，漏一处就出现
 * 「有的提示会消失、有的不消失」的鬼故事。收敛成一份才有"全站行为一致"这回事。
 *
 * ⚠️ **自动关闭是自己实现的，没用 EP 的 `auto-close`**：
 *    EP 的 `autoClose` 只在 `onOpen` 里读一次、且必须**在弹出的那一刻**
 *    就已经知道"这是触屏"。但触屏判定是**用户点下去那一刻**才确定的
 *    （EP 的 mouseenter 兼容事件在 pointerdown 之后才补上）⇒
 *    用 prop 传值会赶不上 ⇒ 改成自己监听 `@show` 事件，
 *    那一刻直接读 `utils/touch.js` 的当前指针类型。
 */
import { computed, onBeforeUnmount, ref } from 'vue'
import { isTouchInteraction, onPointerTypeChange } from '../../utils/touch'

defineOptions({ inheritAttrs: false })

const props = defineProps({
  /** 提示文字 */
  content: { type: String, default: '' },
  /** 弹出位置（EP 原样透传） */
  placement: { type: String, default: 'bottom' },
  /** 整个关掉提示（比如内容为空时） */
  disabled: { type: Boolean, default: false },
  /** 覆盖：延迟多久弹（默认 150ms，挡掉鼠标扫过一排时的闪烁） */
  showAfter: { type: Number, default: null },
  /** 覆盖：移开多久关（默认 0 = 立刻） */
  hideAfter: { type: Number, default: null },
  /** 覆盖：提示框能不能被鼠标进入（默认 false = 滑进去也照样关） */
  enterable: { type: Boolean, default: null },
  /** 覆盖：手指交互时自动关闭的毫秒数（默认 1200） */
  autoClose: { type: Number, default: null }
})

const tipRef = ref(null)

/** 触发方式。**单一路径**：鼠标靠 hover，手指靠浏览器的兼容 mouseenter 事件
 *  （tap 后浏览器会补一次 mouseover/mouseenter，弹出来给我们看）——
 *  两种都统一走 hover 分支，关掉的策略由指针类型决定。 */
const trigger = computed(() => 'hover')

/** 延迟弹出。默认 150ms（挡掉鼠标扫过一排时的闪烁）。 */
const showAfter = computed(() => (props.showAfter != null ? props.showAfter : 150))

/** 移开立刻关（0 = 不等）。 */
const hideAfter = computed(() => (props.hideAfter != null ? props.hideAfter : 0))

/** 提示框不可进入 —— 鼠标滑进去也照样关（EP 默认 true 是"赖着不走"的病根）。 */
const enterable = computed(() => (props.enterable != null ? props.enterable : false))

/** 最近一次交互是不是手指/触控笔（2026-11-13）：触屏模式下整个不弹。
 *  首值直接问 touch.js —— 手机上第一次 tap 之前 pointerdown 已经先把状态记成 touch，
 *  随后浏览器补发的 mouseenter 到这里时 disabled 已经立起来了。 */
const touchNow = ref(isTouchInteraction())

/** 整个关掉：调用方显式禁 / 没内容 / 触屏模式。 */
const tipDisabled = computed(() => props.disabled || !props.content || touchNow.value)

/** 自动关闭的定时器 */
let autoTimer = null

function clearAutoTimer() {
  if (autoTimer) {
    clearTimeout(autoTimer)
    autoTimer = null
  }
}

/** 强制收起（EP 的 tooltip 实例暴露了 `hide()`） */
function closeNow() {
  clearAutoTimer()
  if (tipRef.value && typeof tipRef.value.hide === 'function') tipRef.value.hide()
}

/**
 * 提示刚弹出来。
 *
 * ⚠️ 这里是**唯一**能可靠判断"这次是不是手指点的"的地方 ——
 * EP 在 `onOpen` 里读 autoClose 的时刻，触屏判定可能还没生效。
 */
function onShow() {
  clearAutoTimer()
  if (isTouchInteraction()) {
    autoTimer = setTimeout(closeNow, props.autoClose != null ? props.autoClose : 1200)
  }
}

function onHide() {
  clearAutoTimer()
}

/**
 * 指针类型变了（用户换了手指/鼠标）。
 *
 * 切到"手指"时：正在显示的提示**立刻收掉** ——
 * disabled 拦得住"新弹"，拦不住"已经弹着的"（触屏下它没有 mouseleave 可指望）。
 */
const stopPointerWatch = onPointerTypeChange((type) => {
  touchNow.value = type === 'touch'
  if (touchNow.value) closeNow()
})

onBeforeUnmount(() => {
  stopPointerWatch()
  clearAutoTimer()
})
</script>
