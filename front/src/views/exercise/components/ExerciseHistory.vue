<script setup lang="ts">
/**
 * 健康 · 锻炼历史子页组件
 * 日期范围筛选 + 分页列表（DataList）+ 修改弹窗 + 删除
 * 列表展示原始参数 + 前端计算的净/总消耗
 */
import { computed, markRaw, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Pencil, Trash2, Copy } from '@lucide/vue'
import { listExerciseItems, listExerciseRecords, updateExerciseRecord, deleteExerciseRecord } from '@/api/exercise'
import type { ExerciseItem, ExerciseRecord } from '@/api/exercise'
import { listWeightRecords } from '@/api/health'
import { walkSpeedKmh, walkMet, cyclingMet, speedMet, stairsMet, repsEffectiveMinutes, calcKcal, totalSeconds, formatDuration } from '@/utils/exercise'
import { confirmDelete } from '@/utils/confirm'
import PagePager from '@/components/PagePager/PagePager.vue'
import LoadingMask from '@/components/LoadingMask/LoadingMask.vue'
import BlockTitle from '@/components/BlockTitle/BlockTitle.vue'
import DateRangePicker from '@/components/DateRangePicker/DateRangePicker.vue'
import DataList from '@/components/DataList/DataList.vue'
import RecordDetailDialog from '@/components/RecordDetailDialog/RecordDetailDialog.vue'
import type { DetailRow } from '@/components/RecordDetailDialog/RecordDetailDialog.vue'
import type { DataListColumn } from '@/components/DataList/DataList.vue'

const props = defineProps<{ tick: number }>()
const emit = defineEmits<{ (e: 'changed'): void; (e: 'copy', r: ExerciseRecord): void }>()

/* ---------- 列表 ---------- */
const listLoading = ref(false)
const records = ref<ExerciseRecord[]>([])
const items = ref<ExerciseItem[]>([])
const weightKg = ref<number | null>(null)
const total = ref(0)
const page = ref(1)
const size = ref(15)
const dateRange = ref<[string, string] | null>(null)

function itemName(id: number) {
  return items.value.find((i) => i.id === id)?.name ?? '动作'
}
function itemOf(r: ExerciseRecord) {
  return items.value.find((i) => i.id === r.exerciseId)
}

/** 计算一条记录的净消耗（前端公式；优先用记录时体重快照，历史消耗固定） */
function calcNet(r: ExerciseRecord): number {
  const w = r.bodyWeight ?? weightKg.value
  if (!w) return 0
  const item = itemOf(r)
  if (!item) return 0
  let met = item.baseMet
  let minutes = 0
  if ((item.type === 'strength' || item.type === 'cardio') && r.reps) {
    // v2 模型：速度→MET 强度 + 等效分钟总量
    minutes = repsEffectiveMinutes(r.reps, item.refSpeed)
    met = speedMet(item.baseMet, r.reps, totalSeconds(r.minutes, r.seconds), item.refSpeed, item.maxSpeed)
  } else if ((item.type === 'walk' || item.type === 'cycling') && r.distance && r.minutes) {
    const kmh = walkSpeedKmh(Number(r.distance), Number(r.minutes))
    met = (item.type === 'cycling' ? cyclingMet(kmh) : walkMet(kmh)).met
    minutes = Number(r.minutes)
  } else if (item.type === 'stairs' && r.floors && r.times) {
    // 秒/层 → MET 档（快爬 8.8 ~ 慢爬 4.2）
    const secTotal = totalSeconds(r.minutes, r.seconds)
    met = stairsMet(Number(r.floors), Number(r.times), secTotal).met
    minutes = secTotal / 60
  } else if (item.type === 'plank' && r.seconds) {
    minutes = r.seconds / 60
  }
  return calcKcal(met, minutes, w).net
}
function calcTotal(r: ExerciseRecord): number {
  const w = r.bodyWeight ?? weightKg.value
  if (!w) return 0
  const item = itemOf(r)
  if (!item) return 0
  let met = item.baseMet
  let minutes = 0
  if ((item.type === 'strength' || item.type === 'cardio') && r.reps) {
    // v2 模型：速度→MET 强度 + 等效分钟总量
    minutes = repsEffectiveMinutes(r.reps, item.refSpeed)
    met = speedMet(item.baseMet, r.reps, totalSeconds(r.minutes, r.seconds), item.refSpeed, item.maxSpeed)
  } else if ((item.type === 'walk' || item.type === 'cycling') && r.distance && r.minutes) {
    const kmh = walkSpeedKmh(Number(r.distance), Number(r.minutes))
    met = (item.type === 'cycling' ? cyclingMet(kmh) : walkMet(kmh)).met
    minutes = Number(r.minutes)
  } else if (item.type === 'stairs' && r.floors && r.times) {
    // 秒/层 → MET 档（快爬 8.8 ~ 慢爬 4.2）
    const secTotal = totalSeconds(r.minutes, r.seconds)
    met = stairsMet(Number(r.floors), Number(r.times), secTotal).met
    minutes = secTotal / 60
  } else if (item.type === 'plank' && r.seconds) {
    minutes = r.seconds / 60
  }
  return calcKcal(met, minutes, w).total
}

