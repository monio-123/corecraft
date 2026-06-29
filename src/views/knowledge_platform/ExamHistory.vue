<template>
  <div class="history-page">
    <div class="page-header">
      <h2>考试历史</h2>
      <p class="subtitle">系统会记录你每次考试的完整信息，包括成绩、AI 反馈和你的笔记</p>
    </div>

    <div v-if="records.length" class="history-list">
      <div
        v-for="record in records"
        :key="record.id"
        class="history-card"
        :class="{ 'is-expanded': expandedId === record.id }"
      >
        <div class="history-card__summary" @click="toggleExpand(record.id)">
          <div class="history-card__info">
            <h3>{{ record.examTitle }}</h3>
            <div class="history-card__meta">
              <span>{{ record.date }}</span>
              <span>用时 {{ record.duration }}</span>
            </div>
          </div>
          <div class="history-card__score">
            <span class="score-value" :class="scoreClass(record)">{{ record.score }} / {{ record.totalScore }}</span>
            <span class="score-pct">{{ scorePercent(record) }}%</span>
          </div>
          <el-icon class="expand-icon" :class="{ rotated: expandedId === record.id }">
            <ArrowDown />
          </el-icon>
        </div>

        <div v-if="expandedId === record.id" class="history-card__detail">
          <el-divider />

          <!-- 每道题的回顾 -->
          <div v-for="(item, idx) in record.details" :key="idx" class="review-item">
            <div class="review-item__header">
              <span class="review-num">第 {{ idx + 1 }} 题</span>
              <el-tag size="small" :type="item.correct ? 'success' : 'danger'">
                {{ item.correct ? '正确' : '错误' }}
              </el-tag>
            </div>
            <p class="review-question">{{ item.question }}</p>
            <div class="review-answer">
              <span>你的答案：<strong>{{ item.userAnswer }}</strong></span>
              <span v-if="!item.correct" class="correct-answer">正确答案：{{ item.correctAnswer }}</span>
            </div>
            <div class="review-feedback">
              <span class="ai-label">🤖 AI反馈：</span>{{ item.aiComment }}
            </div>
            <div v-if="item.note" class="review-note">
              <span class="note-label">📝 你的笔记：</span>{{ item.note }}
            </div>
          </div>
        </div>
      </div>
    </div>

    <el-empty v-else description="暂无考试记录" />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ArrowDown } from '@element-plus/icons-vue'

const expandedId = ref(null)

// 模拟历史记录数据
const records = ref([
  {
    id: 1,
    examTitle: '数组与链表专项练习',
    date: '2026-06-10 14:30',
    duration: '18分钟',
    score: 80,
    totalScore: 100,
    details: [
      { question: '数组在内存中的存储方式是？', correct: true, userAnswer: 'B', correctAnswer: 'B', aiComment: '回答正确！你对这个知识点掌握得很好。', note: '' },
      { question: '在单链表中，已知某个节点的指针，在该节点之后插入一个新节点的时间复杂度是？', correct: true, userAnswer: 'A', correctAnswer: 'A', aiComment: '完全正确，理解很到位。', note: '' },
      { question: '以下哪些是数组的缺点？（多选）', correct: false, userAnswer: 'A, B', correctAnswer: 'A, B, D', aiComment: '漏选了"大小固定，扩容成本高"，这也是数组的重要缺点。', note: '数组扩容时需要重新分配更大的连续空间并复制数据，这也是重要缺点' },
      { question: '对于一个长度为 n 的数组，在末尾插入一个元素的平均时间复杂度是？', correct: true, userAnswer: 'A', correctAnswer: 'A', aiComment: '很好，这个知识点你已经掌握了。', note: '' },
      { question: '请简述数组和链表的主要区别，并说明各自适合的使用场景。', correct: true, userAnswer: '数组连续存储支持随机访问适合查询多；链表通过指针链接适合频繁插入删除', correctAnswer: '', aiComment: '回答要点齐全，总结到位。', note: '面试高频题，需要能流利答出' }
    ]
  },
  {
    id: 2,
    examTitle: 'TCP 协议深度测试',
    date: '2026-06-08 09:15',
    duration: '25分钟',
    score: 80,
    totalScore: 120,
    details: [
      { question: 'TCP 三次握手中，第二次握手服务器发送的报文标志位是？', correct: true, userAnswer: 'C', correctAnswer: 'C', aiComment: '回答正确！', note: '' },
      { question: '以下哪些是 TCP 提供的特性？（多选）', correct: false, userAnswer: 'A, B', correctAnswer: 'A, B, C', aiComment: '漏选"拥塞控制"，TCP 确实提供拥塞控制机制。', note: '拥塞控制是TCP的核心特性：慢启动、拥塞避免、快速重传、快速恢复' },
      { question: 'TIME_WAIT 状态的持续时间通常是？', correct: true, userAnswer: 'C', correctAnswer: 'C', aiComment: '完全正确。', note: '' }
    ]
  },
  {
    id: 3,
    examTitle: 'JVM 垃圾回收机制',
    date: '2026-06-05 16:45',
    duration: '12分钟',
    score: 60,
    totalScore: 60,
    details: [
      { question: 'JVM 判断对象是否可回收的主要方法是？', correct: true, userAnswer: 'B', correctAnswer: 'B', aiComment: '回答正确！', note: '' },
      { question: '请简述 CMS 收集器和 G1 收集器的主要区别。', correct: true, userAnswer: 'CMS基于标记清除有碎片停顿时间短；G1基于分区整体标记整理可预测停顿', correctAnswer: '', aiComment: '要点明确，表述清晰。', note: 'G1从JDK9开始成为默认收集器' }
    ]
  }
])

