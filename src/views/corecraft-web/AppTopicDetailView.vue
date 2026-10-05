<template>
  <div class="detail">
    <!-- 顶栏：返回 · 目录面包屑 · 保存状态/时间 · 删除。
         面包屑是"这条知识在知识库里处于什么位置"的唯一线索——原来详情页完全没有，
         进来之后不知道自己站在哪棵树下。归类仍然在列表页拖拽完成，这里只读不改。
         未归类（categoryId 为 null）时什么都不显示：CategoryCrumbs 对空值本来就返回空数组，
         没有位置信息可呈现时，一个"未归类"的标签只是噪音（曾经是个虚线框，视觉上比旁边的
         面包屑还重）。 -->
    <header class="bar">
      <button type="button" class="ghost" @click="goBack">
        <el-icon><ArrowLeft /></el-icon>返回
      </button>

      <CategoryCrumbs v-if="topic" :category-id="topic.categoryId" @pick="goCategory" />

      <div class="bar__right">
        <span v-if="saveStatusText" class="save-state" :class="`is-${saveStatus}`">
          <i class="save-state__dot" />{{ saveStatusText }}
        </span>
        <span v-if="topic?.updatedAt" class="bar__time">更新于 {{ topic.updatedAt }}</span>
        <!-- 「简洁 / 完整」放在页面顶栏而不是工具栏里：它是**整个编辑器**的模式开关，
             不是格式动作，工具栏那一排是"格式动作区"，混进去是降一级。
             而且简洁模式下工具栏整行不渲染（原来那个只有切换按钮的空行就是这么来的），
             开关挪到顶栏后两种模式位置不变，来回切它不会跳。 -->
        <el-tooltip :content="toolbar ? '切换到简洁模式（隐藏工具栏）' : '切换到完整模式（显示格式工具栏）'" placement="bottom">
          <button
            type="button"
            class="kb-icon-btn"
            :aria-label="toolbar ? '切换到简洁模式' : '切换到完整模式'"
            @click="toolbar = !toolbar"
          >
            <el-icon><Tools /></el-icon>
          </button>
        </el-tooltip>
        <!-- 全屏开关。沉浸模式下 AppLayout 的顶栏被收起来了，但这一条 .bar 是页面自己的头
             （返回 / 面包屑 / 保存状态都在里面），留着正好当退出路径，Esc 也是一条。 -->
        <el-tooltip :content="immersive.on ? '退出全屏 (Esc)' : '全屏阅读'" placement="bottom">
          <button
            type="button"
            class="kb-icon-btn"
            :aria-label="immersive.on ? '退出全屏' : '全屏阅读'"
            @click="immersive.toggle"
          >
            <el-icon><component :is="immersive.on ? Close : FullScreen" /></el-icon>
          </button>
        </el-tooltip>
        <el-tooltip v-if="topic" content="删除这条知识" placement="bottom">
          <button
            type="button"
            class="kb-icon-btn kb-icon-btn--danger"
            aria-label="删除"
            @click="handleDelete"
          >
            <el-icon><Delete /></el-icon>
          </button>
        </el-tooltip>
      </div>
    </header>

    <div v-if="loading" class="state">加载中…</div>

    <template v-else-if="topic">
      <!-- 标题现在在下面 .content 框内第一行（语雀那种），这里不再单独占一行 -->
      <!-- 标签：pill + 一个入口。
           收起态：无标签时是「添加标签」，有标签时收成一个 + 号（同一个动作，只是没地方写
           那四个字了）。点开 → 变成一个**有边框的输入框** + 右侧向下箭头：
             框里打字回车 = 建新标签；点箭头 = 挑一个已有的。两个动作分开，
             不做成"一个框既搜又建"——短框塞不下「新建「xxx」」那一行。
           **箭头只在展开态出现**：收起态它没有可点的东西，一直挂着只是个装饰。
           入口常驻（不管这条知识有没有标签），所以有标签之后照样能建新标签
           ——原来的入口文字挂在 editTags.length === 0 上，一有标签就消失。
           文案用「添加」而不是「新建」：它挂的是**这条知识**的标签，而"新建"
           会被读成"要造一个全局的新标签"——实际两件事都在（打回车建全局标签、
           点箭头挂已有的），「添加」只描述"往这条知识上加"这层意思，不误导。 -->
      <div class="tags">
        <span v-for="t in editTags" :key="t" class="kb-chip">
          <span class="kb-chip__text">{{ t }}</span>
          <button
            type="button"
            class="kb-chip__x"
            :aria-label="`移除标签 ${t}`"
            @click="removeTag(t)"
          ><el-icon><Close /></el-icon></button>
        </span>

        <button
          v-if="!tagInputOpen"
          type="button"
          class="tags__add"
          aria-label="添加标签"
          @click="openTagInput"
        >
          <template v-if="!editTags.length">添加标签</template>
          <el-icon v-else><Plus /></el-icon>
        </button>

        <div v-else class="tags__picker">
          <el-input
            ref="tagInputRef"
            v-model="tagQuery"
            size="small"
            placeholder="标签名"
            @keyup.enter="commitTypedTag"
            @keydown.esc="closeTagInput"
            @blur="closeTagInput"
          />
          <button
            type="button"
            class="tags__caret"
            :class="{ 'is-open': tagListOpen }"
            :aria-label="tagListOpen ? '收起已有标签' : '选择已有标签'"
            @mousedown.prevent
            @click="tagListOpen = !tagListOpen"
          ><el-icon><ArrowDown /></el-icon></button>

          <!-- @mousedown.prevent 是必需的，不是保险：不阻止默认行为的话 mousedown 会把焦点
               从输入框抢走 → blur → 列表当场收起，列表项永远点不中。阻止之后焦点留在
               输入框，列表能正常点。 -->
          <div v-if="tagListOpen" class="tags__list">
            <button
              v-for="t in filteredTags"
              :key="t.name"
              type="button"
              class="tags__list-item"
              @mousedown.prevent
              @click="pickExisting(t)"
            >
              <span class="tags__list-name">{{ t.name }}</span>
              <span class="tags__list-count">{{ t.count }}</span>
            </button>
            <p v-if="!filteredTags.length" class="tags__list-empty">还没有别的标签</p>
          </div>
        </div>
      </div>

      <!-- 内容：Milkdown WYSIWYG（ProseMirror 内核 + 可选工具栏，封装在 MilkdownEditor 内）
           key=topic.id：切 topic 时强制重建编辑器。
           keep-alive 同 route 名复用组件实例，MilkdownEditorBody 的 useEditor 只在 setup 时跑一次；
           不重建 → 旧 editor 仍渲染旧 doc，新 markdown 传进来也只在 MilkdownEditor.vue 的 markdown ref 里，
           不会更新到 ProseMirror 视图。 -->
      <!-- 内容框：白底卡片。**标题是框内第一行**（语雀那种"第一行就是标题"），
           不再是框外一个孤立的表单字段——框外那行 23px 粗体压在正文上方，
           整页看起来像一张详情表单，而不是一份文档。
           title 字段本身没变，只是换了个渲染位置：卡片、搜索、删除确认、首页
           照旧读 kp_topic.title，零后端改动。 -->
      <div class="content">
        <!-- 内容：Milkdown WYSIWYG（ProseMirror 内核 + 可选工具栏，封装在 MilkdownEditor 内）
             key=topic.id：切 topic 时强制重建编辑器。
             keep-alive 同 route 名复用组件实例，MilkdownEditorBody 的 useEditor 只在 setup 时跑一次；
             不重建 → 旧 editor 仍渲染旧 doc，新 markdown 传进来也只在 MilkdownEditor.vue 的 markdown ref 里，
             不会更新到 ProseMirror 视图。

             标题走 #before-content 插槽递进去，于是框内顺序是
             「工具栏 → 标题 → 正文」——工具栏在框顶、标题是文档第一行，和语雀一致。
             不用插槽就得把工具栏搬到标题上面，但工具栏必须在 MilkdownProvider 内部
             才能拿到编辑器 ctx，搬出去就废了；反过来把标题递进来，顺序自然就对了。 -->
        <MilkdownEditor :key="topic.id" v-model="editContent" v-model:show-toolbar="toolbar">
          <!-- 标题：无边框 input，点哪都能输，失焦即存。placeholder 是「请输入标题」
               不是「未命名」——标题本来就常是空的（createTopic 传 title: ''），
               所以这里不是"兜底占位"，而是一个**真的可以立刻点进去写**的标题位。
               上一版给它外面套了一层「＋ 起个名字」按钮再点一次才出输入框，
               是多出来的一跳，没必要。列表卡片那边也共用 TopicTitleInput。 -->
          <template #before-content>
            <TopicTitleInput
              :topic-id="topic.id"
              :title="topic.title"
              class="title"
              @draft-change="editTitle = $event"
              @saved="onTitleSaved"
            />
          </template>
        </MilkdownEditor>
      </div>

      <!-- 关联知识点：直接复用 TopicCard，和列表页是同一张卡片 -->
      <section v-if="relatedTopics.length" class="section">
        <h4 class="section__title">关联知识点</h4>
        <div class="related">
          <TopicCard
            v-for="r in relatedTopics"
            :key="r.id"
            :topic="r"
            :deletable="false"
            :editable="false"
            @click="goRelated(r.id)"
          />
        </div>
      </section>
    </template>

    <div v-else class="state">
      <p>这条知识不存在，可能已经删掉了。</p>
      <button type="button" class="tags__back" @click="goBack">回到列表</button>
    </div>
  </div>
