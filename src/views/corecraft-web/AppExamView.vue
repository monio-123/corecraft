<template>
  <div class="exam">
    <!-- ==================== 顶栏 ==================== -->
    <header class="exam__top">
      <div class="exam__top-inner">
        <div class="exam__id">
          <span class="exam__tag">考试</span>
          <h1 class="exam__topic">{{ scopeTitle || '考试' }}</h1>
        </div>

        <div class="exam__ops">
          <!-- 答题与判分的过程、分数环都在答题器里（**这一块只有页面级的按钮**）：
               答完了才给"重做本卷 / 重新生成 / 回到目录"。
               重做 = 同卷新 attempt（原记录不动，设计 §10.2.1）；重新生成 = 新卷。 -->
          <template v-if="phase === 'running' && runnerPhase === 'done'">
            <el-button @click="redo">重做本卷</el-button>
            <el-button @click="backToSettings">重新生成</el-button>
            <el-button type="primary" @click="backToTopic">
              {{ categoryId ? '回到目录' : '回到列表' }}
            </el-button>
          </template>
        </div>
      </div>
    </header>

    <!-- ==================== 主体 ==================== -->
    <main class="exam__body">
      <!-- ========== 考试设置 ========== -->
      <section v-if="phase === 'idle'" class="setup">
        <header class="setup__head">
          <h2 class="setup__title">考试设置</h2>
          <span class="setup__scope">{{ scopeText }}</span>
        </header>
        <!-- 有作答还没交的旧考试：只提示不自动恢复（目录考试的 scope 是动态筛选集合，
             上次那批和这次这批对不上；去考试记录里续，TODO-exam-lifecycle §五） -->
        <div v-if="unfinishedCount" class="setup__unfinished">
          有 {{ unfinishedCount }} 场未交卷的考试
          <el-button text type="primary" size="small" @click="router.push({ name: 'AppExamRecords' })">
            考试记录
          </el-button>
        </div>

        <div class="setup__grid">
          <!-- 左：题型、道数、分值 -->
          <div class="setup__col">
            <div class="setup__label">题型 · 道数 · 分值</div>
            <div v-for="(s, i) in sections" :key="i" class="setup__row">
              <el-select v-model="s.type" size="small" class="setup__type">
                <el-option v-for="o in TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
              <div class="stepper">
                <button type="button" class="stepper__btn" :disabled="s.count <= MIN_COUNT" @click="bump(i, -1)">−</button>
                <span class="stepper__num">{{ s.count }}</span>
                <button type="button" class="stepper__btn" :disabled="s.count >= MAX_COUNT" @click="bump(i, 1)">+</button>
              </div>
              <!-- 分值 = 这道题值多少分。**生成试卷时就定好**，跟着这张卷子走。 -->
              <el-input-number
                v-model="s.score"
                :min="MIN_SCORE"
                :max="MAX_SCORE"
                size="small"
                controls-position="right"
                class="setup__score"
              />
              <button type="button" class="setup__del" aria-label="去掉这一种题型" @click="removeSection(i)">×</button>
            </div>
            <button type="button" class="setup__add" @click="addSection">+ 加一种题型</button>
          </div>

          <!-- 右：难度 + 附加要求 -->
          <div class="setup__col">
            <div class="setup__field">
              <div class="setup__label">难度</div>
              <el-select v-model="levelName" size="small" class="setup__level">
                <el-option v-for="l in levels" :key="l.name" :label="l.name" :value="l.name" />
              </el-select>
              <p v-if="levelDesc" class="setup__hint">{{ levelDesc }}</p>
            </div>
            <div class="setup__field">
              <!-- 「可跳过」这个信息并进 label（§4.11(l)）：placeholder 一输入就没了，
                   而"这里不填也行"是这格的属性，不是一次性的提示。 -->
              <div class="setup__label">我自己的要求（选填）</div>
              <!-- §4.11(l)：placeholder「选填」删掉。
                   纪律：placeholder 默认不给，只**格式类**输入留示例；这格不是格式类。
                   ⚠️ **「可跳过」这个信息要留着**（它告诉用户这里不填也行），
                   但它属于**label**，不属于 placeholder ⇒ 改进 label，不留占位。 -->
              <el-input v-model="extra" type="textarea" :rows="3" />
            </div>
          </div>
        </div>

        <div class="setup__foot">
          <span class="setup__count-all">最多 {{ total }} 道 · 满分 {{ planMax }} 分</span>
          <span v-if="specWarning" class="setup__warn">{{ specWarning }}</span>
          <el-button
            type="primary"
            class="setup__go"
            :loading="phase === 'loading'"
            :disabled="!total"
            @click="generate"
          >
            生成试卷
          </el-button>
        </div>
      </section>

      <!-- ========== 出题任务在后台跑（可离开，任务中心看进度） ========== -->
      <div v-else-if="phase === 'task'" class="kb-center">
        <KbLoader size="sm" />
        <p>正在生成试卷… 可随时离开</p>
      </div>

      <!-- ========== 生成中（消费任务：解析建卷） ========== -->
      <div v-else-if="phase === 'loading'" class="kb-center">
        <KbLoader size="sm" />
        <p>正在生成试卷…</p>
      </div>

      <!-- ========== 生成失败 ========== -->
      <div v-else-if="phase === 'error'" class="kb-center">
        <p class="kb-error" :class="{ 'is-quiet': quiet }">{{ error }}</p>
        <el-button v-if="!quiet" @click="generate">重试</el-button>
      </div>

      <!-- ========== 答题 / 判分 / 结果 ==========
           这三段是**答题器**的活（与详情页抽屉里的随堂测验共用同一个组件），
           这里只负责把 attempt 递进去：初卷（刚出的）或空（刷新/重做时它自己恢复）。 -->
      <ExamRunner
        v-else
        :initial-questions="questions"
        :attempt-id="attemptId"
        :note="note"
        :plan="examPlan"
        @phase="runnerPhase = $event"
      />
    </main>
  </div>
