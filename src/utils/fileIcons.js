/**
 * 文件类型图标（2026-11-16）。
 *
 * **为什么按"家族"分而不是按扩展名分**：16px 下同一家族的图标（doc / pdf / md / txt）
 * 形状本来就没法区分，用户认的是文件名后面的扩展名。⇒ 只分**形状家族**：
 * 文档 / 表格 / 演示 / 压缩 / 图片 / 音视频 / 其他。
 *
 * **颜色不写死**：图标是 SVG（`currentColor` 描边），随所在文字的颜色走 ⇒
 * 切主题不用重画（与 kbFig 的「颜色走 --cc-*」同一条原则）。
 *
 * 图形语言与全站 1.7px 圆帽线性族一致（`KbSvgIcon` 同一套规矩）。
 */

/** @type {{ match: string[], svg: string }[]} 命中顺序 = 显示顺序 */
const FAMILIES = [
  {
    // 表格：xls / xlsx / csv / ods / numbers
    match: ['xls', 'xlsx', 'csv', 'ods', 'numbers', 'et'],
    svg: '<rect x="4" y="5" width="16" height="14" rx="2"/><path d="M4 10h16M4 15h16M10 5v14"/>'
  },
  {
    // 演示：ppt / pptx / odp / key
    match: ['ppt', 'pptx', 'odp', 'key'],
    svg: '<rect x="3.5" y="5" width="17" height="11" rx="2"/><path d="M12 16v3M8.5 19h7M7 12.5l3-2.5 2.5 2 3.5-4"/>'
  },
  {
    // 压缩包：zip / rar / 7z / tar / gz
    match: ['zip', 'rar', '7z', 'tar', 'gz', 'bz2'],
    svg: '<path d="M4 8.5h16v10a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 18.5z"/><path d="M9.5 8.5V6h5v2.5M10 12h4M10 15.5h4"/>'
  },
  {
    // 图片：png / jpg / jpeg / gif / webp / bmp / svg / tiff / heic
    match: ['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp', 'svg', 'tiff', 'tif', 'heic', 'ico'],
    svg: '<rect x="3.5" y="5" width="17" height="14" rx="2"/><circle cx="9" cy="10" r="1.6"/><path d="M4.5 17l5-5 3.5 3.5 2.5-2.5 4 4"/>'
  },
  {
    // 音视频：mp4 / mov / avi / mkv / mp3 / wav / flac / m4a
    match: ['mp4', 'mov', 'avi', 'mkv', 'webm', 'mp3', 'wav', 'flac', 'm4a', 'aac', 'ogg'],
    svg: '<rect x="3.5" y="5.5" width="17" height="13" rx="2"/><path d="M10.5 9.5l4.5 2.5-4.5 2.5z"/>'
  },
  {
    // 代码：js / ts / java / py / go / rs / html / css / json / xml / sh / sql
    match: ['js', 'ts', 'jsx', 'tsx', 'java', 'kt', 'py', 'go', 'rs', 'c', 'cpp', 'h', 'php', 'rb', 'swift', 'html', 'htm', 'css', 'scss', 'less', 'json', 'xml', 'yml', 'yaml', 'sh', 'bat', 'sql', 'vue'],
    svg: '<path d="M9 8.5L5.5 12 9 15.5M15 8.5L18.5 12 15 15.5"/>'
  },
  {
    // 文档：doc / docx / md / txt / rtf / pdf / odt / wps / pages
    match: ['doc', 'docx', 'md', 'markdown', 'txt', 'rtf', 'pdf', 'odt', 'wps', 'pages', 'epub', 'mobi'],
    svg: '<path d="M6 3.5h7.5L18 8v12.5H6z"/><path d="M13.5 3.5V8H18M9 12h6M9 15.5h4"/>'
  }
]

/** 兜底图标（不认识的文件类型） */
const FALLBACK = '<path d="M6 3.5h7.5L18 8v12.5H6z"/><path d="M13.5 3.5V8H18"/>'

/**
 * 文件名 → 图标 SVG（内联字符串）。
 *
 * @param {string} name 文件名（取扩展名）
 * @returns {string} SVG 内部内容（不含 <svg> 外壳）
 */
export function fileIconSvg(name) {
  const ext = String(name || '').toLowerCase().split('.').pop() || ''
  if (!name || name.indexOf('.') < 0) return FALLBACK
  for (const f of FAMILIES) {
    if (f.match.indexOf(ext) >= 0) return f.svg
  }
  return FALLBACK
}
