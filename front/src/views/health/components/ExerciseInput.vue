<script setup lang="ts">
/**
 * 健康 · 锻炼打卡子页组件
 * 选动作 → 记参数 → 自动按体重/MET 算大卡 → 保存；下方今日已练列表（可删）
 * 数据变更时 emit('changed') → 父级递增 tick，联动 统计/历史 刷新
 *
 * 消耗公式（utils/exercise.ts 纯函数）：
 * - 力量：个数+分钟 → 速度 → 强度系数 → MET×基础
 * - 散步：距离+分钟 → 速度 → 档位 MET
 * - 爬楼梯：层数×次数×12秒 → 分钟 → MET 8.0
 * - 平板：秒数 → 分钟 → MET 4.0
 * 体重取「最新一条体重记录」，绝不用目标体重
 */
import { computed, markRaw, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Dumbbell, Plus, Copy } from '@lucide/vue'
import { listExerciseItems, listExerciseRecords, getExerciseLatest, createExerciseRecord, deleteExerciseRecord, createExerciseItem } from '@/api/exercise'
import type { ExerciseItem, ExerciseRecord } from '@/api/exercise'
import { listWeightRecords } from '@/api/health'
import { formatDate } from '@/utils/format'
import { useUserStore } from '@/store/user'
import { walkSpeedKmh, walkMet, strengthIntensity, strengthMet, calcKcal, totalSeconds, formatDuration, WALK_SPEED_MAX } from '@/utils/exercise'
import BlockTitle from '@/components/BlockTitle/BlockTitle.vue'
import LoadingMask from '@/components/LoadingMask/LoadingMask.vue'

const props = defineProps<{ tick: number }>()
const emit = defineEmits<{ (e: 'changed'): void }>()

const userStore = useUserStore()

/* ---------- 动作字典 ---------- */
const items = ref<ExerciseItem[]>([])
const loading = ref(false)
const activeId = ref<number | null>(null)
const activeItem = computed(() => items.value.find((i) => i.id === activeId.value) ?? null)

async function loadItems() {
  loading.value = true
  try {
    items.value = await listExerciseItems()
    if (activeId.value == null || !items.value.some((i) => i.id === activeId.value)) {
      activeId.value = items.value[0]?.id ?? null
    }
  } finally {
    loading.value = false
  }
}

/* ---------- 当前体重（最新记录，非目标体重） ---------- */
const weightKg = ref<number | null>(null)
async function loadLatestWeight() {
  try {
    const res = await listWeightRecords({ page: 1, size: 1 })
    const w = res.records?.[0]?.weight
    weightKg.value = w != null ? Number(w) : null
  } catch {
    weightKg.value = null
  }
}

/* ---------- 打卡表单 ---------- */
const form = ref({
  weight: '',
  reps: '',
  /** 力量：分钟 + 秒（合并总数，避免除不尽） */
  min: '',
  sec: '',
  minutes: '',
  distance: '',
  floors: '',
  times: '',
  seconds: '',
  hand: 'left' as 'left' | 'right' | 'both',
  note: '',
  recordDate: formatDate(new Date())
})
const saving = ref(false)

/** 上次记录（带出「上次」提示） */
const lastRecord = ref<ExerciseRecord | null>(null)

/** 实时计算：返回 { met, minutes, detail } */
function liveCalc() {
  const item = activeItem.value
  if (!item) return null
  switch (item.type) {
    case 'strength': {
      const reps = Number(form.value.reps) || 0
      const secTotal = timeSeconds()
      if (reps <= 0 || secTotal <= 0) return { met: item.baseMet, minutes: 0, detail: '输入个数与时长' }
      const min = secTotal / 60
      const speed = reps / min
      const it = strengthIntensity(speed, item.refSpeed ?? 12)
      const met = strengthMet(item.baseMet, speed, item.refSpeed ?? 12)
      return {
        met,
        minutes: min,
        detail: `${reps}个 ÷ ${formatDuration(secTotal)} = ${speed.toFixed(1)}个/分钟 · ${it.label}强度 · MET ${item.baseMet}→${met}`
      }
    }
    case 'walk': {
      const d = Number(form.value.distance) || 0
      const min = Number(form.value.minutes) || 0
      if (d <= 0 || min <= 0) return { met: 0, minutes: 0, detail: '输入距离与分钟' }
      const kmh = walkSpeedKmh(d, min)
      // 速度超常必为输入错误（如分钟误填成小时），拦截而非默默算个怪数字
      if (kmh > WALK_SPEED_MAX) {
        return { met: 0, minutes: 0, detail: `输入的距离与时间不匹配（${kmh.toFixed(0)}km/h 超常，分钟可能填错）` }
      }
      const w = walkMet(kmh)
      return { met: w.met, minutes: min, detail: `${d}km ÷ ${min}min = ${kmh.toFixed(1)}km/h · ${w.label} · MET ${w.met}` }
    }
    case 'stairs': {
      const f = Number(form.value.floors) || 0
      const t = Number(form.value.times) || 0
      const secTotal = timeSeconds()
      if (f <= 0 || t <= 0 || secTotal <= 0) return { met: item.baseMet, minutes: 0, detail: '输入层数、次数与时长' }
      const min = secTotal / 60
      return { met: item.baseMet, minutes: min, detail: `${f}层 × ${t}次 · ${formatDuration(secTotal)}` }
    }
    case 'plank': {
      const s = Number(form.value.seconds) || 0
      if (s <= 0) return { met: item.baseMet, minutes: 0, detail: '输入秒数' }
      const min = s / 60
      return { met: item.baseMet, minutes: min, detail: `${s}秒 = ${min.toFixed(2)}min` }
    }
    default:
      return null
  }
}

