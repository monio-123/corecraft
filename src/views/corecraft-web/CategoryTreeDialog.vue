<template>
  <el-dialog
    :model-value="modelValue"
    :title="mode === 'points' ? '生成知识点' : '生成知识包'"
    width="640px"
    :close-on-click-modal="false"
    @update:model-value="close"
  >
    <!-- pack 模式四步：主题 → 骨架 → 知识点 → 完成。
         points 模式没有骨架（目录是现成的），直接进"知识点"那一步。 -->
    <el-steps v-if="mode === 'pack'" :active="step" simple class="steps">
      <el-step title="主题" />
      <el-step title="骨架" />
      <el-step title="知识点" />
      <el-step title="完成" />
    </el-steps>

    <!-- 第 1 步：来源 -->
    <section v-if="at.theme" class="pane">
      <div class="tabs">
        <button class="tabs__item" :class="{ 'is-on': source === 'ai' }" @click="source = 'ai'">AI 生成</button>
        <button class="tabs__item" :class="{ 'is-on': source === 'paste' }" @click="source = 'paste'">粘贴</button>
      </div>

      <template v-if="source === 'ai'">
        <el-input v-model="topic" placeholder="主题，如：操作系统" @keyup.enter="generateOutline" />
        <div class="acts">
          <el-button type="primary" size="small" :loading="generating" :disabled="!topic.trim()" @click="generateOutline">
            生成骨架
          </el-button>
        </div>
      </template>

      <template v-else>
        <el-input
          v-model="text"
          type="textarea"
          :rows="8"
          placeholder="操作系统
