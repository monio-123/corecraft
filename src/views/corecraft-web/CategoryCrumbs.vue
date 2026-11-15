<template>
  <!-- 目录路径（面包屑）：回答"这条内容在知识库的哪个位置"。
       **2026-11-08 移进 AppLayout 顶栏**（用户：「把下方那个 一级目录 -> 二级目录 的那个索引
       移动到上面去 就在这个一级目录名字之后 格式参考 一级目录/二级目录/三级目录」）——
       原来详情页页头、卡片视图筛选行各画一份，现在全站只在顶栏这一处。

       ⚠️ **形态由顶栏定**（2026-11-09 用户：「搜索框优先级比较高 优先压缩目录索引文字
       （超过展示长度了就不压缩中间的目录就行 你看着怎么显示好看 比如显示* 缩略之类的）
       当目录索引文字压缩到最小的时候再压缩搜索框的长度」）：
       顶栏管**宽度怎么分**（它同时管搜索框），这里只报**两种形态各有多宽**，由顶栏选一种
       ⇒ 宽度和形态在同一个人手里，才不会打架。
       - 'full'：整条链（一级目录 / 二级 / 三级 / 当前目录）
       - 'collapsed'：**中间层级收成一个「…」**，一级目录和当前目录照旧留着
         （收的是"中间"，不是把尾巴截掉 —— 尾巴是"我在哪一级"，不能丢） -->
  <nav
    v-if="crumbs.length"
    ref="navEl"
    class="crumbs"
    :class="{ 'is-collapsed': collapsed }"
    :aria-label="`目录路径：${fullPath}`"
  >
    <!-- 一级目录（哪本书）—— **盒子窄的时候它先让位**（见 .crumbs__head 注释） -->
    <span class="crumbs__head" :title="crumbs[0].name">
      <a
        v-if="crumbs[0].id !== currentId"
        class="crumbs__link"
        :href="categoryHref(crumbs[0].id)"
        @click="onLinkClick($event, crumbs[0].id)"
      >{{ crumbs[0].name }}</a>
      <span v-else class="crumbs__cur">{{ crumbs[0].name }}</span>
    </span>

    <!-- 中间层级：收拢时整块让位（不占位） -->
    <span v-if="crumbs.length > 2" class="crumbs__mid">
      <template v-for="c in middle" :key="c.id">
        <span class="crumbs__sep">/</span>
        <a class="crumbs__link" :href="categoryHref(c.id)" @click="onLinkClick($event, c.id)">{{ c.name }}</a>
      </template>
    </span>

    <!-- 收拢标记：静态文字（**不是**能展开的面板 —— 2026-11-08 用户「切记不要折叠」）。
         悬停给出被收掉的层级名。 -->
    <span v-if="crumbs.length > 2" class="crumbs__fold" :title="foldedTitle">
      <span class="crumbs__sep">/</span>…
    </span>

    <!-- 当前目录（在哪一级）—— **固定不收缩**（"我在哪一级"不能被裁掉） -->
    <span v-if="crumbs.length > 1" class="crumbs__tail">
      <span class="crumbs__sep">/</span>
      <span class="crumbs__cur" aria-current="location">{{ crumbs[crumbs.length - 1].name }}</span>
    </span>
  </nav>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { getCategoryPath } from './useCorecraftWebStore'
import { categoryHref } from '../../utils/categoryNav'

const props = defineProps({
  // 当前所在的目录 id。null / undefined = 未归类，此时不渲染
  // （没有位置信息可呈现时，一个"未归类"的标签只是噪音）
  categoryId: { type: [Number, String], default: null },
  /**
   * 形态（2026-11-09）：'full' = 整条链；'collapsed' = 中间层级收成一个「…」。
   *
   * <p>⚠️ **为什么不由这里定**：宽度是顶栏在算（它同时管搜索框），
   * 这里只负责报两种形态各有多宽 —— 两件事在同一个人手里，才不会互相打架。
   */
  mode: { type: String, default: 'full' }
})

const emit = defineEmits(['pick', 'measure'])

// Number(null) 是 0 = ROOT_CATEGORY，会把"未归类"错判成"在根目录下"，所以先挡掉空值
const currentId = computed(() =>
  props.categoryId == null ? null : Number(props.categoryId)
)

const crumbs = computed(() =>
  currentId.value == null ? [] : getCategoryPath(currentId.value)
)

const collapsed = computed(() => props.mode === 'collapsed')

/** 中间层级（去掉一级目录和当前目录） */
const middle = computed(() => crumbs.value.slice(1, crumbs.value.length - 1))

/** 完整路径（读屏用） */
const fullPath = computed(() => crumbs.value.map(c => c.name).join(' / '))

/** 被收掉的层级名（悬停「…」时看到） */
const foldedTitle = computed(() => middle.value.map(c => c.name).join(' / '))

const navEl = ref(null)

