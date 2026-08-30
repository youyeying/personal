/**
 * ECharts 实例公共生命周期管理（composable）
 * 抽离各页面重复的样板：
 * - init 保护：容器 clientWidth>0 才初始化（子页 Tab v-show 初始 display:none 时宽高为 0，
 *   init 会产生 ECharts 告警并得到无效尺寸）
 * - 窗口 resize 自动同步
 * - 主题切换（html.dark）自动触发 redraw（canvas 不解析 CSS 变量，需重绘跟随明暗）
 * - 组件卸载时 dispose
 *
 * 用法：const c = useECharts(elRef, { redraw: renderXxx })
 * 渲染：c.ensure(onInit)?.setOption(...)
 */
import { onBeforeUnmount, onMounted, type Ref } from 'vue'
import * as echarts from 'echarts'
import { watchTheme } from './theme'

export interface UseEChartsOptions {
  /** 重绘回调（主题切换时触发；也用于切到子页 Tab 后补渲染） */
  redraw?: () => void
  /** 是否跟随窗口 resize（默认 true） */
  responsive?: boolean
  /** 是否跟随主题切换重绘（默认 true） */
  themeSync?: boolean
}

export function useECharts(el: Ref<HTMLElement | null>, options: UseEChartsOptions = {}) {
  const { redraw, responsive = true, themeSync = true } = options
  let chart: echarts.ECharts | null = null
  let stopTheme: (() => void) | null = null
  // 观测图表容器自身尺寸变化：窗口 resize 之外由导航伸缩/过渡动画/内容自适应引起的宽度变化也能同步
  let ro: ResizeObserver | null = null

  /** 容器可见时初始化（参数可选，首次创建时绑定事件）；不可见返回 null */
  function ensure(onInit?: (c: echarts.ECharts) => void): echarts.ECharts | null {
    if (!el.value) return null
    ensureObserve()
    if (!el.value.clientWidth) return null
    if (!chart) {
      chart = echarts.init(el.value)
      onInit?.(chart)
    }
    return chart
  }

  /** 容器尺寸变化时重设图表（配合导航伸缩/动画等非 window resize 的宽度变化） */
  function ensureObserve() {
    if (ro || !el.value || typeof ResizeObserver === 'undefined') return
    ro = new ResizeObserver(() => resize())
    ro.observe(el.value)
  }

  function resize() {
    chart?.resize()
  }

  function dispose() {
    chart?.dispose()
    chart = null
  }

  onMounted(() => {
    if (responsive) window.addEventListener('resize', resize)
    if (themeSync) stopTheme = watchTheme(() => redraw?.())
  })

  onBeforeUnmount(() => {
    if (responsive) window.removeEventListener('resize', resize)
    ro?.disconnect()
    stopTheme?.()
    dispose()
  })

  return { ensure, resize, dispose }
}