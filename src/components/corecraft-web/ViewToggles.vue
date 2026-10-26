<template>
  <!-- 两个开关：格式工具栏的显隐、整页全屏。
       首页（录入区）和详情页（顶栏）原来各画一份，四个文案一字不差 ——
       收到这里一份，差异只有「提示挂在哪边」和「全屏叫什么」，那两个走 prop。 -->
  <el-tooltip :content="toolbarTip" :placement="placement">
    <button
      type="button"
      class="kb-icon-btn"
      :aria-label="toolbarLabel"
      @click="$emit('update:showToolbar', !showToolbar)"
    >
      <el-icon><Tools /></el-icon>
    </button>
  </el-tooltip>
  <el-tooltip :content="fullTip" :placement="placement">
    <button
      type="button"
      class="kb-icon-btn"
      :aria-label="fullLabel"
      @click="$emit('toggle-full')"
    >
      <el-icon><component :is="full ? Close : FullScreen" /></el-icon>
    </button>
  </el-tooltip>
</template>

<script setup>
import { computed } from 'vue'
import { Close, FullScreen, Tools } from '@element-plus/icons-vue'

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

/* 四条文案只存在这里一份 */
const toolbarTip = computed(() => (props.showToolbar
  ? '切换到简洁模式（隐藏工具栏）'
  : '切换到完整模式（显示格式工具栏）'))
const toolbarLabel = computed(() => (props.showToolbar ? '切换到简洁模式' : '切换到完整模式'))
const fullTip = computed(() => (props.full ? '退出全屏 (Esc)' : props.fullLabel))
const fullLabel = computed(() => (props.full ? '退出全屏' : props.fullLabel))
</script>
