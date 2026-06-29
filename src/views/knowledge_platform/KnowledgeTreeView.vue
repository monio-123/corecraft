<template>
  <div class="tree-view-page">
    <!-- 顶栏 -->
    <div class="tree-view-header">
      <el-button text @click="goBack">
        <el-icon><ArrowLeft /></el-icon> 返回
      </el-button>
      <div class="tree-view-header__title">
        <span class="tree-icon">{{ tree.icon }}</span>
        <template v-if="editingTitle">
          <el-input
            ref="titleInputRef"
            v-model="titleDraft"
            size="small"
            class="title-input"
            @blur="saveTitle"
            @keyup.enter="saveTitle"
            @keyup.escape="cancelTitle"
          />
        </template>
        <template v-else>
          <h2 @dblclick="startEditTitle">{{ tree.name }}</h2>
          <el-button text size="small" @click="startEditTitle" class="title-edit-btn">
            <el-icon><Edit /></el-icon>
          </el-button>
        </template>
      </div>
      <div class="tree-view-header__toolbar">
        <el-button size="small" @click="addRootNode">
          <el-icon><Plus /></el-icon> 添加根节点
        </el-button>
        <el-button size="small" text @click="expandAll">展开全部</el-button>
        <el-button size="small" text @click="collapseAll">收起全部</el-button>
      </div>
    </div>

    <div class="tree-view-body">
      <!-- 左侧树导航 -->
      <div class="tree-nav-panel">
        <el-input
          v-model="searchText"
          placeholder="搜索知识点..."
          size="small"
          clearable
          class="tree-search"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-tree
          ref="treeRef"
          :data="treeData"
          :props="treeProps"
          node-key="id"
          :filter-node-method="filterNode"
          :expand-on-click-node="false"
          highlight-current
          @node-click="handleNodeClick"
          @node-contextmenu="handleContextMenu"
        >
          <template #default="{ data }">
            <span class="tree-node-label">
              <span class="tree-node-icon">{{ typeIcon(data.nodeType) }}</span>
              <span class="tree-node-title">{{ data.title }}</span>
            </span>
          </template>
        </el-tree>

        <!-- 右键菜单 -->
        <div
          v-if="contextMenu.visible"
          class="context-menu"
          :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }"
        >
          <div class="context-menu__item" @click="addChildNode">
            <el-icon><Plus /></el-icon> 添加子节点
          </div>
          <div class="context-menu__item" @click="editNode">
            <el-icon><Edit /></el-icon> 编辑
          </div>
          <div class="context-menu__divider"></div>
          <div class="context-menu__item" @click="openMergeDialog">
            <el-icon><Connection /></el-icon> 合并到...
          </div>
          <div class="context-menu__divider"></div>
          <div class="context-menu__item context-menu__item--danger" @click="deleteCurrentNode">
            <el-icon><Delete /></el-icon> 删除
          </div>
        </div>
      </div>

      <!-- 右侧详情面板 -->
      <div class="tree-detail-panel">
        <template v-if="selectedNode">
          <div class="node-detail">
            <!-- 节点类型 & 标题 -->
            <div class="node-detail__header">
              <el-select v-model="editForm.nodeType" size="small" style="width: 110px" @change="autoSaveNode">
                <el-option v-for="nt in NODE_TYPES" :key="nt.value" :value="nt.value" :label="nt.icon + ' ' + nt.label" />
              </el-select>
              <h3>
                <el-input
                  v-model="editForm.title"
                  class="inline-title-input"
                  placeholder="节点标题"
                  @blur="autoSaveNode"
                  @keyup.enter="autoSaveNode"
                />
              </h3>
            </div>

            <!-- 内容编辑区 -->
            <div class="node-detail__content">
              <el-input
                v-model="editForm.content"
                type="textarea"
                :rows="6"
                placeholder="在这里填写知识点内容… 支持 Markdown 语法"
                @blur="autoSaveNode"
              />
              <p class="content-hint">内容自动保存 · 支持 Markdown 格式（**加粗**、### 标题、- 列表等）</p>
            </div>

            <!-- 元信息 -->
            <div class="node-meta">
              <span>创建于 {{ selectedNode.createdAt }}</span>
              <span v-if="selectedNode.updatedAt !== selectedNode.createdAt">· 更新于 {{ selectedNode.updatedAt }}</span>
            </div>
          </div>

          <!-- 关联知识点 -->
          <div class="related-section">
            <div class="related-section__header">
              <h4>关联知识点</h4>
              <el-button size="small" @click="openAddRelationDialog">
                <el-icon><Plus /></el-icon> 添加关联
              </el-button>
            </div>
            <div v-if="relatedList.length === 0" class="related-empty">
              暂无关联 · 点击添加关联，将本节点与其他知识点串联起来
            </div>
            <div v-else class="related-list">
              <div
                v-for="rel in relatedList"
                :key="rel.id"
                class="related-item"
                @click="jumpToNode(rel.id)"
              >
                <span class="related-type">{{ relationTypeLabel(rel.relationType) }}</span>
                <span class="related-name">{{ rel.title }}</span>
                <el-icon class="related-jump"><ArrowRight /></el-icon>
                <el-button class="related-remove" text size="small" type="danger" @click.stop="removeRelation(rel.id)">
                  <el-icon><Close /></el-icon>
                </el-button>
              </div>
            </div>
          </div>
        </template>

        <!-- 未选择节点 -->
        <div v-else class="empty-detail">
          <div class="empty-detail__icon">👆</div>
          <p>在左侧点击知识点查看详情</p>
          <p class="empty-detail__hint">右键节点可以添加子节点、编辑、合并或删除</p>
        </div>
      </div>

      <!-- 右侧 AI 对话面板 -->
      <div v-if="selectedNode" class="ai-panel-col">
        <AiChatPanel
          :node="selectedNode"
          :tree-name="tree.name"
          :breadcrumb="nodeBreadcrumb"
          :related-nodes="relatedList"
          :exam-summary="nodeExamSummary"
          @jump-to-node="jumpToNode"
          @add-relation="addRelationForNode"
        />
      </div>
    </div>

    <!-- 节点编辑弹窗 -->
    <el-dialog v-model="nodeDialogVisible" title="编辑知识点" width="520px" destroy-on-close>
      <el-form :model="nodeForm" label-width="80px" label-position="left">
        <el-form-item label="标题" required>
          <el-input v-model="nodeForm.title" placeholder="知识点标题" maxlength="50" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="nodeForm.nodeType" style="width: 100%">
            <el-option v-for="nt in NODE_TYPES" :key="nt.value" :value="nt.value" :label="nt.icon + ' ' + nt.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="nodeForm.content" type="textarea" :rows="6" placeholder="知识点详细内容..." />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="nodeDialogVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!nodeForm.title.trim()" @click="saveNodeDialog">保存</el-button>
      </template>
    </el-dialog>

    <!-- 添加关联弹窗 -->
    <el-dialog v-model="relationDialogVisible" title="添加关联知识点" width="480px" destroy-on-close>
      <el-form label-width="80px" label-position="left">
        <el-form-item label="目标节点">
          <el-select v-model="relationForm.targetId" placeholder="选择要关联的知识点" filterable style="width: 100%">
            <el-option
              v-for="n in allNodes"
              :key="n.id"
              :value="n.id"
              :label="n.title"
              :disabled="n.id === selectedNode?.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="关联类型">
          <el-select v-model="relationForm.relationType" style="width: 100%">
            <el-option v-for="rt in RELATION_TYPES" :key="rt.value" :value="rt.value" :label="rt.label" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="relationDialogVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!relationForm.targetId" @click="addRelation">确认</el-button>
      </template>
    </el-dialog>

    <!-- 合并节点弹窗 -->
    <el-dialog v-model="mergeDialogVisible" title="合并节点" width="480px" destroy-on-close>
      <p class="merge-hint">
        将 <strong>「{{ contextMenu.node?.title }}」</strong> 合并到以下目标节点。
        合并后，源节点的子节点将移动到目标节点，关联关系也会合并。源节点将被删除。
      </p>
      <el-form label-width="80px" label-position="left">
        <el-form-item label="目标节点">
          <el-select v-model="mergeTargetId" placeholder="选择目标节点" filterable style="width: 100%">
            <el-option
              v-for="n in allNodes"
              :key="n.id"
              :value="n.id"
              :label="n.title"
              :disabled="n.id === contextMenu.node?.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="mergeDialogVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!mergeTargetId" @click="doMerge">确认合并</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft, ArrowRight, Plus, Edit, Delete, Search, Close, Connection
} from '@element-plus/icons-vue'
import {
  getTree, getNodes, getNode, createNode, updateNode, deleteNode,
  buildTreeData, addRelatedNode, removeRelatedNode, mergeNodes,
  NODE_TYPES, RELATION_TYPES
} from './useKnowledgeStore'
import AiChatPanel from './AiChatPanel.vue'

