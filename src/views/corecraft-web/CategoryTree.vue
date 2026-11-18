<template>
  <div
    ref="rootEl"
    class="ct ct--nav"
    :class="{ 'ct--cards': mode === 'cards' }"
  >

    <!-- ⚠️ **浏览模式（nav）有一个顶部标题行**，拖拽落点模式（drop）没有：
         落点浮层是一棵纯目录树，没有"这一本书"可言。

         标题行放四样东西：
         - **书名**（点它 = 进这本书的卡片视图，与原来的根行一个意思）；
         - **条数**（这本书一共多少条，含下级）；
         - **「总览」**：这本书**直接挂着的那条知识点**。原来它摊在树上是一条灰色的孤行
           （夹在章之间，像多出来的一行）⇒ 收进标题行，树上不再画；
         - **展开全部 / 收起全部 / 过滤**：三个"一次定位"用的工具。

         ⇒ 树里**不再画根行**（书名已经在标题行里，再画一遍就是两行标题）。 -->

    <!-- 行是 store 摊平好的**前序**列表：父节点后面紧跟自己的子树，
         所以「收起一个目录」= 把它那一整段剔掉，祖先收起 = 整棵子树不画。

         **知识点行**：知识点当叶子摊在树上，挂在哪一级就在哪一级出现。
         它**不带 data-cat-id**：知识点不是**落点**（不能被拖进去），
         它**也不再是拖拽源**（拖知识点换归属那套 2026-11-05 删了）。 -->
    <!-- ⚠️ **树身外面套一层 `.ct__body`** —— 它现在**只剩一条上边**。
         ⚠️ 2026-11-05 用户：「这几根线连在一起太难看了，要不还是把左右的线给去掉吧」
         ⇒ 原来的"左、右、上三条边"去掉了左右两条，**只留上边**
         （它是"标题行 ↔ 树身"的分隔线；框是同一套语言的最后一点残留，
         层级导轨那条竖线也跟着拖拽落点浮层一起撤了）。 -->
    <!-- ⚠️ **v-show 不拆**：切去卡片模式只是把树藏起来 —— 树记住的展开状态 / 返回定位
         都还活着，切回「目录树」原样还在。 -->
    <div v-show="mode === 'tree'" class="ct__body">
    <!-- ⚠️ **命中浮条撤了**（2026-11-09 用户：「目录树的那个定位框也跟这里一样复用上面那个就行」）：
         原来这里挂着一个可拖的命中浮条（「N 个命中」+ ↑↓）——
         那是**另一套**命中浮条（目录那档），和顶栏搜索框里的正文命中计数各画各的。
         ⇒ 撤掉，计数与 ↑↓ 统一到**顶栏搜索框的 suffix 区**（壳里画，
         数据经 store 的 `treeSearchHits` / `treeSearchJump` 两条通道过来）。
         树里**仍然标蓝命中字**、**仍然有当前命中的游标**（`is-cursor`）——
         撤的只是那个浮条容器，不是"命中这件事本身"。 -->
    <!-- ⚠️ 2026-11-08 按老 demo（book-bak.html）重排：**章 = 一张圆角卡片**。
         卡片头 = 序号徽章 + 章名 + chevron（常驻、随开合旋转）+「⋯」+ 条数药丸；
         卡片身 = 子目录行（序号拆出来用主题色小字）。
         原来的「点状引线」排版（2026-11-04 #749 那版书籍目录）被这一版取代 ——
         老稿里没有引线：名字吃满剩余宽度，条数贴行尾。
         分组方式：`rows` 是**前序**列表（父节点后紧跟自己的子树）⇒ 按 depth===1 的
         目录行切块，一块 = 一张卡片；被收起的章 rest 为空 ⇒ 卡片只剩头。 -->
    <!-- 树身只画目录行（2026-11-16 D7）：知识点行、直属知识点尾块都搬去了卡片视图。 -->
    <template v-for="g in chapterGroups" :key="'c' + g.head.c.id">
      <!-- ⚠️ 章视图模式已删（2026-11-14）：章头行**永远画**（原来"打开某章 ⇒ 章头不画、
           节上移"的 is-entered 整套撤掉），章就是普通目录。 -->
      <section
        class="ct__card"
        :class="{ 'is-open': !isCollapsed(g.head.c.id) }"
      >
        <div
          class="ct__node ct__node--cat ct__node--chapter"
          :class="{
            'is-drop': dnd.over === g.head.c.id && dnd.overOk,
            'is-bad': dnd.live && dnd.over === g.head.c.id && !dnd.overOk,
            'is-cursor': isCursor('cat', g.head.c.id),
            'is-hl': isHl('cat', g.head.c.id),
            'is-cur': currentId === g.head.c.id
          }"
          :style="rowStyle(g.head)"
          :data-cat-id="g.head.c.id"
          :data-cat-name="g.head.c.name"
          @click="onPick($event, g.head.c)"
          @pointerdown="onPointerDown"
        >
          <!-- 章号徽章：28×28 圆角方块（老稿 .tch__num）。序号从名字里拆出来
               （「1. 网络基础与体系结构」→ 徽章「1」+ 名字「网络基础与体系结构」），
               没有序号前缀的名字不拆、不出徽章。
               ⚠️ 搜索词命中序号本身时徽章标蓝（名字部分照常走 parts 标字）。 -->
          <span v-if="catNum(g.head.c.name)" class="ct__num" :class="{ 'is-numhit': numHit(catNum(g.head.c.name).num) }">{{ catNum(g.head.c.name).num }}</span>
          <span class="ct__name"><span class="ct__text"><template v-for="(p, i) in parts(catNum(g.head.c.name) ? catNum(g.head.c.name).rest : g.head.c.name)" :key="i"><mark v-if="p.hit" class="ct__hl">{{ p.text }}</mark><template v-else>{{ p.text }}</template></template></span></span>
          <!-- ⚠️ 行序照老稿 .tch-row：名字 → 条数 → chevron（条数在箭头**左边**）。 -->
          <!-- ⚠️ 行序照老稿 .tch-row：名字 → 掌握度条 → 条数 → chevron（条数在箭头**左边**）。
               掌握度条是轻量角标（设计 §8.3）：无数据=空轨道，数字只进 title。 -->
          <MasteryBar :row="getCategoryMastery(g.head.c.id)" />
          <span class="ct__count" :title="countTitle(g.head.c.id)">{{ counts[g.head.c.id] || 0 }}</span>
          <span class="ct__tools">
            <CategoryRowMenu
              class="ct__menu"
              btn-class="ct-dots"
              :category="g.head.c"
              @add="emit('add', $event)"
              @rename="emit('rename', $event)"
              @points="emit('points', $event)"
            />
          </span>
        </div>
        <div v-if="g.rest.length" class="ct__card-body">
          <template v-for="r in g.rest" :key="r.kind === 'cat' ? 'c' + r.c.id : 't' + r.t.id">
            <div
              v-if="r.kind === 'cat'"
              class="ct__node ct__node--cat"
              :class="{
                'ct__node--d2': r.depth === 2,
                'ct__node--d3': r.depth >= 3,
                'is-drop': dnd.over === r.c.id && dnd.overOk,
                'is-bad': dnd.live && dnd.over === r.c.id && !dnd.overOk,
                'is-cursor': isCursor('cat', r.c.id),
                'is-hl': isHl('cat', r.c.id),
                'is-cur': currentId === r.c.id
              }"
              :style="rowStyle(r)"
              :data-cat-id="r.c.id"
              :data-cat-name="r.c.name"
              @click="onPick($event, r.c)"
              @pointerdown="onPointerDown"
            >
              <!-- 节号：拆出来用主题色小字（老稿 .tsec__num）。 -->
              <span v-if="catNum(r.c.name)" class="ct__secnum" :class="{ 'is-numhit': numHit(catNum(r.c.name).num) }">{{ catNum(r.c.name).num }}</span>
              <span class="ct__name"><span class="ct__text"><template v-for="(p, i) in parts(catNum(r.c.name) ? catNum(r.c.name).rest : r.c.name)" :key="i"><mark v-if="p.hit" class="ct__hl">{{ p.text }}</mark><template v-else>{{ p.text }}</template></template></span></span>
              <!-- ⚠️ 行序与章行一致：名字 → 条数 → chevron →「⋯」。
                   ⚠️ **展开控件统一成常驻 chevron 图标**（2026-11-08 用户：
                   「三级的展开和二级的展开现在不一样 一个是图标一个文字 统一为图标」）——
                   悬停文字那套（.ct__toggle）整套撤了。 -->
              <MasteryBar :row="getCategoryMastery(r.c.id)" />
              <span class="ct__count" :title="countTitle(r.c.id)">{{ counts[r.c.id] || 0 }}</span>
              <span class="ct__tools">
                <CategoryRowMenu
                  class="ct__menu"
                  btn-class="ct-dots"
                  :category="r.c"
                  @add="emit('add', $event)"
                  @rename="emit('rename', $event)"
                  @points="emit('points', $event)"
                />
              </span>
            </div>

            <div
              v-else
              class="ct__node ct__node--topic"
              :class="{
                'is-cursor': isCursor('topic', r.t.id),
                'is-hl': isHl('topic', r.t.id)
              }"
              :style="rowStyle(r)"
              :data-topic-id="r.t.id"
              :data-topic-title="r.t.title"
              @click="onPickTopic(r.t)"
            >
              <span class="ct__dot" aria-hidden="true" :style="dotStyle(r.t.id)" />
              <span class="ct__name"><span class="ct__text"><template v-for="(p, i) in parts(r.t.title)" :key="i"><mark v-if="p.hit" class="ct__hl">{{ p.text }}</mark><template v-else>{{ p.text }}</template></template></span></span>
              <span v-if="!r.t.hasContent" class="ct__skel">未写正文</span>
              <span v-if="topicLevelOf(r.t.id)" class="ct__mbar" :title="mbarTitle(r.t.id)"><i :style="{ width: levelProgress(topicLevelOf(r.t.id).level) + '%', background: mbarColor(r.t.id) }" /></span>
            </div>
          </template>
        </div>
      </section>
    </template>
    </div>

    <!-- **卡片模式的内容盒子**（2026-11-08 用户：「把下方方框内的内容替换就行」+
         「以首页那边点进去展示的格式为准 把卡片格式筛选条件这些都移动到这边来」）：
         ⚠️ **里面放的是外层（AppLearnView）的卡片视图块经插槽传进来的东西** ——
         同一个筛选条、同一个服务端分页网格，**和首页点进去的是同一套逻辑**，
         这里只负责显隐（不再自制第二套卡片格 —— 上一版自制格子全量拉正文，
         生产上"加载中好几秒"，两套逻辑也违反收敛）。 -->
    <div v-show="mode === 'cards'" class="ct__cards">
      <slot name="cards" />
    </div>
  </div>
