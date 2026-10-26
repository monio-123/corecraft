/**
 * 界面偏好：**键 → CSS 变量**，加上"这些键现在是什么值"。
 *
 * ## 分工（2026-10-21 改）
 *
 * | 什么 | 在哪 |
 * |---|---|
 * | 有哪些项、各项叫什么、属于哪一组 | **库里的配置树**（`sys_config_node`），管理员在「系统管理 → 系统配置」里改 |
 * | 值 | 库里的 `sys_config`（系统默认 + 每个人的覆盖） |
 * | **能拖到多少、落到哪些 CSS 变量上** | **这里**（代码，`PREF_SPECS`） |
 *
 * ⚠️ **滑块的范围/步长留在代码里，不进配置树**（用户："还有耦合问题"）：
 * 配置树是**通用的系统级 KV**，新建一条普通 KV 时界面上不该凭空多出
 * 「最小 / 最大 / 步长 / 内置默认 / 单位」五个填不上的框 ——
 * 那是界面偏好这一件专属的东西。范围怎么说明？写在那一项的「说明」里。
 *
 * 原来"有哪些项"也写死在这个文件里（`PREF_GROUPS` / `PREF_ITEMS`），
 * 于是加一项偏好 = 改代码 + 发版；现在加一项 = 配置树上加一行。
 * （CSS 变量映射还是要写——CSS 变量不可能从数据库里长出来。）
 *
 * ## 值从哪来
 *
 * **生效值 = 我的覆盖 > 系统默认 > 内置默认（`PREF_SPECS` 里的 `def`）。**
 *
 * ⚠️ **只有"人设过的"才写进行内变量**（我的覆盖 / 系统默认那一行）。
 * 内置默认不写——它本来就写在 CSS 层那边（`base.css` 的 `:root`、
 * 各组件里的 `var(--x, 兜底)`），写一遍是多余的。
 *
 * 这条规则不是为了省事，是**为了保住"正文白边"那一项的自动值**：
 * 它的内置默认是「跟着字号缩放的自动值」，钉成 34px 就把这个自动值废了。
 * 没设过的项一律不写 ⇒ 走的还是 CSS 那个自动值。
 *
 * ## 本地那份只是缓存
 *
 * 接口没回来之前首屏不用等；接口挂了也能用。后端才是真相。
 *
 * ⚠️ **这里原来还有一整套 `--ui-scale` 乘数（顶栏一个百分比 chip、85/100/115/130 四档），
 * 2026-10-07 整个删掉了。** 删的理由不是"不够好看"，是两条更硬的：
 *
 * 1. **说不清它是什么的比例。** 那个数乘的是 66 个毫不相干的数值——2px 边框、4px 间距、
 *    1440px 内容宽一起乘 1.3。没有任何一句话能解释 130% 代表什么。
 * 2. **粒度对不上。** 想调某一类做不到：动 4px 会连带把 1440px 和 2px 一起动。
 *    用户原话："**只能统一调无法精确调，某个地方我很满意但是另外的地方不满意**"。
 *
 * 换成什么：**没有全局旋钮了，按维度一个一个来。** 组件里写 `var(--token)`，
 * 面板改的是 token 的**具体值**。
 *
 * ⚠️ **正文字号现在在里面了**（`ui.detail.editorFs`，详情组「正文字号」，2026-10-09 加）。
 * 之前这里写的是"字号不在其中，由浏览器 Ctrl +/− 管"，理由是浏览器缩放会把界面和正文
 * 一起缩、粒度太粗。**这条规矩撤销了**——但**不是**改成接管全站字号，而是另开一个
 * `--editor-fs` 只给编辑器读：**要的是"正文能单独放大"，不是"整站放大"**。
 *
 * ⚠️ 也**别用 CSS `zoom` 顶替字号这个空缺**——zoom 在渲染那一刻乘，会把 JS 行内样式的
 * 像素值也乘一遍，于是「JS 量到的坐标」和「浏览器画出来的位置」对不上
 * （拖拽幽灵飘到光标旁边、`elementFromPoint` 命中的不是你看到的那行、
 * EP 浮层同样飘）。自建拖拽和 EP 浮层定位都靠「JS 坐标 = 视口坐标」这条不变式。
 */
import { ref } from 'vue'
import { clearMyConfig, getConfigNodes, getMyConfig, saveMyConfig } from '../api/config'

