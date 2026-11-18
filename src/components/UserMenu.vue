<template>
  <!--
    用户菜单：**两个壳共用这一份**（管理后台的顶栏、业务壳的侧边栏底栏）。

    下拉视觉对齐 RowMenu.vue（16px KbSvgIcon + .row-menu__item--danger 危险红字红 hover），
    弹窗结构按 brove-skills/ui-style §1（440 宽 / 上标签表单 / 居中 64px 头像即入口 / 去昵称）。
  -->
  <div class="user-menu">
    <el-dropdown
      trigger="click"
      popper-class="um-popper"
      :persistent="keepMounted"
      @visible-change="onMenuVisible"
      @command="onCommand"
    >
      <span class="user-menu__trigger">
        <KbAvatar :value="user.avatar" :fallback="avatarFallback" :size="size" />
        <span class="user-menu__name">{{ sessionUserName(user) }}</span>
        <KbSvgIcon class="user-menu__caret" :class="{ 'is-open': menuOpen }" name="caret-down" :sw="2.4" />
      </span>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="profile">
            <KbSvgIcon class="row-menu__item-icon" name="user" />
            <span>编辑个人信息</span>
          </el-dropdown-item>
          <el-dropdown-item command="password">
            <KbSvgIcon class="row-menu__item-icon" name="lock" />
            <span>修改密码</span>
          </el-dropdown-item>
          <el-dropdown-item command="logout" divided class="row-menu__item--danger">
            <KbSvgIcon class="row-menu__item-icon" name="logout" />
            <span>退出登录</span>
          </el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>

    <el-dialog v-model="profileVisible" title="编辑个人信息" width="440px">
      <!-- 居中 64px 头像即换肤入口：hover 现相机角标、点击展开选择条、选完即收 -->
      <div class="um-head">
        <button
          type="button"
          class="um-avatar-btn"
          :class="{ 'is-on': picking }"
          :aria-expanded="picking"
          @pointerdown.stop
          @click="picking = !picking"
        >
          <KbAvatar :value="profileForm.avatar" :fallback="avatarFallback" :size="64" />
          <span class="um-cam" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round"><path d="M4 8h3.2L9.4 5h5.2l2.2 3H20v11H4z"/><circle cx="12" cy="13.2" r="3.4"/></svg>
          </span>
        </button>
      </div>

      <div v-if="picking" class="um-pick">
        <button
          v-for="a in BUILTIN_AVATARS"
          :key="a.id"
          type="button"
          class="um-pick__item"
          :class="{ 'is-on': profileForm.avatar === 'builtin:' + a.id }"
          @pointerdown.stop
          @click="pickBuiltin(a.id)"
        >
          <KbAvatar :value="'builtin:' + a.id" :size="30" />
        </button>
        <el-upload :show-file-list="false" :http-request="handleAvatarUpload" accept="image/*" class="um-pick__upload">
          <button type="button" class="um-pick__item um-pick__add">＋</button>
        </el-upload>
      </div>

      <div class="um-field">
        <label>用户名</label>
        <el-input v-model="profileForm.username" maxlength="20" />
      </div>
      <div class="um-field">
        <label>邮箱</label>
        <el-input v-model="profileForm.email" maxlength="30" />
      </div>
      <div class="um-field">
        <label>手机</label>
        <el-input v-model="profileForm.mobile" maxlength="20" />
      </div>

      <template #footer>
        <el-button @click="profileVisible = false">取消</el-button>
        <el-button type="primary" :loading="profileSaving" @click="saveProfile">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="passwordVisible" title="修改密码" width="440px">
      <div class="um-field">
        <label>旧密码</label>
        <el-input v-model="passwordForm.oldPassword" type="password" show-password @keyup.enter="savePassword" />
      </div>
      <div class="um-field">
        <label>新密码</label>
        <el-input v-model="passwordForm.newPassword" type="password" show-password @keyup.enter="savePassword" />
      </div>
      <div class="um-field">
        <label>确认密码</label>
        <el-input v-model="passwordForm.confirmPassword" type="password" show-password @keyup.enter="savePassword" />
      </div>
      <template #footer>
        <el-button @click="passwordVisible = false">取消</el-button>
        <el-button type="primary" :loading="passwordSaving" @click="savePassword">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { clearAuth } from '../utils/auth'
