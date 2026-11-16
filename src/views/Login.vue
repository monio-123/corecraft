<template>
  <!-- 登录页 = 产品封面（2026-11-15 重设计，方案「登录页重设计方案-1115」）：
       居中单卡 + 曜夜门面。**固定视觉不跟主题**（待办-1115 问题 6 的收敛一半在这）——
       样式直接写登录专属值（深底 #0f1117 + 星尘 grain + 暗角 + 金色主色），
       不走 --cc-* 主题 token（那套是给"会换装"的业务壳准备的）。
       老版的左右分栏（EP 默认蓝渐变 + 圆点纹理）整个退役。 -->
  <div class="login">
    <div class="login__card">
      <div class="login__brand">
        <span class="login__logo">B</span>
        <span class="login__name">Bro 知识库</span>
      </div>
      <el-form :model="loginForm" :rules="rules" ref="loginFormRef">
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="用户名"
            :prefix-icon="User"
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="密码"
            :prefix-icon="Lock"
            show-password
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        <el-form-item>
          <el-button class="login__go" type="primary" :loading="loading" @click="handleLogin">
            登录
          </el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { login } from '../api/auth'
import { saveAuth } from '../utils/auth'
import { bootstrapSession, landingRoute } from '../utils/session'

const router = useRouter()
const loginFormRef = ref(null)
const loading = ref(false)

const loginForm = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' }
  ]
}

const handleLogin = async () => {
  if (!loginFormRef.value) return

  await loginFormRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        const res = await login(loginForm.username, loginForm.password)
        saveAuth(res)

        ElMessage.success('已登录')
        // 先拉菜单才知道该落哪个壳：有管理后台菜单（超管）→ 后台，只有业务菜单 → 业务界面
        try { await bootstrapSession() } catch { /* 拉不到就按默认落点，不挡住登录 */ }
        router.replace(landingRoute())
      } catch (error) {
        // 处理错误信息
        const errorMsg = error.response?.data?.error_description || '登录失败'
        ElMessage.error(errorMsg)
      } finally {
        loading.value = false
      }
    }
  })
}
</script>

<style scoped>
/* ---------- 门面（固定曜夜色系，不跟主题） ---------- */
.login {
  height: 100%;
  width: 100%;
  display: flex;
  justify-content: center;
  align-items: center;
  overflow: hidden;
  background-color: #0f1117;
  /* 星尘 grain（曜夜 themes.css 同一份 tile）+ 四角暗角 ambience，同画两笔 */
  background-image:
    radial-gradient(130% 100% at 50% 30%, transparent 60%, rgba(0, 0, 0, 0.28) 100%),
    url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='600' height='420'%3E%3Ccircle cx='108' cy='88' r='1' fill='%23f0f2f8' fill-opacity='.5'/%3E%3Ccircle cx='432' cy='50' r='1' fill='%23f0f2f8' fill-opacity='.35'/%3E%3Ccircle cx='330' cy='285' r='1.5' fill='%23f0c87e' fill-opacity='.4'/%3E%3Ccircle cx='516' cy='185' r='1' fill='%23f0f2f8' fill-opacity='.3'/%3E%3Ccircle cx='198' cy='344' r='1' fill='%23f0c87e' fill-opacity='.3'/%3E%3Ccircle cx='48' cy='230' r='1.5' fill='%23f0f2f8' fill-opacity='.35'/%3E%3Ccircle cx='378' cy='138' r='1' fill='%23f0f2f8' fill-opacity='.28'/%3E%3C/svg%3E");
}

.login__card {
  width: min(400px, 92vw);
  /* 矮窗守卫（老版的 min() 经验保留）：表单 ~340px，装不下就滚 */
  max-height: calc(100vh - 48px);
  overflow-y: auto;
  padding: 40px 36px 32px;
  background: #161a23;
  border: 1px solid #2b3242;
  border-radius: 14px;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.25), 0 18px 44px rgba(0, 0, 0, 0.22);
}

.login__brand {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-bottom: 32px;
}

.login__logo {
  width: 38px;
  height: 38px;
  border-radius: 9px;
  background: linear-gradient(160deg, #e8b059, #c99440);
  color: #14100a;
  font-size: 20px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.login__name {
  font-size: 19px;
  font-weight: 600;
  color: #e8eaf0;
  letter-spacing: 0.02em;
}

/* 表单控件按曜夜语言覆盖（EP 默认白底在深卡上是两个世界）：
   只写登录页这一份，不进主题系统 */
.login :deep(.el-input__wrapper) {
  background: #1d222e;
  border: 1px solid #2b3242;
  box-shadow: none;
}
.login :deep(.el-input__wrapper.is-focus),
.login :deep(.el-input__wrapper:hover) {
  border-color: #e8b059;
  box-shadow: 0 0 0 2px rgba(232, 176, 89, 0.14);
}
.login :deep(.el-input__inner) {
  color: #e8eaf0;
  height: 40px;
}
.login :deep(.el-input__inner::placeholder) {
  color: #565d6e;
}
.login :deep(.el-input__prefix) {
  color: #565d6e;
}

.login__go {
  width: 100%;
  height: 40px;
  /* 曜夜金实底：登录是这一页唯一的"往前推进"动作 */
  background: linear-gradient(180deg, #e8b059, #d19e4c);
  border: none;
  color: #14100a;
  font-weight: 600;
}
.login__go:hover {
  background: linear-gradient(180deg, #f0bd6d, #e8b059);
  color: #14100a;
}

@media screen and (max-width: 480px) {
  .login__card {
    padding: 32px 24px 24px;
  }
}
</style>
