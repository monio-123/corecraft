<template>
  <div class="permissions-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <h2>资源管理</h2>
          <div class="header-actions">
            <el-button type="primary" @click="openCreateDialog()">
              <el-icon><Plus /></el-icon>
              新增根节点
            </el-button>
            <el-button @click="fetchTree">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
          </div>
        </div>
      </template>

      <div class="content">
        <div class="tree-panel">
          <el-tree
            :data="treeData"
            node-key="id"
            :props="{ label: 'name', children: 'children' }"
            highlight-current
            default-expand-all
            @node-click="onNodeClick"
          >
            <template #default="{ data }">
              <span class="tree-node">
                <span class="tree-node__label">{{ data.name }}</span>
                <el-tag size="small" class="tree-node__tag" :type="tagType(data.type)">{{ data.type }}</el-tag>
              </span>
            </template>
          </el-tree>
        </div>

        <div class="detail-panel">
          <el-empty v-if="!currentNode" description="请选择左侧节点" />

          <div v-else>
            <div class="detail-header">
              <div class="detail-title">{{ currentNode.name }}</div>
              <div class="detail-actions">
                <el-button type="primary" @click="openCreateDialog(currentNode)">
                  <el-icon><Plus /></el-icon>
                  在此节点下新增
                </el-button>
                <el-button type="danger" @click="removeNode(currentNode)">
                  <el-icon><Delete /></el-icon>
                  删除
                </el-button>
              </div>
            </div>

            <el-form :model="editForm" label-width="90px" class="detail-form">
              <el-form-item label="类型">
                <el-select v-model="editForm.type" style="width: 220px">
                  <el-option label="GROUP" value="GROUP" />
                  <el-option label="MENU" value="MENU" />
                  <el-option label="API" value="API" />
                  <el-option label="OP" value="OP" />
                </el-select>
              </el-form-item>
              <el-form-item label="名称">
                <el-input v-model="editForm.name" />
              </el-form-item>
              <el-form-item label="编码">
                <el-input v-model="editForm.code" />
              </el-form-item>
              <el-form-item label="父节点">
                <el-input v-model="editForm.parentId" disabled />
              </el-form-item>
              <el-form-item label="排序">
                <el-input-number v-model="editForm.sort" :min="0" />
              </el-form-item>
              <el-form-item label="启用">
                <el-switch v-model="editForm.enabled" />
              </el-form-item>
              <el-form-item label="Meta">
                <el-input v-model="editForm.meta" type="textarea" :rows="6" placeholder='JSON 字符串，如 {"path":"/system/permission"}' />
              </el-form-item>

              <el-form-item>
                <el-button type="primary" :loading="saving" @click="saveEdit">保存</el-button>
              </el-form-item>
            </el-form>
          </div>
        </div>
      </div>
    </el-card>

    <el-dialog v-model="createDialogVisible" title="新增资源" width="520px">
      <el-form :model="createForm" label-width="90px">
        <el-form-item label="父节点">
          <el-input :model-value="createForm.parentId ?? 'ROOT'" disabled />
        </el-form-item>
        <el-form-item label="类型" required>
          <el-select v-model="createForm.type" style="width: 220px">
            <el-option label="GROUP" value="GROUP" />
            <el-option label="MENU" value="MENU" />
            <el-option label="API" value="API" />
            <el-option label="OP" value="OP" />
          </el-select>
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="createForm.name" />
        </el-form-item>
        <el-form-item label="编码" required>
          <el-input v-model="createForm.code" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="createForm.sort" :min="0" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="createForm.enabled" />
        </el-form-item>
        <el-form-item label="Meta">
          <el-input v-model="createForm.meta" type="textarea" :rows="6" placeholder='JSON 字符串，如 {"path":"/system/permission"}' />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="createNode">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete, Refresh } from '@element-plus/icons-vue'
import request from '../utils/request'

const treeData = ref([])
const currentNode = ref(null)

const saving = ref(false)
const creating = ref(false)

const editForm = reactive({
  id: null,
  parentId: null,
  type: 'GROUP',
  name: '',
  code: '',
  sort: 0,
  enabled: true,
  meta: ''
})

const createDialogVisible = ref(false)
const createForm = reactive({
  parentId: null,
  type: 'GROUP',
  name: '',
  code: '',
  sort: 0,
  enabled: true,
  meta: ''
})

const tagType = (type) => {
  switch (type) {
    case 'GROUP': return 'info'
    case 'MENU': return ''
    case 'API': return 'success'
    case 'OP': return 'warning'
    default: return 'info'
  }
}

const fetchTree = async () => {
  const res = await request.get('/permission/tree')
  treeData.value = res?.data || []
}

const onNodeClick = (data) => {
  currentNode.value = data
  editForm.id = data.id
  editForm.parentId = data.parentId ?? null
  editForm.type = data.type || 'GROUP'
  editForm.name = data.name || ''
  editForm.code = data.code || ''
  editForm.sort = data.sort ?? 0
  editForm.enabled = data.enabled ?? true
  editForm.meta = data.meta ?? ''
}

const openCreateDialog = (parent) => {
  createForm.parentId = parent?.id ?? null
  createForm.type = 'GROUP'
  createForm.name = ''
  createForm.code = ''
  createForm.sort = 0
  createForm.enabled = true
  createForm.meta = ''
  createDialogVisible.value = true
}

const createNode = async () => {
  if (!createForm.name || !createForm.code) {
    ElMessage.error('请填写名称与编码')
    return
  }
  creating.value = true
  try {
    await request.post('/permission', {
      parentId: createForm.parentId,
      type: createForm.type,
      name: createForm.name,
      code: createForm.code,
      sort: createForm.sort,
      enabled: createForm.enabled,
      meta: createForm.meta
    })
    createDialogVisible.value = false
    ElMessage.success('创建成功')
    await fetchTree()
  } finally {
    creating.value = false
  }
}

const saveEdit = async () => {
  if (!editForm.id) {
    return
  }
  saving.value = true
  try {
    await request.put('/permission', {
      id: editForm.id,
      parentId: editForm.parentId,
      type: editForm.type,
      name: editForm.name,
      code: editForm.code,
      sort: editForm.sort,
      enabled: editForm.enabled,
      meta: editForm.meta
    })
    ElMessage.success('保存成功')
    await fetchTree()
  } finally {
    saving.value = false
  }
}

const removeNode = async (node) => {
  if (!node?.id) {
    return
  }
  await ElMessageBox.confirm(`确认删除 "${node.name}" ?`, '提示', { type: 'warning' })
  await request.delete(`/permission/${node.id}`)
  ElMessage.success('删除成功')
  currentNode.value = null
  await fetchTree()
}

onMounted(async () => {
  try {
    await fetchTree()
  } catch (e) {
    ElMessage.error('加载资源树失败')
  }
})
</script>

<style scoped>
.permissions-container {
  padding: 20px 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-header h2 {
  margin: 0;
  font-size: 20px;
}

.header-actions {
  display: flex;
  gap: 10px;
}

.content {
  margin-top: 16px;
  display: grid;
  grid-template-columns: 360px 1fr;
  gap: 16px;
}

.tree-panel {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 12px;
  min-height: 520px;
  overflow: auto;
}

.detail-panel {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 12px;
  min-height: 520px;
}

.tree-node {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.tree-node__tag {
  margin-left: 6px;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.detail-title {
  font-size: 16px;
  font-weight: 600;
}

.detail-actions {
  display: flex;
  gap: 10px;
}

.detail-form {
  max-width: 760px;
}
</style>
