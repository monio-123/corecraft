<template>
  <!-- 画廊级双栏雕塑门面（三主题 1:1 联动，未登录即可切换并同步至业务内页与 Favicon） -->
  <div class="login" :class="`is-${curTheme}`">
    <div class="gallery-frame" aria-hidden="true" />

    <div class="monolith-shell">
      <div class="monolith">
        <!-- 左栏：主题专属矢量艺术画舱 + 品牌印记 + 内嵌三主题切换底座 -->
        <div class="art-col">
          <!-- 1. 曜夜：黑曜星盘 -->
          <svg
            v-if="curTheme === 'yaoye'"
            class="art-canvas"
            viewBox="0 0 320 420"
            fill="none"
            aria-hidden="true"
          >
            <circle cx="160" cy="205" r="118" stroke="rgba(235,182,98,0.16)" stroke-width="1" />
            <circle cx="160" cy="205" r="92" stroke="rgba(235,182,98,0.28)" stroke-width="1" stroke-dasharray="4 6" />
            <circle cx="160" cy="205" r="58" stroke="rgba(88,166,255,0.22)" stroke-width="1" />
            <ellipse cx="160" cy="205" rx="112" ry="42" transform="rotate(-28 160 205)" stroke="rgba(235,182,98,0.32)" stroke-width="1.2" />
            <ellipse cx="160" cy="205" rx="112" ry="42" transform="rotate(32 160 205)" stroke="rgba(88,166,255,0.20)" stroke-width="1" />
            <line x1="160" y1="65" x2="160" y2="345" stroke="rgba(235,182,98,0.12)" stroke-width="1" />
            <line x1="20" y1="205" x2="300" y2="205" stroke="rgba(235,182,98,0.12)" stroke-width="1" />
            <path d="M160 183 L165 200 L182 205 L165 210 L160 227 L155 210 L138 205 L155 200 Z" fill="rgba(235,182,98,0.85)" />
            <circle cx="242" cy="162" r="4" fill="#ebb665" />
            <circle cx="84" cy="242" r="3.5" fill="#58a6ff" />
            <circle cx="195" cy="122" r="2.5" fill="#fff" fill-opacity="0.6" />
          </svg>

          <!-- 2. 素雅：宋韵远山与宣纸青瓷 -->
          <svg
            v-else-if="curTheme === 'suya'"
            class="art-canvas"
            viewBox="0 0 320 420"
            fill="none"
            aria-hidden="true"
          >
            <circle cx="210" cy="145" r="46" fill="rgba(238,220,182,0.16)" stroke="rgba(238,220,182,0.42)" stroke-width="1" />
            <circle cx="210" cy="145" r="78" stroke="rgba(238,220,182,0.14)" stroke-width="1" stroke-dasharray="3 6" />
            <line x1="64" y1="0" x2="64" y2="420" stroke="rgba(238,220,182,0.08)" stroke-width="1" />
            <line x1="256" y1="0" x2="256" y2="420" stroke="rgba(238,220,182,0.08)" stroke-width="1" />
            <path d="M-20 310 Q 70 235, 165 285 T 340 250 L 340 420 L -20 420 Z" fill="rgba(45,106,93,0.32)" />
            <path d="M-20 345 Q 115 275, 220 325 T 340 295 L 340 420 L -20 420 Z" fill="rgba(28,68,59,0.55)" />
            <path d="M-20 378 Q 90 335, 205 365 T 340 340 L 340 420 L -20 420 Z" fill="rgba(17,42,36,0.82)" />
          </svg>

          <!-- 3. 本色：秩序蓝图与等轴测棱镜 -->
          <svg
            v-else
            class="art-canvas"
            viewBox="0 0 320 420"
            fill="none"
            aria-hidden="true"
          >
            <circle cx="160" cy="205" r="105" stroke="rgba(147,197,253,0.18)" stroke-width="1" />
            <circle cx="160" cy="205" r="70" stroke="rgba(56,189,248,0.25)" stroke-width="1" stroke-dasharray="5 5" />
            <polygon points="160,115 238,160 160,205 82,160" fill="rgba(59,130,246,0.20)" stroke="rgba(147,197,253,0.65)" stroke-width="1.2" />
            <polygon points="160,158 238,203 160,248 82,203" fill="rgba(14,165,233,0.16)" stroke="rgba(56,189,248,0.48)" stroke-width="1.2" />
            <polygon points="160,201 238,246 160,291 82,246" fill="rgba(30,64,175,0.24)" stroke="rgba(147,197,253,0.32)" stroke-width="1.2" />
            <line x1="160" y1="75" x2="160" y2="335" stroke="rgba(147,197,253,0.18)" stroke-width="1" stroke-dasharray="3 4" />
            <circle cx="160" cy="115" r="3.5" fill="#60a5fa" />
            <circle cx="238" cy="160" r="3" fill="#38bdf8" />
            <circle cx="82" cy="160" r="3" fill="#38bdf8" />
          </svg>

          <div class="art-top">
            <span class="brand-mark">B</span>
            <span class="brand-title">Bro 知识库</span>
          </div>

          <div class="art-bottom">
            <div class="theme-dock" role="tablist" aria-label="界面主题">
              <button
                v-for="t in themeList"
                :key="t.key"
                type="button"
                class="theme-pill"
                :class="{ on: curTheme === t.key }"
                @click="switchTheme(t.key)"
              >
                {{ t.name }}
              </button>
            </div>
          </div>
        </div>

        <!-- 右栏：纯粹登录区 -->
        <div class="form-col">
          <div class="form-eyebrow">KNOWLEDGE WORKSPACE</div>
          <h1 class="form-heading">登录</h1>

          <el-form :model="loginForm" :rules="rules" ref="loginFormRef" class="login-form">
            <div class="field">
              <label for="login-username">用户名</label>
              <el-form-item prop="username">
                <el-input
                  id="login-username"
                  v-model="loginForm.username"
                  autocomplete="username"
                >
                  <template #prefix>
                    <KbSvgIcon name="user" />
                  </template>
                </el-input>
              </el-form-item>
            </div>

            <div class="field">
              <label for="login-password">密码</label>
              <el-form-item prop="password">
                <el-input
                  id="login-password"
                  v-model="loginForm.password"
                  type="password"
                  show-password
                  autocomplete="current-password"
                  @keyup.enter="handleLogin"
                >
                  <template #prefix>
                    <KbSvgIcon name="lock" />
                  </template>
                </el-input>
              </el-form-item>
            </div>

            <el-button
              class="submit-btn"
              type="primary"
              :loading="loading"
              @click="handleLogin"
            >
              <span>登 录</span>
              <i v-if="!loading" class="submit-arr" aria-hidden="true">→</i>
            </el-button>
          </el-form>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import KbSvgIcon from '../components/corecraft-web/KbSvgIcon.vue'
