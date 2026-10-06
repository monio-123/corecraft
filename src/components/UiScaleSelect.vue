<template>
  <el-popover
    v-model:visible="panelVisible"
    :width="170"
    trigger="click"
    placement="bottom-end"
    :teleported="false"
  >
    <template #reference>
      <!-- 文字 chip 不用图标：放大镜紧挨搜索框会被直接当成"搜索按钮"。
           chip 上只留百分比，不点就知道当前多大。 -->
      <button class="us-chip" :aria-label="`界面缩放，当前 ${current.hint}`">
        {{ current.hint }}
        <el-icon class="us-chip__caret"><ArrowDown /></el-icon>
      </button>
    </template>
    <div class="us-panel">
      <button
        v-for="s in UI_SCALES"
        :key="s.value"
        class="us-panel__item"
        :class="{ 'is-on': s.value === scale }"
        @click="pick(s.value)"
      >
        <span>{{ s.hint }}</span>
      </button>
    </div>
  </el-popover>
</template>

<script setup>
/**
 * 界面缩放档位选择。两个壳（知识库 AppLayout / 管理后台 Layout）共用这一个组件——
 * 交互和长相完全一样，只有存的位置不同，所以不写两遍。
 *
 * @prop shell 'app' | 'admin' —— 决定存哪个 localStorage key。两个壳的值互不影响。
 */
import { ref, computed } from 'vue'
import { ArrowDown } from '@element-plus/icons-vue'
import { UI_SCALES, getUiScale, saveUiScale } from '../utils/uiScale'

const props = defineProps({
  shell: { type: String, required: true }
})

const panelVisible = ref(false)
const scale = ref(getUiScale(props.shell))

const current = computed(
  () => UI_SCALES.find((s) => s.value === scale.value) ?? { hint: `${Math.round(scale.value * 100)}%` }
)

function pick(value) {
  panelVisible.value = false
  if (value === scale.value) return
  scale.value = value
  saveUiScale(props.shell, value)
}
</script>

<style scoped>
/* 形态照抄筛选条那排药丸（.pill），不另发明一套 */
.us-chip {
  font-size: calc(14px * var(--ui-scale));
  padding: calc(5px * var(--ui-scale)) calc(12px * var(--ui-scale));
  border: 1px solid transparent;
  border-radius: calc(14px * var(--ui-scale));
  background: var(--cc-surface-2);
  color: var(--cc-text-2);
  cursor: pointer;
  transition: var(--cc-transition);
  display: inline-flex;
  align-items: center;
  gap: calc(4px * var(--ui-scale));
  white-space: nowrap;
  flex-shrink: 0;
}
.us-chip:hover {
  color: var(--cc-primary);
}
.us-chip__caret {
  font-size: calc(10px * var(--ui-scale));
  opacity: 0.6;
}

.us-panel {
  display: flex;
  flex-direction: column;
  gap: 1px;
}
.us-panel__item {
  border: none;
  background: transparent;
  text-align: left;
  padding: calc(7px * var(--ui-scale)) calc(10px * var(--ui-scale));
  border-radius: var(--cc-radius-sm);
  font-size: calc(15px * var(--ui-scale));
  color: var(--cc-text-2);
  cursor: pointer;
  transition: var(--cc-transition);
}
.us-panel__item:hover {
  background: var(--cc-surface-2);
  color: var(--cc-text-1);
}
.us-panel__item.is-on {
  background: var(--cc-primary-soft);
  color: var(--cc-primary);
  font-weight: 500;
}
</style>