/**
 * 在一份**克隆**上量宽，量完立刻把它摘掉。
 *
 * <p>做法 = 克隆一份 nav，摆在同一个父级下（字号与标题体是祖先
 * `.app-top__crumb` 给的 —— 挂到别处量就是另一套字号，数字全错），
 * 摆成 `width: max-content` 再量宽。
 *
 * <p>⚠️ **摆成 max-content**：容器给的宽度是"分到的"宽度，量的是**内容需要多宽**。
 * 摆成 max-content 后，盒宽 = 内容自然宽（nowrap ⇒ 整条链一行铺开）。
 *
 * <p>⚠️ **量的是克隆，不是真实节点**：真实节点的形态由顶栏定，
 * 这里改它就等于自己跟自己商量（下次顶栏改回来还要再量一遍）。
 *
 * <p>⚠️ **克隆必须摘掉**：量完不摘 = 页面上留着一个看不见的重复面包屑
 * （`visibility:hidden` 只是看不见，**它还占着 DOM、还参与选中文本**）。
 *
 * @param mode 'full' = 整条链；'collapsed' = 收拢后的链
 * @param {(clone: HTMLElement) => number} measure 量什么（克隆已挂上、可量了）
 * @returns {number} 量出来的宽度（量不到 = 0）
 */
function measureClone(mode, measure) {
  const nav = navEl.value
  if (!nav) return 0
  const clone = nav.cloneNode(true)
  clone.classList.toggle('is-collapsed', mode === 'collapsed')
  clone.style.cssText =
    'position:absolute;left:0;top:0;width:max-content;visibility:hidden;pointer-events:none;'
  nav.parentElement.appendChild(clone)
  try {
    return measure(clone)
  } finally {
    clone.remove()
  }
}

/**
 * 量某种形态下这条链的**自然宽度**（不被容器裁掉的那部分）。
 *
 * @param mode 'full' = 整条链；'collapsed' = 收拢后的链
 */
function measureChain(mode) {
  return measureClone(mode, (clone) => clone.getBoundingClientRect().width)
}

/**
 * 量**尾部**（收拢标记 + 当前目录）的宽度 —— 目录索引的"最小可读宽度"。
 *
 * <p>⚠️ **为什么要单独量**（2026-11-09）：首级目录是可被裁的（哪本书，
 * 悬停有全名、侧边栏也列着书名），但**当前目录不能裁**（"我在哪一级"）。
 * ⇒ 顶栏分宽度时，目录索引的**下限 = 尾部宽度**，不是整条链的宽度。
 * 少了这一档，窄窗口下当前目录会被 nav 的 overflow:hidden 裁掉（看不见）。
 */
function measureTail(mode) {
  return measureClone(mode, (clone) => {
    const tail = clone.querySelector('.crumbs__tail')
    const fold = clone.querySelector('.crumbs__fold')
    return (
      (tail ? tail.getBoundingClientRect().width : 0) +
      (fold && getComputedStyle(fold).display !== 'none' ? fold.getBoundingClientRect().width : 0)
    )
  })
}

/**
 * 把两种形态的宽度报给顶栏。
 *
 * <p>⚠️ **只有链的内容变了才要重量**（窗口变宽变窄不影响自然宽度）。
 */
function reportWidths() {
  if (!navEl.value || !crumbs.value.length) return
  emit('measure', {
    full: measureChain('full'),
    collapsed: measureChain('collapsed'),
    // 尾部宽度（两种形态各要一次：full 态没有收拢标记，但当前目录同样要保住）
    tail: Math.max(measureTail('full'), measureTail('collapsed'))
  })
}

onMounted(() => {
  nextTick(reportWidths)
  // 字体是异步加载的 ⇒ 加载完字宽会变 ⇒ 重量一次
  if (document.fonts?.ready) document.fonts.ready.then(reportWidths)
})

watch(
  () => crumbs.value.map(c => c.id).join(','),
  () => nextTick(reportWidths)
)

