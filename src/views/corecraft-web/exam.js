/**
 * 考试的前端能力：**参数（从配置读 → 用户可改 → 拼成 prompt）** + **出卷链路
 * （问模型 → 解析 → 归属 → 落库 → 建作答）** + **入口（开考试页）**。
 *
 * ## 出卷链路（设计 §4.2 / §12）
 *
 * 出完题不是终点：题目 + 归属快照落库（paper），服务端发稳定的
 * `paperId/questionId`，再建好 attempt 进答题——证据、回看、重做全挂这些 id。
 * 证据归属是**出题后独立的第二步 AI 调用**：出题不背归属硬约束（题目质量优先），
 * 归属失败不阻塞考试，事后可补做。归属输出格式与解析器在 `examParse.js` 同一处。
 *
 * ## 为什么要有这个文件
 *
 * 以前「出题规范」是一整段文本，题量、题型、难度全写在里面。
 * 用户想改「单选 5 道改成 3 道」就得去改那段文字，而**前端解析器里还有一份
 * 同样的题号映射**（1-5 单选 / 6-10 多选…）—— 两处不会自动同步，
 * 改了一处就出现"模型出的题号和解析器认的题型对不上"，而且**不报错**，只是悄悄乱。
 *
 * ## 这一版怎么解决
 *
 * **题量结构是数据，不是文字**。它同时喂三个地方：
 * 1. 拼给模型的那段「题量结构：第 1-5 题：单选 …」
 * 2. 前端解析器（`parsePaper(text, ranges)` 按它推题号 → 题型）
 * 3. 考试页的默认值（来自系统配置）
 *
 * 三处共用**同一份**数据 ⇒ 不可能漂移。
 * 模板里因此**一个数字都不许有**（数字都在 `ai.prompt.exam.types` 里）。
 *
 * ## 配置项（在「系统配置 → 系统 AI 配置 → AI 提示词 → 考试」下）
 *
 * | 键 | 格式 | 作用 |
 * |---|---|---|
 * | `ai.prompt.exam.paper` | 一段文本 | 规范**模板**：只写「怎么写题」（两种出题共用这一份） |
 * | `ai.prompt.exam.grade` | 一段文本 | 判分规范（两种出题共用这一份） |
 * | `ai.prompt.exam.types` | 每行 `题型\|道数\|分值` | 目录考试的题量结构（**分值可省 ⇒ 1 分/题**） |
 * | `ai.prompt.exam.scope` | 一段文本 | 目录考试**考什么**：范围是内部全部内容，优先知识点相结合 |
 * | `ai.prompt.exam.quiz.types` | 每行 `题型\|道数\|分值` | 随堂测验的题量结构 |
 * | `ai.prompt.exam.quiz.scope` | 一段文本 | 随堂测验**考什么**：主要考当前这条知识 |
 * | `ai.prompt.exam.level` | 每行 `档位名\|这个档位考什么` | 难度档位（**第一行是默认档**） |
 * | `ai.prompt.exam.extra` | 一段文本 | 考生自己加的要求（默认空） |
 *
 * 存**行格式**不存 JSON：配置页是个纯文本框，管理员要能一眼看懂一行是什么。
 */
import { getMyConfig } from '../../api/config'
import { getTopicIndex } from '../../api/knowledge'
import { createAttempt, getPaper, savePaper } from '../../api/exam'
import { getGenTaskDetail } from '../../api/genTask'
import { ask } from './useAi'
import {
  EXAM_TYPES,
  markOf,
  typeKeyOf,
  DEFAULT_SCORE,
  totalOf,
  rangesOf,
  parsePaper,
  isNoPaper,
  paperNote,
  buildAttributionPrompt,
  buildAttributionText,
  parseAttribution
} from './examParse'

/** 考试这一组配置键的命名空间。**键全从这里长出来，别再各处抄一遍 */
const NS = 'ai.prompt.exam.'

/** 配置键（都在 `ai.prompt.exam.` 下面） */
export const CFG_KEYS = {
  paper: NS + 'paper',
  grade: NS + 'grade',
  types: NS + 'types',
  scope: NS + 'scope',
  level: NS + 'level',
  extra: NS + 'extra',
  quizTypes: NS + 'quiz.types',
  quizScope: NS + 'quiz.scope',
  quizLevel: NS + 'quiz.level'
}

