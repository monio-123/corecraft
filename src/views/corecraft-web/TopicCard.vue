<template>
  <!-- draggable 必须写全 "true"，不能只写 draggable。draggable 是枚举属性（合法值只有
       true / false），Vue 把裸属性渲染成 draggable=""，浏览器按非法值退回默认的 auto；
       auto 只有链接和图片能拖，<article> 不算 —— 结果是 dragstart 根本不触发，
       拖一下变成选中文字、松手变成 click 跳进详情页。别再"简化"回裸属性。 -->
  <article
    class="card"
    :class="{ 'is-dragging': dragging }"
    draggable="true"
    @click="onClick"
    @dragstart="onDragStart"
    @dragend="onDragEnd"
  >
    <div class="card__head">
      <h3 class="card__title">{{ topic.title || '未命名' }}</h3>
      <button
        v-if="deletable"
        type="button"
        class="card__del"
        title="删除"
        aria-label="删除"
        @click.stop="emit('delete', topic)"
      >
        <el-icon><Delete /></el-icon>
      </button>
    </div>

    <p v-if="excerpt" class="card__excerpt">{{ excerpt }}</p>

    <!-- 底部只剩标签，没有目录。目录不进卡片：目录一多，平铺的下拉列表就没法用了，
         归类和改类统一走"把卡片拖到右侧那棵树上"这一个动作。 -->
    <div v-if="tags.length" class="card__tags">
      <span v-for="t in tags" :key="t" class="card__tag">{{ t }}</span>
    </div>
  </article>
</template>

<script setup>
/**
 * 知识点卡片。整块都是拖拽源——已归类的也能拖，改目录和归类是同一个动作。
 * 以前只有未归类的可拖、已归类的靠卡片上的目录 chip 改，chip 撤掉之后就没有第二条路了。
 */
import { computed, ref } from 'vue'
import { Delete } from '@element-plus/icons-vue'
import { dnd, TOPIC_DND_TYPE } from './useCorecraftWebStore'

const props = defineProps({
  topic: { type: Object, required: true },
  deletable: { type: Boolean, default: true }
})

const emit = defineEmits(['click', 'delete'])

const dragging = ref(false)
// 拖拽结束后浏览器还会补一个 click，不拦掉就会从"归类"变成误跳详情页。
// dragend 和补发的 click 几乎同时发生，所以延到下一轮宏任务再放开。
let dragged = false

function onClick() {
  if (dragged) return
  emit('click')
}

function onDragStart(e) {
  dragging.value = true
  dragged = true
  e.dataTransfer.setData(TOPIC_DND_TYPE, String(props.topic.id))
  e.dataTransfer.effectAllowed = 'move'
  // 落点浮层在 AppLayout 里，靠 dnd 让它知道"现在有东西可以放"
  dnd.live = true
}

function onDragEnd() {
  dragging.value = false
  dnd.live = false
  dnd.over = null
  setTimeout(() => { dragged = false }, 0)
}

const tags = computed(() => (props.topic.tags || []).map((t) => t.name || t))

// 内容摘要：去掉 markdown 标记和多余空白，截 2 行
const excerpt = computed(() => {
  const raw = props.topic.content || ''
  if (!raw) return ''
  const text = raw
    .replace(/<br\s*\/?>/gi, ' ')
    .replace(/[*#>`_]/g, '')
    .replace(/\s+/g, ' ')
    .trim()
  return text.length > 90 ? `${text.slice(0, 90)}…` : text
})
</script>

<style scoped>
.card {
  background: #fff;
  border: 1px solid #eef0f3;
  border-radius: 12px;
  padding: 14px 15px 12px;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;
  display: flex;
  flex-direction: column;
  min-height: 118px;
}
.card:hover {
  border-color: #cfe0f7;
  box-shadow: 0 3px 14px rgba(31, 35, 41, 0.05);
}
.card.is-dragging {
  opacity: 0.45;
}

.card__head {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}
.card__title {
  flex: 1;
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.5;
  color: #1f2329;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-word;
}

.card__del {
  border: none;
  background: transparent;
  color: #d4d7dd;
  cursor: pointer;
  padding: 2px;
  border-radius: 4px;
  display: flex;
  flex-shrink: 0;
  opacity: 0;
  transition: opacity 0.15s, color 0.15s;
}
.card:hover .card__del {
  opacity: 1;
}
.card__del:hover {
  color: #f56c6c;
  background: #fef0f0;
}

.card__excerpt {
  flex: 1;
  margin: 6px 0 0;
  font-size: 12.5px;
  line-height: 1.65;
  color: #8a9099;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-word;
}

.card__tags {
  display: flex;
  gap: 4px;
  margin-top: 10px;
  min-width: 0;
  overflow: hidden;
}
.card__tag {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 4px;
  background: #f4f5f7;
  color: #9096a0;
  white-space: nowrap;
  flex-shrink: 0;
}
</style>