├── 进程与线程
│   ├── 进程与线程的区别
│   └── 线程的调度策略
└── 内存管理
    └── 虚拟内存的工作原理"
        />
        <div class="acts">
          <el-button type="primary" size="small" :disabled="!text.trim()" @click="parseOutline">解析</el-button>
        </div>
      </template>
    </section>

    <!-- 第 2 步：骨架。**目录 = 有下级的节点，知识点 = 叶子**，两件事都在这棵树里 -->
    <section v-else-if="at.skeleton" class="pane">
      <div class="sum">
        <span>将建 {{ plan.dirCount }} 个目录</span>
        <span>· 生成 {{ plan.pointCount }} 个知识点</span>
      </div>
      <p v-if="plan.tooLong.length" class="warn">{{ plan.tooLong.length }} 个目录名超过 {{ MAX_NAME }} 字</p>
      <p v-if="plan.tooDeep.length" class="warn">{{ plan.tooDeep.length }} 个目录超过 {{ MAX_DEPTH }} 层</p>
      <p v-if="plan.skippedDeep.length" class="pane__hint">{{ plan.skippedDeep.length }} 个目录超过 {{ MAX_DEPTH }} 层，已跳过</p>
      <p v-if="plan.tooMany" class="warn">一次最多创建 {{ MAX_NODES }} 个目录，这批有 {{ plan.tooMany }} 个</p>
      <p v-if="plan.orphan.length" class="pane__hint">{{ plan.orphan.length }} 个知识点没有目录可挂</p>

      <div class="tools">
        <el-button size="small" text @click="checkAll">全选</el-button>
        <el-button size="small" text @click="checkNone">全不选</el-button>
      </div>

      <div class="tree">
        <div v-for="r in rows" :key="r.key" class="row" :style="{ paddingLeft: (r.depth - 1) * 16 + 'px' }">
          <button
            class="row__fold"
            :class="{ 'is-empty': !r.node.children.length }"
            :aria-label="collapsed.has(r.node) ? '展开' : '收起'"
            @click="fold(r.node)"
          >
            <!-- ⚠️ **class 必须绑在 el-icon 上**，不能绑在里面那个 ArrowRight 上：
                 ArrowRight 自己渲染一个 <svg>，class 落在它身上 ⇒ 选择器
                 `.el-icon.is-open`（要求同一个元素同时有两个 class）永远匹配不上
                 ⇒ 箭头不转，展开/收起看起来"没反应"。 -->
            <el-icon v-if="r.node.children.length" :class="{ 'is-open': !collapsed.has(r.node) }">
              <ArrowRight />
            </el-icon>
          </button>
          <el-checkbox :model-value="r.node.checked" @change="(v) => toggle(r.node, v)" />
          <!-- 名字可以直接改：AI 起的名不一定好用，改完再落库比导进去再一个个改名少一半事。 -->
          <el-input v-model="r.node.name" size="small" class="row__input" :class="{ 'is-bad': isBadName(r.node) }" />
          <span v-if="r.node.exists" class="tag tag--exists">合并</span>
          <span v-else-if="r.node.leaf" class="tag tag--point">知识点</span>
        </div>
      </div>
    </section>

    <!-- 第 3 步：生成知识点 -->
    <section v-else-if="at.points" class="pane">
      <!-- 还没开始：先让用户看清"要给哪个目录写"，再点开始（写一堆知识是不可逆的） -->
      <template v-if="!started">
        <p class="done">给「{{ category?.name }}」写知识点</p>
        <div class="acts">
          <el-button type="primary" size="small" @click="startPoints">开始</el-button>
        </div>
      </template>
      <template v-else>
        <p class="prog">
          <!-- points 模式事先不知道要写几条（模型自己定）⇒ total = 0 ⇒ 只报"已写入多少" -->
          <template v-if="progress.total">第 {{ progress.done }} / {{ progress.total }} 条 · </template>已写入 {{ progress.points }} 条
        </p>
        <p v-if="stopped" class="pane__hint">已停止</p>
        <div class="log">
          <div v-for="(r, i) in results" :key="i" class="log__row">
            <span class="log__dir">{{ r.dir }}</span>
            <span class="log__num">{{ r.count }} 个</span>
            <span v-if="r.error" class="log__err">{{ r.error }}</span>
          </div>
        </div>
      </template>
    </section>

    <!-- 第 4 步：完成 -->
    <section v-else class="pane">
      <p class="done">
        已建 {{ result.dirs }} 个目录<template v-if="result.points">，{{ result.points }} 个知识点</template>
      </p>
    </section>

    <template #footer>
      <el-button v-if="at.skeleton" size="small" @click="step = 0">上一步</el-button>
      <el-button v-if="!at.done" size="small" @click="close">取消</el-button>
      <el-button v-else size="small" @click="close">完成</el-button>
      <el-button
        v-if="at.skeleton"
        type="primary"
        size="small"
        :loading="building"
        :disabled="!canBuild"
        @click="build"
      >
        创建并生成
      </el-button>
      <el-button v-if="at.points && started" type="primary" size="small" :loading="running" @click="stop">
        停止
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
/**
 * 生成知识包：**主题 → 目录骨架 → 逐目录写知识点**。
 *
 * ## 为什么是两步（骨架先、知识点后）
 *
 * 一次调用出不了几十篇正文（那是几万 token 的输出，硬要它一次吐，它会重复、
 * 写空、越写越水）。所以：**先出骨架**（目录 + 每个目录的知识点标题），
 * **再对着标题写正文**。标题先定下来，写正文时才不会跑题、不会重复。
 *
 * ## 这棵树同时是两份清单
 *
 * - **目录 = 有下级的节点**（要建出来）
 * - **知识点 = 叶子**（要写正文，挂在它直接的那个目录下）
 *
 * 叶子不再是"被扔掉的条目"——它是要写正文的东西。
 *
 * ## 两种模式
 *
 * - `pack`（侧边栏入口）：主题 → 骨架 → 逐目录生成知识点 → 完成。
 * - `points`（目录「⋯」入口）：目录是现成的，直接给它写知识点。
 *   材料里带上**这个目录现有的子目录**，模型就知道哪些领域已经有人管了。
 *
 * ## 后端几乎不用改
 *
 * 目录走 `POST /kp/category/import`（一个事务建完整棵树，返回这批目录的 id）；
 * 知识点走现有的 `POST /kp/topic`。**"逐目录生成"这个编排在前端**，
 * 因为进度、停止、单目录重试都是界面状态。
 */
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowRight } from '@element-plus/icons-vue'
import { ask } from './useAi'
import {
  importCategories,
  createTopic,
  updateTopic,
  ensureTopicsLoaded,
  ensureTopicTypesLoaded,
  getCategories,
  getChildCategories,
  getTopics,
  topicTypeIdByValue,
  TOPIC_TYPE_LEARN
} from './useCorecraftWebStore'
import {
  parseOutlineTree,
  markOutline,
  importPlan,
  renderOutlineTree,
  allNodes,
  joinPath,
  indexByParentName,
  MAX_DEPTH,
  MAX_NAME,
  MAX_NODES
} from '../../utils/outlineTree'
import { parsePoints, buildMaterial, buildTagMaterial, parseTagNames, fixCategoryLinks, linkTopicMarks, splitShortContent } from '../../utils/knowledgePoints'
import { categoryHref } from '../../utils/categoryNav'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** pack = 生成一整套（主题 → 骨架 → 知识点）；points = 给一个现成目录写知识点 */
  mode: { type: String, default: 'pack' },
  /** points 模式的目标目录 */
  category: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'done'])

