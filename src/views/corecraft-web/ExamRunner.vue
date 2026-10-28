<template>
  <div class="runner">
    <!-- ==================== 答题 / 判分中 ==================== -->
    <template v-if="phase !== 'done'">
      <!-- 进度条走的是"答了几道"，不是"看到第几道"（可以来回跳，位置会骗人） -->
      <div class="runner__track">
        <i :style="{ width: progress + '%' }"></i>
      </div>

      <div class="runner__ops">
        <span class="runner__pos">
          <b>{{ cur + 1 }}</b><i>/ {{ questions.length }}</i>
        </span>
        <span class="runner__done">已答 {{ answeredCount }}</span>
        <el-button size="small" :loading="phase === 'grading'" @click="submit">交卷</el-button>
      </div>

      <!-- 卷面和规范对不上的地方：只报事实 -->
      <p v-if="note" class="runner__note">{{ note }}</p>

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
        <div v-else-if="isJudge(curQ.type)" class="qjudge">
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

        <el-input
          v-else-if="isFill(curQ.type)"
          v-model="curAnswer.typed"
          :disabled="phase !== 'ready'"
          placeholder="填答案"
        />
        <el-input
          v-else
          v-model="curAnswer.typed"
          type="textarea"
          :rows="5"
          :disabled="phase !== 'ready'"
          placeholder="作答"
        />

        <footer class="qcard__foot">
          <el-button :disabled="cur === 0" @click="goPrev">上一道</el-button>
          <el-button v-if="!isLast" type="primary" @click="goNext">下一道</el-button>
          <el-button v-else type="primary" :loading="phase === 'grading'" @click="submit">
            交卷判分
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
  </div>
</template>

<script setup>
/**
 * 答题器：**一屏一题 → 交卷 → 判分 → 逐题讲解**。
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
 * 管：作答状态、当前第几道、交卷调 AI 判分、结果区。
 * **不管**：出卷（谁出、用什么范围、几道题）—— 那是调用方的事，
 * 出完把 `questions` 递进来就行。
 *
 * ## 零存储
 *
 * 题目和答题记录都只活在内存里，刷新就没了（跟考试页原来一样）。
 */
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ask } from './useAi'
import {
  buildPaperText,
  describeAnswer,
  DEFAULT_SCORE,
  isFillType,
  isJudgeType,
  isMultiType,
  JUDGE_CHOICES,
  parseGrade,
  paperScore,
  verdictClassOf,
  VERDICTS
} from './examParse'
import { CFG_KEYS } from './exam'

defineOptions({ name: 'ExamRunner' })

const props = defineProps({
  /** 解析出来的题（`parsePaper` 的结果） */
  questions: { type: Array, default: () => [] },
  /** 题量结构（`题型|道数|分值` 解析出来的），算分要用 */
  sections: { type: Array, default: () => [] },
  /** 卷面和规范对不上的地方（只报事实） */
  note: { type: String, default: '' },
  /** 判分时把哪些知识当材料。**不给就只发整卷**（判卷靠标准答案，不靠原文） */
  topicIds: { type: Array, default: () => [] }
})

/** 阶段变化告诉调用方（考试页要据此换顶栏，抽屉要据此换按钮） */
const emit = defineEmits(['phase'])

/** 内部阶段：答题中 / 判分中 / 出结果了 */
const phase = ref('ready')
/** 作答状态，按题号记：`{ picked: [选项下标], typed: '输入的文字', judge: '正确|错误' }` */
const answers = reactive({})
/** 判定结果，按题号记 */
const grades = reactive({})
/** 当前第几道（一屏一题） */
const cur = ref(0)

/** 出卷中的中止器：离开页面就掐掉，别让请求在后台继续烧 token */
let abort = null

onBeforeUnmount(() => abort?.abort())

/**
 * 换一份卷子就重置作答状态。
 *
 * **答题记录挂在组件自己身上**：换卷 = 清空（题目换了，旧的作答对不上）。
 * 立即执行一次（`immediate`）是为了首帧就有 `answers`，
 * 模板里 `v-model="curAnswer.typed"` 直接绑它。
 */
