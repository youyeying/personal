<script setup lang="ts">
/**
 * 公共组件 · 应用 Logo（随模块/路由切换专属图标）
 * 与导航栏菜单图标刻意不同：每个模块一枚独立的线性徽标
 * 父组件可传 kind 指定；不传时按当前模块（theme store）
 * 颜色取 --cb-mod（随模块变色），尺寸由使用方 CSS 控制
 */
import { computed } from 'vue'
import { useThemeStore } from '@/store/theme'

const props = withDefaults(
  defineProps<{
    /** 指定要显示的 Logo 类型（module key）；不传则跟随当前模块 */
    kind?: string
  }>(),
  { kind: '' }
)

const themeStore = useThemeStore()

/** 各模块专属 Logo（SVG 内部元素，24×24 视图，currentColor） */
const LOGOS: Record<string, string> = {
  // 首页概览：罗盘（全局视角）
  overview: `
    <circle cx="12" cy="12" r="8.5"/>
    <path d="M14.6 9.4l-1.5 3.7-3.7 1.5 1.5-3.7z"/>`,
  // 记账：钱包（钱袋 + 硬币）
  expense: `
    <rect x="2.5" y="6" width="19" height="12.5" rx="2"/>
    <path d="M2.5 7.5h13.5v3H2.5z" fill="currentColor" fill-opacity="0.18" stroke="none"/>
    <circle cx="16.8" cy="12.3" r="1" fill="currentColor" stroke="none"/>`,
  // 健康：心率
  health: `
    <path d="M12 20s-7-4.6-9.1-9A5 5 0 0 1 12 6.6 5 5 0 0 1 21.1 11C19 15.4 12 20 12 20z"/>
    <path d="M4.5 12h3.2l1.6-3.2 2.6 6 1.9-3.5H19"/>`,
  // 学习：翻开的书
  learn: `
    <path d="M12 6.6C10.2 5.2 7.5 4.6 4 4.6v13c3.5 0 6.2.6 8 2 1.8-1.4 4.5-2 8-2v-13c-3.5 0-6.2.6-8 2z"/>
    <path d="M12 6.6v13"/>`,
  // 每日总结：钢笔
  'daily-note': `
    <path d="M4 20l.9-3.4L15.7 5.8a1.5 1.5 0 0 1 2.1 0l.4.4a1.5 1.5 0 0 1 0 2.1L7.4 19.1 4 20z"/>
    <path d="M13.4 7.6l3 3"/>`,
  // 操作日志：剪贴板清单
  'operation-log': `
    <rect x="5" y="4" width="14" height="17" rx="2"/>
    <path d="M9 4.6V3h6v1.6"/>
    <path d="M8.7 11h6.6M8.7 15h4.4"/>`,
  // 开发日志：终端
  'dev-log': `
    <rect x="3" y="4" width="18" height="16" rx="2"/>
    <path d="M7 9l3 3-3 3"/>
    <path d="M12 15h5"/>`,
  // 个人中心：用户
  profile: `
    <circle cx="12" cy="8" r="3.6"/>
    <path d="M5 20c1.5-3.4 3.9-5 7-5s5.5 1.6 7 5"/>`
}

/** 当前要展示的 Logo（优先 kind prop，其次当前模块；无匹配回退首页） */
const current = computed(() => LOGOS[props.kind || themeStore.module.key] ?? LOGOS.overview)
</script>

<template>
  <svg
    class="app-logo"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.8"
    stroke-linecap="round"
    stroke-linejoin="round"
    v-html="current"
  />
</template>

<style lang="scss" scoped>
@use './appLogo';
</style>