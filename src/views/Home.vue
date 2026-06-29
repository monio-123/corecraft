<template>
  <div class="home">
    <div class="welcome-card">
      <h1>欢迎使用 CoreCraft</h1>
      <p>使用左侧菜单开始管理系统</p>
    </div>
  </div>
</template>

<style scoped>
.home {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100%;
  padding: 40px 0;
}

.welcome-card {
  text-align: center;
  padding: 60px 80px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

.welcome-card h1 {
  font-size: 28px;
  color: #303133;
  margin: 0 0 12px;
}

.welcome-card p {
  font-size: 15px;
  color: #909399;
  margin: 0;
}
</style>
