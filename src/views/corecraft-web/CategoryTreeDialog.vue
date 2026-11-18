<template>
  <el-dialog
    :model-value="modelValue"
    :title="dialogTitle"
    width="var(--cc-dialog-w-lg)"
    :close-on-click-modal="false"
    @update:model-value="close"
  >
    <!-- ⚠️ **步骤指示条已删**（2026-11-15 用户）：生成搬后台后，「从第 1 步走到第 3 步」
         的旅程不存在（第 1 步转后台即关、确认完第 3 步也后台跑）——指示一段不存在的
         路程只会让人困惑「什么时候能点后面的」。标题跟着**入口的动作名**走
         （ui-style §1：标题 = 动作名）：任务卡「确认」→ 确认目录；侧栏「生成主题」→
         生成主题；目录「⋯ → 写知识点」→ 写知识点。 -->

    <!-- 第 1 步：来源 -->
    <section v-if="at.theme" class="pane">
      <div class="tabs" role="tablist">
        <button
          type="button"
          class="tabs__item"
          :class="{ 'is-on': source === 'ai' }"
          @click="source = 'ai'"
        >
          AI 生成
        </button>
        <button
          type="button"
          class="tabs__item"
          :class="{ 'is-on': source === 'paste' }"
          @click="source = 'paste'"
        >
          粘贴
        </button>
      </div>

      <template v-if="source === 'ai'">
        <div class="field">
          <label class="field__label">主题名称</label>
          <!-- 示例**不带技术栈的味道**：这是个通用学习平台，
               示例给成「MySQL」会让人以为它只服务技术类主题。 -->
          <el-input v-model="topic" placeholder="如：中国近代史" @keyup.enter="generateOutline" />
        </div>
        <div class="field">
          <label class="field__label">补充说明</label>
          <!-- 这一格是"面向谁、要多深、什么场景"的整体限定。
               **层数不在这里暗示**：层数是内容长出来的结果，不是输入项 ——
               写"最多 4 层"会让模型把该分的内容硬压回一层，得不偿失。 -->
          <el-input
            v-model="extra"
            type="textarea"
            :rows="3"
            placeholder="如：面向初学者"
          />
        </div>
      </template>

      <!-- 只留一个框：**没有标题、没有格式说明**。
           框里的示例本身就是格式说明（照着它写就对了），再补一段"我们认哪几种写法"
           等于让用户先读一遍文档再动手 —— 解析器什么都认，用户不需要知道。 -->
      <template v-else>
        <el-input
          v-model="text"
          type="textarea"
          :rows="10"
          placeholder="# 中国近代史
