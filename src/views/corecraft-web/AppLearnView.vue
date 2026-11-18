<template>
  <div class="learn">
    <!-- 这里原来还有一条录入框（MilkdownEditor）。本页的职能是**浏览**：
         目录筛选 / 时间筛选 / 类型筛选 / 搜索 / 卡片网格，录入框只是顶部一条，
         名字叫「随手记」名不副实。录入已统一收到首页，这一页改成纯浏览页，
         侧边菜单同步改名「知识点」。 -->

    <!-- 筛选条：**只在卡片视图里**。
         树视图那一屏回答的是"这本书里有什么"（结构）—— 筛知识点、考这个目录对它没意义，
         那些操作在卡片视图那一层。

         ⚠️ **面包屑 2026-11-08 移到 AppLayout 顶栏**（用户：「把下方那个 一级目录 ->
         二级目录 、、、的那个索引页移动到上面去 就在这个一级目录名字之后」）
         ⇒ 这一行只剩筛选条，"我在哪"由顶栏那条「一级目录/二级目录/…」回答。 -->
    <!-- 卡片视图顶部净空，仅「待归类」屏保留批量工具条 -->
    <div v-if="!isBookPage && showBatch && (pageRecords.length > 0 || batchMode)" class="cc-toprow">
      <TopicFilters
        :show-batch="showBatch"
        :batch-mode="batchMode"
        :batch-count="selectedTopics.length"
        :page-count="pageRecords.length"
        :all-selected="allSelected"
        @toggle-batch="toggleBatch"
        @select-all="selectAllOnPage"
        @batch-categorize="onBatchCategorize"
        @batch-delete="onBatchDelete"
      />
    </div>

    <BookToc
      v-if="renderTree"
      v-show="isBookPage"
      :category="treeViewRoot"
      :current="currentId"
      :active="pageActive && isBookPage && !treeCards"
      :mode="treeCards ? 'cards' : 'tree'"
      @pick="onTreePick"
      @pick-topic="goDetail"
      @structure-done="onTreeStructureDone"
    />

    <template v-if="!isBookPage || treeCards">
      <!-- 平铺。**网格形态**（没有选中目录）：这里显示的是当前筛选条件下的知识点。
           ⚠️ **加载中不画旧内容**：过了 160ms 就换成**同构骨架**。
           旧列表杵在那儿的时候，"没变"和"正在变"长得一模一样 ——
           用户看到的就是"点了没反应，过几秒突然换了"。

           ⚠️ **前 160ms 保留旧内容**（2026-11-13 统一加载层）：
           请求很快时旧内容直接换成新内容（不闪骨架）；
           超过 160ms 才换成骨架（形状对着真实卡片 ⇒ 高度接近，不跳版）。
           单纯"要么旧内容要么骨架"会在快请求时闪一下骨架 —— 那比闪图标更刺眼。 -->
      <KbLoading :loading="loading">
        <template #loading>
          <div class="grid learn__skeleton">
            <KbSkeletonCard v-for="i in 6" :key="i" />
          </div>
        </template>

        <!-- 出错：拉不到的时候摆出来。错误详情由 request.js 弹（那里是全站唯一的
             提示出口），这里只摆状态，不重复弹一遍。 -->
        <div v-if="loadFailed" class="kb-center">
          <p class="kb-error">加载失败</p>
          <el-button @click="loadPage">重试</el-button>
        </div>

        <!-- ⚠️ **学习掌握概览在卡片网格上面**（2026-11-12 方案 C）：
             它回答的是"这一屏里学过的东西掌握得怎么样"——
             档位分布 + 「已检验 N / M 条学习」。
             ⚠️ **在 `.grid` 外面** —— 概览是整屏一条，不是"一张卡片"，
             放进去会被当成网格单元（宽度只占一列、行高还跟卡片对齐）。
             ⚠️ **没有已检验内容时整块不渲染**（组件内部判断），
             全是笔记的目录就不会多出这条空条。 -->
        <div v-else-if="pageRecords.length">
          <MasteryOverview
            class="learn__overview"
            :row="overviewRow"
          />
          <div class="grid">
            <TopicCard
              v-for="t in pageRecords"
              :key="t.id"
              :topic="t"
              :selectable="batchMode"
              :selected="batchMode && selectedIds.has(t.id)"
              @click="onCardClick(t)"
              @delete="handleDelete"
              @categorize="onCategorize"
              @mastery-scope="onMasteryScope"
            />
          </div>
        </div>
        <!-- 列表为空时**什么都不画**：上面已经有筛选条件（目录 / 搜索词 / 类型），
             空白是自解释的。⚠️ 以前这里写着「没有待归类的知识点」「这个目录还是空的」
             「没搜到」「还没有知识点」四句 —— 加载中的那几秒它就在说"没有"，那是句假话
             （用户明明有）；现在不说了，也就没有假话。 -->
      </KbLoading>

      <!-- 分页。只有超过一页才画：一页装得下的时候画个"1"是噪音。
           ⚠️ §4.8(a)：加载中时 pageTotal 还是**上一次**的旧值 ⇒ 原来转圈和
           分页条同屏、用户还能点「下一页」（点到的是旧数据那一页）。
           条件加上 !loading，两者互斥。 -->
      <div v-if="!loading && pageTotal > pageSize" class="pager">
        <el-pagination
          layout="prev, pager, next"
          background
          :total="pageTotal"
          :page-size="pageSize"
          :current-page="page"
          @current-change="onPageChange"
        />
      </div>
    </template>

    <!-- ⚠️ **AI 归类弹窗已删**（2026-11-16 D2 AI 归类退役）：全树自动分类这条能力下线 ⇒
         归类改为**手动**（卡片「⋯ → 归类到目录」/ 批量「归类到目录」都还在）。 -->

    <!-- 手动归类：**一个**弹窗实例（不是每张卡各挂一个）。
         卡片只发 `categorize` 事件（见 TopicCard 的 ITEMS）——
         ⚠️ 2026-11-05 撤掉的是**拖动归类**，手动选目录这条路 2026-11-06 补回来
         （用户：「我之前让删除的是 拖动卡片归类但是手动选择这个还是要有」）。 -->
    <CategorizeDialog
      v-model="categorizeOpen"
      :topic="categorizeTopic"
      :topics="batchCategorizeTopics"
      :categories="allCategories"
      @done="onCategorized"
    />

    <!-- 掌握度参与设置：**一个**弹窗实例（同归类弹窗的规矩：不是每张卡挂一个）。
         卡片「⋯ → 掌握度设置」只发事件，真正开弹窗的是这里。 -->
    <MasteryScopeDialog
      v-model="masteryScopeOpen"
      :topic="masteryScopeTopic"
    />

    <!-- 目录档：**这一轮一个书都没命中** ⇒ 这一屏是空的。
         ⚠️ **不能还挂着上一轮的那棵树**（用户 2026-11-05：「为什么目录树里还是上一次的那颗树
         也应该清空啊」）⇒ 树不画，只留一句短话。
         ⚠️ §4.7(p)：用 .kb-center--bare 变体 —— .kb-center 的 padding 是
         var(--sp-16)(40px) 上下，为"图标 + 一句话"两行内容设计的，
         "没有匹配"四个字孤零零漂在 80px 高的空白中间。 -->
    <p v-if="isBookPage && !renderTree" class="kb-center kb-center--bare">没有匹配</p>
  </div>
</template>

