<script setup lang="ts">
/**
 * 每日总结 · 当日小汇总卡（纯展示）
 * - date：选中日期；mood：该日心情 emoji；summary：当日汇总（可空=加载中）；exerciseKcal：当日锻炼净消耗
 */
import type { DailySummary } from '@/api/dailyNote'
import InlineLoading from '@/components/loading/InlineLoading.vue'
import { moodLabel } from '../dailyNoteShared'

defineProps<{
  date: string
  mood: string | null
  summary: DailySummary | null
  exerciseKcal?: number
}>()
</script>

<template>
  <div class="card daily-note__summary">
    <h3 class="daily-note__summary-title">
      <span class="num">{{ date }}</span>
      <span class="daily-note__summary-sub">{{ mood ? `心情 ${mood} ${moodLabel(mood)}` : '未写总结' }}</span>
    </h3>
    <div v-if="summary" class="daily-note__stats">
      <div class="daily-note__stat">
        <span class="daily-note__stat-label">支出</span>
        <span class="daily-note__stat-value num">¥{{ summary.expense.toFixed(2) }}</span>
      </div>
      <div class="daily-note__stat">
        <span class="daily-note__stat-label">锻炼</span>
        <span class="daily-note__stat-value num">{{ exerciseKcal ?? 0 }} kcal</span>
      </div>
      <div class="daily-note__stat">
        <span class="daily-note__stat-label">体重</span>
        <span class="daily-note__stat-value num">
          {{ summary.weight != null ? summary.weight.toFixed(1) + 'kg' : '—' }}
          <em v-if="summary.weightChange != null" class="daily-note__stat-delta" :class="summary.weightChange > 0 ? 'is-up' : 'is-down'">
            {{ summary.weightChange > 0 ? '▲' : '▼' }}{{ Math.abs(summary.weightChange).toFixed(1) }}
          </em>
        </span>
      </div>
      <div class="daily-note__stat">
        <span class="daily-note__stat-label">学习</span>
        <span class="daily-note__stat-value num">{{ summary.learnCount }}条 · {{ summary.learnMinutes }}m</span>
      </div>
      <div class="daily-note__stat">
        <span class="daily-note__stat-label">开发</span>
        <span class="daily-note__stat-value num">{{ summary.devMinutes }}m</span>
      </div>
    </div>
    <InlineLoading v-else class="daily-note__summary-empty" :size="20" text="正在加载当日汇总…" color="var(--sk-mod)" />
  </div>
</template>

<style lang="scss" scoped>
@use '../dailyNote';
</style>