<script setup lang="ts">
/**
 * 收支分类模板管理：列表 + 新增/编辑弹窗 + 删除
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import BaseButton from '@/components/BaseButton/BaseButton.vue'
import DataList from '@/components/DataList/DataList.vue'
import type { DataListColumn } from '@/components/DataList/DataList.vue'
import LoadingMask from '@/components/LoadingMask/LoadingMask.vue'
import EmptyState from '@/components/EmptyState/EmptyState.vue'
import {
  listTemplateCategories,
  createTemplateCategory,
  updateTemplateCategory,
  deleteTemplateCategory,
  type TemplateCategory
} from '@/api/adminTemplate'

const typeLabel = (t: number) => (t === 2 ? '收入' : '支出')

const columns: DataListColumn[] = [
  { key: 'name', label: '名称', ratio: 12 },
  { key: 'type', label: '类型', ratio: 8 },
  { key: 'sort', label: '排序', ratio: 6 },
  { key: 'ops', label: '操作', ratio: 8, ops: true }
]

const items = ref<TemplateCategory[]>([])
const loading = ref(false)
const saving = ref(false)

/** 搜索关键字（本地过滤名称） */
const keyword = ref('')
const filteredItems = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  if (!k) return items.value
  return items.value.filter((i) => i.name.toLowerCase().includes(k) || typeLabel(i.type).includes(k))
})

const dialogOpen = ref(false)
const editingId = ref<number | null>(null)
const form = reactive({
  name: '',
  type: 1,
  sortOrder: 0
})

const dialogTitle = computed(() => (editingId.value == null ? '新增模板分类' : '编辑模板分类'))

async function load() {
  loading.value = true
  try {
    items.value = await listTemplateCategories()
  } finally {
    loading.value = false
  }
}

onMounted(load)

function openCreate() {
  editingId.value = null
  Object.assign(form, {
    name: '',
    type: 1,
    sortOrder: (items.value.length + 1) * 10
  })
  dialogOpen.value = true
}

function openEdit(item: TemplateCategory) {
  editingId.value = item.id
  Object.assign(form, {
    name: item.name,
    type: item.type,
    sortOrder: item.sortOrder
  })
  dialogOpen.value = true
}

async function handleSave() {
  if (!form.name.trim()) {
    ElMessage.warning('分类名不能为空')
    return
  }
  saving.value = true
  try {
    const payload = { ...form }
    if (editingId.value == null) {
      await createTemplateCategory(payload)
      ElMessage.success('模板分类已添加')
    } else {
      await updateTemplateCategory(editingId.value, payload)
      ElMessage.success('模板分类已修改')
    }
    dialogOpen.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function handleDelete(item: TemplateCategory) {
  try {
    await ElMessageBox.confirm(`确认删除模板分类「${item.name}」？删除后所有用户将不再看到该模板。`, '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  await deleteTemplateCategory(item.id)
  ElMessage.success('模板分类已删除')
  await load()
}
</script>

<template>
  <div class="tmpl-page">
    <div class="tmpl-page__toolbar">
      <span class="tmpl-page__count">共 {{ items.length }} 个模板分类</span>
      <span class="tmpl-page__toolbar-right">
        <el-input v-model="keyword" placeholder="搜索名称/类型" clearable style="width: 220px">
          <template #prefix>
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="11" cy="11" r="7" /><path d="M21 21l-4.35-4.35" /></svg>
          </template>
        </el-input>
        <BaseButton text="新增分类" @click="openCreate">
          <template #icon>
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
              <path d="M12 5v14M5 12h14" />
            </svg>
          </template>
        </BaseButton>
      </span>
    </div>

    <div class="tmpl-page__list">
      <LoadingMask :show="loading" />
      <EmptyState v-if="!loading && !items.length" text="暂无模板分类" />
      <DataList v-else :items="items" :columns="columns" :max-rows="items.length" :row-height="44" :card-below="560">
          <template #cell="{ item, column }">
            <template v-if="column.key === 'name'">{{ item.name }}</template>
            <template v-else-if="column.key === 'type'">{{ typeLabel(item.type) }}</template>
            <template v-else-if="column.key === 'sort'"><span class="num">{{ item.sortOrder }}</span></template>
            <template v-else-if="column.key === 'ops'">
              <span class="tmpl-page__ops">
                <el-button link type="primary" size="small" @click="openEdit(item)">编辑</el-button>
                <el-button link type="danger" size="small" @click="handleDelete(item)">删除</el-button>
              </span>
            </template>
          </template>
        </DataList>
    </div>

    <el-dialog v-model="dialogOpen" :title="dialogTitle" width="400px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" maxlength="10" placeholder="如：餐饮" />
        </el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="form.type">
            <el-radio :value="1">支出</el-radio>
            <el-radio :value="2">收入</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" :max="99999" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use './templateManage';

.tmpl-page__ops {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
}
</style>