<script setup>
defineOptions({ name: 'CorecraftWebAppLearn' })
import { ref, computed, onMounted, onActivated, onDeactivated, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ensureTopicIndexLoaded, ensureCategoriesLoaded,
  loadTopicPage, getTopicIndex, getCategory, getCategories,
  getCategorySubtreeIds, getDataVersion, getCategoryCountMap,
  getChildCategories, getCategoryPath, ROOT_CATEGORY, categoryScopeFromQuery,
  confirmDeleteTopic, confirmDeleteTopics, UNCATEGORIZED, ALL,
  getCategoryMastery, ensureCategoryMasteryLoaded,
  refreshTopicIndexes, bumpDataVersion, getFullTopicIndex
} from './useCorecraftWebStore'
import { keywordFromRoute, rangeFromRoute } from '../../utils/routeQuery'
import { rangeBounds } from '../../utils/timeRange'
import { canUseTree } from '../../utils/categoryNav'
import { visibleRootCategoriesOf } from '../../utils/categoryTree'
import {
  searchModeFromRoute, searchScopeOf, isCatMode, matchNameFromRoute, SEARCH_MODE_CAT
} from '../../utils/searchMode'
import TopicFilters from '../../components/corecraft-web/TopicFilters.vue'
import KbLoading from '../../components/corecraft-web/KbLoading.vue'
import KbSkeletonCard from '../../components/corecraft-web/KbSkeletonCard.vue'
import BookToc from './BookToc.vue'
import TopicCard from './TopicCard.vue'
import MasteryOverview from './MasteryOverview.vue'
import CategorizeDialog from './CategorizeDialog.vue'
import MasteryScopeDialog from './MasteryScopeDialog.vue'

const route = useRoute()
const router = useRouter()

/**
 * 现在是不是正停在列表页。
 *
 * <p>**这一页的筛选条件只在这一页开着的时候才算数**：路由上的 `?category=` / `?q=`
 * 离开这一页就没了（详情页的 URL 里没有它们），
 * 而"空"会被当成"用户把筛选清掉了" ⇒ 列表在后台被重拉成「全部」那份，
 * 回来时先闪一下「全部」，再跳回该目录的那份（实测就是这样）。
 *
 * <p>以前这里还有一个 `viewActive` 标志（onDeactivated 置否），
 * 说的是同一件事 —— 两个名字两份判据，路由一换就可能一边说开着一边说关着。
 * **只留路由这一个判据**。
 */
const onLearnRoute = computed(() => route.name === 'AppLearn')

// 类型筛选。**现在进 URL**（`?type=`）—— 原来只存本地 ref。
//
// ⚠️ **为什么改**：详情页要能还原"这次搜索的命中集"（浮条上的「第 3/52 个」和 ↑↓
// 跳的那一条，得是**用户在卡片列表里看到的同一条**）⇒ 命中集得连类型条件一起还原。
// 类型筛选只有这一个入口（列表页那个下拉）⇒ 进 URL 不会多出第二个写方。

// 目录筛选的真相源是 URL。
//
// ⚠️ **不抄一份本地 ref**：以前是 `ref` + 一个 watch 同步，**两处可写** ——
// 药丸改了本地值但地址栏还停在 ?category=1，再点同一个侧边栏目录时路由无变化、
// watch 不触发，界面再也回不去。**URL 就是唯一真相**，读它不需要再抄一份。
//
// ⚠️ **范围有级**（2026-11-05 晚）：`?category=`（某个目录，更细）优先；
// **没有它、而有 `?tree=`（哪本书）时 ⇒ 范围 = 那本书**（用户拍板：正文档那档
// 点侧边栏的书 = 卡片收窄到那本书；「默认没有选择一级目录的时候是直接展示
// 所有符合的卡片的」）⇒ 两档（正文 / 目录）**共用同一个范围参数**、**切档不丢范围**。
//
// ⚠️ **这条推导只有一份**（store 的 `categoryScopeFromQuery`）：详情页算"我是第几个命中"
// 用的是同一个函数 —— 各写一遍的话两边算出的命中集不是一批 ⇒ 序号错位，
// **而界面上什么异常都看不到**。
const categoryFilter = computed(() => categoryScopeFromQuery(route.query))

/**
 * **URL 上"正在看哪个目录"**（`?category=` 里的那个真实目录 id），没有就是 null。
 *
 * <p>⚠️ **不能读 `categoryFilter`**：那个是"这一屏的范围"，没有 `?category=` 时
 * 会退成 `?tree=` 那本书的 id ⇒ 一进书页就被当成"在看这本书这个目录"，
 * 内容盒子直接变成卡片（书页的树永远出不来）。
 *
 * <p>`?category=uncategorized`（待归类）不是目录，算 null。
 */
const currentCatId = computed(() => {
  const c = route.query.category
  if (c == null || c === UNCATEGORIZED) return null
  // query 的值可能是数组（手改的 URL：`?category=a&category=b`）⇒ 取第一个。
  const id = Number(Array.isArray(c) ? c[0] : c)
  return Number.isFinite(id) && id > 0 ? id : null
})
// 关键词：**直接从 URL 读**，不是 ref、也没有 watch。
// 原来筛选条里那个输入框和顶栏那个共享同一个 `?q=`，于是 keyword 既是 ref 又被
// watch 同步一次 —— 两处可写、一条同步路径，清空顶栏那个时下面这个就留住了旧词
// （两个框各存一份，漏的是"反方向"）。现在筛选条里那个输入框删了，**只有顶栏一个写方**，
// URL 就是唯一真相，读它不需要再抄一份到 ref 里。
const keyword = computed(() => keywordFromRoute(route))

/**
 * **当前搜索模式**（URL 上的 `?mode=`）：`content`（搜正文）/ `cat`（搜目录）。
 *
 * <p>**结果长什么样由它定**（2026-11-05 用户拍板：「在搜索框中加一个切换按钮
 * 默认查询正文 结果以卡片格式展示 也可切换为查询目录…这个结果就以我们刚才定的
 * 那个格式来展示」）⇒ **与你在哪个页面无关**。
 */
const searchMode = computed(() => searchModeFromRoute(route))
/** 有没有搜索词（没有搜索词就没有"结果形态"这回事）。 */
const hasKeyword = computed(() => keyword.value.trim().length > 0)
/** 这一档在服务端搜哪一块（`scope` 参数）：正文那档搜正文，目录那档搜标题。 */
const searchScope = computed(() => searchScopeOf(searchMode.value))
/** 目录名算不算命中（只有「搜目录」那档要）。
 *  ⚠️ **这条规则只有一份**（`utils/searchMode.js` 的 `matchNameFromRoute`）：
 *  侧边栏、列表页、目录树三处都要它。 */
const matchName = computed(() => matchNameFromRoute(route))


/**
 * 时间范围（不限 / 今天 / 本周 / 本月）。
 *
 * <p>**真相源是 URL**（`?range=`）：这一页只有这一个写方，
 * 但它得能刷新不丢、能分享 ⇒ 写 URL。
 *
 * <p>⚠️ **这里原来是一份"本地 ref + watch 同步"的镜像**（2026-11-03 晚改成 computed）：
 * 镜像意味着**两处可写**（URL 一处、本地 ref 一处），而同步只靠那个 watch ——
 * watch 只在 **URL 真的变了**的时候才跑。于是"选了「今天」之后选不回「不限」"这类
 * 症状就出来了：**URL 变没变、ref 跟没跟上是两件事**，界面上的药丸读的是 ref。
 *
 * <p>⇒ **跟目录筛选一个做法**：`categoryFilter` 早就是"直接从 URL 算的 computed"
 * （注释里写着「留一个 watch 同步等于多出一个能写的地方」）——
 * 时间范围当时没跟上，现在跟上了。**computed ⇒ 没有第二份状态可对不上。**
 *
 * <p>⚠️ **口径只有一份**（utils/timeRange.js）：发给服务端的起止、分面数字、
 * 首页的「今日新增」都从那儿算 —— 三处各算一遍的话，数字会和条数对不上，
 * **而界面上什么异常都看不到**。
 */
const timeRange = computed(() => rangeFromRoute(route))

/** 当前时间窗的起止（不限时间 = null）。 */
const timeBounds = computed(() => rangeBounds(timeRange.value))

/**
 * **结构索引**（全集，不含正文）：分面计数、考试范围、目录树都用它。
 *
 * <p>它是"**当前关键词下的全集**"（关键词要搜正文，只有服务端能筛）⇒ 关键词一变就得重拉。
 * 它**不含正文**：正文那 90 万字符是列表接口慢的真正原因（实测读全文 4416ms、不读 78ms）。
 */
const allTopics = computed(() => getTopicIndex())

// ==================== 分页 ====================

