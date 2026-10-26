<template>
  <div class="ai">
    <div class="panel">
      <div class="panel__head">
        <span class="panel__title">AI 能力</span>
      </div>

      <!-- 开关。关掉之后，首页 / 知识点详情 / 列表页里所有 AI 入口都不出现。
           不写解释文案：「启用 AI」+ 一个开关已经说清了它是什么，
           再补一段"关掉会怎样"是在给一个开关讲道理。 -->
      <div class="switch-row">
        <div class="switch-row__name">启用 AI</div>
        <el-switch v-model="form.enabled" @change="onToggle" />
      </div>

      <!-- 配置只在开启时才有意义：关着还让用户填，是让人做一件当下用不上的事 -->
      <template v-if="form.enabled">
        <!-- API 列表：一人多个，提问时选一个用。
             整行点击 = 选中当前 API；编辑 / 收起来是两个明确的按钮，
             不用"点名字编辑 + 点圈选中"两块区域——两个点击区挨着最容易点错。 -->
        <div class="list">
          <div
            v-for="e in aiEndpoints"
            :key="e.id"
            class="row"
            :class="{ 'is-current': e.id === currentEndpointId }"
            @click="useEndpoint(e.id)"
          >
            <span class="row__mark" />
            <span class="row__text">
              <span class="row__name">
                {{ e.name }}
                <span v-if="e.id === currentEndpointId" class="row__badge">当前</span>
                <span v-if="!e.ready" class="row__badge is-warn">未配全</span>
              </span>
              <span class="row__sub">{{ e.model || '未填模型' }} · {{ hostOf(e.baseUrl) || '未填地址' }}</span>
            </span>
            <span class="row__ops">
              <button type="button" class="kb-text-btn" @click.stop="openEdit(e)">编辑</button>
              <button type="button" class="kb-text-btn kb-text-btn--danger" @click.stop="remove(e)">删除</button>
            </span>
          </div>

        </div>

        <el-button text type="primary" @click="openEdit(null)">
          <el-icon><Plus /></el-icon>新增 API
        </el-button>
      </template>
    </div>

    <!-- 新增 / 编辑同一个弹窗：字段完全一样，没必要分两个 -->
    <el-dialog v-model="editing" :title="editingId ? '编辑 API' : '新增 API'" width="520px">
      <el-form label-width="76px">
        <el-form-item label="名称">
          <el-input v-model="draft.name" />
        </el-form-item>
        <el-form-item label="厂商">
          <el-select v-model="draft.vendor" clearable class="ai-field" @change="onVendorChange">
            <el-option v-for="v in aiVendors" :key="v.code" :label="v.name" :value="v.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="API 地址">
          <el-input v-model="draft.baseUrl" placeholder="https://api.deepseek.com" />
        </el-form-item>
        <!-- API Key 在模型之上：**填了密钥才能更新模型清单** -->
        <el-form-item label="API Key">
          <el-input v-model="draft.apiKey" type="password" show-password :placeholder="keyPlaceholder" />
        </el-form-item>
        <el-form-item label="模型">
          <div class="ai-row">
            <el-select
              v-model="draft.model"
              class="ai-row__main"
              filterable
              allow-create
              default-first-option
            >
              <el-option v-for="m in modelOptions" :key="m.id" :label="modelLabel(m)" :value="m.id" />
            </el-select>
            <el-button size="small" :loading="refreshing" :disabled="!draft.vendor" @click="onRefresh">更新</el-button>
          </div>
        </el-form-item>
        <el-form-item label="自定义参数">
          <el-input v-model="draft.custom" type="textarea" :rows="3" placeholder='{"temperature": 0.7}' />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="editing = false">取消</el-button>
        <el-button size="small" type="primary" :loading="saving" :disabled="!draft.name.trim()" @click="saveDraft">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * AI 设置页。
 *
 * **为什么放业务壳（/app/*）而不是管理后台**：这是**每个人自己的**配置。
 * 放管理后台就变成只有超管能改，跟"每人一份"直接矛盾。skill 里"业务功能不挂
 * admin 壳"那条铁律在这里的具体理由就是这个。
 *
 * **开关和 API 列表分成两块**：开关是"要不要用 AI"，列表是"用哪个"。
 * 用户要能看到自己配过哪几个、切换着用，所以是列表不是一个表单。
 *
 * **界面上统一叫「API」，不叫「接口」**：用户配的是一个具体的模型服务商地址
 * （DeepSeek / 通义 / Moonshot…），"接口"在这个语境里含义太宽——同一个词项目里
 * 还用来指后端 HTTP 接口。两处混用时用户得自己猜这里说的是哪一种。
 */
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  ensureAiConfigLoaded, aiEndpoints, saveAiConfig,
  addAiEndpoint, editAiEndpoint, removeAiEndpoint, useEndpoint, currentEndpointId,
  aiVendors, ensureAiVendorsLoaded, refreshVendorModels
} from './useAi'

const form = ref({ enabled: false })
const editing = ref(false)
const editingId = ref(null)
const draft = ref({ name: '', vendor: '', baseUrl: '', model: '', apiKey: '', custom: '' })
const saving = ref(false)
const refreshing = ref(false)
/** 这条端点有没有配过密钥（**密钥本身不再回传**，只知道有没有） */
const hasKey = ref(false)

onMounted(async () => {
  const [configRes] = await Promise.all([ensureAiConfigLoaded(), ensureAiVendorsLoaded()])
  form.value = { enabled: configRes.config.enabled }
})

async function onToggle(enabled) {
  await saveAiConfig({ enabled })
}

/** 当前选中的厂商（模型下拉的选项来自它） */
const currentVendor = computed(() => aiVendors.value.find((v) => v.code === draft.value.vendor) || null)

