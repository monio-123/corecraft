import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { confirmAction } from '../../utils/confirm'
import {
  getTopicList, getTopicIndex as apiGetTopicIndex, getTopicById, getTagList, getCategoryList,
  createTopic as apiCreateTopic, updateTopic as apiUpdateTopic, deleteTopic as apiDeleteTopic,
  setTopicMastery as apiSetTopicMastery,
  createCategory as apiCreateCategory, renameCategory as apiRenameCategory,
  moveCategory as apiMoveCategory, deleteCategory as apiDeleteCategory,
  importCategories as apiImportCategories,
  renameTag as apiRenameTag, deleteTag as apiDeleteTag
} from '../../api/knowledge'
import { getDictTypeList, getDictItemList } from '../../api/dict'
import {
  getLearningTree as apiGetLearningTree,
  getMasteryLevels as apiGetMasteryLevels
} from '../../api/learning'
import { onAuthChange } from '../../utils/auth'
import { createLoadSlot, loadOnce, resetSlot } from '../../utils/lazyLoad'
import { normalizeSearchScope, SEARCH_SCOPE_BOTH } from '../../utils/searchMode'
// ⚠️ **原来这里还 import 了 `DEFAULT_RANGE` / `rangeBounds` / `withinRange`** ——
// 时间筛选的判据已经全搬到 `AppHomeView.vue` / `AppLearnView.vue`（列表页与首页），
// store 这份是搬迁后残留的零使用导入 ⇒ 删除。口径仍只有 `utils/timeRange.js` 一份。

// ==================== 内部状态 ====================

/**
 * **结构索引**：全部知识点的轻量投影（id / 标题 / 目录 / 类型 / 标签 / 有无正文）。
 *
 * <p>**一条正文都不含**（2026-10-27 性能诊断）：正文是 TEXT 列，636 条加起来 90 万字符，
 * JDBC 逐行读回它们要 4.4 秒（实测），而目录树、各处计数、筛选范围**一条正文都不需要**。
 * ⇒ "全集"用这份（不碰 content 列，78ms），正文只在**要显示正文的那一页**里取。
 *
 * <p>它同时是**当前关键词下的全集** —— 关键词要搜正文，只有服务端能筛。
 */
const topicIndex = ref(new Map())
const topicIndexSlot = createLoadSlot()

/**
 * **全集索引**（不按关键词筛）：**目录树要的"完整的书"**。
 *
 * <p>⚠️ **为什么要有第二份**（用户 2026-11-05：「对于目录树搜索既然加了命中栏
 * 那么展示的树就要是完整的啊 你把目录数据过滤了然后加个命中栏的意义在哪？」）：
 * 搜索索引是"**当前关键词下的**全集"（服务端按 `q` 筛过的）⇒ 用它画树，
 * 树上就只剩命中的那些条目 ⇒ **树被过滤了** ⇒ 再加个命中栏等于把同一件事做了两遍。
 * ⇒ 树要画**完整的书**，就得有一份**不筛**的索引。
 *
 * <p>⚠️ **没搜索词时两份是同一份**（搜索索引 = ('', both) = 全集）
 * ⇒ 那时直接共用，**不重复打接口**（见 {@link ensureFullTopicIndexLoaded}）。
 */
const fullTopicIndex = ref(new Map())
const fullIndexSlot = createLoadSlot()

/**
 * 当前这份索引对应的关键词。**换了就是另一份数据**，必须重拉。
 *
 * <p>它是这份数据的**身份**的一部分（和"哪个账号的数据"同级），所以记住它：
 * 两个调用方拿同一个关键词时不必各拉一次。
 *
 * <p>⚠️ **它是 ref 不是普通变量**：改关键词之后，**拿着这份数据在渲染的人**
 * （树视图那棵树、侧边栏的计数）得知道"这份数据是按哪个词筛的" ⇒ 得能触发重算。
 */
const indexKeyword = ref(null)

/**
 * 当前这份索引对应的 **scope**（搜哪一块：标题 / 正文 / 都搜）。
 *
 * <p>⚠️ **它和关键词一样是这份数据的"身份"的一部分** —— 同一个词、
 * 一档搜标题一档搜正文，**是两批完全不同的命中集** ⇒ scope 变了也得重拉。
 *
 * <p>（2026-11-05 新增：搜索分成「正文」「目录」两档，各搜各的。）
 */
const indexScope = ref(null)

/**
 * **当前页**的知识点（带正文）—— **不在这儿存**。
 *
 * <p>⚠️ 原来这里缓存了一份（`topicPage` 这个 Map），可**全项目没有一处读它**：
 * 列表页渲染的是它自己的 `pageRecords`。两份"当前页"只有一份是活的 ⇒
 * ① store 刷了、视图没刷 ⇒ **在列表页删掉一条知识点，卡片不消失**（得刷新页面）；
 * ② 每次建/改/删都多打一次 `GET /kp/topic/list`，结果写进一个没人读的 Map。
 *
 * <p>⇒ **"当前页"归视图管**（它知道自己在第几页、筛了什么），
 * store 只管"数据变没变"（见下面的 dataVersion）。
 */

/**
 * **数据版本号**：知识点变了就 +1。
 *
 * <p>视图拿它当"该重拉这一页"的信号（`watch(getDataVersion, ...)`）。
 * store 不再替视图缓存那一页 —— 它只负责说"变了"，怎么拉是视图的事。
 */
const dataVersion = ref(0)

/** 数据版本号（视图 watch 它来重拉当前页） */
export function getDataVersion() {
  return dataVersion.value
}

/**
 * **说一声"数据变了"**（数据版本号 +1）。
 *
 * <p>给**从 store 外面**改库的那些地方用：任务中心的"采纳 AI 归类"
 * （在 {@code GenTaskPanel} 里，碰不到 store 的内部函数）、
 * 别的跨模块写路径。它们改了 kp_topic，列表页那一页得跟着变。
 *
 * <p>⚠️ **只说"变了"，不替视图重拉**：怎么拉是视图的事（它知道自己在第几页、筛了什么）。
 * store 内部那些写路径走的是 {@link refreshAfterTopicChange}，
 * 它除了 +1 还会刷索引缓存 —— **外面直接调这个不会刷缓存**，
 * 所以调用点要自己刷（或者本来就用 store 的写函数）。
 */
export function bumpDataVersion() {
  dataVersion.value++
}

// 标签缓存：{ name, count }。原本从 topicMap 聚合，要先 ensureTopicsLoaded 拉全部 topic。
// 现在改成直接调 /kp/tag/list 拉全部 tag（含孤儿），不依赖全部 topic。
// 详情页打开不再调 /kp/topic/list，"选择已有标签" popover 懒加载触发请求。
const allTags = ref([])
const allTagsSlot = createLoadSlot()

// 目录缓存：[{ id, name, parentId }]。树的骨架，标签不参与建树。
// parentId 归一：后端对历史 NULL 返回 0，但老数据/老接口可能给 null，
// 这里统一收口成 0，全站只面对"0 = 顶层"一种表示。
const categories = ref([])
const categoriesSlot = createLoadSlot()
/**
 * 目录的加载状态（侧边栏要用）：'loading' | 'ready' | 'failed'。
 *
 * **响应式**（而不是一个普通 `let`）：侧边栏要靠它区分三种完全不同的情况 ——
 * "还在加载"、"加载完了但一个目录都没有"、"加载失败了"。
 * 不区分的话，加载中的那几秒侧边栏会显示「没有目录」——**那是句假话**
 * （用户明明有目录），看着像"目录被清空了"。
 */
export const categoriesState = categoriesSlot.state

// 知识点类型缓存：[{ id, label, value }]，来自字典 sys_dict_type.code = 'kp_topic_type'。
//
// **字典是全局的**，所以每个用户天然都有默认档——不给它建独立主表正是因为这个。
// kp_topic.typeId 存的是这里的 id（纯数字），不是 label 也不是 0/1/2：
// 字典项增删排序都不影响任何代码，代码里也不存在"1 = 笔记"这种要同步维护的映射。
//
// **value 才是能写进代码的语义键**：label 是显示名（管理员可以改），
// value 是机器名（note / learn）。代码要指名某一档时认 value，不认 label。
const topicTypes = ref([])
const topicTypesSlot = createLoadSlot()

function normalizeCategory(c) {
  return {
    id: c.id,
    name: c.name,
    parentId: c.parentId || ROOT_CATEGORY,
    // 「生成于 …」（树视图 meta 行用）。后端目录接口不带这个字段 ⇒ undefined，
    // 组件按 falsy 不渲染 —— demo 假数据里有，就显示。
    createdAt: c.createdAt || null
  }
}