/** 当前页码（1 起）。筛选条件一变就回到第 1 页。 */
const page = ref(1)
/** 一页多少条。 */
const pageSize = ref(20)
/**
 * 当前页的知识点（**带正文**）。列表显示的就是它。
 *
 * <p>⚠️ **不再本地筛一遍**：服务端已经按目录/类型/标签/关键词筛好了，
 * 本地那份是结构索引（没有正文），用它筛会把正文命中的那几条滤掉。
 */
const pageRecords = ref([])
/** 当前条件下**一共**多少条（服务端数出来的）。 */
const pageTotal = ref(0)
/**
 * 这一页正在拉。**加载中不画旧内容**（模板里那一块整个换成转圈）：
 * 旧列表杵在那儿的时候，"没变"和"正在变"在界面上长得一模一样，
 * 用户只能靠"过几秒突然变了"判断刚才那一下到底有没有生效。
 */
const loading = ref(true)
/** 这一次拉失败了（界面摆「加载失败」+ 重试）。 */
const loadFailed = ref(false)
/**
 * 这一页的请求参数。
 *
 * <p>筛选条件**全部交给服务端** —— 正文在服务端才有意义（浏览器那份索引没有正文，
 * 用它筛会把"正文里命中的那几条"滤掉，那就等于没有全文搜索了）。
 *
 * <p>⚠️ **目录范围传的是一串 id**（逗号分隔），不是单个 categoryId：
 * 「选中一个目录 = 看整棵子树」这条规则**只有前端一份**
 * （store 的 getCategorySubtreeIds）—— 列表的筛选、考试的范围都按它算。
 * 后端再实现一遍就成了两套规则：目录一改，列表看到的和考的就对不上，
 * **而界面上什么异常都看不到**。
 */
function buildPageParams() {
  const params = {
    page: page.value,
    size: pageSize.value,
    keyword: keyword.value,
    // ⚠️ **scope 跟着"哪一档"走**：「正文」那档只搜正文，「目录」那档只搜标题。
    // 没搜索词时不发（服务端默认 both = 标题 + 正文都搜，那条语义不变）。
    scope: hasKeyword.value ? searchScope.value : undefined
  }
  if (categoryFilter.value === UNCATEGORIZED) {
    params.uncategorized = true
  } else if (categoryFilter.value !== ALL) {
    const targetId = isBookPage.value && currentId.value != null ? currentId.value : categoryFilter.value
    params.categoryIds = [...getCategorySubtreeIds(targetId)].join(',')
    // 书页看卡片按目录树顺序 + id 升序（与书中讲解顺序一致），全局列表保持最新在前
    if (isBookPage.value) params.sort = 'asc'
  }
  // **时间范围必须在服务端**：分页是服务端做的，前端筛完再发就没有分页可言。
  // 起止由前端按本地时区算好（utils/timeRange.js）⇒ 和后端、和分面数字同一口径。
  const bounds = timeBounds.value
  if (bounds) {
    params.createdFrom = bounds.from
    params.createdTo = bounds.to
  }
  // ⚠️ **不再发 `tag`**：标签筛选这个维度已经从界面上下线了（2026-11-03）。
  // 后端那个参数**还认**（接口没动）—— 留着不冲突，前端不发就是了。
  return params
}

/**
 * 在飞的那一次列表请求（去重 + 竞态守卫）。
 *
 * <p>⚠️ **同一组条件已经在飞了就不要再发一份**：
 * 「切回列表页」这条路上有**两个**触发点 —— onActivated 的重拉、筛选条件的 watch。
 * 两边参数一样 ⇒ 实测**两条一模一样的重查询并发跑**，
 * 单条 440ms 的活被两份同时压着变成 **5.5s / 7.2s**（后端那条查询被压住了）。
 * 也就是说：返回一次列表，从"等半秒"变成"等七秒"。
 *
 * <p>⚠️ **旧响应不许盖掉新响应**（`seq`）：两条请求同时在飞时慢的那条后到，
 * 会把新筛选的结果顶掉 —— 界面上是"筛选已经变了，列表还是旧的"，而且不会再自己纠正。
 */
let pageRequest = null
let pageRequestSeq = 0

/**
 * **上一次成功拉到的那一页是"按哪组参数"拉的**（`JSON.stringify(buildPageParams())`）。
 *
 * <p>⚠️ 它存在的唯一理由：**切视图（目录树 ⇄ 卡片）不再无条件重拉**（2026-11-11 用户：
 * 「我在点击这个目录/正文进行切换的时候 会马上进行下方数据的刷新…干脆这个切换就不要刷新了」）。
 * 切到卡片时，参数没变、手上还有数据 ⇒ 直接复用，一个请求都不发。
 *
 * <p>⚠️ **什么时候作废**：数据版本号变了（别处改/删了知识点）、重新激活这一页 ——
 * 那时候手上的数据可能已经过期，切过去必须先拉（见下面两条 watch）。
 */
let pageLoadedKey = null

/** 把当前这组参数算成一个可比较的串（和请求去重那把 key 是同一个口径）。 */
function currentPageKey() {
  return JSON.stringify(buildPageParams())
}

/** 拉当前页。 */
async function loadPage() {
  // 纯树模式**不画卡片** ⇒ 这一页用不上 ⇒ 不拉。
  // ⚠️ 列表那条查询要读正文（实测读全文 4416ms、不读 78ms）⇒ 树视图下拉它
  // 就是白等几秒。树要的数据在**结构索引**里（标题/类型/目录），那份已经在了。
  // ⚠️ **树 + 卡片模式（?view=cards）要拉**：卡片内容就是**标准卡片视图**
  // （服务端分页 20 条，和首页点进去同一套），不走全量。
  // ⚠️ **但不许把已有的那页数据清掉**（2026-11-11）：清了之后"树 → 卡片"就必然
  // 得重拉一次，那正是用户说的"一切换就刷新"。数据留着、`pageLoadedKey` 留着，
  // 切过去先看参数有没有变（见 `watch(treeCards)`）；真过期了由数据版本号那条 watch 作废。
  if (isBookPage.value && !treeCards.value) {
    loading.value = false
    loadFailed.value = false
    return
  }
  let seq = 0
  // ⚠️ **转圈要在第一个 await 之前亮起来**（2026-11-03 晚 · 性能）。
  //
  // 原来 `loading = true` 写在 `await ensureCategoriesLoaded()` **之后**。
  // 目录树命中缓存时那次 await 也只是让出一个 microtask，但这一让就够了：
  // 那一两帧里 `loading` 是 false、`pageRecords` 是空的（树视图期间清空的）
  // ⇒ 模板三个分支（loading / loadFailed / pageRecords.length）**全不成立**
  // ⇒ **界面是空的**。
  //
  // 用户看到的就是「点了没反应，过一会儿才转圈」——**那半拍不是网络慢，
  // 是自己漏画的一帧**。树视图切卡片视图（以及任何筛选变化）都走这条路径。
  loading.value = true
  try {
    // 子树 id 列表要用目录树 ⇒ **目录得先在**（不强制重拉：命中缓存是零成本，
    // 没命中就等它 —— 否则"刚进来就改筛选条件"会拿着空的目录树算出错的范围）。
    await ensureCategoriesLoaded()
    // 掌握概览要目录三指标（`/kp/learning/tree` 一次拉整棵树）。
    // ⚠️ **不 await 它**：概览是**锦上添花**的那一条，列表本身不该等它 ——
    // 拉它要读一遍整棵树的投影，比列表本身还慢。
    // 拉回来是响应式的 ⇒ 概览稍后自己出现，不用用户做任何操作。
    // ⚠️ 失败不弹提示（request 层会弹，但那是"列表都拉不动"的级别）；
    // 拉不到就**没有概览条**，不影响别的。
    ensureCategoryMasteryLoaded().catch(() => {})
    const params = buildPageParams()
    const key = JSON.stringify(params)
    // 同样的条件已经在飞 ⇒ 跟着那一次，不重发（见 pageRequest 那段注释）。
    if (pageRequest && pageRequest.key === key) return pageRequest.promise

    seq = ++pageRequestSeq
    const promise = (async () => {
      let { records, total } = await loadTopicPage(params)
      // 删掉最后一页的最后一条 ⇒ 会停在一个**空页**上。回到还有内容的那一页。
      if (!records.length && page.value > 1) {
        const last = Math.max(1, Math.ceil(total / pageSize.value))
        if (last < page.value) {
          page.value = last
          const again = await loadTopicPage(buildPageParams())
          records = again.records
          total = again.total
        }
      }
      return { records, total }
    })()
    pageRequest = { key, seq, promise }
    loading.value = true
    loadFailed.value = false
    const { records, total } = await promise
    // 慢的那条后到 ⇒ 它是过期的，丢掉（见 pageRequest 那段注释）。
    if (seq !== pageRequestSeq) return
    pageRecords.value = records
    pageTotal.value = total
    pageLoadedKey = key
  } catch {
    // 错误详情由 request.js 弹（那里是全站唯一的提示出口），这里只摆状态。
    loadFailed.value = true
  } finally {
    // 只有**最新**那次负责收尾：loading 复位、在途标记清掉。
    // 旧的那条收尾时若把 loading 置回 false，新请求还在飞、界面就不转圈了。
    if (seq === pageRequestSeq) {
      loading.value = false
      pageRequest = null
    }
  }
}

