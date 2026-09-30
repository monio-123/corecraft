<template>
  <div class="learn-page">
    <div class="page-header">
      <h2>随手记</h2>
    </div>

    <!-- 主输入区：第一眼只看到这个 -->
    <el-card class="input-card" shadow="never">
      <!-- Milkdown WYSIWYG：6 按钮工具栏 + ProseMirror 渲染（封装在 MilkdownEditor 内） -->
      <MilkdownEditor v-model="content" />
      <div class="actions">
        <el-button
          type="primary"
          :loading="saving"
          :disabled="!content.trim()"
          @click="handleSave"
        >保存</el-button>
      </div>
    </el-card>

    <!-- 已保存列表 -->
    <div v-if="topics.length" class="list-section">
      <div class="list-header">
        <h3>知识点</h3>
        <el-radio-group v-model="viewMode" size="small">
          <el-radio-button value="list">列表</el-radio-button>
          <el-radio-button value="tree">按树</el-radio-button>
        </el-radio-group>
        <el-button size="small" text @click="openTagManager">
          <el-icon><CollectionTag /></el-icon> 管理标签
        </el-button>
        <span class="list-count">{{ topics.length }} 个</span>
      </div>

      <!-- 列表模式：所有 topic 平铺 -->
      <div v-show="viewMode === 'list'" class="mode-panel">
        <el-row :gutter="12">
          <el-col
            v-for="t in topics"
            :key="t.id"
            :xs="24" :sm="12" :md="8"
          >
            <TopicCard :topic="t" @click="goDetail(t)" @delete="handleDelete" />
          </el-col>
        </el-row>
      </div>

      <!-- 按树模式：按 tags 图构建的嵌套知识森林 -->
      <div v-show="viewMode === 'tree'" class="mode-panel">
        <el-collapse v-model="expandedRoots">
          <el-collapse-item
            v-for="root in knowledgeForest"
            :key="rootTagKey(root)"
            :name="rootTagKey(root)"
          >
            <template #title>
              <span class="group-label">{{ formatTagLabel(root.tag) }}</span>
              <span class="group-count">{{ root.children.length }} 个</span>
            </template>
            <TreeNode
              v-for="child in root.children"
              :key="childNodeKey(child)"
              :node="child"
              @topic-click="goDetail"
              @topic-delete="handleDelete"
            />
          </el-collapse-item>
        </el-collapse>
      </div>
    </div>

    <el-empty v-else description="还没有知识点" />

    <!-- 标签管理 dialog：列出所有 tag（含孤儿），支持重命名/删除 -->
    <el-dialog
      v-model="tagDialogVisible"
      title="管理标签"
      width="520px"
      :close-on-click-modal="false"
    >
      <div v-if="tags.length" class="tag-manager-list">
        <div v-for="t in tags" :key="t.id" class="tag-row">
          <div class="tag-row__main">
            <div class="tag-row__name">{{ t.name }}</div>
            <div class="tag-row__meta">被 {{ t.refCount }} 个知识点引用</div>
          </div>
          <div class="tag-row__ops">
            <el-button size="small" text @click="handleRenameTag(t)">重命名</el-button>
            <el-button size="small" text type="danger" :disabled="t.refCount > 0" @click="handleDeleteTag(t)">删除</el-button>
          </div>
        </div>
      </div>
      <el-empty v-else description="还没有标签" :image-size="60" />
      <div v-if="tags.length" class="tag-manager-tip">
        提示：仍被引用的标签请先移除所有引用（去 topic 详情页删标签）后再删除。
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
// 让 component.name 与 route.name ('CorecraftWebLearnTopic') 对齐——
// 否则 Layout.vue 里 keep-alive 的 include 用 route.name 匹配，但 SFC 默认按文件名推断
// component.name = 'LearnTopicView'，不命中 → 实例被销毁 → 切换菜单状态丢失。
defineOptions({ name: 'CorecraftWebLearnTopic' })
import { ref, computed, onMounted, onBeforeUnmount, h } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElCollapse, ElCollapseItem } from 'element-plus'
import { CollectionTag } from '@element-plus/icons-vue'
import MilkdownEditor from '../../components/MilkdownEditor.vue'
import {
  ensureTopicsLoaded, getTopics, getKnowledgeForest,
  createTopic, deleteTopic, formatTagLabel, encodeTag
} from './useCorecraftWebStore'
import { getTagList, renameTag as apiRenameTag, deleteTag as apiDeleteTag } from '../../api/knowledge'
import TopicCard from './TopicCard.vue'

