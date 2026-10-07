<template>
  <el-popover
    v-model:visible="panelVisible"
    :width="260"
    trigger="click"
    placement="top-end"
    :teleported="false"
  >
    <template #reference>
      <!-- 齿轮而不是百分比：这里管的是两个独立值（宽度 + 密度），
           拿一个数字当 chip 会让人以为"点它就调那个数字"。
           侧边栏自己的设置就该待在侧边栏里，不塞进顶栏。
           常显：它落在最右、和用户行的主次不打架；早先那版靠
           "hover 底栏才显形"，为此得在外面套一层 span 专门挂 opacity，
           还要绕开"`<button>` 套 `<button>` 是非法 HTML"——两层壳换一个
           可有可无的显隐，不值。 -->
      <button class="kb-icon-btn" aria-label="侧边栏设置">
        <el-icon><Setting /></el-icon>
      </button>
    </template>

    <div class="side-prefs">
      <div class="side-prefs__title">侧边栏</div>

      <div class="side-prefs__row">
        <label class="side-prefs__label" for="side-prefs-width">宽度</label>
        <el-slider
          id="side-prefs-width"
          class="side-prefs__slider"
          :min="SIDE_WIDTH.min"
          :max="SIDE_WIDTH.max"
          :step="SIDE_WIDTH.step"
          :model-value="width"
          :show-tooltip="false"
          @update:model-value="setWidth"
        />
        <span class="side-prefs__value">{{ width }}px</span>
      </div>

      <div class="side-prefs__row">
        <span class="side-prefs__label">密度</span>
        <div class="side-prefs__seg">
          <button
            v-for="(d, key) in SIDE_DENSITY"
            :key="key"
            class="side-prefs__seg-item"
            :class="{ 'is-on': key === density }"
            @click="setDensity(key)"
          >{{ d.label }}</button>
        </div>
      </div>

      <el-button class="side-prefs__reset" text size="small" @click="reset">恢复默认</el-button>
    </div>
  </el-popover>
</template>

<script setup>
/**
 * 侧边栏的两把旋钮：**宽度** 和 **密度**（紧凑 / 适中 / 松散）。
 *
 * 它是**第一组**，不是唯一一组。2026-10-07 顶栏那个百分比 chip（`--ui-scale` 乘数）
 * 已经整个删掉了——**没有全局档位，只有按维度一组一组来**。
 * 其余组（字号除外，它归浏览器 Ctrl +/−；还有间距 / 圆角 / 内容宽 / 卡片密度）
 * 按同样方式往这个面板里加：面板加一行，utils/uiPrefs.js 的 GROUPS 加一条。
 *
 * 写值的方式是往 `<html>` 行内写 CSS 变量，:root 里的设计值一个字不动——
 * 所以没设过偏好的用户看到的仍然是设计出来的样子。
 */
import { ref, computed } from 'vue'
import { Setting } from '@element-plus/icons-vue'
import {
  SIDE_DENSITY, SIDE_WIDTH,
  getUiPrefs, saveUiPrefs, resetUiPrefs, clampSideWidth
} from '../../utils/uiPrefs'

const panelVisible = ref(false)
const prefs = ref(getUiPrefs())

// 存的值可能是 null（= 用默认），显示时要落到具体值上
const width = computed(() => prefs.value.sideWidth ?? SIDE_WIDTH.def)
const density = computed(() => prefs.value.sideDensity ?? 'normal')

function setWidth(v) {
  prefs.value = { ...prefs.value, sideWidth: clampSideWidth(v) }
  saveUiPrefs({ sideWidth: prefs.value.sideWidth })
}

function setDensity(key) {
  prefs.value = { ...prefs.value, sideDensity: key }
  saveUiPrefs({ sideDensity: key })
}

function reset() {
  resetUiPrefs()
  prefs.value = getUiPrefs()
}
</script>

<style scoped>
.side-prefs {
  display: flex;
  flex-direction: column;
  gap: var(--sp-6);
}
.side-prefs__title {
  font-size: var(--text-md);
  color: var(--cc-text-3);
  font-weight: 500;
}
.side-prefs__row {
  display: flex;
  align-items: center;
  gap: var(--sp-8);
}
.side-prefs__label {
  font-size: var(--text-sm);
  color: var(--cc-text-2);
  flex-shrink: 0;
  width: 28px;
}
.side-prefs__slider {
  flex: 1;
  min-width: 0;
}
.side-prefs__value {
  font-size: var(--text-xs);
  color: var(--cc-text-4);
  font-variant-numeric: tabular-nums;
  flex-shrink: 0;
  width: 34px;
  text-align: right;
}
/* 三档分段：形态照抄筛选条那排药丸（.pill），不另发明一套 */
.side-prefs__seg {
  display: flex;
  flex: 1;
  gap: 1px;
}
.side-prefs__seg-item {
  flex: 1;
  border: none;
  background: transparent;
  border-radius: var(--radius-xs);
  padding: var(--sp-3) 0;
  font-size: var(--text-sm);
  color: var(--cc-text-2);
  cursor: pointer;
  transition: var(--cc-transition);
}
.side-prefs__seg-item:hover {
  background: var(--cc-surface-2);
  color: var(--cc-text-1);
}
.side-prefs__seg-item.is-on {
  background: var(--cc-primary-soft);
  color: var(--cc-primary);
  font-weight: 500;
}
.side-prefs__reset {
  align-self: flex-start;
  color: var(--cc-text-3);
}
</style>
