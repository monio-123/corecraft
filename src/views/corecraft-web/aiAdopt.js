/**
 * AI 回答 → 能写进 kp_topic 的值。
 *
 * ## 为什么在前端解析
 *
 * 四条引导问题的 `remark` 都把输出格式写死了（"只输出名字本身" / "从清单里挑，原样输出名字"
 * / "每个一行、前面加 - " / 每题 4 个选项 1 个答案 / "名字：目录：标签："三段），
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
 * 它只认引导问题里那几个 key（rename / pick_category / pick_tags / organize）。
 * 用户自己敲的问题 `questionKey` 是空的，压根不进这里——它没有"该写进哪个字段"的语义
 * （要把它的答案写进正文是另一件事，不在采纳范围内）。
 */

/**
 * `organize` 一次产出三个字段。
 *
 * **导出它，而不是让 UI 侧各写各的字面量**：这个串原先在 `AiAdoptDialog` 的
 * dialogTitle / 模板 / canConfirm / confirm / watch 里各出现一次共 5 处，
 * 改一次漏 4 处，而漏掉的那处**不报错**，只是某个分支静默失效
 * （2026-10-10 修 organize 采纳时就是这么坏的：四层 UI 全都没写它，
 * 界面却显示「采纳到undefined」+「AI 没按要求的格式回答」）。
 */
export const ORGANIZE_FIELD = 'title|categoryId|tags'

/**
 * questionKey 的 key 段 → kp_topic 的字段名。
 * 数组形式的 `ORGANIZE_FIELD` 只给 `organize` 用：它一次产出三个字段，
 * 不走单字段的 switch（`adoptFieldOf` 只是拿它当"这条能不能被采纳"的判据）。
 *
 * ⚠️ **`pick_tags` 不在里面了**（2026-11-03 用户拍板：标签功能从页面上去掉，
 * 后台逻辑暂时不去）：「挑标签」那个引导问题不再出现在界面上，
 * 它的答案也就没有"该写进哪个字段"的语义了 ⇒ 不给采纳按钮。
 * ⚠️ **后端的提示词（`ai.prompt.question.pick_tags`）和写标签的口子都还在** ——
 * `organize` 那条仍然产出标签并照写（见 `parseOrganize`）。
 */
const ADOPT_FIELDS = {
  rename: 'title',
  pick_category: 'categoryId',
  organize: ORGANIZE_FIELD
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
 * 列表行的一行：`- xxx` / `* xxx` / `• xxx`，以及 `1. xxx`。
 *
 * remark 里要求的是"前面加 - "，但模型给标签时也时不时写成编号列表，
 * 只认 `- ` 时那几行会被静默丢掉（`valueOfLine` 取不出值，上层 filter 掉）。
 *
 * 内容用 `(.*)` 不是 `(.+)`：一个空的 `-` 行不该被当成"没匹配"，
 * 那会让这个破折号本身被当成一条值。
 */
const BULLET = /^\s*(?:[-*•·]|\d{1,2}\s*[.、)）])\s*(.*)$/

