<template>
  <div
    ref="rootEl"
    class="ct"
    :class="`ct--${mode}`"
  >
    <p v-if="mode === 'drop'" class="ct__hint">
      {{ dnd.picking ? '点哪个目录' : '放到哪个目录' }}
    </p>

    <!-- ⚠️ **这个组件里没有"收起全部 / 展开全部"的按钮条**：
         它**在 `BookToc.vue` 里**（树视图顶部靠右两个小图标：展开⇄收起 + 用卡片看）——
         树视图那一屏的顶部只有那一行，**不放面包屑 / 筛选条 / 标题行**
         （这一屏回答的是"这本书里有什么"，筛知识点、考这个目录对它没意义）。
         组件内部**每行那个箭头**管的是"就地展开/收起它自己那棵"。 -->

    <!-- 行是 store 摊平好的**前序**列表：父节点后面紧跟自己的子树，
         所以「收起一个目录」= 把它那一整段剔掉，祖先收起 = 整棵子树不画。

         **知识点行**（浏览模式才有）：知识点当叶子摊在树上，挂在哪一级就在哪一级出现。
         它**不带 data-cat-id**：知识点不是**落点**（不能被拖进去），
         但它**自己是拖拽源**（拖着它 = 换个目录归属，见下面的 onTopicPointerDown）。 -->
    <template v-for="r in rows" :key="r.kind === 'cat' ? 'c' + r.c.id : 't' + r.t.id">
      <div
        v-if="r.kind === 'cat'"
        class="ct__node ct__node--cat"
        :class="{
          'ct__node--root': r.depth === 0,
          'is-drop': dnd.over === r.c.id && dnd.overOk,
          'is-bad': dnd.live && dnd.over === r.c.id && !dnd.overOk
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
             `.stop` 挡住冒泡：不拦住的话点箭头会顺带进这个目录（展开和进入是两件事）。
             ⚠️ **`@pointerdown.stop` 也不能少**：指针按在箭头上时不该起拖
             （那是"点一下展开"，不是"把这一行拖走"）。 -->
        <button
          v-if="mode === 'nav' && branchy.has(r.c.id)"
          type="button"
          class="ct__toggle"
          :class="{ 'is-open': !isCollapsed(r.c.id) }"
          :style="caretStyle(r)"
          :aria-label="(isCollapsed(r.c.id) ? '展开 ' : '收起 ') + r.c.name"
          :aria-expanded="!isCollapsed(r.c.id)"
          @click.stop.prevent="toggleOne(r.c.id)"
          @pointerdown.stop
        ><i class="ct__caret" /></button>
        <span class="ct__name">{{ r.c.name }}</span>
        <span class="ct__count" :title="countTitle(r.c.id)">{{ counts[r.c.id] || 0 }}</span>
        <!-- 展开 / 收起**整棵树**：只挂在**根行**（这一棵树就是那一本书）。
             ⚠️ **和行首那个箭头不是一回事**：箭头管"就地展开收起它自己那棵"，
             这个管"整棵树"——**收起 = 收到二级目录这一层**（根展开、二级目录收起，
             屏幕上就是"这本书有哪些章"），**展开 = 全展开**（连知识点叶子一起）。
             2026-11-04 用户拍板：它原来在树顶部独占一行，看着突兀 ⇒ 挪到根行上。
             `.stop` 挡住冒泡（点它不该顺带进这个目录）；`@pointerdown.stop` 不挡拖拽。 -->
        <button
          v-if="mode === 'nav' && r.depth === 0"
          type="button"
          class="ct__expand"
          :aria-label="fullyExpanded ? '收起' : '展开全部'"
          :title="fullyExpanded ? '收起' : '展开全部'"
          @click.stop.prevent="toggleAll"
          @pointerdown.stop
        >
          <el-icon><Fold v-if="fullyExpanded" /><Expand v-else /></el-icon>
        </button>
        <!-- 「⋯」只在浏览模式给：拖拽时鼠标正按着卡片，弹个菜单点一下"删除目录"就是误删。 -->
        <CategoryRowMenu
          v-if="mode === 'nav'"
          class="ct__menu"
          :category="r.c"
          @add="emit('add', $event)"
          @rename="emit('rename', $event)"
          @points="emit('points', $event)"
        />
      </div>

      <div
        v-else
        class="ct__node ct__node--topic"
        :class="{ 'is-hl': r.t.id === highlightId }"
        :style="rowStyle(r)"
        :data-topic-id="r.t.id"
        :data-topic-title="r.t.title"
        @click="onPickTopic(r.t)"
        @pointerdown="onTopicPointerDown"
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
 * - `mode="nav"`  **浏览**：树的行可点（进那个目录）、可展开收起、每行带目录操作菜单。
 * - `mode="drop"` 拖拽落点。行是可投放区，数字给出"这个目录下有多少条"。
 *
 * 三种用法曾经是两个组件（原 CategoryCascade 递归级联 + CategoryDropTree），
 * 但改成"一次铺开整棵子树的前序列表"之后两者长得一模一样了，所以合成一个。
 * 差异只有几处：贴哪、行的行为是什么、数字给不给——都由 mode 分支，没有别的分叉。
 *
 * 行是 store 摊平好的**前序**列表，不是这里递归出来的：递归写在模板里就只能
 * "本层列完再列子层"，父节点会被同层的兄弟拦腰截断，缩进表达不出父子关系。
 */
