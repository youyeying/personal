/**
 * 首页概览接口：今日 + 本月汇总、目标进度、分类构成
 */
import { requestApi } from './request'

/** 今日四模块完成度（记账 / 体重 / 学习 / 每日总结） */
export interface OverviewDone {
  expense: boolean
  weight: boolean
  learn: boolean
  note: boolean
}

/** 今日概览 */
export interface TodayOverview {
  /** 今日支出 */
  expense: number
  /** 今日收入 */
  income: number
  /** 支出笔数 */
  expenseCount: number
  incomeCount: number
  /** 今日体重（未记录为 null） */
  weight: number | null
  /** 今日学习分钟数 */
  learnMinutes: number
  learnCount: number
  done: OverviewDone
}

/** 本月分类构成（Top5，超出合并为「其他」） */
export interface CategoryItem {
  name: string
  amount: number
}

/** 本月概览 */
export interface MonthOverview {
  expense: number
  income: number
  balance: number
  learnMinutes: number
  /** 最新体重 */
  latestWeight: number | null
  /** 上一次体重（用于较上次变化） */
  previousWeight: number | null
  /** 起始体重（最早一条记录） */
  startWeight: number | null
  /** 目标体重（个人中心可设，未设 null） */
  targetWeight: number | null
  expenseCategories: CategoryItem[]
}

export interface OverviewData {
  today: TodayOverview
  month: MonthOverview
  /** 全量可支配余额（历史收入−支出，发薪自动+工资） */
  disposable: number
}

/** 首页概览（今日 + 本月） */
export function getOverview() {
  return requestApi<OverviewData>({
    url: '/overview',
    method: 'GET'
  })
}