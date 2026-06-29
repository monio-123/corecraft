<template>
  <div class="learn-topic-page">
    <div class="page-header">
      <h2>学习新主题</h2>
      <p class="subtitle">输入你想学习的主题，AI 自动生成知识点内容、标签和自测题目</p>
    </div>

    <!-- 输入区域 -->
    <div class="input-card">
      <el-input
        v-model="topicTitle"
        size="large"
        placeholder="输入学习主题，如：HTTP 协议、MySQL 索引、DNS..."
        :disabled="aiGenerating"
        @keyup.enter="handleGenerate"
        clearable
      >
        <template #prepend>📖 主题</template>
      </el-input>

      <!-- 标签区域 -->
      <div class="tag-area">
        <span class="tag-area__label">标签：</span>
        <el-select
          v-model="selectedTags"
          multiple
          filterable
          allow-create
          default-first-option
          placeholder="选择或输入标签（如 #计算机网络）"
          :disabled="aiGenerating"
          style="flex:1"
        >
          <el-option
            v-for="tag in existingTags"
            :key="tag.category + '::' + tag.name"
            :label="tagLabel(tag)"
            :value="tag.category + '::' + tag.name"
          />
        </el-select>
        <span class="tag-area__hint">选择已有标签可自动关联相关知识</span>
      </div>

      <el-button
        type="primary"
        size="large"
        :loading="aiGenerating"
        :disabled="!topicTitle.trim()"
        @click="handleGenerate"
        class="generate-btn"
      >
        <el-icon><MagicStick /></el-icon>
        {{ aiGenerating ? 'AI 生成中...' : 'AI 生成' }}
      </el-button>
    </div>

    <!-- AI 生成进度 -->
    <div v-if="aiGenerating" class="progress-card">
      <div class="progress-card__icon">🤖</div>
      <h3>AI 正在生成知识点</h3>
      <div class="progress-steps">
        <div
          v-for="(s, idx) in aiSteps"
          :key="idx"
          class="progress-step"
          :class="{ done: idx < aiStepIdx, active: idx === aiStepIdx }"
        >
          <span class="progress-step__dot">
            <el-icon v-if="idx < aiStepIdx"><Check /></el-icon>
            <span v-else-if="idx === aiStepIdx" class="progress-step__spinner"></span>
            <span v-else class="progress-step__pending"></span>
          </span>
          <span class="progress-step__label">{{ s }}</span>
        </div>
      </div>
    </div>

    <!-- 生成结果 -->
    <div v-if="generatedContent && !aiGenerating" class="result-area">
      <!-- 操作行 -->
      <div class="result-actions">
        <h3>{{ topicTitle }}</h3>
        <div class="result-actions__btns">
          <el-button @click="resetForm">
            <el-icon><Plus /></el-icon> 学习新主题
          </el-button>
          <el-button type="primary" @click="saveTopic">
            <el-icon><Check /></el-icon> 保存到我的知识点
          </el-button>
        </div>
      </div>

      <!-- 标签 -->
      <div v-if="generatedTags.length > 0" class="result-tags">
        <el-tag
          v-for="(tag, i) in generatedTags"
          :key="i"
          :type="tagType(tag.category)"
          effect="plain"
          size="default"
        >
          {{ tagLabel(tag) }}
        </el-tag>
      </div>

      <!-- 内容 -->
      <div class="result-content" v-html="renderedContent"></div>

      <!-- 自测题区域 -->
      <div class="result-quiz">
        <!-- 未生成题目：显示生成按钮 -->
        <div v-if="!quizGenerating && generatedQuiz.length === 0" class="quiz-generate">
          <p class="quiz-generate__hint">AI 可根据已生成的内容智能出题，帮助巩固记忆</p>
          <el-button
            type="primary"
            :loading="quizGenerating"
            @click="handleGenerateQuiz"
          >
            <el-icon><MagicStick /></el-icon> 生成自测题
          </el-button>
        </div>

        <!-- 生成进度 -->
        <div v-if="quizGenerating" class="quiz-progress">
          <div class="quiz-progress__icon">📝</div>
          <p>AI 正在根据内容生成自测题…</p>
          <div class="quiz-progress__steps">
            <span
              v-for="(s, idx) in quizSteps"
              :key="idx"
              class="quiz-progress__step"
              :class="{ done: idx < quizStepIdx, active: idx === quizStepIdx }"
            >{{ s }}{{ idx < quizSteps.length - 1 ? ' → ' : '' }}</span>
          </div>
        </div>

        <!-- 已生成题目 -->
        <div v-if="generatedQuiz.length > 0 && !quizGenerating">
          <h4>📝 配套自测题</h4>
          <div v-for="(q, qi) in generatedQuiz" :key="qi" class="quiz-item">
            <p class="quiz-item__q">
              <span class="quiz-item__num">{{ qi + 1 }}.</span>
              {{ q.question }}
              <el-tag size="small" :type="q.type === 'single' ? '' : 'warning'">
                {{ q.type === 'single' ? '单选' : '多选' }}
              </el-tag>
            </p>
            <div class="quiz-item__options">
              <span
                v-for="(opt, oi) in q.options"
                :key="oi"
                class="quiz-option"
                :class="{ correct: oi === q.answer }"
              >
                {{ String.fromCharCode(65 + oi) }}. {{ opt }}
                <el-icon v-if="oi === q.answer" class="quiz-option__check"><Check /></el-icon>
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- 关联已有知识点 -->
      <div v-if="relatedTopics.length > 0" class="result-related">
        <h4>🔗 自动关联已有知识点</h4>
        <p class="result-related__hint">
          系统根据标签自动匹配以下相关知识点，无需手动关联
        </p>
        <div class="related-list">
          <div v-for="rt in relatedTopics" :key="rt.id" class="related-item">
            <div class="related-item__title">{{ rt.title }}</div>
            <div class="related-item__tags">
              <el-tag
                v-for="(tag, ti) in rt.tags"
                :key="ti"
                size="small"
                effect="plain"
              >{{ tagLabel(tag) }}</el-tag>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 已保存的知识点列表 -->
    <div v-if="savedTopics.length > 0" class="history-section">
      <div class="history-header">
        <h3>我的知识点</h3>
        <span class="history-count">共 {{ savedTopics.length }} 个</span>
        <el-button text size="small" @click="handleResync" style="margin-left: auto;">
          重新同步到知识树
        </el-button>
      </div>
      <div class="history-grid">
        <div
          v-for="t in savedTopics"
          :key="t.id"
          class="history-card"
          @click="viewTopic(t)"
        >
          <div class="history-card__title">{{ t.title }}</div>
          <div class="history-card__tags" @click.stop>
            <template v-if="editingTagsId === t.id">
              <el-select
                v-model="editingTagValues"
                multiple
                filterable
                allow-create
                default-first-option
                placeholder="选择或输入标签"
                size="small"
                class="tag-editor"
                @blur="saveTags(t)"
              >
                <el-option
                  v-for="tag in existingTags"
                  :key="tag.category + '::' + tag.name"
                  :label="tagLabel(tag)"
                  :value="tag.category + '::' + tag.name"
                />
              </el-select>
            </template>
            <template v-else>
              <el-tag
                v-for="(tag, ti) in t.tags"
                :key="ti"
                size="small"
                effect="plain"
              >{{ tagLabel(tag) }}</el-tag>
              <el-button
                text
                size="small"
                class="tag-edit-btn"
                @click.stop="startEditTags(t)"
              >
                <el-icon><Edit /></el-icon>
              </el-button>
            </template>
          </div>
          <div class="history-card__meta">
            <span>{{ t.quizQuestions?.length || 0 }} 道自测题</span>
            <span v-if="t.relatedTopicIds?.length">
              · 关联 {{ t.relatedTopicIds.length }} 个知识点
            </span>
            <span class="history-card__time">{{ t.createdAt }}</span>
          </div>
          <el-button
            text
            size="small"
            type="danger"
            class="history-card__delete"
            @click.stop="confirmDelete(t)"
          >
            <el-icon><Delete /></el-icon>
          </el-button>
        </div>
      </div>
    </div>

    <!-- 查看详情弹窗 -->
    <el-dialog
      v-model="detailVisible"
      :title="detailTopic?.title"
      width="700px"
      destroy-on-close
    >
      <div v-if="detailTopic">
        <div class="detail-tags">
          <el-tag
            v-for="(tag, i) in detailTopic.tags"
            :key="i"
            :type="tagType(tag.category)"
            effect="plain"
          >{{ tagLabel(tag) }}</el-tag>
        </div>
        <div class="detail-content" v-html="renderMarkdown(detailTopic.content)"></div>
        <div v-if="detailTopic.quizQuestions?.length > 0" class="detail-quiz">
          <h4>📝 自测题</h4>
          <div v-for="(q, qi) in detailTopic.quizQuestions" :key="qi" class="quiz-item">
            <p class="quiz-item__q">
              {{ qi + 1 }}. {{ q.question }}
            </p>
            <div class="quiz-item__options">
              <span
                v-for="(opt, oi) in q.options"
                :key="oi"
                class="quiz-option"
                :class="{ correct: oi === q.answer }"
              >
                {{ String.fromCharCode(65 + oi) }}. {{ opt }}
                <el-icon v-if="oi === q.answer" class="quiz-option__check"><Check /></el-icon>
              </span>
            </div>
          </div>
        </div>
        <!-- 关联知识点 -->
        <div v-if="detailRelatedTopics.length > 0" class="detail-related">
          <h4>🔗 关联知识点</h4>
          <div v-for="rt in detailRelatedTopics" :key="rt.id" class="related-item">
            <span class="related-item__title">{{ rt.title }}</span>
            <span class="related-item__tags-inline">
              <el-tag
                v-for="(tag, ti) in rt.tags"
                :key="ti"
                size="small"
              >{{ tagLabel(tag) }}</el-tag>
            </span>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, MagicStick, Check, Delete, Edit } from '@element-plus/icons-vue'
