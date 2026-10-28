<template>
  <div class="learn">
    <!-- 这里原来还有一条录入框（MilkdownEditor）。本页的职能是**浏览 + 归类**：
         目录筛选 / 标签筛选 / 搜索 / 卡片网格 / 拖拽归类，录入框只是顶部一条，
         名字叫「随手记」名不副实。录入已统一收到首页，这一页改成纯浏览页，
         侧边菜单同步改名「知识点」。 -->

    <!-- 面包屑：只在层级 > 1 时出现。层级本身不常驻展示，靠拖拽树（放）和 hover 级联（进）。
         和知识点详情页共用 CategoryCrumbs；这里要 replace 不 push，免得每切一次目录
         就往浏览历史里塞一条。 -->
    <CategoryCrumbs
      :category-id="currentCategoryId"
      :min-level="2"
      @pick="setCategoryFilter"
    />

    <!-- 筛选：一行，不再两排 chip 糊满屏 -->
    <div class="bar">
      <div class="bar__cats">
        <button class="pill" :class="{ 'is-on': categoryFilter === ALL }" @click="setCategoryFilter(ALL)">
          全部 {{ allCount }}
        </button>
        <button
          class="pill"
          :class="{ 'is-on': categoryFilter === UNCATEGORIZED }"
          @click="setCategoryFilter(UNCATEGORIZED)"
        >待归类 {{ uncategorizedCount }}</button>
      </div>

      <div class="bar__right">
        <!-- 只在「待归类」这一屏出现，处理的就是屏幕上这批。
             不做成全局入口是因为它没有"处理所有待归类"的独立语义——**看到什么就处理什么**，
             按钮上把条数写出来，就不会有人以为它连没显示出来的那些也一起动了。 -->
        <button v-if="showOrganize" class="pill pill--ai" @click="organizeOpen = true">
          <el-icon><MagicStick /></el-icon>
          AI 归类 {{ scopedTopics.length }}
        </button>

        <!-- 出卷：考**当前筛选出来的这些**（默认 = 全部）。
             范围只有这一种：屏幕上筛出什么就考什么。⚠️ **未归类的条目不参与**——
             待归类不是目录（用户 2026-10-23 定），里面也是没整理过的东西。
             正文不由前端带过去：只给 id 列表，服务端自己按 id 捞（归属按登录态收）。 -->
        <button v-if="showExam" class="pill pill--ai" @click="openExam">
          <el-icon><EditPen /></el-icon>
          出卷 {{ examScope.length }}
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

        <!-- 标签收进下拉：低频操作，不该占首屏 -->
        <el-popover
          v-model:visible="tagPanelVisible"
          :width="260"
          trigger="click"
          placement="bottom-end"
          :teleported="false"
        >
          <template #reference>
            <button class="pill pill--filter" :class="{ 'is-on': tagFilter !== null }">
              <span class="pill__label" :title="tagFilter || '标签'">{{ tagFilter || '标签' }}</span>
              <el-icon class="pill__caret"><ArrowDown /></el-icon>
            </button>
          </template>
          <div class="tagpanel">
            <button class="tagpanel__item" :class="{ 'is-on': tagFilter === null }" @click="pickTag(null)">
              全部标签
            </button>
            <div v-if="tagList.length" class="tagpanel__list">
              <!-- 整行是 div 不是 button：行尾还要再放一个「⋯」按钮，
                   <button> 套 <button> 是非法 HTML，浏览器会把里层那个提到外层外面，
                   结果布局和点击都乱。拆成"选它的按钮"+"操作按钮"两个兄弟节点。 -->
              <div
                v-for="t in tagList"
                :key="t.name"
                class="tagpanel__item"
                :class="{ 'is-on': tagFilter === t.name }"
              >
                <button class="tagpanel__pick" @click="pickTag(t.name)">
                  <span class="tagpanel__name">{{ t.name }}</span>
                  <span class="tagpanel__count">{{ t.count }}</span>
                </button>
                <TagRowMenu :tag="t" />
              </div>
            </div>
            <p v-else class="tagpanel__empty">没有标签</p>
          </div>
        </el-popover>
      </div>
    </div>

    <!-- 平铺。这里显示的是**当前目录整棵子树**的知识点（见 matchCategory）。
         卡片上不放目录入口：目录一多就没法在小 chip 里选了，
         归类和改类都是"把卡片拖到右侧浮出的那棵树"这一个动作。

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

    <AiOrganizeDialog
      v-model="organizeOpen"
      :topics="scopedTopics"
      :categories="organizeCategories"
    />
  </div>
