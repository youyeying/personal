<script setup lang="ts">
/**
 * 饮食 · 历史子页组件
 * - 日期范围筛选 + 按天分组列表（每日合计 kcal + 项数）
 * - 操作：复制整餐到今日 / 修改 / 删除
 */
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Copy, Trash2, Pencil } from '@lucide/vue'
import { listFoodItems, listFoodRecords, updateFoodRecord, deleteFoodRecord, createFoodRecord } from '@/api/food'
import type { FoodItem, FoodRecord, MealType } from '@/api/food'
import { MEAL_LABELS } from '@/api/food'
import { formatDate } from '@/utils/format'
import { fetchAllRecords } from '@/utils/fetchAll'
import { groupByDate } from '@/utils/daysSeries'
import BlockTitle from '@/components/BlockTitle/BlockTitle.vue'
import DateRangePicker from '@/components/DateRangePicker/DateRangePicker.vue'
import LoadingMask from '@/components/LoadingMask/LoadingMask.vue'

const props = defineProps<{ tick: number }>()
const emit = defineEmits<{ (e: 'changed'): void; (e: 'navigate', tab: string): void }>()

const today = formatDate(new Date())
function daysAgo(n: number) {
  const d = new Date()
  d.setDate(d.getDate() - n)
  return formatDate(d)
}
const dateRange = ref<[string, string] | null>([daysAgo(29), today])

const loading = ref(false)
const records = ref<FoodRecord[]>([])
const items = ref<FoodItem[]>([])

function foodOf(id: number) {
  return items.value.find((i) => i.id === id)
}
function kcalOf(r: FoodRecord) {
  const f = foodOf(r.foodId)
  return f ? Math.round(f.kcal * Number(r.grams) / 100) : 0
}
/** 份量单位：液体 ml，其余 g */
function unitOf(r: FoodRecord) {
  return foodOf(r.foodId)?.unitLabel === 'ml' ? 'ml' : 'g'
}

/** 按天分组（倒序） */
const byDay = computed(() => {
  const map = groupByDate<FoodRecord, FoodRecord[]>(records.value, (r) => r.recordDate, (acc, r) => [...(acc ?? []), r])
  return [...map.entries()].sort((a, b) => b[0].localeCompare(a[0]))
})

async function load(silent = false) {
  if (!silent) loading.value = true
  try {
    const [recList, itms] = await Promise.all([
      // 区间全量（后端每页上限 100，工具循环翻页取完）
      fetchAllRecords((page, size) =>
        listFoodRecords({ startDate: dateRange.value?.[0], endDate: dateRange.value?.[1], page, size })),
      listFoodItems()
    ])
    records.value = recList
    items.value = itms
  } finally {
    if (!silent) loading.value = false
  }
}

/** 复制某天到今日（逐条新增） */
async function copyDay(date: string) {
  const list = records.value.filter((r) => r.recordDate === date)
  if (!list.length) return
  for (const r of list) {
    await createFoodRecord({ foodId: r.foodId, recordDate: today, mealType: r.mealType, grams: Number(r.grams) })
  }
  ElMessage.success(`已复制 ${date} 的 ${list.length} 条到今日`)
  emit('changed')
}

/* ---------- 修改弹窗 ---------- */
const editVisible = ref(false)
const editForm = ref<{ id: number; foodId: number; recordDate: string; mealType: MealType; grams: string }>({ id: 0, foodId: 0, recordDate: today, mealType: 'lunch', grams: '100' })

/** 当前编辑食物（默认份量/单位提示用） */
const editFood = computed(() => items.value.find((i) => i.id === editForm.value.foodId))

/** 切换食物 → 带出默认份量（无默认则保持 100） */
function onEditFoodChange(id: number) {
  const f = items.value.find((i) => i.id === id)
  editForm.value.grams = f?.defaultGrams != null ? String(f.defaultGrams) : '100'
}

function openEdit(r: FoodRecord) {
  editForm.value = { id: r.id, foodId: r.foodId, recordDate: r.recordDate, mealType: r.mealType, grams: String(r.grams) }
  editVisible.value = true
}

