<template>
  <div class="dict-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <h2>字典管理</h2>
          <div class="header-actions">
            <el-button type="primary" @click="openTypeDialog()">
              <el-icon><Plus /></el-icon>
              新增字典类型
            </el-button>
            <el-button @click="fetchDictTypes">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
          </div>
        </div>
      </template>

      <div class="dict-content">
        <div class="dict-type-panel">
          <el-input
            v-model="keyword"
            placeholder="搜索字典名称 / 编码"
            clearable
            class="search-input"
          />

          <div class="dict-type-list">
            <div
              v-for="item in filteredDictTypes"
              :key="item.id"
              class="dict-type-item"
              :class="{ active: currentType && currentType.id === item.id }"
              @click="selectType(item)"
            >
              <div class="dict-type-item__main">
                <div class="dict-type-item__title">{{ item.name }}</div>
                <div class="dict-type-item__code">{{ item.code }}</div>
              </div>
              <div class="dict-type-item__meta">
                <el-tag size="small" :type="item.enabled ? 'success' : 'info'">
                  {{ item.enabled ? '启用' : '停用' }}
                </el-tag>
                <span class="dict-type-item__count">{{ item.itemCount || 0 }} 项</span>
              </div>
            </div>
          </div>
        </div>

        <div class="dict-item-panel">
          <el-empty v-if="!currentType" description="请选择左侧字典类型" />

          <template v-else>
            <div class="panel-header">
              <div>
                <div class="panel-title">{{ currentType.name }}</div>
                <div class="panel-subtitle">
                  编码：{{ currentType.code }} ｜ 描述：{{ currentType.remark || '无' }}
                </div>
              </div>
              <div class="panel-actions">
                <el-button @click="openTypeDialog(currentType)">编辑类型</el-button>
                <el-button type="danger" plain @click="removeType(currentType.id)">删除类型</el-button>
                <el-button type="primary" @click="openItemDialog()">
                  <el-icon><Plus /></el-icon>
                  新增字典项
                </el-button>
              </div>
            </div>

            <el-table :data="dictItems" stripe style="width: 100%" v-loading="itemsLoading">
              <el-table-column prop="label" label="标签" min-width="160" />
              <el-table-column prop="value" label="键值" min-width="120" />
              <el-table-column prop="sort" label="排序" width="90" />
              <el-table-column prop="cssClass" label="样式标记" min-width="120" />
              <el-table-column prop="remark" label="备注" min-width="180" />
              <el-table-column label="状态" width="90">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.enabled ? 'success' : 'info'">
                    {{ row.enabled ? '启用' : '停用' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="180" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openItemDialog(row)">编辑</el-button>
                  <el-button link type="danger" @click="removeItem(row.id)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </template>
        </div>
      </div>
    </el-card>

    <el-dialog
      v-model="typeDialogVisible"
      :title="typeForm.id ? '编辑字典类型' : '新增字典类型'"
      width="520px"
    >
      <el-form :model="typeForm" label-width="90px">
        <el-form-item label="名称" required>
          <el-input v-model="typeForm.name" />
        </el-form-item>
        <el-form-item label="编码" required>
          <el-input v-model="typeForm.code" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="typeForm.enabled" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="typeForm.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="typeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="typeSaving" @click="saveType">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="itemDialogVisible"
      :title="itemForm.id ? '编辑字典项' : '新增字典项'"
      width="560px"
    >
      <el-form :model="itemForm" label-width="90px">
        <el-form-item label="标签" required>
          <el-input v-model="itemForm.label" />
        </el-form-item>
        <el-form-item label="键值" required>
          <el-input v-model="itemForm.value" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="itemForm.sort" :min="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="itemForm.enabled" />
        </el-form-item>
        <el-form-item label="样式标记">
          <el-input v-model="itemForm.cssClass" placeholder="如 success / warning" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="itemForm.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="itemDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="itemSaving" @click="saveItem">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  createDictItem,
  createDictType,
  deleteDictItem,
  deleteDictType,
  getDictItemList,
  getDictTypeList,
  updateDictItem,
  updateDictType
} from '../api/dict'

const dictTypes = ref([])
const dictItems = ref([])
const currentTypeId = ref(null)
const keyword = ref('')
const itemsLoading = ref(false)

const typeSaving = ref(false)
const itemSaving = ref(false)
const typeDialogVisible = ref(false)
const itemDialogVisible = ref(false)

const typeForm = reactive({
  id: null,
  name: '',
  code: '',
  enabled: true,
  remark: ''
})

const itemForm = reactive({
  id: null,
  label: '',
  value: '',
  sort: 0,
  enabled: true,
  cssClass: '',
  remark: ''
})

const filteredDictTypes = computed(() => {
  const text = keyword.value.trim().toLowerCase()
  if (!text) {
    return dictTypes.value
  }
  return dictTypes.value.filter(item =>
    item.name.toLowerCase().includes(text) || item.code.toLowerCase().includes(text)
  )
})

const currentType = computed(() =>
  dictTypes.value.find(item => item.id === currentTypeId.value) || null
)

const selectType = (item) => {
  currentTypeId.value = item.id
}

const fetchDictTypes = async () => {
  const res = await getDictTypeList()
  const types = (res?.data || []).map(item => ({
    ...item,
    itemCount: item.itemCount || 0
  }))
  dictTypes.value = types
  if (!types.length) {
    currentTypeId.value = null
    dictItems.value = []
    return
  }
  const exists = types.some(item => item.id === currentTypeId.value)
  if (!exists) {
    currentTypeId.value = types[0].id
  }
}

const fetchDictItems = async (dictTypeId) => {
  if (!dictTypeId) {
    dictItems.value = []
    return
  }
  itemsLoading.value = true
  try {
    const res = await getDictItemList({ dictTypeId })
    dictItems.value = res?.data || []
    const current = dictTypes.value.find(item => item.id === dictTypeId)
    if (current) {
      current.itemCount = dictItems.value.length
    }
  } finally {
    itemsLoading.value = false
  }
}

const openTypeDialog = (item) => {
  typeForm.id = item?.id ?? null
  typeForm.name = item?.name ?? ''
  typeForm.code = item?.code ?? ''
  typeForm.enabled = item?.enabled ?? true
  typeForm.remark = item?.remark ?? ''
  typeDialogVisible.value = true
}

const saveType = async () => {
  if (!typeForm.name || !typeForm.code) {
    ElMessage.error('请填写字典名称和编码')
    return
  }
  typeSaving.value = true
  try {
    const previousCode = typeForm.code
    const payload = {
      code: typeForm.code,
      name: typeForm.name,
      enabled: typeForm.enabled,
      remark: typeForm.remark
    }
    if (typeForm.id) {
      await updateDictType({ id: typeForm.id, ...payload })
      ElMessage.success('字典类型已更新')
    } else {
      await createDictType(payload)
      ElMessage.success('字典类型已创建')
    }
    typeDialogVisible.value = false
    await fetchDictTypes()
    if (!typeForm.id) {
      const created = dictTypes.value.find(item => item.code === previousCode)
      if (created) {
        currentTypeId.value = created.id
      }
    }
  } finally {
    typeSaving.value = false
  }
}

const removeType = async (id) => {
  await ElMessageBox.confirm('确认删除该字典类型及其下所有字典项吗？', '提示', { type: 'warning' })
  await deleteDictType(id)
  ElMessage.success('字典类型已删除')
  await fetchDictTypes()
}

const openItemDialog = (item) => {
  if (!currentType.value) {
    ElMessage.warning('请先选择字典类型')
    return
  }
  itemForm.id = item?.id ?? null
  itemForm.label = item?.label ?? ''
  itemForm.value = item?.value ?? ''
  itemForm.sort = item?.sort ?? 0
  itemForm.enabled = item?.enabled ?? true
  itemForm.cssClass = item?.cssClass ?? ''
  itemForm.remark = item?.remark ?? ''
  itemDialogVisible.value = true
}

const saveItem = async () => {
  if (!currentType.value) {
    return
  }
  if (!itemForm.label || !itemForm.value) {
    ElMessage.error('请填写字典项标签和键值')
    return
  }

  itemSaving.value = true
  try {
    const payload = {
      dictTypeId: currentType.value.id,
      label: itemForm.label,
      value: itemForm.value,
      sort: itemForm.sort,
      enabled: itemForm.enabled,
      cssClass: itemForm.cssClass,
      remark: itemForm.remark
    }
    if (itemForm.id) {
      await updateDictItem({ id: itemForm.id, ...payload })
      ElMessage.success('字典项已更新')
    } else {
      await createDictItem(payload)
      ElMessage.success('字典项已创建')
    }
    itemDialogVisible.value = false
    await fetchDictItems(currentType.value.id)
  } finally {
    itemSaving.value = false
  }
}

const removeItem = async (id) => {
  await ElMessageBox.confirm('确认删除该字典项吗？', '提示', { type: 'warning' })
  await deleteDictItem(id)
  ElMessage.success('字典项已删除')
  await fetchDictItems(currentType.value?.id)
}

watch(currentTypeId, async (value) => {
  await fetchDictItems(value)
})

onMounted(async () => {
  try {
    await fetchDictTypes()
  } catch (e) {
    ElMessage.error('加载字典数据失败')
  }
})
</script>

<style scoped>
.dict-container {
  padding: 20px 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-header h2 {
  margin: 0;
  font-size: 20px;
}

.header-actions {
  display: flex;
  gap: 10px;
}

.dict-content {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 16px;
}

.dict-type-panel,
.dict-item-panel {
  min-height: 540px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 16px;
  background: #fff;
}

.search-input {
  margin-bottom: 12px;
}

.dict-type-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.dict-type-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 14px 12px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.dict-type-item:hover,
.dict-type-item.active {
  border-color: #409eff;
  background: #ecf5ff;
}

.dict-type-item__title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.dict-type-item__code {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}

.dict-type-item__meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
}

.dict-type-item__count {
  font-size: 12px;
  color: #909399;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.panel-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.panel-subtitle {
  margin-top: 4px;
  color: #909399;
  font-size: 13px;
}

.panel-actions {
  display: flex;
  gap: 10px;
}
</style>
