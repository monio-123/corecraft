/**
 * 可缩放的图片节点。
 *
 * ## 为什么覆写 preset 的 image
 *
 * preset 给的 `image` 只有 `src` / `alt` / `title` **三个属性，没有尺寸**——
 * 所以"调整比例"在这套编辑器里根本不存在，不是没接上（2026-10-18 用户报的现象）。
 * Milkdown 的 `$node` 用 `upsertById` 按 id 覆盖，本模块在 `.use(commonmark)` **之后**
 * 注册就会把 preset 那份顶掉。
 *
 * ## 尺寸存在哪：`title` 里的一段标记
 *
 * `image` 的 `title` 在 preset 里**本来就往返**（`parseMarkdown` 读 mdast 的 `title`，
 * `toMarkdown` 原样写回），而标准 markdown 的图片语法没有任何尺寸字段。所以：
 *
 *     `![](https://x/a.png "w:640")`   ← 640px 宽
 *
 * **为什么不让 title 直接存 "640"**：会和真的标题撞。语雀里写
 * `![](url "架构图")` 是很正常的用法，那句话会被当成尺寸、图就缩没了。
 * 所以用 `w:` 前缀，**只有 `^w:\\d+$` 才当尺寸**，其余一律还是标题。
 *
 * ⚠️ 代价：markdown 文本里会出现 `w:640` 这么一段。渲染出来看不出来——
 * `toDOM` 把 title 清空，浏览器上**不会有 tooltip**，只有查看原文才看得见。
 *
 * ## 拖拽手柄
 *
 * nodeView 而不是 CSS：拖拽要拿到 `getPos` 才能把新尺寸写回文档，
 * 纯 CSS 改不动数据。这是本项目里第一处 nodeView（其余交互都走
 * `handleDOMEvents`，见 linkInteractions）。
 */
import { Plugin } from '@milkdown/prose/state'
import { $node, $prose } from '@milkdown/utils'

const IMAGE_ID = 'image'

/** title 里尺寸标记的判据。**不是 `^w:\\d+$` 的一律当普通标题** */
const WIDTH_RE = /^w:(\d{1,5})$/

const readWidth = (title) => (WIDTH_RE.test(title || '') ? Number(WIDTH_RE.exec(title)[1]) : null)

const writeWidth = (title, width) => (width ? `w:${width}` : title || '')

export function imageNodePlugin() {
  return $node(IMAGE_ID, (ctx) => ({
    inline: true,
    group: 'inline',
    selectable: true,
    draggable: true,
    marks: '',
    atom: true,
    defining: true,
    isolating: true,
    attrs: {
      src: { default: '', validate: 'string' },
      alt: { default: '', validate: 'string' },
      title: { default: '', validate: 'string' },
      /** 像素宽度。`null` = 不限（跟着 max-width 走） */
      width: { default: null }
    },
    parseDOM: [{
      tag: 'img[src]',
      getAttrs: (dom) => {
        if (!(dom instanceof HTMLElement)) throw new Error('期望 DOM 元素')
        const w = Number(dom.getAttribute('width') || dom.style.width.replace('px', ''))
        return {
          src: dom.getAttribute('src') || '',
          alt: dom.getAttribute('alt') || '',
          title: dom.getAttribute('title') || '',
          width: Number.isFinite(w) && w > 0 ? Math.round(w) : null
        }
      }
    }],
    toDOM: (node) => ['img', { src: node.attrs.src, alt: node.attrs.alt, title: '', width: node.attrs.width || null }],
    parseMarkdown: {
      match: ({ type }) => type === 'image',
      runner: (state, node, type) => {
        const title = node.title || ''
        state.addNode(type, {
          src: node.url,
          alt: node.alt,
          // 标题原样保留（"架构图" 就是标题），尺寸另存
          title: WIDTH_RE.test(title) ? '' : title,
          width: readWidth(title)
        })
      }
    },
    toMarkdown: {
      match: (node) => node.type.name === IMAGE_ID,
      runner: (state, node) => {
        state.addNode('image', void 0, void 0, {
          title: writeWidth(node.attrs.title, node.attrs.width),
          url: node.attrs.src,
          alt: node.attrs.alt
        })
      }
    }
  }))
}

