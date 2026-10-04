import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getTopicList, getTopicById, getTagList, getCategoryList,
  createTopic as apiCreateTopic, updateTopic as apiUpdateTopic, deleteTopic as apiDeleteTopic,
  createCategory as apiCreateCategory, deleteCategory as apiDeleteCategory
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

export async function createTopic({ title, content, tags, quizQuestions, parentTopicId, categoryId }) {
  const beforeIds = new Set(getTopics().map((item) => item.id))
  await apiCreateTopic({
    title: title || '',
    content: content || '',
    parentTopicId: parentTopicId || null,
    categoryId: categoryId || null,
    tags: (tags || []).map(encodeTag).filter(Boolean),
    quizQuestions: (quizQuestions || []).map((q) => typeof q === 'string' ? q : JSON.stringify(q))
  })
  await ensureTopicsLoaded(true)
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
  await ensureTopicsLoaded(true)
  return getTopic(id)
}

export async function deleteTopic(id) {
  await apiDeleteTopic(id)
  await ensureTopicsLoaded(true)
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
 * 该目录下还有没有下级。侧边栏据此决定给「›」还是给「＋」、级联据此决定给不给箭头，
 * 收在这里一处，别让两个组件各写一遍 length > 0。
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
 * 卡片在 AppLearnView，目录在 AppLayout 侧边栏——两个组件，跨组件拖拽靠这一个上下文。
 * 拖拽数据本身走 dataTransfer（标准做法），这里只共享"正在拖"和"拖到哪个"两个状态，
 * 前者给目录项加可投放提示，后者给命中的目录加高亮。
 */
export const TOPIC_DND_TYPE = 'application/x-kp-topic'
export const dnd = reactive({ live: false, over: null })

/**
 * 落点协议。
 *
 * 全站只有拖拽浮出的目录树是落点：侧边栏和筛选条都不是。
 * 层级只在"放东西"那两秒需要被看见，平时不该有常驻的落点区。
 * 归类和改类是同一个动作——所有卡片都是拖拽源，卡片上不再挂目录入口
 * （目录一多，平铺的下拉列表就没法用了）。
 *
 * dragover 不 preventDefault，浏览器就不会派发 drop——HTML5 拖放的硬约定。
 * 高亮用 dragover 设、drop/dragend 清；不用 dragleave，避免跨节点时的闪烁。
 */
export function dropOver(categoryId, e) {
  e.preventDefault()
  e.dataTransfer.dropEffect = 'move'
  dnd.over = categoryId
}

export async function dropTo(categoryId, e) {
  e.preventDefault()
  dnd.over = null
  const topic = getTopics().find((t) => t.id === Number(e.dataTransfer.getData(TOPIC_DND_TYPE)))
  if (topic) await categorizeTopic(topic, categoryId)
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

export async function deleteCategory(id) {
  await apiDeleteCategory(id)
  // 目录下知识点回到了"未归类"，topic 缓存也得刷
  await ensureCategoriesLoaded(true)
  await ensureTopicsLoaded(true)
}

/**
 * 删除目录的交互流。入口在目录树每一行的「－」。
 * 返回是否真的执行了：调用方据此决定要不要把整条树收走（删的可能正是它锚定的那个节点）。
 *
 * 这里没有改名：卡片上的目录 chip 和详情页的「目录」行都撤了之后，
 * 前端已经没有重命名入口，`promptRenameCategory` / `renameCategory` 一起删掉，
 * 不留零调用的死代码。后端 `PUT /kp/category/rename` 还在，要接回来从这里加。
 */
export async function confirmDeleteCategory(c) {
  try {
    await ElMessageBox.confirm(
      `删除目录「${c.name}」后，里面的知识点会回到"未归类"（知识点不会被删），`
      + '它的下级目录会挂到它的上级，不会跟着一起删。确定删除吗？',
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
