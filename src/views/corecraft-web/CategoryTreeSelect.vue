<template>
  <el-tree-select
    :model-value="modelValue"
    :data="options"
    :props="{ label: 'label', children: 'children' }"
    :render-after-expand="false"
    :size="size"
    node-key="value"
    :placeholder="placeholder"
    @update:model-value="emit('update:modelValue', $event)"
  />
</template>

<script setup>
/**
 * 目录选择器：**树形**下拉 + 可选的「新建」项。
 *
 * ## 为什么是树形
 *
 * 目录是真的分层的（`数据库/持久化/Mybatis`），而**不同父目录下允许同名**——
 * 唯一键是 (user_id, parent_id, name)。所以平铺的名字列表分不清"这个 Mybatis 是哪一个"，
 * 树形结构用缩进把层级说清楚，label 就不用再把路径重复一遍。
 *
 * ## 「新建」不是树上的节点，但它得能选
 *
 * AI 提议一个新目录时（比如 `数据库/MySQL`），用户在树上找不到它。做法是把它作为
 * **一个额外的顶层节点**放在最上面，label 写成 `+ 新建 数据库 / MySQL`——
 * 带 `+` 前缀、和目录节点视觉上分得开，位置也符合直觉（"多一个"）。
 * 选中它等于"确认建这个"，不选就是不改。
 *
 * ## 目录树只算一次
 *
 * 批量归类一行一个选择器，18 行就有 18 个。树的结构对所有行是一样的
 * （只有那个"新建"节点每行不同），所以 `baseTree` 提到 computed 里共用，
 * 不要每行各建一份。
 *
 * 建树本身在 `utils/categoryTree.js`——正文里「设为目录链接」的选择器
 * 要的是同一棵树，两边各写一遍的话改排序就得改两处，而那不会报错。
 *
 * `size` 透传下去：批量弹框里一行一个，用默认尺寸会长得很高。
 */
import { computed } from 'vue'
import { newCategoryPath, NEW_CATEGORY } from './aiAdopt'
import { toCategoryTree } from '../../utils/categoryTree'

const props = defineProps({
  modelValue: { type: [Number, String], default: null },
  categories: { type: Array, default: () => [] },
  /** AI 建议的新目录 `{ name, parentId }`；为 null 时不显示「新建」项 */
  newCategory: { type: Object, default: null },
  placeholder: { type: String, default: '不改目录' },
  size: { type: String, default: 'default' }
})
const emit = defineEmits(['update:modelValue'])

/** 已有目录构成的树（规则见 utils/categoryTree） */
const baseTree = computed(() => toCategoryTree(props.categories))

/** 「+ 新建 数据库 / MySQL」——路径拼接在 aiAdopt 的 newCategoryPath，这里只加前缀 */
function newCategoryLabel(nc) {
  const path = newCategoryPath(nc, props.categories)
  return path ? `+ 新建 ${path}` : ''
}

const options = computed(() =>
  props.newCategory
    ? [{ value: NEW_CATEGORY, label: newCategoryLabel(props.newCategory) }, ...baseTree.value]
    : baseTree.value
)
</script>
