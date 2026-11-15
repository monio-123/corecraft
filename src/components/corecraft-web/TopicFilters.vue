<template>
  <!--
    卡片视图的筛选 + 动作条（2026-11-08 用户：「所有卡片的视图都是同一个 我不要维护两套」）。
    ⚠️ **全站唯一一套**：树视图的卡片模式（标题行 tools 槽）和独立卡片视图页（面包屑行）
    用的是**同一个组件**——改这里，两边同时变；再也不许出现第二份筛选 UI。
    状态（时间/类型）由外层持有（URL 是真相源），这里只负责呈现和发事件。
    字体一律继承主题（button 由 base.css 的 font: inherit 兜底），配色全走 --cc-* token。 -->
  <div class="tf">
    <!-- ===== 批量模式（2026-11-11）=====
         进了批量模式，这一条工具条**整条换成批量条**，不是"在筛选旁边再长一排按钮"：
         这时候筛选/考试/AI 归类都还在屏幕上就会出现两种"对谁生效"的动作挨着，
         用户没法判断「删除」删的是筛出来的全部还是勾上的那几张。
         ⚠️ **筛选也是藏掉的**（不是禁用）：改筛选会清空已选（只认当前页），
         把它留在那儿只会诱导用户点，然后看着勾好的消失。

         ⚠️ **这一整套只在「待归类」屏出现**（`showBatch`，由外层给）：已归好类的
         卡片本来就各自有 ⋯ 菜单，批量归类/删除是"清理待归类堆积"这个场景的动作。
         外层在离开待归类时会自动退出批量模式 ⇒ 这里不用再判一次。

         控件语言全部沿用本组件既有那几样，**没有一个新形态**：
         - 「批量 · 已选 N 条 ×」= `.tf__chip`（和生效中的筛选 chip 同一句法：
           现在有这个条件、点 × 撤掉）
         - 其余四个动作 = `.tf__act`（和 AI 归类 / 考试同一族描边钮）
         - 禁用 = `.tf__act:disabled`（已选 0 时四个动作都不可点） -->
    <template v-if="batchMode">
      <button type="button" class="tf__chip" @click="emit('toggle-batch')">
        批量 · 已选 {{ batchCount }} 条<KbSvgIcon name="close" :sw="2.4" />
      </button>
      <!-- 全选本页：**只选当前页**（用户 2026-11-11 拍板）。分页下"全选"必须说清楚
           范围，所以文案里带「本页」两个字，和"5 页一起选"划清界限。
           当前页都选满了 ⇒ 变成「取消全选」，同一个位置上直接反选。 -->
      <button
        v-if="pageCount"
        type="button"
        class="tf__act"
        @click="emit('select-all')"
      >{{ allSelected ? '取消全选' : '全选本页' }}</button>
      <button type="button" class="tf__act" :disabled="!batchCount" @click="emit('batch-categorize')">
        归类到目录
      </button>
      <button
        v-if="organizeCount"
        type="button"
        class="tf__act"
        :disabled="!batchCount"
        @click="emit('batch-organize')"
      >AI 归类</button>
      <button type="button" class="tf__act tf__act--danger" :disabled="!batchCount" @click="emit('batch-delete')">
        删除
      </button>
    </template>

    <template v-else>
      <el-popover
        v-model:visible="panelOpen"
        width="248"
        trigger="click"
        placement="bottom-end"
        :teleported="false"
        :persistent="false"
        popper-class="tf__popper"
      >
        <template #reference>
          <button type="button" class="tf__trigger" :class="{ 'is-on': active }" aria-label="筛选">
            <KbSvgIcon name="filter" :sw="1.8" />
            <span>筛选</span>
            <KbSvgIcon name="caret-down" :sw="2" />
          </button>
        </template>
        <div class="tf__panel">
          <div class="tf__group">
            <p class="tf__group-name">时间</p>
            <button
              v-for="r in ranges"
              :key="r.kind"
              type="button"
              class="tf__row"
              :class="{ 'is-on': r.kind === timeRange }"
              @click="pick(r.kind)"
            >
              <span class="tf__check"><KbSvgIcon v-if="r.kind === timeRange" name="check" :sw="2.2" /></span>
              <span class="tf__label">{{ r.label }}</span>
              <span class="tf__count">{{ r.count }}</span>
            </button>
          </div>
          <div class="tf__sep" />
          <div class="tf__group">
            <p class="tf__group-name">类型</p>
            <button type="button" class="tf__row" :class="{ 'is-on': typeFilter == null }" @click="pickType(null)">
              <span class="tf__check"><KbSvgIcon v-if="typeFilter == null" name="check" :sw="2.2" /></span>
              <span class="tf__label">全部类型</span>
            </button>
            <button
              v-for="t in types"
              :key="t.id"
              type="button"
              class="tf__row"
              :class="{ 'is-on': typeFilter === t.id }"
              @click="pickType(t.id)"
            >
              <span class="tf__check"><KbSvgIcon v-if="typeFilter === t.id" name="check" :sw="2.2" /></span>
              <span class="tf__label">{{ t.label }}</span>
              <span class="tf__count">{{ t.count }}</span>
            </button>
          </div>
        </div>
      </el-popover>

      <!-- 生效中的筛选 = 可摘的 chip（值直接印出来，点 × 移除）。 -->
      <button v-if="rangeChip" type="button" class="tf__chip" @click="pick('all')">
        {{ rangeChip }}<KbSvgIcon name="close" :sw="2.4" />
      </button>
      <button v-if="typeChip" type="button" class="tf__chip" @click="pickType(null)">
        {{ typeChip }}<KbSvgIcon name="close" :sw="2.4" />
      </button>

      <!-- AI 归类：**提交中禁用**（点了就变成后台任务，再点一下会重复提交一个任务）。
           提交很快（只是建任务），但失败的窗口是存在的（队列满 / 后端没起）。
           ⚠️ **忙碌态用统一按钮形态**（2026-11-13 统一加载层）：图形 + 文案，
           宽度锁死（.tf__act--busy 的 min-width）—— 不锁的话按钮会横向跳一下，
           后面几个按钮整排都在动。 -->
      <button
        v-if="organizeCount"
        type="button"
        class="tf__act"
        :class="{ 'tf__act--busy': organizing }"
        :disabled="organizing"
        @click="emit('organize')"
      >
        <KbLoader v-if="organizing" size="sm" />
        <KbSvgIcon v-else name="sparkle" :sw="1.8" />
        <span>{{ organizing ? '提交中' : `AI 归类 ${organizeCount}` }}</span>
      </button>
      <button v-if="examCount" type="button" class="tf__act" @click="emit('exam')">
        <KbSvgIcon name="edit" :sw="1.8" /><span>考试 {{ examCount }}</span>
      </button>
      <!-- 批量开关：**排在最后一个**（它是"换一种操作方式"，不是又一个即点即做的动作）；
           ⚠️ **只在待归类屏画**（`showBatch`）——别的屏上出一颗"批量"是噪声；
           当前页没有卡片也不画（没什么可勾的）。
           图标用 `check` —— 全站"选中"这件事一直是这一个勾（筛选弹层的勾位、
           侧栏的选中柱同源），不为它另造一个"多选框"图形。 -->
      <button v-if="showBatch && pageCount" type="button" class="tf__act" @click="emit('toggle-batch')">
        <KbSvgIcon name="check" :sw="1.9" /><span>批量</span>
      </button>
    </template>
  </div>