// 几个缓存都是模块级的，只有整页刷新才会重建。换账号时不丢，B 会看到 A 的知识点/目录/标签。
// 登录态一变就全部作废：数据本身也要清，否则 loaded=false 但列表里还挂着旧账号的内容。
onAuthChange(() => {
  topicIndex.value = new Map()
  resetSlot(topicIndexSlot)
  indexKeyword.value = null
  indexScope.value = null
  fullTopicIndex.value = new Map()
  resetSlot(fullIndexSlot)
  allTags.value = []
  resetSlot(allTagsSlot)
  categories.value = []
  resetSlot(categoriesSlot)
  topicTypes.value = []
  resetSlot(topicTypesSlot)
  // 学习数据也是账号级的：B 不能看到 A 的掌握度
  categoryMasteryMap.value = new Map()
  resetSlot(categoryMasterySlot)
  // 数据换人了 ⇒ 视图手里那一页也是旧账号的 ⇒ 说一声，让它重拉。
  dataVersion.value++
})

// ==================== 通用 helpers ====================

/** 顶层目录的 parentId。侧边栏、级联、面包屑都以它为根 */
export const ROOT_CATEGORY = 0
/** 「待归类」不是目录，是个固定桶。用字符串哨兵，避免和数字 id 撞类型 */
export const UNCATEGORIZED = 'uncategorized'
/** 「全部」也不是目录，是另一种范围。同样用字符串哨兵（原来它只在列表页内部是个局部常量，
 *  范围计算收进 store 之后，它就成了范围的一种，值必须只有一份） */
export const ALL = 'all'
/** 知识点类型所在的字典编码。类型存字典而不是本地表——字典是全局的，每个用户都有默认档 */
export const TOPIC_TYPE_DICT = 'kp_topic_type'
/**
 * 「学习」档的字典 **value**（sys_dict_item.value）。
 *
 * <p>生成出来的知识点是系统整理过的学习内容 ⇒ 落这一档（不是随手记那一档）。
 * 认 value 不认 label：label 是显示名，管理员改个名代码就失效了。
 */
export const TOPIC_TYPE_LEARN = 'learn'

/**
 * 目录树往下走的**层数上限**。**只有这一份**。
 *
 * <p>三处防环都用它：向上找祖先链（面包屑）、向下摊平（拖拽落点那棵树）、
 * 向上数层数（侧边栏计数）。同一个"最多往下 10 层"的约定散在三个函数里，
 * 改一处别的不知道。
 *
 * <p>⚠️ **它是兜底，不是防环本身**：数据被改坏出现环时，真正能终止的是
 * 「已访问集合」那套（见 `getCategorySubtreeIds` / `flattenCategoryTree`）。
 */
const MAX_TREE_DEPTH = 10

function parseJson(raw, fallback) {
  try {
    return raw ? JSON.parse(raw) : fallback
  } catch {
    return fallback
  }
}

export function encodeTag(tag) {
  if (typeof tag === 'string') return tag
  return tag?.name || ''
}

/** 标签：界面上是对象，线上是名字数组。建/改两条路各写一遍 map 的话，改一边不同步 */
const encodeTags = (tags) => (tags || []).map(encodeTag).filter(Boolean)


function decodeTag(raw) {
  const value = String(raw || '').trim()
  return { name: value }
}

// ==================== 标准化 ====================

/** 完整的一条知识点（**带正文**）：列表页的卡片、详情页的正文都用它。 */
function normalizeTopic(item) {
  return {
    id: item.id,
    title: item.title || '',
    content: item.content || '',
    parentTopicId: item.parentTopicId ?? null,
    categoryId: item.categoryId ?? null,
    typeId: item.typeId ?? null,
    // ⚠️ **掌握度相关字段（2026-11-12）**：
    //   masteryEnabled —— 后端**已解析**的最终判据（单条 > 类型 > 兜底），前端不自己判；
    //   masteryOverride —— **原始**单条覆盖（null=跟随类型 / true=参与 / false=不参与），
    //     和 masteryEnabled 分开带：两者在"明确关掉"和"跟随类型(而类型默认关)"时长得一样，
    //     设置弹窗要靠 override 才分得清自己停在哪一档；
    //   masteryLevel   —— 后端算好的档位词；**null = 没有有效评估** ⇒ 卡片上完全不画标记。
    masteryEnabled: item.masteryEnabled ?? false,
    masteryOverride: item.masteryOverride ?? null,
    masteryLevel: item.masteryLevel ?? null,
    tags: parseJson(item.tags, []).map(decodeTag),
    relatedTopicIds: item.relatedTopicIds || [],
    createdAt: item.createTime || '',
    updatedAt: item.updateTime || ''
  }
}

/**
 * 结构索引里的一条（**没有正文**）：只有"这条在哪、叫什么、属于什么、写了没有"。
 *
 * <p>`hasContent` 是"**有没有**正文"（布尔），不是正文本身 ——
 * "这条还没写正文"要判的是这件事，而正文是几十万字符。
 */
function normalizeIndexEntry(item) {
  return {
    id: item.id,
    title: item.title || '',
    categoryId: item.categoryId ?? null,
    typeId: item.typeId ?? null,
    // 已解析的「是否参与掌握度」（后端判据，前端不自己判）
    masteryEnabled: item.masteryEnabled ?? false,
    tags: parseJson(item.tags, []).map(decodeTag),
    hasContent: !!item.hasContent,
    createdAt: item.createTime || '',
    // 「更新于」的时间戳：保存类动作（applyPatch）强刷索引后从这儿补新值
    //（2026-11-09 用户报「更新正文后时间不变，只有刷新页面才会变」——
    // applyPatch 一直想补 fresh.updatedAt，但索引条目从来没带过这个字段）。
    updatedAt: item.updateTime || ''
  }
}

// ==================== 知识点 ====================

/**
 * 载入**结构索引**（全集，不含正文）。
 *
 * @param {boolean} force 强制重拉（改完库要刷）
 * @param {string} keyword 当前关键词。**关键词是这份数据的身份的一部分** ——
 *        换了就得重拉，而且必须服务端筛：浏览器这边没有正文，筛不了正文。
 * @param {string} scope 搜哪一块（标题 / 正文 / 都搜，见 utils/searchMode.js）。
 *        ⚠️ **它也是身份的一部分**：同一个词，一档搜标题一档搜正文，是两批数据。
 */
export async function ensureTopicIndexLoaded(
  force = false,
  keyword = indexKeyword.value || '',
  scope = indexScope.value || SEARCH_SCOPE_BOTH
) {
  const kw = String(keyword || '').trim()
  const sc = normalizeSearchScope(scope)
  const changed = kw !== indexKeyword.value || sc !== indexScope.value
  if (!force && !changed && topicIndexSlot.state.value === 'ready') return getTopicIndex()
  // 先记住新关键词/新 scope：两个调用方（壳 / 列表页）拿同一份时，
  // 第二个会算成"没变" ⇒ 复用第一个的**在途**请求，不会打两遍接口。
  if (changed) {
    indexKeyword.value = kw
    indexScope.value = sc
  }
  await loadOnce(topicIndexSlot, force || changed, async (isCurrent) => {
    const res = await apiGetTopicIndex({ q: kw, scope: sc })
    const nextMap = new Map()
    ;(res?.data || []).map(normalizeIndexEntry).forEach((t) => nextMap.set(t.id, t))
    // **只有最新发起的那次才落缓存**：旧请求晚回来时它带的是**更早的库状态**
    // （建骨架时那次索引请求被后面的写请求堵住，回来晚了 ⇒ 缓存倒退）。
    if (isCurrent()) topicIndex.value = nextMap
    return nextMap
  })
  return getTopicIndex()
}

/**
 * **现拉一份结构索引**（不进缓存、**不带关键词**）。
 *
 * <p>给"要拿全集"的场合用：**生成知识点**算范围时不能拿共享缓存那份 ——
 * ① 它是**当前关键词下的**全集（列表页搜过东西时，生成范围就会少一批）；
 * ② 它可能停在**旧快照**（2026-10-28 实测：建骨架途中发的那次索引请求晚回来，
 * 把刚刷新的完整索引覆盖成"建到一半"的样子 ⇒ 74 条知识点从来没进过生成循环，
 * 界面却显示"完成"）。
 *
 * <p>⇒ 生成范围**自己拉一份**，不靠任何缓存：这份数据只在这一刻用一次，
 * 落进共享缓存反而会污染别人。
 *
 * @returns {Promise<Array>} 结构索引条目（id / 标题 / 目录 / 有无正文）
 */
