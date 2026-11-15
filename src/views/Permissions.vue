<template>
  <div class="page">
    <div class="page-head">
      <div class="page-head__title">资源管理</div>
      <div class="page-head__actions">
        <el-button type="primary" @click="openCreateDialog()">
          <el-icon><Plus /></el-icon>
          新增根节点
        </el-button>
        <!-- 图标走 :icon 而不是默认插槽：EP 在 loading 时只跳过 icon 插槽，
             默认插槽照渲染 ⇒ 写在里面会和转圈叠成两个图标 -->
        <el-button :icon="Refresh" :loading="treeLoading" @click="fetchTree">刷新</el-button>
      </div>
    </div>

    <div class="content">
      <!-- 左栏要有"加载中"：表格空态的「暂无数据」被 admin-page.css 收掉了，
           不给 loading 的话"正在拉"和"真的没有"看起来一模一样（§8 ④） -->
      <div class="panel" v-loading="treeLoading">
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

      <div class="panel">
        <!-- 没选中节点时这一格什么都不画：左边有选中项就说明加载完了，
             用户分得清"空的"和"坏的"，再写一句「请选择左侧节点」就是指令口气 -->
        <div v-if="currentNode">
          <div class="panel-head">
            <div class="panel__title">{{ currentNode.name }}</div>
            <div class="panel-head__actions">
              <el-button type="primary" @click="openCreateDialog(currentNode)">
                <el-icon><Plus /></el-icon>
                在此节点下新增
              </el-button>
              <el-button link type="danger" @click="removeNode(currentNode)">删除</el-button>
            </div>
          </div>

          <PermissionForm :form="editForm" :parent-label="editParentLabel">
            <el-button type="primary" :loading="saving" @click="saveEdit">保存</el-button>
          </PermissionForm>
        </div>
      </div>
    </div>

    <el-dialog v-model="createDialogVisible" title="新增资源" width="520px">
      <PermissionForm :form="createForm" :parent-label="parentLabel" required />
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="createNode">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import PermissionForm from '../components/PermissionForm.vue'
import request from '../utils/request'
import { confirmAction } from '../utils/confirm'
import { parseMeta } from '../utils/menu'

const treeData = ref([])
const currentNode = ref(null)
const treeLoading = ref(false)

const saving = ref(false)
const creating = ref(false)

// 节点表单的字段集。编辑面板和新增弹窗是同一套结构，只是新增用不到 id
function emptyForm() {
  return {
    id: null,
    parentId: null,
    type: 'GROUP',
    name: '',
    code: '',
    sort: 0,
    enabled: true,
    meta: '',
    // MENU 类型专用字段
    menuPath: '',
    menuIcon: '',
    // 壳归属：'admin' | 'app'，写入 meta.shell
    menuShell: 'admin'
  }
}

const editForm = reactive(emptyForm())

const createDialogVisible = ref(false)
const createForm = reactive(emptyForm())

// 在树里按 id 找节点名
function findNodeName(nodes, id) {
  for (const n of nodes) {
    if (n.id === id) return n.name || ''
    const hit = findNodeName(Array.isArray(n.children) ? n.children : [], id)
    if (hit) return hit
  }
  return ''
}

// 父节点只显示裸 id（"1"）看不出是哪一项，这里带上名称；根节点没有父级
const nodeLabel = (id) =>
  id == null ? 'ROOT' : findNodeName(treeData.value, id) || String(id)

const parentLabel = computed(() => nodeLabel(createForm.parentId))
const editParentLabel = computed(() => nodeLabel(editForm.parentId))

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
  treeLoading.value = true
  try {
    const res = await request.get('/permission/tree')
    treeData.value = res?.data || []
  } finally {
    treeLoading.value = false
  }
}

const onNodeClick = (data) => {
  currentNode.value = data
  const meta = parseMeta(data.meta)
  // 从 emptyForm 起步再覆盖，避免上一个节点残留的字段（比如 MENU 的 path）留在这个节点上
  Object.assign(editForm, emptyForm(), {
    id: data.id,
    parentId: data.parentId ?? null,
    type: data.type || 'GROUP',
    name: data.name || '',
    code: data.code || '',
    sort: data.sort ?? 0,
    enabled: data.enabled ?? true,
    meta: data.meta ?? '',
    menuPath: meta.path || '',
    menuIcon: meta.icon || '',
    // 缺省 admin：历史菜单没有 shell 字段，行为不变
    menuShell: meta.shell === 'app' ? 'app' : 'admin'
  })
}

const openCreateDialog = (parent) => {
  Object.assign(createForm, emptyForm(), { parentId: parent?.id ?? null })
  createDialogVisible.value = true
}

// 构建 meta JSON
// menuShell 对 GROUP 和 MENU 都生效：分组本身要标壳，前端 flattenShellNav 才能把它收进对应侧边栏
function buildMeta(type, meta, menuPath, menuIcon, menuShell) {
  const metaObj = parseMeta(meta)
  if (type === 'MENU') {
    if (menuPath) metaObj.path = menuPath
    else delete metaObj.path
    if (menuIcon) metaObj.icon = menuIcon
    else delete metaObj.icon
  }
  if (menuShell) metaObj.shell = menuShell
  else delete metaObj.shell
  return JSON.stringify(metaObj)
}

// 新增和保存的请求体结构完全一样，只是方法/URL/是否带 id 不同
function permissionPayload(form) {
  return {
    parentId: form.parentId,
    type: form.type,
    name: form.name,
    code: form.code,
    sort: form.sort,
    enabled: form.enabled,
    meta: buildMeta(form.type, form.meta, form.menuPath, form.menuIcon, form.menuShell)
  }
}

const createNode = async () => {
  if (!createForm.name || !createForm.code) {
    ElMessage.error('请填写名称与编码')
    return
  }
  creating.value = true
  try {
    await request.post('/permission', permissionPayload(createForm))
    createDialogVisible.value = false
    ElMessage.success('已创建')
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
    await request.put('/permission', { id: editForm.id, ...permissionPayload(editForm) })
    ElMessage.success('已保存')
    await fetchTree()
  } finally {
    saving.value = false
  }
}

const removeNode = async (node) => {
  if (!node?.id) {
    return
  }
  if (!(await confirmAction('删除资源'))) return
  await request.delete(`/permission/${node.id}`)
  ElMessage.success('已删除')
  currentNode.value = null
  await fetchTree()
}

onMounted(async () => {
  try {
    await fetchTree()
  } catch (e) {
    // 提示归 request.js（走 axios 实例，唯一提示出口）
  }
})
</script>

<style scoped>
/* 页面骨架（.page / .page-head / .page-head__title / .panel / .panel-head /
   .panel__title / .hint / .tree-node / .tree-node__tag）在 assets/admin-page.css */

.content {
  display: grid;
  grid-template-columns: 360px 1fr;
  gap: var(--sp-12);
}
</style>
