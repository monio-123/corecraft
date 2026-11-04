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
import { setView, VIEW_CARD } from '../../utils/viewPref'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 'create' = 新建（建在哪看 parentId）；'rename' = 改名（认的是 initialName） */
  mode: { type: String, default: 'create' },
  parentId: { type: Number, default: ROOT_CATEGORY },
  /** 父目录名，用来写清"建在哪" / "改的是谁" */
  parentLabel: { type: String, default: '' },
  /** 改名时的当前名字；新建时为空。只用来回显和"没改就别提交" */
  initialName: { type: String, default: '' },
  /** 改名时被改的是哪个目录。后端按 id 定位——名字是可变字段，认名字认不准 */
  initialId: { type: Number, default: null }
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
  // 建在哪**写在标题里**：原来它藏在 placeholder 里 —— 那个框是输入框、
  // 不是说明文字，父目录名放在那儿等于把它藏起来了。
  return props.parentLabel ? `在「${props.parentLabel}」下新建子目录` : '新建目录'
})

/**
 * placeholder 只说**这是什么**，不给业务示例。
 *
 * <p>原来的示例是「如 Java / 面试准备」—— 那是个技术面试的备考工具，
 * 而这是个**通用学习平台** ⇒ 示例给成任何具体方向都会替用户预设了学什么。
 * 留下的「绪论」是**中性的章节名**，只示范"名字大概这么写"，不带任何学科。
 */
const placeholder = '目录名，如：绪论'

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
      await renameCategory(props.initialId, n)
      ElMessage.success('已更新')
    } else {
      const created = await createCategory(n, props.parentId)
      // ⚠️ **手工建的一级目录 ⇒ 默认用「卡片」看**（2026-11-03 用户拍板：
      // 「AI 生成的默认是树，其他场景默认是卡片，老数据不用管」）。
      // AI 生成的书走代码兜底（一级目录 = 树，见 utils/viewPref.js）；
      // 手工建的一级目录写一份**我的覆盖 = 卡片** —— 它多半只有几条，
      // 摊成树只剩一行根，反而不如卡片直白。
      //
      // ⚠️ 这条覆盖写的是**"我的覆盖"**，系统默认那一行不动。
      if (created?.id != null && !props.parentId) {
        setView(created.id, VIEW_CARD)
      }
      ElMessage.success('已创建')
    }
    visible.value = false
  } catch (e) {
    // 提示归 request.js（走 axios 实例，唯一提示出口）
  } finally {
    saving.value = false
  }
}
</script>
