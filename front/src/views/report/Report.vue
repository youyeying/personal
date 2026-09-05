<script setup lang="ts">
/**
 * 周报/月报 · 跨模块复盘页（全前端聚合，复用各模块 list API + fetchAllRecords）
 * - 周期切换：本周（周一~今天）/ 本月 / 上月
 * - 汇总：记账收支结余 / 锻炼次数与净消耗 / 饮食摄入与缺口（只计有记录天）/ 学习时长 / 体重变化 / 每日总结心情
 * - 消耗口径与全站一致：锻炼净消耗按记录时体重快照（recordNetKcal），不做历史重算
 */
import { computed, onMounted, ref, watch } from 'vue'
import { Notebook } from '@element-plus/icons-vue'
import { listExpenseRecords } from '@/api/expense'
import type { ExpenseRecord } from '@/api/expense'
import { listFoodItems, listFoodRecords } from '@/api/food'
import type { FoodRecord } from '@/api/food'
import { listExerciseItems, listExerciseRecords } from '@/api/exercise'
import type { ExerciseRecord } from '@/api/exercise'
import { listWeightRecords } from '@/api/health'
import { listLearnRecords } from '@/api/learn'
import type { LearnRecord } from '@/api/learn'
import { listDailyNotes } from '@/api/dailyNote'
import { formatDate } from '@/utils/format'
import { recordNetKcal } from '@/utils/exercise'
import { fetchAllRecords } from '@/utils/fetchAll'
import MetricCard from '@/components/MetricCard/MetricCard.vue'
import BlockTitle from '@/components/BlockTitle/BlockTitle.vue'
import EmptyState from '@/components/EmptyState/EmptyState.vue'
import LoadingMask from '@/components/LoadingMask/LoadingMask.vue'

/* ---------- 周期 ---------- */
type RangeKey = 'week' | 'month' | 'lastMonth'
const rangeKey = ref<RangeKey>('week')
const today = formatDate(new Date())

function weekStartStr() {
  const d = new Date()
  const day = d.getDay() === 0 ? 7 : d.getDay()
  d.setDate(d.getDate() - (day - 1))
  return formatDate(d)
}

const range = computed<[string, string]>(() => {
  if (rangeKey.value === 'week') return [weekStartStr(), today]
  const d = new Date()
  if (rangeKey.value === 'month') return [formatDate(new Date(d.getFullYear(), d.getMonth(), 1)), today]
  // 上月整月
  const start = new Date(d.getFullYear(), d.getMonth() - 1, 1)
  const end = new Date(d.getFullYear(), d.getMonth(), 0)
  return [formatDate(start), formatDate(end)]
})

const rangeLabel = computed(() => {
  const [s, e] = range.value
  return `${s} ~ ${e}`
})

/* ---------- 汇总数据 ---------- */
const loading = ref(false)
const report = ref<{
  expenseOut: number; expenseIn: number
  exCount: number; exDays: number; exKcal: number
  fdDays: number; fdIntake: number; fdGap: number | null
  learnMinutes: number; learnCount: number
  weightStart: number | null; weightEnd: number | null
  moodCounts: { mood: string; n: number }[]
  noteDays: number
} | null>(null)

