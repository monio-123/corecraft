/**
 * 随堂测验（**详情页抽屉里就地考当前这条知识**）——有锚、有状态、可恢复（2026-11-13 重构）。
 *
 * ## 锚（TODO-exam-lifecycle §二/§三）
 *
 * 「这条知识当前的测验」= 服务端 `quiz-current` 判定的最新一张 quiz 卷。
 * **点开先查锚**：在答的续答、考完的回看（ExamRunner 只凭 attemptId 就能恢复现场），
 * 只有「没考过」或用户点「重新生成」才出新卷——不再是"每次点击都现新卷"。
 *
 * ## 重新生成的语义：历史累加、锚覆盖
 *
 * 旧卷/旧作答是掌握度证据（挂 questionId），不删不覆盖；新卷落库后锚按「最新」自然挪过去。
 * 重做本卷 = replay 同卷新 attempt（不花 AI，分数可比）；重新生成 = 新卷（花一次 AI）。
 *
 * ## 为什么单独一个文件
 *
 * 这一块（查锚 + 出题 + 状态机）自成一体，详情页只留"调用它"的几行：
 * - 出题：**后台任务**（2026-11-14 起，与考试页共用 `exam.js` 的
 *   `paperTaskPayload()` / `consumePaperTask()`）——人可以走开，关页面都不丢；
 * - 答题：与考试页**共用** `ExamRunner.vue` 组件（恢复/续答/回看它自己认 attempt 状态）；
 * - 状态：试卷/作答**持久化**在服务端，关掉抽屉只是关界面。
 *
 * ## 状态机
 *
 * `idle` → `loading`（查锚 / 建卷，`quizLoading` 的文案区分）
 *        → `task`（出题任务在后台跑——**这一档不再绑着页面**，`quizTask` 从任务列表
 *          派生：走开再回来还在；任务转「待作答」时这一档里出现「作答」按钮）
 *        → `running`（答题器接管：新卷喂 initialQuestions，恢复只喂 attemptId）
 *        → `error`（查锚 / 建卷失败；**重试一律重查锚**——出题失败时卷没落库，
 *          锚上不会有半截卷，重查即安全）
 *
 * ⚠️ **`quizQuiet` = 中性结果不是错误**：「出不了题」通常是内容太少，
 * 不是坏了 ⇒ 不给错误色、不给「重试」按钮（重试也没用）。
 */
import { computed, ref, watch } from 'vue'
import { loadExamSpec, paperTaskPayload, typesBadNote } from './exam'
import { createAttempt, getQuizCurrent, replayAttempt } from '../../api/exam'
import { ackGenTask } from '../../api/genTask'
import { ensureAiConfigLoaded, endpointProblem, resolveEndpoint } from './useAi'
import { genTasks, refreshGenTasksNow } from './useGenTasks'
import { usePaperTask } from './usePaperTask'

/**
 * @param {object} opts
 * @param {() => Array<number>} opts.topicIds 这次考哪几条知识（要能跟着正文变）
 * @param {() => string} [opts.titleOf] 当前这条的标题（卷名带上，考试记录里分得出是哪条的测验）
 * @param {() => string} [opts.topicUpdatedAt] 当前这条的正文更新时间（锚上的卷过没过期）
 * @param {() => void} opts.onBeforeStart 开始出题前的收尾（掐掉正在流的问答）
 * @returns {object} 测验的状态与动作
 */
