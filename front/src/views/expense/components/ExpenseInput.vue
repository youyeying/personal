<script setup lang="ts">
/**
 * 记账 · 记一笔子页组件
 * 支出/收入切换 + 分类宫格 + 大金额输入 + 日期/备注 + 保存 + 最近记录速览
 * 数据变更时 emit('changed') → 父级递增 tick，联动 概览/明细 刷新
 */
import { computed, markRaw, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { listExpenseRecords, createExpenseRecord } from '@/api/expense'
import type { ExpenseCategory, ExpenseRecord } from '@/api/expense'
import { formatDate, formatShortTime, formatMoney } from '@/utils/format'
import { catIcon } from '@/utils/category'
import LoadingMask from '@/components/LoadingMask/LoadingMask.vue'
import BlockTitle from '@/components/BlockTitle/BlockTitle.vue'

const props = defineProps<{
  /** 全部分类（父级加载，本组件与明细共享） */
  cats: ExpenseCategory[]
  catLoading: boolean
  tick: number
}>()

const emit = defineEmits<{
  (e: 'changed'): void
  (e: 'navigate', tab: string): void
}>()

/* ---------- 分类 ---------- */
const curType = ref<1 | 2>(1)
const curCatId = ref<number>(0)

/** 当前类型对应的分类列表（按 sortOrder 排序） */
const curCats = computed(() =>
  props.cats.filter((c) => c.type === curType.value).sort((a, b) => a.sortOrder - b.sortOrder)
)

/** 分类加载完成后默认选中当前类型第一个分类 */
watch(
  () => props.cats,
  () => {
    curCatId.value = curCats.value[0]?.id ?? 0
  }
)

function switchType(t: 1 | 2) {
  curType.value = t
  curCatId.value = curCats.value[0]?.id ?? 0
}

/* ---------- 表单 ---------- */
const amount = ref('')
const note = ref('')
const recordDate = ref(formatDate(new Date()))
const saving = ref(false)

async function onSave() {
  const val = parseFloat(amount.value)
  if (!val || val <= 0) {
    ElMessage.warning('请输入金额')
    return
  }
  if (!curCatId.value) {
    ElMessage.warning('请选择分类')
    return
  }
  saving.value = true
  try {
    await createExpenseRecord({
      type: curType.value,
      categoryId: curCatId.value,
      amount: val,
      note: note.value.trim(),
      recordDate: recordDate.value
    })
    ElMessage.success('记账成功')
    amount.value = ''
    note.value = ''
    emit('changed')
  } finally {
    saving.value = false
  }
}

/* ---------- 最近记录速览 ---------- */
const recentLoading = ref(false)
const recentRecords = ref<ExpenseRecord[]>([])
async function loadRecent() {
  recentLoading.value = true
  try {
    const res = await listExpenseRecords({ page: 1, size: 5 })
    recentRecords.value = res.records
  } finally {
    recentLoading.value = false
  }
}

onMounted(loadRecent)
// 任一子页数据变更 → 重载最近记录
watch(() => props.tick, loadRecent)
</script>

<template>
  <section class="card exp__entry">
    <div class="exp__seg">
      <button class="exp__seg-btn" :class="{ 'is-active': curType === 1 }" type="button" @click="switchType(1)">支出</button>
      <button class="exp__seg-btn" :class="{ 'is-active': curType === 2 }" type="button" @click="switchType(2)">收入</button>
    </div>

    <div class="exp__entry-body">
      <!-- 分类宫格 -->
      <div class="exp__cats">
        <div class="exp__catgrid">
          <button
            v-for="c in curCats"
            :key="c.id"
            class="exp__cat"
            :class="{ 'is-active': c.id === curCatId }"
            type="button"
            @click="curCatId = c.id"
          >
            <span class="exp__cat-icon">
              <component :is="markRaw(catIcon(c.name))" :size="18" />
            </span>
            <span class="exp__cat-name">{{ c.name }}</span>
          </button>
        </div>
        <LoadingMask :show="catLoading" :size="26" text="加载分类…" />
      </div>

      <!-- 金额 + 日期备注 -->
      <div class="exp__form">
        <div class="exp__amount">
          <span class="exp__amount-sign">{{ curType === 1 ? '-' : '+' }}</span>
          <input
            v-model="amount"
            class="exp__amount-input"
            inputmode="decimal"
            placeholder="0.00"
          />
        </div>
        <div class="exp__field">
          <label class="exp__field-label">日期</label>
          <el-date-picker v-model="recordDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </div>
        <div class="exp__field">
          <label class="exp__field-label">备注</label>
          <el-input v-model="note" maxlength="40" placeholder="选填，说点什么…" />
        </div>
        <el-button type="primary" size="large" :loading="saving" class="exp__save" @click="onSave">保存</el-button>
        <p class="exp__tip">保存后清空金额、保留上次分类，可连续记账。</p>
      </div>
    </div>
  </section>

  <!-- 最近记录速览 -->
  <section class="card exp__recent">
    <BlockTitle title="最近记录" hint="记完可快速核对">
      <template #aside>
        <button class="exp__link exp__recent-all" type="button" @click="emit('navigate', 'detail')">查看全部</button>
      </template>
    </BlockTitle>
    <template v-if="recentRecords.length">
      <div v-for="r in recentRecords" :key="r.id" class="exp__row">
        <span class="exp__row-icon"><component :is="markRaw(catIcon(r.categoryName))" :size="16" /></span>
        <span class="exp__row-cat">{{ r.categoryName }}</span>
        <span class="exp__row-note">{{ r.note || '-' }}</span>
        <span class="exp__row-t num">{{ formatShortTime(r.createdAt) }}</span>
        <span class="exp__row-amt num" :class="r.type === 1 ? 'exp__row-amt--err' : 'exp__row-amt--ok'">{{ r.type === 1 ? '-' : '+' }}{{ formatMoney(r.amount) }}</span>
      </div>
    </template>
    <p v-else-if="!recentLoading" class="exp__empty">还没有记录，先在上面记一笔吧</p>
    <LoadingMask :show="recentLoading" :size="22" text="加载最近记录…" />
  </section>
</template>

<style lang="scss" scoped>
@use '../expenseList';
</style>