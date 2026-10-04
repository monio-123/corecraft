<template>
  <el-form :model="form" label-width="90px" class="permission-form">
    <!-- 父节点由父级算好后传进来：树的查找逻辑留在页面里，表单只负责显示 -->
    <el-form-item label="父节点">
      <el-input :model-value="parentLabel" disabled />
    </el-form-item>

    <el-form-item label="类型" :required="required">
      <el-select v-model="form.type" style="width: 220px">
        <el-option v-for="t in TYPES" :key="t" :label="t" :value="t" />
      </el-select>
    </el-form-item>

    <el-form-item label="名称" :required="required">
      <el-input v-model="form.name" />
    </el-form-item>

    <el-form-item label="编码" :required="required">
      <el-input v-model="form.code" />
    </el-form-item>

    <el-form-item label="排序">
      <el-input-number v-model="form.sort" :min="0" />
    </el-form-item>

    <el-form-item label="启用">
      <el-switch v-model="form.enabled" />
    </el-form-item>

    <!-- MENU 类型专用字段 -->
    <el-form-item label="界面类型">
      <el-radio-group v-model="form.menuShell">
        <el-radio-button value="admin">管理后台</el-radio-button>
        <el-radio-button value="app">业务界面</el-radio-button>
      </el-radio-group>
      <div class="hint">{{ SHELL_HINT }}</div>
    </el-form-item>

    <template v-if="isMenuType">
      <el-form-item label="路由路径">
        <el-input v-model="form.menuPath" placeholder="/app/learn" />
      </el-form-item>
      <el-form-item label="图标">
        <el-select v-model="form.menuIcon" clearable placeholder="选择图标" style="width: 220px">
          <el-option v-for="icon in ICONS" :key="icon" :label="icon" :value="icon">
            <div class="icon-option">
              <el-icon><component :is="Icons[icon]" /></el-icon>
              <span>{{ icon }}</span>
            </div>
          </el-option>
        </el-select>
      </el-form-item>
    </template>

    <el-form-item v-else label="Meta">
      <el-input v-model="form.meta" type="textarea" :rows="3" placeholder='JSON 字符串，如 {"icon":"Setting"}' />
    </el-form-item>

    <!-- 编辑面板把「保存」传进来，新增弹窗的按钮在弹窗 footer 上，不走这里 -->
    <el-form-item v-if="$slots.default">
      <slot />
    </el-form-item>
  </el-form>
</template>

<script setup>
import { computed } from 'vue'
import * as Icons from '@element-plus/icons-vue'

const props = defineProps({
  // 字段集由页面的 emptyForm() 产出，表单只双向绑定，不自己持有
  form: { type: Object, required: true },
  parentLabel: { type: String, default: '' },
  // 新增时类型/名称/编码标必填，编辑已存在的节点不标
  required: { type: Boolean, default: false }
})

const TYPES = ['GROUP', 'MENU', 'API', 'OP']

const ICONS = [
  'House', 'Setting', 'User', 'UserFilled', 'Lock', 'CollectionTag',
  'Reading', 'Document', 'Timer', 'MagicStick', 'List', 'Grid',
  'Menu', 'Search', 'Bell', 'Star', 'Flag', 'Folder',
  'Files', 'Link', 'Edit', 'Delete', 'Plus', 'Minus',
  'Check', 'Close', 'ArrowRight', 'ArrowDown', 'Back', 'Right'
]

const SHELL_HINT = '决定这一项出现在哪个壳里。分组和菜单都生效。'

const isMenuType = computed(() => props.form.type === 'MENU')
</script>

<style scoped>
.permission-form {
  max-width: 760px;
}

.icon-option {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
