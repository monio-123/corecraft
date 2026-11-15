/**
 * AI 回答 → 能写进正文的**新正文**。
 *
 * ## 约定：两行标记
 *
 * 要求模型：用户要改这份正文时，输出**改写后的完整正文**（整篇，不只是改动的那部分），
 * 前后各加一行标记 —— 第一行【正文开始】、最后一行【正文结束】。
 *
 * **模型爱在正文外面加东西**：「好的，以下是改写后的内容：」，或者把整篇塞进
 * ``` 代码块。那些进了编辑器就**成了正文的一部分** —— 界面上看不出异常，
 * 只是"AI 写得不对"，而且用户已经点下"应用"了。按标记取就绕开了这一层。
 *
 * ## 应用侧不解析意图，只认标记
 *
 * 用户要的是「对原文的任意修改（增/删/改）都能通过对话完成并应用」，
 * 但**具体改什么、怎么改，是 AI 自己定的**（提示词说了算）。
 * 所以应用侧只有一条判据：**这段回答里有没有那两行标记** ——
 * 有 ⇒ 它是一份可应用的新正文（整篇替换）；没有 ⇒ 它是一段解释（照旧追加）。
 *
 * ## 解析不出来就返回 null，不猜也不兜底
 *
 * 与 `aiAdopt.js` 同一口径：**写进库比不写更糟**（用户还得再手工改回去）。
 * 返回 null 时界面上不给「应用到正文」的入口，只给复制 —— 用户仍可以自己贴。
 */
import { getComposedPrompt } from '../../api/ai'

/** 正文开始标记。与要求模型写的那两行一一对应 */
export const BODY_START = '【正文开始】'
/** 正文结束标记 */
export const BODY_END = '【正文结束】'

/**
 * 改写规范在配置里的键（配置页：系统 AI 配置 → AI 提示词 → 正文调整 → 改写规范）。
 *
 * **前端读它，不是后端读** —— 为什么：
 * 后端 `AiChatServiceImpl.buildMessages` 里，`questionKey` 命中配置时
 * `current = 配置里的那段规范` ⇒ **用户自己敲的问题被整段替换掉**
 * （`instruction.isEmpty() ? req.getQuestion() : instruction`）。
 * 而这次的要求正是用户自己敲的、每次都不一样 ⇒ 只能走 `question`。
 *
 * 规范也就只能由前端自己读（考试那组就是这么干的，见 `exam.js` 的 `loadExamSpec`）。
 */
export const EDIT_SPEC_KEY = 'ai.prompt.edit.body'

/**
 * 「这一轮要什么」的判定 + 输出格式。
 *
 * **应用侧不解析意图**（改什么、怎么改是 AI 自己定的）⇒ 谁来判断
 * 「这一轮是要答案，还是要改写」？答案：模型 —— 但**必须让它在最后一条消息里就看到判据**。
 *
 * - 「讲明白」⇒ 正常作答，**不要输出正文、不要加标记**；
 * - 「改出来」⇒ 只输出改写后的整篇正文（写法见配置里那份规范）。
 *
 * ⚠️ 为什么这段跟用户的话一起走 `question`（而不是放进材料）：
 * 后端拼出来的 user 消息是 `材料 + "\n\n" + 这一轮的要求` ⇒
 * **要求永远在最后一条**；而材料（正文本体）是钉在第一条的。
 * 2026-11-05 实测：要求放进材料时，模型最后看到的是**上一轮**的旧问题
 * ⇒ 它去答了旧问题（实测「改写 2.2」被当成了「解释上一段」）。
 * ⇒ 判据 + 要求 = 最后一条消息（`question`），材料里只留正文本体。
 */
const ANSWER_GATE = `【怎么答】
先判断【用户的话】要的是哪一种，两种情况的输出格式完全不同，判错了整轮就废了。
判据：用户是想要你「讲明白」，还是想要你「改出来」。
- 「讲明白」（提问、问原理、问区别、要举例、要总结、要评价、要对比…；话里出现「解释 / 讲讲 / 是什么 / 为什么 / 怎么理解 / 区别 / 对比 / 举例 / 总结 / 评价 / 建议」这类词，就是这一类）：只回答那个问题，正常作答就行 —— 不要输出正文、不要加任何标记。
- 「改出来」（新增、替换、删掉、扩写、精简、纠错、改得更清楚…；话里出现「改写 / 替换 / 改成 / 新增 / 删掉 / 精简 / 扩写」这类词，就是这一类）：只输出改写后的完整正文（整篇，不只是改动的那部分），前后各加一行标记 —— 第一行写 【正文开始】，最后一行写 【正文结束】；标记之间就是改写后的完整正文，标记之外一个字都不写（不要开场白、不要解释改了什么、不要结尾总结）。写法按下面的规范。
⚠️ 材料里给了【原文】不代表用户要你改它 —— 给了原文只是让你知道在说哪一段。用户要答案，就只给答案。`

