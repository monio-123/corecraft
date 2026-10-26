<template>
  <!--
    Milkdown 集成封装层（根治方案）
    - 包 <MilkdownProvider>，让 <MilkdownEditorBody> 作为 Provider child 调 useEditor
    - v-model 双向：外部 props.modelValue 进来 → 内部 markdown；内部 markdown 变化 → emit update:modelValue
    - 业务 view 只用 <MilkdownEditor v-model="..." />，不接触 Milkdown API
    - Mod-Enter 提交：Body 里注册 keymap，事件原样往上抛（@submit），由使用方接保存。
      随手记的"Ctrl+Enter 保存"就接在这里——编辑器是 contenteditable，
      挂在外层 div 上的 keydown 收不到这个键。

    #before-content 插槽落在工具栏之下、正文之上。知识点详情页把标题塞进去，
    于是 DOM 顺序是「工具栏 → 标题 → 正文」，和语雀那类文档编辑器一致
    （用插槽而不是让使用方把工具栏搬到标题上面：搬出去就脱离 Provider 了）。

    **「简洁 / 完整」的开关不在这里，由使用方渲染**（v-model:show-toolbar）。
    理由是它是**整个编辑器**的模式开关，不是格式动作——工具栏那一排是"格式动作区"，
    把模式开关混进去是降一级。放在使用方自己的顶栏里还有两个好处：
    ① 简洁模式下工具栏整行不渲染，标题直接顶到框顶，不像原来那样空一行；
    ② 两种模式开关位置不变，来回切它不会跳。
    插槽做不到这件事：插槽内容渲染在声明插槽的地方，而顶栏在编辑器外面。
  -->
  <div class="milkdown-editor-wrapper">
    <MilkdownProvider>
      <MilkdownEditorBody
        ref="bodyRef"
        v-model="markdown"
        :show-toolbar="showToolbar"
        :category-tree="categoryTree"
        @submit="emit('submit')"
      @ready="emit('ready')"
      @outline="emit('outline', $event)"
      >
        <template #before-content>
          <slot name="before-content" />
        </template>
      </MilkdownEditorBody>
    </MilkdownProvider>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { MilkdownProvider } from '@milkdown/vue'
import MilkdownEditorBody from './MilkdownEditorBody.vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
  // 工具栏显不显示。**由使用方持有**（v-model:show-toolbar），
  // 因为开关按钮画在编辑器外面的顶栏上，状态得和使用方那份是同一份。
  showToolbar: { type: Boolean, default: false },
  // 目录树，「设为目录链接」的选择器要。**纯转发**——正文编辑器不碰目录数据，
  // 取数在建树的业务层（见 MilkdownEditorBody 里 categoryTree 那段注释）。
  categoryTree: { type: Array, default: () => [] }
})
// 'ready'：编辑器视图建好、正文已渲染完。**纯转发**，判断在 MilkdownEditorBody 的
// `useEditor().loading` 上；外层（详情页的滚动对齐）靠它知道"现在量得到最终高度了"。
// 'outline'：正文里的标题结构（右侧大纲栏用）。**纯转发**。
//
// ⚠️ **没有 'update:showToolbar'**：工具栏开关画在**父层**（那一排按钮不在编辑器里），
// 状态由父层持有，编辑器只读。以前声明了却从不 emit，三个使用方却写的是
// `v-model:show-toolbar` —— 看着是双向绑定，实际是单向，哪天在编辑器内部改它
// 父层那份不会动、且不报错。
const emit = defineEmits(['update:modelValue', 'submit', 'ready', 'outline'])

/** Body 的实例，暴露出来的方法靠它转发给外层（大纲点击跳转要滚到编辑器里的某个标题） */
const bodyRef = ref(null)

defineExpose({
  /** 滚到指定标题（按 id 定位），返回是否找得到 */
  scrollToHeading: (id) => bodyRef.value?.scrollToHeading(id) ?? false,
  /** 整篇替换正文（AI 改写写回）。返回有没有替换成功 */
  replaceContent: (md) => bodyRef.value?.replaceContent(md) ?? false,
  /** markdown → ProseMirror doc（对比区复用编辑器的解析器，保证渲染与正文一致）。解析不了返回 null */
  docOf: (md) => bodyRef.value?.docOf(md) ?? null
})

// v-model 双向：props.modelValue 进来同步到内部 markdown，内部 markdown 变化 emit 出去
const markdown = ref(props.modelValue)
watch(() => props.modelValue, (v) => { markdown.value = v })
watch(markdown, (v) => emit('update:modelValue', v))
</script>
