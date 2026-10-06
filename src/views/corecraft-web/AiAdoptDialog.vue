<template>
  <el-dialog
    :model-value="modelValue"
    :title="dialogTitle"
    width="460px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <!-- 标题 -->
    <template v-if="field === 'title'">
      <p class="adopt__now">现在：{{ topic?.title || '（空）' }}</p>
      <el-input v-model="draftTitle" placeholder="给它起个名字" />
    </template>

    <!-- 目录。**匹配不上也给下拉**：AI 推荐的那个名字用户目录里没有，
         但他自己能挑一个，比整条回答都不能采纳强 -->
    <template v-else-if="field === 'categoryId'">
      <p class="adopt__now">现在：{{ currentCategoryName }}</p>
      <p v-if="parsed?.unmatched" class="adopt__warn">
        AI 推荐「{{ parsed.unmatched }}」，现有目录里没有这个名字。下面自己挑一个。
      </p>
      <el-select v-model="draftCategoryId" placeholder="选择目录" style="width: 100%">
        <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
    </template>

    <!-- 标签。**写进去的是全部标签，不是只有 AI 推荐的**：
         updateTopic 是 deleteByTopicId 后重插（替换不是合并），
         只传新增的那几个会把用户原有的标签抹掉 -->
    <template v-else-if="field === 'tags'">
      <p class="adopt__hint">
        采纳后这条知识会有下面这些标签（原有的 + AI 推荐的），点 × 就是不要它。
      </p>
      <div class="adopt__tags">
        <span v-for="(t, i) in draftTags" :key="t" class="kb-chip">
          <span class="kb-chip__text">{{ t }}</span>
          <button
            type="button"
            class="kb-chip__x"
            :aria-label="`移除标签 ${t}`"
            @click="draftTags.splice(i, 1)"
          ><el-icon><Close /></el-icon></button>
        </span>
      </div>
      <el-input
        v-model="newTag"
        size="small"
        placeholder="再加一个标签"
        @keyup.enter="addTag"
      />
    </template>

    <!-- 测验题：只读。结构化编辑（改题干 / 增删选项 / 换答案）比重新问一遍还费劲，
         要改就取消、改完重新问 —— 这里只保证"写之前先看清楚" -->
    <template v-else-if="field === 'quizQuestions' && parsed">
      <p class="adopt__hint">
        采纳后写入 {{ parsed.quizQuestions.length }} 道单选题（正确项已标出）。
      </p>
      <div class="adopt__quiz">
        <p v-for="(q, i) in parsed.quizQuestions" :key="i" class="adopt__quiz-block">
          <span class="adopt__quiz-stem">{{ i + 1 }}. {{ q.question }}</span>
          <span
            v-for="(o, j) in q.options"
            :key="j"
            class="adopt__quiz-option"
            :class="{ 'is-answer': j === q.answer }"
          >{{ LETTERS[j] }}. {{ o }}</span>
        </p>
      </div>
    </template>

    <!-- 解析不出来。不是兜底：格式没对上说明 AI 没照 remark 的要求答，
         硬凑出来的值写进库是垃圾，所以这里只把原文摆出来让人复制 -->
    <template v-else>
      <p class="adopt__warn">
        这段回答没能解析成可采纳的值 —— AI 这次没按要求的格式回答。下面是原文，可以复制走。
      </p>
      <pre class="adopt__raw">{{ turn?.answer }}</pre>
    </template>

    <template #footer>
      <el-button size="small" @click="emit('update:modelValue', false)">取消</el-button>
      <el-button
        size="small"
        type="primary"
        :disabled="!canConfirm"
        @click="confirm"
      >
        确认采纳
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
/**
 * 采纳预览。**先看清楚要写什么，确认了才写库。**
 *
 * 为什么不让 AI 直接改：正文 / 目录 / 标签都是用户自己攒的东西，被模型一句话改掉
 * 又改不回来是最难接受的一种失控。这个框的作用就是把"它建议改成什么"摊开给用户看，
 * 顺带允许改（标签能去掉、目录能换、标题能重打），最后一步的确认权在人。
 */
import { ref, computed, watch } from 'vue'
import { Close } from '@element-plus/icons-vue'
import { adoptFieldOf, parseAdopt } from './aiAdopt'

const props = defineProps({
  modelValue: Boolean,
  turn: { type: Object, default: null },
  categories: { type: Array, default: () => [] },
  topic: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'confirm'])

const LETTERS = 'ABCDEFGH'

const FIELD_LABEL = {
  title: '标题',
  categoryId: '目录',
  tags: '标签',
  quizQuestions: '测验题'
}