import { computed, nextTick, ref, watch } from 'vue'
import { Expand, Fold } from '@element-plus/icons-vue'
import CategoryRowMenu from './CategoryRowMenu.vue'
import { useTopicDrag, gestureWasDrag } from './useTopicDrag'
import { branchyIds, visibleRows, ancestorIds } from '../../utils/categoryTree'
import {
  dnd, dndPickTo, flattenCategoryTree, getCategoryCountMap
} from './useCorecraftWebStore'

const props = defineProps({
  items: { type: Array, required: true },
  /** 'nav' = 浏览并进入；'drop' = 拖拽落点 */
  mode: { type: String, default: 'nav' },
  /**
   * **默认收起**在第几层及以下的目录（按前序行里的 depth 算）。
   *
   * <p>null = 全部展开。
   *
   * <p>树视图用它把一棵 195 条的书摊成"骨架"：书和章展开、更深的收起 ——
   * 全展开是两百多行，滑十几屏才到底；收起是几十行，两屏看完整本书的结构。
   */
  collapseBelowDepth: { type: Number, default: null },
  /**
   * 这一屏现在是不是"在眼前"。
   *
   * <p>**由外层（页面）说了算**，不在这儿自己判断：页面被 keep-alive 缓存着
   * （切走不销毁），"我回来了"这件事只有页面知道 ⇒ 由它传下来，
   * 树就不必依赖"后代组件的 activated 钩子会不会触发"这种版本差异。
   *
   * <p>它管两件事：① **返回时定位到刚才那一行**（`active` 变 true 那一刻做）；
   * ② **不在眼前就不定位**（后台数据刷新时别把定位提前消费掉）。
   */
  active: { type: Boolean, default: true }
})

// 2026-10-07 展开改成"点 › 打开、点遮罩关闭"之后，`enter` / `close` 事件就不再有人发、
// 也没人接了，一并去掉。现在这棵树对外面只说四句话：选中一行（pick）、
// 有人要新增子目录（add）、有人要改名（rename）、有人要在这个目录下写知识点（points）。
const emit = defineEmits(['pick', 'pickTopic', 'add', 'rename', 'points'])

// **知识点只进浏览模式的树**：拖拽落点那棵树的每一行都得是可投放的目录，
// 多出来的叶子行只会挡住命中检测（指针落在它上面 → closest 找不到落点）。
const allRows = computed(() =>
  flattenCategoryTree(props.items, { withTopics: props.mode === 'nav' })
)

/**
 * 收起了的目录 id 集合。
 *
 * <p>两个来源：
 * - **默认**：`collapseBelowDepth` 算出来的那批（"树视图摊骨架"用）。
 * - **用户**：点箭头 / 「收起全部」手动改的。**用户一动就以用户为准**
 *   （`userTouched`）—— 否则每次数据刷新都把他的操作冲掉。
 */