export function useQuiz({ topicIds, titleOf, topicUpdatedAt, onBeforeStart } = {}) {
  const quizOn = ref(false)
  /** idle / loading / task / running / error */
  const quizPhase = ref('idle')
  /** loading 档的文案（查锚 = 加载中…，出题 = 正在出题…） */
  const quizLoading = ref('加载中…')
  const quizQuestions = ref([])
  /** 计划题量结构（出题任务的 plan）——卷面弹窗的"计划 vs 实出" */
  const quizPlan = ref([])
  /** 当前作答 id：答题器（ExamRunner）按它恢复现场 */
  const quizAttemptId = ref(null)
  const quizNote = ref('')
  const quizError = ref('')
  /** 这一条是"中性结果"不是错误（「出不了题」= 内容太少，不是坏了） */
  const quizQuiet = ref(false)
  /** 正文在出卷之后改过（只提示不拦截，重新生成按钮就在旁边） */
  const quizStale = ref(false)
  /** 测验用的那几项配置。**读一次就存着**，反复开测验不用每次都拉 */
  const quizSpec = ref(null)

  /** 判分时把哪些知识当材料：当前这条 */
  const quizTopicIds = computed(() => (topicIds ? topicIds() : []))

  /**
   * 本条知识的出题任务（在途 / 待作答）——**抽屉就地跟进度的来源**。
   *
   * <p>从任务列表派生（模块级共享的那份 `genTasks`）而不是存一个本地 id：
   * 走开再回来、整页刷新，都能重新找到它。已 ack（succeeded）的不再算——
   * 那时卷已落库，锚（quiz-current）接管恢复。
   */
  const quizTask = computed(() => {
    const tid = Number(quizTopicIds.value[0])
    if (!Number.isFinite(tid)) return null
    return (
      genTasks.value.find((t) => {
        if (t.type !== 'quiz') return false
        if (!['queued', 'running', 'canceling', 'awaiting'].includes(t.status)) return false
        let p = {}
        try {
          p = JSON.parse(t.params || '{}')
        } catch {
          return false
        }
        return Number(p.topicIds?.[0]) === tid
      }) || null
    )
  })

  /**
   * 出题任务的提交/等待/消费/收档骨架（与考试页共用 `usePaperTask`）。
   * 落点只给两个：**任务脱靶的收口走下面 quizTask 的 watch**（它同时覆盖
   * 「本方在跟的」和「走开再回来重新对上的」两种来源，composable 的 onVanish 就不重复给了）。
   */
  const paperTask = usePaperTask({
    async onResult(r) {
      if (r.error) {
        quizQuiet.value = r.quiet
        quizError.value = r.error
        quizPhase.value = 'error'
        return
      }
      const bits = []
      if (r.note) bits.push(r.note)
      const spec = quizSpec.value || (quizSpec.value = await loadExamSpec())
      const bad = typesBadNote(spec.quizTypes.bad)
      if (bad) bits.push(bad)
      quizNote.value = bits.join('，')
      quizQuestions.value = r.questions
      quizPlan.value = r.plan || []
      quizAttemptId.value = r.attemptId
      // 刚出的卷不可能过期
      quizStale.value = false
      quizPhase.value = 'running'
    },
    onFail(message) {
      quizError.value = message
      quizPhase.value = 'error'
    }
  })

  /**
   * 打开随堂测验：**先查锚再决定做什么**（TODO-exam-lifecycle §三的决策树）。
   *
   * 没卷 → 出新卷；在答 → 续答；考完 → 回看（答题器直接给 done 视图）；
   * 有卷没作答过 → 建一次 attempt 直接进答题（与考试记录页同口径）。
   */
  async function openQuiz() {
    if (quizPhase.value === 'loading') return
    quizOn.value = true
    quizPhase.value = 'loading'
    quizLoading.value = '加载中…'
    quizError.value = ''
    quizQuiet.value = false
    quizNote.value = ''
    quizQuestions.value = []
    quizPlan.value = []
    quizAttemptId.value = null
    quizStale.value = false
    onBeforeStart?.()
    try {
      // **本条有在途 / 待作答的出题任务 ⇒ 抽屉直接跟任务**（2026-11-14）：
      // 在跑的显示进度、待作答的给「作答」按钮——都盖过锚（锚上是旧卷或没卷）。
      if (quizTask.value) {
        quizPhase.value = 'task'
        return
      }
      const tid = quizTopicIds.value[0]
      // 锚查询失败（后端还没上这接口 / 瞬时网络）**降级为无锚**：
      // 走旧行为直接出新卷，锚查询的失败不该挡住考试本身
      let cur = null
      try {
        cur = tid ? (await getQuizCurrent(tid))?.data : null
      } catch { /* 降级：当没有卷 */ }
      if (!cur?.paper) {
        // 没考过：这才是"生成新卷"的入口
        await generate()
        return
      }
      quizStale.value = isStale(cur.paper.createTime)
      if (!cur.attempt) {
        // 有卷、从没答过（极小窗口：paper 落了库 attempt 没建成）⇒ 补建直接进答题
        quizAttemptId.value = (await createAttempt(cur.paper.id, {}))?.data
      } else {
        // answering 续答 / partial·completed 回看 / grading·failed 重试判分——答题器自己认
        quizAttemptId.value = cur.attempt.id
      }
      quizPhase.value = 'running'
    } catch (e) {
      if (e?.name === 'AbortError') return
      quizError.value = e?.message || '加载失败'
      quizPhase.value = 'error'
    }
  }

  /** 正文在出卷后改过吗（只比时间，零 DDL；判据只有这一份） */
  function isStale(paperCreateTime) {
    const updated = topicUpdatedAt ? topicUpdatedAt() : ''
    if (!paperCreateTime || !updated) return false
    return new Date(updated).getTime() > new Date(paperCreateTime).getTime()
  }

  /**
   * 出新卷（「开始测验」与「重新生成」同一条路）——**提交后台任务**（2026-11-14）。
   *
   * 配置只读一次（`loadExamSpec`）：题量与范围来自配置，反复开不重拉。
   * 卷名带知识点标题——考试记录里一列「随堂测验」分不清是哪条的。
   * 提交完就进 `task` 档：这一页只显示进度，出题本身在服务端——人可以走开。
   */
  async function generate() {
    quizPhase.value = 'task'
    quizLoading.value = '正在出题…'
    try {
      const spec = quizSpec.value || (quizSpec.value = await loadExamSpec())
      const sections = spec.quizTypes.sections
      if (!sections.length) throw new Error('没有可用题型')
      // ⚠️ **端点必须解析出来并放进任务快照**（2026-11-15 修「还没选好 API」）：
      // 后端建任务时从快照读 endpointId 判端点配全没 —— 快照里没有 ⇒ requireReady(null)
      // ⇒ 每一步都撞同一堵墙（与 AI 归类同一个坑，那条路有 resolveEndpoint 兜底）。
      // 端点列表是异步拉的 ⇒ 先 ensure 加载完再判（没加载就判 = 假结论）。
      await ensureAiConfigLoaded()
      const endpoint = resolveEndpoint()
      if (!endpoint) throw new Error(endpointProblem() || '还没选好 API')
      const title = titleOf?.() || ''
      // 持久化参数：随堂测验（§5.1 证据来源 kind='quiz'，可信度靠它凑来源多样性）
      const paperTitle = title ? `随堂测验 · ${title}` : '随堂测验'
      const { material, params } = paperTaskPayload({
        paper: spec.paper,
        scope: spec.quizScope,
        sections,
        // 随堂自己的难度档（quiz.level，默认基础）；没配回落目录考试的默认档
        level: spec.quizLevel || spec.levels[0],
        extra: '',
        topicIds: quizTopicIds.value,
        kind: 'quiz',
        title: paperTitle,
        endpointId: endpoint.id
      })
      // 提交/等待/消费/收档的骨架在 usePaperTask（promptKey、作废 awaiting 任务
      // 这些不变量都收在那一边，与考试页同一份）
      await paperTask.submit({ type: 'quiz', phaseKey: 'quiz', title: paperTitle, params, material })
    } catch (e) {
      quizError.value = e?.message || '出题失败'
      quizPhase.value = 'error'
    }
  }

  /**
   * **消费出题任务**（任务转「待作答」后的动作；任务卡「作答」跳回来也走这里）：
   * 拿原文 → 解析 → 建卷落库 → 直接进答题。收档/作废都在骨架里（usePaperTask）。
   */
  function answerQuizTask(id) {
    // id 可显式传（任务卡「作答」跳回来带 ?quizTask=）：那一刻任务列表可能还没拉到
    const taskId = Number(id || quizTask.value?.id)
    if (!Number.isFinite(taskId) || quizPhase.value === 'loading') return
    quizPhase.value = 'loading'
    quizLoading.value = '正在建卷…'
    quizError.value = ''
    quizQuiet.value = false
    paperTask.consume(taskId)
  }

  /**
   * 重做本卷：replay 同卷新 attempt（原记录不动，不花 AI）。
   * 换掉 attemptId 即可——答题器 watch 到变化自己 `loadAttempt`（新 attempt 是
   * answering、空作答 ⇒ 从头作答）。
   */
  async function redoQuiz() {
    if (!quizAttemptId.value || quizPhase.value === 'loading') return
    try {
      // ⚠️ 信封解包：`request` resolve 的是 `{code, data, message}`，新 id 在 `data` 里
      const newId = (await replayAttempt(quizAttemptId.value))?.data
      if (!Number.isFinite(Number(newId)) || Number(newId) <= 0) throw new Error('重做没有拿到新作答 id')
      quizAttemptId.value = Number(newId)
    } catch (e) {
      quizError.value = e?.message || '重做失败'
      quizPhase.value = 'error'
    }
  }

  /** 重新生成 = 出新卷（旧卷进历史、锚挪到新卷；见文件头「重新生成的语义」） */
  async function regenerateQuiz() {
    if (quizPhase.value === 'loading') return
    onBeforeStart?.()
    quizQuestions.value = []
    quizAttemptId.value = null
    quizError.value = ''
    quizQuiet.value = false
    // ⚠️ 先切出 task 档再作废旧任务：作废会让 quizTask 脱靶，而脱靶 watch
    // 只对 task 档收口——不先切走，抽屉会在「重新生成」半路被当成分外人取消而关掉。
    quizPhase.value = 'loading'
    quizLoading.value = '正在出题…'
    // ⚠️ **作废在途/待作答的旧任务**（2026-11-15 修死循环）：不作废的话
    // quizTask 会一直命中它，抽屉永远走不进新生成。
    if (quizTask.value) {
      try { await ackGenTask(quizTask.value.id) } catch { /* 作废失败也不挡生成 */ }
      await refreshGenTasksNow()
    }
    generate()
  }

  /** 回到聊天（只关界面：任务在服务端、试卷与作答在库里，下次点开各自恢复） */
  function closeQuiz() {
    quizOn.value = false
    quizPhase.value = 'idle'
    quizQuestions.value = []
    quizPlan.value = []
    quizAttemptId.value = null
    quizStale.value = false
  }

  /**
   * 出题任务**脱靶**（在任务中心被取消 / 在别处消费成 succeeded）⇒ quizTask 变 null。
   * `task` 档只跟着 quizTask 走，它没了抽屉就会永远停在「正在出题…」⇒ 按关闭处理
   * （下次打开重查锚，锚上有卷就恢复现场）。与考试页 `AppExamView` 对账 watch 同一口径。
   */
  watch(quizTask, (t) => {
    if (!t && quizPhase.value === 'task') closeQuiz()
  })

  return {
    quizOn,
    quizPhase,
    quizLoading,
    quizQuestions,
    quizPlan,
    quizAttemptId,
    quizNote,
    quizError,
    quizQuiet,
    quizStale,
    quizTopicIds,
    quizTask,
    openQuiz,
    answerQuizTask,
    redoQuiz,
    regenerateQuiz,
    closeQuiz
  }
}
