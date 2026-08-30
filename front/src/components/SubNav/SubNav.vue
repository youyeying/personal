<script setup lang="ts">
/**
 * 公共组件 · SubNav 内容区子页 Tab（页头切换，记账/健康等页面复用）
 * - items：每个子页 { key, label }
 * - v-model:active 双向绑定当前 key；点击触发 change 事件（供父组件做副作用，如切到概览时刷新）
 * - 仅装饰性 autocomplete 提示由父组件通过默认插槽传入
 */
import { computed } from 'vue'

export interface SubNavItem {
  key: string
  label: string
}

const props = withDefaults(
  defineProps<{
    items: SubNavItem[]
    active: string
    hint?: string
  }>(),
  { hint: '' }
)

const emit = defineEmits<{
  (e: 'update:active', v: string): void
  (e: 'change', v: string): void
}>()

const activeKey = computed(() => props.active)

function onPick(key: string) {
  if (key === props.active) return
  emit('update:active', key)
  emit('change', key)
}
</script>

<template>
  <div class="subnav">
    <button
      v-for="item in items"
      :key="item.key"
      class="subnav__btn"
      :class="{ 'is-active': item.key === activeKey }"
      type="button"
      @click="onPick(item.key)"
    >{{ item.label }}</button>
    <span v-if="hint" class="subnav__hint">{{ hint }}</span>
  </div>
</template>

<style lang="scss" scoped>
@use './subnav';
</style>