/**
 * 班表接口：按「每月21日 → 次月20日」一个班期批量上传、按日期范围查询
 */
import { requestApi } from './request'
import * as XLSX from 'xlsx'
import { formatShiftRange } from '@/utils/format'

/** 班表记录 */
export interface ShiftRecord {
  id: number
  userId: number
  shiftDate: string
  shiftName: string
  note: string | null
  createdAt?: string
  updatedAt?: string
}

/** 班期批量保存请求 */
export interface ShiftBatchPayload {
  startDate: string
  endDate: string
  shifts: { date: string; shiftName: string; note?: string }[]
}

/** 按日期范围查班表 */
export function getShifts(startDate?: string, endDate?: string) {
  return requestApi<ShiftRecord[]>({
    url: '/shifts',
    method: 'GET',
    params: { startDate, endDate }
  })
}

/** 批量保存一个班期 */
export function saveShiftsBatch(payload: ShiftBatchPayload) {
  return requestApi<{ created: number; updated: number; count: number }>({
    url: '/shifts/batch',
    method: 'POST',
    data: payload
  })
}

/** 班次预设（用户实际班表） */
export const SHIFT_OPTIONS = [
  '早上7-16',
  '早上7点半-16点30',
  '早上10-19',
  '中午12-21',
  '下午15-24',
  '下午17-第二天2',
  '23-第二天8',
  '休息'
] as const

/** 班次类型：短标签（日历格展示）+ 颜色（日历/徽章着色） */
export interface ShiftMeta {
  value: string
  label: string
  color: string
}

/** 班次时间定义：value=班次名、start/end=起止分钟（0-1440，跨天班次 end 存凌晨时刻）、color=着色 */
const SHIFT_DEFS: { value: string; start: number; end: number; color: string }[] = [
  { value: '早上7-16', start: 7 * 60, end: 16 * 60, color: '#c08a3e' },
  { value: '早上7点半-16点30', start: 7 * 60 + 30, end: 16 * 60 + 30, color: '#b98a4a' },
  { value: '早上10-19', start: 10 * 60, end: 19 * 60, color: '#4f7a8c' },
  { value: '中午12-21', start: 12 * 60, end: 21 * 60, color: '#5a8a9c' },
  { value: '下午15-24', start: 15 * 60, end: 24 * 60, color: '#5a5a8c' },
  { value: '下午17-第二天2', start: 17 * 60, end: 2 * 60, color: '#6a5a9c' },
  { value: '23-第二天8', start: 23 * 60, end: 8 * 60, color: '#3a3a6c' },
  { value: '休息', start: 0, end: 0, color: '#7a8a6a' }
]

/** SHIFT_META：由 SHIFT_DEFS 派生短标签（统一 HH:mm 格式），供日历/下拉使用 */
export const SHIFT_META: ShiftMeta[] = SHIFT_DEFS.map((s) => ({
  value: s.value,
  label: s.value === '休息' ? '休' : formatShiftRange(s.start, s.end),
  color: s.color
}))

/** 按班次取元信息（未知班次回退默认色） */
export function shiftMetaOf(v: string): ShiftMeta {
  return SHIFT_META.find((s) => s.value === v) ?? { value: v, label: v, color: '#8a8a80' }
}

/** 班表文件（xlsx）里的班次代码 → 班次名映射 */
export const SHIFT_CODE_MAP: Record<string, string> = {
  P7A1: '早上7-16',
  P7C1: '早上7点半-16点30',
  P10A1: '早上10-19',
  P12A1: '中午12-21',
  P15A1: '下午15-24',
  P17A1: '下午17-第二天2',
  P23A1: '23-第二天8',
  休: '休息'
}

/** Excel 序列号 → yyyy-MM-dd（UTC，避免时区偏移） */
function excelSerialToDateStr(serial: number): string {
  const d = new Date((serial - 25569) * 86400 * 1000)
  const y = d.getUTCFullYear()
  const m = String(d.getUTCMonth() + 1).padStart(2, '0')
  const day = String(d.getUTCDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

/** 解析班表 xlsx 文件 → [{ date, shiftName }]（取第一个工作表，第1行=日期、第2行=班次代码） */
export async function parseShiftFile(file: File): Promise<{ date: string; shiftName: string }[]> {
  const buf = await file.arrayBuffer()
  const wb = XLSX.read(buf, { type: 'array' })
  const ws = wb.Sheets[wb.SheetNames[0]]
  const rows = XLSX.utils.sheet_to_json<unknown[]>(ws, { header: 1, defval: null })
  // 兼容：日期可能被解析成数字（Excel 序列号）或已是字符串
  const dates = rows[0] ?? []
  const codes = rows[1] ?? []
  const items: { date: string; shiftName: string }[] = []
  for (let i = 0; i < dates.length; i++) {
    const rawDate = dates[i]
    if (rawDate == null) continue
    const dateStr =
      typeof rawDate === 'number'
        ? excelSerialToDateStr(rawDate)
        : String(rawDate).slice(0, 10)
    const code = String(codes[i] ?? '').trim()
    if (!code) continue
    items.push({ date: dateStr, shiftName: SHIFT_CODE_MAP[code] ?? code })
  }
  return items
}