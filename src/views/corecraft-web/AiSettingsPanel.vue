<template>
  <div class="ai-panel">
    <!-- ⚠️ **总开关已删除**（2026-11-16 D6）：那个开关同时管着交互类（问答）、
         一次点击多次调用（生成/出卷）、零点击自主任务（命名）——三件不相干的事
         绑在一起，关掉它三件一起变行为。现在 AI 入口**一律常显**，
         没配好就在调用时报事实错误并引导到这里。
         **急停 = 停用或删除某条 API**（列表行上就有）。 -->
      <!-- API 列表：一人多个，提问时选一个用。
           整行点击 = 选中当前 API；编辑 / 收起来是两个明确的按钮，
           不用"点名字编辑 + 点圈选中"两块区域——两个点击区挨着最容易点错。
           ⚠️ 滚动收在这层内框里，「新增 API」钉在框外常驻；一条都没有时不画框。 -->
      <div class="ai-body">
        <el-scrollbar v-if="aiEndpoints.length" class="list-scroll">
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
                <span class="row__name">{{ e.name }}</span>
                <span v-if="e.id === currentEndpointId" class="row__badge">当前</span>
                <span v-if="!e.ready" class="row__badge is-warn">未配全</span>
                <span
                  v-if="testOf(e.id)?.done"
                  class="row__test"
                  :class="testOf(e.id).ok ? 'is-ok' : 'is-bad'"
                  :title="testOf(e.id).text"
                >{{ testOf(e.id).text }}</span>
              </span>
              <span class="row__ops">
                <KbTooltip content="测试连接" placement="top">
                  <button
                    type="button"
                    class="kb-icon-btn kb-icon-btn--primary row__op"
                    aria-label="测试连接"
                    :disabled="testOf(e.id)?.loading"
                    @click.stop="testRow(e)"
                  >
                    <KbLoader v-if="testOf(e.id)?.loading" size="sm" />
                    <KbSvgIcon v-else name="pulse" />
                  </button>
                </KbTooltip>
                <KbTooltip content="编辑" placement="top">
                  <button
                    type="button"
                    class="kb-icon-btn row__op"
                    aria-label="编辑"
                    @click.stop="openEdit(e)"
                  >
                    <KbSvgIcon name="edit" />
                  </button>
                </KbTooltip>
                <KbTooltip content="删除" placement="top">
                  <button
                    type="button"
                    class="kb-icon-btn kb-icon-btn--danger row__op"
                    aria-label="删除"
                    @click.stop="remove(e)"
                  >
                    <KbSvgIcon name="trash" />
                  </button>
                </KbTooltip>
              </span>
            </div>
          </div>
        </el-scrollbar>
      </div>

      <div class="ai-foot">
        <button type="button" class="ai-add" @click="openEdit(null)">
          <KbSvgIcon name="plus" />
          <span>新增 API</span>
        </button>
      </div>
  </div>

  <!-- 新增 / 编辑同一个弹窗：字段完全一样，没必要分两个 -->
  <el-dialog v-model="editing" :title="editingId ? '编辑 API' : '新增 API'" width="var(--cc-dialog-w-md)">
    <el-form label-width="76px">
      <el-form-item label="名称">
        <el-input v-model="draft.name" />
      </el-form-item>
      <el-form-item label="厂商">
        <el-select v-model="draft.vendor" clearable class="ai-field" @change="onVendorChange">
          <el-option v-for="v in aiVendors" :key="v.code" :label="v.name" :value="v.code" />
          <!-- 固定在最下方：字典里没有的厂商 / 自建中转。选它后地址和模型都手填 -->
          <el-option label="其他" :value="OTHER_VENDOR" />
        </el-select>
      </el-form-item>
      <!-- 协议类型在厂商和地址之间：它决定**地址怎么拼**（/chat/completions
           还是 /v1/messages），不是厂商的一个属性。选项读字典 ai_protocol，
           切换时按字典项 remark 带出该协议自己的默认地址。 -->
      <el-form-item label="协议类型">
        <el-select v-model="draft.protocol" class="ai-field" @change="onProtocolChange">
          <el-option v-for="p in aiProtocols" :key="p.value" :label="p.label" :value="p.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="API 地址">
        <el-input v-model="draft.baseUrl" placeholder="https://api.example.com" />
      </el-form-item>
      <!-- API Key 在模型之上：**填了密钥才能更新模型清单**。
           编辑时明文回填，type=password 默认掩码；show-password 的小眼睛点一下显明文。 -->
      <el-form-item label="API Key">
        <el-input v-model="draft.apiKey" type="password" show-password />
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
          <el-button
            size="small"
            :loading="refreshing"
            :disabled="!canRefresh"
            title="自定义厂商没有模型清单可拉，模型名直接手填"
            @click="onRefresh"
          >更新</el-button>
        </div>
      </el-form-item>
      <el-form-item label="自定义参数">
        <el-input v-model="draft.custom" type="textarea" :rows="3" placeholder='{"temperature": 0.7}' />
      </el-form-item>
    </el-form>
    <!-- 测试放左、保存放右：测试是「验一下」，保存才是这一步的终点。
         结果紧贴按钮内联显示（成功给耗时、失败给上游原文），不弹提示、不撑高 footer。 -->
    <template #footer>
      <div class="dlg-foot">
        <div class="dlg-foot__left">
          <el-button :loading="testOf('dialog')?.loading" @click="testDraft()">测试连接</el-button>
          <span
            v-if="testOf('dialog')?.done"
            class="test-result"
            :class="testOf('dialog').ok ? 'is-ok' : 'is-bad'"
            :title="testOf('dialog').text"
          >{{ testOf('dialog').text }}</span>
        </div>
        <div class="dlg-foot__right">
          <el-button @click="editing = false">取消</el-button>
          <el-button type="primary" :loading="saving" :disabled="!draft.name.trim()" @click="saveDraft">
            保存
          </el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