/**
 * 拖角改宽度。
 *
 * ⚠️ **`atom: true` + nodeView 的坑**：nodeView 的 `update` 只在**节点本身被替换**时
 * 被调用，同一个节点改 attr 不会触发它。所以宽度变化时不能指望 `update` 把
 * `style.width` 刷新掉 —— 拖拽过程中直接在 mousemove 里写 DOM、松手再落到文档，
 * 两次都不经过 `update`。
 */
export function imageResizeView() {
  return $prose(
    () =>
      new Plugin({
        props: {
          nodeViews: {
            [IMAGE_ID]: (node, view, getPos) => {
              const dom = document.createElement('span')
              dom.className = 'kb-img'
              const img = document.createElement('img')
              img.src = node.attrs.src
              img.alt = node.attrs.alt || ''
              img.draggable = false
              const handle = document.createElement('span')
              handle.className = 'kb-img__handle'
              handle.title = '拖动改宽度'
              dom.appendChild(img)
              dom.appendChild(handle)

              let width = node.attrs.width
              let resizing = false

              const paint = () => {
                img.style.width = width ? `${width}px` : ''
                // 拖拽中才给"正在改"的样式：选中状态由 ProseMirror 的 node 选中类负责
                dom.classList.toggle('is-resizing', resizing)
              }

              const isSelected = () => {
                const { selection } = view.state
                return !selection.empty && selection.from <= getPos() && getPos() < selection.to
              }
              const syncSelected = () => dom.classList.toggle('is-selected', isSelected())

              let startX = 0
              let startWidth = 0

              const onDown = (e) => {
                e.preventDefault()
                const pos = getPos()
                if (pos == null) return
                startX = e.clientX
                // 从当前显示宽度起步，而不是从"真实宽度"起步 ——
                // 图可能被 max-width 压小过，按真实宽度起算会一拖就跳
                startWidth = img.getBoundingClientRect().width
                resizing = true
                paint()
                document.addEventListener('mousemove', onMove)
                document.addEventListener('mouseup', onUp)
              }

              const onMove = (e) => {
                if (!resizing) return
                const next = Math.max(MIN_WIDTH, Math.round(startWidth + (e.clientX - startX)))
                if (next === width) return
                width = next
                paint()
              }

              const onUp = () => {
                document.removeEventListener('mousemove', onMove)
                document.removeEventListener('mouseup', onUp)
                if (!resizing) return
                resizing = false
                paint()
                const pos = getPos()
                if (pos == null) return
                view.dispatch(
                  view.state.tr.setNodeMarkup(pos, null, { ...view.state.doc.nodeAt(pos).attrs, width }).setMeta('addToHistory', true)
                )
              }

              handle.addEventListener('mousedown', onDown)

              paint()

              return {
                dom,
                // atom 节点没有内容区。**不设 ignoreMutation 的话，
                // 我们自己改 style 会被 ProseMirror 当成"外部改了 DOM"再同步回去，
                // 表现为拖一下就被弹回原宽度。
                ignoreMutation: () => true,
                update: (updated) => {
                  if (updated.type !== node.type) return false
                  node = updated
                  width = updated.attrs.width
                  if (img.src !== updated.attrs.src) img.src = updated.attrs.src
                  paint()
                  syncSelected()
                  return true
                },
                selectNode: () => {
                  dom.classList.add('is-selected')
                },
                deselectNode: () => {
                  dom.classList.remove('is-selected')
                },
                stopEvent: (e) => handle.contains(e.target)
              }
            }
          }
        }
      })
  )
}

/** 小到这个宽度就没法看了。低于它时双击复位成"不限宽" */
const MIN_WIDTH = 80