</template>

<script setup>
defineOptions({ name: 'CorecraftWebAppLearn' })
import { ref, computed, onMounted, onActivated, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowDown, EditPen, Loading, MagicStick } from '@element-plus/icons-vue'
import {
  ensureTopicIndexLoaded, ensureCategoriesLoaded, ensureAllTagsLoaded,
  loadTopicPage, getTopicIndex, getCategory, getCategories, topicsInScope,
  getCategorySubtreeIds, getDataVersion,
  getAllTags, confirmDeleteTopic, encodeTag, UNCATEGORIZED, ALL,
  getTopicTypes, getTopicTypeLabel, resolveTopicTypeId
} from './useCorecraftWebStore'
import { aiEnabled } from './useAi'
import { openExamTab } from './exam'
import { keywordFromRoute } from '../../utils/routeQuery'
import CategoryCrumbs from './CategoryCrumbs.vue'
import TopicCard from './TopicCard.vue'
import TagRowMenu from './TagRowMenu.vue'
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

const tagPanelVisible = ref(false)
const tagFilter = ref(null)

// 类型筛选。和 tagFilter 一样**只存本地 ref，不进 URL**——
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

// 目录筛选的真相源是 URL：侧边栏的目录链接和这里的两个药丸都在写它，本地 ref 只是它的镜像。
// 两个方向的同步缺一不可——
//   只读不写：侧边栏换目录时组件复用不重挂载，筛选不跟着变（URL 变了、列表没反应，只能硬刷新）；
//   只写不读：药丸改了本地值但地址栏还停在 ?category=1，再点同一个侧边栏目录时路由无变化、
//             watch 不触发，界面再也回不去。
function readCategoryQuery(c) {
  if (c === undefined) return ALL
  return c === UNCATEGORIZED ? UNCATEGORIZED : Number(c)
}

const categoryFilter = ref(readCategoryQuery(route.query.category))
// 关键词：**直接从 URL 读**，不是 ref、也没有 watch。
// 原来筛选条里那个输入框和顶栏那个共享同一个 `?q=`，于是 keyword 既是 ref 又被
// watch 同步一次 —— 两处可写、一条同步路径，清空顶栏那个时下面这个就留住了旧词
// （两个框各存一份，漏的是"反方向"）。现在筛选条里那个输入框删了，**只有顶栏一个写方**，
// URL 就是唯一真相，读它不需要再抄一份到 ref 里。
const keyword = computed(() => keywordFromRoute(route))

