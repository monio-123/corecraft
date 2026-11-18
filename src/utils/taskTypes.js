/**
 * 任务类型注册表（**前端一份**，后端只存 `type` 字符串）。
 *
 * ## 为什么要有这张表
 *
 * 任务中心不是"写正文的进度条"，它后面还要接 **AI 归类 / 出卷 / 每日测验 / 判分**。
 * 那些类型和"写正文"的差别全在文案上：进度单位是"条"还是"题"、动作用"写"还是"出"、
 * 完成后往哪跳。把这些集中成一张表，新接一个类型只改这里 ——
 * 组件里不出现 `if (type === 'content')` 这种分叉。
 *
 * ## ⚠️ 节点链**不在这里**
 *
 * v2 之前节点（阶段）是前端写死的 `PHASES` 表 —— 于是：
 * 后端算出来的节点和前端画的节点**各有一份**，改一处漏一处，
 * 症状是「卡片上三个节点，点进去只有一个有内容」。
 *
 * ⇒ **节点链只有后端一份**（`GenTaskNodes`），随任务一起下发（`task.nodes`）。
 * 本文件只留**跟任务类型绑死**的东西：类型名、图标、进度单位、结果链接。
 */

/** 认不出来的类型用它兜底（后端加了新类型、前端还没更新时不会崩） */
export const TYPE_FALLBACK = {
  key: 'other',
  name: '任务',
  icon: 'list',
  unit: '项',
  doneVerb: '已处理',
  runningVerb: '正在处理',
}

export const TASK_TYPES = {
  /**
   * **生成主题**（v2）：生成目录 → 确认 → 生成正文，**一个任务跑完三段**。
   *
   * <p>节点链由后端下发（`task.nodes`），这里只有类型元信息。
   */
  theme: {
    key: 'theme',
    name: '生成主题',
    icon: 'sparkle',
    unit: '条',
    doneVerb: '已写入',
    runningVerb: '正在写',
  },
  /**
   * 生成目录（规划）：**分段跑**，最后停在「待确认」。
   *
   * <p>⚠️ 它不是"写完就完"的任务 —— 产物是一棵树，要人看过才建进知识库。
   * ⇒ 主操作是「确认」（回弹窗第 2 步）。
   */
  plan: {
    key: 'plan',
    name: '生成目录',
    icon: 'expand',
    unit: '步',
    doneVerb: '已完成',
    runningVerb: '正在',
  },
  /** 写正文：知识点正文逐条生成（生成主题 / 目录「写知识点」两个入口） */
  content: {
    key: 'content',
    name: '写正文',
    icon: 'list',
    unit: '条',
    doneVerb: '已写入',
    runningVerb: '正在写',
  },
  /**
   * **命名**（2026-11-16 新增）：给随手记起一个标题。
   *
   * <p>⚠️ **单节点、无人工确认**：成功即写回标题（前端不给「采纳」入口）——
   * 起个标题不是需要人把关的决定。而这恰恰是系统里唯一一处**AI 自动写回内容**的地方，
   * 所以**任务卡就是这次自动改动的唯一留痕**。
   */
  name: {
    key: 'name',
    name: '命名',
    icon: 'sparkle',
    unit: '条',
    doneVerb: '已命名',
    runningVerb: '正在命名',
  },
  /** 出卷：按范围/难度生成题目 */
  exam: {
    key: 'exam',
    name: '生成考试',
    icon: 'copy',
    unit: '题',
    doneVerb: '已出题',
    runningVerb: '正在出题',
  },
  /** 随堂测验：就当前这条知识出几道题（详情页抽屉里考） */
  quiz: {
    key: 'quiz',
    name: '随堂测验',
    icon: 'clock',
    unit: '题',
    doneVerb: '已出题',
    runningVerb: '正在出题',
  }
  // 「判分」（grade）注册项已删：判分走 /ai/chat/stream，任务链从未有过创建方
  //（与后端 GenTaskNodes 同步删除，2026-11-15）。真进任务链时按 task-protocol 三步重新登记。
}

