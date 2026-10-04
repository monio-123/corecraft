<template>
  <div class="topic-detail-page">
    <div class="detail-header">
      <el-button text @click="goBack">
        <el-icon><ArrowLeft /></el-icon> 返回
      </el-button>
      <el-button v-if="topic" text type="danger" @click="handleDelete">
        <el-icon><Delete /></el-icon> 删除
      </el-button>
    </div>

    <div v-if="loading" class="loading">加载中…</div>

    <template v-else-if="topic">
      <!-- 标题（点击编辑） -->
      <input
        v-model="editTitle"
        class="title-input"
        placeholder="未命名"
        maxlength="100"
        @blur="saveTitle"
      />

      <!-- 这里没有目录行：目录一多，平铺的下拉列表就没法用了。
           归类和改类都回列表页——把卡片拖到右侧浮出的那棵树上。 -->
      <!-- 标签：自定义输入 + tag pill 列表，避免 el-select multiple 模式的重复添加问题 -->
      <div class="meta-row">
        <div class="tag-editor">
          <el-tag
            v-for="t in editTags"
            :key="t"
            closable
            size="small"
            class="tag-pill"
            @close="removeTag(t)"
          >{{ decodeTagDisplay(t) }}</el-tag>
          <!-- 编辑中：inline 输入框（无外边框，跟 pills 同基线） -->
          <el-input
            v-if="tagInputEditing"
            ref="tagInputRef"
            v-model="tagInput"
            size="small"
            class="tag-input"
            placeholder="新标签名"
            @keyup.enter="onTagInputEnter"
            @keydown.delete="onDeleteEmpty"
            @blur="onTagInputBlur"
          />
          <!-- 无标签 + 未编辑：占位文字（点一下展开输入框） -->
          <span
            v-else-if="editTags.length === 0"
            class="tag-placeholder"
            @click="openTagInput"
          >新建标签</span>
          <!-- 有标签 + 未编辑：+ 小图标（点击展开输入框） -->
          <el-icon
            v-else
            class="tag-add-trigger"
            title="新增标签"
            @click="openTagInput"
          ><Plus /></el-icon>
        </div>
        <el-popover
          placement="bottom-start"
          :width="280"
          trigger="click"
          v-model:visible="tagPickerVisible"
          @show="tagPickerQuery = ''"
        >
          <template #reference>
            <el-button size="small" text class="tag-picker-trigger">
              <el-icon><Plus /></el-icon> 选择已有标签
            </el-button>
          </template>
          <div class="tag-picker">
            <el-input
              v-model="tagPickerQuery"
              size="small"
              placeholder="搜索标签名"
              clearable
              class="tag-picker__search"
            />
            <div v-if="filteredAvailableTags.length" class="tag-picker__list">
              <div
                v-for="t in filteredAvailableTags"
                :key="t.name"
                class="tag-picker__item"
                @click="pickTag(t)"
              >
                <span class="tag-picker__item-name">{{ t.name }}</span>
                <span class="tag-picker__item-count">{{ t.count }} 次</span>
              </div>
            </div>
            <el-empty v-else :description="tagPickerQuery ? '没找到匹配的标签' : '暂无可选标签'" :image-size="40" />
          </div>
        </el-popover>
        <span class="meta-time">创建于 {{ topic.createdAt }}</span>
        <span v-if="saveStatusText" class="save-status" :class="`save-status--${saveStatus}`">{{ saveStatusText }}</span>
      </div>

      <!-- 内容：Milkdown WYSIWYG（封装在 MilkdownEditor 内：ProseMirror 内核 + 6 按钮工具栏）
           key=topic.id：切 topic 时强制重建编辑器。
           keep-alive 同 route 名复用组件实例，MilkdownEditorBody 的 useEditor 只在 setup 时跑一次；
           不重建 → 旧 editor 仍渲染旧 doc，新 markdown 传进来也只在 MilkdownEditor.vue 的 markdown ref 里，
           不会更新到 ProseMirror 视图。 -->
      <div class="content-area">
        <MilkdownEditor :key="topic.id" v-model="editContent" />
      </div>

      <!-- 关联知识点 -->
      <div v-if="relatedTopics.length" class="section">
        <h4>关联知识点</h4>
        <div class="related-grid">
          <el-card
            v-for="r in relatedTopics"
            :key="r.id"
            class="related-card"
            shadow="hover"
            @click="goRelated(r.id)"
          >
            <div class="related-card__title">{{ r.title || '未命名' }}</div>
            <div v-if="r.tags && r.tags.length" class="related-card__tags">
              <el-tag
                v-for="(t, i) in r.tags.slice(0, 2)"
                :key="i"
                size="small"
                effect="plain"
              >{{ t.name }}</el-tag>
            </div>
          </el-card>
        </div>
      </div>
    </template>

    <el-empty v-else description="知识点不存在" />
  </div>
