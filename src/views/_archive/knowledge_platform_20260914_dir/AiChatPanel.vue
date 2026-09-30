<template>
  <div class="ai-chat-panel">
    <!-- 头部 -->
    <div class="ai-chat-header">
      <div class="ai-chat-header__title">
        <span class="ai-icon">🤖</span>
        <span>AI 导师</span>
      </div>
      <el-button v-if="messages.length" text size="small" type="danger" @click="clearChat">清空对话</el-button>
    </div>

    <!-- 上下文卡片 -->
    <div class="context-card" :class="{ collapsed: contextCollapsed }">
      <div class="context-card__header" @click="contextCollapsed = !contextCollapsed">
        <span class="context-card__label">📋 AI 可见的上下文</span>
        <el-icon class="context-card__toggle" :class="{ rotated: !contextCollapsed }"><ArrowDown /></el-icon>
      </div>
      <div v-show="!contextCollapsed" class="context-card__body">
        <div class="context-item">
          <span class="context-item__key">知识树</span>
          <span class="context-item__val">{{ treeName }}</span>
        </div>
        <div class="context-item" v-if="breadcrumb.length">
          <span class="context-item__key">路径</span>
          <span class="context-item__val">{{ breadcrumb.join(' › ') }}</span>
        </div>
        <div class="context-item">
          <span class="context-item__key">当前节点</span>
          <span class="context-item__val">{{ node.title }}</span>
        </div>
        <div class="context-item" v-if="node.content">
          <span class="context-item__key">节点内容</span>
          <span class="context-item__val context-item__val--content">{{ truncatedContent }}</span>
        </div>
        <div class="context-item" v-if="relatedNodes.length">
          <span class="context-item__key">关联节点</span>
          <span class="context-item__val">
            <el-tag
              v-for="r in relatedNodes"
              :key="r.id"
              size="small"
              class="context-tag"
              @click="$emit('jumpToNode', r.id)"
            >{{ r.title }}</el-tag>
          </span>
        </div>
        <div class="context-item" v-if="examSummary">
          <span class="context-item__key">考试记录</span>
          <span class="context-item__val">{{ examSummary }}</span>
        </div>
      </div>
    </div>

    <!-- 快捷提问（无对话时显示） -->
    <div v-if="messages.length === 0" class="quick-actions">
      <button
        v-for="qa in quickActions"
        :key="qa.label"
        class="quick-action-btn"
        @click="sendQuick(qa.prompt)"
      >
        <span class="quick-action__icon">{{ qa.icon }}</span>
        <span>{{ qa.label }}</span>
      </button>
    </div>

    <!-- 对话消息流 -->
    <div ref="msgListRef" class="chat-messages">
      <div v-if="messages.length === 0" class="chat-empty">
        <p class="chat-empty__icon">💬</p>
        <p>选择一个快捷提问开始对话，或直接输入你的问题</p>
        <p class="chat-empty__hint">AI 能根据当前知识点的内容、关联节点和你的考试记录来回答问题</p>
      </div>

      <div
        v-for="msg in messages"
        :key="msg.id"
        class="chat-msg"
        :class="'chat-msg--' + msg.role"
      >
        <div class="chat-msg__avatar">
          {{ msg.role === 'assistant' ? '🤖' : '👤' }}
        </div>
        <div class="chat-msg__body">
          <div class="chat-msg__text">{{ msg.content }}</div>
          <div v-if="msg.actions" class="chat-msg__actions">
            <el-button
              v-for="act in msg.actions"
              :key="act.label"
              size="small"
              text
              type="primary"
              @click="act.handler"
            >{{ act.label }}</el-button>
          </div>
        </div>
      </div>

      <!-- AI 思考中 -->
      <div v-if="thinking" class="chat-msg chat-msg--assistant">
        <div class="chat-msg__avatar">🤖</div>
        <div class="chat-msg__body">
          <div class="thinking-dots">
            <span></span><span></span><span></span>
          </div>
        </div>
      </div>
    </div>

    <!-- 输入区 -->
    <div class="chat-input-area">
      <el-input
        v-model="inputText"
        type="textarea"
        :rows="2"
        placeholder="向 AI 提问 ..."
        resize="none"
        @keydown.enter.exact="sendMessage"
      />
      <el-button
        type="primary"
        :disabled="!inputText.trim() || thinking"
        :loading="thinking"
        @click="sendMessage"
        class="send-btn"
      >
        <el-icon><Promotion /></el-icon>
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import { ArrowDown, Promotion } from '@element-plus/icons-vue'
import { getChatMessages, addChatMessage, clearChatMessages, getNodes, RELATION_TYPES } from './useKnowledgeStore'