function onPageChange(p) {
  page.value = p
  loadPage()
}

// 目录被删掉后 URL 还指着它时 getCategory 拿不到，当成"全部"，不白屏
const currentCategoryId = computed(() => getCategory(categoryFilter.value)?.id ?? null)

/**
 * 学习掌握概览的数据源（方案 C，2026-11-12）。
 *
 * <p>⚠️ **概览是「当前这一屏范围」的聚合，不是当前分页的**：
 * 后端 {@code /kp/learning/tree} 一次返回**整棵树**的聚合，每个目录那一条
 * 已经是「直挂 + 全部子目录」的折叠结果（同一知识点只算一次）⇒
 * 拿当前选中目录那一行，就是这一屏（子树）的掌握分布。
 *
 * <p>⚠️ **没有选中目录时（全部 / 待归类）不画概览**：
 * "全部"是一个跨目录的桶，聚合出来没有对应的目录行，
 * 而"待归类"压根不参与目录聚合（设计 §3.1）⇒ 两种情况都拿不到行。
 */
const overviewRow = computed(() => {
  if (categoryFilter.value === ALL || categoryFilter.value === UNCATEGORIZED) return null
  const id = currentCategoryId.value
  if (id == null) return null
  return getCategoryMastery(id)
})

/**
 * 选一个目录 = 看**整棵子树**。
 *
 * 为什么不是"只显示直接挂载"：那样进到「集合」只能看到 3 条，
 * 它父目录里另外 20 条就凭空消失了，层级会变成"建得进、看不见"。
 * 子树平铺让页面永远是一排卡片，层级靠侧边栏当前高亮的那一项表达，
 * 不靠额外的子目录区块。（原来这里写的是"靠卡片上的目录 chip"，
 *  那个 chip 早就撤掉了，注释和代码对不上。）
 *
 * **范围怎么算只有一份**（store 的 `scopeMatcher`）：列表筛出来的这批，
 * 和考试要考的必须是同一批。各算一遍，目录一改就可能对不上——**而且界面上什么异常都看不到**。
 *
 * <p>**不自己再算一遍子树**：以前这里是"先算出范围内的知识点、再拿它们的 id
 * 集合去过滤知识点"——同一棵子树过滤两遍。现在直接问"这条在不在范围内"。
 */
const scopeMatch = computed(() => scopeMatcher(categoryFilter.value))

function matchCategory(t) {
  return scopeMatch.value(t)
}

// ⚠️ **关键词这一维没有本地谓词了**：它**已经在服务端应用过了** ——
// 结构索引就是"当前关键词下的全集"（正文只有服务端能搜），
// 本地再判一次的话索引里没有正文，会把正文命中的那几条全滤掉。


// ==================== 两种形态：书页（标题行 + 内容盒子）/ 网格 ====================

/**
 * 这一屏是不是「**一本书的目录页**」—— 顶部标题行（统计 / 分段切换 / 筛选条）+ 内容盒子。
 *
 * <p>⚠️ **2026-11-08 晚把"形态"拆成两层**（用户：「如果我从目录树中点进某个子目录
 * 那么展示的卡片栏的上方就会不一样 没有统一 那些统计、切换按钮都没了」）：
 * 原来 `?category=` 一律判成"不是树" ⇒ 整棵 BookToc（**连着它的标题行**）被 `v-show`
 * 藏掉，只剩一条右对齐的筛选条 + 卡片 ⇒ 从树里点进子目录，页头凭空矮一截、
 * 「目录树 | 卡片」切换也没了 —— 同一条路（进目录看内容）走了两种页头。
 * ⇒ 拆成两件事，各自只有一个判据：
 * - **这一屏是不是书页**（本函数）⇒ **标题行画不画**；
 * - **内容盒子放什么**（{@link treeCards}）⇒ 树 / 卡片。
 * 标题行因此在一本书的所有形态下都是**同一行**（同一份 meta、同一个分段切换、同一份筛选条）。
 *
 * <p>判据：
 * - **搜索态**（`?q=`）：形态由**档位**定（「目录」档 = 书页、「正文」档 = 卡片页），
 *   **与你在哪个页面无关**（2026-11-05 用户拍板「行为要保持一致」）——
 *   搜索时 `?tree=` / `?category=` 只管范围，不管形态。
 * - **带 `?tree=`**：那本书能画树就是书页（`canUseTree`：二级以下目录没有自己的树）。
 * - **只带 `?category=`**（正文里的目录链接、详情页顶栏面包屑点某一级）：
 *   它**属于某本书**（祖先链walk得到第一级）⇒ 同样是那本书的书页。
 *   这些入口在 URL 上带不了 `tree`（markdown 里的 href 是字符串常量，
 *   见 utils/categoryNav.js 的 `CATEGORY_HREF_PREFIX`），但用户看到的页面该是同一个样子。
 *
 * <p>⚠️ 「能不能用树」这条判据只有一份（utils/viewPref.js 的 canUseTree）——
 * 原来这里和那边的兜底各写一遍 `!parentId`，两边漂了的话侧边栏会把二级目录
 * 送成 ?tree=<id>，而这一屏判它不是树 ⇒ 退成全量平铺，界面上一点异常都看不出来。
 */
const isBookPage = computed(() => {
  if (hasKeyword.value) return isCatMode(searchMode.value)
  const t = route.query.tree
  if (t != null) return canUseTree(getCategory(Number(t)))
  const cid = currentCatId.value
  return cid != null && getCategoryPath(cid).length > 0
})

/**
 * 树的根：**记下来的那一本**，不是"当前路由上的那一本"。
 *
 * <p>⚠️ **为什么不能直接用路由上的**（2026-11-03 实测踩出来）：
 * 路由是全局的 —— 从树里点进知识点详情时，地址变成 `/app/topic/xxx`，
 * `route.query.tree` 随之**变成 undefined** ⇒ 判据（`v-show="isBookPage"`）翻成 false
 * ⇒ **整棵树被销毁**（`v-if`）⇒ 树里记住的「刚才打开的是哪一条」（`lastTopicId`）
 * 和「哪些目录是收着的」（`collapsed`）**全丢了** ⇒
 * 返回时"回到刚才那一行"根本无从谈起（实测：返回后树回到默认骨架，那条知识点不在视野里）。
 *
 * <p>所以这里只**记 id**：进树视图时记下这一本，离开这一屏时**不擦**
 * ⇒ 树在详情页期间保持挂载，返回时它还活着、还记得。
 *
 * <p>对象仍然从 store 现取（`getCategory`）—— 目录改过名、改过父级，
 * 树拿到的必须是**当前**那一份，不能是进树那一刻的快照。
 */
const treeViewRootId = ref(null)

// ⚠️ **只记 `?tree=`**。`?category=` 的归属书由下面 `treeViewRoot` **现算**
// （目录是异步来的 —— 在这个 watch 里记一次的话，目录还没到时算不出祖先链，
// 记下来就是空的 / 上一次那本，而且不会自己纠正）。
watch(
  () => route.query.tree,
  (t) => { if (t != null) treeViewRootId.value = Number(t) },
  { immediate: true }
)

