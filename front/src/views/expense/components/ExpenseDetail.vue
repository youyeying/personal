<script setup lang="ts">
/**
 * 记账 · 明细子页组件
 * 类型/分类/日期筛选 + 按日分组列表 + 分页 + 修改/删除
 * - props.jumpDate 从概览趋势点柱子联动到当天
 * - 数据变更时 emit('changed') → 父级递增 tick，联动 记一笔/概览 刷新
 */
import { computed, markRaw, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Pencil, Trash2 } from '@lucide/vue'
import {
  listExpenseRecords,
  updateExpenseRecord,
  deleteExpenseRecord
} from '@/api/expense'
import type { ExpenseCategory, ExpenseRecord } from '@/api/expense'
import { formatShortTime, formatMoney } from '@/utils/format'
import { catIcon } from '@/utils/category'
import { confirmDelete } from '@/utils/confirm'
import PagePager from '@/components/PagePager/PagePager.vue'
import LoadingMask from '@/components/LoadingMask/LoadingMask.vue'
import BlockTitle from '@/components/BlockTitle/BlockTitle.vue'

const props = defineProps<{
  /** 全部分类（父级加载，本组件与记一笔共享） */
  cats: ExpenseCategory[]
  tick: number
  /** 从概览趋势点柱子联动到当天（值即日期） */
  jumpDate: string
}>()

const emit = defineEmits<{
  (e: 'changed'): void
}>()

