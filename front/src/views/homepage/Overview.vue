<script setup lang="ts">
/**
 * 首页概览（views/homepage）
 * 四区块自上而下：
 * - 问候区：时段问候 + 日期星期 + 今日四模块完成度徽标
 * - 今日四指标：支出(含笔数) / 收入 / 体重(含较上次变化) / 学习(含条数)
 * - 本月两卡：左 收支汇总 + 支出分类构成；右 减重进度 + 本月学习时长
 * - 快捷入口：记一笔 / 体重打卡 / 记学习
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, Activity, BookOpen } from '@lucide/vue'
import { getOverview } from '@/api/overview'
import type { OverviewData } from '@/api/overview'
import { listWeightRecords } from '@/api/health'
import { listExerciseItems, listExerciseRecords } from '@/api/exercise'
import { listFoodItems, listFoodRecords } from '@/api/food'
import { useUserStore } from '@/store/user'
import { formatMoney, formatDate } from '@/utils/format'
import { calcBmr, SEDENTARY_FACTOR } from '@/utils/activity'
import { recordNetKcal } from '@/utils/exercise'
import MetricCard from '@/components/MetricCard/MetricCard.vue'
import ProgressRing from '@/components/ProgressRing/ProgressRing.vue'
import LoadingMask from '@/components/LoadingMask/LoadingMask.vue'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const overviewLoading = ref(false)
const consumeLoading = ref(false)
const overview = ref<OverviewData | null>(null)

/**
 * 每日消耗（双块：本周实际日均 + 今日实际，v1.35.0 废除活动系数档位）
 * 每天实际消耗 = BMR × 1.2（久坐基准） + 当天锻炼净消耗（按体重快照）
 * 本周平均 = 周一~今天 每天实际消耗之和 ÷ 本周已过天数（含今天，实时）
 */
const consumeInfo = ref<{
  bmr: number | null
  /** 本周平均实际消耗 kcal/天（含今天） */
  weekAverage: number | null
  /** 本周已过天数（周一~今天） */
  weekDays: number
  /** 本周锻炼净消耗合计 */
  weekExercise: number
  /** 今日实际消耗 = 1.2BMR + 今日锻炼净 */
  todayActual: number | null
  /** 今日锻炼净消耗 */
  todayExercise: number
  /** 今日饮食摄入 kcal */
  todayIntake: number
  /** 今日剩余额度 = 预算(1.2BMR+锻炼−500) − 已摄入 */
  todayRemain: number | null
  /** 今日摄入预算（进度环分母） */
  todayBudget: number | null
  /** 本周饮食摄入合计 */
  weekIntake: number
  /** 本周累计缺口 ≈ 预估减脂 kg（缺口=周消耗−周摄入，÷7700） */
  weekPredictKg: number | null
} | null>(null)

async function load() {
  overviewLoading.value = true
  try {
    overview.value = await getOverview()
  } finally {
    overviewLoading.value = false
  }
}

/** 本周起始（自然周周一） */
const weekStart = (() => {
  const d = new Date()
  const day = d.getDay() === 0 ? 7 : d.getDay()
  d.setDate(d.getDate() - (day - 1))
  return formatDate(d)
})()

