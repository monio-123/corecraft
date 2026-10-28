<template>
  <el-dialog
    :model-value="modelValue"
    :title="mode === 'points' ? '生成正文' : '生成知识包'"
    width="640px"
    :close-on-click-modal="false"
    @update:model-value="close"
  >
    <!-- pack 模式四步：主题 → 骨架 → 正文 → 完成。
         **骨架那步只落结构**（目录 + 知识点占位：title 有、content 空）；
         写正文是**下一步**的事（那一步再选要不要标签 / 要不要知识点关联）。
         points 模式只有一步：写正文。 -->
    <el-steps v-if="mode === 'pack'" :active="step" simple class="steps">
      <el-step title="主题" />
      <el-step title="骨架" />
      <el-step title="正文" />
      <el-step title="完成" />
    </el-steps>

    <!-- 第 1 步：来源 -->
    <section v-if="at.theme" class="pane">
      <div class="tabs">
        <button class="tabs__item" :class="{ 'is-on': source === 'ai' }" @click="source = 'ai'">AI 生成</button>
        <button class="tabs__item" :class="{ 'is-on': source === 'paste' }" @click="source = 'paste'">粘贴</button>
      </div>

      <template v-if="source === 'ai'">
        <div class="field">
          <label class="field__label">目录名称</label>
          <el-input v-model="topic" placeholder="如：MySQL" @keyup.enter="generateOutline" />
        </div>
        <div class="field">
          <label class="field__label">补充说明</label>
          <el-input v-model="extra" type="textarea" :rows="3" placeholder="如：面向开发、面向考试" />
        </div>
        <!-- **层数上限**：目录最多几层（0 = 不限）。只管 AI 生成的骨架，
             粘贴的树是用户自己写的，本来就多深都行。 -->
        <div class="field">
          <label class="field__label">层数上限</label>
          <el-select v-model="maxDepth" size="small" class="field__select">
            <el-option v-for="n in [3, 4, 5, 6, 7, 8]" :key="n" :label="`${n} 层`" :value="n" />
            <el-option :label="'不限'" :value="0" />
          </el-select>
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
      </template>
    </section>

    <!-- 第 2 步：骨架。**目录 = 有下级的节点，知识点 = 叶子**，两件事都在这棵树里 -->
    <section v-else-if="at.skeleton" class="pane">
      <div class="sum">
        <span>将建 {{ plan.dirCount }} 个目录</span>
        <span v-if="plan.pointCount">· 生成 {{ plan.pointCount }} 个知识点</span>
        <!-- **正文在下一步写** ⇒ 这里先说清要写多少篇（这一批要花的时间） -->
        <span v-if="plan.pointCount">· 写 {{ plan.pointCount }} 篇正文</span>
      </div>
      <p v-if="plan.tooLong.length" class="warn">{{ plan.tooLong.length }} 个目录名超过 {{ MAX_NAME }} 字</p>
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

    <!-- 正文那一步：pack 模式写「刚建好的这一批」，points 模式给「一个现成目录」写 -->
    <section v-else-if="at.points" class="pane">
      <!-- 还没开始：pack 先说清写多少、让用户选两个开关；
           points 先让用户看清"要给哪个目录写"，再点开始（写一堆知识是不可逆的） -->
      <template v-if="!started">
        <div v-if="mode === 'pack'" class="opts">
          <p class="opts__sum">将写 {{ result.points }} 篇正文</p>
          <div class="opts__row">
            <!-- **两个开关只在这一步出现**：生成目录那步看不到它们。 -->
            <el-checkbox v-model="withTags" class="opt">
              <span class="opt__name">生成标签</span>
              <span class="opt__hint">每条另挑一次标签</span>
            </el-checkbox>
            <el-checkbox v-model="withLinks" class="opt">
              <span class="opt__name">知识点关联</span>
              <span class="opt__hint">正文里提到的知识点给链接</span>
            </el-checkbox>
          </div>
        </div>
        <p v-else class="done">给「{{ category?.name }}」写正文</p>
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

    <!-- 完成 -->
    <section v-else class="pane">
      <div class="donebox">
        <el-icon class="donebox__icon"><CircleCheckFilled /></el-icon>
        <p class="done">{{ doneText }}</p>
      </div>
      <!-- **失败明细跟到完成页**：正文那一步一结束这页就换掉了，
           哪几条没写出来、为什么，不能跟着消失（2026-10-28：跑到一半跳到"完成"，
           用户只看到一个对不上的数字，不知道该重写哪几条）。 -->
      <div v-if="failedResults.length" class="log">
        <div v-for="(r, i) in failedResults" :key="i" class="log__row">
          <span class="log__dir">{{ r.dir }}</span>
          <span class="log__num">{{ r.count }} 个</span>
          <span class="log__err">{{ r.error }}</span>
        </div>
      </div>
      <!-- **没写完**（被停了 / 有几条失败）⇒ 告诉用户还能接着写 -->
      <p v-if="mode === 'pack' && result.points > result.written" class="pane__hint">
        没写完的可在目录「⋯ → 生成正文」里接着写
      </p>
    </section>

    <!-- 底部一排：**左边是上一步，右边是这次生成的参数 + 主按钮**。
         取消按钮**没有**：右上角那个 × 就是关掉，底部再摆一个"取消"是多余的。 -->
    <template #footer>
      <div class="foot">
        <div class="foot__side">
          <el-button v-if="at.skeleton" size="small" @click="step = 0">上一步</el-button>
        </div>
        <div class="foot__side foot__side--right">
          <!-- **模型和思考，每一步都在**：生成中途改了也生效（作用于下一次调用）。 -->
          <template v-if="!at.done">
            <el-select v-model="pickEndpointId" size="small" class="foot__model" placeholder="模型">
              <el-option
                v-for="e in aiEndpoints"
                :key="e.id"
                :label="e.name || e.model"
                :value="e.id"
                :disabled="!e.ready"
              />
            </el-select>
            <el-checkbox v-model="thinking">开启思考</el-checkbox>
          </template>

          <el-button
            v-if="at.theme && source === 'ai'"
            type="primary"
            size="small"
            :loading="generating"
            :disabled="!topic.trim()"
            @click="generateOutline"
          >
            生成骨架
          </el-button>
          <el-button v-else-if="at.theme" type="primary" size="small" :disabled="!text.trim()" @click="parseOutline">
            解析
          </el-button>
          <!-- 骨架那步的按钮是「下一步」：**这一步建结构，下一步才写正文** -->
          <el-button
            v-if="at.skeleton"
            type="primary"
            size="small"
            :loading="building"
            :disabled="!canBuild"
            @click="build"
          >
            下一步
          </el-button>
          <el-button v-if="at.points && !started" type="primary" size="small" @click="startContent">开始生成</el-button>
          <el-button v-if="at.points && started" type="primary" size="small" :loading="running" @click="stop">
            停止
          </el-button>
          <el-button v-if="at.done" type="primary" size="small" @click="close">完成</el-button>
        </div>
      </div>
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
 * - `pack`（侧边栏入口）：主题 → 骨架 → **正文** → 完成（**四步是固定的**）。
 *   骨架那步**只落结构**（目录 + 每个目录的知识点标题占位），点「下一步」建完结构
 *   就进「正文」那步 —— 那一步再选**要不要标签 / 要不要知识点关联**，然后写正文。
 *   ⇒ 标题先定下来，写正文时才不会跑题、不会重复；中断了可以在目录的
 *   「⋯ → 生成正文」里接着写（**只写还没写正文的那几条**）。
 * - `points`（目录「⋯」入口）：目录是现成的，直接给它写正文。
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
import { ArrowRight, CircleCheckFilled } from '@element-plus/icons-vue'
import { ask, aiEndpoints, currentEndpointId, ensureAiConfigLoaded } from './useAi'
import {
  importCategories,
  createTopic,
  updateTopic,
  ensureTopicIndexLoaded,
  ensureTopicTypesLoaded,
  getCategories,
  getChildCategories,
  getCategoryPath,
  getCategorySubtreeIds,
  fetchTopicIndexFresh,
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
  MAX_NAME,
  MAX_NODES
} from '../../utils/outlineTree'
import { parsePoints, buildMaterial, buildTagMaterial, parseTagNames, fixCategoryLinks, linkTopicMarks, splitShortContent } from '../../utils/knowledgePoints'
import { getMyConfig } from '../../api/config'
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
/** 目录名称（也是骨架的根目录名） */
const topic = ref('')
/** 补充说明：用户自己写的一句话（"面向开发"、"面向考试"……）。空 = 没有补充 */
const extra = ref('')
/**
 * **生成偏好**：整个系统通用的默认口味（系统配置里的「生成偏好」那一项）。
 *
 * <p>**它是系统级的，没有"我的"那份** —— 偏好不是个人的，是"这套系统生成东西时的口味"。
 * <p>**没配 = 材料里就不给这一段**（模型按统一提示词写）。
 */