## 鸦片战争
- 背景与导火索
- 结果与影响
## 洋务运动
- 代表人物与主张"
        />
      </template>
    </section>

    <!-- 第 2 步：规划。**目录 = 有下级的节点，知识点 = 叶子**，两件事都在这棵树里 -->
    <section v-else-if="at.skeleton" class="pane">
      <div class="sum">
        <span>{{ plan.dirCount }} 个目录</span>
        <span v-if="plan.pointCount">· {{ plan.pointCount }} 条知识点</span>
      </div>
        <p v-if="plan.tooLong.length" class="warn">{{ plan.tooLong.length }} 个目录名超过 {{ MAX_NAME }} 字</p>
        <p v-if="plan.tooMany" class="warn">一次最多创建 {{ MAX_NODES }} 个目录，这批有 {{ plan.tooMany }} 个</p>
        <p v-if="planIssue" class="warn">{{ planIssue }}</p>
        <p v-if="plan.orphan.length" class="pane__hint">{{ plan.orphan.length }} 个知识点没有目录可挂</p>

        <!-- 建结构：逐条落库（目录 + 知识点占位），**不给取消**（写了一半会留脏数据）。
             进度条放树外面：树此刻是只读淡出的，进度得看得见。
             ⚠️ **只画在本地建结构那一路**（planTask 为空）：后台任务交回来的树走服务端建，
             弹窗只是等一拍——画进度就是「正在创建结构 0 / 0」的空转（2026-11-15 用户点名删）。 -->
        <div v-if="building && !planTask" class="prog">
          <i :style="{ width: buildPercent }" />
          <span class="prog__text">正在创建结构 {{ buildProgress.done }} / {{ buildProgress.total }}</span>
        </div>

        <div class="tools">
          <!-- 用 default 尺寸（14px）：size="small" 是 12px，和旁边的名字输入框
               差一档，看着像"另一套界面"。 -->
          <el-button text :disabled="building" @click="setAllChecked(true)">全选</el-button>
          <el-button text :disabled="building" @click="setAllChecked(false)">全不选</el-button>
        </div>

        <div class="tree" :class="{ 'is-busy': building }">
          <div v-for="r in rows" :key="r.key" class="row-item">
            <div class="row" :style="{ paddingLeft: (r.depth - 1) * 16 + 'px' }">
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
            <!-- 知识点（叶子）多一行「定位」：讲什么、讲到什么程度。可改可留空，
                 只进材料不落库。**不再写一句说明这段是什么** —— 输入框自己的
                 placeholder 已经说清了，正文里再解释一遍是重复。 -->
            <div
              v-if="r.node.leaf"
              class="row__intent"
              :style="{ paddingLeft: (r.depth - 1) * 16 + ROW_GUTTER + 'px' }"
            >
              <el-input
                v-model="r.node.intent"
                size="small"
                placeholder="这条讲什么、讲到什么程度（可留空）"
              />
            </div>
          </div>
        </div>
    </section>

    <!-- 知识点那一步：pack 提交「刚建好的这一批」，points 提交「一个现成目录」 -->
    <section v-else-if="at.points" class="pane">
      <div v-if="mode === 'pack'" class="opts">
        <p class="opts__sum">{{ result.points }} 条知识点</p>
        <p class="pane__hint">提交后后台逐条写，关页面不影响；进度在「任务」里看</p>
      </div>
      <p v-else class="done">给「{{ category?.name }}」写知识点</p>
    </section>

    <!-- 底部一排：**左边是上一步，右边是这次生成的参数 + 主按钮**。
         取消按钮**没有**：右上角那个 × 就是关掉，底部再摆一个"取消"是多余的。 -->
    <template #footer>
      <div class="foot">
        <div class="foot__side">
          <el-button v-if="at.skeleton" @click="step = 0">上一步</el-button>
        </div>
        <div class="foot__side foot__side--right">
          <!-- **模型和思考，每一步都在**：规划与正文各按当时的设置走（任务会把它们存进参数快照）。 -->
          <el-select v-model="pickEndpointId" class="foot__model" placeholder="模型">
            <el-option
              v-for="e in aiEndpoints"
              :key="e.id"
              :label="e.name || e.model"
              :value="e.id"
              :disabled="!e.ready"
            />
          </el-select>
          <!-- **思考开关删了**（2026-11-12）：思考是厂商参数，且"开"的写法就是"不发"，
               放在界面上对一半模型无效。改成**按环节固定策略**（`AiStagePolicy`）：
               规划关思考、正文开思考，由服务端决定。 -->
          <el-button
            v-if="at.theme && source === 'ai'"
            type="primary"
            :loading="generating"
            :disabled="!topic.trim()"
            @click="generateOutline"
          >
            生成规划
          </el-button>
          <el-button v-else-if="at.theme" type="primary" :disabled="!text.trim()" @click="parseOutline">
            解析
          </el-button>
          <!-- 第 1 步主按钮分两条路（2026-11-13）：
               · **后台任务交回来的树**（任务中心「确认」）⇒「确认并生成」：树交给服务端，
                 建目录 / 写正文是任务链上的节点，一口气跑 —— 暂停 / 恢复都不会重建目录。
               · 本地树（解析 / 粘贴）⇒ 老路「下一步」：本地建结构，进正文那一步挑范围。 -->
          <el-button
            v-if="at.skeleton"
            type="primary"
            :loading="building"
            :disabled="!canBuild"
            @click="confirmStep"
          >
            {{ building ? (planTask ? '确认中…' : '创建中…') : planTask ? '确认并生成' : '下一步' }}
          </el-button>
          <el-button v-if="at.points" type="primary" :loading="submitting" @click="startContent">
            开始生成
          </el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
/**
 * 生成主题：**主题 → 规划 → 提交后台逐条写正文**。
 *
 * ## 为什么分两段（先规划、后正文）
 *
 * 一次调用出不了几十篇正文（那是几万 token 的输出，硬要它一次吐，它会重复、
 * 写空、越写越水）。所以：**先出规划**（目录层级 + 每个知识点的标题与一句定位），
 * **再对着标题逐条写**。标题先定下来，写正文时才不会跑题、不会重复。
 *
 * ## 这棵树同时是两份清单
 *
 * - **目录 = 有下级的节点**（要建出来）
 * - **知识点 = 叶子**（要写正文，挂在它直接的那个目录下）
 *
 * ## 两种模式
 *
 * - `pack`（侧边栏「生成主题」）：主题 → 规划（带定位、可改）→ 建结构 → **提交任务**。
 *   建结构时落「目录 + 知识点占位」（标题有、正文空），然后把这个目录下的
 *   **每一条待写知识点**打包成任务步骤交给服务端 ⇒ 弹窗关掉也继续写。
 * - `points`（目录「⋯ → 写知识点」）：目录是现成的，直接给它写。
 *   材料里带上**这个目录现有的子目录**，模型就知道哪些领域已经有人管了。
 *
 * ## 编排位置（2026-11-10 改过一次）
 *
 * 目录走 `POST /kp/category/import`（一个事务建完整棵树，返回这批目录的 id）；
 * 知识点占位走 `POST /kp/topic`。**材料在这里算好**（`buildMaterial` 唯一一份定义），
 * 但**执行在服务端**（`/kp/gen-task`）：前端只负责"算材料 + 提交 + 关窗"。
 * 老的前端本地循环（关页面就断、空正文没人管）已整条删除。
 *
 * ## 任务模式不做"生成时关联"
 *
 * 正文里提到别的知识点时，**不在生成时换成链接**：改为阅读页按标题**现算**
 * （`components/kbAutoLink.js`）—— 改标题、移动目录、后建的条目都能自动跟上。
 * 服务端落库前会把模型写的 `[[标题]]` 剥成纯文字，正好由自动链接接上。
 */
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowRight } from '@element-plus/icons-vue'
import { aiEndpoints, currentEndpointId, ensureAiConfigLoaded } from './useAi'
import {
  importCategories,
  createTopic,
  refreshTopicIndexes,
  getCategories,
  getChildCategories,
  categoryNameChain,
  getCategorySubtreeIds,
  fetchTopicIndexFresh
} from './useCorecraftWebStore'
import {
  parseOutlineTree,
  markOutline,
  importPlan,

  allNodes,
  indexByParentName,
  walkTree,
  MAX_NAME,
  MAX_NODES
} from '../../utils/outlineTree'
import { toggleInSet } from '../../utils/categoryTree'
import { buildMaterial } from '../../utils/knowledgePoints'
import { percentText } from '../../utils/progress'
import { getMyConfig } from '../../api/config'
import { createGenTask, continueGenTask } from '../../api/genTask'
import { genTaskCreated } from './useGenTasks'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** pack = 生成一整套（主题 → 骨架 → 知识点）；points = 给一个现成目录写知识点 */
  mode: { type: String, default: 'pack' },
  /** points 模式的目标目录 */
  category: { type: Object, default: null },
  /**
   * 后台目录任务（任务中心「继续编辑」交过来的）+ 它生成好的树。
   *
   * <p>有这两个 ⇒ 打开就直接进第 2 步：树已经在库里了，**不再调一次模型**。
   * 没有 ⇒ 走"点生成规划 → 建任务 → 转后台"。
   */
  planTask: { type: Object, default: null },
  planOutline: { type: String, default: '' }
})
// ⚠️ **`openTasks` 已删**（2026-11-12）：那个事件没有任何监听方，
// 是个"发出去就消失"的空事件 ⇒ 任务列表刷不出来的原因之一。
// 现在统一走 `genTaskCreated()`（见 useGenTasks）。
const emit = defineEmits(['update:modelValue', 'done'])