const step = ref(0)
const source = ref('ai')
const topic = ref('')
const text = ref('')
const generating = ref(false)
const building = ref(false)
const running = ref(false)
const stopped = ref(false)
/** 知识点那一步有没有开始（points 模式要先点"开始"，别一打开就写一堆知识） */
const started = ref(false)
/** 解析 + 标记之后的树 */
const marked = ref([])
const collapsed = ref(new Set())
const progress = ref({ done: 0, total: 0, points: 0 })
const results = ref([])
const result = ref({ dirs: 0, points: 0 })
let controller = null

/**
 * 当前落在哪一步。
 *
 * <p>points 模式没有"主题"和"骨架"（目录是现成的），它的一步就是"知识点"。
 * 用一份 computed 算，模板不再到处写"这个模式下这一步是哪一步"。
 */
const at = computed(() => {
  const pack = props.mode === 'pack'
  return {
    theme: pack && step.value === 0,
    skeleton: pack && step.value === 1,
    points: step.value === (pack ? 2 : 0),
    done: step.value === (pack ? 3 : 1)
  }
})

/** 可见行（收起的子树不展开） */
const rows = computed(() => {
  const out = []
  const walk = (nodes, depth, path) => {
    ;(nodes || []).forEach((node, i) => {
      const key = `${path}/${i}`
      out.push({ key, node, depth })
      if (!collapsed.value.has(node)) walk(node.children, depth + 1, key)
    })
  }
  walk(marked.value, 1, 'n')
  return out
})

/** **唯一的口径**：要建的目录 + 要写的知识点（见 utils/outlineTree#importPlan） */
const plan = computed(() => importPlan(marked.value))

const canBuild = computed(
  () =>
    plan.value.dirCount > 0 &&
    !plan.value.tooLong.length &&
    !plan.value.tooDeep.length &&
    !plan.value.tooMany
)

watch(
  () => props.modelValue,
  (open) => {
    if (!open) {
      reset()
      return
    }
    // pack 模式第 0 步是"主题"，points 模式第 0 步是"开始"—— 两种都从 0 起
    step.value = 0
  }
)

function reset() {
  step.value = 0
  source.value = 'ai'
  topic.value = ''
  text.value = ''
  marked.value = []
  collapsed.value = new Set()
  progress.value = { done: 0, total: 0, points: 0 }
  results.value = []
  result.value = { dirs: 0, points: 0 }
  stopped.value = false
  started.value = false
  controller?.abort()
  controller = null
}

function close() {
  controller?.abort()
  emit('update:modelValue', false)
}

/** 名字超长（目录名的列长是 50；知识点标题落在 title 列，不受这个限） */
function isBadName(node) {
  return !node.leaf && node.name.length > MAX_NAME
}

/**
 * 勾一个节点 = 这一支都要（目录要建、知识点要写）。
 *
 * <p>叶子是知识点：勾它 = 要写这一条；不勾 = 这条不写。
 */
function toggle(node, checked) {
  node.checked = checked
  for (const child of node.children) toggle(child, checked)
}

/** 全选 / 全不选：所有节点（含知识点那些叶子）—— "全选"不选全等于没全选 */
function checkAll() {
  for (const n of allNodes(marked.value)) n.checked = !n.tooDeep
}

function checkNone() {
  for (const n of allNodes(marked.value)) n.checked = false
}

function fold(node) {
  const next = new Set(collapsed.value)
  if (next.has(node)) next.delete(node)
  else next.add(node)
  collapsed.value = next
}

/** 有下级的节点（折叠箭头只给它） */
function branches(nodes, out = []) {
  for (const n of nodes || []) {
    if (n.children.length) out.push(n)
    branches(n.children, out)
  }
  return out
}

/** 默认只展开第一层：树再大也不铺满一屏 */
function collapseDefault() {
  collapsed.value = new Set(branches(marked.value).filter((n) => n.depth >= 2))
}

// ---------------- 第 1 步：出骨架 ----------------

