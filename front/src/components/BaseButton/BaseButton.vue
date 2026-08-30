<script setup lang="ts">
/**
 * BaseButton 通用按钮组件
 * 特性：
 * - 无边框、小圆角（非椭圆）
 * - 结构：文本区 + 图标区（由父组件传入）
 * - hover：微微抬起 + 底部阴影 + 文本与图标间距展开
 * - loading：图标区被转圈覆盖，按钮禁用（区域内加载）
 * - 主题由 color 控制：primary 主色 / plain 浅色
 */
import { computed } from 'vue'
import InlineLoading from '@/components/loading/InlineLoading.vue'

const props = withDefaults(
  defineProps<{
    /** 按钮文字 */
    text: string
    /** 图标名称（可选，配合 <template #icon> 使用 slot 更灵活） */
    // 注：图标通过 slot 传入，这里保留 text 和 color
    /** 主题：primary 主色 / plain 浅色 / text 纯文字 */
    color?: 'primary' | 'plain' | 'text'
    /** 是否加载中（图标区显示转圈 + 禁用） */
    loading?: boolean
    /** 是否禁用 */
    disabled?: boolean
    /** 原生类型 */
    type?: 'button' | 'submit'
  }>(),
  {
    color: 'primary',
    loading: false,
    disabled: false,
    type: 'button'
  }
)

const emit = defineEmits<{
  (e: 'click'): void
}>()

/** 是否不可点击（loading 或 disabled） */
const isDisabled = computed(() => props.loading || props.disabled)

function handleClick() {
  if (!isDisabled.value) {
    emit('click')
  }
}
</script>

<template>
  <button
    class="base-button"
    :class="[`base-button--${color}`, { 'base-button--loading': loading }]"
    :type="type"
    :disabled="isDisabled"
    :aria-busy="loading"
    @click="handleClick"
  >
    <!-- 文本区（在左） -->
    <span class="base-button__text">{{ text }}</span>

    <!-- 图标区（在右）：默认显示图标；loading 时图标被不透明转圈替换 -->
    <span v-if="!loading" class="base-button__icon">
      <slot name="icon" />
    </span>

    <!-- 区域内加载：不透明转圈，完全替换图标位置（颜色继承按钮文字色） -->
    <span v-else class="base-button__spinner">
      <InlineLoading :size="16" mode="spinner" />
    </span>
  </button>
</template>

<style lang="scss">
@use './baseButton';
</style>