/**
 * 这个目录的**直属**知识点条数（2026-11-16 D7：卡片 = 当前目录的直属，不递归）。
 * 「直属」这个口径只此一份：卡片取数（buildPageParams）和分段显隐都问它。
 */
function directCountOf(id) {
  if (id == null) return 0
  let n = 0
  for (const t of getFullTopicIndex()) if (t.categoryId === id) n++
  return n
}

/** 当前目录（没选 ⇒ 书根——书根的直属就是「便签层」）。 */
const currentId = computed(() => currentCatId.value ?? treeViewRoot.value?.id ?? null)
/** 书里有没有子目录（没有 ⇒ 树没得画，「目录树」段不渲染、直接落卡片）。 */
const hasTreeBox = computed(() =>
  treeViewRoot.value != null && getChildCategories(treeViewRoot.value.id).length > 0
)
const currentIsLeafWithDirect = computed(() =>
  currentId.value != null && directCountOf(currentId.value) > 0 && getChildCategories(currentId.value).length === 0
)

/**
 * **内容盒子放什么**：卡片（true）还是树的正文（false）。
 */
const treeCards = computed(() => {
  if (!isBookPage.value) return false
  if (!hasTreeBox.value) return true
  const v = route.query.view
  if (v === 'cards') return true
  if (v === 'tree') return false
  return currentIsLeafWithDirect.value
})

/**
 * **侧边栏那一列**（命中的书）—— 与侧边栏用的是**同一份**判据
 * （utils/categoryTree.js 的 visibleRootCategories）。
 *
 * <p>⚠️ **各写一遍的话会漂**：侧边栏剩三本、树却长在第四本上，
 * 而界面上看不出哪儿不对。
 */
const hitBooks = computed(() =>
  visibleRootCategoriesOf(
    route,
    getChildCategories(ROOT_CATEGORY),
    getCategories(),
    categoryCounts.value
  )
)

/** 目录命中计数（索引按 categoryId 聚合、沿祖先链累加）。 */
const categoryCounts = computed(() => getCategoryCountMap())

/**
 * **这一屏的书**：目录那档（搜索态）⇒ **这一轮命中的书**；
 * 没有搜索词 ⇒ URL 上那本（没有就第一本）。
 *
 * <p>⚠️ **目录那档不能沿用"记住的那本"**（用户 2026-11-05：「我搜一个搜不到的东西
 * 侧边栏清空了正常但是 为什么目录树里还是上一次的那颗树 也应该清空啊」）：
 * 搜一个新词时，上一轮的书跟这一轮**没关系** ⇒ 树必须重新按这一轮的命中来挑，
 * 一个都没命中 ⇒ **树不画**（清空）。
 *
 * <p>⚠️ **不兜底的话右边一片空白**：目录那档的结果是一棵树，
 * 树得有一本书才画得出来 ⇒ 用户会以为"搜不出东西"（而侧边栏明明列着几本）。
 */
const treeViewRoot = computed(() => {
  // 目录那档（搜索态）：只在**这一轮命中的书**里挑。
  if (hasKeyword.value && isCatMode(searchMode.value)) {
    const books = hitBooks.value
    if (!books.length) return null
    const remembered = treeViewRootId.value
    return books.find((b) => b.id === remembered) || books[0]
  }
  // ① URL 上明确的那本书（侧边栏点书 / 树里点目录都带着它）。
  const t = route.query.tree
  if (t != null) return getCategory(Number(t))
  // ② **只带 `?category=`**（正文里的目录链接、详情页顶栏面包屑点某一级）：
  //    那本书 = 它的**祖先链第一级**。⚠️ 目录还没到时返回 null（**别退回"记下的那本"**
  //    —— 那可能是上一本书，标题行会显示错书的统计，而卡片是另一个目录的）。
  const cid = currentCatId.value
  if (cid != null) {
    const chain = getCategoryPath(cid)
    return chain.length ? chain[0] : null
  }
  // ③ 都没有（离开这一屏期间 / 什么范围都没选）：用记下的那本。
  if (treeViewRootId.value != null) return getCategory(treeViewRootId.value)
  return hitBooks.value[0] || null
})

/**
 * 树上**画不画**。
 *
 * <p>⚠️ **2026-11-04 改了判据**：原来还要求"此刻在树视图里，或者已经离开这一屏"，
 * 卡片视图期间（标题行藏起来、`onLearnRoute` 真）整棵树被**销毁重建**
 * ⇒ 树里记住的「刚才点的是哪一行」（`lastSpot`）和「哪些目录是收着的」（`collapsed`）
 * **全丢了** ⇒ 从目录卡片页返回时根本恢复不了（用户报的「返回的目录都不对了」）。
 *
 * <p>现在**只要记下了那一本就一直挂着**（模板里 `v-show="isBookPage"` 只藏不销毁），
 * 卡片内容期间也在 ⇒ 返回时 `active` 一变 true 就恢复。
 *
 * <p>⚠️ 离开这一屏时**不能新建**：那时候 `route.query.tree` / `category` 都不在了
 * ⇒ 用 ③（记下来的那本），没有记过（为 null）就不画
 * —— 不给 `BookToc` 传 null（它的 `category` 是 required）。
 *
 * <p>⚠️ 跟下面那个 `treeRoot`（**当前选中目录所属的那本书**，给「切回目录树」按钮用）
 * **是两个东西**：那个从 `categoryFilter` 往上找，这一个是**标题行正在显示的那一本**。
 */
const renderTree = computed(() => treeViewRoot.value != null)

// ==================== 分面计数 ====================

/**
 * 分面计数的**唯一一份实现**（2026-11-04 收敛）。
 *
 * <p>规则只有一条：**某维度的数字 = 其它维度已生效条件下的条数** —— 自己那一维
 * 换成"候选值"再数（原来这些数字是全库计数：选了「待归类」再点类型「笔记」，
 * 上面写着有数、点进去空空如也 —— **数字在骗人，比没有数字更糟**）。
 *
 * <p>⚠️ **时间窗是公共条件**（不是哪一维的"自己"）⇒ 每个数字都在时间窗内算。
 *
 * <p>三个消费方都从它出（原来是一个 `topicsInScope` + 一个 `countBy` 两套实现，
 * 各算一遍 ⇒ 以后加一维只改一处，数字和条数对不上时界面上看不出异常）：
 * - 时间下拉里每一项：`{ time: 该项的时间窗 }`（时间这一维用候选值）
 * - 类型下拉里每一项：`{ time: 当前时间窗, type: 候选类型 }`
 * - 当前筛选出的全集：`{ time: 当前时间窗 }`
 *
 * @param {{time?: {from:string,to:string}|null}} dims
 *        `time` 不给 = 不按时间筛。
 */
/** 手动归类弹窗：开着没有 / 归的是哪一条。 */
const categorizeOpen = ref(false)
const categorizeTopic = ref(null)

/**
 * ⚠️ 「正在看哪」的**唯一判据**：`categoryFilter === UNCATEGORIZED`。
 * AI 归类和批量选择都只在待归类屏出现（一个是 AI 功能、一个不是），
 * 两边各写一遍同样的比较就会漏改一处 ⇒ 收敛成这一份。
 */
const isUncategorized = computed(() => categoryFilter.value === UNCATEGORIZED)


/**
 * 批量入口**只在待归类屏**（2026-11-11 用户："批量操作只在待归类里面显示这个按钮"）。
 * ⚠️ **不看 `aiEnabled`**：批量归类／删除不是 AI 功能，AI 开关关了它们照样该在。
 * 也不看有没有知识点 —— 当前页空不空由 `pageCount` 管。
 */
const showBatch = isUncategorized

// 包一层 computed 才是响应式的：getCategories() 只是读 categories.value，
// 直接在模板里调拿到的会是一份普通数组快照，弹框里新增目录后这里不跟着变。
// ⚠️ **AI 归类弹框和手动归类弹框共用这一份**（2026-11-06）：
// 两个 computed 各写一遍同样的 getCategories()，改一处就有一处不跟 —— 合成一个。
const allCategories = computed(() => getCategories())

