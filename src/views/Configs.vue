<template>
  <div class="page">
    <div class="page-head">
      <div class="page-head__title">系统配置</div>
      <div class="page-head__actions">
        <!-- 系统默认 / 用户覆盖 是**两套东西**，不是上下并列的两块：
             默认看系统的，点一下才切到"谁改成了什么"。 -->
        <el-radio-group v-model="view" size="default">
          <el-radio-button value="system">系统默认</el-radio-button>
          <el-radio-button value="user">用户覆盖</el-radio-button>
        </el-radio-group>
        <el-button @click="fetchAll">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
    </div>

    <div class="content" :class="{ 'content--single': view === 'user' }">
      <!-- 左：分类。**它只用来分类**，不摆配置项 -->
      <div v-show="view === 'system'" class="panel">

        <el-tree
          :data="categoryTree"
          node-key="id"
          :props="{ label: 'name', children: 'children' }"
          highlight-current
          default-expand-all
          @node-click="onSelectCategory"
        >
          <template #default="{ data }">
            <span class="tree-node">
              <span class="tree-node__label">{{ data.name }}</span>
              <span v-if="data.itemCount" class="tree-node__count">{{ data.itemCount }} 项</span>
            </span>
          </template>
        </el-tree>
      </div>

      <!-- 右：这一类下面的一条条配置项 -->
      <div class="panel">
        <el-empty v-if="view === 'system' && !currentCategory" description="请选择左侧分类" />

        <template v-else-if="view === 'system'">
          <div class="panel-head">
            <div>
              <div class="panel__title">{{ currentCategory.name }}</div>
              <div class="panel-subtitle">
                编码：{{ currentCategory.configKey }} ｜
                {{ items.length }} 项
              </div>
            </div>
            <div class="panel-head__actions">
              <el-button type="primary" @click="openItemDialog()">
                <el-icon><Plus /></el-icon>
                新增配置项
              </el-button>
              <el-button @click="openCategoryDialog(currentCategory)">编辑分类</el-button>
              <el-button link type="danger" @click="removeCategory(currentCategory)">删除分类</el-button>
            </div>
          </div>

          <el-table :data="items" stripe style="width: 100%" v-loading="loading">
            <el-table-column prop="name" label="中文描述" min-width="120" />
            <el-table-column prop="configKey" label="配置键" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="mono">{{ row.configKey }}</span>
              </template>
            </el-table-column>
            <el-table-column label="值" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">
                <span :class="row.valueType === 'number' ? 'cell-number' : 'cell-text'">
                  {{ draft[row.configKey] }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="remark" label="说明" min-width="200" show-overflow-tooltip />
            <el-table-column label="操作" width="110" fixed="right">
              <template #default="{ row }">
                <div class="ops">
                  <el-button link type="primary" @click="openItemDialog(row)">编辑</el-button>
                  <el-button link type="danger" @click="removeItem(row)">删除</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </template>

        <!-- 用户覆盖：谁把哪一项改成了什么 -->
        <template v-else>
          <div class="panel-head">
            <div>
              <div class="panel__title">用户覆盖</div>
            </div>
          </div>

          <el-table :data="userRows" stripe style="width: 100%" v-loading="loading">
            <el-table-column prop="userId" label="用户" width="90" />
            <el-table-column label="配置项" min-width="260">
              <template #default="{ row }">
                <div>{{ labelOf(row.key) }}</div>
                <div class="mono">{{ row.key }}</div>
              </template>
            </el-table-column>
            <el-table-column prop="value" label="值" min-width="140" show-overflow-tooltip />
            <el-table-column prop="updateTime" label="更新时间" width="180" />
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <div class="ops">
                  <el-button link type="danger" @click="removeOverride(row)">删除</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </template>
      </div>
    </div>

    <!-- 分类的增删改 -->
    <el-dialog
      v-model="categoryDialog"
      :title="categoryForm.id ? '编辑分类' : '新增分类'"
      width="520px"
    >
      <el-form :model="categoryForm" label-width="90px">
        <el-form-item label="名称" required>
          <el-input v-model="categoryForm.name" />
        </el-form-item>
        <el-form-item label="编码" required>
          <el-input v-model="categoryForm.code" :disabled="!!categoryForm.id" placeholder="例 side" />
        </el-form-item>
        <el-form-item label="父分类">
          <el-input :model-value="categoryParentLabel" disabled />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="categoryForm.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="categoryDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveCategory">保存</el-button>
      </template>
    </el-dialog>

    <!-- 配置项的增删改：只有通用的四样 -->
    <el-dialog
      v-model="itemDialog"
      :title="itemForm.id ? '编辑配置项' : '新增配置项'"
      width="520px"
    >
      <el-form :model="itemForm" label-width="90px">
        <el-form-item label="中文描述" required>
          <el-input v-model="itemForm.name" placeholder="例 宽度" />
        </el-form-item>
        <el-form-item label="编码" required>
          <el-input v-model="itemForm.code" :disabled="!!itemForm.id" placeholder="例 sideWidth" />
        </el-form-item>
        <el-form-item label="值">
          <el-input v-if="itemForm.valueType === 'number'" v-model="itemForm.value" />
          <el-input v-else v-model="itemForm.value" type="textarea" :rows="6" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input
            v-model="itemForm.remark"
            type="textarea"
            :rows="3"
            placeholder="例 范围 200~360，步长 4，单位 px"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="itemDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveItem">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * 系统配置（管理后台）：**通用的 KV 组浏览器**。
 *
 * ## 左边是分类，右边是配置项
 *
 * 分类树存在库里（`sys_config_node`），它只回答一件事：**这一组归谁管**。
 * 配置项（KV）不摆进树里 —— 点开「侧边栏」这一类，右边是一张表，
 * 一条一条列出来：中文描述 / 配置键 / 值 / 范围 / 说明。
 *
 * 树是**展示用的分类**，不参与业务：真正决定"这个值干什么"的
 * 仍然是代码里的「键 → CSS 变量」那一张映射表。
 *
 * ## 两种值，**切换**，不并列
 *
 * - **系统默认**（user_id=0）：所有人共用的那一份，管理员在这一页改；
 * - **用户覆盖**（user_id>0）：用户在业务界面拖滑块时写下来的那些。
 *
 * 默认只看系统默认；要看"谁改成了什么"才切过去。
 *
 * ## 为什么不是一份写死的清单
 *
 * 原来这一页是按前端 `PREF_GROUPS` 那份写死的清单渲染的，于是
 * "加一组配置" = 改代码 + 发版。现在加一组 = 在这儿建一个分类。
 */
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  createConfigNode,
  deleteConfig,
  deleteConfigNode,
  getConfigNodes,
  listConfig,
  saveSystemConfig,
  updateConfigNode
} from '../api/config'

