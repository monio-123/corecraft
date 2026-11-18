<template>
  <el-dialog
    :model-value="modelValue"
    title="掌握度参与设置"
    width="var(--cc-dialog-w-sm)"
    :close-on-click-modal="false"
    @update:model-value="close"
  >
    <!-- **哪一条**：弹窗盖在列表上，原来那张卡片看不清了 ⇒ 只留一行认得出就够。
         没有标题的（随手记进来的那批）退回正文开头 —— 清洗规则用 store 的 excerptOf，
         和卡片摘要、删除确认框是同一份。 -->
    <p class="ms__now">{{ subject }}</p>

    <!-- 三态选择：**参与 / 不参与 / 跟随默认**。
         ⚠️ **第三档不是"多余的选项"**：「参与不参与」是二元的，但"这条自己说了算"
         和"这条跟着默认走"是两回事 —— 用户可能先关掉某一条，
         之后又想让它跟着回来。没有第三档就回不去了。
         ⚠️ **默认的口径变了**（2026-11-16 D10 类型退役）：原来默认是"跟随类型"
         （学习默认参与、笔记默认不参与），现在默认是"**有没有被检验过**"——
         被考过就参与，没被考过就不参与（后端判据只有一处 MasteryScope）。 -->
    <el-radio-group v-model="choice" class="ms__choices">
      <el-radio value="on">参与掌握度</el-radio>
      <el-radio value="off">不参与掌握度</el-radio>
      <el-radio value="inherit">跟随默认</el-radio>
    </el-radio-group>

    <p class="hint">
      「不参与」= 这条不统计掌握度、不显示档位、不计入目录的掌握度分布。<br />
      「跟随默认」= 取消这一条的单独设置：被检验过（有证据）就参与，没被检验过就不参与。
    </p>

    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button type="primary" :loading="saving" @click="confirm">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
/**
 * **单条知识点的掌握度参与设置**（2026-11-12 方案 C 的"可配置"那一半）。
 *
 * <p>方案 C 定了两件事：① 只有「学习」参与掌握度；② 这个"参与不参与"
 * **不能写死在代码里** —— 要能按类型配默认值，也能按单条覆盖。
 * 覆盖的入口就在这里。
 *
 * <p>⚠️ **一份弹窗，不是一堆**：列表页一页 20 张卡 ⇒ 每张卡各挂一个弹窗就是 20 个实例。
 * 弹窗只有**一个**，挂在列表页（AppLearnView）上，卡片只发 `masteryScope` 事件。
 *
 * <p>⚠️ **判据在前端只有"读"，没有"判"**：这一档对应的最终参与状态由后端解析
 * （`masteryEnabled`），这里展示的"当前设置"是从**原始覆盖值**（`masteryOverride`）
 * 推出来的 —— null=跟随类型、true=参与、false=不参与。
 */
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { setTopicMasteryEnabled, excerptOf } from './useCorecraftWebStore'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  topic: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'done'])

const saving = ref(false)
/** 'on' | 'off' | 'inherit' */
const choice = ref('inherit')

/** 哪一条 */
const subject = computed(() => {
  const t = props.topic
  if (!t) return ''
  return t.title || excerptOf(t.content, 24) || '这一条'
})


/**
 * 当前档位：'on' / 'off' / 'inherit'。
 *
 * <p>⚠️ **看的是原始覆盖值 `masteryOverride`，不是已解析的 `masteryEnabled`**：
 * 后端解析出来的最终值，在"明确关掉"和"跟随类型（而类型默认关）"两种情况下**是一样的**，
 * 只看最终值的话弹窗打开就分不清自己停在哪一档、也不知道怎么改回去。
 */
watch(() => [props.modelValue, props.topic?.id], ([open]) => {
  if (!open || !props.topic) return
  const ov = props.topic.masteryOverride
  choice.value = ov === true ? 'on' : (ov === false ? 'off' : 'inherit')
}, { immediate: true })

function close() {
  emit('update:modelValue', false)
}

async function confirm() {
  if (!props.topic) return
  saving.value = true
  try {
    const enabled = choice.value === 'on' ? true : (choice.value === 'off' ? false : null)
    await setTopicMasteryEnabled(props.topic.id, enabled)
    ElMessage.success('已保存')
    close()
    emit('done', props.topic)
  } catch {
    // 失败提示归 axios 拦截器
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.ms__now {
  margin: 0 0 var(--sp-10);
  font-size: var(--text-md);
  color: var(--cc-text-1);
}
.ms__choices {
  display: flex;
  flex-direction: column;
  gap: var(--sp-6);
}
</style>