/** 参数摘要文本 */
function paramText(r: ExerciseRecord) {
  const item = itemOf(r)
  if (!item) return ''
  switch (item.type) {
    case 'strength':
    case 'cardio':
      return `${r.weight != null ? r.weight + 'kg × ' : ''}${r.reps}个 · ${formatDuration(totalSeconds(r.minutes, r.seconds))}`
    case 'walk':
    case 'cycling':
      return `${r.distance}km · ${r.minutes}min`
    case 'stairs':
      return `${r.floors}层 × ${r.times}次 · ${formatDuration(totalSeconds(r.minutes, r.seconds))}`
    case 'plank':
      return `${r.seconds}秒`
    default:
      return ''
  }
}
function handText(h: string | null) {
  if (!h || h === 'both') return '双'
  return h === 'left' ? '左' : '右'
}
function handTone(h: string | null) {
  return h === 'both' ? '' : 'is-hand'
}

const columns: DataListColumn[] = [
  { key: 'date', label: '日期', width: 'calc(6.8 * var(--sk-font-md) + 2 * var(--sk-space-3) + 4px)' },
  { key: 'action', label: '动作', ratio: 2 },
  { key: 'param', label: '参数', ratio: 3 },
  { key: 'kcal', label: '净消耗', width: 'calc(5.2 * var(--sk-font-md) + 2 * var(--sk-space-3) + 4px)' },
  { key: 'ops', label: '操作', width: 96, ops: true }
]

/** silent=true 后台静默刷新（保存/删除后的 tick 联动），不显示加载遮罩防闪烁 */
async function loadList(silent = false) {
  if (!silent) listLoading.value = true
  try {
    const [res, itms, wres] = await Promise.all([
      listExerciseRecords({
        startDate: dateRange.value?.[0],
        endDate: dateRange.value?.[1],
        page: page.value,
        size: size.value
      }),
      listExerciseItems(),
      listWeightRecords({ page: 1, size: 1 })
    ])
    records.value = res.records
    total.value = res.total
    items.value = itms
    weightKg.value = wres.records?.[0]?.weight != null ? Number(wres.records[0].weight) : null
  } finally {
    if (!silent) listLoading.value = false
  }
}
function onFilterChange() {
  page.value = 1
  loadList()
}

async function onDelete(r: ExerciseRecord) {
  await confirmDelete(`确定删除 ${itemName(r.exerciseId)} 的这条锻炼记录吗？`, () => deleteExerciseRecord(r.id))
  emit('changed')
}

/* ---------- 修改弹窗 ---------- */
const editVisible = ref(false)
const editSaving = ref(false)
const editForm = ref<{ id: number; exerciseId: number; weight: string; reps: string; min: string; sec: string; minutes: string; distance: string; floors: string; times: string; seconds: string; hand: string; note: string; recordDate: string }>({
  id: 0, exerciseId: 0, weight: '', reps: '', min: '', sec: '', minutes: '', distance: '',
  floors: '', times: '', seconds: '', hand: 'left', note: '', recordDate: ''
})
const editItem = computed(() => items.value.find((i) => i.id === editForm.value.exerciseId) ?? null)

function openEdit(r: ExerciseRecord) {
  const item = items.value.find((i) => i.id === r.exerciseId)
  // strength/stairs：时长拆成分+秒
  const totalSec = totalSeconds(r.minutes, r.seconds)
  const needSplit = item?.type === 'strength' || item?.type === 'cardio' || item?.type === 'stairs'
  editForm.value = {
    id: r.id,
    exerciseId: r.exerciseId,
    weight: r.weight != null ? String(r.weight) : '',
    reps: r.reps != null ? String(r.reps) : '',
    min: needSplit ? String(Math.floor(totalSec / 60) || '') : '',
    sec: needSplit ? String(totalSec % 60 || '') : '',
    minutes: r.minutes != null ? String(r.minutes) : '',
    distance: r.distance != null ? String(r.distance) : '',
    floors: r.floors != null ? String(r.floors) : '',
    times: r.times != null ? String(r.times) : '',
    seconds: r.seconds != null ? String(r.seconds) : '',
    hand: r.hand ?? 'left',
    note: r.note ?? '',
    recordDate: r.recordDate
  }
  editVisible.value = true
}