/** AI 生成骨架：主题 + 现有目录清单（让它避开重复造同义目录） */
async function generateOutline() {
  generating.value = true
  try {
    const cats = getCategories()
    const parts = [`主题：${topic.value.trim()}`]
    const existing = renderOutlineTree(cats)
    if (existing) parts.push(`现有目录清单（缩进表示层级）：\n${existing}`)
    const answerText = await ask('生成目录骨架', {
      material: parts.join('\n\n'),
      questionKey: 'ai.prompt.knowledge.outline'
    })
    applyOutline(answerText)
  } catch (e) {
    ElMessage.error(e?.message || '生成失败')
  } finally {
    generating.value = false
  }
}

/** 粘贴的树 → 骨架。**解析和"没认出"的判断都在 applyOutline 里**，这里不重复一遍 */
function parseOutline() {
  applyOutline(text.value)
}

function applyOutline(raw) {
  const forest = parseOutlineTree(raw)
  if (!forest.length) {
    ElMessage.error('没认出目录树')
    return
  }
  marked.value = markOutline(forest, getCategories(), { id: 0, depth: 0 })
  step.value = 1
  collapseDefault()
}

// ---------------- 第 2 步：建目录 + 写知识点 ----------------

/**
 * 目录路径 → 目录 id。
 *
 * <p>后端返回这批目录的 `{ id, name, parentId }`，从根往下按名字逐层对上，
 * 就知道骨架里每个目录实际是哪一个。**用路径而不是名字定位**：同层同名不可能，
 * 但不同层同名是常事（"集合"和"网络集合"）。
 */
function resolveDirIds(dirs, rows, mountId = 0) {
  const byParent = indexByParentName(rows)
  const map = new Map()
  const walk = (nodes, parentId, path) => {
    for (const n of nodes) {
      const id = (byParent.get(parentId) || new Map()).get(n.name)
      if (id == null) continue
      const p = joinPath(path, n.name)
      map.set(p, id)
      walk(n.children, id, p)
    }
  }
  walk(dirs, mountId, '')
  return map
}

/** 知识点按目录分组（同目录的一次问出来） */
function groupPoints(points) {
  const groups = []
  const byDir = new Map()
  for (const p of points) {
    if (!byDir.has(p.dirPath)) {
      const g = { dirPath: p.dirPath, titles: [] }
      byDir.set(p.dirPath, g)
      groups.push(g)
    }
    byDir.get(p.dirPath).titles.push(p.title)
  }
  return groups
}

/** 建目录 + 逐目录写知识点 */
async function build() {
  building.value = true
  try {
    const rows = await importCategories(plan.value.dirs, 0)
    const ids = resolveDirIds(plan.value.dirs, rows, 0)
    const groups = groupPoints(plan.value.points)
    result.value = { dirs: plan.value.dirCount, points: 0 }
    step.value = 2
    await runGroups(groups, ids)
  } catch (e) {
    ElMessage.error(e?.message || '创建失败')
    step.value = 1
  } finally {
    building.value = false
  }
}

/** points 模式：给一个现成目录写知识点（材料里带它现有的子目录） */
async function startPoints() {
  const cat = props.category
  if (!cat?.id) return
  await runGroups([{ dirPath: cat.name, titles: [] }], new Map(), cat.id, cat)
}

/**
 * 逐目录写知识点。
 *
 * <p>**一次一条**（有标题清单时）：
 * 一次问一个目录的全部知识点 ⇒ 一次调用要吐 N 条正文。
 * 而后端请求**没带 `max_tokens`**（`AiChatServiceImpl.stream` 只发 model/stream/messages）
 * ⇒ 吃上游默认的输出上限（OpenAI 兼容协议默认 4096 tokens）
 * ⇒ 一个目录 4 条 × 1500 字就**撞上限被截断**，界面上表现为「少 N 条」，
 * 而且**看不出是截断**（模型自己也不知道）。
 * ⇒ 拆成**一条一次调用**：每条只吐一条正文，永远撞不到上限；
 * 而且漏一条只丢一条（能单独重来），不用整目录重跑。
 *
 * <p>**没有标题清单时**（points 模式：给一个现成目录补内容）：
 * 事先不知道这个目录该有哪几条 ⇒ 只能**一次调用**，模型自己定 3 到 5 条。
 * 提示词 `ai.prompt.knowledge.point` 里「**没给标题清单**时：先自己定这个目录该有的
 * 3 到 5 个知识点标题，再按同样的格式写正文」就是为这条路写的。
 * ⇒ 两种情况是**同一条规则的两个分支**，不是两套流程：
 * 「一次调用」拿回若干条 → 逐条落库；「一次一条」拿回一条 → 落库。
 *
 * <p>**目录仍然是外层单位**：结果一行一个目录（用户看的是目录），
 * 同目录的兄弟标题作为"别写成同一件事"的提示一起给模型。
 *
 * @param {Array} groups 一批（每个目录一组）
 * @param {Map<string, number>} ids 目录路径 → 目录 id（这批建出来的）
 * @param {number|null} targetId points 模式的目标目录 id
 * @param {object|null} cat points 模式的目标目录
 */
