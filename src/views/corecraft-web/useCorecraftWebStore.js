import { ref } from 'vue'
import {
  getTopicList, getTopicById, getTagList,
  createTopic as apiCreateTopic, updateTopic as apiUpdateTopic, deleteTopic as apiDeleteTopic
} from '../../api/knowledge'

// ==================== 内部状态 ====================

const topicMap = ref(new Map())
let topicsLoaded = false

// 标签缓存：{ name, count }。原本从 topicMap 聚合，要先 ensureTopicsLoaded 拉全部 topic。
// 现在改成直接调 /kp/tag/list 拉全部 tag（含孤儿），不依赖全部 topic。
// 详情页打开不再调 /kp/topic/list，"选择已有标签" popover 懒加载触发请求。
const allTags = ref([])
let allTagsLoaded = false

// ==================== 通用 helpers ====================

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
    treeId: item.treeId ?? null,
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

export async function createTopic({ title, content, tags, quizQuestions, parentTopicId, treeId }) {
  const beforeIds = new Set(getTopics().map((item) => item.id))
  await apiCreateTopic({
    title: title || '',
    content: content || '',
    parentTopicId: parentTopicId || null,
    treeId: treeId || null,
    tags: (tags || []).map(encodeTag).filter(Boolean),
    quizQuestions: (quizQuestions || []).map((q) => typeof q === 'string' ? q : JSON.stringify(q))
  })
  await ensureTopicsLoaded(true)
  return getTopics().find((item) => !beforeIds.has(item.id)) || null
}