const genPref = ref('')
/**
 * 目录层数上限。**5 = 目录最多 5 层**（知识点是叶子，不占目录的层数）；0 = 不限。
 *
 * <p>**只管 AI 生成**：粘贴的树是用户自己写的，本来就多深都行。
 * 这个值只进材料（`层数上限：N`），由提示词照着办 —— 代码不拦（`kp_category`
 * 是邻接表，层数只是数据）。
 */
const maxDepth = ref(5)
const text = ref('')
const generating = ref(false)
const building = ref(false)
const running = ref(false)
const stopped = ref(false)
/** 知识点那一步有没有开始（points 模式要先点"开始"，别一打开就写一堆知识） */
const started = ref(false)
/**
 * **这次用哪个模型**（用户注册过的 API）。
 *
 * <p>**不写进全局选择**：全局那一份是"我平时用哪个"，这里是"这一批用哪个"——
 * 临时挑一个跑完，不该把用户平时的选择改掉。
 */
const pickEndpointId = ref(null)
/**
 * **这次要不要模型的思考**。默认开着。
 *
 * <p>关掉能快好几倍（实测正文快 3~4 倍），代价是**更容易出事实错误**：
 * 同一份提示词，关思考那份出现过「MySQL 8.0.29 起 TRUNCATE 可回滚」这种硬伤，
 * 开思考那份没有 ⇒ 所以它**默认开着**，要快自己关。
 */
