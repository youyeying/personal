<script setup lang="ts">
/**
 * 饮食 · 记录子页组件
 * - 今日能量结余横幅：预算 = (1.2BMR + 今日锻炼净) − 目标缺口（用户自定义）；剩余 = 预算 − 已摄入
 * - 餐次分段 → 食物搜索/分组chips(收藏优先★) → 默认份量带出可改 → 六大营养实时预览 → 保存
 * - 餐次筛选：点早/午/晚/加餐 → 今日列表只显示该餐（带「全部」）；「正在记录」提示当前保存目标
 * - 我的模板一键复制到今日；今日已记列表按餐次汇总可删；收藏切换即时生效；自定义食物弹窗
 * - 目标缺口由用户在页内自行设置（updateProfileApi dietTargetGap，0=维持 负=增肌）
 */
import { computed, onMounted, ref, watch } from 'vue'
import { Plus, Copy, Trash2, Settings, Search } from '@lucide/vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listFoodItems, listFoodRecords, createFoodRecord, deleteFoodRecord,
  toggleFoodFavorite, createFoodItem, updateFoodItem, listMealTemplates, createMealTemplate, deleteMealTemplate
} from '@/api/food'
import type { FoodItem, FoodRecord, MealTemplateItem, MealType } from '@/api/food'
import { FOOD_TYPE_LABELS, MEAL_LABELS } from '@/api/food'
import { listExerciseItems, listExerciseRecords } from '@/api/exercise'
import { listWeightRecords } from '@/api/health'
import { updateProfileApi } from '@/api/auth'
import { useUserStore } from '@/store/user'
import { formatDate } from '@/utils/format'
import { calcBmr, SEDENTARY_FACTOR } from '@/utils/activity'
import { recordNetKcal } from '@/utils/exercise'
import BlockTitle from '@/components/BlockTitle/BlockTitle.vue'
import EmptyState from '@/components/EmptyState/EmptyState.vue'
import LoadingMask from '@/components/LoadingMask/LoadingMask.vue'
import GroupedChips from '@/components/GroupedChips/GroupedChips.vue'
import type { GroupedChipGroup, GroupedChipItem } from '@/components/GroupedChips/GroupedChips.vue'
import ProgressRing from '@/components/ProgressRing/ProgressRing.vue'
import RecordHeatmap from '@/components/RecordHeatmap/RecordHeatmap.vue'
import type { HeatmapRow } from '@/components/RecordHeatmap/RecordHeatmap.vue'
import { fetchAllRecords } from '@/utils/fetchAll'
import { fillDaysRange } from '@/utils/daysSeries'

const props = defineProps<{ tick: number }>()
const emit = defineEmits<{ (e: 'changed'): void; (e: 'navigate', tab: string): void }>()

const userStore = useUserStore()
/** 目标热量缺口（用户自定义，默认 500 = 减0.5kg/周；0=维持，负=增肌） */
const TARGET_GAP = computed(() => userStore.userInfo?.dietTargetGap ?? 500)

/* ---------- 数据 ---------- */
const loading = ref(false)
const items = ref<FoodItem[]>([])
const records = ref<FoodRecord[]>([])
const templates = ref<{ id: number; name: string; items: MealTemplateItem[] }[]>([])
const today = formatDate(new Date())
function daysAgo(n: number) {
  const d = new Date()
  d.setDate(d.getDate() - n)
  return formatDate(d)
}

const mealType = ref<MealType>('breakfast')
/** 餐次筛选：all=显示全部，否则只显示该餐 */
const activeMeal = ref<MealType | 'all'>('all')
const keyword = ref('')
const selected = ref<FoodItem | null>(null)
const grams = ref<string>('')

/** 今日能量结余（预算 = 1.2BMR + 锻炼净 − 目标缺口） */
const balance = ref<{ bmr: number | null; burn: number; intake: number; budget: number | null; remain: number | null }>({
  bmr: null, burn: 0, intake: 0, budget: null, remain: null
})

/** 今日已摄入（kcal） */
const todayIntake = computed(() => balance.value.intake)

