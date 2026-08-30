<script setup lang="ts">
/**
 * 记账 · 概览子页组件
 * 指标(随范围) + ECharts 横向条形趋势(7/30/全部, 可收纳) + 支出/收入构成环形图
 * - props.tick 任一子页数据变更后递增 → 重载概览
 * - props.active 切到本子页时重载并补渲染（对应原 onSubChange 切概览刷新）
 * - 点趋势柱子联动明细到当天 emit('select-date', date)
 */
import { computed, markRaw, onMounted, ref, watch } from 'vue'
import { ChevronUp, ChevronDown } from '@lucide/vue'
import {
  listExpenseRecords,
  getExpenseStatistics
} from '@/api/expense'
import type { ExpenseRecord, CategoryStat, ExpenseStatistics } from '@/api/expense'
import { formatDate, formatMoney } from '@/utils/format'
import { cssVar } from '@/utils/theme'
import { useECharts } from '@/utils/useECharts'
import LoadingMask from '@/components/LoadingMask/LoadingMask.vue'
import RangeTabs, { type RangeTabItem } from '@/components/RangeTabs/RangeTabs.vue'
import CollapseBox from '@/components/CollapseBox/CollapseBox.vue'
import MetricCard from '@/components/MetricCard/MetricCard.vue'
import BlockTitle from '@/components/BlockTitle/BlockTitle.vue'

const props = defineProps<{
  tick: number
  active: boolean
}>()

const emit = defineEmits<{
  (e: 'select-date', date: string): void
}>()

/* ---------- 概览数据 ---------- */
type RangeKey = '7' | '30' | 'all'
const range = ref<RangeKey>('30')
const ovRangeOptions: RangeTabItem[] = [
  { key: '7', label: '7 天' },
  { key: '30', label: '30 天' },
  { key: 'all', label: '全部' }
]
const overviewCollapsed = ref(false)
const overviewLoading = ref(false)
const todayExpense = ref(0)
const stat = ref<ExpenseStatistics>({
  totalExpense: 0,
  totalIncome: 0,
  balance: 0,
  expenseByCategory: [],
  incomeByCategory: []
})
const trend = ref<{ dates: string[]; amounts: number[] }>({ dates: [], amounts: [] })

function rangeBounds(key: RangeKey): { start?: string; end?: string } {
  const end = new Date()
  if (key === 'all') return { end: formatDate(end) }
  const days = Number(key)
  const start = new Date(end)
  start.setDate(start.getDate() - (days - 1))
  return { start: formatDate(start), end: formatDate(end) }
}

/** 概览标题随范围切换：7天→本周、30天→本月、全部→全部 */
const overviewTitle = computed(() => {
  if (range.value === '7') return '本周概览'
  if (range.value === 'all') return '全部概览'
  return '本月概览'
})

/** 指标标签随范围切换（今日支出不变） */
const metricLabel = (key: 'expense' | 'income' | 'balance') => {
  const prefix = range.value === '7' ? '本周' : range.value === 'all' ? '全部' : '本月'
  const map = { expense: '支出', income: '收入', balance: '结余' }
  return `${prefix}${map[key]}`
}

/** 拉取某范围内全部支出记录（翻页），用于按天聚合趋势 */
async function fetchAllExpense(start?: string, end?: string): Promise<ExpenseRecord[]> {
  const acc: ExpenseRecord[] = []
  let page = 1
  const size = 100
  for (;;) {
    const res = await listExpenseRecords({ type: 1, startDate: start, endDate: end, page, size })
    acc.push(...res.records)
    if (page * size >= res.total) break
    page++
  }
  return acc
}

async function loadOverview() {
  overviewLoading.value = true
  try {
    const { start, end } = rangeBounds(range.value)
    const [st, below] = await Promise.all([
      getExpenseStatistics({ startDate: start, endDate: end }),
      fetchAllExpense(start, end)
    ])
    stat.value = st
    const today = formatDate(new Date())
    todayExpense.value = below
      .filter((r) => r.recordDate === today)
      .reduce((s, r) => s + r.amount, 0)
    const map = new Map<string, number>()
    for (const r of below) map.set(r.recordDate, (map.get(r.recordDate) ?? 0) + r.amount)
    let dates: string[]
    if (start) {
      dates = []
      const cur = new Date(start)
      const stop = new Date(end!)
      while (cur <= stop) {
        dates.push(formatDate(cur))
        cur.setDate(cur.getDate() + 1)
      }
    } else {
      dates = [...map.keys()].sort()
    }
    trend.value = { dates, amounts: dates.map((d) => map.get(d) ?? 0) }
    renderChart()
    renderDonut()
  } finally {
    overviewLoading.value = false
  }
}

