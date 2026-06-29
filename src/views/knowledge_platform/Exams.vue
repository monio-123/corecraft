<template>
  <div class="exams-page">
    <div class="page-header">
      <h2>试卷中心</h2>
      <p class="subtitle">试卷由 AI 根据知识树自动生成，完成后 AI 会自动评分并给出反馈</p>
    </div>

    <div class="exam-list">
      <div v-for="exam in exams" :key="exam.id" class="exam-card">
        <div class="exam-card__main">
          <div class="exam-card__info">
            <h3>{{ exam.title }}</h3>
            <p class="exam-card__desc">{{ exam.description }}</p>
            <div class="exam-card__tags">
              <el-tag size="small">{{ exam.topic }}</el-tag>
              <el-tag size="small" type="warning">{{ exam.questionCount }} 题</el-tag>
              <el-tag size="small" :type="exam.difficulty === '困难' ? 'danger' : exam.difficulty === '中等' ? 'warning' : 'success'">
                {{ exam.difficulty }}
              </el-tag>
              <el-tag size="small" type="info">AI 生成</el-tag>
            </div>
          </div>
          <div class="exam-card__action">
            <el-button type="primary" @click="startExam(exam)">开始考试</el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

const exams = ref([
  {
    id: 1,
    title: '数组与链表专项练习',
    description: '涵盖数组随机访问特性、链表插入删除操作、以及两者性能对比的核心题目。',
    topic: '数据结构',
    questionCount: 10,
    difficulty: '简单'
  },
  {
    id: 2,
    title: '二叉树与遍历算法',
    description: '包含二叉树的四种遍历方式、BST 特性、以及常见树的变体（完全二叉树、平衡树）的考点。',
    topic: '数据结构',
    questionCount: 8,
    difficulty: '中等'
  },
  {
    id: 3,
    title: 'TCP 协议深度测试',
    description: '聚焦 TCP 三次握手/四次挥手、拥塞控制、流量控制、TIME_WAIT 状态等核心机制。',
    topic: '计算机网络',
    questionCount: 12,
    difficulty: '困难'
  },
  {
    id: 4,
    title: 'JVM 垃圾回收机制',
    description: '涵盖 GC 算法（标记-清除/复制/整理）、分代收集、常用收集器（CMS/G1）的原理与对比。',
    topic: 'Java',
    questionCount: 6,
    difficulty: '中等'
  }
])

function startExam(exam) {
  router.push({ name: 'ExamTake', params: { id: exam.id } })
}
</script>

<style scoped>
.exams-page {
  padding: 24px;
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

.exam-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.exam-card {
  padding: 20px 24px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  background: #fff;
  transition: box-shadow 0.2s;
}

.exam-card:hover {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

.exam-card__main {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.exam-card__info h3 {
  margin: 0 0 6px 0;
  font-size: 16px;
}

.exam-card__desc {
  color: #606266;
  font-size: 13px;
  margin: 0 0 12px 0;
  max-width: 600px;
}

.exam-card__tags {
  display: flex;
  gap: 8px;
}
</style>
