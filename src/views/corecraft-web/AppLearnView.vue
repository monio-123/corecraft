<template>
  <div class="learn">
    <!-- 这里原来还有一条录入框（MilkdownEditor）。本页的职能是**浏览 + 归类**：
         目录筛选 / 时间筛选 / 类型筛选 / 搜索 / 卡片网格 / 拖拽归类，录入框只是顶部一条，
         名字叫「随手记」名不副实。录入已统一收到首页，这一页改成纯浏览页，
         侧边菜单同步改名「知识点」。 -->

    <!-- 面包屑 + 筛选条：**只在卡片视图里**。
         树视图那一屏回答的是"这本书里有什么"（结构）—— 筛知识点、考这个目录对它没意义，
         那些操作在卡片视图那一层。

         ⚠️ 面包屑**从当前那一级目录开始**（没有「全部」根项，2026-11-03 用户拍板：
         「这个全部可以不要 没有应用场景 直接去掉就行 第一个就直接是一级目录」）。

         ⚠️ **只有两级及以上才画**（2026-11-04 用户拍板：「一级目录点进的卡片栏 就不展示上面
         那么 一级目录的名字了 因为只有一个」）⇒ 一级目录的卡片视图**上面什么都不显示**
         （一个孤零零的名字不回答任何问题）；从二级目录点进来才画
         「一级目录 › 二级目录」——那才是"我在哪本书的哪一节"。 -->
    <CategoryCrumbs
      v-if="!isTree"
      :category-id="currentCategoryId"
      :min-level="2"
      @pick="setCategoryFilter"
    />

    <!-- 筛选：一行，不再两排 chip 糊满屏 -->
    <div v-if="!isTree" class="bar">
      <div class="bar__cats">
        <!-- 时间范围：默认「不限」（不限时间，但列表按新的在前 —— 服务端本来就是这个顺序）。
             ⚠️ **目录维度不再占药丸位**：选目录走侧边栏（那儿才是目录的入口），
             「全部目录」= 没选中任何目录的**默认态** —— 是个状态，不该长成可点的东西
             （原来那个「全部 N」药丸点进去是 317 条平铺，谁点它呀）。
             ⚠️ **「待归类」也不占药丸位**（2026-11-03）：它只剩首页那一张卡这一个入口。
             下拉里每一项带条数：数字答的是"我选了它会看到多少"。 -->
        <el-popover
          v-model:visible="rangePanelVisible"
          :width="200"
          trigger="click"
          placement="bottom-end"
          :teleported="false"
        >
          <template #reference>
            <button class="pill pill--filter" :class="{ 'is-on': timeRange !== DEFAULT_RANGE }">
              <span class="pill__label">{{ rangeLabel(timeRange) }}</span>
              <el-icon class="pill__caret"><ArrowDown /></el-icon>
            </button>
          </template>
          <div class="tagpanel">
            <button
              v-for="r in TIME_RANGES"
              :key="r.kind"
              class="tagpanel__item"
              :class="{ 'is-on': timeRange === r.kind }"
              @click="pickRange(r.kind)"
            >
              <span class="tagpanel__name">{{ r.label }}</span>
              <span class="tagpanel__count">{{ rangeCounts.get(r.kind) || 0 }}</span>
            </button>
          </div>
        </el-popover>

        <!-- ⚠️ **这里原来有一个「待归类」药丸**（2026-11-03 用户拍板删掉）。
             原话：「待归类的 只留一个入口吧我觉得 就是首页的那个待归类分类可以跳过去
             在这些一级目录中直接把这个待归类删除掉」⇒ **待归类只剩首页那一张卡**这一个入口。

             为什么删得对：它是个**筛选值**，而筛选条上其它几个（时间 / 类型）都是
             "这一屏的哪一维"，只有它是"另一个桶"。留着它，筛选条就多出第四种形状。
             ⇒ 首页「待归类」那张卡跳过来时，这一屏就是待归类那批（URL 里带
             `?category=UNCATEGORIZED`），**状态在 URL 里，不用药丸告诉用户**——
             这一屏另有信号：「AI 归类」按钮只在这一屏出现。 -->
      </div>

      <div class="bar__right">
        <!-- ⚠️ **这里原来有一个「目录」pill**（切回目录树的出口）。
             2026-11-04 用户拍板删掉：**树 ⇄ 卡片的切换统一收进侧边栏目录行的 hover 按钮**
             （一个地方切，不再两个地方各切一次、图标还不一样）。

             二级目录的卡片视图怎么回那本书的树？**同一件事** —— 侧边栏那一行（一级目录）
             hover 出按钮，点它就是树。⚠️ 这也是为什么侧边栏那个按钮必须记在
             **那本书**身上（`ui.view.category.<一级目录 id>`），不是当前这一级。 -->

        <!-- 只在「待归类」这一屏出现，处理的就是屏幕上这批。
             不做成全局入口是因为它没有"处理所有待归类"的独立语义——**看到什么就处理什么**，
             按钮上把条数写出来，就不会有人以为它连没显示出来的那些也一起动了。 -->
        <button v-if="showOrganize" class="pill pill--ai" @click="organizeOpen = true">
          <el-icon><MagicStick /></el-icon>
          AI 归类 {{ scopedTopics.length }}
        </button>

        <!-- 考试：考**当前筛选出来的这些**（默认 = 全部）。
             范围只有这一种：屏幕上筛出什么就考什么。⚠️ **未归类的条目不参与**——
             待归类不是目录（用户 2026-10-23 定），里面也是没整理过的东西。
             正文不由前端带过去：只给 id 列表，服务端自己按 id 捞（归属按登录态收）。 -->
        <button v-if="showExam" class="pill pill--ai" @click="openExam">
          <el-icon><EditPen /></el-icon>
          考试 {{ examScope.length }}
        </button>

        <!-- 类型也收进下拉，和标签并排。同一把尺子：90% 的时候想看全部，
             不该为"偶尔筛一下学习类"在首屏摆一排药丸。
             类型是单选（一律"什么性质"），标签是多选（"涉及什么"），
             两个筛选互不干涉，可以叠加。
             药丸上**只显示当前选中的名字**（没选中才写「类型」）：写着"类型 笔记"
             是把这层筛选的**属性**和它的**取值**并排印出来，而类型只有这一个下拉，
             属性是白占的宽度。标签是同一套形状，跟着改。 -->
        <el-popover
          v-model:visible="typePanelVisible"
          :width="200"
          trigger="click"
          placement="bottom-end"
          :teleported="false"
          :persistent="false"
        >
          <template #reference>
            <button class="pill pill--filter" :class="{ 'is-on': typeFilter !== null }">
              <span class="pill__label" :title="typeFilterLabel || '类型'">{{ typeFilterLabel || '类型' }}</span>
              <el-icon class="pill__caret"><ArrowDown /></el-icon>
            </button>
          </template>
          <div class="tagpanel">
            <button class="tagpanel__item" :class="{ 'is-on': typeFilter === null }" @click="pickType(null)">
              全部类型
            </button>
            <p v-if="!topicTypes.length" class="tagpanel__empty">没有类型</p>
            <div v-else class="tagpanel__list">
              <button
                v-for="t in topicTypes"
                :key="t.id"
                class="tagpanel__item"
                :class="{ 'is-on': typeFilter === t.id }"
                @click="pickType(t.id)"
              >
                <span class="tagpanel__name">{{ t.label }}</span>
                <span class="tagpanel__count">{{ typeCountMap.get(t.id) || 0 }}</span>
              </button>
            </div>
          </div>
        </el-popover>

        <!-- ⚠️ **标签筛选已下线**（2026-11-03 用户拍板：「标签功能没啥用，从页面上去掉，
             后台逻辑暂时不去」）。
             这一格原来在这儿：一个下拉里 212 个标签（每行还带一个「⋯」菜单），
             实测那个浮层每次进卡片视图都要白付 **267ms**（EP 的 `persistent` 默认 true
             ⇒ 浮层内容在挂载那一刻就渲染出来）。标签没了，这一格也省了。

             ⚠️ **后端那套标签逻辑（表 / 接口 / 服务 / 前端 store 层）一行没动** ——
             用户明确说"后台逻辑暂时不去"，而且要在 md/skill 里写明这是**有意保留**，
             别让下一次清理把它当死代码删了（见 brove-skills/corecraft-web-context/SKILL.md
             「标签：界面已下线，后端有意保留」）。
             ⚠️ 因此**列表接口的 tag 过滤参数也没删**（后端还认），
             只是前端不再有"标签"这个筛选维度。 -->
      </div>
    </div>

    <!-- 树视图：**一个一级目录的整棵目录树**。
         点目录名 ⇒ 进那个目录的**卡片视图**（子树平铺）；点知识点叶子 ⇒ 进详情。
         ⚠️ 一级目录永远不会平铺全部 —— 一级目录是「一本书」，平铺出来是把整本书抖一遍。 -->
    <BookToc
      v-if="renderTree"
      :category="treeViewRoot"
      :active="pageActive"
      @pick="onTreePick"
      @pick-topic="goDetail"
    />

    <template v-else>
      <!-- 平铺。**网格形态**（没有选中目录）：这里显示的是当前筛选条件下的知识点。
           ⚠️ **加载中不画旧内容**：这一块整个换成转圈。
           旧列表杵在那儿的时候，"没变"和"正在变"长得一模一样 ——
           用户看到的就是"点了没反应，过几秒突然换了"。 -->
      <div v-if="loading" class="kb-center">
        <el-icon class="is-loading"><Loading /></el-icon>
        <p>加载中…</p>
      </div>

      <!-- 出错：拉不到的时候摆出来。错误详情由 request.js 弹（那里是全站唯一的
           提示出口），这里只摆状态，不重复弹一遍。 -->
      <div v-else-if="loadFailed" class="kb-center">
        <p class="kb-error">加载失败</p>
        <el-button @click="loadPage">重试</el-button>
      </div>

      <div v-else-if="pageRecords.length" class="grid">
        <TopicCard
          v-for="t in pageRecords"
          :key="t.id"
          :topic="t"
          @click="goDetail(t)"
          @delete="handleDelete"
        />
      </div>
      <!-- 列表为空时**什么都不画**：上面已经有筛选条件（目录 / 搜索词 / 类型），
           空白是自解释的。⚠️ 以前这里写着「没有待归类的知识点」「这个目录还是空的」
           「没搜到」「还没有知识点」四句 —— 加载中的那几秒它就在说"没有"，那是句假话
           （用户明明有）；现在不说了，也就没有假话。 -->

      <!-- 分页。只有超过一页才画：一页装得下的时候画个"1"是噪音。 -->
      <div v-if="pageTotal > pageSize" class="pager">
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

    <AiOrganizeDialog
      v-model="organizeOpen"
      :topics="scopedTopics"
      :categories="organizeCategories"
    />
  </div>
