<template>
  <el-popover
    v-model:visible="panelVisible"
    :width="260"
    trigger="click"
    placement="top-end"
    :teleported="false"
  >
    <template #reference>
      <!-- 齿轮而不是百分比：一组里管的是几个独立值（宽度 / 左右 / 上下 / 占比），
           拿一个数字当 chip 会让人以为"点它就调那个数字"。 -->
      <button class="kb-icon-btn" :aria-label="label">
        <el-icon><Setting /></el-icon>
      </button>
    </template>

    <div class="prefs">
      <!-- 额外的入口（当前是「AI 能力」）。**由调用方给**：
           这个面板只管尺寸/密度/宽度那几组滑块，"AI 能力"是跳另一个页面，
           塞进面板的 props 就把它绑死了；给个插槽，谁要谁放。 -->
      <slot name="extra" />
      <!-- 两组以上时给一条切换栏：一次只看到一组。侧边栏底栏那条弹层本来就窄，
           两组直接堆一起会顶出一屏（每组 3 行控件 + 一个恢复默认）。
           分段控件而不是两个标题并排：标题并排分不出"当前在调哪组"，
           而"调错了组"恰好是这次合并最需要防的事。
           ⚠️ **组名只在这条切换栏里出现一次**，面板正文不再重复画一遍标题
           ——上面已经写着"侧边栏 / 首页"了，下面再写一遍是同一句话画两次。 -->
      <el-radio-group v-if="shown.length > 1" v-model="active" size="small" class="prefs__switch">
        <el-radio-button v-for="g in shown" :key="g.key" :value="g.key">{{ g.title }}</el-radio-button>
      </el-radio-group>

      <div v-for="it in curItems" :key="it.configKey" class="prefs__row">
        <label class="prefs__label" :for="`prefs-${it.configKey}`">{{ it.name }}</label>
        <el-slider
          :id="`prefs-${it.configKey}`"
          class="prefs__slider"
          :min="it.min"
          :max="it.max"
          :step="it.step"
          :model-value="valueOf(it)"
          :show-tooltip="false"
          @update:model-value="set(it.configKey, $event)"
        />
        <span class="prefs__value">{{ valueOf(it) }}{{ it.unit }}</span>
      </div>

      <!-- 树没加载出来（后端没上新版本 / 会话引导失败）时，面板是空的。
           这时要说一句 —— 面板空着而用户以为"没有可调的项"是误导。
           **用词跟全站一致**：「加载失败」在侧边栏那两处也是这三个字。 -->
      <div v-if="!curItems.length" class="prefs__empty">加载失败</div>

      <el-button class="prefs__reset" text size="small" @click="resetCur">
        恢复系统默认
      </el-button>
    </div>
  </el-popover>
</template>

<script setup>
/**
 * 界面偏好面板：**一个组件，一个入口，按 `groups` 传要显示哪几组**，顶部切换栏换组。
 *
 * 为什么不是"侧边栏一个、首页一个"：两个组件就是两份几乎一样的模板 + 一份几乎一样的
 * 样式，加一行控件要敲两遍、样式改一边另一边慢慢漂。这个组件由**配置树**驱动，
 * **加一项 = 配置树上加一行**，这里一行不动。
 *
 * 为什么不摆两个入口（用户明确要求合并）：同一个组件挂在两个位置，用户得先知道
 * "哪条调哪个"——那是把实现的分组泄漏给用户。现在只有一个齿轮，切组就完。
 *
 * **每一行都是滑块**：尺寸类调 px、占比类调百分比（用户定的口径："间隔类的就用
 * xxpx 滑动，调整大小的就用百分比"）。这里**不判断行类型**——以前有个 `kind` 字段
 * 分 slider / seg，seg（紧凑/适中/松散那种三档分段）随侧边栏密度和首页间距一起改成
 * 滑块之后已经零消费者，整套分段控件连同它的样式一起删了。再加回来时记得它得连
 * `css()` 之外的分段分支一起加，别只把模板抄回来。
 *
 * 「恢复默认」只重置**当前这一组**：在首页这组点它不该顺手抹掉侧边栏宽度。
 */
import { ref, computed } from 'vue'
import { Setting } from '@element-plus/icons-vue'
import { getUiPrefs, prefGroups, resetUiPrefs, saveUiPrefs } from '../../utils/uiPrefs'