</template>

<script setup>
/**
 * 卡片视图的筛选 + 动作条（**全站唯一一套**，2026-11-08）。
 *
 * 交互：「筛选」开弹层（时间 / 类型两组，行内带条数、选中打勾）；生效中的筛选
 * 在按钮右边以 chip 呈现、点 × 移除；AI 归类 / 考试是动作不是筛选，安静地排在右边。
 *
 * ⚠️ **状态都在外层**（URL 是唯一真相源）：这里只收 ranges/types 的展示数据、
 * 发 pick-range / pick-type / organize / exam 事件——没有第二份可写状态。
 */
import { computed, ref } from 'vue'
import KbSvgIcon from './KbSvgIcon.vue'
import KbLoader from './KbLoader.vue'

const props = defineProps({
  /** 当前时间档（kind，'all' = 不限）。 */
  timeRange: { type: String, default: 'all' },
  /** 当前类型（id，null = 全部）。 */
  typeFilter: { type: [Number, String], default: null },
  /** 时间档列表 [{ kind, label, count }]。 */
  ranges: { type: Array, default: () => [] },
  /** 类型列表 [{ id, label, count }]。 */
  types: { type: Array, default: () => [] },
  /** AI 归类条数（0 = 不显示这个动作——它只属于待归类屏）。 */
  organizeCount: { type: Number, default: 0 },
  /** 考试条数（0 = 不显示）。 */
  examCount: { type: Number, default: 0 },
  /** AI 归类任务**提交中**（禁用按钮，防重复提交）。 */
  organizing: { type: Boolean, default: false },
  /** **能不能进批量模式**（只在待归类屏为 true；判据在外层，这里只管显示）。 */
  showBatch: { type: Boolean, default: false },
  /** 是否在**批量模式**（这条工具条整条换成批量条）。 */
  batchMode: { type: Boolean, default: false },
  /** 已勾选条数。 */
  batchCount: { type: Number, default: 0 },
  /** 当前页有几条（0 = 这一页没东西可勾 ⇒ 连「批量」开关都不画）。 */
  pageCount: { type: Number, default: 0 },
  /** 当前页是否**全部**已勾选（决定「全选本页 / 取消全选」的文案）。 */
  allSelected: { type: Boolean, default: false }
})
const emit = defineEmits([
  'pick-range', 'pick-type', 'organize', 'exam',
  // 批量：开关 / 全选本页 / 三个动作。**动作只发事件**，真正做什么由列表页决定
  // （弹窗、确认框都只有一份实例，挂在 AppLearnView 上）。
  'toggle-batch', 'select-all', 'batch-categorize', 'batch-organize', 'batch-delete'
])

