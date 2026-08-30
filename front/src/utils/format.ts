/**
 * 时间格式化工具（公共）
 * 统一处理后端 LocalDateTime 字符串（如 2026-08-20T01:23:45）与 Date
 */

/** 两位补零 */
function pad(n: number): string {
  return n < 10 ? `0${n}` : `${n}`
}

/** 转 Date，非法输入返回 null */
function toDate(v: string | Date | null | undefined): Date | null {
  if (!v) return null
  if (v instanceof Date) return v
  const d = new Date(v)
  return Number.isNaN(d.getTime()) ? null : d
}

/** yyyy-MM-dd */
export function formatDate(v: string | Date | null | undefined): string {
  const d = toDate(v)
  if (!d) return ''
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** yyyy-MM-dd HH:mm:ss */
export function formatDateTime(v: string | Date | null | undefined): string {
  const d = toDate(v)
  if (!d) return ''
  return `${formatDate(d)} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

/** 紧凑时间：MM-dd HH:mm（列表行内展示用） */
export function formatShortTime(v: string | Date | null | undefined): string {
  const d = toDate(v)
  if (!d) return ''
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** 分钟数（0 起）→ 'HH:mm'，如 450 → '07:30'、1440 → '24:00' */
export function formatMinute(min: number): string {
  const h = Math.floor(min / 60)
  const m = min % 60
  return `${pad(h)}:${pad(m)}`
}

/**
 * 班次时间区间标签：HH:mm 24 小时补零格式，如 '07:30-16:30'、'17:00-02:00'、'15:00-24:00'。
 * 起止分钟均为 0-1440（跨天班次 end 直接存凌晨时刻，不再标「次日」）。
 */
export function formatShiftRange(startMin: number, endMin: number): string {
  return `${formatMinute(startMin)}-${formatMinute(endMin)}`
}

/** 金额格式化：¥1,280.50；signed=true 时正数带 + */
export function formatMoney(n: number | string | null | undefined, signed = false): string {
  const num = n == null || n === '' ? 0 : Number(n)
  const sign = num < 0 ? '-' : signed && num >= 0 ? '+' : ''
  const abs = Math.abs(num).toFixed(2)
  return `${sign}¥${abs}`
}