/** 摄入进度百分比（已摄入 ÷ 预算；>100 超预算） */
const intakePct = computed(() => {
  const b = balance.value
  return b.budget && b.budget > 0 ? Math.round(b.intake / b.budget * 100) : 0
})

/* ---------- 目标缺口设置弹窗 ---------- */
const gapDialog = ref(false)
const gapInput = ref('500')
function openGapDialog() {
  gapInput.value = String(TARGET_GAP.value)
  gapDialog.value = true
}
async function saveGap() {
  const v = Number(gapInput.value)
  if (!Number.isFinite(v) || v < -9999 || v > 3000) return ElMessage.warning('缺口需在 -9999~3000 之间（0=维持，负=增肌）')
  const res = await updateProfileApi({ dietTargetGap: v })
  userStore.setUserInfo(res.userInfo)
  ElMessage.success(`目标缺口已设为 ${v} kcal/天`)
  gapDialog.value = false
  await load(true)
}

/** 食物单位：液体（ml）用 ml，其余 g */
function unitOf(f: FoodItem | undefined | null) {
  return f?.unitLabel === 'ml' ? 'ml' : 'g'
}
/** 营养基准标签：液体按 100ml（1ml≈1g，营养数值不变） */
function per100(f: FoodItem | undefined | null) {
  return f?.unitLabel === 'ml' ? 'kcal/100ml' : 'kcal/100g'
}

/** 食物营养值（每100g/ml × 份量 ÷ 100） */
function nutri(f: FoodItem, g: number) {
  return {
    kcal: f.kcal * g / 100,
    protein: f.protein * g / 100,
    fat: f.fat * g / 100,
    carbs: f.carbs * g / 100,
    sodium: f.sodium * g / 100,
    fiber: f.fiber * g / 100
  }
}
/** 当前选中食物 + 份量的预览 */
const preview = computed(() => {
  const f = selected.value
  const g = Number(grams.value)
  if (!f || !g || g <= 0) return null
  return nutri(f, g)
})

/** FoodItem → chip 数据 */
function toChip(f: FoodItem): GroupedChipItem {
  return { id: f.id, label: f.name, sub: `${f.kcal} ${per100(f)}`, favorite: f.favorite }
}

/** 分组展示（收藏优先 + 搜索过滤） */
const chipGroups = computed<GroupedChipGroup[]>(() => {
  const kw = keyword.value.trim()
  const list = items.value.filter((i) => !kw || i.name.includes(kw))
  const fav = list.filter((i) => i.favorite)
  const rest = list.filter((i) => !i.favorite)
  const favGroup = fav.length ? [{ label: '★ 常用', items: fav.map(toChip) }] : []
  const groups = (['staple', 'protein', 'veg', 'fruit', 'snack', 'other'] as const)
    .map((t) => ({ label: FOOD_TYPE_LABELS[t], items: rest.filter((i) => i.type === t).map(toChip) }))
    .filter((g) => g.items.length)
  return [...favGroup, ...groups]
})

/** chip 事件 → 映射回 FoodItem */
function onSelectChip(c: GroupedChipItem) {
  const f = items.value.find((i) => i.id === c.id)
  if (f) selectFood(f)
}
async function onToggleFavChip(c: GroupedChipItem) {
  const f = items.value.find((i) => i.id === c.id)
  if (f) await toggleFav(f)
}

/** 今日列表（按餐次排序；筛选 activeMeal 时只显示该餐） */
const todayList = computed(() => {
  const order: MealType[] = ['breakfast', 'lunch', 'dinner', 'snack']
  const list = activeMeal.value === 'all'
    ? records.value
    : records.value.filter((r) => r.mealType === activeMeal.value)
  return [...list].sort((a, b) => order.indexOf(a.mealType) - order.indexOf(b.mealType))
})
/** 筛选餐次显示名（全部=null） */
const activeMealLabel = computed(() => activeMeal.value === 'all' ? null : MEAL_LABELS[activeMeal.value])

/** 点击餐次：切换筛选 + 同时设为记录目标 */
function setMeal(m: MealType | 'all') {
  activeMeal.value = m
  if (m !== 'all') mealType.value = m
}

