<template>
  <div
    class="ct"
    :class="`ct--${mode}`"
    :style="mode === 'nav' ? { top: anchorTop + 'px' } : null"
  >
    <p v-if="mode === 'drop'" class="ct__hint">
      {{ dnd.picking ? '点哪个目录' : '放到哪个目录' }}
    </p>

    <!-- drop 模式下 data-cat-id 是命中检测的标记（store 的 hitCategory 用
         elementFromPoint 找最近的带这个属性的元素）。
         dnd.picking 是触屏的"点选归类"模式——此时点一行就归类，不用把卡片拖进去。
         两种模式共用这一棵树，用户看到的东西完全一样。 -->
    <div
      v-for="r in rows"
      :key="r.c.id"
      class="ct__node"
      :class="{
        'is-drop': dnd.over === r.c.id && dnd.overOk,
        'is-bad': dnd.live && !dnd.overOk && dnd.over === r.c.id
      }"
      :style="{ paddingLeft: 8 + r.depth * 14 + 'px' }"
      :data-cat-id="r.c.id"
      :data-cat-name="r.c.name"
      @click="onPick(r.c)"
      @pointerdown="onPointerDown"
    >
      <span class="ct__name">{{ r.c.name }}</span>
      <span class="ct__count">{{ counts[r.c.id] || 0 }}</span>
      <!-- 「⋯」只在浏览模式给：拖拽时鼠标正按着卡片，弹个菜单点一下"删除目录"就是误删。
           浏览态的每一行（侧边栏一级、弹层任意层）都是同一个 CategoryRowMenu。 -->
      <CategoryRowMenu
        v-if="mode === 'nav'"
        :category="r.c"
        @add="emit('add', $event)"
        @rename="emit('rename', $event)"
      />
    </div>

    <p v-if="!rows.length" class="ct__empty">还没有目录</p>
  </div>
</template>

<script setup>
/**
 * 一棵目录树，三个用法：
 *
 * - `mode="nav"`  侧边栏一级目录 hover 出来的弹层。铺开整棵子树，点行进入该目录，
 *                 行尾 ＋/－ 建和删子目录，**按住行可以拖到别的目录下去**。移开鼠标即消失。
 * - `mode="drop"` 拖起卡片/目录时浮在内容区右侧的落点。行是可投放区，数字给出
 *                 "这个目录下有多少条"，帮用户判断往哪放。
 *
 * 三种用法曾经是两个组件（原 CategoryCascade 递归级联 + CategoryDropTree），
 * 但改成"一次铺开整棵子树的前序列表"之后两者长得一模一样了，所以合成一个。
 * 差异只有几处：贴哪、行的行为是什么、数字给不给——都由 mode 分支，没有别的分叉。
 *
 * 行是 store 摊平好的**前序**列表，不是这里递归出来的：递归写在模板里就只能
 * "本层列完再列子层"，父节点会被同层的兄弟拦腰截断，缩进表达不出父子关系。
 */
import { computed } from 'vue'
import CategoryRowMenu from './CategoryRowMenu.vue'
import { useTopicDrag } from './useTopicDrag'
import {
  dnd, dndPickTo, flattenCategoryTree, getCategoryCountMap
} from './useCorecraftWebStore'

const props = defineProps({
  items: { type: Array, required: true },
  /** 'nav' = 浏览并进入；'drop' = 拖拽落点 */
  mode: { type: String, default: 'nav' },
  /** nav 模式相对视口顶部，贴住触发它的侧边栏那一行 */
  anchorTop: { type: Number, default: 0 }
})

// 2026-10-07 展开改成"点 › 打开、点遮罩关闭"之后，`enter` / `close` 事件就不再有人发、
// 也没人接了，一并去掉。现在这棵树对外面只说三句话：选中一行（pick）、
// 有人要新增子目录（add）、有人要改名（rename）——**开合不再归它管**。
const emit = defineEmits(['pick', 'add', 'rename'])

const rows = computed(() => flattenCategoryTree(props.items))
const counts = computed(() => getCategoryCountMap())

/**
 * nav 模式下每行都是拖拽源：把某个目录拖到另一行下面 = 换父级。
 * drop 模式不行——那时候用户正拖着别的东西，这棵树是落点不是源。
 *
 * **拖的是哪一行，从 event 的 currentTarget 上读**（和命中检测读 data-cat-id 同一套思路）：
 * 18 行共用一个 handler，靠行上挂数据区分是哪一行，不用给每行绑一个闭包。
 */
const { onPointerDown } = useTopicDrag((e) => {
  if (props.mode !== 'nav') return null
  const id = Number(e.currentTarget?.dataset?.catId)
  if (!id) return null
  return { what: { catId: id }, label: e.currentTarget.dataset.catName || '' }
})

function onPick(c) {
  // 触屏的"点选归类"：点哪行就归到哪，归完浮层自动关
  if (dnd.picking) {
    dndPickTo(c.id)
    return
  }
  if (props.mode === 'nav') emit('pick', c)
}