</template>

<script setup>
/**
 * 考试模式（试卷与作答**持久化**在服务端，刷新/重进都在）。
 *
 * ## 它是什么
 *
 * 从一条（或一组）知识出发出一份卷子 → **一题一题**作答 → 交卷 → AI 判分并给逐题讲解。
 * **试卷、作答、判定都落库**：卷子和结果刷新不丢，答完还能「重做本卷」（同卷新 attempt，
 * 原记录不动，设计 §10.2.1）。
 *
 * ## 考哪一批知识：URL 说了算
 *
 * `/exam?ids=1,2,3&name=…&cat=…` —— 这次就考这几条。
 * **唯一入口是列表页筛选条上的「考试」**（范围 = 当前筛选出来的那些，未归类的不算）：
 * `ids` = 这批知识的 id，`name` = 范围的名字（目录名或「全部」），
 * `cat` = 选了目录时的目录 id（考完退回那个目录的列表页）。
 * 生成成功后 URL 追加 `paper`（试卷 id）与 `attempt`（作答 id）⇒ 刷新/分享链接都能恢复现场。
 *
 * **正文由服务端按 id 自己捞**（AiChatReq.topicIds）：前端不传正文，
 * 归属也由服务端按登录态收 —— 与这个项目"客户端不能指定把哪段文本当上下文"的老规矩一致。
 *
 * ## 考试参数：配置给默认值，用户在页面上改
 *
 * 规范模板 / 题量结构（题型·道数·分值）/ 难度档位 / 附加要求，四样都在系统配置里
 * （`ai.prompt.exam.*`），本页读它们当默认值，用户可以改完再生成。
 * **题量结构是一份数据**，同时喂「拼给模型的题量结构」和「解析器按它推题号→题型」⇒ 两边不会错位。
 * 详见 `exam.js`。
 *
 * ## 四个状态
 *
 * 考试设置 → 生成中 → 答题 → 判分中 → 看结果。
 * **判分之前一个字的对错都不显示**——边做边看答案是自测题，不是考试。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import KbLoader from '../../components/corecraft-web/KbLoader.vue'
import { ElMessage } from 'element-plus'
import { ensureAiConfigLoaded, endpointProblem, resolveEndpoint } from './useAi'
import { categoryLink } from '../../utils/categoryNav'
import { DEFAULT_SCORE } from './examParse'
import { loadExamSpec, maxScoreOf, paperTaskPayload, totalOf, typesBadNote, TYPE_OPTIONS } from './exam'
import { getPapers, replayAttempt } from '../../api/exam'
import { usePaperTask } from './usePaperTask'
import ExamRunner from './ExamRunner.vue'

defineOptions({ name: 'AppExam' })

/** 每种题型的道数范围（设置面板加减的上下限） */
const MIN_COUNT = 1
const MAX_COUNT = 20
/** 每道题的分值范围。**和道数范围是两件事**，数字碰巧一样 */
const MIN_SCORE = 1
const MAX_SCORE = 20

