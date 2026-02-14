<template>
  <div class="roles-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <h2>角色管理</h2>
          <el-button type="primary">
            <el-icon><Plus /></el-icon>
            新增角色
          </el-button>
        </div>
      </template>
      
      <!-- 角色管理内容区域 -->
      <div class="roles-content">
        <el-table :data="rolesList" stripe style="width: 100%">
          <el-table-column prop="id" label="角色ID" width="80" />
          <el-table-column prop="name" label="角色名称" />
          <el-table-column prop="description" label="角色描述" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="scope">
              <el-switch v-model="scope.row.status" />
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="创建时间" width="180" />
          <el-table-column label="操作" width="200">
            <template #default="scope">
              <el-button type="primary" size="small" @click="editRole(scope.row)">
                <el-icon><Edit /></el-icon>
                编辑
              </el-button>
              <el-button type="danger" size="small" @click="deleteRole(scope.row.id)">
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

// 模拟角色数据
const rolesList = ref([
  { id: 1, name: '管理员', description: '系统管理员', status: true, createTime: '2024-01-01 10:00:00' },
  { id: 2, name: '普通用户', description: '普通系统用户', status: true, createTime: '2024-01-02 14:30:00' },
  { id: 3, name: '访客', description: '只读权限用户', status: false, createTime: '2024-01-03 09:15:00' }
])

const editRole = (row) => {
  ElMessage.info(`编辑角色: ${row.name}`)
}

const deleteRole = (id) => {
  ElMessage.warning(`删除角色ID: ${id}`)
}
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
</style>