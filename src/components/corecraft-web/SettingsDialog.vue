<template>
  <!-- 设置入口：齿轮。点开「设置」弹窗（分类目录 + 右区内容）。
       齿轮就是自己的触发器，不套 popover/dropdown（双触发的坑见 git 历史）。 -->
  <button class="kb-icon-btn" aria-label="设置" @click="visible = true">
    <el-icon><Setting /></el-icon>
  </button>

  <el-dialog v-model="visible" title="设置" width="var(--cc-dialog-w-lg)" class="settings-dlg">
    <div class="settings">
      <!-- 分类目录：**这里是扩展点** —— 后续新增一类设置（编辑器 / 快捷键 / 复习…）
           就往 pages 里加一项、右区挂一个面板组件，框架不动。
           目录里的项一律出右区；只有"编辑某条 API"那种动作表单才叠弹窗
           （2026-11-13 定：界面出右区、AI 单独弹窗 = 割裂，已收口）。 -->
      <nav class="settings__rail">
        <button
          v-for="p in pages"
          :key="p.key"
          type="button"
          class="settings__rail-btn"
          :class="{ 'is-on': page === p.key }"
          @click="page = p.key"
        >{{ p.name }}</button>
      </nav>

      <div class="settings__pane">
        <!-- ==================== 界面 ==================== -->
        <template v-if="page === 'ui'">
          <!-- 风格：微缩预览小卡（2026-11-13 用户认可的形态）——风格是"看"的，
               三个名字不传达长相；小卡里纸色 + 主色一眼可选。
               peek 色在 utils/theme.js 的 themeList 上（加主题时一起给）。 -->
          <div class="prefs__cap">风格</div>
          <div class="prefs__swatches">
            <button
              v-for="t in themeList"
              :key="t.key"
              type="button"
              class="prefs__swatch"
              :class="{ 'is-on': theme === t.key }"
              :aria-pressed="theme === t.key"
              @click="pickTheme(t.key)"
            >
              <span class="prefs__peek" :style="{ background: t.peek.paper }">
                <i class="prefs__peek-side" :style="{ background: t.peek.side }" />
                <i class="prefs__peek-l prefs__peek-l1" :style="{ background: t.peek.line }" />
                <i class="prefs__peek-l prefs__peek-l2" :style="{ background: t.peek.line }" />
                <i class="prefs__peek-chip" :style="{ background: t.peek.accent }" />
              </span>
              <span class="prefs__swatch-name">{{ t.name }}</span>
              <span class="prefs__tick">✓</span>
            </button>
          </div>

          <!-- 组切换：下划线 tabs（导航形态 ≠ 风格卡的选择形态，两排不再打架）。
               ⚠️ 组名只在这条 tabs 里出现一次，正文不再重复标题。 -->
          <div v-if="shown.length > 1" class="prefs__tabs">
            <button
              v-for="g in shown"
              :key="g.key"
              type="button"
              class="prefs__tab"
              :class="{ 'is-on': active === g.key }"
              @click="active = g.key"
            >{{ g.title }}</button>
          </div>

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

          <!-- 树还没到 / 没拿到时说什么：加载中与失败要分开（utils/uiPrefs.js 的
               prefTreeState 是唯一出口），别让面板空着被当成"没有可调的项"。 -->
          <div v-if="prefTreeState === 'loading'" class="prefs__empty kb-state-line">
            <KbLoader size="sm" />
            <span>加载中…</span>
          </div>
          <div v-else-if="prefTreeState === 'failed' && !curItems.length" class="prefs__empty">加载失败</div>

          <!-- 「恢复系统默认」只重置**当前这一组**（在首页这组点它不该抹掉侧边栏宽度）。 -->
          <div class="prefs__foot">
            <button type="button" class="quiet-btn" @click="resetCur">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M3 12a9 9 0 1 0 3-6.7"/><path d="M3 4v5h5"/></svg>
              恢复系统默认
            </button>
          </div>
        </template>

        <!-- ==================== AI 能力 ==================== -->
        <AiSettingsPanel v-else />
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
/**
 * 设置弹窗（齿轮的唯一出口）：**分类目录 + 右区内容**。
 *
 * 2026-11-13 由 PrefsPanel（popover 形态的「界面设置」）改来，两个决定：
 *
 * 1. **齿轮 = 系统设置入口**，不只是界面设置 —— 后续扩展的设置都往目录（pages）里加，
 *    右区出内容，框架不动。
 * 2. **目录里的项一律出右区**：「界面」出右区、「AI 能力」也出右区（AiSettingsPanel）。
 *    曾经"界面直接显示、AI 单独弹窗"，用户判**割裂**；只有"编辑某条 API"的表单
 *    才是叠层弹窗（表单是动作不是分类）。
 *
 * **本组件管目录与「界面」右区**；每个分类自己的内容归各自的面板组件
 * （现在只有 AiSettingsPanel 一个）。加新分类 = pages 加一项 + 右区挂一个组件。
 *
 * 「界面」右区由**配置树**驱动（utils/uiPrefs.js），加一项滑块 = 树上加一行，
 * 这里一行不动。每一行都是滑块：尺寸类调 px、占比类调百分比（用户定的口径）。
 */
