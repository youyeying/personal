<script setup lang="ts">
/**
 * 公共组件 · 白天/黑夜模式切换（苹果风开关）
 * 通过给 <html> 加/去 `dark` class 切换主题，状态持久化到 localStorage('theme-dark')
 * 可在任意需要处复用（本项目中放在导航栏左下角）
 */
import { onMounted, ref } from 'vue'

const isDark = ref(false)

/** 应用主题到根节点并持久化 */
function apply(v: boolean) {
  document.documentElement.classList.toggle('dark', v)
  localStorage.setItem('theme-dark', v ? '1' : '0')
}

/** 切换主题 */
function toggle() {
  isDark.value = !isDark.value
  apply(isDark.value)
}

onMounted(() => {
  const stored = localStorage.getItem('theme-dark')
  isDark.value = stored ? stored === '1' : document.documentElement.classList.contains('dark')
  apply(isDark.value)
})
</script>

<template>
  <button
    class="theme-toggle"
    :class="{ 'theme-toggle--dark': isDark }"
    type="button"
    role="switch"
    :aria-checked="isDark"
    :aria-label="isDark ? '切换到白天模式' : '切换到黑夜模式'"
    @click="toggle"
  >
    <span class="theme-toggle__track">
      <!-- 太阳 -->
      <svg class="theme-toggle__sun" viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
        <circle cx="12" cy="12" r="4" />
        <path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41" />
      </svg>
      <!-- 滑钮（内含月亮） -->
      <span class="theme-toggle__thumb">
        <svg class="theme-toggle__moon-icon" viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
        </svg>
      </span>
      <!-- 月亮 -->
      <svg class="theme-toggle__moon" viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
      </svg>
    </span>
  </button>
</template>

<style lang="scss" scoped>
@use './themeToggle';
</style>