const props = defineProps({
  node: { type: Object, default: null },
  treeName: { type: String, default: '' },
  breadcrumb: { type: Array, default: () => [] },
  relatedNodes: { type: Array, default: () => [] },
  examSummary: { type: String, default: '' }
})

const emit = defineEmits(['jumpToNode', 'addRelation'])

const inputText = ref('')
const thinking = ref(false)
const contextCollapsed = ref(true)
const msgListRef = ref(null)

const allNodes = computed(() => getNodes(props.node?.treeId || ''))

const messages = computed(() => {
  if (!props.node?.id) return []
  return getChatMessages(props.node.id)
})

const truncatedContent = computed(() => {
  const c = props.node?.content || ''
  return c.length > 80 ? c.slice(0, 80) + '...' : c
})

const quickActions = [
  { icon: '💡', label: '用更简单的话解释这个概念', prompt: '请用更简单、更通俗的语言解释一下' },
  { icon: '📝', label: '给我一个实际例子', prompt: '请给我一个生活中的实际例子来帮助理解' },
  { icon: '🔗', label: '和哪些知识点有关联？', prompt: '这个知识点和知识树中哪些其他知识点有关联？请分析一下' },
  { icon: '📋', label: '帮我出几道题测试一下', prompt: '请根据这个知识点的内容出2-3道选择题来测试我的理解' }
]

// 滚动到底部
function scrollToBottom() {
  nextTick(() => {
    if (msgListRef.value) {
      msgListRef.value.scrollTop = msgListRef.value.scrollHeight
    }
  })
}

watch(messages, scrollToBottom, { deep: true })

