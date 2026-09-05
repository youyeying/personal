/**
 * 趋势图公共样板（composable）：容器 ref + useECharts 生命周期 + 色板 + 通用轴/网格
 * 饮食统计「摄入 vs 消耗」、锻炼分析「净消耗柱 / 实际消耗折线」等趋势图复用，
 * 在 useECharts 之上再收敛一层各页重复的「色板读取 + 轴样式」样板。
 */
import { ref } from 'vue'
import { useECharts } from './useECharts'
import { cssVar } from './theme'

/** 图表色板（canvas 不解析 CSS 变量，渲染前取实际值） */
export interface ChartPalette {
  mod: string      // 模块主色（--sk-mod，随路由模块变化）
  muted: string    // 弱化文本
  ink2: string     // 次要文本
  hairline: string // 分隔线
  teal: string     // 语义 teal
  intake: string   // V2 摄入语义色（--c-intake）
  burn: string     // V2 消耗语义色（--c-burn）
  gap: string      // V2 缺口正向语义色（--c-gap）
  over: string     // V2 超标语义色（--c-over）
}

/**
 * 单个趋势图的完整样板
 * 用法：
 * ```ts
 * const { el: chartEl, chart, palette } = useTrendChart(() => renderCharts())
 * function renderCharts() {
 *   const el = chart.ensure()
 *   if (!el) return
 *   const c = palette()
 *   el.setOption({ grid: TREND_GRID, ...trendAxes(labels, c), series: [...] } as any)
 * }
 * ```
 */
export function useTrendChart(redraw: () => void) {
  /** 图表容器 ref（模板 ref="chartEl" 绑定） */
  const el = ref<HTMLDivElement | null>(null)
  const chart = useECharts(el, { redraw })

  /** 读取当前主题色板（每次渲染前调用；主题切换 redraw 后取到新值） */
  function palette(): ChartPalette {
    const base = el.value ?? document.documentElement
    return {
      mod: cssVar('--sk-mod', cssVar('--sk-primary'), base),
      muted: cssVar('--sk-ink-muted'),
      ink2: cssVar('--sk-ink-secondary'),
      hairline: cssVar('--sk-hairline'),
      teal: cssVar('--sk-teal'),
      intake: cssVar('--c-intake'),
      burn: cssVar('--c-burn'),
      gap: cssVar('--c-gap'),
      over: cssVar('--c-over')
    }
  }

  /** #hex → rgba()（ECharts 渐变 areaStyle 取主色半透明用） */
  function hexA(hex: string, a: number): string {
    const h = hex.replace('#', '')
    const n = parseInt(h.length === 3 ? h.split('').map((x) => x + x).join('') : h, 16)
    return `rgba(${(n >> 16) & 255}, ${(n >> 8) & 255}, ${n & 255}, ${a})`
  }

  return { el, chart, palette, hexA }
}

/** 通用网格内边距（趋势图统一；个别图可用 { ...TREND_GRID, top: 32 } 覆盖） */
export const TREND_GRID = { top: 20, right: 12, bottom: 24, left: 44 }

/** 通用轴：category X（hairline 轴线 + 弱化标签）+ value Y（虚线网格） */
export function trendAxes(labels: string[], c: ChartPalette) {
  return {
    xAxis: {
      type: 'category' as const,
      data: labels,
      axisLine: { lineStyle: { color: c.hairline } },
      axisTick: { show: false },
      axisLabel: { color: c.muted, interval: 'auto' as const }
    },
    yAxis: {
      type: 'value' as const,
      axisLabel: { color: c.muted },
      splitLine: { lineStyle: { color: c.hairline, type: 'dashed' as const } }
    }
  }
}
