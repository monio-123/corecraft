<template>
  <div
    class="ct"
    :class="`ct--${mode}`"
    :style="mode === 'nav' ? navStyle : null"
  >
    <p v-if="mode === 'drop'" class="ct__hint">
      {{ dnd.picking ? '点哪个目录' : '放到哪个目录' }}
    </p>

    <!-- 收起/展开：**只给浏览模式**。
         拖拽落点那棵树不能折叠 —— 那一棵的每一行都是落点，收起来就少一块能放的地方。
         浏览模式这棵树一次铺开整棵子树（目录 + 知识点，上百行），默认全展开；
         顶上给一个「收起全部 / 展开全部」，每个有下级的目录前再给一个箭头，单独开合它自己那棵。

         **`branchy.size` 才给这个条**：树是平的（没有子级）时，"收起全部"什么也收不掉 ——
         按钮在、点了没反应，比没有按钮更让人以为坏了。真出现这种树（缓存旧数据也会造成
         这副样子）就不给这个条。 -->
    <div v-if="mode === 'nav' && branchy.size" class="ct__bar">
      <el-tooltip :content="toggleAllLabel" placement="bottom">
        <button class="ct__bar-btn" :aria-label="toggleAllLabel" @click="toggleAll">
          <el-icon><component :is="collapsed.size ? Expand : Fold" /></el-icon>
        </button>
      </el-tooltip>
    </div>

    <!-- drop 模式下 data-cat-id 是命中检测的标记（store 的 hitCategory 用
         elementFromPoint 找最近的带这个属性的元素）。
         dnd.picking 是触屏的"点选归类"模式——此时点一行就归类，不用把卡片拖进去。
         两种模式共用这一棵树，用户看到的东西完全一样。

         **知识点行**（浏览模式才有）：知识点当叶子摊在树上，
         挂在哪一级就在哪一级出现 —— 「总览」于是和自己的子目录并排，一眼看得见。
         它**不带 data-cat-id**：知识点不是落点，也不可拖（只有目录能换父级）。 -->
    <template v-for="r in rows" :key="r.kind === 'cat' ? 'c' + r.c.id : 't' + r.t.id">
      <div
        v-if="r.kind === 'cat'"
        class="ct__node"
        :class="{
          'is-drop': dnd.over === r.c.id && dnd.overOk,
          'is-bad': dnd.live && !dnd.overOk && dnd.over === r.c.id
        }"
        :style="rowStyle(r)"
        :data-cat-id="r.c.id"
        :data-cat-name="r.c.name"
        @click="onPick(r.c)"
        @pointerdown="onPointerDown"
      >
        <!-- 展开/收起箭头：**绝对定位**画在缩进那一格里（不占文档流）。
             没有下级的目录不给箭头（弹出来是空的），但**那一格照样留出来** ——
             留出来各行文字才对得齐（见 rowStyle 的注释）。
             `.stop` 挡住冒泡：不拦住的话点箭头会顺带进这个目录（展开和进入是两件事）。 -->
        <button
          v-if="mode === 'nav' && branchy.has(r.c.id)"
          type="button"
          class="ct__toggle"
          :class="{ 'is-open': !isCollapsed(r.c.id) }"
          :style="caretStyle(r)"
          :aria-label="(isCollapsed(r.c.id) ? '展开 ' : '收起 ') + r.c.name"
          :aria-expanded="!isCollapsed(r.c.id)"
          @click.stop.prevent="toggleOne(r.c.id)"
        ><el-icon><ArrowRight /></el-icon></button>
        <span class="ct__name">{{ r.c.name }}</span>
        <span class="ct__count">{{ counts[r.c.id] || 0 }}</span>
        <!-- 「⋯」只在浏览模式给：拖拽时鼠标正按着卡片，弹个菜单点一下"删除目录"就是误删。
             浏览态的每一行（侧边栏一级、弹层任意层）都是同一个 CategoryRowMenu。 -->
        <CategoryRowMenu
          v-if="mode === 'nav'"
          :category="r.c"
          @add="emit('add', $event)"
          @rename="emit('rename', $event)"
          @points="emit('points', $event)"
        />
      </div>

      <div
        v-else
        class="ct__node ct__node--topic"
        :style="rowStyle(r)"
        @click="onPickTopic(r.t)"
      >
        <span class="ct__name">{{ r.t.title }}</span>
      </div>
    </template>

    <p v-if="!rows.length" class="ct__empty">没有目录</p>
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
import { computed, ref } from 'vue'
import { ArrowRight, Expand, Fold } from '@element-plus/icons-vue'
import CategoryRowMenu from './CategoryRowMenu.vue'
import { useTopicDrag } from './useTopicDrag'
import { branchyIds, visibleRows } from '../../utils/categoryTree'
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
// 也没人接了，一并去掉。现在这棵树对外面只说四句话：选中一行（pick）、
// 有人要新增子目录（add）、有人要改名（rename）、有人要在这个目录下生成知识点（points）——
// **开合不再归它管**。
const emit = defineEmits(['pick', 'pickTopic', 'add', 'rename', 'points'])

