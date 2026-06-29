import { ref, watch } from 'vue'

const STORAGE_KEY_TREES = 'knowledge_trees'
const STORAGE_KEY_NODES = 'knowledge_nodes'

// --------------- helpers ---------------

function genId() {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 8)
}

function load(key) {
  try {
    return JSON.parse(localStorage.getItem(key) || '[]')
  } catch {
    return []
  }
}

function save(key, data) {
  localStorage.setItem(key, JSON.stringify(data))
}

function now() {
  return new Date().toISOString().slice(0, 19).replace('T', ' ')
}



// --------------- store ---------------

const treeMap = ref(new Map())
const nodeMap = ref(new Map())

function rebuildMaps() {
  const tm = new Map()
  load(STORAGE_KEY_TREES).forEach((t) => tm.set(t.id, t))
  treeMap.value = tm

  const nm = new Map()
  load(STORAGE_KEY_NODES).forEach((n) => nm.set(n.id, n))
  nodeMap.value = nm
}

rebuildMaps()

// persist on any change
watch([treeMap, nodeMap], () => {
  save(STORAGE_KEY_TREES, [...treeMap.value.values()])
  save(STORAGE_KEY_NODES, [...nodeMap.value.values()])
}, { deep: true })

// --------------- tree API ---------------

export function getTrees() {
  return [...treeMap.value.values()].sort((a, b) => a.sort - b.sort)
}

export function getTree(id) {
  return treeMap.value.get(id) || null
}

export function createTree({ name, description, icon, category }) {
  const tree = {
    id: genId(),
    name,
    description: description || '',
    icon: icon || '📚',
    category: category || '',
    sort: treeMap.value.size,
    createdAt: now(),
    updatedAt: now()
  }
  treeMap.value = new Map(treeMap.value).set(tree.id, tree)
  return tree
}

export function updateTree(id, patch) {
  const old = treeMap.value.get(id)
  if (!old) return
  const updated = { ...old, ...patch, updatedAt: now() }
  treeMap.value = new Map(treeMap.value).set(id, updated)
}

export function deleteTree(id) {
  const m1 = new Map(treeMap.value)
  m1.delete(id)
  treeMap.value = m1

  const m2 = new Map(nodeMap.value)
  for (const [nid, n] of m2) {
    if (n.treeId === id) m2.delete(nid)
  }
  nodeMap.value = m2
}

// --------------- node API ---------------

export function getNodes(treeId) {
  return [...nodeMap.value.values()]
    .filter((n) => n.treeId === treeId)
    .sort((a, b) => a.sort - b.sort)
}

function recalcSort(treeId) {
  const sorted = getNodes(treeId)
  const cols = []
  const childrenOf = {}
  sorted.forEach((n) => {
    const pid = n.parentId || '__root__'
    if (!childrenOf[pid]) childrenOf[pid] = []
    childrenOf[pid].push(n)
  })

  function walk(pid, depth) {
    const list = childrenOf[pid] || []
    list.forEach((n) => {
      cols.push({ ...n, sort: cols.length })
      walk(n.id, depth + 1)
    })
  }
  walk('__root__', 0)

  if (cols.length === sorted.length) {
    const m = new Map(nodeMap.value)
    cols.forEach((n) => m.set(n.id, n))
    nodeMap.value = m
  }
}

export function createNode({ treeId, parentId, title, content, nodeType, topicId }) {
  const newNode = {
    id: genId(),
    treeId,
    parentId: parentId || null,
    title: title || '新知识点',
    content: content || '',
    sort: getNodes(treeId).length,
    nodeType: nodeType || 'topic',
    relatedNodes: [],
    topicId: topicId || null,
    createdAt: now(),
    updatedAt: now()
  }
  nodeMap.value = new Map(nodeMap.value).set(newNode.id, newNode)
  return newNode
}

export function getNode(id) {
  return nodeMap.value.get(id) || null
}

export function updateNode(id, patch) {
  const old = nodeMap.value.get(id)
  if (!old) return
  const updated = { ...old, ...patch, updatedAt: now() }
  nodeMap.value = new Map(nodeMap.value).set(id, updated)
}