const route = useRoute()
const router = useRouter()

/** 这次考哪几条知识（URL 带过来） */
const topicIds = computed(() => {
  const raw = route.query.ids
  if (!raw) return []
  return String(raw)
    .split(',')
    .map((s) => Number(s.trim()))
    .filter((n) => Number.isFinite(n) && n > 0)
})

/** 范围里选中的目录（考完退回那个目录的列表页）。没选目录就是「全部」⇒ 退回列表页 */
const categoryId = computed(() => {
  const n = Number(route.query.cat)
  return Number.isFinite(n) && n > 0 ? n : null
})

/**
 * 页面阶段：**只管生成试卷这一段**。答题与判分归答题器（`ExamRunner`）管。
 * idle（考试设置）/ loading（生成中）/ error（生成失败）/ running（答题器接管）
 */
const phase = ref('idle')
const error = ref('')
/** 有作答还没交的旧考试数（设置面板那条提示；0 = 不显示） */
const unfinishedCount = ref(0)
/** 这一条是"中性结果"不是错误：「出不了题」= 内容太少，不是坏了 */
const quiet = ref(false)
/** 这次考的东西叫什么（= 目录名，URL 带过来） */
const scopeTitle = ref('')
const questions = ref([])
/**
 * 当前作答 id（URL `attempt`）——**答题器恢复现场的钥匙**。
 * 生成成功 / 重做后都写回 URL ⇒ 刷新、分享链接都能接着看。
 * 没有它 = 全新考试（答题器吃 initialQuestions 直接开答）。
 */
const attemptId = computed(() => {
  const n = Number(route.query.attempt)
  return Number.isFinite(n) && n > 0 ? n : null
})
/** 卷面和规范对不上的地方（哪几道没解析出来、哪种题型少了） */
const note = ref('')
/** 计划题量结构（出题任务的 plan）——答题器卷面弹窗的"计划 vs 实出" */
const examPlan = ref([])
/** 答题器内部的阶段（ready / grading / done）—— 顶栏要据此换按钮 */
const runnerPhase = ref('ready')

/** ========== 考试设置（默认值来自系统配置） ========== */
const sections = ref([])
/** 每种题型的**默认分值**（来自配置）。新加一种题型时用它兜底 */
const defaultScores = ref({})
/** 难度档位：`[{name, desc}]` */
const levels = ref([])
const levelName = ref('')
const extra = ref('')
/** 规范模板（配置里的那段文本） */
const paperTemplate = ref('')
/** 出题范围（配置里的那段文本）：这次考哪一块 */
const scopeRule = ref('')
/** 配置有问题时的一句话提示（比如题型没认出来） */
const specWarning = ref('')

