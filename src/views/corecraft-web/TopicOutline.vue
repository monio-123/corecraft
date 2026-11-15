<template>
  <!-- 大纲被隐藏后，右边缘留一个常驻的小按钮把它叫回来。
       藏起来却叫不回来比不藏更糟，所以这个入口不能也跟着一起藏。
       ⚠️ 要带 items.length：窄屏下 hidden 的初值是 true（见 script），
       而"没有标题就没有大纲"——那时给出一个点了什么也不出现的按钮，
       正好是它要避免的那个毛病（藏起来叫不回来）。
       ⚠️ **受控模式不画这个按钮**（hidden 由外层传进来 = 外层有自己的入口，
       详情页是 meta 行图标排的「大纲」键）——两个入口并存就是"上哪儿关它"的混乱。 -->
  <button
    v-if="selfManaged && isHidden && items.length"
    type="button"
    class="outline__restore"
    aria-label="显示大纲"
    @click="isHidden = false"
  >
    大纲
  </button>

  <!-- 大纲本体。**没有标题就不存在**（v-if="items.length"）——
       没标题的知识点（大多数）右边挂一个空栏是纯噪音。语雀那套就是这样。

       ⚠️ **内部点击一律不冒泡给文档级「点空白收起」**（2026-11-10 用户报
       「点击后这个大纲弹窗就没了」）：工具按钮/折叠箭头/条目都在浮层**里面**，
       不是"空白"。踩过的坑：点「全部折叠」时 allFolded 翻转 ⇒ 图标组件
       `Fold`→`Expand` 换代，**点击目标（svg 的 path）被从 DOM 拔掉**，
       冒泡到 document 时 `$el.contains(e.target)` 已经判 false
       ⇒ 整个浮层被当成"点空白"收走。`.stop` 后浮层内任何点击都走不到那条监听
       （该关的两条路——点条目、隐藏按钮——各自关自己，不依赖它）。 -->
  <aside v-else-if="items.length && !isHidden" class="outline" :class="{ 'is-floating': isFloating }" @click.stop>
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
        <KbTooltip content="隐藏大纲" placement="top">
          <button type="button" class="outline__tool" aria-label="隐藏大纲" @click="isHidden = true">
            <el-icon><Hide /></el-icon>
          </button>
        </KbTooltip>
        <!-- 「全部折叠/全部展开」折的是**大纲自己**：全折之后只剩一级标题。
             折的是大纲、不是正文 —— 正文里那一节折不折由用户自己在正文上点箭头。 -->
        <KbTooltip :content="allFolded ? '全部展开' : '全部折叠'" placement="top">
          <button
            type="button"
            class="outline__tool"
            :aria-label="allFolded ? '全部展开' : '全部折叠'"
            @click="toggleFoldAll"
          >
            <el-icon><component :is="allFolded ? 'Expand' : 'Fold'" /></el-icon>
          </button>
        </KbTooltip>
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
          @click="pick(r.id)"
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
 * 所以折叠状态**住在大纲自己这里**（`foldedIds`），跟正文各管各的：
 * - 正文那侧：鼠标移到标题上出现箭头，点一下折那一节（kbHeadingFold.js 管）
 * - 大纲这侧：点箭头折掉这一支下级条目（只影响这一栏显示什么）
 *
 * **判据**：有下级条目（`descendants` 非空）才给箭头。
 * 下级是谁算的？编辑器（`kbHeadingFold.js` 的 scanHeadings）——
 * 大纲不自己按 level 推，那条规则只有一份。
 */
import { computed, onBeforeUnmount, ref } from 'vue'
import KbTooltip from '../../components/corecraft-web/KbTooltip.vue'