const route = useRoute()
const router = useRouter()
const treeRef = ref(null)
const titleInputRef = ref(null)
const searchText = ref('')
const selectedNodeId = ref(null)
const contextMenu = reactive({ visible: false, x: 0, y: 0, node: null })
const editingTitle = ref(false)
const titleDraft = ref('')

// dialogs
const nodeDialogVisible = ref(false)
const relationDialogVisible = ref(false)
const mergeDialogVisible = ref(false)
const mergeTargetId = ref('')

const nodeForm = reactive({ title: '', nodeType: 'topic', content: '' })
const relationForm = reactive({ targetId: '', relationType: 'related' })
const editForm = reactive({ title: '', nodeType: 'topic', content: '' })

const tree = computed(() => getTree(route.params.id) || { id: '', name: '未知', icon: '📚' })
const allNodes = computed(() => getNodes(tree.value.id))
const treeData = computed(() => buildTreeData(tree.value.id))
const selectedNode = computed(() => selectedNodeId.value ? getNode(selectedNodeId.value) : null)
const treeProps = { children: 'children', label: 'title' }

const relatedList = computed(() => {
  if (!selectedNode.value) return []
  return (selectedNode.value.relatedNodes || []).map((r) => {
    const target = getNode(r.id)
    return { ...r, title: target ? target.title : r.title || '(已删除)' }
  })
})

