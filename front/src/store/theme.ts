/**
 * 模块主题 store：每个业务模块拥有独立主色
 * - 布局按当前路由匹配模块，导航栏 / header 平滑过渡到对应主色
 * - 主色由 Home.vue 写入容器 CSS 变量 `--cb-mod`，供各组件经 color-mix 派生深浅值
 */
import { defineStore } from 'pinia'
import { computed, markRaw } from 'vue'
import { useRoute } from 'vue-router'
import type { Component } from 'vue'
import {
  House,
  Wallet,
  Opportunity,
  Basketball,
  Food,
  Reading,
  Notebook,
  EditPen,
  List,
  Monitor,
  User
} from '@element-plus/icons-vue'

/** 单模块主题配置 */
export interface ModuleTheme {
  key: string
  /** 路由前缀（供匹配；'/' 为首页），导航跳转用 */
  path: string
  /** 菜单名 */
  name: string
  desc: string
  /** 导航行右侧的小字标注（未指定的先用占位设计，后续补） */
  tag: string
  /** 模块主色（暖色系内协调，与画布 #f2ede4 / 文本 #2c312f 融合） */
  accent: string
  /** Element 图标组件 */
  icon: Component
}

/** 全部模块主题（顺序即导航顺序） */
export const MODULES: ModuleTheme[] = [
  { key: 'overview', path: '/', name: '首页概览', desc: '今日与本月收支、体重、学习全貌', tag: 'LIVE', accent: '#a8765a', icon: markRaw(House) },
  { key: 'expense', path: '/expense', name: '记账', desc: '收支记录、分类与统计', tag: '存钱余额', accent: '#c08a3e', icon: markRaw(Wallet) },
  { key: 'health', path: '/health', name: '健康', desc: '体重打卡与趋势', tag: '减重中', accent: '#3f7a72', icon: markRaw(Opportunity) },
  { key: 'exercise', path: '/exercise', name: '锻炼', desc: '锻炼打卡、统计与消耗分析', tag: '燃脂中', accent: '#b0653f', icon: markRaw(Basketball) },
  { key: 'food', path: '/food', name: '饮食', desc: '饮食记录、能量结余与营养分析', tag: '控卡中', accent: '#7a8c3e', icon: markRaw(Food) },
  { key: 'learn', path: '/learn', name: '学习', desc: '学习记录与附件', tag: '精进', accent: '#4f7a8c', icon: markRaw(Reading) },
  { key: 'daily-note', path: '/daily-note', name: '每日总结', desc: '每天一句话小结', tag: '今日心情', accent: '#a8716a', icon: markRaw(Notebook) },
  { key: 'report', path: '/report', name: '周报', desc: '跨模块周 / 月复盘汇总', tag: '复盘', accent: '#8a6a4f', icon: markRaw(EditPen) },
  { key: 'operation-log', path: '/operation-log', name: '操作日志', desc: '系统操作记录', tag: '轨迹', accent: '#6b7a66', icon: markRaw(List) },
  { key: 'dev-log', path: '/dev-log', name: '开发日志', desc: '开发会话与功能记录', tag: '进度', accent: '#5f7a8c', icon: markRaw(Monitor) },
  { key: 'profile', path: '/profile', name: '个人中心', desc: '昵称、密码、目标体重', tag: '我的', accent: '#8a8a80', icon: markRaw(User) }
]

/** 根据路径解析所属模块；非 '/' 的子路径按前缀归属，否则回首页 */
function matchModule(path: string): ModuleTheme {
  return MODULES.find((m) => m.path !== '/' && path.startsWith(m.path)) ?? MODULES[0]
}

export const useThemeStore = defineStore('theme', () => {
  const route = useRoute()

  /** 当前模块：随路由导航响应式变化 */
  const module = computed(() => matchModule(route.path))

  return { module }
})