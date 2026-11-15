/**
 * 任务列表的**共享状态 + 轮询**（模块级一份，不是一个组件一份）。
 *
 * ## 为什么收在这里
 *
 * 两处要同一份数据：**任务抽屉**（列表）和**侧边栏「任务」图标的角标**（进行中数量）。
 * 各自轮询就是两份请求、两个节拍，还会出现"角标上有 1 个，打开抽屉是空的"。
 * ⇒ 数据只有一份，**节流级别由调用方切**：
 *
 * - 抽屉打开：3 秒（要看进度跳动）
 * - 抽屉关闭：30 秒（只为角标，够用且几乎不耗）
 * - 页面隐藏（`document.hidden`）：跳过这一拍，不做无意义的请求
 */

import { ref, computed } from 'vue'
import { getGenTaskList } from '../../api/genTask'

/** 任务列表（`/kp/gen-task/list` 的 records） */
export const genTasks = ref([])
/** 首次加载完成没有（决定显示骨架还是空态） */
export const genTasksLoaded = ref(false)

/**
 * 拉多少条。
 *
 * <p>⚠️ **比默认的 20 多**：抽屉里的类别筛选是**客户端筛**的（见 GenTaskPanel），
 * 只给 20 条的话"筛 AI 归类"可能一条都没有（那 20 条里没有归类任务）。
 * ⇒ 一次给 50 条，筛选才有意义。
 */
const LIST_SIZE = 50

/**
 * 轮询频率（**只有这两个档**，别再散着写字面量）。
 *
 * - `POLL_FAST`：抽屉打开时用，要看得见进度在动
 * - `POLL_SLOW`：抽屉关闭时只为角标，够用且几乎不耗
 */
export const POLL_FAST = 3000
export const POLL_SLOW = 30000

/**
 * **等你处理**的任务数（待确认 awaiting + 失败 failed）—— 侧边栏角标看它
 * （2026-11-13 用户拍板，取代「进行中 + 等确认」）。
 *
 * <p>角标的语义是「需要你动手」，不是「后台在跑」：进行中的任务用户无事可做，
 * 红点跟着后台常亮只会训练人无视它；任务跑完转待确认、或失败的那一刻角标才亮，
 * 亮了就是「该你了」。失败也算：它停在原地等重试 / 删除，和待确认一样是动作点。
 */
export const attentionTaskCount = computed(
  () => genTasks.value.filter((t) => t.status === 'awaiting' || t.status === 'failed').length
)

let timer = null
let currentMs = 0

/** 上一次的列表（用来判断"这一拍到底变没变"） */
const SIGN_KEYS = [
  'id', 'type', 'title', 'status', 'stage', 'nodeKey',
  'total', 'doneCount', 'failedCount', 'error', 'finishTime'
]

/**
 * 一条任务的"签名"：只有这些字段变了，卡片才需要重画。
 *
 * <p>⚠️ **节点链也算**（`nodes` 单独比）—— 节点颜色就是进度，
 * 漏掉它会出现"进度条在动、节点还是灰的"。
 */
function sign(t) {
  let s = ''
  for (const k of SIGN_KEYS) s += (t[k] ?? '') + '\u0001'
  // 节点链**逐字段拼**，不做 JSON.stringify：50 条 × 每 3 秒一次，
  // 序列化整棵 nodes（还有 groups）是纯白花的 CPU，而卡片只画这几个值。
  for (const n of t.nodes || []) {
    s += n.key + ':' + n.status + ':' + n.done + '/' + n.total + '\u0001'
    for (const g of n.groups || []) {
      s += g.key + ':' + g.done + '/' + g.total + '/' + g.running + '/' + g.failed + '\u0001'
    }
  }
  return s
}

/**
 * 拉一次（失败保留上一屏：任务面板不该因为一次网络抖动清空）。
 *
 * <p>⚠️ **没变的任务要保留原对象**：每 3 秒整表换成一批新对象，Vue 会认为
 * "23 张卡全变了" ⇒ 全部重渲染。而卡片里还挂着 `···` 下拉 —— 重渲染会把
 * 正在展开的菜单动画打断，看起来就是"点三个点时卡卡的"。
 * ⇒ 逐条比签名，**只换真的变了的那些**，其余复用旧对象（Vue 直接跳过）。
 */

/** 在途的那次列表请求（见下面的去重说明） */
let inflight = null