</template>

<script setup>
defineOptions({ name: 'CorecraftWebAppLearn' })
import { ref, computed, onMounted, onActivated, onDeactivated, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowDown, EditPen, Loading, MagicStick } from '@element-plus/icons-vue'
import {
  ensureTopicIndexLoaded, ensureCategoriesLoaded,
  loadTopicPage, getTopicIndex, getCategory, getCategories, scopeMatcher,
  getCategorySubtreeIds, getDataVersion,
  confirmDeleteTopic, UNCATEGORIZED, ALL,
  getTopicTypes, getTopicTypeLabel, resolveTopicTypeId
} from './useCorecraftWebStore'
import { aiEnabled } from './useAi'
import { openExamTab } from './exam'
import { keywordFromRoute, rangeFromRoute } from '../../utils/routeQuery'
import { TIME_RANGES, DEFAULT_RANGE, rangeLabel, rangeBounds, withinRange, isRangeKind } from '../../utils/timeRange'
import { canUseTree } from '../../utils/viewPref'
import CategoryCrumbs from './CategoryCrumbs.vue'
import BookToc from './BookToc.vue'
import TopicCard from './TopicCard.vue'
import AiOrganizeDialog from './AiOrganizeDialog.vue'

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

// 类型筛选。**只存本地 ref，不进 URL**——
// 目录筛选要进 URL 是因为侧边栏也在写它；类型只有这一个入口，没有第二个写方。
// 存 ref 的代价：刷新页面筛选会丢。目录筛选不丢是因为它本来就该能被链接分享。
const typePanelVisible = ref(false)
const typeFilter = ref(null)
const topicTypes = computed(() => getTopicTypes())
const typeFilterLabel = computed(() => (typeFilter.value ? getTopicTypeLabel(typeFilter.value) : ''))