/** 代码围栏行（``` / ~~~）：是包裹不是值——模型爱把输出整个裹进代码块 */
const FENCE = /^\s*(?:`{3}|~{3})/

/**
 * 一行里的值：剥掉列表行首标记（`- xxx` → `xxx`），再剥引号和句末标点。
 *
 * **取值一律走 cleanValue**：模型很爱写 `- 「Lombok」。`，只 trim 的话引号和句号
 * 会一起存进 kp_tag，变成一个叫"「Lombok」。"的标签，以后再匹配就永远对不上。
 * 标签行、"值写在键的下一行"的补值都从这里走。
 */
function valueOfLine(line) {
  const m = String(line || '').match(BULLET)
  return cleanValue(m ? m[1] : line)
}

/**
 * `organize` 的三段键：`名字：`（`标题：` 同义）/ `目录：` / `标签：`。
 *
 * **值可以在冒号同行，也可以在冒号的下一行**。提示词模板本身就是三键各占一行
 * （见 `ai.prompt.question.organize:categories,tags`），模型照模板输出时值必然在
 * 下一行——只认同行值会把这种回答的名字和目录**整段静默丢掉**（2026-11-11 任务 23
 * 步骤 1 实证：采纳行显示「（无标题）· 待归类」，用户以为在采纳名字和目录，
 * 实际只写了标签）。键值同行反而是模型的另一种写法，两种都认。
 *
 * ⚠️ 值的捕获用 `(.*)` 不是 `(.+)`：冒号后**空着**正是"值在下一行"的信号。
 */
const NAME_KEY = /^\s*(?:名字|标题)\s*[:：]\s*(.*)$/
const CATEGORY_KEY = /^\s*目录\s*[:：]\s*(.*)$/
const TAGS_KEY = /^\s*标签\s*[:：]\s*(.*)$/

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
    case ORGANIZE_FIELD:
      return parseOrganize(turn?.answer, categories)
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
  const pathOf = pathBuilder(list)
  const text = cleanValue(rawPath)
  if (!text) return { categoryId: null, newCategory: null }

  const exact = list.find((c) => pathOf(c) === text)
  if (exact) return { categoryId: exact.id, newCategory: null }

  const parts = text.split('/').map((s) => s.trim()).filter(Boolean)
  const name = parts[parts.length - 1]
  const parentPath = parts.slice(0, -1).join('/')

  if (!parentPath) {
    // 只给了名字：按名字找（大小写不敏感是最后一层，不做模糊匹配）
    const hit =
      list.find((c) => c.name === name) ||
      list.find((c) => String(c.name).toLowerCase() === name.toLowerCase())
    return hit
      ? { categoryId: hit.id, newCategory: null }
      : { categoryId: null, newCategory: { name, parentId: null } }
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

/**
 * 新目录的判重键：**名字 + 父级**。
 *
 * ⚠️ 不能只按名字 —— 唯一键是 `(user_id, parent_id, name)`，
 * 「数据库/JDBC」和「中间件/JDBC」是**两个不同的目录，都要建**。
 *
 * **同步/异步两条采纳路共用这一份**（`AiOrganizeDialog#createProposed`、
 * `classifyTask#adoptClassifyResults`）：两处各写一份的话，
 * 一批里两条建议同一个路径就会建出两个同名的目录。
 */
export function newCategoryKey(nc) {
  return `${nc.parentId ?? 0}/${nc.name}`
}

/**
 * 把一批采纳行里提议的新目录**先去重再逐个建**，返回 `newCategoryKey → 新目录 id` 的表。
 *
 * 18 条一起跑，模型很容易对好几条提同一个路径（「数据库/MySQL」）。不去重的话第二次
 * `createCategory` 会撞后端的重名校验（「同一层下已有同名目录」），整批采纳就断在那里
 * ——前面的目录建了、后面的知识一条没写。**先去重是一次必要的批处理，不是兜底。**
 *
 * 建不成的（重名等）不写进表，对应行就只写别的字段。
 *
 * ⚠️ **同步采纳（AiOrganizeDialog）与异步采纳（classifyTask）共用这一份**——
 * 两处各写一份的话，判重口径早晚漂（一批里两条建议同一个路径 ⇒ 建出两个同名目录）。
 * `createCategory` 由调用方传入：本文件是纯函数，不反向依赖 store。
 *
 * @param {Array<{pick:string, newCategory:object}>} rows 采纳行（pick 为 NEW_CATEGORY 的才建）
 * @param {(name:string, parentId?:number) => Promise<{id:number}>} createCategory
 * @returns {Promise<Map<string, number>>}
 */
export async function createProposedCategories(rows, createCategory) {
  const unique = new Map()
  for (const r of rows) {
    if (r.pick !== NEW_CATEGORY || !r.newCategory) continue
    const key = newCategoryKey(r.newCategory)
    if (!unique.has(key)) unique.set(key, r.newCategory)
  }
  const byKey = new Map()
  for (const [key, nc] of unique) {
    const created = await createCategory(nc.name, nc.parentId ?? undefined)
    if (created?.id != null) byKey.set(key, created.id)
  }
  return byKey
}

/**
 * 「目录建议放不进去」的说明文案（**唯一一份**，两个采纳对话框共用）。
 *
 * 说的是**目录建议落空**，不是这条没得采：标题、标签照常，目录留待手动。
 * 说清楚是为了不静默消失——AI 明明建议了，界面上得看得见它建议了什么。
 */
export function unplacedNote(path) {
  return `AI 建议的目录「${path}」放不进当下的目录树（它的上级不存在）；标题、标签照常采纳，目录可在下拉手动选`
}

function parseCategory(answer, categories) {
  return matchCategory(cleanName(answer), categories)
}

/**
 * `organize`：一次产出标题 / 目录 / 标签（列表页「AI 归类」用）。
 *
 * **按行首的键取，不靠位置**。三样东西一次产出，模型很容易在前面加一段解释；
 * 靠"第一行是名字、第二行是目录"去猜的话，少一行后面全错位。
 *
 * **三个字段各自独立成败**：名字那行没写就只补目录，目录没匹配上就只补名字和标签。
 * 一个字段解析不出来不该把另外两个也扔掉——那正是分三段写格式换来的好处。
 *
 * 目录匹配不上时**不进 patch**，只把 AI 建议的 `{name, parentId}` 放进 `newCategory`
 * 交给界面。不能拿一个"我这里没有的目录"去写库，更不能悄悄替用户建一个新目录——
 * 建不建、挂哪里，都由用户点确认，见 matchCategory 的注释。
 * 上级路径不存在时连 `{name, parentId}` 都给不出（挂哪？）——把**原始建议文本**
 * 放进 `unplaced` 交给界面说明（`unplacedNote`），这条**不能静默消失**：
 * 2026-11-11 任务 23 步骤 1 就是活例子，AI 建议「开发工具/git」而目录树里没有
 * 「开发工具」，界面上只显示「待归类」，建议原文根本看不见。
 *
 * 三个字段一个都没取到 → 返回 null，界面上不给采纳入口。
 */
function parseOrganize(answer, categories) {
  let title = ''
  let categoryName = ''
  let inTags = false
  // 键行没带值时值在后面的行里：'title' | 'category' | ''（不用等值）
  let pending = ''
  const tagNames = []
  const seen = new Set()
  const addTag = (name) => {
    if (name && !seen.has(name)) {
      seen.add(name)
      tagNames.push(name)
    }
  }

  for (const line of String(answer || '').split('\n')) {
    if (inTags) {
      // 标签行要求带列表符：无标记的行是说明文字，宁可少收一条也不把解释存成标签
      if (BULLET.test(line)) addTag(valueOfLine(line))
      continue
    }
    // ⚠️ 键行**优先**于"补上一行的值"：`名字：` 后面紧跟 `目录：` 说明名字是空的
    const nameKey = line.match(NAME_KEY)
    if (nameKey) {
      if (nameKey[1].trim()) {
        title = cleanValue(nameKey[1])
        pending = ''
      } else {
        pending = 'title'
      }
      continue
    }
    const catKey = line.match(CATEGORY_KEY)
    if (catKey) {
      if (catKey[1].trim()) {
        categoryName = cleanValue(catKey[1])
        pending = ''
      } else {
        pending = 'category'
      }
      continue
    }
    const tagKey = line.match(TAGS_KEY)
    if (tagKey) {
      inTags = true
      pending = ''
      // 行内给的标签（`标签：git`）整段当一个标签收——和名字/目录的行内值同等待遇，
      // 不猜逗号分隔符：真给了多个就存成一个原样标签，用户一扫就能发现
      addTag(valueOfLine(tagKey[1]))
      continue
    }
    if (pending && !FENCE.test(line)) {
      // 围栏行是包裹不是值；空行/清不出值的行继续等真正的值（值只取第一行）
      const value = valueOfLine(line)
      if (value) {
        if (pending === 'title') title = value
        else categoryName = value
        pending = ''
      }
    }
  }

  const patch = {}
  if (title) patch.title = title
  if (tagNames.length) patch.tags = tagNames
  const hit = categoryName ? matchCategory(categoryName, categories) : null
  if (hit?.categoryId) patch.categoryId = hit.categoryId
  if (!Object.keys(patch).length) return null
  // 上级不存在 → 连"建什么、挂哪"都定不了（matchCategory 的"宁可不猜"）。
  // 但建议原文交给界面显示，别让人以为 AI 什么都没建议
  const unplaced = categoryName && hit && !hit.categoryId && !hit.newCategory ? categoryName : ''
  return { ...patch, newCategory: hit?.newCategory || null, unplaced }
}


