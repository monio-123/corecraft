/**
 * **AI 归类的异步链路**（后台跑 + 采纳时再校验）。
 *
 * ## 为什么要有这条链路
 *
 * 同步的那条（`AiOrganizeDialog`）在浏览器里逐条问 AI：一条一个往返，
 * 18 条就是 18 次串行请求，**关掉页面就断了**，而且**没有进度**。
 * 待归类的量恰恰是最大的（一次整理几十条很常见）⇒ 这条路撑不住。
 *
 * ⇒ 改成后台任务：提交上去，服务端一条一条跑，
 * 结果存 `kp_gen_step.result`，人在**任务中心**看进度、看结果、挑着采纳。
 *
 * ## 采纳时**重新解析**（本模块最要紧的一条）
 *
 * AI 给的是**目录路径**（"数据库/MySQL"），不是 id。而任务跑完到人点采纳之间，
 * 目录可能变了：被删了、被改名了、新建了同名的。
 * ⇒ **解析必须发生在采纳那一刻**，用**当下的目录清单**。
 * 这也是为什么后端存的是**原始回答**而不是解析结果 ——
 * 存了解析结果就等于把"跑那一刻的目录世界"冻住了。
 *
 * ## 材料与目录清单都**不进材料**
 *
 * 这两样都由**后端现查**（目录清单见 `AiChatServiceImpl#appendContext`，
 * 正文见 `KpGenTaskServiceImpl#classifyMaterial`）：
 * - 目录清单：前端传的那份是快照，跑的过程中目录可能已被删/改；
 * - 正文：前端在"待归类"那一屏拿到的是**结构索引**，而结构索引**刻意不含正文**
 *   （列表页读全文实测 4.4 秒，索引 78 毫秒）⇒ 前端**根本没有正文**可传。
 *
 * ## 三个模块的分工
 *
 * - 本模块：提交任务、解析结果、组装采纳请求
 * - `aiAdopt.js`：**解析规则**（唯一一份，`parseAdopt`）
 * - 后端 `KpClassifyServiceImpl`：**写库 + 校验归属**（目录是不是你的、步骤是不是你的）
 */

import { getGenTaskDetail, adoptClassify, createGenTask } from '../../api/genTask'
import { parseAdopt, categoryPathOf, newCategoryPath, NEW_CATEGORY, newCategoryKey, createProposedCategories, unplacedNote } from './aiAdopt'
import { createCategory, getCategories } from './useCorecraftWebStore'
import { resolveEndpoint, endpointProblem, ensureAiConfigLoaded } from './useAi'

// `getGenTaskDetail` 在这里**转一手**再导出：采纳对话框要拉任务详情，
// 而"归类任务详情长什么样"是**这个模块的事**（解析规则在这儿）⇒
// 对话框不直接依赖 api 层。
export { getGenTaskDetail }

/**
 * 归类用的引导问题 key（短名 + 附料）。
 *
 * <p>⚠️ **短名要和后端 sys_config 里那条一致**（`ai.prompt.question.organize:categories,tags`）。
 * 后端执行器按**它自己那一份**取提示词（不在任务参数里传）——
 * 两边各写一份短名，早晚会漂，而漂了的后果是"模型收到的是另一条提示词，
 * 输出格式对不上，解析全失败"，界面上只看得出"AI 什么都没说"。
 */
const ORGANIZE_KEY = 'organize:categories,tags'

/**
 * 提交一批待归类的知识为后台任务。
 *
 * <p>⚠️ **只传 id 和标题，不传材料**：正文由后端现查（见文件头）。
 * 前端硬塞一份材料进去有两条害处：
 * ① 结构索引里**没有正文**，前端传的必然是个空壳；
 * ② 即使能传，材料是**快照**——跑的过程中正文可能已经改了。
 *
 * <p>⚠️ **端点必须传进去**（2026-11-11 实跑踩到）：
 * 参数快照里没有 `endpointId` ⇒ 后端 `requireReady(null)` 抛「还没选好 API」
 * ⇒ **每一步都撞同一堵墙**，100 条就是 100 次失败，界面上只看得见一堆红字。
 * ⇒ 端点由**本模块自己兜底解析**（`resolveEndpoint()`），
 * 调用方不用管、也没法管错 —— 判据只有一份（和 `useAi#ask` 同一个）。
 *
 * @param {Array<{id: number, title?: string}>} topics 待归类的条目（结构索引条目即可）
 * @param {object} [opts] { endpointId, thinking }
 * @returns {Promise<number>} 任务 id
 */
export async function submitClassifyTask(topics, opts = {}) {
  const list = (topics || []).filter((t) => t && t.id != null)
  if (!list.length) {
    throw new Error('没有待归类的知识')
  }
  // ⚠️ **先确保配置已加载**：判据要看 `aiEndpoints`（哪些端点配全了），
  // 它是异步拉的 ⇒ 配置没到就判会得出"一个都没配"的**假结论**。
  // `ensureAiConfigLoaded` 幂等（已加载直接返回），不产生额外请求。
  await ensureAiConfigLoaded()
  // ⚠️ **再拦一道**：没有能用的端点就别建任务了。
  // 后端也会拦（它是权威），但后端拦的代价是**先建了任务再报错**，
  // 而这里拦是**一个任务都不建** ⇒ 任务中心里不留"注定失败"的记录。
  const endpoint = resolveEndpoint(opts.endpointId)
  if (!endpoint) {
    throw new Error(endpointProblem() || '还没选好 API')
  }
  const steps = list.map((t, i) => ({
    seq: i + 1,
    // 步骤标题 = 这条知识点叫什么（空就空着，界面上会退回正文摘要）。
    // ⚠️ 它只是**给人看的标签**，材料是另一回事（材料由后端现查）。
    title: String(t.title || '').trim(),
    topicId: t.id
  }))
  const res = await createGenTask({
    type: 'classify',
    title: `AI 归类 · ${steps.length} 条`,
    promptKey: ORGANIZE_KEY,
    // 参数快照：重跑要一模一样，就靠这一份
    params: JSON.stringify({
      endpointId: endpoint.id,
      thinking: opts.thinking ?? null
    }),
    steps
  })
  return res?.data
}