import { login } from '../api/auth'
import { saveAuth } from '../utils/auth'
import { bootstrapSession, landingRoute } from '../utils/session'
import { currentTheme, setTheme, themeList } from '../utils/theme'

const router = useRouter()
const loginFormRef = ref(null)
const loading = ref(false)

const curTheme = computed(() => currentTheme())
const switchTheme = (key) => setTheme(key)

const loginForm = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const handleLogin = async () => {
  if (!loginFormRef.value) return
  await loginFormRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      const res = await login(loginForm.username, loginForm.password)
      saveAuth(res)
      ElMessage.success('已登录')
      try { await bootstrapSession() } catch { /* 拉不到就按默认落点，不挡住登录 */ }
      router.replace(landingRoute())
    } catch (error) {
      const errorMsg = error.response?.data?.error_description || '登录失败'
      ElMessage.error(errorMsg)
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.login {
  position: relative;
  height: 100%;
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.gallery-frame {
  position: absolute;
  inset: 20px;
  border: 1px solid currentColor;
  border-radius: 10px;
  pointer-events: none;
  opacity: 0.07;
}

@keyframes login-in {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.monolith-shell {
  position: relative;
  z-index: 2;
  width: 760px;
  height: 424px;
  padding: 6px;
  border-radius: 26px;
  animation: login-in 200ms cubic-bezier(0.2, 0.7, 0.3, 1) both;
}

.monolith {
  width: 100%;
  height: 100%;
  border-radius: 21px;
  display: grid;
  grid-template-columns: 320px 1fr;
  overflow: hidden;
  position: relative;
}

/* ---------- 左栏：艺术画舱 ---------- */
.art-col {
  position: relative;
  padding: 28px 26px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  overflow: hidden;
}

.art-canvas {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
}

.art-top {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: 10px;
}

.brand-mark {
  width: 32px;
  height: 32px;
  border-radius: 9px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 16px;
}

.brand-title {
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 0.02em;
}

.art-bottom {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.theme-dock {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 3px;
  border-radius: 999px;
}

.theme-pill {
  border: none;
  background: transparent;
  color: inherit;
  font-family: inherit;
  font-size: 11px;
  font-weight: 500;
  padding: 3px 10px;
  border-radius: 999px;
  cursor: pointer;
  transition: background 0.16s, color 0.16s;
}

/* ---------- 右栏：纯粹登录区 ---------- */
.form-col {
  padding: 44px 48px 38px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  position: relative;
}

.form-eyebrow {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  margin-bottom: 6px;
}

.form-heading {
  margin: 0 0 26px;
  font-size: 22px;
  font-weight: 600;
  letter-spacing: -0.01em;
}

.field {
  margin-bottom: 16px;
}

.field label {
  display: block;
  font-size: 12px;
  font-weight: 500;
  margin-bottom: 7px;
}

.field :deep(.el-form-item) {
  margin-bottom: 0;
}

.login :deep(.el-input__wrapper) {
  height: 42px;
  padding: 0 13px;
  border-radius: 10px;
  box-shadow: none;
  transition: background-color 0.15s, border-color 0.15s, box-shadow 0.15s;
}

.login :deep(.el-input__prefix) {
  margin-right: 9px;
}

.login :deep(.el-input__prefix svg) {
  width: 15px;
  height: 15px;
}

.login :deep(.el-input__inner) {
  height: 40px;
  font-size: 13.5px;
}

.submit-btn {
  width: 100%;
  margin-top: 12px;
  height: 43px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.04em;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  transition: transform 0.15s, box-shadow 0.15s, opacity 0.15s;
}

.submit-btn:hover:not(:disabled) {
  transform: translateY(-1px);
}

.submit-btn:active:not(:disabled) {
  transform: translateY(0);
}

.submit-arr {
  margin-left: 6px;
  font-style: normal;
  font-family: system-ui, sans-serif;
  font-weight: 400;
  opacity: 0.85;
}

/* =========================================================================
 * 主题 1：曜夜（Obsidian Astrolabe）
 * ========================================================================= */
.login.is-yaoye {
  background-color: #07090e;
  background-image:
    radial-gradient(50% 55% at 24% 25%, rgba(226, 164, 72, 0.15) 0%, transparent 70%),
    radial-gradient(50% 55% at 78% 80%, rgba(72, 118, 214, 0.12) 0%, transparent 70%),
    linear-gradient(to right, rgba(255, 255, 255, 0.025) 1px, transparent 1px),
    linear-gradient(to bottom, rgba(255, 255, 255, 0.025) 1px, transparent 1px);
  background-size: 100% 100%, 100% 100%, 40px 40px, 40px 40px;
  color: #f3c270;
}
.login.is-yaoye .monolith-shell {
  background: linear-gradient(155deg, rgba(235, 182, 98, 0.22) 0%, rgba(255, 255, 255, 0.03) 45%, rgba(88, 166, 255, 0.12) 100%);
  border: 1px solid rgba(235, 182, 98, 0.24);
  box-shadow: 0 32px 80px rgba(0, 0, 0, 0.78), 0 0 60px rgba(212, 155, 66, 0.08);
}
.login.is-yaoye .monolith {
  background: #10141d;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.12);
}
.login.is-yaoye .art-col {
  background:
    radial-gradient(85% 75% at 25% 20%, rgba(229, 173, 87, 0.22) 0%, transparent 68%),
    linear-gradient(165deg, #171c29 0%, #0b0e15 100%);
  border-right: 1px solid rgba(235, 182, 98, 0.16);
}
.login.is-yaoye .brand-mark {
  background: linear-gradient(145deg, #262e42, #121621);
  border: 1px solid rgba(235, 182, 98, 0.5);
  color: #f5ce85;
  box-shadow: 0 6px 16px rgba(212, 155, 66, 0.22);
}
.login.is-yaoye .brand-title {
  color: #f5f7fb;
}
.login.is-yaoye .theme-dock {
  background: rgba(8, 10, 16, 0.72);
  border: 1px solid rgba(255, 255, 255, 0.1);
  color: #8b95a7;
}
.login.is-yaoye .theme-pill.on {
  background: linear-gradient(180deg, #ebb665, #cf8d2e);
  color: #11141c;
  font-weight: 600;
}
.login.is-yaoye .form-col {
  background: linear-gradient(180deg, #131823 0%, #0e121b 100%);
}
.login.is-yaoye .form-eyebrow {
  color: #d49b42;
}
.login.is-yaoye .form-heading {
  color: #f3f6fc;
  font-family: "Noto Serif SC", Georgia, serif;
}
.login.is-yaoye .field label {
  color: #8c96a8;
}
.login.is-yaoye :deep(.el-input__wrapper) {
  background: #090c12;
  border: 1px solid rgba(255, 255, 255, 0.09);
  box-shadow: inset 0 1px 2px rgba(0, 0, 0, 0.45);
}
.login.is-yaoye :deep(.el-input__prefix),
.login.is-yaoye :deep(.el-input__suffix) {
  color: #647084;
}
.login.is-yaoye :deep(.el-input__inner) {
  color: #f1f5f9;
}
.login.is-yaoye :deep(.el-input__inner:-webkit-autofill) {
  -webkit-box-shadow: 0 0 0 100px #090c12 inset;
  -webkit-text-fill-color: #f1f5f9;
  caret-color: #f1f5f9;
}
.login.is-yaoye :deep(.el-input__wrapper.is-focus) {
  border-color: rgba(235, 182, 98, 0.7);
  box-shadow: inset 0 1px 2px rgba(0, 0, 0, 0.4), 0 0 0 3px rgba(212, 155, 66, 0.15);
}
.login.is-yaoye :deep(.el-input__wrapper.is-focus .el-input__prefix) {
  color: #ebb665;
}
.login.is-yaoye .submit-btn {
  background: linear-gradient(180deg, #eec073 0%, #cf8d2e 100%);
  border: 1px solid rgba(255, 231, 186, 0.4);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.45), 0 10px 26px rgba(212, 148, 54, 0.3);
  color: #12151c;
}

/* =========================================================================
 * 主题 2：素雅（Celadon Landscape）
 * ========================================================================= */
.login.is-suya {
  background-color: #ede7da;
  background-image:
    radial-gradient(55% 55% at 22% 20%, rgba(45, 106, 93, 0.14) 0%, transparent 70%),
    radial-gradient(55% 55% at 80% 82%, rgba(176, 138, 84, 0.15) 0%, transparent 70%),
    linear-gradient(to right, rgba(45, 75, 68, 0.04) 1px, transparent 1px),
    linear-gradient(to bottom, rgba(45, 75, 68, 0.04) 1px, transparent 1px);
  background-size: 100% 100%, 100% 100%, 36px 36px, 36px 36px;
  color: #2b5f53;
}
.login.is-suya .monolith-shell {
  background: linear-gradient(155deg, rgba(45, 106, 93, 0.22) 0%, rgba(255, 255, 255, 0.65) 50%, rgba(176, 138, 84, 0.22) 100%);
  border: 1px solid rgba(45, 106, 93, 0.22);
  box-shadow: 0 28px 68px rgba(42, 54, 49, 0.14), 0 4px 14px rgba(42, 54, 49, 0.06);
}
.login.is-suya .monolith {
  background: #fbf8f1;
  box-shadow: inset 0 1px 0 #ffffff;
}
.login.is-suya .art-col {
  background:
    radial-gradient(80% 70% at 30% 22%, rgba(56, 117, 102, 0.22) 0%, transparent 68%),
    linear-gradient(168deg, #23483f 0%, #17302a 100%);
  border-right: 1px solid rgba(45, 106, 93, 0.18);
}
.login.is-suya .brand-mark {
  background: rgba(251, 248, 241, 0.14);
  border: 1px solid rgba(235, 216, 178, 0.45);
  color: #f5ebd6;
  font-family: "Noto Serif SC", Georgia, serif;
}
.login.is-suya .brand-title {
  color: #f5ebd6;
  font-family: "Noto Serif SC", Georgia, serif;
}
.login.is-suya .theme-dock {
  background: rgba(15, 32, 28, 0.55);
  border: 1px solid rgba(235, 216, 178, 0.22);
  color: #b7c9c3;
}
.login.is-suya .theme-pill.on {
  background: #e9dfc8;
  color: #19332d;
  font-weight: 600;
}
.login.is-suya .form-col {
  background: linear-gradient(180deg, #fcfaf4 0%, #f6f1e5 100%);
}
.login.is-suya .form-eyebrow {
  color: #387566;
}
.login.is-suya .form-heading {
  color: #1f2926;
  font-family: "Noto Serif SC", Georgia, serif;
}
.login.is-suya .field label {
  color: #5c6863;
}
.login.is-suya :deep(.el-input__wrapper) {
  background: #efeadd;
  border: 1px solid #d5ccb8;
  box-shadow: inset 0 1px 2px rgba(31, 41, 38, 0.04);
}
.login.is-suya :deep(.el-input__prefix),
.login.is-suya :deep(.el-input__suffix) {
  color: #74827c;
}
.login.is-suya :deep(.el-input__inner) {
  color: #1f2926;
}
.login.is-suya :deep(.el-input__inner:-webkit-autofill) {
  -webkit-box-shadow: 0 0 0 100px #efeadd inset;
  -webkit-text-fill-color: #1f2926;
  caret-color: #1f2926;
}
.login.is-suya :deep(.el-input__wrapper.is-focus) {
  background: #fdfbf7;
  border-color: #2f6b5c;
  box-shadow: 0 0 0 3px rgba(47, 107, 92, 0.14);
}
.login.is-suya :deep(.el-input__wrapper.is-focus .el-input__prefix) {
  color: #2f6b5c;
}
.login.is-suya .submit-btn {
  background: linear-gradient(180deg, #387566 0%, #245347 100%);
  border: 1px solid rgba(255, 255, 255, 0.22);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.28), 0 10px 24px rgba(36, 83, 71, 0.26);
  color: #f8f5ec;
}

/* =========================================================================
 * 主题 3：本色（Blueprint Prism）
 * ========================================================================= */
.login.is-bense {
  background-color: #eaf0f8;
  background-image:
    radial-gradient(50% 55% at 20% 20%, rgba(37, 99, 235, 0.16) 0%, transparent 70%),
    radial-gradient(50% 55% at 82% 82%, rgba(14, 165, 233, 0.15) 0%, transparent 70%),
    linear-gradient(to right, rgba(30, 64, 175, 0.05) 1px, transparent 1px),
    linear-gradient(to bottom, rgba(30, 64, 175, 0.05) 1px, transparent 1px);
  background-size: 100% 100%, 100% 100%, 32px 32px, 32px 32px;
  color: #1d4ed8;
}
.login.is-bense .monolith-shell {
  background: linear-gradient(155deg, rgba(59, 130, 246, 0.26) 0%, rgba(255, 255, 255, 0.85) 50%, rgba(14, 165, 233, 0.24) 100%);
  border: 1px solid rgba(59, 130, 246, 0.24);
  box-shadow: 0 28px 68px rgba(15, 23, 42, 0.12), 0 4px 14px rgba(30, 64, 175, 0.06);
}
.login.is-bense .monolith {
  background: #ffffff;
  box-shadow: inset 0 1px 0 #ffffff;
}
.login.is-bense .art-col {
  background:
    radial-gradient(85% 75% at 20% 18%, rgba(56, 189, 248, 0.28) 0%, transparent 65%),
    linear-gradient(165deg, #172554 0%, #0f172a 100%);
  border-right: 1px solid rgba(226, 232, 240, 0.8);
}
.login.is-bense .brand-mark {
  background: linear-gradient(145deg, #3b82f6, #1d4ed8);
  border: 1px solid rgba(147, 197, 253, 0.5);
  color: #ffffff;
  box-shadow: 0 6px 16px rgba(37, 99, 235, 0.35);
}
.login.is-bense .brand-title {
  color: #f8fafc;
}
.login.is-bense .theme-dock {
  background: rgba(15, 23, 42, 0.65);
  border: 1px solid rgba(148, 163, 184, 0.24);
  color: #94a3b8;
}
.login.is-bense .theme-pill.on {
  background: #3b82f6;
  color: #ffffff;
  font-weight: 600;
}
.login.is-bense .form-col {
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
}
.login.is-bense .form-eyebrow {
  color: #2563eb;
}
.login.is-bense .form-heading {
  color: #0f172a;
}
.login.is-bense .field label {
  color: #475569;
}
.login.is-bense :deep(.el-input__wrapper) {
  background: #f1f5f9;
  border: 1px solid #cbd5e1;
}
.login.is-bense :deep(.el-input__prefix),
.login.is-bense :deep(.el-input__suffix) {
  color: #64748b;
}
.login.is-bense :deep(.el-input__inner) {
  color: #0f172a;
}
.login.is-bense :deep(.el-input__inner:-webkit-autofill) {
  -webkit-box-shadow: 0 0 0 100px #f1f5f9 inset;
  -webkit-text-fill-color: #0f172a;
  caret-color: #0f172a;
}
.login.is-bense :deep(.el-input__wrapper.is-focus) {
  background: #ffffff;
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.14);
}
.login.is-bense :deep(.el-input__wrapper.is-focus .el-input__prefix) {
  color: #2563eb;
}
.login.is-bense .submit-btn {
  background: linear-gradient(180deg, #3b82f6 0%, #1d4ed8 100%);
  border: 1px solid rgba(191, 219, 254, 0.35);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.35), 0 10px 24px rgba(37, 99, 235, 0.26);
  color: #ffffff;
}

@media (prefers-reduced-motion: reduce) {
  .monolith-shell,
  .submit-btn,
  .theme-pill {
    animation: none;
    transition: none;
  }
}

@media screen and (max-width: 760px) {
  .login {
    padding: 16px 0;
    overflow-y: auto;
  }
  .gallery-frame {
    display: none;
  }
  .monolith-shell {
    width: min(420px, 92vw);
    height: auto;
  }
  .monolith {
    grid-template-columns: 1fr;
  }
  .art-col {
    min-height: 168px;
    border-right: none !important;
  }
  .login.is-yaoye .art-col {
    border-bottom: 1px solid rgba(235, 182, 98, 0.16);
  }
  .login.is-suya .art-col {
    border-bottom: 1px solid rgba(45, 106, 93, 0.18);
  }
  .login.is-bense .art-col {
    border-bottom: 1px solid rgba(226, 232, 240, 0.8);
  }
  .form-col {
    padding: 30px 26px 28px;
  }
}
</style>
