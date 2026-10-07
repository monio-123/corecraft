<template>
  <RowMenu title="目录操作" :items="ITEMS" @command="onCommand" />
</template>

<script setup>
/**
 * 目录行右侧的「⋯」：全站目录行唯一的操作入口。壳（外观/旋转/点击拦截）在 RowMenu 里，
 * 这里只留目录自己的逻辑。
 *
 * 之前每行右侧要按"有没有下级"在「›」和「＋」之间二选一，弹层里又是另一套「＋ －」，
 * 同一个意思写了两遍还写得不一样——而且二选一还把"给已有子目录的一级目录再建一个兄弟"
 * 这条路整个关掉了（侧边栏给 › 的时候就没有 ＋，弹层里又没有这一级自己）。
 * 收成一个菜单之后，行上不再有任何"该显示哪个图标"的判断。
 */
import RowMenu from './RowMenu.vue'
import { confirmDeleteCategory } from './useCorecraftWebStore'

const props = defineProps({
  category: { type: Object, required: true }
})
const emit = defineEmits(['add', 'rename', 'removed'])

// 菜单项写死成常量而不是在 onCommand 里比字符串：加一项只要动这一个数组，
// 加错 command 名会在模板里就暴露（选项和分支对不上），不会变成"点了没反应"。
const ITEMS = [
  { command: 'add', label: '新增子目录' },
  { command: 'rename', label: '重命名' },
  { command: 'delete', label: '删除目录', divided: true }
]

async function onCommand(cmd) {
  if (cmd === 'add') emit('add', props.category)
  else if (cmd === 'rename') emit('rename', props.category)
  // 删除是终态：真删掉了要往上说一声，弹层可能正锚定在这个节点上，不收的话停在一棵
  // 少了一截的树上。确认框取消了不发。
  else if (await confirmDeleteCategory(props.category)) emit('removed')
}
</script>
