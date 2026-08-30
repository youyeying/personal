/**
 * 公共组件 · BlockTitle 区块标题（各模块页头「标题 + 提示 + 右侧操作」统一）
 * - title：主标题
 * - hint：旁的灰色小提示
 * - aside 具名插槽：右侧操作区（如「查看全部」链接、范围切换按钮组），自动推到最右
 */
<script setup lang="ts">
defineProps<{
  title: string
  hint?: string
}>()
</script>

<template>
  <div class="block-title">
    <h2 class="block-title__heading">{{ title }}</h2>
    <span v-if="hint" class="block-title__hint">{{ hint }}</span>
    <span v-if="$slots.aside" class="block-title__aside"><slot name="aside" /></span>
  </div>
</template>

<style lang="scss" scoped>
@use './blockTitle';
</style>