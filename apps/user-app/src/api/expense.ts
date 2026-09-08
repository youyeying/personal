/**
 * 记账接口：收支分类 + 记录分页 + 统计 + 增删改
 */
import { requestApi } from './request'
import type { PageResult } from './types'

/** 收支分类 */
export interface ExpenseCategory {
  id: number
  userId: number
  name: string
  /** 1 支出 / 2 收入 */
  type: number
  sortOrder: number
  createdAt: string
  updatedAt: string
}

/** 记账记录（分页返回，含分类名） */
export interface ExpenseRecord {
  id: number
  type: number
  categoryId: number
  categoryName: string
  amount: number
  note: string
  /** yyyy-MM-dd */
  recordDate: string
  createdAt: string
}

/** 查询参数 */
export interface ExpenseQuery {
  type?: number
  categoryId?: number
  startDate?: string
  endDate?: string
  page?: number
  size?: number
}

/** 分类汇总项 */
export interface CategoryStat {
  categoryId: number
  categoryName: string
  amount: number
}

/** 统计结果 */
export interface ExpenseStatistics {
  totalExpense: number
  totalIncome: number
  balance: number
  expenseByCategory: CategoryStat[]
  incomeByCategory: CategoryStat[]
}

/** 分类列表（type 可选，缺省返回全部） */
export function getCategories(type?: number | 'all') {
  return requestApi<ExpenseCategory[]>({
    url: '/expense-categories',
    method: 'GET',
    params: type && type !== 'all' ? { type } : {}
  })
}

/** 分页查询记录 */
export function listExpenseRecords(query: ExpenseQuery = {}) {
  return requestApi<PageResult<ExpenseRecord>>({
    url: '/expense-records',
    method: 'GET',
    params: query
  })
}

/** 统计：总支出 / 总收入 / 分类汇总（按日期范围） */
export function getExpenseStatistics(query: Omit<ExpenseQuery, 'page' | 'size'> = {}) {
  return requestApi<ExpenseStatistics>({
    url: '/expense-records/statistics',
    method: 'GET',
    params: query
  })
}

/** 保存参数（新增 / 修改共用） */
export interface ExpenseSaveData {
  type: number
  categoryId: number
  amount: number
  note?: string
  recordDate: string
}

/** 新增记录 */
export function createExpenseRecord(data: ExpenseSaveData) {
  return requestApi<ExpenseRecord>({
    url: '/expense-records',
    method: 'POST',
    data
  })
}

/** 修改记录 */
export function updateExpenseRecord(id: number, data: ExpenseSaveData) {
  return requestApi<ExpenseRecord>({
    url: `/expense-records/${id}`,
    method: 'PUT',
    data
  })
}

/** 删除记录 */
export function deleteExpenseRecord(id: number) {
  return requestApi<null>({
    url: `/expense-records/${id}`,
    method: 'DELETE'
  })
}