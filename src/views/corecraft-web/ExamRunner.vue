<template>
  <div class="runner">
    <!-- ==================== 恢复作答中 / 恢复失败 ====================
         刷新、重做、回看都从 attempt 恢复（题目与作答在库里，见脚本 loadAttempt）。
         ⚠️ 恢复作答是**大块内容**，但结构不可知（题目数量不定）⇒ 不画骨架，
         用紧凑指示（统一加载层，2026-11-13）。 -->
    <div v-if="phase === 'loading'" class="kb-center">
      <KbLoader size="sm" />
      <p>正在恢复作答…</p>
    </div>
    <div v-else-if="phase === 'error'" class="kb-center">
      <p class="kb-error">{{ loadError }}</p>
      <el-button @click="loadAttempt">重试</el-button>
    </div>

    <!-- ==================== 答题 / 判分中 ==================== -->
    <template v-else-if="phase !== 'done'">
      <!-- 进度条走的是"答了几道"，不是"看到第几道"（可以来回跳，位置会骗人） -->
      <div class="runner__track">
        <i :style="{ width: progress + '%' }"></i>
      </div>

      <div class="runner__ops">
        <span class="runner__pos">
          <b>{{ cur + 1 }}</b><i>/ {{ questions.length }}</i>
        </span>
        <span class="runner__done">已答 {{ answeredCount }}</span>
        <!-- 卷面结构与跳题（计划 vs 实出、哪些答了），一点就能跳到那道题 -->
        <button type="button" class="runner__plan" @click="planOpen = true">卷面</button>
        <!-- ⚠️ §4.11(h)：这个「交卷」原来是 size="small"(24px)，
             而下面那张卡里的「上一道 / 下一道 / 交卷」是 default(32px)
             ⇒ **同屏两档按钮**。
             ⇒ 删掉 size，走 --ctl-* 那一套。
             ⚠️ §4.11(f)：判分期间文字改成「判分中…」——
             原来只有一个转圈的 spinner，用户不知道它在判分（判分要 10–30s）。
             ⚠️ 判分失败后这里是「重试判分」：作答已经交上去了（不能再改），
             重试只重跑判分这一段（回填用同一个 resultId，幂等）。 -->
        <el-button
          v-if="phase === 'grading'"
          :loading="gradingInFlight"
          :disabled="gradingInFlight"
          @click="submit"
        >
          {{ gradingInFlight ? '判分中…' : '重试判分' }}
        </el-button>
        <el-button v-else @click="submit">交卷</el-button>
      </div>

      <!-- 判分/回填失败：作答已锁定，只剩「重试判分」这一条路 -->
      <p v-if="gradeError" class="runner__note">{{ gradeError }}，点「重试判分」再来一次</p>

      <!-- 卷面弹窗：整体题型结构（计划 vs 实出）+ 题号跳转 + 卷面说明。
           答题界面不再常驻那句「出题 N 道（计划 M 道）」——它是一份查得到的信息，不是状态 -->
      <el-dialog v-model="planOpen" title="卷面结构" width="var(--cc-dialog-w-sm)" append-to-body>
        <div class="plan">
          <div v-for="g in planGroups" :key="g.type" class="plan__row">
            <span class="plan__label">{{ g.label }}</span>
            <span class="plan__count" :class="{ 'is-short': g.actual < g.planned }">{{ g.actual }}/{{ g.planned }}</span>
            <span class="plan__nos">第 {{ g.from }}–{{ g.to }} 题</span>
          </div>
          <div class="plan__grid">
            <button
              v-for="(q, i) in questions"
              :key="q.no"
              type="button"
              class="plan__jump"
              :class="{ 'is-answered': isAnswered(q.no), 'is-cur': cur === i }"
              @click="jumpTo(i)"
            >{{ q.no }}</button>
          </div>
          <p v-if="note" class="plan__note">{{ note }}</p>
        </div>
      </el-dialog>

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

        <!-- 判断题：答案只有两个词，界面就是两个按钮（没有选项行） -->
        <div v-else-if="isJudgeType(curQ.type)" class="qjudge">
          <button
            v-for="c in JUDGE_CHOICES"
            :key="c"
            type="button"
            class="qjudge__btn"
            :class="{ 'is-picked': curAnswer?.judge === c }"
            :disabled="phase !== 'ready'"
            @click="pickJudge(curQ.no, c)"
          >
            {{ c }}
          </button>
        </div>

        <!-- §4.11(l)：填空题的输入框。placeholder「填答案」删掉 ——
             纪律：placeholder 默认不给，只**格式类**输入留示例。
             这格上面有题型标签（.qcard__type），它是「这道题要你填什么」的唯一说明，
             再补一句 placeholder 就是把同一件事说两遍。
             ⚠️ 判别题那两个按钮（.qbtn）没有输入框，placeholder 在那儿无处可放。 -->
        <el-input
          v-else-if="isFillType(curQ.type)"
          v-model="curAnswer.typed"
          :disabled="phase !== 'ready'"
        />
        <!-- §4.11(l)：同上，「作答」删掉。 -->
        <el-input
          v-else
          v-model="curAnswer.typed"
          type="textarea"
          :rows="5"
          :disabled="phase !== 'ready'"
        />

        <footer class="qcard__foot">
          <el-button :disabled="cur === 0" @click="goPrev">上一道</el-button>
          <!-- §4.11(f)：判分中（10–30s）原来只有两个 24px 按钮里的 spinner，
               选项**静默 disabled** ⇒ 用户不知道"为什么点不动"。
               ⇒ 判分期间按钮文字改「判分中…」。
               ⚠️ **「进行中」态是准则 §8 允许的四类提示之一**（进行中 / 进行不了 /
               出错 / 不可逆确认），合规。
               ⚠️ 顶栏那颗「交卷」按钮也是同一个 phase ⇒ 一起改。 -->
          <el-button
            v-if="phase === 'grading'"
            type="primary"
            :loading="gradingInFlight"
            :disabled="gradingInFlight"
            @click="submit"
          >
            {{ gradingInFlight ? '判分中…' : '重试判分' }}
          </el-button>
          <el-button v-else-if="!isLast" type="primary" @click="goNext">下一道</el-button>
          <!-- §4.11(m)：按钮文字原来「交卷判分」，上面那个叫「交卷」⇒ 同一个动作两种叫法。
               「判分」是交卷的必然结果，不需要写进按钮 ⇒ 统一「交卷」。 -->
          <el-button v-else type="primary" @click="submit">
            交卷
          </el-button>
        </footer>
      </article>
    </template>

    <!-- ==================== 结果 ==================== -->
    <section v-else class="runner__result">
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
          <!-- 这道题的分：满分是出卷时定下的，得了多少看判定。
               **未判定写「未判定」，不是 0 分**——0 是「判错」那一档的值（设计 §12.4） -->
          <span class="rev__points">{{ pointsLineOf(q) }}</span>
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
  </div>
