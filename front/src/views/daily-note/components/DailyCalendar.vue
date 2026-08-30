<script setup lang="ts">
/**
 * 每日总结 · 班期日历子组件（纯展示 + 选中交互）
 * - cycleDays：班期日期串数组；columnsTemplate：列数（grid-template-columns）
 * - noteMap：日期→总结（取心情 emoji）；shiftMap：日期→班次；selectedDate/today：选中与今日
 * - 点击某天 emit('select', date)
 */
import { formatDate } from '@/utils/format'
import { shiftMetaOf } from '@/api/shift'
import { WEEK_LABELS, weekdayOf } from '../dailyNoteShared'

defineProps<{
  cycleDays: string[]
  columnsTemplate: string
  noteMap: Record<string, { mood: string | null }>
  shiftMap: Record<string, string>
  selectedDate: string
}>()

const emit = defineEmits<{
  (e: 'select', date: string): void
}>()
</script>

<template>
  <section class="card daily-note__cal">
    <div class="daily-note__week" :style="{ gridTemplateColumns: columnsTemplate }">
      <span v-for="w in WEEK_LABELS" :key="w" class="daily-note__week-item">{{ w }}</span>
    </div>
    <div class="daily-note__grid" :style="{ gridTemplateColumns: columnsTemplate }">
      <button
        v-for="d in cycleDays"
        :key="d"
        class="daily-note__day"
        :class="{
          'daily-note__day--selected': d === selectedDate,
          'daily-note__day--today': d === formatDate(new Date())
        }"
        type="button"
        @click="emit('select', d)"
      >
        <span class="daily-note__day-num num">{{ d.slice(8) }}</span>
        <span class="daily-note__day-week">{{ WEEK_LABELS[weekdayOf(d)] }}</span>
        <span class="daily-note__day-mood">{{ noteMap[d]?.mood ?? '' }}</span>
        <span
          v-if="shiftMap[d]"
          class="daily-note__day-shift"
          :style="{ background: shiftMetaOf(shiftMap[d]).color }"
        >{{ shiftMetaOf(shiftMap[d]).label }}</span>
      </button>
    </div>
  </section>
</template>

<style lang="scss" scoped>
@use '../dailyNote';
</style>