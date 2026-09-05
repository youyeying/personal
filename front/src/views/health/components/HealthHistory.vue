<script setup lang="ts">
/**
 * 健康 · 历史子页组件
 * 日期范围筛选 + 分页列表 + 修改弹窗 + 删除
 * 数据变更时 emit('changed') → 父级递增 tick，联动 打卡/趋势 刷新
 */
import { computed, markRaw, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Pencil, Trash2 } from '@lucide/vue'
import {
  listWeightRecords,
  updateWeightRecord,
  deleteWeightRecord
} from '@/api/health'
import type { WeightRecord } from '@/api/health'
import { formatShortTime } from '@/utils/format'
import { confirmDelete } from '@/utils/confirm'
import PagePager from '@/components/PagePager/PagePager.vue'
import LoadingMask from '@/components/LoadingMask/LoadingMask.vue'
import BlockTitle from '@/components/BlockTitle/BlockTitle.vue'
import DateRangePicker from '@/components/DateRangePicker/DateRangePicker.vue'
import DataList from '@/components/DataList/DataList.vue'
import RecordDetailDialog from '@/components/RecordDetailDialog/RecordDetailDialog.vue'
import type { DetailRow } from '@/components/RecordDetailDialog/RecordDetailDialog.vue'
import type { DataListColumn } from '@/components/DataList/DataList.vue'

const props = defineProps<{
  tick: number
}>()

const emit = defineEmits<{
  (e: 'changed'): void
}>()

