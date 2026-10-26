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
          <template v-if="phase === 'ready' || phase === 'grading'">
            <span class="exam__pos">
              <b>{{ cur + 1 }}</b><i>/ {{ questions.length }}</i>
            </span>
            <span class="exam__done">已答 {{ answeredCount }}</span>
            <el-button @click="submitPaper">交卷</el-button>
          </template>
          <template v-else-if="phase === 'done'">
            <span class="exam__score kb-verdict" :class="scoreClass">{{ scoreText }}</span>
            <el-button @click="backToSettings">重新出卷</el-button>
            <el-button type="primary" @click="backToTopic">
              {{ categoryId ? '回到目录' : '回到列表' }}
            </el-button>
          </template>
        </div>
      </div>

      <!-- 进度条：走的是"答了几道"，不是"看到第几道"（可以来回跳，位置会骗人） -->
      <div v-if="phase === 'ready' || phase === 'grading'" class="exam__track">
        <i :style="{ width: progress + '%' }"></i>
      </div>
    </header>

    <!-- ==================== 主体 ==================== -->
    <main class="exam__body">
      <!-- ========== 出卷设置 ========== -->
      <section v-if="phase === 'idle'" class="setup">
        <header class="setup__head">
          <h2 class="setup__title">出卷设置</h2>
          <span class="setup__scope">{{ scopeText }}</span>
        </header>

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
              <!-- 分值 = 这道题值多少分。**出卷时就定好**，跟着这张卷子走。 -->
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
              <div class="setup__label">我自己的要求</div>
              <el-input v-model="extra" type="textarea" :rows="3" placeholder="选填" />
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
            出卷
          </el-button>
        </div>
      </section>

      <!-- ========== 出卷中 ========== -->
      <div v-else-if="phase === 'loading'" class="exam__center">
        <el-icon class="is-loading"><Loading /></el-icon>
        <p>正在出卷…</p>
      </div>

      <!-- ========== 出卷失败 ========== -->
      <div v-else-if="phase === 'error'" class="exam__center">
        <p class="exam__error">{{ error }}</p>
        <el-button @click="generate">重试</el-button>
      </div>

      <!-- ========== 答题：一屏一道题 ========== -->
      <section v-else-if="phase === 'ready' || phase === 'grading'" class="exam__stage">
        <!-- 卷面和规范对不上的地方：只报事实 -->
        <p v-if="note" class="exam__note">{{ note }}</p>

        <article v-if="curQ" class="qcard">
          <header class="qcard__head">
            <span class="qcard__type">{{ curQ.typeLabel }}</span>
            <span class="qcard__no">第 {{ curQ.no }} 题</span>
          </header>

          <div class="qcard__stem">{{ curQ.stem }}</div>

          <div v-if="curQ.options.length" class="qcard__options">
            <button
              v-for="(o, i) in curQ.options"
              :key="o.label"
              type="button"
              class="qopt"
              :class="{ 'is-picked': isPicked(curQ.no, i), 'qopt--multi': isMultiType(curQ.type) }"
              :disabled="phase !== 'ready'"
              @click="togglePick(curQ.no, i)"
            >
              <span class="qopt__label">{{ o.label }}</span>
              <span class="qopt__text">{{ o.text }}</span>
            </button>
          </div>

          <el-input
            v-else-if="isFillType(curQ.type)"
            v-model="answers[curQ.no].typed"
            :disabled="phase !== 'ready'"
            placeholder="填答案"
          />
          <el-input
            v-else
            v-model="answers[curQ.no].typed"
            type="textarea"
            :rows="5"
            :disabled="phase !== 'ready'"
            placeholder="作答"
          />

          <footer class="qcard__foot">
            <el-button :disabled="cur === 0" @click="goPrev">上一道</el-button>
            <el-button v-if="!isLast" type="primary" @click="goNext">下一道</el-button>
            <el-button v-else type="primary" :loading="phase === 'grading'" @click="submitPaper">
              交卷判分
            </el-button>
          </footer>
        </article>
      </section>

      <!-- ========== 结果 ========== -->
      <section v-else class="exam__result">
        <div class="score">
          <div class="score__ring kb-verdict" :class="scoreClass" :style="{ '--pct': ratioPct + '%' }">
            <span class="score__pct">{{ ratioPct }}<i>%</i></span>
          </div>
          <div class="score__meta">
            <div class="score__num kb-verdict" :class="scoreClass">{{ fmtScore(score.sum) }}</div>
            <div class="score__total">满分 {{ fmtScore(score.total) }}</div>
            <div class="score__chips">
              <span v-for="v in VERDICTS" :key="v.key" class="chip kb-verdict" :class="v.cls">{{ v.verdict }} {{ counts[v.key] }}</span>
              <span v-if="counts.none" class="chip is-none">未判定 {{ counts.none }}</span>
            </div>
          </div>
        </div>

        <article v-for="q in questions" :key="q.no" class="rev">
          <header class="rev__head">
            <span class="rev__no">{{ q.no }}</span>
            <span class="rev__type">{{ q.typeLabel }}</span>
            <span class="rev__verdict kb-verdict" :class="verdictClassOf(gradeOf(q)?.verdict)">
              {{ gradeOf(q)?.verdict || '未判定' }}
            </span>
            <!-- 这道题的分：满分是出卷时定下的，得了多少看判定 -->
            <span class="rev__points">{{ fmtScore(gainedOf(q)) }} / {{ fmtScore(scoreOf(q)) }} 分</span>
          </header>
          <div class="rev__stem">{{ q.stem }}</div>
          <div class="rev__lines">
            <!-- 选择题不单独列"正确答案"：下面每个选项自己带 ✓/✗，那才是能看懂的 -->
            <div v-if="!q.options.length" class="rev__line">
              <span class="rev__k">正确答案</span>
              <span class="rev__v">{{ q.isOpen ? q.answerPoints.join('；') : q.answer }}</span>
            </div>
            <div class="rev__line">
              <span class="rev__k">你的作答</span>
              <span class="rev__v">{{ describeAnswer(q, answers[q.no]) }}</span>
            </div>
          </div>

          <!-- 原来的选项：只看一个字母是看不出对错的，得看见选项本身 -->
          <div v-if="q.options.length" class="rev__options">
            <div
              v-for="(o, i) in q.options"
              :key="o.label"
              class="rev__opt"
              :class="{ 'is-right': isRightOption(q, o.label), 'is-mine': isPicked(q.no, i) }"
            >
              <span class="rev__opt-label">{{ o.label }}</span>
              <span class="rev__opt-text">{{ o.text }}</span>
              <span v-if="optionMark(q, o.label, i)" class="rev__opt-mark">
                {{ optionMark(q, o.label, i) }}
              </span>
            </div>
          </div>

          <div v-if="gradeOf(q)?.explain" class="rev__explain">{{ gradeOf(q).explain }}</div>
        </article>
      </section>
    </main>
  </div>