import {
  getTopics, createTopic, deleteTopic, getAllTags, updateTopic,
  aiGenerateTopic, aiGenerateQuiz, refreshTopicRelations, getTopic,
  syncTopicToTrees, removeTopicFromTrees, resolveTags, resyncAllTopics
} from './useKnowledgeStore'

// 输入状态
const topicTitle = ref('')
const selectedTags = ref([])
const aiGenerating = ref(false)
const aiStepIdx = ref(0)
const aiSteps = ['正在分析主题...', '生成知识点内容...', '分析标签关联...', '完成！']

// quiz 生成状态
const quizGenerating = ref(false)
const quizStepIdx = ref(0)
const quizSteps = ['正在分析知识点内容...', '生成自测题目...', '完成！']

// 卡片标签编辑状态
const editingTagsId = ref(null)
const editingTagValues = ref([])

function startEditTags(t) {
  editingTagsId.value = t.id
  editingTagValues.value = (t.tags || []).map((tg) => tg.category + '::' + tg.name)
}

function saveTags(t) {
  const tags = resolveTags(editingTagValues.value)
  updateTopic(t.id, { tags })
  syncTopicToTrees(getTopic(t.id))
  refreshSaved()
  editingTagsId.value = null
  ElMessage.success('标签已更新，知识树已同步')
}

