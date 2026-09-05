<script setup lang="ts">
/**
 * 锻炼 · 组合层（内容区页头四子页）
 * - 只负责：SubNav Tab 状态 + 四个子页组件渲染 + 跨页刷新协调
 * - 子页组件：components/ExerciseInput(打卡)、ExerciseStats(统计)、ExerciseHistory(历史)、ExerciseAnalysis(消耗分析)
 * - 任一子页数据变更 emit('changed') → tick++ → 各子页各自 watch 重载（等价原 refreshAll）
 * - 子页内跳转 emit('navigate', tab) → 切换 activeSub
 * - 注：采用独立导航模块形态（App 端将来与健康合并分页时，本骨架可直接平移为分页容器）
 */
import { ref } from 'vue'
import SubNav, { type SubNavItem } from '@/components/SubNav/SubNav.vue'
import ExerciseInput from './components/ExerciseInput.vue'
import ExerciseStats from './components/ExerciseStats.vue'
import ExerciseHistory from './components/ExerciseHistory.vue'
import ExerciseAnalysis from './components/ExerciseAnalysis.vue'
import type { ExerciseRecord } from '@/api/exercise'

type SubTab = 'input' | 'stats' | 'history' | 'analysis'
const activeSub = ref<SubTab>('input')
const subItems: SubNavItem[] = [
  { key: 'input', label: '打卡' },
  { key: 'stats', label: '统计' },
  { key: 'history', label: '历史' },
  { key: 'analysis', label: '消耗分析' }
]

/** 跨页刷新序号：任意子页数据变更后递增 */
const tick = ref(0)
function onChanged() {
  tick.value = tick.value + 1
}
/** 子页内跳转（如「去打卡」「查看历史」） */
function onNavigate(tab: string) {
  activeSub.value = tab as SubTab
}

/** 锻炼打卡组件实例（历史页「复制」时调用其 reuse 并切回打卡子页） */
const exInputRef = ref<InstanceType<typeof ExerciseInput> | null>(null)
function onCopyExercise(r: ExerciseRecord) {
  // 先切回打卡子页（历史已是独立子页，不切回看不到填充的表单），再填充并滚到表单
  activeSub.value = 'input'
  exInputRef.value?.reuse(r)
  requestAnimationFrame(() => {
    document.querySelector('.exi')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  })
}
</script>

<template>
  <div class="ex">
    <!-- ===== 内容区页头（子页 Tab） ===== -->
    <SubNav :items="subItems" v-model:active="activeSub" hint="锻炼 · 打卡与消耗分析" />

    <!-- ===== 子页1：打卡 ===== -->
    <div v-show="activeSub === 'input'" class="ex__sub">
      <ExerciseInput :ref="(el: any) => exInputRef = el" :tick="tick" @changed="onChanged" />
    </div>

    <!-- ===== 子页2：统计 ===== -->
    <div v-show="activeSub === 'stats'" class="ex__sub">
      <ExerciseStats :tick="tick" :active="activeSub === 'stats'" @navigate="onNavigate" />
    </div>

    <!-- ===== 子页3：历史 ===== -->
    <div v-show="activeSub === 'history'" class="ex__sub">
      <ExerciseHistory :tick="tick" @changed="onChanged" @copy="onCopyExercise" />
    </div>

    <!-- ===== 子页4：消耗分析 ===== -->
    <div v-show="activeSub === 'analysis'" class="ex__sub">
      <ExerciseAnalysis :tick="tick" :active="activeSub === 'analysis'" />
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use './exercise';
</style>