/**
 * composable · useWindowWidth
 * 响应式视口宽度：用于 DataList 列隐藏（hideBelow）等按宽度适配的逻辑
 * SSR 安全：非浏览器环境取默认值 1024
 */
import { onMounted, onUnmounted, ref } from 'vue'

export function useWindowWidth(defaultWidth = 1024) {
  const width = ref(typeof window !== 'undefined' ? window.innerWidth : defaultWidth)

  function onResize() {
    width.value = window.innerWidth
  }

  onMounted(() => {
    width.value = window.innerWidth
    window.addEventListener('resize', onResize)
  })

  onUnmounted(() => {
    window.removeEventListener('resize', onResize)
  })

  return { width }
}
