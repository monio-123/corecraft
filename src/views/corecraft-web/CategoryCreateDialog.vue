<template>
  <el-dialog v-model="visible" :title="title" width="380px" append-to-body @open="name = ''">
    <el-input
      v-model="name"
      :placeholder="parentLabel ? `在「${parentLabel}」下新建子目录` : '目录名，如 Java / 面试准备'"
      maxlength="50"
      show-word-limit
      @keyup.enter="submit"
    />
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" :disabled="!name.trim()" @click="submit">创建</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { createCategory, ROOT_CATEGORY } from './useCorecraftWebStore'

// 侧边栏「目录」标题右边的 ＋ 建一级，级联菜单里的「＋ 新建子目录」建子级——
// 两处是同一个动作，只是父级不同，所以共用这一个弹窗。
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  parentId: { type: Number, default: ROOT_CATEGORY },
  // 父目录名只用于标题和 placeholder 提示"建在哪"，不多查一次
  parentLabel: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue', 'created'])

const name = ref('')
const saving = ref(false)

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const title = computed(() => (props.parentLabel ? '新建子目录' : '新建目录'))

async function submit() {
  const n = name.value.trim()
  if (!n || saving.value) return
  saving.value = true
  try {
    await createCategory(n, props.parentId)
    visible.value = false
    ElMessage.success(`已创建目录「${n}」`)
    emit('created', n)
  } catch (e) {
    ElMessage.error(e?.message || '创建失败')
  } finally {
    saving.value = false
  }
}
</script>