</template>

<script setup>
/**
 * 一棵目录树，一个用法：**浏览**。
 *
 * 树的行可点（进那个目录）、可展开收起、每行带目录操作菜单；
 * **目录行还是拖拽源**（拖到另一行下面 = 换父级）。
 *
 * ⚠️ **原来的第二种形态 `mode="drop"`（拖拽落点浮层）删了**（2026-11-05 用户：
 * 「图二是最早做的拖动归类 也直接删掉吧」）—— 那个浮层是**拖知识点进目录**时弹的
 * 落点树，知识点不再能拖 ⇒ 那个形态永远出不来 ⇒ 连同 mode 分支一起撤掉。
 *
 * 行是 store 摊平好的**前序**列表，不是这里递归出来的：递归写在模板里就只能
 * "本层列完再列子层"，父节点会被同层的兄弟拦腰截断，缩进表达不出父子关系。
 */
import { computed, nextTick, onActivated, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import CategoryRowMenu from './CategoryRowMenu.vue'
import MasteryBar from './MasteryBar.vue'
import { levelColor, levelProgress } from './masteryLevels'
import { isMasteryShown } from '../../utils/uiPrefs'
import { useCategoryDrag, consumeDragGesture } from './useCategoryDrag'
import {
  branchyIds, visibleRows, ancestorIds, namedHitIds, highlightParts, matchesKeyword,
  catNum, toggleInSet, removeFromSet
} from '../../utils/categoryTree'
import { matchNameFromRoute } from '../../utils/searchMode'
import { keywordFromRoute } from '../../utils/routeQuery'
import {
  flattenCategoryTree, getFullCategoryCountMap, getCategoryMastery, getFullTopicIndex,
  getTopicLevel, ensureTopicLevelsLoaded,
  treeSearchHits, treeSearchJump
} from './useCorecraftWebStore'
import { dnd } from './useCategoryDnd'

const route = useRoute()

const props = defineProps({
  items: { type: Array, required: true },
  /**
   * 这一屏的形态：'tree'（章卡片树）| 'cards'（这本书的知识点卡片视图）。
   * ⚠️ **状态在外层**（AppLearnView 的 `?view=cards`）—— 卡片内容是**外层的卡片视图块**
   * 经插槽放进来的（和首页点进去的那套是**同一个**：同一个筛选条、同一个服务端分页网格），
   * 这里只负责显隐和分段按钮的高亮；按钮点了往上抛 `mode-change`，由外层改地址。
   */
  mode: { type: String, default: 'tree' },
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
  active: { type: Boolean, default: true },
  /**
   * **当前目录**（2026-11-16 D7）：面包屑当前截 = 树里高亮的那一行 = 卡片内容源。
   * 点行设当前（emit('pick')），外层把它写进地址再传回来。
   * null = 还没选（用书根）。
   */
  current: { type: Number, default: null }
})

// 2026-10-07 展开改成"点 › 打开、点遮罩关闭"之后，`enter` / `close` 事件就不再有人发、
// 也没人接了，一并去掉。现在这棵树对外面只说四句话：选中一行（pick）、
// 有人要新增子目录（add）、有人要改名（rename）、有人要在这个目录下写知识点（points）。
const emit = defineEmits(['pick', 'pickTopic', 'add', 'rename', 'points'])

const allRows = computed(() =>
  flattenCategoryTree(props.items)
)

/**
 * 这本书的**目录**（平铺，知识点不算）。
 *
 * <p>搜索判据（{@link hitCategoryIds}）拿的是这份：名字命中的那条目录，
 * 它的**祖先链**也得跟着留（不然就成了"孤儿目录"）⇒ 判据得看得见祖先。
 */
const bookCats = computed(() => allRows.value.filter((r) => r.kind === 'cat').map((r) => r.c))

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

/** 有下级的目录 id —— **能展开/收起的那批**。 */
const branchy = computed(() => branchyIds(allRows.value))

/**
 * 这棵树的书 = **根目录**（`items` 就是它一棵）。
 *
 * <p>浏览模式下它不再占一行（书名在顶部标题行里），但它仍是这一屏的主角：
 * 标题行的书名、条数、「总览」都从这儿出。
 */
const rootCat = computed(() => {
  const first = allRows.value.find((r) => r.kind === 'cat')
  return first ? first.c : null
})

/** 当前目录（没选 = 书根）。树里高亮它，卡片视图显示它的直属。 */
const currentId = computed(() => props.current ?? rootCat.value?.id ?? null)


/**
 * **当前搜索词**（空 = 没在搜）。
 *
 * <p>⚠️ **它不是"这棵树里自己那个输入框的值"**（那个框 2026-11-05 晚删了）——
 * 是**顶栏那个公共搜索**的词，也就是 URL 上的 `?q=` ⇒ 走 `keywordFromRoute`
 * （**关键词的读法只有一处**，见 utils/routeQuery.js）。
 *
 * <p>⚠️ **为什么直接读 URL 而不是读"索引用的那个词"**：
 * 树的行来自**全集索引**（什么词都不筛，搜索态下树也要完整）⇒ 树上的东西
 * **本来就不是按词筛出来的** ⇒ 用索引那份词来判命中，是拿"上一批数据的身份"
 * 去解释"这一批数据"，慢半拍时会判错（症状：词已经换了，标蓝还停在旧词上）。
 * URL 是唯一真相 ⇒ 词、档位都从 URL 取。
 */
const searchKw = computed(() => keywordFromRoute(route).trim())

/**
 * **要不要把目录名也算命中** —— 只有「搜目录」那档要。
 *
 * <p>判据是**档位**（URL 上的 `?mode=`）—— 和侧边栏（AppLayout）、列表页
 * （AppLearnView）**同一份推导**（`matchNameFromRoute`，见 utils/searchMode.js）。
 *
 * <p>⚠️ **不能拿"索引的 scope"当判据**（那是上一版的做法）：索引是异步来的，
 * 换档位到索引回来之间有一小段时间里它还是上一档的 scope ⇒ 侧边栏按新档位、
 * 树按旧档位 ⇒ 两边对不上（症状：侧边栏有"目录名命中"，树上没有）。
 */
const matchName = computed(() => matchNameFromRoute(route))

/**
 * **命中的目录**（目录名里有那个词的那些）。
 *
 * <p>⚠️ **只用来标"名字里那几个字"**（{@link parts}）——
 * 不是"整行算命中"（用户 2026-11-05：「我让你标蓝是标蓝命中的文字
 * 你把他对应的目录标蓝」⇒ 标的是**字**，不是**行**）。
 *
 * <p>⚠️ **只要"名字命中"这一份**（{@link namedHitIds}）：树上**不过滤**
 * （树永远完整，见上面 `rows` 那段）⇒ "留得住哪些目录"那一半跟这棵树没关系。
 */
const namedHits = computed(() => namedHitIds(bookCats.value, searchKw.value))

/**
 * **画出来的行**。
 *
 * <p>两件事：
 * - 浏览模式**不画根行**（书名在顶部标题行里）；
 * - **知识点行不在这里画**（2026-11-16 D7：树只画目录行，内容归卡片）。
 *
 * ⚠️ **搜索时也不过滤**（2026-11-05 用户：「目录树的搜索展示完整
 * 不要把目录树和数据做任何过滤了」＋「既然加了命中栏 那么展示的树就要是完整的啊
 * 你把目录数据过滤了然后加个命中栏的意义在哪？」）
 * ⇒ 树 = **这本书的完整结构**：目录一个不少、知识点一条不少。
 * 命中靠**标字**和**命中浮条**表达，不靠"把不相关的藏起来"。
 */
const rows = computed(() => {
  const list = allRows.value.filter((r) => {
    if (r.kind === 'cat' && r.depth === 0) return false
    return true
  })
  return visibleRows(list, collapsed.value)
})

// 换了一棵树（切书）、或者**数据到了**（行的条数变了）就重算一次默认值。
// ⚠️ 两处都要盯：树的行数会因为结构索引异步到位而变 —— 只盯 items 的话
// 默认收起算在数据之前 ⇒ 算了空的 ⇒ 不生效且不纠正。
// **用 id 串 + 行数当依赖**：`items` 是每次渲染新建的数组，拿它当 watch 源
// 会在每次重算时都触发 ⇒ 用户手动收起的状态会被冲掉。
//
// ⚠️ **换书要把 `userTouched` 作废**（2026-11-13 用户：「目录树进来默认展示为全收缩」）：
// 原来它一旦被用户手动展开过就永远为 true ⇒ 换到另一本书也不再重算，新书整棵展开，
// 看着就像"默认不收缩"。现在把它限定在**同一本书内**：换书 = 新的一本，重新全收起；
// 同一本书里切走再回来（keep-alive 不销毁）、从详情页返回定位，都照旧保留。
//
// ⚠️ **这个 watch 必须放在 `branchy` 之后**：immediate 会在 setup 期间同步跑一次，
// 而它要读 `branchy.value` —— 放在 `branchy` 声明前面就是 TDZ（还没初始化就取）。
let lastRootKey = null
watch(
  () => [props.items.map((c) => c.id).join(','), allRows.value.length].join('|'),
  () => {
    const rootKey = props.items.map((c) => c.id).join(',')
    if (rootKey !== lastRootKey) {
      lastRootKey = rootKey
      userTouched.value = false
    }
    resetCollapsed()
  },
  { immediate: true }
)

const counts = computed(() => getFullCategoryCountMap())

/** 那个数字是"含下级一共多少条"——不说清楚就是个不明所以的数字。 */
const countTitle = (id) => `含下级共 ${counts.value[id] || 0} 条`

// ==================== 老稿样式：章卡片 / 序号拆分 / meta 行（2026-11-08） ====================

/**
 * **把「1.2 分层体系结构」拆成序号和名字** —— 拆法在 `utils/categoryTree.js` 的
 * {@link catNum}（**只有一份**，这里只是转出来给模板用）。
 */

/** 搜索词命中**序号本身**时徽章/节号标蓝（名字部分照常走 parts 标字）。 */
function numHit(num) {
  return !!searchKw.value && matchesKeyword(num, searchKw.value)
}

/**
 * **行按章分组**：`rows`（前序、已按收起过滤）里每个 depth===1 的目录行开一张卡片，
 * 它后面、下一个 depth===1 之前的所有行都是卡片身。
 * 被收起的章 ⇒ rest 为空 ⇒ 卡片只剩头（和"收起 = 子树不画"是同一条规矩）。
 * 根下直挂的散行（总览之外理论上没有）落成 head:null 的组，按老样子画一行兜底。
 */
const chapterGroups = computed(() => {
  const groups = []
  let cur = null
  for (const r of rows.value) {
    if (r.kind === 'cat' && r.depth === 1) {
      cur = { head: r, rest: [] }
      groups.push(cur)
    } else if (cur) {
      cur.rest.push(r)
    }
  }
  return groups
})

/**
 * 单个目录的展开 / 收起（行点 = 设当前 + 开合，2026-11-16 D7 之后 chevron 没了，
 * 开合整个落在行上）。
 */
function toggleOne(id) {
  userTouched.value = true
  collapsed.value = toggleInSet(collapsed.value, id)
}

const isCollapsed = (id) => collapsed.value.has(id)

// 缩进（2026-11-08 代码级复刻 book-bak.html）：章行不缩进（卡片头的 padding 14/18 自己给）；
// 节行左内边距 **24px**（老稿 .tsec-row）；更深层级按每层 +20px 顺延（24 → 44 → 64）。
const CAT_INDENT = 24
const TOPIC_INDENT = 52
const STEP = 20

/**
 * 行的左内边距 = 缩进（章行 0，由卡片头的 padding 提供）。
 *
 * <p>层级靠**缩进 + 字号**表达（书籍目录就是这么做的），不用竖线。
 */
function rowStyle(r) {
  // ⚠️ 章行**不返回 inline padding**（返回空对象）——章行的 14px/18px 在样式表里给；
  // 这里要是返回 paddingLeft: 0，内联样式会盖掉样式表的 padding-left（diff 实测踩过）。
    if (r.depth <= 1) return {}
  if (r.kind === 'cat') return { paddingLeft: CAT_INDENT + (r.depth - 2) * STEP + 'px' }
  const d = Math.max(0, r.depth - 2)
  return { paddingLeft: TOPIC_INDENT + d * STEP + 'px' }
}

/**
 * ⚠️ **这里原来有一个 `caretStyle`**（箭头落在缩进那一格里的位置）——
 * 行首那颗 `>/∨` 小箭头 2026-11-05 晚撤掉了（换成了悬停才现形的「展开 / 收起」文字），
 * 位置随它一起删。
 *
 * ⚠️ **这里原来有一个 `navStyle`**（nav 模式浮在视口里的定位）——
 * 它是**侧边栏级联弹层**留下的（那个弹层 2026-11-03 已经删了）⇒ 现在 nav 模式
 * 只有内嵌这一种形态（树视图），定位由文档流决定 ⇒ 这段计算永远不生效，删掉。
 *
 * 落点浮层（drop 模式）的定位在 AppLayout 那边算好后用行内样式传进来。
 */
// ==================== 返回定位：状态 ====================

/**
 * 刚才点进去的那一行（**目录或知识点**）。
 *
 * <p>用户 2026-11-03：「我从目录树点进某个知识点再返回现在要重新加载整个目录书
 * 我邀可以直接返回到刚刚打开的那个知识点的那个展开且hover住」。
 * 2026-11-04 追加：目录也一样 —— 从树点进某个目录的卡片页再返回，
 * 要回到**那一行目录**、把它展开着。
 *
 * <p>⚠️ **"hover 住"做不到**：浏览器返回后鼠标未必还在那儿，鼠标一动就没了
 * ⇒ 落地成「**展开到它 + 那一行高亮 + 滚到可见处**」（用户 2026-11-03 拍板）。
 *
 * <p>kind: 'cat'（目录）| 'topic'（知识点）—— 两种点法共用这一份状态，
 * 恢复逻辑也只有一份（见 restoreSpot）。
 */
const lastSpot = ref(null)
/** 当前高亮的那一行（返回时定位到它，几秒后淡出）。 */
const highlight = ref(null)
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
const { onPointerDown } = useCategoryDrag((e) => {
  const id = Number(e.currentTarget?.dataset?.catId)
  if (!id) return null
  return { catId: id, label: e.currentTarget.dataset.catName || '' }
})

/**
 * 点一行目录（2026-11-17 调整）：有下级（含知识点行）时 **点一次展开，再点一次收起**；
 * 看卡片统一走行尾 `⋯ → 查看卡片`；真·空目录（无下级）直接切去卡片区。
 */
function onPick(e, c) {
  if (consumeDragGesture()) return
  if (e.target.closest('.ct__tools')) return
  lastSpot.value = { kind: 'cat', id: c.id }
  if (branchy.value.has(c.id)) {
    toggleOne(c.id)
    emit('pick', c, 'tree')
  } else {
    emit('pick', c, 'cards')
  }
}

/**
 * 点知识点行 ⇒ 进它的**详情页**（树里的知识点是"看见"，读它去详情）。
 */
function onPickTopic(t) {
  if (consumeDragGesture()) return
  lastSpot.value = { kind: 'topic', id: t.id }
  emit('pickTopic', t)
}


// ==================== 返回时回到刚才那一行 ====================

/**
 * 展开到某一行的祖先（**目录连它自己一起展开**，知识点只展开祖先）。
 *
 * <p>⚠️ **祖先必须展开**：树是"被收起的目录，它自己那棵子树整段不画"
 * ⇒ 那条知识点藏在收起的目录下面时**根本不画**（`visibleRows` 的规矩）。
 * 不展开就高亮，等于高亮一个不存在的元素。
 *
 * <p>**这套只有一份**（{@link restoreSpot} 与 {@link focusSpot} 都用它）——
 * 两处各写一遍的话，改一处就漂（症状是"树明明展开了，那一条还是不出现"）。
 *
 * @param {{kind:'cat'|'topic', id:number}} spot 那一行
 * @returns {number} 这一行在前序列表里的下标（-1 = 数据还没到 / 不在这本书里）
 */
function expandToSpot(spot) {
  const rows = allRows.value
  const idx = rows.findIndex((r) =>
    spot.kind === 'cat'
      ? r.kind === 'cat' && r.c.id === spot.id
      : r.kind === 'topic' && r.t.id === spot.id
  )
  if (idx < 0) return -1
  const anc = ancestorIds(rows, idx)
  // 目录：展开祖先**和它自己**（它自己展开 ⇒ 它的子级是画出来的）。
  // 知识点是叶子，没有"自己"这一级 ⇒ 只展开祖先。
  const toOpen = spot.kind === 'cat' ? [...anc, spot.id] : anc
  if (toOpen.length) {
    // 用户动过展开状态 ⇒ 之后数据刷新不再按默认值重算（否则又给收起来了）
    userTouched.value = true
    collapsed.value = removeFromSet(collapsed.value, toOpen)
  }
  return idx
}

/**
 * 回到"刚才那一行"：**展开它的祖先 → 高亮它 → 滚到眼前**。
 *
 * <p>⚠️ **祖先必须展开**：树是"被收起的目录，它自己那棵子树整段不画"
 * ⇒ 那条知识点藏在收起的目录下面时，它**根本不画**（`visibleRows` 的规矩）。
 * 不展开就高亮，等于高亮一个不存在的元素。
 *
 * <p>⚠️ **目录要多展开它自己一级**：用户要的是"回到那个目录、**它的子级是展开的**"
 * （2026-11-04）⇒ 只展开祖先的话，那个目录是个光杆，看不出它下面有什么。
 *
 * <p>⚠️ **展开完要等一拍再滚**（`nextTick`）：`collapsed` 一改，DOM 是下一帧才更新的
 * ⇒ 不等就找不到那一行的元素。
 *
 * <p>⚠️ **不在眼前就不定位**（`props.active`）：用户已经跳进详情页了，
 * 这时候后台数据一刷新就把树定位好、高亮上 —— 等他返回时那一下高亮早就淡没了
 * （高亮几秒就淡出）⇒ 返回过去看着跟没定位一样。
 */
async function restoreSpot() {
  const spot = lastSpot.value
  if (spot == null || !props.active) return
  if (expandToSpot(spot) < 0) return // 数据还没到（或那一条不在这一本书里）⇒ 下次再试
  highlight.value = spot
  await nextTick()
  scrollToRow(spot)
  clearTimeout(hlTimer)
  hlTimer = setTimeout(() => { highlight.value = null }, HL_MS)
}

/**
 * **把某个目录滚到眼前**（命中浮条的 ↑↓ 用）。
 *
 * <p>⚠️ **它不管游标**：游标状态在**外面**（页面知道"现在是第几个命中"），
 * 这里只负责"展开祖先 + 滚过去"，展开那套与 {@link restoreSpot} 共用。
 */
async function focusSpot(spot) {
  if (expandToSpot(spot) < 0) return
  await nextTick()
  scrollToRow(spot)
}

// ==================== 命中：标字 + 浮条 ====================

// ==================== 知识点行的掌握度（圆点染色 + 右侧进度条） ====================
// 数据源 = store 的 topicLevelsMap（GET /kp/learning/levels，行数=考过的知识点数）。
// **没有评估的知识点行保持原样**（不染色不画条，用户口径）。

/** 档位（{level, mastery}）；没评估或偏好隐藏 = null */
function topicLevelOf(id) {
  return isMasteryShown() ? getTopicLevel(id) : null
}

/** 圆点染色：色 = masteryLevels 的 levelColor（与卡片档位签同一份色源） */
function dotStyle(id) {
  const lv = topicLevelOf(id)
  return lv ? { background: levelColor(lv.level) } : null
}

function mbarTitle(id) {
  const lv = topicLevelOf(id)
  return lv ? `${lv.level} · 掌握度 ${Math.round(lv.mastery || 0)}%` : ''
}

/** 条的填充色 = 档位色（与圆点同一份色源；主色档的条也就不再与圆点色打架） */
function mbarColor(id) {
  const lv = topicLevelOf(id)
  return lv ? levelColor(lv.level) : null
}

/**
 * **一行文字切成"命中的字"和"其余的字"**。
 *
 * <p>⚠️ **标的是字，不是行**（用户 2026-11-05：「我让你标蓝是标蓝命中的文字
 * 你把他对应的目录标蓝」⇒ 他要的是"那几个字是蓝的"，不是"这一行是蓝的"）。
 *
 * <p>⚠️ **判"是不是命中"和"标哪几个字"必须同一套规则** ⇒ 两边都走
 * {@link matchesKeyword}（大小写不敏感的子串）：它和 {@link highlightParts}
 * 共用同一份关键词归一 ⇒ 不会出现"命中了却一个字都没标"或者"标了却没算命中"。
 */
function parts(text) {
  return highlightParts(text, searchKw.value)
}

/**
 * **命中清单**（按树上的顺序）：**目录名命中** ∪ **知识点标题命中**。
 *
 * <p>⚠️ **只有目录那档有**（`matchName`）：正文那档是**纯过滤**（命中什么只剩什么），
 * 满屏都是命中的卡片 ⇒ 没有"哪一条命中了"这个问题（用户 2026-11-05）。
 *
 * <p>⚠️ **根（书名）也算一条**：它的名字里可能有那个词，而且它就在树顶上的标题行里
 * ⇒ 标了字就得能被 ↑↓ 跳到（否则"标蓝了却跳不到"）。
 */
const hitRows = computed(() => {
  if (!searchKw.value || !matchName.value) return []
  const named = namedHits.value
  const out = []
  for (const r of allRows.value) {
    if (r.kind === 'cat') {
      if (named.has(r.c.id)) out.push(r)
    } else if (matchesKeyword(r.t.title, searchKw.value)) {
      out.push(r)
    }
  }
  return out
})

/** 命中游标指着的那一条（null = 还没指）。 */
const hitCursor = ref(null)

/** 这一行是不是命中游标指着的那一条。 */
function isCursor(kind, id) {
  return hitCursor.value?.kind === kind && hitCursor.value.id === id
}

/**
 * 这一行是不是**搜索定位高亮**的那一条。
 *
 * <p>⚠️ **判据只有这一份**（原来模板里手写了 5 遍 `highlight?.kind === K && highlight.id === ID`）：
 * 改判据（比如加上"只在目录那档高亮"）漏掉一处 ⇒ 那一类行不高亮，
 * 而界面上什么异常都看不出来。
 */
function isHl(kind, id) {
  return highlight.value?.kind === kind && highlight.value.id === id
}

/** 现在是第几个命中（**1 起**；0 = 还没指）。 */
const hitIndex = computed(() => {
  const c = hitCursor.value
  if (!c) return 0
  const i = hitRows.value.findIndex(
    (r) => r.kind !== 'topic' && r.c.id === c.id
  )
  return i < 0 ? 0 : i + 1
})

/**
 * **一行 → 「哪一条」**（`{ kind:'cat'|'topic', id }`）。
 *
 * <p>⚠️ **只有这一份**：`moveHit`（↑↓ 跳命中）和「落到第一处命中」两处各写一遍
 * 那个三元表达式，改一处漏一处 ⇒ 目录行和知识点行的定位会走岔。
 *
 * @param {{kind:'cat'|'topic', c?:object, t?:object}} row 前序行
 * @returns {{kind:string, id:number}}
 */
function spotOf(row) {
  return row.kind === 'cat' ? { kind: 'cat', id: row.c.id } : { kind: 'topic', id: row.t.id }
}

/**
 * **↑ / ↓：在命中清单里移动**（定位 + 把游标挪过去）。
 *
 * <p>⚠️ **清单顺序 = 树上的顺序** ⇒ ↑↓ 走起来就是"按目录往下走"，
 * 不会出现"跳到一个看不见的地方"。
 *
 * <p>⚠️ **触发源有两个**（2026-11-09 用户：「目录树的那个定位框也跟这里一样
 * 复用上面那个就行」）：原来只有树里那个浮条的 ↑↓，现在**顶栏搜索框的 ↑↓**
 * 也管这一档 ⇒ 走 store 的 `treeSearchJump` 通道（写方是壳）。
 */
function moveHit(delta) {
  const list = hitRows.value
  if (!list.length) return
  const cur = hitIndex.value
  const next = cur === 0 ? (delta > 0 ? 0 : list.length - 1) : cur - 1 + delta
  if (next < 0 || next >= list.length) return
  const row = list[next]
  const spot = spotOf(row)
  hitCursor.value = spot
  focusSpot(spot)
}

/**
 * **顶栏搜索框的 ↑↓ 命令**（壳 → 树）⇒ 跳到上/下一处命中。
 *
 * <p>⚠️ **消费后置回 `''`**（边沿触发）⇒ 连点两次「↓」会跳两处，
 * 而不是停在原地（电平触发会一直跳）。
 */
watch(treeSearchJump, (dir) => {
  if (!dir) return
  if (dir === 'next') moveHit(1)
  else if (dir === 'prev') moveHit(-1)
  treeSearchJump.value = ''
})

/**
 * **命中计数报给壳**（顶栏搜索框显示「k/N」或「N 处」）。
 *
 * <p>⚠️ **只在"目录那档 + 有命中"时报**（正文那档是纯过滤，没有"第几处"这回事，
 * 树里也没有命中可言）⇒ 不该报的时候报 0，壳就不显示计数和 ↑↓。
 *
 * <p>⚠️ **卡片视图一律报 0**（2026-11-11 用户：「这个上下可跳的定位栏只能在目录树栏和
 * 正文详情栏才能展示 在卡片栏就不要展示了 因为卡片栏本身就已经根据这个搜索词进行筛选过了」）：
 * 卡片视图里这棵树是**藏着**的（外层 `v-show`，组件没卸）⇒ 它的命中行还在算，
 * 而"跳到树里第 2 处"跳的是一棵看不见的树。⇒ 按 `mode` 直接报 0。
 */
watch([hitRows, hitIndex, () => props.mode], () => {
  // 卡片刻里「第几处 / 共几处」这组数字本身就是错的（卡片是被筛过的结果集，
  // 不是"命中位置"）⇒ 连计数一起不报。
  const inTree = props.mode !== 'cards'
  treeSearchHits.value = inTree
    ? { total: hitRows.value.length, index: hitIndex.value }
    : { total: 0, index: 0 }
}, { immediate: true })

/**
 * **组件被销毁 ⇒ 计数归零**。
 *
 * <p>⚠️ 这棵树是**唯一**写 `treeSearchHits` 的地方（壳只读）⇒ 它没了却把值留在
 * store 里，顶栏就会一直挂着上一次树里的「1/2」+ 一对点不动的箭头
 * （2026-11-11 用户截图就是这一屏：卡片视图上还印着 `1/2`）。
 * 卸载路径 = 离开这本书 / 进详情页 / 切到没有树的那一屏。
 */
onUnmounted(() => {
  treeSearchHits.value = { total: 0, index: 0 }
})

/**
 * **命中浮条的拖动整套删了**（2026-11-09 用户：「目录树的那个定位框也跟这里一样
 * 复用上面那个就行」）—— 浮条（`hitsDrag` / `hitsOffset` / `onHitsPointerDown`）
 * 随那个容器一起撤了 ⇒ 没有可拖的东西了 ⇒ 这三样也一起删（别留死代码）。
 */
/**
 * 已经定位到哪一处了。
 *
 * <p>⚠️ **要记住，否则会一直重复定位**：数据一刷新（`allRows` 变了）就会重跑这段，
 * 而用户可能已经点开了别的知识点（`lastSpot` 已经被改过）⇒ 这时候再把他拽回第一处命中，
 * 就把"返回时回到刚才那一行"给冲掉了。
 * ⇒ 同一处命中不重复定位；换关键词（key 变了）才重新定位。
 */
const locatedKey = ref('')

// 有搜索词 ⇒ 落到第一处命中（展开祖先 + 高亮 + 滚到眼前）。
// ⚠️ **跟着 allRows 走**：换关键词后索引是异步回来的 ⇒ 行没变之前算不出命中。
watch([searchKw, allRows], () => {
  if (!searchKw.value) return
  const row = hitRows.value[0]
  if (!row) return
  const spot = spotOf(row)
  const key = `${searchKw.value}|${spot.kind}|${spot.id}`
  if (locatedKey.value === key) return
  locatedKey.value = key
  lastSpot.value = spot
  hitCursor.value = spot
  restoreSpot()
})

/** 把那一行滚到眼前（树里只有目录行 ⇒ 认 data-cat-id）。 */
function scrollToRow(spot) {
  const attr = spot.kind === 'cat' ? 'data-cat-id' : 'data-topic-id'
  const el = rootEl.value?.querySelector(`[${attr}="${spot.id}"]`)
  if (el?.scrollIntoView) el.scrollIntoView({ block: 'center', behavior: 'smooth' })
}

// 数据是异步来的（结构索引），回来时可能还没到 ⇒ 行数一变就再试一次。
watch(allRows, () => { restoreSpot() })

// **单条档位先拉一份**（树行的圆点/进度条数据源；loadOnce 有缓存与在途去重，重复调无害）。
onMounted(() => { ensureTopicLevelsLoaded() })

// **这一屏重新回到眼前**（从详情页返回）⇒ 回到刚才那一行。
// ⚠️ **触发点只有一个**：外层（页面）把 `active` 传下来。
// 树自己不用 `onActivated` —— **后代组件的 activated 钩子在不同 Vue 版本里
// 触发行为不一致**，页面级才知道"我回来了"（页面被 keep-alive 缓存着，切走不销毁）。
watch(() => props.active, (v) => {
  if (v) {
    ensureTopicLevelsLoaded()
    restoreSpot()
  }
})


// ==================== 知识点行的掌握度（圆点染色 + 右侧进度条） ====================
// 单条档位的数据源已随树里知识点行一起退场（2026-11-16 D7）——卡片上的档位签走列表字段。
// **没有评估的知识点行保持原样**（不染色不画条，用户口径）。

/** 档位（{level, mastery}）；没评估 = null */
</script>

<style scoped>
/* ⚠️ 2026-11-06 删掉整个 `.ct` 基样式块（§5.1 #1）。
   它的 6 条属性**逐条被下面的 `.ct--nav` 盖掉**（两者特异性相同、`.ct--nav` 在后）：
     background / border / border-radius / box-shadow / padding / overflow-y
   而 `.ct--nav` 是**无条件**绑在模板上的（`class="ct ct--nav"`，无 mode prop）⇒ 这层基样式
   从来没有生效过。
   ⚠️ **只删基样式、不动 `.ct--nav` 的选择器** —— 反过来做（把 nav 的值合并回基层、
   删掉 `--nav` 前缀）会让基层的权重掉下去、下面那些 `.ct--nav .ct__X` 全部失效，
   那是可见的视觉变更，不能在这种看不见效果的环境里做。 */
/* 树视图（nav）：这棵树是**主区的一部分**，不是浮在别处的一个弹层 ⇒
   没有阴影/边框/内边距，跟着主区一起滚，宽度铺满。

   ⚠️ 2026-11-04：这里原来还留着**浮层形态**（position: fixed + 贴侧边栏 + min-width）
   —— 那是**侧边栏级联弹层**留下的（那个弹层 2026-11-03 已经删了）⇒ 那套规则永远不生效，
   而"内嵌"那一档还得一条条把它盖掉。⇒ 现在 nav 只有内嵌这一种形态，
   直接写在这一条里；`inline` 属性和 `.ct--inline` 一起删掉。 */
.ct--nav {
  /* ⚠️ 2026-11-08 复刻 book-bak：**版心（1160 居中 + 34/44/60）在 AppLearnView 的
     `.learn` 上**（= 老稿 .main__inner）—— 这里只铺满版心，不再写一份 1160
     （同一份约束写两处，改一处就漂；此前 max-width:none 静默盖掉 1160 的坑同源）。 */
  width: 100%;
  margin: 0;
  background: transparent;
  border: none;
  border-radius: 0;
  box-shadow: none;
  padding: 0;
  overflow-y: visible;
}
/* **树身的横线撤了**（2026-11-08 对齐老稿）：标题行 ↔ 树身的分界由**章卡片自己**承担
   （每张卡自带边框），树身再画一条线就是第三层框。 */
.ct--nav .ct__body {
  border-top: none;
}
/* **行尾那格工具**：「展开 / 收起」+「⋯」。
   ⚠️ **它永远占位**（不显形只是 opacity 归零）⇒ 悬停时点线不会伸缩一下。
   —— 点线是这棵树的骨架，让它每悬停一次就伸缩一次，比多一个常驻箭头更难看。 */
.ct__tools {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-9);
  flex-shrink: 0;
}
/* ⚠️ **「展开 / 收起」文字按钮（.ct__toggle）整套撤了**（2026-11-08 用户：
   「三级的展开和二级的展开现在不一样 一个是图标一个文字 统一为图标」）——
   所有层级的展开/收起都走常驻 chevron（.ct__chev）。 */
