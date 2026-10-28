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
          <!-- 答题与判分的过程、分数环都在答题器里（**这一块只有两个页面级的按钮**）：
               答完了才给"重新出卷 / 回到目录"。 -->
          <template v-if="phase === 'running' && runnerPhase === 'done'">
            <el-button @click="backToSettings">重新出卷</el-button>
            <el-button type="primary" @click="backToTopic">
              {{ categoryId ? '回到目录' : '回到列表' }}
            </el-button>
          </template>
        </div>
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
      <div v-else-if="phase === 'loading'" class="kb-center">
        <el-icon class="is-loading"><Loading /></el-icon>
        <p>正在出卷…</p>
      </div>

      <!-- ========== 出卷失败 ========== -->
      <div v-else-if="phase === 'error'" class="kb-center">
        <p class="kb-error" :class="{ 'is-quiet': quiet }">{{ error }}</p>
        <el-button v-if="!quiet" @click="generate">重试</el-button>
      </div>

      <!-- ========== 答题 / 判分 / 结果 ==========
           这三段是**答题器**的活（与详情页抽屉里的随堂测验共用同一个组件），
           这里只负责把卷子递进去。 -->
      <ExamRunner
        v-else
        :questions="questions"
        :sections="sections"
        :note="note"
        :topic-ids="topicIds"
        @phase="runnerPhase = $event"
      />
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
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Loading } from '@element-plus/icons-vue'
import { ensureAiConfigLoaded } from './useAi'
import { categoryLink } from '../../utils/categoryNav'
import { DEFAULT_SCORE } from './examParse'
import { generatePaper, loadExamSpec, maxScoreOf, totalOf, typesBadNote, TYPE_OPTIONS } from './exam'
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
 * 页面阶段：**只管出卷这一段**。答题与判分归答题器（`ExamRunner`）管。
 * idle（出卷设置）/ loading（出卷中）/ error（出卷失败）/ running（答题器接管）
 */
const phase = ref('idle')
const error = ref('')
/** 这一条是"中性结果"不是错误：「出不了题」= 内容太少，不是坏了 */
const quiet = ref(false)
/** 这次考的东西叫什么（= 目录名，URL 带过来） */
const scopeTitle = ref('')
const questions = ref([])
/** 卷面和规范对不上的地方（哪几道没解析出来、哪种题型少了） */
const note = ref('')
/** 答题器内部的阶段（ready / grading / done）—— 顶栏要据此换按钮 */
const runnerPhase = ref('ready')

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
/** 出题范围（配置里的那段文本）：这次考哪一块 */
const scopeRule = ref('')
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
  if (!topicIds.value.length) {
    error.value = '没有指定要考的知识'
    phase.value = 'error'
  }
})

onBeforeUnmount(() => abort?.abort())

// ==================== 出卷设置 ====================

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

// ==================== 出卷 ====================

/**
 * 出卷。**走现成的问答通道**，提示词是前端拼的
 * （题量结构 + 出题范围 + 模板 + 难度 + 附加要求）。
 *
 * 出卷那段（拼 prompt / 问模型 / 解析 / 空卷分流）在 `exam.js` 的 `generatePaper()`，
 * **与详情页随堂测验共用**；这里只管本页的阶段状态。
 *
 * 题目解析不出来时**不整卷作废**：能解析几道算几道（见 `examParse.js`）。
 */
async function generate() {
  if (!topicIds.value.length || !total.value) return
  abort?.abort()
  abort = new AbortController()
  phase.value = 'loading'
  error.value = ''
  quiet.value = false
  note.value = ''
  questions.value = []
  try {
    const r = await generatePaper({
      paper: paperTemplate.value,
      scope: scopeRule.value,
      sections: sections.value,
      level: levels.value.find((l) => l.name === levelName.value),
      extra: extra.value,
      topicIds: topicIds.value,
      signal: abort.signal
    })
    if (r.error) {
      quiet.value = r.quiet
      error.value = r.error
      phase.value = 'error'
      return
    }
    note.value = r.note
    questions.value = r.questions
    phase.value = 'running'
  } catch (e) {
    if (e?.name === 'AbortError') return
    error.value = e?.message || '出卷失败'
    phase.value = 'error'
  }
}

/** 回到出卷设置（重新出一份） */
function backToSettings() {
  phase.value = 'idle'
  error.value = ''
  quiet.value = false
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

/* ---------- 主体 ---------- */
.exam__body {
  max-width: 840px;
  margin: 0 auto;
  padding: var(--sp-15) var(--sp-15) var(--sp-16);
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

/* ---------- 出卷中 / 失败 ----------
 * 这两条在 `assets/kb.css`（`.kb-center` / `.kb-error`）——
 * 详情页随堂测验用的是同一份，两边各写一遍会漂（原来图标字号就差 2px）。 */

</style>