function switchRange() {
  loadOverview()
}

/* ---------- 图表 ---------- */
const chartEl = ref<HTMLDivElement | null>(null)
let trendDates: string[] = []

const donutExpenseEl = ref<HTMLDivElement | null>(null)
const donutIncomeEl = ref<HTMLDivElement | null>(null)
const trendC = useECharts(chartEl, { redraw: () => renderChart() })
const donutExpenseC = useECharts(donutExpenseEl, { redraw: () => renderDonut() })
const donutIncomeC = useECharts(donutIncomeEl, { redraw: () => renderDonut() })

const DONUT_COLORS = [
  '#c08a3e', '#b04a3a', '#3f7a72', '#4f7a8c', '#5a5a8c',
  '#6b7a66', '#a8821f', '#8a5a5a', '#92988d'
]

function statOf(type: 1 | 2): CategoryStat[] {
  return type === 1 ? stat.value.expenseByCategory : stat.value.incomeByCategory
}

const maxCat = computed(() => {
  const all = [...statOf(1), ...statOf(2)].map((s) => s.amount)
  return all.length ? Math.max(...all) : 1
})
function catColor(type: 1 | 2) {
  return type === 1 ? 'var(--cb-error)' : 'var(--cb-success)'
}

function renderDonut() {
  // 环形图在子页 Tab 内（v-show），容器 display:none 时宽高为 0，
  // useECharts 的 ensure 已做 clientWidth>0 保护，避免 ECharts init 告警
  const de = donutExpenseC.ensure()
  const di = donutIncomeC.ensure()
  if (!de && !di) return
  const muted = cssVar('--cb-ink-muted')
  const surface = cssVar('--cb-surface')
  const base = {
    tooltip: { trigger: 'item', formatter: '{b}<br/>{c}（{d}%）' },
    legend: { bottom: 0, textStyle: { color: muted, fontSize: 12 } },
    series: [{
      type: 'pie',
      radius: ['52%', '74%'],
      center: ['50%', '44%'],
      itemStyle: { borderRadius: 4, borderColor: surface, borderWidth: 2 },
      emphasis: { scaleSize: 6 },
      label: { show: false },
      data: [] as { name: string; value: number; itemStyle: { color: string } }[]
    }]
  }
  if (de) {
    const data = statOf(1).map((s, i) => ({ name: s.categoryName, value: s.amount, itemStyle: { color: DONUT_COLORS[i % DONUT_COLORS.length] } }))
    de.setOption({ ...base, series: [{ ...base.series[0], data }] } as any)
  }
  if (di) {
    const data = statOf(2).map((s, i) => ({ name: s.categoryName, value: s.amount, itemStyle: { color: DONUT_COLORS[i % DONUT_COLORS.length] } }))
    di.setOption({ ...base, series: [{ ...base.series[0], data }] } as any)
  }
}

function renderChart() {
  const chart = trendC.ensure((inst) => {
    inst.on('click', (params: any) => {
      if (params.dataIndex != null) {
        const d = trendDates[params.dataIndex]
        if (d) emit('select-date', d)
      }
    })
  })
  if (!chart) return
  trendDates = trend.value.dates
  const amounts = trend.value.amounts
  const h = Math.min(Math.max(trendDates.length * 26, 170), 520)
  chartEl.value!.style.height = `${h}px`
  chart.resize()
  const muted = cssVar('--cb-ink-muted')
  const hairline = cssVar('--cb-hairline')
  const mod = cssVar('--cb-mod', cssVar('--cb-primary'), chartEl.value)
  const isAll = range.value === 'all'
  chart.setOption({
    grid: { top: 8, right: 24, bottom: 8, left: 58 },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (ps: any) => {
        const p = ps[0]
        return `${p.axisValue}<br/>支出 <b>${formatMoney(p.value)}</b>`
      }
    },
    xAxis: {
      type: 'value',
      axisLabel: { color: muted, formatter: (v: number) => `¥${v}` },
      splitLine: { lineStyle: { color: hairline, type: 'dashed' } }
    },
    yAxis: {
      type: 'category',
      data: trendDates,
      axisLine: { lineStyle: { color: hairline } },
      axisTick: { show: false },
      axisLabel: {
        color: muted,
        interval: isAll ? Math.max(0, Math.floor(amounts.length / 18) - 1) : 0,
        formatter: (v: string) => v.slice(5)
      }
    },
    series: [
      {
        type: 'bar',
        data: amounts,
        barMaxWidth: 14,
        itemStyle: { color: mod, borderRadius: [0, 3, 3, 0] }
      }
    ]
  } as any)
}

