<template>
  <div class="exam-take-page">
    <!-- 未开始 / 作答中 -->
    <template v-if="!submitted">
      <div class="exam-header">
        <div>
          <h2>{{ exam.title }}</h2>
          <p class="exam-subtitle">{{ exam.description }}</p>
        </div>
        <div class="exam-header__right">
          <span class="timer">⏱ {{ formattedTime }}</span>
          <el-button type="primary" @click="submitExam">提交 &amp; AI 评分</el-button>
        </div>
      </div>

      <div class="exam-body">
        <div
          v-for="(q, idx) in exam.questions"
          :key="q.id"
          class="question-card"
        >
          <div class="question-header">
            <span class="question-num">第 {{ idx + 1 }} 题</span>
            <el-tag size="small" :type="q.type === 'single' ? '' : q.type === 'multi' ? 'warning' : 'info'">
              {{ q.type === 'single' ? '单选题' : q.type === 'multi' ? '多选题' : '简答题' }}
            </el-tag>
          </div>
          <p class="question-text">{{ q.text }}</p>

          <template v-if="q.type === 'single'">
            <el-radio-group v-model="answers[q.id]" class="option-group">
              <el-radio v-for="opt in q.options" :key="opt.key" :value="opt.key" class="option-item">
                {{ opt.key }}. {{ opt.text }}
              </el-radio>
            </el-radio-group>
          </template>

          <template v-else-if="q.type === 'multi'">
            <el-checkbox-group v-model="answers[q.id]" class="option-group">
              <el-checkbox v-for="opt in q.options" :key="opt.key" :value="opt.key" class="option-item">
                {{ opt.key }}. {{ opt.text }}
              </el-checkbox>
            </el-checkbox-group>
          </template>

          <template v-else>
            <el-input
              v-model="answers[q.id]"
              type="textarea"
              :rows="3"
              placeholder="请输入你的答案..."
            />
          </template>
        </div>
      </div>
    </template>

    <!-- 已提交：AI 评分结果 -->
    <template v-else>
      <div class="exam-header result-header">
        <div>
          <h2>{{ exam.title }} — 成绩报告</h2>
          <p class="exam-subtitle">AI 已完成评分，请查看每道题的反馈</p>
        </div>
        <div class="score-display">
          <span class="score-num">{{ score }}</span>
          <span class="score-total"> / {{ exam.totalScore }}</span>
        </div>
      </div>

      <div class="exam-body">
        <div
          v-for="(q, idx) in exam.questions"
          :key="q.id"
          class="question-card result-card"
          :class="{ 'is-correct': feedback[q.id]?.correct, 'is-wrong': !feedback[q.id]?.correct }"
        >
          <div class="question-header">
            <span class="question-num">第 {{ idx + 1 }} 题</span>
            <el-tag size="small" :type="feedback[q.id]?.correct ? 'success' : 'danger'">
              {{ feedback[q.id]?.correct ? '正确' : '错误' }}
            </el-tag>
          </div>
          <p class="question-text">{{ q.text }}</p>

          <div class="answer-review">
            <p><strong>你的答案：</strong>{{ formatAnswer(answers[q.id]) }}</p>
            <p v-if="q.type !== 'text'"><strong>正确答案：</strong>{{ formatAnswer(q.answer) }}</p>
          </div>

          <div class="ai-feedback">
            <div class="ai-feedback__header">
              <span>🤖 AI 点评</span>
            </div>
            <p>{{ feedback[q.id]?.comment }}</p>
          </div>

          <div class="user-note">
            <el-input
              v-model="notes[q.id]"
              type="textarea"
              :rows="2"
              placeholder="添加你的笔记（错题原因、知识点总结等）..."
            />
            <el-button size="small" type="primary" text @click="saveNote(q.id)" style="margin-top: 8px">
              保存笔记
            </el-button>
            <span v-if="noteSaved[q.id]" class="note-saved-hint">已保存</span>
          </div>
        </div>
      </div>

      <div class="exam-footer">
        <el-button @click="router.push({ name: 'Exams' })">返回试卷列表</el-button>
        <el-button type="primary" @click="router.push({ name: 'ExamHistory' })">查看历史记录</el-button>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const submitted = ref(false)
const score = ref(0)
const answers = reactive({})
const feedback = reactive({})
const notes = reactive({})
const noteSaved = reactive({})

const timer = ref(0)
let timerInterval = null