function pickType(id) {
  typeFilter.value = id
  typePanelVisible.value = false
}

// 目录筛选的真相源是 URL。
//
// ⚠️ **不抄一份本地 ref**：以前是 `ref` + 一个 watch 同步，**两处可写** ——
// 药丸改了本地值但地址栏还停在 ?category=1，再点同一个侧边栏目录时路由无变化、
// watch 不触发，界面再也回不去。**URL 就是唯一真相**，读它不需要再抄一份。
const categoryFilter = computed(() => {
  const c = route.query.category
  if (c === undefined) return ALL
  if (c === UNCATEGORIZED) return UNCATEGORIZED
  return Number(c)
})
// 关键词：**直接从 URL 读**，不是 ref、也没有 watch。
// 原来筛选条里那个输入框和顶栏那个共享同一个 `?q=`，于是 keyword 既是 ref 又被
// watch 同步一次 —— 两处可写、一条同步路径，清空顶栏那个时下面这个就留住了旧词
// （两个框各存一份，漏的是"反方向"）。现在筛选条里那个输入框删了，**只有顶栏一个写方**，
// URL 就是唯一真相，读它不需要再抄一份到 ref 里。
const keyword = computed(() => keywordFromRoute(route))

/**
 * 选一个目录 = 进它的**卡片视图**（子树平铺）。
 *
 * <p>⚠️ **同时清掉 `tree`**：树视图（`?tree=`）和卡片视图（`?category=`）是两种形态，
 * 共存的话就得判"两个都带时听谁的" —— 那正是"入口多"的另一个名字。
 *
 * <p>⚠️ **这里原来还有"null = 清除目录筛选"那一支**（面包屑的根项「全部」点它）。
 * 面包屑的根项 2026-11-03 删掉了（用户：「这个全部可以不要 没有应用场景」）⇒
 * 调用方只剩两处（面包屑点目录、树里点目录名），**传进来的都是目录 id** ⇒ 那支删掉。
 */