</template>

<script setup>
/**
 * 答题器：**一屏一题 → 交卷 → 判分 → 逐题讲解**，以 attempt 为中心读写。
 *
 * ## 为什么抽出来
 *
 * 同一套「出卷 → 答题 → 交卷 → 讲解」现在有两处用：
 * 考试页（考一个目录的全部内容）和详情页抽屉里的随堂测验（考当前这条知识）。
 * 两处要的**差别只有出卷那一段**（范围和题量结构不同），答题与判分完全一样。
 * 不抽的话，判分规则、计分口径、判定三档的样式就会有两份 ——
 * 改一处漏一处的症状是"判了 3 道对，计数只显示 2 道"，而且不报错。
 *
 * ## 它管什么，不管什么
 *
 * 管：**这个 attempt 的一切读写**——作答、交卷落库、AI 判分、判分结果回填、
 * 结果区；刷新 / 重做 / 回看时从 `attempts/{id}/answers` 恢复（题目快照 + 你的作答
 * + 判定 + 讲解，一次拿全）。
 * **不管**：出卷（谁出、用什么范围、几道题）—— 那是调用方的事，出完把初卷递进
 * `initial-questions`、把 `attempt-id` 递进来就行；**不递初卷就自己从 attempt
 * 恢复**（设计 §12 必须补 #2：不再只发 phase，以 attempt 为中心读写）。
 *
 * ## 交卷那一刻才落库（阶段 A 口径）
 *
 * 作答在点「交卷」时一次性 PUT 落库、attempt 进 grading，然后判分回填。
 * 判分失败**作答不丢**（已落库、界面保持只读），点「重试判分」只重跑判分这一段，
 * 回填用同一个 resultId（同 resultId 重复回调服务端幂等短路，不重复产生证据）。
 */
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { confirmAction } from '../../utils/confirm'
import { percentNumber } from '../../utils/progress'
import KbLoader from '../../components/corecraft-web/KbLoader.vue'
import { ask } from './useAi'
import { getAttemptReview, gradeResult, saveAnswers, submitAttempt } from '../../api/exam'
import { invalidateLearningData } from './useCorecraftWebStore'
import {
  buildPaperText,
  describeAnswer,
  DEFAULT_SCORE,
  EXAM_TYPES,
  fmtScore,
  isFillType,
  isJudgeType,
  isMultiType,
  JUDGE_CHOICES,
  parseGrade,
  paperScore,
  verdictClassOf,
  verdictRatioOf,
  VERDICTS
} from './examParse'
import { CFG_KEYS } from './exam'

