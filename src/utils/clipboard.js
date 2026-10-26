import { ElMessage } from 'element-plus'

/**
 * 复制一段文本。**全项目只有这一个出口**。
 *
 * 原来有两条，行为还不一致：链接工具条的 `copyLink` 把 Promise 丢了、连成功都没有
 * 提示；AI 回答的 `copyAnswer` 至少有 `ElMessage`。用户反馈「复制按钮点击没反应」——
 * **"点了没反应"是最难查的一类症状**，因为它既可能是失败了，也可能是成功了但没告诉
 * 用户，两种情况在界面上长得一模一样。所以这里三条路都试，并且成败都有反馈。
 *
 * ## 为什么需要降级
 *
 * `navigator.clipboard` 只在**安全上下文**（HTTPS / localhost）且**文档处于焦点**时可用。
 * 任何一条不满足，它要么是 `undefined`（直接抛 TypeError），要么静默 reject 一个
 * `NotAllowedError`——两种都不报错，界面上就是"点了没反应"。
 * 本项目的部署形态里这三条都可能不成立：局域网 HTTP 访问、Electron 内嵌浏览器、
 * 以及浮层上的 `@mousedown.prevent`（它保住了编辑器选区，代价是文档可能处于失焦态）。
 *
 * `document.execCommand('copy')` 是被标记为废弃但**至今所有浏览器仍然支持**的老接口，
 * 对焦点的要求宽松得多。留着它当兜底，比留一句「请用 HTTPS 访问」有用。
 */

/** 老办法：临时 textarea + execCommand。要求文档在同一用户手势内 */
function copyByExecCommand(text) {
  const ta = document.createElement('textarea')
  ta.value = text
  ta.setAttribute('readonly', '')
  // 不能用 display:none / visibility:hidden —— 那样选不中，execCommand 直接返回 false
  ta.style.position = 'fixed'
  ta.style.top = '-9999px'
  ta.style.opacity = '0'
  document.body.appendChild(ta)
  ta.select()
  let ok = false
  try {
    ok = document.execCommand('copy')
  } catch {
    ok = false
  }
  document.body.removeChild(ta)
  return ok
}

/**
 * @param {string} text 要复制的文本
 * @returns {Promise<boolean>} 是否复制成功（**只用于测试和判断，界面上已经提示过了**）
 */
export async function copyText(text) {
  const s = String(text ?? '')
  if (!s) {
    ElMessage.warning('没有可复制的内容')
    return false
  }
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(s)
      ElMessage.success('已复制')
      return true
    }
  } catch {
    // 落到下面的降级，不在这里报错——降级成功的话用户根本不需要知道它失败过
  }
  if (copyByExecCommand(s)) {
    ElMessage.success('已复制')
    return true
  }
  ElMessage.error('复制失败，请手动选中后按 Ctrl+C')
  return false
}