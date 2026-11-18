/**
 * 文档导出（PDF / Word）——**纯前端，零服务器**（2026-11-16，方案「编辑器工具区方案-1115」二稿）。
 *
 * 两条路线共用「编辑器自己的 DOMSerializer」产出的 HTML（docOf 同源）：
 * - **PDF = 浏览器打印转存**：开一个打印窗（同一份 HTML + 打印样式），用户在浏览器
 *   对话框里「另存为 PDF」⇒ 文字矢量、mermaid/表格原样（打印的是渲染结果，不重新解析）。
 *   语雀的卡是服务器转码队列；这条路没有队列，重活（PDF 引擎）是浏览器原生的。
 * - **Word = HTML-.doc**：Word/WPS 直接打开带 Word 命名空间头的 HTML 文件。
 *   字符串拼接 + Blob 下载，毫秒级。
 *
 * ⚠️ **mermaid 的兜底**：SVG 在 Word 里兼容一般 ⇒ 导出前把每张 svg 转成 PNG 内嵌
 * （canvas 客户端转换，每张 ~50ms）。PDF 不需要——打印引擎直接吃 SVG。
 */

/** 打印与 Word 导出共享的浅色主题变量表：PRINT_CSS `:root` 与 SVG `var()` 兜底同源，只维护一份。 */
const EXPORT_CSS_VARS = {
  '--cc-bg': '#ffffff',
  '--cc-surface': '#ffffff',
  '--cc-surface-2': '#f7f8fa',
  '--cc-surface-3': '#eef0f4',
  '--cc-text-1': '#1f2329',
  '--cc-text-2': '#4e5969',
  '--cc-text-3': '#646a73',
  '--cc-border': '#e5e6eb',
  '--cc-border-strong': '#d0d3d9',
  '--cc-primary': '#2b7fff',
  '--cc-warning': '#d97706',
  '--cc-success': '#16a34a',
  '--cc-danger': '#dc2626',
  '--font-body': 'Segoe UI, PingFang SC, Microsoft YaHei, sans-serif'
}

const PRINT_ROOT_VARS = Object.entries(EXPORT_CSS_VARS)
  .filter(([k]) => k.startsWith('--cc-'))
  .map(([k, v]) => `${k}: ${v};`)
  .join('\n    ')

/** 打印样式：PDF 导出（浏览器打印转存）。浅色纸底 + 展开折叠 + 防截断。 */
const PRINT_CSS = `
  :root {
    ${PRINT_ROOT_VARS}
  }
  body { margin: 0; padding: 32px 40px; background: #fff; color: #1f2329;
    font-family: 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
    font-size: 14px; line-height: 1.9; }
  h1, h2, h3, h4 { color: #1f2329; break-after: avoid; page-break-after: avoid; }
  pre, blockquote, table, figure, img { break-inside: avoid; page-break-inside: avoid; }
  pre { background: #f5f6f8; border: 1px solid #e5e6eb; border-radius: 6px; padding: 12px;
    white-space: pre-wrap; word-break: break-word; }
  code { font-family: Consolas, Monaco, monospace; font-size: 13px; }
  table { border-collapse: collapse; width: 100%; }
  th, td { border: 1px solid #d9dbe1; padding: 6px 10px; text-align: left; }
  blockquote { margin: 0; padding: 8px 16px; border-left: 3px solid #ccc;
    background: #f7f8fa; color: #4e5969; }
  img, svg { max-width: 100%; }
  hr { border: none; border-top: 1px solid #d9dbe1; }
  a { color: inherit; }
`

/** Word 文档头：mso 命名空间 + 基础样式（Word/WPS 打开 HTML 的老后门）。 */
function wordDocHtml(bodyHtml) {
  return `<html xmlns:o="urn:schemas-microsoft-com:office:office"
      xmlns:w="urn:schemas-microsoft-com:office:word"
      xmlns="http://www.w3.org/TR/REC-html40">
  <head><meta charset="utf-8">
  <style>
    body { font-family: 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
      font-size: 14px; line-height: 1.9; color: #1f2329; }
    pre { background: #f5f6f8; border: 1px solid #e5e6eb; padding: 12px;
      font-family: Consolas, Monaco, monospace; font-size: 13px;
      white-space: pre-wrap; word-break: break-word; }
    table { border-collapse: collapse; width: 100%; }
    th, td { border: 1px solid #d9dbe1; padding: 6px 10px; text-align: left; }
    blockquote { margin: 0; padding: 8px 16px; border-left: 3px solid #ccc;
      background: #f7f8fa; color: #4e5969; }
    img, svg { max-width: 100%; }
  </style></head>
  <body>${bodyHtml}</body></html>`
}

