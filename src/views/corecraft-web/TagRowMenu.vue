<template>
  <RowMenu title="标签操作" :items="ITEMS" @command="onCommand" @changed="emit('changed')" />
</template>

<script setup>
/**
 * 标签行右侧的「⋯」：重命名 / 删除。壳在 RowMenu 里，这里只有标签自己的逻辑。
 *
 * 之前标签只能"筛选"，没有任何管理入口——打错一个标签只能去每条知识点上手改，
 * 或者留着它一直污染筛选列表。目录行早就有这个菜单，标签这边是补齐。
 */
import RowMenu from './RowMenu.vue'
import { renameTag, confirmDeleteTag } from './useCorecraftWebStore'

const props = defineProps({
  tag: { type: Object, required: true }
})
// 改名/删除后标签表变了。往上说一声是为了让调用方把"正按这个名字筛选"的状态收掉——
// 那个名字已经不存在了，继续筛就是空列表，用户会以为数据没了。
const emit = defineEmits(['changed'])

const ITEMS = [
  { command: 'rename', label: '重命名' },
  { command: 'delete', label: '删除标签', divided: true }
]

async function onCommand(cmd) {
  const done = cmd === 'rename' ? await renameTag(props.tag) : await confirmDeleteTag(props.tag)
  if (done) emit('changed')
}
</script>
