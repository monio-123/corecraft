<template>
  <!-- 待归类屏批量工具条：未开启批量时只显「批量」；开启后整条换成批量操作条。 -->
  <div class="tf">
    <template v-if="batchMode">
      <button type="button" class="tf__chip" @click="emit('toggle-batch')">
        批量 · 已选 {{ batchCount }} 条<KbSvgIcon name="close" :sw="2.4" />
      </button>
      <button
        v-if="pageCount"
        type="button"
        class="tf__act"
        @click="emit('select-all')"
      >{{ allSelected ? '取消全选' : '全选本页' }}</button>
      <button type="button" class="tf__act" :disabled="!batchCount" @click="emit('batch-categorize')">
        归类到目录
      </button>
      <button type="button" class="tf__act tf__act--danger" :disabled="!batchCount" @click="emit('batch-delete')">
        删除
      </button>
    </template>

    <template v-else>
      <button v-if="showBatch && pageCount" type="button" class="tf__act" @click="emit('toggle-batch')">
        <KbSvgIcon name="check" :sw="1.9" /><span>批量</span>
      </button>
    </template>
  </div>
</template>

<script setup>
/**
 * 待归类屏批量工具条（原卡片视图筛选 + 考试条已净空，考试收进目录 ⋮ 菜单）。
 */
import KbSvgIcon from './KbSvgIcon.vue'

defineProps({
  /** 能不能进批量模式（只在待归类屏为 true） */
  showBatch: { type: Boolean, default: false },
  /** 是否在批量模式 */
  batchMode: { type: Boolean, default: false },
  /** 已勾选条数 */
  batchCount: { type: Number, default: 0 },
  /** 当前页有几条（0 = 无卡片不画按钮） */
  pageCount: { type: Number, default: 0 },
  /** 当前页是否全部已勾选 */
  allSelected: { type: Boolean, default: false }
})

const emit = defineEmits(['toggle-batch', 'select-all', 'batch-categorize', 'batch-delete'])
</script>

<style scoped>
.tf {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-4);
}
.tf__chip {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-2);
  height: 24px;
  padding: 0 var(--sp-3) 0 var(--sp-5);
  border: none;
  border-radius: 999px;
  background: var(--cc-primary-soft);
  color: var(--cc-primary);
  font-family: var(--font-body);
  font-size: var(--text-xs);
  cursor: pointer;
  white-space: nowrap;
}
.tf__chip:hover {
  background: var(--cc-primary-border);
  color: var(--cc-surface);
}
.tf__act {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-3);
  height: 28px;
  padding: 0 var(--sp-6);
  border: 1px solid var(--cc-border-strong);
  border-radius: 7px;
  background: var(--cc-surface);
  color: var(--cc-text-2);
  font-family: var(--font-body);
  font-size: var(--text-sm);
  cursor: pointer;
  white-space: nowrap;
  transition: color var(--t-fast, 140ms) var(--ease, ease), background var(--t-fast, 140ms) var(--ease, ease), border-color var(--t-fast, 140ms) var(--ease, ease);
}
.tf__act:hover {
  color: var(--cc-primary);
  border-color: var(--cc-primary-border);
}
.tf__act:disabled {
  color: var(--cc-text-4);
  border-color: var(--cc-border);
  cursor: default;
}
.tf__act:disabled:hover {
  color: var(--cc-text-4);
  border-color: var(--cc-border);
}
.tf__act--danger {
  color: var(--cc-danger);
}
.tf__act--danger:hover {
  color: var(--cc-danger-deep);
  background: var(--cc-danger-soft);
  border-color: var(--cc-danger);
}
</style>
