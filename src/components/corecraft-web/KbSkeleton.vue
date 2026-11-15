<template>
  <span
    class="kb-skel"
    :class="variantClass"
    :style="style"
    aria-hidden="true"
  ></span>
</template>

<script setup>
import { computed } from 'vue'

/**
 * 骨架占位块（2026-11-13）。
 *
 * ## 为什么用骨架而不是转圈
 * 大块内容（列表、详情文档）的形状是**已知的** —— 卡片有标题/摘要/标签，
 * 文档有标题/meta/正文段落。转圈只说"在加载"，骨架同时说了"加载完会长什么样"，
 * 布局也不会在内容到达时跳一下（⚠️ 加载开始与结束**不改变容器尺寸**是硬要求）。
 *
 * ## 材质跟主题走
 * 底色 / 高光 / 扫光周期 / 圆角都读 --kb-skel-*，主题层覆盖 ⇒ 素雅的骨架是暖纸色、
 * 量子是冷白网格色。**主题里不定义 --kb-skel-* 就会露出本色的冷灰**，
 * 这也是主题适配清单里的一项。
 */
const props = defineProps({
  /** 形状档：chip / title / line / line-short / line-mid / doctitle / meta */
  variant: { type: String, default: 'line' },
  /** 自定义宽度（内联样式，覆盖 variant 的宽度） */
  width: { type: String, default: '' },
  /** 自定义高度 */
  height: { type: String, default: '' }
})

const variantClass = computed(() => `kb-skel--${props.variant}`)

const style = computed(() => {
  const s = {}
  if (props.width) s.width = props.width
  if (props.height) s.height = props.height
  return s
})
</script>