const thinking = ref(true)
/**
 * **正文那一步的两个开关**（只在 pack 模式的「正文」那步出现，生成目录那步看不到）。
 *
 * - `withTags`：要不要给每条知识点挑标签（标签是**另一次**调用，见 pickTags）
 * - `withLinks`：要不要让正文里提到的**这次生成结构里的**知识点变成链接
 *   （模型写 `[[标题]]`，落库前换成 `/app/topic/<id>`，见 linkTopicMarks）
 *
 * **默认都开着**（= 现在的行为）。关掉哪一个，那一项的工作就一点不做：
 * 标签不调模型；关联不给模型"可标的名字清单"，它就不会写标记。
 *
 * <p>⚠️ **只有 pack 模式露这两个开关**：用户改的是「生成知识包」这一步的流程，
 * 目录「⋯ → 生成正文」那个入口保持原样（标签和关联照做）。
 */
const withTags = ref(true)
const withLinks = ref(true)
/** 解析 + 标记之后的树 */
const marked = ref([])
const collapsed = ref(new Set())
const progress = ref({ done: 0, total: 0, points: 0 })
const results = ref([])
/** 有失败的那些目录（完成页要接着显示，不能让失败明细跟着正文那一步一起消失） */
const failedResults = computed(() => results.value.filter((r) => r.error))
const result = ref({ dirs: 0, points: 0, written: 0 })
/**
 * **这一批的上下文**（建完结构后**从库里现取**的）：写正文那一步要用
 * —— 标题清单（模型能标的名字）、目录链接的合法集合、这批知识点。
 *
 * <p>**为什么存下来而不是写正文时再取**：结构刚落库时取一次最准
 * （刚建的那批占位也在里面）；写正文是**另一次点击**（用户要选两个开关），
 * 那时再取也行，但**多一次全量查**且会掺进这期间别人建的东西。
 *
 * <p>⚠️ 取的时候**现拉一份索引**，不拿 store 的共享缓存：那份可能停在旧快照
 * （2026-10-28 实测：少算 74 条 ⇒ 一半知识点没写，界面却显示"完成"）。
 */
const batch = ref(null)
let controller = null

/**
 * 当前落在哪一步。
 *
 * <p>points 模式没有"主题"和"骨架"（目录是现成的），它的一步就是"知识点"。
 * 用一份 computed 算，模板不再到处写"这个模式下这一步是哪一步"。
 */
