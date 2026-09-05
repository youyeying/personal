<script setup lang="ts">
/**
 * 健康 · 趋势子页组件
 * 四指标 + 体重折线（目标虚线）+ 30/90/全部切换 + 可收纳 + 体脂/腰围小趋势
 * - props.tick 任一子页数据变更后递增 → 重载趋势
 * - props.active 切到本子页时补渲染（v-show 容器从隐藏变可见）
 */
import { markRaw, computed, onMounted, ref, watch } from 'vue'
import { Weight, ChevronUp, ChevronDown, Percent, Ruler } from '@lucide/vue'
import * as echarts from 'echarts'
import { getWeightTrend } from '@/api/health'
import type { WeightTrend } from '@/api/health'
import { formatDate } from '@/utils/format'
import { cssVar } from '@/utils/theme'
import { useECharts } from '@/utils/useECharts'
import { useUserStore } from '@/store/user'
import RangeTabs, { type RangeTabItem } from '@/components/RangeTabs/RangeTabs.vue'
import CollapseBox from '@/components/CollapseBox/CollapseBox.vue'
import MetricCard from '@/components/MetricCard/MetricCard.vue'
import LoadingMask from '@/components/LoadingMask/LoadingMask.vue'
import BlockTitle from '@/components/BlockTitle/BlockTitle.vue'
import EmptyState from '@/components/EmptyState/EmptyState.vue'

const props = defineProps<{
  tick: number
  active: boolean
}>()

const emit = defineEmits<{
  (e: 'navigate', tab: string): void
}>()

const userStore = useUserStore()
/** 目标体重（来自用户信息，刷新页面由布局壳 ensureUserInfo 补齐） */
const targetWeight = computed(() => userStore.userInfo?.targetWeight ?? null)

/* ---------- 趋势数据 ---------- */
type RangeKey = '30' | '90' | 'all'
const range = ref<RangeKey>('30')
const trendCollapsed = ref(false)
const trendRangeOptions: RangeTabItem[] = [
  { key: '30', label: '30 天' },
  { key: '90', label: '90 天' },
  { key: 'all', label: '全部' }
]
const trendLoading = ref(false)
const trend = ref<WeightTrend>({ dates: [], weights: [], bodyFats: [], waists: [] })

/** 按范围过滤趋势数据（trend 接口返回全量升序，前端切范围不重复请求） */
const scopedTrend = computed<WeightTrend>(() => {
  const all = trend.value
  if (range.value === 'all' || all.dates.length === 0) return all
  const days = Number(range.value)
  const start = new Date()
  start.setDate(start.getDate() - (days - 1))
  const startStr = formatDate(start)
  const idx = all.dates.findIndex((d) => d >= startStr)
  if (idx <= 0) return idx === 0 ? all : { dates: [], weights: [], bodyFats: [], waists: [] }
  return {
    dates: all.dates.slice(idx),
    weights: all.weights.slice(idx),
    bodyFats: all.bodyFats.slice(idx),
    waists: all.waists.slice(idx)
  }
})

/** 四指标：当前体重 / 较上次 / 距目标 / 累计记录 */
const metrics = computed(() => {
  const all = trend.value
  const n = all.weights.length
  const current = n ? all.weights[n - 1] : null
  const prev = n > 1 ? all.weights[n - 2] : null
  const change = current != null && prev != null ? Number((current - prev).toFixed(1)) : null
  const toTarget = current != null && targetWeight.value != null
    ? Number((current - targetWeight.value).toFixed(1))
    : null
  return { current, change, toTarget, days: n }
})

async function loadTrend() {
  trendLoading.value = true
  try {
    trend.value = await getWeightTrend()
    renderCharts()
  } finally {
    trendLoading.value = false
  }
}

/* ---------- ECharts 图表 ---------- */
const chartEl = ref<HTMLDivElement | null>(null)
const fatEl = ref<HTMLDivElement | null>(null)
const waistEl2 = ref<HTMLDivElement | null>(null)
const weightC = useECharts(chartEl, { redraw: () => renderWeightChart() })
const fatC = useECharts(fatEl, { redraw: () => renderCharts() })
const waistC = useECharts(waistEl2, { redraw: () => renderCharts() })

