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

    <!-- 目录。树形下拉 + 顶上那个「+ 新建 数据库 / MySQL」——
         做成一个选项而不是额外一个按钮：选它 = 建，不选它 = 改选已有的或不动。
         闸门在用户手上，模型炸不出目录。层级由树的缩进表达，label 不重复路径。 -->
    <template v-else-if="field === 'categoryId'">
      <p class="adopt__now">现在：{{ currentCategoryName }}</p>
      <CategoryTreeSelect
        v-model="draftCategoryId"
        :categories="categories"
        :new-category="parsed?.newCategory || null"
        placeholder="选择目录"
      />
    </template>

    <!-- 标签 和 organize 合成一处分支。**这两块的标签编辑逐字相同**，
         organize 只是多出「标题」「目录」两项控件，标签那块一个字都不用变。
         ⚠️ 写进去的是全部标签，不是只有 AI 推荐的：updateTopic 是
         deleteByTopicId 后重插（替换不是合并），只传新增的那几个会把用户原有的标签抹掉 -->
    <template v-else-if="field === 'tags' || field === ORGANIZE_FIELD">
      <template v-if="field === ORGANIZE_FIELD">
        <div class="adopt__field">
          <span class="adopt__label">标题</span>
          <el-input v-model="draftTitle" placeholder="给它起个名字" />
        </div>

        <div class="adopt__field">
          <span class="adopt__label">目录</span>
          <p class="adopt__now">现在：{{ currentCategoryName }}</p>
          <CategoryTreeSelect
            v-model="draftCategoryId"
            :categories="categories"
            :new-category="parsed?.newCategory || null"
            placeholder="选择目录"
          />
        </div>
      </template>

      <div class="adopt__field">
        <span v-if="field === ORGANIZE_FIELD" class="adopt__label">标签</span>
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
import { ElMessage } from 'element-plus'
import { Close } from '@element-plus/icons-vue'
import { adoptFieldOf, parseAdopt, NEW_CATEGORY, ORGANIZE_FIELD } from './aiAdopt'
import CategoryTreeSelect from './CategoryTreeSelect.vue'
import { createCategory } from './useCorecraftWebStore'

const props = defineProps({
  modelValue: Boolean,
  turn: { type: Object, default: null },
  categories: { type: Array, default: () => [] },
  topic: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'confirm'])

const FIELD_LABEL = {
  title: '标题',
  categoryId: '目录',
  tags: '标签'
}

const field = computed(() => adoptFieldOf(props.turn?.questionKey))
const parsed = computed(() => (props.turn ? parseAdopt(props.turn, props.categories) : null))

/**
 * `organize` 的 field 是 `'title|categoryId|tags'`——**一次三个字段的串，不是单个键名**。
 * 直接拿它查 FIELD_LABEL 必然 miss，标题就会渲染成「采纳到undefined」。
 */
const dialogTitle = computed(() => {
  if (!field.value) return '无法采纳'
  const names = field.value.split('|').map((k) => FIELD_LABEL[k] || k)
  return `采纳到${names.join(' / ')}`
})

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
    // 目录：AI 匹配到就预选它，匹配不到但提议了新建就预选「新建」，
    // **AI 压根没提目录就保持原样**。原来写 `p ? p.categoryId : …`，p 存在但
    // 没 categoryId 时会得到 undefined，界面上表现为"目录被清空了"
    draftCategoryId.value = p
      ? (p.categoryId ?? (p.newCategory ? NEW_CATEGORY : props.topic?.categoryId ?? null))
      : props.topic?.categoryId ?? null
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
    // organize 一次三个字段，**各自独立**：有一项有值就能确认。
    // 目录没选**不拦**——patch 里没有 categoryId 就是"不动目录"，
    // 跟"选了空目录等于把这条知识移回未归类"完全是两回事
    case ORGANIZE_FIELD:
      return draftTitle.value.trim().length > 0
        || draftTags.value.length > 0
        || draftCategoryId.value != null
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
 * 目录可能要新建：选了「+ 新建「X」」就真的建，返回它的 id。
 *
 * **返回 null = 建了但没查到（列表没同步），调用方必须就此打住**：
 * categoryId 传 undefined 会被 updateTopic 回填成 null，等于"把这条知识移回未归类"
 * ——一个用户根本没要求的改动。返回 undefined 则只是"这次不用建"。
 */