const live = computed(() => liveCalc())
const liveKcal = computed(() => {
  if (!live.value || !weightKg.value) return { total: 0, net: 0 }
  return calcKcal(live.value.met, live.value.minutes, weightKg.value)
})

/** 分钟+秒 → 总秒数 */
function timeSeconds() {
  const m = Number(form.value.min) || 0
  const s = Number(form.value.sec) || 0
  return Math.round(m * 60 + s)
}

/** 切换动作：带出上次参数 */
async function onSelectItem(item: ExerciseItem) {
  activeId.value = item.id
  resetForm()
  try {
    const last = await getExerciseLatest(item.id)
    lastRecord.value = last
    if (last) {
      form.value.weight = last.weight != null ? String(last.weight) : ''
      form.value.reps = last.reps != null ? String(last.reps) : ''
      if (item.type === 'strength' || item.type === 'stairs') {
        // 上次时长：拆成 分 + 秒（总秒数优先，兼容旧分钟字段）
        const ts = totalSeconds(last.minutes, last.seconds)
        form.value.min = String(Math.floor(ts / 60) || '')
        form.value.sec = String(ts % 60 || '')
      } else {
        form.value.minutes = last.minutes != null ? String(last.minutes) : ''
      }
      form.value.distance = last.distance != null ? String(last.distance) : ''
      form.value.floors = last.floors != null ? String(last.floors) : ''
      form.value.times = last.times != null ? String(last.times) : ''
      form.value.seconds = last.seconds != null ? String(last.seconds) : ''
      form.value.hand = (last.hand as 'left' | 'right' | 'both') ?? 'left'
      form.value.note = last.note ?? ''
    }
  } catch {
    lastRecord.value = null
  }
}

function resetForm() {
  form.value = {
    weight: '', reps: '', min: '', sec: '', minutes: '', distance: '',
    floors: '', times: '', seconds: '', hand: 'left',
    note: '', recordDate: formatDate(new Date())
  }
  lastRecord.value = null
}

function onSave() {
  const item = activeItem.value
  if (!item) return
  if (!weightKg.value) {
    ElMessage.warning('请先记录体重，锻炼消耗按体重计算')
    return
  }
  const calc = liveCalc()
  if (!calc || calc.detail.startsWith('输入')) {
    ElMessage.warning(calc?.detail ?? '请补全参数')
    return
  }
  saving.value = true
  createExerciseRecord({
    exerciseId: item.id,
    recordDate: form.value.recordDate,
    weight: item.hasWeight && form.value.weight ? Number(form.value.weight) : null,
    reps: item.type === 'strength' ? Number(form.value.reps) : null,
    minutes: item.type === 'walk' ? Number(form.value.minutes) : null,
    distance: item.type === 'walk' ? Number(form.value.distance) : null,
    floors: item.type === 'stairs' ? Number(form.value.floors) : null,
    times: item.type === 'stairs' ? Number(form.value.times) : null,
    // strength/stairs 存总秒数（分钟+秒合并，精确到秒）；plank 存秒
    seconds: item.type === 'strength' || item.type === 'stairs'
      ? timeSeconds()
      : item.type === 'plank' ? Number(form.value.seconds) : null,
    hand: item.hasHand ? form.value.hand : null,
    note: form.value.note.trim()
  })
    .then((rec) => {
      ElMessage.success('已记录')
      // 本地插入新记录（顶部），避免整表重拉闪烁；tick 仍通知统计/历史静默刷新
      todayRecords.value = [rec, ...todayRecords.value]
      resetForm()
      emit('changed')
    })
    .catch(() => {})
    .finally(() => {
      saving.value = false
    })
}

