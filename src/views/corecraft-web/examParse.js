/**
 * 考试卷的解析：**按我们自己定的规范解析**，不是猜模型写了什么。
 *
 * ## 为什么这次可以写死
 *
 * 一般的 AI 出题解析要认一堆"模型可能的写法"——六种题头、四种答案前缀、
 * 题型标缺失时的兜底……因为**没人规定模型该写成什么样**。
 *
 * 考试卷有规范：规范就是提示词本身（配置项 `ai.prompt.exam.paper` / `.grade`），
 * 管理员在系统配置里改。于是解析器只认一种写法，模型跑偏的题**丢掉那一道**，
 * 其余照常显示。
 *
 * ## 逐块容错，不是全有或全无
 *
 * 25 道题里坏一道就整卷作废是不能接受的：用户等了一分钟，界面告诉他"没出成"，
 * 体验最差。坏的题丢掉 ⇒ 好的 24 道照常考，界面上补一句"有 1 道没解析出来"。
 *
 * ## 题型表是**这里唯一一份**
 *
 * 旧那套自测题（`utils/quizView.js` + `kp_topic_quiz`）2026-10-22 整块撤了，
 * 题型表、解析器、本地判分、每日复习一起没的。现在全站只有这张表。
 * ⚠️ **以后再加题型，只改 `EXAM_TYPES` 这一处** —— 下面的 `TYPE_MARK` / `TYPE_BY_MARK`
 * 都是**从这张表算出来的**，不是另抄一份。抄一份的后果：加第 6 种题型时表里加了、
 * 正则里没加 ⇒ 解析器认不出题头括号里的标记，而 `typeByNo` 能按题号推出来
 * ⇒ 两套判定打架，症状是"那道题类型错了"或"整道题被丢掉"，**且不报错**。
 */

/**
 * 考试的五种题型。**这张表是全站唯一一份题型定义**。
 *
 * - `marks` = 模型在题头括号里会写的中文
 * - `kind`  = 这类题的形态：choice（有选项、答案是字母）/ fill（答案是词）/ open（没有唯一答案，判分交给 AI）
 * - `multi` = 能不能多选（只有多选题能）
 *
 * ⚠️ **下面那些集合和判别函数全从这张表算出来**，不另抄一份名单 ——
 * 抄一份的后果：加第 6 种题型时表里加了、子集没加 ⇒ 解析器认不出、
 * 界面判错，**而且不报错**。
 */
export const EXAM_TYPES = {
  single: { label: '单选题', marks: ['单选'], kind: 'choice' },
  multiple: { label: '多选题', marks: ['多选'], kind: 'choice', multi: true },
  blank: { label: '填空题', marks: ['填空'], kind: 'fill' },
  concept: { label: '概念题', marks: ['概念'], kind: 'open' },
  interview: { label: '面试题', marks: ['面试'], kind: 'open' }
}

/** 某种形态的题型（从表里算，不手写名单） */
function typesOfKind(kind) {
  return Object.keys(EXAM_TYPES).filter((k) => EXAM_TYPES[k].kind === kind)
}

/** 开放题：没有唯一答案，判分只能交给 AI */
const OPEN_EXAM_TYPES = new Set(typesOfKind('open'))

/** 选择型：有选项、答案是选项下标 */
const CHOICE_TYPES = new Set(typesOfKind('choice'))

const MARKS = Object.entries(EXAM_TYPES).flatMap(([value, t]) => t.marks.map((mark) => [mark, value]))
const TYPE_BY_MARK = Object.fromEntries(MARKS)
const TYPE_BY_LABEL = Object.fromEntries(Object.entries(EXAM_TYPES).map(([k, t]) => [t.label, k]))

/** 全部题型标记（扁平成一个词表）—— 正则从它生成，不另抄一份 */
const ALL_MARKS = Object.keys(TYPE_BY_MARK)

/** 这个题型是不是填空（答案是词，不是选项、也不是开放题） */
export function isFillType(type) {
  return EXAM_TYPES[type]?.kind === 'fill'
}

/** 这个题型能不能多选（只有多选题能） */
export function isMultiType(type) {
  return EXAM_TYPES[type]?.multi === true
}

/**
 * 配置里写的题型名 → 题型 key。**「单选」和「单选题」都认**（管理员手写时两种都会写）。
 *
 * @returns {string} 认不出来就是空串（调用方自己决定怎么处理，别静默当默认值）
 */
