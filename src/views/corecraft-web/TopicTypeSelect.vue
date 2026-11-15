<template>
  <!-- 类型切换。首页录入和详情页改类型是**同一个数据动作**，两处共用这一个组件 ——
       写两遍的话，改了其中一处的形态，另一处就跟不上了。
       形态 = 下拉 chip（2026-11-08 曾为 v6 详情页加过 segmented 分段形态，
       2026-11-09 v10 用户拍板「学习/笔记使用频率非常少，收起来」⇒ 详情页也改回 chip、
       分支与样式整个删除，别再往回加）。 -->
  <el-popover
    v-model:visible="panelVisible"
    width="var(--cc-popover-w)"
    trigger="click"
    placement="bottom-start"
    :teleported="false"
  >
    <template #reference>
      <button class="tt" :aria-label="`类型：${currentLabel}`">
        <KbSvgIcon class="tt__icon" name="note" />
        <span class="tt__label">{{ currentLabel }}</span>
        <KbSvgIcon class="tt__caret" name="caret-down" :sw="2" />
      </button>
    </template>

    <!-- 面板样式用全局 .kb-pick* 一族（kb.css）：和其它「从一列里挑一个」的浮层同形，
         本地不再留一份副本。 -->
    <div class="kb-pick">
      <p v-if="!types.length" class="kb-pick__empty">没有类型</p>
      <template v-else>
        <button
          v-for="t in types"
          :key="t.id"
          class="kb-pick__item"
          :class="{ 'is-on': t.id === current }"
          @click="pick(t.id)"
        >
          {{ t.label }}
        </button>
      </template>
    </div>
  </el-popover>
</template>

<script setup>
/**
 * 知识点类型选择器（随手记 / 系统学……）。
 *
 * 排第一的那条是**默认档**——新建知识点时它就是默认值，不用额外标记。
 */
import { ref, computed, watch } from 'vue'
import KbSvgIcon from '../../components/corecraft-web/KbSvgIcon.vue'
import { getTopicTypes, resolveTopicTypeId } from './useCorecraftWebStore'

const props = defineProps({
  modelValue: { type: Number, default: null }
})
const emit = defineEmits(['update:modelValue', 'change'])

const panelVisible = ref(false)
const types = computed(() => getTopicTypes())

// 可能悬空（字典项被软删），统一收口成清单里真实存在的 id
const current = computed(() => resolveTopicTypeId(props.modelValue))
const currentLabel = computed(
  () => types.value.find((t) => t.id === current.value)?.label || '类型'
)

watch(types, (list) => {
  // 清单变了（比如刚拉回来）而当前值还悬着，向上报一次，让调用方把值落到真实 id 上
  if (list.length && props.modelValue !== current.value) {
    emit('update:modelValue', current.value)
  }
})

function pick(id) {
  panelVisible.value = false
  if (id === props.modelValue) return
  emit('update:modelValue', id)
  emit('change', id)
}
</script>

<style scoped>
/* ---------- chip：下拉 chip（首页录入区 + 详情页 meta 行） ---------- */
/* 形态对齐全局 .kb-chip —— chip 没有定高（字号 × 行高 + 内边距撑出来，实际约 23px），
   这里定高 24px，视觉上同高。但颜色重刷了一遍：
   标签是"涉及什么"（中性信息），类型是"这条是什么"（带分类意味），
   两者在一条行里出现时必须一眼能分开。 */
.tt {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-5);
  height: 26px;
  padding: 0 var(--sp-10);
  border: none;
  /* chip 族圆角例外 6px：和全局 .kb-chip 一致（按控件高度归档 24px 本该是 xs=4） */
  border-radius: var(--radius-sm);
  background: var(--cc-primary-soft);
  color: var(--cc-primary);
  font-size: var(--text-sm);
  line-height: 1.6;
  cursor: pointer;
  white-space: nowrap;
  flex-shrink: 0;
  transition: var(--cc-transition);
}
.tt:hover {
  background: var(--el-color-primary-light-8);
}
.tt__icon {
  font-size: var(--icon-sm);
}
.tt__caret {
  /* 12px 是符号级字形（小箭头），刻意不进 --icon-*(14/16/18) 的尺寸阶梯 */
  font-size: 12px;
  opacity: 0.6;
}
</style>