export async function updateTopic(id, patch) {
  await apiUpdateTopic({
    id,
    title: patch.title,
    content: patch.content,
    parentTopicId: patch.parentTopicId,
    treeId: patch.treeId,
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
  // /kp/tag/list 返回 { id, name, refCount }，store 统一暴露 { name, count }
  allTags.value = (res?.data || []).map((t) => ({ name: t.name, count: t.refCount }))
  allTagsLoaded = true
  return allTags.value
}

export function resolveTags(rawTags) {
  return (rawTags || []).map(decodeTag).filter((tag) => tag.name)
}

// ==================== 派生：按 tag 图构建知识森林 ====================

/**
 * 按 topic.tags 构知识森林（嵌套树），前端实时计算，不落库。
 *
 * 选根 + 嵌套规则（与"按树视图"产品语义对齐）：
 * 1. 统计每个 tag 的频次（被多少 topic 持有），按频次从高到低排序
 * 2. 贪心选根：频次最高的 tag 作主根
 * 3. 嵌套子 tag 候选：频次 ≥ 2 且该 tag 的 topic 集合 ⊂ 主根的 topic 集合
 *    —— 频次 1 的 tag 是"内容描述"（如 https/http/dns），不进树结构，只在 topic 卡片里展示
 * 4. DFS 展开：
 *    - 纯 topic（持的 other 都不在嵌套子 tag 集合内）→ 当前 tag 下直接展示
 *    - impure topic（持当前 tag + 一个嵌套子 tag X）→ 推到 X 子树展示
 * 5. 多棵树 = 主根的 topic 集合无法包含某些 tag（这些 tag 各自作根）
 *
 * 节点结构：
 *   { kind: 'tag', tag, children: [Node] }      tag 节点（可嵌套子 tag），用 el-collapse-item 渲染
 *   { kind: 'topic', topic, hideTagKey }        topic 节点，用 TopicCard 渲染；
 *                                                hideTagKey 让卡片不重复展示自己所在 tag 节点对应的标签
 *
 * 为什么不依赖后端 treeId：topic 属于多棵树（k1 同时持有 test/jsjwl）时 treeId 单字段表达不下，
 * 改 schema 收益小、迁移成本高。前端 topic 数量级 O(N) 实时算完全够。
 */
export function getKnowledgeForest() {
  const topics = [...topicMap.value.values()]
  if (topics.length === 0) return []

  // 1. 邻接表 + 频次
  const tagToTopics = new Map()
  const topicToTags = new Map()
  const tagByKey = new Map()
  const tagFreq = new Map()
  const topicById = new Map()

  for (const t of topics) {
    topicById.set(t.id, t)
    const tagKeys = []
    for (const tag of t.tags || []) {
      const key = encodeTag(tag)
      tagKeys.push(key)
      if (!tagByKey.has(key)) tagByKey.set(key, tag)
      if (!tagToTopics.has(key)) tagToTopics.set(key, new Set())
      tagToTopics.get(key).add(t.id)
    }
    topicToTags.set(t.id, new Set(tagKeys))
    for (const k of tagKeys) {
      tagFreq.set(k, (tagFreq.get(k) || 0) + 1)
    }
  }

  // 2. tag 按频次从高到低排序
  const sortedTagKeys = [...tagByKey.keys()].sort((a, b) => {
    const diff = (tagFreq.get(b) || 0) - (tagFreq.get(a) || 0)
    return diff !== 0 ? diff : a.localeCompare(b)
  })

  // 3. 贪心选根 + 嵌套判定
  // tagToRoot[k] = k 的父级 tag（嵌套时指向父 tag，独立时指向自己）；不进入树结构的 tag 不在 map 里
  const tagToRoot = new Map()
  const rootTagKeys = []

  for (const key of sortedTagKeys) {
    if (rootTagKeys.length === 0) {
      rootTagKeys.push(key)
      tagToRoot.set(key, key)
      continue
    }
    const topicSet = tagToTopics.get(key)
    let parent = null
    for (const rk of rootTagKeys) {
      if (isSubset(topicSet, tagToTopics.get(rk))) {
        parent = rk
        break
      }
    }
    if (parent && (tagFreq.get(key) || 0) >= 2) {
      // 频次 ≥ 2 且被主根包含 → 嵌套
      tagToRoot.set(key, parent)
    } else if (!parent) {
      // 不被任何主根包含 → 独立成根
      rootTagKeys.push(key)
      tagToRoot.set(key, key)
    }
    // 频次 1 + 被包含 → 忽略（不在 tagToRoot 里）
  }

  // 4. 对每个根 DFS 展开
  const ctx = { tagToTopics, topicToTags, tagByKey, topicById, tagToRoot }
  return rootTagKeys.map((rootKey) => buildTagNode(rootKey, ctx))
}

/** 集合 A 是否是 B 的子集（A ⊆ B） */
function isSubset(setA, setB) {
  if (setA.size > setB.size) return false
  for (const x of setA) {
    if (!setB.has(x)) return false
  }
  return true
}

/**
 * 构建一个 tag 节点：{ kind: 'tag', tag, children: [...] }
 * 直接子 tag 通过 tagToRoot 找（指向自己的 tag）；递归处理。
 */
function buildTagNode(tagKey, ctx) {
  const { tagToTopics, topicToTags, tagByKey, topicById, tagToRoot } = ctx
  const adjacentTopicIds = tagToTopics.get(tagKey) || new Set()
  // 直接子 tag：tagToRoot[k] === tagKey 且 k !== tagKey
  const childTagKeys = [...tagByKey.keys()].filter(
    (k) => tagToRoot.get(k) === tagKey && k !== tagKey
  )
  const childTagSet = new Set(childTagKeys)

  const directTopics = []
  for (const tid of adjacentTopicIds) {
    const topicTagKeys = topicToTags.get(tid) || new Set()
    // 找这个 topic 持有的"嵌套子 tag"（childTagSet 内的）
    const subTag = [...topicTagKeys].find((k) => childTagSet.has(k))
    if (subTag) {
      // impure topic 持嵌套子 tag → 推到子 tag 子树（在 buildTagNode(subTag) 时处理）
    } else {
      // 纯 topic 或 持的 other 都不是嵌套子 tag → 当前 tag 下直接展示
      directTopics.push({
        kind: 'topic',
        topic: topicById.get(tid),
        hideTagKey: tagKey
      })
    }
  }

  const subChildren = childTagKeys.map((subKey) => buildTagNode(subKey, ctx))

  return {
    kind: 'tag',
    tag: tagByKey.get(tagKey),
    children: [...directTopics, ...subChildren]
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
