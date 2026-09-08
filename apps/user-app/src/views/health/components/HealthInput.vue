<script setup lang="ts">
/**
 * 健康 · 打卡子页组件
 * 大号体重输入 + 可选体脂/腰围 + 日期 + 备注 + 保存 + 体重速览（最近 5 条）
 * 数据变更时 emit('changed') → 父级递增 tick，联动 趋势/历史 刷新
 */
import { markRaw, computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Weight, Percent, Ruler } from '@lucide/vue'
import { listWeightRecords, createWeightRecord } from '@/api/health'
import type { WeightRecord } from '@/api/health'
import { formatDate } from '@/utils/format'
import LoadingMask from '@/components/LoadingMask/LoadingMask.vue'
import BlockTitle from '@/components/BlockTitle/BlockTitle.vue'
import RecordHeatmap from '@/components/RecordHeatmap/RecordHeatmap.vue'
import type { HeatmapRow } from '@/components/RecordHeatmap/RecordHeatmap.vue'

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

/* ---------- 体重速览（最近 5 条）+ 30 天打卡热力图 ---------- */
const recentLoading = ref(false)
const recentRecords = ref<WeightRecord[]>([])
/** 近 30 天打卡日期 → 是否含体脂（热力档：0 无 / 1 打卡 / 3 含体脂；ref 保证 computed 响应） */
const checkinMap = ref(new Map<string, boolean>())

const heatRows = computed<HeatmapRow[]>(() => {
  const cells: { date: string; level: number; tip: string }[] = []
  const today = new Date()
  for (let i = 29; i >= 0; i--) {
    const d = new Date(today)
    d.setDate(d.getDate() - i)
    const key = formatDate(d)
    const has = checkinMap.value.has(key)
    const hasFat = checkinMap.value.get(key) === true
    cells.push({ date: key, level: hasFat ? 3 : has ? 1 : 0, tip: hasFat ? `${key} · 已打卡（含体脂）` : has ? `${key} · 已打卡` : `${key} · 未打卡` })
  }
  return [{ label: '体重', cells }]
})

async function loadRecent() {
  recentLoading.value = true
  try {
    // 拉最近 30 条记录（size 30 覆盖每日一记场景，含补打卡）
    const res = await listWeightRecords({ page: 1, size: 30 })
    recentRecords.value = res.records.slice(0, 5)
    const map = new Map<string, boolean>()
    for (const r of res.records) map.set(r.recordDate, r.bodyFat != null)
    checkinMap.value = map
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
    <template v-if="recentRecords.length">
      <div v-for="r in recentRecords" :key="r.id" class="hl__row">
        <span class="hl__row-icon"><component :is="markRaw(Weight)" :size="15" /></span>
        <span class="hl__row-date num">{{ r.recordDate }}</span>
        <span class="hl__row-sub">{{ r.bodyFat != null ? `体脂 ${r.bodyFat}%` : '' }}{{ r.waist != null ? ` · 腰围 ${r.waist}cm` : '' }}</span>
        <span class="hl__row-note">{{ r.note || '' }}</span>
        <span class="hl__row-val num">{{ r.weight }}<i>kg</i></span>
      </div>
    </template>
    <p v-else-if="!recentLoading" class="hl__empty">还没有记录，先在上面打卡吧</p>
    <LoadingMask :show="recentLoading" :size="22" text="加载最近记录…" />
  </section>

  <!-- V2 30 天打卡热力图（打卡即点亮，色随健康主色） -->
  <section class="card hl__heat">
    <BlockTitle title="最近 30 天" hint="打卡即点亮 · 含体脂更深一格" />
    <RecordHeatmap :rows="heatRows" :legend-text="''" />
  </section>
</template>

<style lang="scss" scoped>
@use '../health';
</style>