async function onEditSave() {
  const item = editItem.value
  if (!item) return
  editSaving.value = true
  try {
    // strength/stairs 时长：分钟+秒 → 总秒数
    const timeSec = (item.type === 'strength' || item.type === 'cardio' || item.type === 'stairs')
      ? Math.round((Number(editForm.value.min) || 0) * 60 + (Number(editForm.value.sec) || 0))
      : 0
    await updateExerciseRecord(editForm.value.id, {
      exerciseId: editForm.value.exerciseId,
      recordDate: editForm.value.recordDate,
      weight: item.hasWeight && editForm.value.weight ? Number(editForm.value.weight) : null,
      reps: item.type === 'strength' || item.type === 'cardio' ? Number(editForm.value.reps) : null,
      minutes: item.type === 'walk' || item.type === 'cycling' ? Number(editForm.value.minutes) : null,
      distance: item.type === 'walk' || item.type === 'cycling' ? Number(editForm.value.distance) : null,
      floors: item.type === 'stairs' ? Number(editForm.value.floors) : null,
      times: item.type === 'stairs' ? Number(editForm.value.times) : null,
      seconds: item.type === 'strength' || item.type === 'cardio' || item.type === 'stairs'
        ? timeSec
        : item.type === 'plank' ? Number(editForm.value.seconds) : null,
      hand: item.hasHand ? editForm.value.hand : null,
      note: editForm.value.note.trim()
    })
    ElMessage.success('修改成功')
    editVisible.value = false
    emit('changed')
  } finally {
    editSaving.value = false
  }
}

/* ---------- 行点击详情（只读） ---------- */
const detail = ref<ExerciseRecord | null>(null)
const dialogVisible = computed(() => detail.value !== null)
function onRowClick(r: ExerciseRecord) {
  detail.value = r
}
function onDialogClose() {
  detail.value = null
}

/** 详情弹窗行（手为可选行） */
const detailRows = computed<DetailRow[]>(() => {
  const d = detail.value
  if (!d) return []
  return [
    { key: 'date', label: '日期', value: d.recordDate, mono: true },
    { key: 'item', label: '动作', value: itemName(d.exerciseId) },
    { key: 'param', label: '参数', value: paramText(d), mono: true },
    ...(d.hand ? [{ key: 'hand', label: '手', value: handText(d.hand) }] : []),
    { key: 'kcal', label: '净消耗', value: `${calcNet(d)} kcal（总 ${calcTotal(d)}）`, mono: true },
    { key: 'note', label: '备注', value: d.note || '—', wide: true }
  ]
})

onMounted(loadList)
watch(() => props.tick, () => loadList(true))
</script>