async function load() {
  if (!loading.value) loading.value = true
  try {
    const [start, end] = range.value
    const [expenses, foodRecs, foodItems, exRecs, exItems, weights, learns, notes] = await Promise.all([
      fetchAllRecords<ExpenseRecord>((page, size) => listExpenseRecords({ startDate: start, endDate: end, page, size })),
      fetchAllRecords<FoodRecord>((page, size) => listFoodRecords({ startDate: start, endDate: end, page, size })),
      listFoodItems(),
      fetchAllRecords<ExerciseRecord>((page, size) => listExerciseRecords({ startDate: start, endDate: end, page, size })),
      listExerciseItems(),
      listWeightRecords({ page: 1, size: 100 }),
      fetchAllRecords<LearnRecord>((page, size) => listLearnRecords({ startDate: start, endDate: end, page, size })),
      listDailyNotes(start, end)
    ])

    // 记账（type：1 支出 / 2 收入，与记账模块分类口径一致）
    let expenseOut = 0
    let expenseIn = 0
    for (const r of expenses) {
      if (r.type === 2) expenseIn += Number(r.amount)
      else expenseOut += Number(r.amount)
    }

    // 锻炼（净消耗按记录时体重快照；weight 仅作无快照记录的回退）
    const wSorted = [...weights.records].sort((a, b) => a.recordDate.localeCompare(b.recordDate))
    const fallbackW = wSorted.length ? Number(wSorted[wSorted.length - 1].weight) : null
    let exKcal = 0
    const exDaySet = new Set<string>()
    for (const r of exRecs) {
      const item = exItems.find((i) => i.id === r.exerciseId)
      if (!item) continue
      exKcal += recordNetKcal(r, item, fallbackW)
      exDaySet.add(r.recordDate)
    }

    // 饮食（摄入/缺口只按有记录天；消耗按 1.2BMR + 锻炼净需 BMR——缺体脂则无法算缺口，退化为仅展示摄入）
    let fdIntake = 0
    const fdDaySet = new Set<string>()
    for (const r of foodRecs) {
      const f = foodItems.find((i) => i.id === r.foodId)
      if (!f) continue
      fdIntake += f.kcal * Number(r.grams) / 100
      fdDaySet.add(r.recordDate)
    }
    // 基础消耗（有体脂快照才可估）：BMR × 1.2 × 天数 + 记录天锻炼净
    const wFat = [...weights.records].reverse().find((w) => w.bodyFat != null)
    let fdGap: number | null = null
    if (wFat && fallbackW != null) {
      const bmr = 370 + 21.6 * fallbackW * (1 - Number(wFat.bodyFat) / 100)
      const base = bmr * 1.2 * fdDaySet.size
      const fdExKcal = exRecs
        .filter((r) => fdDaySet.has(r.recordDate))
        .reduce((s, r) => {
          const item = exItems.find((i) => i.id === r.exerciseId)
          return item ? s + recordNetKcal(r, item, fallbackW) : s
        }, 0)
      fdGap = Math.round(fdIntake - base - fdExKcal)
    }

    // 学习
    const learnMinutes = learns.reduce((s, r) => s + (r.duration ?? 0), 0)

    // 体重：区间内首末（无记录则就近取）
    const inRange = wSorted.filter((w) => w.recordDate >= start && w.recordDate <= end)
    let weightStart: number | null = null
    let weightEnd: number | null = null
    if (inRange.length >= 2) {
      weightStart = Number(inRange[0].weight)
      weightEnd = Number(inRange[inRange.length - 1].weight)
    }

    // 心情统计
    const moodMap = new Map<string, number>()
    for (const n of notes) {
      if (n.mood) moodMap.set(n.mood, (moodMap.get(n.mood) ?? 0) + 1)
    }
    const moodCounts = [...moodMap.entries()]
      .map(([mood, n]) => ({ mood, n }))
      .sort((a, b) => b.n - a.n)

    report.value = {
      expenseOut: Math.round(expenseOut),
      expenseIn: Math.round(expenseIn),
      exCount: exRecs.length,
      exDays: exDaySet.size,
      exKcal: Math.round(exKcal),
      fdDays: fdDaySet.size,
      fdIntake: Math.round(fdIntake),
      fdGap,
      learnMinutes,
      learnCount: learns.length,
      weightStart,
      weightEnd,
      moodCounts,
      noteDays: notes.length
    }
  } finally {
    loading.value = false
  }
}

/** 学习时长展示（Xh Ym） */
const learnText = computed(() => {
  const r = report.value
  if (!r) return '0'
  const h = Math.floor(r.learnMinutes / 60)
  const m = r.learnMinutes % 60
  return h ? `${h}h ${m}m` : `${m}m`
})

/** 区间总天数 */
const totalDays = computed(() => {
  const [s, e] = range.value
  return Math.round((new Date(`${e}T00:00:00`).getTime() - new Date(`${s}T00:00:00`).getTime()) / 86400000) + 1
})

watch(rangeKey, () => load())
onMounted(load)
</script>

