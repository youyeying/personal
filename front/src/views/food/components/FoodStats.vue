<script setup lang="ts">
/**
 * 饮食 · 统计子页组件
 * - 能量结余：每日实际消耗 = 1.2BMR + 当天锻炼净（与首页同口径）；缺口 = 摄入 − 消耗
 * - 展示：区间汇总卡 + 摄入vs消耗双折线 + 餐次/营养构成 + 最爱Top5 + 缺口vs体重对照
 * - 缺口 ÷ 7700 ≈ 预估减脂kg，与实际体重变化对照验证记录准确性
 */
import { computed, onMounted, ref, watch } from 'vue'
import { Utensils, Flame } from '@lucide/vue'
import { getFoodStatistics } from '@/api/food'
import type { FoodItem, FoodRecord } from '@/api/food'
import { FOOD_TYPE_LABELS, MEAL_LABELS } from '@/api/food'
import { listFoodItems } from '@/api/food'
import { listExerciseItems, listExerciseRecords } from '@/api/exercise'
import { listWeightRecords } from '@/api/health'
import { formatDate } from '@/utils/format'
import { calcBmr, SEDENTARY_FACTOR } from '@/utils/activity'
import { recordNetKcal } from '@/utils/exercise'
import { fetchAllRecords } from '@/utils/fetchAll'
import { fillDaysRange } from '@/utils/daysSeries'
import { useTrendChart, TREND_GRID, trendAxes } from '@/utils/useTrendChart'
import { useUserStore } from '@/store/user'
import MetricCard from '@/components/MetricCard/MetricCard.vue'
import BlockTitle from '@/components/BlockTitle/BlockTitle.vue'
import EmptyState from '@/components/EmptyState/EmptyState.vue'
import LoadingMask from '@/components/LoadingMask/LoadingMask.vue'

const props = defineProps<{ tick: number; active: boolean }>()

const userStore = useUserStore()
/** 目标热量缺口（供能比类参考值的预算基数） */
const TARGET_GAP = computed(() => userStore.userInfo?.dietTargetGap ?? 500)

const today = formatDate(new Date())
function daysAgo(n: number) {
  const d = new Date()
  d.setDate(d.getDate() - n)
  return formatDate(d)
}
const rangeKey = ref<'7' | '30' | '90'>('30')
const range = computed<[string, string]>(() => [daysAgo(Number(rangeKey.value) - 1), today])

const loading = ref(false)
const items = ref<FoodItem[]>([])
const foodRecs = ref<FoodRecord[]>([])
const bmr = ref<number | null>(null)
/** 最新体重（营养参考值按体重计算用） */
const weight = ref<number | null>(null)

/** 按天：摄入 / 消耗 / 缺口（负=减脂）；counted = 当天有饮食记录（缺口只按记录天计） */
interface DayRow { date: string; intake: number; burn: number; gap: number; counted: boolean }
const days = ref<DayRow[]>([])

/** 周对照行 */
interface WeekRow { week: string; gap: number; predictKg: number; weightChange: number | null }
const weeks = ref<WeekRow[]>([])

function foodNutri(f: FoodItem, g: number) {
  return { kcal: f.kcal * g / 100, protein: f.protein * g / 100, fat: f.fat * g / 100, carbs: f.carbs * g / 100, sodium: f.sodium * g / 100, fiber: f.fiber * g / 100 }
}