export function typeKeyOf(name) {
  const n = String(name || '').trim()
  return TYPE_BY_MARK[n] || TYPE_BY_LABEL[n] || ''
}

/**
 * 题型 → 模型在题头括号里写的那个词（解析器认的就是它）。
 *
 * ⚠️ 给模型看的题量结构要用**它**（"第 1-5 题：单选"），不是显示名（"单选题"）——
 * 模型照着写，解析器才认得。
 */
export function markOf(type) {
  return EXAM_TYPES[type]?.marks?.[0] || ''
}

/**
 * 题量结构：**这次出多少道、每种题型几道**。
 *
 * @typedef {{ type: string, count: number }} Section
 * @typedef {{ type: string, from: number, to: number, count: number }} Range
 */

/**
 * 题量结构 → 题号区间。**只在这里算一次，别处引用结果。**
 *
 * ⚠️ **为什么必须是"一处算"**：拼给模型的那段「题量结构：第 1-5 题：单选…」和
 * 解析器推「题号 → 题型」用的是**同一份区间**。两边各算一遍，
 * 用户改一次道数就可能对不上——**而且不报错**，只是题号和题型悄悄错位。
 *
 * @param {Section[]} sections [{type, count}]
 * @returns {Range[]} [{type, from, to, count}]
 */
export function rangesOf(sections) {
  const out = []
  let from = 1
  for (const s of sections || []) {
    const to = from + s.count - 1
    out.push({ type: s.type, from, to, count: s.count })
    from = to + 1
  }
  return out
}

/**
 * 总题数（sections / ranges 都带 count，通吃）。**只有这一份** ——
 * 界面上「最多 N 道」和「计划出 N 道」是同一个数，两处各算一遍就会差一个。
 */
export function totalOf(list) {
  return (list || []).reduce((n, s) => n + s.count, 0)
}

/**
 * 一道题值多少分：**题量结构里没写这一型时的默认**（**只有这一份**）。
 * 配置里「分值」那一列可以省，省了就按这个算。
 */
export const DEFAULT_SCORE = 1

/**
 * 题号 → 题型。按 ranges 找。
 *
 * **为什么是运行时数据**：以前这里是写死的 1-5 单选 / 6-10 多选…，
 * 可题量是用户可调的（系统配置给默认值，考试页还能改）。
 *
 * @param {number} no 题号（1 起）
 * @param {Range[]} ranges {@link rangesOf} 的结果
 * @returns {string} 题型 key；题号超出结构就是空串
 */
function typeByNo(no, ranges) {
  return (ranges || []).find((r) => no >= r.from && no <= r.to)?.type || ''
}

/**
 * 题头：`## 12（多选）`。
 *
 * **只认题号**：括号里的题型是**可选项**。
 * 认死了（题型必须有、还得是那五个字之一）有个坏处：模型漏写一次题型，
 * 那道题的题头就不匹配 ⇒ **整块并进上一道题**，上一道题被污染，后面全乱。
 * 宁可让题型缺着（按编号补），也别动块边界。
 */
const PAPER_HEAD = /^\s*#{1,6}\s*(\d{1,2})\b/

/**
 * 题型标记：题头括号里的那个词。只在题头后面十几个字里找（再往后是题干了）。
 *
 * **从 `EXAM_TYPES` 算出来，不另抄一份** —— 抄一份的话，加题型时两处不同步，
 * 症状是「那道题类型错了 / 整道被丢掉」，而且不报错。
 */
const TYPE_MARK = new RegExp(`[（(]\\s*(${ALL_MARKS.join('|')})\\s*[)）]`)

/**
 * 判分结果的题头：`## 12`。
 *
 * **后面允许跟题型**（`## 12（单选）`）：模型眼前就是那份卷子，
 * 很可能把卷面的格式一起抄过来。认死了就整块丢掉 —— 宁可宽松一点。
 */
const GRADE_HEAD = /^\s*#{1,6}\s*(\d{1,2})\s*(?:[（(]\s*[一-龥]{2,4}\s*[)）])?\s*[.、:：]?\s*$/

/**
 * 选项字母表（A–H）：**只有这一份**。
 * 解析选项行、抽答案里的字母，两条正则都从它生成 ——
 * 抄两遍的话，加到第 9 个选项时只改了一处，另一处静默不认。
 */
const OPTION_LETTERS = 'ABCDEFGH'