/** 改写规范：读一次就够（同一个 Promise 复用） */
let specPromise = null

/**
 * 读改写规范（配置里那一份）。
 *
 * 读不到就当没有（返回空串）——「怎么答」那段（判据 + 输出格式）在代码里，
 * 标记照样有，只是少了些写法上的讲究 ⇒ **不会因为配置读不到就整条链断掉**。
 *
 * @returns {Promise<string>}
 */
export function loadEditSpec() {
  if (!specPromise) {
    // ⚠️ **读「展开版」而不是原文**：这条配置里写着 `【引用】ai.skill.writing`，
    // 用 `getMyConfig` 拿到的是原文 ⇒ 模型收到的只有一行字面量的【引用】标记，
    // 写作规范一个字都到不了（2026-11-12 实测踩到）。`/ai/prompt` 走后端同一份展开逻辑。
    specPromise = getComposedPrompt(EDIT_SPEC_KEY)
      .then((res) => res?.data || '')
      .catch(() => '')
  }
  return specPromise
}

/**
 * 发给模型的那条要求 = **怎么答** + 改写规范 + 用户的话。
 *
 * **这一整段落在整段对话的最后**（后端把它当 `current`）⇒
 * 模型最后看到的是"这次要什么"，不是上一轮的问题。
 *
 * @param {string} words 用户这次的原话（问答框敲的，或选段那套拼好的）
 * @param {string} spec 改写规范（配置里那份；空就省掉）
 * @returns {string}
 */
export function buildEditQuestion(words, spec) {
  const parts = [ANSWER_GATE]
  const s = String(spec || '').trim()
  if (s) parts.push(s)
  parts.push(`【用户的话】\n${String(words || '').trim()}`)
  return parts.join('\n\n')
}

/**
 * 给模型的材料：**正文本体**（屏幕上那一版，含还没保存的编辑）。
 *
 * 材料是钉在第一条的 ⇒ 只放"这次处理的是哪份内容"，
 * **不放要求**（要求在最后一条，见 `buildEditQuestion`）。
 *
 * @param {string} content 要改写的正文
 * @returns {string}
 */
export function buildEditMaterial(content) {
  return `【原文】\n${String(content || '')}`
}

/**
 * 这段回答里**有没有那两行标记**。
 *
 * **"这是一份可应用的新正文"的唯一判据**（2026-11-05 用户定）：
 * 应用侧不解析 AI 的意图 —— 改什么、怎么改是 AI 自己定的，
 * 应用侧只看它有没有按约定把改写后的整篇正文标出来。
 *
 * ⚠️ **只看开始标记**：没有标记的回答一律当"解释"处理。
 * 早先这里还认一种形状（整段被 ``` 围栏包住），后来去掉了 ——
 * 用户问一句"这段代码怎么写"，AI 回一段围栏里的代码，那**不是**正文，
 * 按围栏认就会把整篇正文判成"被替换成了一段代码"。
 */
export function hasBodyMark(text) {
  return String(text || '').includes(BODY_START)
}

/**
 * 整段被代码围栏包住时剥掉围栏。
 *
 * 只认**整体**被包住的那种（第一行是 ``` 开头、最后一行是 ``` 结尾）。
 * 正文里自己带的代码块不在这个范围里 —— 那是内容的一部分。
 *
 * @param {string} text
 * @returns {string} 剥掉围栏的正文；没被整体包住就原样返回
 */
function stripFence(text) {
  const m = String(text || '').match(/^\s*```[^\n]*\n([\s\S]*?)\n?```\s*$/)
  return m ? m[1] : text
}

/**
 * 从 AI 的输出里取出改写后的正文。**取不到返回 null**。
 *
 * 两个分支：
 * 1. 有开始标记 → 取它后面那段。有结束标记就取到结束标记为止，
 *    没有就取到文本末尾（模型显然已经进入正文了，流式期间更是常态）。
 * 2. 没有标记 → null（不猜）。
 *
 * @param {string} text AI 的原始输出
 * @returns {string|null} 改写后的正文；解析不出来是 null
 */
export function parseEditedBody(text) {
  const raw = String(text || '')
  const start = raw.indexOf(BODY_START)
  if (start < 0) return null
  const from = start + BODY_START.length
  const end = raw.indexOf(BODY_END, from)
  const body = end >= 0 ? raw.slice(from, end) : raw.slice(from)
  // 标记里包着的正文偶尔被模型套了代码围栏 ⇒ 剥掉（格式规整，不是猜结构）
  return stripFence(body).trim() || null
}
