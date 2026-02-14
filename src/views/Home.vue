<template>
  <div class="home">
    <h1>欢迎来到首页</h1>
    <el-button type="danger" @click="handleLogout">退出登录</el-button>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

const router = useRouter()

const handleLogout = () => {
  localStorage.removeItem('token')
  ElMessage.success('退出成功')
  router.push('/login')
}
</script>

<style scoped>
.home {
  padding: 20px;
  text-align: center;
}
</style> 