function setCategoryFilter(id) {
  router.replace({
    path: route.path,
    query: { ...route.query, tree: undefined, category: id }
  })
}

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
const rangePanelVisible = ref(false)
const timeRange = computed(() => rangeFromRoute(route))

/** 当前时间窗的起止（不限时间 = null）。 */
const timeBounds = computed(() => rangeBounds(timeRange.value))

function pickRange(kind) {
  const next = isRangeKind(kind) ? kind : DEFAULT_RANGE
  // 不限时间就是"不带 range 参数"（vue-router 会把 undefined 的 query 删掉）
  router.replace({
    path: route.path,
    query: { ...route.query, range: next === DEFAULT_RANGE ? undefined : next }
  })
  rangePanelVisible.value = false
}

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
    keyword: keyword.value
  }
  if (categoryFilter.value === UNCATEGORIZED) {
    params.uncategorized = true
  } else if (categoryFilter.value !== ALL) {
    params.categoryIds = [...getCategorySubtreeIds(categoryFilter.value)].join(',')
  }
  // **时间范围必须在服务端**：分页是服务端做的，前端筛完再发就没有分页可言。
  // 起止由前端按本地时区算好（utils/timeRange.js）⇒ 和后端、和分面数字同一口径。
  const bounds = timeBounds.value
  if (bounds) {
    params.createdFrom = bounds.from
    params.createdTo = bounds.to
  }
  if (typeFilter.value != null) params.typeId = typeFilter.value
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

