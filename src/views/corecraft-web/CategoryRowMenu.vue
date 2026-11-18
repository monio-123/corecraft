<template>
  <RowMenu title="目录操作" :items="items" :btn-class="btnClass" @command="onCommand" />
</template>

<script setup>
/**
 * 目录行右侧的「⋯」：全站目录行唯一的操作入口。壳（外观/旋转/点击拦截）在 RowMenu 里，
 * 这里只留目录自己的逻辑。
 *
 * 目录分枝与叶子承载严格互斥：
 * - 已有子目录（非叶子）不显示「写知识点」；
 * - 已有直属知识点（承载叶子）不显示「新增子目录」。
 */
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import RowMenu from './RowMenu.vue'
import { confirmDeleteCategory } from './categoryDeleteDialog'
import {
  getChildCategories, hasDirectTopics, getCategorySubtreeIds,
  getCategoryPath, getFullTopicIndex
} from './useCorecraftWebStore'
import { openExamTab } from './exam'

const props = defineProps({
  category: { type: Object, required: true },
  /** 透传给 RowMenu 触发按钮的额外 class（调用方按所在行定制外观，如树里的幽灵横点）。 */
  btnClass: { type: String, default: '' }
})
const emit = defineEmits(['add', 'points', 'rename', 'removed'])

const route = useRoute()
const router = useRouter()

/** 该目录子树内所有知识点 / 已写正文（可考）知识点 */
const subtreeStats = computed(() => {
  const id = props.category?.id
  if (id == null) return { total: 0, examIds: [] }
  const ids = getCategorySubtreeIds(id)
  let total = 0
  const examIds = []
  for (const t of getFullTopicIndex()) {
    if (t.categoryId != null && ids.has(t.categoryId)) {
      total += 1
      if (t.hasContent) examIds.push(t.id)
    }
  }
  return { total, examIds }
})

const items = computed(() => {
  const id = props.category?.id
  const hasChildren = id != null && getChildCategories(id).length > 0
  const hasTopics = id != null && hasDirectTopics(id)
  const { total, examIds } = subtreeStats.value
  const list = []
  if (!hasTopics) list.push({ command: 'add', label: '新增子目录', icon: 'plus' })
  if (!hasChildren) list.push({ command: 'points', label: '写知识点', icon: 'edit' })
  if (hasChildren && total > 0) list.push({ command: 'cards', label: '查看卡片', icon: 'grid' })
  if (examIds.length > 0) list.push({ command: 'exam', label: '考试', icon: 'note' })
  list.push({ command: 'rename', label: '重命名', icon: 'text' })
  list.push({ command: 'delete', label: '删除目录', icon: 'trash', tone: 'danger', divided: true })
  return list
})

async function onCommand(cmd) {
  const c = props.category
  if (cmd === 'add') emit('add', c)
  else if (cmd === 'points') emit('points', c)
  else if (cmd === 'cards') {
    const chain = getCategoryPath(c.id)
    const rootId = chain.length ? chain[0].id : c.id
    router.push({
      path: '/app/learn',
      query: { ...route.query, tree: rootId, category: c.id, view: 'cards' }
    })
  } else if (cmd === 'exam') {
    const { examIds } = subtreeStats.value
    if (!examIds.length) return
    openExamTab(examIds, c.name || '考试', `cat-${c.id}`, c.id)
  } else if (cmd === 'rename') emit('rename', c)
  else if (await confirmDeleteCategory(c)) emit('removed')
}
</script>
