/**
 * 界面主题（2026-11-08）：本色（默认）/ 素雅 / 量子；2026-11-13 曜夜（深色）上线。
 *
 * 挂载点 = html[data-theme]（documentElement）—— EP 的 dialog/popper 传送门挂在
 * body 下，只有挂在 html 上 token 才罩得住传送门。代价：管理后台壳也会跟着变色
 * （token 全局生效，零专项适配；用户已拍板管理后台不做专项）。
 *
 * ## ⚠️⚠️ 加新主题时必读（**加载适配是最容易漏的一项**）⚠️⚠️
 *
 * **新主题 = 颜色 + 加载签名 + 骨架材质，三件都要给。**
 * 只复制一份颜色 token 不叫适配完成 —— 那样加载图形还是本色的蓝环、
 * 骨架还是本色的冷灰，落在新主题里就是两个"外来件"。
 *
 * 加主题时要做的事：
 *   ① 在 `assets/themes.css` 写 `html[data-theme="xxx"] { … }` 覆盖块
 *   ② 在这个文件的 `VALID` 与 `themeList` 里登记
 *   ③ **定义 `--kb-load-*`（加载签名）与 `--kb-skel-*`（骨架材质）** ——
 *      加载层（`assets/kb-loading.css` + `components/corecraft-web/KbLoader.vue`）
 *      的图形由这组 token 决定，换主题不用碰组件代码。
 *      现有四种签名：本色=环形｜素雅=墨迹行（横线）｜量子=双轨环+中心点
 *      ｜曜夜=单环+脉动金点（--kb-load-dot-anim / --kb-load-glow 开启）。
 *      新主题要挑一个与它气质相配的图形，别默认环形。
 *   ④ 验：对比度 / prefers-reduced-motion 下仍可辨识 / EP 传送门跟随 /
 *      加载前后容器尺寸不变
 *
 * 完整清单见 brove-skills/corecraft-web-context/SKILL.md「主题适配清单」。
 *
 * ## 主题值住哪（2026-11-09 改：入个人配置）
 *
 * 2026-11-09 之前只存 localStorage（当时的取舍："主题先不做跨设备"），用户报：
 * 「明明是素雅 每次进来都显示的是本色……这个没保存到个人的那个配置中么？」
 * ⇒ 迁入配置树（`ui.theme`，后端 sys_config），跟侧边栏宽度同一条通道：
 *   跨会话、跨设备都在。localStorage 只剩两个辅助角色：
 *   - `app-ui-prefs`（uiPrefs 的本地缓存，首屏不等接口）；
 *   - `ui-theme`（老键）：2026-11-09 前的存量选择，读序里兜底 + 首屏防闪镜像。
 *
 * ## 选中态与视觉必须同一条数据源
 *
 * 之前选中框是组件里一个一次性 ref（setup 时读一次 localStorage），视觉走
 * html[data-theme] —— 两条独立状态线，localStorage 写入失败 / 登出 clearAuth()
 * 清库 / 组件重挂载，任一发生就分裂（用户截图实锤：视觉素雅、选中框停在本色）。
 * 现在**唯一真相 = uiPrefs 的生效值**：选中框由 computed 派生（SettingsDialog），
 * data-theme 由 watchEffect 跟着同一条源走（initTheme）——结构上不可能再分裂。
 */
import { watchEffect } from 'vue'
import { getUiPref, setUiPref } from './uiPrefs'

/** 配置树 / 后端 sys_config 里的键（命名空间 ui 下） */
const KEY = 'ui.theme'
/** 老 localStorage 键（2026-11-09 前只存这个）：存量兜底 + 首屏防闪镜像 */
const LEGACY_KEY = 'ui-theme'
const VALID = ['bense', 'suya', 'liangzi', 'yaoye']
const DEFAULT = 'bense'

/**
 * peek = 设置弹窗「风格」小卡里的**微缩预览**四色（纸面 / 侧栏 / 线 / 主色）。
 *
 * ⚠️ 为什么写死在这里而不是运行时读 token：预览要**同时**画出三个主题的长相，
 * 而 getComputedStyle 只能读到当前主题那一份 ⇒ 只能按主题登记。
 * 四个色值镜像 themes.css 里对应主题的 --cc-bg / --cc-side-bg / --cc-text-4 / --cc-primary，
 * **加新主题时和主题块一起给**（这正是登记在 themeList 上的理由：一处加齐）。
 */
