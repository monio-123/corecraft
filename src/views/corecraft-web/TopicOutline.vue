<template>
  <!-- 大纲被隐藏后，右边缘留一个常驻的小按钮把它叫回来。
       藏起来却叫不回来比不藏更糟，所以这个入口不能也跟着一起藏。 -->
  <button
    v-if="hidden"
    type="button"
    class="outline__restore"
    aria-label="显示大纲"
    @click="hidden = false"
  >
    大纲
  </button>

  <!-- 大纲本体。**没有标题就不存在**（v-if="items.length"）——
       没标题的知识点（大多数）右边挂一个空栏是纯噪音。语雀那套就是这样。 -->
  <aside v-else-if="items.length" class="outline">
    <div class="outline__head">
      <div class="outline__title">大纲</div>
      <!-- 工具条：**鼠标移到大纲上才出现**（语雀那套）。这两个按钮不常用，
           常驻反而抢正文的风头；用 opacity 不是 display —— display 会让
           标题和按钮在出现/消失时抖一下。

           ⚠️ **tooltip 放上方（top），不放左边（left）**：
           左边紧挨着的就是另一个工具按钮和「大纲」两个字，
           提示框往左一弹**正好压住它们**（用户反馈的遮挡问题）。
           语雀那套也是浮在按钮上方。 -->
      <div class="outline__tools">
        <el-tooltip content="隐藏大纲" placement="top">
          <button type="button" class="outline__tool" aria-label="隐藏大纲" @click="hidden = true">
            <el-icon><Hide /></el-icon>
          </button>
        </el-tooltip>
        <!-- 「全部折叠/全部展开」折的是**大纲自己**：全折之后只剩一级标题。
             折的是大纲、不是正文 —— 正文里那一节折不折由用户自己在正文上点箭头。 -->
        <el-tooltip :content="allFolded ? '全部展开' : '全部折叠'" placement="top">
          <button
            type="button"
            class="outline__tool"
            :aria-label="allFolded ? '全部展开' : '全部折叠'"
            @click="toggleFoldAll"
          >
            <el-icon><component :is="allFolded ? 'Expand' : 'Fold'" /></el-icon>
          </button>
        </el-tooltip>
      </div>
    </div>
    <nav class="outline__nav">
      <!-- 一行 = [折叠箭头][标题]。
           箭头**紧挨标题文字**（中间留 4px），随层级一起缩进 —— 语雀就是这样
           （它的箭头也是紧贴标题的，不在左边单开一列）。
           没有下级的标题留一个**同宽的空槽**，这样同一层的标题文字仍然齐平。 -->
      <div
        v-for="r in visibleItems"
        :key="r.id"
        class="outline__row"
        :style="{ paddingLeft: `${(r.level - 1) * 12}px` }"
      >
        <button
          v-if="hasChildren(r)"
          type="button"
          class="outline__fold"
          :class="{ 'is-closed': foldedIds.has(r.id) }"
          :aria-label="foldedIds.has(r.id) ? `展开 ${r.text}` : `折叠 ${r.text}`"
          @click="toggleFold(r.id)"
        >{{ foldedIds.has(r.id) ? '▸' : '▾' }}</button>
        <span v-else class="outline__fold outline__fold--empty" />
        <button
          class="outline__item"
          :class="{ 'is-active': r.id === activeId }"
          @click="emit('navigate', r.id)"
        >
          {{ r.text || '（无标题）' }}
        </button>
      </div>
    </nav>
  </aside>
</template>