</template>

<script setup>
// 让 component.name = 'AppTopicDetail' 对齐 route.name，配合 AppLayout 的 keep-alive 缓存实例。
defineOptions({ name: 'AppTopicDetail' })
import { ref, computed, nextTick, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Delete, Plus } from '@element-plus/icons-vue'
import MilkdownEditor from '../../components/MilkdownEditor.vue'
import {
  fetchTopic, updateTopic, deleteTopic, getAllTags, resolveTags,
  ensureAllTagsLoaded, aiGenerateTopic, aiGenerateQuiz
} from './useCorecraftWebStore'

const route = useRoute()
const router = useRouter()

const topic = ref(null)
const loading = ref(true)
const editTitle = ref('')
const editContent = ref('')
const editTags = ref([])
const tagInput = ref('')
// 标签输入框展开状态：默认收起（不显示大空白框）。点 + 或"新建标签"占位展开。
const tagInputEditing = ref(false)
const tagInputRef = ref(null)

// 标签选择 popover：点击"选择已有标签"按钮展开，搜索过滤未选 tag
const tagPickerVisible = ref(false)
const tagPickerQuery = ref('')

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
  if (saveStatus.value === 'saving') return '保存中…'
  if (saveStatus.value === 'saved') return '已保存'
  return ''
})

const existingTags = computed(() => getAllTags())

// 标签选择 popover：未选 tag 列表 + 搜索过滤
const availableTags = computed(() =>
  existingTags.value
    .filter((t) => !editTags.value.includes(t.name))
)

const filteredAvailableTags = computed(() => {
  const q = tagPickerQuery.value.trim().toLowerCase()
  // 无搜索词：按频次倒序，限 50 条（够扫一眼，太多会触发"按名字搜索"）
  if (!q) return [...availableTags.value].sort((a, b) => b.count - a.count).slice(0, 50)
  return availableTags.value.filter((t) => t.name.toLowerCase().includes(q))
})

function pickTag(t) {
  addTag(t.name)
  tagPickerVisible.value = false
  tagPickerQuery.value = ''
}

const relatedTopics = computed(() => {
  if (!topic.value || !topic.value.relatedTopicIds) return []
  return topic.value.relatedTopicIds
    .map((id) => topicMap.value?.get(id))
    .filter(Boolean)
})

// 简易 topicMap（仅用于关联跳转查找）
const topicMap = ref(new Map())

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
    // 只调详情接口；"选择已有标签" popover 懒加载（用户点开时才调 ensureAllTagsLoaded）
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