// 计算面包屑路径
const nodeBreadcrumb = computed(() => {
  if (!selectedNode.value) return []
  const path = []
  let current = selectedNode.value
  while (current) {
    path.unshift(current.title)
    current = current.parentId ? getNode(current.parentId) : null
  }
  return path
})

// 模拟考试记录摘要
const nodeExamSummary = computed(() => {
  if (!selectedNode.value) return ''
  // 从 localStorage 读取考试历史
  try {
    const records = JSON.parse(localStorage.getItem('exam_history') || '[]')
    const nodeRecords = records.filter(r =>
      r.nodeTitle && r.nodeTitle.includes(selectedNode.value.title)
    )
    if (nodeRecords.length > 0) {
      const latest = nodeRecords[nodeRecords.length - 1]
      return `最近考试「${latest.examTitle}」得分 ${latest.score}/${latest.totalScore}`
    }
  } catch { /* ignore */ }
  return ''
})

function addRelationForNode(targetNodeId) {
  if (!selectedNode.value) return
  addRelatedNode(selectedNode.value.id, targetNodeId, 'related')
  ElMessage.success('关联已添加')
}

function typeIcon(type) {
  const found = NODE_TYPES.find((t) => t.value === type)
  return found ? found.icon : '📘'
}

function relationTypeLabel(type) {
  const found = RELATION_TYPES.find((t) => t.value === type)
  return found ? found.label : '相关'
}

// --------------- tree title ---------------

function startEditTitle() {
  titleDraft.value = tree.value.name
  editingTitle.value = true
  nextTick(() => titleInputRef.value?.focus())
}

function saveTitle() {
  if (titleDraft.value.trim()) {
    import('./useKnowledgeStore').then((m) => m.updateTree(tree.value.id, { name: titleDraft.value.trim() }))
  }
  editingTitle.value = false
}

function cancelTitle() {
  editingTitle.value = false
}

// --------------- tree node click ---------------

function handleNodeClick(data) {
  selectedNodeId.value = data.id
  contextMenu.visible = false
  syncEditForm()
}

function syncEditForm() {
  if (selectedNode.value) {
    editForm.title = selectedNode.value.title
    editForm.nodeType = selectedNode.value.nodeType
    editForm.content = selectedNode.value.content || ''
  }
}

function autoSaveNode() {
  if (!selectedNode.value) return
  updateNode(selectedNode.value.id, {
    title: editForm.title,
    nodeType: editForm.nodeType,
    content: editForm.content
  })
}