export function deleteNode(id) {
  const m = new Map(nodeMap.value)
  // also delete descendants
  const toDelete = new Set([id])
  const allNodes = [...m.values()]
  // BFS find all children
  let found = true
  while (found) {
    found = false
    for (const n of allNodes) {
      if (!toDelete.has(n.id) && n.parentId && toDelete.has(n.parentId)) {
        toDelete.add(n.id)
        found = true
      }
    }
  }
  for (const nid of toDelete) m.delete(nid)
  // also remove references in relatedNodes
  for (const [nid, n] of m) {
    const filtered = (n.relatedNodes || []).filter((r) => !toDelete.has(r.id))
    if (filtered.length !== (n.relatedNodes || []).length) {
      m.set(nid, { ...n, relatedNodes: filtered })
    }
  }
  nodeMap.value = m

  // update tree updatedAt
  const tree = treeMap.value.get(getTree(id)?.id)
  if (tree) updateTree(tree.id, {})
}

export function addRelatedNode(nodeId, targetNodeId, relationType) {
  const node = nodeMap.value.get(nodeId)
  const target = nodeMap.value.get(targetNodeId)
  if (!node || !target) return
  const related = node.relatedNodes || []
  if (related.some((r) => r.id === targetNodeId)) return
  updateNode(nodeId, {
    relatedNodes: [...related, { id: targetNodeId, title: target.title, relationType: relationType || 'related' }]
  })
}

export function removeRelatedNode(nodeId, targetNodeId) {
  const node = nodeMap.value.get(nodeId)
  if (!node) return
  updateNode(nodeId, {
    relatedNodes: (node.relatedNodes || []).filter((r) => r.id !== targetNodeId)
  })
}

export function mergeNodes(sourceId, targetId) {
  const src = nodeMap.value.get(sourceId)
  const tgt = nodeMap.value.get(targetId)
  if (!src || !tgt || src.treeId !== tgt.treeId) return

  // move source's children under target
  const m = new Map(nodeMap.value)
  for (const [nid, n] of m) {
    if (n.parentId === sourceId) {
      m.set(nid, { ...n, parentId: targetId })
    }
  }
  // combine related nodes
  const mergedRelated = [...(tgt.relatedNodes || [])]
  for (const r of src.relatedNodes || []) {
    if (!mergedRelated.some((mr) => mr.id === r.id) && r.id !== targetId) {
      mergedRelated.push(r)
    }
  }
  m.set(targetId, { ...tgt, relatedNodes: mergedRelated, content: tgt.content + '\n\n--- 合并自「' + src.title + '」---\n\n' + src.content, updatedAt: now() })
  // repoint any relatedNodes that point to source -> target
  for (const [nid, n] of m) {
    const updated = (n.relatedNodes || []).map((r) =>
      r.id === sourceId ? { ...r, id: targetId, title: tgt.title } : r
    )
    if (JSON.stringify(updated) !== JSON.stringify(n.relatedNodes)) {
      m.set(nid, { ...n, relatedNodes: updated })
    }
  }
  m.delete(sourceId)
  nodeMap.value = m

  // Now delegate sorting to a clean rebuild
  recalcSort(tgt.treeId)
  updateTree(tgt.treeId, {})
}

// --------------- tree nodes helper ---------------

export function buildTreeData(treeId) {
  const nodes = getNodes(treeId)
  const nodeMapLocal = {}
  nodes.forEach((n) => { nodeMapLocal[n.id] = { ...n, children: [] } })

  const roots = []
  nodes.forEach((n) => {
    const entry = nodeMapLocal[n.id]
    if (n.parentId && nodeMapLocal[n.parentId]) {
      nodeMapLocal[n.parentId].children.push(entry)
    } else {
      roots.push(entry)
    }
  })
  return roots
}

// seed demo data if empty
// ❌ 已移除 seedDemoIfEmpty — 违反极简原则，自动插入预设树
// ❌ 已移除 AI_PRESETS / matchPreset / aiGenerateTree — 违反极简原则，预设驱动

// --------------- AI 对话存储 ---------------

const chatMessageMap = ref(new Map())

export function getChatMessages(nodeId) {
  return chatMessageMap.value.get(nodeId) || []
}

export function addChatMessage(nodeId, msg) {
  const list = [...(chatMessageMap.value.get(nodeId) || []), { ...msg, id: genId(), timestamp: now() }]
  chatMessageMap.value = new Map(chatMessageMap.value).set(nodeId, list)
}