/** 本地缓存（只是缓存，后端才是真相） */
const PREF_KEY = 'app-ui-prefs'
/** 一次性迁移标记：老版本（只有本地、没有后端）调好的值要搬上后端一次 */
const MIGRATED_KEY = 'app-ui-prefs-migrated'
/** 后端 sys_config 里界面偏好的命名空间 = 配置树上界面偏好那一块的根（根节点的 code = 命名空间） */
const UI_NS = 'ui'

/**
 * 面板上的百分比 → 一对 flex-grow 比值（录入框 : 弹性尾巴）。存的是百分比（人能读懂），
 * 写进 CSS 的是比值。
 *
 * ⚠️ **必须成对写，不能只写一个**。之前只有 `--home-quick-grow`，省下的高度靠下面两排
 * 一起长来消化；现在下面两排固定自身高度（用户要求），如果还只写一个，录入区就成了
 * 唯一的 grow 项 —— 它会**恒定地拿走全部剩余高度**，滑块变成一根废的（实测 65%–100%
 * 完全不动，ProseMirror 都是 232px）。
 *
 * 100% 时尾巴取 0（不是极小值）：默认档必须**一点不多一点不少**等于现状。
 */
function quickGrow(share) {
  if (share >= 100) return { quick: 999, tail: 0 }
  const quick = share / (100 - share)
  return { quick, tail: 1 / quick }
}

/**
 * 界面偏好项的**规格**（范围 / 步长 / 单位 / 内置默认）+ CSS 变量投影。
 *
 * ⚠️ **为什么规格在代码里，不在配置树上**（用户 2026-10-21："还有耦合问题"）：
 * 配置树是**通用的系统级 KV**，每一项只有「中文描述 / 编码 / 值 / 说明」——
 * 滑块的上下限、步长是**界面偏好这一件专属的**东西，塞进通用结构里就是耦合：
 * 新建一条普通 KV 时，界面上凭空多出五个填不上的输入框。
 * **范围怎么说明？写在配置项的「说明」里**（人能读懂那句话），
 * 结构化的那部分在这里——两边各管各的。
 *
 * 键 → CSS 变量怎么投影，是这一份文件里唯一的"行为"；每一条写的是
 * "这个值落到哪些变量上、怎么换算"；名字、范围、默认值、层级全在配置树上，
 * **不在这里重复写一遍**。
 *
 * - `--side-py` / `--side-gap` 一起写：这两个本来就是同一个"一行有多松"
 *   （原来的三档密度里它们三档全都相等），拆成两个滑块只会得到
 *   "行内元素贴死、行与行之间还松着"的怪状态。
 * - `ui.home.homeQuick` 是**唯一**的百分比项：它调的不是长度，是"剩下的高度怎么分"。
 * - `ui.detail.detailAskW` **不在这里**：AI 抽屉宽度是 `el-drawer :size` 的 JS 值，
 *   不是 CSS 变量。EP 会给 `size` 拼 px 后缀，喂一个 `var(...)` 进去会变成
 *   `var(--detail-ask-w)px` —— 一个非法宽度，抽屉直接打不开。
 * - `ui.detail.editorFs` 只接 `--editor-fs`，**不接 `--text-md`**：那是全站通用的字号
 *   token，侧边栏、顶栏、按钮、表格全在读它，接上去就是"整个站点跟着变大"。
 * - **没有「阅读宽度」**：详情页不再有任何宽度封顶（2026-10-21 删掉）。
 */
