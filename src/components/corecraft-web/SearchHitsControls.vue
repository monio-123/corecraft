<template>
  <!-- 搜索命中计数 + ↑↓ 跳转（全站一份）。
       顶栏那个搜索框有**三种形态**，其中两种要这套控件：
       - **详情页** = 本文实时检索 ⇒ 跳正文里的上/下一处；
       - **列表页·目录档** = 命中在树里 ⇒ 跳树里的上/下一行。
       两者**外观与口径完全一样**（用户 2026-11-09 拍板「目录树的那个定位框
       也跟这里一样复用上面那个就行」）⇒ 合成一个组件，两处各传各自的计数。

       ⚠️ **计数口径**：定位了印「k/N」，没定位印「N 处」。
       ⚠️ **没命中时整个不显示**（`total > 0` 才渲染）—— 空状态不配废话。

       ⚠️ **样式必须写在本组件里**（2026-11-10 用户报「显示的这么丑」）：
       原来 `.app-search__jump` 写在 **AppLayout 的 scoped 样式**里 ⇒ 编译成
       `.app-search__jump[data-v-xxx]`，而按钮 DOM 在**本组件**里（多根组件
       不继承父组件的 data-v）⇒ **选择器压根没命中** ⇒ 浏览器 UA 默认按钮外观
       （灰底 + `2px outset` 边框 + 直角）⇒ 截图里那两个"丑方块"。
       ⇒ 样式跟着 DOM 走（组件自带 scoped），别指望父组件的 scoped 管到子组件的元素。

       ⚠️ **图标用 KbSvgIcon 而不是 EP 的 el-icon**（同一天）：全站图标族是
       `KbSvgIcon`（1.7px 圆帽线性，2026-11-08 定的），EP 的 `ArrowUp/ArrowDown`
       是另一套粗描边风格 ⇒ 同一排里两种笔触，看着就是"拼的"。 -->
  <span class="app-search__hits-group">
    <span v-if="hits.total > 0" class="app-search__hits">
      <b v-if="hits.index > 0">{{ hits.index }}/{{ hits.total }}</b>
      <b v-else>{{ hits.total }} 处</b>
    </span>
    <button
      type="button"
      class="app-search__jump"
      :disabled="!hits.total"
      aria-label="上一处命中"
      @click="$emit('jump', 'prev')"
    ><KbSvgIcon name="arrow-up" /></button>
    <button
      type="button"
      class="app-search__jump"
      :disabled="!hits.total"
      aria-label="下一处命中"
      @click="$emit('jump', 'next')"
    ><KbSvgIcon name="arrow-down" /></button>
  </span>
</template>

<script setup>
/**
 * 顶栏搜索框的「命中计数 + ↑↓」。
 *
 * 为什么单独一个组件：这两套控件在 `AppLayout.vue` 里**逐行相同**，
 * 只有计数来源和跳转通道不同（详情页 ⇄ store 的 `bodySearchHits`；
 * 目录树 ⇄ `treeSearchHits`）。抄两份 ⇒ 改口径（"N 处"改成别的写法）
 * 容易只改一处 ⇒ 同一个搜索框在两个页面显示的口径不一样。
 */
import KbSvgIcon from './KbSvgIcon.vue'

defineProps({
  /** 命中计数：`{ total, index }`（index = 当前第几处，1 起；0 = 还没定位） */
  hits: { type: Object, required: true }
})

defineEmits(['jump'])
</script>

<style scoped>
/**
 * 命中计数 + ↑↓ 的视觉（**只能写在这里**，见模板那段注释）。
 *
 * ⚠️ **UA 默认样式为什么那么丑**：全站 reset（`base.css`）只归零
 * margin/padding，**不清 border / background** ⇒ `<button>` 保留 UA 的
 * `border: 2px outset` + 灰底 + 直角 ⇒ 不写样式就是那个"灰方块"。
 *
 * 方向：**无壳小图标钮**（suffix 里地方小，不套药丸），
 * hover/disabled 反馈与顶栏其它图标钮（`.app-top__icon-btn`）同一套。
 */
.app-search__hits-group {
  display: inline-flex;
  align-items: center;
  gap: 1px;
}
/* 计数：「3/12」= 在第 3 处、「12 处」= 还没定位。数字等宽，切换不跳宽。 */
.app-search__hits {
  font-size: 12px;
  color: var(--cc-text-3);
  white-space: nowrap;
  line-height: 1;
  margin-right: 3px;
}
.app-search__hits b {
  font-weight: 600;
  color: var(--cc-text-1);
  font-variant-numeric: tabular-nums;
}
.app-search__jump {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  padding: 0;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--cc-text-2);
  cursor: pointer;
  font-size: 15px;
  line-height: 1;
  transition: background 0.15s, color 0.15s;
}
.app-search__jump:hover:not(:disabled) {
  background: var(--cc-primary-soft);
  color: var(--cc-primary);
}
.app-search__jump:disabled {
  opacity: 0.35;
  cursor: default;
}
</style>