async function runGroups(groups, ids, targetId = null, cat = null) {
  // **知识点类型**：生成出来的是系统整理过的学习内容 ⇒ 落「学习」档。
  //
  // 以前这里**不传 typeId**，于是 `createTopic` 走"字典里排第一的那条" =「笔记」——
  // 一批教材式正文全被标成随手记，而且界面上完全看不出来。
  // 现在显式传；字典里没有这一档就**直接报错停**，不静默落回默认档
  // （落回去等于生成一批类型全错、还查不出是哪错的知识点）。
  await ensureTopicTypesLoaded()
  const typeId = topicTypeIdByValue(TOPIC_TYPE_LEARN)
  if (typeId == null) {
    ElMessage.error('知识点类型里没有「学习」这一档')
    return
  }
  running.value = true
  started.value = true
  stopped.value = false
  results.value = []
  // **进度按「知识点条数」算**（一次一条之后，目录不再是进度单位）。
  // points 模式是「模型自己定写哪几条」⇒ 事先不知道总数 ⇒ total = 0，
  // 界面改成只报「已写入 N 条」（见模板里 progress.total 的判断）。
  const totalPoints = groups.reduce((n, g) => n + g.titles.length, 0)
  progress.value = { done: 0, total: totalPoints, points: 0 }
  controller = new AbortController()
  const written = []
  /** 这一批建出来的知识点（目录 + 标题）—— 整批结束后靠它回填正文里的知识点链接 */
  const created = []
  // **目录链接**：正文里指向目录的链接，地址由材料给模型照抄。
  // 认不出来的（模型把 id 写错了）一律退成普通文字 —— 宁可少一个链接，
  // 不要一个点进去是空列表的链接。
  const validHrefs = [...ids.values()].map((id) => categoryHref(id))
  const linkOf = (path) => {
    const id = ids.get(path)
    return id == null ? null : categoryHref(id)
  }
  let done = 0
  for (const g of groups) {
    if (controller.signal.aborted) break
    const cid = ids.get(g.dirPath) ?? targetId
    if (cid == null) {
      results.value.push({ dir: g.dirPath, count: 0, error: '目录没建出来' })
      continue
    }
    let n = 0
    const notes = []
    // 保存失败（后端挂了 / 库写不进去）⇒ **这个目录剩下的都不试了**，
    // 但下一个目录照常试（是这一目录的问题，不是全局的）。
    let saveBroken = false
    // **这一目录要写哪几条**：
    // - 有清单（pack 模式，标题来自骨架）⇒ 一条一次调用
    // - 没清单（points 模式）⇒ 一次调用，模型自己定写哪几条
    const targets = g.titles.length ? g.titles : [null]
    for (const title of targets) {
      if (controller.signal.aborted || saveBroken) break
      // **一次一条**时，兄弟标题作为"别写成同一件事"的提示一起给
      // （原来一次问整个目录时，模型看得见兄弟标题 —— 拆开之后得补上这个上下文）。
      const one = {
        dirPath: g.dirPath,
        titles: title ? [title] : [],
        siblings: title ? g.titles.filter((t) => t !== title) : []
      }
      const { text: answerText, error: askError, aborted } = await askWithRetry(materialFor(one, written, cat, linkOf))
      done += 1
      progress.value = { done, total: totalPoints, points: written.length }
      if (aborted) break
      if (askError) {
        notes.push(`${title || g.dirPath}：${askError?.message || '失败'}`)
        continue
      }
      // 有标题清单 ⇒ 只认那一条（**按标题对齐，不是按位置**）；
      // 没清单 ⇒ 模型写了几条就是几条（`parsePoints` 不给清单时返回全部）。
      const parsed = parsePoints(answerText, one.titles).map((p) => ({
        ...p,
        content: fixCategoryLinks(p.content, validHrefs)
      }))
      if (!parsed.length) {
        notes.push(`${title || g.dirPath}：模型没输出正文`)
        continue
      }
      // **正文过短的不落库**：模型把"材料里没写"当正文写出来的卡片，比没有更糟。
      const { keep, short } = splitShortContent(parsed)
      for (const s of short) notes.push(`${s.title}：正文过短`)
      for (const p of keep) {
        // **标签**：单独一次调用（见 pickTags 的注释）
        const tags = await pickTags(p.title, p.content, g.dirPath)
        try {
          // **不刷新缓存**：批量建几十条时，每建一条都全量拉一次列表是纯浪费
          // （80 条 = 80 次请求）。整批结束统一刷一次。
          // **tags 一起带上**：后端按名字 upsert（同一个 (user, name) 复用同一个 tag）
          // ⇒ 不用先建标签再关联，也就不用再改一次。
          await createTopic({ title: p.title, content: p.content, categoryId: cid, typeId, tags }, { refresh: false })
          written.push(p.title)
          created.push({ title: p.title, categoryId: cid })
          n += 1
        } catch (e) {
          notes.push(`${p.title}：${e?.message || '保存失败'}`)
          saveBroken = true
          break
        }
      }
    }
    // 一行说清这一目录的情况：**每条各是什么问题**（模型没写出来 / 正文过短 / 失败）。
    // 只放前三条 —— 一次写十条的话，错误串会长到看不清。
    const errText = notes.length
      ? notes.slice(0, 3).join('；') + (notes.length > 3 ? ` 等 ${notes.length} 条` : '')
      : ''
    results.value.push({ dir: g.dirPath, count: n, error: errText })
    progress.value = { done, total: totalPoints, points: written.length }
  }
  running.value = false
  stopped.value = controller?.signal.aborted
  // 整批结束统一刷一次知识列表（建的时候没刷）
  await ensureTopicsLoaded(true)
  // 知识点之间的跳转：把正文里的 [[标题]] 换成指向那条知识的链接（见 linkCreatedTopics）
  const linkFailed = await linkCreatedTopics(created)
  if (linkFailed) {
    results.value.push({ dir: '正文里的知识点链接', count: 0, error: `${linkFailed} 条没链上` })
  }
  // 回填正文改过内容 ⇒ 缓存里还是带标记的旧正文，再刷一次
  await ensureTopicsLoaded(true)
  result.value = { dirs: props.mode === 'points' ? 0 : result.value.dirs, points: progress.value.points }
  step.value = props.mode === 'pack' ? 3 : 1
  emit('done')
}