const collapsed = ref(new Set())
/** 用户手动改过没有。改过之后默认值不再插手。 */
const userTouched = ref(false)

/**
 * 按 `collapseBelowDepth` 重算"默认收起"的那一批。
 *
 * <p>⚠️ **判据是 `branchy`**（下一行更深 = 有下级，**知识点叶子也算**）：
 * 没有下级的目录收不收起都一样，把它塞进集合只是让「收起全部」的按钮显示成
 * "有东西被收了"（按钮按 `collapsed.size` 换图标），而实际上一行都没少。
 *
 * <p>⚠️ **算的时机要跟着数据走**：树的数据是异步来的（结构索引），
 * 算默认值的时候索引还没到 ⇒ 算出来是空的 ⇒ "默认收起"没生效，
 * 而且**不会自己纠正**（没有第二次机会算）。⇒ 下面那个 watch 盯着行的条数。
 */
function resetCollapsed() {
  if (userTouched.value) return
  const limit = props.collapseBelowDepth
  if (limit == null) {
    collapsed.value = new Set()
    return
  }
  const next = new Set()
  for (const r of allRows.value) {
    if (r.kind === 'cat' && r.depth >= limit && branchy.value.has(r.c.id)) next.add(r.c.id)
  }
  collapsed.value = next
}

/** 有下级的目录 id —— **能画出展开箭头的那批**。 */
const branchy = computed(() => branchyIds(allRows.value))

/** 画出来的行：**被收起的目录，它自己那棵子树整段不画**（它自己那一行留着）。 */
const rows = computed(() => visibleRows(allRows.value, collapsed.value))

// 换了一棵树（切书）、或者**数据到了**（行的条数变了）就重算一次默认值。
// ⚠️ 两处都要盯：树的行数会因为结构索引异步到位而变 —— 只盯 items 的话
// 默认收起算在数据之前 ⇒ 算了空的 ⇒ 不生效且不纠正。
// **用 id 串 + 行数当依赖**：`items` 是每次渲染新建的数组，拿它当 watch 源
// 会在每次重算时都触发 ⇒ 用户手动收起的状态会被冲掉。
//
// ⚠️ **这个 watch 必须放在 `branchy` 之后**：immediate 会在 setup 期间同步跑一次，
// 而它要读 `branchy.value` —— 放在 `branchy` 声明前面就是 TDZ（还没初始化就取）。
watch(
  () => [props.items.map((c) => c.id).join(','), allRows.value.length].join('|'),
  () => resetCollapsed(),
  { immediate: true }
)

const counts = computed(() => getCategoryCountMap())

/** 那个数字是"含下级一共多少条"——不说清楚就是个不明所以的数字。 */
const countTitle = (id) => `含下级共 ${counts.value[id] || 0} 条`

