<script setup lang="ts">
/**
 * 动作模板管理：列表 + 新增/编辑弹窗 + 删除
 * MET 与参考速度口径与业务端锻炼模块一致
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import BaseButton from '@/components/BaseButton/BaseButton.vue'
import DataList from '@/components/DataList/DataList.vue'
import type { DataListColumn } from '@/components/DataList/DataList.vue'
import LoadingMask from '@/components/LoadingMask/LoadingMask.vue'
import EmptyState from '@/components/EmptyState/EmptyState.vue'
import {
  listTemplateExercises,
  createTemplateExercise,
  updateTemplateExercise,
  deleteTemplateExercise,
  type TemplateExercise
} from '@/api/adminTemplate'

/** 动作类型字典（与业务端 exercise.ts 一致） */
const EXERCISE_TYPES = [
  { value: 'strength', label: '力量' },
  { value: 'cardio', label: '有氧计数' },
  { value: 'plank', label: '平板' },
  { value: 'walk', label: '散步' },
  { value: 'cycling', label: '骑行' },
  { value: 'stairs', label: '爬楼梯' }
] as const

const exerciseTypeLabel = (t: string) => EXERCISE_TYPES.find((i) => i.value === t)?.label ?? t

const columns: DataListColumn[] = [
  { key: 'name', label: '名称', ratio: 18 },
  { key: 'type', label: '类型', ratio: 9 },
  { key: 'met', label: '基础MET', ratio: 8 },
  { key: 'speed', label: '参考/上限', ratio: 10 },
  { key: 'flags', label: '计重/手', ratio: 8 },
  { key: 'ops', label: '操作', ratio: 9, ops: true }
]

const items = ref<TemplateExercise[]>([])
const loading = ref(false)
const saving = ref(false)

/** 搜索关键字（本地过滤名称/类型） */
const keyword = ref('')
const filteredItems = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  if (!k) return items.value
  return items.value.filter(
    (i) => i.name.toLowerCase().includes(k) || exerciseTypeLabel(i.type).toLowerCase().includes(k)
  )
})

const dialogOpen = ref(false)
const editingId = ref<number | null>(null)
const form = reactive({
  name: '',
  type: 'strength',
  baseMet: 0,
  refSpeed: null as number | null,
  maxSpeed: null as number | null,
  hasWeight: false,
  hasHand: false,
  sortOrder: 0
})

const dialogTitle = computed(() => (editingId.value == null ? '新增模板动作' : '编辑模板动作'))

async function load() {
  loading.value = true
  try {
    items.value = await listTemplateExercises()
  } finally {
    loading.value = false
  }
}

onMounted(load)

function openCreate() {
  editingId.value = null
  Object.assign(form, {
    name: '',
    type: 'strength',
    baseMet: 0,
    refSpeed: null,
    maxSpeed: null,
    hasWeight: false,
    hasHand: false,
    sortOrder: (items.value.length + 1) * 10
  })
  dialogOpen.value = true
}

function openEdit(item: TemplateExercise) {
  editingId.value = item.id
  Object.assign(form, {
    name: item.name,
    type: item.type,
    baseMet: item.baseMet,
    refSpeed: item.refSpeed,
    maxSpeed: item.maxSpeed,
    hasWeight: item.hasWeight,
    hasHand: item.hasHand,
    sortOrder: item.sortOrder
  })
  dialogOpen.value = true
}

async function handleSave() {
  if (!form.name.trim()) {
    ElMessage.warning('动作名不能为空')
    return
  }
  saving.value = true
  try {
    const payload = { ...form }
    if (editingId.value == null) {
      await createTemplateExercise(payload)
      ElMessage.success('模板动作已添加')
    } else {
      await updateTemplateExercise(editingId.value, payload)
      ElMessage.success('模板动作已修改')
    }
    dialogOpen.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function handleDelete(item: TemplateExercise) {
  try {
    await ElMessageBox.confirm(`确认删除模板动作「${item.name}」？删除后所有用户将不再看到该模板。`, '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  await deleteTemplateExercise(item.id)
  ElMessage.success('模板动作已删除')
  await load()
}
</script>

<template>
  <div class="tmpl-page">
    <div class="tmpl-page__toolbar">
      <span class="tmpl-page__count">共 {{ items.length }} 个模板动作</span>
      <span class="tmpl-page__toolbar-right">
        <el-input v-model="keyword" placeholder="搜索名称/类型" clearable style="width: 220px">
          <template #prefix>
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="11" cy="11" r="7" /><path d="M21 21l-4.35-4.35" /></svg>
          </template>
        </el-input>
        <BaseButton text="新增动作" @click="openCreate">
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
      <EmptyState v-if="!loading && !items.length" text="暂无模板动作" />
      <DataList v-else :items="items" :columns="columns" :max-rows="items.length" :row-height="44" :card-below="560">
          <template #cell="{ item, column }">
            <template v-if="column.key === 'name'">{{ item.name }}</template>
            <template v-else-if="column.key === 'type'">{{ exerciseTypeLabel(item.type) }}</template>
            <template v-else-if="column.key === 'met'"><span class="num">{{ item.baseMet }}</span></template>
            <template v-else-if="column.key === 'speed'">
              <span class="num">{{ item.refSpeed ?? '-' }} / {{ item.maxSpeed ?? '-' }}</span>
            </template>
            <template v-else-if="column.key === 'flags'">
              {{ item.hasWeight ? '记重' : '-' }} · {{ item.hasHand ? '记手' : '-' }}
            </template>
            <template v-else-if="column.key === 'ops'">
              <span class="tmpl-page__ops">
                <el-button link type="primary" size="small" @click="openEdit(item)">编辑</el-button>
                <el-button link type="danger" size="small" @click="handleDelete(item)">删除</el-button>
              </span>
            </template>
          </template>
        </DataList>
    </div>

    <el-dialog v-model="dialogOpen" :title="dialogTitle" width="520px" append-to-body>
      <el-form label-width="110px" class="tmpl-form">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" maxlength="20" placeholder="如：深蹲" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.type" style="width: 100%">
            <el-option v-for="t in EXERCISE_TYPES" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="基础MET">
          <el-input-number v-model="form.baseMet" :min="0" :max="99" :precision="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="参考速度/分钟">
          <el-input-number v-model="form.refSpeed" :min="0" :max="999" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="速度上限/分钟">
          <el-input-number v-model="form.maxSpeed" :min="0" :max="999" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="计重">
          <el-switch v-model="form.hasWeight" />
        </el-form-item>
        <el-form-item label="计左右手">
          <el-switch v-model="form.hasHand" />
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