/** 选项行：`A. 内容` / `A、内容` / `A) 内容` */
const OPTION = new RegExp(`^\\s*([${OPTION_LETTERS}])[.、)）]\\s*(.+)$`)

/** 答案里只留选项字母（`答案：AB` → `AB`） */
const NON_OPTION_LETTER = new RegExp(`[^${OPTION_LETTERS}${OPTION_LETTERS.toLowerCase()}]`, 'g')

/**
 * 答案行。`参考答案` 是开放题那两类的写法。
 *
 * 「标准答案 / 正确答案 / 答案」是一组同义词，**收全**：
 * 只认「答案：」的话，模型写成「正确答案：」的那道题会因为找不到答案行而**整道丢掉**。
 * 这是**封闭的一组**，不是"模型可能怎么写"的无限枚举。
 */
const ANSWER = /^\s*(?:标准答案|正确答案|答案)\s*[:：]\s*(.*)$/
const REF_ANSWER = /^\s*参考答案\s*[:：]\s*(.*)$/

/**
 * 判分行。判定只有三个：**对 / 部分对 / 错**。
 *
 * 「正确 / 错误」是同一批词的另一种写法，一并收下（理由同上）。
 * **注意匹配顺序**：`部分对` 必须排在 `对` 前面（长词先匹配）。
 */
/**
 * 判定只有三种：**这张表是唯一一份**。
 *
 * - `verdict`  = 规范里的写法（判分规范说"对 / 部分对 / 错"）
 * - `cls`      = 界面上的样式
 * - `ratio`    = 这一档能拿多少分（满分 × 比例）
 * - `synonyms` = 模型可能写的同义写法（收全，认死了整道题会被丢掉）
 *
 * ⚠️ 界面上的三档计数和样式**从这张表出**（`VERDICTS` / `verdictClassOf`），
 * 别在组件里再写一遍"对 / 部分对 / 错"三个字面量 —— 少改一处，
 * 结果页上就会出现"判了 3 道对，计数只显示 2 道"。
 * ⚠️ **折算比例也在这张表里**（`ratio`）：判定词只有一份，
 * 写分的地方就不许再写一遍 `verdict === '部分对' ? 一半 : ...`。
 */
export const VERDICTS = [
  { key: 'right', verdict: '对', cls: 'is-good', ratio: 1, synonyms: ['对', '正确'] },
  { key: 'part', verdict: '部分对', cls: 'is-mid', ratio: 0.5, synonyms: ['部分对'] },
  { key: 'wrong', verdict: '错', cls: 'is-bad', ratio: 0, synonyms: ['错', '错误'] }
]

/** 判定 → 界面上的样式（认不出来 = 未判定） */
export function verdictClassOf(verdict) {
  return VERDICTS.find((v) => v.verdict === verdict)?.cls || 'is-none'
}

const VERDICT_MAP = Object.fromEntries(
  VERDICTS.flatMap((v) => v.synonyms.map((s) => [s, v.verdict]))
)
const VERDICT = new RegExp(
  `^\\s*判定\\s*[:：]\\s*(${Object.keys(VERDICT_MAP).sort((a, b) => b.length - a.length).join('|')})\\s*(?:[（(].*)?$`
)

const EXPLAIN = /^\s*讲解\s*[:：]\s*(.*)$/

/**
 * 解析出题结果。
 *
 * @param {string} text 模型出的整卷
 * @param {Range[]} ranges 题号区间（{@link rangesOf} 的结果）—— 题号 → 题型靠它推
 * @returns {{questions: Array, problems: number[]}} `problems` = 没解析出来的题号
 */
export function parsePaper(text, ranges) {
  const questions = []
  const problems = []
  for (const block of splitBlocks(text, PAPER_HEAD, ranges)) {
    const q = parsePaperBlock(block)
    if (q) questions.push(q)
    else problems.push(block.no)
  }
  return { questions, problems }
}

/**
 * 卷面和规范对不上的地方，合成一句话给界面（没有就返回空串）。
 *
 * **只报事实**：用户看得见"出了几道""哪种题型少了几道"，
 * 不该看到界面替 AI 打分。
 *
 * ⚠️ **少出不是错**（2026-10-23）：题量结构是**上限**——材料撑不住就少出
 * （见 `ai.prompt.exam.paper` 的"考点个数最多和总题数一样"）。
 * 所以这里报"出了几道 / 计划几道"，不报"缺了几道"：后者会让人以为出卷失败了。
 */
