<template>
  <el-dialog
    :model-value="modelValue"
    title="AI 归类"
    width="var(--cc-dialog-w-md)"
    :close-on-click-modal="false"
    @update:model-value="close"
  >
    <!-- 只有状态：进度和错误。**不解释怎么用**——勾掉不要的、选目录这些界面上
         都看得见，再写一段话是在替用户复述一遍（skill §2 的提示文案硬规矩）。 -->
    <p v-if="running" class="og__now">
      正在处理第 {{ finished + 1 }} / {{ rows.length }} 条
    </p>
    <!-- ⚠️ §4.11(o)：这个 `.og__warn` 一类两义 ——
         ① 「生成失败」的**错误字**
         ② 「会新建目录」的**高亮提示**
         共用同一个黄色 warn 类 ⇒ 用户分不清"坏了"和"告知"。
         ⇒ 拆成两个类：**黄色只留给真警告**（错误字），
         信息提示（会新建目录）走主色（--cc-primary / --cc-primary-soft）。 -->
    <p v-else-if="error" class="og__error">{{ error }}</p>

    <div class="og__list">
      <div
        v-for="r in rows"
        :key="r.id"
        class="og__row"
        :class="{ 'is-off': r.status !== 'running' && !r.patch }"
      >
        <input v-if="r.patch" v-model="r.on" type="checkbox" :aria-label="`采纳 ${r.title}`" />
        <span v-else class="og__box" aria-hidden="true" />

        <div class="og__body">
          <!-- 没标题就用正文摘要顶上（待归类的知识往往正是没起名字的那批）。
               **限两行**：摘要能到三四十行字，不截的话行高参差，一屏看不全。
               这里**不加序号**——每行开头那几个字（"合并远程分支"/"推送master分支"）
               本来就能认出是哪条，序号是纯噪声（skill §2 提示文案硬规矩）。 -->
          <p class="og__title">{{ r.patch?.title || r.fallback }}</p>

          <!-- **还在跑的这一行只显示标题。** 结果没出来就把目录、标签、选择器
               摆出来，等于告诉用户"这是结果"，而它随时会变。 -->
          <template v-if="r.status !== 'running'">
            <p class="og__meta">
              <!-- 要新建的用主色高亮：它还没建，用户一眼能看出"这行会多一个目录"。
                   ⚠️ §4.11(o)：原来这里跟**错误字**共用黄色 warn 类 ⇒ 一类两义。
                   ⇒ 信息提示走主色（--cc-primary / --cc-primary-soft），
                   黄色只留给真警告（上面那个 .og__error）。 -->
              <span v-if="r.pick === NEW_CATEGORY" class="og__new">
                {{ newCategoryPath(r.newCategory, categories) }}
              </span>
              <span v-else-if="r.pick != null">目录：{{ pathOf(r.pick) }}</span>
              <!-- ⚠️ **标签那一行撤了**（2026-11-03 用户拍板：标签从页面上去掉，
                   后台逻辑暂时不去）。**AI 提议的标签仍然会写进库**
                   （patch.tags 那条路一行没动）—— 只是界面上不露出来。 -->
            </p>
            <!-- **每行都有**（跑完的），不管 AI 是提议新建还是选了已有的——用户要能随时改选 -->
            <CategoryTreeSelect
              v-model="r.pick"
              class="og__pick"
              size="small"
              :categories="categories"
              :new-category="r.newCategory"
            />
            <p v-if="r.note" class="og__note">{{ r.note }}</p>
          </template>
        </div>
      </div>
    </div>

    <template #footer>
      <!-- 跑着的时候**只有「停止」**。摆一个 disabled 的「采纳 0 条」在那儿，
           是禁用色却占着主按钮的位置，看着像能点（skill §2：不加没用的东西）。 -->
      <el-button v-if="running" @click="close">停止</el-button>
      <template v-else>
        <el-button @click="close">取消</el-button>
        <el-button
          type="primary"
          :loading="saving"
          :disabled="!picked.length"
          @click="confirm"
        >
          采纳 {{ picked.length }} 条
        </el-button>
      </template>
    </template>
  </el-dialog>
</template>