/** 触发浏览器下载（Blob → a[download]）。 */
function download(blob, filename) {
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = filename
  a.click()
  URL.revokeObjectURL(a.href)
}

/**
 * SVG → PNG dataURL（Word 兜底用）：画到 canvas 再读 dataURL。
 * 读不出来（跨域污染等）返回 null，调用方保留原 svg。
 */
function resolveSvgVars(clone) {
  const cs = getComputedStyle(document.documentElement)
  const resolveVal = (raw) => {
    if (!raw || !raw.includes('var(')) return raw
    return raw.replace(/var\(\s*(--[a-zA-Z0-9_-]+)\s*(?:,\s*([^)]+))?\)/g, (_, name, fb) => {
      const v = cs.getPropertyValue(name).trim()
      return v || EXPORT_CSS_VARS[name] || (fb ? fb.trim() : '#1f2329')
    })
  }
  const attrs = ['stroke', 'fill', 'color', 'stop-color']
  const all = [clone, ...clone.querySelectorAll('*')]
  for (const node of all) {
    for (const a of attrs) {
      const val = node.getAttribute(a)
      if (val && val.includes('var(')) node.setAttribute(a, resolveVal(val))
    }
    const st = node.getAttribute('style')
    if (st && st.includes('var(')) node.setAttribute('style', resolveVal(st))
  }
}

function svgToPngDataUrl(svg) {
  try {
    const w = svg.clientWidth || svg.viewBox?.baseVal?.width || 300
    const h = svg.clientHeight || svg.viewBox?.baseVal?.height || 150
    const clone = svg.cloneNode(true)
    clone.setAttribute('width', String(w))
    clone.setAttribute('height', String(h))
    resolveSvgVars(clone)
    const xml = new XMLSerializer().serializeToString(clone)
    const img = new Image()
    const url = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(xml)
    // 同步画不了（img.onload 异步）⇒ 这层包成 promise
    return new Promise((resolve) => {
      img.onload = () => {
        const canvas = document.createElement('canvas')
        canvas.width = w
        canvas.height = h
        const ctx = canvas.getContext('2d')
        ctx.drawImage(img, 0, 0, w, h)
        resolve(canvas.toDataURL('image/png'))
      }
      img.onerror = () => resolve(null)
      img.src = url
    })
  } catch {
    return Promise.resolve(null)
  }
}

/**
 * PDF 导出：开打印窗渲染同一份 HTML（+ 打印样式），调打印 —— 用户「另存为 PDF」。
 *
 * <p>用**独立打印窗**而不是 window.print()：当前页面带着侧栏/顶栏/主题底色，
 * 打它就得把整站样式抵消掉；打印窗只装文档自己的 HTML + PRINT_CSS，干净且不影响页面。
 *
 * @param {string} html 文档 HTML（编辑器 DOMSerializer 产物）
 * @param {string} title 打印窗标题（会成为 PDF 默认文件名）
 */
export function exportPdf(html, title) {
  const win = window.open('', '_blank', 'width=900,height=700')
  if (!win) return false
  win.document.write(`<html><head><meta charset="utf-8"><title>${title}</title>
    <style>${PRINT_CSS}</style></head><body>${html}</body></html>`)
  win.document.close()
  // 等渲染/字体就位再唤起打印（立刻打会拿到空白或未布局的文档）。
  // 只调一次：onload 在 document.write 的窗上不可靠，统一走延时兜底。
  setTimeout(() => { try { win.focus(); win.print() } catch { /* 窗被关了就算了 */ } }, 600)
  return true
}

/**
 * Word 导出：HTML-.doc（Word 命名空间头 + 下载）。
 *
 * @param {string} html 文档 HTML（编辑器 DOMSerializer 产物）
 * @param {string} filename 文件名（不带扩展名；非法字符由调用方先清）
 */
export async function exportWord(html, filename) {
  // mermaid 兜底：SVG 转 PNG 内嵌（Word 对 SVG 兼容一般）
  const holder = document.createElement('div')
  holder.innerHTML = html
  const svgs = [...holder.querySelectorAll('svg')]
  for (const svg of svgs) {
    const png = await svgToPngDataUrl(svg)
    if (png) {
      const img = document.createElement('img')
      img.src = png
      svg.replaceWith(img)
    }
  }
  const blob = new Blob(['\ufeff' + wordDocHtml(holder.innerHTML)],
    { type: 'application/msword;charset=utf-8' })
  download(blob, `${filename}.doc`)
}
