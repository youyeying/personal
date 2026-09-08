<script setup lang="ts">
/**
 * 记账 · 组合层（内容区页头三子页）
 * - 只负责：SubNav Tab 状态 + 三个子页组件渲染 + 共享分类加载 + 跨页刷新协调
 * - 子页组件：components/ExpenseInput(记一笔)、ExpenseOverview(概览)、ExpenseDetail(明细)
 * - 分类由本层加载一次，下发给 记一笔(分类宫格) 与 明细(筛选/弹窗下拉) 共享
 * - 任一子页数据变更 emit('changed') → tick++ → 三个子页各自 watch 重载（等价原 refreshAll）
 * - 子页内跳转 emit('navigate', tab)；概览趋势点柱子 emit('select-date') → jumpDate 下发明细联动
 */
import { onMounted, ref } from 'vue'
import { getCategories } from '@/api/expense'
import type { ExpenseCategory } from '@/api/expense'
import SubNav, { type SubNavItem } from '@/components/SubNav/SubNav.vue'
import ExpenseInput from './components/ExpenseInput.vue'
import ExpenseOverview from './components/ExpenseOverview.vue'
import ExpenseDetail from './components/ExpenseDetail.vue'

type SubTab = 'input' | 'overview' | 'detail'
const activeSub = ref<SubTab>('input')
const subItems: SubNavItem[] = [
  { key: 'input', label: '记一笔' },
  { key: 'overview', label: '概览' },
  { key: 'detail', label: '明细' }
]

/* ---------- 分类（一次加载，两个子页共享） ---------- */
const catLoading = ref(false)
const cats = ref<ExpenseCategory[]>([])
async function loadCategories() {
  catLoading.value = true
  try {
    cats.value = await getCategories()
  } finally {
    catLoading.value = false
  }
}

/** 跨页刷新序号：任意子页数据变更后递增 */
const tick = ref(0)
function onChanged() {
  tick.value = tick.value + 1
}
/** 子页内跳转（如「查看全部」） */
function onNavigate(tab: string) {
  activeSub.value = tab as SubTab
}
/** 概览趋势点柱子联动明细到当天 */
const jumpDate = ref('')
function onSelectDate(date: string) {
  jumpDate.value = date
}

onMounted(loadCategories)
</script>

<template>
  <div class="exp">
    <!-- ===== 内容区页头（子页 Tab） ===== -->
    <SubNav :items="subItems" v-model:active="activeSub" hint="记账 · 内容区内独立子页" />

    <!-- ===== 子页1：记一笔 ===== -->
    <div v-show="activeSub === 'input'" class="exp__sub">
      <ExpenseInput
        :cats="cats"
        :cat-loading="catLoading"
        :tick="tick"
        @changed="onChanged"
        @navigate="onNavigate"
      />
    </div>

    <!-- ===== 子页2：概览 ===== -->
    <div v-show="activeSub === 'overview'" class="exp__sub">
      <ExpenseOverview
        :tick="tick"
        :active="activeSub === 'overview'"
        @select-date="onSelectDate"
      />
    </div>

    <!-- ===== 子页3：明细 ===== -->
    <div v-show="activeSub === 'detail'" class="exp__sub">
      <ExpenseDetail
        :cats="cats"
        :tick="tick"
        :jump-date="jumpDate"
        @changed="onChanged"
      />
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use './expenseList';
</style>