<script setup>
/**
 * 列表页「AI 归类」：对待归类的知识**逐条**问一遍 AI 要标题/目录/标签，先摊开给用户看，
 * 勾掉不要的，确认了才写库。
 *
 * ## 目录和标签怎么分（完整的版本在 brove-skills/corecraft-web-context/SKILL.md §2.2）
 *
 * 目录 = **主归属**（这条主要在学谁），单选、有层级；标签 = **其余涉及的**，多选。
 * 判据三步：是概念吗（不是→正文）→ 是主归属吗（是→目录）→ 否则（→标签）。
 *
 * **同一条知识可以既属于 MyBatis 又涉及 MySQL**，主归属占掉目录位，`MySQL` 就落在标签里
 * ——这是单选目录的正常形态，不是退而求其次。
 * ⚠️ 两者在**数据上毫无关联**（`category_id` 和 `kp_topic_tag` 之间没有外键）。所以别写
 * 「建目录时顺手把带同名标签的知识也迁过去」这种逻辑：那是**改主归属**，主归属是用户的主观
 * 判断，批量替他决定会把一个**看得见的不一致**换成一个**看不见的错误**。
 *
 * ## 为什么是逐条而不是一次问一批
 *
 * 一次问一批就得让模型吐 JSON，而 JSON 解析失败时没有"部分可用"这个状态——
 * 整批都白花。逐条的话每条独立成败：格式没对上就只跳掉那一条，剩下照常采纳。
 * 代价是 N 次调用，所以必须有进度和停止。
 *
 * ## 目录匹配不上就把它当成「建议新建」，建不建由用户点确认
 *
 * 2026-10-07 改了政策：以前提示词写着「只能从清单里选」，模型只能答一句固定的
 * 「都不合适，建议新建一个」，而那句话落到界面上就是一句废话。现在允许它提路径，
 * 下拉第一项就是「+ 新建 数据库 / MySQL」——**选了才建，不选就留在待归类**。
 * 18 条一起跑时模型很容易对好几条提同一个路径，所以建之前先按「名字+父级」去重。
 *
 * ## 标签是「合集」不是「替换」
 *
 * `updateTopic` 对 tags 是 deleteByTopicId 后重插（替换）。批量只传 AI 推荐的那几个，
 * 会把用户**已经打好的标签抹掉**——而这是批量操作，没有人一条条看过。
 * 所以这里传「原有 + 推荐」的全集，界面上也照实显示合集，用户能看见自己原有的那几个还在。
 */
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { ask as askAi, aiQuestions, ensureAiQuestionsLoaded } from './useAi'
import { parseAdopt, categoryPathOf, newCategoryPath, NEW_CATEGORY, newCategoryKey, createProposedCategories, unplacedNote } from './aiAdopt'
import CategoryTreeSelect from './CategoryTreeSelect.vue'
import { updateTopic, createCategory, refreshAfterTopicChange, excerptOf } from './useCorecraftWebStore'

/**
 * organize 的机器标识。
 *
 * **只认 key 段、不写 contexts**：配置树上那条的短名（`organize:categories,tags`）
 * 原样透传给后端，配置里改 contexts 加一种附料时前端不用动。
 */
const ORGANIZE = 'organize'

const props = defineProps({
  modelValue: Boolean,
  topics: { type: Array, default: () => [] },
  categories: { type: Array, default: () => [] }
})
const emit = defineEmits(['update:modelValue', 'done'])

const rows = ref([])
const running = ref(false)
const saving = ref(false)
const error = ref('')
let abort = null

const picked = computed(() => rows.value.filter((r) => r.on && r.patch))
const finished = computed(() => rows.value.filter((r) => r.status !== 'running').length)

/** 目录 id → 完整路径，给「目录：xxx」那行显示 */
function pathOf(id) {
  const c = props.categories.find((x) => x.id === id)
  return c ? categoryPathOf(c, props.categories) : '（目录已不存在）'
}

watch(
  () => props.modelValue,
  (open) => {
    if (open) start()
  }
)

function close() {
  abort?.abort()
  emit('update:modelValue', false)
}

/** 问一条要它的完整回答。这里不显示逐帧过程，只在行上标"还在跑" */
function collectOne(topic, questionKey, question, signal) {
  return askAi(question, {
    topicId: topic.id,
    questionKey,
    signal
  })
}

async function start() {
  const list = [...props.topics]
  if (!list.length) {
    error.value = '没有待归类的知识'
    return
  }

  await ensureAiQuestionsLoaded()
  const q = (aiQuestions.value || []).find(
    (it) => String(it.key || '').split(':')[0] === ORGANIZE
  )
  if (!q) {
    error.value = '没有「补全这条知识」这条引导问题（管理后台 → 系统配置 → 系统 AI 配置 → AI 提示词 → 引导问题）'
    return
  }

  rows.value = list.map((t) => ({
    id: t.id,
    title: t.title,
    fallback: t.title || excerptOf(t.content) || '（空）',
    status: 'running',
    patch: null,
    newCategory: null,
    pick: null,
    on: true,
    note: ''
  }))

  running.value = true
  error.value = ''
  abort = new AbortController()

  // 同一批里**同一个目录只建一次**。逐条串行的固有代价就在这儿：
  // 每条都看不见别的条，于是第 1 条提议「工作/Git」、第 2 条提议「git」、第 3 条提议
  // 「Git」——三个都会真建出来，界面上看着像三回事，库里就是三个目录。
  //
  // key 用**名字 + 父级**（`newCategoryKey`）：这和后端的唯一键
  // `(user_id, parent_id, name)` 对得上，也和 `createProposed` 建目录时的判重口径一致。
  // 「数据库/JDBC」和「中间件/JDBC」是**两个不同的目录，都要建** —— 拿名字当键
  // 会把它们并成一个，"中间件/JDBC" 这条就永远建不出来。
  const proposedDirs = new Map()

  for (let i = 0; i < rows.value.length; i++) {
    if (abort.signal.aborted) break
    const row = rows.value[i]
    try {
      const answer = await collectOne(list[i], q.key, q.label, abort.signal)
      const parsed = parseAdopt({ questionKey: q.key, answer }, props.categories)
      if (!parsed) {
        row.note = '这次没按要求的格式回答，跳过'
      } else {
        const { newCategory, unplaced, ...patch } = parsed
        // 目录建议落空（上级不存在）⇒ 说明白，但**不影响这条的其他字段**
        if (unplaced) row.note = unplacedNote(unplaced)
        if (patch.tags) {
          const existing = (list[i].tags || []).map((t) => t.name || t)
          patch.tags = [...new Set([...existing, ...patch.tags])]
        }
        row.patch = patch
        if (newCategory) {
          const key = newCategoryKey(newCategory)
          if (!proposedDirs.has(key)) proposedDirs.set(key, newCategory)
          row.newCategory = proposedDirs.get(key)
          // 预选「+ 新建」。行本身的勾选框就是确认：
          // 取消勾选就既不写别的字段也不建目录
          row.pick = NEW_CATEGORY
        } else {
          row.pick = patch.categoryId ?? null
        }
      }
    } catch (e) {
      if (e?.name === 'AbortError') break
      row.note = e?.message || '失败'
    }
    row.status = 'done'
  }
  running.value = false
  abort = null
}