const at = computed(() => {
  const pack = props.mode === 'pack'
  if (!pack) {
    // points 模式：目录是现成的，它的一步就是"写正文"
    return { theme: false, skeleton: false, points: step.value === 0, done: step.value === 1 }
  }
  // pack 模式：主题(0) → 骨架(1) → 正文(2) → 完成(3)
  // **四步是固定的**：骨架那步点「下一步」建完结构就进正文那步，
  // 不再由一个勾选决定"有没有正文那一步"。
  return {
    theme: step.value === 0,
    skeleton: step.value === 1,
    points: step.value === 2,
    done: step.value === 3
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

/**
 * 完成页那一句。
 *
 * <p>pack 模式说三样：建了多少目录、多少知识点（**标题**，都落库了）、
 * 写了多少篇正文（**被停了 / 有几条失败时可能少于知识点数**）。
 */
const doneText = computed(() => {
  if (props.mode === 'pack') {
    const parts = [`已建 ${result.value.dirs} 个目录`]
    if (result.value.points) parts.push(`${result.value.points} 个知识点`)
    if (result.value.written) parts.push(`写了 ${result.value.written} 篇正文`)
    return parts.join('，')
  }
  return `已写入 ${result.value.written || result.value.points} 条正文`
})

const canBuild = computed(
  () =>
    plan.value.dirCount > 0 &&
    !plan.value.tooLong.length &&
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
    // **偏好是系统级的**：读一次就够，一次生成中途不会变
    loadGenPref()
    // **默认用全局当前选中的那个模型**（AI 设置里选的那条）。
    // 端点列表是懒加载的 ⇒ 得等它到齐，才知道有没有得选。
    ensureAiConfigLoaded().then(() => {
      pickEndpointId.value =
        currentEndpointId.value ?? aiEndpoints.value.find((e) => e.ready)?.id ?? null
    })
  }
)

/**
 * 读「生成偏好」那一项（系统默认）。
 *
 * <p>**拉不到就当没配，不报错**：偏好是可选的，缺了照常生成。
 * <p>**只认系统级**（`values` 里就是生效值 = 我的 > 系统默认；个人那份没有 ⇒ 拿到的就是系统默认）。
 */
async function loadGenPref() {
  try {
    const res = await getMyConfig('ai.prompt.knowledge.pref')
    genPref.value = res?.data?.values?.['ai.prompt.knowledge.pref'] || ''
  } catch {
    genPref.value = ''
  }
}

function reset() {
  step.value = 0
  source.value = 'ai'
  topic.value = ''
  extra.value = ''
  maxDepth.value = 5
  text.value = ''
  pickEndpointId.value = null
  thinking.value = true
  withTags.value = true
  withLinks.value = true
  marked.value = []
  collapsed.value = new Set()
  progress.value = { done: 0, total: 0, points: 0 }
  results.value = []
  result.value = { dirs: 0, points: 0, written: 0 }
  batch.value = null
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
  for (const n of allNodes(marked.value)) n.checked = true
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

/**
 * 这次调用的两个参数：**模型 + 思考**。
 *
 * <p>**每次调用都现取** —— 生成中途改了模型或思考开关，下一条立刻生效
 * （一次生成要跑几十次调用，中间换模型是正常需求）。
 *
 * @returns {{endpointId: number|null, thinking: boolean}}
 */
function callOpts() {
  return { endpointId: pickEndpointId.value, thinking: thinking.value }
}

/** AI 生成骨架：目录名称 + 补充说明 + 层数上限 + 现有目录清单（让它避开重复造同义目录） */
async function generateOutline() {
  generating.value = true
  try {
    const cats = getCategories()
    const parts = [`主题：${topic.value.trim()}`]
    // **补充说明**紧跟主题：它是"这一批要写什么"的整体限定
    // （面向开发 / 面向考试 / 只要原理……），放在现有目录清单前面。
    if (extra.value.trim()) parts.push(`补充说明：${extra.value.trim()}`)
    // **层数上限**：0 = 不限。写成一句人话，提示词里点名要用它。
    parts.push(maxDepth.value ? `层数上限：${maxDepth.value} 层` : '层数上限：不限')
    const existing = renderOutlineTree(cats)
    if (existing) parts.push(`现有目录清单（缩进表示层级）：\n${existing}`)
    const answerText = await ask('生成目录骨架', {
      material: parts.join('\n\n'),
      questionKey: 'ai.prompt.knowledge.outline',
      ...callOpts()
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

/**
 * 原始文本 → 骨架。
 *
 * @param {string} raw 模型或用户给的 markdown 树
 */
function applyOutline(raw) {
  const forest = parseOutlineTree(raw)
  if (!forest.length) {
    ElMessage.error('没认出目录树')
    return
  }
  marked.value = markOutline(forest, getCategories(), { id: 0 })
  step.value = 1
  collapseDefault()
}

// ---------------- 第 2 步：建结构（目录 + 知识点占位） ----------------

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

/**
 * 这个目录**现有的**子目录名（模型别写这些领域的内容）。
 *
 * <p>**只有这一处取**：材料里"这个目录现有的子目录"那段就是它。
 */
function kidsOf(dirId) {
  return getChildCategories(dirId).map((c) => c.name)
}

/**
 * 待写的知识点**按目录分组**（同目录的兄弟标题互为提示 ⇒ 不会写成同一件事）。
 *
 * <p>分组单位是**目录 id**（不是路径）：正文是**另一次**生成的，
 * 路径那会儿已经不在内存里了，只有 id 认得出。
 *
 * @param {Array} list 这棵子树里还没写正文的知识点
 * @param {object} scope 子树上下文（拿目录全路径用）
 * @returns {Array<{dirId:number, dirName:string, titles:string[], topicIds:number[], kids:string[]}>}
 */
function groupByCategory(list, scope) {
  const byDir = new Map()
  for (const t of list) {
    if (!byDir.has(t.categoryId)) {
      byDir.set(t.categoryId, {
        dirId: t.categoryId,
        dirName: scope.pathOf(t.categoryId),
        titles: [],
        topicIds: [],
        // **这个目录现有的子目录名**（模型别写这些领域的内容）。
        // **按目录各给各的**：整棵树都列一遍的话，每条正文都拖着几十行 ——
        // 浪费 token，还让模型分不清"我这条归谁管"。
        kids: kidsOf(t.categoryId)
      })
    }
    const g = byDir.get(t.categoryId)
    g.titles.push(t.title)
    g.topicIds.push(t.id)
  }
  return [...byDir.values()]
}

/**
 * 目录 id → 目录全路径（"MySQL/1. 架构/1.1 连接"）。
 *
 * <p>**只有这一处拼**：材料里的"目录"、知识点清单里的 dirPath 都用它。
 */
function pathOfDir(id) {
  return joinPath('', getCategoryPath(id).map((c) => c.name).join('/'))
}

/**
 * 这批要写的知识点（**现拉一份全集索引**再筛）。
 *
 * <p>**为什么不用共享缓存那份**（`topicsInScope`）：
 * ① 那份是**当前关键词下的全集** —— 列表页搜过东西时，生成范围会少一批；
 * ② 它可能停在**旧快照**：2026-10-28 实测，建骨架途中顺手发的那次索引请求被后面
 * 一百多次写请求堵住、回来晚了，把刚刷新的完整索引覆盖成"建到一半"的样子
 * ⇒ 生成范围只剩 79 条，另外 74 条**从来没进过生成循环**，界面却显示"完成"。
 *
 * <p>⇒ 生成范围**自己拉一份**：要的是这批的全部，一条都不能少。
 *
 * @param {Set<number>} catIds 这批知识点可能挂着的目录 id
 * @returns {Promise<Array>} 结构索引条目（id / 标题 / 目录 / 有无正文）
 */
async function fetchScopeTopics(catIds) {
  const all = await fetchTopicIndexFresh()
  return all.filter((t) => catIds.has(t.categoryId))
}

/**
 * 这棵子树的上下文：标题清单（模型标 [[标记]] 用）+ 目录链接的合法集合。
 *
 * <p>**从库里现取，不从骨架取** —— 正文是**另一次**生成的，骨架那会儿已经不在内存里了。
 * 子树范围用 store 那份 `getCategorySubtreeIds`（**进目录 = 看整棵子树**，规则只有一份）。
 */
async function buildScope(cat) {
  const dirIds = getCategorySubtreeIds(cat.id)
  const topics = await fetchScopeTopics(dirIds)
  return {
    catId: cat.id,
    dirName: cat.name,
    dirIds,
    topics,
    /** 目录全路径（"MySQL/1. 架构/1.1 连接"）—— 材料里的"目录"用它，
     *  模型才知道这条属于哪一片 */
    pathOf: pathOfDir,
    /** 材料里那份《全部知识点》清单（模型能标的名字就这些） */
    allPoints: topics.map((t) => ({ dirPath: pathOfDir(t.categoryId), title: t.title }))
  }
}

/**
 * **这批新树**的上下文（写正文用）。
 *
 * <p>范围 = **这批刚建出来的目录**（`ids` 是骨架路径 → 目录 id 的映射）。
 * 骨架可能有**多棵根**（粘贴路径能粘多棵）⇒ 把这批目录的 id 全收进来。
 *
 * <p>每篇正文各自的"下级章节"由 `groupByCategory` **按目录**给（更准，也更省材料）。
 *
 * @param {Map<string, number>} ids 目录路径 → 目录 id（`resolveDirIds` 的结果）
 */
async function buildBatchScope(ids) {
  const dirIds = new Set(ids.values())
  const topics = await fetchScopeTopics(dirIds)
  return {
    topics,
    allPoints: topics.map((t) => ({ dirPath: pathOfDir(t.categoryId), title: t.title })),
    dirIds,
    pathOf: pathOfDir
  }
}

/**
 * 「学习」这一档的类型 id。**没有就直接报错停**，不静默落回默认档
 * （落回去等于生成一批类型全错、还查不出是哪错的知识点）。
 *
 * <p>以前这里**不传 typeId** ⇒ `createTopic` 走"字典里排第一的那条" =「笔记」——
 * 一批教材式正文全被标成随手记，而且界面上完全看不出来。
 */
async function ensureLearnTypeId() {
  await ensureTopicTypesLoaded()
  const typeId = topicTypeIdByValue(TOPIC_TYPE_LEARN)
  if (typeId == null) throw new Error('知识点类型里没有「学习」这一档')
  return typeId
}

/**
 * 建目录 + 落知识点**占位**（title 有、content 空）。**这一步只落结构**，
 * 写正文是**下一步**的事（那一步再选要不要标签 / 要不要知识点关联）。
 *
 * <p>**占位为什么也要落库**：① 用户要看到的是"目录**和知识点**"，不落库就没地方存标题；
 * ② 知识点之间的跳转靠**标题 → id**，标题先在库里，链接才有目标（正文晚点写不影响）。
 * ⇒ 写正文时走的是**更新占位**（`runGroups` 里 `g.topicIds[ti]`），不是新建。
 */
async function build() {
  building.value = true
  try {
    const rows = await importCategories(plan.value.dirs, 0)
    const ids = resolveDirIds(plan.value.dirs, rows, 0)
    const typeId = await ensureLearnTypeId()
    let n = 0
    for (const p of plan.value.points) {
      const cid = ids.get(p.dirPath)
      if (cid == null) continue
      // **content 传空串**：占位就是"还没写正文"。
      // （`updateTopic` 把 null 当"不修改"⇒ 只能写 ''；库里本来就有一条空正文的老数据，约定一致。）
      await createTopic({ title: p.title, content: '', categoryId: cid, typeId }, { refresh: false })
      n += 1
    }
    await ensureTopicIndexLoaded(true)
    result.value = { dirs: plan.value.dirCount, points: n, written: 0 }
    // **上下文从库里现取**（结构刚落库）⇒ 标题清单、目录链接集合都是最新的。
    // ⚠️ 这一步**现拉一份索引**，不拿 store 的共享缓存：那份可能停在旧快照
    // （2026-10-28 实测：少算 74 条 ⇒ 界面显示"完成"，实际有一半没写）。
    batch.value = await buildBatchScope(ids)
    // **通知外面刷新**：目录树和知识点列表都变了
    emit('done')
    // 结构建好 ⇒ 进「正文」那一步（写哪几个、要不要标签 / 关联，由那一步定）
    step.value = 2
  } catch (e) {
    ElMessage.error(e?.message || '创建失败')
    // **留在骨架步**：结构没建完，让用户重来一次
    // （目录是**按名字合并**的 ⇒ 重来不会建出两棵同名树）。
    step.value = 1
  } finally {
    building.value = false
  }
}

/**
 * 「正文」那一步的「开始生成」：pack 写**刚建好的这一批**，points 给**一个现成目录**写。
 *
 * <p>两个入口共用一条生成链路（`runGroups`）⇒ 差在哪一步、两个开关怎么用，
 * 全都在一处（`runGroups` / `materialFor`），不各写一份。
 *
 * <p>**`running` 在这里同步置位**：生成链路里第一个 await 之前还有一段同步代码，
 * 只在 `runGroups` 里置位的话，**连点两下「开始生成」会并发跑两批**（各写一遍正文）。
 */
async function startContent() {
  if (running.value) return
  running.value = true
  try {
    if (props.mode === 'pack') await startPackContent()
    else await startPoints()
  } catch (e) {
    // **失败要说出来**（别把异常吞成"点了没反应"）
    ElMessage.error(e?.message || '生成失败')
    running.value = false
  }
}

/**
 * pack 模式：写**刚建好的这一批**的正文。
 *
 * <p>**范围取 `batch`**（`build()` 建完结构时从库里取的）：标题清单、目录链接集合
 * 都是这批新建的东西。**只写还没写正文的那几条** ⇒ 中断之后再点一次接着写。
 */
async function startPackContent() {
  const scope = batch.value
  if (!scope) return
  const pending = scope.topics.filter((t) => !t.hasContent)
  await runGroups(groupByCategory(pending, scope), scope)
}

/**
 * points 模式：给一个**现成目录**写正文（目录「⋯ → 生成正文」）。
 *
 * <p>**两条分支，同一个入口**：
 * ① 这棵子树里有**还没写正文的知识点**（骨架落库时留的占位）⇒ 按标题逐条写
 *    （一条一次调用，漏一条只丢一条）；
 * ② 一条占位都没有 ⇒ 让模型自己定这个目录该有哪几条（提示词里"没给标题清单"那条分支）。
 *
 * <p>**只写没正文的** ⇒ 中断之后再点一次接着写，不会把写好的重写一遍。
 */
async function startPoints() {
  const cat = props.category
  if (!cat?.id) return
  const scope = await buildScope(cat)
  const pending = scope.topics.filter((t) => !t.hasContent)
  if (pending.length) {
    await runGroups(groupByCategory(pending, scope), scope)
  } else {
    await runGroups([{ dirId: cat.id, dirName: cat.name, titles: [], topicIds: [], kids: kidsOf(cat.id) }], scope)
  }
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
 * @param {Array<{dirId:number, dirName:string, titles:string[], topicIds:number[]}>} groups
 *        每个目录一组。titles 为空 = 让模型自己定这个目录该有哪几条；
 *        titles 非空 = 按这些标题逐条写（topicIds 是对应的**占位**知识点）。
 * @param {object} scope 这棵子树的上下文（标题清单 / 目录链接的合法集合）
 */
async function runGroups(groups, scope) {
  // **知识点类型**：落「学习」档（以前不传 typeId ⇒ 一批教材式正文全被标成「笔记」，
  // 界面上完全看不出来）。判据和"没有就报错停"都在 `ensureLearnTypeId` 里。
  const typeId = await ensureLearnTypeId()
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
  // **知识点之间的链接**：模型写 `[[标题]]`，**落库前**换成指向 id 的链接。
  // 标题 → id 的映射**一次建好**（这棵子树的知识点都已经在库里了）⇒ 不用等整批结束
  // 再回填（那样中断会在库里留一堆方括号）。
  // **不做关联时映射是空的** ⇒ 认不出来的标记退成普通文字（`[[x]]` → `x`）。
  const idByTitle = withLinks.value ? titleIdMap(scope.topics) : new Map()
  // **目录链接**：正文里指向目录的链接，地址由材料给模型照抄。
  // 认不出来的（模型把 id 写错了）一律退成普通文字 —— 宁可少一个链接，
  // 不要一个点进去是空列表的链接。
  const validHrefs = [...scope.dirIds].map((id) => categoryHref(id))
  let done = 0
  for (const g of groups) {
    if (controller.signal.aborted) break
    const cid = g.dirId
    if (cid == null) {
      results.value.push({ dir: g.dirName, count: 0, error: '目录没建出来' })
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
    for (let ti = 0; ti < targets.length; ti++) {
      const title = targets[ti]
      if (controller.signal.aborted || saveBroken) break
      // **一次一条**时，兄弟标题作为"别写成同一件事"的提示一起给
      // （原来一次问整个目录时，模型看得见兄弟标题 —— 拆开之后得补上这个上下文）。
      const one = {
        dirName: g.dirName,
        titles: title ? [title] : [],
        siblings: title ? g.titles.filter((t) => t !== title) : [],
        // **这个目录现有的子目录**（材料里"别写这些领域"那段）
        kids: g.kids || []
      }
      const { text: answerText, error: askError, aborted } = await askWithRetry(materialFor(one, written, scope))
      done += 1
      progress.value = { done, total: totalPoints, points: written.length }
      if (aborted) break
      if (askError) {
        notes.push(`${title || g.dirName}：${askError?.message || '失败'}`)
        continue
      }
      // 有标题清单 ⇒ 只认那一条（**按标题对齐，不是按位置**）；
      // 没清单 ⇒ 模型写了几条就是几条（`parsePoints` 不给清单时返回全部）。
      const parsed = parsePoints(answerText, one.titles).map((p) => ({
        ...p,
        content: fixCategoryLinks(p.content, validHrefs)
      }))
      if (!parsed.length) {
        notes.push(`${title || g.dirName}：模型没输出正文`)
        continue
      }
      // **正文过短的不落库**：模型把"材料里没写"当正文写出来的卡片，比没有更糟。
      const { keep, short } = splitShortContent(parsed)
      for (const s of short) notes.push(`${s.title}：正文过短`)
      for (const p of keep) {
        // **标签**：单独一次调用（见 pickTags 的注释）；**关掉标签就不调**（省一次调用）。
        // 传 undefined 而不是 []：`updateTopic` 只在 tags **存在**时才改它
        // ⇒ 关掉标签不会把占位上原有的标签抹掉。
        const tags = withTags.value ? await pickTags(p.title, p.content, g.dirName) : undefined
        // **标记在落库前替换** ⇒ 落库的就是带链接的正文（不用事后再回填一遍）
        const content = linkTopicMarks(p.content, idByTitle)
        try {
          // **不刷新缓存**：批量写几十条时，每写一条都全量拉一次列表是纯浪费
          // （80 条 = 80 次请求）。整批结束统一刷一次。
          // **tags 一起带上**：后端按名字 upsert（同一个 (user, name) 复用同一个 tag）
          // ⇒ 不用先建标签再关联，也就不用再改一次。
          const existingId = title ? g.topicIds[ti] : null
          if (existingId) {
            // 有占位 ⇒ **更新它**（标题早就在那儿了，只补正文和标签）
            await updateTopic(existingId, { content, tags }, { refresh: false })
          } else {
            // 模型自己定的标题 ⇒ **新建**
            await createTopic({ title: p.title, content, categoryId: cid, typeId, tags }, { refresh: false })
          }
          written.push(p.title)
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
    results.value.push({ dir: g.dirName, count: n, error: errText })
    progress.value = { done, total: totalPoints, points: written.length }
  }
  running.value = false
  stopped.value = controller?.signal.aborted
  // 整批结束统一刷一次知识列表（写的时候没刷）
  await ensureTopicIndexLoaded(true)
  // **只加"写了多少"，不覆盖别的**（pack 模式已经填了目录数和知识点数）
  result.value = { ...result.value, written: progress.value.points }
  // pack 模式写完 ⇒ 到完成页（正文那一步是第 2 步）；points 模式写完 ⇒ 也到完成页
  step.value = props.mode === 'pack' ? 3 : 1
  emit('done')
}

/**
 * 标题 → 知识点 id。**只收标题不重名的那些**。
 *
 * <p>**重名为什么指不出来**：同一棵子树里两个知识点同名 ⇒ 不知道正文里那个 `[[X]]`
 * 指的是哪一个 ⇒ 退成普通文字（宁可少一个链接，也不要一个点进去是空的）。
 *
 * <p>**为什么一次建好**：正文是**另一次**生成的，生成时那些知识点都已经在库里了
 * ⇒ 映射表不用等整批结束再算，**落库前**就能把 `[[标题]]` 换成真链接
 * （以前是整批结束再回填一遍：中断就在库里留一堆方括号）。
 *
 * @param {Array<{id:number, title:string}>} topics 这棵子树里的知识点
 * @returns {Map<string, number>} 标题 → id
 */
function titleIdMap(topics) {
  const byTitle = new Map()
  for (const t of topics || []) {
    const title = String(t.title || '').trim()
    if (!title) continue
    // 第二次出现同名 ⇒ 把 id 抹掉（标成"指不出来"）
    if (byTitle.has(title)) byTitle.set(title, null)
    else byTitle.set(title, t.id)
  }
  const out = new Map()
  for (const [title, id] of byTitle) if (id != null) out.set(title, id)
  return out
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
        signal: controller.signal,
        ...callOpts()
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
      signal: controller.signal,
      ...callOpts()
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
 * @param {object} scope 这棵子树的上下文
 */
function materialFor(group, written, scope) {
  return buildMaterial({
    topic: topic.value,
    // **补充说明**跟着每一条正文走：它是这一批的总要求
    // （面向开发 / 面向考试……），模型写每一条时都要看到。
    extra: extra.value.trim(),
    // **生成偏好**：系统级的默认口味，一直生效（跟「补充说明」的区别就是"这次"和"一直"）。
    pref: genPref.value.trim(),
    dirPath: group.dirName,
    titles: group.titles,
    siblings: group.siblings || [],
    written,
    // **这个目录现有的子目录**（按目录各给各的 ⇒ 别把该属于子目录的内容写在这里）
    kids: group.kids || [],
    // ⚠️ **不给骨架节点**（`node: null`）：正文是**另一次**生成的，骨架那会儿已经不在内存里。
    // ⇒ 总览知识点拿不到"下级章节"那份材料 ⇒ 它写不出索引（**已知不足**，记在 FEATURES）。
    node: null,
    linkOf: null,
    // **这棵子树里的全部知识点**：正文里提到哪一个可以写成 [[标题]]，
    // 落库前由 `titleIdMap` 的映射换成链接（标题都已经在库里了 ⇒ 换得了）。
    // **不做关联时不给这份清单** ⇒ 模型不知道哪些名字可标 ⇒ 不会写标记。
    allPoints: withLinks.value ? scope.allPoints : []
  })
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
/* 一个带标题的输入块。**标题用 label 而不是 placeholder**——
   placeholder 一输入就没了，用户不知道那格是干什么的。 */
.field {
  margin-bottom: var(--sp-12);
}
.field__label {
  display: block;
  margin-bottom: var(--sp-6);
  font-size: var(--text-sm);
  color: var(--cc-text-2);
}
/* 下拉跟上面那两个输入框一样宽，别一格宽一格窄 */
.field__select {
  width: 100%;
}
/* 底部一排：左边上一步，右边这次生成的参数 + 主按钮 */
.foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-12);
}
.foot__side {
  display: flex;
  align-items: center;
  gap: var(--sp-10);
}
.foot__side--right {
  margin-left: auto;
}
/* 模型下拉给个固定宽度：名字长短不一，不限宽的话整排会跟着跳 */
.foot__model {
  width: 180px;
}
.sum {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-9);
  font-size: var(--text-sm);
  color: var(--cc-text-2);
}
/* 正文那步的两个开关：装在一个盒子里 ⇒ 一眼看出"这一步要选什么"，
   不像散在下面的表单那样不知道是干什么的。
   勾选用 el-checkbox 的**默认插槽**：名字和说明都在 label 里 ⇒ 点文字也能勾上。 */
.opts {
  border: 1px solid var(--cc-border-strong);
  border-radius: var(--radius-sm);
  background: var(--cc-surface-2);
  padding: var(--sp-12) var(--sp-13);
}
.opts__sum {
  margin: 0 0 var(--sp-10);
  font-size: var(--text-base);
  color: var(--cc-text-1);
}
.opts__row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-10);
}
/* 每个开关一张小卡片：勾选框 + 名字（第一行）+ 一句说明（第二行）。
   **说明另起一行**，不是跟在名字后面 —— 弹窗就 640px 宽，一行放不下会顶出卡片。 */
.opt {
  flex: 1;
  min-width: 240px;
  height: auto;
  margin: 0;
  padding: var(--sp-9) var(--sp-11);
  border: 1px solid var(--cc-border-strong);
  border-radius: var(--radius-sm);
  background: var(--cc-surface);
  align-items: flex-start;
}
.opt :deep(.el-checkbox__label) {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  padding-left: var(--sp-8);
  line-height: 1.5;
}
.opt__name {
  font-size: var(--text-md);
  color: var(--cc-text-1);
}
.opt__hint {
  font-size: var(--text-sm);
  color: var(--cc-text-3);
}
/* 完成页：一个勾 + 一句话。**不用文字说"完成了"** */
.donebox {
  display: flex;
  align-items: center;
  gap: var(--sp-10);
}
.donebox__icon {
  font-size: 20px;
  color: var(--cc-success);
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