// 模拟试卷数据
const examData = {
  1: {
    id: 1,
    title: '数组与链表专项练习',
    description: '涵盖数组随机访问、链表插入删除、性能对比的核心题目',
    totalScore: 100,
    questions: [
      {
        id: 'q1', type: 'single', text: '数组在内存中的存储方式是？',
        options: [
          { key: 'A', text: '分散存储，通过指针连接' },
          { key: 'B', text: '连续存储' },
          { key: 'C', text: '以哈希表方式存储' },
          { key: 'D', text: '以树形结构存储' }
        ],
        answer: 'B'
      },
      {
        id: 'q2', type: 'single', text: '在单链表中，已知某个节点的指针，在该节点之后插入一个新节点的时间复杂度是？',
        options: [
          { key: 'A', text: 'O(1)' },
          { key: 'B', text: 'O(n)' },
          { key: 'C', text: 'O(log n)' },
          { key: 'D', text: 'O(n²)' }
        ],
        answer: 'A'
      },
      {
        id: 'q3', type: 'multi', text: '以下哪些是数组的缺点？（多选）',
        options: [
          { key: 'A', text: '插入和删除需要移动大量元素' },
          { key: 'B', text: '需要连续的内存空间' },
          { key: 'C', text: '不支持随机访问' },
          { key: 'D', text: '大小固定，扩容成本高' }
        ],
        answer: ['A', 'B', 'D']
      },
      {
        id: 'q4', type: 'single', text: '对于一个长度为 n 的数组，在末尾插入一个元素的平均时间复杂度是？',
        options: [
          { key: 'A', text: 'O(1) 均摊' },
          { key: 'B', text: 'O(n)' },
          { key: 'C', text: 'O(log n)' },
          { key: 'D', text: 'O(n log n)' }
        ],
        answer: 'A'
      },
      {
        id: 'q5', type: 'text', text: '请简述数组和链表的主要区别，并说明各自适合的使用场景。',
        options: [],
        answer: '数组连续存储，支持O(1)随机访问，适合读多写少；链表通过指针连接，插入删除O(1)，适合频繁增删。'
      }
    ]
  },
  2: {
    id: 2,
    title: '二叉树与遍历算法',
    description: '二叉树的四种遍历方式、BST 特性、以及常见树的变体',
    totalScore: 80,
    questions: [
      {
        id: 'q1', type: 'single', text: '二叉搜索树（BST）的中序遍历结果是？',
        options: [
          { key: 'A', text: '无序序列' },
          { key: 'B', text: '递减序列' },
          { key: 'C', text: '递增序列' },
          { key: 'D', text: '取决于树的形状' }
        ],
        answer: 'C'
      },
      {
        id: 'q2', type: 'single', text: '一棵完全二叉树有 1000 个节点，其高度大约是？',
        options: [
          { key: 'A', text: '10' },
          { key: 'B', text: '20' },
          { key: 'C', text: '100' },
          { key: 'D', text: '1000' }
        ],
        answer: 'A'
      },
      {
        id: 'q3', type: 'text', text: '请描述前序遍历、中序遍历、后序遍历的区别，并给出一个简单的例子。',
        options: [],
        answer: '前序(根左右)、中序(左根右)、后序(左右根)。以根为A、左B、右C为例：前序ABC，中序BAC，后序BCA。'
      }
    ]
  },
  3: {
    id: 3,
    title: 'TCP 协议深度测试',
    description: '聚焦 TCP 三次握手/四次挥手、拥塞控制等核心机制',
    totalScore: 120,
    questions: [
      {
        id: 'q1', type: 'single', text: 'TCP 三次握手中，第二次握手服务器发送的报文标志位是？',
        options: [
          { key: 'A', text: 'SYN' },
          { key: 'B', text: 'ACK' },
          { key: 'C', text: 'SYN+ACK' },
          { key: 'D', text: 'FIN+ACK' }
        ],
        answer: 'C'
      },
      {
        id: 'q2', type: 'multi', text: '以下哪些是 TCP 提供的特性？（多选）',
        options: [
          { key: 'A', text: '可靠传输' },
          { key: 'B', text: '流量控制' },
          { key: 'C', text: '拥塞控制' },
          { key: 'D', text: '多播支持' }
        ],
        answer: ['A', 'B', 'C']
      },
      {
        id: 'q3', type: 'single', text: 'TIME_WAIT 状态的持续时间通常是？',
        options: [
          { key: 'A', text: '1 秒' },
          { key: 'B', text: '30 秒' },
          { key: 'C', text: '2MSL（约 60 秒）' },
          { key: 'D', text: '永久保持' }
        ],
        answer: 'C'
      }
    ]
  },
  4: {
    id: 4,
    title: 'JVM 垃圾回收机制',
    description: 'GC 算法、分代收集、常用收集器的原理与对比',
    totalScore: 60,
    questions: [
      {
        id: 'q1', type: 'single', text: 'JVM 判断对象是否可回收的主要方法是？',
        options: [
          { key: 'A', text: '引用计数法' },
          { key: 'B', text: '可达性分析' },
          { key: 'C', text: '标记-清除' },
          { key: 'D', text: '时间戳判断' }
        ],
        answer: 'B'
      },
      {
        id: 'q2', type: 'text', text: '请简述 CMS 收集器和 G1 收集器的主要区别。',
        options: [],
        answer: 'CMS以最短停顿为目标，基于标记-清除，会产生碎片，需要Serial Old兜底；G1将堆划分为Region，可预测停顿时间，整体基于标记-整理。'
      }
    ]
  }
}

