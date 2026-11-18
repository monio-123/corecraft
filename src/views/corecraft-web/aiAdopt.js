/**
 * AI 回答 → 能写进 kp_topic 的值。
 *
 * ## 为什么在前端解析
 *
 * 四条引导问题的 `remark` 都把输出格式写死了（"只输出名字本身" / "从清单里挑，原样输出名字"
 * / "每个一行、前面加 - " / 每题 4 个选项 1 个答案），
 * 格式是**已知的**，不需要为了稳妥再花一次模型调用去换一份 JSON。
 *
 * ## 解析不出来就返回 null，不猜也不兜底
 *
 * 格式没对上说明模型没照做，这时候硬凑出来的值是垃圾——**写进库比不写更糟**，
 * 用户还得再手工改回去。返回 null 时界面上不给出采纳入口，用户仍可复制原文自行处理。
 * 同理不要在这里写"实在解析不出就取前 N 个字"之类的兜底。
 *
 * ## 这不是「从自由文本里猜结构」
 *
 * 它只认引导问题里那两个 key（rename / pick_category）。
 * 用户自己敲的问题 `questionKey` 是空的，压根不进这里——它没有"该写进哪个字段"的语义
 * （要把它的答案写进正文是另一件事，不在采纳范围内）。
 */

/**
 * questionKey 的 key 段 → kp_topic 的字段名。
 *
 * <p>⚠️ **`pick_tags` 不在里面**（2026-11-03 用户拍板：标签功能从页面上去掉，
 * 后台逻辑暂时不去）：「挑标签」那个引导问题不再出现在界面上，
 * 它的答案也就没有"该写进哪个字段"的语义了 ⇒ 不给采纳按钮。
 * ⚠️ **`organize` 也不在了**（2026-11-16 D2 AI 归类退役）：它一次产出三个字段
 * （标题/目录/标签），那条引导问题连同它的配置一起下线了 ⇒ 这里只剩单字段的两条。
 */
const ADOPT_FIELDS = {
  rename: 'title',
  pick_category: 'categoryId'
}

/** 这条回答能不能被采纳；不能就返回 null（界面上不给按钮） */
export function adoptFieldOf(questionKey) {
  return ADOPT_FIELDS[String(questionKey || '').split(':')[0]] || null
}

/**
 * 下拉里「+ 新建目录「X」」那一项的值。
 *
 * **用字符串不能用数字**：目录 id 是数字，哨兵混进去会被当成一个真 id 传下去，
 * `updateTopic` 的归属校验会报一个跟真实原因完全不搭的「目录不存在或不属于当前用户」。
 * 字符串和数字永远不会相等，所以不撞。
 *
 * 跟 `newCategory` 是配套的（一个是"建什么、挂哪"，一个是"建"这个动作），所以住在一起。
 */
export const NEW_CATEGORY = '__new__'

/** 第一段非空行。模型的回答常带一句解释说明，真正要的值在第一行 */
function firstLine(text) {
  return String(text || '').split('\n').map((s) => s.trim()).find(Boolean) || ''
}

/**
 * 剥掉包裹的引号和句末标点。
 *
 * **结尾的引号和标点要在同一个字符类里一起剥**，不能先剥引号再剥标点：
 * 模型很爱输出「MyBatis 转换失败」。——句号在引号**外面**，先剥引号时末尾还是「。」
 * 那一步什么都没剥掉，剥完标点引号就露在结果里了。
 * 反引号（markdown 代码引号）同理：`` `Git 配置` `` 不该带着反引号存进库。
 */
