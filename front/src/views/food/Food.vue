<script setup lang="ts">
/**
 * 饮食 · 组合层（内容区页头三子页）
 * - 只负责：SubNav Tab 状态 + 三个子页组件渲染 + 跨页刷新协调
 * - 子页组件：components/FoodInput(记录)、FoodStats(统计)、FoodHistory(历史)
 * - 任一子页数据变更 emit('changed') → tick++ → 各子页各自 watch 重载
 */
import { ref } from 'vue'
import SubNav, { type SubNavItem } from '@/components/SubNav/SubNav.vue'
import FoodInput from './components/FoodInput.vue'
import FoodStats from './components/FoodStats.vue'
import FoodHistory from './components/FoodHistory.vue'

type SubTab = 'input' | 'stats' | 'history'
const activeSub = ref<SubTab>('input')
const subItems: SubNavItem[] = [
  { key: 'input', label: '记录' },
  { key: 'stats', label: '统计' },
  { key: 'history', label: '历史' }
]

/** 跨页刷新序号 */
const tick = ref(0)
function onChanged() {
  tick.value = tick.value + 1
}
/** 子页内跳转（如「查看历史」） */
function onNavigate(tab: string) {
  activeSub.value = tab as SubTab
}
</script>

<template>
  <div class="fd">
    <SubNav :items="subItems" v-model:active="activeSub" hint="饮食 · 摄入与消耗联动" />

    <div v-show="activeSub === 'input'" class="fd__sub">
      <FoodInput :tick="tick" @changed="onChanged" @navigate="onNavigate" />
    </div>

    <div v-show="activeSub === 'stats'" class="fd__sub">
      <FoodStats :tick="tick" :active="activeSub === 'stats'" />
    </div>

    <div v-show="activeSub === 'history'" class="fd__sub">
      <FoodHistory :tick="tick" @changed="onChanged" @navigate="onNavigate" />
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use './food';
</style>