// ==================== 掌握度参与设置 ====================
//
// 卡片「⋯ → 掌握度设置」⇒ 打开**这一个**弹窗，让用户选"参与 / 不参与 / 跟随类型默认"。
// ⚠️ **弹窗只有一个实例**（一页 20 张卡就是 20 个实例的问题，同手动归类）。
// ⚠️ **判据不在前端**：这一档的最终参与状态由后端解析（MasteryScope），
// 前端只把用户的选择原样发过去。
const masteryScopeOpen = ref(false)
const masteryScopeTopic = ref(null)

function onMasteryScope(t) {
  masteryScopeTopic.value = t
  masteryScopeOpen.value = true
}

// ==================== 手动归类 ====================
//
// 卡片「⋯」菜单里的「归类到目录」⇒ 打开**这一个**弹窗，让用户挑一个目录。
// ⚠️ **弹窗只有一个实例**（不是每张卡挂一个）：一页 20 张卡就是 20 个实例。
function onCategorize(t) {
  categorizeTopic.value = t
  // ⚠️ **清掉批量那一份**：同一个弹窗，`rows` 的取法是"有批量就用批量"，
  // 残留一批在这里 ⇒ 点单条的「归类到目录」会去动上一批 17 条。
  batchCategorizeTopics.value = []
  categorizeOpen.value = true
}

// ==================== 刷新 ====================
// （这里原来有一整段「切回目录树」—— 2026-11-08 起树 ⇄ 卡片在树视图标题行就地切换，
// 这个页面不再管形态切换；「哪本书的树」由侧边栏那一行自己知道。）

async function refresh() {
  // 目录先刷：**子树的 id 列表要用它**（列表请求要传 categoryIds）
  await ensureCategoriesLoaded()
  // 索引（结构：目录/计数/范围）和这一页（内容：正文/摘要）**互不依赖** ⇒ 一起发，
  // 别一个接一个等（两个都是几百毫秒量级，串行就是两倍）。
  //
  // ⚠️ **都不强制重拉**：壳（AppLayout）启动时已经把目录 / 索引拉了，
  // 这里再 force 一遍就是**同一个接口打两次**（实测每次进列表页多打一遍
  // /kp/topic/index，而它和列表请求是并发的，等于把列表也一起拖慢）。
  // 数据变了由 store 自己 force（改完知识点就会刷一遍）⇒ 这儿重拉是白拉。
  //
  // ⚠️ **不再拉标签**（`ensureAllTagsLoaded`）：标签筛选已从界面下线，
  // 这一屏不需要那份清单了（少一个并行请求，实测 228ms —— 不在关键路径，
  // 但白拉就是白拉）。后端那套标签接口**一行没动**，见 utils/viewPref.js
  // 顶部那段「标签：界面已下线，后端有意保留」。
  await Promise.all([
    ensureTopicIndexLoaded(false, keyword.value, searchScope.value),
    loadPage()
  ])
}

/**
 * **进详情页时把搜索上下文带上**。
 *
 * <p>详情页要显示**同一套**命中浮条（「第 3/52 个」+ ↑↓ 跳上一条下一条）⇒ 它得知道：
 * 搜的什么、哪一档、当前范围（目录 / 时间 / 类型）。
 *
 * <p>⚠️ **类型筛选也在里面**（它现在进 URL 了）—— 不带的话详情页还原不出
 * 同一个命中集，两边的序号会错位，而界面上什么异常都看不到。
 */
function goDetail(t) {
  router.push({
    name: 'AppTopicDetail',
    params: { id: t.id },
    query: searchContext()
  })
}

/**
 * **搜索上下文（进详情页要带着走的那些参数）**。
 *
 * <p>⚠️ **详情页自己不读这些**（它的命中浮条 2026-11-05 撤了）⇒ **读它们的是业务壳**
 * （AppLayout，详情页是 `/app` 的子路由）：
 * - `q` / `mode` ⇒ 顶栏那个搜索框和侧边栏（壳里还搜着同一批）；
 * - `tree` / `category` ⇒ 侧边栏高亮的是哪本书（壳的 `currentBookId`）。
 *
 * <p>⚠️ **不带就变了**：从"搜出来的结果"点进详情，壳却回到"没搜"的形态
 * （搜索框空、侧边栏列全部书）⇒ 看起来像"搜索丢了"。
 *
 * <p>⚠️ **`range` / `type` 不带**：那两个只有列表页读（AppLearnView），
 * 详情页上没有人读 ⇒ 带着只是把 URL 弄长。
 */
function searchContext() {
  if (!hasKeyword.value) return {}
  const q = { q: keyword.value }
  if (matchName.value) q.mode = SEARCH_MODE_CAT
  // ⚠️ **query 的值可能是数组**（手改的 URL：`?category=a&category=b`）⇒ 取第一个。
  const one = (v) => (Array.isArray(v) ? v[0] : v)
  if (route.query.tree != null) q.tree = one(route.query.tree)
  if (route.query.category != null) q.category = one(route.query.category)
  return q
}

/**
 * 树里点目录名 ⇒ 看那个目录的**内容**（该目录子树的知识点平铺）。
 *
 * <p>和行首那个箭头分工：箭头 = 就地展开/收起，目录名 = 去看内容。
 * 两个动作长得不一样（一个是图标、一个是名字），不会点错。
 *
 * <p>⚠️ **用 push 不用 replace**（2026-11-04）：`replace` 把当前历史条目**替换**掉了
 * ⇒ 浏览器"返回"回到的是**进树之前**的那一屏，不是刚才这棵树
 * （用户报的「我返回的时候返回的目录都不对了」）。
 * `push` 多留一条历史 ⇒ 返回正好回到刚才那棵树。
 *
 * <p>⚠️ **树参数不能清掉**：清掉 `tree` 之后这一屏就判不出"哪本书"（见 `treeViewRoot`）
 * ⇒ 标题行、树、切换按钮全没了 —— 只剩一张孤立的全量网格。
 *
 * <p>⚠️ **`?category=` 换的是内容盒子，不是页面**（2026-11-08 晚）：这一屏仍是那本书的
 * 目录页（标题行原样），只是内容盒子从树换成卡片 —— 判据见 `isBookPage` / `treeCards`。
 */
/**
 * 目录「写知识点」建完结构 ⇒ 刷目录树 + 知识点列表。
 *
 * <p>⚠️ **任务列表不在这里刷**：那件事由弹窗自己走统一出口（`genTaskCreated`），
 * 两处都刷就是两次请求。
 */
async function onTreeStructureDone() {
  await refreshTopicIndexes()
  bumpDataVersion()
}

/**
 * 点一行目录 = **设为当前 + 外层定视图**（2026-11-16 D7）：
 * 叶子且有直属 ⇒ 直接进卡片（看内容）；否则 ⇒ 树（开合已经在树里发生了）。
 *
 * <p>⚠️ **写显式 `?view=`**：点进行为是确定性的（不跟偏好跑）——
 * 同一行点两次看到的必须是同一个盒子。
 */
function onTreePick(c, intent) {
  // 点击意图由树说了算（2026-11-17）：'tree' = 这一下是展开；'cards' = 这一下是进内容。
  // 没有直属内容时卡片段不存在 ⇒ 夹回树（view 解析那一层也会兜）。
  const view = intent === 'cards' && directCountOf(c.id) > 0 ? 'cards' : 'tree'
  router.push({
    path: route.path,
    query: {
      ...route.query,
      tree: treeViewRoot.value?.id,
      category: c.id,
      view
    }
  })
}

// ==================== 批量选择（2026-11-11）====================
//
// 口径（用户拍板）：
// ① **只选当前页**（服务端分页 20 条/页，跨页选择要维护跨请求的集合，
//   还得回答"翻走了的那些还算不算"——用户明确不要）；
// ② **除了「在新标签页打开」，卡片 ⋯ 菜单里的都能批量**（归类到目录 / AI 归类 / 删除；
//   "开新标签页" × 20 条 = 一次弹 20 个 tab，不是操作是事故）；
// ③ **点卡片即选中**（批量模式下点卡片不进详情）。
// ④ **只在待归类屏出现**（`showBatch`）：已归好类的卡片本来各有 ⋯ 菜单，
//   批量是"清理待归类堆积"这个场景的动作，摆到别的屏上就是噪声。
//
// ⚠️ 三个动作**没有一个是新逻辑**：归类复用 CategorizeDialog（加了个"多条"形态）、
// 删除复用 store 的删除流。
// 这一轮新写的只有"选哪些"这件事本身。

