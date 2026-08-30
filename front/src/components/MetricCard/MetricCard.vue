<script setup lang="ts">
/**
 * 公共组件 · MetricCard 指标卡（记账/健康概览四指标复用）
 * - label：指标名
 * - tone：'' 默认 / ok 成功色 / err 错误色 / accent 强调卡（背景主色淡染 + 边框主色）
 * - 默认插槽：数值内容（灵活放 <i>单位</i> 等）；具名插槽 sub：底部辅助文字
 */
withDefaults(
  defineProps<{
    label: string
    tone?: '' | 'ok' | 'err' | 'accent'
  }>(),
  { tone: '' }
)
</script>

<template>
  <div class="metric" :class="[`metric--${tone || 'plain'}`]">
    <span class="metric__label">{{ label }}</span>
    <span class="metric__value" :class="tone === 'ok' ? 'is-ok' : tone === 'err' ? 'is-err' : ''">
      <slot />
    </span>
    <span v-if="$slots.sub" class="metric__sub"><slot name="sub" /></span>
  </div>
</template>

<style lang="scss" scoped>
@use './metricCard';
</style>