<script setup lang="ts">
/**
 * 公共布局 · 左侧导航栏
 * 三态：导航栏（完整）/ 缩导航栏（仅图标 + tooltip）/ 隐藏（不在流内，宽度过渡隐藏）
 * - 底部「白天黑夜 + 版本号」固定在侧栏底部：菜单滚动时不被卷走
 * - 行右侧 tag：概览=LIVE、记账=存钱余额；操作日志=实时「共 N 条」；开发日志=今日时长或「休息」
 * - fullscreen 模式（<600 全屏抽屉）：占满宿主，右上关闭按钮关闭抽屉
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Expand, Fold, Close } from '@element-plus/icons-vue'
import { MODULES, useThemeStore } from '@/store/theme'
import type { ModuleTheme } from '@/store/theme'
import { useLayoutStore, BREAKPOINT_AUTO_COLLAPSE } from '@/store/layout'
import { APP_VERSION } from '@/config'
import { listOperationLogs } from '@/api/operationLog'
import { getDevSummary } from '@/api/dev'
import AppLogo from './AppLogo.vue'
import ThemeToggle from './ThemeToggle.vue'

/** 是否全屏模式（抽屉 overlay 时由 Home 传入） */
defineProps<{ fullscreen?: boolean }>()

const route = useRoute()
const themeStore = useThemeStore()
const layout = useLayoutStore()

/** 操作日志总数（tag 用） */
const opLogTotal = ref<number | null>(null)
/** 今日开发时长文本（tag 用） */
const devDuration = ref('')

/** 是否当前激活：首页精确匹配 '/'，其余按路由前缀 */
function isActive(path: string): boolean {
  if (path === '/') return route.path === '/'
  return route.path.startsWith(path)
}

/** 行右侧 tag：动态模块取实时值，其余用静态配置 */
function displayTag(m: ModuleTheme): string {
  if (m.key === 'operation-log') return opLogTotal.value != null ? `共 ${opLogTotal.value} 条` : ''
  if (m.key === 'dev-log') return devDuration.value ? `今日 ${devDuration.value}` : '休息'
  return m.tag
}

/** 拉取今日开发时长（tag 用，静默，失败不影响布局）——单独拆出以便轮询只刷它 */
async function loadDevDuration() {
  try {
    const sum = await getDevSummary(undefined, true)
    const m = sum?.durationMinutes ?? 0
    devDuration.value = m > 0 ? `${Math.floor(m / 60)}h ${m % 60}m` : ''
  } catch {
    devDuration.value = ''
  }
}

/** 拉取动态 tag 数据（初始 / 路由切换时全量刷新） */
async function loadDynamicTags() {
  try {
    const res = await listOperationLogs({ page: 1, size: 1, silent: true })
    opLogTotal.value = res.total
  } catch {
    opLogTotal.value = null
  }
  await loadDevDuration()
}

/** 开发会话进行中时今日时长持续增长：每 60s 静默轮询一次 */
let tagTimer: ReturnType<typeof setInterval> | null = null
function startTagPolling() {
  stopTagPolling()
  tagTimer = setInterval(loadDevDuration, 60_000)
}
function stopTagPolling() {
  if (tagTimer) {
    clearInterval(tagTimer)
    tagTimer = null
  }
}

onMounted(() => {
  loadDynamicTags()
  startTagPolling()
})
// 切换路由时刷新（如刚记了日志/开发时长有变化），并重启轮询
watch(() => route.path, () => {
  loadDynamicTags()
  startTagPolling()
})
onBeforeUnmount(stopTagPolling)

/**
 * 点击左上角 Logo 的行为（替代原右边框按钮）：
 * - 页面 <600（自动抽屉）：打开全屏导航栏
 * - 页面 <1280：切换隐藏 / 恢复导航栏
 * - 页面 ≥1280：仅当处于缩导航栏（手动收缩）时展开；完整展开时点击无操作
 */
function onLogoClick() {
  if (layout.isAutoDrawer) {
    layout.openDrawer()
    return
  }
  if (layout.width < BREAKPOINT_AUTO_COLLAPSE) {
    if (layout.hidden) {
      layout.restoreNav()
    } else {
      layout.closeNav()
    }
    return
  }
  if (layout.collapsed) {
    layout.collapsed = false
  }
}
</script>

<template>
  <aside
    class="side-nav"
    :class="{
      'side-nav--fullscreen': fullscreen,
      'side-nav--collapsed': !fullscreen && layout.isCollapsed,
      'side-nav--hidden': !fullscreen && !layout.inFlow
    }"
    :style="fullscreen ? undefined : { width: layout.navWidth + 'px' }"
  >
    <!-- 可滚动区域：仅品牌行 + 菜单 -->
    <div class="side-nav__scroller">
      <!-- 首行：Logo + 品牌 + 右侧按钮 -->
      <div class="side-nav__brand" @click="onLogoClick">
        <span class="side-nav__brand-left">
          <span class="side-nav__logo"><AppLogo /></span>
          <span v-if="fullscreen || !layout.isCollapsed" class="side-nav__brand-name">个人记录</span>
        </span>
        <span v-if="fullscreen" class="side-nav__controls">
          <!-- @click.stop：阻止冒泡到品牌行 onLogoClick（<600 会重新 openDrawer）导致关闭又打开 -->
          <button class="side-nav__icon-btn" type="button" title="关闭全屏导航" @click.stop="layout.closeDrawer">
            <el-icon><Close /></el-icon>
          </button>
        </span>
        <span v-else-if="layout.inFlow" class="side-nav__controls">
          <button
            class="side-nav__icon-btn"
            type="button"
            :title="layout.isCollapsed ? '展开导航栏' : '收缩导航栏'"
            @click.stop="layout.toggleCollapse"
          >
            <el-icon><component :is="layout.isCollapsed ? Expand : Fold" /></el-icon>
          </button>
          <button class="side-nav__icon-btn" type="button" title="隐藏导航栏" @click.stop="layout.closeNav">
            <el-icon><Close /></el-icon>
          </button>
        </span>
      </div>

      <!-- 菜单 -->
      <nav class="side-nav__menu">
        <el-tooltip
          v-for="m in MODULES"
          :key="m.key"
          :disabled="fullscreen || !layout.isCollapsed"
          :content="m.name"
          placement="right"
          :show-after="200"
        >
          <router-link
            :to="m.path"
            class="side-nav__item"
            :class="{ 'side-nav__item--active': isActive(m.path) }"
          >
            <span class="side-nav__item-main">
              <el-icon class="side-nav__item-icon"><component :is="m.icon" /></el-icon>
              <span v-if="fullscreen || !layout.isCollapsed" class="side-nav__item-label">{{ m.name }}</span>
            </span>
            <span v-if="fullscreen || !layout.isCollapsed" class="side-nav__item-tag">{{ displayTag(m) }}</span>
          </router-link>
        </el-tooltip>
      </nav>
    </div>

    <!-- 底部：白天黑夜开关（上）+ 版本号（下），固定在侧栏底部
         仅在 全屏抽屉 或 导航在流内 时显示；导航隐藏（不在流内）时开关交给 header 右上角 -->
    <div v-if="fullscreen || layout.inFlow" class="side-nav__foot">
      <span class="side-nav__foot-theme">
        <ThemeToggle />
      </span>
      <span v-if="fullscreen || !layout.isCollapsed" class="side-nav__version">v{{ APP_VERSION }}</span>
    </div>
  </aside>
</template>

<style lang="scss" scoped>
@use './sidenav';
</style>