async function load(silent = false) {
  if (!silent) loading.value = true
  try {
    const [s, itms, erecs, eitems, wres, wAll] = await Promise.all([
      getFoodStatistics({ startDate: range.value[0], endDate: range.value[1] }),
      listFoodItems(),
      // 区间全量锻炼记录（后端每页上限 100，工具循环翻页取完）
      fetchAllRecords((page, size) =>
        listExerciseRecords({ startDate: range.value[0], endDate: range.value[1], page, size })),
      listExerciseItems(),
      listWeightRecords({ page: 1, size: 1 }),
      listWeightRecords({ page: 1, size: 100 })
    ])
    items.value = itms
    foodRecs.value = s.records
    const w = wres.records?.[0]
    const wt = w ? Number(w.weight) : null
    weight.value = wt
    bmr.value = calcBmr(wt, w?.bodyFat != null ? Number(w.bodyFat) : null)

    // 按天聚合：摄入（食物）+ 消耗（锻炼净；1.2BMR 在补齐时统一加）；counted 仅食物记录置 true
    const map = new Map<string, DayRow>()
    for (const r of s.records) {
      const f = itms.find((i) => i.id === r.foodId)
      if (!f) continue
      const row = map.get(r.recordDate) ?? { date: r.recordDate, intake: 0, burn: 0, gap: 0, counted: false }
      row.intake += foodNutri(f, Number(r.grams)).kcal
      row.counted = true
      map.set(r.recordDate, row)
    }
    for (const r of erecs) {
      const item = eitems.find((i) => i.id === r.exerciseId)
      if (!item) continue
      const row = map.get(r.recordDate) ?? { date: r.recordDate, intake: 0, burn: 0, gap: 0, counted: false }
      row.burn += recordNetKcal(r, item, wt)
      map.set(r.recordDate, row)
    }
    const base = bmr.value != null ? Math.round(bmr.value * SEDENTARY_FACTOR) : null
    // 补齐区间每一天（无记录日空行），统一加基础代谢；缺口只按有饮食记录的天计算
    const rows = fillDaysRange(range.value[0], range.value[1], map, (date) => ({ date, intake: 0, burn: 0, gap: 0, counted: false }))
    for (const row of rows) {
      row.burn += base ?? 0
      row.gap = row.counted ? row.intake - row.burn : 0
    }
    days.value = rows
    renderCharts()

    // 周对照：周累计缺口 ÷ 7700 = 预估减脂；实际体重变化 = 周初 − 周末
    weeks.value = buildWeeks(wAll.records)
  } finally {
    if (!silent) loading.value = false
  }
}

/** 按自然周聚合缺口，并对齐该周实际体重变化（减=正） */
function buildWeeks(wRecords: { recordDate: string; weight: number }[]): WeekRow[] {
  const wk = new Map<string, WeekRow>()
  for (const d of days.value) {
    const key = weekKey(d.date)
    const row = wk.get(key) ?? { week: key, gap: 0, predictKg: 0, weightChange: null }
    row.gap += d.gap
    wk.set(key, row)
  }
  const res = [...wk.values()]
  const wMap = new Map<string, number>()
  for (const r of wRecords) wMap.set(r.recordDate, Number(r.weight))
  for (const row of res) {
    row.predictKg = Math.round(row.gap / 7700 * 100) / 100
    // 周一记录（或周内最早）与下周一记录（或周内最晚）
    const inWeek = wRecords.filter((r) => weekKey(r.recordDate) === row.week).sort((a, b) => a.recordDate.localeCompare(b.recordDate))
    const nextWeek = wRecords.filter((r) => weekKey(r.recordDate) > row.week).sort((a, b) => a.recordDate.localeCompare(b.recordDate))[0]
    if (inWeek.length && nextWeek) {
      row.weightChange = Math.round((inWeek[0].weight - nextWeek.weight) * 100) / 100
    } else if (inWeek.length >= 2) {
      row.weightChange = Math.round((inWeek[0].weight - inWeek[inWeek.length - 1].weight) * 100) / 100
    }
  }
  return res
}

function weekKey(date: string) {
  const d = new Date(`${date}T00:00:00`)
  const day = d.getDay() === 0 ? 7 : d.getDay()
  d.setDate(d.getDate() - (day - 1))
  return formatDate(d)
}

/* ---------- 汇总（缺口/日均按有饮食记录的天计，未记录天不计入） ---------- */
const summary = computed(() => {
  const intake = days.value.reduce((s, d) => s + d.intake, 0)
  const burn = days.value.reduce((s, d) => s + d.burn, 0)
  const gap = days.value.filter((d) => d.counted).reduce((s, d) => s + d.gap, 0)
  const n = days.value.length
  const foodDays = foodRecs.value.length ? new Set(foodRecs.value.map((r) => r.recordDate)).size : 0
  return { intake: Math.round(intake), burn: Math.round(burn), gap: Math.round(gap), avgIntake: foodDays ? Math.round(intake / foodDays) : 0, days: n, foodDays, predictKg: Math.round(gap / 7700 * 100) / 100 }
})

/* ---------- 摄入 vs 消耗双折线 ---------- */
const { el: chartEl, chart, palette } = useTrendChart(() => renderCharts())