</template>

<script setup>
// 让 component.name = 'AppTopicDetail' 对齐 route.name，配合 AppLayout 的 keep-alive 缓存实例。
defineOptions({ name: 'AppTopicDetail' })
import { ref, computed, nextTick, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, ArrowLeft, Close, Delete, FullScreen, Plus, Tools } from '@element-plus/icons-vue'
import MilkdownEditor from '../../components/MilkdownEditor.vue'
import CategoryCrumbs from './CategoryCrumbs.vue'
import TopicCard from './TopicCard.vue'
import TopicTitleInput from './TopicTitleInput.vue'
import { useImmersive } from './useImmersive'
import {
  fetchTopic, updateTopic, deleteTopic, getTopic, getAllTags, resolveTags,
  deleteConfirmMessage, ensureAllTagsLoaded, aiGenerateTopic, aiGenerateQuiz
} from './useCorecraftWebStore'

const route = useRoute()
const router = useRouter()

const topic = ref(null)
const loading = ref(true)
// 全屏阅读：收起源的壳，只留这一页。和首页全屏录入、AppLayout 共用同一个判据。
const immersive = useImmersive()
// 编辑器工具栏显不显示。状态在**这一层**而不是 MilkdownEditor 内部，
// 因为"简洁/完整"的开关按钮画在这一层的顶栏里（.bar），
// 按钮和状态必须是同一份，否则切了按钮不动。
const toolbar = ref(false)
const editTitle = ref('')
const editContent = ref('')
const editTags = ref([])
// 标签输入分两层：tagInputOpen 控制"入口 ⇄ 输入框"这次切换，
// tagListOpen 控制"箭头下拉"这一次。箭头只在 tagInputOpen 时才存在。
// 原来是两个入口（inline 输入框 + "选择已有标签" popover），功能重叠；
// 而且 inline 输入框挂在 `editTags.length === 0` 上，有标签之后就再也建不了新标签。
const tagInputOpen = ref(false)
const tagQuery = ref('')
const tagListOpen = ref(false)
const tagInputRef = ref(null)

