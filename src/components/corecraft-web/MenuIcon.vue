<template>
  <!-- ⚠️ **没有图标就整个不画**（`:icon` 为空时不渲染 `el-icon`）——
       原来模板里是 `<el-icon v-if="getIconComponent(...)">`，
       三处各写一遍这个判断。 -->
  <el-icon v-if="icon">
    <component :is="icon" />
  </el-icon>
</template>

<script setup>
/**
 * 菜单项的图标（**只有一份**）。
 *
 * <p>原来 Layout.vue 的模板里，三处各写一遍
 * `<el-icon v-if="getIconComponent(parseMeta(x.meta).icon)">
 *    <component :is="getIconComponent(parseMeta(x.meta).icon)" />
 *  </el-icon>` —— 每个 `v-if` 和 `component` 各调一次 `getIconComponent`，
 * 也就是**每处 JSON.parse 两遍 meta、查两遍图标表**。
 *
 * <p>⇒ 这里只解析一次、只查一次；调用点只管传 `meta`。
 */
import { computed } from 'vue'
import { parseMeta, getIconComponent } from '../../utils/menu'

const props = defineProps({
  /** 菜单项的 meta（**原始字符串**，解析在内部做一次） */
  meta: { type: String, default: '' }
})

/** 图标组件（null = 这个菜单项没有图标） */
const icon = computed(() => getIconComponent(parseMeta(props.meta).icon))
</script>
