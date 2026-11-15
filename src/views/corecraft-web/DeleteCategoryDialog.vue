<template>
  <!--
    「删目录」确认框。

    ⚠️ 全站确认框统一口径见 `utils/confirm.js`：标题 = 动作名、
    一句「你确认要 X 么？」、按钮「取消 / 确认」、无图标、无后果说明。
    这里唯一多出来的是**真实选项**「同时删除 N 条知识点」—— 它是功能，
    不是文案，所以自己画而没走 `confirmAction`（统一出口塞不进复选框）。
    除此之外弹窗里不放任何东西。
  -->
  <el-dialog
    :model-value="state.visible"
    title="删除目录"
    width="var(--cc-dialog-w-sm)"
    :close-on-click-modal="false"
    :close-on-press-escape="!state.saving"
    @update:model-value="onClose"
  >
    <p class="dc__lead">你确认要删除目录么？</p>

    <!-- 唯一的额外选项：**默认不勾**（勾上 = 连带删知识点，不可逆）。 -->
    <label v-if="topicCount > 0" class="dc__opt">
      <input v-model="cascade" type="checkbox" />
      <span>同时删除这 {{ topicCount }} 条知识点</span>
    </label>

    <template #footer>
      <el-button :disabled="state.saving" @click="cancel">取消</el-button>
      <el-button type="primary" :loading="state.saving" @click="submit">确认</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
/**
 * 「删目录」确认框（全站**一个**实例）。
 *
 * <p>挂在 `AppLayout` 上（壳里），不是挂在目录行里 —— 那个「⋯」菜单在树上出现
 * 三次（书名那行、章行、节行），每个挂一个弹窗就是三个实例。
 *
 * <p>状态和出口都在 store（`deleteCategoryState` / `openDeleteCategoryDialog`）：
 * 发起方（`confirmDeleteCategory`）拿一个 Promise 收答复。
 */
import { ref, computed, watch } from 'vue'
import {
  deleteCategoryState, cancelDeleteCategory, submitDeleteCategory
} from './useCorecraftWebStore'

const state = deleteCategoryState

/** 「同时删除 N 条知识点」—— **默认不勾** */
const cascade = ref(false)

const topicCount = computed(() => state.preview?.topicCount || 0)

// 每次打开都重置勾选：上一次勾了「连带删」，这一次不该继承
// ——那是上一次那个目录的意图。
watch(
  () => state.visible,
  (open) => {
    if (open) cascade.value = false
  }
)

function onClose(v) {
  if (!v) cancelDeleteCategory()
}

function cancel() {
  cancelDeleteCategory()
}

function submit() {
  submitDeleteCategory(cascade.value)
}
</script>

<style scoped>
.dc__lead {
  margin: 0 0 var(--sp-8);
  color: var(--cc-text-1);
  font-size: var(--text-sm);
  line-height: 1.6;
}
.dc__opt {
  display: flex;
  align-items: flex-start;
  gap: var(--sp-8);
  padding: var(--sp-9) var(--sp-10);
  border: 1px solid var(--cc-border);
  border-radius: var(--radius-md);
  font-size: var(--text-sm);
  color: var(--cc-text-1);
  line-height: 1.6;
  cursor: pointer;
}
.dc__opt input {
  margin-top: 3px;
  flex: 0 0 auto;
}
</style>