// ============ AI 优化 + 自测题（#49 模板暂时禁用，state 保留方便以后加回） ============
const aiGenerating = ref(false)
const aiDraft = ref('')

const quizGenerating = ref(false)
const quizList = ref([])

// 内容自动保存状态机：'idle'（不显示）| 'saving'（debounce 中 / 调用中）| 'saved'（保存成功，2s 后回 idle）
const saveStatus = ref('idle')
let saveTimer = null  // debounce 计时器
let statusTimer = null  // 'saved' 状态自动回 'idle' 计时器

const saveStatusText = computed(() => {
  if (saveStatus.value === 'saving') return '保存中'
  if (saveStatus.value === 'saved') return '已保存'
  return ''
})

const existingTags = computed(() => getAllTags())

// 还没挂在这条知识上的标签
const availableTags = computed(() =>
  existingTags.value
    .filter((t) => !editTags.value.includes(t.name))
)

// 箭头展开的列表：输入框里有字就按字收窄，没字就按频次倒序扫一眼。
const filteredTags = computed(() => {
  const q = tagQuery.value.trim().toLowerCase()
  if (!q) return [...availableTags.value].sort((a, b) => b.count - a.count).slice(0, 50)
  return availableTags.value.filter((t) => t.name.toLowerCase().includes(q))
})