.ct__node {
  display: flex;
  align-items: center;
  /* ⚠️ 2026-11-06 删掉两行死声明（§5.1 #1）：
     `gap: var(--sp-7)` 与 `font-size: var(--text-xs)`（12px）——
     两条都被后面 `.ct--nav .ct__node`（特异性 0,2,0 > 这里的 0,1,0）盖掉
     （那里是 gap: var(--sp-10) / font-size: var(--text-base)）。
     这层基样式只在 nav 形态下用，而 nav 形态**永远**带 `--nav` ⇒ 死。 */
  border-radius: var(--radius-sm);
  color: var(--cc-text-1);
  cursor: pointer;
  /* 展开箭头是**绝对定位**画在缩进那一格里的 ⇒ 行要是定位参照。 */
  position: relative;
  /* §4.8(d)：原来是 `transition: background 0.25s ease, box-shadow 0.25s ease` ——
     ① 0.25s 与全站 hover 过渡（--cc-transition = 0.15s）不一致 ⇒ 鼠标扫过一列
        目录行时底色拖尾明显更慢；
     ② **box-shadow 被列进过渡，而没有任何规则给 .ct__node 设 box-shadow**
        （只有 :794 那个死块里有）⇒ 过渡一个永远不存在的属性。
     ⇒ 只留 background，时长跟全站一致。 */
  transition: background 0.15s ease;
  /* 行尾工具（条数、「⋯」）**默认不显形**，悬停/聚焦这一行才露出来。
     —— 跟侧边栏目录行**同一套**（那边是 `.app-cats__item` 上的 `--row-tools`）。
     一屏几十行都挂着一个数字/按钮 = 几十个视觉噪点，把结构本身盖住了。
     ⚠️ 变量挂在**行**上而不是按钮上：自定义属性沿 DOM 继承，能穿过子组件的
     scoped 边界（`CategoryRowMenu` 的根元素拿不到本组件的 data-v）。 */
  --row-tools: 0;
  /* 目录行是拖拽源（挪目录换父级），禁掉文本选择。
     少了 user-select，按下拖动时浏览器当场开始选文字，树里几行目录名会被染成蓝块
     （2026-10-07 实机）。少了 -webkit-touch-callout，iOS 长按还会先弹系统「复制/查找」
     菜单，拖拽根本起不来。
     **touch-action 不在这里设**：树本身要能滚，写死等于把滚动禁了。 */
  user-select: none;
  -webkit-user-select: none;
  -webkit-touch-callout: none;
}
/* **目录行能拖**（拖目录换父级）。
 * ⚠️ **光标跟全站一致：可点的就是手型**（2026-11-05 晚用户拍板「其它地方都是遇到能点击的
 * 会变成一个手指之类的图标，这里也还是保持一样」⇒ 行、行的「展开/收起」、树内的「⋯」
 * 三处都是 `pointer`）。
 * —— 中间试过给整行 `default`（普通箭头）想"统一"，**撤回**：全站其它可点的地方都是手型，
 * 树里单独一个箭头反而像"这行点不了"。
 * ⚠️ **拖起来的那一刻才给 grabbing**：`body.dnd-active` 是在位移过了阈值之后才挂上的
 * ⇒ 平时是手型，**真拖起来了**才变抓手（跟系统拖文件一致）。 */