/**
 * 从任务详情里把**归类结果**解析出来。
 *
 * <p>⚠️ **解析在这里现做**，而且用的是**当下的目录清单**（`getCategories()`）
 * —— 这就是"采纳时按当下目录清单重新校验"落在哪里的地方。
 *
 * @param {object} task 任务详情（`getGenTaskDetail` 的返回）
 * @returns {Array<{stepId, topicId, title, display, categoryId, newCategory, pick, tags, on, note}>}
 */
export function parseClassifyResults(task) {
  const categories = getCategories()
  const out = []
  for (const step of task?.steps || []) {
    if (step.status !== 'ok' || !step.result) continue
    let answer = ''
    try {
      answer = JSON.parse(step.result)?.answer || ''
    } catch {
      answer = ''
    }
    if (!answer) continue
    const parsed = parseAdopt({ questionKey: ORGANIZE_KEY, answer }, categories)
    if (!parsed) {
      // 解析不出来**不静默跳过**：给一行说明，让人知道"这一条 AI 没按格式回答"。
      out.push({
        stepId: step.id,
        topicId: step.topicId,
        // ⚠️ **标题**：AI 没给名字就**空着**（空 = 采纳时不改标题）。
        // 拿步骤标题顶上会出问题：步骤标题可能是「（无标题）」这种占位，
        // 写回去就是把占位当成了用户的标题。
        title: '',
        display: step.title || '（无标题）',
        categoryId: null,
        newCategory: null,
        pick: null,
        tags: [],
        on: false,
        note: '这次没按要求的格式回答，跳过'
      })
      continue
    }
    const { newCategory, unplaced, ...patch } = parsed
    out.push({
      stepId: step.id,
      topicId: step.topicId,
      // 要写入的标题（**只有 AI 给了才写**；空 = 不改）
      title: patch.title || '',
      // 界面显示的那一行（AI 建议优先，没有就用这条知识点原来的名字）
      display: patch.title || step.title || '（无标题）',
      categoryId: patch.categoryId ?? null,
      newCategory: newCategory || null,
      // pick：AI 建议归到哪。**新建目录**用 NEW_CATEGORY 哨兵
      pick: newCategory ? NEW_CATEGORY : (patch.categoryId ?? null),
      tags: patch.tags || [],
      on: true,
      // 目录建议落空（上级不存在）⇒ 说明白，但**不排除采纳**：
      // 标题、标签照常写，目录留待手动（`adoptableRows` 只看 on，不看 note）
      note: unplaced ? unplacedNote(unplaced) : ''
    })
  }
  return out
}

/**
 * 这一批里**会被写库**的行（唯一的采纳口径，按钮计数和写库都用它）。
 *
 * ⚠️ **只看 `on`，不看 `note`**：note 是说明文字（如"目录建议落空"），
 * 不是排除器。以前按钮计数用 `on && !note`、写库用 `on !== false` 两套口径，
 * note 行一多两边就对不上（按钮说 2 条、实际写 3 条）。
 */
export function adoptableRows(rows) {
  return (rows || []).filter((r) => r.on !== false && r.topicId != null)
}

/**
 * 采纳一批归类结果（写库）。
 *
 * <p>⚠️ **写库走后端的批量采纳接口**（`POST /kp/gen-task/classify/adopt`），
 * 不是前端逐条 `updateTopic`：
 * - 一次事务 ⇒ 要么都写要么都不写（逐条写的话中途失败会留下"一半写了"的库）；
 * - **归属校验在服务端做**（这个目录是不是你的、这个步骤是不是你的）——
 *   前端校验不了，那是服务端的知识；
 * - 标签的"原有 + 推荐"合集也由服务端算（前端那份可能是筛过的，不全）。
 *
 * @param {Array} rows `parseClassifyResults` 的结果（用户可能改过 pick / 勾掉了几条）
 * @returns {Promise<number>} 写了几条
 */
export async function adoptClassifyResults(rows) {
  const targets = adoptableRows(rows)
  if (!targets.length) return 0

  // **要新建目录的先建**（去重 + 逐个建收在 `aiAdopt.js` 的 `createProposedCategories`，
  // 与同步采纳路 `AiOrganizeDialog` 共用同一份判重口径）。
  const createdId = await createProposedCategories(targets, createCategory)

  const items = targets.map((r) => {
    const categoryId = r.pick === NEW_CATEGORY
      ? createdId.get(newCategoryKey(r.newCategory)) ?? null
      : (r.pick ?? null)
    return {
      stepId: r.stepId,
      categoryId,
      // 标题：空 = 不改（后端判空就不写）
      title: r.title || null,
      // 标签：**空 = 不动**（后端按"原有 + 推荐"自己算合集）
      tags: r.tags?.length ? r.tags : null
    }
  })
  const res = await adoptClassify({
    stepIds: items.map((i) => i.stepId),
    items
  })
  return res?.data ?? items.length
}

/** 一条建议的目录显示文字（给界面用） */
export function classifyPickText(row, categories = getCategories()) {
  if (row.pick === NEW_CATEGORY) return newCategoryPath(row.newCategory, categories)
  if (row.pick != null) {
    const c = categories.find((x) => x.id === row.pick)
    return c ? categoryPathOf(c, categories) : '（目录已不存在）'
  }
  return '待归类'
}