export function clearChatMessages(nodeId) {
  const m = new Map(chatMessageMap.value)
  m.delete(nodeId)
  chatMessageMap.value = m
}

// --------------- AI 生成 ---------------

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms))
}

// ❌ 已移除 AI_PRESETS / matchPreset / aiGenerateTree — 违反极简原则，预设驱动
// ❌ 已移除 seedDemoIfEmpty — 违反极简原则，自动插入预设树

export const NODE_TYPES = [
  { value: 'section', label: '章节', icon: '📁' },
  { value: 'topic', label: '主题', icon: '📘' },
  { value: 'concept', label: '概念', icon: '💡' },
  { value: 'fact', label: '事实', icon: '📌' },
  { value: 'note', label: '笔记', icon: '📝' }
]

export const RELATION_TYPES = [
  { value: 'prerequisite', label: '前置知识' },
  { value: 'extends', label: '扩展' },
  { value: 'contrast', label: '对比' },
  { value: 'related', label: '相关' }
]

// --------------- Topic（学习主题）---------------

const STORAGE_KEY_TOPICS = 'knowledge_topics'

const topicMap = ref(new Map())

function rebuildTopicMap() {
  const tm = new Map()
  load(STORAGE_KEY_TOPICS).forEach((t) => tm.set(t.id, t))
  topicMap.value = tm
}
rebuildTopicMap()

watch(topicMap, () => {
  save(STORAGE_KEY_TOPICS, [...topicMap.value.values()])
}, { deep: true })

export function getTopics() {
  return [...topicMap.value.values()].sort((a, b) => (b.createdAt || '').localeCompare(a.createdAt || ''))
}

export function getTopic(id) {
  return topicMap.value.get(id) || null
}

export function createTopic({ title, content, tags, quizQuestions, parentTopicId }) {
  const topic = {
    id: genId(),
    title: title || '',
    content: content || '',
    tags: tags || [],
    quizQuestions: quizQuestions || [],
    relatedTopicIds: [],
    parentTopicId: parentTopicId || null,
    createdAt: now(),
    updatedAt: now()
  }
  topicMap.value = new Map(topicMap.value).set(topic.id, topic)
  return topic
}

export function updateTopic(id, patch) {
  const old = topicMap.value.get(id)
  if (!old) return
  const updated = { ...old, ...patch, updatedAt: now() }
  topicMap.value = new Map(topicMap.value).set(id, updated)
}

export function deleteTopic(id) {
  const m = new Map(topicMap.value)
  m.delete(id)
  // 清理其他 topic 中对它的引用
  for (const [tid, t] of m) {
    const filtered = (t.relatedTopicIds || []).filter((rid) => rid !== id)
    if (filtered.length !== (t.relatedTopicIds || []).length) {
      m.set(tid, { ...t, relatedTopicIds: filtered })
    }
  }
  topicMap.value = m
}

// --------------- Topic ↔ 知识树联动 ---------------

/**
 * 将 Topic 按标签类别+标签名二级聚合到知识树。
 * 结构：类别(树) → 标签名(section 目录节点) → Topic(topic 节点)
 */
export function syncTopicToTrees(topic) {
  // 数据量小，直接全量重建保证一致性
  resyncAllTopics()
}

// 辅助：按名称获取或创建树
function getOrCreateTree(name) {
  let tree = getTrees().find((tr) => tr.name === name)
  if (!tree) tree = createTree({ name, description: '', icon: '📂', category: '' })
  return tree.id
}

/**
 * 删除 Topic 时同步清理所有关联的知识树节点。
 */
export function removeTopicFromTrees(topicId) {
  for (const tree of getTrees()) {
    const node = getNodes(tree.id).find((n) => n.topicId === topicId)
    if (node) deleteNode(node.id)
    // 清理空的 section 目录节点
    for (const n of getNodes(tree.id)) {
      if (n.nodeType === 'section' && !getNodes(tree.id).some((c) => c.parentId === n.id)) {
        deleteNode(n.id)
      }
    }
  }
}

/**
 * 全量重新同步所有 Topic 到知识树（一次性重建）。
 * 规则：
 *   - 没有标签的 topic 不显示
 *   - 通过共享 tag.name 连通的 topic 归为一棵树
 *   - 不共享任何 tag.name 的 topic 独立成一棵树
 *   - tag.name 被 >=2 个 topic 共享 → section（文件夹）
 *   - tag.name 只被 1 个 topic 使用 → topic 直接挂根
 */