/**
 * 读考试参数。
 *
 * 走 `/sys/config/mine?prefix=ai.prompt.exam.`（登录即可读）：
 * `values` = 生效值（我的覆盖 > 系统默认），`defaults` = 系统默认原值。
 *
 * **一次读全部**（前缀是 `ai.prompt.exam.`，随堂测验那几项也在这个前缀下）：
 * 两种出题共用「写法规范 / 判分规范 / 难度档位」，各自只有「范围 + 题量」不同。
 *
 * @returns {Promise<{paper: string, grade: string, scope: string, types: Object,
 *   quizScope: string, quizTypes: Object, levels: Array, extra: string}>}
 */
export async function loadExamSpec() {
  const res = await getMyConfig(NS)
  const v = res?.data?.values || {}
  return {
    paper: v[CFG_KEYS.paper] || '',
    grade: v[CFG_KEYS.grade] || '',
    scope: v[CFG_KEYS.scope] || '',
    types: parseTypes(v[CFG_KEYS.types]),
    quizScope: v[CFG_KEYS.quizScope] || '',
    quizTypes: parseTypes(v[CFG_KEYS.quizTypes]),
    levels: parseLevels(v[CFG_KEYS.level]),
    // 随堂自己的难度档（2026-11-14）：学完立刻的自检以基础为主，不再蹭目录
    // 考试的默认档（level 第一行=进阶，单条小材料下两个指令打架）。配置没配
    // 就回落目录考试的默认档——旧行为兜底
    quizLevel: parseLevels(v[CFG_KEYS.quizLevel])[0] || null,
    extra: v[CFG_KEYS.extra] || ''
  }
}

/**
 * 解析题量结构：每行 `题型|道数|分值`。
 *
 * 认不出来的行**丢掉并计数**（`bad`）：静默当默认值 = 用户改了没生效还不自知。
 *
 * **分值可省**（老配置只有两段）⇒ 默认 1 分/题，一题一分，和加列之前一个样。
 *
 * @returns {{sections: Array<{type: string, count: number, score: number}>, bad: number}}
 */
function parseTypes(text) {
  const sections = []
  let bad = 0
  for (const line of String(text || '').split('\n')) {
    const t = line.trim()
    if (!t) continue
    const [name, countText, scoreText] = t.split('|')
    const type = typeKeyOf(name)
    const count = Math.floor(Number(countText))
    const score = scoreText === undefined ? DEFAULT_SCORE : Number(scoreText)
    if (!type || !Number.isFinite(count) || count <= 0 || !Number.isFinite(score) || score <= 0) {
      bad++
      continue
    }
    sections.push({ type, count, score })
  }
  return { sections, bad }
}

/**
 * 解析难度档位：每行 `档位名|这个档位考什么`，**第一行是默认档**。
 *
 * @returns {Array<{name: string, desc: string}>}
 */
function parseLevels(text) {
  const levels = []
  for (const line of String(text || '').split('\n')) {
    const t = line.trim()
    if (!t) continue
    const [name, desc] = t.split('|')
    if (!name || !name.trim()) continue
    levels.push({ name: name.trim(), desc: (desc || '').trim() })
  }
  return levels
}

/** 总题数：**实现在 `examParse.js`**（和解析器那份是同一个数），这里只是转出去 */
export { totalOf }

/**
 * 满分：每种题型的 **道数 × 每题分值**，加起来。
 *
 * <p>和 `totalOf`（总题数）是两件事：一个数"多少道"，一个数"多少分"。
 * 计划里配的是**计划满分**；实际这张卷的满分按**实际出的题**算
 * （模型少出题时，卷子就按出的那些题定分，见 `AppExamView` 的 `score`）。
 */
export function maxScoreOf(sections) {
  return (sections || []).reduce((n, s) => n + s.count * s.score, 0)
}

/**
 * 拼最终 prompt：**题量结构 + 出题范围 + 规范模板 + 难度 + 考生自己的要求**。
 *
 * ⚠️ **顺序有讲究**：
 * 1. 题量结构在最前面（"出多少道、怎么编号"）
 * 2. 范围紧跟着（"这次考哪一块"）—— 范围是**上限与取舍**的前提，
 *    放在模板前面，模板里那些"怎么写题"的细则才知道自己在给谁写
 * 3. 模板在后（"怎么写题"）—— 模板里说"考点个数和题量结构的总题数一样"，
 *    指的就是**上面**那份
 *
 * @param {Object} p
 * @param {string} p.paper 规范模板
 * @param {string} p.scope 出题范围（考什么）。可空
 * @param {Array} p.ranges 题号区间（`rangesOf` 的结果）
 * @param {Object} p.level 选中的难度档 `{name, desc}`
 * @param {string} p.extra 考生自己加的要求（可空）
 * @returns {string}
 */