</template>

<script setup>
/**
 * 考试模式（**demo**：零存储、零建表，觉得不好用整块删掉）。
 *
 * ## 它是什么
 *
 * 从一条（或一组）知识出发出一份卷子 → **一题一题**作答 → 交卷 → AI 判分并给逐题讲解。
 * **题目和答题记录都不保存**：卷子只活在内存里，刷新就没了。
 *
 * ## 考哪一批知识：URL 说了算
 *
 * `/exam?ids=1,2,3&name=…&cat=…` —— 这次就考这几条。
 * **唯一入口是列表页筛选条上的「出卷」**（范围 = 当前筛选出来的那些，未归类的不算）：
 * `ids` = 这批知识的 id，`name` = 范围的名字（目录名或「全部」），
 * `cat` = 选了目录时的目录 id（考完退回那个目录的列表页）。
 *
 * **正文由服务端按 id 自己捞**（AiChatReq.topicIds）：前端不传正文，
 * 归属也由服务端按登录态收 —— 与这个项目"客户端不能指定把哪段文本当上下文"的老规矩一致。
 *
 * ## 出卷参数：配置给默认值，用户在页面上改
 *
 * 规范模板 / 题量结构（题型·道数·分值）/ 难度档位 / 附加要求，四样都在系统配置里
 * （`ai.prompt.exam.*`），本页读它们当默认值，用户可以改完再出卷。
 * **题量结构是一份数据**，同时喂「拼给模型的题量结构」和「解析器按它推题号→题型」⇒ 两边不会错位。
 * 详见 `exam.js`。
 *
 * ## 四个状态
 *
 * 出卷设置 → 出卷中 → 答题 → 判分中 → 看结果。
 * **判分之前一个字的对错都不显示**——边做边看答案是自测题，不是考试。
 */
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import { ask, ensureAiConfigLoaded } from './useAi'
import { categoryLink } from '../../utils/categoryNav'
import {
  buildPaperText,
  describeAnswer,
  isFillType,
  isMultiType,
  parseGrade,
  parsePaper,
  paperNote,
  paperScore,
  rangesOf,
  verdictClassOf,
  VERDICTS,
  DEFAULT_SCORE
} from './examParse'
import { buildPaperPrompt, CFG_KEYS, loadExamSpec, maxScoreOf, totalOf, TYPE_OPTIONS } from './exam'

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

