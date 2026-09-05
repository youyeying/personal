<script setup lang="ts">
/**
 * 锻炼 · 消耗分析子页组件
 * - 每天实际消耗 = 1.2×BMR（久坐基准，需最新体重+体脂率快照）+ 当天锻炼净消耗
 * - 净消耗按「记录时体重快照」MET 公式前端计算（utils/exercise.ts），与分析口径一致
 * - 展示：区间汇总卡 + 每天锻炼净消耗柱状图 + 每天总实际消耗折线图（含基准线）+ 按天明细表
 */
import { computed, onMounted, ref, watch } from 'vue'
import { CalendarDays, ChartColumnBig } from '@lucide/vue'
import { listExerciseItems, listExerciseRecords } from '@/api/exercise'
import { listWeightRecords } from '@/api/health'
import { formatDate } from '@/utils/format'
import { calcBmr, SEDENTARY_FACTOR } from '@/utils/activity'
import { recordNetKcal } from '@/utils/exercise'
import { fetchAllRecords } from '@/utils/fetchAll'
import { fillDaysRange } from '@/utils/daysSeries'
import { useTrendChart, TREND_GRID, trendAxes } from '@/utils/useTrendChart'
import MetricCard from '@/components/MetricCard/MetricCard.vue'
import BlockTitle from '@/components/BlockTitle/BlockTitle.vue'
import EmptyState from '@/components/EmptyState/EmptyState.vue'
import LoadingMask from '@/components/LoadingMask/LoadingMask.vue'
import DateRangePicker from '@/components/DateRangePicker/DateRangePicker.vue'
import RecordHeatmap from '@/components/RecordHeatmap/RecordHeatmap.vue'
import type { HeatmapRow } from '@/components/RecordHeatmap/RecordHeatmap.vue'

const props = defineProps<{ tick: number; active: boolean }>()

/* ---------- 日期范围 ---------- */
const today = formatDate(new Date())
function daysAgo(n: number) {
  const d = new Date()
  d.setDate(d.getDate() - n)
  return formatDate(d)
}
/** 默认近 30 天；null 表示已清空（保持上次数据不重拉） */
const range = ref<[string, string] | null>([daysAgo(29), today])
const presets = [
  { label: '近7天', start: () => daysAgo(6) },
  { label: '近30天', start: () => daysAgo(29) },
  { label: '近90天', start: () => daysAgo(89) }
]

/** 每天一行（含动作明细），actual 为每天实际消耗 = 1.2BMR + 当天净消耗 */
interface DayRow {
  date: string
  net: number
  count: number
  items: string[]
  actual: number | null
}

/* ---------- 数据 ---------- */
const loading = ref(false)
const bmr = ref<number | null>(null)
const weightKg = ref<number | null>(null)
const days = ref<DayRow[]>([])

async function load(silent = false) {
  // 区间被清空（null）时不请求，保持上次数据
  if (!range.value) return
  if (!silent) loading.value = true
  try {
    const [start, end] = range.value
    const [recs, itms, wres] = await Promise.all([
      // 区间全量锻炼记录（后端每页上限 100，工具循环翻页取完）
      fetchAllRecords((page, size) => listExerciseRecords({ startDate: start, endDate: end, page, size })),
      listExerciseItems(),
      listWeightRecords({ page: 1, size: 1 })
    ])
    const w = wres.records?.[0]
    weightKg.value = w?.weight != null ? Number(w.weight) : null
    bmr.value = calcBmr(weightKg.value, w?.bodyFat != null ? Number(w.bodyFat) : null)

    // 按天聚合（净消耗按记录时体重快照，与统计页同口径）
    const map = new Map<string, DayRow>()
    for (const r of recs) {
      const item = itms.find((i) => i.id === r.exerciseId)
      if (!item) continue
      const net = recordNetKcal(r, item, weightKg.value)
      const row = map.get(r.recordDate) ?? { date: r.recordDate, net: 0, count: 0, items: [], actual: null }
      row.net += net
      row.count++
      row.items.push(`${item.name} +${net}`)
      map.set(r.recordDate, row)
    }
    // 补齐区间内每一天（含无记录日），供折线连续；actual 回退基准
    const base = bmr.value != null ? Math.round(bmr.value * SEDENTARY_FACTOR) : null
    const rows = fillDaysRange(start, end, map, (date) => ({ date, net: 0, count: 0, items: [], actual: null }))
    for (const row of rows) row.actual = base != null ? base + row.net : null
    days.value = rows
    renderCharts()
  } finally {
    if (!silent) loading.value = false
  }
}

