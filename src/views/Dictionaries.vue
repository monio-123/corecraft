<template>
  <div class="page">
    <div class="page-head">
      <div class="page-head__title">字典管理</div>
      <div class="page-head__actions">
        <el-button type="primary" @click="openTypeDialog()">
          <el-icon><Plus /></el-icon>
          新增字典类型
        </el-button>
        <!-- 图标走 :icon 而不是默认插槽：EP 在 loading 时只跳过 icon 插槽，
             默认插槽照渲染 ⇒ 写在里面会和转圈叠成两个图标 -->
        <el-button :icon="Refresh" :loading="typesLoading" @click="fetchDictTypes">刷新</el-button>
      </div>
    </div>

    <div class="dict-content">
      <!-- 左栏要有"加载中"：列表为空的那一瞬用户分不清"在拉"还是"真的没有"（§8 ④） -->
      <div class="panel" v-loading="typesLoading">
        <el-input
          v-model="keyword"
          placeholder="名称 / 编码"
          :prefix-icon="Search"
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

      <div class="panel">
        <!-- 没选中类型时这一格什么都不画（本页会自动选中第一项）：
             左边有选中项就说明加载完了，用户分得清"空的"和"坏的" -->
        <template v-if="currentType">
          <div class="panel-head">
            <div>
              <div class="panel__title">{{ currentType.name }}</div>
              <div class="panel-subtitle">
                编码：{{ currentType.code }} ｜ 描述：{{ currentType.remark || '无' }}
              </div>
            </div>
            <div class="panel-head__actions">
              <el-button @click="openTypeDialog(currentType)">编辑类型</el-button>
              <el-button type="primary" @click="openItemDialog()">
                <el-icon><Plus /></el-icon>
                新增字典项
              </el-button>
              <el-button link type="danger" @click="removeType(currentType)">删除类型</el-button>
            </div>
          </div>

          <el-table :data="dictItems" stripe style="width: 100%" v-loading="itemsLoading">
            <el-table-column prop="label" label="标签" min-width="120" />
            <el-table-column prop="value" label="键值" min-width="100" />
            <el-table-column prop="sort" label="排序" width="80" />
            <el-table-column prop="cssClass" label="样式标记" min-width="110" show-overflow-tooltip />
            <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
            <!-- 「参与掌握度」只在知识点类型这套字典上有意义（其它字典项没这个概念） -->
            <el-table-column v-if="isTopicTypeDict" label="掌握度" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="row.masteryEnabled ? 'success' : 'info'">
                  {{ row.masteryEnabled ? '参与' : '不参与' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="row.enabled ? 'success' : 'info'">
                  {{ row.enabled ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <div class="ops">
                  <el-button link type="primary" @click="openItemDialog(row)">编辑</el-button>
                  <el-button link type="danger" @click="removeItem(row)">删除</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </template>
      </div>
    </div>

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
        <!-- 「参与掌握度」只对知识点类型这一套字典有意义（其它字典项没这个概念），
             所以按字典类型编码决定显不显示，而不是给所有字典项都摆一个看不懂的开关。
             类型层只有「参与 / 不参与」两种有效状态（未配置与不参与行为一致），
             所以用开关而不是三态选择器。 -->
        <el-form-item v-if="isTopicTypeDict" label="参与掌握度">
          <el-switch v-model="itemForm.masteryEnabled" active-text="参与" inactive-text="不参与" />
          <div class="hint">
            该类型下的知识点默认是否参与掌握度统计（学习默认参与、笔记默认不参与）。<br />
            单条知识点可以在卡片菜单里单独覆盖这一条。
          </div>
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
import { ElMessage } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { confirmAction } from '../utils/confirm'
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
const typesLoading = ref(false)

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
  remark: '',
  masteryEnabled: false
})

/** 只有知识点类型这一套字典有「参与掌握度」的概念 */
const isTopicTypeDict = computed(() => currentType.value?.code === 'kp_topic_type')

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
  typesLoading.value = true
  try {
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
  } finally {
    typesLoading.value = false
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
      ElMessage.success('已更新')
    } else {
      await createDictType(payload)
      ElMessage.success('已创建')
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

const removeType = async (type) => {
  if (!(await confirmAction('删除字典类型'))) return
  await deleteDictType(type.id)
  ElMessage.success('已删除')
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
  // 类型层：null（未配置）与 false（明确不参与）行为一致 ⇒ 统一落成 false
  itemForm.masteryEnabled = item?.masteryEnabled === true
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
      remark: itemForm.remark,
      // 只在知识点类型这套字典上带这个字段：其它字典项传了也是白传
      ...(isTopicTypeDict.value ? { masteryEnabled: itemForm.masteryEnabled } : {})
    }
    if (itemForm.id) {
      await updateDictItem({ id: itemForm.id, ...payload })
      ElMessage.success('已更新')
    } else {
      await createDictItem(payload)
      ElMessage.success('已创建')
    }
    itemDialogVisible.value = false
    await fetchDictItems(currentType.value.id)
  } finally {
    itemSaving.value = false
  }
}

const removeItem = async (row) => {
  if (!(await confirmAction('删除字典项'))) return
  await deleteDictItem(row.id)
  ElMessage.success('已删除')
  await fetchDictItems(currentType.value?.id)
}

watch(currentTypeId, async (value) => {
  await fetchDictItems(value)
})

onMounted(async () => {
  try {
    await fetchDictTypes()
  } catch (e) {
    // 提示归 request.js（走 axios 实例，唯一提示出口）
  }
})
</script>

<style scoped>
/* 页面骨架（.page / .page-head / .page-head__title / .panel / .panel-head /
   .panel__title / .panel-subtitle）在 assets/admin-page.css，这里只留本页专有样式 */

.dict-content {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: var(--sp-12);
}

.search-input {
  margin-bottom: var(--sp-10);
}

.dict-type-list {
  display: flex;
  flex-direction: column;
  gap: var(--sp-8);
}

.dict-type-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--sp-10);
  padding: var(--sp-10);
  border: 1px solid var(--cc-border);
  border-radius: var(--radius-lg);
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}
.dict-type-item:hover {
  border-color: var(--cc-primary);
}
.dict-type-item.active {
  border-color: var(--cc-primary);
  background: var(--cc-primary-soft);
}

.dict-type-item__title {
  font-size: var(--text-md);
  font-weight: 500;
  color: var(--cc-text-1);
}
/* 编码是要读的主数据，不是装饰 ⇒ text-2（text-3 对白底只有 2.48:1） */
.dict-type-item__code {
  margin-top: var(--sp-4);
  font-size: var(--text-xs);
  color: var(--cc-text-2);
}

.dict-type-item__meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: var(--sp-6);
}

.dict-type-item__count {
  font-size: var(--text-xs);
  color: var(--cc-text-3);
}
</style>
