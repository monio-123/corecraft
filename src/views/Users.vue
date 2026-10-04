<template>
  <div class="page">
    <div class="page-head">
      <div class="page-head__title">用户管理</div>
      <el-button v-if="canCreateUser" type="primary" @click="openCreate">
        <el-icon><Plus /></el-icon>
        新增用户
      </el-button>
    </div>

    <el-table :data="usersList" stripe style="width: 100%" v-loading="loading">
      <el-table-column prop="id" label="用户ID" width="80" />
      <el-table-column prop="username" label="用户名" min-width="110" />
      <el-table-column prop="nickname" label="昵称" min-width="100" />
      <el-table-column prop="email" label="邮箱" min-width="160" show-overflow-tooltip />
      <el-table-column prop="mobile" label="手机" width="140" />
      <el-table-column prop="roleCode" label="角色" width="130" />
      <el-table-column prop="createTime" label="创建时间" width="170" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="scope">
          <div class="ops">
            <el-button link type="primary" @click="editUser(scope.row)">编辑</el-button>
            <el-button link @click="openAssignRole(scope.row)">分配角色</el-button>
            <el-button link type="danger" @click="removeUser(scope.row.id)">删除</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <div class="users-pagination">
      <el-pagination
        background
        layout="total, prev, pager, next, sizes"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        :page-size="pageSize"
        :current-page="page"
        @update:current-page="onPageChange"
        @update:page-size="onPageSizeChange"
      />
    </div>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :disabled="mode === 'edit'" />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item label="手机" prop="mobile">
          <el-input v-model="form.mobile" />
        </el-form-item>

        <template v-if="mode === 'create'">
          <el-form-item label="密码" prop="password">
            <el-input v-model="form.password" type="password" show-password />
          </el-form-item>
          <el-form-item label="确认密码" prop="confirmPassword">
            <el-input v-model="form.confirmPassword" type="password" show-password />
          </el-form-item>
        </template>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="roleDialogVisible" title="分配角色" width="520px">
      <el-form label-width="90px">
        <el-form-item label="角色">
          <el-select v-model="roleForm.roleId" filterable clearable :loading="roleLoading" style="width: 100%">
            <el-option
              v-for="r in roleOptions"
              :key="r.id"
              :label="`${r.name}（${r.code}）`"
              :value="r.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="roleSaving" @click="submitRole">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { assignUserRole, createUser, deleteUser, getUserPage, updateUser } from '../api/sysUser'
import { getRoleList } from '../api/role'
import { hasToken, hasPermission } from '../utils/auth'

const usersList = ref([])
const loading = ref(false)
const saving = ref(false)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

const dialogVisible = ref(false)
const mode = ref('create')
const formRef = ref()
const roleLoading = ref(false)
const roleOptions = ref([])
const roleDialogVisible = ref(false)
const roleSaving = ref(false)

const form = reactive({
  id: null,
  username: '',
  nickname: '',
  email: '',
  mobile: '',
  password: '',
  confirmPassword: ''
})

const roleForm = reactive({
  userId: null,
  roleId: null
})

const dialogTitle = computed(() => (mode.value === 'create' ? '新增用户' : '编辑用户'))
// 按钮显隐完全由权限配置决定，没有"超管恒真"的旁路
const canCreateUser = computed(() => hasPermission('user:add'))

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  mobile: [{ required: true, message: '请输入手机号码', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  confirmPassword: [{ required: true, message: '请输入确认密码', trigger: 'blur' }]
}

const resetForm = () => {
  form.id = null
  form.username = ''
  form.nickname = ''
  form.email = ''
  form.mobile = ''
  form.password = ''
  form.confirmPassword = ''
}

const resetRoleForm = () => {
  roleForm.userId = null
  roleForm.roleId = null
}

const fetchRoles = async () => {
  roleLoading.value = true
  try {
    const res = await getRoleList({})
    roleOptions.value = res?.data || []
  } finally {
    roleLoading.value = false
  }
}

const ensureRolesLoaded = async () => {
  if (roleOptions.value?.length) return
  await fetchRoles()
}

const fetchPage = async () => {
  loading.value = true
  try {
    const res = await getUserPage({ page: page.value, pageSize: pageSize.value })
    const data = res?.data || {}
    usersList.value = data.records || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

const onPageChange = (v) => {
  page.value = v
  fetchPage()
}

const onPageSizeChange = (v) => {
  pageSize.value = v
  page.value = 1
  fetchPage()
}

const openCreate = () => {
  mode.value = 'create'
  resetForm()
  dialogVisible.value = true
}

const editUser = async (row) => {
  mode.value = 'edit'
  resetForm()
  form.id = row?.id ?? null
  form.username = row?.username || ''
  form.nickname = row?.nickname || ''
  form.email = row?.email || ''
  form.mobile = row?.mobile || ''
  dialogVisible.value = true
}

const openAssignRole = async (row) => {
  resetRoleForm()
  await ensureRolesLoaded()
  roleForm.userId = row?.id ?? null
  roleForm.roleId = roleOptions.value.find((r) => r?.code === row?.roleCode)?.id ?? null
  roleDialogVisible.value = true
}

const removeUser = async (id) => {
  await ElMessageBox.confirm(`确认删除用户ID: ${id} ?`, '提示', { type: 'warning' })
  await deleteUser(id)
  ElMessage.success('删除成功')
  await fetchPage()
}

const submitRole = async () => {
  if (!roleForm.userId) {
    ElMessage.error('用户ID不能为空')
    return
  }
  roleSaving.value = true
  try {
    await assignUserRole(roleForm.userId, roleForm.roleId)
    ElMessage.success('保存成功')
    roleDialogVisible.value = false
    await fetchPage()
  } finally {
    roleSaving.value = false
  }
}

const submit = async () => {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  if (mode.value === 'create' && form.password !== form.confirmPassword) {
    ElMessage.error('密码和确认密码不一致')
    return
  }

  saving.value = true
  try {
    if (mode.value === 'create') {
      await createUser({
        username: form.username,
        password: form.password,
        confirmPassword: form.confirmPassword,
        nickname: form.nickname,
        email: form.email,
        mobile: form.mobile
      })
      ElMessage.success('创建成功')
    } else {
      await updateUser({
        id: form.id,
        username: form.username,
        nickname: form.nickname,
        email: form.email,
        mobile: form.mobile
      })
      ElMessage.success('保存成功')
    }
    dialogVisible.value = false
    await fetchPage()
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  fetchPage()
})
</script>

<style scoped>
/* 页面骨架（.page / .page-head / .page-head__title）在 assets/admin-page.css，这里只留本页专有样式 */

.users-pagination {
  display: flex;
  justify-content: flex-end;
}
</style>