const props = defineProps({
  /** 正文里的标题层级：[{ id, level, text, descendants }] */
  items: { type: Array, default: () => [] },
  /** 当前所在章节（高亮它） */
  activeId: { type: String, default: '' },
  /**
   * 受控显隐（v-model:hidden）：true = 藏起来，外层决定显不显。
   * **不传 = 自管**（编辑器实验台那份，行为与从前完全一致：窄屏默认藏、宽屏常驻、
   * 右边缘留恢复按钮）。
   *
   * ⚠️ `default: undefined` 是必须的：Boolean 类型的 prop 缺省时会被 Vue 铸成 false，
   * 只有显式声明 default 才留得出「没传」这个第三态 —— 否则实验台会被误判成受控模式。
   */
  hidden: { type: Boolean, default: undefined },
  /**
   * 浮层形态：不占正文旁边的版位，浮在正文右上方（点一条自动收起）。
   * 详情页用它（2026-11-09：入口是 meta 行图标排的「大纲」按钮，按需弹出）；
   * 不传 = 宽屏粘性右栏 + 窄屏浮层（原来的双形态）。
   */
  floating: { type: Boolean, default: false }
})
const emit = defineEmits(['navigate', 'update:hidden'])

/**
 * 窄屏断点。⚠️ 断点值只在这一处（原来 CSS 里还有一份同数的 media，
 * 2026-11-09 清理时把窄屏形态收成这里的 `is-floating` 类，两份重复样式合一）。
 */
const NARROW = '(max-width: 1100px)'
const narrowMq = window.matchMedia(NARROW)

/**
 * 整栏隐藏。**两态**：
 * - 自管（hidden prop 没传）：初值随屏宽走 —— 窄屏下大纲是**浮在正文上的一层**
 *   （见样式里那条 media），一进来就展开等于挡住正文 ⇒ 默认收起，用右边缘那个
 *   「大纲」竖条叫出来；宽屏下它是常驻右栏，默认展开（原来的行为）。
 * - 受控（hidden prop 由外层传）：显不显听外层的，初值/恢复按钮都不归这里管。
 *
 * ⚠️ **判据只有一处**（{@link selfManaged}）：原来 `props.hidden === undefined`
 * 这个三态判据在四个地方各写一遍 ⇒ 改判据漏一处 ⇒ "自管模式"和"受控模式"
 * 两套行为会同时存在（症状：受控模式下大纲也自己收起来了）。
 */
const selfHidden = ref(narrowMq.matches)
const selfManaged = computed(() => props.hidden === undefined)
const isHidden = computed({
  get: () => (selfManaged.value ? selfHidden.value : props.hidden),
  set: (v) => {
    if (selfManaged.value) selfHidden.value = v
    else emit('update:hidden', v)
  }
})

/**
 * 跨过断点时回到那一侧的默认形态（只管自管模式；受控模式外层说了算）。
 *
 * 不跟着改的话会留下两条死角：窄屏收起 → 拉宽窗口，浮层样式已经不生效了、
 * 大纲却一直收着，看着像凭空少了一栏；反过来宽屏展开 → 缩窗，
 * 浮层会直接盖在正文上。
 *
 * ⚠️ **只有一份**（原来 `narrow` 和 `selfHidden` 两个 ref 存的是同一个值，
 * 每次断点变化都写两遍）⇒ 一份状态、两个用途：
 * `narrow` 判"当前是不是窄屏"，`selfHidden` 判"自管模式下收没收起"。
 */
const narrow = ref(narrowMq.matches)
/** 浮层形态 = 显式要浮（floating）或 窄屏（浮在正文上，与 floating 同款）。样式只有一份（.is-floating）。 */
const isFloating = computed(() => props.floating || narrow.value)
function onNarrowChange(e) {
  narrow.value = e.matches
  if (!selfManaged.value) return
  selfHidden.value = e.matches
}
narrowMq.addEventListener('change', onNarrowChange)
onBeforeUnmount(() => narrowMq.removeEventListener('change', onNarrowChange))

/** 点一条大纲。浮层形态（盖在正文上）点完就收起 —— 不收就一直挡着跳转的目标。 */
function pick(id) {
  emit('navigate', id)
  if (isFloating.value) isHidden.value = true
}