/* ---------- 列表 ---------- */
const listLoading = ref(false)
const records = ref<ExpenseRecord[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(30)
const filterType = ref<'' | '1' | '2'>('')
const filterCatId = ref<number | undefined>(undefined)
const detailDate = ref('')

/** silent=true 后台静默刷新（编辑/删除后的 tick 联动），不显示加载遮罩防闪烁 */
async function loadList(silent = false) {
  if (!silent) listLoading.value = true
  try {
    const res = await listExpenseRecords({
      type: filterType.value ? Number(filterType.value) : undefined,
      categoryId: filterCatId.value,
      startDate: detailDate.value || undefined,
      endDate: detailDate.value || undefined,
      page: page.value,
      size: size.value
    })
    records.value = res.records
    total.value = res.total
  } finally {
    if (!silent) listLoading.value = false
  }
}

function onListFilter() {
  page.value = 1
  loadList()
}

function onDateChange() {
  page.value = 1
  loadList()
}

function selectDetailDate(date: string) {
  detailDate.value = date
  onDateChange()
}

function clearDetailDate() {
  detailDate.value = ''
  onDateChange()
}

async function onDelete(row: ExpenseRecord) {
  await confirmDelete(
    `确定删除这条「${row.categoryName} ${formatMoney(row.amount)}」吗？`,
    () => deleteExpenseRecord(row.id)
  )
  emit('changed')
}

/* ---------- 修改弹窗 ---------- */
const editVisible = ref(false)
const editSaving = ref(false)
const editForm = ref<{ id: number; type: 1 | 2; categoryId: number; amount: string; note: string; recordDate: string }>({
  id: 0, type: 1, categoryId: 0, amount: '', note: '', recordDate: ''
})

/** 该记录类型对应的分类（用于弹窗内分类下拉，按 sortOrder 排序） */
const editCats = computed(() =>
  props.cats.filter((c) => c.type === editForm.value.type).sort((a, b) => a.sortOrder - b.sortOrder)
)

function openEdit(row: ExpenseRecord) {
  editForm.value = {
    id: row.id,
    type: (row.type as 1 | 2) ?? 1,
    categoryId: row.categoryId,
    amount: String(row.amount),
    note: row.note ?? '',
    recordDate: row.recordDate
  }
  editVisible.value = true
}

/** 弹窗内切换类型：重置为对应类型首个分类 */
function editSwitchType(t: 1 | 2) {
  editForm.value.type = t
  editForm.value.categoryId = props.cats.filter((c) => c.type === t).sort((a, b) => a.sortOrder - b.sortOrder)[0]?.id ?? 0
}

async function onEditSave() {
  const val = parseFloat(editForm.value.amount)
  if (!val || val <= 0) {
    ElMessage.warning('请输入金额')
    return
  }
  if (!editForm.value.categoryId) {
    ElMessage.warning('请选择分类')
    return
  }
  editSaving.value = true
  try {
    await updateExpenseRecord(editForm.value.id, {
      type: editForm.value.type,
      categoryId: editForm.value.categoryId,
      amount: val,
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

/** 明细按日分组（含当日小计） */
const grouped = computed(() => {
  const byDate = new Map<string, ExpenseRecord[]>()
  for (const r of records.value) {
    if (!byDate.has(r.recordDate)) byDate.set(r.recordDate, [])
    byDate.get(r.recordDate)!.push(r)
  }
  const list = Array.from(byDate, ([date, items]) => ({
    date,
    items,
    balance: items.reduce((s, r) => s + (r.type === 1 ? -r.amount : r.amount), 0)
  }))
  return list.sort((a, b) => b.date.localeCompare(a.date))
})

onMounted(loadList)
// 任一子页数据变更 → 重载列表
watch(() => props.tick, () => loadList(true))
// 从概览趋势联动到某天
watch(() => props.jumpDate, (d) => {
  if (d) selectDetailDate(d)
})
</script>

<template>
  <section class="card exp__listcard">
    <BlockTitle title="收支明细" :hint="!listLoading ? `共 ${total} 条` : undefined" />

    <!-- 筛选栏 -->
    <div class="exp__listbar">
      <el-select v-model="filterType" placeholder="全部类型" clearable style="width: 110px" @change="onListFilter">
        <el-option label="支出" value="1" /><el-option label="收入" value="2" />
      </el-select>
      <el-select v-model="filterCatId" placeholder="全部分类" clearable style="width: 120px" @change="onListFilter">
        <el-option v-for="c in cats" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <el-date-picker v-model="detailDate" type="date" value-format="YYYY-MM-DD" placeholder="按日期查看" style="width: 150px" @change="onDateChange" />
      <button v-if="detailDate" class="exp__link" type="button" @click="clearDetailDate">× 清除日期</button>
    </div>

    <p v-if="detailDate" class="exp__date-hint">正在查看：{{ detailDate }}</p>
    <!-- 按日分组的列表 -->
    <template v-if="records.length">
      <template v-for="g in grouped" :key="g.date">
        <div class="exp__daygrp">
          <div class="exp__day-head">
            <span class="exp__day-date num">{{ g.date }}</span>
            <span class="exp__day-sub num">{{ formatMoney(g.balance, true) }}</span>
          </div>
          <div v-for="r in g.items" :key="r.id" class="exp__row">
            <span class="exp__row-icon"><component :is="markRaw(catIcon(r.categoryName))" :size="16" /></span>
            <span class="exp__row-cat">{{ r.categoryName }}</span>
            <span class="exp__row-note">{{ r.note || '-' }}</span>
            <span class="exp__row-t num">{{ formatShortTime(r.createdAt) }}</span>
            <span class="exp__row-amt num" :class="r.type === 1 ? 'exp__row-amt--err' : 'exp__row-amt--ok'">{{ r.type === 1 ? '-' : '+' }}{{ formatMoney(r.amount) }}</span>
            <el-tooltip content="修改" placement="top">
              <button class="exp__op" type="button" aria-label="修改" @click="openEdit(r)"><component :is="markRaw(Pencil)" :size="14" /></button>
            </el-tooltip>
            <el-tooltip content="删除" placement="top">
              <button class="exp__op exp__op--del" type="button" aria-label="删除" @click="onDelete(r)"><component :is="markRaw(Trash2)" :size="14" /></button>
            </el-tooltip>
          </div>
        </div>
      </template>
    </template>
    <p v-else class="exp__empty">还没有记录，先到「记一笔」记录吧</p>

    <div class="exp__pager">
      <PagePager
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        @change="loadList"
      />
    </div>
    <LoadingMask :show="listLoading" :size="26" text="加载明细…" />
  </section>

  <!-- 修改弹窗 -->
  <el-dialog v-model="editVisible" title="修改记账" width="420px">
    <div class="exp__edit">
      <div class="exp__seg">
        <button class="exp__seg-btn" :class="{ 'is-active': editForm.type === 1 }" type="button" @click="editSwitchType(1)">支出</button>
        <button class="exp__seg-btn" :class="{ 'is-active': editForm.type === 2 }" type="button" @click="editSwitchType(2)">收入</button>
      </div>
      <div class="exp__field">
        <label class="exp__field-label">分类</label>
        <el-select v-model="editForm.categoryId" placeholder="请选择分类" style="width: 100%">
          <el-option v-for="c in editCats" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
      </div>
      <div class="exp__field">
        <label class="exp__field-label">金额</label>
        <el-input v-model="editForm.amount" inputmode="decimal" placeholder="0.00" />
      </div>
      <div class="exp__field">
        <label class="exp__field-label">日期</label>
        <el-date-picker v-model="editForm.recordDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
      </div>
      <div class="exp__field">
        <label class="exp__field-label">备注</label>
        <el-input v-model="editForm.note" maxlength="40" placeholder="选填" />
      </div>
    </div>
    <template #footer>
      <el-button @click="editVisible = false">取消</el-button>
      <el-button type="primary" :loading="editSaving" @click="onEditSave">保存</el-button>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
@use '../expenseList';
</style>