/* ---------- 列表 ---------- */
const listLoading = ref(false)
const records = ref<WeightRecord[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(15)
/** 日期范围筛选（服务端过滤） */
const dateRange = ref<[string, string] | null>(null)

/** 列定义：日期/体重/体脂腰围备注(自适应)/操作 */
const columns: DataListColumn[] = [
  // 日期/体重同为 mono 定宽（随 --sk-font-md 缩放），避免大屏字号下被省略号截断
  { key: 'date', label: '日期', width: 'calc(6.8 * var(--sk-font-md) + 2 * var(--sk-space-3) + 4px)' },
  { key: 'weight', label: '体重', width: 'calc(4.8 * var(--sk-font-md) + 2 * var(--sk-space-3) + 4px)' },
  { key: 'extra', label: '体脂 / 腰围 / 备注', ratio: 5 },
  { key: 'ops', label: '操作', width: 88, ops: true }
]

/** silent=true 后台静默刷新（编辑/删除后的 tick 联动），不显示加载遮罩防闪烁 */
async function loadList(silent = false) {
  if (!silent) listLoading.value = true
  try {
    const res = await listWeightRecords({
      startDate: dateRange.value?.[0],
      endDate: dateRange.value?.[1],
      page: page.value,
      size: size.value
    })
    records.value = res.records
    total.value = res.total
  } finally {
    if (!silent) listLoading.value = false
  }
}

function onFilterChange() {
  page.value = 1
  loadList()
}

async function onDelete(row: WeightRecord) {
  await confirmDelete(`确定删除 ${row.recordDate} 的记录（${row.weight}kg）吗？`, () => deleteWeightRecord(row.id))
  emit('changed')
}

/* ---------- 行点击详情（只读） ---------- */
const detail = ref<WeightRecord | null>(null)
const dialogVisible = computed(() => detail.value !== null)
function onRowClick(item: WeightRecord) {
  detail.value = item
}
function onDialogClose() {
  detail.value = null
}

/** 详情弹窗行 */
const detailRows = computed<DetailRow[]>(() => {
  const d = detail.value
  if (!d) return []
  return [
    { key: 'date', label: '日期', value: d.recordDate, mono: true },
    { key: 'weight', label: '体重', value: `${d.weight} kg`, mono: true },
    { key: 'bodyFat', label: '体脂率', value: d.bodyFat != null ? `${d.bodyFat}%` : '未记录', mono: true },
    { key: 'waist', label: '腰围', value: d.waist != null ? `${d.waist} cm` : '未记录', mono: true },
    { key: 'time', label: '记录时间', value: formatShortTime(d.createdAt), mono: true },
    { key: 'note', label: '备注', value: d.note || '—', wide: true }
  ]
})

/* ---------- 修改弹窗 ---------- */
const editVisible = ref(false)
const editSaving = ref(false)
const editForm = ref<{ id: number; weight: string; bodyFat: string; waist: string; note: string; recordDate: string }>({
  id: 0, weight: '', bodyFat: '', waist: '', note: '', recordDate: ''
})

function openEdit(row: WeightRecord) {
  editForm.value = {
    id: row.id,
    weight: String(row.weight),
    bodyFat: row.bodyFat != null ? String(row.bodyFat) : '',
    waist: row.waist != null ? String(row.waist) : '',
    note: row.note ?? '',
    recordDate: row.recordDate
  }
  editVisible.value = true
}

async function onEditSave() {
  const val = parseFloat(editForm.value.weight)
  if (!val || val <= 0) {
    ElMessage.warning('请输入体重')
    return
  }
  const fat = editForm.value.bodyFat ? parseFloat(editForm.value.bodyFat) : null
  if (fat != null && (fat < 0 || fat > 100)) {
    ElMessage.warning('体脂率应在 0-100 之间')
    return
  }
  const wa = editForm.value.waist ? parseFloat(editForm.value.waist) : null
  if (wa != null && wa <= 0) {
    ElMessage.warning('腰围需大于 0')
    return
  }
  editSaving.value = true
  try {
    await updateWeightRecord(editForm.value.id, {
      weight: val,
      bodyFat: fat,
      waist: wa,
      note: editForm.value.note.trim(),
      recordDate: editForm.value.recordDate
    })
    ElMessage.success('修改成功')
    editVisible.value = false
    emit('changed')
  } finally {
    editSaving.value = false
  }
}

onMounted(loadList)
// 任一子页数据变更 → 重载列表
watch(() => props.tick, () => loadList(true))
</script>

<template>
  <section class="card hl__listcard">
    <BlockTitle title="历史记录" :hint="!listLoading ? `共 ${total} 条` : undefined" />

    <!-- 筛选栏 -->
    <div class="hl__listbar">
      <DateRangePicker v-model="dateRange" @change="onFilterChange" />
      <button v-if="dateRange" class="hl__link" type="button" @click="dateRange = null; onFilterChange()">× 清除筛选</button>
    </div>

    <!-- 列表 -->
    <template v-if="records.length">
      <DataList :items="records" :columns="columns" :max-rows="size" :card-below="560" clickable @row-click="onRowClick">
        <template #cell="{ item, column }">
          <span v-if="column.key === 'date'" class="num">{{ item.recordDate }}</span>
          <span v-else-if="column.key === 'weight'" class="hl__c-weight num">{{ item.weight }}<i>kg</i></span>
          <span v-else-if="column.key === 'extra'" class="hl__c-extra">
            <span class="num">{{ item.bodyFat != null ? item.bodyFat + '%' : '-.-' }}</span>
            <span class="num">{{ item.waist != null ? item.waist + 'cm' : '--' }}</span>
            <span class="hl__c-note">{{ item.note || '-' }}</span>
          </span>
          <span v-else-if="column.key === 'ops'">
            <el-tooltip content="修改" placement="top">
              <button class="hl__op" type="button" @click.stop="openEdit(item)"><component :is="markRaw(Pencil)" :size="14" /></button>
            </el-tooltip>
            <el-tooltip content="删除" placement="top">
              <button class="hl__op hl__op--del" type="button" @click.stop="onDelete(item)"><component :is="markRaw(Trash2)" :size="14" /></button>
            </el-tooltip>
          </span>
        </template>
      </DataList>
    </template>
    <p v-else class="hl__empty">暂无记录</p>

    <div class="hl__pager">
      <PagePager
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        @change="loadList"
      />
    </div>
    <LoadingMask :show="listLoading" :size="26" text="加载记录…" />
  </section>

  <!-- 修改弹窗 -->
  <el-dialog v-model="editVisible" title="修改体重记录" width="420px">
    <div class="hl__edit">
      <div class="hl__field">
        <label class="hl__field-label">体重 (kg) *</label>
        <el-input v-model="editForm.weight" inputmode="decimal" />
      </div>
      <div class="hl__optgrid">
        <div class="hl__field">
          <label class="hl__field-label">体脂率 (%)</label>
          <el-input v-model="editForm.bodyFat" inputmode="decimal" />
        </div>
        <div class="hl__field">
          <label class="hl__field-label">腰围 (cm)</label>
          <el-input v-model="editForm.waist" inputmode="decimal" />
        </div>
      </div>
      <div class="hl__field">
        <label class="hl__field-label">日期</label>
        <el-date-picker v-model="editForm.recordDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
      </div>
      <div class="hl__field">
        <label class="hl__field-label">备注</label>
        <el-input v-model="editForm.note" maxlength="60" />
      </div>
    </div>
    <template #footer>
      <el-button @click="editVisible = false">取消</el-button>
      <el-button type="primary" :loading="editSaving" @click="onEditSave">保存</el-button>
    </template>
  </el-dialog>

  <!-- 行详情（只读） -->
  <RecordDetailDialog :model-value="dialogVisible" title="体重记录详情" width="420px" :rows="detailRows" @update:model-value="onDialogClose" />
</template>

<style lang="scss" scoped>
@use '../health';
</style>