const props = defineProps({
  // 显示哪几组。key 是配置树上那一组的短名（侧边栏 side / 首页 home / …）。
  groups: { type: Array, required: true },
  label: { type: String, default: '界面设置' }
})

const panelVisible = ref(false)
const prefs = ref(getUiPrefs())
const active = ref(props.groups[0])

/** 配置树上要显示的那几组（树还没到的时候是空的，面板就还没东西可画） */
const shown = computed(() =>
  props.groups.map((k) => prefGroups.value.find((g) => g.key === k)).filter(Boolean)
)
const cur = computed(() => shown.value.find((g) => g.key === active.value))

/**
 * 当前这一组的项。**空数组兜底，不能让模板去读 `cur.items`**：
 * 树没加载出来时 `cur` 是 undefined（那一组不存在），
 * 模板里 `cur.items` 会直接抛错 —— **渲染抛错 = 整个 App 白屏**，
 * 不只是面板坏掉。所以这里把"没有"收敛成"空的项列表"。
 */
const curItems = computed(() => cur.value?.items || [])

/** 存的值可能是没存过（= 用内置默认），显示时要落到具体值上 */
function valueOf(item) {
  return prefs.value[item.configKey] ?? item.defaultValue
}

/**
 * 本地 state 跟着存档走的**唯一入口**：改完存档一律重读，不在这儿手工拼一份镜像。
 *
 * 原来 `set` 是 `{ ...prefs.value, [key]: v }` 手工合并、和存档两条路。`readPrefs()`
 * 还要做「坏值当没存」的归一（`Number.isFinite`），手工合并那一份不过这一层——存档里
 * 存了个越界值时，面板显示的和下次刷新后显示的就不是同一个数。
 * 重读多一次 localStorage + JSON.parse，换来"面板读到的永远是归一后的存档"。
 */
function sync() {
  prefs.value = getUiPrefs()
}

function set(configKey, v) {
  saveUiPrefs({ [configKey]: v })
  sync()
}

/**
 * 「恢复系统默认」：**当前这一组**恢复成后端存的系统默认。
 *
 * ⚠️ **"默认"现在有两层**：后端的系统默认（管理员在「系统管理 → 系统配置」里改）
 * 和配置树上的内置默认。这个按钮恢复的是**前者** —— 后端没有这一项时
 * 才落到后者。所以按钮文案写「恢复系统默认」而不是「恢复默认」，
 * 省得恢复完发现和"默认"不是一回事。
 */
async function resetCur() {
  await resetUiPrefs([active.value])
  sync()
}
</script>

<style scoped>
.prefs {
  display: flex;
  flex-direction: column;
  gap: var(--sp-6);
}
/* 组切换条：占满整宽，两个组名对半分。`--el-radio-button` 的边框交给 EP 主题，
   这里只把它撑满和去掉多余的圆角接缝。
   ⚠️ 不要再补 margin：父级 `.prefs` 的 `gap: var(--sp-6)` 已经给了它和下面第一行的
   间距。原来这里叠了个 `margin-bottom: var(--sp-2)`，实际间距是 8px 而不是 6px，
   而注释里没写过这个 2px 的理由——间距只有一个出处，就是父级那个 gap。 */
.prefs__switch {
  display: flex;
  width: 100%;
}
.prefs__switch :deep(.el-radio-button) {
  flex: 1;
}
.prefs__switch :deep(.el-radio-button__inner) {
  width: 100%;
}
.prefs__row {
  display: flex;
  align-items: center;
  gap: var(--sp-8);
}
.prefs__label {
  font-size: var(--text-sm);
  color: var(--cc-text-2);
  flex-shrink: 0;
  /* 按最长的一个来：知识点那组的「卡片间距」是四个字，写窄了会在标签里折行，
     而一个折成两行的标签会让那行整体变高、下面几行跟着错位。 */
  width: 60px;
}
.prefs__slider {
  flex: 1;
  min-width: 0;
}
.prefs__value {
  font-size: var(--text-xs);
  color: var(--cc-text-4);
  font-variant-numeric: tabular-nums;
  flex-shrink: 0;
  /* 最宽的是「360px」，五个半角字符 */
  width: 40px;
  text-align: right;
}
.prefs__reset {
  align-self: flex-start;
  color: var(--cc-text-3);
}
.prefs__empty {
  font-size: var(--text-sm);
  color: var(--cc-text-3);
}
</style>