const exam = computed(() => examData[route.params.id] || { title: '未知试卷', description: '', questions: [], totalScore: 0 })

const formattedTime = computed(() => {
  const m = Math.floor(timer.value / 60)
  const s = timer.value % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
})

onMounted(() => {
  timerInterval = setInterval(() => { timer.value++ }, 1000)
  // 初始化答案
  exam.value.questions.forEach(q => {
    if (!answers[q.id]) {
      answers[q.id] = q.type === 'multi' ? [] : ''
    }
  })
})

onBeforeUnmount(() => {
  clearInterval(timerInterval)
})

function formatAnswer(val) {
  if (Array.isArray(val)) return val.join(', ')
  return val || '（未作答）'
}

// 模拟 AI 评分
function submitExam() {
  submitted.value = true
  clearInterval(timerInterval)

  let total = 0
  const perQuestionScore = Math.floor(exam.value.totalScore / exam.value.questions.length)

  exam.value.questions.forEach((q, idx) => {
    let correct = false
    const userAnswer = answers[q.id]

    if (q.type === 'multi') {
      const userSet = new Set(userAnswer || [])
      const correctSet = new Set(q.answer)
      correct = userSet.size === correctSet.size && [...userSet].every(v => correctSet.has(v))
    } else if (q.type === 'text') {
      // 简答题：mock AI 判断（演示用，总是算对一半）
      correct = (userAnswer || '').length > 5
    } else {
      correct = userAnswer === q.answer
    }

    if (correct) total += perQuestionScore

    // 模拟 AI 生成的点评
    const comments = {
      correct: [
        '回答正确！你对这个知识点掌握得很好。',
        '完全正确，理解很到位。',
        '很好，这个知识点你已经掌握了。'
      ],
      wrong: [
        `正确答案是 ${formatAnswer(q.answer)}，建议回顾相关知识点。`,
        `这道题答错了，${q.type === 'text' ? '请参考标准答案' : '正确答案是 ' + formatAnswer(q.answer)}，可以复习一下相关内容。`,
        '回答不正确，建议重新学习该知识点后再来尝试。'
      ]
    }

    const pool = correct ? comments.correct : comments.wrong
    feedback[q.id] = {
      correct,
      comment: pool[idx % pool.length]
    }
  })

  score.value = total
  ElMessage.success(`AI 评分完成！你的得分是 ${total} / ${exam.value.totalScore}`)
}

function saveNote(qId) {
  noteSaved[qId] = true
  setTimeout(() => { noteSaved[qId] = false }, 2000)
  ElMessage.success('笔记已保存')
}
</script>

<style scoped>
.exam-take-page {
  padding: 24px;
  max-width: 900px;
  margin: 0 auto;
}

.exam-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid #e4e7ed;
}

.exam-header h2 {
  margin: 0 0 6px 0;
  font-size: 20px;
}

.exam-subtitle {
  color: #909399;
  font-size: 13px;
  margin: 0;
}

.exam-header__right {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-shrink: 0;
}

.timer {
  font-size: 18px;
  font-weight: 600;
  color: #409eff;
  font-variant-numeric: tabular-nums;
}

.exam-body {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.question-card {
  padding: 20px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  background: #fff;
}

.question-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.question-num {
  font-weight: 600;
  color: #303133;
}

.question-text {
  margin: 0 0 16px 0;
  font-size: 15px;
  line-height: 1.6;
}

.option-group {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.option-item {
  margin: 0;
}

.result-header {
  flex-wrap: wrap;
}

.score-display {
  flex-shrink: 0;
  text-align: right;
}

.score-num {
  font-size: 36px;
  font-weight: 700;
  color: #409eff;
}

.score-total {
  font-size: 18px;
  color: #909399;
}

.result-card {
  border-left: 4px solid #e4e7ed;
}

.result-card.is-correct {
  border-left-color: #67c23a;
}

.result-card.is-wrong {
  border-left-color: #f56c6c;
}

.answer-review {
  background: #f5f7fa;
  padding: 12px 16px;
  border-radius: 6px;
  margin-bottom: 12px;
  font-size: 13px;
  line-height: 1.8;
}

.answer-review p {
  margin: 0;
}

.ai-feedback {
  background: #fdf6ec;
  padding: 12px 16px;
  border-radius: 6px;
  margin-bottom: 12px;
  font-size: 13px;
}

.ai-feedback__header {
  font-weight: 600;
  margin-bottom: 6px;
}

.ai-feedback p {
  margin: 0;
  line-height: 1.6;
}

.user-note {
  margin-top: 8px;
}

.note-saved-hint {
  color: #67c23a;
  font-size: 12px;
  margin-left: 8px;
}

.exam-footer {
  margin-top: 24px;
  display: flex;
  gap: 12px;
  justify-content: center;
}
</style>