/**
 * 取类型配置（认不出来给兜底）。
 *
 * @param {string} type 后端返回的 type
 */
export function taskType(type) {
  return TASK_TYPES[type] || { ...TYPE_FALLBACK, key: type || 'other' }
}

/**
 * 任务的**展示状态**。
 *
 * <p>⚠️ **两处都由这里定，别在组件里各判一遍**：
 * <ol>
 *   <li><b>人工节点还等着</b>（`nodes` 里有 `awaiting`）⇒ 待确认 ——
 *       哪怕后端说 succeeded。后端的 succeeded 只说明**模型那段**跑完了，
 *       「确认 / 采纳」是人的事，还没办。不这么判就会出现
 *       「右上角已完成、节点链上确认还是琥珀」这种自相矛盾的卡片。</li>
 *   <li><b>每条都跑过但有几条失败</b>（`done ≥ total && failed > 0`）⇒ 部分完成 ——
 *       比"已完成"准确，也比"失败"准确（大部分结果是可用的）。</li>
 * </ol>
 *
 * @param {{status:string, doneCount:number, total:number, failedCount:number, nodes?:Array}} t
 * @returns {{key:string, text:string, failed?:number}}
 */
export function taskStatus(t) {
  const done = t?.doneCount || 0
  const total = t?.total || 0
  const failed = t?.failedCount || 0
  const s = t?.status
  // **等人工**：既不是失败也不是完成 —— 模型跑完了，但下一步是"人"。
  if (s === 'awaiting') return { key: 'awaiting', text: '待确认' }
  if (s === 'succeeded') {
    // 人工节点还亮着琥珀 ⇒ 事情没办完（归类跑完但还没采纳、目录跑完但还没确认）
    if (hasAwaitingNode(t)) return { key: 'awaiting', text: '待确认' }
    // **部分完成 = 成了几条、也坏了几条**；一条都没成就是彻底失败
    if (total > 0 && done >= total && failed > 0 && done - failed > 0) {
      // 单位（条/题）由调用方按类型补 —— 这里只知道"有几项失败"
      return { key: 'partial', text: '部分完成', failed }
    }
    if (total > 0 && done >= total && failed > 0) return { key: 'failed', text: '失败' }
    // 后端说成功、实际没跑完（中断过）⇒ 不能显示"已完成"：
    // 那种卡片的右上角写着完成、底下却挂着一条错误，自相矛盾。
    if (total > 0 && done < total) return { key: 'failed', text: '未完成' }
    return { key: 'succeeded', text: '已完成' }
  }
  if (s === 'failed') {
    // 全部跑过一遍、只是有几条没成 ⇒ 部分完成（比"失败"更准确：大部分结果是可用的）
    // **但一条都没成就不叫"部分完成"** —— 那是彻底失败
    if (total > 0 && done >= total && failed > 0 && done - failed > 0) {
      return { key: 'partial', text: '部分完成', failed }
    }
    return { key: 'failed', text: '失败' }
  }
  if (s === 'running') return { key: 'running', text: '进行中' }
  // ⚠️ **文案是「暂停」不是「停止」**（2026-11-12 用户定）：
  // 暂停之后能恢复、能删除；叫「停止」会让用户以为"彻底不跑了"。
  if (s === 'canceling') return { key: 'canceling', text: '正在暂停' }
  if (s === 'canceled') return { key: 'canceled', text: '已暂停' }
  if (s === 'queued') return { key: 'queued', text: '排队中' }
  return { key: 'queued', text: s || '未知' }
}

/**
 * 节点链里**有没有人工节点在等人**。
 *
 * <p>判据只看 `nodes`（后端下发），不看 `task.type` —— 类型不同只是节点不同，
 * 「等人」这件事对所有类型是同一回事。
 */
export function hasAwaitingNode(t) {
  return (t?.nodes || []).some((n) => n.status === 'awaiting')
}

/**
 * "还在跑"的状态集合。**只有这一份** —— 判定「进行中」的地方有三处
 * （侧边栏角标、抽屉顶部计数、要不要拉步骤明细），各写一遍字面量数组
 * 就会出现"角标有 1 个、面板说没有"这种不一致。
 */
