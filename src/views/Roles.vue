<template>
  <div class="roles-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <h2>角色管理</h2>
          <el-button type="primary" @click="openRoleDialog()">
            <el-icon><Plus /></el-icon>
            新增角色
          </el-button>
        </div>
      </template>

      <div class="roles-content">
        <el-table :data="rolesList" stripe style="width: 100%" v-loading="loading">
          <el-table-column prop="id" label="角色ID" width="80" />
          <el-table-column prop="code" label="角色编码" min-width="140" />
          <el-table-column prop="name" label="角色名称" />
          <el-table-column prop="description" label="角色描述" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="row.enabled ? 'success' : 'info'">
                {{ row.enabled ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="创建时间" width="180" />
          <el-table-column label="操作" width="240" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" size="small" @click="openRoleDialog(row.id)">
                <el-icon><Edit /></el-icon>
                编辑
              </el-button>
              <el-tooltip
                content="超级管理员默认拥有所有权限，无需手动分配"
                :disabled="!isAdminRoleCode(row.code)"
                placement="top"
              >
                <el-button size="small" @click="openPermDialog(row.id)" :disabled="isAdminRoleCode(row.code)">
                  资源分配
                </el-button>
              </el-tooltip>
              <el-tooltip
                content="超级管理员角色不允许删除"
                :disabled="!isAdminRoleCode(row.code)"
                placement="top"
              >
                <el-button type="danger" size="small" @click="removeRole(row.id)" :disabled="isAdminRoleCode(row.code)">
                  <el-icon><Delete /></el-icon>
                  删除
                </el-button>
              </el-tooltip>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>

    <el-dialog
      v-model="roleDialogVisible"
      :title="roleForm.id ? '编辑角色' : '新增角色'"
      width="560px"
    >
      <el-form :model="roleForm" label-width="90px" class="role-form">
        <el-form-item label="角色名称" required>
          <el-input v-model="roleForm.name" />
        </el-form-item>
        <el-form-item label="角色编码" required>
          <el-input v-model="roleForm.code" :disabled="!!roleForm.id" />
        </el-form-item>
        <el-form-item label="角色描述">
          <el-input v-model="roleForm.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="是否启用">
          <el-switch v-model="roleForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="roleSaving" @click="saveRoleInfo">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="permDialogVisible"
      title="资源分配"
      width="860px"
      destroy-on-close
    >
      <div class="permission-panel">
        <el-tree
          ref="permissionTreeRef"
          :data="permissionTree"
          node-key="id"
          show-checkbox
          check-strictly
          default-expand-all
          :props="{ label: 'name', children: 'children' }"
        >
          <template #default="{ data }">
            <span class="permission-node">
              <span>{{ data.name }}</span>
              <el-tag size="small" class="permission-node__tag">{{ data.type }}</el-tag>
            </span>
          </template>
        </el-tree>
      </div>
      <template #footer>
        <el-button @click="permDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="permSaving" @click="saveRolePermissions">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete } from '@element-plus/icons-vue'
import { isAdminRoleCode } from '../utils/auth'
import request from '../utils/request'
import { createRole, deleteRole, getRoleDetail, getRoleList, updateRole } from '../api/role'

const loading = ref(false)
const roleSaving = ref(false)
const permSaving = ref(false)
const roleDialogVisible = ref(false)
const permDialogVisible = ref(false)
const rolesList = ref([])
const permissionTree = ref([])
const permissionTreeRef = ref(null)

const roleForm = reactive({
  id: null,
  name: '',
  code: '',
  description: '',
  enabled: true,
})

const permForm = reactive({
  roleId: null,
  permissionIds: []
})

const fetchRoles = async () => {
  loading.value = true
  try {
    const res = await getRoleList()
    rolesList.value = res?.data || []
  } finally {
    loading.value = false
  }
}

const fetchPermissionTree = async () => {
  const res = await request.get('/permission/tree')
  permissionTree.value = res?.data || []
}

const resetRoleForm = () => {
  roleForm.id = null
  roleForm.name = ''
  roleForm.code = ''
  roleForm.description = ''
  roleForm.enabled = true
}

const resetPermForm = () => {
  permForm.roleId = null
  permForm.permissionIds = []
}

const collectAllIds = (nodes = [], idSet = new Set()) => {
  nodes.forEach((n) => {
    idSet.add(n.id)
    const children = Array.isArray(n?.children) ? n.children : []
    if (children.length) {
      collectAllIds(children, idSet)
    }
  })
  return idSet
}

const openRoleDialog = async (id) => {
  resetRoleForm()
  roleDialogVisible.value = true
  if (!id) return
  const res = await getRoleDetail({ id })
  const data = res?.data || {}
  roleForm.id = data.id
  roleForm.name = data.name || ''
  roleForm.code = data.code || ''
  roleForm.description = data.description || ''
  roleForm.enabled = data.enabled ?? true
}

const openPermDialog = async (roleId) => {
  resetPermForm()
  permForm.roleId = roleId
  permDialogVisible.value = true

  await fetchPermissionTree()
  const allIdSet = collectAllIds(permissionTree.value, new Set())

  const res = await getRoleDetail({ id: roleId })
  const data = res?.data || {}
  const permissionIds = Array.isArray(data.permissionIds) ? data.permissionIds : []
  permForm.permissionIds = permissionIds.filter((id) => allIdSet.has(id))

  await nextTick()
  permissionTreeRef.value?.setCheckedKeys([])
  permissionTreeRef.value?.setCheckedKeys(permForm.permissionIds)
}

const saveRoleInfo = async () => {
  if (!roleForm.name || !roleForm.code) {
    ElMessage.error('请填写角色名称和编码')
    return
  }

  roleSaving.value = true
  try {
    const payload = {
      name: roleForm.name,
      code: roleForm.code,
      description: roleForm.description,
      enabled: roleForm.enabled
    }

    if (roleForm.id) {
      await updateRole({ id: roleForm.id, ...payload })
      ElMessage.success('角色已更新')
    } else {
      await createRole(payload)
      ElMessage.success('角色已创建')
    }

    roleDialogVisible.value = false
    await fetchRoles()
  } finally {
    roleSaving.value = false
  }
}

const saveRolePermissions = async () => {
  if (!permForm.roleId) {
    ElMessage.error('角色ID不能为空')
    return
  }
  permSaving.value = true
  try {
    const permissionIds = permissionTreeRef.value?.getCheckedKeys() || []
    await updateRole({ id: permForm.roleId, permissionIds })
    ElMessage.success('权限已保存')
    permDialogVisible.value = false
    await fetchRoles()
  } finally {
    permSaving.value = false
  }
}

const removeRole = async (id) => {
  await ElMessageBox.confirm('确认删除该角色吗？', '提示', { type: 'warning' })
  await deleteRole(id)
  ElMessage.success('角色已删除')
  await fetchRoles()
}

onMounted(async () => {
  try {
    await fetchRoles()
  } catch (e) {
    ElMessage.error('加载角色列表失败')
  }
})
</script>

<style scoped>
.roles-container {
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

.roles-content {
  margin-top: 20px;
}

.permission-panel {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 12px;
  min-height: 360px;
  overflow: auto;
}

.permission-panel__title {
  margin-bottom: 12px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.permission-node {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.permission-node__tag {
  margin-left: 6px;
}
</style>
