<template>
  <!-- 设置入口：齿轮。点开「设置」弹窗（分类目录 + 右区内容）。
       齿轮就是自己的触发器，不套 popover/dropdown（双触发的坑见 git 历史）。 -->
  <button class="kb-icon-btn" aria-label="设置" @click="visible = true">
    <el-icon><Setting /></el-icon>
  </button>

  <el-dialog v-model="visible" title="设置" width="var(--cc-dialog-w-lg)" class="settings-dlg">
    <div class="settings">
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
        <!-- ==================== 1. 界面设置 ==================== -->
        <div v-if="page === 'ui'" class="settings__ui">
          <el-scrollbar class="settings__scroll">
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

          <div class="prefs__cap prefs__cap--gap">字体</div>
          <div class="prefs__swatches">
            <button
              v-for="f in fontOptions"
              :key="f.key"
              type="button"
              class="prefs__swatch"
              :class="{ 'is-on': font === f.key }"
              :aria-pressed="font === f.key"
              @click="pickFont(f.key)"
            >
              <span class="prefs__peek prefs__peek-font" :style="{ fontFamily: f.preview }">{{ f.sample }}</span>
              <span class="prefs__swatch-name">{{ f.name }}</span>
              <span class="prefs__swatch-hint">{{ f.hint }}</span>
              <span class="prefs__tick">✓</span>
            </button>
          </div>

          <div class="prefs__cap prefs__cap--gap">浏览与布局</div>
          <div class="prefs__card">
            <div class="prefs__row">
              <label class="prefs__label" for="prefs-ui.side.sideWidth">{{ sliderSpec('ui.side.sideWidth').label }}</label>
              <el-slider
                id="prefs-ui.side.sideWidth"
                class="prefs__slider"
                :min="sliderSpec('ui.side.sideWidth').min"
                :max="sliderSpec('ui.side.sideWidth').max"
                :step="sliderSpec('ui.side.sideWidth').step"
                :model-value="numVal('ui.side.sideWidth')"
                :show-tooltip="false"
                @update:model-value="set('ui.side.sideWidth', $event)"
              />
              <span class="prefs__value">{{ numVal('ui.side.sideWidth') }}{{ sliderSpec('ui.side.sideWidth').unit }}</span>
            </div>

            <div class="prefs__row">
              <span class="prefs__label">掌握度指示</span>
              <el-switch
                size="small"
                :model-value="boolVal('ui.browse.showMastery', true)"
                @update:model-value="set('ui.browse.showMastery', $event ? '1' : '0')"
              />
            </div>
          </div>

          <div class="prefs__cap prefs__cap--gap">正文排版</div>
          <div class="prefs__card">
            <div v-for="k in TYPO_SLIDER_KEYS" :key="k" class="prefs__row">
              <label class="prefs__label" :for="`prefs-${k}`">{{ sliderSpec(k).label }}</label>
              <el-slider
                :id="`prefs-${k}`"
                class="prefs__slider"
                :min="sliderSpec(k).min"
                :max="sliderSpec(k).max"
                :step="sliderSpec(k).step"
                :model-value="numVal(k)"
                :show-tooltip="false"
                @update:model-value="set(k, $event)"
              />
              <span class="prefs__value">{{ numVal(k) }}{{ sliderSpec(k).unit }}</span>
            </div>

            <div class="prefs__row">
              <span class="prefs__label">代码折行</span>
              <el-switch
                size="small"
                :model-value="boolVal('ui.detail.codeWrap', false)"
                @update:model-value="set('ui.detail.codeWrap', $event ? '1' : '0')"
              />
            </div>
          </div>

          </el-scrollbar>

          <div class="prefs__foot">
            <button type="button" class="quiet-btn" @click="resetAllUi">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M3 12a9 9 0 1 0 3-6.7"/><path d="M3 4v5h5"/></svg>
              恢复系统默认
            </button>
          </div>
        </div>

        <!-- ==================== 2. AI 能力 ==================== -->
        <AiSettingsPanel v-else />
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
/**
 * 设置弹窗（齿轮的唯一出口）：**分类目录 + 右区内容**。
 *
 * 2026-11-17 待办-1115 第8点终版收敛：
 * 1. 左栏收敛为：界面 / AI 能力；
 * 2. 滑块规格（label / min / max / step / unit / def）单一权威在 uiPrefs.js；
 * 3. 无影子状态、无口水话描述，所有状态切换即时响应。
 */
import { ref, computed } from 'vue'
import { Setting } from '@element-plus/icons-vue'
import { getUiPref, getPrefSpec, saveUiPrefs, resetPrefKeys } from '../../utils/uiPrefs'
import { themeList, currentTheme, setTheme, currentFont, setFont, clearThemeLegacy } from '../../utils/theme'
import AiSettingsPanel from '../../views/corecraft-web/AiSettingsPanel.vue'

const pages = [
  { key: 'ui', name: '界面设置' },
  { key: 'ai', name: 'AI 能力' }
]

const TYPO_SLIDER_KEYS = ['ui.detail.editorFs', 'ui.detail.lineHeight', 'ui.detail.paraGap']

const UI_RESET_KEYS = [
  'ui.theme',
  'ui.font',
  'ui.side.sideWidth',
  'ui.browse.showMastery',
  ...TYPO_SLIDER_KEYS,
  'ui.detail.codeWrap'
]

function resetAllUi() {
  clearThemeLegacy()
  resetPrefKeys(UI_RESET_KEYS)
}

const visible = ref(false)
const page = ref('ui')

const theme = computed(() => currentTheme())
function pickTheme(key) {
  setTheme(key)
}

