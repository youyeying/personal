<script setup lang="ts">
/**
 * 公共组件 · PagePanel 页面模板（多个页面可复用：开发日志 / 操作日志 / 记账等）
 * - header 卡片：标题 + 搜索/介绍等操作区（具名插槽 header）
 * - 数据卡片：内容区（默认插槽 default），卡片高度随内容自适应、内部无滚动条
 * - 数据卡片内嵌「不透明」加载动画（loading=true 时显示），避免透出底层内容
 * 所有内容均由父组件通过插槽传入，组件自身不含业务数据
 */
import InlineLoading from '@/components/loading/InlineLoading.vue'

withDefaults(
  defineProps<{
    /** 标题（可选，不传则不显示） */
    title?: string
    /** 是否加载中（显示不透明遮罩） */
    loading?: boolean
    /** 加载提示文字 */
    loadingText?: string
    /** 数据卡加载模式：转圈/纯文字/转圈+文字 */
    loadingMode?: 'spinner' | 'text' | 'spinner-text'
  }>(),
  {
    title: '',
    loading: false,
    loadingText: '加载中…',
    loadingMode: 'spinner-text'
  }
)
</script>

<template>
  <div class="page-panel">
    <!-- header 卡片：标题 + 操作区 -->
    <section class="page-panel__header card">
      <h2 v-if="title" class="page-panel__title">{{ title }}</h2>
      <div class="page-panel__toolbar">
        <slot name="header" />
      </div>
    </section>

    <!-- 数据卡片：高度自适应、无内部滚动条 -->
    <section class="page-panel__data card">
      <div class="page-panel__body">
        <slot />
      </div>
      <!-- 不透明加载遮罩 -->
      <div v-if="loading" class="page-panel__loading">
        <InlineLoading :size="26" :text="loadingText" :mode="loadingMode" />
      </div>
    </section>
  </div>
</template>

<style lang="scss" scoped>
@use './pagePanel';
</style>