/** 每日消耗卡数据：体重(含体脂) + 动作字典 + 本周锻炼/饮食记录，独立加载互不阻塞主体 */
async function loadConsume() {
  consumeLoading.value = true
  try {
    const [wres, itms, erecs, fitms, frecs] = await Promise.all([
      listWeightRecords({ page: 1, size: 1 }),
      listExerciseItems(),
      listExerciseRecords({ startDate: weekStart, page: 1, size: 100 }),
      listFoodItems(),
      listFoodRecords({ startDate: weekStart, page: 1, size: 100 })
    ])
    const w = wres.records?.[0]
    const weight = w ? Number(w.weight) : null
    const bodyFat = w?.bodyFat != null ? Number(w.bodyFat) : null
    const bmr = calcBmr(weight, bodyFat)
    // 本周 / 今日锻炼净消耗（按记录时体重快照）；countedExercise = 有饮食记录当天的锻炼净（缺口口径）
    const today = formatDate(new Date())
    const countedDates = new Set(frecs.records.map((r) => r.recordDate))
    let weekExercise = 0
    let countedExercise = 0
    let todayExercise = 0
    for (const r of erecs.records) {
      const item = itms.find((i) => i.id === r.exerciseId)
      if (!item) continue
      const net = recordNetKcal(r, item, weight)
      weekExercise += net
      if (countedDates.has(r.recordDate)) countedExercise += net
      if (r.recordDate === today) todayExercise += net
    }
    // 本周 / 今日饮食摄入（每100g × 克数 ÷ 100）
    let weekIntake = 0
    let todayIntake = 0
    for (const r of frecs.records) {
      const f = fitms.find((i) => i.id === r.foodId)
      if (!f) continue
      const kcal = f.kcal * Number(r.grams) / 100
      weekIntake += kcal
      if (r.recordDate === today) todayIntake += kcal
    }
    // 本周已过天数（周一~今天，含今天；按今天 0 点算差值，避免用 Date.now() 含时刻导致 round 在午后多算一天）
    const todayMidnight = new Date(`${today}T00:00:00`).getTime()
    const weekDays = Math.round((todayMidnight - new Date(`${weekStart}T00:00:00`).getTime()) / 86400000) + 1
    const base = bmr != null ? Math.round(bmr * SEDENTARY_FACTOR) : null
    // 今日剩余额度 = 预算(1.2BMR + 锻炼净 − 目标缺口500) − 已摄入
    const budget = base != null ? base + todayExercise - 500 : null
    // 本周预估减脂 = (周消耗 − 周摄入) ÷ 7700；未记录吃饭的天不计入（消耗也只计有记录天）
    const weekPredictKg = base != null && countedDates.size > 0
      ? Math.round(((base * countedDates.size + countedExercise - weekIntake) / 7700) * 100) / 100
      : null
    consumeInfo.value = {
      bmr,
      weekDays,
      weekExercise,
      todayExercise,
      todayIntake: Math.round(todayIntake),
      weekIntake: Math.round(weekIntake),
      todayRemain: budget != null ? Math.round(budget - todayIntake) : null,
      todayBudget: budget != null ? Math.round(budget) : null,
      weekPredictKg,
      todayActual: base != null ? base + todayExercise : null,
      weekAverage: base != null ? Math.round((base * weekDays + weekExercise) / weekDays) : null
    }
  } finally {
    consumeLoading.value = false
  }
}
onMounted(() => {
  load()
  loadConsume()
})

/* ---------- 问候区 ---------- */
const WEEKDAYS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

/** 时段问候语 */
const greetText = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 12) return '早上好'
  if (h < 18) return '下午好'
  return '晚上好'
})