export function paperNote(questions, problems, ranges) {
  const bits = []
  if (problems.length) bits.push(`${problems.length} 道没解析出来`)
  const counts = {}
  for (const q of questions) counts[q.type] = (counts[q.type] || 0) + 1
  const planned = totalOf(ranges)
  if (questions.length < planned) {
    bits.push(`出题 ${questions.length} 道（计划 ${planned} 道）`)
  }
  const off = (ranges || []).filter((r) => (counts[r.type] || 0) !== r.count)
  if (off.length) {
    bits.push(off.map((r) => `${EXAM_TYPES[r.type]?.label || r.type} ${counts[r.type] || 0}/${r.count} 道`).join('、'))
  }
  return bits.join('，')
}

/**
 * 解析判分结果。
 *
 * @returns {Object} `{ 12: { verdict: '对', explain: '…' } }`
 */
export function parseGrade(text) {
  const grades = {}
  let cur = null
  for (const raw of String(text || '').split('\n')) {
    const line = raw.trim()
    if (!line) continue
    const head = line.match(GRADE_HEAD)
    if (head) {
      cur = { no: Number(head[1]), verdict: '', explain: [] }
      grades[cur.no] = cur
      continue
    }
    if (!cur) continue
    const verdict = line.match(VERDICT)
    if (verdict) {
      cur.verdict = VERDICT_MAP[verdict[1]] || verdict[1]
      continue
    }
    const explain = line.match(EXPLAIN)
    if (explain) {
      if (explain[1]) cur.explain.push(explain[1])
      continue
    }
    // 讲解的第一行后面还可能跟着几行（规范说最多 4 行），都算这一题的
    cur.explain.push(line)
  }
  for (const g of Object.values(grades)) {
    g.explain = g.explain.join('\n')
  }
  return grades
}

/**
 * 计分。**判定 → 分数的折算只在这里定一次。**
 *
 * <p>分值来自**题量结构**（`题型|道数|分值`）：题型 → 每题分值。
 * 判定只有三档（判分规范里写死的）：对 / 部分对 / 错
 * ⇒ **对 = 满分，部分对 = 一半，错 = 0**。
 * 选择题（单选/多选/填空）判分规范只给对/错 ⇒ 只有 0 和满分两档。
 *
 * <p>**满分按实际出的题算**：模型少出题时，卷子就按出的那些题定分
 * （题量结构是上限，不是必须出满 —— 见 `ai.prompt.exam.paper`）。
 *
 * <p>放在这儿而不是 `AppExamView.vue` 里：**纯函数、能直接跑**（判分链要能整条验），
 * 顺带 SFC 里那份"折算规则"也就不用再抄一遍。**只导出 `paperScore`** ——
 * 折算和查表都是它的内部事。
 */

/** 题型 → 每题分值 */
function scoreMapOf(sections) {
  return Object.fromEntries((sections || []).map((s) => [s.type, s.score]))
}

/**
 * 判定 + 这道题的分值 → 得了多少分。
 * 折算比例从 VERDICTS 那张表取（`ratio`）—— 判定词只存在一份，
 * 这里不再写一遍"对 = 满分 / 部分对 = 一半 / 错 = 0"。
 */
function pointsOf(verdict, score) {
  const hit = VERDICTS.find((v) => v.verdict === verdict)
  return hit ? score * hit.ratio : 0
}

/**
 * 一张卷子的计分结果。
 *
 * @param {Array} questions 解析出来的题
 * @param {Object} grades 题号 → 判定（`parseGrade` 的结果）
 * @param {Array} sections 题量结构（`{type, count, score}`）
 * @returns {{items: Object, sum: number, total: number}}
 *   `items` = 题号 → `{ weight, points }`（这道题值多少分、得了多少分）
 */
export function paperScore(questions, grades, sections) {
  const scoreMap = scoreMapOf(sections)
  const items = {}
  let sum = 0
  let total = 0
  for (const q of questions) {
    // 题量结构里没配这一型 ⇒ 默认分（和"分值可省"的默认一致）
    const weight = scoreMap[q.type] ?? DEFAULT_SCORE
    const points = pointsOf(grades[q.no]?.verdict, weight)
    items[q.no] = { weight, points }
    total += weight
    sum += points
  }
  return { items, sum, total }
}

/**
 * 把卷子 + 考生作答拼成判卷要发给模型的那段文本。
 *
 * **标准答案必须一起给**：判分是"对着标准答案判"，不是"重新答一遍"——
 * 没有标准答案，模型只能按自己的理解判，分数就没有意义。
 */