<script setup>
/**
 * 右侧大纲栏。
 *
 * **为什么抽成组件**：详情页和编辑器实验台都要它 —— 之前实验台里自己写了一份
 * 列表（同样按 level 缩进、同样点一条跳过去），那是**两份真相**：改了一份另一份不动，
 * 而且实验台那份没有工具条、没有高亮，测不到真实效果。合成一处之后，
 * 实验台就是这块 UI 的自测面。
 *
 * **它只管"看"**：大纲数据由编辑器报上来（页面自己不解析 markdown），
 * 点一条跳过去由外层接（`@navigate`）。
 *
 * ## 折叠：折的是**大纲自己**，不动正文（2026-10-21 用户定）
 *
 * 早先这里折的是正文那一节（跟正文共用一份折叠状态），用户明确否掉了：
 * **大纲里的收缩/展开只控制大纲的目录**。理由也说得通 ——
 * 在大纲里点一下就把正文的段落藏起来，是"看目录"的动作顺手改了正文，
 * 误触成本太高。
 *
 * 所以折叠状态**住在大纲自己这里**（`collapsedIds`），跟正文各管各的：
 * - 正文那侧：鼠标移到标题上出现箭头，点一下折那一节（kbHeadingFold.js 管）
 * - 大纲这侧：点箭头折掉这一支下级条目（只影响这一栏显示什么）
 *
 * **判据**：有下级条目（`descendants` 非空）才给箭头。
 * 下级是谁算的？编辑器（`kbHeadingFold.js` 的 scanHeadings）——
 * 大纲不自己按 level 推，那条规则只有一份。
 */
import { computed, ref } from 'vue'

const props = defineProps({
  /** 正文里的标题层级：[{ id, level, text, descendants }] */
  items: { type: Array, default: () => [] },
  /** 当前所在章节（高亮它） */
  activeId: { type: String, default: '' }
})
const emit = defineEmits(['navigate'])

// 整栏隐藏
const hidden = ref(false)

/** 折起来的大纲条目 id（**只管大纲自己**，跟正文的折叠状态无关） */
const collapsedIds = ref([])

/** 折起来的标题 id */
const foldedIds = computed(() => new Set(collapsedIds.value))

/** 这一支还有没有下级条目（有才给箭头） */
function hasChildren(r) {
  return r.descendants.length > 0
}

/**
 * 折起来的标题，它罩着的那一支都不显示了。
 *
 * **用上报下来的 `descendants`，不在这里按 level 重新推**：
 * "什么算下级"这条规则由编辑器算（`kbHeadingFold.js` 的 scanHeadings），
 * 两边各推一遍的话，改一边另一边不动。
 */
const hiddenIds = computed(() => {
  const s = new Set()
  for (const r of props.items) {
    if (!foldedIds.value.has(r.id)) continue
    r.descendants.forEach((id) => s.add(id))
  }
  return s
})

const visibleItems = computed(() => props.items.filter((r) => !hiddenIds.value.has(r.id)))

/** 所有带下级的条目（"全部折叠"用） */
const branchIds = computed(() => props.items.filter(hasChildren).map((r) => r.id))

/** 全折了没有（决定那个按钮显示"全部折叠"还是"全部展开"） */
const allFolded = computed(() =>
  branchIds.value.length > 0 && branchIds.value.every((id) => foldedIds.value.has(id))
)

