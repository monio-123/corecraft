<template>
  <!-- 行尾「⋯」的壳：竖排三个点的下拉菜单。
       目录行和标签行用的是同一套外观和交互，抽出来共用，逻辑各留在各自的包装里。

       trigger="click" 而不是 hover：菜单 teleport 到 body，hover 触发时鼠标从 ⋯ 滑到菜单的
       路径会跨出触发元素的边界，菜单当场就关了（之前级联菜单踩过这个坑）。点开就没这问题。 -->
  <!-- ⚠️ **persistent=false 是性能项，不是风格选择**（2026-11-03 晚 · 性能）。
       persistent=true（EP 默认）时，菜单内容在**挂载那一刻**就渲染出来，
       于是 floating-ui 的 createPopper 立刻被调用（usePopper 里 watch 两个 ref 都非空就建实例）
       ⇒ **每个下拉菜单在挂载时就付一次建实例 + 摆位的钱**，哪怕用户一次都没点开它。

       实测（真实 TopicCard 组件，20 张卡片，浏览器里跑 5 次取中位数）：
         带 el-dropdown : 挂载 71.8ms ｜ 画出一帧 133.3ms
         不带 dropdown  : 挂载  2.8ms ｜ 画出一帧  18.4ms
       ⇒ **20 个下拉菜单白花 114.9ms**。知识点列表一页就是 20 张卡片 ⇒ 每次进卡片视图
       都先付这 115ms，这就是「点目录跳卡片页慢半拍」的主因（网络只占几十毫秒）。

       persistent=false 时内容**首次点开才渲染** ⇒ 挂载时一个 popper 都不建，
       列表里几十个菜单一个都不用花钱；真点开了再建那一个。
       ⚠️ **首开之后翻成 persistent=true（keepMounted，2026-11-13）**：false 的代价是
       **每次打开都重建弹层**（挂载 + createPopper + 弹出动画前的强制布局）——
       「点 ⋯ 到菜单弹出这段卡」的一部分就是它。首开那一次逃不掉（内容还没渲染过），
       但开过一次之后的重开不该再付 ⇒ visible-change 首次为 true 时翻一次、不回退。 -->
  <el-dropdown
    trigger="click"
    :persistent="keepMounted"
    @visible-change="onDropdownShow"
    @command="(cmd) => emit('command', cmd)"
  >
    <!-- prevent.stop 必须写在这里：调用方那一行本身通常也是可点的（RouterLink / button），
         只 stop 拦得住冒泡，拦不住浏览器照着 href 或外层的 click 走。写一次，两处都受益。 -->
    <button
      class="row-menu__btn"
      :class="btnClass"
      type="button"
      :title="title"
      @click.prevent.stop
    >
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
          :class="{ 'row-menu__item--danger': item.tone === 'danger', 'row-menu__item--has-meta': item.meta }"
        >
          <KbSvgIcon v-if="item.icon" class="row-menu__item-icon" :name="item.icon" :sw="1.7" />
          <span class="row-menu__item-label">{{ item.label }}</span>
          <span v-if="item.meta" class="row-menu__item-meta">{{ item.meta }}</span>
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<script setup>
/**
 * 行尾「⋯」菜单的壳。菜单项由调用方给（`items: { command, label, icon?, meta?, tone?, divided? }[]`），
 * 点到哪一项由 `command` 事件抛出去——**这里不做任何业务动作**。
 *
 * 项的形态（2026-11-13）：`icon`（KbSvgIcon 名）+ `label` + 可选右对齐 `meta`（状态值，
 * 如掌握度的「不参与」——不再把状态塞进 label 的括号里）+ `tone: 'danger'`（删除类动作，
 * 红字红 hover）。长相全在 base.css 的下拉皮肤里，这里只做拼装。
 *
 * 为什么单独抽一个组件：目录行和标签行都要"行尾一个竖排 ⋯ + 点开菜单"，
 * 外观、旋转、prevent.stop 这些一字不差。各写一份的话，改一次样式要改两个文件，
 * 而且早晚会写成不一样的两份。
 */
