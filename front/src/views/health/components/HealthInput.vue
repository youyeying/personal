<script setup lang="ts">
/**
 * 健康 · 打卡子页组件
 * 大号体重输入 + 可选体脂/腰围 + 日期 + 备注 + 保存 + 体重速览（最近 5 条）
 * 数据变更时 emit('changed') → 父级递增 tick，联动 趋势/历史 刷新
 */
import { markRaw, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Weight, Percent, Ruler } from '@lucide/vue'
import { listWeightRecords, createWeightRecord } from '@/api/health'
import type { WeightRecord } from '@/api/health'
import { formatDate } from '@/utils/format'
import InlineLoading from '@/components/loading/InlineLoading.vue'
import BlockTitle from '@/components/BlockTitle/BlockTitle.vue'

const props = defineProps<{
  tick: number
}>()

const emit = defineEmits<{
  (e: 'changed'): void
  (e: 'navigate', tab: string): void
}>()

/* ---------- 打卡表单 ---------- */
const weight = ref('')
const bodyFat = ref('')
const waist = ref('')
const note = ref('')
const recordDate = ref(formatDate(new Date()))
const saving = ref(false)

async function onSave() {
  const val = parseFloat(weight.value)
  if (!val || val <= 0) {
    ElMessage.warning('请输入体重')
    return
  }
  if (val > 500) {
    ElMessage.warning('体重超出合理范围')
    return
  }
  const fat = bodyFat.value ? parseFloat(bodyFat.value) : null
  if (fat != null && (fat < 0 || fat > 100)) {
    ElMessage.warning('体脂率应在 0-100 之间')
    return
  }
  const wa = waist.value ? parseFloat(waist.value) : null
  if (wa != null && wa <= 0) {
    ElMessage.warning('腰围需大于 0')
    return
  }
  saving.value = true
  try {
    await createWeightRecord({
      weight: val,
      bodyFat: fat,
      waist: wa,
      note: note.value.trim(),
      recordDate: recordDate.value
    })
    ElMessage.success('打卡成功')
    weight.value = ''
    bodyFat.value = ''
    waist.value = ''
    note.value = ''
    emit('changed')
  } finally {
    saving.value = false
  }
}

/* ---------- 体重速览（最近 5 条） ---------- */
const recentLoading = ref(false)
const recentRecords = ref<WeightRecord[]>([])
async function loadRecent() {
  recentLoading.value = true
  try {
    const res = await listWeightRecords({ page: 1, size: 5 })
    recentRecords.value = res.records
  } finally {
    recentLoading.value = false
  }
}

onMounted(loadRecent)
// 任一子页数据变更 → 重载速览
watch(() => props.tick, loadRecent)
</script>

<template>
  <section class="card hl__entry">
    <BlockTitle title="体重打卡" hint="填体重即可，体脂 / 腰围可选" />
    <div class="hl__entry-body">
      <!-- 大号体重输入 -->
      <div class="hl__form">
        <label class="hl__field-label">今日体重 (kg)</label>
        <div class="hl__amount">
          <input
            v-model="weight"
            class="hl__amount-input"
            inputmode="decimal"
            placeholder="0.0"
          />
          <span class="hl__amount-unit">kg</span>
        </div>
      </div>
      <!-- 可选指标 + 日期备注 -->
      <div class="hl__form">
        <div class="hl__optgrid">
          <div class="hl__field">
            <label class="hl__field-label"><component :is="markRaw(Percent)" :size="13" /> 体脂率 (%)</label>
            <el-input v-model="bodyFat" placeholder="选填" inputmode="decimal" />
          </div>
          <div class="hl__field">
            <label class="hl__field-label"><component :is="markRaw(Ruler)" :size="13" /> 腰围 (cm)</label>
            <el-input v-model="waist" placeholder="选填" inputmode="decimal" />
          </div>
        </div>
        <div class="hl__field">
          <label class="hl__field-label">日期</label>
          <el-date-picker v-model="recordDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </div>
        <div class="hl__field">
          <label class="hl__field-label">备注</label>
          <el-input v-model="note" maxlength="60" placeholder="选填，说点什么…" />
        </div>
      </div>
    </div>
    <el-button type="primary" size="large" :loading="saving" class="hl__save" @click="onSave">保存</el-button>
    <p class="hl__tip">保存后清空表单，可连续补记多天。</p>
  </section>

  <!-- 体重速览 -->
  <section class="card hl__recent">
    <BlockTitle title="体重速览" hint="最近 5 次">
      <template #aside>
        <button class="hl__link" type="button" @click="emit('navigate', 'history')">查看全部</button>
      </template>
    </BlockTitle>
    <template v-if="recentLoading">
      <div class="hl__recent-loading">
        <InlineLoading :size="22" text="加载最近记录…" color="var(--cb-mod)" />
      </div>
    </template>
    <template v-else-if="recentRecords.length">
      <div v-for="r in recentRecords" :key="r.id" class="hl__row">
        <span class="hl__row-icon"><component :is="markRaw(Weight)" :size="15" /></span>
        <span class="hl__row-date num">{{ r.recordDate }}</span>
        <span class="hl__row-sub">{{ r.bodyFat != null ? `体脂 ${r.bodyFat}%` : '' }}{{ r.waist != null ? ` · 腰围 ${r.waist}cm` : '' }}</span>
        <span class="hl__row-note">{{ r.note || '' }}</span>
        <span class="hl__row-val num">{{ r.weight }}<i>kg</i></span>
      </div>
    </template>
    <p v-else class="hl__empty">还没有记录，先在上面打卡吧</p>
  </section>
</template>

<style lang="scss" scoped>
@use '../health';
</style>