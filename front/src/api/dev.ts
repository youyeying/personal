/**
 * 开发日志接口：开发会话 + 功能变更
 */
import { requestApi } from './request'

/** 开发会话 */
export interface DevelopmentSession {
  id: number
  sessionDate: string
  startTime: string | null
  endTime: string | null
  durationMinutes: number | null
  /** 0 进行中 / 1 已结束 */
  status: number
  createdAt?: string
  updatedAt?: string
}

/** 功能变更记录 */
export interface FeatureLog {
  id: number
  sessionId: number
  /** 变更类型：新增/修改/删除/修复 */
  type: string
  /** 所属模块 */
  module: string
  /** 变更描述 */
  content: string
  createdAt: string
  updatedAt: string
}

/** 当天统计 */
export interface DevSummary {
  date: string
  /** 当天进行中的会话（无则 null） */
  session: DevelopmentSession | null
  /** 当天所有会话（一天可多段，时段卡用） */
  sessions: DevelopmentSession[]
  durationMinutes: number
  featureCount: number
  types: Record<string, number>
  features: FeatureLog[]
}

/** 历史统计 */
export interface DevStatistics {
  sessionCount: number
  totalDurationMinutes: number
  totalFeatures: number
  sessions: DevelopmentSession[]
}

/** 范围汇总（开发日志「汇总」Tab）：近 N 天或全部 */
export interface DevRangeStats {
  durationMinutes: number
  sessionCount: number
  featureCount: number
  /** 按天开发时长（分钟，升序）：{ '2026-08-27': 30 } */
  byDay: Record<string, number>
  /** 按模块功能条数：{ '前端': 5, '个人中心': 2 } */
  byModule: Record<string, number>
  /** 按类型功能条数：新增/修改/删除/修复 */
  byType: Record<string, number>
}

/** 开始开发 */
export function startDevSession() {
  return requestApi<DevelopmentSession>({ url: '/dev/session/start', method: 'POST' })
}

/** 结束开发 */
export function endDevSession() {
  return requestApi<DevelopmentSession>({ url: '/dev/session/end', method: 'POST' })
}

/** 记录一条功能变更 */
export function addFeature(data: { type: string; module: string; content: string }) {
  return requestApi<FeatureLog>({ url: '/dev/features', method: 'POST', data })
}

/** 当天统计（date 缺省为今天；silent=true 时静默，用于导航栏取今日时长） */
export function getDevSummary(date?: string, silent?: boolean) {
  return requestApi<DevSummary>({
    url: '/dev/summary',
    method: 'GET',
    params: date ? { date } : undefined,
    silent
  })
}

/** 历史统计 */
export function getDevStatistics() {
  return requestApi<DevStatistics>({ url: '/dev/statistics', method: 'GET' })
}

/** 范围汇总（days 缺省=全部；sessionsRunning 时静默不适用，正常弹错） */
export function getDevStatsRange(days?: number) {
  return requestApi<DevRangeStats>({
    url: '/dev/statistics/range',
    method: 'GET',
    params: days ? { days } : undefined
  })
}