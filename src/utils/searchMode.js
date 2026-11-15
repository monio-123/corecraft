/**
 * 搜索的**两档**：搜什么 + 结果长什么样。
 *
 * ## 为什么是两档，而不是"在哪个页面就长什么样"
 *
 * 原来同一个顶栏搜索框，**在列表页给卡片、在树视图给树** ⇒ 同一个词两种样子，
 * 用户得自己记"我在哪一屏才看到哪种结果"（2026-11-05 用户拍板：「在首页和在某个目录中
 * 进行搜索的行为还是不一样 我觉得行为要保持一致」）。
 *
 * ⇒ 现在**结果的形态由这一档决定，与你在哪个页面无关**：
 * - 「正文」那档：搜**知识点正文**，结果是**卡片**；
 * - 「目录」那档：搜**目录**（目录名 + 知识点的标题），结果是**目录树**。
 *
 * ## 两档各自搜什么
 *
 * | 档 | 搜什么 | 服务端 scope | 结果形态 |
 * |---|---|---|---|
 * | 正文 | 知识点**标题 + 正文** | `both` | **卡片**（直接过滤，只留命中的） |
 * | 目录 | **目录名**（本地）+ 知识点**标题** | `title` | **目录树**（树完整展示，标出命中的文字） |
 *
 * ⚠️ **目录名是本地匹配的** —— 目录列表本来就在浏览器里（`getCategories()`），
 * 不必也不该往服务端跑一趟 ⇒ 服务端那档只管**知识点标题**。
 *
 * ⚠️ **正文那档搜「标题 + 正文」**（用户 2026-11-05：「对于卡片栏搜索模式
 * 搜索的包括正文和正文标题」）⇒ scope 是 `both`（服务端那条默认语义）。
 * 它那一档**只做过滤**：命中什么就只剩什么，**不画命中栏、不把命中的标蓝**
 * —— 一屏全是命中的卡片，再标一遍等于没标。
 */

/** 搜正文（默认那档）⇒ 结果是卡片。 */
export const SEARCH_MODE_CONTENT = 'content'
/** 搜目录（目录名 + 知识点标题）⇒ 结果是目录树。 */
export const SEARCH_MODE_CAT = 'cat'

/**
 * scope 的三个合法取值（**与服务端 `KpTopicQuery#normalizeScope` 一一对应**）。
 *
 * <p>`both` = 标题 + 正文都搜（**默认那条语义**，老调用方不受影响）。
 */
export const SEARCH_SCOPE_BOTH = 'both'
export const SEARCH_SCOPE_CONTENT = 'content'
export const SEARCH_SCOPE_TITLE = 'title'

/** scope 归一：认不出来的按 `both` 算（一个手改坏的参数不该把搜索搞空）。 */
export function normalizeSearchScope(scope) {
  const s = String(scope || '').trim()
  if (s === SEARCH_SCOPE_CONTENT || s === SEARCH_SCOPE_TITLE) return s
  return SEARCH_SCOPE_BOTH
}

/** 切换按钮上写什么（**两个字**——它就贴在搜索框里，不能占地方）。 */
export const SEARCH_MODE_LABEL = {
  [SEARCH_MODE_CONTENT]: '正文',
  [SEARCH_MODE_CAT]: '目录'
}

/**
 * 每一档在**服务端**搜哪一块（`scope` 参数）。
 *
 * <p>⚠️ **和服务端 `KpTopicQuery#normalizeScope` 一一对应** ——
 * 前端传错一个值，服务端会静默按 `both`（标题 + 正文都搜）算，
 * 于是"目录那档"里混进了正文命中的条目，而界面上什么异常都看不出来。
 *
 * <p>⚠️ **正文那档是 `both` 不是 `content`**（用户 2026-11-05：
 * 「对于卡片栏搜索模式 搜索的包括正文和正文标题」）—— 卡片栏要的是
 * "标题里有的 **和** 正文里有的，都算数"。
 */
export const SEARCH_MODE_SCOPE = {
  [SEARCH_MODE_CONTENT]: SEARCH_SCOPE_BOTH,
  [SEARCH_MODE_CAT]: SEARCH_SCOPE_TITLE
}

/**
 * 读 URL 上的模式（`?mode=`）。
 *
 * <p>⚠️ **认不出来的按默认那档算** —— 一个手改坏的参数不该把页面搞空
 * （和 `rangeFromRoute` 那条一个道理）。
 *
 * @param {object} route vue-router 的 route 对象
 * @returns {'content'|'cat'}
 */
export function searchModeFromRoute(route) {
  const m = String(route?.query?.mode || '')
  return m === SEARCH_MODE_CAT ? SEARCH_MODE_CAT : SEARCH_MODE_CONTENT
}

/**
 * 这一档在服务端搜哪一块。
 *
 * @param {'content'|'cat'} mode
 * @returns {'content'|'title'}
 */
export function searchScopeOf(mode) {
  return mode === SEARCH_MODE_CAT
    ? SEARCH_MODE_SCOPE[SEARCH_MODE_CAT]
    : SEARCH_MODE_SCOPE[SEARCH_MODE_CONTENT]
}

/**
 * 这一档是不是「搜目录」那档。
 *
 * @param {'content'|'cat'} mode
 * @returns {boolean}
 */
export function isCatMode(mode) {
  return mode === SEARCH_MODE_CAT
}

/**
 * **目录名算不算命中** —— 只有「搜目录」那档要。
 *
 * <p>⚠️ **这条规则只有这一份**：侧边栏（AppLayout）、列表页（AppLearnView）、
 * 目录树（CategoryTree）三处都要它。各写一遍就会漂
 * （症状：侧边栏按目录名算命中、树按知识点标题算命中 ⇒ 两边数出来的命中条数对不上，
 * **而界面上什么异常都看不到**）。
 *
 * <p>⚠️ **判据是档位（URL 上的 `?mode=`），不是"索引正在用哪个 scope"** ——
 * 索引是异步来的，换档到它回来之间有一小段时间它还是上一档的 scope
 * ⇒ 拿它当判据，侧边栏和树会在那一小段时间里对不上。
 *
 * @param {object} route vue-router 的 route 对象
 * @returns {boolean}
 */
export function matchNameFromRoute(route) {
  return isCatMode(searchModeFromRoute(route))
}