onMounted(async () => {
  // AI 配置**这个页面自己拉**（不在业务壳里，壳那份它拿不到）
  try {
    await ensureAiConfigLoaded()
  } catch {
    // 拉不到就让 ask() 报它自己的错（开关没开 / 没选好 API），这里不重复报
  }
  try {
    const spec = await loadExamSpec()
    paperTemplate.value = spec.paper
    scopeRule.value = spec.scope
    sections.value = spec.types.sections.map((s) => ({ ...s }))
    // 每种题型的默认分值（新加题型时按它兜底）
    defaultScores.value = Object.fromEntries(spec.types.sections.map((s) => [s.type, s.score]))
    levels.value = spec.levels
    levelName.value = spec.levels[0]?.name || ''
    extra.value = spec.extra
    if (spec.types.bad) specWarning.value = typesBadNote(spec.types.bad)
  } catch (e) {
    specWarning.value = e?.message || '读考试配置失败'
  }
  // 范围的名字 = 入口那边给的（目录名 / 「全部」）。范围是**当前筛选出来的那些**。
  scopeTitle.value = String(route.query.name || '')
  if (attemptId.value) {
    // URL 带 attempt = 恢复现场（刷新 / 从链接进来）：卷子与作答由答题器按 id 自己拉，
    // 这里不重新生成。空 questions ⇒ ExamRunner 走 loadAttempt() 分支。
    questions.value = []
    phase.value = 'running'
    return
  }
  if (!topicIds.value.length) {
    error.value = '没有指定要考的知识'
    phase.value = 'error'
  }
  // 「有未交卷的考试」提示（只在出全新卷的设置面板上出现；回看/续答不打扰）。
  // 拉不到就闭嘴（接口挂了不该拦住出卷）。
  try {
    const res = await getPapers({ kind: 'exam', unfinished: true, size: 1 })
    unfinishedCount.value = res?.data?.total || 0
  } catch { /* 提示可有可无 */ }
})

// ==================== 考试设置 ====================

/**
 * 计划题数与计划满分：**都从 `sections` 算**（同一份设置、两道口径）。
 */
const total = computed(() => totalOf(sections.value))
const planMax = computed(() => maxScoreOf(sections.value))

/** 这次考的范围（顶栏已经写了范围的名字，这里只报条数） */
const scopeText = computed(() => `${topicIds.value.length} 条`)

const levelDesc = computed(() => levels.value.find((l) => l.name === levelName.value)?.desc || '')

function addSection() {
  // 补一种还没用过的题型；都用过了就补单选
  const used = new Set(sections.value.map((s) => s.type))
  const next = TYPE_OPTIONS.find((o) => !used.has(o.value)) || TYPE_OPTIONS[0]
  // 分值用配置里这一型的默认值，没有就按默认分
  sections.value.push({ type: next.value, count: MIN_COUNT, score: defaultScores.value[next.value] || DEFAULT_SCORE })
}

function removeSection(i) {
  sections.value.splice(i, 1)
}

/** 道数加减（上下限见 MIN_COUNT / MAX_COUNT） */
function bump(i, d) {
  const s = sections.value[i]
  if (!s) return
  s.count = Math.min(MAX_COUNT, Math.max(MIN_COUNT, s.count + d))
}

// ==================== 生成试卷 ====================

/**
 * 生成试卷 = **提交后台任务**（2026-11-14，与随堂测验同一条链）。
 *
 * 提示词照旧前端拼（题量结构 + 出题范围 + 模板 + 难度 + 附加要求全在材料里），
 * 出题在服务端跑——这一页只显示进度，人可以走开；任务转「待作答」时
 * 本页自动消费（人还等在这儿的话），从任务卡「作答」跳回来也走同一段。
 */
async function generate() {
  if (!topicIds.value.length || !total.value) return
  error.value = ''
  quiet.value = false
  note.value = ''
  questions.value = []
  phase.value = 'task'
  try {
    // ⚠️ **端点必须在快照里**（2026-11-15 修「还没选好 API」）：后端建任务时从快照读
    // endpointId 判端点配全没 —— 没有 ⇒ requireEndpoint(null) ⇒ 每一步都撞同一堵墙。
    const endpoint = resolveEndpoint()
    if (!endpoint) throw new Error(endpointProblem() || '还没选好 API')
    const { material, params } = paperTaskPayload({
      paper: paperTemplate.value,
      scope: scopeRule.value,
      sections: sections.value,
      level: levels.value.find((l) => l.name === levelName.value),
      extra: extra.value,
      topicIds: topicIds.value,
      // 持久化参数：试卷归到这次考的目录下，标题就用范围名（§5.1 证据来源靠 kind 分流）
      kind: 'exam',
      categoryId: categoryId.value,
      title: scopeTitle.value || '考试',
      scopeName: scopeTitle.value,
      endpointId: endpoint.id
    })
    // 提交/等待/消费/收档的骨架在 usePaperTask（与随堂抽屉共用，含全部不变量）
    await paperTask.submit({ type: 'exam', phaseKey: 'exam', title: params.title, params, material })
  } catch (e) {
    error.value = e?.message || '生成试卷失败'
    phase.value = 'error'
  }
}

