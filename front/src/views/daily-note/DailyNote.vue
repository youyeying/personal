<script setup lang="ts">
/**
 * 每日总结 · 组合层（班期日历 + 当日详情 + 班表编辑）
 * - 持有：班期日期/班表/总结数据、选中日期、汇总、班表草稿、逻辑与加载
 * - 子组件（components/）：DailyCalendar(日历) / DailySummaryCard(小汇总) /
 *   DailyEdit(心情+小结) / DailyTimeline(时间线) / ShiftEditorDialog(班表编辑弹窗)
 * - 班期按「每月21日 → 次月20日」，可切换上一/下一班期
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowLeft, ArrowRight, Calendar } from '@element-plus/icons-vue'
import {
  saveDailyNote,
  listDailyNotes,
  getDailySummary
} from '@/api/dailyNote'
import type { DailyNote, DailySummary } from '@/api/dailyNote'
import { getShifts, saveShiftsBatch, parseShiftFile } from '@/api/shift'
import { formatDate } from '@/utils/format'
import LoadingMask from '@/components/LoadingMask/LoadingMask.vue'
import { cycleStartOf, cycleEndOf } from './dailyNoteShared'
import DailyCalendar from './components/DailyCalendar.vue'
import DailySummaryCard from './components/DailySummaryCard.vue'
import DailyEdit from './components/DailyEdit.vue'
import DailyTimeline from './components/DailyTimeline.vue'
import ShiftEditorDialog, { type ShiftDraftRow } from './components/ShiftEditorDialog.vue'

const loading = ref(false)
const cycleStart = ref<Date>(cycleStartOf(new Date()))
/** 班期 30 余天日期串（21号 → 次月20号，含首尾，跨月正确） */
const cycleDays = computed(() => {
  const list: string[] = []
  const cur = new Date(cycleStart.value)
  const end = cycleEndOf(cycleStart.value)
  while (cur <= end) {
    list.push(formatDate(cur))
    cur.setDate(cur.getDate() + 1)
  }
  return list
})

/** 班期数据：日期 → 班次 */
const shiftMap = ref<Record<string, string>>({})
/** 班期数据：日期 → 总结 */
const noteMap = ref<Record<string, DailyNote>>({})
/** 选中的日期 */
const selectedDate = ref('')
const summary = ref<DailySummary | null>(null)
/** 保存小结 loading */
const saving = ref(false)

/** 班表编辑弹窗 */
const shiftDialog = ref(false)
const shiftDraft = ref<ShiftDraftRow[]>([])
const shiftSaving = ref(false)

/** 班期标签 */
const cycleLabel = computed(() => {
  const end = cycleEndOf(cycleStart.value)
  return `${formatDate(cycleStart.value).slice(5)} ~ ${formatDate(end).slice(5)}`
})

/** 切换班期 */
function shiftCycle(offset: number) {
  const t = new Date(cycleStart.value)
  t.setMonth(t.getMonth() + offset)
  cycleStart.value = cycleStartOf(t)
  selectedDate.value = ''
  loadCycle()
}

/** 加载整个班期：总结列表 + 班表；silent=true 后台静默刷新（保存后联动，不显示遮罩防闪烁） */
async function loadCycle(silent = false) {
  if (!silent) loading.value = true
  try {
    const end = cycleEndOf(cycleStart.value)
    const [notes, shifts] = await Promise.all([
      listDailyNotes(formatDate(cycleStart.value), formatDate(end)),
      getShifts(formatDate(cycleStart.value), formatDate(end))
    ])
    const nm: Record<string, DailyNote> = {}
    for (const n of notes) nm[n.noteDate] = n
    noteMap.value = nm
    const sm: Record<string, string> = {}
    for (const s of shifts) sm[s.shiftDate] = s.shiftName
    shiftMap.value = sm
    // 默认选中：今天（若在本班期）否则班期首日
    const today = formatDate(new Date())
    selectDate(cycleDays.value.includes(today) ? today : cycleDays.value[0])
  } finally {
    if (!silent) loading.value = false
  }
}

/** 选中某天：加载当日小汇总 */
async function selectDate(date: string) {
  selectedDate.value = date
  summary.value = null
  try {
    summary.value = await getDailySummary(date)
  } catch {
    summary.value = null
  }
}

/** 保存当日总结（payload 来自 DailyEdit 的心情 + 小结） */
async function onSave(payload: { mood: string; content: string }) {
  if (!payload.mood && !payload.content.trim()) {
    ElMessage.warning('心情或内容至少填写一项')
    return
  }
  saving.value = true
  try {
    await saveDailyNote(selectedDate.value, {
      mood: payload.mood || undefined,
      content: payload.content.trim() || undefined
    })
    ElMessage.success('已保存')
    await loadCycle(true)
  } finally {
    saving.value = false
  }
}

/** 打开班表编辑：用当前班期数据预填 */
function openShiftEditor() {
  shiftDraft.value = cycleDays.value.map((d) => ({ date: d, shiftName: shiftMap.value[d] ?? '休息' }))
  shiftDialog.value = true
}

