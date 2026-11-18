import { watch } from 'vue'
import { keywordFromRoute } from '../../utils/routeQuery'
import { bodySearchKeyword, bodySearchHits, bodySearchJump } from './useCorecraftWebStore'

/**
 * 详情页正文检索（高亮 + 首次自动定位 + ↑↓ 跳转）。
 *
 * 词源单一：URL `?q=` ⇄ 顶栏搜索框 ⇄ bodySearchKeyword，通过 afterBodyTop 排在归顶之后执行。
 */
export function useTopicBodyFind({ route, editorRef, afterBodyTop }) {
  /**
   * 一次详情页会话里，同一个（文档 + 词）只自动定位一次（离开详情页时清账）。
   * 放在 setup 作用域，编辑器子组件 HMR 重建时不丢账；换知识点或切出再切回按 rule 重算。
   */
  let lastLocatedKey = ''

  /**
   * 把词交给编辑器高亮，同步命中计数。
   *
   * @param {string} kw 检索词（空 = 清高亮）
   * @param {boolean} [gotoFirst] true = 定位到第一处命中（打字时不传，避免正文上下乱跳）
   */
  function applyBodySearch(kw, gotoFirst) {
    const ed = editorRef.value
    const n = ed ? ed.searchInBody(kw) : 0
    bodySearchHits.value = { total: n, index: 0 }
    if (gotoFirst && n > 0 && ed.gotoMatch(0)) {
      bodySearchHits.value = { total: n, index: 1 }
      return true
    }
    return false
  }

  /** URL 的 `?q=` 是检索词唯一权威，避免经 store 双向通道绕一圈。 */
  function bodySearchKeywordOfRoute() {
    return keywordFromRoute(route).trim()
  }

  /** 正文里检索目标词并排在归顶后定位到第一处（幂等）。 */
  function locateBodySearch() {
    const kw = bodySearchKeywordOfRoute()
    if (!kw) return
    const key = String(route.params.id) + '|' + kw
    afterBodyTop(() => {
      if (!editorRef.value) return
      if (key === lastLocatedKey) {
        applyBodySearch(kw, false)
        return
      }
      if (applyBodySearch(kw, true)) lastLocatedKey = key
    })
  }

  watch(bodySearchKeyword, (kw) => {
    if (route.name !== 'AppTopicDetail') return
    applyBodySearch(kw, false)
  })

  function jumpNext() {
    const { total, index } = bodySearchHits.value
    if (!total) return
    const next = index >= total ? 0 : index
    if (editorRef.value?.gotoMatch(next)) bodySearchHits.value = { total, index: next + 1 }
  }

  function jumpPrev() {
    const { total, index } = bodySearchHits.value
    if (!total) return
    const prev = index <= 1 ? total - 1 : index - 2
    if (editorRef.value?.gotoMatch(prev)) bodySearchHits.value = { total, index: prev + 1 }
  }

  watch(bodySearchJump, (dir) => {
    if (!dir) return
    if (dir === 'next') jumpNext()
    else if (dir === 'prev') jumpPrev()
    bodySearchJump.value = ''
  })

  function clearLocateTrack() {
    lastLocatedKey = ''
  }

  return {
    applyBodySearch,
    locateBodySearch,
    clearLocateTrack
  }
}