const PREF_SPECS = {
  'ui.side.sideWidth': { min: 200, max: 360, step: 4, unit: 'px', def: 264, css: (v) => ({ '--w-side': `${v}px` }) },
  'ui.side.sidePadX': { min: 0, max: 32, step: 2, unit: 'px', def: 12, css: (v) => ({ '--side-px': `${v}px` }) },
  'ui.side.sidePadY': { min: 0, max: 24, step: 1, unit: 'px', def: 7, css: (v) => ({ '--side-py': `${v}px`, '--side-gap': `${v}px` }) },
  'ui.home.homeQuick': {
    min: 30, max: 100, step: 5, unit: '%', def: 100,
    css: (v) => {
      const g = quickGrow(v)
      return { '--home-quick-grow': String(g.quick), '--home-tail-grow': String(g.tail) }
    }
  },
  'ui.home.homePadX': { min: 0, max: 120, step: 4, unit: 'px', def: 32, css: (v) => ({ '--home-pad-x': `${v}px` }) },
  'ui.home.homeGap': { min: 0, max: 64, step: 4, unit: 'px', def: 24, css: (v) => ({ '--home-gap': `${v}px` }) },
  'ui.learn.learnGap': { min: 0, max: 48, step: 2, unit: 'px', def: 16, css: (v) => ({ '--learn-gap': `${v}px` }) },
  'ui.learn.learnPadX': { min: 0, max: 120, step: 4, unit: 'px', def: 32, css: (v) => ({ '--learn-pad-x': `${v}px` }) },
  'ui.learn.learnGridGap': { min: 0, max: 48, step: 2, unit: 'px', def: 14, css: (v) => ({ '--learn-grid-gap': `${v}px` }) },
  'ui.learn.learnPillPy': { min: 0, max: 20, step: 1, unit: 'px', def: 3, css: (v) => ({ '--learn-pill-py': `${v}px` }) },
  'ui.detail.detailTagsGap': { min: 0, max: 64, step: 2, unit: 'px', def: 20, css: (v) => ({ '--detail-tags-gap': `${v}px` }) },
  'ui.detail.detailTextInset': { min: 20, max: 96, step: 2, unit: 'px', def: 34, css: (v) => ({ '--detail-text-inset': `${v}px` }) },
  'ui.detail.editorFs': { min: 12, max: 20, step: 1, unit: 'px', def: 14, css: (v) => ({ '--editor-fs': `${v}px` }) },
  // ---- 正文排版（2026-10-24 加）----
  // 标题原来**一条字号规则都没有** ⇒ 走浏览器默认（h3 1.17em vs 正文 14px，
  // 几乎看不出差别），加上全局 reset 把标题上下 margin 归零 ⇒ 层次全靠加粗撑着。
  // 下面的比例写在 kb-editor.css 里（那边才是排版的地方），这里只管"能拖到多少"。
  //
  // ⚠️ **标题不给独立字号，给一个"放大倍数"**：
  // 标题字号 = 正文字号 × 固定层级比例 × 这个倍数 ⇒ 层次永远成比例，
  // 不会出现"标题调大了、正文没动"的失衡。拖它 = 整体调标题大小（不改编排比例）。
  // 真要改比例（层级感更强/更弱）得改 CSS，那是排版规范的事，不是"我的偏好"。
  'ui.detail.headingScale': {
    min: 0.8, max: 1.6, step: 0.05, unit: '×', def: 1,
    css: (v) => ({ '--kb-head-scale': String(v) })
  },
  // 标题上下留白。**上下不对称是 CSS 那边写死的比例**（上 = 它 ×1.8…0.8 随层级递减，
  // 下 = 它 ×0.6…0.35），这里拖的是那个基准值。
  'ui.detail.headingGap': { min: 0, max: 40, step: 2, unit: 'px', def: 20, css: (v) => ({ '--kb-head-gap': `${v}px` }) },
  // 行高：行与行之间的距离（比值，1.8 = 1.75 的旧值再松一点，语雀那档）。
  'ui.detail.lineHeight': { min: 1.4, max: 2.2, step: 0.05, unit: '', def: 1.8, css: (v) => ({ '--kb-line-height': String(v) }) },
  // 段间距：段落之间留多少。**列表 / 表格 / 引用 / 代码块也用这一个值**
  //（它们跟段落是同一件事的"块与块之间" ⇒ 一处定义，不各自一套）。
  'ui.detail.paraGap': { min: 0, max: 24, step: 2, unit: 'px', def: 12, css: (v) => ({ '--kb-para-gap': `${v}px` }) },
  // 段首缩进：几个字宽。**单位是 em 不是 px** —— 缩进要跟着字号缩放，
  // 写死 px 的话，字号一调大缩进就不够看了。
  'ui.detail.paraIndent': { min: 0, max: 4, step: 1, unit: '字', def: 0, css: (v) => ({ '--kb-para-indent': `${v}em` }) },
  // 问答抽屉宽度：**JS 值，不是 CSS 变量**（el-drawer 的 size 会把 var() 拼成非法宽度）。
  // 它没有 css ⇒ 不进偏好面板（面板上那根滑块改不到任何东西），
  // 但规格要留着：详情页拖动把手时用它夹上下限（见 AppTopicDetailView 的 clampAskW）。
  'ui.detail.detailAskW': { min: 320, max: 1200, step: 20, unit: 'px', def: 420 },
  // AI 调整正文抽屉的宽度：同样是 JS 值（同一段理由：size 只吃真数字）。
  // **单独一项，不复用问答那一项**：两边形态不同 —— 问答是一问一答，420px 够；
  // 调整正文是**左右两栏并排对比**，420px 分两半每栏只剩 210px，读不下去 ⇒ 默认 720。
  'ui.detail.detailEditW': { min: 480, max: 1200, step: 20, unit: 'px', def: 720 }
}

