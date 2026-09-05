/**
 * 饮食接口：食物字典 + 饮食记录 + 整餐模板
 */
import { requestApi } from './request'
import type { PageResult } from './types'

/** 食物类型：staple主食/protein肉蛋/veg蔬菜/fruit水果/snack零食饮品/other */
export type FoodType = 'staple' | 'protein' | 'veg' | 'fruit' | 'snack' | 'other'

/** 餐次 */
export type MealType = 'breakfast' | 'lunch' | 'dinner' | 'snack'

/** 食物字典（每100g营养 + 默认份量参考） */
export interface FoodItem {
  id: number
  name: string
  type: FoodType
  /** 每100g热量 */
  kcal: number
  protein: number
  fat: number
  carbs: number
  /** 每100g钠 mg */
  sodium: number
  fiber: number
  /** 默认份量 g（个/根/碗等参考值） */
  defaultGrams: number | null
  /** 默认单位标签 */
  unitLabel: string | null
  favorite: boolean
  sortOrder: number
}

/** 饮食记录（原始参数，营养由前端按份量计算） */
export interface FoodRecord {
  id: number
  foodId: number
  recordDate: string
  mealType: MealType
  /** 份量 g */
  grams: number
  note: string | null
  createdAt: string
}

/** 整餐模板（items 为 JSON 数组字符串） */
export interface FoodMealTemplate {
  id: number
  name: string
  items: string
  sortOrder: number
}

/** 模板条目（items 解析后） */
export interface MealTemplateItem {
  foodId: number
  grams: number
  mealType: MealType
}

/** 查询参数 */
export interface FoodRecordQuery {
  startDate?: string
  endDate?: string
  mealType?: MealType
  page?: number
  size?: number
}

/** 保存参数（新增/修改共用） */
export interface FoodRecordSave {
  foodId: number
  recordDate: string
  mealType: MealType
  grams: number
  note?: string | null
}

/** 模板保存参数 */
export interface MealTemplateSave {
  name: string
  items: string
}

/** 食物类型 → 中文 */
export const FOOD_TYPE_LABELS: Record<FoodType, string> = {
  staple: '主食',
  protein: '肉蛋',
  veg: '蔬菜',
  fruit: '水果',
  snack: '零食饮品',
  other: '其他'
}

/** 餐次 → 中文 */
export const MEAL_LABELS: Record<MealType, string> = {
  breakfast: '早餐',
  lunch: '午餐',
  dinner: '晚餐',
  snack: '加餐'
}

/** 我的食物列表（收藏优先） */
export function listFoodItems() {
  return requestApi<FoodItem[]>({ url: '/food/items', method: 'GET' })
}

/** 新增自定义食物 */
export function createFoodItem(data: Partial<FoodItem> & { name: string; type: FoodType; kcal: number }) {
  return requestApi<FoodItem>({ url: '/food/items', method: 'POST', data })
}

/** 修改食物 */
export function updateFoodItem(id: number, data: Partial<FoodItem>) {
  return requestApi<FoodItem>({ url: `/food/items/${id}`, method: 'PUT', data })
}

/** 收藏/取消收藏 */
export function toggleFoodFavorite(id: number, favorite: boolean) {
  return requestApi<FoodItem>({ url: `/food/items/${id}/favorite`, method: 'PUT', params: { favorite } })
}

/** 删除食物 */
export function deleteFoodItem(id: number) {
  return requestApi<null>({ url: `/food/items/${id}`, method: 'DELETE' })
}

/** 分页查询饮食记录 */
export function listFoodRecords(query: FoodRecordQuery = {}) {
  return requestApi<PageResult<FoodRecord>>({ url: '/food', method: 'GET', params: query })
}

/** 区间全量记录（统计页聚合） */
export function getFoodStatistics(query: { startDate?: string; endDate?: string } = {}) {
  return requestApi<{ records: FoodRecord[] }>({ url: '/food/statistics', method: 'GET', params: query })
}

/** 新增饮食记录 */
export function createFoodRecord(data: FoodRecordSave) {
  return requestApi<FoodRecord>({ url: '/food', method: 'POST', data })
}

/** 修改饮食记录 */
export function updateFoodRecord(id: number, data: FoodRecordSave) {
  return requestApi<FoodRecord>({ url: `/food/${id}`, method: 'PUT', data })
}

/** 删除饮食记录 */
export function deleteFoodRecord(id: number) {
  return requestApi<null>({ url: `/food/${id}`, method: 'DELETE' })
}

/** 我的模板列表 */
export function listMealTemplates() {
  return requestApi<FoodMealTemplate[]>({ url: '/food/templates', method: 'GET' })
}

/** 新增模板 */
export function createMealTemplate(data: MealTemplateSave) {
  return requestApi<FoodMealTemplate>({ url: '/food/templates', method: 'POST', data })
}

/** 修改模板 */
export function updateMealTemplate(id: number, data: MealTemplateSave) {
  return requestApi<FoodMealTemplate>({ url: `/food/templates/${id}`, method: 'PUT', data })
}

/** 删除模板 */
export function deleteMealTemplate(id: number) {
  return requestApi<null>({ url: `/food/templates/${id}`, method: 'DELETE' })
}
