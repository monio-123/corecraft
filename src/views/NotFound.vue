<template>
  <div class="error-page">
    <div class="error-card">
      <h1>404</h1>
      <p>页面不存在</p>
      <el-button type="primary" @click="$router.push('/')">返回首页</el-button>
    </div>
  </div>
</template>

<script setup>
</script>

<style scoped>
.error-page {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100%;
  min-height: 400px;
}

.error-card {
  text-align: center;
}

.error-card h1 {
  font-size: 72px;
  color: #409eff;
  margin: 0;
}

.error-card p {
  font-size: 16px;
  color: #909399;
  margin: 16px 0 24px;
}
</style>
