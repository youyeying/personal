<script setup lang="ts">
/**
 * 每日总结 · 心情 + 小结编辑卡（自持编辑态）
 * - date 切换时重置为 noteMood/noteContent（该日既有心情/小结）
 * - 保存触发 emit('save', { mood, content })，提交由父级完成
 */
import { onMounted, ref, watch } from 'vue'
import { MOODS } from '../dailyNoteShared'

const props = defineProps<{
  date: string
  saving: boolean
  /** 该日班次（今日班次展示，无则 null） */
  todayShift: string | null
  noteMood: string
  noteContent: string
}>()

const emit = defineEmits<{
  (e: 'save', payload: { mood: string; content: string }): void
}>()

const mood = ref('')
const content = ref('')

function reset() {
  mood.value = props.noteMood
  content.value = props.noteContent
}

watch(() => props.date, reset)
onMounted(reset)
</script>

<template>
  <div class="card daily-note__editor">
    <div class="daily-note__moods">
      <button
        v-for="m in MOODS"
        :key="m.emoji"
        class="daily-note__mood"
        :class="{ 'daily-note__mood--active': mood === m.emoji }"
        type="button"
        @click="mood = mood === m.emoji ? '' : m.emoji"
      >
        <span class="daily-note__mood-emoji">{{ m.emoji }}</span>
        <span class="daily-note__mood-label">{{ m.label }}</span>
      </button>
    </div>
    <el-input
      v-model="content"
      type="textarea"
      :rows="4"
      maxlength="500"
      show-word-limit
      placeholder="今天过得怎么样？写下你的今日小结…"
    />
    <div class="daily-note__editor-foot">
      <span v-if="todayShift" class="daily-note__today-shift">
        今日班次：<b>{{ todayShift }}</b>
      </span>
      <el-button type="primary" :loading="saving" @click="emit('save', { mood, content })">保存今日总结</el-button>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '../dailyNote';
</style>