/** 收纳/展开（CollapseBox 展开动画约 0.36s 完成后容器高度才到位，图表需重新布局） */
function onOverviewExpand() {
  trendC.resize()
  donutExpenseC.resize()
  donutIncomeC.resize()
}

onMounted(loadOverview)
// 任一子页数据变更 → 重载概览
watch(() => props.tick, loadOverview)
// 切到本子页：重载并补渲染（对应原 onSubChange 切概览刷新）
watch(() => props.active, (a) => {
  if (a) {
    requestAnimationFrame(() => {
      loadOverview()
    })
  }
})
// 范围切换：立即重载
watch(range, () => {
  if (props.active) switchRange()
})
</script>

<template>
  <section class="card exp__overview">
    <BlockTitle :title="overviewTitle">
      <template #aside>
        <div class="exp__range">
          <RangeTabs
            :options="ovRangeOptions"
            :model-value="range"
            @update:model-value="(v: string) => { range = v as RangeKey }"
          />
          <el-tooltip :content="overviewCollapsed ? '展开概览' : '收纳概览'" placement="top">
            <button
              class="exp__collapse-btn"
              :class="{ 'is-collapsed': overviewCollapsed }"
              type="button"
              aria-label="收纳或展开概览"
              @click="overviewCollapsed = !overviewCollapsed"
            >
              <component :is="overviewCollapsed ? ChevronDown : ChevronUp" :size="15" />
            </button>
          </el-tooltip>
        </div>
      </template>
    </BlockTitle>

    <CollapseBox :collapsed="overviewCollapsed" @expand-end="onOverviewExpand">
      <div class="exp__metrics">
        <MetricCard :label="metricLabel('expense')" tone="err">
          <template #default><span class="num">{{ formatMoney(stat.totalExpense) }}</span></template>
        </MetricCard>
        <MetricCard :label="metricLabel('income')" tone="ok">
          <template #default><span class="num">{{ formatMoney(stat.totalIncome) }}</span></template>
        </MetricCard>
        <MetricCard :label="metricLabel('balance')" :tone="stat.balance < 0 ? 'err' : ''">
          <template #default><span class="num">{{ formatMoney(stat.balance, true) }}</span></template>
        </MetricCard>
        <MetricCard label="今日支出" tone="accent">
          <template #default><span class="num">{{ formatMoney(todayExpense) }}</span></template>
        </MetricCard>
      </div>

      <div class="exp__panel">
        <div class="exp__panel-head">
          <span class="exp__panel-title">每日支出趋势</span>
          <span class="exp__panel-hint">hover 查看金额，点柱子可看当天明细</span>
        </div>
        <div ref="chartEl" class="exp__chart"></div>
      </div>
    </CollapseBox>

    <LoadingMask :show="overviewLoading" :size="28" text="加载概览…" />
  </section>

  <!-- 支出/收入构成 -->
  <section class="card exp__statcard">
    <BlockTitle title="支出 / 收入构成" hint="本范围占比" />
    <div class="exp__statgrid">
      <div class="exp__statblock">
        <p class="exp__stat-title">支出构成</p>
        <div ref="donutExpenseEl" class="exp__donut"></div>
        <div class="exp__rank">
          <div v-if="statOf(1).length" v-for="s in statOf(1)" :key="s.categoryId" class="exp__rank-item">
            <span class="exp__rank-name">{{ s.categoryName }}</span>
            <span class="exp__rank-bar"><span class="exp__rank-fill" :style="{ width: (s.amount / maxCat * 100) + '%', background: catColor(1) }"></span></span>
            <span class="exp__rank-val num">{{ formatMoney(s.amount) }}</span>
          </div>
          <p v-else class="exp__empty">暂无数据</p>
        </div>
      </div>
      <div class="exp__statblock">
        <p class="exp__stat-title">收入构成</p>
        <div ref="donutIncomeEl" class="exp__donut"></div>
        <div class="exp__rank">
          <div v-if="statOf(2).length" v-for="s in statOf(2)" :key="s.categoryId" class="exp__rank-item">
            <span class="exp__rank-name">{{ s.categoryName }}</span>
            <span class="exp__rank-bar"><span class="exp__rank-fill" :style="{ width: (s.amount / maxCat * 100) + '%', background: catColor(2) }"></span></span>
            <span class="exp__rank-val num">{{ formatMoney(s.amount) }}</span>
          </div>
          <p v-else class="exp__empty">暂无数据</p>
        </div>
      </div>
    </div>
  </section>
</template>

<style lang="scss" scoped>
@use '../expenseList';
</style>