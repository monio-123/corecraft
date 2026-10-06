<template>
  <div
    class="ct"
    :class="`ct--${mode}`"
    :style="mode === 'nav' ? { top: anchorTop + 'px' } : null"
    @mouseenter="onEnter"
    @mouseleave="onLeave"
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
      :class="{ 'is-drop': dnd.over === r.c.id }"
      :style="{ paddingLeft: 8 + r.depth * 14 + 'px' }"
      :data-cat-id="r.c.id"
      @click="onPick(r.c)"
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
        @removed="emit('close')"
        @menu="onMenu"
      />
    </div>

    <p v-if="!rows.length" class="ct__empty">还没有目录</p>
  </div>
</template>

<script setup>
/**
 * 一棵目录树，两个用法：
 *
 * - `mode="nav"`  侧边栏一级目录 hover 出来的弹层。铺开整棵子树，点行进入该目录，
 *                 行尾 ＋/－ 建和删子目录。移开鼠标即消失。
 * - `mode="drop"` 拖起未归类卡片时浮在内容区右侧的落点。行是可投放区，数字给出
 *                 "这个目录下有多少条"，帮用户判断往哪放。
 *
 * 两种用法曾经是两个组件（原 CategoryCascade 递归级联 + CategoryDropTree），
 * 但改成"一次铺开整棵子树的前序列表"之后两者长得一模一样了，所以合成一个。
 * 差异只有三处：贴哪、行的行为是什么、数字给不给——都由 mode 分支，没有别的分叉。
 *
 * 行是 store 摊平好的**前序**列表，不是这里递归出来的：递归写在模板里就只能
 * "本层列完再列子层"，父节点会被同层的兄弟拦腰截断，缩进表达不出父子关系。
 */
import { computed, ref } from 'vue'
import CategoryRowMenu from './CategoryRowMenu.vue'
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

const emit = defineEmits(['pick', 'add', 'rename', 'enter', 'close'])

const rows = computed(() => flattenCategoryTree(props.items))
const counts = computed(() => getCategoryCountMap())

// 某一行上的「⋯」菜单是不是开着
const menuOpen = ref(false)

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

// 只有 nav 模式需要向外挂开合：落点浮层是跟着 dnd.live 生死走的，没有开合这回事。
// 而在拖拽过程中发 close 会顺手 cancel 掉侧边栏那 300ms 的开菜单定时器。
function onEnter() {
  if (props.mode === 'nav') emit('enter')
}

function onLeave() {
  // 菜单开着的时候不收这棵树。菜单 teleport 到 body，鼠标从行移到菜单的路上必然
  // 触发一次 mouseleave，220ms 缓冲一到树就被收走，菜单连同它的事件处理一起没了——
  // 表现就是"点开 ⋯ 能看见，点「重命名」没反应"。菜单本身就是当前在用的界面，
  // 它关了树再收。
  if (props.mode === 'nav' && !menuOpen.value) emit('close')
}

function onMenu(v) {
  menuOpen.value = v
  // 菜单关掉 = 用户已经用完了（点了某项、或点了空白、Esc）。没有命令跟着来的时候
  // 就把树一起收走，免得"点空白关菜单"留下一个赖着不走的弹层。
  if (!v) emit('close')
}
</script>

<style scoped>
.ct {
  background: #fff;
  border: 1px solid #e3e6eb;
  border-radius: calc(10px * var(--ui-scale));
  box-shadow: 0 8px 28px rgba(31, 35, 41, 0.12);
  padding: calc(7px * var(--ui-scale));
  max-height: 60vh;
  overflow-y: auto;
}
/* 贴侧边栏右缘，右移 6px 让它和触发行"接上"，留空隙就是死区 */
.ct--nav {
  position: fixed;
  left: calc(220px * var(--ui-scale));
  min-width: calc(180px * var(--ui-scale));
  z-index: 40;
}
/* 落点浮层：位置由 AppLayout 按指针坐标算好传进来（:style），这里只管外观。
   原来是写死的 `right: 24px; top: 50%`——指针在左半屏拖、落点在右边，视线要来回找。
   跟着指针走之后 "指针右下" 是默认位，靠近右/下边缘时由 AppLayout 翻到另一侧。 */
.ct--drop {
  position: fixed;
  width: calc(264px * var(--ui-scale));
  max-height: calc(312px * var(--ui-scale));
  overflow-y: auto;
  z-index: 20;
}
.ct__hint {
  margin: calc(2px * var(--ui-scale)) 0 calc(6px * var(--ui-scale));
  padding: 0 calc(8px * var(--ui-scale));
  font-size: calc(13px * var(--ui-scale));
  color: #b6bac1;
}
.ct__node {
  display: flex;
  align-items: center;
  gap: calc(7px * var(--ui-scale));
  padding: calc(6px * var(--ui-scale)) calc(10px * var(--ui-scale));
  border-radius: calc(5px * var(--ui-scale));
  font-size: calc(12.5px * var(--ui-scale));
  color: #1f2329;
  cursor: pointer;
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
.ct__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ct__count {
  font-size: calc(13px * var(--ui-scale));
  color: #b6bac1;
  flex-shrink: 0;
}
.ct__empty {
  margin: 0;
  padding: calc(7px * var(--ui-scale)) calc(10px * var(--ui-scale));
  font-size: calc(14px * var(--ui-scale));
  color: #b6bac1;
}
</style>