/**
 * 折起来的大纲条目 id（**只有一份**）。
 *
 * ⚠️ 原来这里是**数组 + Set 两份**（`collapsedIds` 数组、`foldedIds` 它的 Set 视图），
 * `toggleFold` 里还要再造第三个 Set ⇒ 一次折叠要过三道转换，
 * 漏一处就出现"点了箭头没反应"（而界面上看不出哪儿不对）。
 * ⇒ 现在**只有 Set 一份**，要数组的时候现转。
 */
const foldedIds = ref(new Set())

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
  foldedIds.value = next
}

/** 全折 = 所有带下级的都折上（于是只剩一级）；全展 = 一个都不折 */
function toggleFoldAll() {
  foldedIds.value = allFolded.value ? new Set() : new Set(branchIds.value)
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
  /* 上下各留 --sp-12：sticky 的 top 是 16，高度就得从 100vh 里减掉两个 16，
     否则底边正好压在视口下沿、最后一行贴着边。
     ⚠️ 原来写的是 `calc(100vh - var(--sp-24))`，而间距阶梯只到 --sp-16、
     **全项目没有 --sp-24 这个变量** ⇒ calc() 无效 ⇒ 整条 max-height 被浏览器丢弃
     ⇒ 配套的 overflow-y 与 sticky 从来没生效过（大纲跟着页面一起长，
     条目超过一屏时底部那些既看不见也滚不到）。这类 bug 不报错、不警告。 */
  max-height: calc(100vh - 2 * var(--sp-12));
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
  /* ⚠️ 原来是 `var(--cc-surface-1)` —— token 里只有 --cc-surface 和 --cc-surface-2，
     **没有 -1** ⇒ 背景是透明的，滚过的正文从这个竖条底下透出来。同 P0-6 那一类：静默失效。 */
  background: var(--cc-surface);
  color: var(--cc-text-2);
  font-size: var(--text-sm);
  cursor: pointer;
  writing-mode: vertical-rl;
}
.outline__restore:hover {
  color: var(--cc-primary);
  border-color: var(--cc-primary);
}
/* **触屏没有 hover**，"移上去才出现"等于永远不出现。
   照抄 kb-editor.css 里 .kb-img__handle 那套兜底（全站唯一一处做对的范本）。
   .outline__fold 还带 pointer-events:none —— 不一起放开，那个半透明的箭头也点不动。 */
@media (hover: none) {
  .outline__tools,
  .outline__fold {
    opacity: 0.5;
    pointer-events: auto;
  }
}

/* 浮层形态：不占正文旁边的版位，浮在正文右上方。
   触发有两条路，**样式只有这一份**（2026-11-09 收敛）：
   ① `floating` prop（详情页按需弹出的那套）；
   ② 窄屏（≤1100px）——script 里的 `isFloating` 把两种情况都映射到 .is-floating 类。
   ⚠️ 窄屏形态**只管"长什么样"，"显不显示"交回 hidden 一个开关**（初值随屏宽，见 script）：
   原来这里写的是 `display: none`，而恢复按钮只在 hidden=true 时渲染 ⇒ 两头都是死路：
   ① 窄屏用户（1024 半屏 / 750 / 600）永远没有大纲入口；
   ② 宽屏手动隐藏 → 缩窗 → 点恢复：hidden=false 了，aside 渲染出来却被 media 藏掉，
      恢复按钮也因为 hidden=false 不再渲染 ⇒ 再也调不回来。 */
.outline.is-floating {
  position: fixed;
  /* 让开壳的顶栏（--h-top）再留一格 */
  top: calc(var(--h-top) + var(--sp-12));
  right: var(--sp-12);
  width: 240px;
  max-height: 70vh;
  padding: var(--sp-12);
  background: var(--cc-surface);
  border: 1px solid var(--cc-border-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--cc-shadow-2);
  /* 压在正文上，但要在壳顶栏（AppLayout 的 .app-top 是 60）之下 */
  z-index: 30;
}
</style>