// --------------- context menu ---------------

function handleContextMenu(event, data) {
  event.preventDefault()
  contextMenu.node = data
  contextMenu.x = Math.min(event.offsetX, 260)
  contextMenu.y = event.offsetY
  contextMenu.visible = true
  selectedNodeId.value = data.id
  syncEditForm()
}

function hideContextMenu() {
  contextMenu.visible = false
}

onMounted(() => document.addEventListener('click', hideContextMenu))
onBeforeUnmount(() => document.removeEventListener('click', hideContextMenu))

// --------------- node CRUD ---------------

function addRootNode() {
  const node = createNode({ treeId: tree.value.id, parentId: null, title: '新知识点', content: '', nodeType: 'topic' })
  selectedNodeId.value = node.id
  syncEditForm()
  ElMessage.success('已添加根节点')
}

function addChildNode() {
  const parent = contextMenu.node
  const node = createNode({ treeId: tree.value.id, parentId: parent?.id || null, title: '子知识点', content: '', nodeType: 'topic' })
  selectedNodeId.value = node.id
  syncEditForm()
  contextMenu.visible = false
  ElMessage.success('已添加子节点')
}

function editNode() {
  const n = contextMenu.node
  nodeForm.title = n.title
  nodeForm.nodeType = n.nodeType
  nodeForm.content = n.content || ''
  nodeDialogVisible.value = true
  contextMenu.visible = false
}

async function deleteCurrentNode() {
  const n = contextMenu.node
  contextMenu.visible = false
  try {
    await ElMessageBox.confirm(
      `确定删除「${n.title}」吗？其下的子节点也会一并删除。`,
      '删除知识点',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
    if (selectedNodeId.value === n.id) selectedNodeId.value = null
    deleteNode(n.id)
    ElMessage.success('已删除')
  } catch { /* cancelled */ }
}

function saveNodeDialog() {
  const n = contextMenu.node || selectedNode.value
  if (!n) return
  updateNode(n.id, {
    title: nodeForm.title.trim(),
    nodeType: nodeForm.nodeType,
    content: nodeForm.content
  })
  nodeDialogVisible.value = false
  syncEditForm()
  ElMessage.success('已保存')
}

// --------------- relation ---------------

function openAddRelationDialog() {
  relationForm.targetId = ''
  relationForm.relationType = 'related'
  relationDialogVisible.value = true
}

function addRelation() {
  if (!selectedNode.value || !relationForm.targetId) return
  addRelatedNode(selectedNode.value.id, relationForm.targetId, relationForm.relationType)
  relationDialogVisible.value = false
  ElMessage.success('关联已添加')
}

function removeRelation(targetId) {
  if (!selectedNode.value) return
  removeRelatedNode(selectedNode.value.id, targetId)
  ElMessage.success('关联已移除')
}

// --------------- merge ---------------

function openMergeDialog() {
  mergeTargetId.value = ''
  mergeDialogVisible.value = true
  contextMenu.visible = false
}

function doMerge() {
  if (!contextMenu.node || !mergeTargetId.value) return
  mergeNodes(contextMenu.node.id, mergeTargetId.value)
  selectedNodeId.value = mergeTargetId.value
  mergeDialogVisible.value = false
  syncEditForm()
  ElMessage.success('节点已合并')
}

// --------------- jump / expand ---------------

function jumpToNode(nodeId) {
  nextTick(() => {
    treeRef.value?.setCurrentKey(nodeId)
    const node = treeRef.value?.getNode(nodeId)
    if (node) {
      node.expand()
      selectedNodeId.value = nodeId
      syncEditForm()
    }
  })
}

function expandAll() {
  const keys = allNodes.value.map((n) => n.id)
  keys.forEach((k) => {
    const node = treeRef.value?.getNode(k)
    if (node) node.expand()
  })
}

function collapseAll() {
  const keys = allNodes.value.map((n) => n.id)
  keys.forEach((k) => {
    const node = treeRef.value?.getNode(k)
    if (node && node.childNodes.length > 0) node.collapse()
  })
}

function filterNode(value, data) {
  if (!value) return true
  return data.title.toLowerCase().includes(value.toLowerCase())
}

watch(searchText, (val) => {
  treeRef.value?.filter(val)
})

function goBack() {
  router.push({ name: 'KnowledgeTrees' })
}
</script>

<style scoped>
.tree-view-page {
  height: calc(100vh - 120px);
  display: flex;
  flex-direction: column;
}

/* header */
.tree-view-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 20px;
  border-bottom: 1px solid #ebeef5;
  background: #fff;
  flex-shrink: 0;
}

