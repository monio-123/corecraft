import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getTopicList, getTopicById, getTagList, getCategoryList,
  createTopic as apiCreateTopic, updateTopic as apiUpdateTopic, deleteTopic as apiDeleteTopic,
  createCategory as apiCreateCategory, renameCategory as apiRenameCategory,
  deleteCategory as apiDeleteCategory,
  renameTag as apiRenameTag, deleteTag as apiDeleteTag
} from '../../api/knowledge'
import { onAuthChange } from '../../utils/auth'

// ==================== 内部状态 ====================

const topicMap = ref(new Map())
let topicsLoaded = false

// 标签缓存：{ name, count }。原本从 topicMap 聚合，要先 ensureTopicsLoaded 拉全部 topic。
// 现在改成直接调 /kp/tag/list 拉全部 tag（含孤儿），不依赖全部 topic。
// 详情页打开不再调 /kp/topic/list，"选择已有标签" popover 懒加载触发请求。
const allTags = ref([])
let allTagsLoaded = false

// 目录缓存：[{ id, name, parentId }]。树的骨架，标签不参与建树。
// parentId 归一：后端对历史 NULL 返回 0，但老数据/老接口可能给 null，
// 这里统一收口成 0，全站只面对"0 = 顶层"一种表示。
const categories = ref([])
let categoriesLoaded = false

function normalizeCategory(c) {
  return { id: c.id, name: c.name, parentId: c.parentId || ROOT_CATEGORY }
}

// 三个缓存都是模块级的，只有整页刷新才会重建。换账号时不丢，B 会看到 A 的知识点/目录/标签。
// 登录态一变就全部作废：数据本身也要清，否则 loaded=false 但列表里还挂着旧账号的内容。
onAuthChange(() => {
  topicMap.value = new Map()
  topicsLoaded = false
  allTags.value = []
  allTagsLoaded = false
  categories.value = []
  categoriesLoaded = false
})

// ==================== 通用 helpers ====================

/** 顶层目录的 parentId。侧边栏、级联、面包屑都以它为根 */
export const ROOT_CATEGORY = 0
/** 「待归类」不是目录，是个固定桶。用字符串哨兵，避免和数字 id 撞类型 */
export const UNCATEGORIZED = 'uncategorized'