// 生成结果
const generatedContent = ref('')
const generatedTags = ref([])
const generatedQuiz = ref([])
const relatedTopics = ref([])
const saved = ref(false)

// 详情弹窗
const detailVisible = ref(false)
const detailTopic = ref(null)

// 已有标签
const existingTags = computed(() => getAllTags())

// 已保存的知识点
const savedTopics = ref([])

function refreshSaved() {
  savedTopics.value = getTopics()
}

function resetForm() {
  topicTitle.value = ''
  selectedTags.value = []
  generatedContent.value = ''
  generatedTags.value = []
  generatedQuiz.value = []
  relatedTopics.value = []
  saved.value = false
  quizGenerating.value = false
}

async function handleGenerate() {
  if (!topicTitle.value.trim() || aiGenerating.value) return

  aiGenerating.value = true
  aiStepIdx.value = 0
  saved.value = false
  generatedContent.value = ''
  generatedTags.value = []
  generatedQuiz.value = []
  relatedTopics.value = []
  quizGenerating.value = false

  const result = await aiGenerateTopic(topicTitle.value.trim(), (step) => {
    aiStepIdx.value = aiSteps.indexOf(step)
  })

  generatedContent.value = result.content
  generatedTags.value = result.tags
  aiGenerating.value = false

  // 根据标签查找关联的已有 topic
  if (result.tags.length > 0 && savedTopics.value.length > 0) {
    const tagKeys = result.tags.map((t) => t.category + '::' + t.name)
    relatedTopics.value = savedTopics.value.filter((t) => {
      const otherKeys = (t.tags || []).map((tt) => tt.category + '::' + tt.name)
      return tagKeys.some((k) => otherKeys.includes(k))
    })
  }
}