export function buildPaperPrompt({ paper, scope, ranges, level, extra }) {
  const list = ranges || []
  const total = totalOf(list)
  const parts = []

  // 1. 题量结构。**是上限不是指标**（宁缺毋滥：内容太少就少出题）
  const lines = [`最多出 ${total} 道题考读者。题量结构（按这个顺序编号）：`]
  for (const r of list) {
    lines.push(`第 ${r.from}-${r.to} 题：${markOf(r.type)}`)
  }
  lines.push(`最多 ${total} 道题。`)
  parts.push(lines.join('\n'))

  // 2. 出题范围
  const scopeText = String(scope || '').trim()
  if (scopeText) parts.push(scopeText)

  // 3. 规范模板
  if (paper) parts.push(paper.trim())

  // 4. 难度：整卷一档（用户定的：难度不是按题型分，是整卷一个档）
  if (level?.name) {
    parts.push(`难度要求：整卷按「${level.name}」出题${level.desc ? ' —— ' + level.desc : ''}。`)
  }

  // 5. 考生自己的要求
  const extraText = String(extra || '').trim()
  if (extraText) parts.push(`考生自己的要求：\n${extraText}`)

  return parts.join('\n\n')
}

/** 题型下拉的可选项（就是解析器认的那五种，加不了别的） */
export const TYPE_OPTIONS = Object.entries(EXAM_TYPES).map(([value, t]) => ({
  value,
  label: t.label
}))

/**
 * 出题走**后台任务**（2026-11-14 起，考试页与随堂测验共用这一段）：
 * 提交时拼好提示词当材料（模板/解析器全在前端这一份），任务在服务端调模型、
 * 原文存进步骤；点「作答」那一刻才由 {@link consumePaperTask} 解析 + 归属 + 建卷。
 * 与「AI 归类：建议存任务、采纳才写库」同构——人可以走开，关页面都不丢。
 * （原来的 generatePaper 是前端等模型、组件一卸载就 abort，已删。）
 *
 * @param {Object} p 同 consumePaperTask 消费端要的那些：paper/scope/sections/level/extra/
 *   topicIds/kind/categoryId/title/scopeName
 * @returns {{material: string, params: object}} 建任务用的材料 + 参数快照
 */
export function paperTaskPayload({ paper, scope, sections, level, extra, topicIds, kind, categoryId, title, scopeName, endpointId }) {
  return {
    material: buildPaperPrompt({ paper, scope, ranges: rangesOf(sections), level, extra }),
    // 消费端要用的全部快照：题量结构（含分值）在提交那一刻冻结——「分值随卷定型」，
    // 之后配置怎么改，这张卷按提交时的结构解析、记分。
    // ⚠️ **endpointId 必须在快照里**（2026-11-15 修「还没选好 API」）：
    // 后端建任务时从快照读端点（`endpointIdOf`）判「配全没」——快照里没有 ⇒
    // `requireReady(null)` ⇒ 每一步都撞同一堵墙（与 AI 归类同一坑，
    // 那条路有 `resolveEndpoint` 兜底，出题这条路漏了）。
    params: { topicIds, kind, categoryId, title, scopeName, sections, level, extra: extra || '', endpointId: endpointId ?? null }
  }
}

/**
 * 消费一个出题任务：拿步骤里的原文 → 解析成题 → 证据归属 → 落库 → 建作答。
 * 返回形状与原 generatePaper 相同（quiet / error / note / paperId / attemptId）。
 *
 * @param {number} taskId 任务 id（详情接口现拉——列表不带回步骤原文）
 * @returns {Promise<{questions: Array, plan: Array, note: string, quiet: boolean, error: string,
 *   paperId: number|null, attemptId: number|null}>}
 */