const panelOpen = ref(false)

/** 有筛选生效 ⇒ 按钮高亮。 */
const active = computed(() => props.timeRange !== 'all' || props.typeFilter != null)

/** 时间 chip 文字（没生效 = 空）。 */
const rangeChip = computed(() =>
  props.timeRange !== 'all' ? (props.ranges.find((r) => r.kind === props.timeRange) || {}).label || '' : ''
)
/** 类型 chip 文字。 */
const typeChip = computed(() =>
  props.typeFilter != null ? (props.types.find((t) => t.id === props.typeFilter) || {}).label || '' : ''
)

function pick(kind) {
  panelOpen.value = false
  emit('pick-range', kind)
}
function pickType(id) {
  panelOpen.value = false
  emit('pick-type', id)
}
</script>

<style scoped>
.tf {
  display: inline-flex;
  align-items: center;
  /* **一行只有一种间距**：筛选 / AI 归类 / 考试 / 批量 全是同一族描边钮，
     中间不再插"分组空隙"。以前那里有个 2px 的 spacer ⇒ 筛选到第一个动作
     是 4+2+4=10px，而动作之间是 4px ⇒ 同一条工具条上两种间距，
     看着像"筛选和后面那几颗不是一伙的"。 */
  gap: var(--sp-4);
}
/* 触发钮：和分段切换（.ct__views）同一家族语言——28 高、7 圆角、描边。 */
.tf__trigger {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-3);
  height: 28px;
  padding: 0 var(--sp-6);
  border: 1px solid var(--cc-border-strong);
  border-radius: 7px;
  background: var(--cc-surface);
  color: var(--cc-text-2);
  font-family: var(--font-body);
  font-size: var(--text-sm);
  cursor: pointer;
  white-space: nowrap;
  transition: color var(--t-fast, 140ms) var(--ease, ease), border-color var(--t-fast, 140ms) var(--ease, ease), background var(--t-fast, 140ms) var(--ease, ease);
}
.tf__trigger:hover {
  color: var(--cc-primary);
  border-color: var(--cc-primary-border);
}
.tf__trigger.is-on {
  color: var(--cc-primary);
  border-color: var(--cc-primary-border);
  background: var(--cc-primary-soft);
}
/* 生效中的筛选：可摘 chip。 */
.tf__chip {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-2);
  height: 24px;
  padding: 0 var(--sp-3) 0 var(--sp-5);
  border: none;
  border-radius: 999px;
  background: var(--cc-primary-soft);
  color: var(--cc-primary);
  font-family: var(--font-body);
  font-size: var(--text-xs);
  cursor: pointer;
  white-space: nowrap;
}
.tf__chip:hover {
  background: var(--cc-primary-border);
  color: var(--cc-surface);
}
/* 动作（AI 归类 / 考试）：与「筛选」触发钮**同一族描边 chip**（2026-11-09 用户：
   「筛选、考试、卡片切换全是不同的风格 统一一下」）——一行工具条只有一种控件长相；
   accent 只留给"激活态"（筛选高亮、分段选中、可摘 chip）。 */
