<template>
  <!-- 只读的目录路径。归类/改类在列表页拖拽完成，这里只回答"这条知识在知识库的哪个位置"。
       两处调用方（列表页筛选态、知识点详情页）共用这一个组件，导航动作由 @pick 交给各自决定
       （列表页要 replace 免得筛选把历史堆满，详情页要 push 好让返回键回到这条知识）。

       ⚠️ **没有根项**（2026-11-03 用户拍板）：原来列表页的面包屑第一级是「全部」，
       点了 = 清除目录筛选。用户原话「这个全部可以不要 没有应用场景 直接去掉就行
       第一个就直接是一级目录」⇒ 面包屑从**当前那一级目录**开始画。

       ⇒ 「回到不限目录」这件事**不再由面包屑承担**：
       - 列表页 → 首页「知识点」那张卡（它就是"全部 + 不限时间"）
       - 详情页 → 顶栏「返回」回到来处
       侧边栏的目录树是另一回事（那是导航，不是筛选）。 -->
  <nav v-if="crumbs.length >= minLevel" class="crumbs">
    <template v-for="(c, i) in crumbs" :key="c.id">
      <span v-if="i" class="crumbs__sep">›</span>
      <button
        type="button"
        class="crumbs__link"
        :class="{ 'is-cur': c.id === currentId }"
        @click="emit('pick', c.id)"
      >{{ c.name }}</button>
    </template>
  </nav>
</template>

<script setup>
import { computed } from 'vue'
import { getCategoryPath } from './useCorecraftWebStore'

const props = defineProps({
  // 当前所在的目录 id。null / undefined = 未归类，此时不渲染
  categoryId: { type: [Number, String], default: null },
  // 少于几级就不显示。列表页传 1（一级目录时也得有——那是"我在哪本书里"的线索）；
  // 详情页用默认 1（哪怕只在一级目录下，"这条属于哪里"也是详情页唯一的位置线索）
  minLevel: { type: Number, default: 1 }
})

const emit = defineEmits(['pick'])

// Number(null) 是 0 = ROOT_CATEGORY，会把"未归类"错判成"在根目录下"，所以先挡掉空值
const currentId = computed(() =>
  props.categoryId == null ? null : Number(props.categoryId)
)

const crumbs = computed(() =>
  currentId.value == null ? [] : getCategoryPath(currentId.value)
)
</script>

<style scoped>
.crumbs {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  flex-wrap: wrap;
  min-width: 0;
  font-size: var(--text-md);
}
.crumbs__link {
  border: none;
  background: transparent;
  padding: var(--sp-3) var(--sp-6);
  border-radius: var(--radius-xs);
  color: var(--cc-text-3);
  font-size: var(--text-md);
  cursor: pointer;
  max-width: 168px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: var(--cc-transition);
}
.crumbs__link:hover {
  color: var(--cc-text-1);
  background: var(--cc-surface-2);
}
/* 当前项：只加粗不加底色。底色是侧边栏"已选中"的信号，
   面包屑里再给一次底色会和侧边栏撞成两处高亮 */
.crumbs__link.is-cur {
  color: var(--cc-text-1);
  font-weight: 600;
  cursor: default;
}
.crumbs__link.is-cur:hover {
  color: var(--cc-text-1);
  background: transparent;
}
.crumbs__sep {
  color: var(--cc-text-4);
}
</style>
