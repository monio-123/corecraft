<template>
  <div
    class="ct"
    :class="`ct--${mode}`"
    :style="mode === 'nav' ? { top: anchorTop + 'px' } : null"
    @mouseenter="onEnter"
    @mouseleave="onLeave"
  >
    <p v-if="mode === 'drop'" class="ct__hint">放到哪个目录</p>

    <div
      v-for="r in rows"
      :key="r.c.id"
      class="ct__node"
      :class="{ 'is-drop': dnd.over === r.c.id }"
      :style="{ paddingLeft: 8 + r.depth * 14 + 'px' }"
      @click="onPick(r.c)"
      @dragover="onDragOver(r.c.id, $event)"
      @drop="onDrop(r.c.id, $event)"
    >
      <span class="ct__name">{{ r.c.name }}</span>
      <!-- 数字只在落点模式出现：拖的时候要判断"往哪个厚目录放"，数字有用；
           浏览层级时结构已经摊平在缩进里了，数字是侧边栏那份的重复。 -->
      <span v-if="mode === 'drop'" class="ct__count">{{ counts[r.c.id] || 0 }}</span>
      <!-- 建/删子目录只在浏览模式给：拖拽中不该改结构，否则手一抖就删了目录。 -->
      <template v-if="mode === 'nav'">
        <el-icon class="ct__act" title="新建子目录" @click.stop="$emit('add', r.c)">
          <Plus />
        </el-icon>
        <el-icon class="ct__act ct__act--danger" title="删除目录" @click.stop="remove(r.c)">
          <Minus />
        </el-icon>
      </template>
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
import { Plus, Minus } from '@element-plus/icons-vue'
import {
  dnd, dropOver, dropTo, flattenCategoryTree, getCategoryCountMap, confirmDeleteCategory
} from './useCorecraftWebStore'

const props = defineProps({
  items: { type: Array, required: true },
  /** 'nav' = 浏览并进入；'drop' = 拖拽落点 */
  mode: { type: String, default: 'nav' },
  /** nav 模式相对视口顶部，贴住触发它的侧边栏那一行 */
  anchorTop: { type: Number, default: 0 }
})

const emit = defineEmits(['pick', 'add', 'enter', 'close'])

const rows = computed(() => flattenCategoryTree(props.items))
// computed 是惰性的：nav 模式模板里根本不读它，这个遍历一次都不会跑
const counts = computed(() => getCategoryCountMap())

function onPick(c) {
  if (props.mode === 'nav') emit('pick', c)
}

function onDragOver(id, e) {
  if (props.mode === 'drop') dropOver(id, e)
}

function onDrop(id, e) {
  if (props.mode === 'drop') dropTo(id, e)
}

// 只有 nav 模式需要向外挂开合：落点浮层是跟着 dnd.live 生死走的，没有开合这回事。
// 而在拖拽过程中发 close 会顺手 cancel 掉侧边栏那 300ms 的开菜单定时器。
function onEnter() {
  if (props.mode === 'nav') emit('enter')
}

function onLeave() {
  if (props.mode === 'nav') emit('close')
}

// 删掉的可能正是这棵树上的某个节点，不收的话弹层停在一棵少了一截的树上。
async function remove(c) {
  if (await confirmDeleteCategory(c)) emit('close')
}
</script>

<style scoped>
.ct {
  background: #fff;
  border: 1px solid #e3e6eb;
  border-radius: 10px;
  box-shadow: 0 8px 28px rgba(31, 35, 41, 0.12);
  padding: 6px;
  max-height: 60vh;
  overflow-y: auto;
}
/* 贴侧边栏右缘，右移 6px 让它和触发行"接上"，留空隙就是死区 */
.ct--nav {
  position: fixed;
  left: 220px;
  min-width: 150px;
  z-index: 40;
}
/* 落点浮层固定在内容区右侧居中：源卡片就在鼠标底下，浮层盖上去就没法拖了。
   左边拖、右边选，视线不用来回找。 */
.ct--drop {
  position: fixed;
  right: 24px;
  top: 50%;
  transform: translateY(-50%);
  width: 232px;
  z-index: 20;
}
.ct__hint {
  margin: 2px 0 6px;
  padding: 0 8px;
  font-size: 11px;
  color: #b6bac1;
}
.ct__node {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 8px;
  border-radius: 5px;
  font-size: 12.5px;
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
  font-size: 11px;
  color: #b6bac1;
  flex-shrink: 0;
}
/* ＋/－ 常显：hover 才显的话，"删掉一个空目录"就等于没有入口，
   而空目录恰恰是最该被清掉的。 */
.ct__act {
  font-size: 12px;
  color: #c0c4cc;
  cursor: pointer;
  flex-shrink: 0;
}
.ct__node:hover .ct__act {
  color: #606a75;
}
.ct__act--danger:hover {
  color: #d93026;
}
.ct__empty {
  margin: 0;
  padding: 6px 8px;
  font-size: 12px;
  color: #b6bac1;
}
</style>
