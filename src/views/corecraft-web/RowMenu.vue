<template>
  <!-- 行尾「⋯」的壳：竖排三个点的下拉菜单。
       目录行和标签行用的是同一套外观和交互，抽出来共用，逻辑各留在各自的包装里。

       trigger="click" 而不是 hover：菜单 teleport 到 body，hover 触发时鼠标从 ⋯ 滑到菜单的
       路径会跨出触发元素的边界，菜单当场就关了（之前级联菜单踩过这个坑）。点开就没这问题。 -->
  <el-dropdown trigger="click" @command="(cmd) => emit('command', cmd)" @visible-change="onVisible">
    <!-- prevent.stop 必须写在这里：调用方那一行本身通常也是可点的（RouterLink / button），
         只 stop 拦得住冒泡，拦不住浏览器照着 href 或外层的 click 走。写一次，两处都受益。 -->
    <button class="row-menu__btn" type="button" :title="title" @click.prevent.stop>
      <!-- 转 90° 变竖排 ⋮：EP 自带的 MoreFilled / More 都是横排 ⋯，Operation 是三条横线，
           都不是竖排 ellipsis。列表是纵向的，竖排的点跟"这一行"对齐，横排的像浮在行上。
           转的是图标，按钮本身是方的，不影响点击区。 -->
      <el-icon class="row-menu__icon"><MoreFilled /></el-icon>
    </button>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item
          v-for="item in items"
          :key="item.command"
          :command="item.command"
          :divided="item.divided"
        >
          {{ item.label }}
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<script setup>
/**
 * 行尾「⋯」菜单的壳。菜单项由调用方给（`items: { command, label, divided? }[]`），
 * 点到哪一项由 `command` 事件抛出去——**这里不做任何业务动作**。
 *
 * 为什么单独抽一个组件：目录行和标签行都要"行尾一个竖排 ⋯ + 点开菜单"，
 * 外观、旋转、prevent.stop 这些一字不差。各写一份的话，改一次样式要改两个文件，
 * 而且早晚会写成不一样的两份。
 */
import { MoreFilled } from '@element-plus/icons-vue'

const props = defineProps({
  title: { type: String, default: '操作' },
  items: { type: Array, required: true }
})
const emit = defineEmits(['command', 'menu'])

function onVisible(v) {
  // 菜单 teleport 到 body，弹层那边要靠它知道"这个菜单开着没"（级联菜单用它决定何时收）
  emit('menu', v)
}
</script>

<style scoped>
.row-menu__btn {
  display: flex;
  align-items: center;
  border: none;
  background: transparent;
  color: #c0c4cc;
  cursor: pointer;
  padding: calc(3px * var(--ui-scale));
  border-radius: calc(4px * var(--ui-scale));
  font-size: calc(14px * var(--ui-scale));
  flex-shrink: 0;
  /* 默认不显形，由**所在那一行**决定：行在自己 hover / focus-within 时设
     `--row-menu-show: 1`（自定义属性会继承到子组件里），这里只管照着用。
     为什么不直接在这里写「父行:hover → 本按钮」那条规则：那样要把调用方的
     class 名硬编进子组件，父子耦合方向就反了。默认值是 1——
     没有主动声明要隐藏的行，⋯ 照常常显。 */
  opacity: var(--row-menu-show, 1);
  transition: opacity 0.15s;
}
.row-menu__btn:hover {
  color: #606a75;
  background: #f2f4f7;
}
.row-menu__icon {
  transform: rotate(90deg);
}
</style>