/** 单个目录的展开 / 收起。 */
function toggleOne(id) {
  userTouched.value = true
  const next = new Set(collapsed.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  collapsed.value = next
}

const isCollapsed = (id) => collapsed.value.has(id)

/**
 * 「全展开」——**知识点叶子也一起展开**（树视图里知识点是叶子，收起的是目录）。
 */
function expandAll() {
  userTouched.value = true
  collapsed.value = new Set()
}

/**
 * 「收起」= **收到二级目录这一层**（用户 2026-11-04：「收缩收缩到所有的二级目录就行
 * 如图一所示就行 展开就是全展开包括知识点」）。
 *
 * <p>也就是：根**展开**（一级目录下面那层照画）、**所有二级目录收起**（它们的内容整段不画）、
 * 根自己挂着的知识点叶子照画（它是那一层的内容，不是结构）。
 *
 * <p>⚠️ **根不收**：收了根就只剩根一行 —— 那不是"收到二级目录"，是"收到零级"。
 * 判据就是**深度 ≥ 1 且有下级**（`branchy` 保证"有下级"，
 * 否则一个没下级的目录收不收起都一样，塞进去只会让按钮状态看着别扭）。
 */
function collapseToLevel2() {
  userTouched.value = true
  const next = new Set()
  for (const r of allRows.value) {
    if (r.kind === 'cat' && r.depth >= 1 && branchy.value.has(r.c.id)) next.add(r.c.id)
  }
  collapsed.value = next
}

/**
 * 现在是不是**一个都没收**。
 *
 * <p>根行那颗按钮据此决定画成「展开」还是「收起」：按钮画的是**点了会发生什么**
 * （现在是全展开 ⇒ 画「收起」）。默认那个骨架态（根展开、二级目录收起）算"还收着"
 * ⇒ 按钮显示「展开」。
 */
const fullyExpanded = computed(() => collapsed.value.size === 0)

/** 根行那颗按钮：两态切换（收起 = 收到二级目录 / 展开 = 全展开）。 */
function toggleAll() {
  if (fullyExpanded.value) collapseToLevel2()
  else expandAll()
}

// 缩进：每层 20px。
// ⚠️ **20 不是随便取的**：它同时是**箭头的命中区宽度**
// （`.ct__toggle` 是 20×20，绝对定位在缩进那一格里）⇒ 槽位比箭头窄的话，
// 箭头会盖住名字的第一个字。槽位 = 箭头宽度，两边刚好不打架。
const INDENT = 20
const BASE = 4
/** 知识点比它**同层的子目录**再深一档 —— 它是"这个目录里的内容"，不是结构的一层。 */
const TOPIC_EXTRA = 8

const indentOf = (r) => BASE + r.depth * INDENT

/**
 * 行的左内边距 = 缩进 + **箭头槽**。
 *
 * <p>**箭头槽为什么每一行都要留**（包括没有下级的目录、知识点叶子）：箭头是
 * **绝对定位**画在缩进那一格里的，不占文档流 ⇒ 不留槽的话，有箭头的行文字往右挪一格、
 * 没有的原地不动 ⇒ **同一层的目录和知识点对不齐**。统一留一格，各行文字才对得齐。
 *
 * <p>**拖拽落点模式不留**：那一棵树没有箭头（不能折叠），留个空槽只会白缩进。
 *
 * <p>顺带算出**导轨竖线**的位置（`--ct-rail`）：画在**父级名字起点**往左一点 ——
 * 每一行的竖线都贴着它父级的名字 ⇒ 一串竖线连起来就是一条阶梯，
 * 层级一眼看得见（光靠缩进看不出来谁是谁的孩子）。
 */
function rowStyle(r) {
  const slot = props.mode === 'nav' ? INDENT : 0
  const extra = r.kind === 'topic' ? TOPIC_EXTRA : 0
  return {
    paddingLeft: indentOf(r) + extra + slot + 'px',
    '--ct-rail': r.depth === 0 ? '-99px' : indentOf(r) - 6 + 'px'
  }
}

/** 箭头落在缩进那一格的起点上。 */
function caretStyle(r) {
  return { left: indentOf(r) + 'px' }
}

/**
 * ⚠️ **这里原来有一个 `navStyle`**（nav 模式浮在视口里的定位）——
 * 它是**侧边栏级联弹层**留下的（那个弹层 2026-11-03 已经删了）⇒ 现在 nav 模式
 * 只有内嵌这一种形态（树视图），定位由文档流决定 ⇒ 这段计算永远不生效，删掉。
 *
 * 落点浮层（drop 模式）的定位在 AppLayout 那边算好后用行内样式传进来。
 */

// ==================== 返回定位：状态 ====================

/**
 * 从这棵树点进详情的那条知识点。
 *
 * <p>用户 2026-11-03：「我从目录树点进某个知识点再返回现在要重新加载整个目录书
 * 我邀可以直接返回到刚刚打开的那个知识点的那个展开且hover住」。
 *
 * <p>⚠️ **"hover 住"做不到**：浏览器返回后鼠标未必还在那儿，鼠标一动就没了
 * ⇒ 落地成「**展开到它 + 那一行高亮 + 滚到可见处**」（用户 2026-11-03 拍板）。
 */
const lastTopicId = ref(null)
/** 当前高亮的那一行（返回时定位到它，几秒后淡出）。 */
const highlightId = ref(null)
/** 高亮多久后淡出。 */
const HL_MS = 4000
let hlTimer = null

/** 树的根元素（滚到可见处时在它里面找那一行）。 */
const rootEl = ref(null)

/**
 * nav 模式下每行都是拖拽源：把某个目录拖到另一行下面 = 换父级。
 * drop 模式不行——那时候用户正拖着别的东西，这棵树是落点不是源。
 *
 * **拖的是哪一行，从 event 的 currentTarget 上读**（和命中检测读 data-cat-id 同一套思路）：
 * 18 行共用一个 handler，靠行上挂数据区分是哪一行，不用给每行绑一个闭包。
 *
 * ⚠️ **目录行同时也是拖拽落点**（`data-cat-id` + 模板里的 `is-drop`/`is-bad`）：
 * 命中检测走的是 `elementFromPoint(...).closest('[data-cat-id]')`
 * ⇒ 树上任何一行都是落点。**但高亮必须显式绑**（原来 CSS 写了、模板没绑
 * ⇒ 拖到哪一行都没有反馈，看着就像"拖不了"，用户 2026-11-03 报的正是这个）。
 */
const { onPointerDown } = useTopicDrag((e) => {
  if (props.mode !== 'nav') return null
  const id = Number(e.currentTarget?.dataset?.catId)
  if (!id) return null
  return { what: { catId: id }, label: e.currentTarget.dataset.catName || '' }
})

/**
 * **知识点行也是拖拽源**（2026-11-03 用户拍板：「拖目录换父级 + 拖知识点叶子改归属」）。
 *
 * <p>拖它 = 换个目录归属（跟卡片拖到侧边栏目录是同一件事）⇒ 落点还是那些目录行
 * （`data-cat-id`），松手走 store 的 `dndDrop` ⇒ `categorizeTopic`。
 *
 * <p>⚠️ **知识点行同时也是"进详情"的入口** ⇒ 拖完之后浏览器照样会发 click
 * ⇒ 那一行会被"点"进详情页。**`gestureWasDrag` 就是挡这个的**（见 useTopicDrag.js）。
 */
const { onPointerDown: onTopicPointerDown } = useTopicDrag((e) => {
  if (props.mode !== 'nav') return null
  const id = Number(e.currentTarget?.dataset?.topicId)
  if (!id) return null
  return { what: { topicId: id }, label: e.currentTarget.dataset.topicTitle || '' }
})

function onPick(c) {
  // 拖过了就不算"点"（拖完浏览器照样发 click ⇒ 不挡的话会顺手跳进这个目录）
  if (gestureWasDrag.value) return
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
  // 拖过了就不算"点"（同上：拖着知识点改归属，落下松手不该跳进详情）
  if (gestureWasDrag.value) return
  // 记住是哪一条 ⇒ 返回时把树恢复到"刚才那一行"（见下面的 restoreSpot）
  lastTopicId.value = t.id
  emit('pickTopic', t)
}

// ==================== 返回时回到刚才那一行 ====================

/**
 * 回到"刚才那一行"：**展开它的祖先 → 高亮它 → 滚到眼前**。
 *
 * <p>⚠️ **祖先必须展开**：树是"被收起的目录，它自己那棵子树整段不画"
 * ⇒ 那条知识点藏在收起的目录下面时，它**根本不画**（`visibleRows` 的规矩）。
 * 不展开就高亮，等于高亮一个不存在的元素。
 *
 * <p>⚠️ **展开完要等一拍再滚**（`nextTick`）：`collapsed` 一改，DOM 是下一帧才更新的
 * ⇒ 不等就找不到那一行的元素。
 *
 * <p>⚠️ **不在眼前就不定位**（`props.active`）：用户已经跳进详情页了，
 * 这时候后台数据一刷新就把树定位好、高亮上 —— 等他返回时那一下高亮早就淡没了
 * （高亮几秒就淡出）⇒ 返回过去看着跟没定位一样。
 */
async function restoreSpot() {
  const id = lastTopicId.value
  if (id == null || !props.active) return
  const rows = allRows.value
  const idx = rows.findIndex((r) => r.kind === 'topic' && r.t.id === id)
  if (idx < 0) return // 数据还没到（或那条不在这一本书里）⇒ 下次再试
  const anc = ancestorIds(rows, idx)
  if (anc.length) {
    // 用户动过展开状态 ⇒ 之后数据刷新不再按默认值重算（否则又给收起来了）
    userTouched.value = true
    const next = new Set(collapsed.value)
    for (const a of anc) next.delete(a)
    collapsed.value = next
  }
  highlightId.value = id
  await nextTick()
  scrollToRow(id)
  clearTimeout(hlTimer)
  hlTimer = setTimeout(() => { highlightId.value = null }, HL_MS)
}

/** 把那一行滚到眼前。 */
function scrollToRow(id) {
  const el = rootEl.value?.querySelector(`[data-topic-id="${id}"]`)
  if (el?.scrollIntoView) el.scrollIntoView({ block: 'center', behavior: 'smooth' })
}

// 数据是异步来的（结构索引），回来时可能还没到 ⇒ 行数一变就再试一次。
watch(allRows, () => { restoreSpot() })

// **这一屏重新回到眼前**（从详情页返回）⇒ 回到刚才那一行。
// ⚠️ **触发点只有一个**：外层（页面）把 `active` 传下来。
// 树自己不用 `onActivated` —— **后代组件的 activated 钩子在不同 Vue 版本里
// 触发行为不一致**，页面级才知道"我回来了"（页面被 keep-alive 缓存着，切走不销毁）。
watch(() => props.active, (v) => { if (v) restoreSpot() })
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
/* 树视图（nav）：这棵树是**主区的一部分**，不是浮在别处的一个弹层 ⇒
   没有阴影/边框/内边距，跟着主区一起滚，宽度铺满。

   ⚠️ 2026-11-04：这里原来还留着**浮层形态**（position: fixed + 贴侧边栏 + min-width）
   —— 那是**侧边栏级联弹层**留下的（那个弹层 2026-11-03 已经删了）⇒ 那套规则永远不生效，
   而"内嵌"那一档还得一条条把它盖掉。⇒ 现在 nav 只有内嵌这一种形态，
   直接写在这一条里；`inline` 属性和 `.ct--inline` 一起删掉。 */
.ct--nav {
  width: 100%;
  background: transparent;
  border: none;
  border-radius: 0;
  box-shadow: none;
  padding: 0;
  overflow-y: visible;
}
/* 落点浮层：位置由 AppLayout 按指针坐标算好传进来（:style），这里只管外观。 */
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
/* 行首的展开/收起箭头。**绝对定位**在缩进那一格里（不占文档流，
   那一格由 rowStyle 统一留出来 ⇒ 各行文字才对得齐）。
   ⚠️ **命中区 20×20**（原来只有 14×14）：那个小三角太小，12~15px 的行高里
   几乎点不准（用户 2026-11-03：「特别是这个箭头」）。 */
.ct__toggle {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  padding: 0;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--cc-text-4);
  cursor: pointer;
  transition: color 0.12s ease;
}
.ct__node:hover .ct__toggle {
  color: var(--cc-text-2);
}
/* **折角（chevron）**：CSS 画的两条边，不是图标库那个三角。
   实心三角缩到 6px 就糊成一坨；折角是两根 1.5px 的线，缩到任何尺寸都还是一根折线。
   收起 = 指向右（-45°），展开 = 指向下（45°）。 */