export async function consumePaperTask(taskId) {
  const detail = (await getGenTaskDetail(taskId))?.data || {}
  // 没跑完 / 没跑成：如实说（任务卡「作答」只在待作答时出现，这里是兜住
  // 「人守在本页时任务失败」和「拿旧 URL 早早跳回来」两条路）
  if (['queued', 'running', 'canceling'].includes(detail.status)) {
    return { questions: [], plan: [], note: '', quiet: false, error: '任务还在出题', paperId: null, attemptId: null }
  }
  if (detail.status === 'failed') {
    return { questions: [], plan: [], note: '', quiet: false, error: detail.error || '出题失败', paperId: null, attemptId: null }
  }
  let params = {}
  try {
    params = JSON.parse(detail.params || '{}') || {}
  } catch {
    // params 坏了按空处理：下面拿不到 sections/原文，自然报「没出成」
  }
  const step = (detail.steps || []).find((s) => s.result)
  let paperText = ''
  try {
    paperText = JSON.parse(step?.result || '{}').answer || ''
  } catch {
    // 同上
  }
  const sections = params.sections || []
  const ranges = rangesOf(sections)
  if (!paperText) {
    return { questions: [], plan: [], note: '', quiet: false, error: '这个任务没有出题结果', paperId: null, attemptId: null }
  }
  const { questions, problems } = parsePaper(paperText, ranges)
  if (!questions.length) {
    // 「出不了题」= 内容太少（正常，宁缺毋滥）；没解析出来 = 模型没按规范写（异常）
    const quiet = isNoPaper(paperText)
    return { questions: [], plan: [], note: '', quiet, error: quiet ? '出不了题' : '这次没解析出题目', paperId: null, attemptId: null }
  }

  // 每题分值随卷定型（提交那一刻的题量结构里配的；没配这一型就默认 1 分）——
  // 存库后卷子和题量结构脱钩，回看历史卷按的还是当年那张卷的分值
  const scoreMap = Object.fromEntries(sections.map((s) => [s.type, s.score]))
  for (const q of questions) q.maxScore = scoreMap[q.type] ?? DEFAULT_SCORE

  // 证据归属（设计 §4.2）：失败不阻塞。这一步仍是前端的一次 AI 调用——
  // 归属的材料依赖刚解析出来的题，没法预拼进任务。
  const attrib = await attributePaper({ questions, topicIds: params.topicIds })

  const { paperId, attemptId } = await persistPaper({
    questions,
    attrib,
    sections,
    level: params.level,
    extra: params.extra,
    topicIds: params.topicIds,
    kind: params.kind,
    categoryId: params.categoryId,
    title: params.title,
    scopeName: params.scopeName
  })

  return { questions, plan: ranges, note: paperNote(questions, problems, ranges), quiet: false, error: '', paperId, attemptId }
}

/**
 * 出题后的证据归属（设计 §4.2「出题后独立做证据归属」）。
 *
 * <p>候选 = 这次出题范围里的知识点（带 id 和标题，模型按 id 认领）。
 * **失败返回「归属失败」而不是抛**：试卷照常生成，没归属的题不进掌握度，
 * 归属可以稍后补做（`saveAttribution` 会按新归属重建证据）。
 * 中止（AbortError）照抛——用户连卷子都不要了。
 */
async function attributePaper({ questions, topicIds, signal }) {
  try {
    const ids = new Set((topicIds || []).map(Number).filter((n) => Number.isFinite(n) && n > 0))
    const rows = (await getTopicIndex({}))?.data || []
    const candidates = rows.filter((t) => ids.has(Number(t.id))).map((t) => ({ id: Number(t.id), title: t.title }))
    if (!candidates.length) return { ok: false, itemsByNo: {} }
    const text = await ask(buildAttributionPrompt({ candidates }), {
      topicIds: [...ids],
      material: buildAttributionText(questions),
      signal
    })
    return { ok: true, itemsByNo: parseAttribution(text, ids) }
  } catch (e) {
    if (e?.name === 'AbortError') throw e
    return { ok: false, itemsByNo: {} }
  }
}

/**
 * 落库：paper（题目快照 + 归属快照 + 范围快照 + 设置快照）→ 回读发号 → attempt。
 *
 * <p>**题目 id 由服务端发号** ⇒ 存完回读一次卷子拿 `questionNo → questionId` 的映射，
 * 挂到内存里的题上（前面解析出来的题只有卷内题号，那是会重排的）。
 */