const step = ref(0)

/** 弹窗标题 = 入口的动作名（2026-11-15 用户定，取代「生成主题」一统到底）。 */
const dialogTitle = computed(() => {
  if (props.mode === 'points') return '写知识点'
  if (props.planTask) return '确认目录'
  return '生成主题'
})
const source = ref('ai')
/**
 * 新版：每个知识点的「定位」（规划阶段产出，**只进材料、不落库**）。
 *
 * key = `目录全路径|标题` —— 同名知识点可能出现在不同目录里，只用标题当键会串。
 * 落库要加列，这一版先不动后端（对比完再定）。
 */
const intentMap = ref(new Map())
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
const text = ref('')
/**
 * 名字输入框相对行首的左边距（px）。
 *
 * <p>「定位」那行要跟**上一行的名字**左对齐，所以得自己算缩进 —— 值 = 折叠按钮 24
 * + 勾选框 22 + 间距 4。写成常量是因为以前这里硬编码 44，实测差 6px（定位框整排
 * 比名字往左探出去一截），而这种 6px 在截图里不逐像素量根本看不出来。
 */
const ROW_GUTTER = 50

const generating = ref(false)
const building = ref(false)
/**
 * 第 2 步顶部的提醒（现在只有一种来源：规划被截断、用的是半棵树）。
 *
 * <p>空串 = 没话要说。**不弹 toast**：这句话要一直挂在树上陪着用户删改，
 * 而不是三秒后消失、留下半棵树让人自己猜为什么少了几个模块。
 */
const planIssue = ref('')
/** 建结构的进度（逐条 createTopic）—— 树那一刻是只读淡出的，进度得另外显示 */
const buildProgress = ref({ done: 0, total: 0 })
const buildPercent = computed(() => {
  const { done, total } = buildProgress.value
  return percentText(done, total)
})
/** 提交任务中（防连点提交两次） */
const submitting = ref(false)
/**
 * **这次用哪个模型**（用户注册过的 API）。
 *
 * <p>**不写进全局选择**：全局那一份是"我平时用哪个"，这里是"这一批用哪个"——
 * 临时挑一个跑完，不该把用户平时的选择改掉。
 */
const pickEndpointId = ref(null)
/**
 * ⚠️ **「开启思考」开关已删**（2026-11-12）。
 *
 * <p>三条理由：① 它是厂商参数，对一半模型无效（"开"的写法就是"不发"）；
 * ② 它压过后端策略，策略配了也白配；③ 用户拍板"能开就开、别给我开关"。
 * 现在由 {@code AiStagePolicy} 按环节定：规划关思考、正文开思考。
 */
