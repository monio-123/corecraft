/**
 * 加载反馈的**时序**（2026-11-13 建）。
 *
 * ## 为什么需要这一层
 * 统一加载最容易做坏的一步是"只要在加载就显示图形"：
 * 命中缓存、秒回的请求也会闪一下 —— 用户看到的是"点一下闪一下"，
 * 反而比不显示更糟。
 *
 * ## 口径：160ms 延迟露出
 *   请求开始 → 旧内容**先留着**
 *   160ms 内完成 → **直接换新内容**（没有等待，也就没有闪烁）
 *   超过 160ms → 才换成骨架 / 紧凑指示
 *
 * ⚠️ 这个延迟只作用于"是否露出加载图形"，
 *    绝不作用于"内容什么时候出现" —— 内容该到就到，不能因为慢了一点点而延后。
 *
 * ## 缓存命中不进入加载态
 * 缓存命中由各业务层自己判定（复用已有数据、不发请求），**不进这里**。
 * 本模块只负责"已经在飞的那个请求，要不要露出图形"。
 */
import { ref, watch, getCurrentScope, onScopeDispose } from 'vue'

/** 露出加载图形前的等待时长（ms）。别各处写死不同值（模块内自用，不导出）。 */
const LOAD_DELAY = 160

/**
 * 把"是否在加载"变成"是否已经该露出加载图形"。
 *
 * @param {import('vue').Ref<boolean>|(() => boolean)} source 真实加载状态
 * @param {{ delay?: number }} [opts]
 * @returns {{ delayed: import('vue').Ref<boolean> }}
 *   delayed = 已经过了 delay ⇒ 该露出骨架/指示了
 */
export function useDelayedLoading(source, opts = {}) {
  const delay = opts.delay ?? LOAD_DELAY
  const delayed = ref(false)
  let timer = null

  const read = () => (typeof source === 'function' ? source() : source.value)

  const clear = () => {
    if (timer) {
      clearTimeout(timer)
      timer = null
    }
  }

  watch(
    () => read(),
    (on) => {
      clear()
      if (!on) {
        delayed.value = false
        return
      }
      timer = setTimeout(() => {
        timer = null
        delayed.value = true
      }, delay)
    },
    { immediate: true }
  )

  // 组件卸载时把定时器清掉 —— 否则组件没了、回调还在跑（会写一个已卸载组件的 ref）。
  if (getCurrentScope()) onScopeDispose(clear)

  return { delayed }
}
