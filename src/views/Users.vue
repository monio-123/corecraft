<template>
  <div class="users-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <h2>用户管理</h2>
          <el-button type="primary">
            <el-icon><Plus /></el-icon>
            新增用户
          </el-button>
        </div>
      </template>
      
      <!-- 用户管理内容区域 -->
      <div class="users-content">
        <el-table :data="usersList" stripe style="width: 100%">
          <el-table-column prop="id" label="用户ID" width="80" />
          <el-table-column prop="username" label="用户名" />
          <el-table-column prop="name" label="姓名" />
          <el-table-column prop="email" label="邮箱" />
          <el-table-column prop="role" label="角色" width="100" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="scope">
              <el-switch v-model="scope.row.status" />
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="创建时间" width="180" />
          <el-table-column label="操作" width="200">
            <template #default="scope">
              <el-button type="primary" size="small" @click="editUser(scope.row)">
                <el-icon><Edit /></el-icon>
                编辑
              </el-button>
              <el-button type="danger" size="small" @click="deleteUser(scope.row.id)">
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

// 模拟用户数据
const usersList = ref([
  { id: 1, username: 'admin', name: '系统管理员', email: 'admin@example.com', role: '管理员', status: true, createTime: '2024-01-01 10:00:00' },
  { id: 2, username: 'user1', name: '张三', email: 'user1@example.com', role: '普通用户', status: true, createTime: '2024-01-02 14:30:00' },
  { id: 3, username: 'user2', name: '李四', email: 'user2@example.com', role: '普通用户', status: false, createTime: '2024-01-03 09:15:00' }
])

const editUser = (row) => {
  ElMessage.info(`编辑用户: ${row.name}`)
}

const deleteUser = (id) => {
  ElMessage.warning(`删除用户ID: ${id}`)
}
</script>

<style scoped>
.users-container {
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

.users-content {
  margin-top: 20px;
}
</style>