const batchMode = ref(false)
/** 选中的 id。用 Set：勾选判据 O(1)，也不会有重复项。 */
const selectedIds = ref(new Set())
/** 批量归类时塞进弹窗的那一批（空 = 弹窗处于单条形态）。 */
const batchCategorizeTopics = ref([])

/** 当前页能勾的那些（顺序跟页面一致，"全选本页"直接照它来）。 */
const selectableIds = computed(() => pageRecords.value.map((t) => t.id))

/**
 * 已选的那些**对象**（不是 id）。
 * 三个动作要的都是完整对象（归类弹窗要标题/摘要、AI 归类要 id+标题），
 * 每次从这里按**页面顺序**取，动作执行时拿到的就是用户眼前那个顺序。
 */
const selectedTopics = computed(() => pageRecords.value.filter((t) => selectedIds.value.has(t.id)))

/** 当前页是否已全选（决定「全选本页 / 取消全选」的文案）。 */
const allSelected = computed(
  () => selectableIds.value.length > 0 && selectedTopics.value.length === selectableIds.value.length
)

function clearSelection() {
  selectedIds.value = new Set()
}

/**
 * 进出批量模式。
 * ⚠️ **进出都清空**：出去时留着没有意义；进来时若不清，上一轮的勾会跟着回来
 * ——用户看不到"为什么一进来就有 3 条选中"。
 */
function toggleBatch() {
  batchMode.value = !batchMode.value
  clearSelection()
  // 批量那一份也得清：不然下次点单条的「归类到目录」，弹窗会拿上一批去动。
  batchCategorizeTopics.value = []
}

function toggleSelect(t) {
  const next = new Set(selectedIds.value)
  if (next.has(t.id)) next.delete(t.id)
  else next.add(t.id)
  selectedIds.value = next
}

function selectAllOnPage() {
  selectedIds.value = allSelected.value ? new Set() : new Set(selectableIds.value)
}

/** 卡片点击：批量模式下是"勾选"，其余情况才是"进详情"。 */
function onCardClick(t) {
  if (batchMode.value) toggleSelect(t)
  else goDetail(t)
}

/**
 * ⚠️ **页面数据一换就清空选择**（翻页 / 改筛选 / 改搜索 / 数据版本变化后重拉）。
 *
 * <p>判据用「当前那组请求参数」（`currentPageKey`，含页码）：它变了就说明眼前
 * 这批卡片不是刚才那批了，留着旧 id 会出现"已选 3 条、页面上却只勾着 1 张"
 * ——那比清空更难解释。**清空但保留批量模式**：翻页多半是想接着勾，
 * 顺手退出模式反而让用户重新点一次。
 */
watch(currentPageKey, () => {
  if (batchMode.value) clearSelection()
})

/**
 * **离开待归类就退出批量模式**（不只是清勾选）。
 * ⚠️ 上面那条 watch 只清选择、保留模式（翻页是同一屏，接着勾很顺）；
 * 但换屏之后再保留就没有入口能退了 —— 别的屏不画「批量」这颗钮，
 * 工具条却还是批量条、卡片还带勾选框。判据和入口同源（`showBatch`）。
 */
watch(showBatch, (ok) => {
  if (!ok && batchMode.value) toggleBatch()
})

/**
 * 批量 · 归类到目录。
 * ⚠️ **和单条共用同一个弹窗实例**（`categorizeOpen`），只是这次塞进去的是一批：
 * `CategorizeDialog` 的取法是「有 topics 就用 topics」，所以这里必须把单条那个
 * `categorizeTopic` 清掉，否则弹窗里会显示上一张卡的标题、动的是这一批。
 */
function onBatchCategorize() {
  if (!selectedTopics.value.length) return
  categorizeTopic.value = null
  batchCategorizeTopics.value = selectedTopics.value
  categorizeOpen.value = true
}

/** 归类完成：卡片已经动了，勾选跟着失效 ⇒ 清空（批量模式不退，用户可能接着勾）。 */
function onCategorized() {
  if (batchMode.value) clearSelection()
}

/** 批量 · 删除：确认框写明条数，删完清空勾选（卡片已经被刷掉了）。 */
async function onBatchDelete() {
  if (!selectedTopics.value.length) return
  const done = await confirmDeleteTopics(selectedTopics.value)
  if (done) clearSelection()
}

async function handleDelete(t) {
  // 确认框 + 删除 + 提示**全在 store**（和详情页顶栏同一个入口）：
  // confirmDeleteTopic 内部已经删完并刷了索引和标签。
  // ⚠️ **别在这里再调一次 refresh()** —— store 删完会把数据版本号 +1，
  // 下面那个 watch 看到就会重拉这一页（再调一次就是同一个列表被拉两遍）。
  await confirmDeleteTopic(t)
}

/**
 * ⚠️ **第一次激活不重拉**（onMounted 的 refresh 已经在拉了）⇒ 用 activatedOnce 区分。
 */
let activatedOnce = false

/**
 * 这一屏现在是不是"在眼前"。
 *
 * <p>列表页被 `AppLayout` 的 `<keep-alive :max="6">` 缓存着（切走**不销毁**）
 * ⇒ **"我回来了"这件事只有页面知道**，由它往下传（`BookToc` → `CategoryTree` 的 `active`）。
 *
 * <p>树用它做两件事：① **返回时定位回刚才打开的那条知识点**；
 * ② **不在眼前就别定位**（后台数据刷新时提前定位，等用户回来时高亮早淡没了）。
 */
const pageActive = ref(false)

onMounted(() => { pageActive.value = true })

onActivated(() => {
  // 切回来的：离开这段时间数据可能变了（别处改的、别处删的）⇒ 重拉这一页。
  // ⚠️ **同时作废"可复用"凭据**：不做的话，树视图下 `loadPage` 会早退（不拉），
  // 而 `pageLoadedKey` 还是离开前那个 ⇒ 再切卡片就直接复用了一份**离开前**的数据。
  if (activatedOnce) {
    pageLoadedKey = null
    loadPage()
  }
  activatedOnce = true
  pageActive.value = true
})
onDeactivated(() => { pageActive.value = false })

/**
 * store 说"数据变了" ⇒ **重拉这一页**。
 *
 * <p>「当前页」归这一侧管（它知道第几页、筛了什么），store 只负责说"变了"。
 * ⚠️ 原来 store 自己缓存了一份当前页、视图渲染的却是另一份 ⇒
 * **在列表页删掉一条知识点，卡片不消失**（得刷新页面）——
 * 两份"当前页"只有一份是活的，另一份白拉一次请求。
 *
 * <p>⚠️ **只在这一页正开着的时候重拉**（见 onLearnRoute）：
 * AppLayout 的 `<keep-alive :max="6">` 把页面实例缓存着 ——
 * 切到详情页时**组件不销毁、watch 还在跑**，
 * 而详情页每 1.5s 防抖保存一次正文 ⇒ 不加这个判据就是
 * **每存一次字，列表页在后台偷偷重拉一遍**（那正是这次要省掉的那次请求）。
 */
watch(getDataVersion, () => {
  // 数据变了 ⇒ 手上那页（`pageRecords`）可能已经过期 ⇒ 作废"可直接复用"的凭据
  //（2026-11-11：视图切换现在会复用已拉到的数据，不作废的话删掉一条切过去还在）。
  pageLoadedKey = null
  if (onLearnRoute.value) loadPage()
})

// ⚠️ **目录筛选不用 watch**：`categoryFilter` 现在是**直接从 URL 算的 computed**
// ⇒ 侧边栏改 URL、面包屑改 URL，它自己就跟着变。留一个 watch 同步等于
// 多出一个能写的地方（两处可写就有一处对不上）。