export function resyncAllTopics() {
  treeMap.value = new Map()
  nodeMap.value = new Map()

  const topics = [...topicMap.value.values()].filter((t) => (t.tags || []).length > 0)
  if (topics.length === 0) return

  // 1. tag.name → Set<topicId>
  const nameToTopics = {}
  for (const t of topics) {
    for (const tag of (t.tags || [])) {
      if (!nameToTopics[tag.name]) nameToTopics[tag.name] = new Set()
      nameToTopics[tag.name].add(t.id)
    }
  }

  // 2. 并查集：共享 tag.name 的 topic 连通
  const parent = {}
  for (const t of topics) parent[t.id] = t.id
  function find(x) {
    while (parent[x] !== x) { parent[x] = parent[parent[x]]; x = parent[x] }
    return x
  }
  function union(a, b) {
    const ra = find(a), rb = find(b)
    if (ra !== rb) parent[ra] = rb
  }
  for (const ids of Object.values(nameToTopics)) {
    const arr = [...ids]
    for (let i = 1; i < arr.length; i++) union(arr[0], arr[i])
  }

  // 3. 分组
  const groups = {}
  for (const t of topics) {
    const root = find(t.id)
    if (!groups[root]) groups[root] = []
    groups[root].push(t)
  }

  // 4. 每组一棵树
  for (const group of Object.values(groups)) {
    // 统计组内 tag.name 频次
    const localNameCount = {}
    for (const t of group) {
      for (const tag of (t.tags || [])) {
        localNameCount[tag.name] = (localNameCount[tag.name] || 0) + 1
      }
    }

    // 树名 = 组内使用次数最多的 tag.name（平局取字母序最小）
    const treeName = Object.keys(localNameCount)
      .sort((a, b) => localNameCount[b] - localNameCount[a] || a.localeCompare(b))[0]
    const treeId = getOrCreateTree(treeName)

    for (const topic of group) {
      const tagNames = (topic.tags || []).map((t) => t.name).filter((n) => n !== treeName)
      let placed = false

      for (const tagName of tagNames) {
        if ((localNameCount[tagName] || 0) >= 2) {
          let section = getNodes(treeId).find((n) => n.nodeType === 'section' && n.title === tagName && !n.topicId)
          if (!section) {
            section = createNode({ treeId, parentId: null, title: tagName, content: '', nodeType: 'section', topicId: null })
          }
          createNode({
            treeId, parentId: section.id, title: topic.title,
            content: topic.content, nodeType: 'topic', topicId: topic.id
          })
          placed = true
          break
        }
      }

      if (!placed) {
        createNode({
          treeId, parentId: null, title: topic.title,
          content: topic.content, nodeType: 'topic', topicId: topic.id
        })
      }
    }
  }
}

// 汇总所有已使用的标签（去重，按使用次数排序）
export function getAllTags() {
  const tagCount = {}
  for (const t of topicMap.value.values()) {
    for (const tag of (t.tags || [])) {
      const key = tag.category + '::' + tag.name
      if (!tagCount[key]) tagCount[key] = { ...tag, count: 0 }
      tagCount[key].count++
    }
  }
  return Object.values(tagCount).sort((a, b) => b.count - a.count)
}

/**
 * 解析标签输入，不自动补全类别。
 * "计算机网络::HTTP" → {category:"计算机网络", name:"HTTP"}
 * "HTTP" → {category:"HTTP", name:"HTTP"}（裸标签）
 */
export function resolveTags(rawTags) {
  return rawTags.map((s) => {
    const parts = s.split('::')
    if (parts.length >= 2 && parts[0] && parts[1]) {
      return { category: parts[0], name: parts[1] }
    }
    const name = parts[0] || s
    return { category: name, name }
  })
}