function parseJson(raw, fallback) {
  try {
    return raw ? JSON.parse(raw) : fallback
  } catch {
    return fallback
  }
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

export function encodeTag(tag) {
  if (typeof tag === 'string') return tag
  return tag?.name || ''
}

function decodeTag(raw) {
  const value = String(raw || '').trim()
  return { name: value }
}

export function formatTagLabel(tag) {
  if (!tag) return ''
  return tag.name || ''
}

function decodeQuizQuestion(raw) {
  if (typeof raw !== 'string') return raw
  try {
    const parsed = JSON.parse(raw)
    if (parsed && typeof parsed === 'object' && parsed.question) {
      return parsed
    }
  } catch {
    // ignore
  }
  return {
    type: 'single',
    question: raw,
    options: [],
    answer: 0
  }
}

// ==================== 标准化 ====================

function normalizeTopic(item) {
  return {
    id: item.id,
    title: item.title || '',
    content: item.content || '',
    parentTopicId: item.parentTopicId ?? null,
    categoryId: item.categoryId ?? null,
    tags: parseJson(item.tags, []).map(decodeTag),
    quizQuestions: parseJson(item.quizQuestions, []).map(decodeQuizQuestion),
    relatedTopicIds: item.relatedTopicIds || [],
    createdAt: item.createTime || '',
    updatedAt: item.updateTime || ''
  }
}

// ==================== 知识点 ====================

export async function ensureTopicsLoaded(force = false) {
  if (!topicsLoaded || force) {
    const res = await getTopicList()
    const nextMap = new Map()
    ;(res?.data || []).map(normalizeTopic).forEach((topic) => nextMap.set(topic.id, topic))
    topicMap.value = nextMap
    topicsLoaded = true
  }
  return getTopics()
}

export function getTopics() {
  return [...topicMap.value.values()].sort(
    (a, b) => String(b.createdAt).localeCompare(String(a.createdAt))
  )
}

export function getTopic(id) {
  return topicMap.value.get(Number(id)) || topicMap.value.get(id) || null
}

export async function fetchTopic(id) {
  const res = await getTopicById(id)
  if (!res?.data) return null
  const topic = normalizeTopic(res.data)
  const nextMap = new Map(topicMap.value)
  nextMap.set(topic.id, topic)
  topicMap.value = nextMap
  return topic
}

// 知识点变动之后刷缓存。**tagsChanged 必须由调用点老实说**：
// 标签的 refCount 存在**另一份**缓存 allTags 里（来自 /kp/tag/list），刷 topicMap 刷不到它。
// 之前三条路径（建 / 改 / 删知识点）都只刷了 topicMap，标签下拉里的计数于是永远是旧的，
// 表现为"详情页移掉一个标签 → 返回列表 → 计数没变，得刷新页面"。
// 收敛成一个函数，调用点只回答"这次动没动标签"，没动就不发那个多余请求。
async function refreshAfterTopicChange(tagsChanged) {
  await ensureTopicsLoaded(true)
  if (tagsChanged) await ensureAllTagsLoaded(true)
}

export async function createTopic({ title, content, tags, quizQuestions, parentTopicId, categoryId }) {
  const beforeIds = new Set(getTopics().map((item) => item.id))
  const encoded = (tags || []).map(encodeTag).filter(Boolean)
  await apiCreateTopic({
    title: title || '',
    content: content || '',
    parentTopicId: parentTopicId || null,
    categoryId: categoryId || null,
    tags: encoded,
    quizQuestions: (quizQuestions || []).map((q) => typeof q === 'string' ? q : JSON.stringify(q))
  })
  // 带标签建出来才会多出引用；不带标签建一条不碰 refCount
  await refreshAfterTopicChange(encoded.length > 0)
  return getTopics().find((item) => !beforeIds.has(item.id)) || null
}

export async function updateTopic(id, patch) {
  // categoryId 的 null 有含义（未归类），跟"没传这个字段"不是一回事。
  // 后端对 categoryId 是无条件覆盖写（配合 updateStrategy = ALWAYS 支持置空），
  // 所以没传时必须回填当前值，否则会被当成 null 误抹掉归类。
  const categoryId = patch.categoryId !== undefined
    ? patch.categoryId
    : (getTopic(id)?.categoryId ?? null)
  await apiUpdateTopic({
    id,
    title: patch.title,
    content: patch.content,
    parentTopicId: patch.parentTopicId,
    categoryId,
    tags: patch.tags ? patch.tags.map(encodeTag).filter(Boolean) : undefined,
    quizQuestions: patch.quizQuestions
      ? patch.quizQuestions.map((q) => typeof q === 'string' ? q : JSON.stringify(q))
      : undefined
  })
  await refreshAfterTopicChange(patch.tags !== undefined)
  return getTopic(id)
}

export async function deleteTopic(id) {
  await apiDeleteTopic(id)
  // 删一条一定会带走它身上的标签引用（哪怕它本来没标签），所以这里没法省那个请求
  await refreshAfterTopicChange(true)
}

// ==================== 标签 ====================

export function getAllTags() {
  // 直接返回缓存（懒加载见 ensureAllTagsLoaded）。不再从 topicMap 聚合——避免详情页打开时
  // 必须先 ensureTopicsLoaded 拉全部 topic。
  return allTags.value
}

export async function ensureAllTagsLoaded(force = false) {
  if (allTagsLoaded && !force) return allTags.value
  const res = await getTagList()
  // /kp/tag/list 返回 { id, name, refCount }，store 统一暴露 { id, name, count }
  // id 留着：标签管理 dialog 改名/删除要按 id 调接口，不用再单独拉一份
  allTags.value = (res?.data || []).map((t) => ({ id: t.id, name: t.name, count: t.refCount }))
  allTagsLoaded = true
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
  await ensureTopicsLoaded(true)  // topic 上的 tags 字段带着旧名字，不刷就是脏的
  ElMessage.success('标签已重命名')
  return true
}

/** 删标签。后端会同时清掉所有 topic 上的关联，topic 本身不删（和删目录一个语义）。 */
export async function confirmDeleteTag(tag) {
  try {
    await ElMessageBox.confirm(
      `「${tag.name}」会从 ${tag.count || 0} 条知识点上移除，知识点本身不删。确定删除？`,
      '删除标签',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return false  // 用户取消
  }
  await apiDeleteTag(tag.id)
  await ensureAllTagsLoaded(true)
  await ensureTopicsLoaded(true)
  ElMessage.success('标签已删除')
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

/**
 * 删除确认框的整句文案。有标题用标题，没标题退回正文首句，都没有就用兜底句。
 * 列表页卡片和详情页顶栏是两个删除入口，规则必须一致：随手记存进来的知识点
 * title 本来就是空的，一屏卡片全是「未命名」时两个弹框长得一模一样，
 * 删错了都不知道删了哪条。两个入口调 ElMessageBox 的参数（按钮文案等）也相同，
 * 所以整句一起收敛在这里，而不是只共享"叫什么"再各自拼三元。
 */
export function deleteConfirmMessage(topic) {
  const label = String(topic?.title || excerptOf(topic?.content, 20) || '').trim()
  return label ? `确定删除「${label}」吗？` : '确定删除这条知识点吗？'
}

// ==================== 知识目录（树的骨架） ====================

export function getCategories() {
  return categories.value
}

export async function ensureCategoriesLoaded(force = false) {
  if (categoriesLoaded && !force) return categories.value
  const res = await getCategoryList()
  // /kp/category/list 返回 { id, name, parentId }；refCount 前端自己算（见 getCategoryCountMap）
  categories.value = (res?.data || []).map(normalizeCategory)
  categoriesLoaded = true
  return categories.value
}

export function getCategory(id) {
  if (id == null) return null
  return categories.value.find((c) => c.id === Number(id)) || null
}

/** 某节点的直接子目录；传 ROOT_CATEGORY 拿的就是侧边栏那一层 */
export function getChildCategories(parentId) {
  return categories.value.filter((c) => c.parentId === Number(parentId))
}

/**
 * 该目录下还有没有下级。只剩一个用途：空目录 hover 时不弹弹层（弹出来是个空壳）。
 * 之前还拿它决定侧边栏给「›」还是给「＋」——那行现在统一成「⋯」菜单，不再判断。
 */
export function hasChildCategories(id) {
  return getChildCategories(id).length > 0
}

/**
 * 从根到该节点的祖先链（不含"知识库"这个虚拟根），面包屑直接 v-for。
 * 找不到的节点给空链：面包屑退化成只剩根，不崩。
 */
export function getCategoryPath(id) {
  const chain = []
  let cur = getCategory(id)
  // 上限 10 层：数据被改坏出现环时不会把渲染卡死
  while (cur && chain.length < 10) {
    chain.unshift(cur)
    cur = getCategory(cur.parentId)
  }
  return chain
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
 * 深度优先的**前序**扁平行：[{ c, depth }]，拖拽浮树直接 v-for 这一份。
 *
 * 必须是前序——父节点后面紧跟自己的子树。要是"先把本层列完再把子目录组接在后面"
 * （层序），「计算机网络」会夹在「JAVA」和「JAVA 的子目录」中间，缩进再准也读不出
 * 父子关系，看起来就像三个平级目录。
 *
 * seen + 10 层上限双重防环，数据被改坏出现环时不会把渲染卡死。
 */
export function flattenCategoryTree(roots) {
  const rows = []
  const seen = new Set()
  const walk = (list, depth) => {
    if (depth > 10) return
    for (const c of list) {
      if (seen.has(c.id)) continue
      seen.add(c.id)
      rows.push({ c, depth })
      walk(getChildCategories(c.id), depth + 1)
    }
  }
  walk(roots, 0)
  return rows
}

// ==================== 归类 ====================

/**
 * 归类的唯一入口：卡片的 chip 点选、侧边栏目录树的拖拽落点、「待归类」的拖拽落点，
 * 最终都调它。原来这逻辑写在 AppLearnView 里，落点搬到 AppLayout 后就得有两份，
 * 收到这里——三条路径、同一套写入和同一句反馈。
 */
export async function categorizeTopic(topic, categoryId) {
  if (categoryId === topic.categoryId) return
  await updateTopic(topic.id, { categoryId })
  ElMessage.success(categoryId == null ? '已移回待归类' : `已归入「${getCategoryName(categoryId)}」`)
}

// ==================== 拖拽归类 ====================

/**
 * 拖拽状态。卡片在 AppLearnView，落点浮层在 AppLayout 侧边栏外——跨组件共享这一份。
 *
 * ⚠️ 这里**不用 HTML5 Drag and Drop**：那套 API 在触摸设备上不工作（iOS Safari 完全
 * 不支持，Android Chrome 长按会先触发原生文本选择菜单，dragstart 根本轮不到触发），
 * 平板上表现为"长按没反应"。现在由自建的 Pointer Events 接管三端，见 useTopicDrag.js。
 *
 * 原来靠 dataTransfer 传 topicId，现在直接放 topicId —— 省掉一次序列化，
 * 也让命中检测（elementFromPoint）能独立于拖拽协议存在。
 */
export const dnd = reactive({
  live: false,      // 浮层可见（PC 拖拽中 / 触屏点了「⋯」）
  picking: false,   // 触屏的"点选归类"模式：点树上一行即归类，不拖东西进去
  topicId: null,    // 正在归类哪条
  over: null,       // 当前命中的目录
  x: 0, y: 0,       // 指针当前位置（幽灵跟这个走）
  ax: 0, ay: 0,     // 浮层锚点：起拖那一刻记下来，拖拽期间不再变
  label: ''         // 幽灵上显示的一行文字
})

/** 命中检测：指针下最近的、带 data-cat-id 的目录行（CategoryTree drop 模式渲染的）。
 *
 * 拿不到坐标（0,0）时返回 null 而不是去命中 (0,0) 处的元素——
 * 视口原点通常没有目录行，但真命中了就是一次"凭空归类"，比不命中更坏。
 */
function hitCategory(x, y) {
  if (!x && !y) return null
  const el = document.elementFromPoint(x, y)
  const node = el?.closest?.('[data-cat-id]')
  return node ? Number(node.dataset.catId) : null
}

export function dndStart(topicId, x, y, label = '') {
  dnd.live = true
  dnd.picking = false
  dnd.topicId = topicId
  dnd.over = null
  dnd.x = x
  dnd.y = y
  dnd.label = label
  // 落点浮层锚在起拖的位置，整个拖拽过程中**不动**。
  // 之前浮层跟着指针走，结果是想把卡片拖进某一行时，浮层一直在飘，
  // 要追着它挪才能命中 —— 而且指针压在浮层上还会挡住命中检测。
  dnd.ax = x
  dnd.ay = y
}

export function dndMove(x, y) {
  dnd.x = x
  dnd.y = y
  dnd.over = hitCategory(x, y)
}

/**
 * 触屏的"点选归类"：点卡片的「⋯」→ 打开同一个落点浮层，改成点树上一行即归类。
 * 和 PC 拖拽共用这一个浮层 / 这棵树 / 这一套 data-cat-id，只是打开方式不同，
 * 所以两端看到的东西和操作结果完全一致（见 TopicCard 的 pick 入口）。
 */
export function dndPick(topicId, anchorEl) {
  const r = anchorEl?.getBoundingClientRect()
  const x = r ? r.left + r.width / 2 : window.innerWidth / 2
  const y = r ? r.bottom : window.innerHeight / 3
  dnd.live = true
  dnd.picking = true
  dnd.topicId = topicId
  dnd.over = null
  dnd.x = x
  dnd.y = y
  dnd.ax = x
  dnd.ay = y
}

/** 触屏点选：直接归类，不拖东西进去。浮层关掉。 */
export async function dndPickTo(categoryId) {
  const topicId = dnd.topicId
  dndReset()
  const topic = getTopics().find((t) => t.id === topicId)
  if (topic) await categorizeTopic(topic, categoryId)
}

/** 放下：命中了才归类，没命中就是取消（拖到空白处松手不该有副作用）。 */
export async function dndDrop() {
  const { topicId, over } = dnd
  dndReset()
  if (topicId == null || over == null) return
  const topic = getTopics().find((t) => t.id === topicId)
  if (topic) await categorizeTopic(topic, over)
}

export function dndReset() {
  dnd.live = false
  dnd.picking = false
  dnd.topicId = null
  dnd.over = null
}

export function getCategoryName(categoryId) {
  if (categoryId == null) return '未归类'
  return categories.value.find((c) => c.id === categoryId)?.name || '未归类'
}

/**
 * 各目录下的条数：{ [categoryId]: n }，**含子树**。
 *
 * 和「点进目录看到整棵子树」是同一个口径：侧边栏 JAVA 显示 8，
 * 点进去正好 8 条，级联里集合 3 + 并发 2 + 自己 3 = 8，数字对得上。
 * 混用两种口径（父节点合计、子节点直挂）会让人以为少了东西。
 *
 * 一次遍历出全量计数，视图里侧边栏/级联/拖拽树共用这一份，不走后端 group by。
 */
export function getCategoryCountMap() {
  const counts = {}
  for (const t of topicMap.value.values()) {
    if (t.categoryId == null) continue
    // 沿祖先链逐层 +1。上限 10 层防环，坏数据不至于把渲染卡死。
    let cur = getCategory(t.categoryId)
    for (let i = 0; cur && i < 10; i++) {
      counts[cur.id] = (counts[cur.id] || 0) + 1
      cur = getCategory(cur.parentId)
    }
  }
  return counts
}

/** 未归类（categoryId 为 null）的条数 */
export function getUncategorizedCount() {
  let n = 0
  for (const t of topicMap.value.values()) {
    if (t.categoryId == null) n++
  }
  return n
}

export async function createCategory(name, parentId = ROOT_CATEGORY) {
  await apiCreateCategory({ name, parentId })
  await ensureCategoriesLoaded(true)
}

/** 重命名。后端按 (userId, oldName) 定位，所以要传当前真名而不是 id */
export async function renameCategory(oldName, newName) {
  await apiRenameCategory({ oldName, newName })
  await ensureCategoriesLoaded(true)
}

export async function deleteCategory(id) {
  await apiDeleteCategory(id)
  // 目录下知识点回到了"未归类"，topic 缓存也得刷
  await ensureCategoriesLoaded(true)
  await ensureTopicsLoaded(true)
}

/**
 * 删除目录的交互流。入口在目录行「⋯」菜单里的"删除目录"。
 * 返回是否真的执行了：调用方据此决定要不要把整条树收走（删的可能正是它锚定的那个节点）。
 */
export async function confirmDeleteCategory(c) {
  try {
    await ElMessageBox.confirm(
      // 只留用户真正需要预判的两件事：知识点去哪、下级目录去哪。
      // 「不会被删」是对"回到未归类"的重复解释，删掉
      `「${c.name}」里的知识点回到未归类，下级目录挂到它的上级。确定删除？`,
      '删除目录',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return false
  }
  try {
    await deleteCategory(c.id)
    ElMessage.success('已删除')
    return true
  } catch (e) {
    ElMessage.error(e?.message || '删除失败')
    return false
  }
}

// ==================== AI（占位实现） ====================

const TOPIC_TEMPLATE = `## {{title}}

### 概述
{{title}} 是什么，解决什么问题，在什么场景下使用。

### 核心原理
深入解释 {{title}} 的底层工作机制，不只是表面概念。

### 关键要点
- 最重要的核心知识点 1
- 最重要的核心知识点 2
- 最重要的核心知识点 3

### 常见误区
学习 {{title}} 时容易混淆或理解错误的地方。

### 实践建议
实际开发/工作中如何应用 {{title}}，有哪些最佳实践。`

export async function aiGenerateTopic(title, onProgress) {
  onProgress?.('正在分析知识点...')
  await sleep(600)
  onProgress?.('生成知识点内容...')
  await sleep(1000)
  onProgress?.('分析标签关联...')
  await sleep(400)
  onProgress?.('完成！')
  return {
    content: TOPIC_TEMPLATE.replace(/\{\{title\}\}/g, title),
    tags: []
  }
}

export async function aiGenerateQuiz(title, content, onProgress) {
  onProgress?.('正在分析知识点内容...')
  await sleep(500)
  onProgress?.('生成自测题目...')
  await sleep(800)

  const lines = (content || '').split('\n').filter((line) => line.trim())
  const keywords = []
  lines.forEach((line) => {
    const matches = line.match(/\*\*(.+?)\*\*/g)
    if (!matches) return
    matches.forEach((match) => {
      const keyword = match.replace(/\*\*/g, '').trim()
      if (keyword.length >= 2 && keyword.length <= 15 && !keywords.includes(keyword)) {
        keywords.push(keyword)
      }
    })
  })

  const questions = keywords.slice(0, 4).map((keyword) => ({
    type: 'single',
    question: `在「${title}」中，「${keyword}」的含义是什么？`,
    options: ['见上文内容中的定义', '与该知识点无关', '是一个错误概念', '尚未定义'],
    answer: 0
  }))

  if (questions.length === 0) {
    return [
      {
        type: 'single',
        question: `关于「${title}」，以下哪个说法最准确？`,
        options: ['需要理解原理并实践', '只需了解概念', '工作中很少用到', '已被新技术替代'],
        answer: 0
      }
    ]
  }

  onProgress?.('完成！')
  return questions
}