/* ---------- 加载 ---------- */
/** 近 30 天记录（页尾热力图用） */
const heatRows = ref<HeatmapRow[]>([])

/** 记录数 → 热力档（0-4） */
function heatLevel(n: number) {
  if (n <= 0) return 0
  if (n <= 2) return 1
  if (n <= 4) return 2
  if (n <= 6) return 3
  return 4
}

async function load(silent = false) {
  if (!silent) loading.value = true
  try {
    const since = daysAgo(29)
    const [itms, recPage, tpls, erecs, itms2, wres, food30, ex30] = await Promise.all([
      listFoodItems(),
      listFoodRecords({ startDate: today, endDate: today, page: 1, size: 100 }),
      listMealTemplates(),
      listExerciseRecords({ startDate: today, endDate: today, page: 1, size: 100 }),
      listExerciseItems(),
      listWeightRecords({ page: 1, size: 1 }),
      // 近 30 天全量（热力图）
      fetchAllRecords((page, size) => listFoodRecords({ startDate: since, endDate: today, page, size })),
      fetchAllRecords((page, size) => listExerciseRecords({ startDate: since, endDate: today, page, size }))
    ])
    items.value = itms
    records.value = recPage.records
    templates.value = tpls.map((t) => ({ id: t.id, name: t.name, items: safeParse(t.items) }))

    // 30 天热力图：饮食/锻炼两行，档位按当天记录条数
    const foodByDay = new Map<string, number>()
    for (const r of food30) foodByDay.set(r.recordDate, (foodByDay.get(r.recordDate) ?? 0) + 1)
    const exByDay = new Map<string, number>()
    for (const r of ex30) exByDay.set(r.recordDate, (exByDay.get(r.recordDate) ?? 0) + 1)
    const dayList = fillDaysRange(since, today, new Map(), (d) => d)
    heatRows.value = [
      {
        label: '饮食',
        cells: dayList.map((d) => ({
          date: d,
          level: heatLevel(foodByDay.get(d) ?? 0),
          tip: `${d} · 饮食 ${foodByDay.get(d) ?? 0} 条`
        }))
      },
      {
        label: '锻炼',
        cells: dayList.map((d) => ({
          date: d,
          level: heatLevel(exByDay.get(d) ?? 0),
          tip: `${d} · 锻炼 ${exByDay.get(d) ?? 0} 条`
        }))
      }
    ]

    // 预算：1.2BMR + 今日锻炼净 − 目标缺口
    const w = wres.records?.[0]
    const weight = w ? Number(w.weight) : null
    const bodyFat = w?.bodyFat != null ? Number(w.bodyFat) : null
    const bmr = calcBmr(weight, bodyFat)
    let burn = 0
    let intake = 0
    for (const r of records.value) {
      const f = items.value.find((i) => i.id === r.foodId)
      if (!f) continue
      intake += f.kcal * Number(r.grams) / 100
    }
    for (const r of erecs.records) {
      const item = itms2.find((i) => i.id === r.exerciseId)
      if (!item) continue
      burn += recordNetKcal(r, item, weight)
    }
    const base = bmr != null ? Math.round(bmr * SEDENTARY_FACTOR) : null
    const budget = base != null ? base + Math.round(burn) - TARGET_GAP.value : null
    balance.value = {
      bmr,
      burn: Math.round(burn),
      intake: Math.round(intake),
      budget,
      remain: budget != null ? budget - intake : null
    }
  } finally {
    if (!silent) loading.value = false
  }
}

/**
 * 缺口变化 → 重算预算（不重拉数据）
 * 修复时序竞态：F5 时 load() 可能先于 getMe 返回执行，TARGET_GAP 回退默认 500 算出 budget；
 * getMe 回来后 store 更新缺口，此 watch 用已有 bmr/burn/intake 重算，无需再请求
 */
watch(TARGET_GAP, (gap) => {
  const b = balance.value
  if (b.bmr == null) return
  const base = Math.round(b.bmr * SEDENTARY_FACTOR)
  b.budget = base + b.burn - gap
  b.remain = b.budget - b.intake
})

