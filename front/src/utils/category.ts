/**
 * 记账模块：分类名 → Lucide 图标映射（记一笔 与 明细 两个子页共用）
 */
import type { Component } from 'vue'
import {
  Utensils, Car, ShoppingBag, House, Gamepad2, Pill, BookOpen,
  Gift, MoreHorizontal, Briefcase, Laptop, TrendingUp
} from '@lucide/vue'

/** 分类名 → Lucide 图标（未匹配回退 MoreHorizontal） */
const CATEGORY_ICONS: Record<string, Component> = {
  餐饮: Utensils,
  交通: Car,
  购物: ShoppingBag,
  居住: House,
  娱乐: Gamepad2,
  医疗: Pill,
  学习: BookOpen,
  人情: Gift,
  工资: Briefcase,
  副业: Laptop,
  理财: TrendingUp,
  红包: Gift,
  其他: MoreHorizontal
}

/** 取分类图标（名匹配失败用默认 MoreHorizontal） */
export function catIcon(name: string): Component {
  return CATEGORY_ICONS[name] ?? MoreHorizontal
}