/** 树上的全部节点（分类 + 配置项） */
const nodes = ref([])
/** 各用户的覆盖行 */
const userRows = ref([])
/** 当前选中的一类 */
const currentId = ref(null)
/** 页面切到哪一边：system（系统默认）/ user（用户覆盖） */
const view = ref('system')

const loading = ref(false)
const saving = ref(false)

/** 表单里的值（配置键 → 值）：拉回来时是什么样就显示什么样 */
const draft = reactive({})

const categoryDialog = ref(false)
const categoryForm = reactive({ id: null, parentId: 0, name: '', code: '', remark: '' })
const itemDialog = ref(false)
const itemForm = reactive({
  id: null,
  parentId: 0,
  name: '',
  code: '',
  valueType: 'text',
  value: '',
  remark: ''
})

/** 是不是"配置项"：有值类型的是配置项，空的是分类。**判据只此一处**，别在别处再写一遍 */
const isItem = (n) => !!n.valueType

/** 某个分类下面那些配置项 */
const itemChildren = (id) => nodes.value.filter((n) => n.parentId === id && isItem(n))

/**
 * 分类树：**只留分类**（没有值类型的那些节点）。
 *
 * 配置项也是这张表里的节点，但它们是"类下面的一条条 KV"，
 * **不进树** —— 树只用来分类，配置项在右边那张表里。
 */
