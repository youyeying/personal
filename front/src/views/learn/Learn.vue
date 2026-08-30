<script setup lang="ts">
/**
 * 学习 · 组合层（内容区页头三子页）
 * - 只负责：SubNav Tab 状态 + 三个子页组件渲染 + 跨页刷新协调
 * - 子页组件：components/LearnInput(记一笔)、LearnStats(统计)、LearnHistory(历史)
 * - 任一子页数据变更 emit('changed') → tick++ → 三个子页各自 watch 重载（等价原 refreshAll）
 * - 子页内跳转 emit('navigate', tab) → 切换 activeSub
 * - 统计组件加载后 emit('stats-loaded', duration) → SubNav hint 展示累计时长
 */
import { ref } from 'vue'
import SubNav, { type SubNavItem } from '@/components/SubNav/SubNav.vue'
import LearnInput from './components/LearnInput.vue'
import LearnStats from './components/LearnStats.vue'
import LearnHistory from './components/LearnHistory.vue'
import { durationParts } from './learnShared'

type SubTab = 'input' | 'stats' | 'history'
const activeSub = ref<SubTab>('input')
const subItems: SubNavItem[] = [
  { key: 'input', label: '记一笔' },
  { key: 'stats', label: '统计' },
  { key: 'history', label: '历史' }
]

/** 跨页刷新序号：任意子页数据变更后递增 */
const tick = ref(0)
function onChanged() {
  tick.value = tick.value + 1
}
/** 子页内跳转（如「查看全部」「去记一笔」） */
function onNavigate(tab: string) {
  activeSub.value = tab as SubTab
}

/** 统计组件回报的累计时长（用于 SubNav hint） */
const totalDuration = ref(0)
</script>

<template>
  <div class="lr">
    <!-- ===== 内容区页头（子页 Tab） ===== -->
    <SubNav
      :items="subItems"
      v-model:active="activeSub"
      :hint="`学习 · 累计 ${totalDuration ? durationParts(totalDuration).h + 'h' + durationParts(totalDuration).m + 'm' : '0m'}`"
    />

    <!-- ===== 子页1：记一笔 ===== -->
    <div v-show="activeSub === 'input'" class="lr__sub">
      <LearnInput :tick="tick" @changed="onChanged" @navigate="onNavigate" />
    </div>

    <!-- ===== 子页2：统计 ===== -->
    <div v-show="activeSub === 'stats'" class="lr__sub">
      <LearnStats
        :tick="tick"
        :active="activeSub === 'stats'"
        @stats-loaded="(d: number) => totalDuration = d"
        @navigate="onNavigate"
      />
    </div>

    <!-- ===== 子页3：历史 ===== -->
    <div v-show="activeSub === 'history'" class="lr__sub">
      <LearnHistory :tick="tick" @changed="onChanged" />
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use './learn';
</style>