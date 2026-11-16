/**
 * 内置头像（2026-11-15，用户菜单「编辑个人信息」用，设计稿见 ui-preview/2026-11-15-usermenu）。
 *
 * - **全部 SVG 矢量** ⇒ 任意分辨率清晰，不存在"分辨率高一点"的诉求；打包进产物，零网络/存储成本。
 * - **主题款**（前 4 颗）颜色走 `var(--cc-primary)` / `var(--cc-primary-soft)` —— SVG 内联在 DOM 里，
 *   变量从主题解析 ⇒ 切主题自动变色（用户点名"适配主题"）。
 * - **装饰款**（后 4 颗）固定配色（渐变的 `id` 各自唯一；同一颗头像多实例同 id 引用同一份定义，
 *   渐变内容相同、渲染一致）。
 * - 存储：`sys_user.avatar` 存标识 `builtin:<id>`（varchar(255) 够用）；上传头像仍存 URL，两条路共存。
 */

/** @type {{ id: string, svg: string, theme: boolean }[]} 按选择条展示顺序 */
export const BUILTIN_AVATARS = [
  {
    id: 'ring',
    theme: true,
    svg: '<svg viewBox="0 0 48 48" xmlns="http://www.w3.org/2000/svg"><circle cx="24" cy="24" r="21" fill="var(--cc-primary-soft)"/><circle cx="24" cy="24" r="12" fill="none" stroke="var(--cc-primary)" stroke-width="3.4"/></svg>'
  },
  {
    id: 'mountain',
    theme: true,
    svg: '<svg viewBox="0 0 48 48" xmlns="http://www.w3.org/2000/svg"><circle cx="24" cy="24" r="21" fill="var(--cc-primary-soft)"/><path d="M10 33l9-11 7 8 5-6 7 9z" fill="var(--cc-primary)"/></svg>'
  },
  {
    id: 'waves',
    theme: true,
    svg: '<svg viewBox="0 0 48 48" xmlns="http://www.w3.org/2000/svg"><circle cx="24" cy="24" r="21" fill="var(--cc-primary-soft)"/><path d="M12 29q6-7 12 0t12 0M12 36q6-7 12 0t12 0" fill="none" stroke="var(--cc-primary)" stroke-width="2.6" stroke-linecap="round"/></svg>'
  },
  {
    id: 'star',
    theme: true,
    svg: '<svg viewBox="0 0 48 48" xmlns="http://www.w3.org/2000/svg"><circle cx="24" cy="24" r="21" fill="var(--cc-primary-soft)"/><path d="M24 12l2.6 7.4L34 22l-7.4 2.6L24 32l-2.6-7.4L14 22l7.4-2.6z" fill="var(--cc-primary)"/></svg>'
  },
  {
    id: 'night',
    theme: false,
    svg: '<svg viewBox="0 0 48 48" xmlns="http://www.w3.org/2000/svg"><defs><linearGradient id="ga-night" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#1d2b53"/><stop offset="1" stop-color="#0f1730"/></linearGradient></defs><circle cx="24" cy="24" r="23" fill="url(#ga-night)"/><circle cx="17" cy="16" r="1.6" fill="#fff" opacity=".9"/><circle cx="30" cy="12" r="1.1" fill="#fff" opacity=".7"/><circle cx="36" cy="22" r="1.3" fill="#fff" opacity=".8"/><circle cx="12" cy="28" r="1.1" fill="#fff" opacity=".6"/><circle cx="24" cy="34" r="1.4" fill="#fff" opacity=".75"/><circle cx="33" cy="31" r="1" fill="#fff" opacity=".55"/><path d="M28 20a7 7 0 1 0 4 12 8.5 8.5 0 1 1-4-12z" fill="#e8e4d0" opacity=".92"/></svg>'
  },
  {
    id: 'sun',
    theme: false,
    svg: '<svg viewBox="0 0 48 48" xmlns="http://www.w3.org/2000/svg"><defs><linearGradient id="ga-sun" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#f0a56c"/><stop offset="1" stop-color="#d95f4e"/></linearGradient></defs><circle cx="24" cy="24" r="23" fill="url(#ga-sun)"/><circle cx="24" cy="24" r="9" fill="#fff" opacity=".85"/><circle cx="24" cy="24" r="14.5" fill="none" stroke="#fff" stroke-width="1.6" opacity=".5"/></svg>'
  },
  {
    id: 'leaf',
    theme: false,
    svg: '<svg viewBox="0 0 48 48" xmlns="http://www.w3.org/2000/svg"><defs><linearGradient id="ga-leaf" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#3f7d5a"/><stop offset="1" stop-color="#1f5c45"/></linearGradient></defs><circle cx="24" cy="24" r="23" fill="url(#ga-leaf)"/><path d="M24 36c0-9 3-15 12-18-1 10-4 15-12 18z" fill="#dff0e2" opacity=".9"/><path d="M24 36c0-7-2-11-9-13 .8 7 3 11 9 13z" fill="#dff0e2" opacity=".6"/></svg>'
  },
  {
    id: 'orbit',
    theme: false,
    svg: '<svg viewBox="0 0 48 48" xmlns="http://www.w3.org/2000/svg"><defs><linearGradient id="ga-orbit" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#7c6bd6"/><stop offset="1" stop-color="#c65b8f"/></linearGradient></defs><circle cx="24" cy="24" r="23" fill="url(#ga-orbit)"/><ellipse cx="24" cy="24" rx="15" ry="9.5" fill="none" stroke="#fff" stroke-width="1.8" opacity=".75" transform="rotate(-18 24 24)"/><circle cx="24" cy="24" r="4.5" fill="#fff" opacity=".9"/></svg>'
  }
]

/**
 * 取内置头像的 SVG（非 `builtin:` 值返回空串，调用方走上传头像 / 兜底字的分支）。
 * @param {string} value avatar 字段的值（`builtin:<id>` 或上传 URL / 空）
 * @returns {string}
 */
export function builtinAvatarSvg(value) {
  if (typeof value !== 'string' || !value.startsWith('builtin:')) return ''
  const id = value.slice('builtin:'.length)
  const hit = BUILTIN_AVATARS.find((a) => a.id === id)
  return hit ? hit.svg : ''
}