<template>
  <section class="card rp">
    <BlockTitle title="周报 / 月报" :hint="rangeLabel">
      <template #aside>
        <div class="rp__range">
          <button
            v-for="k in (['week', 'month', 'lastMonth'] as const)" :key="k"
            class="rp__range-btn" :class="{ 'is-active': rangeKey === k }"
            @click="rangeKey = k"
          >{{ k === 'week' ? '本周' : k === 'month' ? '本月' : '上月' }}</button>
        </div>
      </template>
    </BlockTitle>

    <EmptyState v-if="!loading && !report" text="该周期还没有任何记录">
      <template #icon><component :is="Notebook" :size="34" /></template>
    </EmptyState>

    <template v-else-if="report">
      <!-- 记账 -->
      <div class="rp__group">
        <div class="rp__group-title">记账</div>
        <div class="rp__metrics">
          <MetricCard label="支出">
            <template #default><span class="num">{{ report.expenseOut }}<i>元</i></span></template>
          </MetricCard>
          <MetricCard label="收入" tone="ok">
            <template #default><span class="num">{{ report.expenseIn }}<i>元</i></span></template>
          </MetricCard>
          <MetricCard label="结余" :tone="report.expenseIn - report.expenseOut >= 0 ? 'ok' : 'err'">
            <template #default><span class="num">{{ report.expenseIn - report.expenseOut }}<i>元</i></span></template>
          </MetricCard>
        </div>
      </div>

      <!-- 身体 -->
      <div class="rp__group">
        <div class="rp__group-title">身体</div>
        <div class="rp__metrics">
          <MetricCard label="锻炼次数">
            <template #default><span class="num">{{ report.exCount }}<i>次</i></span></template>
            <template #sub>覆盖 {{ report.exDays }}/{{ totalDays }} 天</template>
          </MetricCard>
          <MetricCard label="锻炼净消耗" tone="ok">
            <template #default><span class="num">{{ report.exKcal }}<i>kcal</i></span></template>
          </MetricCard>
          <MetricCard label="饮食记录">
            <template #default><span class="num">{{ report.fdDays }}<i>天</i></span></template>
            <template #sub>共摄入 {{ report.fdIntake }} kcal</template>
          </MetricCard>
          <MetricCard label="热量缺口" :tone="report.fdGap == null ? '' : report.fdGap < 0 ? 'ok' : 'err'">
            <template #default>
              <span class="num">{{ report.fdGap == null ? '—' : report.fdGap }}<i v-if="report.fdGap != null">kcal</i></span>
            </template>
            <template #sub v-if="report.fdGap != null && report.fdGap < 0">≈ 减脂 {{ (Math.abs(report.fdGap) / 7700).toFixed(2) }} kg</template>
            <template #sub v-else-if="report.fdGap == null">记录体重+体脂后可得</template>
          </MetricCard>
          <MetricCard label="体重变化">
            <template #default>
              <span v-if="report.weightStart != null && report.weightEnd != null" class="num" :class="report.weightEnd < report.weightStart ? 'rp__good' : 'rp__bad'">
                {{ (report.weightEnd - report.weightStart).toFixed(1) }}<i>kg</i>
              </span>
              <span v-else class="num">—</span>
            </template>
            <template #sub v-if="report.weightStart != null">{{ report.weightStart }} → {{ report.weightEnd ?? '—' }} kg</template>
          </MetricCard>
        </div>
      </div>

      <!-- 学习与心情 -->
      <div class="rp__group">
        <div class="rp__group-title">学习与心情</div>
        <div class="rp__metrics">
          <MetricCard label="学习时长" tone="ok">
            <template #default><span class="num">{{ learnText }}</span></template>
            <template #sub>{{ report.learnCount }} 条记录</template>
          </MetricCard>
          <MetricCard label="写总结">
            <template #default><span class="num">{{ report.noteDays }}<i>天</i></span></template>
            <template #sub>共 {{ totalDays }} 天</template>
          </MetricCard>
          <MetricCard label="心情 Top">
            <template #default><span class="rp__mood">{{ report.moodCounts.length ? `${report.moodCounts[0].mood} ×${report.moodCounts[0].n}` : '—' }}</span></template>
          </MetricCard>
        </div>
        <div v-if="report.moodCounts.length" class="rp__mood-list">
          <span v-for="m in report.moodCounts" :key="m.mood" class="rp__mood-chip">{{ m.mood }} ×{{ m.n }}</span>
        </div>
      </div>
    </template>

    <LoadingMask :show="loading" :size="28" text="汇总中…" />
  </section>
</template>

<style lang="scss" scoped>
@use './report';
</style>
