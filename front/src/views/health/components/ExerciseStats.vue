<script setup lang="ts">
/**
 * 健康 · 锻炼统计子页组件
 * 四指标（今日/本周/本月净消耗 + 连续天数）+ 近 14 天净消耗柱状图 + 动作消耗分布
 * 消耗基于「最新体重记录」按 MET 公式前端计算（utils/exercise.ts）
 */
import { computed, markRaw, onMounted, ref, watch } from 'vue'
import { Flame, CalendarDays, TrendingUp, Repeat } from '@lucide/vue'
import { listExerciseItems, getExerciseStatistics } from '@/api/exercise'
import type { ExerciseItem, ExerciseRecord } from '@/api/exercise'
import { listWeightRecords } from '@/api/health'
import { formatDate } from '@/utils/format'
import { useECharts } from '@/utils/useECharts'
import { cssVar } from '@/utils/theme'
import { walkSpeedKmh, walkMet, strengthMet, calcKcal, totalSeconds } from '@/utils/exercise'
import MetricCard from '@/components/MetricCard/MetricCard.vue'
import BlockTitle from '@/components/BlockTitle/BlockTitle.vue'
import EmptyState from '@/components/EmptyState/EmptyState.vue'
import LoadingMask from '@/components/LoadingMask/LoadingMask.vue'

const props = defineProps<{ tick: number; active: boolean }>()
const emit = defineEmits<{ (e: 'navigate', tab: string): void }>()

/* ---------- 数据 ---------- */
const loading = ref(false)
const records = ref<ExerciseRecord[]>([])
const items = ref<ExerciseItem[]>([])
const weightKg = ref<number | null>(null)

/** 每条的净消耗（含动作类型信息） */
interface KcalRow {
  rec: ExerciseRecord
  net: number
  total: number
  item: ExerciseItem
  date: string
}
const rows = ref<KcalRow[]>([])

function computeRow(r: ExerciseRecord, item: ExerciseItem, w: number): KcalRow {
  let met = item.baseMet
  let minutes = 0
  if (item.type === 'strength' && r.reps) {
    const secTotal = totalSeconds(r.minutes, r.seconds)
    if (secTotal <= 0) return { rec: r, net: 0, total: 0, item, date: r.recordDate }
    minutes = secTotal / 60
    met = strengthMet(item.baseMet, r.reps / minutes, item.refSpeed ?? 12)
  } else if (item.type === 'walk' && r.distance && r.minutes) {
    met = walkMet(walkSpeedKmh(Number(r.distance), Number(r.minutes))).met
    minutes = Number(r.minutes)
  } else if (item.type === 'stairs' && r.floors && r.times) {
    minutes = totalSeconds(r.minutes, r.seconds) / 60
  } else if (item.type === 'plank' && r.seconds) {
    minutes = r.seconds / 60
  }
  const k = calcKcal(met, minutes, w)
  return { rec: r, net: k.net, total: k.total, item, date: r.recordDate }
}

/** silent=true 后台静默刷新（保存/删除后的 tick 联动），不显示加载遮罩防闪烁 */
async function load(silent = false) {
  if (!silent) loading.value = true
  try {
    const [recs, itms, wres] = await Promise.all([
      getExerciseStatistics(),
      listExerciseItems(),
      listWeightRecords({ page: 1, size: 1 })
    ])
    records.value = recs.records
    items.value = itms
    weightKg.value = wres.records?.[0]?.weight != null ? Number(wres.records[0].weight) : null
    rows.value = weightKg.value
      ? records.value
          .map((r) => {
            const item = items.value.find((i) => i.id === r.exerciseId)
            return item ? computeRow(r, item, weightKg.value!) : null
          })
          .filter((x): x is KcalRow => x !== null)
      : []
    renderChart()
  } finally {
    if (!silent) loading.value = false
  }
}

/* ---------- 指标 ---------- */
const today = formatDate(new Date())
const weekStart = (() => {
  const d = new Date()
  const day = d.getDay() === 0 ? 7 : d.getDay()
  d.setDate(d.getDate() - (day - 1))
  return formatDate(d)
})()
const monthStart = formatDate(new Date(new Date().getFullYear(), new Date().getMonth(), 1))

function sumSince(date: string) {
  return rows.value.filter((x) => x.date >= date).reduce((s, x) => s + x.net, 0)
}
const todayKcal = computed(() => sumSince(today))
const weekKcal = computed(() => sumSince(weekStart))
const monthKcal = computed(() => sumSince(monthStart))

/** 连续锻炼天数（从今天往回数，中断即停） */
const streak = computed(() => {
  const daySet = new Set(rows.value.map((x) => x.date))
  let count = 0
  const d = new Date()
  while (true) {
    const s = formatDate(d)
    if (daySet.has(s)) {
      count++
      d.setDate(d.getDate() - 1)
    } else {
      break
    }
  }
  return count
})

/* ---------- 近 14 天柱状图 ---------- */
const chartEl = ref<HTMLDivElement | null>(null)
const chart = useECharts(chartEl, { redraw: () => renderChart() })