// 关联知识点：从 store 的 topicMap 里取。
// 之前这里挂了一个本地的 ref(new Map())，从来没有被任何地方写入过，
// relatedTopics 因此恒为空数组，"关联知识点"整块从来没渲染出来过。
// store 的 getTopic(id) 做的就是这次查表，AppLayout 启动时已 ensureTopicsLoaded。
const relatedTopics = computed(() => {
  const ids = topic.value?.relatedTopicIds || []
  return ids.map((id) => getTopic(id)).filter(Boolean)
})

async function load() {
  loading.value = true
  try {
    const id = Number(route.params.id)
    // URL 里的 id 不合法（手输 /topic/NaN 等）→ 直接走"知识点不存在"分支，不发请求避免后端
    // "Failed to convert 'NaN' to Long"。修传参方即可，fetchTopic / 后端不动。
    if (!Number.isFinite(id) || id <= 0) {
      topic.value = null
      return
    }
    // 只调详情接口；已有标签列表懒加载（用户点开箭头下拉时才调 ensureAllTagsLoaded）
    const t = await fetchTopic(id)
    if (!t) {
      topic.value = null
      return
    }
    topic.value = t
    editTitle.value = t.title || ''
    editContent.value = t.content || ''
    editTags.value = (t.tags || []).map((tg) => tg.name)
    quizList.value = t.quizQuestions || []
  } finally {
    loading.value = false
  }
}

// 标题的存取搬进了 TopicTitleInput（和列表卡片共用）。
// 存完之后这里拿到的是 trim 过的值——store 那边已经刷过了，
// 但本组件的 topic 是 load() 时拍的一份快照，不跟着 store 走，得自己补一下。
function onTitleSaved(title) {
  topic.value = { ...topic.value, title }
}

async function saveContent() {
  if (!topic.value) return
  if ((topic.value.content || '') === editContent.value) return
  // 取消 pending debounce（避免重复保存）
  if (saveTimer) { clearTimeout(saveTimer); saveTimer = null }
  saveStatus.value = 'saving'
  try {
    await updateTopic(topic.value.id, { content: editContent.value })
    topic.value = { ...topic.value, content: editContent.value }
    saveStatus.value = 'saved'
    if (statusTimer) clearTimeout(statusTimer)
    statusTimer = setTimeout(() => { saveStatus.value = 'idle' }, 2000)
  } catch (e) {
    saveStatus.value = 'idle'
    ElMessage.error('保存失败')
    throw e
  }
}

// 内容自动保存：用户编辑后 debounce 1.5s 调用 saveContent。
// load() 设置 editContent 时 watch 也会触发，但 (topic.value.content === newVal) 命中 → 直接 return，不保存。
watch(editContent, (newVal) => {
  if (!topic.value) return
  if ((topic.value.content || '') === newVal) return
  if (saveTimer) clearTimeout(saveTimer)
  saveStatus.value = 'saving'
  saveTimer = setTimeout(() => {
    saveTimer = null
    saveContent()
  }, 1500)
})

async function saveTags() {
  if (!topic.value) return
  // 防御性去重：editTags 理论上 addTag 已 dedup，但避免极端路径把重复字符串传给后端
  const tags = resolveTags([...new Set(editTags.value)])
  await updateTopic(topic.value.id, { tags })
  topic.value = { ...topic.value, tags }
  ElMessage.success('标签已更新')
}

function addTag(name) {
  const value = name.trim()
  if (!value) return
  if (editTags.value.includes(value)) return  // dedup：避免重复
  editTags.value = [...editTags.value, value]
  saveTags()
}

// 展开输入框 + 聚焦。ElInput 实例 expose 了 focus()（EP 官方 API），
// 不用再绕 $el.querySelector('input')。
async function openTagInput() {
  tagInputOpen.value = true
  await nextTick()
  tagInputRef.value?.focus?.()
}