/** 拉当前页。 */
async function loadPage() {
  // 树视图**不画卡片** ⇒ 这一页用不上 ⇒ 不拉。
  // ⚠️ 列表那条查询要读正文（实测读全文 4416ms、不读 78ms）⇒ 树视图下拉它
  // 就是白等几秒。树要的数据在**结构索引**里（标题/类型/目录），那份已经在了。
  if (isTree.value) {
    loading.value = false
    loadFailed.value = false
    pageRecords.value = []
    pageTotal.value = 0
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

/**
 * 这条知识点的**类型档**（收口后的真实 id）。
 *
 * <p>typeId 可能悬空（字典项被软删）⇒ 统一当默认档。
 * **只有这一处**：筛选（matchType）和类型下拉上的数字（typeCountMap）
 * 问的是同一句话，各写一遍的话，字典一改就有一处对不上
 * （症状是"下拉里有数、点进去没有"）。
 */
function typeIdOf(t) {
  return resolveTopicTypeId(t.typeId)
}

// 类型筛选。**普通函数**（原来是个 computed）。
// ⚠️ 那个 computed 的理由（「目录页的树要跟着筛选重摊」）**已经不成立** ——
// 树视图画的是**结构**（目录 + 知识点叶子），它不吃类型筛选，从来没有问过这个谓词。
// ⇒ 留着 computed 只是多一层包装；响应式由**消费方**保证（`topicsInScope` 本身是
// computed，它在求值时读 `typeFilter.value` ⇒ 依赖照样被记上）。
const matchType = (t) => typeFilter.value == null || typeIdOf(t) === typeFilter.value

// ==================== 两种形态：目录页 / 网格 ====================

/**
 * 现在是不是「**树视图**」。
 *
 * <p>判据：URL 上带了一个**一级目录** id（`?tree=`）且那个目录还在。
 * 树视图只对一级目录成立 —— 二级以下的目录要看结构，回它所属那本书的树。
 *
 * <p>⚠️ **它和卡片视图（`?category=`）互斥**：两个 URL 参数各管一种形态，
 * 写的时候互斥（`setCategoryFilter` 会清掉 `tree`）⇒ 不用判"两个都带时听谁的"。
 */
const isTree = computed(() => {
  const t = route.query.tree
  if (t == null) return false
  // 「能不能用树」这条判据只有一份（utils/viewPref.js 的 canUseTree）——
  // 原来这里和那边的兜底各写一遍 `!parentId`，两边漂了的话侧边栏会把二级目录
  // 送成 ?tree=<id>，而这一屏判它不是树 ⇒ 退成全量平铺，界面上一点异常都看不出来。
  return canUseTree(getCategory(Number(t)))
})

/**
 * 树的根：**记下来的那一本**，不是"当前路由上的那一本"。
 *
 * <p>⚠️ **为什么不能直接用路由上的**（2026-11-03 实测踩出来）：
 * 路由是全局的 —— 从树里点进知识点详情时，地址变成 `/app/topic/xxx`，
 * `route.query.tree` 随之**变成 undefined** ⇒ 判据 `v-if="isTree"` 翻成 false
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

watch(
  () => route.query.tree,
  (t) => { if (t != null) treeViewRootId.value = Number(t) },
  { immediate: true }
)

/** 树视图的根（现取的那本）。目录被删了 ⇒ null。 */
const treeViewRoot = computed(() => (treeViewRootId.value == null ? null : getCategory(treeViewRootId.value)))

/**
 * 树上**画不画**。
 *
 * <p>两条：
 * - 在树视图里 ⇒ 画。
 * - **已经离开这一屏**（`!onLearnRoute`，比如在知识点详情页）⇒ **继续挂着**，
 *   树才有得可"保持"（见上面 `treeViewRootId` 那段）。
 *
 * <p>⚠️ 离开这一屏时**不能新建**：那时候 `route.query.tree` 已经没有这一本了
 * ⇒ 用 `treeViewRoot`（记下来的那本），没有记过（为 null）就不画
 * —— 不给 `BookToc` 传 null（它的 `category` 是 required）。
 *
 * <p>⚠️ 跟下面那个 `treeRoot`（**当前选中目录所属的那本书**，给「切回目录树」按钮用）
 * **是两个东西**：那个从 `categoryFilter` 往上找，这一个是**树视图正在显示的那一本**
 * —— 树视图下 `categoryFilter` 是空的 ⇒ 那个算出来是 null。
 */
const renderTree = computed(() => treeViewRoot.value != null && (isTree.value || !onLearnRoute.value))

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
 * @param {{time?: {from:string,to:string}|null, type?: ((t:any)=>boolean)|null}} dims
 *        `time` 不给 = 不按时间筛；`type` 不给 = 用**当前选中的类型**。
 */
function topicsInScope({ time, type } = {}) {
  return allTopics.value.filter(
    (t) =>
      withinRange(t.createdAt, time) &&
      matchCategory(t) &&
      (type ? type(t) : matchType(t))
  )
}

/**
 * 时间下拉里每一项的条数：**排除时间这一维自己**，只用其它维度已生效的条件去数
 * （和下面的分面计数同一条规矩）。
 */
const rangeCounts = computed(() => {
  const out = new Map()
  for (const r of TIME_RANGES) out.set(r.kind, topicsInScope({ time: rangeBounds(r.kind) }).length)
  return out
})

// 原子谓词就是上面那两个（`matchCategory` / `matchType`）：
// 算哪个维度的数字，就挑**除它以外**的那几个交给 `topicsInScope`。
// **不含关键词**：关键词已经在服务端应用了（索引就是"关键词下的全集"）。

// ⚠️ **这里原来还有一个 `uncategorizedCount`**（「待归类」药丸上的那个数字）。
// 药丸删了之后它没有消费方 ⇒ 一并删掉（别留一个"算一遍没人看"的 computed）。

// 类型下拉每项的数字
const typeCountMap = computed(() => {
  const counts = new Map()
  for (const ty of topicTypes.value) {
    counts.set(ty.id, topicsInScope({
      time: timeBounds.value,
      type: (t) => typeIdOf(t) === ty.id
    }).length)
  }
  return counts
})

/**
 * 当前筛选条件下的**全集**（结构索引那份）：目录 ∧ 类型 ∧ 时间。
 *
 * <p>它是"这一屏筛出来的所有条目"，不是"这一页显示的那几条"——
 * 考试要考的是全部，AI 归类要处理的是全部，分面计数要数的也是全部。
 */
const scopedTopics = computed(() => topicsInScope({ time: timeBounds.value }))

// ==================== 考试 ====================

/**
 * 能考的那些：**当前筛选结果里排除未归类的，再排除还没写正文的**。
 *
 * <p>未归类不是目录、也不许考（用户 2026-10-23 定）；它是筛选条上的一个固定桶，
 * 里面是没整理过的东西（还混着一条 3 万字的垃圾）。
 *
 * <p>⚠️ **没写正文的也不许考**（2026-10-26）：送模型的材料是「标题 + 正文」，
 * 占位只有标题没有正文 ⇒ 模型无料出题（空题、少题或胡编）。
 * 判据只有一条：**正文非空**（前后端同一口径，后端那条在 `KpTopicServiceImpl`）。
 */
const examScope = computed(() =>
  scopedTopics.value.filter((t) => t.categoryId != null && t.hasContent)
)

/** 考试入口的显示条件：有东西可考（和「AI 归类」一样，AI 关了就不画这个按钮） */
const showExam = computed(() => aiEnabled.value && examScope.value.length > 0)

/**
 * 去考试：**在当前浏览器开一个新标签页**。
 *
 * <p>范围 = 当前筛选出来的那些（见 `examScope`）。名字带过去，考试页顶栏显示它。
 *
 * <p>URL 怎么拼、弹窗被拦了怎么办，都在 `exam.js` 的 `openExamTab` 里。
 */
function openExam() {
  if (!examScope.value.length) return
  const name = currentCategoryId.value ? getCategory(currentCategoryId.value)?.name || '' : '全部'
  openExamTab(
    examScope.value.map((t) => t.id),
    name,
    `list-${categoryFilter.value}`,
    currentCategoryId.value
  )
}


// ==================== AI 归类 ====================

const organizeOpen = ref(false)

/**
 * AI 入口一律 `v-if` 不画禁用按钮：关闭的语义是"这个功能对他不存在"，不是"它坏了"。
 * 另外两个条件：得正在「待归类」这一屏（别的屏上这个按钮没有意义），而且确实有东西可归。
 */
const showOrganize = computed(
  () => aiEnabled.value && categoryFilter.value === UNCATEGORIZED && scopedTopics.value.length > 0
)

// 包一层 computed 才是响应式的：getCategories() 只是读 categories.value，
// 直接在模板里调拿到的会是一份普通数组快照，弹框里新增目录后这里不跟着变。
const organizeCategories = computed(() => getCategories())

// ==================== 切回目录树 ====================
//
// ⚠️ **这里原来有一整段「切回目录树」**（`treeRoot` / `showTreeView` / `switchToTree`）。
// 2026-11-04 用户拍板：**树 ⇄ 卡片的切换统一收进侧边栏目录行的 hover 按钮**
// （「这个切换放到侧边栏的目录上加一个按钮 hover才显示 这样的话目录树的这个用卡片看
// 这个按钮就也可以去掉了」）⇒ 这一屏不再有第二个切换出口，卡片视图右上那个
// 「目录」pill 也一并删掉（见模板）。
//
// ⇒ 这一段整体删除：**「哪本书的树」这个推导也不在这里算了** ——
// 侧边栏那一行就是那本书，它自己知道。

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
    ensureTopicIndexLoaded(false, keyword.value),
    loadPage()
  ])
}

