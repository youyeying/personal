/**
 * 锻炼接口：动作字典 + 锻炼记录
 */
import { requestApi } from './request'
import type { PageResult } from './types'

/** 动作类型：strength 力量 / cardio 有氧计数 / plank 平板 / walk 散步 / cycling 骑行 / stairs 爬楼梯 */
export type ExerciseType = 'strength' | 'cardio' | 'plank' | 'walk' | 'cycling' | 'stairs'

/** 锻炼动作（字典） */
export interface ExerciseItem {
  id: number
  name: string
  /** 类型：strength/plank/walk/stairs */
  type: ExerciseType
  /** 基础 MET（0=动态计算，如散步按速度定档） */
  baseMet: number
  /** 参考速度（个/分钟，用户平均节奏=中等强度基准；非计数类为空） */
  refSpeed: number | null
  /** 速度上限（个/分钟，世界纪录封顶防 MET 爆炸；缺省参考速度×3） */
  maxSpeed: number | null
  /** 是否记重量 */
  hasWeight: boolean
  /** 是否记左右手 */
  hasHand: boolean
  sortOrder: number
}

/** 锻炼记录（原始参数，大卡由前端计算） */
export interface ExerciseRecord {
  id: number
  exerciseId: number
  /** yyyy-MM-dd */
  recordDate: string
  /** 重量 kg（力量，自重留空） */
  weight: number | null
  /** 个数（力量/平板） */
  reps: number | null
  /** 分钟（力量 / 散步） */
  minutes: number | null
  /** 公里（散步） */
  distance: number | null
  /** 一次爬几层（爬楼梯） */
  floors: number | null
  /** 爬几次（爬楼梯） */
  times: number | null
  /** 秒（平板支撑） */
  seconds: number | null
  /** 左右手：left/right/both */
  hand: string | null
  note: string | null
  /** 记录时体重快照 kg（历史消耗固定，不随当前体重变；为空回退当前体重） */
  bodyWeight: number | null
  createdAt: string
}

/** 查询参数 */
export interface ExerciseRecordQuery {
  startDate?: string
  endDate?: string
  exerciseId?: number
  page?: number
  size?: number
}

/** 保存参数（新增/修改共用） */
export interface ExerciseRecordSave {
  exerciseId: number
  recordDate: string
  weight?: number | null
  reps?: number | null
  minutes?: number | null
  distance?: number | null
  floors?: number | null
  times?: number | null
  seconds?: number | null
  hand?: string | null
  note?: string
}

/** 我的动作列表 */
export function listExerciseItems() {
  return requestApi<ExerciseItem[]>({ url: '/exercise/items', method: 'GET' })
}

/** 新增自定义动作 */
export function createExerciseItem(data: { name: string; type: ExerciseType; baseMet: number; refSpeed?: number | null; hasWeight?: boolean; hasHand?: boolean }) {
  return requestApi<ExerciseItem>({ url: '/exercise/items', method: 'POST', data })
}

/** 修改动作 */
export function updateExerciseItem(id: number, data: Partial<ExerciseItem>) {
  return requestApi<ExerciseItem>({ url: `/exercise/items/${id}`, method: 'PUT', data })
}

/** 删除动作 */
export function deleteExerciseItem(id: number) {
  return requestApi<null>({ url: `/exercise/items/${id}`, method: 'DELETE' })
}

/** 分页查询记录 */
export function listExerciseRecords(query: ExerciseRecordQuery = {}) {
  return requestApi<PageResult<ExerciseRecord>>({
    url: '/exercise',
    method: 'GET',
    params: query
  })
}

/** 统计：返回近 14 天及今日/本周/本月的原始记录（前端算大卡） */
export function getExerciseStatistics() {
  return requestApi<{ records: ExerciseRecord[] }>({
    url: '/exercise/statistics',
    method: 'GET'
  })
}

/** 某动作最近一条记录（打卡页「上次」带出） */
export function getExerciseLatest(exerciseId: number) {
  return requestApi<ExerciseRecord | null>({
    url: '/exercise/latest',
    method: 'GET',
    params: { exerciseId }
  })
}

/** 新增锻炼记录 */
export function createExerciseRecord(data: ExerciseRecordSave) {
  return requestApi<ExerciseRecord>({ url: '/exercise', method: 'POST', data })
}

/** 修改锻炼记录 */
export function updateExerciseRecord(id: number, data: ExerciseRecordSave) {
  return requestApi<ExerciseRecord>({ url: `/exercise/${id}`, method: 'PUT', data })
}

/** 删除锻炼记录 */
export function deleteExerciseRecord(id: number) {
  return requestApi<null>({ url: `/exercise/${id}`, method: 'DELETE' })
}