/**
 * 出题任务骨架的三个落点：消费完成（含中性结果）/ 任务失败 / 任务被取消或他处消费。
 * （骨架与不变量见 `usePaperTask.js` 文件头。）
 */
const paperTask = usePaperTask({
  async onResult(r) {
    if (r.error) {
      quiet.value = r.quiet
      error.value = r.error
      phase.value = 'error'
      return
    }
    note.value = r.note
    examPlan.value = r.plan || []
    questions.value = r.questions
    // ⚠️ **先把 attempt 写回 URL，再切 running**：ExamRunner 挂载即读 attemptId，
    // router.replace 是异步的，抢在它前面切阶段会让答题器拿到 null ⇒ 卡在「恢复中」。
    await router.replace({ query: { ...route.query, paper: r.paperId, attempt: r.attemptId } })
    phase.value = 'running'
  },
  onFail(message) {
    error.value = message
    phase.value = 'error'
  },
  onVanish() {
    // 回设置态是 'idle'（模板里设置区的分支）。写成别的值会落进末尾 v-else
    // 渲染答题器，而它拿到 null attemptId 就永远停在「恢复中」。
    if (phase.value === 'task') phase.value = 'idle'
  }
})

// 任务卡「作答」跳回来（?examTask=<id>）：直接消费那个任务（那一刻任务列表可能还没拉到）
watch(
  () => route.query.examTask,
  (v) => {
    const id = Number(v)
    if (!Number.isFinite(id) || id <= 0) return
    phase.value = 'loading'
    paperTask.consume(id)
  },
  { immediate: true }
)

/**
 * 重做本卷：**同卷新 attempt**（原记录不动，设计 §10.2.1）。
 * 服务端复制一份空作答的 attempt 回来 ⇒ 换掉 URL 里的 attempt，答题器从头作答。
 */
async function redo() {
  try {
    // ⚠️ 信封解包：`request` resolve 的是 `{code, data, message}`，attemptId 在 `data` 里。
    // 直接取 `a.id` 是 undefined ⇒ URL 丢 attempt 参数 ⇒ 答题器 watch 到 null 直接 return，卡死在旧结果页。
    const newId = (await replayAttempt(attemptId.value))?.data
    if (!Number.isFinite(Number(newId)) || Number(newId) <= 0) throw new Error('重做没有拿到新作答 id')
    questions.value = []
    runnerPhase.value = 'ready'
    await router.replace({ query: { ...route.query, attempt: Number(newId) } })
  } catch (e) {
    ElMessage.error(e?.message || '重做失败')
  }
}

/** 回到考试设置（重新出一份）：URL 里的 paper/attempt 作废，别让「恢复现场」把新卷顶掉 */
function backToSettings() {
  phase.value = 'idle'
  error.value = ''
  quiet.value = false
  const query = { ...route.query }
  delete query.paper
  delete query.attempt
  router.replace({ query })
}

/**
 * 回上一页：**回这次范围的目录列表**。
 *
 * <p>范围只有目录一种（单条出题入口下线了），所以"回哪"不用再分叉：
 * 有目录 id 就回那个目录的列表页，没有就回全部列表。
 * 地址用 `categoryLink` —— 和侧边栏、详情页跳目录用的是同一个地址。
 */
function backToTopic() {
  router.push(categoryId.value ? categoryLink(categoryId.value) : '/app/learn')
}
</script>

<style scoped>
/**
 * 考试页的样式。
 *
 * **全部用现成 token**（--cc-* / --sp-* / --text-* / --radius-*）：
 * 这一页要和别的页面看着是一套的，另起一套变量就是两套真相。
 * scoped ⇒ 删掉这个组件，样式一起消失，不留残迹。
 *
 * 唯一的例外是阴影：`rgb(31 35 41 / N%)` 这套写法全站在用（base.css 里也是），
 * 没有 shadow token 可用，就沿用同一串，别自己造一个。
 */
