<script setup lang="ts">
/**
 * FloatingInput 浮动标签输入框
 * 特性：
 * - 初始 placeholder 显示在框内
 * - 点击/聚焦 → 标签上移（带动画）
 * - 失焦且无内容 → 标签回落
 * - 失焦但有内容 → 标签保持上移
 * - 支持 password 类型：右侧可见性切换按钮
 */
import { computed, ref } from 'vue'

/** 组件 props */
const props = withDefaults(
  defineProps<{
    /** 浮动标签文字（placeholder 上移后的标签） */
    label: string
    /** 输入值（v-model） */
    modelValue: string
    /** 输入类型 */
    type?: 'text' | 'password'
    /** 是否禁用 */
    disabled?: boolean
    /** 是否聚焦时上移 */
    autofocus?: boolean
    /** 错误提示文字（非空时输入框进入错误态） */
    error?: string
  }>(),
  {
    type: 'text',
    disabled: false,
    autofocus: false,
    error: ''
  }
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()

/** 输入框是否聚焦 */
const focused = ref(false)
/** 密码是否可见 */
const showPassword = ref(false)

/** 标签是否上移：聚焦 或 有内容 */
const isFloated = computed(() => focused.value || props.modelValue.length > 0)

/** 实际输入类型（password 切换可见性时改为 text） */
const inputType = computed(() => {
  if (props.type !== 'password') return 'text'
  return showPassword.value ? 'text' : 'password'
})

/** 处理输入 */
function handleInput(event: Event) {
  const target = event.target as HTMLInputElement
  emit('update:modelValue', target.value)
}

function handleFocus() {
  focused.value = true
}

function handleBlur() {
  focused.value = false
}

/** 切换密码可见性 */
function togglePassword() {
  showPassword.value = !showPassword.value
}
</script>

<template>
  <div
    class="floating-input"
    :class="{
      'floating-input--floated': isFloated,
      'floating-input--focused': focused,
      'floating-input--disabled': disabled,
      'floating-input--error': !!error
    }"
  >
    <!-- 浮动标签 -->
    <label class="floating-input__label" :for="`fi-${label}`">{{ label }}</label>

    <!-- 输入框 -->
    <input
      :id="`fi-${label}`"
      class="floating-input__field"
      :type="inputType"
      :value="modelValue"
      :disabled="disabled"
      :autofocus="autofocus"
      :placeholder="focused ? ' ' : label"
      :aria-invalid="!!error"
      @input="handleInput"
      @focus="handleFocus"
      @blur="handleBlur"
    />

    <!-- 密码可见性切换 -->
    <button
      v-if="type === 'password'"
      class="floating-input__toggle"
      type="button"
      :aria-label="showPassword ? '隐藏密码' : '显示密码'"
      @click="togglePassword"
    >
      <!-- 眼睛图标：开/闭 -->
      <svg v-if="!showPassword" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7Z" />
        <circle cx="12" cy="12" r="3" />
      </svg>
      <svg v-else viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c6.5 0 10 8 10 8a13.16 13.16 0 0 1-1.67 2.68M6.61 6.61A13.5 13.5 0 0 0 2 12s3.5 7 10 7a9.74 9.74 0 0 0 5.39-1.61M2 2l20 20" />
      </svg>
    </button>

    <!-- 错误提示区：始终占位（无错误时隐藏文字但保留空间），避免布局抖动 -->
    <div class="floating-input__error-wrap" aria-live="polite">
      <p v-if="error" class="floating-input__error" role="alert">{{ error }}</p>
    </div>
  </div>
</template>

<style lang="scss">
@use './floatingInput';
</style>