const categoryTree = computed(() => {
  const cats = nodes.value.filter((n) => !isItem(n))
  const byId = new Map(cats.map((n) => [n.id, { ...n, children: [] }]))
  const roots = []
  for (const n of byId.values()) {
    const parent = byId.get(n.parentId)
    if (parent) parent.children.push(n)
    else roots.push(n)
  }
  // 每个分类下面有几条配置项
  for (const n of cats) {
    n.itemCount = itemChildren(n.id).length
  }
  return roots
})

const currentCategory = computed(
  () => nodes.value.find((n) => n.id === currentId.value && !isItem(n)) || null
)

/** 当前分类下面那些配置项 */
const items = computed(() => {
  const c = currentCategory.value
  if (!c) return []
  return itemChildren(c.id)
})

const categoryParentLabel = computed(() => {
  const p = nodes.value.find((n) => n.id === categoryForm.parentId)
  return p ? p.name : '（顶级）'
})

function onSelectCategory(data) {
  currentId.value = data.id
}

async function fetchAll() {
  loading.value = true
  try {
    const [nodeRes, sysRes, userRes] = await Promise.all([
      getConfigNodes(),
      listConfig({ scope: 'system' }),
      listConfig({ scope: 'user' })
    ])
    nodes.value = nodeRes?.data || []

    const byKey = new Map((sysRes?.data || []).map((r) => [r.key, r.value]))
    for (const n of nodes.value) {
      if (!isItem(n)) continue
      // 没有这一行 = 还没单独设过 ⇒ 显示空（内置默认是界面偏好自己那份，通用页不认）
      const raw = byKey.get(n.configKey)
      draft[n.configKey] = n.valueType === 'number'
        ? (raw == null || raw === '' ? null : Number(raw))
        : (raw ?? '')
    }
    userRows.value = userRes?.data || []
    if (currentId.value == null) {
      // 默认选**第一个有配置项的分类**：顶层分类（界面配置 / 系统 AI 配置）自己不带配置项，
      // 选中它右边的表是空的，看着像"没东西可配"。
      const first = nodes.value.find((n) => !isItem(n) && itemChildren(n.id).length)
      if (first) currentId.value = first.id
    }
  } finally {
    loading.value = false
  }
}

/** 键 → 中文名（名字都在树上） */
function labelOf(configKey) {
  const n = nodes.value.find((x) => x.configKey === configKey)
  if (!n) return '（未知配置项）'
  const parent = nodes.value.find((p) => p.id === n.parentId)
  return parent ? `${parent.name} · ${n.name}` : n.name
}

/** 删掉某用户的那份覆盖 = 那个用户恢复系统默认 */
async function removeOverride(row) {
  await ElMessageBox.confirm('删除这一条覆盖？那个用户就恢复成系统默认。', '提示', { type: 'warning' })
  await deleteConfig(row.key, row.userId)
  await fetchAll()
}

// ---------- 分类的增删改 ----------

function openCategoryDialog(category) {
  Object.assign(categoryForm, {
    id: category?.id ?? null,
    parentId: category?.parentId ?? currentId.value ?? 0,
    name: category?.name ?? '',
    code: category?.code ?? '',
    remark: category?.remark ?? ''
  })
  categoryDialog.value = true
}

async function saveCategory() {
  if (!categoryForm.name.trim() || !categoryForm.code.trim()) {
    ElMessage.error('名称和编码都要填')
    return
  }
  saving.value = true
  try {
    const payload = {
      parentId: categoryForm.parentId,
      name: categoryForm.name.trim(),
      code: categoryForm.code.trim(),
      // 分类没有值类型：留空即"这是一个分类"
      valueType: null,
      remark: categoryForm.remark || null
    }
    if (categoryForm.id) {
      await updateConfigNode({ id: categoryForm.id, ...payload })
    } else {
      await createConfigNode(payload)
    }
    categoryDialog.value = false
    ElMessage.success('已保存')
    await fetchAll()
  } finally {
    saving.value = false
  }
}

