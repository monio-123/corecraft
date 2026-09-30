<template>
  <!--
    Milkdown 集成封装层（根治方案）
    - 包 <MilkdownProvider>，让 <MilkdownEditorBody> 作为 Provider child 调 useEditor
    - v-model 双向：外部 props.modelValue 进来 → 内部 markdown；内部 markdown 变化 → emit update:modelValue
    - 业务 view 只用 <MilkdownEditor v-model="..." />，不接触 Milkdown API

    模式切换：默认简洁模式（不显示工具栏），右上角按钮切到完整模式显示 B/I/H2/H3 工具栏。
    状态本地管理，不外传——业务 view 不需要知道当前是哪种模式。
  -->
  <div class="milkdown-editor-wrapper">
    <div class="milkdown-editor-mode-toggle">
      <el-tooltip
        :content="showToolbar ? '切换到简洁模式（隐藏工具栏）' : '切换到完整模式（显示 B/I/H2/H3 工具栏）'"
        placement="bottom-end"
      >
        <el-button size="small" text @click="showToolbar = !showToolbar">
          <el-icon><Tools /></el-icon>
          <span class="mode-label">{{ showToolbar ? '完整' : '简洁' }}</span>
        </el-button>
      </el-tooltip>
    </div>
    <MilkdownProvider>
      <MilkdownEditorBody v-model="markdown" :show-toolbar="showToolbar" />
    </MilkdownProvider>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { MilkdownProvider } from '@milkdown/vue'
import { Tools } from '@element-plus/icons-vue'
import MilkdownEditorBody from './MilkdownEditorBody.vue'

const props = defineProps({ modelValue: { type: String, default: '' } })
const emit = defineEmits(['update:modelValue'])

// v-model 双向：props.modelValue 进来同步到内部 markdown，内部 markdown 变化 emit 出去
const markdown = ref(props.modelValue)
watch(() => props.modelValue, (v) => { markdown.value = v })
watch(markdown, (v) => emit('update:modelValue', v))

// 默认简洁模式：隐藏工具栏，只露 ProseMirror 编辑区
const showToolbar = ref(false)
</script>

<style scoped>
.milkdown-editor-wrapper {
  position: relative;
}
/*
  toggle 按钮用 absolute 定位在 wrapper 右上角：
  - 完整模式（.md-toolbar 存在）：top: 4px = 工具栏 padding-top:4px，按钮视觉基线跟 B/I/H2 等工具栏按钮一致（不抢工具栏下方空间）
  - 简洁模式（无 .md-toolbar）：按钮浮在 ProseMirror 顶部 padding 区（ProseMirror padding-top:8px），不重叠编辑器内容
*/
.milkdown-editor-mode-toggle {
  position: absolute;
  /* top: 4px = .md-toolbar padding-top:4px，让 toggle 按钮视觉基线跟 B/I/H2 等工具栏按钮一致 */
  top: 4px;
  right: 4px;
  z-index: 10;
  opacity: 0.55;
  transition: opacity 0.15s;
}
.milkdown-editor-mode-toggle:hover {
  opacity: 1;
}
.mode-label {
  margin-left: 2px;
  font-size: 12px;
}
</style>