async function handleGenerateQuiz() {
  if (!generatedContent.value || quizGenerating.value) return

  quizGenerating.value = true
  quizStepIdx.value = 0
  generatedQuiz.value = []

  const questions = await aiGenerateQuiz(
    topicTitle.value.trim(),
    generatedContent.value,
    (step) => { quizStepIdx.value = quizSteps.indexOf(step) }
  )

  generatedQuiz.value = questions
  quizGenerating.value = false
}

function saveTopic() {
  if (saved.value) return

  // 解析用户选择的标签
  const parsedTags = resolveTags(selectedTags.value)

  // 合并用户选择的标签和 AI 生成的标签（去重）
  const allTags = [...parsedTags]
  for (const tag of generatedTags.value) {
    const key = tag.category + '::' + tag.name
    if (!allTags.some((t) => (t.category + '::' + t.name) === key)) {
      allTags.push(tag)
    }
  }

  const topic = createTopic({
    title: topicTitle.value.trim(),
    content: generatedContent.value,
    tags: allTags,
    quizQuestions: generatedQuiz.value
  })

  refreshTopicRelations(topic.id)
  syncTopicToTrees(topic)

  saved.value = true
  refreshSaved()
  ElMessage.success('知识点已保存，已同步到知识树')
}

function viewTopic(t) {
  detailTopic.value = t
  detailVisible.value = true
}

const detailRelatedTopics = computed(() => {
  if (!detailTopic.value) return []
  return (detailTopic.value.relatedTopicIds || [])
    .map((id) => getTopic(id))
    .filter(Boolean)
})