/** 折线基础配色（canvas 不解析 CSS 变量，取实际色值；主题切换时重绘） */
function lineColors() {
  const el = chartEl.value ?? document.documentElement
  return {
    muted: cssVar('--sk-ink-muted'),
    hairline: cssVar('--sk-hairline'),
    mod: cssVar('--sk-mod', cssVar('--sk-primary'), el),
    surface: cssVar('--sk-surface'),
    warn: cssVar('--sk-warning')
  }
}

/** 体重折线 + 目标虚线 */
function renderWeightChart() {
  const chart = weightC.ensure()
  if (!chart) return
  const t = scopedTrend.value
  const c = lineColors()
  const markLine = targetWeight.value != null
    ? {
        silent: true,
        symbol: 'none',
        data: [{ yAxis: targetWeight.value }],
        lineStyle: { color: c.warn, type: 'dashed' as const, width: 1.5 },
        label: {
          formatter: `目标 ${targetWeight.value}kg`,
          color: c.warn,
          position: 'insideEndTop' as const
        }
      }
    : undefined
  chart.setOption({
    grid: { top: 30, right: 20, bottom: 28, left: 46 },
    tooltip: {
      trigger: 'axis',
      formatter: (ps: any) => {
        const p = ps[0]
        return `${p.axisValue}<br/>体重 <b>${p.value}kg</b>`
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
        interval: t.dates.length > 16 ? Math.floor(t.dates.length / 16) : 0,
        formatter: (v: string) => v.slice(5)
      }
    },
    yAxis: {
      type: 'value',
      scale: true,
      axisLabel: { color: c.muted },
      splitLine: { lineStyle: { color: c.hairline, type: 'dashed' } }
    },
    series: [{
      type: 'line',
      data: t.weights,
      smooth: 0.3,
      symbol: 'circle',
      symbolSize: 7,
      lineStyle: { color: c.mod, width: 2.4 },
      itemStyle: { color: c.mod, borderColor: c.surface, borderWidth: 2 },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: c.mod },
          { offset: 1, color: c.surface }
        ])
      },
      markLine
    }]
  } as any)
}

/** 体脂/腰围小趋势共用 Series 配置 */
function miniOption(data: (number | null)[], dates: string[], unit: string) {
  const c = lineColors()
  return {
    grid: { top: 12, right: 8, bottom: 22, left: 36 },
    tooltip: {
      trigger: 'axis',
      formatter: (ps: any) => {
        const p = ps[0]
        return `${p.axisValue}<br/><b>${p.value ?? '-'}${unit}</b>`
      }
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: dates,
      axisLine: { lineStyle: { color: c.hairline } },
      axisTick: { show: false },
      axisLabel: {
        color: c.muted,
        interval: dates.length > 8 ? Math.floor(dates.length / 8) : 0,
        formatter: (v: string) => v.slice(5)
      }
    },
    yAxis: {
      type: 'value',
      scale: true,
      axisLabel: { color: c.muted, fontSize: 11 },
      splitLine: { lineStyle: { color: c.hairline, type: 'dashed' } }
    },
    series: [{
      type: 'line',
      data,
      smooth: 0.3,
      symbol: 'circle',
      symbolSize: 5,
      lineStyle: { color: c.mod, width: 2 },
      itemStyle: { color: c.mod }
    }]
  } as any
}

function renderFatChart() {
  const c = fatC.ensure()
  if (!c) return
  const t = scopedTrend.value
  c.setOption(miniOption(t.bodyFats, t.dates, '%'))
}

function renderWaistChart() {
  const c = waistC.ensure()
  if (!c) return
  const t = scopedTrend.value
  c.setOption(miniOption(t.waists, t.dates, 'cm'))
}

function renderCharts() {
  renderWeightChart()
  renderFatChart()
  renderWaistChart()
}

/** 体脂 / 腰围是否存在有效数据（决定小图卡是否渲染） */
const hasFat = computed(() => scopedTrend.value.bodyFats.some((v) => v != null))
const hasWaist = computed(() => scopedTrend.value.waists.some((v) => v != null))

/** 收纳/展开（CollapseBox 展开动画约 0.36s 完成后容器高度才到位，图表需重新布局） */
function onTrendExpand() {
  weightC.resize()
  fatC.resize()
  waistC.resize()
}