/** 探针值：只为拿到"这个键会写哪些变量名"，值本身不重要 */
const PROBE = 100

/** 一个键会写哪些 CSS 变量（只要名字）。**没有 css 投影的键（JS 值）不写任何变量** */
function varNamesOf(configKey) {
  const spec = PREF_SPECS[configKey]
  return spec && spec.css ? Object.keys(spec.css(PROBE)) : []
}

/** 所有会被写进行内的变量：摘的时候一次摘干净 */
const ALL_VARS = Object.keys(PREF_SPECS).flatMap(varNamesOf)

/**
 * 配置树上的那一组（`{ key, title, items }`），响应式。
 *
 * **面板直接读它**：有哪些组、每组有哪些项、叫什么、能拖到多少 —— 全部来自树上。
 * 树还没到的时候是空数组，面板就还没东西可画（会话引导会先把它拉回来）。
 */
export const prefGroups = ref([])

/** 生效值（键 → 数字）：我的覆盖 > 系统默认 > 内置默认 */
let effective = {}
/** 系统默认（后端 user_id=0 那几行）——「恢复系统默认」之后要回到这里 */
let defaults = {}
/** 哪些键"人设过"（我的覆盖或系统默认那一行）——只有它们写进行内变量 */
let explicit = new Set()
/** 后端的值已经回来过了没有（回来了就别再拿本地缓存盖它） */
let remoteLoaded = false

/**
 * 本地缓存（键 → 数字）。**它只是缓存，后端才是真相**。
 *
 * 留一份本地的是为了两件事：①接口没回来之前首屏不用等；②接口挂了也能用。
 */
function readCache() {
  try {
    const raw = localStorage.getItem(PREF_KEY)
    return raw ? JSON.parse(raw) : {}
  } catch {
    return {}
  }
}

function writeCache(obj) {
  localStorage.setItem(PREF_KEY, JSON.stringify(obj))
}

/**
 * 老版本（2026-10-21 之前）本地缓存用的是短名（`sideWidth`），现在用全键
 * （`ui.side.sideWidth`）。**两份都读**：短名那份搬过来，老用户不用重新调一遍。
 */
function legacyKey(configKey) {
  const i = configKey.lastIndexOf('.')
  return i < 0 ? configKey : configKey.slice(i + 1)
}

/** 把偏好写到 `<html>` 的行内 style。行内压过样式表，所以用户拖一下立刻生效 */
function applyPrefs(values) {
  const el = document.documentElement
  for (const [configKey, v] of Object.entries(values)) {
    const spec = PREF_SPECS[configKey]
    if (!spec || !spec.css) continue
    for (const [name, value] of Object.entries(spec.css(v))) el.style.setProperty(name, value)
  }
}

/**
 * 壳启动时调一次。
 *
 * **顺序**：先套本地缓存（不等接口，首屏就位），后端的值由
 * `bootstrapSession` 拉回来（它在路由守卫里 await，页面出来时已经就位）。
 *
 * ⚠️ **后端回来过之后，本地缓存就不能再往上盖了** —— 本地只是缓存，
 * 拿它盖后端 = 用旧值覆盖新值（症状：管理员刚改完系统默认，刷新一下又变回去）。
 */
export function applyUiPrefs() {
  if (!remoteLoaded) applyPrefs(readCache())
}

/**
 * 拉配置树 + 后端的生效值。
 *
 * ⚠️ **两个请求各兜各的**：配置树取不到（后端还没上新版本）不该把值也一起丢掉，
 * 反过来也一样。**生效值完全由后端给的键值对决定，不依赖配置树** ——
 * 树只决定"面板上有哪些行"和"哪些值算人设过的"。
 *
 * ⚠️ **拉不到就当没这回事**：界面偏好在后端挂了的时候不该拦住用户干活。
 * 失败提示由请求拦截器统一弹过一次（那是全站唯一的提示出口），这里不再弹第二遍。
 */
