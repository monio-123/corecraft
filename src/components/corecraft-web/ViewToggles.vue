<template>
  <!-- 两个开关：格式工具栏的显隐、整页全屏。
       首页（录入区）和详情页（顶栏）原来各画一份，四个文案一字不差 ——
       收到这里一份，差异只有「提示挂在哪边」和「全屏叫什么」，那两个走 prop。

       ⚠️ §4.7(a)：第一个按钮的 tooltip 整个删掉了。
       原来写「切换到简洁模式（隐藏工具栏）」/「切换到完整模式（显示格式工具栏）」——
       按钮本身就是那个开关，括号里那半句是在**解释按钮**，属于"复述界面上看得见的东西"。
       同一条理由上周刚在 AppLayout 的搜索范围切换上用过一次（用户原话：
       「这个提示词给我去掉」）。⇒ 只留 aria-label（无障碍需要，不显示）。
       ⚠️ 第二个按钮的 tooltip **保留**：`'退出全屏 (Esc)'` 里的 `(Esc)` 是界面上
       **看不见**的信息（键盘用户怎么退），是这条规则的唯一例外。 -->
  <button
    type="button"
    class="kb-icon-btn"
    :aria-label="toolbarLabel"
    @click="$emit('update:showToolbar', !showToolbar)"
  >
    <KbSvgIcon name="gear" />
  </button>
  <KbTooltip :content="fullTip" :placement="placement">
    <button
      type="button"
      class="kb-icon-btn"
      :aria-label="fullLabel"
      @click="$emit('toggle-full')"
    >
      <KbSvgIcon :name="full ? 'close' : 'fullscreen'" />
    </button>
  </KbTooltip>
</template>

<script setup>
import { computed } from 'vue'
import KbSvgIcon from './KbSvgIcon.vue'
import KbTooltip from './KbTooltip.vue'

const props = defineProps({
  /** 格式工具栏现在显不显（v-model:show-toolbar 的那一头，状态在调用方） */
  showToolbar: { type: Boolean, default: false },
  /** 现在是不是全屏（沉浸模式）。状态在 URL 上，由调用方的 useImmersive() 决定 */
  full: { type: Boolean, default: false },
  /** 提示挂在哪边：首页在上方（top），详情页在下方（bottom） */
  placement: { type: String, default: 'top' },
  /** 全屏时这个按钮叫什么：首页「全屏录入」/ 详情页「全屏阅读」 */
  fullLabel: { type: String, default: '全屏' }
})
defineEmits(['update:showToolbar', 'toggle-full'])

/* 四条文案只存在这里一份
   ⚠️ 2026-11-06 删掉 toolbarTip（§4.7(a)：tooltip 整个删了，tooltip 文案也就没人用了）。
   ⚠️ toolbarLabel 留着：它是 aria-label，无障碍需要，且不显示在界面上。 */
const toolbarLabel = computed(() => (props.showToolbar ? '切换到简洁模式' : '切换到完整模式'))
const fullTip = computed(() => (props.full ? '退出全屏 (Esc)' : props.fullLabel))
const fullLabel = computed(() => (props.full ? '退出全屏' : props.fullLabel))
</script>