// 收起：输入框和下拉一起收，回到入口
function closeTagInput() {
  tagInputOpen.value = false
  tagListOpen.value = false
  tagQuery.value = ''
}

// 输入框回车：精确命中已有标签就选它（不然会造出一个同名重复，散在各处），
// 否则当新标签建。加完收起，回到入口。
function commitTypedTag() {
  const v = tagQuery.value.trim()
  if (!v) { closeTagInput(); return }
  const hit = availableTags.value.find((t) => t.name === v)
  addTag(hit ? hit.name : v)
  closeTagInput()
}

function pickExisting(t) {
  addTag(t.name)
  closeTagInput()
}

function removeTag(t) {
  editTags.value = editTags.value.filter((x) => x !== t)
  saveTags()
}

async function handleAiGenerate() {
  if (aiGenerating.value) return
  aiGenerating.value = true
  aiDraft.value = ''
  try {
    const result = await aiGenerateTopic(editTitle.value || '未命名', () => {})
    aiDraft.value = result.content
  } finally {
    aiGenerating.value = false
  }
}

function applyAiDraft() {
  if (!aiDraft.value) return
  editContent.value = editContent.value + '\n\n---\n\n' + aiDraft.value
  aiDraft.value = ''
  saveContent()
}

async function handleGenerateQuiz() {
  if (quizGenerating.value) return
  quizGenerating.value = true
  try {
    const questions = await aiGenerateQuiz(
      editTitle.value || '未命名',
      editContent.value,
      () => {}
    )
    quizList.value = questions
    await updateTopic(topic.value.id, { quizQuestions: questions })
    ElMessage.success(`已生成 ${questions.length} 道自测题`)
  } finally {
    quizGenerating.value = false
  }
}

async function handleDelete() {
  try {
    await ElMessageBox.confirm(
      deleteConfirmMessage(topic.value),
      '删除知识点',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  await deleteTopic(topic.value.id)
  ElMessage.success('已删除')
  goBack()
}

// 详情页不是菜单项，没有"上一页"语义：优先原路返回，直接打开的（刷新/外链）才回列表
function goBack() {
  if (window.history.state?.back) {
    router.back()
  } else {
    router.push({ name: 'AppLearn' })
  }
}

function goCategory(id) {
  router.push({ path: '/app/learn', query: { category: id } })
}

function goRelated(id) {
  router.push({ name: 'AppTopicDetail', params: { id } })
}

// 路由 id 变化时检查是否需要重新 load：
// keep-alive 缓存 TopicDetailView 实例后，同 route name 复用实例，topic.value 可能已经是
// 当前 id 的数据——切回同 id 不重新 fetch（保留本地编辑状态）；切到新 id 走 load。
watch(() => route.params.id, () => {
  const id = Number(route.params.id)
  if (!Number.isFinite(id) || id <= 0) return
  if (topic.value && topic.value.id === id) return  // 同 id：复用缓存，不重新加载
  load()
})
onMounted(() => {
  load()
})

// 懒加载 tag 列表：用户点开箭头下拉时才调 /kp/tag/list。
// 详情页打开不请求；首次点开触发请求；后续复用 store 缓存。
// （没点开过箭头就直接打字回车是纯新建路径，此时 availableTags 是空的，
//   commitTypedTag 里的"精确命中就选中"自然匹配不到，不会误判。）
watch(tagListOpen, (open) => {
  if (open) ensureAllTagsLoaded()
})

// 离开详情页时清理自动保存定时器，避免后台 setTimeout 在组件销毁后还触发 updateTopic
onBeforeUnmount(() => {
  if (saveTimer) { clearTimeout(saveTimer); saveTimer = null }
  if (statusTimer) { clearTimeout(statusTimer); statusTimer = null }
})
</script>

<style scoped>
/* 收窄到阅读档：长文一行 920px ≈ 60 个汉字，1280 会到 80+，超出舒适行宽。
   这是这里唯一主动覆盖壳默认值的地方，所以壳那边用 :where() 把权重包成 0。
   margin: 0 auto 壳的 `.app-content > *` 已经给了，不重复写。 */
.detail {
  display: flex;
  flex-direction: column;
  max-width: var(--cc-content-w-reading);
}

/* ---------- 顶栏 ---------- */
.bar {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 24px;
  margin-bottom: 14px;
}
.bar__right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}
.bar__time {
  font-size: 12px;
  color: var(--cc-text-4);
  font-variant-numeric: tabular-nums;
}

/* 文字型幽灵按钮：返回 */
.ghost {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  border: none;
  background: transparent;
  padding: 4px 8px 4px 5px;
  margin-left: -5px;
  border-radius: var(--cc-radius-sm);
  color: var(--cc-text-3);
  font-size: 13px;
  cursor: pointer;
  flex-shrink: 0;
  transition: var(--cc-transition);
}
.ghost:hover {
  background: var(--cc-surface-2);
  color: var(--cc-text-1);
}

/* 自动保存状态：一个小圆点 + 文字，idle 时整块不渲染 */
.save-state {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: var(--cc-text-3);
}
.save-state__dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
}
.save-state.is-saved {
  color: var(--cc-success);
}
/* saving 用主色而不是灰：灰会和"已保存"瞬间的绿撞在一起，看不出是在写还是在存 */
.save-state.is-saving {
  color: var(--cc-primary);
}

