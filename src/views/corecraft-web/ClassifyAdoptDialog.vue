<template>
  <!--
    「采纳 AI 归类结果」。

    ⚠️ **这是唯一一处把 AI 归类的建议写进 kp_topic 的界面上** ——
    跑任务的时候只存建议，一个字都不写库。理由：目录归属是**需要人工确认的主观判断**。

    ⚠️ **解析发生在打开的那一刻**，用的是**当下的目录清单**
    （见 classifyTask.js 的 parseClassifyResults）：
    任务跑完到人点采纳之间目录可能变了 ⇒ 拿旧清单解析出来的目录 id 是错的。
  -->
  <el-dialog
    :model-value="visible"
    title="采纳 AI 归类结果"
    width="var(--cc-dialog-w-lg, 720px)"
    :close-on-click-modal="false"
    @update:model-value="close"
  >
    <!-- ⚠️ **紧凑局部指示**（2026-11-13 统一加载层）：删掉本组件自带的 .ca__loading
         外观（全站统一一份），只留这一行的排布。 -->
    <div v-if="loading" class="ca__loading kb-state-line">
      <KbLoader size="sm" />
      <span>加载中…</span>
    </div>

    <p v-else-if="error" class="ca__error">{{ error }}</p>

    <template v-else>
      <!-- 每一条建议一行：勾选 + 标题 + 目录 + 可改的目录下拉 -->
      <div class="ca__list">
        <div
          v-for="r in rows"
          :key="r.stepId"
          class="ca__row"
          :class="{ 'is-off': !r.on }"
        >
          <input v-model="r.on" type="checkbox" :aria-label="`采纳 ${r.display || r.title}`" />
          <div class="ca__body">
            <!-- ⚠️ **显示用 `display`，写入用 `title`**：
                 AI 没给名字的时候 `title` 是空（= 不改标题），
                 而 `display` 退回这条知识点原来的名字（人认得出来是哪条）。 -->
            <p class="ca__title">{{ r.display || r.title || '（无标题）' }}</p>
            <p class="ca__meta">
              <!-- 目录：AI 建议归到哪。**显示的是当下的目录清单算出来的路径**。 -->
              <span>目录：{{ classifyPickText(r, categories) }}</span>
            </p>
            <!-- 目录下拉：**每一条都能改** —— AI 的建议只是初始值。 -->
            <CategoryTreeSelect
              v-model="r.pick"
              class="ca__pick"
              size="small"
              :categories="categories"
              :new-category="r.newCategory"
            />
            <p v-if="r.note" class="ca__note">{{ r.note }}</p>
          </div>
        </div>
      </div>
      <p v-if="!rows.length" class="ca__empty">这个任务没有可采纳的结果。</p>
    </template>

    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button
        type="primary"
        :loading="saving"
        :disabled="!picked.length"
        @click="confirm"
      >
        采纳 {{ picked.length }} 条
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
/**
 * 「采纳 AI 归类结果」对话框（**全站一个实例**，挂在 GenTaskPanel 里）。
 *
 * <p>形态和 `AiOrganizeDialog`（同步那条路）**刻意做成一样**：
 * 逐条预览 → 勾掉不要的 → 改目录 → 采纳。
 * ⇒ 用户在两条路上学会的动作是同一套，不用重新适应。
 *
 * <p>⚠️ **区别在「解析时机」**：同步那条是"问完就解析"，
 * 这条是"**点开的时候**解析"—— 用的是当下的目录清单。
 */
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import KbLoader from '../../components/corecraft-web/KbLoader.vue'
import CategoryTreeSelect from './CategoryTreeSelect.vue'
import { getCategories } from './useCorecraftWebStore'
import {
  getGenTaskDetail, parseClassifyResults, adoptClassifyResults, classifyPickText, adoptableRows
} from './classifyTask'

const props = defineProps({
  modelValue: Boolean,
  /** 要看结果的任务（{ id }） */
  task: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'done'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const rows = ref([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')

const categories = computed(() => getCategories())
// ⚠️ **按钮计数和写库用同一个口径**（`adoptableRows`）：
// 以前这里 `on && !note`、写库那边 `on !== false` 两套，note 行一多就对不上。
const picked = computed(() => adoptableRows(rows.value))

/** 打开时拉详情 + 现解析 */
async function load() {
  if (!props.task?.id) return
  loading.value = true
  error.value = ''
  try {
    const res = await getGenTaskDetail(props.task.id)
    // ⚠️ **解析在这里现做**（用当下的目录清单）——
    // 任务跑完到现在，目录可能已经被删/改名/新建了同名的。
    rows.value = parseClassifyResults(res?.data || {})
  } catch (e) {
    error.value = e?.message || '拉不到结果'
  } finally {
    loading.value = false
  }
}

function close() {
  visible.value = false
}

async function confirm() {
  if (saving.value) return
  saving.value = true
  try {
    const n = await adoptClassifyResults(rows.value)
    ElMessage.success(`已采纳 ${n} 条`)
    close()
    emit('done')
  } catch (e) {
    // 提示归 request.js（走 axios 实例，唯一提示出口）
  } finally {
    saving.value = false
  }
}

// 打开时加载。watch 而不是 onMounted：这个对话框是**复用**的（开一次关一次再开一次），
// onMounted 只会在第一次挂载时跑。
watch(
  () => props.modelValue,
  (open) => {
    if (open) load()
    else rows.value = []
  }
)
</script>

<style scoped>
/* 弹窗里的加载行：只管内距（图形与文字的排布由 .kb-state-line 统一给）。
   ⚠️ 原来这里还有一整套自己的外观（居中 + 文字色 + 字号）⇒ 与全站其它加载点
   三种形态三种样子。现在统一成紧凑局部指示。 */
.ca__loading {
  padding: var(--sp-13) 0;
}
.ca__error {
  margin: 0;
  color: var(--cc-warning);
  font-weight: 500;
}
.ca__list {
  max-height: 380px;
  overflow-y: auto;
}
.ca__row {
  display: flex;
  align-items: flex-start;
  gap: var(--sp-8);
  padding: var(--sp-7) 0;
  border-bottom: 1px solid var(--cc-border);
}
.ca__row:last-child {
  border-bottom: 0;
}
.ca__row.is-off {
  opacity: 0.55;
}
.ca__body {
  min-width: 0;
  flex: 1;
}
.ca__title {
  margin: 0;
  color: var(--cc-text-1);
  font-size: var(--text-sm);
  line-height: 1.55;
  word-break: break-word;
}
.ca__meta {
  margin: var(--sp-2) 0 0;
  color: var(--cc-text-3);
  font-size: var(--text-xs);
  line-height: 1.6;
  word-break: break-word;
}
.ca__note {
  margin: var(--sp-4) 0 0;
  color: var(--cc-warning);
  font-size: var(--text-xs);
  line-height: 1.5;
}
.ca__pick {
  width: 100%;
  max-width: 280px;
  margin-top: var(--sp-4);
}
.ca__empty {
  margin: 0;
  padding: var(--sp-12) 0;
  text-align: center;
  color: var(--cc-text-3);
  font-size: var(--text-xs);
}
</style>