export const themeList = [
  {
    key: 'bense', name: '本色',
    peek: { paper: '#fafbfc', side: '#eef1f5', line: '#c6cbd2', accent: '#2b7fff' }
  },
  {
    key: 'suya', name: '素雅',
    peek: { paper: '#f7f4ec', side: '#fbf9f2', line: '#b6ad97', accent: '#2f7d6d' }
  },
  {
    key: 'liangzi', name: '量子',
    peek: { paper: '#f4f6fb', side: '#eef1f8', line: '#9aa7ba', accent: '#5b51e8' }
  },
  {
    key: 'yaoye', name: '曜夜',
    peek: { paper: '#0f1117', side: '#14161d', line: '#262b38', accent: '#e8b059' }
  }
]

/**
 * 生效主题：**配置树生效值（后端/本地缓存） > 老键 > 默认**。
 *
 * ⚠️ 读 `getUiPref` 而不是自己 read localStorage：它内部读 uiPrefs 的响应式
 * 生效值 ⇒ 选中框的 computed 会跟着后端的值重算（这正是"同一条数据源"的机制）。
 */
export function currentTheme() {
  const v = getUiPref(KEY)
  if (VALID.includes(v)) return v
  return readLegacy() || DEFAULT
}

function readLegacy() {
  try {
    const v = localStorage.getItem(LEGACY_KEY)
    if (VALID.includes(v)) return v
  } catch (e) { /* 隐私模式等存不了就落默认 */ }
  return null
}

/** 挂 html[data-theme]（themes.css 的覆盖块认它）+ 老键镜像（首屏防闪读它） */
function applyTheme(v) {
  if (v !== DEFAULT) document.documentElement.dataset.theme = v
  else delete document.documentElement.dataset.theme
  try { localStorage.setItem(LEGACY_KEY, v) } catch (e) { /* 存不了只影响"记住" */ }
}

/** 换主题：立即生效（挂属性），再走配置树存档（跨设备/跨会话） */
export function setTheme(v) {
  if (!VALID.includes(v)) return
  applyTheme(v)
  setUiPref(KEY, v)
}

/**
 * 应用启动时调用一次（main.js）：**建立「生效值 → html[data-theme]」的响应式绑定**。
 *
 * 不再只是挂一次属性：配置树的值后到、或者中途换主题，属性都跟着同一条源走。
 * 绑定建立的当下立即跑一遍 —— 首屏就用本地缓存/老键的颜色，不闪默认色。
 */
export function initTheme() {
  watchEffect(() => applyTheme(currentTheme()))
  watchEffect(() => applyFont(currentFont()))
}

/**
 * 老键里存的选择搬进配置树（**只在配置树还没有主题键时推**，推完即幂等）。
 * 由 session.js 在 `loadRemotePrefs()` 之后调 —— 要等后端的值回来才知道"有没有"。
 */
export function migrateThemeToConfig() {
  if (getUiPref(KEY) != null) return
  const legacy = readLegacy()
  if (legacy) setUiPref(KEY, legacy)
}

/* ================= 字体档（2026-11-15，三档收敛 D） =================
 * 正文/展示字体从主题解耦成配置（html[data-font]）：默认 sans（无衬线，
 * 原本色 body 栈 = 用户说的「耀夜的标准字体」）；serif = 原素雅衬线栈。
 * 素雅主题块里的自动衬线已撤——切素雅想要衬线来这里选。
 * 与主题同一条通道（配置树 + getUiPref 同源），入口在设置弹窗「界面」组。 */
const FONT_KEY = 'ui.font'
const FONT_VALID = ['sans', 'serif']
const FONT_DEFAULT = 'sans'

export function currentFont() {
  const v = getUiPref(FONT_KEY)
  return FONT_VALID.includes(v) ? v : FONT_DEFAULT
}

function applyFont(v) {
  if (v !== FONT_DEFAULT) document.documentElement.dataset.font = v
  else delete document.documentElement.dataset.font
}

export function setFont(v) {
  if (!FONT_VALID.includes(v)) return
  applyFont(v)
  setUiPref(FONT_KEY, v)
}