defineOptions({ name: 'ExamRunner' })

const props = defineProps({
  /** 刚出的初卷（`consumePaperTask` 的结果，带 questionId / maxScore）。**不给就从 attempt 恢复** */
  initialQuestions: { type: Array, default: null },
  /** 这次作答的 attempt id——**题目、作答、判定、成绩全挂它**，不是只挂卷内题号 */
  attemptId: { type: Number, required: true },
  /** 判分时把哪些知识当材料。**不给就只发整卷**（判卷靠标准答案，不靠原文） */
  // topicIds 已删（2026-11-14）：唯一消费者是判分，而判分材料（考卷）自带
  // 标准答案，material 非空时后端也不捞正文——出卷/判分的 topicIds 都在调用方自己手里
  /** 卷面和规范对不上的地方（只报事实，只有初卷有）。**显示在卷面弹窗里**，答题界面不再常驻 */
  note: { type: String, default: '' },
  /** 计划题量结构（`rangesOf` 的结果 [{type, from, to}]，调用方从出题任务的 plan 传来）——卷面弹窗的"计划"列 */
  plan: { type: Array, default: () => [] }
})

/** 阶段变化告诉调用方（考试页要据此换顶栏，抽屉要据此换按钮） */
const emit = defineEmits(['phase'])

/**
 * 内部阶段：loading（从 attempt 恢复中）/ ready（答题）/ grading（判分）/
 * done（出结果了）/ error（恢复失败）。
 */
const phase = ref('loading')
/** 当前这份卷（初卷或从 attempt 恢复出来的），每题带 questionId / maxScore */
const questions = ref([])
/** 作答状态，按题号记：`{ picked: [选项下标], typed: '输入的文字', judge: '正确|错误' }` */
const answers = reactive({})
/** 判定结果，按题号记 */
const grades = reactive({})
/** 当前第几道（一屏一题） */
const cur = ref(0)
/** 作答已提交（落库 + 交卷）⇒ 只读。后端只收 answering 状态的作答，改也存不进去 */
const answersLocked = ref(false)
/** 判分进行中（区别于"判分失败等着重试"，两个状态按钮文案不同） */
const gradingInFlight = ref(false)
/** 判分/回填失败的那句话（非空 = 界面上给「重试判分」） */
const gradeError = ref('')
/** 恢复失败的那句话 */
const loadError = ref('')
/** 一次判分会话的幂等键：**重试沿用同一个**，服务端同 resultId 直接短路 */
const resultId = ref('')
/** 已解析、等回填的判分结果：回填失败重试时**不再问一遍模型**，直接重发 */
let pendingGrades = null

