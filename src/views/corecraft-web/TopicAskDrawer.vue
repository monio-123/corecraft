<template>
  <!-- 对这条知识提问 / 随堂测验（右侧抽屉）：覆盖式不压缩正文，尺寸绑定 JS 数值。 -->
  <el-drawer v-model="drawerOpen" direction="rtl" :size="`${askW}px`" :with-header="false" modal>
    <div class="ask">
      <div
        class="ask__grip"
        role="separator"
        aria-orientation="vertical"
        aria-label="拖动调整问答框宽度，双击恢复默认"
        @pointerdown="emit('grip-down', $event)"
        @dblclick="emit('reset-width')"
      >
        <KbSvgIcon name="arrow-left" class="ask__grip__icon" />
      </div>
      <div class="kb-drawer__head">
        <div class="ask__mode-tabs" role="tablist">
          <button
            type="button"
            class="ask__mode-tab"
            :class="{ 'is-active': !quizOn }"
            @click="emit('close-quiz')"
          >AI 问答</button>
          <button
            type="button"
            class="ask__mode-tab"
            :class="{ 'is-active': quizOn }"
            @click="emit('open-quiz')"
          >随堂测验</button>
        </div>
        <button type="button" class="kb-icon-btn" aria-label="关闭" @click="drawerOpen = false">
          <KbSvgIcon name="close" />
        </button>
      </div>

      <div class="ask__body" ref="localBodyRef">
        <template v-if="quizOn">
          <div v-if="quizPhase === 'loading'" class="kb-center">
            <KbLoader size="sm" />
            <p>{{ quizLoading }}</p>
          </div>
          <div v-else-if="quizPhase === 'task'" class="kb-center">
            <template v-if="quizTask && quizTask.status === 'succeeded' && !quizTask.confirmedAt">
              <p>题已出好</p>
              <el-button size="small" @click="emit('answer-quiz-task')">作答</el-button>
              <el-button size="small" @click="emit('regenerate-quiz')">重新生成</el-button>
            </template>
            <template v-else>
              <KbLoader size="sm" />
              <p>正在出题…</p>
            </template>
          </div>
          <div v-else-if="quizPhase === 'error'" class="kb-center">
            <p class="kb-error" :class="{ 'is-quiet': quizQuiet }">{{ quizError }}</p>
            <el-button v-if="quizQuiet" size="small" @click="emit('regenerate-quiz')">重新生成</el-button>
            <el-button v-else size="small" @click="emit('open-quiz')">重试</el-button>
          </div>
          <template v-else>
            <div v-if="quizRunnerPhase === 'done' || quizStale" class="quiz-ops">
              <span v-if="quizStale" class="quiz-ops__stale">正文已变更</span>
              <span class="quiz-ops__gap"></span>
              <template v-if="quizRunnerPhase === 'done'">
                <el-button size="small" @click="emit('redo-quiz')">重做本卷</el-button>
                <el-button size="small" @click="emit('regenerate-quiz')">重新生成</el-button>
              </template>
            </div>
            <ExamRunner
              :initial-questions="quizQuestions"
              :attempt-id="quizAttemptId"
              :note="quizNote"
              :plan="quizPlan"
              @phase="quizRunnerPhase = $event"
            />
          </template>
        </template>

        <div v-if="!quizOn && messages.length && guideQuestions.length" class="ask__guide">
          <button
            v-for="q in guideQuestions"
            :key="q.key || q.label"
            type="button"
            class="ask__chip"
            @click="emit('ask-now', q)"
          >
            <KbSvgIcon :name="guideIconOf(q.key)" class="ask__chip-ico" />
            <span>{{ q.label }}</span>
          </button>
        </div>

        <div v-if="!quizOn && !messages.length && guideQuestions.length" class="ask__starter-wrap">
          <div class="ask__starter">
            <button
              v-for="q in guideQuestions"
              :key="q.key || q.label"
              type="button"
              class="ask__starter-card"
              @click="emit('ask-now', q)"
            >
              <span class="ask__starter-ico">
                <KbSvgIcon :name="guideIconOf(q.key)" />
              </span>
              <span>{{ q.label }}</span>
            </button>
          </div>
        </div>

        <template v-if="!quizOn && messages.length">
          <div v-for="(m, i) in messages" :key="i" class="turn">
            <div class="bubble is-user">{{ m.question }}</div>

            <details v-if="m.prompt" class="ask__meta-fold sent">
              <summary>请求上下文</summary>
              <pre class="sent__body">{{ m.prompt }}</pre>
            </details>

            <details v-if="m.thinking || m.reasoning" class="ask__meta-fold think">
              <summary>思考过程{{ m.thinking ? ' …' : '' }}</summary>
              <div v-if="m.reasoning" class="think__body">{{ m.reasoning }}</div>
              <div v-else class="think__waiting">正在思考…</div>
            </details>

            <div v-if="m.received && !m.done" class="think__count">正在生成…</div>

            <div v-if="m.answer" class="bubble is-ai" :class="{ 'is-error': m.error }">
              <div v-if="m.html && !m.error" class="md-answer milkdown-editor-body">
                <div class="ProseMirror" v-html="m.html"></div>
              </div>
              <template v-else>{{ m.answer }}</template>
            </div>
            <div class="ask__ops">
              <button
                v-if="m.answer && !m.error"
                type="button"
                class="kb-text-btn"
                @click="copyText(m.answer)"
              >
                <KbSvgIcon name="copy" class="kb-text-btn__ico" />复制
              </button>
              <button
                v-if="m.answer && !m.error && (hasBodyMark(m.answer) || m.spot)"
                type="button"
                class="kb-text-btn is-primary"
                @click="emit('open-insert', m, 'body')"
              >
                <KbSvgIcon name="edit" class="kb-text-btn__ico" />写入正文
              </button>
              <button
                v-if="m.answer && !m.error"
                type="button"
                class="kb-text-btn"
                @click="emit('open-insert', m, 'topic')"
              >
                <KbSvgIcon name="plus" class="kb-text-btn__ico" />存为新知识点
              </button>
              <button
                v-if="m.answer && !m.error && adoptFieldOf(m.questionKey)"
                type="button"
                class="kb-text-btn"
                @click="emit('open-adopt', m)"
              >
                <KbSvgIcon name="check" class="kb-text-btn__ico" />采纳
              </button>
            </div>
          </div>
        </template>
      </div>

      <div v-if="!quizOn" class="ask__foot">
        <div class="ask__composer">
          <textarea
            ref="askInputEl"
            :value="askInput"
            class="ask__input"
            rows="1"
            placeholder="输入问题或改写指令…"
            :disabled="asking"
            @input="onComposerInput"
            @keydown="onAskKeydown"
          />
          <div class="ask__composer-bar">
            <el-dropdown
              trigger="click"
              :persistent="ddKeepMounted"
              @visible-change="onDropdownShow"
              @command="emit('use-endpoint', $event)"
            >
              <span class="ask__model">
                {{ currentEndpoint ? currentEndpoint.name : '' }}
                <KbSvgIcon name="caret-down" class="ask__caret" />
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item
                    v-for="e in aiEndpoints"
                    :key="e.id"
                    :command="e.id"
                    :disabled="!e.ready"
                  >
                    {{ e.name }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>

            <button
              type="button"
              class="ask__send"
              :class="{ 'is-stop': asking }"
              :disabled="!asking && !canAsk"
              :aria-label="asking ? '停止生成' : '提问'"
              @click="asking ? emit('cancel-ask') : emit('submit-ask')"
            >
              <KbSvgIcon :name="asking ? 'stop' : 'arrow-up'" />
            </button>
          </div>
        </div>
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import KbSvgIcon from '../../components/corecraft-web/KbSvgIcon.vue'
import KbLoader from '../../components/corecraft-web/KbLoader.vue'
import ExamRunner from './ExamRunner.vue'
import { useDropdownKeepMounted } from '../../utils/dropdown'
import { adoptFieldOf } from './aiAdopt'
import { hasBodyMark } from './aiEdit'
import { copyText } from '../../utils/clipboard'

const props = defineProps({
  open: { type: Boolean, default: false },
  askW: { type: Number, default: 420 },
  askInput: { type: String, default: '' },
  asking: { type: Boolean, default: false },
  messages: { type: Array, default: () => [] },
  canAsk: { type: Boolean, default: false },
  guideQuestions: { type: Array, default: () => [] },
  currentEndpoint: { type: Object, default: null },
  aiEndpoints: { type: Array, default: () => [] },
  quizOn: { type: Boolean, default: false },
  quizPhase: { type: String, default: 'ready' },
  quizLoading: { type: String, default: '' },
  quizQuestions: { type: Array, default: () => [] },
  quizPlan: { type: Object, default: null },
  quizAttemptId: { type: [Number, String], default: null },
  quizNote: { type: String, default: '' },
  quizError: { type: String, default: '' },
  quizQuiet: { type: Boolean, default: false },
  quizStale: { type: Boolean, default: false },
  quizTask: { type: Object, default: null }
})

const emit = defineEmits([
  'update:open',
  'update:askInput',
  'body-ref',
  'grip-down',
  'reset-width',
  'submit-ask',
  'ask-now',
  'cancel-ask',
  'use-endpoint',
  'open-insert',
  'open-adopt',
  'open-quiz',
  'close-quiz',
  'answer-quiz-task',
  'redo-quiz',
  'regenerate-quiz'
])

const drawerOpen = computed({
  get: () => props.open,
  set: (v) => emit('update:open', v)
})

const localBodyRef = ref(null)
watch(localBodyRef, (el) => emit('body-ref', el), { immediate: true })

const { keepMounted: ddKeepMounted, onDropdownShow } = useDropdownKeepMounted()

const GUIDE_ICON_MAP = {
  'comb:none': 'sparkle',
  'polish:none': 'edit',
  'examples:none': 'code-block',
  'pick_category:categories': 'folder',
  'rename:none': 'text'
}
function guideIconOf(key) {
  return GUIDE_ICON_MAP[key] || 'sparkle'
}

const askInputEl = ref(null)
function autoResizeAskInput() {
  const el = askInputEl.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = `${Math.min(el.scrollHeight, 96)}px`
}
function onComposerInput(e) {
  emit('update:askInput', e.target.value)
  autoResizeAskInput()
}
function onAskKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
    e.preventDefault()
    if (!props.asking) emit('submit-ask')
  }
}
watch(() => props.askInput, (v) => {
  if (!v && askInputEl.value) askInputEl.value.style.height = ''
})

