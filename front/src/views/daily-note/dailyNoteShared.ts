/**
 * 每日总结模块共享常量/纯函数（日历 / 小汇总 / 编辑器 / 时间线 子组件共用）
 */

/** 心情预设（emoji 存储，label 展示） */
export const MOODS = [
  { emoji: '😊', label: '开心' },
  { emoji: '😄', label: '不错' },
  { emoji: '😌', label: '平静' },
  { emoji: '😐', label: '一般' },
  { emoji: '😴', label: '疲惫' },
  { emoji: '😣', label: '焦虑' },
  { emoji: '😭', label: '难过' },
  { emoji: '🔥', label: '动力十足' }
]

export const WEEK_LABELS = ['一', '二', '三', '四', '五', '六', '日']

/** 心情 emoji → 中文 label */
export function moodLabel(emoji: string | null): string {
  if (!emoji) return ''
  return MOODS.find((m) => m.emoji === emoji)?.label ?? emoji
}

/** 单元格的周几（0-6，周一为 0） */
export function weekdayOf(dateStr: string): number {
  const t = new Date(dateStr)
  return (t.getDay() + 6) % 7
}

/** 当前选中日期所在班期起始（>=21 取本月21，否则上月21） */
export function cycleStartOf(d: Date): Date {
  return d.getDate() >= 21 ? new Date(d.getFullYear(), d.getMonth(), 21) : new Date(d.getFullYear(), d.getMonth() - 1, 21)
}

/** 班期结束（次月 20 号） */
export function cycleEndOf(cs: Date): Date {
  return new Date(cs.getFullYear(), cs.getMonth() + 1, 20)
}