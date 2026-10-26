/**
 * 考试的前端能力：**参数（从配置读 → 用户可改 → 拼成 prompt）** + **入口（开考试页）**。
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
 * | `ai.prompt.exam.paper` | 一段文本 | 规范**模板**：只写「怎么写题」 |
 * | `ai.prompt.exam.grade` | 一段文本 | 判分规范 |
 * | `ai.prompt.exam.types` | 每行 `题型\|道数\|分值` | 题量结构（**分值可省 ⇒ 1 分/题**） |
 * | `ai.prompt.exam.level` | 每行 `档位名\|这个档位考什么` | 难度档位（**第一行是默认档**） |
 * | `ai.prompt.exam.extra` | 一段文本 | 考生自己加的要求（默认空） |
 *
 * 存**行格式**不存 JSON：配置页是个纯文本框，管理员要能一眼看懂一行是什么。
 */
import { getMyConfig } from '../../api/config'
import { EXAM_TYPES, markOf, typeKeyOf, DEFAULT_SCORE, totalOf } from './examParse'

/** 考试这一组配置键的命名空间。**键全从这里长出来，别再各处抄一遍 */
const NS = 'ai.prompt.exam.'

/** 配置键（都在 `ai.prompt.exam.` 下面） */
export const CFG_KEYS = {
  paper: NS + 'paper',
  grade: NS + 'grade',
  types: NS + 'types',
  level: NS + 'level',
  extra: NS + 'extra'
}

/**
 * 读考试参数。
 *
 * 走 `/sys/config/mine?prefix=ai.prompt.exam.`（登录即可读）：
 * `values` = 生效值（我的覆盖 > 系统默认），`defaults` = 系统默认原值。
 *
 * @returns {Promise<{paper: string, grade: string, types: Object, levels: Array, extra: string}>}
 */
export async function loadExamSpec() {
  const res = await getMyConfig(NS)
  const v = res?.data?.values || {}
  return {
    paper: v[CFG_KEYS.paper] || '',
    grade: v[CFG_KEYS.grade] || '',
    types: parseTypes(v[CFG_KEYS.types]),
    levels: parseLevels(v[CFG_KEYS.level]),
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
 * 拼最终 prompt：**题量结构 + 模板 + 难度 + 考生自己的要求**。
 *
 * ⚠️ **顺序有讲究**：题量结构在最前面（"出多少道、怎么编号"），
 * 模板在后（"怎么写题"）—— 模板里说"考点个数和题量结构的总题数一样"，
 * 指的就是**上面**那份。
 *
 * @param {Object} p
 * @param {string} p.paper 规范模板
 * @param {Array} p.ranges 题号区间（`rangesOf` 的结果）
 * @param {Object} p.level 选中的难度档 `{name, desc}`
 * @param {string} p.extra 考生自己加的要求（可空）
 * @returns {string}
 */
export function buildPaperPrompt({ paper, ranges, level, extra }) {
  const list = ranges || []
  const total = totalOf(list)
  const parts = []

  // 1. 题量结构
  const lines = [`出 ${total} 道题考读者。题量结构（按这个顺序编号）：`]
  for (const r of list) {
    lines.push(`第 ${r.from}-${r.to} 题：${markOf(r.type)}`)
  }
  lines.push(`共 ${total} 道题。`)
  parts.push(lines.join('\n'))

  // 2. 规范模板
  if (paper) parts.push(paper.trim())

  // 3. 难度：整卷一档（用户定的：难度不是按题型分，是整卷一个档）
  if (level?.name) {
    parts.push(`难度要求：整卷按「${level.name}」出题${level.desc ? ' —— ' + level.desc : ''}。`)
  }

  // 4. 考生自己的要求
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
  const url = `/exam?${parts.join('&')}`
  // 返回 null = 被浏览器拦了（弹窗拦截 / 用户禁了）⇒ 退化成普通新标签页再试一次
  const tab = window.open(url, `exam-${key}`)
  if (!tab) window.open(url, '_blank')
}