const field = computed(() => adoptFieldOf(props.turn?.questionKey))
const parsed = computed(() => (props.turn ? parseAdopt(props.turn, props.categories) : null))
const dialogTitle = computed(() => (field.value ? `采纳到${FIELD_LABEL[field.value]}` : '无法采纳'))

const currentCategoryName = computed(
  () => props.categories.find((c) => c.id === props.topic?.categoryId)?.name || '未归类'
)

const draftTitle = ref('')
const draftCategoryId = ref(null)
const draftTags = ref([])
const newTag = ref('')

// 每次打开都重新解析：对话还在增长，同一轮的答案也会变，用打开那一刻的为准
watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    const p = parsed.value
    draftTitle.value = p?.title ?? props.topic?.title ?? ''
    draftCategoryId.value = p ? p.categoryId : props.topic?.categoryId ?? null
    draftTags.value = [...new Set([...(props.topic?.tags || []).map((t) => t.name), ...(p?.tags || [])])]
    newTag.value = ''
  }
)

/**
 * 目录必须真的选了才能确认。匹配不上时预选是空的，这时点确认等于"移回未归类"——
 * 用户明明只是来采纳一条推荐，却把自己的归类悄悄抹掉了。
 * 移回未归类在列表页拖拽那条路走，不该从这个框里顺手发生。
 */
const canConfirm = computed(() => {
  const p = parsed.value
  if (!p) return false
  switch (field.value) {
    case 'title':
      return draftTitle.value.trim().length > 0
    case 'categoryId':
      return draftCategoryId.value != null
    case 'tags':
      return draftTags.value.length > 0
    case 'quizQuestions':
      return true
    default:
      return false
  }
})

function addTag() {
  const name = newTag.value.trim()
  if (name && !draftTags.value.includes(name)) draftTags.value.push(name)
  newTag.value = ''
}

/**
 * 每个分支各自产出一个对象，不要写成 `{title: ..., categoryId: ...}[field]`——
 * 那样 title 分支取出来的是**字符串**，后面 `patch.title` 就是 undefined，
 * JSON.stringify 会把整个键丢掉，后端收到 null，于是"什么也没改"，
 * 而接口照样 200、"已采纳"照弹。字符串是 truthy，`if (!patch)` 那道防线拦不住。 */
function confirm() {
  const p = parsed.value
  let patch = null
  if (field.value === 'title') patch = { title: draftTitle.value.trim() }
  else if (field.value === 'categoryId') patch = { categoryId: draftCategoryId.value }
  else if (field.value === 'tags') patch = { tags: [...draftTags.value] }
  else if (field.value === 'quizQuestions') patch = { quizQuestions: p.quizQuestions }
  if (!patch) return
  // 只发出去，成功提示由写入方在 await 之后再弹。失败提示走 axios 拦截器（它已经做了去重），
  // 在这里先弹"已采纳"会变成先报喜再报错。
  emit('confirm', patch)
  emit('update:modelValue', false)
}
</script>

<style scoped>
.adopt__now,
.adopt__hint {
  margin-bottom: calc(8px * var(--ui-scale));
  color: var(--cc-text-3);
  font-size: calc(12.5px * var(--ui-scale));
}
.adopt__warn {
  margin-bottom: calc(8px * var(--ui-scale));
  color: var(--el-color-warning);
  font-size: calc(12.5px * var(--ui-scale));
  line-height: 1.6;
}
.adopt__tags {
  display: flex;
  flex-wrap: wrap;
  gap: calc(6px * var(--ui-scale));
  margin-bottom: calc(10px * var(--ui-scale));
}
.adopt__quiz {
  max-height: calc(300px * var(--ui-scale));
  overflow-y: auto;
}
.adopt__quiz-block {
  margin-bottom: calc(10px * var(--ui-scale));
  font-size: calc(12.5px * var(--ui-scale));
  line-height: 1.7;
}
.adopt__quiz-stem {
  display: block;
  color: var(--cc-text-1);
}
.adopt__quiz-option {
  display: block;
  padding-left: calc(14px * var(--ui-scale));
  color: var(--cc-text-3);
}
.adopt__quiz-option.is-answer {
  color: var(--cc-primary);
  font-weight: 600;
}
.adopt__raw {
  max-height: calc(300px * var(--ui-scale));
  overflow: auto;
  padding: calc(10px * var(--ui-scale));
  border-radius: var(--cc-radius);
  background: var(--cc-surface-2);
  color: var(--cc-text-2);
  font-size: calc(12px * var(--ui-scale));
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
