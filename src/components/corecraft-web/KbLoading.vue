<template>
  <!-- 加载中但还没到 160ms：**继续画旧内容**（没有等待 ⇒ 就没有闪烁）。
       超过 160ms 才换成 #loading 插槽里的骨架/指示。
       请求完成后 loading=false ⇒ 立刻回到默认插槽。 -->
  <slot v-if="loading && delayed" name="loading"></slot>
  <slot v-else></slot>
</template>

<script setup>
import { toRef } from 'vue'
import { useDelayedLoading } from '../../utils/loadingState'

/**
 * 统一的加载区容器（2026-11-13）。
 *
 * ## 用法
 * ```vue
 * <KbLoading :loading="loading">
 *   <template #loading>
 *     <div class="grid">
 *       <KbSkeletonCard v-for="i in 6" :key="i" />
 *     </div>
 *   </template>
 *   <div class="grid">...真实内容...</div>
 * </KbLoading>
 * ```
 *
 * ## 它替你做掉的判断
 * ① **160ms 延迟**：请求很快时完全不闪（见 utils/loadingState.js）。
 * ② **形态选择**：形状由 #loading 插槽给，组件不管。
 * ③ **加载超过 160ms 后不画旧内容**：旧列表杵在那儿时，
 *    "没变"和"正在变"长得一模一样，用户看到的是"点了没反应，过几秒突然换了"。
 *    ⇒ 过了延迟阈值就把旧内容换成骨架（**骨架必须与真实内容同构**，
 *    否则内容到达时容器高度跳一下）。
 *
 * ## 不要用它做的事
 * ✘ 不要把"真实业务进度"塞进来（AI 逐条归类的 done/total、后台任务进度、
 *   答题进度、自动保存）—— 那些是**进度**，不是"在加载"，
 *   统一成骨架是信息倒退。它们各自保留自己的表现。
 */
const props = defineProps({
  loading: { type: Boolean, default: false }
})

// 延迟取 loadingState 的默认值（160ms）：没有调用方需要不同的延迟，
// 留一个没人传的 prop 只是给"两处各写一个 160"留门。
const { delayed } = useDelayedLoading(toRef(props, 'loading'))
</script>
