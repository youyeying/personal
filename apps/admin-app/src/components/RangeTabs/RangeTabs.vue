<script setup lang="ts">
/**
 * 公共组件 · RangeTabs 范围切换按钮组（7天/30天/全部 或 30天/90天/全部）
 * 由记账/健康概览卡的 range 按钮抽取统一
 */
export interface RangeTabItem {
  key: string
  label: string
}

defineProps<{
  options: RangeTabItem[]
  modelValue: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: string): void
}>()
</script>

<template>
  <div class="range-tabs">
    <button
      v-for="o in options"
      :key="o.key"
      class="range-tabs__btn"
      :class="{ 'is-active': o.key === modelValue }"
      type="button"
      @click="emit('update:modelValue', o.key)"
    >{{ o.label }}</button>
  </div>
</template>

<style lang="scss" scoped>
@use './rangeTabs';
</style>