function toggleExpand(id) {
  expandedId.value = expandedId.value === id ? null : id
}

function scorePercent(record) {
  return Math.round((record.score / record.totalScore) * 100)
}

function scoreClass(record) {
  const pct = scorePercent(record)
  if (pct >= 80) return 'score-good'
  if (pct >= 60) return 'score-mid'
  return 'score-bad'
}
</script>

<style scoped>
.history-page {
  padding: 24px;
  max-width: 900px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 24px;
}

.page-header h2 {
  margin: 0 0 8px 0;
  font-size: 22px;
}

.subtitle {
  color: #909399;
  font-size: 14px;
  margin: 0;
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.history-card {
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  background: #fff;
  overflow: hidden;
}

.history-card__summary {
  display: flex;
  align-items: center;
  padding: 16px 20px;
  cursor: pointer;
  transition: background 0.15s;
}

.history-card__summary:hover {
  background: #f5f7fa;
}

.history-card__info {
  flex: 1;
}

.history-card__info h3 {
  margin: 0 0 4px 0;
  font-size: 15px;
}

.history-card__meta {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #909399;
}

.history-card__score {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-right: 16px;
}

.score-value {
  font-size: 18px;
  font-weight: 700;
}

.score-good { color: #67c23a; }
.score-mid { color: #e6a23c; }
.score-bad { color: #f56c6c; }

.score-pct {
  font-size: 13px;
  color: #909399;
}

.expand-icon {
  transition: transform 0.2s;
  color: #909399;
}

.expand-icon.rotated {
  transform: rotate(180deg);
}

.history-card__detail {
  padding: 0 20px 16px;
}

.review-item {
  padding: 14px 0;
  border-bottom: 1px dashed #ebeef5;
}

.review-item:last-child {
  border-bottom: none;
}

.review-item__header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}

.review-num {
  font-weight: 600;
  font-size: 13px;
}

.review-question {
  margin: 0 0 8px 0;
  font-size: 14px;
}

.review-answer {
  display: flex;
  gap: 20px;
  font-size: 13px;
  color: #606266;
  margin-bottom: 8px;
}

.correct-answer {
  color: #67c23a;
}

.review-feedback {
  background: #fdf6ec;
  padding: 8px 12px;
  border-radius: 4px;
  font-size: 13px;
  line-height: 1.6;
  margin-bottom: 8px;
}

.ai-label {
  font-weight: 600;
}

.review-note {
  background: #ecf5ff;
  padding: 8px 12px;
  border-radius: 4px;
  font-size: 13px;
  color: #409eff;
  line-height: 1.6;
}

.note-label {
  font-weight: 600;
}
</style>
