<script setup lang="ts">
/**
 * 全屏加载
 * 全屏不透明加载画面，支持自动淡出
 * 三种模式：spinner(纯转圈) / text(纯文字) / spinner-text(转圈+文字)
 */
import { computed, onMounted, ref } from 'vue'

const props = withDefaults(
  defineProps<{
    /** 提示文字 */
    text?: string
    /** 展示模式：转圈 / 纯文字 / 转圈+文字 */
    mode?: 'spinner' | 'text' | 'spinner-text'
    /** 自动淡出毫秒数，0 表示不自动关闭 */
    duration?: number
  }>(),
  {
    text: '加载中…',
    mode: 'spinner-text',
    duration: 0
  }
)

const emit = defineEmits<{
  (e: 'fade-out'): void
}>()

/** 是否显示转圈 */
const showRing = computed(() => props.mode !== 'text')
/** 是否显示文字 */
const showText = computed(() => props.mode !== 'spinner' && !!props.text)

/** 控制透明渐隐的 class */
const fading = ref(false)
/** 控制移除 DOM 的 class */
const removed = ref(false)

onMounted(() => {
  if (props.duration > 0) {
    // 到时间后先淡出，再触发完成事件
    window.setTimeout(() => {
      fading.value = true
    }, props.duration)
    window.setTimeout(() => {
      removed.value = true
      emit('fade-out')
    }, props.duration + 500)
  }
})
</script>

<template>
  <Transition name="fsl-fade">
    <div
      v-if="!removed"
      class="full-screen-loading"
      :class="{ 'full-screen-loading--fading': fading }"
      role="status"
      aria-live="polite"
    >
      <div class="full-screen-loading__content">
        <!-- 转圈动画 -->
        <div v-if="showRing" class="full-screen-loading__spinner" aria-hidden="true">
          <svg class="full-screen-loading__ring" viewBox="0 0 48 48" width="48" height="48">
            <circle
              class="full-screen-loading__track"
              cx="24"
              cy="24"
              r="20"
              fill="none"
              stroke-width="4"
            />
            <circle
              class="full-screen-loading__bar"
              cx="24"
              cy="24"
              r="20"
              fill="none"
              stroke-width="4"
              stroke-linecap="round"
            />
          </svg>
        </div>
        <p v-if="showText" class="full-screen-loading__text">{{ text }}</p>
      </div>
    </div>
  </Transition>
</template>

<style lang="scss">
@use './fullScreenLoading';
</style>