function safeParse(json: string): MealTemplateItem[] {
  try {
    const arr = JSON.parse(json)
    return Array.isArray(arr) ? arr : []
  } catch {
    return []
  }
}

/* ---------- 交互 ---------- */
function selectFood(f: FoodItem) {
  selected.value = f
  grams.value = f.defaultGrams != null ? String(f.defaultGrams) : '100'
}

async function toggleFav(f: FoodItem) {
  const next = !f.favorite
  await toggleFoodFavorite(f.id, next)
  f.favorite = next
}

async function save() {
  const f = selected.value
  const g = Number(grams.value)
  if (!f) return ElMessage.warning('请先选择食物')
  if (!g || g <= 0) return ElMessage.warning('请输入份量')
  await createFoodRecord({ foodId: f.id, recordDate: today, mealType: mealType.value, grams: g })
  ElMessage.success('已记录')
  emit('changed')
  await load(true)
}

async function remove(r: FoodRecord) {
  await ElMessageBox.confirm('删除这条饮食记录？', '确认', { type: 'warning' })
  await deleteFoodRecord(r.id)
  ElMessage.success('已删除')
  emit('changed')
  await load(true)
}

/** 一键复制模板到今日 */
async function applyTemplate(t: { id: number; name: string; items: MealTemplateItem[] }) {
  if (!t.items.length) return
  for (const it of t.items) {
    await createFoodRecord({ foodId: it.foodId, recordDate: today, mealType: it.mealType, grams: it.grams })
  }
  ElMessage.success(`已复制模板「${t.name}」到今日`)
  emit('changed')
  await load(true)
}

/** 把今日某餐存为模板 */
async function saveAsTemplate() {
  const meals = ['breakfast', 'lunch', 'dinner', 'snack'] as const
  const tplItems: MealTemplateItem[] = []
  for (const m of meals) {
    for (const r of records.value.filter((x) => x.mealType === m)) {
      tplItems.push({ foodId: r.foodId, grams: Number(r.grams), mealType: m })
    }
  }
  if (!tplItems.length) return ElMessage.warning('今日还没有记录')
  const { value } = await ElMessageBox.prompt('给模板起个名（如：工作日早餐）', '存为我的模板', {
    inputValue: '我的模板',
    inputValidator: (v) => (v && v.trim() ? true : '模板名不能为空')
  })
  await createMealTemplate({ name: value, items: JSON.stringify(tplItems) })
  ElMessage.success('模板已保存')
  await load(true)
}

async function removeTemplate(id: number) {
  await ElMessageBox.confirm('删除这个模板？', '确认', { type: 'warning' })
  await deleteMealTemplate(id)
  await load(true)
}

/** 自定义/编辑食物（编辑时 editTarget 非空；营养素可选填，空按 0） */
const addDialog = ref(false)
const editTarget = ref<FoodItem | null>(null)
const addForm = ref({ name: '', type: 'staple' as FoodItem['type'], kcal: '', protein: '', fat: '', carbs: '', sodium: '', fiber: '', defaultGrams: '' })

function resetAddForm() {
  editTarget.value = null
  addForm.value = { name: '', type: 'staple', kcal: '', protein: '', fat: '', carbs: '', sodium: '', fiber: '', defaultGrams: '' }
}

function openAddDialog() {
  resetAddForm()
  addDialog.value = true
}

/** chip 编辑铅笔 → 回填打开弹窗 */
function onEditChip(c: GroupedChipItem) {
  const f = items.value.find((i) => i.id === c.id)
  if (!f) return
  editTarget.value = f
  addForm.value = {
    name: f.name,
    type: f.type,
    kcal: String(f.kcal),
    protein: f.protein ? String(f.protein) : '',
    fat: f.fat ? String(f.fat) : '',
    carbs: f.carbs ? String(f.carbs) : '',
    sodium: f.sodium ? String(f.sodium) : '',
    fiber: f.fiber ? String(f.fiber) : '',
    defaultGrams: f.defaultGrams != null ? String(f.defaultGrams) : ''
  }
  addDialog.value = true
}