/** 答题器内部阶段（ready / grading / done），抽屉/测验收起时复位 */
const quizRunnerPhase = ref('ready')
watch(() => props.quizOn, (on) => {
  if (!on) quizRunnerPhase.value = 'ready'
})
</script>

<style scoped>
/* ---------- 对这条知识提问（右侧抽屉） ---------- */
.ask {
  display: flex;
  flex-direction: column;
  height: 100%;
  position: relative;
}
.ask__grip {
  position: absolute;
  left: calc(-1 * var(--el-drawer-padding-primary, 20px));
  width: var(--el-drawer-padding-primary, 20px);
  top: 50%;
  transform: translateY(-50%);
  display: flex;
  align-items: center;
  justify-content: center;
  height: 96px;
  cursor: col-resize;
  touch-action: none;
  color: var(--cc-text-3);
  background: var(--cc-surface-2);
  border: 1px solid var(--cc-border);
  border-radius: 0 var(--radius-lg) var(--radius-lg) 0;
  opacity: 0;
  transition: opacity 0.15s;
}
.ask__grip__icon {
  font-size: var(--text-base);
}
.ask__grip:hover,
.ask__grip:active {
  opacity: 1;
  color: var(--cc-primary);
  border-color: var(--cc-primary);
}
@media (hover: none) {
  .ask__grip { opacity: 0.5; }
}
.ask__body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  padding: var(--sp-10) 0;
  container-type: inline-size;
}
.ask__mode-tabs {
  display: inline-flex;
  align-items: center;
  padding: 3px;
  gap: 2px;
  background: var(--cc-surface-2);
  border: 1px solid var(--cc-border);
  border-radius: var(--radius-md);
}
.ask__mode-tab {
  height: 26px;
  padding: 0 var(--sp-10);
  border: 0;
  border-radius: calc(var(--radius-md) - 3px);
  background: transparent;
  font-size: var(--text-xs);
  color: var(--cc-text-3);
  cursor: pointer;
  transition: var(--cc-transition);
}
.ask__mode-tab:hover {
  color: var(--cc-text-1);
}
.ask__mode-tab.is-active {
  background: var(--cc-surface);
  color: var(--cc-text-1);
  font-weight: 600;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.08);
}
.ask__starter-wrap {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100%;
  padding: var(--sp-10) 0;
}
.ask__starter {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--sp-6);
  width: 100%;
}
.ask__starter-card {
  display: flex;
  align-items: center;
  gap: var(--sp-8);
  padding: var(--sp-8) var(--sp-10);
  border: 1px solid var(--cc-border);
  border-radius: var(--radius-md);
  background: var(--cc-surface);
  color: var(--cc-text-2);
  font-size: var(--text-sm);
  text-align: left;
  cursor: pointer;
  transition: var(--cc-transition);
}
.ask__starter-card:hover {
  border-color: var(--cc-primary);
  color: var(--cc-primary);
  background: var(--cc-primary-soft);
}
.ask__starter-ico {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: var(--radius-sm);
  background: var(--cc-surface-2);
  color: var(--cc-primary);
  font-size: var(--text-sm);
  flex: none;
}
.quiz-ops {
  display: flex;
  align-items: center;
  gap: var(--sp-8);
  margin-bottom: var(--sp-10);
}
.quiz-ops__stale {
  font-size: var(--text-xs);
  color: var(--cc-text-3);
}
.quiz-ops__gap {
  flex: 1;
}
.turn {
  margin-bottom: var(--sp-14);
}
.turn > * + * {
  margin-top: var(--sp-8);
}
.ask__meta-fold {
  font-size: var(--text-xs);
  color: var(--cc-text-3);
}
.ask__meta-fold summary {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 22px;
  padding: 0 9px;
  border-radius: var(--radius-pill);
  background: var(--cc-surface-2);
  border: 1px solid var(--cc-border);
  font-size: var(--text-2xs);
  color: var(--cc-text-3);
  cursor: pointer;
  list-style: none;
  user-select: none;
  transition: var(--cc-transition);
}
.ask__meta-fold summary:hover {
  color: var(--cc-text-1);
  border-color: var(--cc-border-card, var(--cc-border));
}
.ask__meta-fold summary::-webkit-details-marker {
  display: none;
}
.ask__meta-fold summary::before {
  content: '▸';
  font-size: 10px;
}
.ask__meta-fold[open] summary::before {
  content: '▾';
}
.think__body,
.sent__body {
  margin-top: var(--sp-6);
  padding: var(--sp-6) var(--sp-10);
  border-left: 2px solid var(--cc-primary-border);
  background: var(--cc-surface-2);
  border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.7;
}
.think__waiting {
  margin-top: var(--sp-6);
  padding-left: var(--sp-10);
  color: var(--cc-text-3);
  font-size: var(--text-xs);
}
.think__count {
  margin-top: var(--sp-6);
  padding-left: var(--sp-10);
  color: var(--cc-text-2);
  font-size: var(--text-xs);
}
.sent__body {
  font-family: var(--font-mono);
  font-size: var(--text-2xs);
  color: var(--cc-text-2);
}
.bubble {
  max-width: 100%;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.8;
}
.bubble.is-user {
  width: fit-content;
  max-width: 80%;
  margin-left: auto;
  padding: var(--sp-6) var(--sp-10);
  font-size: var(--text-sm);
  line-height: 1.6;
  color: var(--cc-text-1);
  background: var(--cc-primary-soft);
  border: 1px solid var(--cc-primary-border);
  border-radius: 12px 12px 3px 12px;
}
.bubble.is-ai {
  font-size: var(--text-md);
  color: var(--cc-text-1);
}
.md-answer :deep(.ProseMirror) {
  min-height: 0;
  max-height: none;
  overflow: visible;
  padding: 0;
  white-space: normal;
  font-size: var(--text-md);
  line-height: 1.75;
}
.md-answer :deep(.ProseMirror h1),
.md-answer :deep(.ProseMirror h2),
.md-answer :deep(.ProseMirror h3),
.md-answer :deep(.ProseMirror h4),
.md-answer :deep(.ProseMirror h5),
.md-answer :deep(.ProseMirror h6) {
  font-size: var(--text-md);
  margin-top: var(--sp-8);
}
.bubble.is-error {
  color: var(--cc-danger);
  font-size: var(--text-sm);
}
.ask__ops {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-4);
  margin-top: var(--sp-6);
  opacity: 0.72;
  transition: opacity 0.15s ease;
}
.turn:hover .ask__ops,
.ask__ops:focus-within {
  opacity: 1;
}
@media (hover: none) {
  .ask__ops {
    opacity: 1;
  }
}
.ask__ops .kb-text-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 24px;
  padding: 0 var(--sp-8);
  border: 1px solid var(--cc-border);
  border-radius: var(--radius-sm);
  background: var(--cc-surface);
  font-size: var(--text-xs);
  color: var(--cc-text-2);
}
.ask__ops .kb-text-btn:hover {
  color: var(--cc-primary);
  border-color: var(--cc-primary-border);
  background: var(--cc-primary-soft);
}
.ask__ops .kb-text-btn.is-primary {
  color: var(--cc-primary);
  border-color: var(--cc-primary-border);
  background: var(--cc-primary-soft);
}
.ask__guide {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-4) var(--sp-6);
  padding-bottom: var(--sp-8);
  margin-bottom: var(--sp-8);
  border-bottom: 1px solid var(--cc-border);
}
.ask__chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: var(--sp-4) var(--sp-8);
  font-size: var(--text-xs);
  color: var(--cc-text-2);
  background: var(--cc-surface);
  border: 1px solid var(--cc-border);
  border-radius: var(--radius-pill);
  cursor: pointer;
  transition: var(--cc-transition);
}
.ask__chip-ico {
  font-size: var(--text-xs);
  color: var(--cc-primary);
}
.ask__chip:hover {
  color: var(--cc-primary);
  border-color: var(--cc-primary);
  background: var(--cc-primary-soft);
}
.ask__foot {
  flex: none;
  padding-top: var(--sp-10);
  border-top: 1px solid var(--cc-border);
}
.ask__composer {
  display: flex;
  flex-direction: column;
  gap: var(--sp-6);
  padding: var(--sp-8) var(--sp-10);
  background: var(--cc-surface);
  border: 1px solid var(--cc-border);
  border-radius: var(--radius-lg);
  transition: var(--cc-transition);
}
.ask__composer:focus-within {
  border-color: var(--cc-primary);
  box-shadow: 0 0 0 2px var(--cc-primary-soft);
}
.ask__composer-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-6);
}
.ask__model {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-4);
  flex: none;
  max-width: 160px;
  height: 26px;
  padding: 0 var(--sp-6);
  font-size: var(--text-xs);
  color: var(--cc-text-2);
  border-radius: var(--radius-sm);
  cursor: pointer;
  outline: none;
  white-space: nowrap;
  overflow: hidden;
}
.ask__model:hover {
  background: var(--cc-surface-2);
  color: var(--cc-text-1);
}
.ask__caret {
  font-size: 11px;
  color: var(--cc-text-3);
}
.ask__input {
  width: 100%;
  min-height: 22px;
  max-height: 96px;
  padding: 0;
  font-family: inherit;
  font-size: var(--text-sm);
  line-height: 1.5;
  color: var(--cc-text-1);
  background: transparent;
  border: none;
  outline: none;
  resize: none;
  overflow-y: auto;
}
.ask__input::placeholder {
  color: var(--cc-text-4);
}
.ask__send {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  padding: 0;
  font-size: var(--text-base);
  color: var(--cc-surface);
  background: var(--cc-primary);
  border: none;
  border-radius: 50%;
  cursor: pointer;
  transition: var(--cc-transition);
}
.ask__send:hover:not(:disabled) {
  opacity: 0.86;
}
.ask__send:disabled {
  color: var(--cc-text-4);
  background: var(--cc-surface-3);
  cursor: not-allowed;
}
.ask__send.is-stop {
  color: var(--cc-text-2);
  background: var(--cc-surface-3);
}
</style>