/** 导入班表文件：解析 xlsx 后按日期合并进 shiftDraft */
async function handleImportShiftFile(raw: File) {
  try {
    const items = await parseShiftFile(raw)
    if (!items.length) {
      ElMessage.warning('未能从文件解析到班表明细')
      return
    }
    const byDate = new Map(items.map((i) => [i.date, i.shiftName]))
    // 文件命中的日期用导入值，未命中的日期保留原有草稿
    shiftDraft.value = cycleDays.value.map((d) => {
      const exist = shiftDraft.value.find((r) => r.date === d)
      return { date: d, shiftName: byDate.get(d) ?? exist?.shiftName ?? '休息' }
    })
    ElMessage.success(`已导入 ${items.length} 天班表`)
  } catch {
    ElMessage.error('班表文件解析失败，请检查格式（xlsx）')
  }
}

/** 保存整个班期班表 */
async function onSaveShifts() {
  const end = cycleEndOf(cycleStart.value)
  shiftSaving.value = true
  try {
    await saveShiftsBatch({
      startDate: formatDate(cycleStart.value),
      endDate: formatDate(end),
      shifts: shiftDraft.value.map((r) => ({ date: r.date, shiftName: r.shiftName }))
    })
    ElMessage.success('班表已保存')
    shiftDialog.value = false
    await loadCycle(true)
  } finally {
    shiftSaving.value = false
  }
}

/** 时间线条目（倒序） */
const timeline = computed(() =>
  Object.values(noteMap.value)
    .slice()
    .sort((a, b) => (a.noteDate < b.noteDate ? 1 : -1))
)

/* ---- 班期日历列数自适应（按容器实际宽度 7→6→5→4→3） ---- */
const rootEl = ref<HTMLElement | null>(null)
const cols = ref(7)
function calcCols(width: number): number {
  if (width >= 1040) return 7
  if (width >= 900) return 6
  if (width >= 760) return 5
  if (width >= 620) return 4
  return 3
}
function updateCols() {
  if (rootEl.value) cols.value = calcCols(rootEl.value.clientWidth)
}
let colsObserver: ResizeObserver | null = null
onMounted(() => {
  updateCols()
  if (typeof ResizeObserver !== 'undefined' && rootEl.value) {
    colsObserver = new ResizeObserver(() => updateCols())
    colsObserver.observe(rootEl.value)
  }
  loadCycle()
})
onBeforeUnmount(() => colsObserver?.disconnect())
/** 日历网格列数样式（表头与日期共用列数） */
const columnsTemplate = computed(() => `repeat(${cols.value}, 1fr)`)
</script>

<template>
  <div ref="rootEl" class="daily-note">
    <!-- 班期工具条 -->
    <section class="card daily-note__toolbar">
      <button class="daily-note__nav" type="button" @click="shiftCycle(-1)">
        <el-icon><ArrowLeft /></el-icon>
      </button>
      <div class="daily-note__cycle">
        <el-icon class="daily-note__cycle-icon"><Calendar /></el-icon>
        <span>班期 {{ cycleLabel }}</span>
      </div>
      <button class="daily-note__nav" type="button" @click="shiftCycle(1)">
        <el-icon><ArrowRight /></el-icon>
      </button>
      <el-button type="primary" style="margin-left: auto" @click="openShiftEditor">编辑班表</el-button>
    </section>

    <div class="daily-note__body">
      <!-- 不透明加载遮罩 -->
      <LoadingMask :show="loading" :size="32" text="加载班期数据…" />

      <!-- 左：班期日历 -->
      <DailyCalendar
        :cycle-days="cycleDays"
        :columns-template="columnsTemplate"
        :note-map="noteMap"
        :shift-map="shiftMap"
        :selected-date="selectedDate"
        @select="selectDate"
      />

      <!-- 右：当日详情 -->
      <section class="daily-note__detail">
        <!-- 当日小汇总 -->
        <DailySummaryCard
          :date="selectedDate"
          :mood="noteMap[selectedDate]?.mood ?? null"
          :summary="summary"
        />

        <!-- 心情 + 小结编辑 -->
        <DailyEdit
          :date="selectedDate"
          :saving="saving"
          :today-shift="shiftMap[selectedDate] ?? null"
          :note-mood="noteMap[selectedDate]?.mood ?? ''"
          :note-content="noteMap[selectedDate]?.content ?? ''"
          @save="onSave"
        />

        <!-- 时间线 -->
        <DailyTimeline :timeline="timeline" :selected-date="selectedDate" @select="selectDate" />
      </section>
    </div>

    <!-- 班表编辑弹窗 -->
    <ShiftEditorDialog
      :show="shiftDialog"
      :cycle-label="cycleLabel"
      :draft="shiftDraft"
      :saving="shiftSaving"
      @update:show="(v: boolean) => shiftDialog = v"
      @import="handleImportShiftFile"
      @save="onSaveShifts"
    />
  </div>
</template>

<style lang="scss" scoped>
@use './dailyNote';
</style>