const router = useRouter()

const content = ref('')
const saving = ref(false)
// 列表数据走 computed 直接读 store 单例缓存：store 一变视图自动同步。
// 之前用 ref([]) + onMounted 拉数据 + 手动 topics.value = getTopics() 同步，
// keep-alive 复用 LearnTopicView 实例后，从详情页返回时本地 topics 是旧的。
const topics = computed(() => getTopics())
const viewMode = ref('list')
const expandedRoots = ref([])

// 标签管理 dialog
const tagDialogVisible = ref(false)
const tags = ref([])

// 按 tags 图派生的知识森林（嵌套树），store 实时算
const knowledgeForest = computed(() => getKnowledgeForest())

// 树节点：递归渲染 forest 子树，topic 节点用 TopicCard，tag 节点用嵌套 el-collapse
const TreeNode = {
  name: 'TreeNode',
  props: ['node'],
  emits: ['topic-click', 'topic-delete'],
  setup(props, { emit }) {
    // 内部折叠状态：默认展开所有子 tag；topic 节点没有 children，置空数组
    const innerExpanded = ref(
      (props.node.children || [])
        .filter((c) => c.kind === 'tag')
        .map((c) => encodeTag(c.tag))
    )
    return () => {
      if (props.node.kind === 'topic') {
        return h('div', { class: 'tree-topic-cell' },
          h(TopicCard, {
            topic: props.node.topic,
            hideTagKey: props.node.hideTagKey,
            showTags: false,
            onClick: () => emit('topic-click', props.node.topic),
            onDelete: (t) => emit('topic-delete', t)
          })
        )
      }
      // tag 节点：嵌套折叠面板，children 递归
      return h(ElCollapse, {
        modelValue: innerExpanded.value,
        'onUpdate:modelValue': (v) => { innerExpanded.value = v },
        class: 'inner-collapse'
      }, () => h(ElCollapseItem, {
        name: encodeTag(props.node.tag)
      }, {
        title: () => [
          h('span', { class: 'group-icon' }, '📁'),
          h('span', { class: 'group-label' }, formatTagLabel(props.node.tag)),
          h('span', { class: 'group-count' }, props.node.children.length + ' 个')
        ],
        default: () => props.node.children.map((child) =>
          h(TreeNode, {
            key: child.kind === 'topic' ? 't' + child.topic.id : 'tag' + encodeTag(child.tag),
            node: child,
            onTopicClick: (t) => emit('topic-click', t),
            onTopicDelete: (t) => emit('topic-delete', t)
          })
        )
      }))
    }
  }
}

// 模板里用的 key 辅助
function rootTagKey(root) {
  return 'root:' + encodeTag(root.tag)
}
function childNodeKey(child) {
  return child.kind === 'topic' ? 't' + child.topic.id : 'tag' + encodeTag(child.tag)
}

async function refresh() {
  await ensureTopicsLoaded(true)
  // topics 走 computed 自动同步；只需维护 expandedRoots（操作后根 tag 集合变化时重置展开状态）
  expandedRoots.value = knowledgeForest.value.map((r) => rootTagKey(r))
}

async function handleSave() {
  if (!content.value.trim() || saving.value) return
  saving.value = true
  try {
    await createTopic({
      title: '',
      content: content.value.trim(),
      tags: []
    })
    ElMessage.success('已保存')
    content.value = ''
    await refresh()
  } finally {
    saving.value = false
  }
}

function goDetail(t) {
  // 走路由 → Layout 顶部 tabs 自动加一个 "知识点详情" tab，可关闭、可切别的菜单再切回来
  router.push({ name: 'CorecraftWebTopicDetail', params: { id: t.id } })
}