function renderCharts() {
  const el = chart.ensure()
  if (!el) return
  const c = palette()
  el.setOption({
    grid: TREND_GRID,
    tooltip: {
      trigger: 'axis',
      formatter: (ps: any) => {
        const p = ps[0]
        const row = days.value[p.dataIndex]
        const gap = row ? Math.round(row.gap) : 0
        const gapHtml = row?.counted
          ? `缺口 <b style="color:${gap < 0 ? '#3d7a55' : '#b04a3a'}">${gap}</b> kcal`
          : '缺口 <b style="color:#92988d">未记录</b>（不计入）'
        return `${p.axisValue}<br/>摄入 <b>${ps.find((x: any) => x.seriesName === '摄入')?.value ?? '-'}</b> kcal<br/>消耗 <b>${ps.find((x: any) => x.seriesName === '消耗')?.value ?? '-'}</b> kcal<br/>${gapHtml}`
      }
    },
    ...trendAxes(days.value.map((d) => d.date.slice(5)), c),
    series: [
      // V2 语义色：摄入=--c-intake / 消耗=--c-burn（同一维度全站一致）
      { name: '摄入', type: 'line', data: days.value.map((d) => Math.round(d.intake)), symbolSize: 5, smooth: true, lineStyle: { color: c.intake, width: 2 }, itemStyle: { color: c.intake } },
      { name: '消耗', type: 'line', data: days.value.map((d) => Math.round(d.burn)), symbolSize: 5, smooth: true, lineStyle: { color: c.burn, width: 2 }, itemStyle: { color: c.burn } }
    ]
  } as any)
}

/* ---------- 构成 ---------- */
const mealDist = computed(() => {
  const order = ['breakfast', 'lunch', 'dinner', 'snack'] as const
  const map = new Map<string, number>()
  for (const r of foodRecs.value) {
    const f = items.value.find((i) => i.id === r.foodId)
    if (!f) continue
    map.set(r.mealType, (map.get(r.mealType) ?? 0) + foodNutri(f, Number(r.grams)).kcal)
  }
  const total = [...map.values()].reduce((s, x) => s + x, 0)
  return order.map((m) => ({ label: MEAL_LABELS[m], kcal: Math.round(map.get(m) ?? 0), pct: total ? Math.round((map.get(m) ?? 0) / total * 100) : 0 })).filter((x) => x.kcal > 0)
})

/**
 * 营养达标：日均摄入 vs 每日参考（医学来源）
 * - 蛋白质 ≥ 1.2g/kg 体重：《中国超重/肥胖医学营养治疗共识》限能量平衡法下限（保肌）
 * - 脂肪 = 预算 25% 供能（DRIs 2023 AMDR 20~30%E 取中）
 * - 碳水 = 预算 50% 供能（DRIs 2023 AMDR，减脂期取 40~50%E 中值）
 * - 钠 ≤ 2000mg（DRIs 2023 PI-NCD 预防慢病建议，≈盐 5g）
 * - 纤维 ≥ 25g（DRIs 2023 AI 25~30g 取下限）
 * 预算 = 1.2BMR + 日均锻炼净 − 目标缺口（与记录页横幅同口径）
 */
