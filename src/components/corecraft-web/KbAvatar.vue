<template>
  <!-- 头像的统一渲染出口（2026-11-15，用户菜单引入）：
       `builtin:<id>`（内置矢量款，见 builtinAvatars.js）内联 SVG——主题款颜色走
       var(--cc-primary)，随主题变色；其余（上传 URL / 空）走 el-avatar（空时显示兜底字）。
       两类值只在这一个组件里分流，调用方不用管 avatar 字段存的是哪一种。 -->
  <span class="kb-avatar" :style="{ width: size + 'px', height: size + 'px' }">
    <span v-if="svg" class="kb-avatar__svg" v-html="svg" />
    <el-avatar v-else :size="size" :src="value || undefined">{{ fallback }}</el-avatar>
  </span>
</template>

<script setup>
import { computed } from 'vue'
import { builtinAvatarSvg } from './builtinAvatars'

const props = defineProps({
  /** avatar 字段的值：`builtin:<id>` / 上传 URL / 空 */
  value: { type: String, default: '' },
  /** 空头像时圆里的兜底字（调用方取昵称/用户名首字） */
  fallback: { type: String, default: '' },
  /** 直径 px */
  size: { type: Number, required: true }
})

const svg = computed(() => builtinAvatarSvg(props.value))
</script>

<style scoped>
.kb-avatar {
  display: inline-flex;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
}
.kb-avatar__svg {
  display: block;
  width: 100%;
  height: 100%;
}
/* v-html 进来的节点没有 data-v（§「三处静默失效点」①）——svg 尺寸必须走 :deep */
.kb-avatar :deep(svg) {
  display: block;
  width: 100%;
  height: 100%;
}
</style>
