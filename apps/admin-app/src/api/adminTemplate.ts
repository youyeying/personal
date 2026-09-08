/**
 * 开发端基础数据模板管理接口（仅 userType=2 可访问）
 * 模板数据 user_id=0 全局共享；业务用户查询 = 模板 + 本人自定义并集
 */
import { requestApi } from './request'

/** 食物模板 */
export interface TemplateFood {
  id: number
  name: string
  /** staple 主食 / meat 肉蛋 / veg 蔬菜 / fruit 水果 / dairy 奶制品 / snack 零食饮品 */
  type: string
  kcal: number
  protein: number
  fat: number
  carbs: number
  sodium: number
  fiber: number
  defaultGrams: number
  /** 单位标签：液体 ml / 固体 g */
  unitLabel: string
  sortOrder: number
}

/** 动作模板 */
export interface TemplateExercise {
  id: number
  name: string
  /** strength / plank / walk / cycling / stairs / cardio */
  type: string
  baseMet: number
  refSpeed: number | null
  maxSpeed: number | null
  hasWeight: boolean
  hasHand: boolean
  sortOrder: number
}

/** 收支分类模板 */
export interface TemplateCategory {
  id: number
  name: string
  /** 1 支出 / 2 收入 */
  type: number
  sortOrder: number
}

// ===================== 食物模板 =====================

export function listTemplateFoods() {
  return requestApi<TemplateFood[]>({ url: '/admin/foods', method: 'GET' })
}

export function createTemplateFood(data: Partial<TemplateFood>) {
  return requestApi<TemplateFood>({ url: '/admin/foods', method: 'POST', data })
}

export function updateTemplateFood(id: number, data: Partial<TemplateFood>) {
  return requestApi<TemplateFood>({ url: `/admin/foods/${id}`, method: 'PUT', data })
}

export function deleteTemplateFood(id: number) {
  return requestApi<null>({ url: `/admin/foods/${id}`, method: 'DELETE' })
}

// ===================== 动作模板 =====================

export function listTemplateExercises() {
  return requestApi<TemplateExercise[]>({ url: '/admin/exercises', method: 'GET' })
}

export function createTemplateExercise(data: Partial<TemplateExercise>) {
  return requestApi<TemplateExercise>({ url: '/admin/exercises', method: 'POST', data })
}

export function updateTemplateExercise(id: number, data: Partial<TemplateExercise>) {
  return requestApi<TemplateExercise>({ url: `/admin/exercises/${id}`, method: 'PUT', data })
}

export function deleteTemplateExercise(id: number) {
  return requestApi<null>({ url: `/admin/exercises/${id}`, method: 'DELETE' })
}

// ===================== 分类模板 =====================

export function listTemplateCategories() {
  return requestApi<TemplateCategory[]>({ url: '/admin/categories', method: 'GET' })
}

export function createTemplateCategory(data: Partial<TemplateCategory>) {
  return requestApi<TemplateCategory>({ url: '/admin/categories', method: 'POST', data })
}

export function updateTemplateCategory(id: number, data: Partial<TemplateCategory>) {
  return requestApi<TemplateCategory>({ url: `/admin/categories/${id}`, method: 'PUT', data })
}

export function deleteTemplateCategory(id: number) {
  return requestApi<null>({ url: `/admin/categories/${id}`, method: 'DELETE' })
}
