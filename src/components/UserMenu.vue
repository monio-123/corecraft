<template>
  <!--
    用户菜单：**两个壳共用这一份**（管理后台的顶栏、业务壳的侧边栏底栏）。

    菜单项两个壳完全一样：编辑个人信息 / 修改密码 / 退出登录。
    「AI 能力」不在这里 —— 它挪到了侧边栏底栏那个齿轮里（那是"设置"的入口，
    低频的东西不该占用户菜单的位置；用户菜单留给"关于我"和"离开"）。

    弹窗也一起收在这里：表单、校验、上传头像、改完密码后清登录态 ——
    这些是同一件事的同一份实现，放两个壳里就是两份，改一边另一边慢慢漂。

    ⚠️ 弹窗结构按 brove-skills/ui-style §1（2026-11-15 定稿，设计稿
       ui-preview/2026-11-15-usermenu/）：440 宽 / 上标签表单 / 表单只放可编辑项 /
       换头像按需展开（入口 = 头像本身，hover 出相机角标，选完即收）。
       文案与元素遵守 brove-shared §8（含 ⑥ 非必要即删：没有一句说明文字）。
  -->
  <div class="user-menu">
    <el-dropdown
      trigger="click"
      :persistent="keepMounted"
      @visible-change="onDropdownShow"
      @command="onCommand"
    >
      <span class="user-menu__trigger">
        <KbAvatar :value="user.avatar" :fallback="avatarFallback" :size="size" />
        <span class="user-menu__name">{{ sessionUserName(user) }}</span>
        <KbSvgIcon class="user-menu__caret" name="caret-down" :sw="2.4" />
      </span>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="profile">编辑个人信息</el-dropdown-item>
          <el-dropdown-item command="password">修改密码</el-dropdown-item>
          <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>

    <el-dialog v-model="profileVisible" title="编辑个人信息" width="440px">
      <!-- 头像即身份区：换头像的入口就是头像本身（hover 相机角标 / 点开选择条） -->
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
        <div class="um-name">{{ sessionUserName({ nickname: profileForm.nickname, username: profileForm.username }) }}</div>
      </div>

      <!-- 选择条只在换头像时出现；选完即收（与上传同口径：立即生效） -->
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
        <label>昵称</label>
        <el-input v-model="profileForm.nickname" />
      </div>
      <div class="um-field">
        <label>邮箱</label>
        <el-input v-model="profileForm.email" />
      </div>
      <div class="um-field">
        <label>手机</label>
        <el-input v-model="profileForm.mobile" />
      </div>

      <template #footer>
        <el-button @click="profileVisible = false">取消</el-button>
        <el-button type="primary" :loading="profileSaving" @click="saveProfile">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="passwordVisible" title="修改密码" width="440px">
      <div class="um-field">
        <label>旧密码</label>
        <el-input v-model="passwordForm.oldPassword" type="password" show-password />
      </div>
      <div class="um-field">
        <label>新密码</label>
        <el-input v-model="passwordForm.newPassword" type="password" show-password />
      </div>
      <div class="um-field">
        <label>确认密码</label>
        <el-input v-model="passwordForm.confirmPassword" type="password" show-password />
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
/** 换头像选择条展开中（2026-11-15 v3：入口 = 头像本身，选完即收） */
const picking = ref(false)

const profileForm = reactive({
  username: '',
  nickname: '',
  avatar: '',
  email: '',
  mobile: ''
})

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

// 下拉「首开后保持挂载」（utils/dropdown.js）：第一次点击不再白付重建弹层的钱
const { keepMounted, onDropdownShow } = useDropdownKeepMounted()

// 头像里的兜底字：取昵称/用户名的第一个字
const avatarFallback = computed(() => {
  const s = props.user.nickname || props.user.username || 'U'
  return String(s).slice(0, 1).toUpperCase()
})