watch(
  () => props.questions,
  (list) => {
    for (const k of Object.keys(answers)) delete answers[k]
    for (const k of Object.keys(grades)) delete grades[k]
    cur.value = 0
    for (const q of list || []) answers[q.no] = { picked: [], typed: '', judge: '' }
    setPhase('ready')
  },
  { immediate: true }
)

function setPhase(next) {
  phase.value = next
  emit('phase', next)
}

// ==================== 题目 ====================

const curQ = computed(() => props.questions[cur.value] || null)
const isLast = computed(() => cur.value === props.questions.length - 1)

/**
 * 当前题目的作答对象。**没有就现造一个**——
 * 模板里 `v-model` 直接绑它，绑一个 undefined 会抛。
 */
const curAnswer = computed(() => {
  const no = curQ.value?.no
  if (no == null) return null
  if (!answers[no]) answers[no] = { picked: [], typed: '', judge: '' }
  return answers[no]
})

function goNext() {
  if (cur.value < props.questions.length - 1) cur.value++
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

/** 判断题：两个按钮，选一个就定下来（再点同一个 = 取消？） */
function pickJudge(no, choice) {
  const a = answers[no]
  if (!a) return
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

const answeredCount = computed(() => props.questions.filter(isAnswered).length)

/**
 * 进度条走"答了几道"：位置会骗人（可以来回跳），答没答不会。
 *
 * ⚠️ **分母是实际解析出来的题数**，不是计划的题数。
 * 解析器是**逐块容错**的（坏的那道丢掉、其余照常）—— 计划 25 道、实际出了 24 道时，
 * 用计划数当分母，进度条会永远停在 96%，看着像"还有一道没答"。
 */
const progress = computed(() =>
  props.questions.length ? Math.round((answeredCount.value / props.questions.length) * 100) : 0
)

// ==================== 交卷判分 ====================

/**
 * 交卷：把整卷 + 考生作答交给 AI 判。
 *
 * **标准答案一起给**——判分是"对着标准答案判"，不是"重新答一遍"。
 * 开放题（概念题 / 面试题）没有唯一答案，规范要求 AI 只给"部分对"这类判定 +
 * 讲解，不许一刀切判对错。
 */
async function submit() {
  if (!props.questions.length || phase.value === 'grading') return
  // 还有没答的就问一句：卷子只在内存里，交上去这一遍就定型了
  const left = props.questions.length - answeredCount.value
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
  setPhase('grading')
  let gradeText = ''
  try {
    gradeText = await ask(
      '判卷',
      {
        topicIds: props.topicIds,
        material: buildPaperText(props.questions, answers),
        questionKey: CFG_KEYS.grade,
        signal: abort?.signal
      }
    )
  } catch (e) {
    // 判分失败**不回退卷子**：用户答的东西还在界面上，让他能直接重试
    ElMessage.error(e?.message || '判分失败')
    setPhase('ready')
    return
  }
  const parsed = parseGrade(gradeText)
  for (const k of Object.keys(grades)) delete grades[k]
  Object.assign(grades, parsed)
  setPhase('done')
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
 * 这里**不抄第二份**。
 */
const score = computed(() => paperScore(props.questions, grades, props.sections))

/** 这道题值多少分（题量结构里配的） */
function scoreOf(q) {
  return score.value.items[q.no]?.weight ?? DEFAULT_SCORE
}

/** 这道题得了多少分（结果卡上显示） */
function gainedOf(q) {
  return score.value.items[q.no]?.points ?? 0
}

/** 分数：整数就写整数，有半分开头就留一位小数（部分对 = 一半分 ⇒ 会出现 .5） */
function fmtScore(n) {
  return Number.isInteger(n) ? String(n) : n.toFixed(1)
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
  for (const q of props.questions) {
    const hit = VERDICTS.find((v) => v.verdict === gradeOf(q)?.verdict)
    if (hit) out[hit.key] += 1
    else out.none += 1
  }
  return out
})

// ==================== 杂项 ====================

function questionOf(no) {
  return props.questions.find((q) => q.no === no) || null
}

/** 题型判断的三个出口（模板里直接调） */
const isFill = isFillType
const isJudge = isJudgeType
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
  transition: var(--cc-transition), border-color 0.15s;
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
