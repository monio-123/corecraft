import { computed, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'

/**
 * 沉浸模式（全屏）：把业务壳的侧边栏和顶栏收起来，只留内容。
 *
 * 状态放 URL（`?full=1`）而不是组件内的 ref，有三个理由：
 *   ① 卡片「在新标签页打开」就是带着这个参数打开的，新标签页一进来直接是全屏；
 *   ② 刷新不丢；
 *   ③ 前进后退能退出去。
 *
 * 用 `replace` 而不是 `push`：进全屏不该往历史里塞一条，
 * 否则按一次后退只是"退掉全屏"，用户会以为后退坏了。
 *
 * 三个地方共用这一个判据：AppLayout（决定收不收壳）、
 * 详情页和首页（决定自己切成什么形态）。各写一份早晚会打起来。
 */
export function useImmersive() {
  const route = useRoute()
  const router = useRouter()

  const on = computed(() => route.query.full === '1')

  function set(next) {
    const query = { ...route.query }
    if (next) query.full = '1'
    else delete query.full
    router.replace({ path: route.path, query })
  }

  // 必须包 reactive，不能直接返回普通对象。
  // Vue 模板只会解包**顶层** ref 绑定；`immersive` 本身是个普通对象，
  // 模板里的 `immersive.on` 是普通属性访问，拿到的仍是那个 ComputedRef 对象——
  // 而对象永远为真，于是 `v-if="!immersive.on"` 恒为 false（壳被藏）、
  // `:class="{ 'is-immersive': immersive.on }"` 恒为真（一进页面就是全屏）。
  // reactive 会在属性访问时解包 ref，三处调用点的 `immersive.on` 才都是布尔值。
  return reactive({
    on,
    enter: () => set(true),
    exit: () => set(false),
    toggle: () => set(!on.value)
  })
}
