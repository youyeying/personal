/**
 * 公共组件 · EmptyState 空态（可选图标 + 文案 + 引导按钮）
 * - icon 具名插槽：可选的界面图标；不传则不画图标圆底
 * - text：空态文案
 * - actionText：引导按钮文字（可选）；点击 emit('action')
 */
<script setup lang="ts">
defineProps<{
  text: string
  actionText?: string
}>()

const emit = defineEmits<{
  (e: 'action'): void
}>()
</script>

<template>
  <div class="empty-state">
    <span v-if="$slots.icon" class="empty-state__icon"><slot name="icon" /></span>
    <p class="empty-state__text">{{ text }}</p>
    <button v-if="actionText" class="empty-state__go" type="button" @click="emit('action')">{{ actionText }}</button>
  </div>
</template>

<style lang="scss" scoped>
@use './emptyState';
</style>