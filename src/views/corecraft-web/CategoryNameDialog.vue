<template>
  <!-- 宽度走 token：新建/重命名/删除是同一个操作簇，删除确认的 MessageBox 也吃这个
       token（base.css 的 --el-messagebox-width），改一处两边一起动。 -->
  <el-dialog v-model="visible" :title="title" width="var(--cc-dialog-w-sm)" append-to-body @open="reset">
    <el-input
      v-model="name"
      :placeholder="placeholder"
      maxlength="50"
      @keyup.enter="submit"
    />
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" :disabled="!name.trim()" @click="submit">
        {{ isRename ? '保存' : '创建' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
/**
 * 「给目录起个名」这一个交互，新建子目录和重命名共用。
 * 两者都是同一个输入框，差别只有初值和文案——拆成两个弹窗就是把同一件事写两遍。
 */
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { createCategory, renameCategory, ROOT_CATEGORY } from './useCorecraftWebStore'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 'create' = 新建（建在哪看 parentId）；'rename' = 改名（认的是 initialName） */
  mode: { type: String, default: 'create' },
  parentId: { type: Number, default: ROOT_CATEGORY },
  /** 父目录名，用来写清"建在哪" / "改的是谁" */
  parentLabel: { type: String, default: '' },
  /** 改名时的当前名字；新建时为空 */
  initialName: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue'])

const name = ref('')
const saving = ref(false)

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const isRename = computed(() => props.mode === 'rename')

const title = computed(() => {
  if (isRename.value) return `重命名「${props.parentLabel}」`
  return props.parentLabel ? '新建子目录' : '新建目录'
})

const placeholder = computed(() => {
  if (isRename.value) return '目录名'
  return props.parentLabel ? `在「${props.parentLabel}」下新建子目录` : '目录名，如 Java / 面试准备'
})

function reset() {
  name.value = props.initialName
}

async function submit() {
  const n = name.value.trim()
  if (!n || saving.value) return
  saving.value = true
  try {
    if (isRename.value) {
      if (n === props.initialName) { visible.value = false; return }
      await renameCategory(props.initialName, n)
      ElMessage.success('已重命名')
    } else {
      await createCategory(n, props.parentId)
      ElMessage.success(`已创建目录「${n}」`)
    }
    visible.value = false
  } catch (e) {
    ElMessage.error(e?.message || '操作失败')
  } finally {
    saving.value = false
  }
}
</script>
