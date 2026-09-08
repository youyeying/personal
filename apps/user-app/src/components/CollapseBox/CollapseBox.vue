<script setup lang="ts">
/**
 * 公共组件 · CollapseBox 可收纳/展开内容容器（带动画）
 * 用 grid-template-rows 0fr↔1fr 过渡，无需知道内容高度（图表等高动态内容也适用）
 * - collapsed=true 时收起，内部 overflow hidden
 * - 展开动画（约 0.32s）结束后触发 expand-end，父组件可借此对图表 resize
 */
import { onBeforeUnmount, ref, watch } from 'vue'

const props = defineProps<{
  collapsed: boolean
}>()

const emit = defineEmits<{
  (e: 'expand-end'): void
}>()

const EXPAND_MS = 360
let timer: number | null = null

watch(() => props.collapsed, (collapsed) => {
  if (timer !== null) {
    window.clearTimeout(timer)
    timer = null
  }
  // 展开动画完成后内容高度才到位，通知父组件重布局图表
  if (!collapsed) {
    timer = window.setTimeout(() => emit('expand-end'), EXPAND_MS)
  }
})

onBeforeUnmount(() => {
  if (timer !== null) window.clearTimeout(timer)
})
</script>

<template>
  <div class="collapse-box" :class="{ 'is-collapsed': collapsed }">
    <div class="collapse-box__inner">
      <slot />
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use './collapseBox';
</style>