const phase = ref('idle')
const error = ref('')
/** 这次考的东西叫什么（= 目录名，URL 带过来） */
const scopeTitle = ref('')
const questions = ref([])
const grades = reactive({})
/** 卷面和规范对不上的地方（哪几道没解析出来、哪种题型少了） */
const note = ref('')
/** 作答状态，按题号记：`{ picked: [选项下标], typed: '输入的文字' }` */
const answers = reactive({})
/** 当前第几道（一屏一题） */
const cur = ref(0)

/** ========== 出卷设置（默认值来自系统配置） ========== */
const sections = ref([])
/** 每种题型的**默认分值**（来自配置）。新加一种题型时用它兜底 */
const defaultScores = ref({})
/** 难度档位：`[{name, desc}]` */
const levels = ref([])
const levelName = ref('')
const extra = ref('')
/** 规范模板（配置里的那段文本） */
const paperTemplate = ref('')
/** 配置有问题时的一句话提示（比如题型没认出来） */
const specWarning = ref('')

/** 出卷中的中止器：离开页面就掐掉，别让请求在后台继续烧 token */
let abort = null

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
    sections.value = spec.types.sections.map((s) => ({ ...s }))
    // 每种题型的默认分值（新加题型时按它兜底）
    defaultScores.value = Object.fromEntries(spec.types.sections.map((s) => [s.type, s.score]))
    levels.value = spec.levels
    levelName.value = spec.levels[0]?.name || ''
    extra.value = spec.extra
    if (spec.types.bad) specWarning.value = `配置里有 ${spec.types.bad} 行题型没认出来，已忽略`
  } catch (e) {
    specWarning.value = e?.message || '读考试配置失败'
  }
  // 范围的名字 = 入口那边给的（目录名 / 「全部」）。范围是**当前筛选出来的那些**。
  scopeTitle.value = String(route.query.name || '')
  if (!topicIds.value.length) {
    error.value = '没有指定要考的知识'
    phase.value = 'error'
  }
})

onBeforeUnmount(() => abort?.abort())

// ==================== 出卷设置 ====================

/**
 * 题号区间：**算一次**，拼 prompt 和解析器都用它。
 *
 * ⚠️ 两边各算一遍 = 迟早对不上（用户改一次道数，模型出的题号和解析器认的题型就错位），
 * 而且**不报错**。见 `examParse.js` 的 `rangesOf`。
 */
const ranges = computed(() => rangesOf(sections.value))

/**
 * 计划题数与计划满分：**都从 `sections` 算**（同一份设置、两道口径）。
 * `ranges` 只干它自己的活——题号区间（拼 prompt + 解析器推题型）。
 */
const total = computed(() => totalOf(sections.value))
const planMax = computed(() => maxScoreOf(sections.value))

/**
 * 得分与满分：**按每题的分值算**，不是一道一分。
 *
 * <p>折算规则在 `examParse.js` 的 `paperScore` 里（纯函数，能整条判分链一起验）；
 * 这里**不抄第二份**。分值是题量结构的一部分（`题型|道数|分值`），
 * **出卷时设置面板不可改，改了也要重新出卷** ⇒ 卷子在、设置也在，两边不会对不上。
 */
const score = computed(() => paperScore(questions.value, grades, sections.value))

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

// ==================== 出卷 ====================

/**
 * 出卷。**走现成的问答通道**，提示词是前端拼的（题量结构 + 模板 + 难度 + 附加要求）。
 *
 * 题目解析不出来时**不整卷作废**：能解析几道算几道（见 `examParse.js`）。
 */
