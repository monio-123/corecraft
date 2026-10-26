<template>
  <!--
    用户菜单：**两个壳共用这一份**（管理后台的顶栏、业务壳的侧边栏底栏）。

    菜单项两个壳完全一样：编辑个人信息 / 修改密码 / 退出登录。
    「AI 能力」不在这里 —— 它挪到了侧边栏底栏那个齿轮里（那是"设置"的入口，
    低频的东西不该占用户菜单的位置；用户菜单留给"关于我"和"离开"）。

    弹窗也一起收在这里：表单、校验、上传头像、改完密码后清登录态 ——
    这些是同一件事的同一份实现，放两个壳里就是两份，改一边另一边慢慢漂。
  -->
  <div class="user-menu">
    <el-dropdown trigger="click" @command="onCommand">
      <span class="user-menu__trigger">
        <el-avatar :size="size" :src="user.avatar || undefined" class="user-menu__avatar">
          {{ avatarFallback }}
        </el-avatar>
        <span class="user-menu__name">{{ sessionUserName(user) }}</span>
        <el-icon class="user-menu__caret"><ArrowDown /></el-icon>
      </span>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="profile">编辑个人信息</el-dropdown-item>
          <el-dropdown-item command="password">修改密码</el-dropdown-item>
          <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>

    <el-dialog v-model="profileVisible" title="编辑个人信息" width="560px">
      <el-form :model="profileForm" label-width="90px">
        <el-form-item label="头像">
          <div class="user-menu__avatar-editor">
            <el-avatar :size="72" :src="profileForm.avatar || undefined">
              {{ avatarFallback }}
            </el-avatar>
            <el-upload :show-file-list="false" :http-request="handleAvatarUpload" accept="image/*">
              <el-button>上传头像</el-button>
            </el-upload>
          </div>
        </el-form-item>
        <el-form-item label="用户名">
          <el-input v-model="profileForm.username" disabled />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="profileForm.nickname" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="profileForm.email" />
        </el-form-item>
        <el-form-item label="手机">
          <el-input v-model="profileForm.mobile" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="profileVisible = false">取消</el-button>
        <el-button type="primary" :loading="profileSaving" @click="saveProfile">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="passwordVisible" title="修改密码" width="520px">
      <el-form :model="passwordForm" label-width="90px">
        <el-form-item label="旧密码">
          <el-input v-model="passwordForm.oldPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="passwordForm.newPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input v-model="passwordForm.confirmPassword" type="password" show-password />
        </el-form-item>
      </el-form>
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown } from '@element-plus/icons-vue'
import { clearAuth } from '../utils/auth'
import { applyProfile, sessionUserName } from '../utils/session'
import { updateMyPassword, updateMyProfile, uploadMyAvatar } from '../api/user'

const props = defineProps({
  /** 当前用户资料（会话里的 profile） */
  user: { type: Object, default: () => ({}) },
  /** 头像尺寸。管理后台顶栏是 28（那边密度高），业务壳侧边栏是 34 */
  size: { type: Number, default: 34 }
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
 * <p>⚠️ **确认框被取消时要 catch**：Element Plus 的 `confirm` 取消是**抛异常**，
 * 不接住的话会变成一条 unhandled rejection（控制台报错，界面没反应）。
 */
async function doLogout() {
  try {
    await ElMessageBox.confirm('确认退出当前登录状态吗？', '退出登录', {
      type: 'warning',
      confirmButtonText: '退出',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
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

const handleAvatarUpload = async ({ file }) => {
  const res = await uploadMyAvatar(file)
  profileForm.avatar = res?.data?.avatar || ''
  applyProfile({ ...profileForm })
  ElMessage.success('已上传')
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
  gap: var(--sp-10);
  cursor: pointer;
  border-radius: var(--cc-radius-sm);
  padding: var(--sp-3);
  margin: calc(-1 * var(--sp-6));
  transition: var(--cc-transition);
}
.user-menu__trigger:hover {
  background: var(--cc-surface-2);
}
.user-menu__name {
  flex: 1;
  font-size: var(--text-base);
  color: var(--cc-text-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.user-menu__caret {
  font-size: var(--text-xs);
  color: var(--cc-text-4);
  flex-shrink: 0;
}
.user-menu__avatar-editor {
  display: flex;
  align-items: center;
  gap: var(--sp-10);
}
</style>
