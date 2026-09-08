<script setup lang="ts">
/**
 * 公共组件 · ProgressRing 环形进度（V2：Apple Watch 心智，SVG 无依赖）
 * - value/max 决定弧长（超量自动封顶 100%，超预算变红）
 * - label：环下方说明文字；ringColor 缺省跟随模块主色 --sk-mod
 * - size：直径 px（窄屏 ≤560px 自动缩至 64px，样式层处理）
 */
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    value: number
    max: number
    label?: string
    /** 环色（CSS 颜色值或 var() 引用；超预算时自动用 --c-over） */
    ringColor?: string
    /** 超预算（value > max）时变红，默认 true */
    overToRed?: boolean
    size?: number
  }>(),
  { label: '', ringColor: 'var(--sk-mod)', overToRed: true, size: 92 }
)

const RADIUS = 42
const CIRC = 2 * Math.PI * RADIUS

/** 0~1 封顶比例 */
const pct = computed(() => {
  if (!props.max || props.max <= 0) return 0
  return Math.min(1, props.value / props.max)
})

const isOver = computed(() => props.overToRed && props.max > 0 && props.value > props.max)

const stroke = computed(() => (isOver.value ? 'var(--c-over)' : props.ringColor))
</script>

<template>
  <div class="pr" :style="{ '--pr-size': size + 'px' }">
    <svg class="pr__svg" viewBox="0 0 100 100" role="img" :aria-label="label">
      <circle class="pr__cap" cx="50" cy="50" :r="RADIUS" />
      <circle
        class="pr__val"
        cx="50" cy="50" :r="RADIUS"
        :stroke="stroke"
        :stroke-dasharray="CIRC"
        :stroke-dashoffset="CIRC * (1 - pct)"
      />
    </svg>
    <span v-if="label" class="pr__label">{{ label }}</span>
  </div>
</template>

<style lang="scss" scoped>
@use './progressRing';
</style>