async function generate() {
  if (!topicIds.value.length || !total.value) return
  abort?.abort()
  abort = new AbortController()
  phase.value = 'loading'
  error.value = ''
  note.value = ''
  cur.value = 0
  for (const k of Object.keys(grades)) delete grades[k]
  for (const k of Object.keys(answers)) delete answers[k]
  questions.value = []
  let paperText = ''
  try {
    paperText = await ask(
      buildPaperPrompt({
        paper: paperTemplate.value,
        ranges: ranges.value,
        level: levels.value.find((l) => l.name === levelName.value),
        extra: extra.value
      }),
      {
        topicIds: topicIds.value,
        signal: abort.signal
      }
    )
  } catch (e) {
    if (e?.name === 'AbortError') return
    error.value = e?.message || '出卷失败'
    phase.value = 'error'
    return
  }
  const { questions: list, problems } = parsePaper(paperText, ranges.value)
  if (!list.length) {
    error.value = '这次没解析出题目'
    phase.value = 'error'
    return
  }
  note.value = paperNote(list, problems, ranges.value)
  questions.value = list
  for (const q of list) answers[q.no] = { picked: [], typed: '' }
  phase.value = 'ready'
}

/** 回到出卷设置（重新出一份） */
function backToSettings() {
  phase.value = 'idle'
  error.value = ''
}

// ==================== 作答 ====================

const curQ = computed(() => questions.value[cur.value] || null)
const isLast = computed(() => cur.value === questions.value.length - 1)

function goNext() {
  if (cur.value < questions.value.length - 1) cur.value++
}

function goPrev() {
  if (cur.value > 0) cur.value--
}

function togglePick(no, i) {
  const q = questionOf(no)
  const a = answers[no]
  if (!q || !a) return
  if (!isMultiType(q.type)) {
    a.picked = [i]
    return
  }
  a.picked = a.picked.includes(i) ? a.picked.filter((x) => x !== i) : [...a.picked, i]
}

function isPicked(no, i) {
  return Boolean(answers[no]?.picked?.includes(i))
}
function isAnswered(q) {
  const a = answers[q.no]
  if (!a) return false
  if (q.options.length) return a.picked.length > 0
  return Boolean(String(a.typed || '').trim())
}

const answeredCount = computed(() => questions.value.filter(isAnswered).length)

/**
 * 进度条走"答了几道"：位置会骗人（可以来回跳），答没答不会。
 *
 * ⚠️ **分母是实际解析出来的题数**，不是计划的题数。
 * 解析器是**逐块容错**的（坏的那道丢掉、其余照常）—— 计划 25 道、实际出了 24 道时，
 * 用计划数当分母，进度条会永远停在 96%，看着像"还有一道没答"。
 * 「计划题数」只在出卷设置区用（那里还没出卷，显示的是"我要考几道"）。
 */
const progress = computed(() =>
  questions.value.length ? Math.round((answeredCount.value / questions.value.length) * 100) : 0
)

// ==================== 交卷判分 ====================

/**
 * 交卷：把整卷 + 考生作答交给 AI 判。
 *
 * **标准答案一起给**——判分是"对着标准答案判"，不是"重新答一遍"。
 * 开放题（概念题 / 面试题）没有唯一答案，规范要求 AI 只给"部分对"这类判定 +
 * 讲解，不许一刀切判对错。
 */
async function submitPaper() {
  if (!questions.value.length || phase.value === 'grading') return
  // 还有没答的就问一句：卷子只在内存里，交上去这一遍就定型了
  const left = questions.value.length - answeredCount.value
  if (left > 0) {
    try {
      await ElMessageBox.confirm(`还有 ${left} 道没答，确定交卷？`, '交卷', {
        confirmButtonText: '交卷',
        cancelButtonText: '再想想',
        type: 'warning'
      })
    } catch {
      return
    }
  }
  phase.value = 'grading'
  let gradeText = ''
  try {
    gradeText = await ask(
      '判卷',
      {
        topicIds: topicIds.value,
        material: buildPaperText(questions.value, answers),
        questionKey: CFG_KEYS.grade,
        signal: abort?.signal
      }
    )
  } catch (e) {
    // 判分失败**不回退卷子**：用户答的东西还在界面上，让他能直接重试
    ElMessage.error(e?.message || '判分失败')
    phase.value = 'ready'
    return
  }
  const parsed = parseGrade(gradeText)
  for (const k of Object.keys(grades)) delete grades[k]
  Object.assign(grades, parsed)
  phase.value = 'done'
}

// ==================== 结果 ====================

function gradeOf(q) {
  return grades[q.no] || null
}

/** 这个选项是不是正确项（选择题的标准答案是字母串："A" / "AB"） */
function isRightOption(q, label) {
  return String(q.answer || '').includes(label)
}