onMounted(loadTrend)
// 任一子页数据变更 → 重载趋势
watch(() => props.tick, loadTrend)
// 切到本子页：容器从 display:none 恢复可见，图表需重新布局/补渲染
watch(() => props.active, (a) => {
  if (a) requestAnimationFrame(() => renderCharts())
})
// 范围切换：数据变化后重绘三张图
watch(range, () => {
  if (props.active) renderCharts()
})
// 目标体重异步补齐后补画目标虚线
watch(targetWeight, () => {
  if (props.active) renderCharts()
})
</script>

<template>
  <section class="card hl__trend">
    <BlockTitle title="体重趋势">
      <template #aside>
        <div class="hl__range">
          <RangeTabs
            :options="trendRangeOptions"
            :model-value="range"
            @update:model-value="(v: string) => range = v as RangeKey"
          />
          <el-tooltip :content="trendCollapsed ? '展开趋势' : '收纳趋势'" placement="top">
            <button
              class="hl__collapse-btn"
              :class="{ 'is-collapsed': trendCollapsed }"
              type="button"
              aria-label="收纳或展开趋势"
              @click="trendCollapsed = !trendCollapsed"
            >
              <component :is="trendCollapsed ? ChevronDown : ChevronUp" :size="15" />
            </button>
          </el-tooltip>
        </div>
      </template>
    </BlockTitle>

    <!-- 空态：无任何记录 -->
    <EmptyState
      v-if="!trendLoading && trend.dates.length === 0"
      text="还没有体重记录，记下第一条开启健康之旅"
      actionText="去打卡"
      @action="emit('navigate', 'input')"
    >
      <template #icon><component :is="markRaw(Weight)" :size="34" /></template>
    </EmptyState>

    <CollapseBox
      v-else
      :collapsed="trendCollapsed"
      @expand-end="onTrendExpand"
    >
      <div class="hl__metrics">
        <MetricCard label="当前体重">
          <template #default><span class="num">{{ metrics.current ?? '--' }}<i>kg</i></span></template>
        </MetricCard>
        <MetricCard
          label="较上次变化"
          :tone="metrics.change != null && metrics.change < 0 ? 'ok' : metrics.change != null && metrics.change > 0 ? 'err' : ''"
        >
          <template #default>
            <span class="num">{{ metrics.change == null ? '--' : (metrics.change > 0 ? '+' : '') + metrics.change }}<i>kg</i></span>
          </template>
        </MetricCard>
        <MetricCard label="距目标" tone="accent">
          <template #default>
            <span class="num">
              <template v-if="metrics.toTarget == null">--</template>
              <template v-else>{{ Math.abs(metrics.toTarget) }}kg<i>{{ metrics.toTarget > 0 ? '还需减' : '已低于' }}</i></template>
            </span>
          </template>
          <template v-if="targetWeight != null" #sub>
            <span>目标 {{ targetWeight }}kg</span>
          </template>
        </MetricCard>
        <MetricCard label="累计记录">
          <template #default><span class="num">{{ metrics.days }}<i>天</i></span></template>
        </MetricCard>
      </div>

      <div class="hl__panel">
        <div class="hl__panel-head">
          <span class="hl__panel-title">体重变化</span>
          <span class="hl__panel-hint">hover 查看数值{{ targetWeight != null ? '，虚线为目标体重' : '' }}</span>
        </div>
        <div ref="chartEl" class="hl__chart"></div>
      </div>
    </CollapseBox>

    <LoadingMask :show="trendLoading" :size="28" text="加载趋势…" />
  </section>

  <!-- 体脂 / 腰围小趋势（有数据才显示） -->
  <section v-if="hasFat || hasWaist" class="card hl__minicard">
    <BlockTitle title="体脂率 / 腰围" hint="有记录才显示" />
    <div class="hl__minigrid">
      <div v-if="hasFat" class="hl__miniblock">
        <p class="hl__mini-title"><component :is="markRaw(Percent)" :size="13" /> 体脂率 (%)</p>
        <div ref="fatEl" class="hl__mini-chart"></div>
      </div>
      <div v-if="hasWaist" class="hl__miniblock">
        <p class="hl__mini-title"><component :is="markRaw(Ruler)" :size="13" /> 腰围 (cm)</p>
        <div ref="waistEl2" class="hl__mini-chart"></div>
      </div>
    </div>
  </section>
</template>

<style lang="scss" scoped>
@use '../health';
</style>