// **知识点只进浏览模式的树**：拖拽落点那棵树的每一行都得是可投放的目录，
// 多出来的叶子行只会挡住命中检测（指针落在它上面 → closest 找不到落点）。
const allRows = computed(() => flattenCategoryTree(props.items, { withTopics: props.mode === 'nav' }))

/**
 * 收起了的目录 id 集合。**默认空 = 全部展开**（这个面板一直以来的样子）。
 *
 * <p>只记「哪些目录被收起来了」，不记「展开到第几层」：这棵树是**平铺的前序列表**
 * （父节点后面紧跟自己的子树）⇒ 收起一个目录 = 把它那一整段子树从列表里剔掉，
 * 不用知道子树长什么样。
 */
const collapsed = ref(new Set())

/** 有下级的目录 id —— **能画出展开箭头的那批**。 */
const branchy = computed(() => branchyIds(allRows.value))

/** 画出来的行：**被收起的目录，它自己那棵子树整段不画**（它自己那一行留着）。 */
const rows = computed(() => visibleRows(allRows.value, collapsed.value))

const counts = computed(() => getCategoryCountMap())

/** 顶部那个按钮：全收起 / 全展开。 */
const toggleAllLabel = computed(() => (collapsed.value.size ? '展开全部' : '收起全部'))

function toggleAll() {
  collapsed.value = collapsed.value.size ? new Set() : new Set(branchy.value)
}

/** 单个目录的展开 / 收起。 */
function toggleOne(id) {
  const next = new Set(collapsed.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  collapsed.value = next
}

const isCollapsed = (id) => collapsed.value.has(id)

// 缩进：每层 14px，最外层留 8px。
const INDENT = 14
const BASE = 8

const indentOf = (r) => BASE + r.depth * INDENT

/**
 * 行的左内边距 = 缩进 + **箭头槽**。
 *
 * <p>**箭头槽为什么每一行都要留**（包括没有下级的目录、知识点叶子）：箭头是
 * **绝对定位**画在缩进那一格里的，不占文档流 ⇒ 不留槽的话，有箭头的行文字往右挪 14px、
 * 没有的原地不动 ⇒ **同一层的目录和知识点对不齐**。统一留一格，各行文字才对得齐。
 *
 * <p>槽宽 = 一层缩进 ⇒ 子节点的箭头正好落在父节点文字的起点上（树的常规画法）。
 *
 * <p>**拖拽落点模式不留**：那一棵树没有箭头（不能折叠），留个空槽只会白缩进 14px。
 */
function rowStyle(r) {
  return { paddingLeft: indentOf(r) + (props.mode === 'nav' ? INDENT : 0) + 'px' }
}

/** 箭头落在缩进那一格的起点上。 */
function caretStyle(r) {
  return { left: indentOf(r) + 'px' }
}

/**
 * nav 模式（侧边栏那一行点「›」出来的目录树弹层）的定位与高度。
 *
 * ⚠️ **原来只给了 `top = 触发行那一行的位置`，高度写死 `max-height: 60vh`** ——
 * 触发行在侧边栏靠下时（比如「初级会计实务」排在第 7 位），**弹层的下半截会跑到视口外**，
 * 而面板自己的滚动条也跟着跑到视口外 ⇒ 用户在里面滚到底了，**最后几行仍然看不见**
 * （症状："明明有 8.x，滑到 7 就没了" —— 数据在库里，是被视口切掉了）。
 *
 * 现在：**高度按「从触发行到视口底」的剩余空间算**；剩余放不下（< 240px）
 * 就**向上展开**（像 select 那样，底边对齐到触发行的上沿）⇒ 面板始终整个在视口里。
 *
 * 为什么不能用纯 CSS 写：剩余空间要拿 `100vh - top` 算，而 `top` 是运行时才知道的
 * （触发行在哪儿随侧边栏滚动而变）⇒ 只能由 JS 算完写成内联样式。
 */
const navStyle = computed(() => {
  if (props.mode !== 'nav') return null
  const vh = window.innerHeight || 800
  const GAP = 16 // 面板与视口边的缝
  const MIN = 240 // 剩余空间不足这么多就翻到上面去
  const top = props.anchorTop
  const below = vh - top - GAP
  if (below >= MIN) {
    return { top: `${top}px`, maxHeight: `${Math.min(vh * 0.6, below)}px` }
  }
  // 向上展开：面板底边对齐到触发行的上沿（留一条缝）
  return {
    top: 'auto',
    bottom: `${vh - top + GAP}px`,
    maxHeight: `${Math.min(vh * 0.6, Math.max(top - GAP, MIN))}px`
  }
})

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

/**
 * 点知识点叶子 ⇒ 进它的详情页。
 *
 * <p>**触屏点选归类模式下不跳**：那时候用户正在选"归到哪个目录"，
 * 点到一条知识点上跳走 = 归类流程被打断（而且知识点本来也不是落点）。
 */
function onPickTopic(t) {
  if (dnd.picking) return
  emit('pickTopic', t)
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
  background: var(--cc-surface);
  border: 1px solid #e3e6eb;
  border-radius: var(--radius-md);
  box-shadow: 0 8px 28px rgba(31, 35, 41, 0.12);
  padding: var(--sp-7);
  overflow-y: auto;
}
/* 贴侧边栏右缘。**left 必须 ≥ 侧边栏宽度**——弹层盖住侧边栏目录行的右半边时，
   指针落在被盖住的那半边上，`elementFromPoint` 命中的是弹层那个**没有 data-cat-id
   的空容器**，`closest` 找不到 → over = null → 落点高亮灭了，而且松手什么也不发生
   （2026-10-07 实机撞出来："往第一层某个目录中拖动不行，蓝框闪一下就没"）。
   现在侧边栏行本身就是落点，重叠从"接得上"变成"挡落点"，必须让开。 */
.ct--nav {
  position: fixed;
  /* 贴着侧边栏右边沿：**跟 --w-side 走，不写死**。
     侧边栏宽度已经可调（面板里能拖），写死一个数的话，改完宽度弹层就和
     侧边栏脱开一条缝——那个"弹层盖住侧边栏落点"的问题会从另一个方向回来。
     多出来的 6px 是刻意留的缝。 */
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
  color: var(--cc-text-4);
}
/* 顶上的收起/展开条。只有浏览模式有（见模板里的说明）。 */
.ct__bar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: var(--sp-6);
}
.ct__bar-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  padding: 0;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--cc-text-4);
  cursor: pointer;
}
.ct__bar-btn:hover {
  background: #f4f8ff;
  color: var(--cc-text-1);
}
/* 行首的展开/收起箭头。**绝对定位**在缩进那一格里（不占文档流，
   那一格由 rowStyle 统一留出来 ⇒ 各行文字才对得齐）。 */