async function createNewCategoryId(nc) {
  if (!nc) return undefined
  // createCategory 直接返回新建的那个（回查在 store 里做了）
  const created = await createCategory(nc.name, nc.parentId ?? undefined)
  if (created?.id == null) {
    ElMessage.error('目录建了但没查到，这条知识先不归类')
    return null
  }
  return created.id
}

/**
 * 每个分支各自产出一个对象，不要写成 `{title: ..., categoryId: ...}[field]`——
 * 那样 title 分支取出来的是**字符串**，后面 `patch.title` 就是 undefined，
 * JSON.stringify 会把整个键丢掉，后端收到 null，于是"什么也没改"，
 * 而接口照样 200、"已采纳"照弹。字符串是 truthy，`if (!patch)` 那道防线拦不住。
 *
 * 目录分支**可能先建目录**：选了「+ 新建「X」」就 createCategory 拿 id。
 * 一次点击 = 一次确认，模型自己炸不出目录。重名会由后端抛，走 axios 拦截器统一提示。
 */
async function confirm() {
  const p = parsed.value
  let patch = null
  if (field.value === 'title') {
    patch = { title: draftTitle.value.trim() }
  } else if (field.value === 'categoryId') {
    if (draftCategoryId.value === NEW_CATEGORY) {
      const id = await createNewCategoryId(p?.newCategory)
      if (id === null) return
      patch = { categoryId: id }
    } else {
      patch = { categoryId: draftCategoryId.value }
    }
  } else if (field.value === ORGANIZE_FIELD) {
    // 三段各自独立：**没有的字段不进 patch，updateTopic 就不会动它**。
    // 用户只想要目录、把标题和标签改回原样，确认后也只动目录
    patch = {}
    if (draftTitle.value.trim()) patch.title = draftTitle.value.trim()
    if (draftTags.value.length) patch.tags = [...draftTags.value]
    if (draftCategoryId.value === NEW_CATEGORY) {
      const id = await createNewCategoryId(p?.newCategory)
      if (id === null) return
      patch.categoryId = id
    } else if (draftCategoryId.value != null) {
      patch.categoryId = draftCategoryId.value
    }
  } else if (field.value === 'tags') {
    patch = { tags: [...draftTags.value] }
  }
  // organize 的 patch 起步是 {}，三个字段全被清空时它仍然是空的——那就不该发生任何写入
  if (!patch || !Object.keys(patch).length) return
  // 只发出去，成功提示由写入方在 await 之后再弹。失败提示走 axios 拦截器（它已经做了去重），
  // 在这里先弹"已采纳"会变成先报喜再报错。
  emit('confirm', patch)
  emit('update:modelValue', false)
}
</script>

<style scoped>
.adopt__now {
  margin-bottom: var(--sp-8);
  color: var(--cc-text-3);
  font-size: var(--text-xs);
}
.adopt__warn {
  margin-bottom: var(--sp-8);
  color: var(--el-color-warning);
  font-size: var(--text-xs);
  line-height: 1.6;
}
/* organize 那一框：三组控件竖着排，组间要能一眼分开。标题 / 目录 / 标签
   三段是独立可选的，所以每组自带一个小标签，不共用一个表头 */
.adopt__field + .adopt__field {
  margin-top: var(--sp-10);
}
.adopt__label {
  display: block;
  margin-bottom: var(--sp-4);
  font-size: var(--text-xs);
  color: var(--cc-text-3);
}
.adopt__tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-6);
  margin-bottom: var(--sp-10);
}

.adopt__raw {
  max-height: 300px;
  overflow: auto;
  padding: var(--sp-10);
  border-radius: var(--cc-radius);
  background: var(--cc-surface-2);
  color: var(--cc-text-2);
  font-size: var(--text-xs);
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
