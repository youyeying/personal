<script setup lang="ts">
/**
 * 食物模板管理：列表 + 新增/编辑弹窗 + 删除
 * 营养按「每100g」口径，与业务端一致
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import BaseButton from '@/components/BaseButton/BaseButton.vue'
import DataList from '@/components/DataList/DataList.vue'
import type { DataListColumn } from '@/components/DataList/DataList.vue'
import LoadingMask from '@/components/LoadingMask/LoadingMask.vue'
import EmptyState from '@/components/EmptyState/EmptyState.vue'
import {
  listTemplateFoods,
  createTemplateFood,
  updateTemplateFood,
  deleteTemplateFood,
  type TemplateFood
} from '@/api/adminTemplate'

/** 食物类型字典（与业务端 food.ts 一致） */
const FOOD_TYPES = [
  { value: 'staple', label: '主食' },
  { value: 'protein', label: '肉蛋' },
  { value: 'veg', label: '蔬菜' },
  { value: 'fruit', label: '水果' },
  { value: 'snack', label: '零食饮品' },
  { value: 'other', label: '其他' }
] as const

const foodTypeLabel = (t: string) => FOOD_TYPES.find((i) => i.value === t)?.label ?? t

const columns: DataListColumn[] = [
  { key: 'name', label: '名称', ratio: 16 },
  { key: 'type', label: '类型', ratio: 8 },
  { key: 'kcal', label: '热量', ratio: 6 },
  { key: 'protein', label: '蛋白', ratio: 5 },
  { key: 'fat', label: '脂肪', ratio: 5 },
  { key: 'carbs', label: '碳水', ratio: 5 },
  { key: 'grams', label: '份量', ratio: 7 },
  { key: 'ops', label: '操作', ratio: 9, ops: true }
]

const items = ref<TemplateFood[]>([])
const loading = ref(false)
const saving = ref(false)

/** 搜索关键字（本地过滤名称/类型） */
const keyword = ref('')
const filteredItems = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  if (!k) return items.value
  return items.value.filter(
    (i) => i.name.toLowerCase().includes(k) || foodTypeLabel(i.type).toLowerCase().includes(k)
  )
})

/** 弹窗编辑中的表单 */
const dialogOpen = ref(false)
const editingId = ref<number | null>(null)
const form = reactive({
  name: '',
  type: 'staple',
  kcal: 0,
  protein: 0,
  fat: 0,
  carbs: 0,
  sodium: 0,
  fiber: 0,
  defaultGrams: 100,
  unitLabel: 'g',
  sortOrder: 0
})

const dialogTitle = computed(() => (editingId.value == null ? '新增模板食物' : '编辑模板食物'))

async function load() {
  loading.value = true
  try {
    items.value = await listTemplateFoods()
  } finally {
    loading.value = false
  }
}

onMounted(load)

function openCreate() {
  editingId.value = null
  Object.assign(form, {
    name: '',
    type: 'staple',
    kcal: 0,
    protein: 0,
    fat: 0,
    carbs: 0,
    sodium: 0,
    fiber: 0,
    defaultGrams: 100,
    unitLabel: 'g',
    sortOrder: (items.value.length + 1) * 10
  })
  dialogOpen.value = true
}

function openEdit(item: TemplateFood) {
  editingId.value = item.id
  Object.assign(form, {
    name: item.name,
    type: item.type,
    kcal: item.kcal,
    protein: item.protein,
    fat: item.fat,
    carbs: item.carbs,
    sodium: item.sodium,
    fiber: item.fiber,
    defaultGrams: item.defaultGrams,
    unitLabel: item.unitLabel,
    sortOrder: item.sortOrder
  })
  dialogOpen.value = true
}

async function handleSave() {
  if (!form.name.trim()) {
    ElMessage.warning('食物名不能为空')
    return
  }
  saving.value = true
  try {
    const payload = { ...form }
    if (editingId.value == null) {
      await createTemplateFood(payload)
      ElMessage.success('模板食物已添加')
    } else {
      await updateTemplateFood(editingId.value, payload)
      ElMessage.success('模板食物已修改')
    }
    dialogOpen.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function handleDelete(item: TemplateFood) {
  try {
    await ElMessageBox.confirm(`确认删除模板食物「${item.name}」？删除后所有用户将不再看到该模板。`, '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  await deleteTemplateFood(item.id)
  ElMessage.success('模板食物已删除')
  await load()
}

/** 数值列统一渲染（空值显示 -） */
function numText(v: number | null | undefined): string {
  return v == null ? '-' : String(v)
}
</script>

<template>
  <div class="tmpl-page">
    <div class="tmpl-page__toolbar">
      <span class="tmpl-page__count">共 {{ items.length }} 个模板食物</span>
      <span class="tmpl-page__toolbar-right">
        <el-input v-model="keyword" placeholder="搜索名称/类型" clearable style="width: 220px">
          <template #prefix>
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="11" cy="11" r="7" /><path d="M21 21l-4.35-4.35" /></svg>
          </template>
        </el-input>
        <BaseButton text="新增食物" @click="openCreate">
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
      <EmptyState v-if="!loading && !items.length" text="暂无模板食物" />
      <DataList v-else :items="filteredItems" :columns="columns" :max-rows="filteredItems.length" :row-height="44" :card-below="560">
          <template #cell="{ item, column }">
            <template v-if="column.key === 'name'">{{ item.name }}</template>
            <template v-else-if="column.key === 'type'">{{ foodTypeLabel(item.type) }}</template>
            <template v-else-if="column.key === 'grams'">
              <span class="num">{{ numText(item.defaultGrams) }} {{ item.unitLabel }}</span>
            </template>
            <template v-else-if="column.key === 'ops'">
              <span class="tmpl-page__ops">
                <el-button link type="primary" size="small" @click="openEdit(item)">编辑</el-button>
                <el-button link type="danger" size="small" @click="handleDelete(item)">删除</el-button>
              </span>
            </template>
            <template v-else><span class="num">{{ numText((item as Record<string, unknown>)[column.key] as number) }}</span></template>
          </template>
      </DataList>
    </div>

    <el-dialog v-model="dialogOpen" :title="dialogTitle" width="560px" append-to-body>
      <el-form label-width="90px" class="tmpl-form">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" maxlength="20" placeholder="如：米饭" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.type" style="width: 100%">
            <el-option v-for="t in FOOD_TYPES" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="热量 kcal">
          <el-input-number v-model="form.kcal" :min="0" :max="9999" style="width: 100%" />
        </el-form-item>
        <el-form-item label="蛋白 g">
          <el-input-number v-model="form.protein" :min="0" :max="999" :precision="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="脂肪 g">
          <el-input-number v-model="form.fat" :min="0" :max="999" :precision="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="碳水 g">
          <el-input-number v-model="form.carbs" :min="0" :max="999" :precision="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="钠 mg">
          <el-input-number v-model="form.sodium" :min="0" :max="99999" :precision="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="纤维 g">
          <el-input-number v-model="form.fiber" :min="0" :max="999" :precision="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="默认份量">
          <el-input-number v-model="form.defaultGrams" :min="1" :max="99999" style="width: 100%" />
        </el-form-item>
        <el-form-item label="单位">
          <el-select v-model="form.unitLabel" style="width: 100%">
            <el-option label="g（固体）" value="g" />
            <el-option label="ml（液体）" value="ml" />
            <el-option label="碗" value="碗" />
            <el-option label="个" value="个" />
            <el-option label="根" value="根" />
            <el-option label="片" value="片" />
            <el-option label="份" value="份" />
          </el-select>
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
</style>