.ct__toggle {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  height: 14px;
  padding: 0;
  border: none;
  background: transparent;
  color: var(--cc-text-4);
  cursor: pointer;
  transition: transform 0.12s ease;
}
.ct__node:hover .ct__toggle {
  color: var(--cc-text-2);
}
/* 展开时箭头转 90°（指向下）= 这一棵是开着的。 */
.ct__toggle.is-open {
  transform: translateY(-50%) rotate(90deg);
}
.ct__node {
  display: flex;
  align-items: center;
  gap: var(--sp-7);
  padding: var(--sp-6) var(--sp-10);
  border-radius: var(--radius-sm);
  font-size: var(--text-xs);
  color: var(--cc-text-1);
  cursor: pointer;
  /* 展开箭头是**绝对定位**画在缩进那一格里的 ⇒ 行要是定位参照。 */
  position: relative;
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
  background: var(--cc-primary);
  color: var(--cc-surface);
}
.ct__node.is-drop .ct__count {
  color: rgba(255, 255, 255, 0.8);
}
/* 命中的位置**不能放**（成环 / 目标下重名）。用红而不是置灰：
   置灰在树里混在正常行里几乎看不出来，红的才"这里被划掉了"。
   能不能放由 store 的 checkDrop 算好放在 dnd.overOk，模板只读结果。 */
.ct__node.is-bad {
  background: var(--cc-danger-soft);
  color: var(--cc-danger-deep);
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
/* 知识点叶子：**跟目录行一眼分得开** —— 浅一点、前面一个点。
   树上现在目录和知识点混在一起，不分开就分不清哪一行是"能进去的结构"、
   哪一行是"内容本身"。 */
.ct__node--topic {
  color: var(--cc-text-3);
}
.ct__node--topic .ct__name::before {
  content: '·';
  margin-right: var(--sp-6);
  color: var(--cc-text-4);
}
.ct__count {
  font-size: var(--text-sm);
  color: var(--cc-text-4);
  flex-shrink: 0;
}
.ct__empty {
  margin: 0;
  padding: var(--sp-7) var(--sp-10);
  font-size: var(--text-md);
  color: var(--cc-text-4);
}
</style>
