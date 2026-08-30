<script setup lang="ts">
/**
 * 健康 · 组合层（内容区页头四子页）
 * - 只负责：SubNav Tab 状态 + 四个子页组件渲染 + 跨页刷新协调
 * - 子页组件：components/HealthInput(打卡)、HealthTrend(趋势)、ExerciseInput(锻炼)、HealthHistory(历史)
 * - 任一子页数据变更 emit('changed') → tick++ → 各子页各自 watch 重载（等价原 refreshAll）
 * - 子页内跳转 emit('navigate', tab) → 切换 activeSub
 */
import { computed, ref } from 'vue'
import SubNav, { type SubNavItem } from '@/components/SubNav/SubNav.vue'
import { useUserStore } from '@/store/user'
import HealthInput from './components/HealthInput.vue'
import HealthTrend from './components/HealthTrend.vue'
import HealthHistory from './components/HealthHistory.vue'
import ExerciseInput from './components/ExerciseInput.vue'
import ExerciseStats from './components/ExerciseStats.vue'
import ExerciseHistory from './components/ExerciseHistory.vue'
import type { ExerciseRecord } from '@/api/exercise'

const userStore = useUserStore()

type SubTab = 'input' | 'trend' | 'exercise' | 'history'
const activeSub = ref<SubTab>('input')
const subItems: SubNavItem[] = [
  { key: 'input', label: '打卡' },
  { key: 'trend', label: '趋势' },
  { key: 'exercise', label: '锻炼' },
  { key: 'history', label: '历史' }
]

/** 目标体重（用于 SubNav hint，与趋势页内目标虚线同源） */
const targetWeight = computed(() => userStore.userInfo?.targetWeight ?? null)

/** 跨页刷新序号：任意子页数据变更后递增 */
const tick = ref(0)
function onChanged() {
  tick.value = tick.value + 1
}
/** 子页内跳转（如「查看全部」「去打卡」） */
function onNavigate(tab: string) {
  activeSub.value = tab as SubTab
}

/** 锻炼打卡组件实例（历史页「复制」时调用其 reuse 并滚回表单） */
const exInputRef = ref<InstanceType<typeof ExerciseInput> | null>(null)
function onCopyExercise(r: ExerciseRecord) {
  exInputRef.value?.reuse(r)
  // 滚动回页面顶部，让用户看到已填充的表单
  requestAnimationFrame(() => {
    document.querySelector('.exi')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  })
}
</script>

<template>
  <div class="hl">
    <!-- ===== 内容区页头（子页 Tab） ===== -->
    <SubNav
      :items="subItems"
      v-model:active="activeSub"
      :hint="`健康 · ${targetWeight != null ? `目标体重 ${targetWeight}kg` : '体重管理'}`"
    />

    <!-- ===== 子页1：打卡 ===== -->
    <div v-show="activeSub === 'input'" class="hl__sub">
      <HealthInput :tick="tick" @changed="onChanged" @navigate="onNavigate" />
    </div>

    <!-- ===== 子页2：趋势 ===== -->
    <div v-show="activeSub === 'trend'" class="hl__sub">
      <HealthTrend
        :tick="tick"
        :active="activeSub === 'trend'"
        @navigate="onNavigate"
      />
    </div>

    <!-- ===== 子页3：锻炼 ===== -->
    <div v-show="activeSub === 'exercise'" class="hl__sub">
      <ExerciseInput :ref="(el: any) => exInputRef = el" :tick="tick" @changed="onChanged" />
      <ExerciseStats :tick="tick" :active="activeSub === 'exercise'" @navigate="onNavigate" />
      <ExerciseHistory :tick="tick" @changed="onChanged" @copy="onCopyExercise" />
    </div>

    <!-- ===== 子页4：历史 ===== -->
    <div v-show="activeSub === 'history'" class="hl__sub">
      <HealthHistory :tick="tick" @changed="onChanged" />
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use './health';
</style>