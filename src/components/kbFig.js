/**
 * kbfig v2 统一画图协议入口（10 种正交图型：DOM 布局见 kbFigDom，矢量 SVG 见 kbFigSvg）。
 */
import { $prose } from '@milkdown/utils'
import { Plugin } from '@milkdown/prose/state'
import { createCodeFigureView, el } from './kbCodeFigure'
import { figKindOf } from './figureKinds'
import { figStats } from './kbFigureStats'
import { LANG, LIMIT, cut, parseFig, shapeOf } from './kbFigCore'
import {
  renderAnatomy,
  renderLayers,
  renderMatrix,
  renderMemory,
  renderPairs,
  renderStages,
  renderTimeline
} from './kbFigDom'
import { renderGraph, renderSequence, renderTree } from './kbFigSvg'

function renderFig(data) {
  const shape = shapeOf(data)
  if (!shape) return null
  const filled = (list) => Array.isArray(list) && list.length > 0
  let node = null
  if (shape === 'pairs') node = filled(data.rows) ? renderPairs(data) : null
  else if (shape === 'layers') node = filled(data.groups) ? renderLayers(data) : null
  else if (shape === 'tree') node = data.root && typeof data.root === 'object' ? renderTree(data) : null
  else if (shape === 'anatomy') node = filled(data.parts) ? renderAnatomy(data) : null
  else if (shape === 'matrix') node = filled(data.cols) && filled(data.rows) ? renderMatrix(data) : null
  else if (shape === 'memory') node = (data.container || filled(data.slots)) ? renderMemory(data) : null
  else if (shape === 'sequence') node = renderSequence(data)
  else if (shape === 'timeline') node = renderTimeline(data)
  else if (shape === 'graph') node = renderGraph(data)
  else if (filled(data.steps)) node = renderStages(data)

  if (!node) return null
  if (data.cap) node.append(el('div', 'kbfig__cap', cut(data.cap, 64)))
  figStats.types[shape] = (figStats.types[shape] || 0) + 1
  return { node, kind: figKindOf(shape, data) }
}

export function figView(node) {
  return createCodeFigureView(node, {
    prefix: 'kbfig',
    paint: ({ canvas, text }) => {
      const data = parseFig(text)
      const rendered = renderFig(data)
      if (!rendered) {
        if (data) figStats.renderFail++
        return {
          error: data
            ? '图没画出来：数据不完整或字段不符合 kbfig v2 规范'
            : '图没画出来：JSON 格式有误'
        }
      }
      canvas.append(rendered.node)
      return { kind: rendered.kind, title: data.title ? cut(data.title, LIMIT.title) : '' }
    }
  })
}

export function kbfigPlugin() {
  return $prose(
    () =>
      new Plugin({
        props: {
          nodeViews: {
            code_block: (node) => {
              const lang = String(node.attrs?.language || '').trim().toLowerCase()
              if (lang === LANG) return figView(node)
              return null
            }
          }
        }
      })
  )
}

export const FIG_LANG = LANG
