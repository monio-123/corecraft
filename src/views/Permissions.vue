<template>
  <div class="permissions-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <h2>权限管理</h2>
          <el-button type="primary">
            <el-icon><Plus /></el-icon>
            新增权限
          </el-button>
        </div>
      </template>
      
      <!-- 权限管理内容区域 -->
      <div class="permissions-content">
        <el-table :data="permissionsList" stripe style="width: 100%">
          <el-table-column prop="id" label="权限ID" width="80" />
          <el-table-column prop="name" label="权限名称" />
          <el-table-column prop="code" label="权限标识" />
          <el-table-column prop="description" label="权限描述" />
          <el-table-column prop="module" label="所属模块" width="120" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="scope">
              <el-switch v-model="scope.row.status" />
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="创建时间" width="180" />
          <el-table-column label="操作" width="200">
            <template #default="scope">
              <el-button type="primary" size="small" @click="editPermission(scope.row)">
                <el-icon><Edit /></el-icon>
                编辑
              </el-button>
              <el-button type="danger" size="small" @click="deletePermission(scope.row.id)">
                <el-icon><Delete /></el-icon>
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Edit, Delete } from '@element-plus/icons-vue'

// 模拟权限数据
const permissionsList = ref([
  { id: 1, name: '查看首页', code: 'home.view', description: '查看系统首页', module: '系统', status: true, createTime: '2024-01-01 10:00:00' },
  { id: 2, name: '管理角色', code: 'roles.manage', description: '管理系统角色', module: '系统管理', status: true, createTime: '2024-01-02 14:30:00' },
  { id: 3, name: '管理用户', code: 'users.manage', description: '管理系统用户', module: '系统管理', status: true, createTime: '2024-01-03 09:15:00' },
  { id: 4, name: '管理权限', code: 'permissions.manage', description: '管理系统权限', module: '系统管理', status: true, createTime: '2024-01-04 16:45:00' }
])

const editPermission = (row) => {
  ElMessage.info(`编辑权限: ${row.name}`)
}

const deletePermission = (id) => {
  ElMessage.warning(`删除权限ID: ${id}`)
}
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

.permissions-content {
  margin-top: 20px;
}
</style>