export const ACTIVE_STATUS = ['queued', 'running', 'canceling']

/** 是不是"还在跑"（决定要不要显示「暂停」/ 隐藏「删除」） */
export const isActive = (t) => ACTIVE_STATUS.includes(t?.status)

/**
 * **节点的展示状态**（和步骤同一套口径）。
 *
 * <p>⚠️ **认不出来给 pending**（空心灰）—— 不给"未知"色，
 * 因为未知色会被当成"进行中"或"失败"，用户会误判。
 */
export const NODE_STATUS = {
  pending: { key: 'pending', text: '待跑' },
  running: { key: 'running', text: '进行中' },
  ok: { key: 'ok', text: '完成' },
  failed: { key: 'failed', text: '失败' },
  awaiting: { key: 'awaiting', text: '待确认' },
  // **人停下来的**（琥珀、不呼吸）。和 skipped 的区别见后端 `manualStatus`：
  // 暂停是"停在这儿、之后接着走"，跳过是"再也不会走到"。
  paused: { key: 'paused', text: '已暂停' },
  skipped: { key: 'skipped', text: '已跳过' }
}

export function nodeStatus(key) {
  return NODE_STATUS[key] || NODE_STATUS.pending
}

/**
 * **状态筛选的可选项**（一栏只有一个 chip 展开这些，和 `matchesStatusFilter` 共用这一份定义）。
 *
 * <p>⚠️ **只有这一份**：筛选口径统一走 `taskStatus(t).key`（不直接比 `t.status`），
 * 否则「部分完成」（后端 `succeeded` + `failedCount > 0`）会落到「已完成」里，
 * 「人工节点等待」（后端 `succeeded` + `hasAwaitingNode`）会落不到「待确认」里。
 */
export const STATUS_FILTERS = [
  { key: '', name: '全部状态' },
  { key: 'running', name: '进行中' },
  { key: 'awaiting', name: '待确认' },
  { key: 'succeeded', name: '已完成' },
  { key: 'failed', name: '失败' },
  { key: 'canceled', name: '已暂停' }
]

/** 状态筛选谓词：按展示语义 `taskStatus(t).key` 归类 */
export function matchesStatusFilter(task, filterKey) {
  if (!filterKey) return true
  const k = taskStatus(task).key
  if (filterKey === 'awaiting') return k === 'awaiting'
  if (filterKey === 'failed') return k === 'failed' || k === 'partial'
  if (filterKey === 'running') {
    return k === 'running' || k === 'queued' || k === 'canceling'
  }
  if (filterKey === 'canceled') return k === 'canceled'
  if (filterKey === 'succeeded') return k === 'succeeded'
  return true
}

/**
 * 秒数 → 人类读法的**唯一算口**：「48 秒」「2 分 46 秒」「1 小时 3 分」。
 * 整分不带尾巴（「2 分」而不是「2 分 0 秒」）。任务卡节点行与收起态共用这一份。
 */
export function formatSeconds(sec) {
  if (sec < 60) return `${sec} 秒`
  const min = Math.floor(sec / 60)
  if (min < 60) return sec % 60 ? `${min} 分 ${sec % 60} 秒` : `${min} 分`
  return `${Math.floor(min / 60)} 小时 ${min % 60} 分`
}

/**
 * **耗时**（人类读法）：「2 分 46 秒」「48 秒」「1 小时 3 分」。
 *
 * <p>⚠️ **只用结束时间减开始时间**：不要用"现在 - 开始"——
 * 任务还没跑完时那个数是"跑了多久"，不是"花了多久"，
 * 两者混在一句里会让人以为任务已经花了那么久。
 */
export function elapsedText(task) {
  const start = task?.startTime
  const end = task?.finishTime
  if (!start) return ''
  const t0 = new Date(start).getTime()
  const t1 = end ? new Date(end).getTime() : Date.now()
  if (!Number.isFinite(t0) || !Number.isFinite(t1) || t1 < t0) return ''
  return formatSeconds(Math.round((t1 - t0) / 1000))
}