export async function loadRemotePrefs() {
  const cached = readCache()
  try {
    const [cfgRes, nodeRes] = await Promise.all([
      getMyConfig(UI_NS).catch(() => null),
      getConfigNodes().catch(() => null)
    ])
    const nodes = nodeRes?.data || []
    if (nodeRes) {
      prefGroups.value = buildGroups(nodes)
    }

    const values = cfgRes?.data?.values || {}
    const defaultsRaw = cfgRes?.data?.defaults || {}
    const eff = {}
    const defs = {}
    // "人设过" = 后端有那一行（系统默认或我的覆盖）；内置默认不算
    for (const [configKey, raw] of Object.entries(values)) {
      if (raw == null || !Number.isFinite(Number(raw))) continue
      eff[configKey] = Number(raw)
    }
    for (const [configKey, raw] of Object.entries(defaultsRaw)) {
      if (raw == null || !Number.isFinite(Number(raw))) continue
      defs[configKey] = Number(raw)
    }
    defaults = defs
    explicit = new Set(Object.keys(eff))

    // 迁移要靠树认得那些键（老缓存里是短名）。**树没到就不动缓存**——
    // 否则会把老缓存里那些还没搬走的短名条目冲掉，用户的偏好就真丢了。
    if (nodeRes) {
      const merged = await migrateOnce(cached, values, nodes)
      for (const [k, v] of Object.entries(merged)) {
        eff[k] = v
        explicit.add(k)
      }
    }
    effective = eff
    remoteLoaded = true
    const written = pickExplicit(eff)
    applyPrefs(written)
    if (nodeRes) {
      writeCache(written)
    }
  } catch {
    // 拉不到就用本地缓存那份（applyUiPrefs 已经套过了）
  }
}

/** 只留"人设过"的那些值：内置默认不写进行内变量（CSS 层那边本来就有） */
function pickExplicit(values) {
  const out = {}
  for (const [k, v] of Object.entries(values)) {
    if (explicit.has(k)) out[k] = v
  }
  return out
}

/**
 * 一次性迁移：老版本（偏好只在本地、没有后端）调好的值，搬成"我自己的覆盖"推上去。
 *
 * **只在第一次做**（本地留个标记）：推完之后后端就有这份值了，
 * 再推一次反而会把系统默认变成我的个人覆盖（以后改系统默认就不影响我了）。
 * 只推后端还没有的那些键 —— 后端有的（系统的或别人的）不动。
 *
 * ⚠️ **只推树上认得的键**：老缓存里可能有别的键（老版本用的是短名），
 * 照着推会在库里留下没人认领的垃圾行。
 *
 * @return 这次搬上去的那些值（调用方要一并算进生效值，
 *         否则这一轮界面显示的还是默认值，要等下次刷新才对）
 */
async function migrateOnce(cached, backendValues, nodes) {
  if (localStorage.getItem(MIGRATED_KEY)) return {}
  const items = []
  const merged = {}
  for (const item of itemsOf(nodes)) {
    const v = cached[item.configKey] != null ? cached[item.configKey] : cached[legacyKey(item.configKey)]
    if (typeof v !== 'number') continue
    if (backendValues[item.configKey] != null) continue
    items.push({ key: item.configKey, value: String(v) })
    merged[item.configKey] = v
  }
  if (items.length) await saveMyConfig(items)
  localStorage.setItem(MIGRATED_KEY, '1')
  return merged
}

export function getUiPrefs() {
  return { ...effective }
}

/**
 * 改偏好：**本地立刻生效**（拖滑块的手感不能等接口），后端的写攒一批延迟发。
 *
 * 攒批是因为滑块每拖一格就回调一次 —— 每一格打一个接口，拖一次就是几十个请求。
 */
export function saveUiPrefs(patch) {
  effective = { ...effective, ...patch }
  explicit = new Set([...explicit, ...Object.keys(patch)])
  const written = pickExplicit(effective)
  writeCache(written)
  applyPrefs(written)
  queueRemoteSave(patch)
}

/** 待提交给后端的改动（键 → 值） */
let pending = {}
let pendingTimer = null

function queueRemoteSave(patch) {
  pending = { ...pending, ...patch }
  if (pendingTimer) clearTimeout(pendingTimer)
  pendingTimer = setTimeout(flushRemoteSave, 400)
}

async function flushRemoteSave() {
  pendingTimer = null
  const batch = pending
  pending = {}
  const items = Object.entries(batch).map(([key, v]) => ({ key, value: String(v) }))
  if (!items.length) return
  try {
    await saveMyConfig(items)
  } catch {
    // 失败提示请求拦截器已经弹过；本地那份还在，下次进来还是这个值
  }
}