/** 解析 + 标记之后的树 */
const marked = ref([])
const collapsed = ref(new Set())
const result = ref({ dirs: 0, points: 0 })
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
    return { theme: false, skeleton: false, points: step.value === 0 }
  }
  // pack 模式：主题(0) → 规划(1) → 知识点(2)。
  // **没有"完成"这一步**：提交任务之后弹窗直接关掉，进度去侧边栏「任务」里看。
  return {
    theme: step.value === 0,
    skeleton: step.value === 1,
    points: step.value === 2
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
    // **上一批的定位不能带到这一批**：key 是「目录路径|标题」，
    // 两批如果有同名目录 + 同名知识点，残留的定位会喂进材料（且界面看不出来）。
    intentMap.value = new Map()
    // **偏好是系统级的**：读一次就够，一次生成中途不会变
    loadGenPref()
    // **默认用全局当前选中的那个模型**（AI 设置里选的那条）。
    // 端点列表是懒加载的 ⇒ 得等它到齐，才知道有没有得选。
    ensureAiConfigLoaded().then(() => {
      pickEndpointId.value =
        currentEndpointId.value ?? aiEndpoints.value.find((e) => e.ready)?.id ?? null
    })
    // **任务中心「继续编辑」交回来的树**：直接进第 2 步，不再调模型。
    // 树的文本就是任务 result 里的 outline（树形符号），解析规则仍然只有一份。
    if (props.planTask && props.planOutline) {
      topic.value = props.planTask.title || ''
      applyOutline(props.planOutline, true)
    }
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
  generating.value = false
  building.value = false
  buildProgress.value = { done: 0, total: 0 }
  step.value = 0
  source.value = 'ai'
  topic.value = ''
  extra.value = ''
  text.value = ''
  pickEndpointId.value = null
  marked.value = []
  collapsed.value = new Set()
  intentMap.value = new Map()
  planIssue.value = ''
  result.value = { dirs: 0, points: 0 }
  batch.value = null
}