.ct__caret {
  width: 6px;
  height: 6px;
  border-right: 1.5px solid currentColor;
  border-bottom: 1.5px solid currentColor;
  transform: rotate(-45deg);
  transition: transform 0.15s ease;
  /* 折角的几何中心偏右，往左挪一点才看着是正中的 */
  margin-left: -2px;
}
.ct__toggle.is-open .ct__caret {
  transform: rotate(45deg);
}
.ct__node {
  display: flex;
  align-items: center;
  gap: var(--sp-7);
  border-radius: var(--radius-sm);
  font-size: var(--text-xs);
  color: var(--cc-text-1);
  cursor: pointer;
  /* 展开箭头是**绝对定位**画在缩进那一格里的 ⇒ 行要是定位参照。 */
  position: relative;
  transition: background 0.25s ease, box-shadow 0.25s ease;
  /* 行尾工具（条数、「⋯」）**默认不显形**，悬停/聚焦这一行才露出来。
     —— 跟侧边栏目录行**同一套**（那边是 `.app-cats__item` 上的 `--row-tools`）。
     一屏几十行都挂着一个数字/按钮 = 几十个视觉噪点，把结构本身盖住了。
     ⚠️ 变量挂在**行**上而不是按钮上：自定义属性沿 DOM 继承，能穿过子组件的
     scoped 边界（`CategoryRowMenu` 的根元素拿不到本组件的 data-v）。 */
  --row-tools: 0;
  /* 行是拖拽源（nav 模式），禁掉文本选择。**跟 .card 上那三条是同一组，缺一条就露馅**：
     少了 user-select，按下拖动时浏览器当场开始选文字，树里几行目录名会被染成蓝块
     （2026-10-07 实机）。少了 -webkit-touch-callout，iOS 长按还会先弹系统「复制/查找」
     菜单，拖拽根本起不来。
     **touch-action 不在这里设**：树本身要能滚，写死等于把滚动禁了。 */
  user-select: none;
  -webkit-user-select: none;
  -webkit-touch-callout: none;
}
/* **导轨竖线**：画在父级名字起点往左一点 ⇒ 一串竖线连成阶梯，层级一眼看得见。
   根行没有父级 ⇒ rowStyle 给的是 -99px（画到屏幕外去了）。 */