// Mock AI 回复逻辑
function mockAiReply(userMsg) {
  const content = props.node?.content || ''
  const title = props.node?.title || ''
  const relList = props.relatedNodes || []
  const q = userMsg.toLowerCase()

  // 解释类
  if (q.includes('解释') || q.includes('是什么') || q.includes('什么意思') || q.includes('通俗')) {
    return {
      content: `好的，我试着用更通俗的语言解释「${title}」：\n\n${content || '（节点内容为空，建议你先完善这个知识点的内容）'}\n\n简单来说，这是一个基础概念，理解它对后续学习很重要。有什么具体不理解的地方可以继续问我。`
    }
  }

  // 例子类
  if (q.includes('例子') || q.includes('举例') || q.includes('场景') || q.includes('实际')) {
    return {
      content: `关于「${title}」的实际例子：\n\n想象你正在${title.includes('数组') ? '整理一排连续编号的储物柜，每个柜子可以放一个物品，你可以瞬间找到第N号柜子里的内容——这就是数组的随机访问特性' : title.includes('链表') ? '玩一个寻宝游戏，每个线索卡上写着下一个线索的位置，你只能按顺序一张张找——这就是链表的遍历方式' : '学习这个概念时，可以联想日常生活或工作中的类似场景，这样更容易理解'}\n\n需要更详细的说明吗？`
    }
  }

  // 关联类
  if (q.includes('关联') || q.includes('相关') || q.includes('关系') || q.includes('联系')) {
    if (relList.length === 0) {
      return {
        content: `目前「${title}」还没有设置关联知识点。\n\n建议你可以添加一些关联，比如：\n- 前置知识（学习这个前需要先掌握什么）\n- 扩展知识（学完这个后可以进一步了解什么）\n- 对比概念（和哪个概念容易混淆，区别是什么）\n\n要我帮你分析一下可以关联哪些节点吗？`,
        actions: [{ label: '帮我分析可关联的节点', handler: () => suggestRelations() }]
      }
    }
    const relDesc = relList.map(r => `- **${r.title}**（${r.relationType === 'prerequisite' ? '前置知识' : r.relationType === 'extends' ? '扩展' : r.relationType === 'contrast' ? '对比' : '相关'}）`).join('\n')
    return {
      content: `「${title}」关联了以下知识点：\n\n${relDesc}\n\n这些关联帮助你建立知识网络，点击关联标签可以跳转到对应知识点。`
    }
  }

  // 出题类
  if (q.includes('出题') || q.includes('测试') || q.includes('考试') || q.includes('题目')) {
    const topic = content || title
    return {
      content: `根据「${title}」的内容，我为你出了几道题：\n\n**选择题 1**：关于${title}，以下说法正确的是？\nA. （需要你补充节点内容后我能生成更精准的题目）\nB. ...\n\n💡 **提示**：完善这个知识点的内容后，我可以生成更贴合实际内容的测试题。`,
      actions: [{ label: '去考试中心做更多练习', handler: () => emit('jumpToNode', '__exams__') }]
    }
  }

  // 建议关联
  if (q.includes('建议') || q.includes('分析') || q.includes('推荐')) {
    return suggestRelationsResponse()
  }

  // 默认
  return {
    content: `关于「${title}」，我的理解是：\n\n${content ? content + '\n\n' : ''}你可以继续问我：\n- 用更简单的话解释\n- 给我一个实际例子\n- 和哪些知识点有关联\n- 帮我出几道题测试一下`
  }
}

function suggestRelations() {
  const response = suggestRelationsResponse()
  addChatMessage(props.node.id, { role: 'assistant', content: response.content, actions: response.actions || [] })
}

function suggestRelationsResponse() {
  const title = props.node?.title || ''
  const content = props.node?.content || ''
  const existingIds = new Set((props.relatedNodes || []).map(r => r.id))
  const candidates = allNodes.value
    .filter(n => n.id !== props.node?.id && !existingIds.has(n.id))
    .slice(0, 5)

  if (candidates.length === 0) {
    return { content: '当前知识树中暂时没有其他可关联的节点。你可以先创建更多知识点再来分析关联。' }
  }

  const candidateList = candidates.map(n => `- **${n.title}**`).join('\n')
  return {
    content: `基于「${title}」的内容，我推荐以下可能有价值的关联：\n\n${candidateList}\n\n你可以在右侧面板中手动添加这些关联。`,
    actions: candidates.slice(0, 3).map(n => ({
      label: `关联「${n.title}」`,
      handler: () => emit('addRelation', n.id)
    }))
  }
}

function sendQuick(prompt) {
  inputText.value = prompt
  sendMessage()
}

async function sendMessage() {
  const text = inputText.value.trim()
  if (!text || thinking.value) return
  if (!props.node?.id) return

  inputText.value = ''

  // 添加用户消息
  addChatMessage(props.node.id, { role: 'user', content: text })

  // 模拟 AI 思考
  thinking.value = true
  await new Promise(r => setTimeout(r, 800 + Math.random() * 700))

  // 生成 Mock 回复
  const reply = mockAiReply(text)
  addChatMessage(props.node.id, { role: 'assistant', content: reply.content, actions: reply.actions || [] })
  thinking.value = false
}

function clearChat() {
  if (props.node?.id) {
    clearChatMessages(props.node.id)
  }
}
</script>

<style scoped>
.ai-chat-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #fafbfc;
  border-left: 1px solid #ebeef5;
}

