<template>
  <div class="error-page">
    <div class="error-card">
      <h1>403</h1>
      <p>抱歉，您没有权限访问此页面</p>
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
  color: #e6a23c;
  margin: 0;
}

.error-card p {
  font-size: 16px;
  color: #909399;
  margin: 16px 0 24px;
}
</style>