/**
 * 把这一批知识点的正文里 `[[标题]]` 换成指向那条知识的链接。
 *
 * <p>**为什么放在整批结束之后**：知识点的 id 是落库那一刻才有的，
 * 而正文是落库**之前**写的 —— 模型写正文时不可能知道目标那条的 id。
 * ⇒ 模型只写名字（`[[标题]]`），地址在这里统一补（跟目录链接一个思路）。
 *
 * <p>**只认这一批建出来的**（目录 + 标题都对上）：不在这一批里的名字
 * （用户自己写的旧知识、别的目录的）一律退成普通文字 —— 宁可少一个链接，
 * 也不要一个点进去是空的。标题重名（同一次生成里两个同名）也退成普通文字：
 * 不知道指哪一条。
 *
 * @param {Array<{title: string, categoryId: number}>} created 这一批建出来的知识点
 * @returns {Promise<number>} 没链上的条数（0 = 全链上了）
 */
async function linkCreatedTopics(created) {
  if (!created.length) return 0
  const found = []
  const byTitle = new Map()
  for (const c of created) {
    const hit = getTopics().find((t) => t.categoryId === c.categoryId && t.title === c.title)
    if (!hit) continue
    found.push(hit)
    // 重名 ⇒ 标成"指不出来"（第二次出现同名就把 id 抹掉）
    byTitle.set(c.title, byTitle.has(c.title) ? null : hit.id)
  }
  const idByTitle = new Map()
  for (const [title, id] of byTitle) if (id != null) idByTitle.set(title, id)
  let failed = 0
  for (const t of found) {
    const next = linkTopicMarks(t.content, idByTitle)
    if (next === t.content) continue
    try {
      // refresh: false —— 批量回填，每改一条都全量拉一次列表是纯浪费
      await updateTopic(t.id, { content: next }, { refresh: false })
    } catch (e) {
      // 链不上不是致命问题（知识点已经建好了），但**要说出来**：
      // 不说的话，正文里就留着 [[标记]]，用户看着像坏了。
      failed += 1
    }
  }
  return failed
}

