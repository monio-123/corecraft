import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { hasBodyMark } from './aiEdit'
import { topicHref } from '../../utils/categoryNav'
import { createTopic } from './useCorecraftWebStore'

/**
 * 详情页选区提问 → 写入正文 / 整篇替换 / 存为新知识点 + 对话微调链路。
 * 写回正文走 editor + 1.5s 防抖自动保存（单一保存路径）。
 */
export function useTopicInsert({ topic, editContent, editorRef, askOpen, quizOn, closeQuiz, submitAsk }) {
  const insertOpen = ref(false)
  const insertAnswer = ref('')
  const insertSpot = ref(null)
  const insertMode = ref('body')
  const insertBase = ref('')
  const insertSaving = ref(false)
  const insertAdjusting = ref(false)
  const insertAdjustTurn = ref(null)
  const insertAdjustError = ref('')

  const blocksInRange = (from, to) => editorRef.value?.blocksInRange(from, to) ?? []

  function buildSpotQuestion(text, supplement) {
    const segment = String(text || '').trim()
    const extra = String(supplement || '').trim()
    if (extra) return `正文里的这一段：\n\n${segment}\n\n${extra}`
    return `请解释正文里的这一段：\n\n${segment}`
  }

  function onAskAi(payload) {
    askOpen.value = true
    if (quizOn.value) closeQuiz()
    submitAsk('', {
      question: buildSpotQuestion(payload.text, payload.supplement),
      spot: {
        blockFrom: payload.blockFrom,
        blockTo: payload.blockTo,
        blockText: payload.blockText
      }
    })
  }

  function openInsert(turn, mode = 'body') {
    insertAnswer.value = turn.answer || ''
    insertSpot.value = turn.spot || null
    insertMode.value = mode
    insertBase.value = turn.bodyAtAsk || ''
    insertAdjustError.value = ''
    insertAdjustTurn.value = null
    insertOpen.value = true
  }

  async function createNewTopic(content, title) {
    insertSaving.value = true
    try {
      const created = await createTopic({
        title,
        content,
        categoryId: topic.value?.categoryId ?? null,
        tags: []
      })
      const spot = insertSpot.value
      if (spot && created?.id) {
        editorRef.value?.insertAfter(
          spot.blockFrom,
          spot.blockTo,
          `[${title}](${topicHref(created.id)})`
        )
      }
      ElMessage.success('已创建')
      insertOpen.value = false
    } finally {
      insertSaving.value = false
    }
  }

  function onInsertConfirm(payload = {}) {
    const { action } = payload
    const content = String(payload.content || '').trim()
    if (!content) {
      ElMessage.error('没写进去')
      return
    }
    if (action === 'topic') {
      createNewTopic(content, String(payload.title || '').trim())
      return
    }
    if (action === 'replace') {
      if (editContent.value !== insertBase.value) {
        ElMessage.error('正文已经改过，请重新问一次')
        return
      }
      if (!editorRef.value?.replaceContent(content)) {
        ElMessage.error('没写进去')
        return
      }
      insertOpen.value = false
      return
    }
    const spot = insertSpot.value
    if (!spot) return
    const now = blocksInRange(spot.blockFrom, spot.blockTo)
      .map((b) => b.node.textContent)
      .join('\n')
    if (now !== spot.blockText) {
      ElMessage.error('正文已经改过，请重新选中这一段')
      return
    }
    if (!editorRef.value?.insertAfter(spot.blockFrom, spot.blockTo, content)) {
      ElMessage.error('没写进去')
      return
    }
    insertOpen.value = false
  }

  function onInsertAdjust({ text, current }) {
    if (insertAdjusting.value) return
    const instruction = String(text || '').trim()
    if (!instruction) return
    insertAdjustError.value = ''
    const rewrite = hasBodyMark(insertAnswer.value)
    const started = submitAsk('', {
      question: rewrite
        ? instruction
        : `请按下面的要求重写上一条回答（保持原来的意思，只按要求调整，不要另起话题）：\n\n` +
          `${instruction}\n\n【当前这一版】\n${String(current || '').trim()}`,
      baseMd: rewrite ? String(current || '').trim() : undefined,
      spot: insertSpot.value,
      onStart: (turn) => {
        insertAdjustTurn.value = turn
      },
      onDone: (turn) => {
        insertAdjusting.value = false
        insertAdjustTurn.value = null
        if (turn.error) {
          insertAdjustError.value = turn.answer || '生成失败'
          return
        }
        insertAnswer.value = turn.answer
      }
    })
    insertAdjusting.value = started
    if (!started) {
      insertAdjustError.value = 'AI 还在生成'
    }
  }

  function resetInsertState() {
    insertOpen.value = false
    insertAdjustTurn.value = null
    insertAdjustError.value = ''
  }

  return {
    blocksInRange,
    onAskAi,
    insertOpen,
    insertAnswer,
    insertSpot,
    insertMode,
    insertBase,
    insertSaving,
    insertAdjusting,
    insertAdjustTurn,
    insertAdjustError,
    openInsert,
    onInsertConfirm,
    onInsertAdjust,
    resetInsertState
  }
}