export async function fetchTopicIndexFresh() {
  const res = await apiGetTopicIndex({})
  return (res?.data || []).map(normalizeIndexEntry)
}

/**
 * 结构索引（全集，不含正文）。目录树、计数、筛选范围都用它。
 */
export function getTopicIndex() {
  return [...topicIndex.value.values()]
}

/**
 * **全集索引**（不按关键词筛）：**目录树要的"完整的书"**。
 *
 * <p>⚠️ **它和 {@link getTopicIndex} 不是一回事**：
 * 那份是"**当前关键词下的**全集"（搜索索引，命中集靠它数），
 * 这份是**什么词都不筛**的全集（**树画的是它** —— 搜索态下树也要完整）。
 *
 * @returns {Array} 结构索引条目（与 getTopicIndex 同一个形状）
 */
export function getFullTopicIndex() {
  return [...fullTopicIndex.value.values()]
}

/**
 * **把全集索引拉到位**（目录树要它）。
 *
 * <p>⚠️ **没搜索词时不重复打接口**：搜索索引本身就是全集（('', both)）
 * ⇒ 等它到位后直接复用（`ensureTopicIndexLoaded` 认得"同一份在途请求"）。
 *
 * <p>⚠️ **身份是"当前搜索索引的身份"**：搜索索引被换成按词筛的那份之后，
 * 全集索引就**不能**再从它那儿拿了 ⇒ 这时才真的发一次请求（不筛）。
 *
 * @param {boolean} force 强制重来（改了库要刷缓存）
 */
export async function ensureFullTopicIndexLoaded(force = false) {
  // **搜索索引本身就是全集**（没有搜索词时，服务端不筛 ⇒ 它就是全部）
  // ⇒ 等它到位后直接复用，不重复打接口。
  // ⚠️ **判据只看"有没有搜索词"，不看 scope**：没有词的时候 scope 筛不出任何东西
  // （服务端那条 SQL 的 `q != ''` 才进分支）⇒ scope 是 title 还是 both 都一样。
  // ⚠️ **这里不 force**：刷不刷由调用方决定（调用方刷完搜索索引，这里自然拿到新那份）。
  if (indexKeyword.value === '') {
    await ensureTopicIndexLoaded(false)
    fullTopicIndex.value = topicIndex.value
    return getFullTopicIndex()
  }
  if (!force && fullIndexSlot.state.value === 'ready') return getFullTopicIndex()
  await loadOnce(fullIndexSlot, force, async (isCurrent) => {
    const res = await apiGetTopicIndex({})
    const nextMap = new Map()
    ;(res?.data || []).map(normalizeIndexEntry).forEach((t) => nextMap.set(t.id, t))
    // **只有最新发起的那次才落缓存**（同 topicIndex 那份的理由：旧请求晚回来会把缓存倒退）。
    if (isCurrent()) fullTopicIndex.value = nextMap
    return nextMap
  })
  return getFullTopicIndex()
}

/**
 * 「当前目录范围 = URL 上的 `?category=`」这条推导。**只有这一份** ——
 * 列表页用它筛，**详情页用它还原"这次搜索的命中集"**（浮条上的序号）。
 *
 * <p>⚠️ 两处各写一遍的话，详情页算出来的命中集和列表页看到的不是一批
 * ⇒ 序号错位，而界面上什么异常都看不出来。
 *
 * @param {object} query 路由上的 query
 * @returns {string|number} ALL / UNCATEGORIZED / 目录 id
 */
export function categoryScopeFromQuery(query) {
  const c = query?.category
  if (c !== undefined) {
    if (c === UNCATEGORIZED) return UNCATEGORIZED
    return Number(c)
  }
  // ⚠️ **没有 `?category=`、而有 `?tree=`（哪本书）时 ⇒ 范围 = 那本书**
  // （2026-11-05 晚用户拍板：正文档那档点侧边栏的书 = 卡片收窄到那本书；
  // 「默认没有选择一级目录的时候是直接展示所有符合的卡片的」）。
  //
  // ⚠️ **这条规则必须只写这一份**：列表页（列表的范围、命中浮条的条数）和
  // 详情页（"我是第几个命中"）都要它 —— 各写一遍的话两边算出的命中集不是一批
  // ⇒ 序号错位，**而界面上什么异常都看不到**。
  const t = query?.tree
  const id = t == null ? NaN : Number(t)
  return Number.isFinite(id) && id ? id : ALL
}

/**
 * **改了库之后刷索引**：**两份都刷**（搜索索引 + 全集索引）。
 *
 * <p>⚠️ **为什么是两份**：搜索索引是"当前关键词下的全集"（侧边栏的命中计数靠它），
 * 全集索引是"什么词都不筛的全集"（**树画的是它**）。刷一份漏一份 ⇒
 * 树的条数和侧边栏的条数对不上，**而界面上什么异常都看不出来**。
 *
 * <p>⚠️ **谁改了库就用这个，别自己写半份**（2026-11-09 用户报「生成主题后目录树
 * 计数全是 0，必须刷新才有」）：生成流程当初写的是裸的 `ensureTopicIndexLoaded(true)`
 * ⇒ 只刷了搜索索引，树的计数读的 `fullTopicIndex` 还是生成前的旧快照
 * ⇒ 树上每个章/节的数字都是 0，**界面上什么异常都看不出来**。
 */
export async function refreshTopicIndexes() {
  await ensureTopicIndexLoaded(true)
  await ensureFullTopicIndexLoaded(true)
}

/**
 * 拉列表页的**一页**（带正文）。
 *
 * <p>**不缓存**：当前页是**视图的事**（它知道自己在第几页、筛了什么）。
 * store 原来也缓存了一份（`topicPage`），可没人读它 ⇒ 反而每次改动都多打一次请求。
 *
 * @param {object} params { page, size, categoryIds, uncategorized, typeId, tag, keyword }
 * @returns {{records: Array, total: number}}
 */
export async function loadTopicPage(params = {}) {
  const res = await getTopicList(params)
  const data = res?.data || {}
  return {
    records: (data.records || []).map(normalizeTopic),
    total: Number(data.total || 0)
  }
}

/**
 * 按 id 取一条（**结构索引里的那份**：有标题/目录/类型/标签/时间戳，**没有正文**）。
 *
 * <p>要正文走 {@link fetchTopic}（详情接口）或列表页那一页。
 *
 * <p>⚠️ **它是"按关键词筛过"的那份**（见 topicIndex 那段）⇒
 * **它不是身份查询**：索引里查不到 ≠ 这条不存在。
 * 要"这条现在的目录归属"这种**权威值**，走 {@link currentCategoryIdOf}。
 */
export function getTopic(id) {
  return topicIndex.value.get(Number(id)) || topicIndex.value.get(id) || null
}

/** 详情：一条完整知识点（带正文）。详情页专用。 */
export async function fetchTopic(id) {
  const res = await getTopicById(id)
  if (!res?.data) return null
  return normalizeTopic(res.data)
}

/**
 * **知识点详情页当前打开那条的目录归属**（顶栏面包屑的数据源）。
 *
 * <p>`topic` 是详情页自己的局部状态，壳（AppLayout 顶栏）拿不到 —— 顶栏那条
 * 「一级目录/二级目录/…」要显示这条知识在哪，只能由详情页**报**上来。
 * 这里只放顶栏用得上的最小值（目录 id），不复制 topic 本体。
 *
 * <p>⚠️ **写方只有详情页一个**（watch topic.categoryId —— 载入、AI 采纳改归类
 * 都走它）；读方按 `route.name === 'AppTopicDetail'` 门控，别的页面不看它，
 * 所以切走之后的残留值不会漏到别处。
 */
export const openTopicCategoryId = ref(null)

/**
 * **顶栏搜索框 → 详情页正文** 的实时检索通道（2026-11-09）。
 *
 * <p>用户在详情页时，顶栏那个搜索框**边打字边**在这篇正文里检索并高亮
 * （用户：「如果我在详情正文中的视图的时候 就变为实时生效查询」）。
 * 列表页仍然是回车才搜（整库检索，回车触发才对）。
 *
 * <p>⚠️ **写方只有壳一个**（AppLayout 的输入框）、读方只有详情页一个
 * ⇒ 与 {@link openTopicCategoryId} 同一个形状：壳拿不到详情页的编辑器实例，
 * 详情页拿不到壳的输入框 ⇒ 两边只能通过这一处共享状态对话。
 *
 * <p>⚠️ **检索词只有这一份**（2026-11-09 用户报「搜过一个东西就删不掉了」）：
 * 顶栏框的字 = 本通道 = URL 的 `?q=`（壳在详情页打字时同步 replace）。
 * 原来正文里还有一个独立检索栏（BodySearchBar），它自持一份词 ⇒ 清它清不掉
 * URL 和顶栏框那份，刷新 / 进新目录又冒出来。独立栏已删，词收敛到这一条。
 */