function setCategoryFilter(key) {
  // 值为 undefined 的 query 会被 vue-router 直接删掉，"全部"就是不带 category 参数
  router.replace({ path: route.path, query: { ...route.query, category: key === ALL ? undefined : key } })
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
  if (typeFilter.value != null) params.typeId = typeFilter.value
  if (tagFilter.value) params.tag = tagFilter.value
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
  let seq = 0
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
 * 这条知识点有没有某个标签。**只有这一处判据** ——
 * 筛选（matchTag）和标签清单上的数字（tagList）问的是同一句话，
 * 各写一遍的话，标签的数据形状一变就有一处对不上
 * （症状是"下拉里有数、点进去没有"）。
 */
function hasTag(t, name) {
  return (t.tags || []).some((x) => encodeTag(x) === name)
}

function matchTag(t) {
  return tagFilter.value === null || hasTag(t, tagFilter.value)
}

/**
 * 选一个目录 = 看**整棵子树**。
 *
 * 为什么不是"只显示直接挂载"：那样进到「集合」只能看到 3 条，
 * 它父目录里另外 20 条就凭空消失了，层级会变成"建得进、看不见"。
 * 子树平铺让页面永远是一排卡片，层级靠侧边栏当前高亮的那一项表达，
 * 不靠额外的子目录区块。（原来这里写的是"靠卡片上的目录 chip"，
 *  那个 chip 早就撤掉了，注释和代码对不上。）
 *
 * **范围怎么算只有一份**（store 的 `topicsInScope`）：列表筛出来的这批，
 * 和考试要考的必须是同一批。各算一遍，目录一改就可能对不上——**而且界面上什么异常都看不到**。
 */
const scopeIds = computed(() => new Set(topicsInScope(categoryFilter.value).map((t) => t.id)))

function matchCategory(t) {
  return scopeIds.value.has(t.id)
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

// 类型筛选
function matchType(t) {
  if (typeFilter.value === null) return true
  return typeIdOf(t) === typeFilter.value
}

// ==================== 分面计数 ====================

/**
 * 算某一维度的数字时**排除自己那一维**，只用其它维度已生效的条件去数。
 *
 * 原来这四个数字全是全库计数：类型下拉走 `getTopicTypeCountMap()`、标签下拉用后端
 * 返回的 `refCount`、「全部 N / 待归类 N」两个药丸也是全局的。于是选了「待归类」
 * 再点类型「笔记」，上面写着有数、点进去空空如也——**数字在骗人，比没有数字更糟**。
 *
 * 规则只有一条：**某维度的数字 = 其它维度已生效条件下的条数**。自己那一维必须排除，
 * 否则已经选了「笔记」之后类型下拉里就只剩「笔记」有数，想切回去都看不见别人的数量。
 *
 * 纯 computed，不发请求：复杂度是 `维度数 × 知识点数`，17 条还是几千条都无所谓。
 */
function countBy(dimensions, extra) {
  return allTopics.value.filter(
    (t) => (extra ? extra(t) : true) && dimensions.every((d) => d(t))
  ).length
}

// 原子谓词。算哪个维度的数字，就挑**除它以外**的那几个交给 countBy。
// **不含关键词**：关键词已经在服务端应用了（索引就是"关键词下的全集"）。
const mCategory = (t) => matchCategory(t)
const mType = (t) => matchType(t)
const mTag = (t) => matchTag(t)

// 标签清单**直接算，不存快照**。
// 原来是 `ref([])` + refresh() 里 `tagList.value = [...getAllTags()].sort(...)`，那是一次性拷贝——
// store 的 renameTag / confirmDeleteTag 刷的是它自己那份 allTags，本地这份是副本不会跟着变，
// 于是"在标签下拉里改名/删除之后列表不动，必须手动刷新页面"。
//
// 清单本身仍来自后端（哪些标签存在只有它知道），但**数字就地算、不看 refCount**——
// refCount 是全库数，显示在这里就是上面说的那种骗人的数字。
// 副作用：没有任何知识点引用的标签会显示 0。
const tagList = computed(() =>
  getAllTags()
    .map((tag) => ({
      ...tag,
      count: countBy([mCategory, mType], (t) => hasTag(t, tag.name))
    }))
    .sort((a, b) => b.count - a.count)
)

// 目录这一维在筛选条上常驻两个值：全部 / 待归类。选中某个具体目录时它们照常显示——
// 数字答的是"假如点它会得到多少"，这正是换维度该给的提示。
const allCount = computed(() => countBy([mType, mTag]))
const uncategorizedCount = computed(() =>
  countBy([mType, mTag], (t) => t.categoryId == null)
)

// 类型下拉每项的数字
const typeCountMap = computed(() => {
  const counts = new Map()
  for (const ty of topicTypes.value) {
    counts.set(ty.id, countBy([mCategory, mTag], (t) => typeIdOf(t) === ty.id))
  }
  return counts
})

/**
 * 当前筛选条件下的**全集**（结构索引那份）：目录 ∧ 类型 ∧ 标签。
 *
 * <p>它是"这一屏筛出来的所有条目"，不是"这一页显示的那几条"——
 * 考试要考的是全部，AI 归类要处理的是全部，分面计数要数的也是全部。
 */
const scopedTopics = computed(() =>
  allTopics.value.filter((t) => matchCategory(t) && matchType(t) && matchTag(t))
)

// ==================== 出卷 ====================

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

/** 出卷入口的显示条件：有东西可考（和「AI 归类」一样，AI 关了就不画这个按钮） */
const showExam = computed(() => aiEnabled.value && examScope.value.length > 0)

/**
 * 去出卷：**在当前浏览器开一个新标签页**。
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

function pickTag(name) {
  tagFilter.value = name
  tagPanelVisible.value = false
}

async function refresh() {
  // 目录先刷：**子树的 id 列表要用它**（列表请求要传 categoryIds）
  await ensureCategoriesLoaded()
  // 索引（结构：目录/计数/范围）和这一页（内容：正文/摘要）**互不依赖** ⇒ 一起发，
  // 别一个接一个等（两个都是几百毫秒量级，串行就是两倍）。
  //
  // ⚠️ **都不强制重拉**：壳（AppLayout）启动时已经把目录 / 索引拉了，
  // 这里再 force 一遍就是**同一个接口打两次**（实测每次进列表页多打一遍
  // /kp/topic/index，而它和列表请求是并发的，等于把列表也一起拖慢）。
  // 数据变了由 store 自己 force（改完知识点、改完标签都会刷一遍）⇒ 这儿重拉是白拉。
  await Promise.all([
    ensureTopicIndexLoaded(false, keyword.value),
    ensureAllTagsLoaded(),
    loadPage()
  ])
}

function goDetail(t) {
  router.push({ name: 'AppTopicDetail', params: { id: t.id } })
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
onActivated(() => {
  // 切回来的：离开这段时间数据可能变了（别处改的、别处删的）⇒ 重拉这一页。
  if (activatedOnce) loadPage()
  activatedOnce = true
})

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

// 侧边栏（AppLayout）也在写这两个 query：目录链接写 category，搜索框回车写 q。
// 只在 onMounted 读一次的话，页面已经挂着时路由变了没人接——URL 变了、界面纹丝不动。
watch(() => route.query.category, (c) => {
  // ⚠️ **只在列表页开着的时候才跟着 URL 变**（见 onLearnRoute）：
  // 离开这一页时详情页的 URL 里没有 category ⇒ 不改它。
  // 改了的话，"暂时不在眼前"就被当成了"用户把目录筛选清了" ⇒ 列表在后台被刷成「全部」。
  if (!onLearnRoute.value) return
  categoryFilter.value = readCategoryQuery(c)
})

/**
 * 筛选条件变了 → 重拉这一页。
 *
 * <p>**回到第 1 页**：翻到第 5 页再换个条件，第 5 页多半是空的（这一次的总数变小了）。
 *
 * <p>关键词也在这个 watch 里：**索引是"关键词下的全集"**（服务端筛的），
 * 关键词一变索引就得跟着重拉 —— 不拉的话侧边栏的条数还是"不筛关键词"的那份。
 * 关键词没变时它是空操作（store 认得"这份就是当前的"）。
 */
watch([categoryFilter, typeFilter, tagFilter, keyword], () => {
  // ⚠️ 关键词是**直接从 URL 读**的 ⇒ 离开这一页时它会变成空（详情页的 URL 里没有 ?q=）。
  // 那一瞬间**不是**"用户清了搜索词"，是"这一页暂时不在眼前" ⇒ 不在这儿发请求。
  if (!onLearnRoute.value) return
  page.value = 1
  ensureTopicIndexLoaded(false, keyword.value)
  loadPage()
})

// 正在筛的标签被改名或删掉了 → 回到"全部"。
// 兜在这里而不是让 TagRowMenu 回调里清：改标签的入口不止这一个（详情页的标签编辑也能改），
// 在这一处 watch 一次，所有入口都覆盖到。不兜的表现是"筛选还开着但列表空了"，
// 用户会以为知识点被删了。
watch(tagList, () => {
  if (tagFilter.value && !tagList.value.some((t) => t.name === tagFilter.value)) {
    tagFilter.value = null
  }
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
.pill.is-on {
  background: var(--cc-text-1);
  color: var(--cc-surface);
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

/* 标签下拉 */
.tagpanel {
  display: flex;
  flex-direction: column;
  gap: var(--sp-1);
  max-height: 288px;
  overflow-y: auto;
}
/* 一行 = 行容器（现在带标签的行是 div，因为要再塞一个「⋯」按钮）+ 选它的按钮 + 操作按钮。
   「全部标签」那行不带菜单，还是个 button，所以 cursor/字体这些两处都要留着。 */
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
  /* 和侧边栏目录行同一套：行尾「⋯」默认不显形，悬停/聚焦这一行才露出来。
     focus-within 不能省——opacity:0 的按钮照样能被 Tab 选中。 */
  --row-tools: 0;
}
.tagpanel__item:hover,
.tagpanel__item:focus-within {
  --row-tools: 1;
}
.tagpanel__item:hover {
  background: var(--cc-surface-2);
}
.tagpanel__item.is-on {
  background: var(--cc-primary-soft);
  color: var(--cc-primary);
  font-weight: 500;
}
/* 行里的"选它"那一半。名字太长得靠 min-width:0 + ellipsis 让位给右边的数字和 ⋯，
   不设的话 flex 子项的 min-width:auto 会顶着把行撑宽。 */
.tagpanel__pick {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-10);
  border: none;
  background: transparent;
  padding: 0;
  font: inherit;
  color: inherit;
  cursor: pointer;
  text-align: left;
}
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