/** 判分那个 AI 调用的中止器：离开页面就拒掉，别让请求在后台继续烧 token */
let abort = null

onBeforeUnmount(() => abort?.abort())

function setPhase(next) {
  phase.value = next
  emit('phase', next)
}

function makeResultId() {
  // ⚠️ crypto.randomUUID 只在安全上下文（https / localhost）里有——线上是 http，得有兑底
  return globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(36).slice(2)}`
}

// ==================== 卷面弹窗（结构 + 跳题） ====================

const planOpen = ref(false)

/** 计划题量结构 × 实出：一种题型一行（实出少于计划标出来——那是「宁缺毋滥」在起作用，不是丢了） */
const planGroups = computed(() => (props.plan || []).map((r) => ({
  type: r.type,
  label: EXAM_TYPES[r.type]?.label || r.type,
  from: r.from,
  to: r.to,
  planned: r.to - r.from + 1,
  actual: questions.value.filter((q) => q.type === r.type).length
})))

/** 弹窗里点题号 = 跳到那道题（答题中随意跳，作答都在 answers 里不丢） */
function jumpTo(i) {
  cur.value = i
  planOpen.value = false
}

// ==================== 进入方式：初卷，或从 attempt 恢复 ====================

/**
 * attempt 变化 = 换了一次作答（重做）或第一次挂载。
 *
 * <p>**immediate 的回调是同步跑的** ⇒ 这个 watch 放在它引用的 state 声明之后，
 * 别往上挪（memory：TDZ「Cannot access 'x' before initialization」的坑）。
 */
watch(
  () => props.attemptId,
  (id, prev) => {
    if (id == null) return
    if (prev == null && props.initialQuestions?.length) {
      setupFresh()
    } else {
      loadAttempt()
    }
  },
  { immediate: true }
)

/** 初卷进答题：题刚出完刚落库，不回读 */
function setupFresh() {
  questions.value = props.initialQuestions || []
  resetState()
  setPhase('ready')
}

/**
 * 从 attempt 恢复（刷新 / 重做 / 回看都走这条）。一次调用拿全：
 * 题目快照 + 你的作答 + 判定 + 讲解。
 *
 * <p>状态分流：`answering` 继续答（有存过的作答就填回来）；`grading` / `failed`
 * 判分没跑完 ⇒ 只能「重试判分」（作答已锁定）；`partial` / `completed` 直接看结果——
 * **未判定的题保持未判定**，不进「隐含零分」路径（设计 §12.4）。
 */
async function loadAttempt() {
  setPhase('loading')
  loadError.value = ''
  try {
    const r = (await getAttemptReview(props.attemptId))?.data
    resetState()
    questions.value = (r?.items || []).map(reviveQuestion)
    for (const item of r?.items || []) {
      const a = normalizeAnswer(item.answerPayload)
      if (a.picked.length || a.typed || a.judge) answers[item.questionNo] = a
      if (item.verdict != null) {
        grades[item.questionNo] = { verdict: item.verdict, explain: item.explanation || '' }
      }
    }
    const status = r?.status
    if (status === 'answering') {
      answersLocked.value = false
      setPhase('ready')
    } else if (status === 'partial' || status === 'completed') {
      answersLocked.value = true
      setPhase('done')
    } else {
      // grading / failed：判分没跑完，作答已锁定，只剩重试判分
      answersLocked.value = true
      gradeError.value = '上次判分没跑完'
      setPhase('grading')
    }
  } catch (e) {
    loadError.value = e?.message || '恢复作答失败'
    setPhase('error')
  }
}

function resetState() {
  for (const k of Object.keys(answers)) delete answers[k]
  for (const k of Object.keys(grades)) delete grades[k]
  cur.value = 0
  answersLocked.value = false
  gradingInFlight.value = false
  gradeError.value = ''
  pendingGrades = null
  resultId.value = ''
}

/** 回看行 → 题目：题目快照在 questionPayload 里，type / maxScore 是独立的列 */
function reviveQuestion(item) {
  const p = item.questionPayload || {}
  return {
    no: item.questionNo,
    questionId: item.questionId,
    type: item.type,
    typeLabel: EXAM_TYPES[item.type]?.label || item.type || '',
    maxScore: item.maxScore,
    isOpen: Boolean(p.isOpen),
    stem: p.stem || '',
    options: p.options || [],
    answer: p.answer || '',
    answerPoints: p.answerPoints || []
  }
}

/** 库里存的作答 → 界面用的形状（缺哪格补哪格，模板 v-model 直接绑它） */
function normalizeAnswer(payload) {
  return {
    picked: Array.isArray(payload?.picked) ? payload.picked : [],
    typed: payload?.typed || '',
    judge: payload?.judge || ''
  }
}

// ==================== 题目 ====================

const curQ = computed(() => questions.value[cur.value] || null)
const isLast = computed(() => cur.value === questions.value.length - 1)

/**
 * 作答对象：**没有就现造一个**。现造逻辑全组件只有这一个口子
 * （curAnswer / togglePick / pickJudge 都走它，别再各自 if (!answers[no])）。
 *
 * <p>⚠️ 不能只靠 curAnswer 懒建：单选/多选分支的模板根本不碰 curAnswer，
 * answers[no] 从未被创建 ⇒ togglePick 里 `if (!a) return` 把点击静默吞掉，
 * 界面表现为「点选项没反应」（恢复路径 100% 中招——loadAttempt 只回填非空作答）。
 */
function ensureAnswer(no) {
  return answers[no] || (answers[no] = { picked: [], typed: '', judge: '' })
}

const curAnswer = computed(() => {
  const no = curQ.value?.no
  if (no == null) return null
  return ensureAnswer(no)
})

function goNext() {
  if (cur.value < questions.value.length - 1) cur.value++
}

function goPrev() {
  if (cur.value > 0) cur.value--
}

function togglePick(no, i) {
  const q = questionOf(no)
  if (!q) return
  const a = ensureAnswer(no)
  if (!isMultiType(q.type)) {
    a.picked = [i]
    return
  }
  a.picked = a.picked.includes(i) ? a.picked.filter((x) => x !== i) : [...a.picked, i]
}

/** 判断题：两个按钮，选一个就定下来（再点同一个 = 取消？） */
function pickJudge(no, choice) {
  const a = ensureAnswer(no)
  a.judge = a.judge === choice ? '' : choice
}

function isPicked(no, i) {
  return Boolean(answers[no]?.picked?.includes(i))
}

function isAnswered(q) {
  const a = answers[q.no]
  if (!a) return false
  if (isJudgeType(q.type)) return Boolean(a.judge)
  if (q.options.length) return a.picked.length > 0
  return Boolean(String(a.typed || '').trim())
}

const answeredCount = computed(() => questions.value.filter(isAnswered).length)

/**
 * 进度条走"答了几道"：位置会骗人（可以来回跳），答没答不会。
 *
 * <p>⚠️ **分母是实际出的题数**（恢复时也一样），不是计划的题数。
 * 解析器是**逐块容错**的（坏的那道丢掉、其余照常）—— 计划 25 道、实际出了 24 道时，
 * 用计划数当分母，进度条会永远停在 96%，看着像"还有一道没答"。
 */
const progress = computed(() =>
  percentNumber(answeredCount.value, questions.value.length)
)

// ==================== 交卷判分 ====================

/**
 * 交卷：作答落库 → 交卷 → AI 判分 → 判分结果回填（成绩、证据、复习调度同事务）。
 *
 * <p>**标准答案一起给**——判分是"对着标准答案判"，不是"重新答一遍"。
 * 开放题（概念题 / 面试题）没有唯一答案，规范要求 AI 只给"部分对"这类判定 +
 * 讲解，不许一刀切判对错。
 *
 * <p>三段各自可重试，互不重跑：① 落库+交卷失败 ⇒ 回 ready（还能改还能再交）；
 * ② 判分失败 ⇒ 锁定在 grading（作答已交），重试只重问模型；
 * ③ 回填失败 ⇒ 判定已在手，重试只重发（同 resultId 幂等）。
 */
async function submit() {
  if (gradingInFlight.value || !questions.value.length) return
  if (!answersLocked.value) {
    // 还有没答的就问一句：交上去这一遍就定型了
    const left = questions.value.length - answeredCount.value
    if (left > 0 && !(await confirmAction('交卷'))) return
    setPhase('grading')
    gradeError.value = ''
    try {
      await saveAnswers(props.attemptId, {
        answers: questions.value.map((q) => ({
          questionId: q.questionId,
          answerPayload: { picked: answers[q.no]?.picked || [], typed: answers[q.no]?.typed || '', judge: answers[q.no]?.judge || '' }
        }))
      })
      await submitAttempt(props.attemptId)
      answersLocked.value = true
    } catch (e) {
      // 落库失败**回 ready**：作答还在界面上，改完能直接再交
      ElMessage.error(e?.message || '交卷失败，作答还在，可以再试')
      setPhase('ready')
      return
    }
  } else {
    // 重试：作答早就交了，只重跑判分/回填
    setPhase('grading')
    gradeError.value = ''
  }
  await runGrade()
}

async function runGrade() {
  gradingInFlight.value = true
  try {
    if (!pendingGrades) {
      if (!resultId.value) resultId.value = makeResultId()
      const gradeText = await ask(
        '判卷',
        {
          // **不传 topicIds**：判分材料（考卷）里已带标准答案与作答，正文不需要——
          // material 非空时后端也不去捞知识（AiChatServiceImpl「给了 material
          // 就不去捞知识」）。判分只对着标准答案判，不给模型另立标准的余地
          material: buildPaperText(questions.value, answers),
          questionKey: CFG_KEYS.grade,
          signal: abort?.signal
        }
      )
      const parsed = parseGrade(gradeText)
      for (const k of Object.keys(grades)) delete grades[k]
      Object.assign(grades, parsed)
      pendingGrades = parsed
    }
    await gradeResult(props.attemptId, buildGradeResult(pendingGrades))
    pendingGrades = null
    // 判分写了一批证据/投影 ⇒ 学习缓存整体作废（目录三指标跟着变，下次读重拉）
    invalidateLearningData()
    setPhase('done')
  } catch (e) {
    if (e?.name === 'AbortError') return
    // 判定解析完之后失败 ⇒ pendingGrades 还在，重试直接重发不再问模型
    gradeError.value = e?.message || '判分失败'
    ElMessage.error(`${gradeError.value}，点「重试判分」再来一次`)
  } finally {
    gradingInFlight.value = false
  }
}

/**
 * 判分结果回填的请求体。**未判定的题不回填**——保持未判定，
 * 不是把 null 当 0 发上去（服务端拿 verdict/scoreRatio 双空判未判定，绝不落 0）。
 */
function buildGradeResult(parsed) {
  const items = []
  for (const q of questions.value) {
    const g = parsed[q.no]
    const ratio = verdictRatioOf(g?.verdict)
    if (ratio == null) continue
    items.push({
      questionId: q.questionId,
      verdict: g.verdict,
      scoreRatio: ratio,
      score: (q.maxScore ?? DEFAULT_SCORE) * ratio,
      explanation: g.explain || '',
      // 整题一条 rubric（__question__）——与归属快照的缺省 rubricKey 对得上，
      // 证据才不会"归属写了键、判分没给键"两头对不上而静默丢证据
      gradePayload: { rubrics: [{ key: '__question__', scoreRatio: ratio }] }
    })
  }
  return { resultId: resultId.value, gradeRevision: 1, answers: items }
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

/**
 * 得分与满分：**按每题的分值算**，不是一道一分。
 *
 * 折算规则在 `examParse.js` 的 `paperScore` 里（纯函数，能整条判分链一起验）；
 * 这里**不抄第二份**。`sum` 只算已判定的题（未判定不计入也不落 0），
 * `total` 是整卷满分——与库里 attempt 的 score / max_score 同口径，刷新不变。
 */
const score = computed(() => paperScore(questions.value, grades))

/** 这道题的分：未判定写「未判定」，不是 0 分（0 是「判错」那一档的值） */
function pointsLineOf(q) {
  const it = score.value.items[q.no]
  if (!it?.graded) return '未判定'
  return `${fmtScore(it.points)} / ${fmtScore(it.weight)} 分`
}

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
</script>

<style scoped>
/**
 * 答题器的样式。
 *
 * **全部用现成 token**（--cc-* / --sp-* / --text-* / --radius-*）：
 * 这一块要和别的页面看着是一套的，另起一套变量就是两套真相。
 * scoped ⇒ 删掉这个组件，样式一起消失，不留残迹。
 *
 * 唯一的例外是阴影：`rgb(31 35 41 / N%)` 这套写法全站在用（base.css 里也是），
 * 没有 shadow token 可用，就沿用同一串，别自己造一个。
 */

/* ---------- 进度条 ---------- */
.runner__track {
  height: 3px;
  margin-bottom: var(--sp-11);
  overflow: hidden;
  background: var(--cc-surface-2);
  border-radius: var(--radius-pill);
}
.runner__track i {
  display: block;
  height: 100%;
  background: var(--cc-primary);
  transition: width 0.25s ease;
}

/* ---------- 顶部操作行 ---------- */
.runner__ops {
  display: flex;
  align-items: center;
  gap: var(--sp-11);
  margin-bottom: var(--sp-12);
}
.runner__pos {
  font-size: var(--text-md);
  color: var(--cc-text-3);
}
.runner__pos b {
  margin-right: 2px;
  font-size: var(--text-xl);
  font-weight: 700;
  color: var(--cc-text-1);
}
.runner__pos i {
  font-style: normal;
}
.runner__done {
  flex: 1;
  font-size: var(--text-sm);
  color: var(--cc-text-3);
}

.runner__plan {
  border: 0;
  background: transparent;
  color: var(--cc-text-3);
  font-family: var(--font-body);
  font-size: var(--text-xs);
  cursor: pointer;
  padding: 2px 6px;
  border-radius: 5px;
}
.runner__plan:hover {
  color: var(--cc-primary);
  background: var(--cc-primary-soft);
}
/* 卷面弹窗：一种题型一行 + 题号网格。实出少于计划 = 宁缺毋滥，标出来但不用警示色 */
.plan__row {
  display: flex;
  align-items: center;
  gap: var(--sp-10);
  padding: var(--sp-6) 0;
  font-size: var(--text-sm);
}
.plan__label {
  flex: 1;
  color: var(--cc-text-1);
}
.plan__count {
  font-variant-numeric: tabular-nums;
  color: var(--cc-text-2);
}
.plan__count.is-short {
  color: var(--cc-warning);
}
.plan__nos {
  color: var(--cc-text-4);
  font-size: var(--text-xs);
}
.plan__grid {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-6);
  margin-top: var(--sp-10);
}
.plan__jump {
  width: 32px;
  height: 32px;
  border: 1px solid var(--cc-border-strong);
  border-radius: 7px;
  background: var(--cc-surface);
  color: var(--cc-text-2);
  font-family: var(--font-body);
  font-size: var(--text-sm);
  font-variant-numeric: tabular-nums;
  cursor: pointer;
}
.plan__jump.is-answered {
  color: var(--cc-primary);
  border-color: var(--cc-primary-border);
  background: var(--cc-primary-soft);
}
.plan__jump.is-cur {
  border-color: var(--cc-primary);
  box-shadow: 0 0 0 1px var(--cc-primary) inset;
  font-weight: 600;
}
.plan__note {
  margin-top: var(--sp-10);
  padding-top: var(--sp-10);
  border-top: 1px solid var(--cc-border);
  font-size: var(--text-xs);
  color: var(--cc-text-3);
  line-height: 1.7;
}
.runner__note {
  margin: 0 0 var(--sp-12);
  font-size: var(--text-sm);
  color: var(--cc-warning);
}

/* ---------- 题目卡 ---------- */
.qcard {
  padding: var(--sp-15);
  background: var(--cc-surface);
  border: 1px solid var(--cc-border);
  /* §4.11(i)：卡片圆角统一 --radius-lg(12)。原来 --radius-xl(16)，
     而同级的 .panel / .content 是 --radius-lg(12) ⇒ **同级容器两档圆角**。 */
  border-radius: var(--radius-lg);
  /* §4.11(n)：同一条卡片阴影串抄了三份（这里 / .score / AppExamView 的 .setup）
     ⇒ 收成 --cc-shadow-card 一支。 */
  box-shadow: var(--cc-shadow-card);
}
/* §4.11(c) 容器查询：**这个组件在两处用**——
   ① 考试页（.exam__body 里，宽度宽）
   ② 详情页右侧的「AI 问答」抽屉里（那是一个 320–1200px 可拖的抽屉，
      你自己拖到 387px ⇒ 内容宽约 347px）
   而这个文件**整文件零 @media、零 @container** ⇒ 塞进 387px 的抽屉时零适配：
   题目卡 padding 32px ⇒ 题面只剩 283px；选项文字宽 ≈ 213px（14px 字 ⇒ ~15 字/行）；
   得分卡 ring 88 + gap 32 ⇒ meta 只剩 ≈163px、chips 折 3 行。
   ⇒ 用**容器查询**（不动组件在页面态的表现）：
   容器是 AppTopicDetailView 的 .ask__body（已挂 container-type: inline-size）。
   ⚠️ 容器查询是按**最近的那个** container-type 祖先算的 ⇒ 组件放进别的容器
      （将来有人在别处挂一个）时这段不生效，页面态那档也不受影响。 */
@container (max-width: 420px) {
  .qcard { padding: var(--sp-12); }
  .score { padding: var(--sp-12); gap: var(--sp-12); }
  .score__ring { width: 64px; height: 64px; }
  .score__num { font-size: var(--text-h); }
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
  transition: var(--cc-transition);
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
  /* 左缘主色条（2026-11-14 修）：浅色主题的 primary-soft（本色 #eef5ff）与卡片白底/灰底
     肉眼几乎同值 ⇒ 多选并列多项时「被蓝色包裹」的信号只剩字母徽章（用户报）。
     左条色 = 主色，四主题自适应；深色主题另有 soft 浓度修正（themes.css 曜夜 0.22）。 */
  box-shadow: inset 2px 0 0 var(--cc-primary);
}
.qopt.is-picked .qopt__label {
  color: var(--cc-surface);
  background: var(--cc-primary);
  border-color: var(--cc-primary);
}
.qopt__text {
  flex: 1;
}

/* ---------- 判断题：两个按钮 ---------- */
.qjudge {
  display: flex;
  gap: var(--sp-9);
}
.qjudge__btn {
  flex: 1;
  padding: var(--sp-11) var(--sp-12);
  font-size: var(--text-md);
  color: var(--cc-text-2);
  background: var(--cc-surface-2);
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: var(--cc-transition);
}
.qjudge__btn:hover:not(:disabled) {
  color: var(--cc-text-1);
  background: var(--cc-surface);
  border-color: var(--cc-border-strong);
}
.qjudge__btn:disabled {
  cursor: default;
}
.qjudge__btn.is-picked {
  color: var(--cc-text-1);
  background: var(--cc-primary-soft);
  border-color: var(--cc-primary);
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
  /* §4.11(i)：同 .qcard ⇒ 卡片圆角统一 --radius-lg */
  border-radius: var(--radius-lg);
  /* §4.11(n)：同 .qcard ⇒ 阴影走 --cc-shadow-card */
  box-shadow: var(--cc-shadow-card);
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
.score__meta {
  min-width: 0;
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