// 落点命中不走 dragover/drop（HTML5 DnD 那套），由 store 的 hitCategory() 用
// elementFromPoint 算，行上只剩 data-cat-id 这个标记（见模板）。
// 本组件 drop 模式下没有事件处理，也不需要。
//
// nav 模式下这棵树是**点开就一直开着**的：AppLayout 用遮罩关，不再靠鼠标移开收走，
// 行上的「⋯」也只管自己的菜单。2026-10-07 把随之空掉的 menuOpen / onMenu 一起删了：
// 那条链子从 RowMenu 一路传下来，终端是一个只写不读的 ref，没有任何消费者。
</script>

<style scoped>
.ct {
  background: #fff;
  border: 1px solid #e3e6eb;
  border-radius: var(--radius-md);
  box-shadow: 0 8px 28px rgba(31, 35, 41, 0.12);
  padding: var(--sp-7);
  max-height: 60vh;
  overflow-y: auto;
}
/* 贴侧边栏右缘。**left 必须 ≥ 侧边栏宽度**（现在 264px，这里 270px 留 6px 缝）。
   原来写 220px 是按更早的侧边栏宽度算的，后来侧边栏变宽了、这里没跟着改，
   结果弹层**盖住侧边栏目录行的右半边**。2026-10-07 实机撞出来：
   "往第一层某个目录中拖动不行，蓝框闪一下就没"——指针落在被盖住的那半边上，
   `elementFromPoint` 命中的是弹层那个**没有 data-cat-id 的空容器**，
   `closest` 找不到 → over = null → 落点高亮灭了，而且松手什么也不发生。
   现在侧边栏行本身就是落点，重叠从"接得上"变成"挡落点"，必须让开。 */
.ct--nav {
  position: fixed;
  /* 贴着侧边栏右边沿：**跟 --w-side 走，不写死**。
     侧边栏宽度已经可调（面板里能拖），这里写死 270 的话，改完宽度弹层就和
     侧边栏脱开一条缝——而 #106 刚把 220→270 修好的那个"弹层盖住侧边栏落点"
     问题会从另一个方向回来。多出来的 6px 是刻意留的缝。 */
  left: calc(var(--w-side) + 6px);
  min-width: 180px;
  z-index: 40;
}
/* 落点浮层：位置由 AppLayout 按指针坐标算好传进来（:style），这里只管外观。
   原来是写死的 `right: 24px; top: 50%`——指针在左半屏拖、落点在右边，视线要来回找。
   跟着指针走之后 "指针右下" 是默认位，靠近右/下边缘时由 AppLayout 翻到另一侧。 */
.ct--drop {
  position: fixed;
  /* 宽高取 token：AppLayout 的 dropTreeStyle 靠 getComputedStyle 读同两个值算
     "贴到边时该往哪边翻"，抄一份常数的话两边一改就不同步。 */
  width: var(--w-drop);
  max-height: var(--h-drop);
  overflow-y: auto;
  z-index: 20;
}
.ct__hint {
  margin: var(--sp-2) 0 var(--sp-6);
  padding: 0 var(--sp-8);
  font-size: var(--text-sm);
  color: #b6bac1;
}
.ct__node {
  display: flex;
  align-items: center;
  gap: var(--sp-7);
  padding: var(--sp-6) var(--sp-10);
  border-radius: var(--radius-sm);
  font-size: var(--text-xs);
  color: #1f2329;
  cursor: pointer;
  /* 行是拖拽源（nav 模式），禁掉文本选择。**跟 .card 上那三条是同一组，缺一条就露馅**：
     少了 user-select，按下拖动时浏览器当场开始选文字，树里几行目录名会被染成蓝块
     （2026-10-07 实机）。少了 -webkit-touch-callout，iOS 长按还会先弹系统「复制/查找」
     菜单，拖拽根本起不来。
     **touch-action 不在这里设**：树本身要能滚，写死等于把滚动禁了。 */
  user-select: none;
  -webkit-user-select: none;
  -webkit-touch-callout: none;
}
.ct--drop .ct__node {
  cursor: copy;
}
.ct__node:hover {
  background: #f4f8ff;
}
/* 命中的落点。压过 :hover，让"正在放这里"最显眼 */
.ct__node.is-drop {
  background: #2b7fff;
  color: #fff;
}
.ct__node.is-drop .ct__count {
  color: rgba(255, 255, 255, 0.8);
}
/* 命中的位置**不能放**（成环 / 目标下重名）。用红而不是置灰：
   置灰在树里混在正常行里几乎看不出来，红的才"这里被划掉了"。
   能不能放由 store 的 checkDrop 算好放在 dnd.overOk，模板只读结果。 */
.ct__node.is-bad {
  background: #fdecea;
  color: #d4302b;
}
.ct__node.is-bad .ct__count {
  color: rgba(212, 48, 43, 0.7);
}
.ct__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ct__count {
  font-size: var(--text-sm);
  color: #b6bac1;
  flex-shrink: 0;
}
.ct__empty {
  margin: 0;
  padding: var(--sp-7) var(--sp-10);
  font-size: var(--text-md);
  color: #b6bac1;
}
</style>