import { ref, computed } from 'vue'
import { Setting } from '@element-plus/icons-vue'
import { getUiPrefs, prefGroups, prefTreeState, resetUiPrefs, saveUiPrefs } from '../../utils/uiPrefs'
import { themeList, currentTheme, setTheme } from '../../utils/theme'
import KbLoader from './KbLoader.vue'
import AiSettingsPanel from '../../views/corecraft-web/AiSettingsPanel.vue'

const props = defineProps({
  // 显示哪几组（配置树上那一组的短名：侧边栏 side / 首页 home / …）。
  groups: { type: Array, required: true }
})

/** 分类目录。加一类设置 = 这里加一项（右区挂对应组件）。 */
const pages = [
  { key: 'ui', name: '界面' },
  { key: 'ai', name: 'AI 能力' }
]

const visible = ref(false)
const page = ref('ui')
const prefs = ref(getUiPrefs())
const active = ref(props.groups[0])

/* 风格（utils/theme.js）：选中框**从生效值派生**——与 html[data-theme] 同一条
   数据源，结构上不会再出现"视觉素雅、框停本色"（一次性 ref 快照会被写失败/
   清库/重挂载击穿，2026-11-09 用户报过）。 */
const theme = computed(() => currentTheme())
function pickTheme(key) {
  setTheme(key)
}

/** 配置树上要显示的那几组（树还没到的时候是空的，界面右区还没东西可画） */
const shown = computed(() =>
  props.groups.map((k) => prefGroups.value.find((g) => g.key === k)).filter(Boolean)
)
const cur = computed(() => shown.value.find((g) => g.key === active.value))

/**
 * 当前这一组的项。**空数组兜底，不能让模板去读 `cur.items`**：
 * 树没加载出来时 `cur` 是 undefined（那一组不存在），
 * 模板里 `cur.items` 会直接抛错 —— **渲染抛错 = 整个 App 白屏**。
 */
const curItems = computed(() => cur.value?.items || [])

/** 存的值可能是没存过（= 用内置默认），显示时要落到具体值上 */
function valueOf(item) {
  return prefs.value[item.configKey] ?? item.defaultValue
}

/**
 * 本地 state 跟着存档走的**唯一入口**：改完存档一律重读，不在这儿手工拼一份镜像
 * （存档里存了越界值时，面板显示的和下次刷新后显示的必须是同一个数）。
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
 * ⚠️ **"默认"有两层**：后端的系统默认（管理员在「系统管理 → 系统配置」里改）
 * 和配置树上的内置默认。这个按钮恢复的是**前者** —— 后端没有这一项时
 * 才落到后者。所以文案写「恢复系统默认」而不是「恢复默认」。
 */
async function resetCur() {
  await resetUiPrefs([active.value])
  sync()
}
</script>

<style scoped>
.settings {
  display: flex;
  gap: var(--sp-13);
}

/* ---------- 左：分类目录（扩展点） ---------- */
.settings__rail {
  flex: none;
  width: 112px;
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
}
.settings__rail-btn {
  height: 34px;
  padding: 0 var(--sp-10);
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  font-size: var(--text-sm);
  color: var(--cc-text-2);
  text-align: left;
  cursor: pointer;
}
.settings__rail-btn:hover {
  background: var(--cc-surface-2);
}
.settings__rail-btn.is-on {
  background: var(--cc-primary-soft);
  color: var(--cc-primary);
  font-weight: 600;
}

/* ---------- 右：当前分类的内容。唯一的滚动容器 ----------
   界面组（详情 8 项）和 AI 列表都可能高过弹窗 ⇒ 滚在右区里，
   弹窗本身永远不超屏。min-height 保两页切换时不跳高。 */