async function persistPaper({ questions, attrib, sections, level, extra, topicIds, kind, categoryId, title, scopeName }) {
  const paperId = (
    await savePaper({
      kind: kind || 'exam',
      categoryId: categoryId ?? null,
      title: title || '',
      scopeSnapshot: { name: scopeName || '', topicIds: topicIds || [] },
      settingsSnapshot: { sections, level: level?.name || '', extra },
      questions: questions.map((q) => {
        const items = attrib.itemsByNo[q.no] || []
        return {
          questionNo: q.no,
          type: q.type,
          maxScore: q.maxScore,
          // 题目快照：题干/选项/答案要点全在（指纹由服务端按 payload.stem 算）
          questionPayload: {
            stem: q.stem,
            options: q.options,
            answer: q.answer,
            answerPoints: q.answerPoints,
            isOpen: q.isOpen
          },
          // 归属快照：items 缺 rubricKey ⇒ 服务端按整题（__question__）——
          // 和判分回填的整题 rubric 对得上，证据才不会两头对不上键
          attributionPayload: { items },
          // attributed=认了归属 / unattributed=判了不归属 / failed=归属调用失败（可补做）
          attributionStatus: attrib.ok ? (items.length ? 'attributed' : 'unattributed') : 'failed'
        }
      })
    })
  ).data
  const saved = (await getPaper(paperId))?.data
  const idByNo = new Map((saved?.questions || []).map((sq) => [sq.questionNo, sq.id]))
  for (const q of questions) q.questionId = idByNo.get(q.no)
  const attemptId = (await createAttempt(paperId, {})).data
  return { paperId, attemptId }
}

/**
 * 配置里有几行题型没认出来（认不出来的行**丢掉并计数**，见 `parseTypes`）。
 *
 * <p>**只有这一份文案**：考试页单独提示、随堂测验并进备注，说的是同一件事。
 */
export function typesBadNote(bad) {
  return bad ? `配置里有 ${bad} 行题型没认出来，已忽略` : ''
}

/**
 * 卷子类型 → 显示名。**唯一一份**（考试记录页的筛选与列表行共用）。
 *
 * <p>`review`（每日复习卷）是阶段 C 才会出现的 kind，**先占位**：
 * 记录页列表里真冒出它时不该显示裸 key。
 */
export const KINDS = {
  exam: '目录考试',
  quiz: '随堂测验',
  review: '每日复习'
}

/** 卷子类型 → 显示名（认不出来就原样显示，别吞掉） */
export function kindLabel(kind) {
  return KINDS[kind] || String(kind || '')
}

/**
 * 开考试页的**新标签页**：URL 怎么拼、弹窗被拦了怎么办，**只有这一处**。
 *
 * <p>原来 `openExamTab` 自己带降级，考试记录页要开「回看」那种 URL 时
 * 就得再抄一遍 window.open + 降级 ⇒ 收敛到这个口子（重复 2 处即收敛）。
 *
 * @param {string} query 已拼好的查询串（不含 `?`）
 * @param {string} key 窗口名的后缀：同一个 key 复用同一个标签页，不开一堆
 */
function openInExamTab(query, key) {
  const url = `/exam?${query}`
  // 返回 null = 被浏览器拦了（弹窗拦截 / 用户禁了）⇒ 退化成普通新标签页再试一次
  const tab = window.open(url, `exam-${key}`)
  if (!tab) window.open(url, '_blank')
}

/**
 * 打开考试页：**当前浏览器的新标签页**。
 *
 * @param {Array<number>|string} ids 这次考哪几条（= 当前筛选出来的那些）
 * @param {string} [name] 范围的名字（目录名 / 「全部」），可空
 * @param {string} key 窗口名的后缀：同一个 key 复用同一个标签页，不开一堆
 * @param {number} [categoryId] 选了目录时的目录 id（考完退回那个目录的列表页）
 */
export function openExamTab(ids, name, key, categoryId) {
  const list = Array.isArray(ids) ? ids.join(',') : String(ids ?? '')
  const parts = [`ids=${list}`]
  if (name) parts.push(`name=${encodeURIComponent(name)}`)
  if (categoryId) parts.push(`cat=${categoryId}`)
  openInExamTab(parts.join('&'), key)
}

/**
 * 打开一次作答的**回看 / 续答**（考试记录页）：`/exam?attempt=…&name=…`。
 *
 * <p>AppExamView 认 URL 里的 `attempt` 自己恢复现场（答题器 `loadAttempt`）——
 * 交过卷的是逐题回看，没交卷的接着答，**同一个入口不用分辨**（答题器认状态）。
 *
 * @param {number} attemptId 作答 id
 * @param {string} [name] 卷名（顶栏显示），可空
 * @param {string} [key] 窗口名的后缀（默认 'review'：记录页点哪行都复用同一个回看标签页）
 */
export function openExamAttemptTab(attemptId, name, key = 'review') {
  const parts = [`attempt=${attemptId}`]
  if (name) parts.push(`name=${encodeURIComponent(name)}`)
  openInExamTab(parts.join('&'), key)
}
