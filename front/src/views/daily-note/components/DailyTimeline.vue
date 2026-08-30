<script setup lang="ts">
/**
 * 每日总结 · 本班期时间线（纯展示 + 选中交互）
 * - timeline：总结条目（倒序）；selectedDate：当前选中
 * - 点击条目 emit('select', date)
 */
export interface DailyNoteItem {
  id: number
  noteDate: string
  mood: string | null
  content: string | null
}

defineProps<{
  timeline: DailyNoteItem[]
  selectedDate: string
}>()

const emit = defineEmits<{
  (e: 'select', date: string): void
}>()
</script>

<template>
  <div class="card daily-note__timeline">
    <h3 class="daily-note__timeline-title">本班期总结</h3>
    <div v-if="timeline.length" class="daily-note__tl">
      <button
        v-for="n in timeline"
        :key="n.id"
        class="daily-note__tl-item"
        :class="{ 'daily-note__tl-item--active': n.noteDate === selectedDate }"
        type="button"
        @click="emit('select', n.noteDate)"
      >
        <span class="daily-note__tl-date num">{{ n.noteDate }}</span>
        <span class="daily-note__tl-mood">{{ n.mood ?? '' }}</span>
        <span class="daily-note__tl-content">{{ n.content || '（空）' }}</span>
      </button>
    </div>
    <p v-else class="daily-note__tl-empty">本班期还没有写总结</p>
  </div>
</template>

<style lang="scss" scoped>
@use '../dailyNote';
</style>