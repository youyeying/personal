/**
 * 主题工具：读取 CSS 变量实际色值
 * 背景：ECharts 等 canvas 渲染不解析 `var(--cb-xxx)` 字符串，需取实际值传入
 */

/**
 * 读取 CSS 变量（如 '--cb-ink-muted'）的当前解析值，缺省回退 fallback
 * @param element 可选；默认从 <html> 读，传具体元素可读该元素及其继承的自定义属性
 *               （如 --cb-mod 由布局在 .app-layout 容器上定义，需传图表容器读取）
 */
export function cssVar(name: string, fallback = '#000', element?: Element | null): string {
  if (typeof document === 'undefined') return fallback
  const root = element ?? document.documentElement
  const val = getComputedStyle(root).getPropertyValue(name).trim()
  return val || fallback
}

/**
 * 主题变化监听：html 的 class（dark）变化时触发回调
 * 返回停止监听函数（组件卸载时调用）
 */
export function watchTheme(cb: () => void): () => void {
  const target = document.documentElement
  const observer = new MutationObserver(() => cb())
  observer.observe(target, { attributes: true, attributeFilter: ['class'] })
  return () => observer.disconnect()
}