export const bodySearchKeyword = ref('')

/**
 * **正文检索的命中计数**（详情页 → 壳）：这次检索命中几处、现在在第几个。
 *
 * <p>壳（AppLayout）把它印在顶栏搜索框的 suffix 区（「3/12」或「12 处」）——
 * 命中数据只在编辑器那边有（ProseMirror 插件算的），显示却在顶栏 ⇒ 反向通道。
 *
 * <p>⚠️ `index` **0 = 还没定位**（只印总数「N 处」），1 起 = 在第 k 处（印「k/N」）。
 * 口径与顶栏搜索框里那套一致（定位了印「k/N」，没定位印「N 处」）。
 */
export const bodySearchHits = ref({ total: 0, index: 0 })

/**
 * **目录树那档的命中计数**（目录树 → 壳）：这次搜索在树里命中几行、现在在第几行。
 *
 * <p>⚠️ **和 {@link bodySearchHits} 是两件事，别混**：
 * - `bodySearchHits` = **正文**里命中几处（详情页那档）；
 * - `treeSearchHits` = **目录树**里命中几行（目录那档，命中的是目录名 / 知识点标题）。
 *
 * <p>用户 2026-11-09：「目录树的那个定位框也跟这里一样复用上面那个就行」
 * ⇒ 树里原来那个独立的命中浮条（可拖）**撤掉**，
 * 计数与 ↑↓ 统一到顶栏搜索框的 suffix 区 ⇒ **同一个框、同一套 ↑↓**。
 */
export const treeSearchHits = ref({ total: 0, index: 0 })

/**
 * **目录树那档的跳转命令**（壳 → 目录树）：`'next'` / `'prev'`。
 *
 * <p>与 {@link bodySearchJump} 同一个形状：写方是壳（顶栏 ↑↓ 按钮），
 * 读方是目录树（`CategoryTree`），消费后置回 `''`（连点两次跳两次）。
 */
export const treeSearchJump = ref('')

/**
 * **正文检索的跳转命令**（壳 → 详情页）：`'next'` / `'prev'`。
 *
 * <p>顶栏搜索框 suffix 区的 ↑↓ 按钮写它，详情页 watch 到就调编辑器
 * `gotoMatch` 跳过去、更新 {@link bodySearchHits}，**消费后置回 `''`**
 * ⇒ 同一个方向连点两次也会跳两次（命令是边沿触发，不是电平）。
 */
export const bodySearchJump = ref('')

/**
 * 这条知识点**现在的**目录归属（**权威值**）。
 *
 * <p>⚠️ **不从结构索引里猜**：索引是**按关键词筛过的**，
 * 猜不到就回填 null ⇒ 后端对 categoryId 是**无条件覆盖写** ⇒
 * **这条知识点被移回「未归类」**（目录归属没了，而界面上看不出任何异常）。
 *
 * <p>⇒ 缓存里没有就问服务端（详情接口）。多一次请求，只在缓存没有时发生 ——
 * 比"把归类清掉"划算得多。
 */
async function currentCategoryIdOf(id) {
  const hit = getTopic(id)
  if (hit) return hit.categoryId ?? null
  const t = await fetchTopic(id)
  return t?.categoryId ?? null
}

// 知识点变动之后刷缓存。**tagsChanged 必须由调用点老实说**：
// 标签的 refCount 存在**另一份**缓存 allTags 里（来自 /kp/tag/list），刷 topicIndex 刷不到它。
// 之前三条路径（建 / 改 / 删知识点）都只刷了 topicMap，标签下拉里的计数于是永远是旧的，
// 表现为"详情页移掉一个标签 → 返回列表 → 计数没变，得刷新页面"。
// 收敛成一个函数，调用点只回答"这次动没动标签"，没动就不发那个多余请求。
export async function refreshAfterTopicChange(tagsChanged) {
  // 索引（结构：目录/计数/范围）要刷。**两份都刷** —— 搜索索引（按词筛）和
  // 全集索引（树的"完整的书"），少刷一份就会出现"树的条数和侧边栏对不上"。
  await refreshTopicIndexes()
  if (tagsChanged) await ensureAllTagsLoaded(true)
  // **当前页（内容：正文/摘要）归视图管** ⇒ 这里只说"变了"。
  // 视图 watch getDataVersion 来重拉那一页 —— store 不再替它缓存一份。
  dataVersion.value++
}

/**
 * 新建一条知识点。
 *
 * @param {object} data 知识点内容
 * @param {{refresh?: boolean}} opts `refresh: false` = **建完不刷新缓存**。
 *        批量建（一次几十条）时用它：每建一条都全量拉一次列表，
 *        80 条就是 80 次请求，纯浪费。批量结束时自己刷一次。
 *        注意：不刷新时**返回 null**（新 id 是从刷新后的缓存里找出来的）。
 */
export async function createTopic(
  { title, content, tags, parentTopicId, categoryId, typeId },
  { refresh = true } = {}
) {
  const beforeIds = new Set(getTopicIndex().map((item) => item.id))
  const encoded = encodeTags(tags)
  await apiCreateTopic({
    title: title || '',
    content: content || '',
    parentTopicId: parentTopicId || null,
    categoryId: categoryId || null,
    // 类型没传就用默认档（字典里排第一的那条）。后端 typeId 允许为空，
    // 但录入路径永远给得出来——默认档一直存在
    typeId: typeId ?? defaultTopicTypeId(),
    tags: encoded,
  })
  if (!refresh) return null
  // 带标签建出来才会多出引用；不带标签建一条不碰 refCount
  await refreshAfterTopicChange(encoded.length > 0)
  return getTopicIndex().find((item) => !beforeIds.has(item.id)) || null
}

/**
 * 改一条知识点。
 *
 * @param {number} id 知识点 id
 * @param {object} patch 要改的字段
 * @param {{refresh?: boolean}} [opts] `refresh: false` = 改完不刷新缓存
 *        （批量改时用：每改一条都全量拉一次列表是纯浪费，批量结束自己刷一次）
 */
export async function updateTopic(id, patch, opts = {}) {
  // categoryId 的 null 有含义（未归类），跟"没传这个字段"不是一回事。
  // 后端对 categoryId 是无条件覆盖写（配合 updateStrategy = ALWAYS 支持置空），
  // 所以没传时必须回填**当前值**，否则会被当成 null 误抹掉归类。
  //
  // ⚠️ 回填的来源必须是**权威值**（currentCategoryIdOf）：结构索引是按关键词筛过的，
  // 从那儿猜不到就回填 null ⇒ **这条知识点被移回「未归类」**，而界面上看不出任何异常。
  const categoryId = patch.categoryId !== undefined
    ? patch.categoryId
    : await currentCategoryIdOf(id)
  await apiUpdateTopic({
    id,
    title: patch.title,
    content: patch.content,
    parentTopicId: patch.parentTopicId,
    categoryId,
    // 没传 typeId = 不打算改它；后端那边也是这么判的（不像 categoryId 无条件写）
    typeId: patch.typeId,
    tags: patch.tags ? encodeTags(patch.tags) : undefined,
  })
  // 批量改（生成完回填链接）时关掉刷新：每改一条都全量拉一次列表是纯浪费，
  // 批量结束时自己刷一次。和 `createTopic` 的 `refresh: false` 同一个理由。
  if (opts.refresh === false) return getTopic(id)
  await refreshAfterTopicChange(patch.tags !== undefined)
  return getTopic(id)
}

/**
 * 设置单条知识点是否参与掌握度（**三态**，2026-11-12）。
 *
 * <p>true = 参与 / false = 明确不参与 / **null = 恢复继承类型默认**。
 *
 * <p>⚠️ **判据全在后端**（`MasteryScope`：单条覆盖 > 类型默认值 > 安全兜底）。
 * 前端**只把用户的选择原样发过去**，不自己算"最终参与不参与"——
 * 自己算一遍就会出现"界面上说不参与、后端统计里却算进去了"。
 *
 * <p>改完要刷两处：
 * <ul>
 *   <li>**目录聚合**（eligibleCount / levelCounts 会变）；</li>
 *   <li>**当前页列表**（卡片右上角的档位签要消失/出现）⇒ 数据版本号 +1。</li>
 * </ul>
 *
 * @param {number} id 知识点 id
 * @param {boolean|null} enabled
 */