function goDetail(t) {
  router.push({ name: 'AppTopicDetail', params: { id: t.id } })
}

/**
 * 树里点目录名 ⇒ 进那个目录的**卡片视图**（该目录子树的知识点平铺）。
 *
 * <p>和行首那个箭头分工：箭头 = 就地展开/收起，目录名 = 去看内容。
 * 两个动作长得不一样（一个是图标、一个是名字），不会点错。
 */
function onTreePick(c) {
  setCategoryFilter(c.id)
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
  if (activatedOnce) loadPage()
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
watch(getDataVersion, () => { if (onLearnRoute.value) loadPage() })

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
 * <p>关键词也在这个 watch 里：**索引是"关键词下的全集"**（服务端筛的），
 * 关键词一变索引就得跟着重拉 —— 不拉的话侧边栏的条数还是"不筛关键词"的那份。
 * 关键词没变时它是空操作（store 认得"这份就是当前的"）。
 */
watch([categoryFilter, typeFilter, keyword, timeRange], () => {
  // ⚠️ 关键词是**直接从 URL 读**的 ⇒ 离开这一页时它会变成空（详情页的 URL 里没有 ?q=）。
  // 那一瞬间**不是**"用户清了搜索词"，是"这一页暂时不在眼前" ⇒ 不在这儿发请求。
  if (!onLearnRoute.value) return
  page.value = 1
  ensureTopicIndexLoaded(false, keyword.value)
  loadPage()
})

onMounted(refresh)
</script>

<style scoped>
.learn {
  display: flex;
  flex-direction: column;
  /* 偏好面板的「上下」（utils/uiPrefs.js 的 LEARN_GAP）。筛选条 / 标签面板 / 卡片区
     这几个区块之间的距离，用户可直接拖。 */
  gap: var(--learn-gap, var(--sp-12));
}

/* 录入（原 .composer 六条规则）：录入框已统一收到首页，这里跟着删。
   连着删掉的还有 `.composer :deep(.el-textarea__inner)`（textarea 早就不存在了）、
   `draft` / `editorKey` / `saving` 三个 state、`save()`、MilkdownEditor 的 import。 */

/* 筛选条：一行 */
.bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-11);
  flex-wrap: wrap;
}
.bar__cats {
  display: flex;
  align-items: center;
  gap: var(--sp-7);
  flex-wrap: wrap;
}
.bar__right {
  display: flex;
  align-items: center;
  gap: var(--sp-10);
  margin-left: auto;
}

