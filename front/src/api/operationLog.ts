/**
 * 操作日志接口
 */
import { requestApi } from './request'
import type { PageResult } from './types'

/** 操作日志记录 */
export interface OperationLog {
  id: number
  userId: number
  /** 操作模块：EXPENSE/WEIGHT/LEARN/USER/NOTE/EXERCISE */
  module: string
  /** 操作动作：CREATE/UPDATE/DELETE/RESTORE/LOGIN/REGISTER */
  action: string
  targetId: number | null
  content: string
  createdAt: string
  updatedAt: string
}

/** 分页查询操作日志（silent=true 时静默，用于导航栏取总数等后台加载） */
export function listOperationLogs(params: {
  module?: string
  action?: string
  startDate?: string
  endDate?: string
  page?: number
  size?: number
  silent?: boolean
}) {
  const { silent, ...rest } = params
  return requestApi<PageResult<OperationLog>>({
    url: '/operation-logs',
    method: 'GET',
    params: rest,
    silent
  })
}

/** 操作日志业务字典 */
export const LOG_MODULES = ['EXPENSE', 'WEIGHT', 'LEARN', 'USER', 'NOTE', 'EXERCISE', 'FOOD'] as const
export const LOG_ACTIONS = ['CREATE', 'UPDATE', 'DELETE', 'RESTORE', 'LOGIN', 'REGISTER'] as const

/** 模块中文显示 */
export const MODULE_LABELS: Record<string, string> = {
  EXPENSE: '记账',
  WEIGHT: '健康',
  LEARN: '学习',
  USER: '用户',
  NOTE: '笔记',
  EXERCISE: '锻炼',
  FOOD: '饮食'
}

/** 动作中文显示 */
export const ACTION_LABELS: Record<string, string> = {
  CREATE: '新增',
  UPDATE: '修改',
  DELETE: '删除',
  RESTORE: '恢复',
  LOGIN: '登录',
  REGISTER: '注册'
}