function cleanValue(text) {
  return String(text || '')
    .replace(/^[「『"'`]+/, '')
    .replace(/[。.，,、」』"'`]+$/, '')
    .trim()
}

/** 只要第一行（模型的回答前面常带解释） */
function cleanName(text) {
  return cleanValue(firstLine(text))
}


/**
 * 解析成 `updateTopic` 的 patch。解析不出来返回 null。
 *
 * @param {object} turn 一轮问答（用它的 questionKey 和 answer）
 * @param {Array}  categories 现有目录，用于把 AI 给的目录名对成 categoryId
 */
export function parseAdopt(turn, categories) {
  switch (adoptFieldOf(turn?.questionKey)) {
    case 'title':
      return parseRename(turn?.answer)
    case 'categoryId':
      return parseCategory(turn?.answer, categories)
    default:
      return null
  }
}

function parseRename(answer) {
  const title = cleanName(answer)
  return title ? { title } : null
}

/**
 * 建一个「目录 → 完整路径」的计算器：`数据库/持久化/Mybatis`。
 *
 * **遍历带 visited 集合**：父子成环（a 的父是 b、b 的父是 a）时靠它收住，
 * 否则是死循环。而这个环是可能的——目录可以自己新增、自己拖。
 */
function pathBuilder(categories) {
  const byId = new Map((categories || []).filter((c) => c.id != null).map((c) => [c.id, c]))
  return (cat) => {
    const parts = []
    const seen = new Set()
    let cur = cat
    while (cur && !seen.has(cur.id)) {
      seen.add(cur.id)
      parts.unshift(cur.name)
      cur = cur.parentId ? byId.get(cur.parentId) : null
    }
    return parts.join('/')
  }
}

/**
 * 目录路径 → categoryId；**匹配不上就当成"AI 建议的新目录"**。
 *
 * ## 输入是一个路径，不是一个名字
 *
 * 斜杠左边是上级、右边是这一条要归的目录：`数据库/MySQL`。
 * 目录树是**真的分层的**（`数据库/持久化/Mybatis`），所以
 *  - 完整路径能定死是哪一个：不同父目录下允许同名（唯一键是
 *    (user_id, parent_id, name)），只有路径能区分
 *  - 只给名字（`MySQL`）是相对路径：按名字找，重名取第一个
 *  - 给了上级但末段不在（`数据库/MySQL`）：这就是"建议新建"
 *
 * ## 上级对不上，两种结果
 *
 * 上级路径匹配不到 → **返回"解析失败"，不是"建在顶层"**。
 * 建在顶层是**猜**：用户以为它挂在「数据库」下，结果挂在根上，
 * 而且界面上看不出来。宁可这一条不采纳。
 */
function matchCategory(rawPath, categories) {
  const list = categories || []
  const isLeaf = (cat) => !list.some((c) => c.parentId === cat.id)
  const pathOf = pathBuilder(list)
  const text = cleanValue(rawPath)
  if (!text) return { categoryId: null, newCategory: null }

  const exact = list.find((c) => pathOf(c) === text)
  if (exact) {
    return isLeaf(exact) ? { categoryId: exact.id, newCategory: null } : { categoryId: null, newCategory: null }
  }

  const parts = text.split('/').map((s) => s.trim()).filter(Boolean)
  const name = parts[parts.length - 1]
  const parentPath = parts.slice(0, -1).join('/')

  if (!parentPath) {
    // 只给了名字：按名字找（大小写不敏感是最后一层，不做模糊匹配）
    const hit =
      list.find((c) => c.name === name) ||
      list.find((c) => String(c.name).toLowerCase() === name.toLowerCase())
    if (hit) {
      return isLeaf(hit) ? { categoryId: hit.id, newCategory: null } : { categoryId: null, newCategory: null }
    }
    return { categoryId: null, newCategory: { name, parentId: null } }
  }

  const parent = list.find((c) => pathOf(c) === parentPath)
  if (!parent) return { categoryId: null, newCategory: null }
  return { categoryId: null, newCategory: { name, parentId: parent.id } }
}

/** 一个目录从顶层到它的完整路径：`数据库/持久化/Mybatis`。给界面显示用 */
export function categoryPathOf(cat, categories) {
  return pathBuilder(categories)(cat)
}

/**
 * AI 建议的新目录该显示成什么：`数据库 / MySQL`。
 *
 * 前缀用**父目录的完整路径**而不是父名——父目录可能在第三层（`数据库/持久化`），
 * 只给个父名看不出这条会挂到哪。挂顶层时没有前缀，就只有新名。
 *
 * 界面有两处要显示它（下拉里的选项、行上的目录文字），只差一个 `+ 新建 ` 前缀——
 * 拼接收敛在这儿，两边各自加自己的前缀。改格式只改这一处。
 */
export function newCategoryPath(nc, categories) {
  if (!nc?.name) return ''
  const p = nc.parentId ? (categories || []).find((c) => c.id === nc.parentId) : null
  const prefix = p ? categoryPathOf(p, categories) : ''
  return prefix ? `${prefix} / ${nc.name}` : nc.name
}

function parseCategory(answer, categories) {
  return matchCategory(cleanName(answer), categories)
}