body.dnd-active .ct--nav .ct__node {
  cursor: grabbing;
}
.ct__node:hover {
  /* 行尾工具（条数、「⋯」）显形。跟侧边栏目录行同一套做法（背景色由下面 .ct--nav .ct__node:hover 定）。 */
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
  /* ⚠️ 2026-11-08 改回 `flex: 1`（老稿 tch__name / tsec__name / ttopic__name 都是 flex:1）：
     卡片形态下条数本来就贴在卡片右缘（卡片把行宽收住了），不存在"眼睛横跨一整屏"
     的问题；点线引线已随卡片版式撤掉，名字不吃满余量的话行尾就会空一截。
     名字过长时靠 ellipsis 截断（min-width: 0 让它能被压缩）。 */
  flex: 1 1 auto;
  min-width: 0;
}
/* 名字里的**文字**（`.ct__name` 现在只是外壳）：省略号挂在它身上。 */
.ct__text {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
/* **目录行**：深色、稍重 —— 它是"结构"。 */
.ct__node--cat .ct__name {
  font-weight: 500;
  color: var(--cc-text-1);
  /* 目录名**自带序号**（「6.1 网络安全基础」）⇒ 数字用等宽字形，
     "6.1" 和 "6.10" 的点才对得齐（默认比例下 "10" 比 "1" 宽，点会错开一格）。
     ⚠️ 只给**目录名**：知识点名是正文标题，不带序号。 */
  font-variant-numeric: tabular-nums;
}
/* ⚠️ 2026-11-06 删掉 `.ct__node--topic` / `.ct__node--topic .ct__name` /
   `.ct__node--topic:hover .ct__name` 这三条基样式（§5.1 #1）：三条都被后面
   `.ct--nav .ct__node--topic .ct__name`（特异性 0,3,0 / 0,4,0 > 这里的 0,2,0 / 0,3,0）
   盖掉，且值相同 ⇒ 纯死代码。
   顺带说明 §5.3 #29 那条注释不符也一起消失：它说的是"知识点行浅一档、小一号"，
   而这层基样式写的是 12px(结构) / 14px(内容) ⇒ 内容反而大一号。
   **实际生效的是 nav 层**：结构 --text-base(15) / 内容 --text-sm(13) ⇒ 内容**小**一号，
   跟注释说的一致。注释描述的是 nav 层，写错了的是那层死代码。
   ⚠️ **层级只走字号一条轴**：颜色一律 --cc-text-1（2026-11-05 晚用户拍板
   「二级目录等多级目录的颜色还是改回来吧」），知识点叶子是唯一的例外
   （--cc-text-2，见下面 nav 层那条）。 */
/* **根行**（大一号、加粗）：**没有了**。
   树视图的根行 2026-11-05 撤了（书名进了顶部标题行，再画一遍就是两行标题），
   拖拽落点浮层 2026-11-05 也撤了（知识点不再能拖）⇒ 整棵树里再没有"根行"这个角色。 */
/* 「⋯」**hover 才显形**：跟条数共用行上的 --row-tools —— 两处各写一套
   opacity 规则的话，改的时候要改两处，而且「谁在后面谁赢」很难看出来。
   opacity 而不是 display —— 按钮始终占位，hover 时不跳。 */
.ct__menu {
  opacity: var(--row-tools, 1);
  transition: opacity 0.15s ease;
  flex-shrink: 0;
}
/* **命中的字标蓝**（不是整行标蓝）—— 用户 2026-11-05：
   「我让你标蓝是标蓝命中的文字 你把他对应的目录标蓝」⇒ 他要的是**那几个字**是蓝的。
   ⚠️ **用 <mark> 但不给浏览器默认那套黄底**：那玩意儿太抢眼，
   这里只要"这几个字是蓝的"。
   ⚠️ **常显**，不靠 hover（用户：「符合条件的要标记出来 注意不是hover」）。 */
.ct__hl {
  background: none;
  color: var(--cc-primary);
  font-weight: 600;
  padding: 0;
}
/* **命中游标指着的当前那条**（命中浮条的 ↑↓ 在动它）。
   ⚠️ **常显，不淡出** —— 它是"现在在这儿"的指示，不是"刚刚定位到"的一次性闪一下。
   ⚠️ **它比 is-hit 强一档**（加底色 + 加粗）⇒ 命中的目录里哪一条是当前这条，分得清。 */
.ct__node.is-cursor {
  background: var(--cc-primary-soft);
}
.ct__node.is-cursor .ct__name {
  color: var(--cc-primary);
  font-weight: 600;
}
/* **返回时定位到的那一行**：亮一下（几秒后淡出）。
   ⚠️ 用**行底色**，不加边框也不加竖条 —— 加边框会把行撑大，
   整棵树在那一刻抖一下（用户正在看的就是那一行，抖一下最难受）。 */
.ct__node.is-hl {
  background: var(--cc-primary-soft);
}
/* **当前目录**（面包屑当前截 = 卡片内容源，2026-11-16 D7）：点行设当前。
   常显不淡出（它是状态，不是一次性闪现）；名字染主色。 */
.ct--nav .ct__node.is-cur {
  background: var(--cc-primary-soft);
}
.ct--nav .ct__node.is-cur .ct__name {
  color: var(--cc-primary);
}
/* 触屏没有 hover ⇒ 常显（不然那些数字/按钮永远看不见）。
   跟 TopicCard 的「⋯」按钮同一口径（那边用 @media (hover: hover) 反着写，
   这里用 (hover: none) 正着写，效果一样）。 */
@media (hover: none) {
  .ct__node {
    --row-tools: 1;
  }
}
/* ============ 树视图：一本书的目录 ============
 *
 * 2026-11-04 用户给了一张参考图（纸质书的目录页）：**安静的灰、宽松的行距、
 * 名字与页码之间一条点状引线、页码右对齐**。
 *
 * 2026-11-05 晚再给了一张「改后效果」图 + 一张改动方向表 ⇒ 这一段按那张图重排：
 *   ① 行首那颗 `>/∨` 箭头撤了，换成**悬停才现形的「展开 / 收起」文字**（在行尾那格）；
 *   ② 层级只走**字号**一条轴（章 15 / 节 14 / 知识点 13），颜色一律深色（2026-11-05 晚又改回来了）；
 *   ③ 条数改成**右对齐的一列**，中间一条点线从名字引到它；
 *   ④ 「总览」收进顶部标题行（**2026-11-10 晚又改掉**：一级目录直挂的知识点
 *      统一进树身上方那块「本书知识点」，标题行只留统计 + ⋯）；
 *   ⑤ 行操作（⋯、展开/收起）悬停才出现；
 *   ⑥ 内容限宽 --w-toc 居中；
 *   ⑦ 顶部加「展开全部 / 收起全部 / 过滤」三个"一次定位"的工具。
 *
 * ⚠️ **引线是 `.ct__leader`（名字和右栏之间那段），不是给名字加 border-bottom** ——
 * 那样引线只到名字的宽度，条数还是飘在别处。
 */
/* ⚠️ 2026-11-08 代码级复刻 book-bak.html：以下树身的间距 / 字号 / 圆角都是**老稿原值**
   （设计真相存档，不走 --text-* / --sp-* 阶梯 —— 复刻优先，注释即出处）。
   基础行（卡片身里的节行 / 知识点行）：左右留 8px（hover 高亮不贴卡片边，老稿 margin 0 8px）。 */
.ct--nav .ct__node {
  padding: 9px 18px;
  gap: 10px;
  margin: 0 8px;
  line-height: 1.6;   /* 老稿 body 的 1.6 —— 行高是"行高呼吸感"的来源（14px→22.4px） */
}
/* ⚠️ **点状引线（.ct__leader）整套撤了**（2026-11-08 对齐老稿）：
   老稿（book-bak.html）里没有引线 —— 名字 flex:1 吃满、条数贴行尾。
   引线是 2026-11-04 #749 书籍化排版的骨架，被这一版章卡片版式取代。 */
/* **条数**：右对齐、淡灰、等宽数字的一列（像书的页码）。
   ⚠️ **不再 hover 才显形**（那是侧边栏的做法：一屏几十行都挂数字是噪点；
   这里是目录页，页码本来就是这一栏的内容）。
   ⚠️ **要定宽**：这一列定宽，右栏那格（展开/收起、⋯）才落在同一个 x 上，
   点线的收尾也才是一条直线（不 hover 的行和 hover 的行一样齐）。 */
.ct--nav .ct__count {
  /* 条数胶囊（2026-11-08 侧栏精修同款）：安静的右对齐容器，hover 加深。
     token 与侧边栏同一族（--cc-side-cnt-*，themes.css 按主题给值）。 */
  --cnt-bg: var(--cc-side-cnt-bg);
  --cnt-fg: var(--cc-text-4);
  background: var(--cnt-bg);
  color: var(--cnt-fg);
  border-radius: 999px;
  padding: 0 6px;
  line-height: 16px;
  flex: 0 0 auto;
  transition: background var(--t-fast, 140ms) var(--ease, ease), color var(--t-fast, 140ms) var(--ease, ease);
}
.ct--nav .ct__node:hover .ct__count {
  --cnt-bg: var(--cc-side-cnt-bg-hover);
  --cnt-fg: var(--cc-text-3);
}
/* **层级只走字号一条轴**（2026-11-05 晚的改动方向表）：
 * 章 15px、节 14px（更深一级同号）、知识点 13px 最浅。
 * ⚠️ **颜色一律 `--cc-text-1`、字重一律 500**（2026-11-05 晚用户：「二级目录等多级目录的
 * 颜色还是改回来吧」）—— 之前按层级分深浅（节 14px 浅一档、更深再浅）⇒ 越深的目录整片发灰，
 * 看着像"次要内容"，其实只是层级更深。
 * ⇒ 层级只剩**字号 + 缩进**两条轴，颜色跟改动前完全一样（`.ct__node--cat .ct__name` 那条）。
 * ⚠️ **d1（章）不写规则**：它跟行上的默认字号同号（--text-base），写了就是第二份。 */
.ct--nav .ct__node--d2 .ct__name,
.ct--nav .ct__node--d3 .ct__name {
  font-size: var(--text-md);
}
/* 知识点叶子：更浅更小的一层，**hover 变黑** —— 它是能点进去的一行，得有"可点"的反馈。
 * ⚠️ 2026-11-06 改（§4.3）：颜色 --cc-text-3（#a0a5ad，13px）只有 **2.48:1**，
 * 低于 AA 4.5:1 —— 而 :966-970 的注释为了 .ct__count **已经**判定"text-4 几乎看不见、
 * text-3 也偏灰"并把它提到了 text-2 ⇒ 同一条判断没有应用到树上**数量最多、
 * 最需要读**的那批文字（知识点叶子名）。
 * ⇒ 提到 --cc-text-2（6.14:1）；**层级差改由已有的字号（13 vs 15）与字重（400 vs 500）承担，
 * 不再靠"更灰"**（与 §4.3 的口径一致）。 */
.ct--nav .ct__node--topic .ct__name {
  font-size: var(--text-sm);
  font-weight: 400;
  color: var(--cc-text-2);
}
.ct--nav .ct__node--topic:hover .ct__name {
  color: var(--cc-text-1);
}
/* 悬停底色：参考图选中行是**极浅的灰**，不是蓝 —— 跟着换成中性灰。 */
.ct--nav .ct__node:hover {
  background: var(--cc-surface-2);
}

/* ============ 章卡片（2026-11-08 代码级复刻 book-bak.html 的 .tch 系列） ============
 *
 * 每条值都照抄老稿（.tch / .tch-row / .tch__num / .tch__name / .tch__cnt /
 * .tch__chev / .tch__body / .tsec-* / .ttopic-*），颜色换成本项目的主题 token
 * （素雅主题下与老稿逐值相同；其他主题走自己的配色）。
 */
.ct__card {
  background: var(--cc-surface);            /* --card */
  border: 1px solid var(--cc-border-card);  /* --line */
  border-radius: 14px;                      /* --r-lg */
  box-shadow: var(--cc-shadow-card);        /* --shadow-card */
  overflow: hidden;
  margin-bottom: 10px;                      /* .tree 的 gap: 10px */
  transition: border-color var(--t-fast, 140ms) var(--ease, ease), box-shadow var(--t-fast, 140ms) var(--ease, ease);
}
.ct__card:hover {
  border-color: var(--cc-card-hover-bd, var(--cc-primary-border));  /* --card-hover-bd，themes.css 按主题给值 */
}
/* 卡片身：与卡片头之间一条线（老稿 .tch__body 的 border-top，--line-soft）。
   ⚠️ **没有上下 padding**（老稿就是 0）—— 首行紧贴分隔线、末行距卡底 2px（行自己的 margin）。 */
.ct__card-body {
  border-top: 1px solid var(--cc-border);   /* --line-soft */
  padding: 0;
  /* 老稿 .tsec-row / .ttopic 的圆角是 8px（--radius-sm 是 6，差一档肉眼可见）。 */
  border-radius: 0;
}
.ct__card-body .ct__node {
  border-radius: 8px;
}
/* 章行 = 卡片头（老稿 .tch-row：padding 14px 18px，gap 12px；rowStyle 对 depth1 返回 0）。 */
.ct--nav .ct__node--chapter {
  padding: 14px 18px;
  gap: 12px;
  margin: 0;
}
/* 章名（老稿 .tch__name：600 15.5px/1.4 衬线、ink、title-tracking）。
   ⚠️ 2026-11-13/14 曾按误读改小（#932/#944，用户划的是顶栏面包屑不是这里）⇒ 2026-11-14 回退
   到老稿复刻原值——「代码级复刻 book-bak」是用户 11-08 拍板的定稿，别再动。 */
.ct__node--chapter .ct__name {
  font-family: var(--font-display);
  font-size: 15.5px;
  font-weight: 600;
  line-height: 1.4;
  color: var(--cc-text-1);                  /* --ink */
  letter-spacing: 0.02em;                   /* --title-tracking */
}
/* 章行 hover：底色 --card-hover（surface-2）。 */
.ct--nav .ct__node--chapter:hover {
  background: var(--cc-surface-2);
}
/* **序号徽章**（老稿 .tch__num：28×28、radius 8、accent 描边 + accent 数字、600 13px 数字字体）。 */
.ct__num {
  width: 28px;
  height: 28px;
  flex: 0 0 28px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  border: 1px solid var(--cc-primary-border);   /* --accent-bd */
  color: var(--cc-primary);                     /* --accent-text */
  font-family: var(--font-display);             /* --font-num = display */
  font-size: 13px;
  font-weight: 600;
  line-height: 1;
  font-variant-numeric: tabular-nums;
  background: none;
}
/* 搜索词命中序号本身：徽章标主题色底（名字部分照常走 parts 标字）。 */
.ct__num.is-numhit,
.ct__secnum.is-numhit {
  color: var(--cc-primary);
  background: var(--cc-primary-soft);
}
.ct--nav .ct__card-body .ct__node--cat {
  padding-top: 11px;
  padding-bottom: 11px;
}
/* 折线（老稿 .tsec-row::before）：从卡片左缘垂下来、圆角拐进节行 —— 只给二级目录
   （老稿只有一层节；更深的行不画，避免导轨复活）。 */
.ct--nav .ct__card-body .ct__node--d2 {
  position: relative;
}
.ct--nav .ct__card-body .ct__node--d2::before {
  content: '';
  position: absolute;
  left: -6px;
  top: 0;
  bottom: 0;
  width: 14px;
  border-left: 1px solid var(--cc-border);          /* --line */
  border-bottom: 1px solid var(--cc-border);
  border-bottom-left-radius: 8px;
}
/* **节号**（老稿 .tsec__num：500 12.5px 数字字体、accent、min-width 26）。 */
.ct__secnum {
  flex: 0 0 auto;
  min-width: 26px;
  color: var(--cc-primary);                 /* --accent-text */
  font-family: var(--font-display);         /* --font-num */
  font-size: 12.5px;
  font-weight: 500;
  line-height: 1;
  font-variant-numeric: tabular-nums;
  align-self: center;
}
/* 节名（老稿 .tsec__name：14.5px、**400 / --text**（= text-2，不加粗、不变深）——
   层级全靠节号的颜色和缩进表达，名字本身跟知识点同档）。 */
.ct__card-body .ct__node--cat > .ct__name {
  font-size: 14.5px;
  font-weight: 400;
  color: var(--cc-text-2);
}
/* **知识点行**（老稿 .ttopic：padding 9px 18px 9px 52px、下距 2px；缩进由 rowStyle 给）。 */
.ct--nav .ct__card-body .ct__node--topic {
  padding-top: 9px;
  padding-bottom: 9px;
  margin-bottom: 2px;
}
/* **知识点行的圆点**（老稿 .ttopic__dot：5px 圆点、faint）。 */
.ct__dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--cc-text-4);             /* --faint */
  flex: 0 0 auto;
}
/* 知识点行的掌握度进度条（2026-11-14）：只对有评估的行渲染（v-if），
   填充值 = 该条的掌握度 M；尺寸与侧栏 MasteryBar 同档（32×3）。
   margin-left:auto 推到行尾——与节行右侧的聚合条对齐。 */