/**
 * 选项角标：**只看"正确答案：A"是看不出错在哪的**，
 * 得看见选项本身，还得知道哪个是你选的、哪个是错的。
 */
function optionMark(q, label, index) {
  const right = isRightOption(q, label)
  const mine = isPicked(q.no, index)
  if (right && mine) return '✓ 选对'
  if (right) return '✓ 正确'
  if (mine) return '✗ 你选错'
  return ''
}

/** 这道题值多少分（题量结构里配的） */
function scoreOf(q) {
  return score.value.items[q.no]?.weight ?? 1
}

/** 这道题得了多少分（结果卡上显示） */
function gainedOf(q) {
  return score.value.items[q.no]?.points ?? 0
}

/** 分数：整数就写整数，有半分开头就留一位小数（部分对 = 一半分 ⇒ 会出现 .5） */
function fmtScore(n) {
  return Number.isInteger(n) ? String(n) : n.toFixed(1)
}

const scoreText = computed(() => `${fmtScore(score.value.sum)} / ${fmtScore(score.value.total)}`)

/** 得分率（百分比整数）：成绩卡的环用 */
const ratioPct = computed(() =>
  score.value.total ? Math.round((score.value.sum / score.value.total) * 100) : 0
)

/**
 * 得分率 → 三档样式类。
 *
 * **类名从 VERDICTS 那张表取**（判定三档的类名只有一份出处）；
 * 阈值是"得分率"这条轴自己的（0.8 / 0.6），跟判定没关系。
 */
const scoreClass = computed(() => {
  const ratio = score.value.total ? score.value.sum / score.value.total : 0
  const key = ratio >= 0.8 ? 'right' : ratio >= 0.6 ? 'part' : 'wrong'
  return VERDICTS.find((v) => v.key === key).cls
})

/**
 * 三个判定各多少道。**从 `VERDICTS` 派生**（判定只有三种那张表）——
 * 未判定单列，它不是"错"，别混进错题里。
 */
const counts = computed(() => {
  const out = { none: 0 }
  for (const v of VERDICTS) out[v.key] = 0
  for (const q of questions.value) {
    const hit = VERDICTS.find((v) => v.verdict === gradeOf(q)?.verdict)
    if (hit) out[hit.key] += 1
    else out.none += 1
  }
  return out
})

// ==================== 杂项 ====================