/** 普通左键走站内导航（去哪由调用方定）；带修饰键的点击交给浏览器（开新标签页）。 */
function onLinkClick(e, id) {
  if (e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return
  e.preventDefault()
  emit('pick', id)
}
</script>

<style scoped>
/* **整条一个字号（宿主给的 16px 标题字号），主次只靠粗细/颜色**（2026-11-13 二稿，
   用户看过一稿后：「次级目录比一级目录小并不好看」）—— 一稿把路径中后段降到 13px，
   章页上「你在这一页」反而成了最小最淡的（大的是上下文、小的是页名，强调点反了）。
   口径：**当前那一截（`.crumbs__cur`）= 标题体**（吃宿主 `.app-top__crumb` 的
   16px/600/display/text-1，首页 / 书页单名天然就是它）；**祖先链（`.crumbs__link` /
   `.crumbs__fold`）= 细浅**（400 / text-3）；分隔符照旧再淡一档（text-4）——
   老稿 `.d-crumb`「祖先 muted、`.here` ink」的结构，升到标题字号。
   ⚠️ 「长路径不抢标题重量」（2026-11-08）由**只有一截是粗深**保证，不靠全员缩小。 */

/* 2026-11-08 晚逐值照老稿（`.d-crumb`，public/mockup/detail-bak.html）：
   这里补三项：
   - 可点项**跟容器同色**（老稿 a 不单独设色，继承 .d-crumb 的 muted），hover 变主题色；
   - 当前项 **--ink**（这里 = text-1）：老稿 `.d-crumb .here`，它是"我在这一级"，比路径深一档；
   - 分隔符 **--faint**（= text-4）+ 两侧各 8px ⇒ 等效老稿的 `gap: 8px`。
   ⚠️ **收拢时中间层级整块让位**（2026-11-09）：不是逐级截断 ——
   截断会把尾巴（"我在哪一级"）切掉，收中间才能保住两头。 */
.crumbs {
  white-space: nowrap;
  /* ⚠️ **nav 自己不裁**（2026-11-09）：`overflow:hidden` 会把**尾部**（当前目录）
     一起裁掉 —— 实测窄窗口下 curInBox=false，"我在哪一级"看不见了。
     ⇒ 裁切交给**首级目录自己**（它有 overflow:hidden + min-width:0），
     nav 只负责"排好这一行"。 */
  display: flex;
  align-items: center;
  min-width: 0;
}
/* 首级目录：**第一个让位的**那截。
   ⚠️ **flex-shrink 权重远大于尾部**（100 vs 1）：flex 收缩量 = 溢出量 × 权重 × 基础宽，
   权重差 100 倍 ⇒ 空间不够时**首级目录先被压到 0**，尾部几乎不动。
   ⚠️ **min-width:0 是关键**：没有它，flex 收缩到它的内容宽以下就不动了
   ⇒ 尾部（当前目录）被挤出可视区（实测 curInBox=false、当前目录看不见）。 */
.crumbs__head {
  display: inline-flex;
  align-items: center;
  min-width: 0;
  flex: 0 100 auto;
  overflow: hidden;
}/* 首级目录里的文字自己截断（全名在 title 属性里，悬停可见）。 */
.crumbs__head .crumbs__link,
.crumbs__head .crumbs__cur {
  overflow: hidden;
  text-overflow: ellipsis;
}
/* 祖先链：**细浅**（400 / text-3，见上方口径）。hover **变主题色**，
   不是加下划线（下划线会把它拽成"链接列表"）。 */
.crumbs__link {
  color: var(--cc-text-3);
  font-weight: 400;
  text-decoration: none;
  transition: color 140ms ease;
}
.crumbs__link:hover {
  color: var(--cc-primary);
}
/* 当前目录：**最后才让位**（"我在哪一级"是这条路径的核心信息）。
   ⚠️ **它自己也得 min-width:0** —— 否则 flex 收缩时它的**内容**会被挤出去，
   表现为「盒子还在、字被压没了」。 */
.crumbs__cur {
  color: var(--cc-text-1);
  flex: 0 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
}
/* 当前目录那截：**几乎不让位**（shrink 权重 1，首级目录是 100）。
   ⚠️ 内部（当前目录文字）仍可截断（ellipsis）—— 极端窄时
   "8.4.2.2 Kubernetes 网络" 变成 "8.4.2.2 Kubern…"，但**开头一定在**，
   认出"我在哪一级"够用；完全看不见才是真丢信息。 */
.crumbs__tail {
  display: inline-flex;
  align-items: center;
  flex: 0 1 auto;
  min-width: 0;
  overflow: hidden;
}
/* 分隔符比路径再淡一档：一条路径里"到哪一级"靠名字，斜杠只是接缝。
   两侧各 8px ⇒ 等效老稿 `.d-crumb { gap: 8px }`（flex 的 gap 是相邻项之间，
   斜杠左右各一份）。
   ⚠️ **当前目录前面的那个斜杠不能被裁掉**（它标出"这条路径的当前位置"），
   中间的斜杠（首级→中间）可以跟着首级一起让位。 */
.crumbs__sep {
  margin: 0 8px;
  color: var(--cc-text-4);
  flex-shrink: 0;
}
/* 收拢标记：静态文字（**不是**能展开的面板 —— 2026-11-08 用户「切记不要折叠」）。
   跟祖先链同档细浅（400 / text-3）：它是"这里省掉了中间层级"的注解，不是页名。 */
.crumbs__fold {
  display: none;
  font-weight: 400;
  color: var(--cc-text-3);
  /* 收拢标记本身**不可收缩**：它是"这里省略了中间层级"的说明，
     缩没了就看不出这条路径被省过。 */
  flex-shrink: 0;
}
.crumbs.is-collapsed .crumbs__fold {
  display: inline;
}
/* 收拢时中间层级整块让位：不占位（收拢后才放得下当前目录）。 */
.crumbs.is-collapsed .crumbs__mid {
  display: none;
}
/* 中间层级：可收缩（盒子极窄时让它让位，保住首级和当前目录两头）。 */
.crumbs__mid {
  display: inline-flex;
  align-items: center;
  min-width: 0;
  flex-shrink: 2;
  overflow: hidden;
}
</style>
