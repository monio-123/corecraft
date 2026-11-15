<template>
  <div class="ai-panel">
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
           不用"点名字编辑 + 点圈选中"两块区域——两个点击区挨着最容易点错。
           ⚠️ 滚动收在这层内框里，「新增 API」钉在框外常驻；一条都没有时不画框。 -->
      <div v-if="aiEndpoints.length" class="list">
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
            <span class="row__sub">
              <span class="row__sub-main">{{ e.model || '未填模型' }} · {{ hostOf(e.baseUrl) || '未填地址' }}</span>
              <span
                v-if="testOf(e.id)?.done"
                class="row__sub-test"
                :class="testOf(e.id).ok ? 'is-ok' : 'is-bad'"
                :title="testOf(e.id).text"
              >{{ testOf(e.id).text }}</span>
            </span>
          </span>
          <span class="row__ops">
            <button
              type="button"
              class="kb-text-btn kb-text-btn--test"
              :class="{ 'kb-busy': testOf(e.id)?.loading }"
              :disabled="testOf(e.id)?.loading"
              @click.stop="testRow(e)"
            >
              <KbLoader v-if="testOf(e.id)?.loading" size="sm" label="测试中" />
              <template v-else>测试</template>
            </button>
            <button type="button" class="kb-text-btn" @click.stop="openEdit(e)">编辑</button>
            <button type="button" class="kb-text-btn kb-text-btn--danger" @click.stop="remove(e)">删除</button>
          </span>
        </div>
      </div>

      <el-button class="ai-add" text type="primary" @click="openEdit(null)">
        <el-icon><Plus /></el-icon>新增 API
      </el-button>
    </template>
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
import { Plus } from '@element-plus/icons-vue'
import KbLoader from '../../components/corecraft-web/KbLoader.vue'
import {
  ensureAiConfigLoaded, aiEndpoints, saveAiConfig,
  addAiEndpoint, editAiEndpoint, removeAiEndpoint, useEndpoint, currentEndpointId,
  aiVendors, ensureAiVendorsLoaded, refreshVendorModels,
  aiProtocols, ensureAiProtocolsLoaded, testAiEndpoint
} from './useAi'

/** 「其他」厂商的固定 code：字典里没有对应类型，所以不给它拉模型清单 */
const OTHER_VENDOR = 'other'

const form = ref({ enabled: false })
const editing = ref(false)
const editingId = ref(null)
const draft = ref({ name: '', vendor: '', protocol: 'openai', baseUrl: '', model: '', apiKey: '', custom: '' })
const saving = ref(false)
const refreshing = ref(false)

onMounted(async () => {
  const [configRes] = await Promise.all([
    ensureAiConfigLoaded(), ensureAiVendorsLoaded(), ensureAiProtocolsLoaded()
  ])
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

function hostOf(url) {
  if (!url) return ''
  return url.replace(/^https?:\/\//, '').split('/')[0]
}
</script>

<style scoped>
.ai-panel {
  display: flex;
  flex-direction: column;
  /* 高度链（2026-11-15）：右区（.settings__pane）是 max-height:62vh 的滚动容器、
     高度随内容 ⇒ height:100% 接不上（列表一长按钮还是被卷走）。
     面板**自己封同样的顶**：内容矮就贴内容，到 62vh 就停——开关和「新增 API」
     钉住，中间的列表内框吃掉剩余高度自己滚，按钮永远可见。 */
  max-height: 62vh;
}
/* flex 列里 el-button 默认 stretch ⇒ 文字被居中；新增按钮是行级动作，靠左。
   钉在内框下方常驻（不跟着列表滚走）。 */
.ai-add {
  align-self: flex-start;
  margin-top: var(--sp-6);
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
/* 滚动不在列表自己身上：右区（SettingsDialog 的 .settings__pane）是唯一的
   滚动容器，这里只管行的排布。 */
/* 列表 = **内框**（2026-11-15）：滚动条收在框里（原来右区整页滚，
   「新增 API」按钮跟着被卷出视野）。框内是无框行（同左栏分类目录那套语言），
   行间靠 hover 底色 / 选中软底区分，不再各画一圈描边。 */
.list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
  padding: var(--sp-2);
  border: 1px solid var(--cc-border-card);
  border-radius: var(--radius);
}
/* §4.4(b) 无框行的 hover 语言：surface-2 底（不是描边） */
.row {
  display: flex;
  align-items: center;
  gap: var(--sp-10);
  padding: var(--sp-10) var(--sp-10);
  /* §4.4(d)：--cc-radius-sm(8px) 别名已删 ⇒ 28–34px 高的控件 → --radius-sm(6) */
  border-radius: var(--radius-sm);
  cursor: pointer;
}
.row:hover {
  background: var(--cc-surface-2);
}
/* 选中 = 主色软底（同 .settings__rail-btn.is-on），空心圈变实心点 */
.row.is-current {
  background: var(--cc-primary-soft);
}
/* 选中指示：空心圈 → 实心点。不用 el-radio，一是自己画可控，
   二是"点整行选中"本来就不需要一个真正的 input。 */
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
  /* §4.3(d)：EP 的 --el-color-warning-light-9 → cc 的 --cc-warning-soft */
  background: var(--cc-warning-soft);
}
/* 副标题行 = 主文字 + 测试结果。结果只在测过之后才出现，
   **主文字让出宽度、行高不变**（不撑高行、列表不跳）。 */
.row__sub {
  display: flex;
  align-items: center;
  gap: var(--sp-10);
  margin-top: var(--sp-3);
  font-size: var(--text-xs);
  color: var(--cc-text-3);
}
.row__sub-main {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.row__sub-test {
  flex: none;
  /* 失败时上游原文很长：最多占四成，保住模型名 / 地址还能读。 */
  max-width: 42%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.is-ok { color: var(--cc-success); }
.is-bad { color: var(--cc-danger); }
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

/* ---------- 连通性测试的两处入口 ---------- */
/* 三个文字按钮并排：给「测试」锁一个宽度，忙碌时换成 1em 加载图形也不横跳。
   .kb-busy 是统一加载层里现成的按钮忙碌态（kb-loading.css），图形跟主题走。 */
.kb-text-btn--test {
  min-width: 4em;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.kb-text-btn:disabled {
  cursor: default;
  opacity: .75;
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
