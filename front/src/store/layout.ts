/**
 * 布局 store：管理导航栏三态（导航栏 / 缩导航栏 / 隐藏）
 * 术语（用户约定）：
 * - 导航栏   ：完全展开（宽 232）
 * - 缩导航栏 ：仅图标（宽 76），<1280 自动收缩或手动收缩
 * - 隐藏导航栏：不在布局流内（手动隐藏，或 <600 自动隐藏成抽屉）
 * - 全屏导航栏：仅页面宽度 <600 时，点 header 菜单按钮以全屏 overlay 弹出
 */
import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

/** 侧边栏宽度（用户确认 280px） */
export const NAV_WIDTH = 280
/** 收缩态宽度（仅图标） */
export const NAV_COLLAPSED_WIDTH = 76
/** 低于此宽度自动收缩 */
export const BREAKPOINT_AUTO_COLLAPSE = 1280
/** 低于此宽度进入抽屉（全屏）态 */
export const BREAKPOINT_DRAWER = 600

export const useLayoutStore = defineStore('layout', () => {
  /** 当前视口宽度 */
  const width = ref(typeof window !== 'undefined' ? window.innerWidth : 1920)
  /** 手动收缩（导航栏 ↔ 缩导航栏），仅在侧栏在流内时生效 */
  const collapsed = ref(false)
  /** 手动隐藏导航栏 */
  const hidden = ref(false)
  /** 抽屉（全屏导航栏）是否展开 */
  const drawerOpen = ref(false)

  /** 是否处于自动抽屉模式（<600px） */
  const isAutoDrawer = computed(() => width.value < BREAKPOINT_DRAWER)
  /** 是否需按宽度自动收缩（<1280px） */
  const autoCollapse = computed(() => width.value < BREAKPOINT_AUTO_COLLAPSE)
  /** 收缩态是否生效 */
  const isCollapsed = computed(() => collapsed.value || autoCollapse.value)
  /** 侧栏是否占用布局流（非隐藏且非自动抽屉） */
  const inFlow = computed(() => !hidden.value && !isAutoDrawer.value)
  /** 侧栏实际宽度（不在流内时 0，配合宽度过渡实现隐藏动画） */
  const navWidth = computed(() => (inFlow.value ? (isCollapsed.value ? NAV_COLLAPSED_WIDTH : NAV_WIDTH) : 0))
  /** header 菜单按钮是否显示（隐藏导航栏 或 自动抽屉模式） */
  const showHeaderMenu = computed(() => hidden.value || isAutoDrawer.value)

  /** 响应视口宽度变化 */
  function handleResize(w: number) {
    const wasAutoDrawer = width.value < BREAKPOINT_DRAWER
    width.value = w
    const nowAutoDrawer = w < BREAKPOINT_DRAWER
    if (nowAutoDrawer) {
      drawerOpen.value = false
    } else if (wasAutoDrawer) {
      // 从小屏回到大屏：自动恢复导航栏显示
      hidden.value = false
    }
  }

  /** 收缩 ↔ 完整 切换 */
  function toggleCollapse() {
    collapsed.value = !collapsed.value
  }

  /** 隐藏导航栏（手动） */
  function closeNav() {
    hidden.value = true
    drawerOpen.value = false
  }

  /** 恢复导航栏（手动隐藏后） */
  function restoreNav() {
    hidden.value = false
  }

  /** 打开抽屉（全屏导航栏，仅 <600 用） */
  function openDrawer() {
    drawerOpen.value = true
  }

  /** 关闭抽屉 */
  function closeDrawer() {
    drawerOpen.value = false
  }

  /**
   * header 左上角菜单按钮的统一行为：
   * - 页面 <600：打开全屏导航栏
   * - 手动隐藏：恢复导航栏（不弹全屏）
   */
  function onHeaderMenu() {
    if (isAutoDrawer.value) {
      openDrawer()
    } else if (hidden.value) {
      restoreNav()
    }
  }

  return { width, collapsed, hidden, drawerOpen, isAutoDrawer, autoCollapse, isCollapsed, inFlow, navWidth, showHeaderMenu, handleResize, toggleCollapse, closeNav, restoreNav, openDrawer, closeDrawer, onHeaderMenu }
})