/* ---------- 今日已练 ---------- */
const todayRecords = ref<ExerciseRecord[]>([])
async function loadToday() {
  try {
    const res = await listExerciseRecords({
      startDate: formatDate(new Date()),
      endDate: formatDate(new Date()),
      page: 1,
      size: 100
    })
    todayRecords.value = res.records
  } catch {
    todayRecords.value = []
  }
}
function itemName(id: number) {
  return items.value.find((i) => i.id === id)?.name ?? '动作'
}
function todayKcal(r: ExerciseRecord) {
  if (!weightKg.value) return 0
  const item = items.value.find((i) => i.id === r.exerciseId)
  if (!item) return 0
  let met = item.baseMet
  let minutes = 0
  if (item.type === 'strength' && r.reps) {
    const secTotal = totalSeconds(r.minutes, r.seconds)
    if (secTotal <= 0) return 0
    minutes = secTotal / 60
    met = strengthMet(item.baseMet, r.reps / minutes, item.refSpeed ?? 12)
  } else if (item.type === 'walk' && r.distance && r.minutes) {
    met = walkMet(walkSpeedKmh(Number(r.distance), Number(r.minutes))).met
    minutes = Number(r.minutes)
  } else if (item.type === 'stairs' && r.floors && r.times) {
    minutes = totalSeconds(r.minutes, r.seconds) / 60
  } else if (item.type === 'plank' && r.seconds) {
    minutes = r.seconds / 60
  }
  return calcKcal(met, minutes, weightKg.value).net
}
function todayDetail(r: ExerciseRecord) {
  const item = items.value.find((i) => i.id === r.exerciseId)
  if (!item) return ''
  switch (item.type) {
    case 'strength':
      return `${r.weight != null ? r.weight + 'kg × ' : ''}${r.reps}个 · ${formatDuration(totalSeconds(r.minutes, r.seconds))}`
    case 'walk':
      return `${r.distance}km · ${r.minutes}min`
    case 'stairs':
      return `${r.floors}层 × ${r.times}次 · ${formatDuration(totalSeconds(r.minutes, r.seconds))}`
    case 'plank':
      return `${r.seconds}秒`
    default:
      return ''
  }
}
const todayTotalKcal = computed(() => todayRecords.value.reduce((s, r) => s + todayKcal(r), 0))

function onDeleteToday(r: ExerciseRecord) {
  deleteExerciseRecord(r.id)
    .then(() => {
      ElMessage.success('已删除')
      emit('changed')
    })
    .catch(() => {})
}

/* ---------- 复制到打卡表单（今日列表 / 历史页共用） ---------- */
function onReuse(r: ExerciseRecord) {
  const item = items.value.find((i) => i.id === r.exerciseId)
  if (!item) {
    ElMessage.warning('找不到该动作，可能已被删除')
    return
  }
  activeId.value = item.id
  resetForm()
  form.value.weight = r.weight != null ? String(r.weight) : ''
  form.value.reps = r.reps != null ? String(r.reps) : ''
  if (item.type === 'strength' || item.type === 'stairs') {
    const ts = totalSeconds(r.minutes, r.seconds)
    form.value.min = String(Math.floor(ts / 60) || '')
    form.value.sec = String(ts % 60 || '')
  } else {
    form.value.minutes = r.minutes != null ? String(r.minutes) : ''
    form.value.seconds = r.seconds != null ? String(r.seconds) : ''
  }
  form.value.distance = r.distance != null ? String(r.distance) : ''
  form.value.floors = r.floors != null ? String(r.floors) : ''
  form.value.times = r.times != null ? String(r.times) : ''
  form.value.hand = (r.hand as 'left' | 'right' | 'both') ?? 'left'
  form.value.note = r.note ?? ''
  // 日期重置为今天（复制到「今天」再练一组）
  form.value.recordDate = formatDate(new Date())
  // 复用「上次」提示条，让用户看到复制的是哪条
  lastRecord.value = r
  ElMessage.success(`已复制「${item.name}」，可修改后保存`)
}
// 暴露给父组件（锻炼历史页「复制」时调用，并滚动回表单）
defineExpose({ reuse: onReuse })

/* ---------- 自定义动作 ---------- */
const addDialog = ref(false)
const addName = ref('')
const addType = ref<'strength' | 'plank' | 'walk' | 'stairs'>('strength')
const addMet = ref('3.5')
const addSaving = ref(false)