/** 壳卸载时用：只摘行内，不动存档。侧边栏那组变量在别的壳里没有意义 */
export function clearUiPrefs() {
  const el = document.documentElement
  for (const k of ALL_VARS) el.style.removeProperty(k)
}

/**
 * 「恢复系统默认」：把行内全摘掉让 CSS 那边接管，**并删掉后端里"我自己的覆盖"**。
 *
 * `only` 给的是组 key 数组，**只处理这几组**：面板按自己显示的组来调，
 * 在首页面板点「恢复系统默认」不该把用户调好的侧边栏宽度一起抹掉。
 * 不传 = 全部（`resetUiPrefs()` 保留这个名字给"清空全部"用）。
 *
 * ⚠️ **只摘行内不够，还要删后端**：行内摘了、后端还留着我的覆盖 ⇒
 * 下次拉后端又把它盖回来。症状是「恢复系统默认」看着生效、刷新一下又变回去。
 */
export async function resetUiPrefs(only) {
  // 不传就是全部——各组是同一件事的几个范围，不是几段逻辑。
  const groups = only || prefGroups.value.map((g) => g.key)
  const picked = prefGroups.value.filter((g) => groups.includes(g.key))
  const el = document.documentElement
  for (const g of picked) {
    for (const it of g.items) {
      for (const name of varNamesOf(it.configKey)) el.style.removeProperty(name)
    }
  }
  // 本地：这几项回到系统默认（系统默认也没有就回到内置默认 ⇒ 摘掉行内）
  const next = { ...effective }
  for (const g of picked) {
    for (const it of g.items) {
      if (defaults[it.configKey] != null) next[it.configKey] = defaults[it.configKey]
      else delete next[it.configKey]
    }
  }
  effective = next
  writeCache(pickExplicit(next))
  applyPrefs(pickExplicit(next))
  // 后端：删掉这几组的"我自己的覆盖"（= 恢复系统默认）
  await Promise.all(picked.map((g) => clearMyConfig(`${g.configKey}.`)))
}

// ==================== 配置树 → 面板的数据 ====================

/**
 * 树 → 面板的组：`[{ key, title, configKey, items: [...] }]`。
 *
 * **名字来自树，规格来自代码**：
 * - 树回答"这一项叫什么、属于哪一组"（通用配置项的事）；
 * - `PREF_SPECS` 回答"能拖到多少、落到哪些 CSS 变量"（界面偏好专属的事）。
 *
 * **面板只列 `PREF_SPECS` 里有的那些键** —— 树上的通用 KV（AI 提示词那些）
 * 不是界面偏好，不该出现在偏好面板里。
 */
function buildGroups(nodes) {
  const byParent = new Map()
  for (const n of nodes) {
    if (!byParent.has(n.parentId)) byParent.set(n.parentId, [])
    byParent.get(n.parentId).push(n)
  }
  const root = nodes.find((n) => n.configKey === UI_NS)
  if (!root) return []
  return (byParent.get(root.id) || []).map((g) => ({
    key: g.code,
    title: g.name,
    configKey: g.configKey,
    items: (byParent.get(g.id) || []).map(toItem).filter(Boolean)
  }))
}

/** 一个叶子节点 → 面板上的一行。**没有 CSS 投影的键不进面板**（树上别的通用 KV 也是） */
function toItem(n) {
  const spec = PREF_SPECS[n.configKey]
  if (!spec || !spec.css) return null
  return {
    configKey: n.configKey,
    name: n.name,
    unit: spec.unit,
    min: spec.min,
    max: spec.max,
    step: spec.step,
    defaultValue: spec.def
  }
}

/** 树里 ui 命名空间下的所有数值项（算生效值时按它过一遍） */
function itemsOf(nodes) {
  return buildGroups(nodes).flatMap((g) => g.items)
}

/**
 * 某一项的规格（上下限 / 步长 / 内置默认）。
 *
 * 给**值不在 CSS 变量上的项**用（现在是 AI 问答抽屉的宽度：它是 JS 值，
 * 面板上调不动它，但详情页拖把手时要拿它的上下限去夹）。
 *
 * 规格表里没有的键返回 null —— 调用方要自己兜（别拿 undefined 去算 clamp）。
 */
export function getPrefSpec(configKey) {
  const spec = PREF_SPECS[configKey]
  if (!spec) return null
  return {
    configKey,
    unit: spec.unit,
    min: spec.min,
    max: spec.max,
    step: spec.step,
    defaultValue: spec.def
  }
}