.exam {
  min-height: 100%;
  /* ⚠️ **背景色要单独写**：只写 `background: linear-gradient(...)` 的话，
     渐变画完 380px 之后下面是**透明**的 ⇒ 透出 body 的灰底（EP 默认那个 #f0f2f5），
     整页看着发脏。 */
  background-color: var(--cc-bg);
  /* 顶部一团极淡的蓝，往下化开 —— 用现成 token 拼，不硬编码颜色 */
  background-image: radial-gradient(
    900px 320px at 50% -60px,
    color-mix(in srgb, var(--cc-primary) 8%, transparent),
    transparent 70%
  );
}

/* ---------- 顶栏 ---------- */
.exam__top {
  position: sticky;
  top: 0;
  z-index: 10;
  background: var(--cc-surface);
  border-bottom: 1px solid var(--cc-border);
}
.exam__top-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-13);
  /* §4.11(r) / §5.4 #44：840px 原来写两份（这里 + 下面的 .exam__body）⇒ 收成 --w-exam。 */
  max-width: var(--w-exam);
  margin: 0 auto;
  padding: var(--sp-11) var(--sp-15);
}
.exam__id {
  display: flex;
  align-items: center;
  gap: var(--sp-9);
  min-width: 0;
}
.exam__tag {
  flex: none;
  padding: 2px var(--sp-8);
  font-size: var(--text-xs);
  font-weight: 600;
  color: var(--cc-primary);
  background: var(--cc-primary-soft);
  border-radius: var(--radius-sm);
}
.exam__topic {
  margin: 0;
  font-size: var(--text-lg);
  font-weight: 600;
  color: var(--cc-text-1);
  letter-spacing: var(--ls-tight);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.exam__ops {
  display: flex;
  flex: none;
  align-items: center;
  gap: var(--sp-11);
}

/* ---------- 主体 ---------- */
.exam__body {
  /* §4.11(r) / §5.4 #44：同 .exam__top-inner ⇒ 840px 收成 --w-exam（只写一处） */
  max-width: var(--w-exam);
  margin: 0 auto;
  padding: var(--sp-15) var(--sp-15) var(--sp-16);
  /* §4.11(c)：这一页的 ExamRunner 也挂在容器里 ⇒ 它那条
     `@container (max-width: 420px)` 的窄屏适配在这一页也能生效
     （考试页本来够宽、不会触发；这一条是为了"组件放进哪个容器就按哪个容器算"）。
     ⚠️ 挂了 container-type 之后，这个元素**成为它后代的 absolute/fixed 定位包含块**
     ⇒ 里面有绝对定位的东西要核一下（这里没有）。 */
  container-type: inline-size;
}

/* ---------- 考试设置 ---------- */
.setup {
  padding: var(--sp-13);
  background: var(--cc-surface);
  border: 1px solid var(--cc-border);
  /* §4.11(i)：卡片圆角统一 --radius-lg（.qcard / .score 也是这一档） */
  border-radius: var(--radius-lg);
  /* §4.11(n)：同一条卡片阴影串抄了三份 ⇒ 收成 --cc-shadow-card */
  box-shadow: var(--cc-shadow-card);
}
/* 卡片头：标题 + 范围。范围（考的是什么）在顶栏已经写着，这里只补一个数量 */
.setup__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-12);
  padding-bottom: var(--sp-12);
  border-bottom: 1px solid var(--cc-border);
}
/* 「有未交卷的考试」提示：状态事实 + 入口，中性色不吓人 */
.setup__unfinished {
  display: flex;
  align-items: center;
  gap: var(--sp-4);
  margin-top: var(--sp-10);
  font-size: var(--text-sm);
  color: var(--cc-text-3);
}
.setup__title {
  margin: 0;
  font-size: var(--text-md);
  font-weight: 600;
  color: var(--cc-text-1);
  letter-spacing: var(--ls-tight);
}
.setup__scope {
  flex: none;
  padding: 2px var(--sp-10);
  font-size: var(--text-xs);
  font-weight: 600;
  color: var(--cc-primary);
  background: var(--cc-primary-soft);
  border-radius: var(--radius-pill);
}
/* 两栏：左边题型与道数，右边难度 + 附加要求 */
.setup__grid {
  display: grid;
  grid-template-columns: 1.05fr 1fr;
  gap: var(--sp-13);
  padding: var(--sp-13) 0;
}
.setup__col + .setup__col {
  padding-left: var(--sp-13);
  border-left: 1px solid var(--cc-border);
}
.setup__label {
  margin-bottom: var(--sp-9);
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--cc-text-1);
}
.setup__field + .setup__field {
  margin-top: var(--sp-12);
}
.setup__hint {
  margin: var(--sp-7) 0 0;
  font-size: var(--text-sm);
  line-height: 1.6;
  color: var(--cc-text-3);
}
.setup__type,
.setup__level {
  width: 100%;
}
/* 一行 = 题型 + 道数 + 分值 + 删 */
.setup__row {
  display: grid;
  grid-template-columns: 1fr auto auto auto;
  align-items: center;
  gap: var(--sp-9);
  margin-bottom: var(--sp-7);
}
/* 分值：Element 的数字输入框默认太宽，收窄成能塞下两位数的样子 */
.setup__score {
  width: 88px;
}
.setup__score :deep(.el-input__wrapper) {
  padding-left: var(--sp-7);
  padding-right: var(--sp-4);
}
/* 道数步进器：Element 的数字输入框又宽又丑，自己画一个 */
.stepper {
  display: flex;
  align-items: center;
  /* §4.1 第 3 条：按钮 22→--ctl-sm(28) ⇒ 容器跟着到 28。
     考试页是"一屏一题"，加减道数是主操作，22px 的热区太小。 */
  height: var(--ctl-sm);
  background: var(--cc-surface-2);
  border: 1px solid var(--cc-border-strong);
  border-radius: var(--radius-sm);
}
.stepper__btn {
  width: var(--ctl-sm);
  height: var(--ctl-sm);
  padding: 0;
  font-size: var(--text-md);
  line-height: 1;
  color: var(--cc-text-2);
  background: none;
  border: none;
  cursor: pointer;
}
.stepper__btn:hover:not(:disabled) {
  color: var(--cc-primary);
}
.stepper__btn:disabled {
  color: var(--cc-text-4);
  cursor: not-allowed;
}
.stepper__num {
  min-width: 20px;
  font-size: var(--text-sm);
  font-weight: 600;
  text-align: center;
  color: var(--cc-text-1);
}
.setup__del {
  width: 22px;
  height: 22px;
  padding: 0;
  font-size: var(--text-md);
  line-height: 1;
  color: var(--cc-text-4);
  background: none;
  border: none;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: var(--cc-transition);
}
.setup__del:hover {
  color: var(--cc-danger);
  background: color-mix(in srgb, var(--cc-danger) 10%, transparent);
}
.setup__add {
  padding: 0;
  font-size: var(--text-sm);
  color: var(--cc-primary);
  background: none;
  border: none;
  cursor: pointer;
}
.setup__add:hover {
  color: var(--cc-primary-hover);
}
.setup__foot {
  display: flex;
  align-items: center;
  gap: var(--sp-11);
  padding-top: var(--sp-12);
  border-top: 1px solid var(--cc-border);
}
.setup__count-all {
  flex: none;
  margin-right: auto;
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--cc-text-2);
}
.setup__warn {
  font-size: var(--text-sm);
  color: var(--cc-warning);
}
/* 窄屏两栏挤不下，叠成一条
   ⚠️ §4.11(r)：断点原来 640px ⇒ **641–900px 这个区间没人管**：
   那一档里 :495 的行内四列 grid 把题型下拉压到 <100px（700px 视口 ≈ **77px**），
   且 `.exam__setup { min-width:300px }` 在 560px 视口的 grid 列（≈216px）里**必然溢出**。
   ⇒ 断点提到 900px。 */
@media (max-width: 900px) {
  .setup__grid {
    grid-template-columns: 1fr;
  }
  .setup__col + .setup__col {
    padding-left: 0;
    border-left: none;
  }
}

/* ---------- 生成中 / 失败 ----------
 * 这两条在 `assets/kb.css`（`.kb-center` / `.kb-error`）——
 * 详情页随堂测验用的是同一份，两边各写一遍会漂（原来图标字号就差 2px）。 */

</style>