async function saveEdit() {
  const f = editForm.value
  if (!f.foodId) return ElMessage.warning('请选择食物')
  const g = Number(f.grams)
  if (!g || g <= 0) return ElMessage.warning('请输入份量')
  await updateFoodRecord(f.id, { foodId: f.foodId, recordDate: f.recordDate, mealType: f.mealType, grams: g })
  ElMessage.success('已更新')
  editVisible.value = false
  emit('changed')
  await load(true)
}

async function remove(r: FoodRecord) {
  await ElMessageBox.confirm('删除这条记录？', '确认', { type: 'warning' })
  await deleteFoodRecord(r.id)
  ElMessage.success('已删除')
  emit('changed')
  await load(true)
}

watch(() => props.tick, () => load(true))
watch(dateRange, () => { if (dateRange.value) load() })
onMounted(load)
</script>

<template>
  <section class="card fdh">
    <BlockTitle title="饮食历史" hint="按天汇总，可复制到今日">
      <template #aside>
        <button class="fdh__link" @click="emit('navigate', 'input')">去记录</button>
      </template>
    </BlockTitle>

    <div class="fdh__bar">
      <DateRangePicker v-model="dateRange" width="min(260px,100%)" />
    </div>

    <div v-if="!loading && !records.length" class="fdh__empty">区间内暂无饮食记录</div>

    <template v-else>
      <div v-for="[date, list] in byDay" :key="date" class="fdh__day">
        <div class="fdh__day-head">
          <span class="date">{{ date }}</span>
          <span class="total">{{ list.reduce((s, r) => s + kcalOf(r), 0) }} kcal · {{ list.length }} 项</span>
          <span class="ops">
            <button title="复制到今日" @click="copyDay(date)"><component :is="Copy" :size="13" /> 复制</button>
          </span>
        </div>
        <div v-for="r in list" :key="r.id" class="fdh__item">
          <span class="meal">{{ MEAL_LABELS[r.mealType] }}</span>
          <span class="name">{{ foodOf(r.foodId)?.name ?? '食物' }}</span>
          <span class="detail">{{ r.grams }}{{ unitOf(r) }}</span>
          <span class="kcal">{{ kcalOf(r) }}<small>kcal</small></span>
          <button class="op" title="修改" @click="openEdit(r)"><component :is="Pencil" :size="13" /></button>
          <button class="op op--del" title="删除" @click="remove(r)"><component :is="Trash2" :size="13" /></button>
        </div>
      </div>
    </template>

    <!-- 修改弹窗 -->
    <el-dialog v-model="editVisible" title="修改饮食记录" width="min(420px,92vw)">
      <div class="fdh__edit">
        <div class="fdh__field"><label>食物（可输入搜索）</label>
          <el-select v-model="editForm.foodId" filterable placeholder="搜索或选择食物" @change="onEditFoodChange">
            <el-option
              v-for="f in items" :key="f.id" :value="f.id" :label="f.name"
            >{{ f.name }}（{{ f.kcal }} kcal/100{{ f.unitLabel === 'ml' ? 'ml' : 'g' }}）</el-option>
          </el-select>
        </div>
        <div class="fdh__optgrid">
          <div class="fdh__field"><label>餐次</label>
            <select v-model="editForm.mealType">
              <option v-for="(label, key) in MEAL_LABELS" :key="key" :value="key">{{ label }}</option>
            </select>
          </div>
          <div class="fdh__field">
            <label>份量（{{ editFood?.unitLabel === 'ml' ? 'ml' : 'g' }}）</label>
            <input v-model="editForm.grams" class="num" type="number" min="1"
              :placeholder="editFood?.defaultGrams != null ? `默认 ${editFood.defaultGrams}` : '如：150'">
          </div>
        </div>
        <p v-if="editFood?.defaultGrams != null" class="fdh__default-tip">该食物默认份量：{{ editFood.defaultGrams }}{{ editFood.unitLabel === 'ml' ? 'ml' : 'g' }}</p>
        <div class="fdh__field"><label>日期</label><input v-model="editForm.recordDate" class="num" type="date"></div>
      </div>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>

    <LoadingMask :show="loading" :size="28" text="加载历史…" />
  </section>
</template>

<style lang="scss" scoped>
@use '../food';
</style>