.pill {
  font-size: var(--text-md);
  /* 只把上下内距接进偏好面板（LEARN_PILL_PY），左右保持 --sp-10：药丸是横着的，
     左右跟着松会让整条筛选条在宽屏上散开，而上下松紧才是这一行真正在调的东西。
     兜底值跟 LEARN_PILL_PY.def 一起改成 --sp-3：两处不一致的话，
     "没设过偏好的用户看到的药丸高度"会和"点恢复默认之后的高度"不一样。 */
  padding: var(--learn-pill-py, var(--sp-3)) var(--sp-10);
  border: 1px solid transparent;
  border-radius: var(--radius-xl);
  background: #f1f2f4;
  color: #6b7280;
  cursor: pointer;
  transition: all 0.15s;
  display: inline-flex;
  align-items: center;
  gap: var(--sp-5);
}
.pill:hover {
  background: #e8eaed;
  color: var(--cc-text-1);
}
/* 选中态。⚠️ **原来是黑底白字**（`--cc-text-1` 那一档）—— 用户 2026-11-03 说
   「这几个框勾选之后变成黑色我感觉很丑」⇒ 换成**浅蓝底 + 蓝字 + 蓝边**。
   颜色取自 token（--cc-primary-soft / --cc-primary / --cc-primary-border），
   和全站"选中"的视觉语言一致（侧边栏当前项、卡片 hover 边框都是这一套）。 */
