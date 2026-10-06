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
  min-height: calc(400px * var(--ui-scale));
}

.error-card {
  text-align: center;
}

.error-card h1 {
  font-size: calc(72px * var(--ui-scale));
  color: #409eff;
  margin: 0;
}

.error-card p {
  font-size: calc(16px * var(--ui-scale));
  color: #909399;
  margin: calc(16px * var(--ui-scale)) 0 calc(24px * var(--ui-scale));
}
</style>