import { confirmAction } from '../utils/confirm'
import { applyProfile, sessionUserName } from '../utils/session'
import { updateMyPassword, updateMyProfile, uploadMyAvatar } from '../api/user'
import { useDropdownKeepMounted } from '../utils/dropdown'
import KbSvgIcon from './corecraft-web/KbSvgIcon.vue'
import KbAvatar from './corecraft-web/KbAvatar.vue'
import { BUILTIN_AVATARS } from './corecraft-web/builtinAvatars'

const props = defineProps({
  /** 当前用户资料（会话里的 profile） */
  user: { type: Object, default: () => ({}) },
  /** 头像直径。业务壳侧边栏 24（默认）；管理后台顶栏密度高，显式传 28 */
  size: { type: Number, default: 24 }
})

const emit = defineEmits([
  /** 登录态已经清了 —— 各壳收拾自己的状态（admin 壳要清页面标签页） */
  'logout',
  /** 密码改了 —— 登录态已清，壳要收拾自己的状态 */
  'password-changed'
])

const router = useRouter()

const profileVisible = ref(false)
const passwordVisible = ref(false)
const profileSaving = ref(false)
const passwordSaving = ref(false)
const picking = ref(false)
const menuOpen = ref(false)
let origUsername = ''

const profileForm = reactive({
  username: '',
  avatar: '',
  email: '',
  mobile: ''
})

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const { keepMounted, onDropdownShow } = useDropdownKeepMounted()
function onMenuVisible(v) {
  menuOpen.value = v
  onDropdownShow(v)
}

const avatarFallback = computed(() => {
  const s = props.user.username || 'U'
  return String(s).slice(0, 1).toUpperCase()
})

function onCommand(command) {
  if (command === 'profile') {
    origUsername = props.user.username || ''
    profileForm.username = origUsername
    profileForm.avatar = props.user.avatar || ''
    profileForm.email = props.user.email || ''
    profileForm.mobile = props.user.mobile || ''
    picking.value = false
    profileVisible.value = true
    return
  }
  if (command === 'password') {
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
    passwordForm.confirmPassword = ''
    passwordVisible.value = true
    return
  }
  if (command === 'logout') doLogout()
}

async function doLogout() {
  if (!(await confirmAction('退出登录'))) return
  clearAuth()
  emit('logout')
  ElMessage.success('已退出')
  router.push({ name: 'Login' })
}

const saveProfile = async () => {
  const nextUsername = String(profileForm.username || '').trim()
  if (!nextUsername) {
    ElMessage.error('用户名不能为空')
    return
  }
  profileSaving.value = true
  try {
    await updateMyProfile({
      username: nextUsername,
      avatar: profileForm.avatar,
      email: profileForm.email,
      mobile: profileForm.mobile
    })
    profileVisible.value = false
    if (nextUsername !== origUsername) {
      clearAuth()
      emit('logout')
      ElMessage.success('已修改，请重新登录')
      router.push({ name: 'Login' })
    } else {
      applyProfile({
        username: nextUsername,
        avatar: profileForm.avatar,
        email: profileForm.email,
        mobile: profileForm.mobile
      })
      ElMessage.success('已更新')
    }
  } finally {
    profileSaving.value = false
  }
}

async function pickBuiltin(id) {
  const avatar = 'builtin:' + id
  try {
    await updateMyProfile({ avatar })
    profileForm.avatar = avatar
    applyProfile({ avatar })
    picking.value = false
  } catch {
    // 错误提示归 axios 拦截器
  }
}