export function buildPaperText(questions, answers) {
  const parts = []
  for (const q of questions) {
    const lines = [`## ${q.no}（${q.typeLabel}）`, q.stem]
    for (const o of q.options) lines.push(`${o.label}. ${o.text}`)
    if (q.isOpen) {
      lines.push('参考答案：')
      for (const p of q.answerPoints) lines.push(`- ${p}`)
    } else {
      lines.push(`标准答案：${q.answer}`)
    }
    lines.push(`考生作答：${describeAnswer(q, answers[q.no])}`)
    parts.push(lines.join('\n'))
  }
  return parts.join('\n\n')
}

/** 考生这一题答了什么（给模型看的那句话） */
export function describeAnswer(q, answer) {
  const a = answer || {}
  if (q.isOpen) {
    return String(a.typed || '').trim() || '（未作答）'
  }
  if (isFillType(q.type)) {
    return String(a.typed || '').trim() || '（未作答）'
  }
  if (!a.picked?.length) return '（未作答）'
  return a.picked.map((i) => q.options[i]?.label || '').join('')
}

// ==================== 内部 ====================

/** 按题头把一段文本切成块 */
function splitBlocks(text, headRe, ranges) {
  const blocks = []
  let cur = null
  for (const raw of String(text || '').split('\n')) {
    const head = raw.match(headRe)
    if (head) {
      const no = Number(head[1])
      const after = raw.slice(head[0].length)
      // 题型写在题头括号里；没写就按题号推（**题量结构**定的，不是猜）
      const mark = after.slice(0, 12).match(TYPE_MARK)
      // ⚠️ 括号里那三个字**要从正文里抠掉**：留在那儿就成了题干的第一行
      //    （「（单选）\n事务的原子性指的是」——题干里冒出一个题型标签）。
      let rest = after.trim()
      if (mark) {
        rest = (after.slice(0, mark.index) + after.slice(mark.index + mark[0].length)).trim()
      }
      cur = { no, type: mark ? TYPE_BY_MARK[mark[1]] : typeByNo(no, ranges), lines: [] }
      // 题头后面接的正文（模型偶尔不换行）要留下
      if (rest) cur.lines.push(rest)
      blocks.push(cur)
      continue
    }
    if (cur) cur.lines.push(raw)
  }
  return blocks
}

/** 一块 → 一道题。解析不出来返回 null（那题丢掉，其余照常） */
function parsePaperBlock(block) {
  if (!block.type) return null
  const stem = []
  const options = []
  const points = []
  let answer = ''
  let inAnswer = false
  for (const raw of block.lines) {
    const line = raw.trim()
    if (!line) continue
    if (!inAnswer) {
      const opt = line.match(OPTION)
      if (opt) {
        options.push({ label: opt[1], text: opt[2].trim() })
        continue
      }
      const ans = line.match(ANSWER)
      const ref = line.match(REF_ANSWER)
      if (ans || ref) {
        inAnswer = true
        answer = (ans || ref)[1].trim()
        continue
      }
      stem.push(line)
      continue
    }
    // 答案段：开放题是若干条「- 要点」
    const point = line.match(/^[-*]\s*(.+)$/)
    if (point) {
      points.push(point[1].trim())
      continue
    }
    if (!answer) answer = line
    else points.push(line)
  }

  const isOpen = OPEN_EXAM_TYPES.has(block.type)
  const isChoice = CHOICE_TYPES.has(block.type)
  if (!stem.length && !options.length) return null

  if (isChoice) {
    // 答案只留字母：`答案：B` → `B`；`答案：AB` → `AB`
    answer = answer.replace(NON_OPTION_LETTER, '').toUpperCase()
    if (!answer || options.length < 2) return null
  } else if (isOpen) {
    const list = points.length ? points : answer ? [answer] : []
    if (!list.length) return null
    return {
      no: block.no,
      type: block.type,
      typeLabel: EXAM_TYPES[block.type].label,
      isOpen: true,
      stem: stem.join('\n'),
      options: [],
      answer: list.join('\n'),
      answerPoints: list
    }
  } else {
    // 填空：答案就是那个词
    if (!answer) return null
  }

  return {
    no: block.no,
    type: block.type,
    typeLabel: EXAM_TYPES[block.type].label,
    isOpen: false,
    stem: stem.join('\n'),
    options,
    answer,
    answerPoints: []
  }
}
