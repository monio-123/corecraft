/**
 * AI 改写正文 → 能写进 kp_topic.content 的正文。
 *
 * ## 为什么按标记取，而不是"取整段"
 *
 * 模型爱在正文外面加东西：「好的，以下是改写后的内容：」，或者把整篇塞进 ``` 代码块。
 * 那些进了编辑器就**成了正文的一部分** —— 界面上看不出异常，
 * 只是"AI 写得不对"，而且用户已经点下"应用"了。
 *
 * 所以提示词（`ai.prompt.edit.body`）要求模型用两个标记把正文包起来：
 * 第一行 【正文开始】、最后一行 【正文结束】。这里按标记取。
 *
 * ## 解析不出来就返回 null，不猜也不兜底
 *
 * 与 `aiAdopt.js` 同一口径：**写进库比不写更糟**（用户还得再手工改回去）。
 * 返回 null 时界面上不给「应用到正文」的入口，只给复制 —— 用户仍可以自己贴。
 *
 * ## 唯一认的第二种形状：整段被代码围栏包住
 *
 * 那不是"猜"，是**另一种确定的输出形状**（模型把整篇正文放进了 ```markdown 块）。
 * 剥围栏是格式规整，不是"从自由文本里猜结构"。
 * 但剥围栏**不覆盖**"前面加一句说明" —— 那种情况既没标记也没围栏，直接判失败。
 */

/** 正文开始标记。与提示词 `ai.prompt.edit.body` 里要求模型写的那两行一一对应 */
export const BODY_START = '【正文开始】'
/** 正文结束标记 */
export const BODY_END = '【正文结束】'

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
 * 三个分支：
 * 1. 有开始标记 → 取它后面那段。有结束标记就取到结束标记为止，
 *    没有就取到文本末尾（模型显然已经进入正文了，流式期间更是常态）。
 * 2. 没有标记，但整段被代码围栏包住 → 剥围栏。
 * 3. 都没有 → null（不猜）。
 *
 * @param {string} text AI 的原始输出
 * @returns {string|null} 改写后的正文；解析不出来是 null
 */
export function parseEditedBody(text) {
  const raw = String(text || '')
  const start = raw.indexOf(BODY_START)
  if (start >= 0) {
    const from = start + BODY_START.length
    const end = raw.indexOf(BODY_END, from)
    const body = end >= 0 ? raw.slice(from, end) : raw.slice(from)
    return body.trim() || null
  }
  const unfenced = stripFence(raw)
  if (unfenced !== raw) return unfenced.trim() || null
  return null
}

/**
 * 给模型的材料：用户的调整要求 + 要改写的正文。
 *
 * ## 为什么要求走 material 而不是 question
 *
 * 后端 `AiChatServiceImpl.buildMessages` 里，`questionKey` 命中配置时
 * `current = 配置里的那段规范` —— **用户自己敲的问题被整段替换掉**
 * （`instruction.isEmpty() ? req.getQuestion() : instruction`）。
 * 而这次的"指令"正是用户自己敲的、每次都不一样 ⇒ 只能走 material。
 *
 * 顺带的好处：给了 material 就不再去捞库里的正文 ⇒
 * **给 AI 的是屏幕上那一版**，含还没保存的编辑。
 *
 * ## 两段用标题隔开
 *
 * 模型得知道哪段是"照着改的要求"、哪段是"要改的东西"。
 * 提示词里写明了这两个标签，两边必须一致 —— 改一处要同时改另一处。
 *
 * @param {string} instruction 用户这次的要求
 * @param {string} content 要改写的正文
 * @returns {string}
 */
export function buildEditMaterial(instruction, content) {
  return `【调整要求】\n${String(instruction || '').trim()}\n\n【原文】\n${String(content || '')}`
}