// 列表/按树两种模式下，卡片右上角删除按钮统一走这里
// store.deleteTopic 内部已触发 ensureTopicsLoaded(true)，这里只需同步本地视图 state
async function handleDelete(t) {
  try {
    await ElMessageBox.confirm(
      `确定删除「${t.title || '未命名'}」吗？`,
      '删除知识点',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  await deleteTopic(t.id)
  ElMessage.success('已删除')
  // topics 走 computed 自动同步
  expandedRoots.value = knowledgeForest.value.map((r) => rootTagKey(r))
}

// ==================== 标签管理 ====================

async function openTagManager() {
  tagDialogVisible.value = true
  await loadTags()
}

async function loadTags() {
  const res = await getTagList()
  tags.value = res?.data || []
}

async function handleRenameTag(t) {
  let input
  try {
    const { value } = await ElMessageBox.prompt(
      `重命名「${t.name}」`,
      '重命名标签',
      {
        inputPlaceholder: '输入新名字',
        inputValue: t.name,
        confirmButtonText: '保存',
        cancelButtonText: '取消'
      }
    )
    input = value
  } catch {
    return
  }
  const newName = String(input || '').trim()
  if (!newName) {
    ElMessage.warning('标签名不能为空')
    return
  }
  if (newName === t.name) return
  try {
    await apiRenameTag({ oldName: t.name, newName })
    ElMessage.success('已重命名')
    await loadTags()
    // 后端 rebuildAutoTrees 已重算 topic.treeId，刷一下 topic 列表让"按树"视图同步
    await refresh()
  } catch (e) {
    ElMessage.error(e?.message || '重命名失败')
  }
}

async function handleDeleteTag(t) {
  try {
    await ElMessageBox.confirm(
      `确定删除标签「${t.name}」吗？`,
      '删除标签',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await apiDeleteTag(t.id)
    ElMessage.success('已删除')
    await loadTags()
    await refresh()
  } catch (e) {
    ElMessage.error(e?.message || '删除失败')
  }
}

onMounted(refresh)
</script>

<style scoped>
.learn-page {
  max-width: 880px;
  margin: 0 auto;
  padding: 24px;
}

.page-header {
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0 0 4px 0;
  font-size: 20px;
  font-weight: 600;
}

.input-card {
  margin-bottom: 32px;
}

.input-card :deep(.el-card__body) {
  padding: 16px;
}

.actions {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

.list-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}

.list-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.list-count {
  font-size: 12px;
  color: #909399;
  margin-left: auto;
}

.mode-panel {
  margin-top: 4px;
}

/* 树节点（tag 节点 / 目录）：浅灰底 + 折叠箭头，hover 无变化（不假装可点进详情） */
:deep(.el-collapse-item__header) {
  border: none;
  padding-left: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  background: #f5f7fa;
  border-radius: 6px;
  margin-bottom: 4px;
  height: 38px;
}

:deep(.el-collapse-item__wrap) {
  border: none;
}

:deep(.el-collapse-item__content) {
  padding: 4px 0 0 0;
}

/* 嵌套子树往里缩进，体现层级 */
:deep(.inner-collapse .el-collapse-item__header) {
  padding-left: 12px;
  font-size: 13px;
  font-weight: 500;
  color: #606266;
  background: #fafbfc;
  height: 34px;
}
:deep(.inner-collapse .el-collapse-item__content) {
  padding-left: 12px;
}

.tree-topic-cell {
  margin-bottom: 8px;
}

.group-icon {
  margin-right: 4px;
  font-size: 13px;
}

.group-label {
  flex: 1;
  font-weight: 600;
  color: #303133;
}

.group-count {
  font-size: 12px;
  font-weight: 400;
  color: #909399;
  margin-right: 12px;
}

/* 标签管理 dialog */
.tag-manager-list {
  max-height: 420px;
  overflow-y: auto;
}
.tag-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 4px;
  border-bottom: 1px solid #f0f2f5;
}
.tag-row:last-child {
  border-bottom: none;
}
.tag-row__main {
  flex: 1;
  min-width: 0;
}
.tag-row__name {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
}
.tag-row__meta {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}
.tag-row__ops {
  display: flex;
  gap: 4px;
}
.tag-manager-tip {
  margin-top: 12px;
  padding: 8px 10px;
  background: #fafbfc;
  border-radius: 4px;
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
}
</style>