/* ---------- 框内标题（语雀那种"第一行就是标题"） ---------- */
/* 这里只剩"这一处的排版"：字号字重颜色内外边距。
   无边框 / 透明底 / 铺满一行 / 占位色那几样在 TopicTitleInput 里，
   两边共用——同一个属性在组件和调用方各写一遍，就会在同权重下抢样式表
   前后顺序，那是最难查的一类问题。
   .content 的 padding-top 是 0（要留给 Milkdown wrapper 自己的 28px），
   所以上边距由标题自己带。上边距从 18px 降到 12px：那 18px 里有一部分
   是在给 wrapper 顶部那条 28px 空道让位（模式切换按钮 absolute 定位在那儿），
   按钮挪到页面顶栏之后就不需要让了。 */
.title {
  font-size: 23px;
  /* 700 是跟正文里的 H1 对齐的：prosemirror.css 没给 h1 任何规则，
     粗细全是浏览器默认的 bold（=700），颜色则继承 .ProseMirror 的 --cc-text-1，
     跟这里本来就是同一个色。之前写 600，比 H1 轻一档，同一句话并排出现时
     上面的标题看着"不是标题"。字号没跟着改成 28px（h1 的 2em）——
     那是排版尺寸，用户只要了粗细和颜色。 */
  font-weight: 700;
  line-height: 1.45;
  letter-spacing: -0.2px;
  color: var(--cc-text-1);
  padding: 12px 0 2px;
  margin: 0 0 2px;
}

/* ---------- 标签行 ---------- */
.tags {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  padding-bottom: 14px;
  margin-bottom: 18px;
  border-bottom: 1px solid var(--cc-border);
}
/* 「回到列表」：空状态里那个导航按钮 */
.tags__back {
  border: none;
  background: transparent;
  padding: 4px 8px;
  border-radius: 4px;
  color: var(--cc-text-3);
  font-size: 13px;
  cursor: pointer;
  font-family: inherit;
  transition: var(--cc-transition);
}
.tags__back:hover {
  background: var(--cc-surface-2);
  color: var(--cc-text-1);
}

/* ---------- 标签入口 / 输入框 ---------- */
/* 两者都是"空 chip"：高 20px、4px 圆角、--cc-surface-2 底色、**没有边框**——
   三样全部照抄全局 .kb-chip 的尺寸，所以它跟在真 chip 旁边是同一种东西。
   之前给它画了一圈 1px 边框、还做到 24px 高，结果在 chip 后面杵着一个
   又高又带框的方块，一眼就是个控件而不是"再加一个标签"。 */
.tags__add,
.tags__picker {
  display: inline-flex;
  align-items: center;
  height: 20px;
  flex-shrink: 0;
  border: none;
  border-radius: 4px;
  background: var(--cc-surface-2);
  color: var(--cc-text-4);
  font-size: 11px;
  transition: var(--cc-transition);
}
.tags__add {
  gap: 3px;
  padding: 1px 6px;
  cursor: pointer;
  font-family: inherit;
}
.tags__add:hover {
  background: var(--cc-border-strong);
  color: var(--cc-text-2);
}
.tags__picker {
  position: relative;
  width: 170px;
  gap: 2px;
  padding: 0 4px 0 6px;
}
/* 框由 .tags__picker 自己画，EP 那圈去掉，否则是双层边。
   代价是默认的聚焦光环也没了，用 :focus-within 补回来。 */