const nutriTargets = computed(() => {
  // 日均预算
  const base = bmr.value != null ? Math.round(bmr.value * SEDENTARY_FACTOR) : null
  const avgBurn = days.value.length ? days.value.reduce((s, d) => s + (d.burn - (base ?? 0)), 0) / days.value.length : 0
  const budget = base != null ? Math.round(base + avgBurn - TARGET_GAP.value) : null
  // 日均摄入（按有记录的天）
  const foodDays = new Set(foodRecs.value.map((r) => r.recordDate)).size || 1
  let protein = 0, fat = 0, carbs = 0, sodium = 0, fiber = 0
  for (const r of foodRecs.value) {
    const f = items.value.find((i) => i.id === r.foodId)
    if (!f) continue
    const n = foodNutri(f, Number(r.grams))
    protein += n.protein; fat += n.fat; carbs += n.carbs; sodium += n.sodium; fiber += n.fiber
  }
  protein /= foodDays; fat /= foodDays; carbs /= foodDays; sodium /= foodDays; fiber /= foodDays

  const rows: { label: string; key: string; daily: number; ref: number | null; unit: string; pct: number | null; status: 'ok' | 'low' | 'high' }[] = []
  const push = (label: string, key: string, daily: number, ref: number | null, unit: string, upper = false) => {
    const pct = ref && ref > 0 ? Math.round(daily / ref * 100) : null
    let status: 'ok' | 'low' | 'high' = 'ok'
    if (pct != null) {
      if (upper) status = pct > 100 ? 'high' : pct > 80 ? 'ok' : 'low'
      else status = pct < 60 ? 'low' : pct <= 150 ? 'ok' : 'high'
    }
    rows.push({ label, key, daily, ref, unit, pct, status })
  }
  push('蛋白质', 'protein', protein, weight.value != null ? Math.round(weight.value * 1.2) : null, 'g')
  push('脂肪', 'fat', fat, budget != null ? Math.round(budget * 0.25 / 9) : null, 'g')
  push('碳水', 'carbs', carbs, budget != null ? Math.round(budget * 0.5 / 4) : null, 'g')
  push('钠', 'sodium', sodium, 2000, 'mg', true)
  push('膳食纤维', 'fiber', fiber, 25, 'g')
  return rows
})

const topFoods = computed(() => {
  const map = new Map<string, { name: string; kcal: number }>()
  for (const r of foodRecs.value) {
    const f = items.value.find((i) => i.id === r.foodId)
    if (!f) continue
    const cur = map.get(f.name) ?? { name: f.name, kcal: 0 }
    cur.kcal += foodNutri(f, Number(r.grams)).kcal
    map.set(f.name, cur)
  }
  return [...map.values()].sort((a, b) => b.kcal - a.kcal).slice(0, 5)
})

const hasAny = computed(() => foodRecs.value.length > 0)

watch(() => props.tick, () => load(true))
watch(() => props.active, (a) => { if (a) requestAnimationFrame(() => renderCharts()) })
watch(rangeKey, () => load())
onMounted(load)
</script>

