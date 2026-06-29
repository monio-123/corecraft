<template>
  <div class="knowledge-trees-page">
    <div class="page-header">
      <div class="page-header__text">
        <h2>知识树</h2>
        <p class="subtitle">自由构建属于你自己的知识体系，每个知识点都可以互相串联</p>
      </div>
      <el-button type="primary" @click="openCreateDialog">
        <el-icon><Plus /></el-icon> 创建知识树
      </el-button>
    </div>

    <div v-if="trees.length === 0" class="empty-state">
      <div class="empty-state__icon">🌱</div>
      <h3>还没有知识树</h3>
      <p>点击上方按钮创建你的第一棵知识树，开始构建知识体系</p>
    </div>

    <div v-else class="tree-grid">
      <div
        v-for="tree in trees"
        :key="tree.id"
        class="tree-card"
        @click="enterTree(tree)"
      >
        <div class="tree-card__icon">{{ tree.icon }}</div>
        <div class="tree-card__info">
          <div class="tree-card__header">
            <h3>{{ tree.name }}</h3>
            <div class="tree-card__actions" @click.stop>
              <el-button text size="small" @click="openEditDialog(tree)">
                <el-icon><Edit /></el-icon>
              </el-button>
              <el-button text size="small" type="danger" @click="confirmDelete(tree)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
          </div>
          <p class="tree-card__desc">{{ tree.description || '暂无描述' }}</p>
          <div class="tree-card__meta">
            <el-tag size="small" type="info">{{ nodeCount(tree.id) }} 个知识点</el-tag>
            <el-tag v-if="tree.category" size="small" type="success">{{ tree.category }}</el-tag>
            <span class="tree-card__time">{{ tree.updatedAt }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 创建/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingTree ? '编辑知识树' : '创建知识树'"
      width="520px"
      destroy-on-close
    >
      <el-form :model="form" label-width="70px" label-position="left">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="如：数据结构与算法" maxlength="30" show-word-limit />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="简单描述这棵知识树的主题范围" maxlength="120" show-word-limit />
        </el-form-item>
        <el-form-item label="图标">
          <div class="emoji-picker">
            <span
              v-for="e in emojiOptions"
              :key="e"
              class="emoji-option"
              :class="{ selected: form.icon === e }"
              @click="form.icon = e"
            >{{ e }}</span>
          </div>
        </el-form-item>
        <el-form-item label="分类">
          <el-input v-model="form.category" placeholder="如：CS基础、语言进阶、数学" maxlength="20" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!form.name.trim()" @click="handleSave">
          {{ editingTree ? '保存' : '创建' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete } from '@element-plus/icons-vue'
import {
  getTrees, createTree, updateTree, deleteTree,
  getNodes
} from './useKnowledgeStore'

const router = useRouter()

const trees = ref([])
const dialogVisible = ref(false)
const editingTree = ref(null)

const form = reactive({
  name: '',
  description: '',
  icon: '📚',
  category: ''
})

const emojiOptions = ['📚', '🌳', '🌐', '☕', '🧠', '⚡', '🔬', '📐', '🎯', '💻', '🗂️', '🔑', '🎨', '📊', '🧩', '🚀', '💡', '📖', '🏗️', '🔗']

function refresh() {
  trees.value = getTrees()
}

function nodeCount(treeId) {
  return getNodes(treeId).length
}

function enterTree(tree) {
  router.push({ name: 'KnowledgeTreeView', params: { id: tree.id } })
}

function openCreateDialog() {
  editingTree.value = null
  form.name = ''
  form.description = ''
  form.icon = '📚'
  form.category = ''
  dialogVisible.value = true
}

function openEditDialog(tree) {
  editingTree.value = tree
  form.name = tree.name
  form.description = tree.description
  form.icon = tree.icon
  form.category = tree.category
  dialogVisible.value = true
}

function handleSave() {
  if (editingTree.value) {
    updateTree(editingTree.value.id, {
      name: form.name.trim(),
      description: form.description.trim(),
      icon: form.icon,
      category: form.category.trim()
    })
    ElMessage.success('已更新')
    dialogVisible.value = false
    refresh()
  } else {
    const tree = createTree({
      name: form.name.trim(),
      description: form.description.trim(),
      icon: form.icon,
      category: form.category.trim()
    })
    ElMessage.success('知识树已创建')
    dialogVisible.value = false
    refresh()
  }
}

async function confirmDelete(tree) {
  try {
    await ElMessageBox.confirm(
      `确定要删除「${tree.name}」吗？树下的所有知识点也会一并删除，不可恢复。`,
      '删除知识树',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
    deleteTree(tree.id)
    refresh()
    ElMessage.success('已删除')
  } catch {
    // cancelled
  }
}

onMounted(() => {
  refresh()
})
</script>

<style scoped>
.knowledge-trees-page {
  padding: 28px 32px;
  max-width: 1100px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 28px;
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

.empty-state {
  text-align: center;
  padding: 80px 20px;
  color: #909399;
}

.empty-state__icon {
  font-size: 56px;
  margin-bottom: 16px;
}

.empty-state h3 {
  margin: 0 0 8px 0;
  font-size: 16px;
  color: #606266;
}

.empty-state p {
  margin: 0;
  font-size: 13px;
}

.tree-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 18px;
}

.tree-card {
  display: flex;
  gap: 16px;
  padding: 20px;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s ease;
  background: #fff;
}

.tree-card:hover {
  border-color: #c6d9f1;
  box-shadow: 0 4px 16px rgba(64, 158, 255, 0.08);
  transform: translateY(-1px);
}

.tree-card__icon {
  font-size: 36px;
  flex-shrink: 0;
  width: 56px;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
  border-radius: 12px;
}

.tree-card__info {
  flex: 1;
  min-width: 0;
}

.tree-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.tree-card__header h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tree-card__actions {
  display: flex;
  gap: 2px;
  opacity: 0;
  transition: opacity 0.15s;
}

.tree-card:hover .tree-card__actions {
  opacity: 1;
}

.tree-card__desc {
  color: #909399;
  font-size: 13px;
  line-height: 1.5;
  margin: 0 0 12px 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.tree-card__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.tree-card__time {
  font-size: 11px;
  color: #c0c4cc;
  margin-left: auto;
}

/* emoji picker */
.emoji-picker {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.emoji-option {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.15s;
  border: 2px solid transparent;
}

.emoji-option:hover {
  background: #ecf5ff;
}

.emoji-option.selected {
  border-color: #409eff;
  background: #ecf5ff;
}

</style>
