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
        :ask-enabled="askEnabled"
        @submit="emit('submit')"
      @ready="emit('ready')"
      @outline="emit('outline', $event)"
      @ask-ai="emit('ask-ai', $event)"
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
import { editorEmits, editorProps } from './milkdownEditorContract'

const props = defineProps(editorProps)
// ⚠️ **props / emits 的契约只有一份**（`milkdownEditorContract.js`）：本组件是
// `MilkdownEditorBody.vue` 的薄封装，父递下去的必须和子认的完全一样。
// 以前两边各抄一份 ⇒ 改一个默认值只改一处就分裂（症状是"传了没反应"，不报错）。
const emit = defineEmits(editorEmits)

/** Body 的实例，暴露出来的方法靠它转发给外层（大纲点击跳转要滚到编辑器里的某个标题） */
const bodyRef = ref(null)

defineExpose({
  /** 滚到指定标题（按 id 定位），返回是否找得到 */
  scrollToHeading: (id) => bodyRef.value?.scrollToHeading(id) ?? false,
  /** 整篇替换正文（AI 改写写回）。返回有没有替换成功 */
  replaceContent: (md) => bodyRef.value?.replaceContent(md) ?? false,
  /** 换一条知识点：替换正文**并清空撤销栈**（复用编辑器时用它） */
  switchContent: (md) => bodyRef.value?.switchContent(md) ?? false,
  /** markdown → ProseMirror doc（对比区复用编辑器的解析器，保证渲染与正文一致）。解析不了返回 null */
  docOf: (md) => bodyRef.value?.docOf(md) ?? null,
  /** 一段范围里的顶层块（AI 写入前的对比区用） */
  blocksInRange: (from, to) => bodyRef.value?.blocksInRange(from, to) ?? [],
  /** 在某段块之后写入新内容（markdown）。返回有没有写进去 */
  insertAfter: (from, to, md) => bodyRef.value?.insertAfter(from, to, md) ?? false,
  /** 正文关键词检索：设词 ⇒ 高亮所有命中，返回命中处数 */
  searchInBody: (kw) => bodyRef.value?.searchInBody(kw) ?? 0,
  /** 跳到第 n 个命中（0 起）并滚过去 */
  gotoMatch: (i) => bodyRef.value?.gotoMatch(i) ?? false,
  /** 当前命中处数 */
  searchHitCount: () => bodyRef.value?.searchHitCount() ?? 0,
  /** 当前在第几个命中（1 起；0 = 还没定位） */
  searchHitIndex: () => bodyRef.value?.searchHitIndex() ?? 0,
  /**
   * 知识点自动链接：给正文里**首次出现**的其它知识点标题加链接装饰。
   *
   * <p>⚠️ **这一层必须转发**：详情页的 `editorRef` 指的是**这个组件**，
   * 不是里层的 `MilkdownEditorBody`。方法只加在 body 上 ⇒ 外层拿不到 ⇒
   * `editorRef.value.setAutoLinks is not a function`（异常被 `?.` 吞成"没反应"，
   * 界面上一条链接都没有、控制台只有一条 Vue warn）。
   */
  setAutoLinks: (entries) => bodyRef.value?.setAutoLinks(entries)
})

// v-model 双向：props.modelValue 进来同步到内部 markdown，内部 markdown 变化 emit 出去
const markdown = ref(props.modelValue)
watch(() => props.modelValue, (v) => { markdown.value = v })
watch(markdown, (v) => emit('update:modelValue', v))
</script>