function renderChart() {
  const el = chart.ensure()
  if (!el) return
  const base = chartEl.value ?? document.documentElement
  const c = {
    mod: cssVar('--cb-mod', cssVar('--cb-primary'), base),
    muted: cssVar('--cb-ink-muted'),
    hairline: cssVar('--cb-hairline'),
    teal: cssVar('--cb-teal')
  }
  // 近 14 天（含今天）
  const days: string[] = []
  const kcal: number[] = []
  for (let i = 13; i >= 0; i--) {
    const d = new Date()
    d.setDate(d.getDate() - i)
    const s = formatDate(d)
    days.push(s)
    kcal.push(rows.value.filter((x) => x.date === s).reduce((sum, x) => sum + x.net, 0))
  }
  el.setOption({
    grid: { top: 24, right: 12, bottom: 26, left: 44 },
    tooltip: {
      trigger: 'axis',
      formatter: (ps: any) => {
        const p = ps[0]
        return `${p.axisValue}<br/>净消耗 <b>${p.value}</b> kcal`
      }
    },
    xAxis: {
      type: 'category',
      data: days.map((d) => d.slice(5)),
      axisLine: { lineStyle: { color: c.hairline } },
      axisTick: { show: false },
      axisLabel: { color: c.muted, interval: 1 }
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: c.muted },
      splitLine: { lineStyle: { color: c.hairline, type: 'dashed' } }
    },
    series: [{
      type: 'bar',
      data: kcal,
      barWidth: '50%',
      itemStyle: {
        color: (p: any) => (p.dataIndex === days.length - 1 ? c.teal : c.mod),
        borderRadius: [3, 3, 0, 0]
      }
    }]
  } as any)
}

/* ---------- 动作消耗分布（Top5） ---------- */
const dist = computed(() => {
  const map = new Map<string, number>()
  for (const x of rows.value) {
    map.set(x.item.name, (map.get(x.item.name) ?? 0) + x.net)
  }
  return [...map.entries()]
    .map(([name, kcal]) => ({ name, kcal }))
    .sort((a, b) => b.kcal - a.kcal)
    .slice(0, 5)
})
const distMax = computed(() => dist.value[0]?.kcal ?? 1)

/* ---------- 空态 ---------- */
const hasAny = computed(() => rows.value.length > 0)

onMounted(load)
watch(() => props.tick, () => load(true))
watch(() => props.active, (a) => {
  if (a) requestAnimationFrame(() => renderChart())
})
</script>

<template>
  <section class="card exs">
    <BlockTitle title="锻炼统计" :hint="weightKg ? `按体重 ${weightKg}kg 估算` : '记录体重后按体重估算'" />

    <EmptyState
      v-if="!loading && !hasAny"
      text="还没有锻炼记录，动起来记下第一条"
      actionText="去锻炼"
      @action="emit('navigate', 'input')"
    >
      <template #icon><component :is="markRaw(Flame)" :size="34" /></template>
    </EmptyState>

    <template v-else>
      <div class="exs__metrics">
        <MetricCard label="今日净消耗" tone="ok">
          <template #default><span class="num">{{ todayKcal }}<i>kcal</i></span></template>
        </MetricCard>
        <MetricCard label="本周净消耗">
          <template #default><span class="num">{{ weekKcal }}<i>kcal</i></span></template>
        </MetricCard>
        <MetricCard label="本月净消耗" tone="accent">
          <template #default><span class="num">{{ monthKcal }}<i>kcal</i></span></template>
        </MetricCard>
        <MetricCard label="连续锻炼">
          <template #default><span class="num">{{ streak }}<i>天</i></span></template>
        </MetricCard>
      </div>

      <div class="exs__panel">
        <div class="exs__panel-head">
          <span class="exs__panel-title"><component :is="markRaw(TrendingUp)" :size="14" /> 近 14 天净消耗</span>
          <span class="exs__panel-hint">hover 查看数值，今日为青色</span>
        </div>
        <div ref="chartEl" class="exs__chart"></div>
      </div>

      <div v-if="dist.length" class="exs__dist">
        <div class="exs__panel-head">
          <span class="exs__panel-title"><component :is="markRaw(CalendarDays)" :size="14" /> 动作消耗分布</span>
          <span class="exs__panel-hint">净消耗 Top5</span>
        </div>
        <div class="exs__dist-list">
          <div v-for="d in dist" :key="d.name" class="exs__dist-row">
            <span class="exs__dist-name">{{ d.name }}</span>
            <div class="exs__dist-track"><div class="exs__dist-fill" :style="{ width: (d.kcal / distMax * 100) + '%' }"></div></div>
            <span class="exs__dist-val num">{{ d.kcal }} kcal</span>
          </div>
        </div>
      </div>
    </template>

    <LoadingMask :show="loading" :size="28" text="加载统计…" />
  </section>
</template>

<style lang="scss" scoped>
@use '../exercise';
</style>
