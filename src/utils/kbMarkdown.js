/**
 * markdown 存进库之前的一道修正。
 *
 * ## 为什么需要它
 *
 * CommonMark 里，`**` 能不能闭合粗体，取决于它**后面跟的是什么**：
 * 后面紧跟字母/数字（word char）时，这个 `**` 同时也算"左向"，
 * 于是**不能闭合** —— `**List：**List代表` 解析出来是两个**字面**的 `**`，
 * 不是粗体。
 *
 * 而 remark 的序列化器不知道自己的 strong 后面还跟着什么（它以为 strong
 * 是段落最后一个东西），于是会写出 `**List：**List代表` 这种**解析不回来**的
 * markdown。表现是：编辑器里粗体好好的，**刷新一次粗体没了、`**` 变成字面量**，
 * 再存一次字面 `*` 被转义成 `\*`，就成了 `\*\*`。
 *
 * 这不是"粘贴进来的内容有问题"——HTML 粘贴走的是 ProseMirror 的 DOM 直解，
 * 粗体是真粗体；是**序列化**这一步把内容写成了自己读不回来的形式。
 *
 * ## 修法
 *
 * 闭合的 `**` 后面紧跟 word char 时，补一个空格：
 * `**List：**List` → `**List：** List`。加的空格让分隔成立，
 * 粗体保住了，内容也读得回来（再解析、再存，都不再变）。
 *
 * ## 为什么只在闭合位置补
 *
 * 开头的 `**` 后面当然也紧跟字母（`**bold**`），但它不需要补 ——
 * 能开不能闭的是**闭合**那个。`**` 在 CommonMark 里不能嵌套，
 * 所以成对出现：按出现顺序奇数个是开、偶数个是闭。
 *
 * 跳过行内代码和围栏代码块里的 `**`（那里的 `**` 是内容，不是标记）。
 */

/** 字母 / 数字 / 汉字 —— 这些字符跟在闭合的 `**` 后面会让它闭不上 */
const WORD_CHAR = /[\p{L}\p{N}]/u

/**
 * @param {string} md
 * @returns {string}
 */
export function fixEmphasisTail(md) {
  if (!md || !md.includes('**')) return md

  let out = ''
  let i = 0
  /** strong 不嵌套，`**` 成对出现：奇数个开、偶数个闭 */
  let strongOpen = false
  /** 在围栏代码块里？里面的 `**` 是代码内容，不动 */
  let inFence = false
  /**
   * 逐行看围栏代码块的开闭。**行首也要算一行** —— markdown 的第一行就可能是
   * ```，只从换行符后面开始找的话，第一段代码块里的 `**` 会被当成标记改掉。
   */
  const fenceState = (line) => {
    if (!/^\s*(```|~~~)/.test(line)) return null
    return !inFence
  }

  while (i < md.length) {
    // ---- 围栏代码块：``` 或 ~~~ 开头的那一整块 ----
    if (md[i] === '\n') {
      const lineEnd = md.indexOf('\n', i + 1)
      const line = md.slice(i + 1, lineEnd === -1 ? md.length : lineEnd)
      const opening = fenceState(line)
      if (opening !== null) inFence = opening
      out += '\n'
      i++
      continue
    }
    // 文档第一行就是围栏的情况
    if (i === 0) {
      const lineEnd = md.indexOf('\n')
      const line = md.slice(0, lineEnd === -1 ? md.length : lineEnd)
      const opening = fenceState(line)
      if (opening !== null) {
        inFence = opening
        out += line
        i = line.length
        continue
      }
    }
    if (inFence) {
      out += md[i]
      i++
      continue
    }

    // ---- 行内代码：`` ` `` 之间的内容原样保留 ----
    if (md[i] === '`') {
      const end = md.indexOf('`', i + 1)
      if (end > -1) {
        out += md.slice(i, end + 1)
        i = end + 1
        continue
      }
    }

    // ---- 转义：\* 是字面星号，不是标记 ----
    if (md[i] === '\\' && i + 1 < md.length) {
      out += md[i] + md[i + 1]
      i += 2
      continue
    }

    // ---- strong 标记 ----
    // **只认正好两个星号**：*** 是"粗体里套斜体"，把它当 strong 会算错配对，
    // 反而把该补空格的地方漏掉、该补的地方多补。
    if (md[i] === '*' && md[i + 1] === '*' && md[i + 2] !== '*') {
      out += '**'
      i += 2
      strongOpen = !strongOpen
      // 刚才是**闭合**位置：后面紧跟 word char 就补一个空格
      if (!strongOpen) {
        const next = md[i]
        if (next && WORD_CHAR.test(next)) out += ' '
      }
      continue
    }

    out += md[i]
    i++
  }

  return out
}
