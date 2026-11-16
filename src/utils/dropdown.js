import { ref } from 'vue'

/**
 * el-dropdown 的「首开后保持挂载」（2026-11-15，从 RowMenu 的内联实现收敛成共用）。
 *
 * `persistent=false` 让菜单内容**首次点开才渲染**——列表里几十个下拉不必在挂载时
 * 就各付一次建 popper 的钱（实测见 RowMenu 头注释）；代价是每次打开都重建弹层
 * （挂载 + createPopper + 弹出动画前的强制布局）。⇒ 首开后翻成 `persistent=true`，
 * 之后重开复用同一份弹层。false→true 只翻一次，不回退。
 *
 * 用法：
 *   const { keepMounted, onDropdownShow } = useDropdownKeepMounted()
 *   `<el-dropdown :persistent="keepMounted" @visible-change="onDropdownShow">`
 */
export function useDropdownKeepMounted() {
  const keepMounted = ref(false)

  /** @param {boolean} visible visible-change 的参数 */
  function onDropdownShow(visible) {
    if (visible) keepMounted.value = true
  }

  return { keepMounted, onDropdownShow }
}