function onCommand(command) {
  if (command === 'profile') {
    profileForm.username = props.user.username || ''
    profileForm.nickname = props.user.nickname || ''
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

/**
 * 退出登录。**确认框 / 清登录态 / 提示 / 跳登录页都在这里** ——
 * 两个壳（管理后台顶栏、业务壳侧边栏底栏）共用这一个菜单，
 * 退出这件事就该只有一份实现。
 *
 * <p>各壳只接 `logout` 事件做**自己的**收尾（admin 壳有页面标签页要清）；
 * 业务壳没有要清的（AI 配置那些归 `useAi.js` 订阅 `onAuthChange` 管）。
 *
 * <p>确认框走 `utils/confirm.js`（取消不抛错、文案口径全站一份）。
 */
async function doLogout() {
  if (!(await confirmAction('退出登录'))) return
  clearAuth()
  emit('logout')
  ElMessage.success('已退出')
  router.push({ name: 'Login' })
}

const saveProfile = async () => {
  profileSaving.value = true
  try {
    await updateMyProfile({
      nickname: profileForm.nickname,
      avatar: profileForm.avatar,
      email: profileForm.email,
      mobile: profileForm.mobile
    })
    profileVisible.value = false
    // 资料写回会话（**会话里那一份，两个壳共用**）——
    // 以前是抛给壳、让壳写回自己那份快照，那就是两份资料、两处写回。
    applyProfile({ ...profileForm })
    ElMessage.success('已更新')
  } finally {
    profileSaving.value = false
  }
}

/**
 * 选内置头像：**立即生效**（与上传同口径）——头像当场就换、看得见，
 * 不另发成功提示（brove-shared §8①：结果自己看得见就不补提示）。
 * 失败（拦截器已提示）选择条不收，留着重选。
 */
async function pickBuiltin(id) {
  const avatar = 'builtin:' + id
  try {
    await updateMyProfile({ avatar })
    profileForm.avatar = avatar
    applyProfile({ avatar })
    picking.value = false
  } catch {
    // 错误提示归 axios 拦截器；这里吞掉只是为了不产生 unhandled rejection
  }
}

const handleAvatarUpload = async ({ file }) => {
  const res = await uploadMyAvatar(file)
  profileForm.avatar = res?.data?.avatar || ''
  applyProfile({ ...profileForm })
  picking.value = false
}

const savePassword = async () => {
  if (!passwordForm.oldPassword || !passwordForm.newPassword || !passwordForm.confirmPassword) {
    ElMessage.error('请完整填写密码信息')
    return
  }
  passwordSaving.value = true
  try {
    await updateMyPassword(passwordForm)
    passwordVisible.value = false
    ElMessage.success('已修改，请重新登录')
    // 改完密码旧登录态就作废了，直接把人送回登录页
    clearAuth()
    emit('password-changed')
    router.push({ name: 'Login' })
  } finally {
    passwordSaving.value = false
  }
}
</script>

<style scoped>
/* 根元素撑满交给外层（业务壳的底栏要它占满剩余宽度，齿轮才能被顶到最右），
   这里只保证"内容不撑破"：长用户名的 min-content 会把这一格顶开。 */
.user-menu {
  display: flex;
  align-items: center;
  min-width: 0;
}
/* ⚠️ **撑的是 wrapper，不是触发器**：ElDropdown 的根是它自己包的一层
   <div class="el-dropdown">，触发器在那一层**里面**。给触发器写 flex: 1
   只会把尖角推到名字右边，footer 层面那个 flex item 仍按内容收窄。 */
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
  /* 图标型 hover 档（2026-11-15 v3）：主题浅底 + 主色细描边（inset 不占布局），
     与侧栏图标钮同一语言；原来是整行灰方块（surface-2）。 */
  background: var(--cc-primary-soft);
  box-shadow: inset 0 0 0 1px var(--cc-primary-border);
}
.user-menu__name {
  flex: 1;
  /* 15→14（2026-11-13 壳上字号三档收敛）：用户名归「导航级」（--text-md）。
     2026-11-15 v3 提到 500 字重：头像收到 24 后比例 1.7:1，名字要有分量才压得住。 */
  font-size: var(--text-md);
  font-weight: 500;
  color: var(--cc-text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.user-menu__caret {
  /* 提示不是装饰（v3）：更小一档、更淡一档 */
  font-size: var(--text-2xs);
  color: var(--cc-text-4);
  opacity: 0.75;
  flex-shrink: 0;
}

/* ============ 编辑个人信息弹窗（ui-style §1，2026-11-15 v3） ============ */

/* 头像即身份区：头像 64 + 名字 20 衬线（比例撑得住；用户名不进表单，只在这里出现一次） */
.um-head {
  display: flex;
  align-items: center;
  gap: var(--sp-14);
  padding: var(--sp-2) 0 var(--sp-12);
}
.um-avatar-btn {
  position: relative;
  padding: 0;
  border: none;
  background: none;
  border-radius: 50%;
  cursor: pointer;
  /* is-on（选择条展开）/ hover：主题色外环 —— 既是"能点"的暗示，也是"正在换"的状态 */
  outline: 2px solid transparent;
  outline-offset: 2px;
  transition: outline-color 0.15s;
}
.um-avatar-btn:hover,
.um-avatar-btn.is-on {
  outline-color: var(--cc-primary);
}
/* 相机角标：hover 才现（状态用 hover 承载，不常驻 —— §8⑥） */
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
.um-name {
  font-family: var(--font-display);
  font-size: 20px;
  font-weight: 600;
  color: var(--cc-text-1);
  letter-spacing: 0.01em;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 选择条（只在换头像时出现）：内置款一排 + ＋ 上传；零说明文字 */
.um-pick {
  display: flex;
  align-items: center;
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

/* 上标签字段（ui-style §1.3）：label 12/text-3 在上，输入框 38 高 / radius 8 */
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
