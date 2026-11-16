/**
 * 附件节点（2026-11-16）——正文里的「一个文件」不再是**文字链接**，是一块**原子**。
 *
 * ## 为什么要有这个节点
 *
 * 附件原来就是一条 markdown 链接（`[文件名](/api/files/…��`）：
 * - **光标能停在文件名中间** ⇒ 用户在文件右侧的空白处点一下，光标被吸进文件名里
 *   （那一段唯一的文字就是链接文字）⇒ 右侧写不了字、想删文件也变成「改文件名」；
 * - 图标只能是 CSS 伪元素里的一个 📎 emoji，所有类型一个样。
 *
 * ⇒ 改成 **inline atom node**：
 * - 原子 ⇒ 光标进不去、选中即整体、Backspace/Delete 整块删；
 * - 节点自带图标（按扩展名分家族，见 `utils/fileIcons.js`）与文件名；
 * - 单击 = 下载（与现在的行为一致）。
 *
 * ## 正文格式一个字都没变
 *
 * 节点在 markdown 里仍然序列化成 `[文件名](url)`（`toMarkdown`），
 * 解析时把「指向 `/api/files/` 的链接」还原成节点（`parseMarkdown`）——
 * ⇒ **老正文进编辑器自动变成原子节点**（光标问题连老内容一起治好），
 * DB 里存的还是原来那串 markdown，**零迁移**。
 *
 * ## 抢在内置 link 标记之前（为什么不会两边打架）
 *
 * Milkdown 的解析器按 `{...schema.nodes, ...schema.marks}` 顺序取**第一个**命中的匹配器
 * （`@milkdown/transformer` 的 `ParserState.#matchTarget`）⇒ 自定义**节点**先于内置
 * **标记**被查到 ⇒ 附件链接归本节点，其它链接仍是普通链接。
 *
 * @see kbImageNode.js —— 同样的 atom + nodeView + markdown 往返写法。
 */
import { $node, $prose } from '@milkdown/utils'
import { Plugin } from '@milkdown/prose/state'
import { fileIconSvg } from '../utils/fileIcons'

const FILE_ID = 'kbFile'

/** 附件的判据：指向文件服务的链接（与渲染层认附件的那条完全一致） */
const FILE_PREFIX = '/api/files/'

/** mdast 链接节点里的文字（= 文件名） */
function linkText(node) {
  const kids = node.children || []
  return kids
    .map((c) => (c && c.type === 'text' ? c.value : ''))
    .join('')
    .trim()
}

export function fileNodePlugin() {
  return $node(FILE_ID, () => ({
    inline: true,
    group: 'inline',
    selectable: true,
    draggable: true,
    /** 原子块不带任何标记**（它不是一段可加样式的文字） */
    marks: '',
    atom: true,
    defining: true,
    isolating: true,
    attrs: {
      /** 相对路径（正文里存的那份） */
      href: { default: '', validate: 'string' },
      /** 文件名（显示 + 下载名都靠它） */
      name: { default: '', validate: 'string' }
    },
    parseDOM: [
      {
        tag: 'a[data-kb-file]',
        getAttrs: (dom) => ({
          href: dom.getAttribute('href') || '',
          name: dom.textContent || ''
        })
      }
    ],
    toDOM: (node) => ['span', { 'data-kb-file': '', 'data-href': node.attrs.href, class: 'kb-file' }],
    parseMarkdown: {
      match: ({ type, url }) => type === 'link' && typeof url === 'string' && url.startsWith(FILE_PREFIX),
      runner: (state, node, type) => {
        // ⚠️ **解析器的 addNode 签名是 (type, attrs, content)** —— attrs 在第二位。
        //    把 attrs 传到第三位（content）会变成 "Can not convert [object Object]
        //    to a Fragment"，且被 addNode 内部的 console.error 吞掉 ⇒ 节点静默变空段落
        //    （2026-11-16 实测踩过：症状是附件链接「整段消失」且 window 无报错）。
        state.addNode(type, { href: node.url, name: linkText(node) })
      }
    },
    toMarkdown: {
      match: (node) => node.type.name === FILE_ID,
      runner: (state, node) => {
        // 写回 markdown 仍是那条链接 ⇒ 正文格式、DB、老内容的兼容性全不变
        state.addNode('link', [{ type: 'text', value: node.attrs.name }], undefined, { url: node.attrs.href })
      }
    }
  }))
}

/**
 * 附件的渲染视图：**一块 chip**（图标 + 文件名），单击下载。
 *
 * ⚠️ `atom: true` + nodeView 的老坑（见 kbImageNode 的注释）：
 * `update` 只在节点被替换时调用 ⇒ 属性变化要自己同步。
 */
export function fileNodeView() {
  return $prose(
    () =>
      new Plugin({
        props: {
          nodeViews: {
            [FILE_ID]: (node, view, getPos) => {
              const dom = document.createElement('span')
              dom.className = 'kb-file'
              dom.dataset.kbFile = ''

              const ico = document.createElement('span')
              ico.className = 'kb-file__ico'
              ico.innerHTML =
                `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">${fileIconSvg(node.attrs.name)}</svg>`

              const name = document.createElement('span')
              name.className = 'kb-file__name'
              name.textContent = node.attrs.name || '文件'

              dom.appendChild(ico)
              dom.appendChild(name)
              dom.title = node.attrs.name || ''

              // 单击 = 下载（与现在「点链接就下载」一致）。
              // ⚠️ 必须 preventDefault：不然浏览器会新标签打开（而且是下载协议）。
              dom.addEventListener('click', (e) => {
                e.preventDefault()
                e.stopPropagation()
                const href = node.attrs.href
                if (href) window.open(href, '_blank', 'noopener,noreferrer')
              })

              return {
                dom,
                // 原子节点没有内容区 ⇒ 必须 ignoreMutation，否则 PM 判它是 dirty 反复重建
                ignoreMutation: (mutation) => !dom.contains(mutation.target),
                update: (updated) => {
                  if (updated.type.name !== FILE_ID) return
                  node = updated
                  name.textContent = updated.attrs.name || '文件'
                  dom.title = updated.attrs.name || ''
                  ico.innerHTML =
                    `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">${fileIconSvg(updated.attrs.name)}</svg>`
                }
              }
            }
          }
        }
      })
  )
}