.tags__picker :deep(.el-input__wrapper) {
  padding: 0;
  box-shadow: none;
  background: transparent;
}
.tags__picker :deep(.el-input__inner) {
  font-size: 11px;
  height: 18px;
}
.tags__picker:focus-within {
  background: var(--cc-surface);
  box-shadow: 0 0 0 1px var(--cc-primary);
}
.tags__caret {
  border: none;
  background: transparent;
  padding: 0 1px;
  cursor: pointer;
  color: var(--cc-text-4);
  display: inline-flex;
  align-items: center;
  font-size: 11px;
  flex-shrink: 0;
  transition: var(--cc-transition);
}
.tags__caret:hover {
  color: var(--cc-primary);
}
/* 展开时箭头翻上去，开合方向自明 */
.tags__caret.is-open {
  transform: rotate(180deg);
}
/* 下拉：就地展开的绝对定位列表，不 teleport、不遮挡正文。
   宽度不跟输入框走——170px 装不下「基本数据类型」这种标签名，会被截断。 */
.tags__list {
  position: absolute;
  top: calc(100% + 4px);
  left: 0;
  min-width: 100%;
  max-width: 220px;
  max-height: 240px;
  overflow-y: auto;
  background: var(--cc-surface);
  border: 1px solid var(--cc-border-strong);
  border-radius: var(--cc-radius-sm);
  box-shadow: 0 8px 28px rgba(31, 35, 41, 0.12);
  padding: 4px;
  z-index: 20;
}
.tags__list-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
  border: none;
  background: transparent;
  padding: 5px 8px;
  border-radius: var(--cc-radius-sm);
  font-size: 13px;
  color: var(--cc-text-2);
  cursor: pointer;
  text-align: left;
  font-family: inherit;
  transition: var(--cc-transition);
}
.tags__list-item:hover {
  background: var(--cc-surface-2);
  color: var(--cc-text-1);
}
.tags__list-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tags__list-count {
  font-size: 11px;
  color: var(--cc-text-4);
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}
.tags__list-empty {
  margin: 0;
  padding: 8px;
  font-size: 12px;
  color: var(--cc-text-4);
}

/* ---------- 正文框 ---------- */
/* 和知识点卡片同一张脸：白底 + #eef0f3 细边 + 12px 圆角。
   之前这里是 6px 圆角 + #ebeef5，比壳里所有卡片都锐一档，一眼就是"外来件"。
   顶部 14px：这一行现在归**工具栏**了（标题递进 MilkdownEditor 的 #before-content 插槽），
   工具栏那排按钮贴到框顶会顶着边框，所以留白改由卡片自己带。
   底部 18px 收口。 */
.content {
  background: var(--cc-surface);
  border: 1px solid #eef0f3;
  border-radius: 12px;
  padding: 14px 18px 18px;
}
/* 正文的字号 / 行高 / 代码排版不在这里写：.ProseMirror 是 Milkdown 这个第三方函数式
   组件渲染出来的，节点上不带 scoped 的 data-v 属性，从本页 scoped 样式里 :deep 过去
   匹配不可靠（实测没生效）。统一放在 MilkdownEditorBody 的全局样式块里，
   和它的高度规则（min-height 135 / max-height 80vh）写在一起——编辑器长什么样只有那一处说了算。 */

/* ---------- 关联知识点 ---------- */
.section {
  margin-top: 28px;
  padding-top: 20px;
  border-top: 1px solid var(--cc-border);
}
.section__title {
  margin: 0 0 12px;
  font-size: 13px;
  font-weight: 600;
  color: var(--cc-text-2);
}
.related {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 12px;
}

/* ---------- 空 / 加载 ---------- */
.state {
  padding: 64px 0;
  text-align: center;
  color: var(--cc-text-4);
  font-size: 13px;
}
.state p {
  margin: 0 0 10px;
}
</style>