export async function refreshGenTasks() {
  // ⚠️ **在途去重**：这个接口偶尔会抖到 1–5 秒，而轮询是 3 秒一拍 ⇒
  // 不挡的话会堆出 2 个以上在途请求，回来时各自写一次 `genTasks`（多次重渲染 + 竞态）。
  // 上一拍还没回来，这一拍就**复用同一个 Promise**，不另发请求。
  if (inflight) return inflight
  inflight = doRefresh()
  try {
    return await inflight
  } finally {
    inflight = null
  }
}

/**
 * **立刻重新拉一次**（不等在途的那一拍）。
 *
 * <p>⚠️ **为什么不能直接用 `refreshGenTasks()`**：它有"在途去重" ——
 * 如果上一拍还没回来，它**复用那个 Promise**，而那一拍是**新任务建之前**发出去的
 * ⇒ 回来的数据里没有新任务 ⇒ **界面不刷新，用户以为没生效**（2026-11-12 实测：
 * 「创建主题后任务列表不能马上看到，要刷新页面」）。
 *
 * <p>⇒ 这里**等在途的那一拍回来，再自己发一次新的** ⇒ 拿到的一定是当下的数据。
 *
 * <p>⚠️ **循环等**：等的过程中可能又发起了新的一拍 ⇒ 继续等，直到真的空出窗口。
 */
export async function refreshGenTasksNow() {
  // eslint-disable-next-line no-unmodified-loop-condition
  while (inflight) {
    await inflight
  }
  return refreshGenTasks()
}

/**
 * **「任务有了」的唯一出口**（2026-11-12）。
 *
 * <p>⚠️ **为什么要有这一个**：任务有好几个创建入口
 * （生成主题 / 写知识点 / AI 归类 / 出卷…），以前各写各的刷新 ——
 * 有的调了 `refreshGenTasks()`、有的没调、有的调的是会被在途去重吞掉的那一版。
 * ⇒ **症状是"有的入口刷新后能看到、有的入口要刷新页面才看到"**，
 * 而且每加一个入口就得记得补一次刷新（漏了就复现这个 bug）。
 *
 * <p>⇒ **收敛成一个出口**：以后新增任务入口，只要调这一个函数。
 *
 * <p>⚠️ **不要在这里打开任务抽屉**：用户可能正在别处工作，
 * 自动弹窗会打断他。列表数据更新了，他什么时候打开抽屉都能看到。
 *
 * @param {object} [opts] { openPanel: true } ⇒ 顺便打开任务抽屉（少数场景要）
 */
export async function genTaskCreated(opts = {}) {
  await refreshGenTasksNow()
  if (opts.openPanel && typeof opts.onOpenPanel === 'function') {
    opts.onOpenPanel()
  }
}

async function doRefresh() {
  try {
    const res = await getGenTaskList({ page: 1, size: LIST_SIZE })
    const incoming = res?.data?.records || []
    const prev = genTasks.value
    const byId = new Map(prev.map((t) => [t.id, t]))
    let changed = prev.length !== incoming.length
    const next = incoming.map((t, i) => {
      const old = byId.get(t.id)
      if (old && sign(old) === sign(t)) {
        // 位置没动 ⇒ 连数组都复用（`changed` 保持原值）
        if (prev[i] !== old) changed = true
        return old
      }
      changed = true
      return t
    })
    if (changed) genTasks.value = next
  } catch {
    // 保持上一屏
  } finally {
    genTasksLoaded.value = true
  }
}

/**
 * 切到某个轮询频率（**同一个频率重复调用是幂等的**，不会叠出多个定时器）。
 *
 * @param {number} ms 间隔毫秒
 * @param {{immediate?: boolean}} [opts] 切档时要不要**立刻**拉一次。
 *        抽屉打开/关闭都不该立刻拉：那一刻正在跑进场/退场动画 + 首帧渲染，
 *        再叠一个「50 条列表」的请求与解析，动画就掉帧（用户报「打开再关闭再打开卡」）。
 *        缓存先渲染、下一拍（3s）再对账；只有**第一次打开（没有缓存）**才需要立刻拉。
 */
export function startGenTaskPolling(ms = POLL_SLOW, { immediate = true } = {}) {
  if (timer && currentMs === ms) return
  stopGenTaskPolling()
  currentMs = ms
  if (immediate) refreshGenTasks()
  timer = setInterval(() => {
    // 页面不可见时不拉：后台标签页的轮询纯浪费（回来的第一拍会补上）
    if (document.hidden) return
    refreshGenTasks()
  }, ms)
}

export function stopGenTaskPolling() {
  if (timer) clearInterval(timer)
  timer = null
  currentMs = 0
}
