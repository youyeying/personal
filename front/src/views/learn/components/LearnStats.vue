<script setup lang="ts">
/**
 * 学习 · 统计子页组件
 * 四指标 + 近14天时长折线 + 按方式构成环形图 + 掌握程度分布（ECharts 动画）
 * - props.tick 任一子页数据变更后递增 → 重载统计
 * - props.active 切到本子页时补渲染（v-show 容器从隐藏变可见）
 * - 加载完成后 emit('stats-loaded', totalDuration)，供父级 SubNav hint 展示累计时长
 */
import { markRaw, onMounted, ref, watch } from 'vue'
import { BookOpen } from '@lucide/vue'
import * as echarts from 'echarts'
import { getLearnStatistics } from '@/api/learn'
import type { LearnStatistics } from '@/api/learn'
import { cssVar } from '@/utils/theme'
import { useECharts } from '@/utils/useECharts'
import MetricCard from '@/components/MetricCard/MetricCard.vue'
import LoadingMask from '@/components/LoadingMask/LoadingMask.vue'
import BlockTitle from '@/components/BlockTitle/BlockTitle.vue'
import EmptyState from '@/components/EmptyState/EmptyState.vue'
import { durationParts } from '../learnShared'

const props = defineProps<{
  tick: number
  /** 是否处于可见子页（v-show），切到时触发图表补渲染 */
  active: boolean
}>()

const emit = defineEmits<{
  (e: 'stats-loaded', totalDuration: number): void
  (e: 'navigate', tab: string): void
}>()

/* ---------- 统计数据 ---------- */
const statsLoading = ref(false)
const stats = ref<LearnStatistics>({
  totalCount: 0,
  totalDuration: 0,
  todayMinutes: 0,
  monthMinutes: 0,
  avgMastery: null,
  byWay: {},
  mastery: {},
  trend: { dates: [], minutes: [] }
})

async function loadStats() {
  statsLoading.value = true
  try {
    // 字段兜底：后端扩展前旧返回无新字段时页面不崩（统计图空）
    const s = await getLearnStatistics()
    stats.value = {
      totalCount: s.totalCount ?? 0,
      totalDuration: s.totalDuration ?? 0,
      todayMinutes: s.todayMinutes ?? 0,
      monthMinutes: s.monthMinutes ?? 0,
      avgMastery: s.avgMastery ?? null,
      byWay: s.byWay ?? {},
      mastery: s.mastery ?? {},
      trend: s.trend ?? { dates: [], minutes: [] }
    }
    emit('stats-loaded', stats.value.totalDuration)
    renderCharts()
  } finally {
    statsLoading.value = false
  }
}

/* ---------- ECharts 图表（动画；useECharts 统一 init 保护/resize/主题重绘/dispose） ---------- */
const trendChartEl = ref<HTMLDivElement | null>(null)
const donutChartEl = ref<HTMLDivElement | null>(null)
const masteryChartEl = ref<HTMLDivElement | null>(null)
const trendC = useECharts(trendChartEl, { redraw: () => renderTrendChart() })
const donutC = useECharts(donutChartEl, { redraw: () => renderDonutChart() })
const masteryC = useECharts(masteryChartEl, { redraw: () => renderMasteryChart() })

function chartColors() {
  // --cb-mod 定义在 .app-layout 容器上（非 <html>），从图表容器读取继承值
  const el = trendChartEl.value ?? document.documentElement
  return {
    muted: cssVar('--cb-ink-muted'),
    hairline: cssVar('--cb-hairline'),
    text: cssVar('--cb-ink-secondary'),
    mod: cssVar('--cb-mod', cssVar('--cb-primary'), el),
    surface: cssVar('--cb-surface'),
    teal: cssVar('--cb-teal'),
    warning: cssVar('--cb-warning'),
    error: cssVar('--cb-error')
  }
}

/** 近 14 天学习时长折线（面积渐变 + 入场动画） */
function renderTrendChart() {
  const chart = trendC.ensure()
  if (!chart) return
  const c = chartColors()
  const t = stats.value.trend
  chart.setOption({
    grid: { top: 30, right: 20, bottom: 28, left: 44 },
    animationDuration: 800,
    tooltip: {
      trigger: 'axis',
      formatter: (ps: any) => {
        const p = ps[0]
        return `${p.axisValue}<br/>学习 <b>${p.value}min</b>`
      }
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: t.dates,
      axisLine: { lineStyle: { color: c.hairline } },
      axisTick: { show: false },
      axisLabel: {
        color: c.muted,
        interval: t.dates.length > 14 ? Math.floor(t.dates.length / 14) : 0,
        formatter: (v: string) => v.slice(5)
      }
    },
    yAxis: {
      type: 'value',
      name: 'min',
      nameTextStyle: { color: c.muted },
      axisLabel: { color: c.muted },
      splitLine: { lineStyle: { color: c.hairline, type: 'dashed' } }
    },
    series: [{
      type: 'line',
      data: t.minutes,
      smooth: 0.3,
      symbol: 'circle',
      symbolSize: 6,
      lineStyle: { color: c.mod, width: 2.4 },
      itemStyle: { color: c.mod, borderColor: c.surface, borderWidth: 2 },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: c.mod },
          { offset: 1, color: c.surface }
        ])
      }
    }]
  } as any)
}