.ct__node::before {
  content: '';
  position: absolute;
  left: var(--ct-rail, -99px);
  top: -1px;
  bottom: -1px;
  width: 1px;
  background: var(--cc-border-card);
  pointer-events: none;
}
.ct--drop .ct__node {
  cursor: copy;
}
.ct__node:hover {
  background: #f4f8ff;
  /* 行尾工具（条数、「⋯」）显形。跟侧边栏目录行同一套做法。 */
  --row-tools: 1;
}
/* 键盘也要能看见：opacity 0 的东西照样能被 Tab 选中，
   只挂 :hover 的话键盘用户会 Tab 到一个看不见的按钮上。 */
.ct__node:focus-within {
  --row-tools: 1;
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
  /* ⚠️ **不占满整行**（原来 `flex: 1`）：树视图里这棵树铺满整个主区（宽屏上千像素），
     名字撑满 ⇒ 行尾的条数和「⋯」被推到屏幕最右，眼睛得横跨一整屏才找到它们。
     改成"按内容占位" ⇒ **工具贴着名字走**（跟文件浏览器一样），
     行右边空着也不影响读。名字过长时靠 ellipsis 截断（min-width: 0 让它能被压缩）。 */
  flex: 0 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
/* **目录行**：深色、稍重 —— 它是"结构"。 */
.ct__node--cat .ct__name {
  font-weight: 500;
  color: var(--cc-text-1);
}
/* **知识点行**：浅一档、小一号、缩进更深 ⇒ 一眼分得开哪行是结构、哪行是内容。
   （以前用「前面一个圆点」区分 —— 点太小、看不清是内容还是结构，还平白多了一个符号。） */
.ct__node--topic {
  color: var(--cc-text-3);
  font-size: var(--text-md);
}
.ct__node--topic .ct__name {
  font-weight: 400;
}
/* 知识点行 hover 时字变深：它是**能点进去**的一行，得跟目录行一样有"可点"的反馈。 */
.ct__node--topic:hover .ct__name {
  color: var(--cc-text-1);
}
/* **根行**（当前这棵树的那本书）：大一号、加粗 —— 它是这一屏的起点。 */
.ct__node--root .ct__name {
  font-size: var(--text-lg);
  font-weight: 600;
}
.ct__count {
  font-size: var(--text-sm);
  /* 数字用 text-3 而不是 text-4：默认虽然不显形（hover 才出来），
     显出来的时候是要能一眼读到的 —— text-4（#b6bac1）在白底上几乎看不见。 */
  color: var(--cc-text-3);
  flex-shrink: 0;
  /* **默认不显形**，悬停/聚焦这一行才露出来（跟侧边栏目录行同一个做法，
     2026-11-03 用户拍板：「数字默认不显示 hover 才显示」）。
     一屏几十行都挂着数字 = 一片噪点，把结构本身盖住了。 */
  opacity: var(--row-tools, 1);
  transition: opacity 0.12s ease;
}
/* 「⋯」**hover 才显形**：跟条数共用行上的 --row-tools —— 两处各写一套
   opacity 规则的话，改的时候要改两处，而且「谁在后面谁赢」很难看出来。
   opacity 而不是 display —— 按钮始终占位，hover 时不跳。 */
.ct__menu {
  opacity: var(--row-tools, 1);
  transition: opacity 0.12s ease;
  flex-shrink: 0;
}
/* 根行的「展开 / 收起整棵树」。**跟行尾那个「⋯」同一档**（小方块、无文字、悬停提示走 title）。
   ⚠️ **分工**：行首那个箭头管"就地展开收起它自己那棵"，这颗管"整棵树"
   （收起 = 收到二级目录这一层，展开 = 全展开，含知识点叶子）。 */
.ct__expand {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: var(--ctl-sm);
  height: var(--ctl-sm);
  padding: 0;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--cc-text-3);
  cursor: pointer;
  flex-shrink: 0;
  transition: color 0.15s, background 0.15s;
}
.ct__expand:hover {
  background: var(--cc-surface-2);
  color: var(--cc-text-1);
}
/* **返回时定位到的那一行**：亮一下（几秒后淡出）。
   ⚠️ 用**行底色 + 左侧一道竖条**，不加边框 —— 加边框会把行撑大，
   整棵树在那一刻抖一下（用户正在看的就是那一行，抖一下最难受）。 */
