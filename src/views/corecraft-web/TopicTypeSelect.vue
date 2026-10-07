<template>
  <!-- 类型切换 chip。首页录入和详情页改类型是**同一个交互**，两处共用这一个组件 ——
       写两遍的话，改了其中一处的形态，另一处就跟不上了。 -->
  <el-popover
    v-model:visible="panelVisible"
    :width="220"
    trigger="click"
    placement="bottom-start"
    :teleported="false"
  >
    <template #reference>
      <button class="tt" :aria-label="`类型：${currentLabel}`">
        <el-icon class="tt__icon"><Memo /></el-icon>
        <span class="tt__label">{{ currentLabel }}</span>
        <el-icon class="tt__caret"><ArrowDown /></el-icon>
      </button>
    </template>

    <div class="ttp">
      <p v-if="!types.length" class="ttp__empty">字典里还没有类型</p>
      <template v-else>
        <button
          v-for="t in types"
          :key="t.id"
          class="ttp__item"
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
import { Memo, ArrowDown } from '@element-plus/icons-vue'
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
/* 形态照抄全局 .kb-chip（20px 高的小圆角块），但颜色重刷了一遍：
   标签是"涉及什么"（中性信息），类型是"这条是什么"（带分类意味），
   两者在一条行里出现时必须一眼能分开。 */
.tt {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-5);
  height: 24px;
  padding: 0 var(--sp-9);
  border: none;
  border-radius: var(--radius-xs);
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
  background: #e3efff;
}
.tt__icon {
  font-size: var(--text-sm);
}
.tt__caret {
  font-size: var(--text-2xs);
  opacity: 0.6;
}
.ttp {
  display: flex;
  flex-direction: column;
  gap: var(--sp-1);
}
.ttp__item {
  border: none;
  background: transparent;
  text-align: left;
  padding: var(--sp-7) var(--sp-10);
  border-radius: var(--cc-radius-sm);
  font-size: var(--text-base);
  color: var(--cc-text-2);
  cursor: pointer;
}
.ttp__item:hover {
  background: var(--cc-surface-2);
  color: var(--cc-text-1);
}
.ttp__item.is-on {
  background: var(--cc-primary-soft);
  color: var(--cc-primary);
  font-weight: 500;
}
.ttp__empty {
  margin: 0;
  padding: var(--sp-7) var(--sp-10);
  font-size: var(--text-sm);
  color: var(--cc-text-4);
  line-height: 1.6;
}
</style>