/* header */
.ai-chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid #ebeef5;
  background: #fff;
  flex-shrink: 0;
}

.ai-chat-header__title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
}

.ai-icon { font-size: 18px; }

/* context card */
.context-card {
  border-bottom: 1px solid #ebeef5;
  background: #fff;
  flex-shrink: 0;
}

.context-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  cursor: pointer;
  user-select: none;
}

.context-card__label {
  font-size: 12px;
  color: #909399;
  font-weight: 500;
}

.context-card__toggle {
  font-size: 12px;
  color: #c0c4cc;
  transition: transform 0.2s;
}

.context-card__toggle.rotated {
  transform: rotate(180deg);
}

.context-card__body {
  padding: 0 16px 12px;
}

.context-item {
  display: flex;
  gap: 8px;
  margin-bottom: 6px;
  font-size: 12px;
  line-height: 1.6;
}

.context-item__key {
  color: #909399;
  flex-shrink: 0;
  min-width: 52px;
}

.context-item__val {
  color: #303133;
}

.context-item__val--content {
  color: #606266;
  font-style: italic;
}

.context-tag {
  cursor: pointer;
  margin-right: 4px;
}

/* quick actions */
.quick-actions {
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex-shrink: 0;
  border-bottom: 1px solid #ebeef5;
  background: #fff;
}

.quick-action-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 12px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  background: #fff;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
  text-align: left;
  color: #606266;
}

.quick-action-btn:hover {
  border-color: #409eff;
  background: #ecf5ff;
  color: #409eff;
}

.quick-action__icon {
  flex-shrink: 0;
  font-size: 15px;
}

/* messages */
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

.chat-empty {
  text-align: center;
  padding: 40px 16px;
  color: #909399;
}

.chat-empty__icon {
  font-size: 32px;
  margin-bottom: 10px;
}

.chat-empty p {
  margin: 0 0 4px 0;
  font-size: 13px;
}

.chat-empty__hint {
  font-size: 11px !important;
  color: #c0c4cc;
}

.chat-msg {
  display: flex;
  gap: 10px;
  margin-bottom: 18px;
}

.chat-msg--user {
  flex-direction: row-reverse;
}

.chat-msg__avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  flex-shrink: 0;
  background: #f0f2f5;
}

.chat-msg--assistant .chat-msg__avatar {
  background: #ecf5ff;
}

.chat-msg__body {
  max-width: 80%;
}

.chat-msg--user .chat-msg__body {
  text-align: right;
}

.chat-msg__text {
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.chat-msg--assistant .chat-msg__text {
  background: #fff;
  border: 1px solid #ebeef5;
  border-bottom-left-radius: 4px;
  color: #303133;
}

.chat-msg--user .chat-msg__text {
  background: #409eff;
  color: #fff;
  border-bottom-right-radius: 4px;
}

.chat-msg__actions {
  margin-top: 6px;
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

/* thinking */
.thinking-dots {
  display: flex;
  gap: 4px;
  padding: 14px 16px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 12px;
  border-bottom-left-radius: 4px;
  width: fit-content;
}

.thinking-dots span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #c0c4cc;
  animation: dot-bounce 1.4s ease-in-out infinite both;
}

.thinking-dots span:nth-child(1) { animation-delay: 0s; }
.thinking-dots span:nth-child(2) { animation-delay: 0.2s; }
.thinking-dots span:nth-child(3) { animation-delay: 0.4s; }

@keyframes dot-bounce {
  0%, 80%, 100% { transform: translateY(0); }
  40% { transform: translateY(-6px); }
}

/* input */
.chat-input-area {
  padding: 12px 14px;
  border-top: 1px solid #ebeef5;
  display: flex;
  gap: 8px;
  align-items: flex-end;
  background: #fff;
  flex-shrink: 0;
}

.send-btn {
  flex-shrink: 0;
  height: 40px;
}
</style>
