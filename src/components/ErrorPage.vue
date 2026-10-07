<template>
  <div class="error-page">
    <div class="error-card">
      <h1 :style="{ color }">{{ code }}</h1>
      <p>{{ message }}</p>
      <el-button type="primary" @click="$router.push('/')">返回首页</el-button>
    </div>
  </div>
</template>

<script setup>
/**
 * 出错页的壳。403 / 404 只有三处不同——大号数字、一句话、数字的颜色，
 * 结构、居中方式、按钮一字不差，所以只留这一个组件，两个页面各自传三个值。
 *
 * 颜色走 EP 的 token 而不是原来的硬编码 hex：`#e6a23c` 就是 `--el-color-warning`，
 * `#409eff` 就是 `--el-color-primary`。写死 hex 的时候改主题就得回来找这两个数。
 */
defineProps({
  code: { type: [String, Number], required: true },
  message: { type: String, required: true },
  color: { type: String, required: true }
})
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
  font-size: var(--text-hero);
  margin: 0;
}

.error-card p {
  font-size: var(--text-lg);
  color: #909399;
  margin: var(--sp-12) 0 var(--sp-14);
}
</style>
