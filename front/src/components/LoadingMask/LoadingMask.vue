<script setup lang="ts">
/**
 * 公共组件 · LoadingMask 不透明加载遮罩
 * 覆盖所在卡片区域（父级需 position:relative），背景用表面色不透出内容
 * 替换各页面重复的 `&__loading`（absolute + surface + InlineLoading）
 */
import InlineLoading from '@/components/loading/InlineLoading.vue'

withDefaults(
  defineProps<{
    /** 是否显示 */
    show?: boolean
    /** 加载模式 */
    mode?: 'spinner' | 'text' | 'spinner-text'
    /** 提示文字 */
    text?: string
    /** 转圈尺寸 */
    size?: number
  }>(),
  {
    show: false,
    mode: 'spinner-text',
    text: '加载中…',
    size: 26
  }
)
</script>

<template>
  <div v-if="show" class="loading-mask">
    <InlineLoading :size="size" :text="text" :mode="mode" color="var(--cb-mod)" />
  </div>
</template>

<style lang="scss" scoped>
@use './loadingMask';
</style>