.tf__act {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-3);
  height: 28px;
  padding: 0 var(--sp-6);
  border: 1px solid var(--cc-border-strong);
  border-radius: 7px;
  background: var(--cc-surface);
  color: var(--cc-text-2);
  font-family: var(--font-body);
  font-size: var(--text-sm);
  cursor: pointer;
  white-space: nowrap;
  transition: color var(--t-fast, 140ms) var(--ease, ease), background var(--t-fast, 140ms) var(--ease, ease), border-color var(--t-fast, 140ms) var(--ease, ease);
}
/* ⚠️ **忙碌态锁宽**（2026-11-13 统一加载层）：图标换成图形、文案从「AI 归类 N」
   变成「提交中」⇒ 内容宽度会变 ⇒ 按钮横向跳一下、整排按钮都在动。
   min-width 取常态那档的宽度（图形 + "AI 归类 N"）≈ 常态宽度，够用且不撑破。 */
.tf__act--busy {
  min-width: 96px;
  justify-content: center;
}
.tf__act--busy .kb-loader {
  width: 14px;
  height: 14px;
}
.tf__act:hover {
  color: var(--cc-primary);
  border-color: var(--cc-primary-border);
}
/* 提交中：禁用态。**别只靠透明度**——那看着像还能点。
   描边和文字都压暗一档，和「不可用」这个语义一致。 */
.tf__act:disabled {
  color: var(--cc-text-4);
  border-color: var(--cc-border);
  cursor: default;
}
.tf__act:disabled:hover {
  color: var(--cc-text-4);
  border-color: var(--cc-border);
}
/* 「删除」是这批动作里唯一不可逆的一个 ⇒ **只染字**（危险档），描边仍走这一族那一套。
   不整颗染红：一行五个钮里有一颗红底，它就成了"主操作"，而这批动作的主操作是归类。
   ⚠️ hover 那条**必须写在本文件里**（`.tf__act:hover` 同特异性，靠源序决胜负）；
   写在 AppLearnView 的父级 scoped 里压根到不了这个子组件 DOM。 */
.tf__act--danger {
  color: var(--cc-danger);
}
.tf__act--danger:hover {
  color: var(--cc-danger-deep);
  background: var(--cc-danger-soft);
  border-color: var(--cc-danger);
}
/* 弹层内面板。 */
.tf__panel {
  padding: var(--sp-3);
}
.tf__group-name {
  margin: 0;
  padding: var(--sp-3) var(--sp-5) var(--sp-2);
  font-family: var(--font-body);
  font-size: var(--text-xs);
  letter-spacing: 0.08em;
  color: var(--cc-text-3);
}
.tf__row {
  display: flex;
  align-items: center;
  gap: var(--sp-4);
  width: 100%;
  height: 32px;
  padding: 0 var(--sp-5);
  border: none;
  border-radius: var(--radius-sm);
  background: none;
  color: var(--cc-text-2);
  font-family: var(--font-body);
  font-size: var(--text-sm);
  cursor: pointer;
  text-align: left;
  transition: color var(--t-fast, 140ms) var(--ease, ease), background var(--t-fast, 140ms) var(--ease, ease);
}
.tf__row:hover {
  background: var(--cc-surface-2);
  color: var(--cc-text-1);
}
.tf__row.is-on {
  background: var(--cc-primary-soft);
  color: var(--cc-primary);
  font-weight: 500;
}
/* 勾位：常态留空（对齐），选中打勾。 */
.tf__check {
  width: 14px;
  flex: 0 0 14px;
  display: inline-flex;
  align-items: center;
}
.tf__label {
  flex: 1;
}
.tf__count {
  font-family: var(--font-num);
  font-size: var(--text-xs);
  font-variant-numeric: tabular-nums;
  color: var(--cc-text-4);
}
.tf__sep {
  height: 1px;
  background: var(--cc-border);
  margin: var(--sp-3) var(--sp-5);
}
</style>