/** 按方式构成环形图（计数，入场动画） */
function renderDonutChart() {
  const chart = donutC.ensure()
  if (!chart) return
  const c = chartColors()
  const data = Object.entries(stats.value.byWay).map(([name, value]) => ({ name, value }))
  chart.setOption({
    animationDuration: 900,
    tooltip: {
      trigger: 'item',
      formatter: '{b}：{c} 条（{d}%）'
    },
    legend: {
      bottom: 0,
      itemWidth: 10,
      itemHeight: 10,
      textStyle: { color: c.text, fontSize: 12 },
      icon: 'circle'
    },
    color: [c.mod, c.teal, c.warning, c.error, c.muted],
    series: [{
      type: 'pie',
      radius: ['52%', '74%'],
      center: ['50%', '44%'],
      avoidLabelOverlap: true,
      itemStyle: { borderColor: c.surface, borderWidth: 2 },
      label: { show: false },
      labelLine: { show: false },
      emphasis: { label: { show: true, fontSize: 13, fontWeight: 600 } },
      data
    }]
  } as any)
}

/** 掌握程度分布（横向条形，1-5★） */
function renderMasteryChart() {
  const chart = masteryC.ensure()
  if (!chart) return
  const c = chartColors()
  const order = ['5', '4', '3', '2', '1']
  const data = order.map((k) => stats.value.mastery[k] ?? 0)
  chart.setOption({
    grid: { top: 8, right: 24, bottom: 22, left: 44 },
    animationDuration: 800,
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    xAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: { color: c.muted },
      splitLine: { lineStyle: { color: c.hairline, type: 'dashed' } }
    },
    yAxis: {
      type: 'category',
      data: order.map((k) => `${k}★`),
      axisLine: { lineStyle: { color: c.hairline } },
      axisTick: { show: false },
      axisLabel: { color: c.text }
    },
    series: [{
      type: 'bar',
      data,
      barWidth: 16,
      itemStyle: { color: c.mod, borderRadius: [0, 4, 4, 0] },
      label: { show: true, position: 'right', color: c.muted, fontSize: 11 }
    }]
  } as any)
}

function renderCharts() {
  renderTrendChart()
  renderDonutChart()
  renderMasteryChart()
}

onMounted(loadStats)
// 任一子页数据变更 → 重载统计
watch(() => props.tick, loadStats)
// 切到本子页：容器从 display:none 恢复可见，图表需重新布局/补渲染
watch(() => props.active, (a) => {
  if (a) requestAnimationFrame(() => renderCharts())
})
</script>

<template>
  <section class="card lr__stats">
    <BlockTitle title="学习统计" hint="近 14 天趋势 · hover 查看数值" />

    <!-- 空态：无任何记录 -->
    <EmptyState
      v-if="!statsLoading && stats.totalCount === 0"
      text="还没有学习记录，记下第一条开启精进之旅"
      actionText="去记一笔"
      @action="emit('navigate', 'input')"
    >
      <template #icon><component :is="markRaw(BookOpen)" :size="34" /></template>
    </EmptyState>

    <template v-else>
      <div class="lr__metrics">
        <MetricCard label="累计学习">
          <template #default>
            <span class="num">{{ durationParts(stats.totalDuration).h }}<i>h</i> {{ durationParts(stats.totalDuration).m }}<i>m</i></span>
          </template>
          <template #sub><span>共 {{ stats.totalCount }} 条记录</span></template>
        </MetricCard>
        <MetricCard label="今日时长">
          <template #default>
            <span class="num">{{ durationParts(stats.todayMinutes).h }}<i>h</i> {{ durationParts(stats.todayMinutes).m }}<i>m</i></span>
          </template>
        </MetricCard>
        <MetricCard label="本月时长">
          <template #default>
            <span class="num">{{ durationParts(stats.monthMinutes).h }}<i>h</i> {{ durationParts(stats.monthMinutes).m }}<i>m</i></span>
          </template>
        </MetricCard>
        <MetricCard label="掌握均分" tone="accent">
          <template #default>
            <span class="num">{{ stats.avgMastery ?? '--' }}<i>/5</i></span>
          </template>
        </MetricCard>
      </div>

      <div class="lr__chart-grid">
        <div class="lr__panel">
          <div class="lr__panel-head">
            <span class="lr__panel-title">近 14 天学习时长</span>
            <span class="lr__panel-hint">分钟</span>
          </div>
          <div ref="trendChartEl" class="lr__chart"></div>
        </div>
        <div class="lr__panel">
          <div class="lr__panel-head">
            <span class="lr__panel-title">按方式构成</span>
            <span class="lr__panel-hint">记录数</span>
          </div>
          <div ref="donutChartEl" class="lr__chart"></div>
        </div>
      </div>

      <div class="lr__panel lr__panel--full">
        <div class="lr__panel-head">
          <span class="lr__panel-title">掌握程度分布</span>
          <span class="lr__panel-hint">记录条数</span>
        </div>
        <div ref="masteryChartEl" class="lr__chart lr__chart--sm"></div>
      </div>
    </template>

    <LoadingMask :show="statsLoading" :size="28" text="加载统计…" />
  </section>
</template>

<style lang="scss" scoped>
@use '../learn';
</style>