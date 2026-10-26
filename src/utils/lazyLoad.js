import { ref } from 'vue'

/**
 * 「同一份数据只拉一次」的状态槽。
 *
 * 三个字段各管一件事：
 * - `state`：'loading'（在途）| 'ready'（真拿到了）| 'failed'（没拿到）
 * - `inflight`：在途请求。**并发调用共享它**（见 loadOnce）
 * - `value`：最近一次真正拿到的数据（`state === 'ready'` 时有效）
 */
export function createLoadSlot() {
  return { state: ref('loading'), inflight: null, value: undefined }
}

/**
 * 懒加载 + **并发去重**：同一份数据的并发加载只发一个请求。
 *
 * ## 为什么必须有「共享在途」
 *
 * `state` 要等请求回来才变 'ready'，而**"壳"和"首页"会在同一时刻各调一次加载**
 * —— 不共享的话，两个调用都判定"还没加载"，于是**同一份数据打两遍接口**。
 *
 * 实测（登录进 `/app`）：`/api/kp/topic/list`（109 kB）、`/api/kp/category/list`、
 * 字典类型清单**各被拉了两次**。
 *
 * ## 为什么 `state` 不是布尔
 *
 * - 失败**不置 'ready'**：把一次失败缓存成"确实没有"，症状会随机成"刷新一下又好"，
 *   而且永远重试不了。保持 'failed'，下一个调用方会重试。
 * - 'loading' 也要能区分出来：界面要能说"还在加载"，而不是"没有"——
 *   「没有目录」在加载中的那几秒是句假话（用户明明有目录）。
 *
 * ## run 的约定
 *
 * `run` 负责取数 + 把结果落进调用方的缓存，**返回这次拿到的数据**；
 * 返回 `undefined` 表示"这次没拿到"（字典里没有这一项之类），
 * 状态停在 'failed'，下次还能重试。
 *
 * @param {object} slot createLoadSlot() 出来的槽
 * @param {boolean} force 强制重来（改完库要刷缓存）—— 这时**不共享**在途的那次
 * @param {() => Promise<any>} run 取数 + 落数据
 */
export async function loadOnce(slot, force, run) {
  if (slot.state.value === 'ready' && !force) return slot.value
  if (slot.inflight && !force) return slot.inflight
  const p = (async () => {
    slot.state.value = 'loading'
    const v = await run()
    slot.value = v
    slot.state.value = v === undefined ? 'failed' : 'ready'
    return v
  })().catch((e) => {
    slot.state.value = 'failed'
    throw e
  })
  // **只有"在途的还是自己"才清**。
  // force 会在旧请求还在飞的时候起一个新的；旧请求的收尾如果无条件把 inflight 清空，
  // 新的那个就被抹掉了 —— 第三个调用于是又发一次请求，重复拉的问题又回来了。
  //
  // `.catch(() => {})` 是必须的：这条链自己没人接，失败会变成一条 unhandled rejection
  // （真正失败的那次，调用方拿的是 p 的 rejection，这里吞掉的是这条副链）。
  p.finally(() => {
    if (slot.inflight === p) slot.inflight = null
  }).catch(() => {})
  slot.inflight = p
  return p
}

/** 重置（换账号时：数据要清、状态回到"没加载"） */
export function resetSlot(slot) {
  slot.state.value = 'loading'
  slot.inflight = null
  slot.value = undefined
}