/**
 * 问模型要这个目录的正文，**失败重试一次**。
 *
 * <p>**为什么要重试**：模型接口的瞬时错误很多（限流 / 断流 / 网关超时）——
 * 实测「8.3 政府会计报告」那一次就是流里回了 error，整目录空着，用户还得自己
 * 去那个目录点「⋯ → 生成知识点」重跑一遍。**重问一次就好** ⇒ 别让一个目录
 * 因为一次抖动就空在那儿。
 *
 * <p>**只重试一次**：连续两次还失败，基本是配置 / 额度 / 内容本身的问题，
 * 再问也是白问（还白烧额度）。
 *
 * <p>用户中途「停止」时**不重试**（AbortError 直接返回 aborted）。
 *
 * @param {string} material 这一批的材料
 * @returns {Promise<{ text: string, error: Error|null, aborted: boolean }>}
 */
async function askWithRetry(material) {
  let error = null
  for (let attempt = 0; attempt < 2; attempt++) {
    try {
      const text = await ask('写知识点', {
        material,
        questionKey: 'ai.prompt.knowledge.point',
        signal: controller.signal
      })
      return { text, error: null, aborted: false }
    } catch (e) {
      if (e?.name === 'AbortError') return { text: '', error: null, aborted: true }
      error = e
    }
  }
  return { text: '', error, aborted: false }
}

/**
 * 给这条知识挑标签。**单独一次调用**。
 *
 * <p>**复用已有的标签判据，不新增第二份规则**：questionKey 指向
 * `ai.prompt.knowledge.tags:tags` —— 它的【判据】是**引用**现成那条
 * （`ai.prompt.question.pick_tags:tags`）的，**判据只有一份**；
 * 它自己的【格式】只多一句「没有合适的标签就写一行「无」」
 * （不那样定的话，模型会**一个字都不吐** —— 实测它的推理原话是
 * 「没有合适的标签就返回空」，而空串分不清是"没有"还是"失败"）。
 * 后端还会顺带附上**用户现有的标签名**（"统一叫法"就靠那份清单）。
 * ⇒ 材料里只需要给"这条知识是什么"。
 *
 * <p>**为什么不放进正文那次调用**：① 标签是**跨知识点**的决策
 * （「索引」和「索引优化」只能留一个叫法），单看一条知识没有全局视野；
 * ② 正文那次调用刚把输出上限的问题解决掉，再往里塞东西等于往超载的调用上加。
 *
 * <p>**失败不抛**：标签拿不到就把知识点照常落库 —— 正文比标签重要。
 *
 * @param {string} title 这条知识的标题
 * @param {string} content 这条知识的正文
 * @param {string} dirPath 它挂在哪个目录（主归属）
 * @returns {Promise<string[]>} 标签名（没有就空数组）
 */
async function pickTags(title, content, dirPath) {
  try {
    const text = await ask('给这条知识挑标签', {
      material: buildTagMaterial({ title, content, dirPath }),
      questionKey: 'ai.prompt.knowledge.tags:tags',
      signal: controller.signal
    })
    return parseTagNames(text)
  } catch (e) {
    // 停止是用户主动的，不是错误；这里一律当作"没有标签"
    return []
  }
}

/**
 * 给模型的材料：主题 / 目录 / 要写什么 / 已经写过什么 / 下级章节。
 *
 * <p>拼法在 `utils/knowledgePoints#buildMaterial`（**只有一份定义**）：
 * 弹窗和验证脚本喂给模型的是同一段话，脚本才能证明界面这条路走得通。
 *
 * @param {object} group 这一批（一个目录；一次一条时 titles 只有一条，兄弟在 siblings）
 * @param {Array<string>} written 已经写过的知识点标题
 * @param {object|null} cat points 模式的目标目录（现成的）
 * @param {(path: string) => string|null} linkOf 目录路径 → 链接地址（总览知识点用）
 */function materialFor(group, written, cat, linkOf) {
  return buildMaterial({
    topic: topic.value,
    dirPath: group.dirPath,
    titles: group.titles,
    siblings: group.siblings || [],
    written,
    kids: cat ? childNamesOf(cat.id) : [],
    node: plan.value.dirNodes.get(group.dirPath),
    linkOf,
    // **这次生成结构里的全部知识点**：正文里提到哪一个可以写成 [[标题]]，
    // 落库后由 linkCreatedTopics 换成链接。
    // points 模式没有骨架（`plan.points` 是空的）⇒ 那条路模型只能标自己写出来的那几条。
    allPoints: plan.value.points
  })
}