async function removeCategory(category) {
  await ElMessageBox.confirm(
    `删除分类「${category.name}」？它下面的配置项和它们的值会一起删掉。`,
    '提示',
    { type: 'warning' }
  )
  await deleteConfigNode(category.id)
  if (currentId.value === category.id) currentId.value = null
  ElMessage.success('已删除')
  await fetchAll()
}

// ---------- 配置项的增删改 ----------

function openItemDialog(item) {
  Object.assign(itemForm, {
    id: item?.id ?? null,
    parentId: item?.parentId ?? currentId.value ?? 0,
    name: item?.name ?? '',
    code: item?.code ?? '',
    // 新建的配置项是"一段文本"：值类型不摆在这个通用表单里
    // （它是界面偏好那件专属的东西），要范围、步长就写在「说明」里。
    valueType: item?.valueType ?? 'text',
    value: item ? (draft[item.configKey] ?? '') : '',
    remark: item?.remark ?? ''
  })
  itemDialog.value = true
}

async function saveItem() {
  if (!itemForm.name.trim() || !itemForm.code.trim()) {
    ElMessage.error('中文描述和编码都要填')
    return
  }
  saving.value = true
  try {
    const payload = {
      parentId: itemForm.parentId,
      name: itemForm.name.trim(),
      code: itemForm.code.trim(),
      valueType: itemForm.valueType,
      remark: itemForm.remark || null
    }
    let configKey
    if (itemForm.id) {
      await updateConfigNode({ id: itemForm.id, ...payload })
      configKey = nodes.value.find((n) => n.id === itemForm.id)?.configKey
    } else {
      await createConfigNode(payload)
      // 新建的键 = 父分类的键 + '.' + 编码（后端按同一套规则拼）
      const parent = nodes.value.find((n) => n.id === itemForm.parentId)
      configKey = parent ? `${parent.configKey}.${itemForm.code.trim()}` : itemForm.code.trim()
    }
    // 值 = 系统默认那一行（user_id=0）
    await saveSystemConfig([{ key: configKey, value: String(itemForm.value ?? '') }])
    itemDialog.value = false
    ElMessage.success('已保存')
    await fetchAll()
  } finally {
    saving.value = false
  }
}

async function removeItem(item) {
  await ElMessageBox.confirm(`删除配置项「${item.name}」？它和它的值会一起删掉。`, '提示', {
    type: 'warning'
  })
  await deleteConfigNode(item.id)
  ElMessage.success('已删除')
  await fetchAll()
}

fetchAll()
</script>

<style scoped>
/* 页面骨架（.page / .page-head / .panel / .panel-head / .panel__title / .ops）
   在 assets/admin-page.css，这里只留本页专有样式 */

.content {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: var(--sp-12);
}
/* 用户覆盖那一屏是全宽表格：左边那列分类在这一屏没有意义 */
.content--single {
  grid-template-columns: 1fr;
}


.tree-node {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-8);
  width: 100%;
}
.tree-node__label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tree-node__count {
  flex: none;
  font-size: var(--text-xs);
  color: var(--cc-text-3);
}

.panel-subtitle {
  margin-top: var(--sp-4);
  color: var(--cc-text-3);
  font-size: var(--text-sm);
}

.mono {
  font-family: ui-monospace, Menlo, Consolas, monospace;
  font-size: var(--text-xs);
  color: var(--cc-text-3);
}

.cell-number {
  color: var(--cc-text-1);
  font-variant-numeric: tabular-nums;
}
.cell-text {
  color: var(--cc-text-3);
  font-size: var(--text-sm);
}
</style>