function openAdd() {
  addName.value = ''
  addType.value = 'strength'
  addMet.value = '3.5'
  addDialog.value = true
}
function onAddItem() {
  const name = addName.value.trim()
  if (!name) {
    ElMessage.warning('请输入动作名')
    return
  }
  const met = Number(addMet.value) || 0
  addSaving.value = true
  createExerciseItem({
    name,
    type: addType.value,
    baseMet: met,
    hasWeight: addType.value === 'strength',
    // 默认双手动作不选手；若日后做单臂/单手动作再在自定义时勾选手
    hasHand: false
  })
    .then((item) => {
      ElMessage.success('动作已添加')
      addDialog.value = false
      items.value = [...items.value, item].sort((a, b) => a.sortOrder - b.sortOrder)
      activeId.value = item.id
      resetForm()
    })
    .catch(() => {})
    .finally(() => {
      addSaving.value = false
    })
}

onMounted(() => {
  loadItems()
  loadLatestWeight()
  loadToday()
})
// 任一子页数据变更 → 重载今日列表
import { watch } from 'vue'
watch(() => props.tick, () => {
  loadToday()
  loadLatestWeight()
})
</script>

<template>
  <section class="card exi">
    <BlockTitle title="锻炼打卡" :hint="weightKg ? `按当前体重 ${weightKg}kg 计算消耗` : '请先在「打卡」记录体重'" />

    <!-- 动作选择 -->
    <div class="exi__groups">
      <span class="exi__group-label">力量 / 核心</span>
      <div class="exi__chips">
        <button
          v-for="it in items.filter((i) => ['strength', 'plank'].includes(i.type))"
          :key="it.id"
          class="exi__chip"
          :class="{ 'is-on': it.id === activeId }"
          type="button"
          @click="onSelectItem(it)"
        >
          {{ it.name }}<i>{{ it.type === 'plank' ? '按秒' : '基础 MET ' + it.baseMet }}</i>
        </button>
      </div>
      <span class="exi__group-label">有氧</span>
      <div class="exi__chips">
        <button
          v-for="it in items.filter((i) => ['walk', 'stairs'].includes(i.type))"
          :key="it.id"
          class="exi__chip"
          :class="{ 'is-on': it.id === activeId }"
          type="button"
          @click="onSelectItem(it)"
        >
          {{ it.name }}<i>{{ it.type === 'walk' ? '按速度定 MET' : 'MET ' + it.baseMet }}</i>
        </button>
        <button class="exi__chip exi__chip--add" type="button" @click="openAdd"><component :is="markRaw(Plus)" :size="14" /> 自定义</button>
      </div>
    </div>

    <!-- 上次提示 -->
    <p v-if="lastRecord" class="exi__last">
      ↩ 上次：{{ itemName(lastRecord.exerciseId) }} · <b>{{ todayDetail(lastRecord) }}</b>{{ lastRecord.hand ? ' · ' + (lastRecord.hand === 'left' ? '左' : lastRecord.hand === 'right' ? '右' : '双') : '' }} —— 已带出，可直接改
    </p>

    <!-- 表单（按动作类型动态渲染） -->
    <div v-if="activeItem" class="exi__form">
      <template v-if="activeItem.type === 'strength'">
        <div class="exi__row">
          <label v-if="activeItem.hasWeight" class="exi__field">
            <span>重量 (kg，自重留空)</span>
            <input v-model="form.weight" class="num" inputmode="decimal" placeholder="如 12.5" />
          </label>
          <label class="exi__field">
            <span>做了多少个</span>
            <input v-model="form.reps" class="num" inputmode="numeric" placeholder="如 12" />
          </label>
          <label class="exi__field">
            <span>这组花了几分钟</span>
            <input v-model="form.min" class="num" inputmode="numeric" placeholder="如 1" />
          </label>
          <label class="exi__field">
            <span>秒（可选）</span>
            <input v-model="form.sec" class="num" inputmode="numeric" placeholder="如 40" />
          </label>
          <label v-if="activeItem.hasHand" class="exi__field">
            <span>手</span>
            <select v-model="form.hand" class="num">
              <option value="left">左手</option>
              <option value="right">右手</option>
              <option value="both">双手</option>
            </select>
          </label>
        </div>
      </template>

      <template v-else-if="activeItem.type === 'walk'">
        <div class="exi__row">
          <label class="exi__field">
            <span>走了多远</span>
            <input v-model="form.distance" class="num" inputmode="decimal" placeholder="如 2.5" />
          </label>
          <label class="exi__field">
            <span>花了多久 (分钟)</span>
            <input v-model="form.minutes" class="num" inputmode="numeric" placeholder="如 30" />
          </label>
        </div>
      </template>

      <template v-else-if="activeItem.type === 'stairs'">
        <div class="exi__row">
          <label class="exi__field">
            <span>一次爬几层</span>
            <input v-model="form.floors" class="num" inputmode="numeric" placeholder="如 10" />
          </label>
          <label class="exi__field">
            <span>爬了几次</span>
            <input v-model="form.times" class="num" inputmode="numeric" placeholder="如 3" />
          </label>
          <label class="exi__field">
            <span>花了多久（分钟）</span>
            <input v-model="form.min" class="num" inputmode="numeric" placeholder="如 1" />
          </label>
          <label class="exi__field">
            <span>秒（可选）</span>
            <input v-model="form.sec" class="num" inputmode="numeric" placeholder="如 30" />
          </label>
        </div>
      </template>

      <template v-else>
        <div class="exi__row">
          <label class="exi__field">
            <span>撑了多久 (秒)</span>
            <input v-model="form.seconds" class="num" inputmode="numeric" placeholder="如 90" />
          </label>
        </div>
      </template>

      <div class="exi__row exi__row--note">
        <label class="exi__field">
          <span>备注（可选）</span>
          <input v-model="form.note" placeholder="如：做慢一点更到位" />
        </label>
        <label class="exi__field">
          <span>日期</span>
          <input v-model="form.recordDate" type="date" class="num" />
        </label>
      </div>

      <div class="exi__calc">
        <template v-if="live && !live.detail.startsWith('输入')">
          {{ live.detail }} · 按 {{ weightKg ?? '--' }}kg
          → 净消耗 <b>{{ liveKcal.net }}</b> kcal <span class="exi__calc-sub">（总 {{ liveKcal.total }}）</span>
        </template>
        <template v-else>{{ live?.detail ?? '选择动作后开始' }}</template>
      </div>

      <button class="exi__save" type="button" :disabled="saving" @click="onSave">
        <component :is="markRaw(Dumbbell)" :size="15" /> {{ saving ? '保存中…' : '保存记录' }}
      </button>
    </div>

    <!-- 今日已练 -->
    <div class="exi__today">
      <p class="exi__today-title">
        今日已练 · {{ todayRecords.length }} 条 · 净消耗 <b>{{ todayTotalKcal }}</b> kcal
      </p>
      <TransitionGroup v-if="todayRecords.length" tag="div" name="exi-today" class="exi__today-list">
        <div v-for="r in todayRecords" :key="r.id" class="exi__today-item">
          <span class="exi__today-name">{{ itemName(r.exerciseId) }}</span>
          <span class="exi__today-detail num">{{ todayDetail(r) }}</span>
          <span v-if="r.hand && r.hand !== 'both'" class="exi__today-hand">{{ r.hand === 'left' ? '左' : '右' }}</span>
          <span class="exi__today-kcal">+{{ todayKcal(r) }} kcal</span>
          <button class="exi__today-copy" type="button" title="复制到表单" @click="onReuse(r)">
            <component :is="markRaw(Copy)" :size="14" />
          </button>
          <button class="exi__today-del" type="button" title="删除" @click="onDeleteToday(r)">✕</button>
        </div>
      </TransitionGroup>
      <p v-else class="exi__today-empty">今天还没锻炼，来一组吧</p>
    </div>

    <LoadingMask :show="loading" :size="26" text="加载动作…" />
  </section>

  <!-- 自定义动作弹窗 -->
  <el-dialog v-model="addDialog" title="自定义动作" width="400px">
    <div class="exi__add">
      <div class="exi__field">
        <span>动作名</span>
        <input v-model="addName" maxlength="20" placeholder="如：仰卧起坐" />
      </div>
      <div class="exi__field">
        <span>类型</span>
        <select v-model="addType" class="num">
          <option value="strength">力量（个数+分钟）</option>
          <option value="plank">静力（秒）</option>
          <option value="walk">有氧（距离+分钟）</option>
          <option value="stairs">爬楼梯（层数+次数）</option>
        </select>
      </div>
      <div class="exi__field">
        <span>基础 MET（散步类可填 0 自动按速度定档）</span>
        <input v-model="addMet" class="num" inputmode="decimal" placeholder="如 3.5" />
      </div>
    </div>
    <template #footer>
      <el-button @click="addDialog = false">取消</el-button>
      <el-button type="primary" :loading="addSaving" @click="onAddItem">添加</el-button>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
@use '../exercise';
</style>
