<template>
  <el-dialog
    :model-value="modelValue"
    title="归类到目录"
    width="var(--cc-dialog-w-sm)"
    :close-on-click-modal="false"
    @update:model-value="close"
  >
    <!-- **哪一条**：弹窗盖在列表上，原来那张卡片看不清了 ⇒ 只留一行认得出就够。
         没有标题的（随手记进来的那批）退回正文开头 —— 清洗规则用 store 的
         excerptOf，和卡片摘要、删除确认框是同一份。
         ⚠️ **批量时没有"哪一条"**（2026-11-11）：直接说多少条。
         这一行不是装饰，它回答"我这一下会动到什么东西"。 -->
    <p class="cz__now">{{ subject }}</p>

    <!-- 目录选择器。**复用 CategoryTreeSelect**（唯一一份目录树）：
         层级由树的缩进表达，不同父目录下同名目录也分得清。
         「待归类」是额外挂在树顶的一个节点（extraTop）—— 它不是目录，是个桶：
         选它 = 把这条移出目录（categoryId 写 null）。 -->
    <CategoryTreeSelect
      v-model="picked"
      class="cz__pick"
      :categories="categories"
      :extra-top="extraTop"
      :placeholder="multiple ? '选择一个目录' : ''"
    />

    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button type="primary" :loading="saving" :disabled="multiple && picked == null" @click="confirm">
        {{ multiple ? `归类 ${rows.length} 条` : '确定' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
/**
 * **手动归类**：给一条知识点挑一个目录（或挑「待归类」把它移出去）。
 *
 * ## 为什么是弹窗，不是拖
 *
 * 2026-11-05 撤掉的是**拖动归类**（把卡片拖到目录上）—— 那条路要拖拽源、
 * 落点浮层、拖到一半的目标高亮一整套。
 * ⚠️ **但"给一条知识换个目录"这件事本身没被撤**（2026-11-06 用户澄清：
 * 「我之前让删除的是 拖动卡片归类但是手动选择这个还是要有」）⇒ 手动这条路补回来，
 * 补的是**最省事**的一种：一个树形下拉 + 一次确认，不需要拖。
 *
 * ## 为什么是树形下拉
 *
 * 目录是真的分层的，而**不同父目录下允许同名**（唯一键是 user_id + parent_id + name），
 * 平铺的名字列表分不清"这个 Mybatis 是哪一个"（见 CategoryTreeSelect 的注释）。
 *
 * ## 一份弹窗，不是一堆
 *
 * 列表页一页 20 张卡 ⇒ 每张卡各挂一个弹窗就是 20 个实例。
 * 弹窗只有**一个**，挂在列表页（AppLearnView）上，卡片只发 `categorize` 事件。
 *
 * ## 批量形态（2026-11-11）
 *
 * 传 `topics`（多条）进来就是批量形态，**不另造第二个"批量归类"弹窗**：
 * 标题行改说「已选 N 条知识点」，目录选择器**留空**（勾的那些往往散在不同目录里，
 * 摆一个上去等于替用户做了个假承诺），按钮「归类 N 条」、没选目录时禁用。
 * 两条路只有一处差异（写库时逐条 + 只刷一次），其余全部共用。
 */
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import CategoryTreeSelect from './CategoryTreeSelect.vue'
import { updateTopic, updateTopicsCategory, excerptOf, UNCATEGORIZED } from './useCorecraftWebStore'

const props = defineProps({
  modelValue: Boolean,
  /** 要归类的那一条。null = 还没选中（弹窗关着时的常态） */
  topic: { type: Object, default: null },
  /**
   * **批量归类**要处理的那一批（2026-11-11）。
   * 非空时这条弹窗就是批量形态；为空时退回单条（读 `topic`）——
   * 两条入口共用一个组件，不另造第二个"批量归类"弹窗。
   */
  topics: { type: Array, default: () => [] },
  categories: { type: Array, default: () => [] }
})
const emit = defineEmits(['update:modelValue', 'done'])

/** 这一下要动到的那些条（单条 = 一条）。 */
const rows = computed(() =>
  props.topics.length ? props.topics : (props.topic ? [props.topic] : [])
)
/** 是不是批量形态。 */
const multiple = computed(() => rows.value.length > 1)

/** 选中的目录 id。「待归类」那个桶用 UNCATEGORIZED 哨兵 —— 它不是目录，没有 id。 */
const picked = ref(null)
const saving = ref(false)

/** 树顶那个「待归类」节点。 */
const extraTop = { value: UNCATEGORIZED, label: '待归类' }

/** 哪一条：标题优先，没有标题就用正文开头；批量则报条数。 */
const subject = computed(() => {
  if (multiple.value) return `已选 ${rows.value.length} 条知识点`
  const t = rows.value[0]
  if (!t) return ''
  return String(t.title || excerptOf(t.content, 24) || '').trim()
})

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    // ⚠️ **批量时留空**，不预选任何目录：
    // 勾的那些很可能散在不同目录里，"摆一个上去"等于替用户做了个假承诺
    // （确定下去剩下的都被搬到这一个目录里）。留空 + 按钮禁用，逼用户明确选一次。
    if (multiple.value) {
      picked.value = null
      return
    }
    // 单条时先摆上它现在在哪：用户多半是"改一个"，不是"从零挑"。
    // 没归类的（categoryId 是 null）⇒ 摆在「待归类」上。
    picked.value = rows.value[0]?.categoryId ?? UNCATEGORIZED
  }
)

function close() {
  emit('update:modelValue', false)
}

async function confirm() {
  if (!rows.value.length || saving.value) return
  // 批量时没选目录就不执行（按钮已禁用，这里是第二道）。
  if (multiple.value && picked.value == null) return
  const categoryId = picked.value === UNCATEGORIZED ? null : picked.value
  saving.value = true
  try {
    if (multiple.value) {
      // 逐条写库、只刷一次索引（见 store 的 updateTopicsCategory）。
      const { ok, failed } = await updateTopicsCategory(rows.value, categoryId)
      if (ok && !failed) ElMessage.success(`已归类 ${ok} 条`)
      else if (ok) ElMessage.warning(`已归类 ${ok} 条，${failed} 条失败`)
    } else {
      await updateTopic(rows.value[0].id, { categoryId })
      ElMessage.success('已保存')
    }
    close()
    emit('done')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.cz__now {
  margin-bottom: var(--sp-8);
  color: var(--cc-text-3);
  font-size: var(--text-xs);
  line-height: 1.6;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cz__pick {
  width: 100%;
}
</style>