/**
 * AI 能力配置面板（启用开关 + API 列表 + 新增/编辑表单）。
 *
 * **为什么放业务壳（/app/*）而不是管理后台**：这是**每个人自己的**配置。
 * 放管理后台就变成只有超管能改，跟"每人一份"直接矛盾。skill 里"业务功能不挂
 * admin 壳"那条铁律在这里的具体理由就是这个。
 *
 * **2026-11-13 从独立页面（AiSettingsView）搬进「设置」弹窗的右区**：
 * 它是设置目录里的一项（SettingsDialog 的一个右区），与「界面」同行为 ——
 * 都出右区，只有本文件里这个新增/编辑表单才是叠层弹窗（表单是动作不是分类）。
 * 内容面一字未改：入口从 `/app/settings/ai` 页面变成设置弹窗的一个目录项。
 *
 * **开关和 API 列表分成两块**：开关是"要不要用 AI"，列表是"用哪个"。
 * 用户要能看到自己配过哪几个、切换着用，所以是列表不是一个表单。
 *
 * **界面上统一叫「API」，不叫「接口」**：用户配的是一个具体的模型服务商地址
 * （DeepSeek / 通义 / Moonshot…），"接口"在这个语境里含义太宽——同一个词项目里
 * 还用来指后端 HTTP 接口。两处混用时用户得自己猜这里说的是哪一种。
 */
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { confirmAction } from '../../utils/confirm'
import KbLoader from '../../components/corecraft-web/KbLoader.vue'
import KbSvgIcon from '../../components/corecraft-web/KbSvgIcon.vue'
import KbTooltip from '../../components/corecraft-web/KbTooltip.vue'
import {
  ensureAiConfigLoaded, aiEndpoints,
  addAiEndpoint, editAiEndpoint, removeAiEndpoint, useEndpoint, currentEndpointId,
  aiVendors, ensureAiVendorsLoaded, refreshVendorModels,
  aiProtocols, ensureAiProtocolsLoaded, testAiEndpoint
} from './useAi'

/** 「其他」厂商的固定 code：字典里没有对应类型，所以不给它拉模型清单 */
const OTHER_VENDOR = 'other'

const editing = ref(false)
const editingId = ref(null)
const draft = ref({ name: '', vendor: '', protocol: 'openai', baseUrl: '', model: '', apiKey: '', custom: '' })
const saving = ref(false)
const refreshing = ref(false)

onMounted(async () => {
  await Promise.all([
    ensureAiConfigLoaded(), ensureAiVendorsLoaded(), ensureAiProtocolsLoaded()
  ])
})

/** 当前选中的厂商（模型下拉的选项来自它） */
const currentVendor = computed(() => aiVendors.value.find((v) => v.code === draft.value.vendor) || null)

const modelOptions = computed(() => currentVendor.value?.models || [])

/** 模型下拉的文字：厂商给了名字就"名字 (id)"，没给就只有 id */
function modelLabel(m) {
  return m.label && m.label !== m.id ? `${m.label} (${m.id})` : m.id
}