.ct__mbar {
  margin-left: auto;
  flex: none;
  width: 32px;
  height: 3px;
  border-radius: var(--radius-pill);
  background: var(--cc-surface-3);
  overflow: hidden;
}
.ct__mbar i {
  display: block;
  height: 100%;
  border-radius: var(--radius-pill);
  /* 填充色 = 档位色（行内 style 绑 mbarColor），这里只留形状兜底 */
}
/* 知识点名（老稿 .ttopic__name：14px、text）。 */
.ct__card-body .ct__node--topic > .ct__name {
  font-size: 14px;
}
/* **「未写正文」pill**（老稿 .ttopic__skel：10.5px、muted、line 边框圆角药丸）。
   ⚠️ 数据驱动：索引条目 hasContent=false 才画。 */
.ct__skel {
  flex: 0 0 auto;
  font-size: 10.5px;
  color: var(--cc-text-3);                  /* --muted */
  border: 1px solid var(--cc-border-strong);/* --line */
  border-radius: 999px;
  padding: 1px 8px;
  white-space: nowrap;
}
/* 知识点行悬停：accent-faint 底 + 名字变 accent（老稿 ttopic:hover）。 */
.ct--nav .ct__node--topic:hover {
  background: var(--cc-primary-soft);
}
.ct--nav .ct__node--topic:hover .ct__name {
  color: var(--cc-primary);
}
/* 节行悬停（老稿 .tsec-row:hover = side-item-hover 中性灰 + 名字变 ink）。 */
.ct--nav .ct__card-body .ct__node--cat:hover {
  background: var(--cc-surface-2);
}
.ct--nav .ct__card-body .ct__node--cat:hover > .ct__name {
  color: var(--cc-text-1);
}
/* 卡片身里的条数（老稿 .tsec__cnt：11.5px 纯文字 --side-cnt，没有药丸底 —— 药丸是章行的）。 */
.ct__card-body .ct__count {
  background: none;
  padding: 0;
  font-size: 11.5px;
  color: var(--cc-side-cnt-fg, var(--cc-text-3));   /* --side-cnt */
}
/* 章行条数 = 老稿 .tch__cnt：12px 药丸（padding 1px 8px），色 --side-cnt。
   ⚠️ 基础 .ct__count 规则给的是 text-4 + 0 6px —— 章行按老稿单独覆写。 */