/** 某目录下的子目录名。**用 store 那份**（同一棵树，别自己再过滤一遍） */
function childNamesOf(id) {
  return getChildCategories(id).map((c) => c.name)
}

/** 停止：掐掉正在进行的流，已经写好的留着 */
function stop() {
  controller?.abort()
}
</script>

<style scoped>
.steps {
  margin-bottom: var(--sp-14);
}
.pane {
  min-height: 180px;
}
.tabs {
  display: flex;
  gap: var(--sp-6);
  margin-bottom: var(--sp-10);
}
.tabs__item {
  border: none;
  background: transparent;
  padding: var(--sp-5) var(--sp-9);
  border-radius: var(--radius-sm);
  color: var(--cc-text-3);
  font-size: var(--text-md);
  cursor: pointer;
}
.tabs__item.is-on {
  background: var(--cc-primary-soft);
  color: var(--cc-primary);
  font-weight: 600;
}
.acts {
  display: flex;
  gap: var(--sp-9);
  margin-top: var(--sp-10);
}
.sum {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-9);
  font-size: var(--text-sm);
  color: var(--cc-text-2);
}
.warn {
  margin: var(--sp-6) 0 0;
  font-size: var(--text-sm);
  color: var(--cc-danger);
}
/* 说明文字。**故意不叫 .hint**：admin-page.css 里有一份全局 .hint（给 teleport 到
   body 的弹窗用，.text-xs + line-height 1.6），同名两份、值还不一样 ⇒
   叠在一起谁生效看权重。这里用自己的名字，跟全局那份彻底分开。 */
.pane__hint {
  margin: var(--sp-6) 0 0;
  font-size: var(--text-sm);
  line-height: 1.6;
  color: var(--cc-text-3);
}
.tools {
  margin-top: var(--sp-10);
}
.tree {
  margin-top: var(--sp-6);
  max-height: 300px;
  overflow-y: auto;
}
.row {
  display: flex;
  align-items: center;
  gap: var(--sp-6);
  height: 28px;
  font-size: var(--text-md);
  color: var(--cc-text-1);
}
.row__fold {
  width: 16px;
  height: 16px;
  flex: 0 0 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  padding: 0;
  cursor: pointer;
  color: var(--cc-text-4);
}
.row__fold.is-empty {
  cursor: default;
}
.row__fold :deep(.el-icon) {
  transition: transform 0.15s;
}
.row__fold :deep(.el-icon.is-open) {
  transform: rotate(90deg);
}
/* 名字是可直接编辑的：输入框只留一条下边框，别在列表里摆一圈框 */
.row__input {
  flex: 1;
  min-width: 0;
}
.row__input :deep(.el-input__wrapper) {
  box-shadow: none;
  background: transparent;
  padding: 0 var(--sp-4) 0 0;
}
.row__input :deep(.el-input__inner) {
  border-bottom: 1px solid var(--cc-border);
  color: var(--cc-text-1);
  height: 22px;
}
/* 超长的目录名标红 —— 它建不了，改短了才能建 */
.row__input.is-bad :deep(.el-input__inner) {
  border-bottom-color: var(--cc-danger);
  color: var(--cc-danger);
}
.tag {
  font-size: var(--text-xs);
  padding: 1px var(--sp-6);
  border-radius: var(--radius-xs);
  flex-shrink: 0;
  color: var(--cc-text-3);
  background: var(--cc-surface-2);
}
.tag--exists {
  color: var(--cc-primary);
  background: var(--cc-primary-soft);
}
.tag--point {
  color: var(--cc-text-2);
}
.prog {
  margin: 0;
  font-size: var(--text-base);
  color: var(--cc-text-1);
}
.log {
  margin-top: var(--sp-10);
  max-height: 260px;
  overflow-y: auto;
}
.log__row {
  display: flex;
  align-items: baseline;
  gap: var(--sp-8);
  padding: var(--sp-4) 0;
  font-size: var(--text-sm);
  color: var(--cc-text-2);
  border-bottom: 1px solid var(--cc-border);
}
.log__dir {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.log__num {
  color: var(--cc-text-3);
  flex-shrink: 0;
}
.log__err {
  color: var(--cc-danger);
  flex-shrink: 0;
}
.done {
  margin: 0;
  font-size: var(--text-base);
  color: var(--cc-text-1);
}
</style>