<template>
  <section class="card fds">
    <BlockTitle title="能量结余" hint="每日实际消耗 = 1.2×BMR + 当天锻炼净（与首页同口径）">
      <template #aside>
        <div class="fds__range">
          <button v-for="k in (['7', '30', '90'] as const)" :key="k" class="fds__range-btn" :class="{ 'is-active': rangeKey === k }" @click="rangeKey = k">近{{ k }}天</button>
        </div>
      </template>
    </BlockTitle>

    <EmptyState v-if="!loading && !hasAny" text="区间内还没有饮食记录，去记录页记下第一笔" @action="undefined">
      <template #icon><component :is="Utensils" :size="34" /></template>
    </EmptyState>

    <template v-else>
      <div class="fds__metrics">
        <MetricCard label="区间摄入">
          <template #default><span class="num">{{ summary.intake }}<i>kcal</i></span></template>
        </MetricCard>
        <MetricCard label="日均摄入" tone="ok">
          <template #default><span class="num">{{ summary.avgIntake }}<i>kcal</i></span></template>
        </MetricCard>
        <MetricCard label="累计缺口" :tone="summary.gap < 0 ? 'ok' : 'err'">
          <template #default><span class="num">{{ summary.gap }}<i>kcal</i></span></template>
          <template #sub v-if="summary.gap < 0">≈ 减脂 {{ summary.predictKg }} kg</template>
        </MetricCard>
        <MetricCard label="记录天数" tone="accent">
          <template #default><span class="num">{{ summary.foodDays }}<i>/{{ summary.days }}天</i></span></template>
        </MetricCard>
      </div>

      <div class="fds__panel">
        <div class="fds__panel-head">
          <span class="fds__panel-title"><component :is="Flame" :size="14" /> 摄入 vs 实际消耗</span>
          <span class="fds__panel-hint">缺口为负 = 减脂中</span>
        </div>
        <div ref="chartEl" class="fds__chart"></div>
        <div class="fds__legend">
          <span><i style="background: var(--c-intake)"></i>摄入</span>
          <span><i style="background: var(--c-burn)"></i>实际消耗（1.2BMR + 锻炼净）</span>
        </div>
      </div>

      <div class="fds__panel">
        <div class="fds__panel-head">
          <span class="fds__panel-title">餐次构成</span>
          <span class="fds__panel-hint">区间内</span>
        </div>
        <div class="fds__dist-list">
          <div v-for="d in mealDist" :key="d.label" class="fds__dist-row">
            <span class="fds__dist-name">{{ d.label }}</span>
            <div class="fds__dist-track"><div class="fds__dist-fill" :style="{ width: d.pct + '%' }"></div></div>
            <span class="fds__dist-val">{{ d.kcal }} kcal · {{ d.pct }}%</span>
          </div>
          <div v-if="!mealDist.length" class="fds__note">区间内暂无记录</div>
        </div>
      </div>

      <div class="fds__panel">
        <div class="fds__panel-head">
          <span class="fds__panel-title">营养达标</span>
          <span class="fds__panel-hint">日均摄入 vs 每日参考（DRIs 2023 + 减脂医学共识）</span>
        </div>
        <div class="fds__dist-list">
          <div v-for="n in nutriTargets" :key="n.label" class="fds__dist-row" :title="n.ref != null ? `参考 ${n.ref}${n.unit}${n.key === 'sodium' ? '（上限）' : ''} · 达成 ${n.pct}%` : '记录体重后可得参考值'">
            <span class="fds__dist-name">{{ n.label }}</span>
            <div class="fds__dist-track">
              <div
                class="fds__dist-fill" :class="[`fds__fill--${n.key}`, n.status === 'low' ? 'is-low' : n.status === 'high' ? 'is-high' : '']"
                :style="{ width: (n.pct != null ? Math.min(100, n.pct) : 0) + '%' }"
              ></div>
            </div>
            <span class="fds__dist-val">
              <b class="fds__val-daily">{{ n.key === 'sodium' ? Math.round(n.daily) : n.daily.toFixed(1) }}</b>
              <i v-if="n.ref != null">/ {{ n.ref }}{{ n.unit }} · {{ n.pct }}%</i>
              <i v-else>/ 参考待体重</i>
            </span>
          </div>
        </div>
        <p class="fds__note">蛋白质 1.2g/kg·天（保肌下限）；脂肪/碳水按预算供能比（20~30% / 40~50%）；钠 ≤2000mg；纤维 ≥25g。绿=达标、黄=偏低、红=偏高或超上限。</p>
      </div>

      <div class="fds__panel">
        <div class="fds__panel-head">
          <span class="fds__panel-title">最爱食物 Top5</span>
          <span class="fds__panel-hint">按区间内摄入热量</span>
        </div>
        <div class="fds__dist-list">
          <div v-for="(t, i) in topFoods" :key="t.name" class="fds__dist-row">
            <span class="fds__dist-name">{{ i + 1 }}. {{ t.name }}</span>
            <div class="fds__dist-track"><div class="fds__dist-fill" :style="{ width: (t.kcal / (topFoods[0]?.kcal ?? 1) * 100) + '%' }"></div></div>
            <span class="fds__dist-val">{{ Math.round(t.kcal) }} kcal</span>
          </div>
        </div>
      </div>

      <div class="fds__panel">
        <div class="fds__panel-head">
          <span class="fds__panel-title">缺口 vs 实际体重</span>
          <span class="fds__panel-hint">周累计缺口 ÷ 7700 = 预估减脂；对照实际体重变化验证记录准确性</span>
        </div>
        <div class="fds__dist-list">
          <div v-for="w in weeks" :key="w.week" class="fds__dist-row">
            <span class="fds__dist-name">{{ w.week.slice(5) }}周</span>
            <div class="fds__dist-track"><div class="fds__dist-fill" :style="{ width: Math.min(100, Math.abs(w.gap) / 7000 * 100) + '%' }"></div></div>
            <span class="fds__dist-val">
              {{ w.gap }}kcal → {{ w.predictKg }}kg
              <template v-if="w.weightChange != null"> · 实际{{ w.weightChange > 0 ? '减' : '增' }}{{ Math.abs(w.weightChange) }}kg</template>
            </span>
          </div>
          <div v-if="!weeks.length" class="fds__note">暂无完整周数据，持续记录后自动生成</div>
        </div>
      </div>
    </template>

    <LoadingMask :show="loading" :size="28" text="加载统计…" />
  </section>
</template>

<style lang="scss" scoped>
@use '../food';
</style>