function confirmDelete(t) {
  ElMessageBox.confirm(
    `确定删除「${t.title}」吗？`,
    '删除知识点',
    { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
  ).then(() => {
    removeTopicFromTrees(t.id)
    deleteTopic(t.id)
    refreshSaved()
    ElMessage.success('已删除')
  }).catch(() => {})
}

function handleResync() {
  resyncAllTopics()
  ElMessage.success('已重新同步所有知识点到知识树')
}

// 简单 Markdown 渲染
function renderMarkdown(text) {
  if (!text) return ''
  let html = text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
  // 标题
  html = html.replace(/^### (.+)$/gm, '<h4>$1</h4>')
  html = html.replace(/^## (.+)$/gm, '<h3>$1</h3>')
  // 加粗
  html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
  // 行内代码
  html = html.replace(/`([^`]+)`/g, '<code>$1</code>')
  // 列表
  html = html.replace(/^- (.+)$/gm, '<li>$1</li>')
  html = html.replace(/(<li>.*<\/li>\n?)+/g, '<ul>$&</ul>')
  // 引用
  html = html.replace(/^> (.+)$/gm, '<blockquote>$1</blockquote>')
  // 表格
  html = html.replace(/^\|(.+)\|$/gm, (line) => {
    const cells = line.split('|').filter((c) => c.trim())
    const isHeader = cells.some((c) => /^[-:]+$/.test(c.trim()))
    if (isHeader) return ''
    const tag = line.startsWith('|') && !html.includes('<thead>') ? 'thead' : 'tbody'
    return `<tr>${cells.map((c) => `<td>${c.trim()}</td>`).join('')}</tr>`
  })
  html = html.replace(/(<tr>.*<\/tr>\n?)+/g, '<table>$&</table>')
  // 换行
  html = html.replace(/\n\n/g, '</p><p>')
  html = html.replace(/\n/g, '<br>')
  html = '<p>' + html + '</p>'
  return html
}

const renderedContent = computed(() => renderMarkdown(generatedContent.value))

function tagType(category) {
  const map = {
    '计算机网络': '',
    '数据库': 'success',
    'Java': 'warning',
    '数据结构': 'danger',
    '后端': '',
    '前端': 'primary'
  }
  return map[category] || 'info'
}

function tagLabel(tag) {
  return tag.category === tag.name ? tag.name : tag.category + ' / ' + tag.name
}

onMounted(() => {
  refreshSaved()
  resyncAllTopics()
})
</script>

<style scoped>
.learn-topic-page {
  padding: 28px 32px;
  max-width: 900px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 24px;
}

.page-header h2 {
  margin: 0 0 6px 0;
  font-size: 22px;
  font-weight: 600;
}

.subtitle {
  color: #909399;
  font-size: 13px;
  margin: 0;
}

/* 输入区域 */
.input-card {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  padding: 24px;
  margin-bottom: 24px;
}

.tag-area {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 16px;
}

.tag-area__label {
  font-size: 13px;
  color: #606266;
  white-space: nowrap;
}

.tag-area__hint {
  font-size: 12px;
  color: #c0c4cc;
  margin-left: 8px;
  white-space: nowrap;
}

.generate-btn {
  margin-top: 20px;
  width: 100%;
}

/* 进度 */
.progress-card {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  padding: 40px 20px;
  text-align: center;
  margin-bottom: 24px;
}

.progress-card__icon {
  font-size: 56px;
  margin-bottom: 12px;
  animation: bounce 1s ease-in-out infinite;
}

@keyframes bounce {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-8px); }
}

.progress-card h3 {
  margin: 0 0 28px 0;
  font-size: 16px;
  color: #303133;
}

.progress-steps {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 0 60px;
}

.progress-step {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  color: #c0c4cc;
  transition: color 0.3s;
}

.progress-step.done { color: #67c23a; }
.progress-step.active { color: #409eff; }

.progress-step__dot {
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.progress-step.done .progress-step__dot { color: #67c23a; }

.progress-step__spinner {
  width: 14px;
  height: 14px;
  border: 2px solid #e4e7ed;
  border-top-color: #409eff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.progress-step__pending {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #e4e7ed;
}

/* 生成结果 */
.result-area {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  padding: 24px;
  margin-bottom: 32px;
}

.result-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.result-actions h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.result-actions__btns {
  display: flex;
  gap: 8px;
}

.result-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid #ebeef5;
}

/* 内容渲染 */
.result-content :deep(h3) {
  font-size: 17px;
  margin: 20px 0 10px;
  color: #303133;
}

.result-content :deep(h4) {
  font-size: 15px;
  margin: 16px 0 8px;
  color: #606266;
}

.result-content :deep(p) {
  line-height: 1.8;
  color: #606266;
  margin: 0 0 10px;
}

.result-content :deep(strong) {
  color: #303133;
}

.result-content :deep(code) {
  background: #f5f7fa;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 13px;
  color: #e6a23c;
}

.result-content :deep(ul) {
  margin: 8px 0;
  padding-left: 20px;
}

.result-content :deep(li) {
  line-height: 1.8;
  color: #606266;
}

.result-content :deep(blockquote) {
  margin: 8px 0;
  padding: 8px 16px;
  border-left: 3px solid #409eff;
  background: #ecf5ff;
  color: #606266;
  border-radius: 0 4px 4px 0;
}

.result-content :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 12px 0;
  font-size: 13px;
}

.result-content :deep(td) {
  padding: 6px 12px;
  border: 1px solid #ebeef5;
  color: #606266;
}

.result-content :deep(td:first-child) {
  font-weight: 600;
  color: #303133;
  white-space: nowrap;
}

/* 自测题 */
.result-quiz {
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid #ebeef5;
}

.result-quiz:has(.quiz-generate) {
  border-top: none;
  padding-top: 0;
}

.quiz-generate {
  text-align: center;
  padding: 16px 0;
}

.quiz-generate__hint {
  color: #909399;
  font-size: 13px;
  margin: 0 0 12px 0;
}

.quiz-progress {
  text-align: center;
  padding: 20px 0;
}

.quiz-progress__icon {
  font-size: 40px;
  margin-bottom: 8px;
  animation: bounce 1s ease-in-out infinite;
}

.quiz-progress p {
  margin: 0 0 12px 0;
  font-size: 14px;
  color: #303133;
}

.quiz-progress__steps {
  font-size: 13px;
  color: #c0c4cc;
}

.quiz-progress__step.done { color: #67c23a; }
.quiz-progress__step.active { color: #409eff; }

.result-quiz h4, .detail-quiz h4, .result-related h4, .detail-related h4 {
  margin: 0 0 16px 0;
  font-size: 15px;
  font-weight: 600;
}

.quiz-item {
  margin-bottom: 16px;
  padding: 12px;
  background: #fafafa;
  border-radius: 8px;
}

.quiz-item__q {
  margin: 0 0 10px 0;
  font-size: 14px;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.quiz-item__num {
  font-weight: 600;
  color: #409eff;
}

.quiz-item__options {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.quiz-option {
  font-size: 13px;
  color: #909399;
  padding: 4px 8px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.quiz-option.correct {
  color: #67c23a;
  font-weight: 500;
  background: #f0f9eb;
}

.quiz-option__check {
  font-size: 13px;
}

/* 关联知识点 */
.result-related {
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid #ebeef5;
}

.result-related__hint {
  color: #909399;
  font-size: 12px;
  margin: 0 0 12px 0;
}

.related-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.related-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 12px;
  background: #f5f7fa;
  border-radius: 6px;
}

.related-item__title {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
  white-space: nowrap;
}

.related-item__tags {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

/* 历史知识点 */
.history-section {
  margin-top: 8px;
}

.history-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}

.history-header h3 {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
}

.history-count {
  font-size: 12px;
  color: #909399;
}

.history-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px;
}

.history-card {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 16px;
  cursor: pointer;
  position: relative;
  transition: all 0.2s;
}

.history-card:hover {
  border-color: #c6d9f1;
  box-shadow: 0 2px 12px rgba(64, 158, 255, 0.08);
}

.history-card__title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
}

.history-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-bottom: 10px;
  align-items: center;
}

.tag-edit-btn {
  opacity: 0;
  transition: opacity 0.15s;
  padding: 0 4px !important;
  height: 22px;
}

.history-card:hover .tag-edit-btn {
  opacity: 1;
}

.tag-editor {
  width: 100%;
}

.history-card__meta {
  font-size: 12px;
  color: #c0c4cc;
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.history-card__time {
  margin-left: auto;
}

.history-card__delete {
  position: absolute;
  top: 8px;
  right: 8px;
  opacity: 0;
  transition: opacity 0.15s;
}

.history-card:hover .history-card__delete {
  opacity: 1;
}

/* 详情弹窗 */
.detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}

.detail-content {
  line-height: 1.8;
}

.detail-content :deep(h3) { font-size: 16px; margin: 16px 0 8px; }
.detail-content :deep(h4) { font-size: 14px; margin: 14px 0 6px; }
.detail-content :deep(p) { margin: 0 0 8px; }
.detail-content :deep(strong) { color: #303133; }
.detail-content :deep(code) { background: #f5f7fa; padding: 1px 5px; border-radius: 3px; font-size: 12px; color: #e6a23c; }
.detail-content :deep(li) { line-height: 1.8; }
.detail-content :deep(blockquote) {
  margin: 8px 0;
  padding: 6px 12px;
  border-left: 3px solid #409eff;
  background: #ecf5ff;
  border-radius: 0 4px 4px 0;
  font-size: 13px;
}
.detail-content :deep(table) { width: 100%; border-collapse: collapse; margin: 8px 0; font-size: 13px; }
.detail-content :deep(td) { padding: 5px 10px; border: 1px solid #ebeef5; }
.detail-content :deep(td:first-child) { font-weight: 600; white-space: nowrap; }

.detail-quiz, .detail-related {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #ebeef5;
}

.related-item__tags-inline {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}
</style>