/** 日期 + 星期，如 2026-08-29 周六 */
const dateText = computed(() => {
  const d = new Date()
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${mm}-${dd} ${WEEKDAYS[d.getDay()]}`
})

/** 今日完成度四项（记账/体重/学习/每日总结） */
const doneItems = computed(() => {
  const done = overview.value?.today.done
  if (!done) return []
  return [
    { label: '记账', ok: done.expense },
    { label: '体重', ok: done.weight },
    { label: '学习', ok: done.learn },
    { label: '每日总结', ok: done.note }
  ]
})
const doneCount = computed(() => doneItems.value.filter((i) => i.ok).length)

/* ---------- 今日指标 ---------- */
/** 体重较上次变化（kg，负为降） */
const weightDelta = computed(() => {
  const m = overview.value?.month
  const latest = overview.value?.today.weight ?? m?.latestWeight ?? null
  const prev = m?.previousWeight ?? null
  if (latest == null || prev == null) return null
  return latest - prev
})
const weightDeltaText = computed(() => {
  const d = weightDelta.value
  if (d == null) return '暂无历史对照'
  if (Math.abs(d) < 0.05) return '较上次持平'
  return `${d < 0 ? '↓' : '↑'} ${Math.abs(d).toFixed(1)} 较上次`
})
const weightDeltaClass = computed(() => {
  const d = weightDelta.value
  return d == null ? '' : d < 0 ? 'ov__trend-down' : 'ov__trend-up'
})

/** 学习时长文案：<60 分钟 → "X 分钟"，否则 "Xh Ym" */
function learnText(min: number | null | undefined): string {
  const m = min ?? 0
  if (m < 60) return `${m} 分钟`
  const h = Math.floor(m / 60)
  const rest = m % 60
  return rest ? `${h}h ${rest}m` : `${h}h`
}

/* ---------- 本月卡 ---------- */
/** 本月支出总额（分类占比分母） */
const monthExpense = computed(() => overview.value?.month.expense ?? 0)

/** 分类条比例（0-100%，保留整数） */
const catPercents = computed(() =>
  (overview.value?.month.expenseCategories ?? []).map((c) => {
    const total = monthExpense.value
    return total > 0 ? Math.round((c.amount / total) * 100) : 0
  })
)

/** 减重进度 %（0-100，目标未设或无可比数据返回 null） */
const goalPct = computed(() => {
  const m = overview.value?.month
  const s = m?.startWeight, l = m?.latestWeight, t = m?.targetWeight
  if (s == null || l == null || t == null || s <= t) return null
  const pct = ((s - l) / (s - t)) * 100
  return Math.min(100, Math.max(0, pct))
})
/** 已减 kg */
const lostKg = computed(() => {
  const s = overview.value?.month.startWeight, l = overview.value?.month.latestWeight
  if (s == null || l == null) return null
  return s - l
})
/** 还需减 kg */
const remainKg = computed(() => {
  const l = overview.value?.month.latestWeight, t = overview.value?.month.targetWeight
  if (l == null || t == null) return null
  return l - t
})
/** 已达标（最新体重 ≤ 目标体重） */
const goalReached = computed(() => {
  const m = overview.value?.month
  return m?.latestWeight != null && m?.targetWeight != null && m.latestWeight <= m.targetWeight
})

/** 本月学习日均（分钟） */
const learnDailyAvg = computed(() => {
  const min = overview.value?.month.learnMinutes ?? 0
  if (!min) return 0
  const day = new Date().getDate()
  return Math.round(min / day)
})

/** 快捷入口跳转 */
function go(path: string) {
  router.push(path)
}
</script>

<template>
  <div class="ov-page">
    <LoadingMask :show="overviewLoading" :size="30" text="正在加载概览…" />
    <template v-if="overview">
      <!-- 1. 问候区 -->
      <header class="ov-greet">
        <div class="ov-greet__text">
          <h1 class="ov-greet__title">{{ greetText }}<template v-if="userStore.userInfo?.nickname">，{{ userStore.userInfo.nickname }}</template></h1>
          <p class="ov-greet__date num">{{ dateText }}</p>
        </div>
        <div class="ov-greet__done">
          <span v-for="item in doneItems" :key="item.label" class="ov-done-item">
            <span class="ov-done-dot" :class="item.ok ? 'is-ok' : 'is-miss'" />
            {{ item.label }}
          </span>
          <b class="num">{{ doneCount }}/{{ doneItems.length }}</b>
        </div>
      </header>

      <!-- 2. 今日四指标 -->
      <section class="ov-today-grid">
        <MetricCard :label="'今日支出'">
          <template #default>
            <span class="num is-err">{{ formatMoney(overview.today.expense) }}</span>
          </template>
          <template #sub>{{ overview.today.expenseCount > 0 ? `${overview.today.expenseCount} 笔` : (overview.today.incomeCount > 0 ? '暂无支出' : '暂无记录') }}</template>
        </MetricCard>
        <MetricCard :label="'可支配余额'" :tone="overview.disposable < 0 ? 'err' : 'ok'">
          <template #default><span class="num">{{ formatMoney(overview.disposable, true) }}</span></template>
          <template #sub>累计收入 − 支出</template>
        </MetricCard>
        <MetricCard :label="'今日体重'">
          <template #default>
            <template v-if="overview.today.weight != null"><span class="num">{{ overview.today.weight }}</span><i>kg</i></template>
            <span v-else class="num">—</span>
          </template>
          <template #sub>
            <span v-if="overview.today.weight != null" class="ov__weight-trend" :class="weightDeltaClass">{{ weightDeltaText }}</span>
            <span v-else>今日未打卡</span>
          </template>
        </MetricCard>
        <MetricCard :label="'今日学习'">
          <template #default>
            <span class="num">{{ overview.today.learnMinutes || '—' }}</span><i>分钟</i>
          </template>
          <template #sub>{{ overview.today.learnCount > 0 ? `${overview.today.learnCount} 条记录` : '暂无记录' }}</template>
        </MetricCard>
      </section>

      <!-- 2.5 每日消耗（双口径两块，独立加载不阻塞主体） -->
      <div class="ov-tdee-wrap">
        <template v-if="consumeInfo?.bmr != null">
          <div class="ov-tdee-grid">
            <!-- 块1：本周平均实际消耗（基础×1.2 + 本周锻炼，除以已过天数含今天） -->
            <section class="ov-tdee-block">
              <p class="ov-tdee-block__label">本周平均实际消耗</p>
              <p class="ov-tdee-block__value num">{{ consumeInfo.weekAverage }}<i>kcal/天</i></p>
              <div class="ov-tdee-block__rows">
                <div class="ov-tdee-block__row"><span>基础 × 1.2</span><b class="num">{{ Math.round((consumeInfo.bmr ?? 0) * 1.2) }} kcal/天</b></div>
                <div class="ov-tdee-block__row"><span>本周锻炼</span><b class="num">+{{ consumeInfo.weekExercise }} kcal</b></div>
                <div class="ov-tdee-block__row"><span>本周摄入</span><b class="num">{{ consumeInfo.weekIntake }} kcal</b></div>
                <div class="ov-tdee-block__row">
                  <span>本周缺口 ≈ 预估</span>
                  <b class="num" :class="(consumeInfo.weekPredictKg ?? 0) > 0 ? 'is-ok' : 'is-err'">{{ consumeInfo.weekPredictKg }} kg</b>
                </div>
                <div class="ov-tdee-block__row"><span>本周已过</span><b class="num">{{ consumeInfo.weekDays }} 天</b></div>
              </div>
            </section>
            <!-- 块2：今日能量结余（实际消耗 vs 摄入；V2 右侧摄入进度环） -->
            <section class="ov-tdee-block">
              <div class="ov-tdee-block__head">
                <div>
                  <p class="ov-tdee-block__label">今日能量结余</p>
                  <p class="ov-tdee-block__value num" :class="(consumeInfo.todayRemain ?? 0) >= 0 ? 'is-ok' : 'is-err'">
                    {{ consumeInfo.todayRemain }}<i>kcal 剩余</i>
                  </p>
                </div>
                <ProgressRing
                  v-if="consumeInfo.todayBudget"
                  :value="consumeInfo.todayIntake"
                  :max="consumeInfo.todayBudget"
                  ring-color="var(--c-intake)"
                  :label="`摄入 ${consumeInfo.todayIntake}/${consumeInfo.todayBudget}`"
                  :size="72"
                />
              </div>
              <div class="ov-tdee-block__rows">
                <div class="ov-tdee-block__row"><span>今日摄入</span><b class="num">{{ consumeInfo.todayIntake }} kcal</b></div>
                <div class="ov-tdee-block__row"><span>今日实际消耗</span><b class="num">{{ consumeInfo.todayActual }} kcal</b></div>
                <div class="ov-tdee-block__row"><span>基础 × 1.2（久坐）</span><b class="num">{{ Math.round((consumeInfo.bmr ?? 0) * 1.2) }} kcal</b></div>
                <div class="ov-tdee-block__row"><span>今日锻炼</span><b class="num">+{{ consumeInfo.todayExercise }} kcal</b></div>
              </div>
            </section>
          </div>
          <p class="ov-tdee-hint">左为本周（周一~今天）实际消耗每日平均与预估减脂（缺口 ÷ 7700）· 右为今日结余 = 预算(1.2BMR+锻炼−目标缺口) − 已摄入，负值即已超预算</p>
        </template>
        <section v-else-if="consumeInfo" class="ov-tdee ov-tdee--empty">
          暂无体脂率数据，去健康页「体重打卡」填一次体脂率后显示每日消耗。
        </section>
        <LoadingMask :show="consumeLoading" :size="22" text="加载消耗数据…" />
      </div>

      <!-- 3. 本月两卡 -->
      <section class="ov-month-grid">
        <div class="ov-card">
          <h2 class="ov-card__title">本月收支</h2>
          <div class="ov-balance">
            <div class="ov-balance__item">
              <span class="ov-balance__label">支出</span>
              <span class="ov-balance__value num is-err">{{ formatMoney(overview.month.expense) }}</span>
            </div>
            <div class="ov-balance__item">
              <span class="ov-balance__label">收入</span>
              <span class="ov-balance__value num">{{ formatMoney(overview.month.income) }}</span>
            </div>
            <div class="ov-balance__item">
              <span class="ov-balance__label">结余</span>
              <span class="ov-balance__value num" :class="overview.month.balance >= 0 ? 'is-ok' : 'is-err'">{{ formatMoney(overview.month.balance, true) }}</span>
            </div>
          </div>
          <div v-if="overview.month.expenseCategories?.length" class="ov-cat">
            <div class="ov-cat-bar">
              <span
                v-for="(item, i) in overview.month.expenseCategories"
                :key="item.name"
                class="ov-cat-bar__seg"
                :class="`s${i % 5}`"
                :style="{ width: catPercents[i] + '%' }"
              />
            </div>
            <div class="ov-cat-legend">
              <span v-for="(item, i) in overview.month.expenseCategories ?? []" :key="item.name" class="ov-cat-legend__item">
                <span class="ov-cat-legend__dot" :class="`s${i % 5}`" />
                {{ item.name }} <b class="num">{{ formatMoney(item.amount) }}</b>
              </span>
            </div>
          </div>
          <p v-else class="ov-card__empty">本月暂无支出记录</p>
        </div>

        <div class="ov-card">
          <h2 class="ov-card__title">目标进度</h2>
          <div v-if="goalReached" class="ov-goal-ok">已达成目标体重 🎉</div>
          <div v-else class="ov-goal">
            <div class="ov-goal__head">
              <span class="ov-goal__label">减重进度</span>
              <span class="ov-goal__value num">{{ overview.month.latestWeight ?? '—' }} / {{ overview.month.targetWeight ?? '—' }} kg</span>
            </div>
            <div class="ov-goal__track">
              <div class="ov-goal__fill" :style="{ width: (goalPct ?? 0) + '%' }" />
            </div>
            <div class="ov-goal__foot">
              <span v-if="goalPct != null" class="ov-goal__pct num">{{ goalPct.toFixed(1) }}%</span>
              <span v-else>未设置目标体重</span>
              <span v-if="lostKg != null && remainKg != null" class="ov-goal__tip">
                已减 {{ lostKg.toFixed(1) }} kg · 还需 {{ remainKg.toFixed(1) }} kg
              </span>
            </div>
          </div>

          <div class="ov-goal ov-goal--learn">
            <div class="ov-goal__head">
              <span class="ov-goal__label">本月学习</span>
              <span class="ov-goal__value num">{{ learnText(overview.month.learnMinutes) }}</span>
            </div>
            <div class="ov-goal__foot">
              <span class="ov-goal__pct">{{ (overview.month.learnMinutes ?? 0) > 0 ? `${(overview.month.learnMinutes ?? 0)} 分钟` : '暂无记录' }}</span>
              <span class="ov-goal__tip">{{ learnDailyAvg > 0 ? `日均 ${learnDailyAvg} 分钟` : '日均 0 分钟' }}</span>
            </div>
            <p class="ov-goal__hint">月度学习目标待「目标体系」接入后显示进度条</p>
          </div>
        </div>
      </section>

      <!-- 4. 快捷入口 -->
      <section class="ov-actions">
        <button class="ov-action" @click="go('/expense')">
          <Plus :size="20" /> 记一笔
        </button>
        <button class="ov-action" @click="go('/health')">
          <Activity :size="20" /> 体重打卡
        </button>
        <button class="ov-action" @click="go('/learn')">
          <BookOpen :size="20" /> 记学习
        </button>
      </section>
    </template>
  </div>
</template>

<style lang="scss" scoped>
@use './overview';
</style>