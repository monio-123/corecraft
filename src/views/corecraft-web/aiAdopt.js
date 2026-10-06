/**
 * AI 回答 → 能写进 kp_topic 的值。
 *
 * ## 为什么在前端解析
 *
 * 四条引导问题的 `remark` 都把输出格式写死了（"只输出名字本身" / "从清单里挑，原样输出名字"
 * / "每个一行、前面加 - " / 每题 4 个选项 1 个答案），格式是**已知的**，不需要为了稳妥
 * 再花一次模型调用去换一份 JSON。
 *
 * ## 解析不出来就返回 null，不猜也不兜底
 *
 * 格式没对上说明模型没照做，这时候硬凑出来的值是垃圾——**写进库比不写更糟**，
 * 用户还得再手工改回去。返回 null 时界面上不给出采纳入口，用户仍可复制原文自行处理。
 * 同理不要在这里写"实在解析不出就取前 N 个字"之类的兜底。
 *
 * ## 这不是「从自由文本里猜结构」
 *
 * 它只认字典里那四个 key（rename / pick_category / pick_tags / make_quiz）。
 * 用户自己敲的问题 `questionKey` 是空的，压根不进这里——它没有"该写进哪个字段"的语义
 * （要把它的答案写进正文是另一件事，不在采纳范围内）。
 */

/** questionKey 的 key 段 → kp_topic 的字段名 */
const ADOPT_FIELDS = {
  rename: 'title',
  pick_category: 'categoryId',
  pick_tags: 'tags',
  make_quiz: 'quizQuestions'
}

/** 这条回答能不能被采纳；不能就返回 null（界面上不给按钮） */
export function adoptFieldOf(questionKey) {
  return ADOPT_FIELDS[String(questionKey || '').split(':')[0]] || null
}

/** 第一段非空行。模型的回答常带一句解释说明，真正要的值在第一行 */
function firstLine(text) {
  return String(text || '').split('\n').map((s) => s.trim()).find(Boolean) || ''
}

/**
 * 剥掉包裹的引号和句末标点。
 *
 * **结尾的引号和标点要在同一个字符类里一起剥**，不能先剥引号再剥标点：
 * 模型很爱输出「MyBatis 转换失败」。——句号在引号**外面**，先剥引号时末尾还是「。」
 * 那一步什么都没剥掉，剥完标点引号就露在结果里了。
 */
function cleanName(text) {
  return firstLine(text)
    .replace(/^[「『"']+/, '')
    .replace(/[。.，,、」』"']+$/, '')
    .trim()
}

/** `- xxx` / `* xxx` / `• xxx` 开头的一行。remark 里要求的就是"前面加 - " */
const BULLET = /^\s*[-*•·]\s*(.+)$/

/** 单选项的一行：`A. 内容` / `A、内容` / `A) 内容` */
const OPTION = /^\s*([A-H])[.、)）]\s*(.+)$/

/** 答案行：`答案：A` / `答案: A` / `正确答案 A` */
const ANSWER = /^\s*(?:正确答案|答案|正确选项)\s*[:：]?\s*([A-H])/i

/** 一道题的编号行：`1. 题干` / `1、题干` */
const QUIZ_HEAD = /^\s*\d+[.、]\s*/

/**
 * 解析成 `updateTopic` 的 patch。解析不出来返回 null。
 *
 * @param {object} turn 一轮问答（用它的 questionKey 和 answer）
 * @param {Array}  categories 现有目录，用于把 AI 给的目录名对成 categoryId
 */
export function parseAdopt(turn, categories) {
  switch (adoptFieldOf(turn?.questionKey)) {
    case 'title':
      return parseRename(turn?.answer)
    case 'categoryId':
      return parseCategory(turn?.answer, categories)
    case 'tags':
      return parseTags(turn?.answer)
    case 'quizQuestions':
      return parseQuiz(turn?.answer)
    default:
      return null
  }
}

function parseRename(answer) {
  const title = cleanName(answer)
  return title ? { title } : null
}

/**
 * 目录名 → categoryId。
 *
 * 匹配不上就返回 `{ categoryId: null, unmatched: AI 给的那个名字 }` 而不是 null：
 * 界面照样给一个下拉让用户自己选，只是预选不上、并且把 AI 说的那个名字摆出来，
 * 用户能看出"它推荐了一个我这里没有的目录"。直接返回 null 会让这条回答整个没法采纳，
 * 但用户明明是能自己挑一个的。
 */
function parseCategory(answer, categories) {
  const name = cleanName(answer)
  const list = categories || []
  const hit =
    list.find((c) => c.name === name) ||
    list.find((c) => String(c.name).toLowerCase() === name.toLowerCase())
  return { categoryId: hit ? hit.id : null, unmatched: hit ? null : name }
}

function parseTags(answer) {
  const names = []
  const seen = new Set()
  for (const line of String(answer || '').split('\n')) {
    const m = line.match(BULLET)
    if (!m) continue
    const name = m[1].trim()
    if (!name || seen.has(name)) continue
    seen.add(name)
    names.push(name)
  }
  return names.length ? { tags: names } : null
}

/**
 * 测验题按题拆成 `{type, question, options, answer}`。
 *
 * **按题拆而不是整段存一条**：`decodeQuizQuestion` 遇到纯文本会兜成
 * `{options: [], answer: 0}`——一个没有选项的"单选题"根本没法考。存结构化对象才
 * 是 `kp_topic_quiz` 这张表存在的意义。
 *
 * 任何一题解析不出来（没有题干、选项少于 2 个、没写答案）就整份不采纳：
 * 存 2 道正常的 + 1 道残缺的，比不存更麻烦。
 */
function parseQuiz(answer) {
  const blocks = splitQuizBlocks(answer)
  if (!blocks.length) return null
  const questions = blocks.map(parseQuizBlock)
  return questions.every(Boolean) ? { quizQuestions: questions } : null
}

function splitQuizBlocks(text) {
  const blocks = []
  let current = null
  for (const line of String(text || '').split('\n')) {
    if (QUIZ_HEAD.test(line)) {
      current = [line.replace(QUIZ_HEAD, '')]
      blocks.push(current)
    } else if (current) {
      current.push(line)
    }
  }
  return blocks.map((b) => b.join('\n').trim()).filter(Boolean)
}

function parseQuizBlock(block) {
  const stem = []
  const options = []
  let answer = ''
  for (const line of block.split('\n')) {
    const option = line.match(OPTION)
    if (option) {
      options.push({ label: option[1].toUpperCase(), text: option[2].trim() })
      continue
    }
    const hit = line.match(ANSWER)
    if (hit && !answer) {
      answer = hit[1].toUpperCase()
      continue
    }
    // 选项之前都算题干；选项之后残留的行（模型多写的解释）丢掉
    if (!options.length) stem.push(line.trim())
  }
  const question = stem.join(' ').trim()
  const index = options.findIndex((o) => o.label === answer)
  if (!question || options.length < 2 || index < 0) return null
  return { type: 'single', question, options: options.map((o) => o.text), answer: index }
}