.ct--nav .ct__node--chapter .ct__count {
  font-size: 12px;
  padding: 1px 8px;
  line-height: 1.6;
  color: var(--cc-side-cnt-fg, var(--cc-text-3));
}

/* ============ 顶部标题行（2026-11-08 代码级复刻 book-bak.html 的 .tree-head） ============
 *
 * 两行式：左块 = 大标题（600 26px/1.25 衬线）+ meta 行（13px，数字 accent）；
 * 右块 = 分段式「目录树 | 卡片」（.view-toggle）+ 展开全部 / 收起全部。
 * align-items: flex-end —— 右侧切换贴着 meta 行的基线（老稿就是这么排的）。
 */
/* ============ 卡片模式的内容盒（2026-11-08） ============
 *
 * 里面是**外层的卡片视图块**（AppLearnView 经插槽放进来的：筛选条 + 分页网格），
 * 这里只管盒子本身的显隐与留白 —— 不再有任何自制卡片样式（上一版的教训：
 * 两套卡片逻辑，全量拉正文，慢一拍还违反收敛）。
 */
.ct__cards {
  margin-top: var(--sp-6);
}
/* ============ 行尾两颗图标按钮：同一套规格（2026-11-13） ============
 * ⚠️ 原来按按钮各写一份：展开键 16×16 / 透明 / hover 只把灰调深一档，
 *    ⋮ 24×24 / 圆角 / hover 主色浅底 ⇒ 挨在一起像两代控件；16×16 的命中区
 *    只有 ⋮ 的 44%，用户报「按起来不方便、点不到就展开不到」。
 * ⚠️ 旧选择器写成 `.ct-dots .row-menu__btn`（**祖孙**关系），但这两个 class
 *    在 RowMenu 里是同一个 button ⇒ 那条 22×22 规则从来没命中过，一直靠
 *    RowMenu 的基础样式撑着（hover 恰好同色，所以没露馅）。⇒ 改成同元素。
 * ⇒ 一条选择器组同时压住两颗（含子组件 scoped 的 .row-menu__btn：
 *    本规则特异性高于它）。规格对齐全站图标按钮那一档（--ctl-sm 28 /
 *    --radius-sm 6 / --cc-icon / 主操作 hover 档，见 kb.css）；没直接并
 *    `.kb-icon-btn` 类，是因为全局类会被 RowMenu scoped 的 padding / radius /
 *    color 盖掉半边，两颗又会不一样。
 * ⚠️ **显隐不是风格**：⋮ 仍由 --row-tools 控制 hover 才显形（一屏几十行降噪），
 *    chevron 是唯一的展开出口 ⇒ 常显。 */
