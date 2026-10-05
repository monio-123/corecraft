<template>
  <input
    class="tti"
    :value="draft"
    placeholder="请输入标题"
    maxlength="100"
    @input="onInput"
    @blur="save"
  />
</template>

<script setup>
/**
 * 知识点标题输入框。详情页和列表卡片共用这一个。
 *
 * 标题是**可选字段**——随手记存进来的知识点 title 本来就是空串
 * （AppLearnView 的 createTopic 传 title: ''），所以两处的形态都是同一个东西：
 * 一个无边框的 input，空着的时候显示灰色「请输入标题」占位，点哪都能输，失焦即存。
 * 各写一份迟早漂移（占位文案、长度上限、什么时候存，三样都得对齐）。
 *
 * 这里只管两边**真正共通**的那几样：透明无边框、铺满一行、能选中文字、占位色。
 * 字号 / 字重 / 内外边距交给调用方的 class——两边都设同名属性就会在同权重下
 * 抢样式表先后顺序，那是最难查的一类问题。
 */
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { updateTopic } from './useCorecraftWebStore'

const props = defineProps({
  topicId: { type: [Number, String], required: true },
  // 已落库的标题。草稿另存一份：输入过程中 props 不会变，跟着它走会冲掉正在打的字。
  title: { type: String, default: '' }
})
const emit = defineEmits(['draft-change', 'saved'])

const draft = ref(props.title || '')
// 换知识点 / 列表刷新后把草稿拉回来。存完也走这里，值一致，不会跳动。
watch(() => props.title, (v) => { draft.value = v || '' })

function onInput(e) {
  draft.value = e.target.value
  // 详情页的「AI 生成」「生成自测题」拿的是当前标题，而那两条路径可能还没失焦落库，
  // 所以草稿要往外送一份。事件名不叫 input 是为了不和原生 input 事件混淆——
  // 原生那个带的是 Event，这里给的是字符串。
  emit('draft-change', draft.value)
}

async function save() {
  const next = draft.value.trim()
  // 没改就不发请求：光是在末尾多敲了个空格也走一趟接口没有必要
  if (next === (props.title || '')) return
  await updateTopic(props.topicId, { title: next })
  emit('saved', next)
  ElMessage.success('标题已更新')
}
</script>

<style scoped>
.tti {
  display: block;
  width: 100%;
  /* flex item 默认 min-width:auto，input 的固有宽度（约 20 字符）会把卡片撑破 */
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  /* input 不继承 font-family（UA 样式表写死了），必须显式拿父级的 */
  font-family: inherit;
  /* 卡片整块是拖拽源，挂着 user-select: none。那是给卡片正文用的，
     落在这里会连输入框里选中文字一起禁掉——直接声明在输入框上即可覆盖继承值。 */
  user-select: text;
  -webkit-user-select: text;
  /* input 天生单行，长标题不折行（两行折行是 h3 那边的事），超出的部分省略 */
  text-overflow: ellipsis;
}
/* 空标题时显示「请输入标题」——这是一个能直接点进去写的标题位，不是"这条没名字"的告示 */
.tti::placeholder {
  color: var(--cc-text-4);
  font-weight: 400;
}
</style>
