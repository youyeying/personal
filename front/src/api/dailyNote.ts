/**
 * 每日总结接口
 */
import { requestApi } from './request'

/** 每日总结 */
export interface DailyNote {
  id: number
  userId: number
  /** yyyy-MM-dd（同日唯一） */
  noteDate: string
  mood: string | null
  content: string | null
  createdAt: string
  updatedAt: string
}

/** 某天小汇总 */
export interface DailySummary {
  date: string
  /** 当日支出 */
  expense: number
  /** 当日收入 */
  income: number
  /** 当日锻炼记录（前端按 MET 公式算净消耗） */
  exerciseRecords: ExerciseRecordForSummary[]
  /** 当日体重（无则 null） */
  weight: number | null
  /** 较上次体重的变化（无则 null） */
  weightChange: number | null
  /** 当日学习条数 */
  learnCount: number
  /** 当日学习时长（分钟） */
  learnMinutes: number
  /** 当日开发时长（分钟） */
  devMinutes: number
}

/** 锻炼记录精简类型（summary 返回，用于算净消耗） */
export interface ExerciseRecordForSummary {
  exerciseId: number
  reps: number | null
  minutes: number | null
  seconds: number | null
  distance: number | null
  floors: number | null
  times: number | null
  bodyWeight: number | null
}

/** 查某天总结 */
export function getDailyNote(date: string) {
  return requestApi<DailyNote | null>({
    url: '/daily-notes/date',
    method: 'GET',
    params: { date }
  })
}

/** 保存总结（心情/内容至少填一项） */
export function saveDailyNote(date: string, data: { mood?: string; content?: string }) {
  return requestApi<DailyNote>({
    url: '/daily-notes',
    method: 'POST',
    params: { date },
    data
  })
}

/** 按日期范围查总结列表 */
export function listDailyNotes(startDate?: string, endDate?: string) {
  return requestApi<DailyNote[]>({
    url: '/daily-notes',
    method: 'GET',
    params: { startDate, endDate }
  })
}

/** 某天小汇总 */
export function getDailySummary(date: string) {
  return requestApi<DailySummary>({
    url: '/daily-notes/summary',
    method: 'GET',
    params: { date }
  })
}