/**
 * 建目录：去重 + 逐个建的那段收在 `aiAdopt.js` 的 `createProposedCategories`
 * （同步/异步两条采纳路共用；去重键 = `newCategoryKey`，与上面 `start()` 里那层同键）。
 * 这里只留"怎么用"：返回 键 → 新目录 id 的表。
 */
async function createProposed(rowsToApply) {
  return createProposedCategories(rowsToApply, createCategory)
}

async function confirm() {
  const targets = picked.value
  saving.value = true
  try {
    const byKey = await createProposed(targets)
    for (const r of targets) {
      // pick 为空 = 用户没动这一行的目录，保持待归类
      const categoryId = r.pick === NEW_CATEGORY
        ? byKey.get(newCategoryKey(r.newCategory))
        : r.pick
      // 批量写：每条都不刷（一条一刷 = 18 条 36 个索引请求 + 18 次列表重拉），
      // 循环完统一刷一次——和 store 的 updateTopicsCategory 同一个口径。
      await updateTopic(r.id, categoryId == null ? r.patch : { ...r.patch, categoryId }, { refresh: false })
    }
    await refreshAfterTopicChange(targets.some((r) => r.patch?.tags !== undefined))
    ElMessage.success('已采纳')
    close()
    emit('done')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.og__now {
  margin-bottom: var(--sp-8);
  color: var(--cc-text-3);
  font-size: var(--text-xs);
}
/* §4.11(o)：`.og__warn` 一类两义拆开。
   ① 错误字：**真警告** ⇒ 黄色（--cc-warning，不是 EP 的 --el-color-warning）。
      ⚠️ EP 的 --el-color-warning(#e6a23c) 与 cc 的 --cc-warning(#d98a1f) 是两个黄，
      同屏能看出"这个黄和那个黄不一样" ⇒ 全站统一 cc 那一支。
   ② 「会新建目录」：**信息提示** ⇒ 主色（--cc-primary / --cc-primary-soft）。 */
.og__error {
  color: var(--cc-warning);
  font-weight: 500;
}
.og__new {
  color: var(--cc-primary);
  font-weight: 500;
}
.og__list {
  max-height: 380px;
  overflow-y: auto;
}
.og__row {
  display: flex;
  align-items: flex-start;
  gap: var(--sp-8);
  padding: var(--sp-7) 0;
  border-bottom: 1px solid var(--cc-border);
}
.og__row:last-child {
  border-bottom: 0;
}
.og__row.is-off {
  opacity: 0.55;
}
/* 没 patch 的行留个等宽的坑，标题才不会左右跳 */
.og__box {
  width: 13px;
  flex: 0 0 13px;
}
.og__body {
  min-width: 0;
}
/* 限两行：摘要能到三四十个字，不截的话行高参差、一屏看不全 */
.og__title {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
  color: var(--cc-text-1);
  font-size: var(--text-sm);
  line-height: 1.55;
  word-break: break-word;
}
.og__meta {
  color: var(--cc-text-3);
  font-size: var(--text-xs);
  line-height: 1.6;
  word-break: break-word;
}
.og__meta span + span::before {
  content: ' ｜ ';
}
.og__note {
  /* §4.3(d)：EP 的 --el-color-warning(#e6a23c) → cc 的 --cc-warning(#d98a1f)。
     全站同一个黄只有一支。 */
  color: var(--cc-warning);
  font-size: var(--text-xs);
  line-height: 1.5;
}
.og__pick {
  width: 100%;
  max-width: 280px;
  margin-top: var(--sp-4);
}
</style>