async function saveFood() {
  const f = addForm.value
  if (!f.name.trim()) return ElMessage.warning('请输入食物名')
  if (!f.kcal || Number(f.kcal) <= 0) return ElMessage.warning('请输入每100g热量')
  const payload = {
    name: f.name.trim(),
    type: f.type,
    kcal: Number(f.kcal),
    protein: Number(f.protein) || 0,
    fat: Number(f.fat) || 0,
    carbs: Number(f.carbs) || 0,
    sodium: Number(f.sodium) || 0,
    fiber: Number(f.fiber) || 0,
    defaultGrams: f.defaultGrams ? Number(f.defaultGrams) : null
  }
  if (editTarget.value) {
    await updateFoodItem(editTarget.value.id, payload)
    ElMessage.success('食物已更新')
  } else {
    await createFoodItem(payload)
    ElMessage.success('食物已添加')
  }
  addDialog.value = false
  resetAddForm()
  await load(true)
  // 正在选中的食物被编辑 → 同步新数据，预览/保存用最新值
  if (selected.value) {
    const fresh = items.value.find((i) => i.id === selected.value!.id)
    if (fresh) selected.value = fresh
  }
}

onMounted(load)
watch(() => props.tick, () => load(true))
</script>

<template>
  <section class="card fdi">
    <BlockTitle title="记录饮食" :hint="balance.bmr != null ? `预算 = 1.2×BMR(${balance.bmr}) + 锻炼(${balance.burn}) − 目标缺口 ${TARGET_GAP}` : '记录体重+体脂率后可按 1.2BMR+锻炼 预算'">
      <template #aside>
        <button class="fdi__gap-btn" :title="`设置每日目标缺口（当前 ${TARGET_GAP}）`" @click="openGapDialog">
          <component :is="Settings" :size="14" />
          <span>设置缺口</span>
        </button>
      </template>
    </BlockTitle>

    <!-- 今日能量结余横幅 -->
    <div class="fdi__balance" :class="{ 'fdi__balance--over': balance.remain != null && balance.remain < 0 }">
      <div class="fdi__balance-main">
        <div class="fdi__balance-row">
          <span class="fdi__balance-label">今日剩余额度</span>
          <span class="fdi__balance-val" :class="balance.remain != null && balance.remain < 0 ? 'fdi__balance-val--over' : 'fdi__balance-val--gap'">
            {{ balance.remain != null ? balance.remain : '—' }}<i>kcal</i>
          </span>
          <span class="fdi__balance-sub">
            <template v-if="balance.bmr != null">预算 = 基础{{ Math.round((balance.bmr ?? 0) * 1.2) }} + 锻炼{{ balance.burn }} − 缺口{{ TARGET_GAP }} = <b class="num">{{ balance.budget }}</b> · 已摄入 {{ todayIntake }}</template>
            <template v-else>记录体重+体脂率后显示预算（1.2BMR + 锻炼 − 缺口）</template>
          </span>
        </div>
        <!-- 摄入进度条：已摄入 ÷ 预算，绿→琥珀→红 -->
        <div v-if="balance.budget" class="fdi__progress" :title="`已摄入 ${todayIntake} / 预算 ${balance.budget} kcal`">
          <div
            class="fdi__progress-fill"
            :class="intakePct > 100 ? 'is-over' : intakePct > 85 ? 'is-warn' : 'is-ok'"
            :style="{ width: Math.min(100, intakePct) + '%' }"
          ></div>
        </div>
      </div>
      <!-- 双进度环：摄入（超预算变红）/ 剩余额度 -->
      <div v-if="balance.budget" class="fdi__balance-rings">
        <ProgressRing
          :value="balance.intake"
          :max="balance.budget"
          ring-color="var(--c-intake)"
          :label="`摄入 ${balance.intake}/${balance.budget}`"
          :size="84"
        />
        <ProgressRing
          :value="Math.max(0, balance.remain ?? 0)"
          :max="balance.budget"
          ring-color="var(--c-gap)"
          :label="`剩余 ${balance.remain ?? '—'}`"
          :size="84"
        />
      </div>
    </div>

    <!-- 餐次（筛选 + 记录目标） -->
    <div class="fdi__meals">
      <button :class="{ 'is-on': activeMeal === 'all' }" @click="setMeal('all')">全部</button>
      <button v-for="(label, key) in MEAL_LABELS" :key="key" :class="{ 'is-on': activeMeal === key }" @click="setMeal(key)">{{ label }}</button>
      <span class="fdi__meals-hint">{{ activeMealLabel ? `正在记录：${activeMealLabel}` : '显示全部' }}</span>
    </div>

    <!-- 搜索 + 分组 chips -->
    <div class="fdi__search-wrap">
      <component :is="Search" :size="14" class="fdi__search-icon" />
      <input v-model="keyword" class="fdi__search" type="text" placeholder="搜索食物…">
    </div>
    <GroupedChips
      class="fdi__groups"
      :groups="chipGroups"
      :active-id="selected?.id ?? null"
      show-fav
      show-edit
      add-label="自定义食物"
      @select="onSelectChip"
      @toggle-fav="onToggleFavChip"
      @edit="onEditChip"
      @add="openAddDialog"
    />

    <!-- 份量 + 营养预览 -->
    <div v-if="selected" class="fdi__form-row" style="margin-top: var(--cb-space-4);">
      <div class="fdi__field">
        <span>份量</span>
        <input v-model="grams" class="num" type="number" min="1" :placeholder="selected.unitLabel ? `默认 1${selected.unitLabel}` : '克数'">
      </div>
      <div class="fdi__field">
        <span>餐次</span>
        <select v-model="mealType">
          <option v-for="(label, key) in MEAL_LABELS" :key="key" :value="key">{{ label }}</option>
        </select>
      </div>
    </div>
    <div v-if="preview" class="fdi__preview">
      预计 <b>{{ Math.round(preview.kcal) }}</b> kcal（{{ selected?.name }} {{ selected?.kcal }} {{ per100(selected) }} × {{ grams }}{{ unitOf(selected) }}）
      <div class="fdi__nutri">
        <span>蛋白质 <b>{{ preview.protein.toFixed(1) }}</b>g</span>
        <span>脂肪 <b>{{ preview.fat.toFixed(1) }}</b>g</span>
        <span>碳水 <b>{{ preview.carbs.toFixed(1) }}</b>g</span>
        <span>钠 <b>{{ Math.round(preview.sodium) }}</b>mg</span>
        <span>纤维 <b>{{ preview.fiber.toFixed(1) }}</b>g</span>
      </div>
    </div>
    <button v-if="selected" class="fdi__save" :disabled="!preview" @click="save"><component :is="Plus" :size="15" /> 保存到今日</button>

    <!-- 我的模板 -->
    <div v-if="templates.length" class="fdi__tpl">
      <div class="fdi__tpl-title"><component :is="Copy" :size="13" /> 我的模板</div>
      <div class="fdi__tpl-list">
        <button v-for="t in templates" :key="t.id" class="fdi__tpl-chip" @click="applyTemplate(t)">
          {{ t.name }}（{{ t.items.length }}项）
          <span style="cursor:pointer" title="删除模板" @click.stop="removeTemplate(t.id)">✕</span>
        </button>
        <button class="fdi__tpl-chip" @click="saveAsTemplate">＋ 把今日存为模板</button>
      </div>
    </div>
    <div v-else class="fdi__tpl">
      <div class="fdi__tpl-title"><component :is="Copy" :size="13" /> 我的模板</div>
      <button class="fdi__tpl-chip" @click="saveAsTemplate">＋ 把今日存为模板</button>
    </div>

    <!-- 今日已记 -->
    <div class="fdi__today">
      <div class="fdi__today-title">
        今日已记<template v-if="activeMealLabel">：{{ activeMealLabel }}</template> · <b>{{ todayIntake }}</b> kcal
        <template v-if="activeMealLabel">（合计）</template>
      </div>
      <div v-if="!todayList.length" class="fdi__today-empty">{{ records.length ? '该餐次还没有记录' : '还没有记录，选个食物记下第一笔' }}</div>
      <div v-else class="fdi__today-list">
        <div v-for="r in todayList" :key="r.id" class="fdi__today-item">
          <span class="fdi__today-name">{{ items.find((i) => i.id === r.foodId)?.name ?? '食物' }}</span>
          <span class="fdi__today-meal">{{ MEAL_LABELS[r.mealType] }}</span>
          <span class="fdi__today-detail">{{ r.grams }}g</span>
          <span class="fdi__today-kcal">{{ Math.round((items.find((i) => i.id === r.foodId)?.kcal ?? 0) * Number(r.grams) / 100) }}<small>kcal</small></span>
          <button class="fdi__today-del" title="删除" @click="remove(r)"><component :is="Trash2" :size="14" /></button>
        </div>
      </div>
    </div>

    <!-- 30 天记录热力图（把记录变成看得见的坚持） -->
    <div class="fdi__heat">
      <div class="fdi__heat-title">最近 30 天</div>
      <RecordHeatmap :rows="heatRows" legend-text="" />
    </div>

    <!-- 自定义/编辑食物弹窗 -->
    <el-dialog v-model="addDialog" :title="editTarget ? '编辑食物' : '自定义食物'" width="min(460px,92vw)">
      <div class="fdh__edit">
        <div class="fdh__field"><label>食物名</label><input v-model="addForm.name" type="text" placeholder="如：杂粮饭"></div>
        <div class="fdh__optgrid">
          <div class="fdh__field"><label>类型</label>
            <select v-model="addForm.type">
              <option v-for="(label, key) in FOOD_TYPE_LABELS" :key="key" :value="key">{{ label }}</option>
            </select>
          </div>
          <div class="fdh__field"><label>每100g/ml热量</label><input v-model="addForm.kcal" class="num" type="number" placeholder="kcal"></div>
        </div>
        <div class="fdh__optgrid">
          <div class="fdh__field"><label>蛋白质 g/100g</label><input v-model="addForm.protein" class="num" type="number" min="0" placeholder="可选"></div>
          <div class="fdh__field"><label>脂肪 g/100g</label><input v-model="addForm.fat" class="num" type="number" min="0" placeholder="可选"></div>
        </div>
        <div class="fdh__optgrid">
          <div class="fdh__field"><label>碳水 g/100g</label><input v-model="addForm.carbs" class="num" type="number" min="0" placeholder="可选"></div>
          <div class="fdh__field"><label>膳食纤维 g/100g</label><input v-model="addForm.fiber" class="num" type="number" min="0" placeholder="可选"></div>
        </div>
        <div class="fdh__optgrid">
          <div class="fdh__field"><label>钠 mg/100g</label><input v-model="addForm.sodium" class="num" type="number" min="0" placeholder="可选"></div>
          <div class="fdh__field"><label>默认份量（g/ml，可选）</label><input v-model="addForm.defaultGrams" class="num" type="number" placeholder="如：150"></div>
        </div>
        <p v-if="editTarget" class="fdi__edit-tip">修改后，今日已记列表与统计将按新营养值重算。</p>
      </div>
      <template #footer>
        <el-button @click="addDialog = false">取消</el-button>
        <el-button type="primary" @click="saveFood">保存</el-button>
      </template>
    </el-dialog>

    <!-- 目标缺口设置弹窗 -->
    <el-dialog v-model="gapDialog" title="每日目标热量缺口" width="min(420px,92vw)">
      <div class="fdh__edit">
        <div class="fdh__field">
          <label>缺口（kcal/天）</label>
          <input v-model="gapInput" class="num" type="number" placeholder="如：1000">
        </div>
        <p class="fdi__gap-tip">预算 = 1.2×BMR + 锻炼净 − 缺口。缺口 0 = 维持体重；500 ≈ 每周减 0.5kg；1000~1500 = 快速减脂；负值为增肌。范围 -9999~3000。</p>
      </div>
      <template #footer>
        <el-button @click="gapDialog = false">取消</el-button>
        <el-button type="primary" @click="saveGap">保存</el-button>
      </template>
    </el-dialog>

    <LoadingMask :show="loading" :size="28" text="加载饮食…" />
  </section>
</template>

<style lang="scss" scoped>
@use '../food';
</style>