export async function setTopicMasteryEnabled(id, enabled) {
  await apiSetTopicMastery(id, enabled === undefined ? null : enabled)
  invalidateCategoryMastery()
  dataVersion.value++
}

/**
 * 删一条知识点。
 *
 * @param {number} id 知识点 id
 * @param {{refresh?: boolean}} [opts] `refresh: false` = 删完不刷新缓存。
 *        **批量删除时用它**：每删一条都刷一遍索引（两份：搜索索引 + 全集索引）
 *        就是 20 条 × 2 次请求，而且页面会被数据版本号推着重拉 20 次。
 *        批量结束自己刷一次 —— 和 `updateTopic` / `createTopic` 同一个口径。
 */
export async function deleteTopic(id, opts = {}) {
  await apiDeleteTopic(id)
  if (opts.refresh === false) return
  // 删一条一定会带走它身上的标签引用（哪怕它本来没标签），所以这里没法省那个请求
  await refreshAfterTopicChange(true)
}

/**
 * **批量归类**：把一批知识点放进同一个目录（或移回「待归类」）。
 *
 * <p>为什么要有这个函数、而不是让页面自己 `for` 一遍 `updateTopic`：
 * 每条 `updateTopic` 都会刷一次索引 + 数据版本号 +1（视图跟着重拉一页）
 * ⇒ 勾 17 条就是把列表重拉 17 遍，中间还能看见卡片一条条消失。
 * 这里**逐条写库、最后只刷一次**：用户点完确定，页面只动一次。
 *
 * <p>⚠️ **失败不中断**：一条写失败（网络抖、被别处删了）不该让剩下的全不执行。
 * 用 `allSettled` 数成功/失败，最后如实报数 —— 报"已归类 15 条、2 条失败"
 * 比一句"已保存"强，比静默失败强得多。
 *
 * @param {Array<{id:number}>} topics 选中的知识点
 * @param {number|null} categoryId 目标目录；null = 移回「待归类」
 * @returns {Promise<{ok:number, failed:number}>}
 */
export async function updateTopicsCategory(topics, categoryId) {
  const list = (topics || []).filter((t) => t && t.id != null)
  if (!list.length) return { ok: 0, failed: 0 }
  const results = await Promise.allSettled(
    list.map((t) => updateTopic(t.id, { categoryId }, { refresh: false }))
  )
  const ok = results.filter((r) => r.status === 'fulfilled').length
  const failed = results.length - ok
  // 一条都没写成功就**不刷**（刷了也白刷，还让页面重拉一次）。
  if (ok) await refreshAfterTopicChange(false)
  return { ok, failed }
}

/**
 * **批量删除**的交互流（统一确认框 → 逐条删 → 一次刷新 → 提示）。
 *
 * <p>和单条 `confirmDeleteTopic` 分开写：批量要一次发 N 个请求、最后只刷一次列表。
 * <p>⚠️ 确认框**不带数量、不带后果说明**（2026-11-13 用户定的口径）：
 * 勾了哪几张在弹框之前就看得见，数量是复述界面。见 `utils/confirm.js`。
 *
 * @returns {Promise<boolean>} 是否真的删了
 */
export async function confirmDeleteTopics(topics) {
  const list = (topics || []).filter((t) => t && t.id != null)
  if (!list.length) return false
  if (!(await confirmAction('批量删除知识点'))) return false
  const results = await Promise.allSettled(list.map((t) => deleteTopic(t.id, { refresh: false })))
  const ok = results.filter((r) => r.status === 'fulfilled').length
  if (ok) await refreshAfterTopicChange(true)
  if (ok === list.length) ElMessage.success(`已删除 ${ok} 条`)
  else ElMessage.warning(`已删除 ${ok} 条，${list.length - ok} 条失败`)
  return ok > 0
}

/**
 * 删除知识点的**交互流**（统一确认框 → 删 → 提示）。
 *
 * 列表页卡片和详情页顶栏是两个删除入口，但只走 `confirmAction` 一个出口
 * ⇒ 文案、按钮、取消行为不可能漂移（见 `utils/confirm.js`）。
 * **删除后各自要做什么由调用方决定**（列表页要刷新，详情页要返回上一页），
 * 所以这里只做"确认 + 删 + 提示"，返回是否真的执行了。
 */
export async function confirmDeleteTopic(topic) {
  if (!(await confirmAction('删除知识点'))) return false
  await deleteTopic(topic.id)
  ElMessage.success('已删除')
  return true
}

// ==================== 目录掌握度聚合 ====================
//
// 学习数据只剩**一层缓存**：
// - {@link categoryMasteryMap}：目录三指标聚合（`GET /kp/learning/tree` 一次拉全树），
//   侧边栏徽章 / 目录树徽章 / 书页顶部概览吃它（设计 §8.3 / 方案 C）。
//
// ⚠️ **单条知识点的学习投影缓存撤了**（2026-11-12）：详情页不再显示掌握度、
// 也没有「标记已学」按钮 ⇒ 那一页没有"看单条投影"的需求。
// 卡片的档位签走**列表响应里的字段**（masteryLevel）⇒ 判分后靠重拉那一页刷新。
//
// ⚠️ **掌握度的权威值在后端**（加权聚合、档位都是服务端算好的，设计 §8.1/§6.5），
// 这里只缓存和转发，**不在客户端另算一套公式**——目录无第二份掌握度。

/** categoryId → 三指标聚合 {eligibleCount, learnedCount, assessedCount, mastery, levelCounts} */
const categoryMasteryMap = ref(new Map())
const categoryMasterySlot = createLoadSlot()

/**
 * 目录三指标聚合：一次拉整棵树（设计 §11.1「不让前端逐目录请求」），按 categoryId 存。
 */
export async function ensureCategoryMasteryLoaded(force = false) {
  await loadOnce(categoryMasterySlot, force, async (isCurrent) => {
    const res = await apiGetLearningTree()
    const nextMap = new Map()
    for (const row of res?.data || []) nextMap.set(row.categoryId, row)
    // 同 topicIndex 的理由：旧请求晚回来不能把缓存倒退
    if (isCurrent()) categoryMasteryMap.value = nextMap
    return nextMap
  })
  return categoryMasteryMap.value
}

/**
 * 目录三指标（{eligibleCount, learnedCount, assessedCount, mastery}）；没拉过返回 null。
 * mastery 为 null = 没有合格条目 ⇒ 中性空态，**不是 0**（§8.1）。
 */
export function getCategoryMastery(categoryId) {
  return categoryMasteryMap.value.get(Number(categoryId)) || null
}

/** 学习数据变了（标记已学）⇒ 作废聚合，下次读重拉 */
export function invalidateCategoryMastery() {
  categoryMasteryMap.value = new Map()
  resetSlot(categoryMasterySlot)
}

/**
 * topicId → 单条档位 {level, mastery}（2026-11-14，目录树知识点行数据源）。
 *
 * <p>`GET /kp/learning/levels` 一次拉**当前用户全部有评估的知识点**（行数 = 考过的
 * 条数，量小）；没有评估的知识点不在 map 里 ⇒ 树行保持原样（不染色不画条）。
 * 与 categoryMasteryMap 同一套缓存纪律：权威值在后端，这里只缓存转发。
 */
const topicLevelsMap = ref(new Map())
const topicLevelsSlot = createLoadSlot()

export async function ensureTopicLevelsLoaded(force = false) {
  await loadOnce(topicLevelsSlot, force, async (isCurrent) => {
    const res = await apiGetMasteryLevels()
    const nextMap = new Map()
    for (const row of res?.data || []) nextMap.set(Number(row.topicId), row)
    if (isCurrent()) topicLevelsMap.value = nextMap
    return nextMap
  })
  return topicLevelsMap.value
}

/** 单条档位（{level, mastery}）；没有评估返回 null —— 树行据此不染色不画条 */
export function getTopicLevel(topicId) {
  return topicLevelsMap.value.get(Number(topicId)) || null
}

/**
 * 学习数据整体作废（判分一次会写一批知识点的证据/投影，逐条对账不值得）。
 * 答题器判分成功后调它。
 *
 * <p>⚠️ **还要 bump 数据版本号**（2026-11-12）：卡片的掌握度档位签是
 * **列表响应里的字段**（masteryLevel）⇒ 判分之后那一页的卡片标签是**旧的**。
 * 只清学习缓存不够 —— 那一页得**重拉**，标签才会变成新的档位。
 * ⇒ 学习缓存清了 + 数据版本号 +1（视图 watch 它重拉当前页）。
 */