.ct--nav :deep(.row-menu__btn.ct-dots) {
  width: var(--ctl-sm, 28px);
  height: var(--ctl-sm, 28px);
  padding: 0;
  border: none;
  border-radius: var(--radius-sm, 6px);
  /* 常态给一层和条数药丸同族的浅底 ⇒ 不 hover 也看得出「这里是一颗按钮」。
     原来只有一个 16px 淡灰箭头浮在白底上，用户瞄不准就是这个。 */
  background: var(--cc-side-cnt-bg);
  color: var(--cc-icon, var(--cc-text-3));
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  cursor: pointer;
  transition: background var(--t-fast, 140ms) var(--ease, ease),
              color var(--t-fast, 140ms) var(--ease, ease),
              opacity 0.15s ease;
}
.ct--nav :deep(.row-menu__btn.ct-dots:hover),
.ct--nav :deep(.row-menu__btn.ct-dots:focus-visible) {
  color: var(--cc-primary);
  background: var(--cc-primary-soft);
}
.ct--nav :deep(.row-menu__btn.ct-dots:focus-visible) {
  outline: 2px solid var(--cc-primary);
  outline-offset: 1px;
}
/* ⚠️ **这里原来有 `.ct__filter`**（树标题行里那个「过滤知识点...」输入框的样式）——
   2026-11-05 晚那个框删了（并进顶栏那个公共搜索）⇒ 样式一起删，别留一条没人用的规则。 */
</style>
