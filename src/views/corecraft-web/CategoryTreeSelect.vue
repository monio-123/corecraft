<template>
  <el-tree-select
    :model-value="modelValue"
    :data="options"
    :props="{ label: 'label', children: 'children' }"
    :render-after-expand="false"
    :size="size"
    node-key="value"
    :placeholder="placeholder"
    :clearable="clearable"
    check-strictly
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
 *
 * ## `check-strictly`（2026-11-06 补，**修的是根**）
 *
 * EP 的树选择器默认 `check-strictly = false`，它的点节点行为是
 * 「**点父节点 = 展开它，点叶子节点 = 选中它**」
 * （源码 `tree-select/src/tree.ts` 的 `onNodeClick`：
 * `if (!showCheckbox && (checkStrictly || node.isLeaf)) { 选中 } else if (expandOnClickNode) { 展开 }`）。
 *
 * ⇒ **有下级的目录根本选不中** —— 点「工作」「数据库」只会展开，
 * 用户只能一路点进最深的一层才能选到它。可这里选的就是**目录**，
 * 「数据库」本身就是一个合法的归属。
 *
 * 打开 `check-strictly` 之后：点文字 = 选中，点左边那个箭头 = 展开。
 * ⚠️ 这一改**同时修好了 AI 归类 / AI 采纳那两个弹窗**（同一个组件）——
 * 它们之前也只能选叶子目录。
 */
import { computed } from 'vue'
import { newCategoryPath, NEW_CATEGORY } from './aiAdopt'
import { toCategoryTree } from '../../utils/categoryTree'

const props = defineProps({
  modelValue: { type: [Number, String], default: null },
  categories: { type: Array, default: () => [] },
  /** AI 建议的新目录 `{ name, parentId }`；为 null 时不显示「新建」项 */
  newCategory: { type: Object, default: null },
  /**
   * **树最上面额外挂的一个节点** `{ value, label }`。
   *
   * <p>为什么是「额外」而不是某一种目录：「待归类」不是目录，是个**桶**（没有 id），
   * 混进目录树里就成了一个假目录。但用户需要一个能选它的地方（把归错了的挪回去），
   * 而这套选择器的节点形状就是 `{ value, label }` ⇒ 给它一个固定的位置最省事。
   *
   * <p>⚠️ **只有一份树**：这个组件是目录选择的唯一入口（AI 归类、正文里的目录链接、
   * 卡片手动归类都走它）⇒ 额外节点的能力放在这儿，不在各调用方各拼一次。
   */
  extraTop: { type: Object, default: null },
  /* ⚠️ 2026-11-06 改（§4.7(c)）：默认值原来写「不改目录」。
     那是一句**行为说明**不是字段名 —— 而这个 placeholder 在调用方全都显式传了值
     （AiAdoptDialog 传「选择目录」、AiOrganizeDialog 传「不改目录」…），
     所以这个默认值在两个调用方身上都渲染不出来 ⇒ 是死文案。
     ⇒ 默认值改成空串：EP 的 select 对 undefined 会 `??` 兜底出语言包的「请选择」，
     空串才是真的什么都不显示（MilkdownEditorBody 的 placeholder 已是这个处理，
     见那边 :102 的注释）。 */
  placeholder: { type: String, default: '' },
  /**
   * 能不能清空回「不选」。**默认关**——弹窗里的归属选择是"选一个目录"，
   * 清空没有语义；考试记录页的**筛选条**要它（清空 = 不限目录）。
   */
  clearable: { type: Boolean, default: false },
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

const options = computed(() => {
  const top = props.extraTop ? [props.extraTop] : []
  return props.newCategory
    ? [...top, { value: NEW_CATEGORY, label: newCategoryLabel(props.newCategory) }, ...baseTree.value]
    : [...top, ...baseTree.value]
})
</script>