export function invalidateLearningData() {
  invalidateCategoryMastery()
  resetSlot(topicLevelsSlot)
  topicLevelsMap.value = new Map()
  dataVersion.value++
}

// ==================== 标签 ====================
// ⚠️ **原来这里有个 `getAllTags()`**（返回缓存里的标签清单）—— 标签功能 2026-11-03
// 已从界面下线（`AppLearnView` 不再拉标签、不再渲染标签筛选），这个导出**全项目零调用**
// ⇒ 删除。标签数据的真实消费方只剩 `ensureAllTagsLoaded` / `renameTag` /
// `confirmDeleteTag`（后台逻辑暂时不去），它们读的是 `allTags` 缓存本身，不需要这个出口。

// ==================== 知识点类型 ====================

/** 类型清单。字典是全局的，懒加载一次就够 */
export function getTopicTypes() {
  return topicTypes.value
}

export async function ensureTopicTypesLoaded(force = false) {
  await loadOnce(topicTypesSlot, force, async (isCurrent) => {
    // 字典编码 → 字典类型 id。取的是 sys_dict_type 那张表的行，不是硬编码的数字。
    const typeRes = await getDictTypeList({ code: TOPIC_TYPE_DICT })
    const dictType = (typeRes?.data || [])[0]
    if (!dictType) {
      // **不上锁**：这次查不到就当没发生过，下一个调用方会重试
      // （返回 undefined = "这次没拿到"，状态停在 failed，下次还能试）。
      //
      // 原来这里是 `topicTypes.value = []; topicTypesLoaded = true`——把"这一次没查到"
      // 存成了"字典里就是没有类型"这个结论。于是上一行的短路生效，之后所有调用
      // 都直接返回那个空数组，永远不重试，只有整页刷新才能恢复。症状是"类型下拉
      // 忽然空了，刷新一下又好"，看起来像随机，其实是被缓存住的失败。
      //
      // 另外三个缓存（topics / categories / allTags）本来就只在真拿到数据时置 true，
      // 只有这一份反着来——口径统一，别给它开特例。
      return undefined
    }
    const itemRes = await getDictItemList({ dictTypeId: dictType.id })
    const next = (itemRes?.data || [])
      .filter((i) => i.enabled)
      .map((i) => ({ id: i.id, label: i.label, value: i.value }))
    // 只有最新发起的那次才落缓存（见 lazyLoad 的「版本号」）
    if (isCurrent()) topicTypes.value = next
    return next
  })
  return topicTypes.value
}

/**
 * 「默认档」= 清单里排第一的那条。**不靠 is_default 标记列**：
 * 排第一是可查可判的（一条 SELECT 就知道），而 NULL / 标记位那种"靠约定猜"的隐式
 * 状态最后都会变成 bug。新建知识点默认选它，它改名也还是默认——"它是默认档"
 * 和它叫什么名字无关。
 */
export function defaultTopicTypeId() {
  return topicTypes.value[0]?.id ?? null
}

/**
 * 按字典项的 **value** 找类型 id。找不到返回 null（**不回落成默认档**）。
 *
 * <p>回落会骗人：调用方要的是"就是这一档"，回落到默认档等于静默给出另一档，
 * 界面上看不出来、数据是错的。要么拿到那一档，要么明确知道没有。
 */
export function topicTypeIdByValue(value) {
  return topicTypes.value.find((t) => t.value === value)?.id ?? null
}

/**
 * 把知识点上可能悬空的 typeId 收口成清单里真实存在的 id。
 * 字典项是**软删**的，后台删掉一条之后，引用它的知识点 typeId 就指向一个查不出来的行。
 * 界面上不能因此显示一个空白或裸数字——统一当"排第一的那档"。
 */
export function resolveTopicTypeId(typeId) {
  if (typeId != null && topicTypes.value.some((t) => t.id === typeId)) return typeId
  return defaultTopicTypeId()
}

export function getTopicTypeLabel(typeId) {
  const hit = topicTypes.value.find((t) => t.id === resolveTopicTypeId(typeId))
  return hit?.label || ''
}

export async function ensureAllTagsLoaded(force = false) {
  await loadOnce(allTagsSlot, force, async (isCurrent) => {
    const res = await getTagList()
    // /kp/tag/list 返回 { id, name, refCount }，store 统一暴露 { id, name, count }
    // id 留着：标签管理 dialog 改名/删除要按 id 调接口，不用再单独拉一份
    const next = (res?.data || []).map((t) => ({ id: t.id, name: t.name, count: t.refCount }))
    // 只有最新发起的那次才落缓存（见 lazyLoad 的「版本号」）
    if (isCurrent()) allTags.value = next
    return next
  })
  return allTags.value
}

export function resolveTags(rawTags) {
  return (rawTags || []).map(decodeTag).filter((tag) => tag.name)
}

/**
 * 改标签名 / 删标签。放在 store 而不是组件里，是因为这两件事都不是"组件能自己做完"的：
 * 改名要连带刷**两份**缓存（标签表 + 每个 topic 上的 tags 字段），
 * 删标签要连带清所有 topic 的关联。两个入口（列表页的标签下拉、详情页的标签编辑）
 * 共用同一份，逻辑写在组件里就得抄两遍。
 *
 * 确认框/输入框也收在这里，和 confirmDeleteCategory 同一个路子——
 * 免得"目录的删除确认在 store、标签的删除确认在组件"这种不对称。
 *
 * 返回是否真的做了（用户取消一律 false，调用方据此不刷新界面）。
 */
export async function renameTag(tag) {
  let next
  try {
    const r = await ElMessageBox.prompt('新的标签名', `重命名「${tag.name}」`, {
      confirmButtonText: '保存',
      cancelButtonText: '取消',
      inputValue: tag.name,
      // 长度限制是后端 @Size(max=50) 定死的，前端先挡一道，省得发出去被弹回来
      inputValidator: (v) => {
        const s = String(v || '').trim()
        if (!s) return '标签名不能为空'
        if (s.length > 50) return '标签名不能超过 50 个字'
        return true
      }
    })
    next = String(r.value || '').trim()
  } catch {
    return false  // 用户取消
  }
  if (!next || next === tag.name) return false
  // 后端按名字定位（KpTagRenameReq 只有 oldName / newName，没有 id）
  await apiRenameTag({ oldName: tag.name, newName: next })
  await ensureAllTagsLoaded(true)
  await refreshTopicIndexes()  // topic 上的 tags 字段带着旧名字，不刷就是脏的
  ElMessage.success('已更新')
  return true
}

/** 删标签。后端会同时清掉所有 topic 上的关联，topic 本身不删（和删目录一个语义）。 */
export async function confirmDeleteTag(tag) {
  if (!(await confirmAction('删除标签'))) return false
  await apiDeleteTag(tag.id)
  await ensureAllTagsLoaded(true)
  await refreshTopicIndexes()
  ElMessage.success('已删除')
  return true
}

/**
 * 正文摘要：去掉 markdown 标记和多余空白，截到 max 字。
 * 两处共用同一份规则——知识点卡片的标题区、以及删除确认框里"删的是哪一条"。
 * 之前这份只内联在 TopicCard 里，确认框要显示内容首句就得再抄一遍，
 * 两处的清洗规则以后必然走偏（谁改了一处，另一处还按老规则转）。
 */