import { MoreFilled } from '@element-plus/icons-vue'
import KbSvgIcon from '../../components/corecraft-web/KbSvgIcon.vue'
import { useDropdownKeepMounted } from '../../utils/dropdown'

const props = defineProps({
  title: { type: String, default: '操作' },
  items: { type: Array, required: true },
  /**
   * 按钮上的额外 class。壳自己的 `.row-menu__btn` 定形状和配色，这里只给"这个菜单
   * 在哪种行上"补规则（知识点卡片要 hover 才显、还要绝对定位到卡片右上角）。
   *
   * 显隐**不在这里做**：`opacity: var(--row-tools, 1)` 读的是所在行设的自定义属性，
   * 调用方改那个变量就行（自定义属性会继承到本组件里），两边不用抢同一条 opacity。
   */
  btnClass: { type: String, default: '' }
})
const emit = defineEmits(['command'])

/** 首开保持挂载（见模板顶部 persistent 注释）：收敛在 utils/dropdown.js，全站下拉共用 */
const { keepMounted, onDropdownShow } = useDropdownKeepMounted()
</script>

<style scoped>
.row-menu__btn {
  display: flex;
  align-items: center;
  border: none;
  background: transparent;
  /* 灰度走 token（--cc-text-4 = 计数 / 占位 / 禁用那一档）：
     原来这里写死一个灰度字面量，AI 设置页那边又出现同一个字面量当 fallback，
     同一个颜色两处各写一份，改一处就漂。 */
  color: var(--cc-text-4);
  cursor: pointer;
  padding: var(--sp-3);
  border-radius: var(--radius-xs);
  /* §4.2：原来是 --text-md(14px)。这个「⋯」长在三种行上（侧栏书名 15px、
     树行 15px、卡片标题 17px），而 AppLayout 的注释刚刚论证过"15px 的线性图标
     实际只有 ~12px 的墨迹高度，摆在 15px 的中文旁边明显小一号"并因此把侧栏那两个
     按钮提到 18px ⇒ **同一条论证没有应用到 ⋯ 上**。
     ⇒ 提到 --text-2xl(18)，与 .app-side__op-btn 同规格。
     ⚠️ **盒子保持 20×20 不变**（padding: var(--sp-3) + 18px 字 ≈ 20px）——
     统一到 --ctl-sm(28) 会把树行从 ≈50px 顶到 ≈56px，50 行的树多 300px。 */
  font-size: var(--text-2xl);
  flex-shrink: 0;
  /* 默认不显形，由**所在那一行**决定：行在自己 hover / focus-within 时设
     `--row-tools: 1`（自定义属性会继承到子组件里），这里只管照着用。
     为什么不直接在这里写「父行:hover → 本按钮」那条规则：那样要把调用方的
     class 名硬编进子组件，父子耦合方向就反了。默认值是 1——
     没有主动声明要隐藏的行，⋯ 照常常显。

     叫 `row-tools` 而不是 `row-menu-show`：侧边栏目录行用它统一控制三样
     （数量 / ⋯ / 展开箭头），只有 ⋯ 的时候叫 menu 才贴切。 */
  opacity: var(--row-tools, 1);
  transition: opacity 0.15s;
}
.row-menu__btn:hover {
  /* §4.3 的硬编色映射表：#606a75 → --cc-text-2、#f2f4f7 → --cc-surface-2。
     ⚠️ §4.4(a) hover 两档：这一颗是**图标型**可点 ⇒ 落"图标型"那档
     （--cc-primary 字 + --cc-primary-soft 底），不是文字型那档。 */
  color: var(--cc-primary);
  background: var(--cc-primary-soft);
}
.row-menu__icon {
  transform: rotate(90deg);
}
</style>