/** 「其他」不在字典里 ⇒ 没有模型清单可刷；模型下拉本来就能手填（allow-create） */
const canRefresh = computed(() => Boolean(draft.value.vendor) && draft.value.vendor !== OTHER_VENDOR)

// ---------- 连通性测试 ----------
// **状态只存一份**：弹窗用 'dialog'，列表行用端点 id。两处入口都走 runTest，
// 不再各写一遍 loading / 成功 / 失败；结果就在触发它的那一行内联显示，不弹提示。
const tests = ref({})

function testOf(key) {
  return tests.value[key] || null
}

function setTest(key, patch) {
  tests.value = { ...tests.value, [key]: { ...(tests.value[key] || {}), ...patch } }
}

function clearTest(key) {
  const next = { ...tests.value }
  delete next[key]
  tests.value = next
}

async function runTest(key, payload) {
  if (tests.value[key]?.loading) return
  setTest(key, { loading: true, done: false, ok: false, text: '' })
  try {
    const res = await testAiEndpoint(payload)
    setTest(key, {
      loading: false,
      done: true,
      ok: Boolean(res.ok),
      // 成功说结果（含耗时），失败说上游原文——不写自己的前缀、不弹第二条提示
      text: res.ok ? `正常 · ${res.latencyMs ?? 0}ms` : (res.message || '不通')
    })
  } catch (e) {
    const text = (e?.code === 'ECONNABORTED' || e?.code === 'ETIMEDOUT')
      ? '请求超时，请稍后重试'
      : (e?.message || '请求失败')
    setTest(key, { loading: false, done: true, ok: false, text })
  }
}

/** 列表行：只带 endpointId ⇒ 地址 / 密钥 / 协议 / 模型全用库里那份，密钥不再过一遍前端 */
function testRow(e) {
  runTest(e.id, { endpointId: e.id })
}

/** 弹窗：用当前草稿（还没保存也能测）；带 endpointId 让后端兜底留空的字段 */
function testDraft() {
  runTest('dialog', {
    endpointId: editingId.value,
    baseUrl: (draft.value.baseUrl || '').trim(),
    apiKey: (draft.value.apiKey || '').trim(),
    protocol: draft.value.protocol || 'openai',
    model: (draft.value.model || '').trim()
  })
}

function openEdit(e) {
  editingId.value = e?.id ?? null
  clearTest('dialog')
  draft.value = {
    name: e?.name || '',
    vendor: e?.vendor || '',
    // 存量端点没存过 protocol，后端已归一成 openai；这里再兜一层是防旧缓存
    protocol: e?.protocol || 'openai',
    baseUrl: e?.baseUrl || '',
    model: e?.model || '',
    // 密钥明文回显（2026-11-11）：列表接口直接带回来，填进 password 框就是掩码，
    // 点右侧小眼睛才显明文。保存时留空仍是"不改"（后端语义没变）。
    apiKey: e?.apiKey || '',
    custom: e?.custom || ''
  }
  editing.value = true
}

/**
 * 字典里所有"自动带出来"的默认地址：厂商类型 remark + 协议项 remark。
 * **判据只留这一份**（切厂商 / 切协议共用）⇒ 不会一个覆盖、一个不覆盖。
 */
const defaultBaseUrls = computed(() => new Set([
  ...aiVendors.value.map((v) => v.baseUrl),
  ...aiProtocols.value.map((p) => p.baseUrl)
].filter(Boolean).map((u) => u.trim())))

/**
 * 带出默认地址：**只覆盖自动带出的值**（空、或正好等于某个字典默认地址），
 * 用户手填的自建 / 中转地址一律保留 —— 切协议「要跟着变」与编辑已有端点
 * 「别把我的地址冲掉」两头都要。两个切换入口共用这一个写入点。
 */
function applyDefaultBaseUrl(url) {
  const cur = (draft.value.baseUrl || '').trim()
  if (!cur || defaultBaseUrls.value.has(cur)) draft.value.baseUrl = url
}

/** 选厂商 = 顺带把它的地址带出来（字典里没填地址 / 选了「其他」就保留自己填的） */
function onVendorChange(vendor) {
  const v = aiVendors.value.find((x) => x.code === vendor)
  if (v?.baseUrl) applyDefaultBaseUrl(v.baseUrl)
}

/** 切协议 = 地址跟着协议变（地址来自字典项 remark，见 useAi.js 的约定） */
function onProtocolChange(protocol) {
  const p = aiProtocols.value.find((x) => x.value === protocol)
  if (p?.baseUrl) applyDefaultBaseUrl(p.baseUrl)
}

