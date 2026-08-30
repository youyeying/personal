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
import { useUserStore } from '@/store/user'
import { formatMoney } from '@/utils/format'
import MetricCard from '@/components/MetricCard/MetricCard.vue'
import InlineLoading from '@/components/loading/InlineLoading.vue'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const overview = ref<OverviewData | null>(null)

async function load() {
  loading.value = true
  try {
    overview.value = await getOverview()
  } finally {
    loading.value = false
  }
}
onMounted(load)

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
    <InlineLoading v-if="loading && !overview" text="正在加载概览…" />

    <template v-else-if="overview">
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
        <MetricCard :label="'今日收入'">
          <template #default>
            <span v-if="overview.today.income > 0" class="num is-ok">{{ formatMoney(overview.today.income) }}</span>
            <span v-else class="num">—</span>
          </template>
          <template #sub>{{ overview.today.incomeCount > 0 ? `${overview.today.incomeCount} 笔` : '暂无记录' }}</template>
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