.pill.is-on {
  background: var(--cc-primary-soft);
  color: var(--cc-primary);
  border-color: var(--cc-primary-border);
}
/* 「待归类」的落点反馈。放在 .is-on 之后：它常常正好是当前筛选的那个，
   同优先级靠后者覆盖，让"正在放这里"压过"已选中"。 */
.pill.is-drop {
  background: var(--cc-primary);
  color: var(--cc-surface);
  box-shadow: 0 0 0 3px rgba(43, 127, 255, 0.16);
}
.pill--filter {
  background: transparent;
  border-color: #e2e5e9;
}
/* 下拉药丸的文字位：显示筛选值（没选中时是「类型」「标签」两个字）。
   固定 min-width 是为了让「类型」和「标签」两个字宽窄不同时药丸**一样宽** ——
   这两个词谁选中谁变长，不锁宽的话药丸宽度跟着选中状态跳，选一下整行就抖一下。 */
.pill__label {
  min-width: 28px;
  max-width: 108px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
/* AI 归类：唯一一个"会改数据"的控件，给个主色描边跟纯筛选药丸拉开。
   不做成实心主色按钮——它旁边就是搜索框和两个筛选下拉，一主一次两个实心太吵。 */
.pill--ai {
  background: transparent;
  border-color: var(--cc-primary);
  color: var(--cc-primary);
}
.pill--ai:hover {
  background: rgba(43, 127, 255, 0.08);
  color: var(--cc-primary);
}
.pill__caret {
  font-size: var(--text-sm);
}

/* 「待归类」的条数：默认不显形，悬停才出来（跟目录计数同一个口径）。
   **位置留着**（不 display:none）—— 显形时药丸长一截，鼠标正指着它的时候
   布局跳一下最难伺候。 */
.pill__n {
  opacity: 0;
  transition: opacity 0.15s;
}
.pill:hover .pill__n {
  opacity: 1;
}

/* 标签下拉 */
.tagpanel {
  display: flex;
  flex-direction: column;
  gap: var(--sp-1);
  max-height: 288px;
  overflow-y: auto;
}
/* 一行 = 行容器。**现在每一行都是 button**（标签那批行走了之后，
   「⋯」菜单那一支也没了 ⇒ 不再需要"行容器 + 里面再放按钮"那套拆法）。
   「全部类型」那行不带数字，还是同样的 button，所以 cursor/字体这些都留着。 */
.tagpanel__item {
  display: flex;
  align-items: center;
  gap: var(--sp-10);
  padding: var(--sp-7) var(--sp-10);
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  font-size: var(--text-base);
  color: #4b5563;
  cursor: pointer;
  text-align: left;
}
.tagpanel__item:hover {
  background: var(--cc-surface-2);
}
.tagpanel__item.is-on {
  background: var(--cc-primary-soft);
  color: var(--cc-primary);
  font-weight: 500;
}
/* 行里的名字。名字太长得靠 ellipsis 让位给右边的数字，
   不设的话 flex 子项的 min-width:auto 会顶着把行撑宽。 */
.tagpanel__name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tagpanel__count {
  font-size: var(--text-sm);
  color: var(--cc-text-4);
  flex-shrink: 0;
}
.tagpanel__empty {
  margin: 0;
  padding: var(--sp-10);
  font-size: var(--text-md);
  color: var(--cc-text-4);
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
  /* 偏好面板的「卡片间距」（LEARN_GRID_GAP）。 */
  gap: var(--learn-grid-gap, var(--sp-11));
}

/* 分页条：居中一行，贴在卡片区下面。 */
.pager {
  display: flex;
  justify-content: center;
  padding: var(--sp-4) 0 var(--sp-2);
}
</style>