/* ---------- 汇总 ---------- */
const summary = computed(() => {
  const net = days.value.reduce((s, d) => s + d.net, 0)
  const exerciseDays = days.value.filter((d) => d.count > 0).length
  const actuals = days.value.filter((d) => d.actual != null)
  const actualTotal = actuals.reduce((s, d) => s + (d.actual ?? 0), 0)
  const base = bmr.value != null ? Math.round(bmr.value * SEDENTARY_FACTOR) : null
  return {
    net,
    exerciseDays,
    days: days.value.length,
    actualTotal,
    avgActual: actuals.length ? Math.round(actualTotal / actuals.length) : null,
    base
  }
})

/* ---------- 图表 ---------- */
const { el: barEl, chart: barChart, palette } = useTrendChart(() => renderCharts())
const { el: lineEl, chart: lineChart, hexA } = useTrendChart(() => renderCharts())

function renderCharts() {
  const b = barChart.ensure()
  const l = lineChart.ensure()
  if (!b || !l) return
  const c = palette()
  const labels = days.value.map((d) => d.date.slice(5))
  const netData = days.value.map((d) => d.net)
  const actualData = days.value.map((d) => d.actual ?? null)

  b.setOption({
    grid: TREND_GRID,
    tooltip: { trigger: 'axis', formatter: (ps: any) => `${ps[0].axisValue}<br/>锻炼净消耗 <b>${ps[0].value}</b> kcal` },
    ...trendAxes(labels, c),
    series: [{
      type: 'bar', data: netData, barWidth: '50%',
      itemStyle: { color: (p: any) => (days.value[p.dataIndex]?.count > 0 ? c.teal : 'transparent'), borderRadius: [3, 3, 0, 0] },
      emphasis: { itemStyle: { color: c.teal } }
    }]
  } as any)

  l.setOption({
    grid: { ...TREND_GRID, top: 32 },
    tooltip: {
      trigger: 'axis',
      formatter: (ps: any) => {
        const p = ps[0]
        const row = days.value[p.dataIndex]
        const lines = [`${p.axisValue}`]
        if (row?.count > 0) lines.push(`锻炼净消耗 +${row.net} kcal`)
        lines.push(`实际消耗 <b>${row?.actual ?? '-'}</b> kcal`)
        if (row?.count > 0) lines.push(`明细：${row.items.join('、')}`)
        return lines.join('<br/>')
      }
    },
    ...trendAxes(labels, c),
    series: [{
      type: 'line', data: actualData, symbolSize: 6, smooth: true,
      // V2 语义色：实际消耗 = --c-burn（与柱状/饮食统计同维度同色）
      lineStyle: { color: c.burn, width: 2 },
      itemStyle: { color: c.burn },
      areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: hexA(c.burn, 0.18) }, { offset: 1, color: hexA(c.burn, 0.02) }] } },
      connectNulls: true,
      markLine: summary.value.base != null ? {
        silent: true,
        symbol: 'none',
        data: [{ yAxis: summary.value.base }],
        lineStyle: { color: c.muted, type: 'dashed', width: 1 },
        label: { formatter: `久坐基准 ${summary.value.base}`, color: c.ink2, fontSize: 11, position: 'insideEndTop' }
      } : undefined
    }]
  } as any)
}

/* ---------- 记录热力图（V2：把坚持变成看得见的格子） ---------- */
/** 锻炼项数 → 热力档（0-4） */
function heatLevel(n: number) {
  if (n <= 0) return 0
  if (n === 1) return 1
  if (n === 2) return 2
  if (n <= 4) return 3
  return 4
}

const heatRows = computed<HeatmapRow[]>(() => [{
  label: '锻炼',
  // 区间内最后 30 天（预设 30 天时恰好全量；7/90 天预设跟随区间）
  cells: days.value.slice(-30).map((d) => ({
    date: d.date,
    level: heatLevel(d.count),
    tip: `${d.date} · ${d.count} 项${d.count ? ` · 净消耗 ${Math.round(d.net)} kcal` : ''}`
  }))
}])

/** 隐式成就标注：连续锻炼天数 + 本月天数（去火焰式激励，保持书卷气） */
const heatLegend = computed(() => {
  let streak = 0
  for (let i = days.value.length - 1; i >= 0; i--) {
    if (days.value[i].count > 0) streak++
    else break
  }
  const month = today.slice(0, 7)
  const monthDays = days.value.filter((d) => d.date.startsWith(month) && d.count > 0).length
  return streak > 1 ? `连续锻炼 ${streak} 天 · 本月 ${monthDays} 天` : `本月 ${monthDays} 天`
})

