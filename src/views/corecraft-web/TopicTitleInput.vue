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
 *
 * **存的动作归调用方**（`emit('save')`）：组件自己再调一遍 updateTopic，
 * 就是同一个动作两个出口 —— 详情页那边有唯一的保存出口（applyPatch），
 * 列表卡片那边直调 store，两边各存各的、只差在谁负责弹提示。
 */
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

const props = defineProps({
  // 已落库的标题。草稿另存一份：输入过程中 props 不会变，跟着它走会冲掉正在打的字。
  title: { type: String, default: '' }
})
const emit = defineEmits(['save', 'saved'])

const draft = ref(props.title || '')
// 刚发出去、还没存好的标题。父组件存完会把它回灌进 props.title ⇒ 对上了才算存好了。
const pending = ref('')
// 换知识点 / 列表刷新后把草稿拉回来。存完也走这里，值一致，不会跳动。
watch(() => props.title, (v) => {
  draft.value = v || ''
  // **存好的凭据**：父组件把存好的新标题回灌进来了（props.title 变了）。
  // 靠这个确认"真的存上了"——存失败时 props.title 不动，也就不会弹「已更新」。
  if (pending.value && v === pending.value) {
    pending.value = ''
    emit('saved', v)
    ElMessage.success('已更新')
  }
})

function onInput(e) {
  draft.value = e.target.value
}

function save() {
  const next = draft.value.trim()
  // 没改就不发请求：光是在末尾多敲了个空格也走一趟接口没有必要
  if (next === (props.title || '')) return
  pending.value = next
  emit('save', next)
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
