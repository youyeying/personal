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

  /** #hex → rgba()（模块级 hexA，见下方导出） */
  return { el, chart, palette, hexA }
}

/** #hex → rgba()（模块级，trendZoom 等图表工具复用） */
export function hexA(hex: string, a: number): string {
  const h = hex.replace('#', '')
  const n = parseInt(h.length === 3 ? h.split('').map((x) => x + x).join('') : h, 16)
  return `rgba(${(n >> 16) & 255}, ${(n >> 8) & 255}, ${n & 255}, ${a})`
}

/**
 * 趋势图 dataZoom（兜底式滑块 + 默认最新窗口，v2.3.0）
 * - 兜底：仅在数据点超过可视容量（>30 天）时返回滑块；小区间（如近 7/30 天）全量展示、
 *   无需滑块，X 轴标签 interval auto 跳着显示
 * - 默认最新窗口：大区间（如近 90 天）默认视图 = 最近 30 天，想看更早拖动滑块
 * - show 由调用方按需控制（默认 false，图表 hover 时再显示，见组件绑定）
 * @param activeIdx 有数据的天在 days 数组中的下标（升序）
 * @param total     days 总天数
 * @returns dataZoom 配置数组（数据量小或无有效数据时返回 []）
 */
export function trendZoom(c: ChartPalette, activeIdx: number[], total: number) {
  // 兜底阈值：≤30 天无需滑块（宽度够展示，标签 interval auto 跳着显示）
  if (!activeIdx.length || total <= 30) return []
  const first = activeIdx[0]
  const last = activeIdx[activeIdx.length - 1]
  // 默认显示最近 30 天窗口；有数据跨度更短则收敛到有数据段
  const windowLen = Math.min(30, last - first + 1)
  let start = Math.max(0, ((last + 1 - windowLen) / total) * 100)
  let end = Math.min(100, ((last + 1) / total) * 100)
  if (end - start < 15) { // 最小跨度兜底：避免收敛过窄看不清
    const mid = (start + end) / 2
    start = Math.max(0, mid - 7.5)
    end = Math.min(100, mid + 7.5)
  }
  const muted = cssVar('--sk-ink-muted')
  return [{
    type: 'slider',
    height: 8,
    bottom: 0,
    show: false, // 默认隐藏，图表 hover 时由组件置 show:true
    start,
    end,
    zoomLock: true,      // 锁定窗口宽度：只允许平移，禁止拉伸缩放
    brushSelect: false,  // 禁用刷选（框选拉宽）
    showDetail: false,   // 拖拽不弹数值浮层
    showDataShadow: false, // 不显示数据缩略图，保持滚动条简洁
    handleSize: 0,       // 隐藏两端缩放手柄（双竖线），仅保留滑块本体
    moveHandleSize: 0,   // 隐藏底部移动手柄长条
    borderRadius: 4,     // 圆角（作用于滑块外框）
    borderColor: hexA(c.mod, 0.3), // 极淡主色边框：仅作圆角描边，视觉上接近无边框
    backgroundColor: 'transparent',
    fillerColor: hexA(c.mod, 0.35),
    handleStyle: { color: c.mod },
    textStyle: { color: muted, fontSize: 10 },
    labelFormatter: (v: number) => ''
  }]
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