const handleAvatarUpload = async ({ file }) => {
  const res = await uploadMyAvatar(file)
  profileForm.avatar = res?.data?.avatar || ''
  applyProfile({ avatar: profileForm.avatar })
  picking.value = false
}

const savePassword = async () => {
  if (!passwordForm.oldPassword || !passwordForm.newPassword || !passwordForm.confirmPassword) {
    ElMessage.error('请完整填写密码信息')
    return
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    ElMessage.error('两次输入的密码不一致')
    return
  }
  passwordSaving.value = true
  try {
    await updateMyPassword(passwordForm)
    passwordVisible.value = false
    ElMessage.success('已修改，请重新登录')
    clearAuth()
    emit('password-changed')
    router.push({ name: 'Login' })
  } finally {
    passwordSaving.value = false
  }
}
</script>

<style scoped>
.user-menu {
  display: flex;
  align-items: center;
  min-width: 0;
}
.user-menu :deep(.el-dropdown) {
  flex: 1;
  min-width: 0;
}
.user-menu__trigger {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: var(--sp-8);
  cursor: pointer;
  border-radius: var(--radius-sm);
  padding: var(--sp-3);
  margin: calc(-1 * var(--sp-6));
  transition: var(--cc-transition), box-shadow 0.15s;
}
.user-menu__trigger:hover {
  background: var(--cc-primary-soft);
  box-shadow: inset 0 0 0 1px var(--cc-primary-border);
}
.user-menu__name {
  flex: 1;
  font-size: var(--text-md);
  font-weight: 500;
  color: var(--cc-text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.user-menu__caret {
  font-size: var(--text-2xs);
  color: var(--cc-text-4);
  opacity: 0.75;
  flex-shrink: 0;
  transition: transform 0.18s var(--ease, ease);
}
.user-menu__caret.is-open {
  transform: rotate(180deg);
}

/* ============ 编辑个人信息弹窗（ui-style §1） ============ */
.um-head {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--sp-2) 0 var(--sp-12);
}
.um-avatar-btn {
  position: relative;
  padding: 0;
  border: none;
  background: none;
  border-radius: 50%;
  cursor: pointer;
  outline: 2px solid transparent;
  outline-offset: 2px;
  transition: outline-color 0.15s;
}
.um-avatar-btn:hover,
.um-avatar-btn.is-on {
  outline-color: var(--cc-primary);
}
.um-cam {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: rgb(0 0 0 / 0.4);
  opacity: 0;
  transition: opacity 0.15s;
}
.um-avatar-btn:hover .um-cam,
.um-avatar-btn.is-on .um-cam {
  opacity: 1;
}
.um-cam svg {
  width: 20px;
  height: 20px;
}

.um-pick {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--sp-8);
  padding-bottom: var(--sp-12);
  border-bottom: 1px solid var(--cc-border);
  margin-bottom: var(--sp-12);
}
.um-pick__item {
  padding: 0;
  border: none;
  background: none;
  border-radius: 50%;
  cursor: pointer;
  line-height: 0;
  outline: 2px solid transparent;
  outline-offset: 2px;
  transition: outline-color 0.15s;
}
.um-pick__item.is-on {
  outline-color: var(--cc-primary);
}
.um-pick__add {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  border: 1px dashed var(--cc-border-strong);
  color: var(--cc-text-3);
  font-size: 15px;
  line-height: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
}
.um-pick__upload {
  line-height: 0;
}

.um-field {
  margin-bottom: var(--sp-12);
}
.um-field > label {
  display: block;
  font-size: var(--text-xs);
  color: var(--cc-text-3);
  margin-bottom: var(--sp-4);
}
.um-field :deep(.el-input__wrapper) {
  border-radius: 8px;
}
.um-field :deep(.el-input__inner) {
  height: 38px;
}
</style>

<style>
.um-popper .el-dropdown-menu {
  min-width: 164px;
  padding: 6px;
}
.um-popper .el-dropdown-menu__item {
  gap: 8px;
  border-radius: var(--radius-xs, 6px);
}
</style>