function questionOf(no) {
  return questions.value.find((q) => q.no === no) || null
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
  max-width: 840px;
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
.exam__pos {
  font-size: var(--text-md);
  color: var(--cc-text-3);
}
.exam__pos b {
  margin-right: 2px;
  font-size: var(--text-xl);
  font-weight: 700;
  color: var(--cc-text-1);
}
.exam__pos i {
  font-style: normal;
}
.exam__done {
  font-size: var(--text-sm);
  color: var(--cc-text-3);
}
.exam__score {
  font-size: var(--text-xl);
  font-weight: 700;
}
/* 三档的颜色（对 / 部分对 / 错）已提到 assets/kb.css 的 .kb-verdict ——
   同一档的颜色原来在这页 5 个 CSS 块里各写一遍，逐字符相同。 */

/* ---------- 进度条 ---------- */
.exam__track {
  height: 3px;
  background: var(--cc-surface-2);
}
.exam__track i {
  display: block;
  height: 100%;
  background: var(--cc-primary);
  transition: width 0.25s ease;
}

/* ---------- 主体 ---------- */
.exam__body {
  max-width: 840px;
  margin: 0 auto;
  padding: var(--sp-15) var(--sp-15) var(--sp-16);
}
.exam__note {
  margin: 0 0 var(--sp-12);
  font-size: var(--text-sm);
  color: var(--cc-warning);
}

/* ---------- 出卷设置 ---------- */
.setup {
  padding: var(--sp-13);
  background: var(--cc-surface);
  border: 1px solid var(--cc-border);
  border-radius: var(--radius-xl);
  box-shadow: 0 10px 32px rgb(31 35 41 / 6%);
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
  height: 24px;
  background: var(--cc-surface-2);
  border: 1px solid var(--cc-border-strong);
  border-radius: var(--radius-sm);
}
.stepper__btn {
  width: 22px;
  height: 22px;
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
/* 窄屏（手机）两栏挤不下，叠成一条 */
@media (max-width: 640px) {
  .setup__grid {
    grid-template-columns: 1fr;
  }
  .setup__col + .setup__col {
    padding-left: 0;
    border-left: none;
  }
}

/* ---------- 出卷中 / 失败 ---------- */
.exam__center {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sp-12);
  padding: var(--sp-16) 0;
  color: var(--cc-text-3);
  font-size: var(--text-sm);
}
.exam__center .is-loading {
  font-size: 30px;
  color: var(--cc-primary);
}
.exam__error {
  color: var(--cc-danger);
}

/* ---------- 题目卡 ---------- */
.qcard {
  padding: var(--sp-15);
  background: var(--cc-surface);
  border: 1px solid var(--cc-border);
  border-radius: var(--radius-xl);
  box-shadow: 0 10px 32px rgb(31 35 41 / 6%);
}
.qcard__head {
  display: flex;
  align-items: center;
  gap: var(--sp-9);
  margin-bottom: var(--sp-12);
}
.qcard__type {
  padding: 2px var(--sp-8);
  font-size: var(--text-xs);
  font-weight: 600;
  color: var(--cc-primary);
  background: var(--cc-primary-soft);
  border-radius: var(--radius-sm);
}
.qcard__no {
  font-size: var(--text-sm);
  color: var(--cc-text-3);
}
.qcard__stem {
  margin-bottom: var(--sp-14);
  font-size: var(--text-xl);
  line-height: 1.7;
  color: var(--cc-text-1);
  white-space: pre-wrap;
}

/* ---------- 选项 ---------- */
.qcard__options {
  display: flex;
  flex-direction: column;
  gap: var(--sp-9);
}
.qopt {
  display: flex;
  gap: var(--sp-11);
  align-items: flex-start;
  width: 100%;
  padding: var(--sp-11) var(--sp-12);
  font-size: var(--text-md);
  line-height: 1.6;
  text-align: left;
  color: var(--cc-text-2);
  background: var(--cc-surface-2);
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: var(--cc-transition), border-color 0.15s;
}
.qopt:hover:not(:disabled) {
  color: var(--cc-text-1);
  background: var(--cc-surface);
  border-color: var(--cc-border-strong);
}
.qopt:disabled {
  cursor: default;
}
.qopt__label {
  flex: none;
  width: 24px;
  height: 24px;
  margin-top: 1px;
  font-size: var(--text-xs);
  font-weight: 600;
  line-height: 22px;
  text-align: center;
  color: var(--cc-text-3);
  background: var(--cc-surface);
  border: 1px solid var(--cc-border-strong);
  border-radius: 50%;
  transition: var(--cc-transition);
}
/* 多选是方的、单选是圆的：一眼看出这道能选几个 */
.qopt--multi .qopt__label {
  border-radius: var(--radius-sm);
}
.qopt.is-picked {
  color: var(--cc-text-1);
  background: var(--cc-primary-soft);
  border-color: var(--cc-primary);
}
.qopt.is-picked .qopt__label {
  color: var(--cc-surface);
  background: var(--cc-primary);
  border-color: var(--cc-primary);
}
.qopt__text {
  flex: 1;
}

/* ---------- 上下题 ---------- */
.qcard__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: var(--sp-14);
  padding-top: var(--sp-13);
  border-top: 1px solid var(--cc-border);
}

/* ---------- 成绩 ---------- */
.score {
  display: flex;
  align-items: center;
  gap: var(--sp-15);
  margin-bottom: var(--sp-14);
  padding: var(--sp-15);
  background: var(--cc-surface);
  border: 1px solid var(--cc-border);
  border-radius: var(--radius-xl);
  box-shadow: 0 10px 32px rgb(31 35 41 / 6%);
}
/* 得分环：conic-gradient 画进度，中间掏空。
   环色跟着那一档走（--kb-verdict-color 由 .kb-verdict.is-* 给），
   没落到三档里就是主色。 */
