/**
 * 健康接口：体重记录 分页 / 趋势 / 打卡 / 修改 / 删除
 */
import { requestApi } from './request'
import type { PageResult } from './types'

/** 体重记录 */
export interface WeightRecord {
  id: number
  weight: number
  /** 体脂率 %（可选） */
  bodyFat: number | null
  /** 腰围 cm（可选） */
  waist: number | null
  note: string | null
  /** yyyy-MM-dd */
  recordDate: string
  createdAt: string
}

/** 查询参数 */
export interface WeightQuery {
  startDate?: string
  endDate?: string
  page?: number
  size?: number
}

/** 趋势数据（日期升序，体脂/腰围可为 null） */
export interface WeightTrend {
  dates: string[]
  weights: number[]
  bodyFats: (number | null)[]
  waists: (number | null)[]
}

/** 保存参数（打卡 / 修改共用） */
export interface WeightSave {
  weight: number
  bodyFat?: number | null
  waist?: number | null
  note?: string
  recordDate: string
}

/** 分页查询（最新在前） */
export function listWeightRecords(query: WeightQuery = {}) {
  return requestApi<PageResult<WeightRecord>>({
    url: '/weight-records',
    method: 'GET',
    params: query
  })
}

/** 体重趋势（日期升序） */
export function getWeightTrend() {
  return requestApi<WeightTrend>({
    url: '/weight-records/trend',
    method: 'GET'
  })
}

/** 打卡（新增一条体重记录） */
export function createWeightRecord(data: WeightSave) {
  return requestApi<WeightRecord>({
    url: '/weight-records',
    method: 'POST',
    data
  })
}

/** 修改记录 */
export function updateWeightRecord(id: number, data: WeightSave) {
  return requestApi<WeightRecord>({
    url: `/weight-records/${id}`,
    method: 'PUT',
    data
  })
}

/** 删除记录 */
export function deleteWeightRecord(id: number) {
  return requestApi<null>({
    url: `/weight-records/${id}`,
    method: 'DELETE'
  })
}
