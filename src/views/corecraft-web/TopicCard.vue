<template>
  <div class="topic-card-wrap" @click="handleClick">
    <button
      v-if="deletable"
      type="button"
      class="topic-card__del"
      title="删除"
      aria-label="删除"
      @click.stop="handleDelete"
    >
      <el-icon><Delete /></el-icon>
    </button>
    <div class="topic-card__title">{{ topic.title || '未命名' }}</div>
    <div v-if="showTags && filteredTags.length" class="topic-card__tags">
      <span
        v-for="(tag, i) in filteredTags.slice(0, 3)"
        :key="i"
        class="topic-tag"
      >{{ formatTagLabel(tag) }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Delete } from '@element-plus/icons-vue'
import { formatTagLabel, encodeTag } from './useCorecraftWebStore'

const props = defineProps({
  topic: { type: Object, required: true },
  // 在按树视图下，topic 卡片不展示自己所在 tag 节点对应的标签（树结构里已经体现了）
  hideTagKey: { type: String, default: null },
  // 按树视图下完全不展示 tag pill；列表视图默认展示
  showTags: { type: Boolean, default: true },
  // 是否显示删除按钮；目前总是 true，预留可关
  deletable: { type: Boolean, default: true }
})

const emit = defineEmits(['click', 'delete'])

const filteredTags = computed(() => {
  if (!props.showTags) return []
  return (props.topic.tags || []).filter(
    (tag) => !props.hideTagKey || encodeTag(tag) !== props.hideTagKey
  )
})

function handleClick() {
  emit('click')
}

function handleDelete() {
  emit('delete', props.topic)
}
</script>

<style scoped>
.topic-card-wrap {
  position: relative;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 14px 16px;
  margin-bottom: 12px;
  cursor: pointer;
  transition: all 0.15s;
}
.topic-card-wrap:hover {
  border-color: #409eff;
  background: #f5f9ff;
  box-shadow: 0 4px 16px rgba(64, 158, 255, 0.16);
  transform: translateY(-2px);
}

.topic-card__title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  padding-right: 24px;
}
.topic-card__title::before {
  content: '📄 ';
  font-size: 13px;
}

.topic-card__tags {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
  margin-top: 6px;
}
.topic-tag {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
  background: #f0f2f5;
  color: #606266;
}

/* 常驻浅色删除按钮：右上角；hover 时变红提示危险 */
.topic-card__del {
  position: absolute;
  top: 6px;
  right: 6px;
  width: 24px;
  height: 24px;
  padding: 0;
  border: none;
  background: transparent;
  color: #c0c4cc;
  cursor: pointer;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  line-height: 1;
  transition: all 0.15s;
}
.topic-card__del:hover {
  background: #fef0f0;
  color: #f56c6c;
}
</style>