// ⚠️ **时间范围也不 watch**（2026-11-03 晚）：它同样是**直接从 URL 算的 computed**
// （见上面 `timeRange` 那段）。原来这里是"ref + watch 镜像"——
// 镜像的那一份只靠 watch 跟，而 watch 只在 URL 变了才跑 ⇒ 界面读的那份可能停在旧值。

/**
 * 筛选条件变了 → 重拉这一页。
 *
 * <p>**回到第 1 页**：翻到第 5 页再换个条件，第 5 页多半是空的（这一次的总数变小了）。
 *
 * <p>关键词也在这个 watch 里：**索引是"关键词 + 当前档下的全集"**（服务端筛的），
 * 关键词或档一变索引就得跟着重拉 —— 不拉的话侧边栏的条数还是"不筛关键词"的那份。
 * 关键词没变时它是空操作（store 认得"这份就是当前的"）。
 *
 * <p>⚠️ **搜索档（`?mode=`）也在这个数组里**（2026-11-13 收回来）：切档已经改成
 * **不导航、不刷新**（见 AppLayout 的 `toggleSearchMode`）⇒ URL 上的 mode
 * 只在**回车提交**（`goSearchWith`）、后退、外链时变。而回车是"词和档一起落地址"，
 * 分成两个 watch 就是同一次提交拉两遍 —— 合并回一条，一次请求。
 *
 * <p>⚠️ **`treeCards` 也拆出去了**（2026-11-11 用户：「干脆这个切换就不要刷新了」）：
 * 视图切换**不改变请求参数**（`buildPageParams` 只读词/范围/时间/类型）⇒ 它触发重拉
 * 从来就不是"必须"，只是"顺手"。切到卡片时按 {@link pageLoadedKey} 判断能不能直接复用。
 */
watch([categoryFilter, keyword, timeRange, searchMode], (now, was) => {
  // ⚠️ 关键词是**直接从 URL 读**的 ⇒ 离开这一页时它会变成空
  //（详情页虽然带着 `?q=` 过来，但**这一页已经不在路由上**了）。
  // 那一瞬间**不是**"用户清了搜索词"，是"这一页暂时不在眼前" ⇒ 不在这儿发请求。
  if (!onLearnRoute.value) return
  // ⚠️ **只有档变了、而两边都没词 ⇒ 空操作**：没词就没有"命中集"，
  // 档位只是"下一搜用哪档"（列表那条查询要读正文，实测 4416ms，白拉一次很贵）。
  const kw = String(now[1] || '').trim()
  const oldKw = String(was && was[1] || '').trim()
  if (now[3] !== (was && was[3]) && !kw && !oldKw) return
  page.value = 1
  ensureTopicIndexLoaded(false, keyword.value, searchScope.value)
  loadPage()
})

/**
 * **视图切换（目录树 ⇄ 卡片）不再无条件重拉**（2026-11-11 用户：「会马上进行下方数据的刷新
 * 但是又没有带上我输入框中的搜索内容 干脆这个切换就不要刷新了」）。
 *
 * <p>切到卡片时只问一句"手上这份数据是不是就是这组参数拉的"：
 * - **是** ⇒ 直接复用，一个请求都不发（这一屏的数据会立刻出现，没有转圈）；
 * - **不是**（从没拉过 / 参数变过 / 被数据版本号作废过）⇒ 拉一次。
 *
 * <p>⚠️ **切回树**什么都不做：树不画卡片（`loadPage` 会自己早退），
 * 数据留着是为了下一次切卡片能直接复用。
 */
watch(treeCards, (cards) => {
  if (!onLearnRoute.value || !cards) return
  if (pageLoadedKey && pageLoadedKey === currentPageKey() && !loadFailed.value) return
  loadPage()
})


onMounted(refresh)
</script>

<style scoped>
/* 2026-11-08 晚（代码级复刻 book-bak 的 .main__inner）：**版心 = 1160 居中 + 34/44/60 内距**。
   ⚠️ 之前那条「解除 920 限宽、树满宽」的 `.learn :deep(.ct--nav){max-width:none}` 删了 ——
   它把 CategoryTree 里照老稿写的 1160 盖掉了（computed style 实测踩过：内联在样式表的
   max-width 赢，树铺满全宽，跟老稿的"一栏书页"完全两个样子）。
   ⚠️ 老稿的 1160 **含**左右 44 内距（内容净宽 1072）⇒ padding 写在 .learn 自己身上；
   .app-content 对这一页的 padding 整个归零（AppLayout 的 :has(.learn) 那条），
   uiPrefs 的 learnPadX / learnPadY 两个滑块改由 .learn 消费（默认值改成 44 / 34）。
   ⚠️ 选择器写成 `.learn.learn`：AppLayout 的 `:has(...) > :where(*) { max-width:none }`
   与本规则**同分**（scoped 属性各 +1），样式表顺序靠后的它赢 —— 双类名抬一级，稳赢。 */
.learn.learn {
  display: flex;
  flex-direction: column;
  gap: var(--sp-12);
  width: 100%;
  max-width: 1160px;                       /* .main__inner 的 max-width */
  margin: 0 auto;                          /* （.app-content > * 已有 auto，这里写明不依赖它） */
  padding: 22px 44px 60px;
}

/* 录入（原 .composer 六条规则）：录入框已统一收到首页，这里跟着删。
   连着删掉的还有 `.composer :deep(.el-textarea__inner)`（textarea 早就不存在了）、
   `draft` / `editorKey` / `saving` 三个 state、`save()`、MilkdownEditor 的 import。 */

/* 筛选条：一行。面包屑 2026-11-08 移到顶栏后这一行只剩 TopicFilters
   ⇒ 靠右停在原来的位置（原来是面包屑 flex:1 把它顶到右边的）。 */
.cc-toprow {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--sp-8);
}

/* 卡片网格：列数由宽度算出来，卡片等高
 *
 * 列数从来没写死，是 `repeat(auto-fill, minmax(N, 1fr))` 由**实际可用宽度**算出来的。
 * 1920 窗口下：视口 − 侧边栏 264 − 内容区左右 padding 64 = 1592 → 3 列 × 521px。
 * **侧边栏宽度可调之后这个数会跟着变**（200→360），所以列数不写死才是对的。
 *
 * ⚠️ **卡片宽度是被 `--w-grid` 自己封住的，不需要再加一个列宽上限**（用户问过要不要
 * "列宽封顶、超宽就多排列"，结论是这件事本来就在发生）：列数 = floor((可用宽 + gap)/(400 + gap))，
 * 列宽在"刚好塞不下一列"时被 `1fr` 拉到最大，上界约 **537px**（3 列撑到 1641px 可用宽时
 * 第 4 列就塞不下了）。**屏幕再宽只是多排列，卡片不会被拉成一条**，也因此不需要
 * `justify-content` —— 1fr 永远把宽度吃满，写了也是空规则。
 *
 * ⚠️ **1440 读宽上限已经在 AppLayout 里对这一页解除了**（`.app-content:has(.learn)`）。
 * 留着它，浏览器一放大、视口宽度掉下来跨过 1440，**卡片宽度当场停住**，两侧开始长空白，
 * 而且屏幕越宽空白越宽 —— 用户原话："很明显缩放的更好看"、"拉满就两侧空一大片"。
 *
 * ⚠️ **列宽下限 --w-grid 不参与任何"疏密"调节**（见 base.css §5.2）：列宽是**内容单位**，
 * 跟着放大就会在某个值上突然放不下三列 → 3 列掉成 2 列，版式整个变样。
 * 要调它就是单独改 token 的值，不是把它接进某个滑块。
 *
 * 视口再窄就自然掉到 2 列，不加断点。
 */
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(var(--w-grid), 1fr));
  gap: var(--sp-11);
}

/* 加载中的同构骨架网格：与真实卡片网格**同一套栅格与间距**
   ⇒ 骨架卡片的位置/宽度与真实卡片完全一致，布局不跳。
   ⚠️ 骨架卡片本身是 KbSkeletonCard（全局样式），这里只负责栅格。 */
.learn__skeleton {
  pointer-events: none;
  user-select: none;
}

/* 分页条 .pager 已提到 assets/kb.css（考试记录页也是"服务端分页的列表"，
   两页共用一份 —— 重复 2 处即收敛）。 */
</style>