.tree-view-header__title {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
}

.tree-icon { font-size: 20px; }

.tree-view-header__title h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  user-select: none;
}

.title-edit-btn {
  opacity: 0;
  transition: opacity 0.15s;
}

.tree-view-header__title h2:hover + .title-edit-btn,
.title-edit-btn:hover {
  opacity: 1;
}

.title-input {
  width: 200px;
}

.tree-view-header__toolbar {
  display: flex;
  gap: 6px;
}

/* body */
.tree-view-body {
  display: flex;
  flex: 1;
  overflow: hidden;
}

.tree-nav-panel {
  width: 300px;
  min-width: 240px;
  border-right: 1px solid #ebeef5;
  padding: 14px;
  overflow-y: auto;
  background: #fafbfc;
  position: relative;
}

.tree-search {
  margin-bottom: 10px;
}

.tree-node-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  overflow: hidden;
}

.tree-node-icon { flex-shrink: 0; }
.tree-node-title { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

/* context menu */
.context-menu {
  position: absolute;
  z-index: 100;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.12);
  padding: 4px 0;
  min-width: 160px;
}

.context-menu__item {
  padding: 9px 16px;
  font-size: 13px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
  transition: background 0.12s;
}

.context-menu__item:hover {
  background: #f5f7fa;
}

.context-menu__item--danger {
  color: #f56c6c;
}

.context-menu__divider {
  height: 1px;
  background: #ebeef5;
  margin: 4px 0;
}

/* detail panel */
.tree-detail-panel {
  flex: 1;
  min-width: 0;
  padding: 28px 36px;
  overflow-y: auto;
  background: #fff;
}

/* AI panel column */
.ai-panel-col {
  width: 380px;
  min-width: 320px;
  flex-shrink: 0;
  overflow: hidden;
}

.node-detail__header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}

.node-detail__header h3 {
  margin: 0;
  flex: 1;
}

.inline-title-input :deep(.el-input__inner) {
  font-size: 18px;
  font-weight: 600;
  border: none;
  padding: 0;
  background: transparent;
}

.inline-title-input :deep(.el-input__inner):focus {
  border: none;
  box-shadow: none;
}

.node-detail__content {
  margin-bottom: 20px;
}

.content-hint {
  color: #c0c4cc;
  font-size: 11px;
  margin: 6px 0 0 0;
}

.node-meta {
  font-size: 12px;
  color: #c0c4cc;
  margin-bottom: 28px;
  padding-bottom: 20px;
  border-bottom: 1px solid #ebeef5;
}

/* related */
.related-section {
  margin-top: 8px;
}

.related-section__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.related-section__header h4 {
  margin: 0;
  font-size: 15px;
}

.related-empty {
  color: #c0c4cc;
  font-size: 13px;
  padding: 20px;
  text-align: center;
  background: #fafbfc;
  border-radius: 8px;
}

.related-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.related-item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 7px 14px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.15s;
  font-size: 13px;
  background: #fff;
}

.related-item:hover {
  border-color: #409eff;
  background: #ecf5ff;
}

.related-type {
  font-size: 11px;
  padding: 1px 7px;
  border-radius: 4px;
  background: #f0f2f5;
  color: #909399;
  flex-shrink: 0;
}

.related-name {
  color: #303133;
}

.related-jump {
  color: #c0c4cc;
  font-size: 12px;
}

.related-remove {
  padding: 0;
  margin-left: 2px;
  opacity: 0;
}

.related-item:hover .related-remove {
  opacity: 1;
}

/* empty */
.empty-detail {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #909399;
}

.empty-detail__icon {
  font-size: 40px;
  margin-bottom: 12px;
}

.empty-detail p {
  margin: 0 0 4px 0;
  font-size: 14px;
}

.empty-detail__hint {
  font-size: 12px !important;
  color: #c0c4cc;
}

.merge-hint {
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
  margin: 0 0 18px 0;
}
</style>