.settings__pane {
  flex: 1;
  min-width: 0;
  min-height: min(380px, 62vh);
  max-height: 62vh;
  overflow-y: auto;
  scrollbar-gutter: stable;
}

/* ---------- 风格小卡（微缩预览） ---------- */
.prefs__cap {
  font-size: var(--text-xs);
  color: var(--cc-text-3);
  margin-bottom: var(--sp-8);
}
.prefs__swatches {
  display: flex;
  gap: var(--sp-8);
}
.prefs__swatch {
  flex: 1;
  position: relative;
  padding: 5px 5px 7px;
  border: 1px solid var(--cc-border);
  border-radius: var(--radius-md);
  background: var(--cc-surface);
  cursor: pointer;
}
.prefs__swatch.is-on {
  border: 1.5px solid var(--cc-primary);
}
.prefs__peek {
  display: block;
  position: relative;
  height: 36px;
  border: 1px solid var(--cc-border);
  border-radius: 7px;
  overflow: hidden;
}
.prefs__peek-side {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 9px;
}
.prefs__peek-l {
  position: absolute;
  left: 15px;
  height: 3px;
  border-radius: 2px;
  opacity: .7;
}
.prefs__peek-l1 { top: 8px; width: 26px }
.prefs__peek-l2 { top: 15px; width: 18px }
.prefs__peek-chip {
  position: absolute;
  left: 15px;
  bottom: 7px;
  width: 16px;
  height: 8px;
  border-radius: 5px;
}
.prefs__swatch-name {
  display: block;
  margin-top: 6px;
  text-align: center;
  font-size: var(--text-xs);
  color: var(--cc-text-2);
}
.prefs__swatch.is-on .prefs__swatch-name {
  color: var(--cc-primary);
  font-weight: 600;
}
.prefs__tick {
  display: none;
  position: absolute;
  top: 9px;
  right: 9px;
  width: 15px;
  height: 15px;
  border-radius: 50%;
  background: var(--cc-primary);
  color: #fff;
  font-size: 10px;
  line-height: 15px;
  text-align: center;
}
.prefs__swatch.is-on .prefs__tick {
  display: block;
}

/* ---------- 组切换：下划线 tabs ---------- */
.prefs__tabs {
  display: flex;
  margin: var(--sp-12) 0 var(--sp-10);
  border-bottom: 1px solid var(--cc-border);
}
.prefs__tab {
  flex: 1;
  position: relative;
  height: 36px;
  border: 0;
  background: transparent;
  font-size: var(--text-sm);
  color: var(--cc-text-3);
  cursor: pointer;
}
.prefs__tab.is-on {
  color: var(--cc-primary);
  font-weight: 600;
}
.prefs__tab.is-on::after {
  content: "";
  position: absolute;
  left: 18%;
  right: 18%;
  bottom: -1px;
  height: 2px;
  border-radius: 1px;
  background: var(--cc-primary);
}

/* ---------- 滑块行（与原 PrefsPanel 同一套） ---------- */
.prefs__row {
  display: flex;
  align-items: center;
  gap: var(--sp-8);
  margin-bottom: var(--sp-10);
}
.prefs__label {
  /* §4.8(c)：64px + nowrap + ellipsis —— 名字来自配置树，宁可截断也不折行 */
  width: 64px;
  flex-shrink: 0;
  font-size: var(--text-sm);
  color: var(--cc-text-2);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.prefs__slider {
  flex: 1;
  min-width: 0;
}
.prefs__value {
  width: 44px;
  flex-shrink: 0;
  text-align: right;
  font-size: var(--text-xs);
  color: var(--cc-text-4);
  font-variant-numeric: tabular-nums;
}
.prefs__foot {
  margin-top: var(--sp-12);
}
.quiet-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-6);
  height: 28px;
  padding: 0 var(--sp-6);
  margin-left: calc(var(--sp-6) * -1);
  border: 0;
  border-radius: 7px;
  background: transparent;
  font-size: var(--text-xs);
  color: var(--cc-text-3);
  cursor: pointer;
}
.quiet-btn:hover {
  background: var(--cc-primary-soft);
  color: var(--cc-primary);
}
.quiet-btn svg {
  width: 13px;
  height: 13px;
}
.prefs__empty {
  font-size: var(--text-sm);
  color: var(--cc-text-3);
}
</style>