async function onRefresh() {
  if (refreshing.value) return
  refreshing.value = true
  try {
    // **不弹成功提示**：模型清单刷新完，下拉里的选项自己就变了 —— 看得见的结果不用再说一遍
    await refreshVendorModels({
      vendor: draft.value.vendor,
      // 拉清单的 URL / 请求头跟协议走，必须和保存端点时选的同一个
      protocol: draft.value.protocol || 'openai',
      baseUrl: (draft.value.baseUrl || '').trim(),
      // 编辑时明文已回填，会原样带回来；留空则后端用这条端点自己存的那把
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
      protocol: draft.value.protocol || 'openai',
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
  if (!(await confirmAction('删除 API'))) return
  await removeAiEndpoint(e.id)
  ElMessage.success('已删除')
}
</script>

<style scoped>
.ai-panel {
  display: flex;
  flex-direction: column;
  /* 撑满 SettingsDialog 固定高度：切 tab 外框和底部栏位置完全不变 */
  height: 100%;
}
.ai-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.list-scroll {
  max-height: 100%;
  border: 1px solid var(--cc-border);
  border-radius: var(--radius-md);
  background: color-mix(in srgb, var(--cc-surface-2) 55%, transparent);
}
.list-scroll :deep(.el-scrollbar__wrap) {
  max-height: 100%;
}
.ai-foot {
  flex: none;
  display: flex;
  align-items: center;
  min-height: 30px;
  margin-top: var(--sp-10);
  padding-top: var(--sp-9);
  border-top: 1px solid var(--cc-border);
}
.ai-add {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-6);
  height: 30px;
  padding: 0 var(--sp-10);
  border: 1px dashed var(--cc-border-strong);
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--cc-text-2);
  font-size: var(--text-xs);
  cursor: pointer;
  transition: var(--cc-transition);
}
.ai-add:hover {
  border-color: var(--cc-primary);
  background: var(--cc-primary-soft);
  color: var(--cc-primary);
}
/* ---------- API 列表 ---------- */
.list {
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
  padding: var(--sp-5);
}
.row {
  display: flex;
  align-items: center;
  gap: var(--sp-9);
  padding: var(--sp-9) var(--sp-11);
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: background .15s ease, box-shadow .15s ease;
}
.row:hover {
  background: var(--cc-surface);
  box-shadow: 0 1px 2px rgba(0, 0, 0, .06);
}
.row.is-current {
  background: var(--cc-surface);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--cc-primary) 42%, transparent), 0 1px 3px color-mix(in srgb, var(--cc-primary) 10%, transparent);
}
.row__mark {
  flex: none;
  width: 12px;
  height: 12px;
  border: 1.5px solid var(--cc-border-strong);
  border-radius: 50%;
  transition: border-color .15s ease, background .15s ease, box-shadow .15s ease;
}
.row.is-current .row__mark {
  border-color: var(--cc-primary);
  background: var(--cc-primary);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--cc-primary) 16%, transparent);
}
.row__text {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: var(--sp-6);
}
.row__name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--cc-text-1);
}
.row.is-current .row__name {
  font-weight: 600;
}
.row__badge {
  flex: none;
  padding: 0 6px;
  font-size: 11px;
  line-height: 16px;
  color: var(--cc-primary);
  background: color-mix(in srgb, var(--cc-primary) 12%, transparent);
  border-radius: 999px;
}
.row__badge.is-warn {
  color: var(--cc-warning);
  background: var(--cc-warning-soft);
}
.row__test {
  min-width: 0;
  max-width: 38%;
  margin-left: auto;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--text-xs);
}
.is-ok { color: var(--cc-success); }
.is-bad { color: var(--cc-danger); }
.row__ops {
  display: flex;
  align-items: center;
  gap: 2px;
  flex: none;
}
.row__op {
  width: 26px;
  height: 26px;
  font-size: 15px;
  opacity: .72;
}
.row:hover .row__op,
.row__op:hover {
  opacity: 1;
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

/* 弹窗底部：左「测试连接 + 结果」，右「取消 / 保存」。
   结果 flex:1 吃掉剩余宽度，长错误原文 ellipsis + title 看全；footer 高度不变。 */
.dlg-foot {
  display: flex;
  align-items: center;
  gap: var(--sp-8);
}
.dlg-foot__left {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 0;
  gap: var(--sp-10);
}
.dlg-foot__right {
  display: flex;
  flex: none;
  align-items: center;
  gap: var(--sp-8);
}
.test-result {
  flex: 1;
  min-width: 0;
  font-size: var(--text-xs);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