// 为 topic 建立与已有 topic 的关联（标签重叠 + 内容相似度）
export function refreshTopicRelations(topicId) {
  const topic = topicMap.value.get(topicId)
  if (!topic) return

  const allTopics = [...topicMap.value.values()].filter((t) => t.id !== topicId)
  const topicTagKeys = (topic.tags || []).map((t) => t.category + '::' + t.name)

  const related = []
  for (const other of allTopics) {
    const otherTagKeys = (other.tags || []).map((t) => t.category + '::' + t.name)
    const sharedTags = topicTagKeys.filter((k) => otherTagKeys.includes(k))
    // 有共享标签即建立关联
    if (sharedTags.length > 0) {
      related.push(other.id)
    }
  }

  if (related.length > 0) {
    updateTopic(topicId, { relatedTopicIds: related })
    // 双向：为关联的 topic 也加上当前 topic
    for (const rid of related) {
      const other = topicMap.value.get(rid)
      if (other && !(other.relatedTopicIds || []).includes(topicId)) {
        updateTopic(rid, { relatedTopicIds: [...(other.relatedTopicIds || []), topicId] })
      }
    }
  }
}


/**
 * 简单 AI 分析：根据标题相似度查找可能的父级主题。
 * @returns {{ id, title, reason } | null}
 */
export function findParentCandidate(title, existingTopics) {
  const tLower = title.toLowerCase()
  for (const t of existingTopics) {
    const eLower = t.title.toLowerCase()
    // 子串包含关系：HTTPS 包含 HTTP → HTTP 是父级
    if (tLower !== eLower && tLower.includes(eLower) && eLower.length >= 2) {
      return { id: t.id, title: t.title, reason: `「${title}」的名称包含「${t.title}」，可能是其子主题` }
    }
    if (eLower.includes(tLower) && tLower.length >= 2) {
      return { id: t.id, title: t.title, reason: `「${t.title}」的名称包含「${title}」，可能是其子主题` }
    }
  }
  return null
}

// --------------- AI 主题生成 ---------------

// 通用知识点模板：任何主题都使用同一套结构，不做预设/命中匹配
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

/**
 * AI 生成知识点内容（异步，模拟 AI 思考过程）。
 * 只生成内容和标签，不生成题目。
 * @param {string} title - 用户输入的主题
 * @param {(step: string) => void} onProgress - 进度回调
 * @returns {Promise<{content: string, tags: {name:string, category:string}[]}>}
 */
export async function aiGenerateTopic(title, onProgress) {
  onProgress?.('正在分析主题...')
  await sleep(600)

  onProgress?.('生成知识点内容...')
  await sleep(1000)

  const content = TOPIC_TEMPLATE.replace(/\{\{title\}\}/g, title)

  onProgress?.('分析标签关联...')
  await sleep(400)

  onProgress?.('完成！')
  return { content, tags: [] }
}

/**
 * AI 根据知识点标题和内容生成自测题（异步，模拟 AI 思考过程）。
 * @param {string} title - 知识点标题
 * @param {string} content - 知识点内容
 * @param {(step: string) => void} onProgress - 进度回调
 * @returns {Promise<{type:string, question:string, options:string[], answer:number}[]>}
 */
export async function aiGenerateQuiz(title, content, onProgress) {
  onProgress?.('正在分析知识点内容...')
  await sleep(500)

  onProgress?.('生成自测题目...')
  await sleep(800)

  // 基于内容提取关键词生成题目
  const lines = (content || '').split('\n').filter((l) => l.trim())
  const keywords = []
  for (const line of lines) {
    const matches = line.match(/\*\*(.+?)\*\*/g)
    if (matches) {
      for (const m of matches) {
        const kw = m.replace(/\*\*/g, '').trim()
        if (kw.length >= 2 && kw.length <= 15 && !keywords.includes(kw)) {
          keywords.push(kw)
        }
      }
    }
  }
  const picked = keywords.slice(0, 4)
  let questions = picked.map((kw) => ({
    type: 'single',
    question: `在「${title}」中，「${kw}」的含义是什么？`,
    options: ['见上文内容中的定义', '与主题无关', '是一个错误概念', '尚未定义'],
    answer: 0
  }))
  if (questions.length === 0) {
    questions = [
      { type: 'single', question: `关于「${title}」，以下哪个说法最准确？`, options: ['需要理解原理并实践', '只需了解概念', '工作中很少用到', '已被新技术替代'], answer: 0 },
      { type: 'single', question: `学习「${title}」对实际开发有帮助吗？`, options: ['非常有帮助', '没什么帮助', '只对特定岗位有用', '不需要学'], answer: 0 }
    ]
  }

  onProgress?.('完成！')
  return questions
}
