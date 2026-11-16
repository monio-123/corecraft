/**
 * ProseMirror doc → HTML（**编辑器自己的 toDOM**）。
 *
 * <p>为什么不用 markdown-it 另渲染一份：正文的渲染管线是 Milkdown（remark →
 * ProseMirror → DOM），再引一个渲染器就会出现两套渲染（标题层级 / 软换行 /
 * 字号 mark / 自定义节点全对不上）。⇒ 直接用 ProseMirror 官方的 `DOMSerializer`
 * 序列化 —— **和编辑器里渲染出来的是同一套标记**。
 *
 * <p>三个出口共用这一份：**对比预览**（docDiff.js 按块比）、**AI 回答渲染**
 * （useTopicAsk.js）、**文档导出**（PDF / Word，utils/exportDoc.js）。
 * 各写一份早晚会漂 —— 导出那侧漏了块壳就打印出一张白纸（2026-11-16 真实踩过）。
 */
import { DOMSerializer, Fragment } from '@milkdown/prose/model'

/**
 * 一个顶层节点 → HTML 字符串（对比预览按块比时用）。
 *
 * @param {object} node ProseMirror 节点
 * @returns {string}
 */
export function nodeToHtml(node) {
  const fragment = DOMSerializer.fromSchema(node.type.schema).serializeFragment(Fragment.from(node))
  const box = document.createElement('div')
  box.appendChild(fragment)
  return box.innerHTML
}

/**
 * doc → 整篇 HTML（不切块）。
 *
 * <p>给"整段渲染"用：AI 回答、**文档导出**（PDF / Word）。
 *
 * ⚠️ **不要用单节点那条路（`nodeToHtml(doc)`）**：doc 的 `content` 是块级
 * fragment，走单节点会把文档的**块壳**丢掉（只剩内联内容）⇒ 导出一片空白。
 *
 * @param {object|null} doc ProseMirror doc
 * @returns {string}
 */
export function htmlOf(doc) {
  if (!doc) return ''
  const fragment = DOMSerializer.fromSchema(doc.type.schema).serializeFragment(doc.content)
  const box = document.createElement('div')
  box.appendChild(fragment)
  return box.innerHTML
}