export function excerptOf(content, max = 90) {
  const text = String(content || '')
    .replace(/<br\s*\/?>/gi, ' ')
    .replace(/[*#>`_]/g, '')
    .replace(/\s+/g, ' ')
    .trim()
  if (!text) return ''
  return text.length > max ? `${text.slice(0, max)}…` : text
}

// ==================== 知识目录（树的骨架） ====================

export function getCategories() {
  return categories.value
}

/**
 * 目录 id → 目录对象。**查表用，不扫数组**。
 *
 * <p>`getCategory` 原来是 `categories.value.find(...)` —— 线性扫描，245 个目录里
 * 找一个要走 245 步。而**算目录计数时每条知识点都要沿祖先链往上走**（1683 条 ×
 * 几层），实测 `getCategoryCountMap` 单次 **1.75ms**；侧边栏和目录树各算一份
 * ⇒ 3.5ms。改成 Map 查表后单次 **0.18ms（快 10 倍）**，两处都受益。
 *
 * <p>**口径一个字没改**（还是同一条链、同一个上限），只是查法换了。
 */
const categoriesById = computed(() => {
  const m = new Map()
  for (const c of categories.value) m.set(c.id, c)
  return m
})

export async function ensureCategoriesLoaded(force = false) {
  await loadOnce(categoriesSlot, force, async (isCurrent) => {
    const res = await getCategoryList()
    // /kp/category/list 返回 { id, name, parentId }；refCount 前端自己算（见 getCategoryCountMap）
    const next = (res?.data || []).map(normalizeCategory)
    // 只有最新发起的那次才落缓存（见 lazyLoad 的「版本号」）
    if (isCurrent()) categories.value = next
    return next
  })
  return categories.value
}

export function getCategory(id) {
  if (id == null) return null
  return categoriesById.value.get(Number(id)) || null
}

/** 某节点的直接子目录；传 ROOT_CATEGORY 拿的就是侧边栏那一层 */
export function getChildCategories(parentId) {
  return categories.value.filter((c) => c.parentId === Number(parentId))
}



/**
 * 从根到该节点的祖先链（不含"知识库"这个虚拟根），面包屑直接 v-for。
 * 找不到的节点给空链：面包屑退化成只剩根，不崩。
 */
export function getCategoryPath(id) {
  const chain = []
  let cur = getCategory(id)
  // 上限 MAX_TREE_DEPTH 层：数据被改坏出现环时不会把渲染卡死
  while (cur && chain.length < MAX_TREE_DEPTH) {
    chain.unshift(cur)
    cur = getCategory(cur.parentId)
  }
  return chain
}

/**
 * 目录从根到自己的一串**名字**（["计算机网络", "3. 网络协议", "HTTP"]）。
 *
 * <p>**只有这一处把目录链变成名字串**：祖先链本身是 {@link getCategoryPath}，
 * 而"取名字"这一步各写各的就会漂（有一处忘了取 name、去取 id 也不会报错）。
 *
 * <p>两处消费者：侧边栏过滤结果要给"祖先路径"（去掉自己那截），
 * 生成弹窗的材料要给完整路径（"MySQL/1. 架构/1.1 连接"）。
 * 分隔符由调用方决定 —— 两边要的分隔符不一样，但**名字串本身是同一份**。
 *
 * @param {number} id 目录 id
 * @returns {string[]} 从根到自己的名字数组
 */
export function categoryNameChain(id) {
  return getCategoryPath(id).map((c) => c.name)
}

/** 该节点及其所有后代的 id。进目录后按子树筛列表时用。 */
export function getCategorySubtreeIds(id) {
  const root = Number(id)
  // 把自己算进去：漏掉的话，进「集合」只能看到孙目录里的东西，直接挂在
  // 「集合」下的知识点会被自己的目录筛掉——目录一进去显示是空的。
  const ids = new Set([root])
  const walk = (pid) => {
    for (const c of getChildCategories(pid)) {
      if (ids.has(c.id)) continue
      ids.add(c.id)
      walk(c.id)
    }
  }
  walk(root)
  return ids
}

/**
 * 一个「范围」的判据（**只有这一份规则**）。
 *
 * <p>范围有三种：`ALL`（全部）、`UNCATEGORIZED`（待归类）、目录 id（**含整棵子树**）。
 *
 * <p>列表页筛出来的就是这个范围，考试要考的就必须是它 ⇒ **收在这里一份，两边共用**。
 * 各算一遍就是两套规则：目录一改，列表看到的和考的就对不上，**而界面上什么异常都看不到**。
 *
 * <p>⚠️ **列表页那一页的请求也用这条规则算出来的目录 id 列表**（`categoryIds`）——
 * 服务端不自己算子树，就是为了让"列表筛出来的"和"这里算出来的"**永远是一批**。
 *
 * <p>**给谓词，不给条目列表**：要的是"这条在不在范围内"，一条一条判就够 ——
 * 先算出范围内的条目、再拿它们的 id 去过滤条目，同一棵子树要过滤两遍。
 *
 * @param {string|number} scope `ALL` / `UNCATEGORIZED` / 目录 id
 * @returns {(t: object) => boolean} 这条知识点在不在这个范围里
 */
export function scopeMatcher(scope) {
  if (scope === ALL) return () => true
  if (scope === UNCATEGORIZED) return (t) => t.categoryId == null
  // **子树只算一次**：谓词一条一条判就是几百次调用，每次重算子树是几百次遍历。
  const ids = getCategorySubtreeIds(scope)
  return (t) => ids.has(t.categoryId)
}

/**
 * 深度优先的**前序**扁平行：[{ kind: 'cat', c, depth } | { kind: 'topic', t, depth }]，
 * 拖拽浮树直接 v-for 这一份。
 *
 * 必须是前序——父节点后面紧跟自己的子树。要是"先把本层列完再把子目录组接在后面"
 * （层序），「计算机网络」会夹在「JAVA」和「JAVA 的子目录」中间，缩进再准也读不出
 * 父子关系，看起来就像三个平级目录。
 *
 * `withTopics` 把**知识点也摊成叶子行**（浏览模式用，拖拽落点模式不要）：
 * 知识点挂在哪一级就在哪一级出现，**排在同级的子目录前面** ——
 * 「总览」是挂在父目录上的知识点，于是它和自己的子目录并排，一眼看得见。
 * 拖拽落点模式不给：那里每一行都是可投放的目录，多出来的叶子行只会挡住命中检测。
 *
 * seen + 层数上限双重防环，数据被改坏出现环时不会把渲染卡死。
 */
export function flattenCategoryTree(roots, { withTopics = false } = {}) {
  const rows = []
  const seen = new Set()
  // 知识点按目录分组：**一次遍历建表**，别在每个目录上全量扫一遍知识点
  const byCat = withTopics ? topicsByCategory() : new Map()
  const walk = (list, depth) => {
    if (depth > MAX_TREE_DEPTH) return
    for (const c of list) {
      if (seen.has(c.id)) continue
      seen.add(c.id)
      rows.push({ kind: 'cat', c, depth })
      // ⚠️ **直挂知识点在前、子目录在后**（2026-11-10 用户：「这里展示的一个目录的总览
      // 应该放在这个目录下的第一个 而不是放在子目录之后」）。
      //
      // <p>之前是反过来的（子目录在前、总览在整张卡片身的末尾，像"本章小结"）——
      // 那是 2026-11-08 复刻 book-bak 老稿时的判断，现在按用户要求改成
      // **「这个目录的第一条就是它的总览」**：读一本书的目录时，
      // 「这一章讲什么」的总览就该紧跟在章标题后面，而不是翻到最后才看到。
      //
      // <p>⚠️ **前序不变式不破**：父行仍在自己所有后代（子目录**和**知识点）之前，
      // `branchyIds` / `ancestorIds` / `visibleRows` 都只依赖这一点 ⇒
      // 把知识点提到子目录之前不影响它们（知识点是叶子，提到自己父行后面仍然是前序）。
      // ⚠️ `branchyIds` 的判据是"下一行更深" ⇒ 直挂知识点在子目录之前时，
      // 章行后面紧跟的是知识点行（depth 更大）⇒ 章**仍然**算"有下级"、仍有箭头（与现状一致）。
      for (const t of byCat.get(c.id) || []) {
        rows.push({ kind: 'topic', t, depth: depth + 1 })
      }
      walk(getChildCategories(c.id), depth + 1)
    }
  }
  walk(roots, 0)
  return rows
}

/**
 * 直挂在各目录下的知识点：{ [categoryId]: [topic] }。
 *
 * <p>**只认直挂**：挂在子目录下的知识点在子目录那一层出现一次，不会重复出现两遍。
 *
 * <p>顺序分两档（2026-11-03）—— **两类内容的"顺序"根本不是一回事**：
 * - **学习类**（AI 生成的教材内容）→ **id 升序** = 生成顺序 = 教材的讲解顺序。
 *   「5.3 HTTP」那 10 条按 id 排出来正好是「报文格式 → 请求方法 → 首部字段 → 长连接 →
 *   Cookie → 同源策略 → 缓存 → HTTP/2 → HTTP/3 → HPACK」，**正是教材序**
 *   （模型是按讲解顺序落库的，create_time 逐秒递增）。倒过来排 = 把整本书倒着展示。
 * - **笔记类**（随手记）→ **id 降序** = 最近记的在前。笔记没有教材序，
 *   它是一条**时间流** ⇒ 跟学习内容共用一个序就是错的。
 *
 * <p>两组**不混排**：学习组在前（它是主线），笔记组在后（是补充）。
 *
 * <p>⚠️ **判据用字典项的 value**（`learn`），不写死 id —— id 是数据，换环境就变。
 * 找不到「学习」这一档就**全当笔记**（保守：笔记是默认档）。
 */
function topicsByCategory() {
  const byCat = new Map()
  const learnId = topicTypeIdByValue(TOPIC_TYPE_LEARN)
  // ⚠️ **用全集索引**（不是搜索索引）：树的知识点行是"这本书里全部的知识点"。
  // 用搜索索引的话，搜索态下树上就只剩命中的那些 ⇒ **树被过滤了**
  // （用户 2026-11-05：「展示的树就要是完整的啊 你把目录数据过滤了
  // 然后加个命中栏的意义在哪？」）。
  for (const t of getFullTopicIndex()) {
    if (t.categoryId == null) continue
    const list = byCat.get(t.categoryId)
    if (list) list.push(t)
    else byCat.set(t.categoryId, [t])
  }
  for (const list of byCat.values()) {
    list.sort((a, b) => {
      const aLearn = learnId != null && a.typeId === learnId
      const bLearn = learnId != null && b.typeId === learnId
      if (aLearn !== bLearn) return aLearn ? -1 : 1 // 学习组在前
      return aLearn ? a.id - b.id : b.id - a.id // 学习升序（教材序）/ 笔记降序（最近在前）
    })
  }
  return byCat
}

// ==================== 拖拽（挪目录） ====================
// 命中检测状态机（dnd / hitCategory / checkDrop / dndStart / dndMove / dndDrop /
// dndReset）已拆到 `useCategoryDnd.js`（2026-11-15）—— 它是纯 DOM 交互逻辑，
// 不是数据缓存；store 这边只留真正动数据的那一步：

/**
 * 移动目录。**只是改一个 parent_id**——下级目录靠 parent_id 引用、知识点靠
 * category_id 引用，所以整棵树跟着动是自动的，不用搬任何数据。
 *
 * 成环和重名的提示由后端给（那里才是真保证），这里只负责把错误显示出来。
 * 命名跟同文件的 renameCategory / deleteCategory 一齐：api 那份是 import 时的别名
 * （apiMoveCategory），这三个才是对外的 store 能力。
 */
export async function moveCategory(categoryId, parentId) {
  try {
    await apiMoveCategory({ id: categoryId, parentId: parentId || null })
    await ensureCategoriesLoaded(true)
  } catch (e) {
    // 提示归 request.js（走 axios 实例，唯一提示出口）
  }
}

/**
 * 各目录下的条数：{ [categoryId]: n }，**含子树**。
 *
 * 和「点进目录看到整棵子树」是同一个口径：侧边栏 JAVA 显示 8，
 * 点进去正好 8 条，级联里集合 3 + 并发 2 + 自己 3 = 8，数字对得上。
 * 混用两种口径（父节点合计、子节点直挂）会让人以为少了东西。
 *
 * 一次遍历出全量计数，视图里侧边栏/级联/拖拽树共用这一份，不走后端 group by。
 *
 * ⚠️ **这一份是模块级 computed，不是每次调用现算**（2026-11-03 晚 · 性能）：
 * 侧边栏和目录树**各有一处调用**，两处都在渲染时读同一批数据 ⇒ 同一份活算了两次。
 * 提成模块级 computed 之后两边读的是**同一个缓存**，数据没变就只算一次。
 *
 * ⚠️ **它数的是"搜索索引"**（当前关键词下的全集）⇒ 搜索态下它是**命中数**
 * （侧边栏那一列要的就是这个："这一本命中了几条"）。
 */
const categoryCountMap = computed(() => countByCategory(topicIndex.value.values()))

export function getCategoryCountMap() {
  return categoryCountMap.value
}

/**
 * **各目录下的条数（全量口径）**：目录树那一列要的是这个。
 *
 * <p>⚠️ **为什么要有两份**（2026-11-05 用户：「目录树的搜索展示完整
 * 不要把目录树和数据做任何过滤了」）：树的行尾那个数字说的是
 * **"这一节一共多少条"**（树画的是完整的书，数字就得是总数），
 * 侧边栏那个数字说的是**"这一本命中了几条"**（它是一份筛选结果）。
 * 一个数字两个意思 ⇒ 界面上什么异常都看不出来。
 *
 * <p>⚠️ **无搜索词时两份是同一份**（那时搜索索引就是全集）。
 */
const fullCategoryCountMap = computed(() => countByCategory(fullTopicIndex.value.values()))

export function getFullCategoryCountMap() {
  return fullCategoryCountMap.value
}

/**
 * **按目录聚合计数**（含子树）：沿祖先链逐层 +1。
 *
 * <p>⚠️ **上限 MAX_TREE_DEPTH 层防环**，坏数据不至于把渲染卡死。
 *
 * @param {Iterable} topics 知识点（结构索引条目）
 * @returns {object} { [categoryId]: n }
 */
function countByCategory(topics) {
  const counts = {}
  for (const t of topics) {
    if (t.categoryId == null) continue
    let cur = getCategory(t.categoryId)
    for (let i = 0; cur && i < MAX_TREE_DEPTH; i++) {
      counts[cur.id] = (counts[cur.id] || 0) + 1
      cur = getCategory(cur.parentId)
    }
  }
  return counts
}

/**
 * 未归类（categoryId 为 null）的条数。**同样提成模块级 computed**：
 * 侧边栏和列表页各读一次，现算就是两遍。
 */
const uncategorizedTotal = computed(() => {
  let n = 0
  for (const t of topicIndex.value.values()) {
    if (t.categoryId == null) n++
  }
  return n
})

export function getUncategorizedCount() {
  return uncategorizedTotal.value
}

/**
 * 新建目录，**返回刚建好的那一个**（拿不到时返回 null）。
 *
 * 后端建目录只返回「成功」，不给 id；而唯一键是 (user_id, parent_id, name)，
 * **同名目录可以存在于不同父下**（`操作系统/内存` 和 `计算机网络/内存` 是两个），
 * 所以回查必须**名字 + 父级一起比**，只按名字找会拿到另一个。
 *
 * 这个回查放在这里而不是让每个调用方自己写，是因为它**每次新建都得做一遍**，
 * 不做的话调用方就必然各写一份（#98：两个采纳弹框里一模一样地写了两遍）。
 * 源头给全，调用方一行拿到。
 */
export async function createCategory(name, parentId = ROOT_CATEGORY) {
  await apiCreateCategory({ name, parentId })
  await ensureCategoriesLoaded(true)
  return getCategories().find(
    (c) => c.name === name && (c.parentId || ROOT_CATEGORY) === (parentId || ROOT_CATEGORY)
  ) || null
}

/**
 * 批量创建目录树（`{ name, children }` 的树）。
 *
 * <p>**一次请求建完整棵树**（服务端一个事务）—— 逐个建就是 N 次请求，
 * 中途失败留半棵树。建完刷目录缓存。
 *
 * @param {Array} nodes 目录树
 * @param {number} parentId 挂在谁底下（0 = 挂根）。服务端按这个 id 定位父目录，
 *        **层数不限**（`kp_category` 是邻接表，层数只是数据 —— 见 `utils/outlineTree.js`）。
 * @returns {Array} 服务端返回的这批目录 `{ id, name, parentId }` ——
 *        知识点要挂到具体目录下，**id 才是身份**（名字会变、还可能撞名）。
 */
export async function importCategories(nodes, parentId = 0) {
  const res = await apiImportCategories({ nodes, parentId: parentId || 0 })
  await ensureCategoriesLoaded(true)
  return res?.data || []
}

/** 重命名。按 id 定位，不按名字——名字是可变字段，拿它当键认不准改的是哪一个 */
export async function renameCategory(id, newName) {
  await apiRenameCategory({ id, newName })
  await ensureCategoriesLoaded(true)
}

// 「删目录」对话框的交互流（状态 + 发起 + 出口）已拆到 `categoryDeleteDialog.js`
// （2026-11-15）——那是交互编排不是数据缓存；这里只留真删的那一步：

export async function deleteCategory(id, cascadeTopics = false) {
  await apiDeleteCategory(id, cascadeTopics)
  // 目录下知识点回到了"未归类"（或被一起删了），topic 缓存也得刷
  await ensureCategoriesLoaded(true)
  await refreshTopicIndexes()
  // ⚠️ **数据版本号也要 +1**（2026-11-06）：删目录改的是**列表页看到的东西**
  // （那条目录下的知识点回到未归类、目录本身从树上消失）⇒ 视图得重拉当前页。
  // 原来只有知识点增删改才 +1 ⇒ 删完目录视图不重拉 ⇒
  // ① 卡片页里"这个目录下的知识点"还挂着刚被删掉的那批；
  // ② 树视图里把**那本书**删掉之后，这一屏从树变成卡片，**卡片是空的**
  // （树视图期间列表根本没拉过）—— 用户看到的是一片空白。
  dataVersion.value++
}
