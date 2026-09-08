/**
 * 通用接口类型（跨模块复用）
 */

/** 分页查询参数（可选） */
export interface PageQuery {
  page?: number
  size?: number
}

/** 分页结果（后端统一返回结构） */
export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
}