function close() {
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

/**
 * 全选 / 全不选：所有节点（含知识点那些叶子）—— "全选"不选全等于没全选。
 *
 * <p>⚠️ **只有这一份实现**（原来 `checkAll` / `checkNone` 是两段一模一样的遍历，
 * 只差一个常量）⇒ 以后加"全选但跳过某某"这种规则，改一处就够。
 *
 * @param {boolean} checked 勾 / 不勾
 */
function setAllChecked(checked) {
  for (const n of allNodes(marked.value)) n.checked = checked
}

function fold(node) {
  collapsed.value = toggleInSet(collapsed.value, node)
}

/**
 * 默认只展开第一层：树再大也不铺满一屏。
 *
 * <p>节点打平用 `outlineTree` 的 `allNodes`（**只有那一份遍历**），
 * 不在这里再写一份"走一遍树"的。
 */
function collapseDefault() {
  // **只收有下级的**：折叠箭头只画给有下级的节点（模板里 `v-if="r.node.children.length"`），
  // 知识点那些叶子收不收都一样 —— 但塞进 `collapsed` 会把它们的 aria-label
  // 从「收起」翻成「展开」（那个按钮还在，只是没有箭头）。
  collapsed.value = new Set(
    allNodes(marked.value).filter((n) => n.depth >= 2 && n.children.length)
  )
}

// ---------------- 第 1 步：出骨架 ----------------

/**
 * 生成规划：**不再在弹窗里等**（2026-11-12）。
 *
 * <p>规划开思考要 3~5 分钟（实测 219~268 秒），在浏览器里等会撞超时、关页面就白跑。
 * ⇒ 改成建一个 `theme` 后台任务：服务端按「骨架 → 逐章展开 → 校验」跑，
 * 跑完停在「待你确认」，任务卡上点「继续」把树交回这个弹窗第 2 步。
 *
 * <p>⚠️ **类型是 `theme` 不是 `plan`**（v2 单任务化）：
 * `theme` 任务的节点链是「生成目录 → 确认 → 生成正文」——
 * 用户确认完目录之后，正文那一步**挂在同一个任务上**接着跑，
 * 而不是新建第二张卡（「生成主题」本来就是一件事，拆成两张卡用户看不到全貌）。
 *
 * <p>材料在这里算好带上去（主题 + 补充说明 + 现有目录清单）；
 * 展开每一章的材料由后端按骨架补 —— 每章只装这一章，上下文才聚焦。
 */
async function generateOutline() {
  if (generating.value) return
  generating.value = true
  try {
    const cats = getCategories()
    const parts = [`主题：${topic.value.trim()}`]
    // **补充说明**紧跟主题：它是"这一批要写什么"的整体限定
    // （面向开发 / 面向考试 / 只要原理……），放在目录名单前面。
    if (extra.value.trim()) parts.push(`补充说明：${extra.value.trim()}`)
    // ⚠️ **只给一级目录名，不给子树**（2026-11-15 用户第 5 条实测铁证：全量目录树当材料，
    // 主题「测试」被清单里最大的 JAVA 树带跑、生成的全是 Java 目录）。材料里 90% 是
    // 现有树 ⇒ 对模型就是"照这个写"的强引导；而这份清单的本意只是**防重名**——
    // 知道"已有哪些书"一层名单就够，子树结构对防重名零增益。
    const topNames = (cats || []).filter((c) => Number(c.parentId || 0) === 0).map((c) => c.name)
    if (topNames.length) {
      parts.push(`已有的一级目录（避免与这些重复，新树自成一体）：\n${topNames.join('、')}`)
    }

    await createGenTask({
      type: 'theme',
      title: topic.value.trim(),
      promptKey: 'ai.prompt.knowledge.point',
      params: JSON.stringify({
        endpointId: pickEndpointId.value,
        theme: topic.value.trim(),
        material: parts.join('\n\n'),
        pref: genPref.value || ''
      }),
      steps: []
    })
    // ⚠️ 文案全站统一（2026-11-15 用户第 4 条）：所有后台任务入口只说这一句
    ElMessage.success('已转到后台任务')
    // ⚠️ **走统一出口**（2026-11-12）：任务列表要**立刻**出现这张卡。
    // 以前这里发的是 `emit('openTasks')` —— 那个事件**没人监听**（AppLayout 只绑了 @done），
    // ⇒ 任务列表要刷新页面才看得到（用户原话「创建主题后任务列表不能马上看到任务」）。
    await genTaskCreated()
    close()
  } catch (e) {
    ElMessage.error(e?.message || '创建任务失败')
  } finally {
    generating.value = false
  }
}

/** 粘贴的树 → 骨架。**解析和"没认出"的判断都在 applyOutline 里**，这里不重复一遍 */
function parseOutline() {
  // 粘贴的树是用户自己写的：**不拆定位**（名字本身就可能带竖线，如「数组｜切片」）
  applyOutline(text.value, false)
}

/**
 * 把新版规划输出里「名字 ｜ 定位」的定位拆到节点上。
 *
 * <p>**只对「AI 规划」的输出拆**：粘贴的树（用户自己写的）不拆 —— 那里的名字本身
 * 就可能带竖线（「数组｜切片」），硬拆会把名字切坏。
 * <p>拆不出来就原样保留（定位为空）—— 定位是增强，不该让整棵树解析失败。
 *
 * @param {Array} nodes `parseOutlineTree` 的节点（就地修改）
 */
function splitIntents(nodes) {
  walkTree(nodes, (n) => {
    const m = String(n.name || '').match(/^(.*?)\s*[｜|]\s*(.+)$/)
    if (m && m[1].trim()) {
      n.name = m[1].trim()
      n.intent = m[2].trim()
    }
  })
}

/**
 * 原始文本 → 骨架。
 *
 * @param {string} raw 模型或用户给的 markdown 树
 */
function applyOutline(raw, splitIntent = true) {
  const forest = parseOutlineTree(raw)
  if (!forest.length) {
    ElMessage.error('没认出目录树')
    return
  }
  // AI 规划的输出行尾带「｜定位」，拆到节点上；粘贴的树不拆
  if (splitIntent) splitIntents(forest)
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
  // ⚠️ **路径按 `joinPath` 拼**（`walkTree` 里的规矩）⇒ 与 `importPlan.points[].dirPath` 同形。
  //   父的目录 id 从 `map` 里按**父路径**取（父先走过 ⇒ 已经在 map 里），
  //   顶层没有父 ⇒ 用 `mountId`。
  walkTree(dirs, (n, path, depth, parentPath) => {
    const parentId = parentPath ? map.get(parentPath) : mountId
    const id = (byParent.get(parentId) || new Map()).get(n.name)
    if (id == null) return false
    map.set(path, id)
  })
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
  return categoryNameChain(id).join('/')
}

/**
 * 这批要写的知识点（**现拉一份全集索引**再筛）。
 *
 * <p>**为什么不用共享缓存那份**（store 的 `getTopicIndex`）：
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
 * 这批的上下文：标题清单（模型标 [[标记]] 用）+ 目录链接的合法集合。
 *
 * <p>**只有一份构造器** —— 两个入口（给现成目录写 / 写刚建好的这一批）
 * 拿到的上下文形状必须一样，不然两处材料就会长得不一样。
 *
 * <p>**从库里现取，不从骨架取** —— 正文是**另一次**生成的，骨架那会儿已经不在内存里了。
 *
 * @param {Set<number>} dirIds 这批知识点可能挂着的目录 id
 *        （**含整棵子树**：给现成目录写时是 `getCategorySubtreeIds` 那份，
 *        写刚建好的这一批时是这批新目录的 id —— 规则只有一份，在 store 里）
 */
async function makeScope(dirIds) {
  const topics = await fetchScopeTopics(dirIds)
  return {
    dirIds,
    topics,
    /** 目录全路径（"MySQL/1. 架构/1.1 连接"）—— 材料里的"目录"用它，
     *  模型才知道这条属于哪一片 */
    pathOf: pathOfDir
  }
}

/**
 * 建目录 + 落知识点**占位**（title 有、content 空）。**这一步只落结构**，
 * 写正文是**下一步**的事（那一步再选要不要标签 / 要不要知识点关联）。
 *
 * <p>⚠️ **typeId 2026-11-16 起不再传**（类型轴退役，见 useCorecraftWebStore「知识点类型（退役）」）：
 * 原来这里调 `ensureLearnTypeId()` 取「学习」档 type_id 显式传给 createTopic，
 * 类型概念已下线（不再按"出生地"分档）⇒ 那个函数连同调用一起删了。
 *
 * <p>**占位为什么也要落库**：① 用户要看到的是"目录**和知识点**"，不落库就没地方存标题；
 * ② 知识点之间的跳转靠**标题 → id**，标题先在库里，链接才有目标（正文晚点写不影响）。
 * ⇒ 写正文时走的是**更新占位**（`submitAsTask` 里 `g.topicIds[ti]` 带上 topicId），不是新建。
 */
/**
 * 新版：把这批知识点的定位收成 `目录全路径|标题 → 定位`。
 *
 * <p>**为什么用「路径 + 标题」当键**：同一个标题可能出现在不同目录（"概述"到处都有），
 * 只用标题会串；只用路径又区分不了同目录的兄弟。
 *
 * <p>路径按 `importPlan` 的同一套 `joinPath` 拼 ⇒ 与写正文时 `scope.pathOf`
 * 给出来的目录链同形，能对上。
 */
function collectIntents() {
  const map = new Map()
  walkTree(marked.value, (n, path, depth, parentPath) => {
    const children = n.children || []
    if (children.length) return
    // **叶子 = 知识点，挂在「父目录」下** —— key 的前半必须是父目录路径，
    // 不是叶子自己的路径（`importPlan.points[].dirPath` 就是父目录）。
    // 写成叶子全路径会永远取不到（命中率 0，而且不报错）。
    //
    // ⚠️ **父目录路径用 `walkTree` 直接给的 `parentPath`，不要从 `path` 里切**：
    // 知识点名里带斜杠是常事（"TCP/IP"、"A/B 测试"）⇒ 按最后一个斜杠切会把
    // 父目录算成 "xxx/TCP" ⇒ key 对不上 ⇒ 这条的定位取不到（而且不报错）。
    const intent = String(n.intent || '').trim()
    if (intent && parentPath) map.set(`${parentPath}|${n.name}`, intent)
  })
  return map
}

/**
 * 建结构进度**前进一步**（每条知识点都走一次）。
 *
 * <p>⚠️ **只有这一份**：原来循环里写了两遍一模一样的
 * `buildProgress.value = { ...buildProgress.value, done: … + 1 }`
 * （一处 `continue` 前、一处循环末）⇒ 改一处漏一处 ⇒ 进度条会差 1 或卡住。
 */
function stepBuildProgress() {
  buildProgress.value = { ...buildProgress.value, done: buildProgress.value.done + 1 }
}

/**
 * 第 1 步主按钮的唯一出口：后台任务的树 ⇒ 确认并续跑；本地的树 ⇒ 老的本地建结构。
 */
function confirmStep() {
  return props.planTask?.id ? confirmAndContinue() : build()
}

/**
 * **确认这棵树并直接续跑**（2026-11-13）：树原样交给服务端，
 * 建目录、建占位、写正文都是任务链上的节点（有步骤、有耗时）。
 *
 * <p>⇒ **暂停 / 恢复不会再重建目录**。老做法目录建在前端：确认完没提交就关弹窗，
 * 任务还停在「待确认」，下次点确认又建一遍（用户实测「重复了都」）。
 *
 * <p>⚠️ 材料在这里算好（复用 `buildMaterial`）—— 后端不理解材料语义，
 * 两份定义必然漂；服务端只负责落库 + 按序执行。
 */
async function confirmAndContinue() {
  building.value = true
  try {
    const steps = stepsFromPlan()
    if (!steps.length) {
      ElMessage.error('这棵树里没有勾选的知识点')
      return
    }
    await continueGenTask(props.planTask.id, {
      // 不带 topicId：占位还没建，服务端建完目录自己解析
      steps: steps.map((s) => ({ seq: s.seq, title: s.title, dirPath: s.dirPath, material: s.material })),
      // 确认过的树原样上传 —— 服务端建目录（同层重名合并，复用 /kp/category/import 那套规则）
      nodes: plan.value.dirs,
      mountParentId: 0,
      endpointId: pickEndpointId.value
    })
    ElMessage.success('已转到后台任务')
    emit('done')
    close()
  } catch (e) {
    ElMessage.error(e?.message || '确认失败')
  } finally {
    building.value = false
  }
}

/**
 * 从**这棵树**算每条知识点的材料（不落库、不带 topicId —— 占位由服务端建）。
 *
 * <p>口径与 `submitAsTask` 完全一致：同目录的互为 siblings、子目录名进 kids、
 * 最近 30 条进 written、「定位」进 intent —— 材料只有前端这一份，换个地方算就会漂。
 */
function stepsFromPlan() {
  // 路径 → 节点（要拿某个目录的子目录名给 kids）
  const nodeByPath = new Map()
  walkTree(plan.value.dirs, (n, path) => {
    nodeByPath.set(path, n)
  })
  // 目录 → 同目录的全部标题（siblings 用）
  const siblingsByDir = new Map()
  for (const p of plan.value.points) {
    const list = siblingsByDir.get(p.dirPath) || []
    list.push(p.title)
    siblingsByDir.set(p.dirPath, list)
  }
  const steps = []
  let seq = 0
  for (const p of plan.value.points) {
    const kids = (nodeByPath.get(p.dirPath)?.children || []).map((c) => c.name)
    seq += 1
    steps.push({
      seq,
      title: p.title,
      dirPath: p.dirPath,
      material: buildMaterial({
        topic: topic.value,
        extra: extra.value.trim(),
        pref: genPref.value.trim(),
        dirPath: p.dirPath,
        titles: [p.title],
        siblings: (siblingsByDir.get(p.dirPath) || []).filter((t) => t !== p.title),
        written: steps.slice(-30).map((x) => x.title),
        kids,
        intent: intentMap.value.get(`${p.dirPath}|${p.title}`) || ''
      })
    })
  }
  return steps
}

async function build() {
  building.value = true
  try {
    const rows = await importCategories(plan.value.dirs, 0)
    const ids = resolveDirIds(plan.value.dirs, rows, 0)
    // 进度按**知识点条数**算（目录是批量 import 的，一次调用；知识点才是逐条）
    const total = plan.value.points.length
    buildProgress.value = { done: 0, total }
    let n = 0
    for (const p of plan.value.points) {
      const cid = ids.get(p.dirPath)
      if (cid == null) {
        // 找不到目录 ⇒ 这条建不了（但**算它试过了**，进度条要走完，不能卡住）
        stepBuildProgress()
        continue
      }
      // **content 传空串**：占位就是"还没写正文"。
      // （`updateTopic` 把 null 当"不修改"⇒ 只能写 ''；库里本来就有一条空正文的老数据，约定一致。）
      await createTopic({ title: p.title, content: '', categoryId: cid }, { refresh: false })
      n += 1
      stepBuildProgress()
    }
    // **两份索引都刷**（搜索索引 + 全集索引）——
    // ⚠️ **别用裸的 `ensureTopicIndexLoaded(true)`**（2026-11-09 用户报
    // 「生成主题后目录树计数全是 0，必须刷新才有」）：树的行尾数字读的是
    // `fullTopicIndex`（"完整的书"那份），只刷搜索索引 ⇒ 树上每个章/节都是 0，
    // **界面上什么异常都看不出来**。
    await refreshTopicIndexes()
    result.value = { dirs: plan.value.dirCount, points: n }
    // **上下文从库里现取**（结构刚落库）⇒ 标题清单、目录链接集合都是最新的。
    // ⚠️ 这一步**现拉一份索引**，不拿 store 的共享缓存：那份可能停在旧快照
    // （2026-10-28 实测：少算 74 条 ⇒ 界面显示"完成"，实际有一半没写）。
    // 范围 = **这批刚建出来的目录**（骨架路径 → 目录 id 的映射）——
    // 骨架可能有**多棵根**（粘贴路径能粘多棵）⇒ 把这批目录的 id 全收进来。
    batch.value = await makeScope(new Set(ids.values()))
    // 把这批的定位收进内存（写正文时进材料，**不落库**）
    intentMap.value = collectIntents()
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
 * 「正文」那一步的「开始生成」：**把这一批打包成任务提交给服务端**。
 *
 * <p>提交完就关弹窗 —— 之后关页面 / 断网 / 睡眠都不影响，进度去侧边栏「任务」看。
 * （老的前端本地循环已整条删除：那套把浏览器标签页当运行器，关掉就断，
 * 留下一堆"有目录没正文"的空条目。）
 */
async function startContent() {
  if (submitting.value) return
  await submitAsTask()
}

/**
 * 新版：把这一批**打包成任务提交给服务端**。
 *
 * <p>材料在这里算好（复用 `buildMaterial`），后端只按序调用 + 落库 + 记进度 ——
 * 所以任务中心才能通用（出卷、判分是同一个形状），后端也不必把材料拼装重写一份。
 *
 * <p>⚠️ **任务模式不做「知识点关联」**：老链路是模型写 `[[标题]]`、落库前换成 id，
 * 服务端执行器不认这个标记（认了等于把一条前端格式规则搬进后端）。
 * 关联的正确形态是**渲染层自动链接**（设计文档里的目标态），在那之前先不给名单。
 */
async function submitAsTask() {
  const scope = props.mode === 'pack'
    ? batch.value
    : await makeScope(getCategorySubtreeIds(props.category?.id))
  if (!scope) return
  const pending = scope.topics.filter((t) => !t.hasContent)
  if (!pending.length) {
    ElMessage.error('没有要写的知识点')
    return
  }

  const steps = []
  let seq = 0
  for (const g of groupByCategory(pending, scope)) {
    for (let ti = 0; ti < g.titles.length; ti++) {
      const title = g.titles[ti]
      const material = buildMaterial({
        topic: topic.value,
        extra: extra.value.trim(),
        pref: genPref.value.trim(),
        dirPath: g.dirName,
        titles: [title],
        siblings: g.titles.filter((t) => t !== title),
        // 「前面写了什么」只带最近 30 条：几百条全带上会把材料撑成一大段标题清单，
        // 而防重复只需要"附近"的上下文。
        written: steps.slice(-30).map((s) => s.title),
        kids: g.kids || [],
        intent: intentMap.value.get(`${g.dirName}|${title}`) || ''
      })
      seq += 1
      steps.push({ seq, title, topicId: g.topicIds[ti], material })
    }
  }

  submitting.value = true
  try {
    const buildSum = `创建目录 ${result.value.dirs} 个 · 知识点 ${result.value.points} 个`
    // 本地树（粘贴模式 / 目录页「写知识点」）：后台任务走单任务 `content`。
    await createGenTask({
      type: 'content',
      title: `${topic.value.trim() || marked.value[0]?.name || props.category?.name || '知识'} · ${steps.length} 条`,
      promptKey: 'ai.prompt.knowledge.point',
      params: JSON.stringify({
        endpointId: pickEndpointId.value,
        buildSummary: buildSum
      }),
      steps
    })
    ElMessage.success('已转到后台任务')
    // ⚠️ **任务列表立刻出现这张卡**（走统一出口，不靠父组件碰巧绑了 @done）。
    // `emit('done')` 那一路是给**目录树 / 知识点列表**刷新的（壳里那个回调管），
    // 它不保证刷任务列表 ⇒ 这里自己调一次统一出口。
    await genTaskCreated()
    emit('done')
    close()
  } catch (e) {
    ElMessage.error(e?.message || '提交失败')
  } finally {
    submitting.value = false
  }
}

</script>

<style scoped>
.pane {
  min-height: 180px;
}

/* 来源切换：**一个轨道 + 一个选中块**（分段控件），不是两个独立按钮 ——
   两个按钮读起来像"两个动作"，而这里是一次二选一。 */
.tabs {
  display: inline-flex;
  padding: 2px;
  margin-bottom: var(--sp-13);
  border-radius: var(--radius-sm);
  background: var(--cc-surface-2);
}
.tabs__item {
  border: none;
  background: transparent;
  padding: var(--sp-4) var(--sp-11);
  border-radius: calc(var(--radius-sm) - 2px);
  color: var(--cc-text-3);
  font-size: var(--text-md);
  line-height: 20px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
.tabs__item:hover {
  color: var(--cc-text-2);
}
.tabs__item.is-on {
  background: var(--cc-surface);
  color: var(--cc-text-1);
  font-weight: 500;
  box-shadow: var(--cc-shadow-1);
}

/* 一个带标题的输入块。**标题用 label 而不是 placeholder**——
   placeholder 一输入就没了，用户不知道那格是干什么的。 */
.field {
  margin-bottom: var(--sp-13);
}
.field:last-child {
  margin-bottom: 0;
}
.field__label {
  display: block;
  margin-bottom: var(--sp-8);
  font-size: var(--text-xs);
  line-height: 16px;
  color: var(--cc-text-3);
}

/* 底部动作行和内容之间拉一条浅分隔：不然"上一步 / 模型 / 按钮"飘在空白里，
   看起来像忘了排版。
   ⚠️ 分隔线挂在 `.foot` 上而不是 `:deep(.el-dialog__footer)`：后者隔着 teleport
   匹配不到（实测 border 仍是 0px），而 `.foot` 是本次模板里的节点，scoped 直接命中。 */
.foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-12);
  margin-top: calc(-1 * var(--sp-6));
  padding-top: var(--sp-12);
  border-top: 1px solid var(--cc-border-strong);
}
.foot__side {
  display: flex;
  align-items: center;
  gap: var(--sp-9);
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
  font-size: var(--text-xs);
  color: var(--cc-text-2);
}
/* 知识点那步的两个开关：装在一个盒子里 ⇒ 一眼看出"这一步要选什么"，
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
.warn {
  margin: var(--sp-6) 0 0;
  font-size: var(--text-xs);
  color: var(--cc-danger);
}
/* 说明文字。**故意不叫 .hint**：admin-page.css 里有一份全局 .hint（给 teleport 到
   body 的弹窗用，.text-xs + line-height 1.6），同名两份、值还不一样 ⇒
   叠在一起谁生效看权重。这里用自己的名字，跟全局那份彻底分开。 */
.pane__hint {
  margin: var(--sp-6) 0 0;
  font-size: var(--text-xs);
  line-height: 1.6;
  color: var(--cc-text-3);
}
.tools {
  margin-top: var(--sp-10);
}
/* 建结构的细进度条：树那一刻只读淡出，进度必须放在树外面看得见 */
.prog {
  position: relative;
  margin-top: var(--sp-10);
  height: 20px;
  border-radius: 99px;
  background: var(--cc-surface-2);
  overflow: hidden;
}
.prog i {
  position: absolute;
  inset: 0 auto 0 0;
  background: var(--cc-primary-soft);
  transition: width 0.25s ease;
}
.prog__text {
  position: relative;
  display: block;
  padding-left: var(--sp-9);
  font-size: var(--text-xs);
  line-height: 20px;
  color: var(--cc-text-2);
  font-variant-numeric: tabular-nums;
}
.tree {
  margin-top: var(--sp-6);
  max-height: 300px;
  overflow-y: auto;
}
/* 建结构期间：**只读不消失**。让用户看得见"我正在建的就是这棵树"，
   但别让他一边建一边改（改了也不会被这次采用）。 */
.tree.is-busy {
  pointer-events: none;
  opacity: 0.5;
}

.row {
  display: flex;
  align-items: center;
  gap: var(--sp-6);
  height: 28px;
  font-size: var(--text-md);
  color: var(--cc-text-1);
}
/* 新版：知识点下面那行「定位」输入。缩进由内联 style 给，这里只管视觉重量 */
.row__intent {
  padding-bottom: var(--sp-4);
}
.row__intent :deep(.el-input__wrapper) {
  box-shadow: none;
  background: var(--cc-surface-2);
}
.row__intent :deep(.el-input__inner) {
  font-size: var(--text-md);
  color: var(--cc-text-2);
}
.row__fold {
  /* 命中区 24px（图标 14px 继承自 .row）：可点区域跟图标一样大就难点了；行高 28px 不变 */
  width: 24px;
  height: 24px;
  flex: 0 0 24px;
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
  font-size: var(--text-md);
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
.done {
  margin: 0;
  font-size: var(--text-base);
  color: var(--cc-text-1);
}

/* 主按钮统一最小宽度：三个步骤的文案长短不同，宽度别跟着跳 */
.foot :deep(.el-button--primary) {
  min-width: 92px;
}
</style>