/** 折/展某一项（只影响大纲这一栏） */
function toggleFold(id) {
  const next = new Set(foldedIds.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  collapsedIds.value = [...next]
}

/** 全折 = 所有带下级的都折上（于是只剩一级）；全展 = 一个都不折 */
function toggleFoldAll() {
  collapsedIds.value = allFolded.value ? [] : [...branchIds.value]
}
</script>

<style scoped>
.outline {
  /* 宽度是结构量，不进 --sp-* 阶梯 */
  width: 200px;
  flex: none;
  /* 跟着滚动停在视口顶部。align-items: flex-start 让它按内容高度收缩，
     不然 flex 子项默认 stretch，会拉成一整屏高，sticky 也就没意义了。 */
  position: sticky;
  top: var(--sp-12);
  max-height: calc(100vh - var(--sp-24));
  overflow-y: auto;
  font-size: var(--text-sm);
}
.outline__head {
  display: flex;
  align-items: center;
  /* 图标**紧挨着「大纲」两个字**（语雀就是这样），不是甩到面板最右边 ——
     甩到最右边的话，两个按钮和标题之间隔着一大片空白，像是两拨东西。 */
  gap: var(--sp-6);
  margin-bottom: var(--sp-6);
}
.outline__title {
  color: var(--cc-text-2);
  font-weight: 600;
}
.outline__tools {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  opacity: 0;
  transition: opacity 0.12s ease;
}
/* 鼠标移到大纲上才露出那两个按钮 */
.outline:hover .outline__tools,
.outline:focus-within .outline__tools {
  opacity: 1;
}
.outline__tool {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  padding: 0;
  border: 0;
  border-radius: var(--radius-xs);
  background: transparent;
  color: var(--cc-text-3);
  cursor: pointer;
}
.outline__tool:hover {
  background: var(--cc-surface-2);
  color: var(--cc-text-1);
}
/* 一行 = 箭头 + 标题。箭头槽是固定宽的，
   **没有下级的标题也留一个空槽**，同一层的标题文字才齐平。 */
.outline__row {
  display: flex;
  align-items: center;
  gap: var(--sp-4);
}
.outline__fold {
  /* **和正文那个箭头同一套构造**：1em 的槽 + 一个 '▾' 字形。
     字形只占 em 的 0.357em（'▾' 本身就小），所以这里把字号调大一号 ——
     否则箭头小到看不见（之前 11px 的字形只有 3.9px 宽，肉眼基本找不到）。
     正文那边是 1em = 标题字号（跟着标题缩放），这边是固定值（大纲不跟标题缩放）。 */
  flex: none;
  width: 1em;
  height: 1em;
  font-size: 18px;
  line-height: 1;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--cc-text-2);
  text-align: center;
  cursor: pointer;
  border-radius: var(--radius-xs);
  /* **默认不显示，鼠标移到这一行才出现**（跟正文那个箭头一个规矩）。
     用 opacity 不用 display —— display 会让标题文字左右跳一下；
     隐藏时 pointer-events: none —— 不然那个看不见的箭头还能被点到。 */
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.12s ease;
}
.outline__row:hover .outline__fold,
.outline__fold:hover {
  opacity: 1;
  pointer-events: auto;
}
/* 折起来的那一节箭头常驻：藏起来就看不出这节是折的，也找不到把它展开的地方 */
.outline__fold.is-closed {
  opacity: 1;
  pointer-events: auto;
}
.outline__fold:hover {
  color: var(--cc-text-1);
  background: var(--cc-surface-2);
}
/* 空槽：只是占位，永远不显示（也别跟着 hover 冒出来） */
.outline__fold--empty {
  cursor: default;
}
.outline__fold--empty:hover {
  background: transparent;
}
.outline__item {
  flex: 1;
  min-width: 0;
  padding: var(--sp-3) var(--sp-6);
  border: 0;
  background: transparent;
  text-align: left;
  color: var(--cc-text-2);
  border-radius: var(--radius-xs);
  cursor: pointer;
  line-height: 1.5;
  /* 长标题省略，别把面板撑宽 */
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.outline__item:hover {
  background: var(--cc-surface-2);
  color: var(--cc-text-1);
}
.outline__item.is-active {
  color: var(--cc-primary);
  font-weight: 600;
}
/* 大纲被隐藏后，右边缘留一个常驻的小按钮把它叫回来 */
.outline__restore {
  position: sticky;
  top: var(--sp-12);
  flex: none;
  align-self: flex-start;
  padding: var(--sp-3) var(--sp-6);
  border: 1px solid var(--cc-border);
  border-radius: var(--radius-xs);
  background: var(--cc-surface-1);
  color: var(--cc-text-2);
  font-size: var(--text-sm);
  cursor: pointer;
  writing-mode: vertical-rl;
}
.outline__restore:hover {
  color: var(--cc-primary);
  border-color: var(--cc-primary);
}
/* 窄屏藏掉大纲：正文宽度优先。留着这一栏正文会被挤成一条窄缝，
   那比没有大纲更糟。 */
@media (max-width: 1100px) {
  .outline {
    display: none;
  }
}
</style>
