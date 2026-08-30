<script setup lang="ts">
/**
 * 公共组件 · 模块占位页（各业务主页未实现前的统一空态）
 * 父组件可传入 title / desc / icon；不传时回退为当前模块（theme store）
 * 主色随模块 --cb-mod 变化
 */
import { computed } from 'vue'
import type { Component } from 'vue'
import { useThemeStore } from '@/store/theme'

const props = withDefaults(
  defineProps<{
    /** 占位标题（默认取当前模块名） */
    title?: string
    /** 占位说明（默认取当前模块描述） */
    desc?: string
    /** 图标组件（默认取当前模块图标） */
    icon?: Component
  }>(),
  {
    title: '',
    desc: '',
    icon: undefined
  }
)

const themeStore = useThemeStore()

const current = computed(() => ({
  title: props.title || themeStore.module.name,
  desc: props.desc || themeStore.module.desc,
  icon: props.icon ?? themeStore.module.icon
}))
</script>

<template>
  <div class="module-placeholder">
    <div class="module-placeholder__card">
      <el-icon class="module-placeholder__icon">
        <component :is="current.icon" />
      </el-icon>
      <h2 class="module-placeholder__name">{{ current.title }}</h2>
      <p class="module-placeholder__desc">{{ current.desc }} —— 功能开发中</p>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use './modulePlaceholder';
</style>