async function saveTitle() {
  if (!topic.value) return
  if ((topic.value.title || '') === editTitle.value) return
  await updateTopic(topic.value.id, { title: editTitle.value.trim() })
  topic.value = { ...topic.value, title: editTitle.value.trim() }
  ElMessage.success('标题已更新')
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

// 展开输入框 + 聚焦。nextTick 后聚焦（el-input 渲染后才能拿到 ref）。
async function openTagInput() {
  tagInputEditing.value = true
  await nextTick()
  // el-input 实例的 focus() 在新版 element-plus 上可能挂在 input 元素上
  const inputEl = tagInputRef.value?.$el?.querySelector('input') || tagInputRef.value?.$el
  if (inputEl && typeof inputEl.focus === 'function') inputEl.focus()
}

// 回车：添加 tag + 收起输入框（如果还有未输入的内容则保留 input 展开）
function onTagInputEnter() {
  const value = tagInput.value.trim()
  if (!value) {
    // 空回车直接收起
    tagInputEditing.value = false
    return
  }
  addTag(value)
  tagInput.value = ''
  // 继续展开（让用户连续输入），不收起
}

// 失焦：清空输入 + 收起输入框
function onTagInputBlur() {
  // 微延迟避免点 + 触发 blur 时把 tag-add-trigger 自己也点上了
  setTimeout(() => {
    tagInput.value = ''
    tagInputEditing.value = false
  }, 100)
}

function removeTag(t) {
  editTags.value = editTags.value.filter((x) => x !== t)
  saveTags()
}

function onDeleteEmpty(e) {
  // 输入框为空时按删除，移除最后一个 tag
  if (tagInput.value === '' && editTags.value.length > 0) {
    e.preventDefault()
    const last = editTags.value[editTags.value.length - 1]
    removeTag(last)
  }
}

function decodeTagDisplay(value) {
  return value
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
      `确定删除「${topic.value.title || '未命名'}」吗？`,
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

// 懒加载 tag 列表：用户点开"选择已有标签" popover 时才调 /kp/tag/list。
// 详情页打开不请求；首次点开触发请求；后续复用 store 缓存。
watch(tagPickerVisible, (visible) => {
  if (visible) ensureAllTagsLoaded()
})

// 离开详情页时清理自动保存定时器，避免后台 setTimeout 在组件销毁后还触发 updateTopic
onBeforeUnmount(() => {
  if (saveTimer) { clearTimeout(saveTimer); saveTimer = null }
  if (statusTimer) { clearTimeout(statusTimer); statusTimer = null }
})
</script>

<style scoped>
.topic-detail-page {
  max-width: 800px;
  margin: 0 auto;
  padding: 20px 24px;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.loading {
  text-align: center;
  color: #909399;
  padding: 60px 0;
}

.title-input {
  width: 100%;
  font-size: 22px;
  font-weight: 600;
  color: #303133;
  border: none;
  outline: none;
  padding: 4px 0;
  margin-bottom: 12px;
  background: transparent;
}

.title-input::placeholder {
  color: #c0c4cc;
  font-weight: 400;
}

.meta-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

/* "目录" / "标签" 这类行首说明文字 */
.meta-label {
  font-size: 12px;
  color: #909399;
  width: 28px;
  flex-shrink: 0;
}

.tag-select {
  flex: 1;
  min-width: 200px;
  max-width: 400px;
}

/* tag-editor：去掉外边框/背景/min-height，纯 flex 容器，pills + 占位 + + icon 直接 inline。
   之前 border+background+min-height:32px 撑出大空白，现在由内容自然撑开。 */
.tag-editor {
  flex: 1;
  min-width: 200px;
  max-width: 500px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}
.tag-editor .tag-input {
  flex: 1;
  min-width: 120px;
  --el-input-width: 100%;
}
/* el-input 在 tag-editor 内不要外层 box-shadow / 边框，跟 pills 同基线 */
.tag-editor .tag-input :deep(.el-input__wrapper) {
  box-shadow: none;
  padding: 0 4px;
}
.tag-pill {
  margin: 0;
}
/* 无标签时 placeholder：灰字，cursor pointer 表示可点击 */
.tag-placeholder {
  color: #c0c4cc;
  font-size: 13px;
  cursor: pointer;
  user-select: none;
}
.tag-placeholder:hover {
  color: #409eff;
}
/* 有标签时 + 图标触发器：低调，跟 pills 同高度 */
.tag-add-trigger {
  color: #909399;
  font-size: 16px;
  cursor: pointer;
  padding: 0 4px;
  user-select: none;
}
.tag-add-trigger:hover {
  color: #409eff;
}

/* 标签选择 popover */
.tag-picker {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.tag-picker__search :deep(.el-input__wrapper) {
  padding: 2px 8px;
}
.tag-picker__list {
  max-height: 240px;
  overflow-y: auto;
  border-top: 1px solid #f0f2f5;
  padding-top: 4px;
}
.tag-picker__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 8px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
  color: #303133;
  transition: background 0.12s;
}
.tag-picker__item:hover {
  background: #ecf5ff;
  color: #409eff;
}
.tag-picker__item-count {
  font-size: 11px;
  color: #909399;
}
.tag-picker__item:hover .tag-picker__item-count {
  color: #409eff;
}
.tag-picker-trigger {
  margin-left: 4px;
}

.meta-time {
  font-size: 12px;
  color: #c0c4cc;
}

/* 自动保存状态指示器（idling 不显示，saving 灰色，saved 绿色，2s 后自动回 idling） */
.save-status {
  font-size: 12px;
  margin-left: 4px;
}
.save-status--saving {
  color: #909399;
}
.save-status--saved {
  color: #67c23a;
}

/* 容器样式：给编辑器白色背景 + 细边框，避免 ProseMirror 没背景没边框融入页面背景（详情页无 el-card 包裹） */
.content-area {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 12px 16px;
  margin-bottom: 16px;
}

/*
  以下样式属于"AI 优化 + 自测题"功能，#49 模板已隐藏（按用户"先去掉"诉求临时禁用）。
  state 和函数保留方便以后加回（模板里 uncomment + 启用样式即可）。
  不删 .section / .ai-result* / .quiz-item* 等死 CSS，避免重新启用时再敲一遍。
*/

.section {
  margin-top: 28px;
  padding-top: 20px;
  border-top: 1px solid #ebeef5;
}

.section h4 {
  margin: 0 0 14px 0;
  font-size: 15px;
  font-weight: 600;
}

.related-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 10px;
}

.related-card {
  cursor: pointer;
}

.related-card :deep(.el-card__body) {
  padding: 12px 14px;
}

.related-card__title {
  font-size: 13px;
  font-weight: 500;
  color: #303133;
  margin-bottom: 4px;
}

.related-card__tags {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}
</style>