const modelOptions = computed(() => currentVendor.value?.models || [])

/** 模型下拉的文字：厂商给了名字就"名字 (id)"，没给就只有 id */
function modelLabel(m) {
  return m.label && m.label !== m.id ? `${m.label} (${m.id})` : m.id
}

/** 只在**已经配过**时给一个状态词。留空 = 没配（不写"填入你的 API Key"那种引导） */
const keyPlaceholder = computed(() => (hasKey.value ? '已配置' : ''))

function openEdit(e) {
  editingId.value = e?.id ?? null
  hasKey.value = Boolean(e?.hasApiKey)
  draft.value = {
    name: e?.name || '',
    vendor: e?.vendor || '',
    baseUrl: e?.baseUrl || '',
    model: e?.model || '',
    // 密钥不再回传（2026-10-26 改）：留空 = 不改。
    // 想换密钥就重新填一次。
    apiKey: '',
    custom: e?.custom || ''
  }
  editing.value = true
}

/** 选厂商 = 顺带把它的地址带出来（字典里没填地址就保留用户自己填的） */
function onVendorChange(vendor) {
  const v = aiVendors.value.find((x) => x.code === vendor)
  if (v?.baseUrl) draft.value.baseUrl = v.baseUrl
}

async function onRefresh() {
  if (refreshing.value) return
  refreshing.value = true
  try {
    // **不弹成功提示**：模型清单刷新完，下拉里的选项自己就变了 —— 看得见的结果不用再说一遍
    await refreshVendorModels({
      vendor: draft.value.vendor,
      baseUrl: (draft.value.baseUrl || '').trim(),
      // 留空就让后端用这条端点自己存的那把（密钥不再下发到浏览器，前端手上没有）
      apiKey: (draft.value.apiKey || '').trim(),
      endpointId: editingId.value
    })
  } finally {
    refreshing.value = false
  }
}

async function saveDraft() {
  if (saving.value) return
  saving.value = true
  try {
    const payload = {
      id: editingId.value,
      name: draft.value.name.trim(),
      vendor: draft.value.vendor || '',
      baseUrl: (draft.value.baseUrl || '').trim(),
      model: (draft.value.model || '').trim(),
      apiKey: (draft.value.apiKey || '').trim(),
      custom: (draft.value.custom || '').trim()
    }
    // 新增时地址/模型/key 是必填的，后端会让它 ready=false，
    // 界面上直接标"未配全"，比在表单里加一堆必填校验省事也更少挡路
    await (editingId.value ? editAiEndpoint(payload) : addAiEndpoint(payload))
    editing.value = false
    ElMessage.success('已保存')
  } finally {
    saving.value = false
  }
}

async function remove(e) {
  await ElMessageBox.confirm('确认删除这个 API 么？', '删除 API', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
  await removeAiEndpoint(e.id)
  ElMessage.success('已删除')
}

function hostOf(url) {
  if (!url) return ''
  return url.replace(/^https?:\/\//, '').split('/')[0]
}
</script>

<style scoped>
.ai {
  max-width: 640px;
}
.switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-12);
  padding: var(--sp-4) 0 var(--sp-10);
  border-bottom: 1px solid var(--cc-border);
  margin-bottom: var(--sp-12);
}
.switch-row__name {
  font-size: var(--text-base);
  color: var(--cc-text-1);
}

/* ---------- API 列表 ---------- */
.list {
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
  margin-bottom: var(--sp-10);
}
.row {
  display: flex;
  align-items: center;
  gap: var(--sp-10);
  padding: var(--sp-10) var(--sp-10);
  border: 1px solid var(--cc-border);
  border-radius: var(--cc-radius-sm);
  cursor: pointer;
}
.row:hover {
  border-color: var(--cc-primary);
}
.row.is-current {
  border-color: var(--cc-primary);
  background: var(--cc-surface-2);
}
/* 选中指示：空心圈 → 实心点。不用 el-radio，一是自己画可控，
   二是"点整行选中"本来就不需要一个真正的 input。
   描边走 --cc-border-strong（base.css 里有这支 token）——
   原来写成 var(--cc-border-strong, <写死的灰>)，那个 fallback 是**死的**（token 一直在），
   同一个字面量还在别的文件里出现过（RowMenu 的 ⋯）。 */
.row__mark {
  flex: none;
  width: 13px;
  height: 13px;
  border: 1.5px solid var(--cc-border-strong);
  border-radius: 50%;
}
.row.is-current .row__mark {
  border-color: var(--cc-primary);
  background: var(--cc-primary);
}
.row__text {
  flex: 1;
  min-width: 0;
}
.row__name {
  display: flex;
  align-items: center;
  gap: var(--sp-6);
  font-size: var(--text-md);
  color: var(--cc-text-1);
}
.row__badge {
  padding: 0 var(--sp-6);
  font-size: var(--text-2xs);
  line-height: 16px;
  color: var(--cc-surface);
  background: var(--cc-primary);
  border-radius: var(--radius-xs);
}
.row__badge.is-warn {
  color: var(--cc-warning);
  background: var(--el-color-warning-light-9);
}
.row__sub {
  display: block;
  margin-top: var(--sp-3);
  font-size: var(--text-xs);
  color: var(--cc-text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.row__ops {
  display: flex;
  gap: var(--sp-2);
  flex: none;
}

/* ---------- 弹窗里的几处布局 ---------- */
/* 下拉铺满整行（厂商、模型） */
.ai-field {
  width: 100%;
}
/* 模型那一行：下拉 + 「更新」按钮并排 */
.ai-row {
  display: flex;
  align-items: center;
  gap: var(--sp-8);
  width: 100%;
}
.ai-row__main {
  flex: 1;
  min-width: 0;
}
</style>