<template>
  <section class="card exh">
    <BlockTitle title="锻炼历史" :hint="!listLoading ? `共 ${total} 条` : undefined" />

    <div class="exh__bar">
      <DateRangePicker v-model="dateRange" @change="onFilterChange" />
      <button v-if="dateRange" class="exh__link" type="button" @click="dateRange = null; onFilterChange()">× 清除筛选</button>
    </div>

    <template v-if="records.length">
      <DataList :items="records" :columns="columns" :max-rows="size" clickable :card-below="560" @row-click="onRowClick">
        <template #cell="{ item, column }">
          <span v-if="column.key === 'date'" class="num">{{ (item as ExerciseRecord).recordDate }}</span>
          <span v-else-if="column.key === 'action'" class="exh__action">{{ itemName((item as ExerciseRecord).exerciseId) }}</span>
          <span v-else-if="column.key === 'param'" class="exh__param num">
            {{ paramText(item as ExerciseRecord) }}
            <span v-if="(item as ExerciseRecord).hand" class="exh__hand" :class="handTone((item as ExerciseRecord).hand)">{{ handText((item as ExerciseRecord).hand) }}</span>
          </span>
          <span v-else-if="column.key === 'kcal'" class="exh__kcal num">
            {{ calcNet(item as ExerciseRecord) }}<small>总 {{ calcTotal(item as ExerciseRecord) }}</small>
          </span>
          <span v-else-if="column.key === 'ops'">
            <el-tooltip content="复制到表单" placement="top">
              <button class="exh__op" type="button" @click.stop="emit('copy', item as ExerciseRecord)"><component :is="markRaw(Copy)" :size="14" /></button>
            </el-tooltip>
            <el-tooltip content="修改" placement="top">
              <button class="exh__op" type="button" @click.stop="openEdit(item as ExerciseRecord)"><component :is="markRaw(Pencil)" :size="14" /></button>
            </el-tooltip>
            <el-tooltip content="删除" placement="top">
              <button class="exh__op exh__op--del" type="button" @click.stop="onDelete(item as ExerciseRecord)"><component :is="markRaw(Trash2)" :size="14" /></button>
            </el-tooltip>
          </span>
        </template>
      </DataList>
    </template>
    <p v-else class="exh__empty">暂无锻炼记录</p>

    <div class="exh__pager">
      <PagePager v-model:current-page="page" v-model:page-size="size" :total="total" @change="loadList" />
    </div>
    <LoadingMask :show="listLoading" :size="26" text="加载记录…" />
  </section>

  <!-- 修改弹窗 -->
  <el-dialog v-model="editVisible" title="修改锻炼记录" width="420px">
    <div class="exh__edit">
      <div class="exh__field">
        <label>动作（可输入搜索）</label>
        <el-select v-model="editForm.exerciseId" filterable placeholder="搜索或选择动作">
          <el-option v-for="it in items" :key="it.id" :value="it.id" :label="it.name" />
        </el-select>
      </div>
      <template v-if="editItem?.type === 'strength' || editItem?.type === 'cardio'">
        <div class="exh__optgrid">
          <div class="exh__field">
            <label>重量 (kg，自重留空)</label>
            <input v-model="editForm.weight" class="num" inputmode="decimal" />
          </div>
          <div class="exh__field">
            <label>个数</label>
            <input v-model="editForm.reps" class="num" inputmode="numeric" />
          </div>
        </div>
        <div class="exh__optgrid">
          <div class="exh__field">
            <label>分钟</label>
            <input v-model="editForm.min" class="num" inputmode="numeric" />
          </div>
          <div class="exh__field">
            <label>秒（可选）</label>
            <input v-model="editForm.sec" class="num" inputmode="numeric" />
          </div>
          <div v-if="editItem.hasHand" class="exh__field">
            <label>手</label>
            <select v-model="editForm.hand" class="num">
              <option value="left">左</option>
              <option value="right">右</option>
              <option value="both">双</option>
            </select>
          </div>
        </div>
      </template>
      <div v-else-if="editItem?.type === 'walk' || editItem?.type === 'cycling'" class="exh__optgrid">
        <div class="exh__field">
          <label>距离 (km)</label>
          <input v-model="editForm.distance" class="num" inputmode="decimal" />
        </div>
        <div class="exh__field">
          <label>分钟</label>
          <input v-model="editForm.minutes" class="num" inputmode="numeric" />
        </div>
      </div>
      <div v-else-if="editItem?.type === 'stairs'" class="exh__optgrid">
        <div class="exh__field">
          <label>层数</label>
          <input v-model="editForm.floors" class="num" inputmode="numeric" />
        </div>
        <div class="exh__field">
          <label>次数</label>
          <input v-model="editForm.times" class="num" inputmode="numeric" />
        </div>
        <div class="exh__field">
          <label>分钟</label>
          <input v-model="editForm.min" class="num" inputmode="numeric" />
        </div>
        <div class="exh__field">
          <label>秒（可选）</label>
          <input v-model="editForm.sec" class="num" inputmode="numeric" />
        </div>
      </div>
      <div v-else-if="editItem?.type === 'plank'" class="exh__field">
        <label>秒数</label>
        <input v-model="editForm.seconds" class="num" inputmode="numeric" />
      </div>
      <div class="exh__field">
        <label>日期</label>
        <input v-model="editForm.recordDate" type="date" class="num" />
      </div>
      <div class="exh__field">
        <label>备注</label>
        <input v-model="editForm.note" maxlength="60" />
      </div>
    </div>
    <template #footer>
      <el-button @click="editVisible = false">取消</el-button>
      <el-button type="primary" :loading="editSaving" @click="onEditSave">保存</el-button>
    </template>
  </el-dialog>

  <!-- 行详情（只读） -->
  <RecordDetailDialog :model-value="dialogVisible" title="锻炼记录详情" width="420px" :rows="detailRows" @update:model-value="onDialogClose" />
</template>

<style lang="scss" scoped>
@use '../exercise';
</style>
