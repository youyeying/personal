/**
 * 开发端模块主题 store：菜单即导航（开发日志/操作日志/基础数据管理）
 * - 布局按当前路由匹配模块，导航栏 / header 平滑过渡到对应主色
 * - 主色由 AdminLayout 写入容器 CSS 变量 `--sk-mod`
 */
import { defineStore } from 'pinia'
import { computed, markRaw } from 'vue'
import { useRoute } from 'vue-router'
import type { Component } from 'vue'
import { Notebook, List as ListIcon, Collection } from '@element-plus/icons-vue'

/** 单模块主题配置（与用户端 store/theme.ts 同结构，菜单数据不同） */
export interface ModuleTheme {
  key: string
  /** 路由前缀（供匹配；'/' 为首页），导航跳转用 */
  path: string
  /** 菜单名 */
  name: string
  desc: string
  /** 导航行右侧的小字标注 */
  tag: string
  /** 模块主色（开发端三模块各取一个协调色） */
  accent: string
  /** Element 图标组件 */
  icon: Component
}

/** 开发端菜单（顺序即导航顺序） */
export const MODULES: ModuleTheme[] = [
  { key: 'dev-log', path: '/dev-log', name: '开发日志', desc: '开发会话与功能记录', tag: '进度', accent: '#5f7a8c', icon: markRaw(Notebook) },
  { key: 'operation-log', path: '/operation-log', name: '操作日志', desc: '系统操作记录', tag: '轨迹', accent: '#6b7a66', icon: markRaw(ListIcon) },
  { key: 'template', path: '/template', name: '基础数据管理', desc: '食物/动作/分类全局模板', tag: '模板', accent: '#7a6a8c', icon: markRaw(Collection) }
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