function applyPreset(start: string) {
  range.value = [start, today]
}

/** 区间变化（预设按钮 / 手选日期，v-model 双向）→ 重新加载；清空为 null 时跳过保持上次数据 */
watch(range, (v) => {
  if (v) load()
})

watch(() => props.tick, () => load(true))
watch(() => props.active, (a) => {
  if (a) requestAnimationFrame(() => renderCharts())
})

onMounted(load)
</script>

<template>
  <section class="card exa">
    <BlockTitle title="消耗分析" :hint="bmr != null ? `每天实际 = ${bmr}×1.2 久坐基准 + 当天锻炼净消耗（按记录时体重快照）` : '记录体重+体脂率后可按 1.2×BMR+锻炼 估算每天实际消耗'" />

    <div class="exa__bar">
      <div class="exa__presets">
        <button v-for="p in presets" :key="p.label" class="exa__preset" @click="applyPreset(p.start())">{{ p.label }}</button>
      </div>
      <DateRangePicker v-model="range" width="min(260px,100%)" />
    </div>

    <EmptyState
      v-if="!loading && !summary.exerciseDays"
      text="区间内没有锻炼记录，选个更大的范围看看"
    />

    <template v-else>
      <div class="exa__metrics">
        <MetricCard label="区间实际消耗">
          <template #default><span class="num">{{ summary.actualTotal }}<i>kcal</i></span></template>
        </MetricCard>
        <MetricCard label="平均每天实际" tone="ok">
          <template #default><span class="num">{{ summary.avgActual ?? '-' }}<i>kcal</i></span></template>
        </MetricCard>
        <MetricCard label="锻炼净消耗">
          <template #default><span class="num">{{ summary.net }}<i>kcal</i></span></template>
        </MetricCard>
        <MetricCard label="锻炼天数" tone="accent">
          <template #default><span class="num">{{ summary.exerciseDays }}<i>/{{ summary.days }}天</i></span></template>
        </MetricCard>
      </div>

      <div class="exa__panel">
        <div class="exa__panel-head">
          <span class="exa__panel-title"><component :is="ChartColumnBig" :size="14" /> 每天锻炼净消耗</span>
          <span class="exa__panel-hint">柱状即当天运动量，今日前无记录日为透明占位</span>
        </div>
        <div ref="barEl" class="exa__chart"></div>
      </div>

      <div class="exa__panel">
        <div class="exa__panel-head">
          <span class="exa__panel-title"><component :is="CalendarDays" :size="14" /> 每天实际消耗</span>
          <span class="exa__panel-hint" v-if="summary.base != null">虚线为久坐基准 {{ summary.base }} kcal/天</span>
        </div>
        <div ref="lineEl" class="exa__chart"></div>
      </div>

      <!-- V2 记录热力图：色随模块主色深浅 -->
      <div class="exa__panel">
        <div class="exa__panel-head">
          <span class="exa__panel-title">记录热力</span>
          <span class="exa__panel-hint">{{ heatLegend }}</span>
        </div>
        <RecordHeatmap :rows="heatRows" :legend-text="''" />
      </div>

      <div class="exa__table">
        <div class="exa__panel-head">
          <span class="exa__panel-title">按天明细</span>
          <span class="exa__panel-hint">最新在上</span>
        </div>
        <div class="exa__table-head exa__table-row">
          <span>日期</span><span>项数</span><span>锻炼净消耗</span><span>实际消耗</span>
        </div>
        <div v-for="d in [...days].reverse()" :key="d.date" class="exa__table-row">
          <span class="exa__date">{{ d.date }}</span>
          <span class="exa__count">{{ d.count ? `${d.count} 项` : '—' }}</span>
          <span class="num exa__net">{{ d.count ? `${d.net}` : '—' }}<small v-if="d.count">kcal</small></span>
          <span class="num exa__actual">{{ d.actual ?? '—' }}<small v-if="d.actual != null">kcal</small></span>
        </div>
        <p v-if="bmr == null" class="exa__note">暂按「当天锻炼净消耗」展示——补记体重与体脂率后，实际消耗将变为 1.2×BMR + 锻炼净消耗。</p>
      </div>
    </template>

    <LoadingMask :show="loading" :size="28" text="加载分析…" />
  </section>
</template>

<style lang="scss" scoped>
@use '../exercise';
</style>