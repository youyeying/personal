<script setup lang="ts">
/**
 * InlineLoading 区域内加载
 * 支持三种模式：spinner(纯转圈) / text(纯文字) / spinner-text(转圈+文字)
 * 用于卡片内 / 按钮内 / 图标内
 */
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    /** 尺寸（px） */
    size?: number
    /** 颜色（默认继承 currentColor） */
    color?: string
    /** 文字提示（可选） */
    text?: string
    /** 展示模式：转圈 / 纯文字 / 转圈+文字 */
    mode?: 'spinner' | 'text' | 'spinner-text'
  }>(),
  {
    size: 16,
    color: '',
    text: '',
    mode: 'spinner-text'
  }
)

/** 是否显示转圈 */
const showRing = computed(() => props.mode !== 'text')
/** 是否显示文字 */
const showText = computed(() => props.mode !== 'spinner' && !!props.text)

/** 根样式：有文字才撑开宽度，否则按 size 定宽 */
const rootStyle = computed(() => ({
  width: showText.value ? 'auto' : `${props.size}px`,
  height: `${props.size}px`,
  color: props.color || 'currentColor'
}))
</script>

<template>
  <span
    class="inline-loading"
    :style="rootStyle"
    role="status"
    aria-label="加载中"
  >
    <svg
      v-if="showRing"
      class="inline-loading__ring"
      :width="size"
      :height="size"
      viewBox="0 0 24 24"
    >
      <circle
        cx="12"
        cy="12"
        r="9"
        fill="none"
        stroke="currentColor"
        stroke-width="2.5"
        stroke-linecap="round"
        stroke-dasharray="40 60"
      />
    </svg>
    <span v-if="showText" class="inline-loading__text">{{ text }}</span>
  </span>
</template>

<style lang="scss">
@use './inlineLoading';
</style>