.ct__node.is-hl {
  background: var(--cc-primary-soft);
  box-shadow: inset 2px 0 0 var(--cc-primary);
}
/* 触屏没有 hover ⇒ 常显（不然那些数字/按钮永远看不见）。
   跟 TopicCard 的「⋯」按钮同一口径（那边用 @media (hover: hover) 反着写，
   这里用 (hover: none) 正着写，效果一样）。 */
@media (hover: none) {
  .ct__node {
    --row-tools: 1;
  }
}
.ct__empty {
  margin: 0;
  padding: var(--sp-7) var(--sp-10);
  font-size: var(--text-md);
  color: var(--cc-text-4);
}

/* 树视图的行**比落点浮层里大一号**：浮层是窄面板里的辅助树，12px 够用；
   树视图是主区，字号要跟侧边栏、卡片那一档（15px）对齐，
   否则整页看着"这块是另一个系统"。
   ⚠️ 2026-11-04：选择器从 `.ct--inline` 改成 `.ct--nav` —— `inline` 这个属性
   删掉了（nav 只有内嵌这一种形态，"内嵌"不再是可选项）。 */
.ct--nav .ct__node {
  font-size: var(--text-base);
  padding: var(--sp-5) var(--sp-8);
}
/* ⚠️ **知识点行要压回小一号**：`.ct--nav .ct__node` 的特异性更高
   （两个类 vs 一个），不显式压回去的话知识点跟目录一样大 ⇒ 又分不开了。 */
.ct--nav .ct__node--topic {
  font-size: var(--text-md);
}
/* 根行大一号：字号设在**行**上（不是 .ct__name）—— 名字是继承来的，
   设在哪一层就在哪一层生效。 */
.ct--nav .ct__node--root {
  font-size: var(--text-lg);
  padding-top: var(--sp-7);
  padding-bottom: var(--sp-7);
}
</style>