const font = computed(() => currentFont())
const fontOptions = [
  { key: 'sans', name: '标准', sample: '文 Aa', hint: '现代无衬线', preview: 'Inter, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif' },
  { key: 'serif', name: '衬线', sample: '文 Aa', hint: '书卷衬线体', preview: '"Noto Serif SC", Georgia, serif' },
  { key: 'mono', name: '等宽', sample: '文 01', hint: '工程等宽体', preview: '"JetBrains Mono", ui-monospace, "Cascadia Mono", "PingFang SC", sans-serif' }
]
function pickFont(key) {
  setFont(key)
}

function sliderSpec(key) {
  return getPrefSpec(key)
}

function numVal(key) {
  const spec = getPrefSpec(key)
  const v = getUiPref(key)
  return v != null && v !== '' ? Number(v) : spec.defaultValue
}

function boolVal(key, def) {
  const v = getUiPref(key)
  if (v == null) return def
  return v !== '0' && v !== 0 && v !== false
}

function set(configKey, v) {
  saveUiPrefs({ [configKey]: v })
}
</script>

<style scoped>
.settings {
  display: flex;
  gap: var(--sp-11);
  /* 固定弹窗内容区高度：切「界面设置 / AI 能力」外框尺寸完全不变 */
  height: min(460px, 68vh);
}

/* ---------- 左：分类目录 ---------- */
.settings__rail {
  flex: none;
  width: 108px;
  padding-right: var(--sp-10);
  border-right: 1px solid var(--cc-border);
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
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
  transition: background .15s ease, color .15s ease, box-shadow .15s ease;
}
.settings__rail-btn:hover {
  background: var(--cc-surface-2);
}
.settings__rail-btn.is-on {
  background: var(--cc-primary-soft);
  color: var(--cc-primary);
  font-weight: 600;
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--cc-primary) 18%, transparent);
}

/* ---------- 右：内容区 ---------- */
.settings__pane {
  flex: 1;
  min-width: 0;
  height: 100%;
}
.settings__ui {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.settings__scroll {
  flex: 1;
  min-height: 0;
}
.settings__scroll :deep(.el-scrollbar__wrap) {
  max-height: 100%;
}
.settings__scroll :deep(.el-scrollbar__view) {
  padding: 2px var(--sp-10) var(--sp-6) 2px;
}

.prefs__cap {
  font-size: var(--text-xs);
  font-weight: 500;
  color: var(--cc-text-3);
  margin-bottom: var(--sp-6);
}
.prefs__cap--gap {
  margin-top: var(--sp-12);
}
.prefs__card {
  padding: var(--sp-7) var(--sp-11);
  border: 1px solid var(--cc-border);
  border-radius: var(--radius-md);
  background: color-mix(in srgb, var(--cc-surface-2) 55%, transparent);
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
  transition: border-color .15s ease, box-shadow .15s ease;
}
.prefs__swatch:hover {
  border-color: var(--cc-border-strong);
}
.prefs__swatch.is-on {
  border-color: var(--cc-primary);
  box-shadow: 0 0 0 1px var(--cc-primary);
}
.prefs__peek {
  display: block;
  position: relative;
  height: 34px;
  border: 1px solid var(--cc-border);
  border-radius: 6px;
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
  bottom: 6px;
  width: 15px;
  height: 7px;
  border-radius: 4px;
}
.prefs__swatch-name {
  display: block;
  margin-top: 5px;
  text-align: center;
  font-size: var(--text-xs);
  color: var(--cc-text-2);
}
.prefs__swatch.is-on .prefs__swatch-name {
  color: var(--cc-primary);
  font-weight: 600;
}
.prefs__swatch-hint {
  font-size: 11px;
  color: var(--cc-text-3);
  line-height: 1.2;
}
.prefs__tick {
  display: none;
  position: absolute;
  top: 8px;
  right: 8px;
  width: 15px;
  height: 15px;
  border-radius: 50%;
  background: var(--cc-primary);
  color: var(--cc-btn-fg, #fff);
  font-size: 9px;
  line-height: 15px;
  text-align: center;
}
.prefs__swatch.is-on .prefs__tick {
  display: block;
}

/* 设置项行（滑块 / 分段钮 / 开关共用同一左对齐标签栅格） */
.prefs__row {
  display: flex;
  align-items: center;
  gap: var(--sp-8);
  min-height: 32px;
  margin-bottom: var(--sp-5);
}
.prefs__row:last-child {
  margin-bottom: 0;
}
.prefs__label {
  width: 84px;
  flex-shrink: 0;
  font-size: var(--text-sm);
  color: var(--cc-text-2);
  white-space: nowrap;
}
.prefs__slider {
  flex: 1;
  min-width: 0;
  height: 24px;
}
.prefs__slider :deep(.el-slider__runway),
.prefs__slider :deep(.el-slider__bar) {
  height: 4px;
}
.prefs__slider :deep(.el-slider__button-wrapper) {
  top: -16px;
}
.prefs__slider :deep(.el-slider__button) {
  width: 14px;
  height: 14px;
}
.prefs__value {
  width: 46px;
  flex-shrink: 0;
  text-align: right;
  font-size: var(--text-xs);
  color: var(--cc-text-4);
  font-variant-numeric: tabular-nums;
}

.prefs__foot {
  flex: none;
  display: flex;
  align-items: center;
  min-height: 30px;
  margin-top: var(--sp-10);
  padding-top: var(--sp-9);
  border-top: 1px solid var(--cc-border);
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
.prefs__peek-font {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--text-lg);
  color: var(--cc-text-1);
}
</style>