.score__ring {
  --ring-color: var(--kb-verdict-color, var(--cc-primary));
  position: relative;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 88px;
  height: 88px;
  border-radius: 50%;
  background: conic-gradient(var(--ring-color) var(--pct), var(--cc-surface-2) 0);
}
/* ::before 是第一个子元素 ⇒ 画在文字下面，正好是那个"洞" */
.score__ring::before {
  content: '';
  position: absolute;
  inset: 8px;
  border-radius: 50%;
  background: var(--cc-surface);
}
.score__pct {
  position: relative;
  z-index: 1;
  font-size: var(--text-xl);
  font-weight: 700;
  color: var(--ring-color);
}
.score__pct i {
  font-size: var(--text-sm);
  font-style: normal;
}
.score__num {
  font-size: var(--text-stat);
  font-weight: 700;
  line-height: 1;
}
.score__total {
  margin-bottom: var(--sp-9);
  font-size: var(--text-sm);
  color: var(--cc-text-3);
}
.score__chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-8);
}
.chip {
  padding: 2px var(--sp-9);
  font-size: var(--text-xs);
  font-weight: 600;
  border-radius: var(--radius-pill);
}
/* 三档 chip：底色是那一档的颜色淡出来的一层。
   颜色和浓度都在 kb.css 的 .kb-verdict（--kb-verdict-color / --kb-verdict-tint）里，
   这里只写"怎么调"——不再各写一遍色值。 */
.chip.is-good,
.chip.is-mid,
.chip.is-bad {
  background: color-mix(in srgb, var(--kb-verdict-color) var(--kb-verdict-tint), transparent);
}
.chip.is-none {
  color: var(--cc-text-3);
  background: var(--cc-surface-2);
}

/* ---------- 逐题解析 ---------- */
.rev {
  margin-bottom: var(--sp-11);
  padding: var(--sp-13) var(--sp-14);
  background: var(--cc-surface);
  border: 1px solid var(--cc-border);
  border-radius: var(--radius-lg);
}
.rev__head {
  display: flex;
  align-items: center;
  gap: var(--sp-9);
  margin-bottom: var(--sp-9);
}
.rev__no {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--cc-text-3);
}
.rev__type {
  font-size: var(--text-xs);
  color: var(--cc-text-3);
}
.rev__verdict {
  margin-left: auto;
  font-size: var(--text-sm);
  font-weight: 600;
}
/* 这道题的分：跟在判定后面（判定是 auto 左边距，所以分在它右边） */
.rev__points {
  font-size: var(--text-xs);
  color: var(--cc-text-3);
  font-variant-numeric: tabular-nums;
}
.rev__verdict.is-none {
  color: var(--cc-text-4);
}
.rev__stem {
  margin-bottom: var(--sp-9);
  font-size: var(--text-md);
  line-height: 1.7;
  color: var(--cc-text-1);
  white-space: pre-wrap;
}
.rev__lines {
  display: flex;
  flex-direction: column;
  gap: var(--sp-5);
  margin-bottom: var(--sp-9);
}
.rev__line {
  display: flex;
  gap: var(--sp-9);
  font-size: var(--text-sm);
  line-height: 1.6;
}
.rev__k {
  flex: none;
  width: 56px;
  color: var(--cc-text-4);
}
.rev__v {
  color: var(--cc-text-2);
}
/* 原题选项：正确项绿、选错的红、其余灰 */
.rev__options {
  display: flex;
  flex-direction: column;
  gap: var(--sp-6);
  margin-bottom: var(--sp-9);
}
.rev__opt {
  display: flex;
  gap: var(--sp-9);
  align-items: baseline;
  padding: var(--sp-7) var(--sp-9);
  font-size: var(--text-sm);
  line-height: 1.6;
  color: var(--cc-text-3);
  background: var(--cc-surface-2);
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
}
.rev__opt.is-right {
  color: var(--cc-text-2);
  background: color-mix(in srgb, var(--cc-success) 8%, transparent);
  border-color: color-mix(in srgb, var(--cc-success) 28%, transparent);
}
.rev__opt.is-mine:not(.is-right) {
  color: var(--cc-text-2);
  background: color-mix(in srgb, var(--cc-danger) 8%, transparent);
  border-color: color-mix(in srgb, var(--cc-danger) 28%, transparent);
}
.rev__opt-label {
  flex: none;
  font-weight: 600;
}
.rev__opt-text {
  flex: 1;
}
.rev__opt-mark {
  flex: none;
  font-size: var(--text-xs);
  font-weight: 600;
}
.rev__opt.is-right .rev__opt-mark {
  color: var(--cc-success);
}
.rev__opt.is-mine:not(.is-right) .rev__opt-mark {
  color: var(--cc-danger);
}
.rev__explain {
  padding: var(--sp-10) var(--sp-11);
  font-size: var(--text-sm);
  line-height: 1.7;
  color: var(--cc-text-2);
  white-space: pre-wrap;
  background: var(--cc-surface-2);
  border-radius: var(--radius-md);
}
</style>
