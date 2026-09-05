<script setup lang="ts">
/**
 * 开发日志页
 * - header 卡：会话状态徽标 + 日期范围筛选 + 开始/结束开发按钮（标题+今日累计时长）
 * - 数据卡顶部「今日开发时段」卡：当天多段会话明细（第 N 段 起止时间 / 时长 / 状态）
 * - 数据卡内列表区：内容模糊搜索（此处置顶）+ 各天开发内容（日期/类型/模块/内容，列定宽+内容省略）
 * - 结束开发：弹窗收集当天功能变更（多条：类型/模块/描述）→ 批量录入 → 再结束会话
 * - 支持一天多次开始/结束：每日时长 = 当天所有段时长累计
 */
import { computed, markRaw, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { VideoPlay, VideoPause } from '@element-plus/icons-vue'
import { ChartBar, List as ListIcon } from '@lucide/vue'
import PagePanel from '@/components/PagePanel/PagePanel.vue'
import DataList from '@/components/DataList/DataList.vue'
import PagePager from '@/components/PagePager/PagePager.vue'
import DateRangePicker from '@/components/DateRangePicker/DateRangePicker.vue'
import RangeTabs, { type RangeTabItem } from '@/components/RangeTabs/RangeTabs.vue'
import MetricCard from '@/components/MetricCard/MetricCard.vue'
import LoadingMask from '@/components/LoadingMask/LoadingMask.vue'
import RecordDetailDialog from '@/components/RecordDetailDialog/RecordDetailDialog.vue'
import type { DetailRow } from '@/components/RecordDetailDialog/RecordDetailDialog.vue'
import type { DataListColumn } from '@/components/DataList/DataList.vue'
import {
  addFeature,
  endDevSession,
  getDevStatistics,
  getDevSummary,
  getDevStatsRange,
  startDevSession
} from '@/api/dev'
import type { DevSummary, DevRangeStats, DevelopmentSession } from '@/api/dev'
import { useECharts } from '@/utils/useECharts'
import { cssVar } from '@/utils/theme'
import { formatDate, formatDateTime } from '@/utils/format'
import { parseMdDraft, FEATURE_TYPES, type FeatureDraft } from '@/utils/mdDraft'

/** 合并后的按天功能行 */
interface DevFeatureRow {
  date: string
  type: string
  module: string
  content: string
  /** 完整时间（详情弹窗用） */
  time: string
}

/** 常用模块（统一清单：页面模块 + 系统/技术域；el-select allow-create 可输入自定义） */
const FEATURE_MODULES = [
  '记账', '健康', '学习', '每日总结', '开发日志', '操作日志', '个人中心', '首页概览',
  '认证', '安全', '布局', '通用', '系统', '文档', '前端', '后端'
]

const loading = ref(false)
const summary = ref<DevSummary | null>(null)
const rows = ref<DevFeatureRow[]>([])

/** 内容模糊搜索（列表区） */
const keyword = ref('')
/** 日期范围筛选（header，yyyy-MM-dd） */
const dateRange = ref<[string, string] | null>(null)

/** 分页 */
const page = ref(1)
const size = ref(15)

/** 过滤后的行（内容模糊 + 日期范围，客户端过滤） */
const filteredRows = computed(() => {
  const kw = keyword.value.trim()
  const [s, e] = dateRange.value ?? []
  return rows.value.filter((r) => {
    if (kw && !r.content.includes(kw)) return false
    if (s && e && (r.date < s || r.date > e)) return false
    return true
  })
})

/** 分页后的行（展示用） */
const pagedRows = computed(() => {
  const start = (page.value - 1) * size.value
  return filteredRows.value.slice(start, start + size.value)
})

/** 筛选/搜索变更：回到第 1 页 */
function onFilter() {
  page.value = 1
}

/** 当天所有会话（时段卡） */
const todaySessions = computed<DevelopmentSession[]>(() => summary.value?.sessions ?? [])
/** 是否有进行中段 */
const sessionRunning = computed(() => todaySessions.value.some((s) => s.status === 0))
/** 今天是否有会议（无论是否结束）——区分「今日已结束」/「今日未开发」 */
const hasTodaySessions = computed(() => todaySessions.value.length > 0)
/** 状态徽标文案：进行中 / 已结束 / 未开发 */
const statusText = computed(() =>
  sessionRunning.value ? '开发进行中' : hasTodaySessions.value ? '今日已结束' : '今日未开发'
)
const durationText = computed(() => {
  const m = summary.value?.durationMinutes ?? 0
  return `${Math.floor(m / 60)}h ${m % 60}m`
})

/** 列定义：日期/类型/模块固定宽完整显示（不省略），仅内容列省略更多
 * 不启用 hideBelow：三个字段始终完整呈现（列表字号固定 14px，日期列容纳 2026-08-24） */
const columns: DataListColumn[] = [
  { key: 'date', label: '日期', width: 132 },
  { key: 'type', label: '类型', width: 60 },
  { key: 'module', label: '模块', width: 104 },
  { key: 'content', label: '内容' }
]

/** 时间戳取 HH:mm（后端 ISO 或空格分隔均兼容） */
function hhmm(t: string | null | undefined): string {
  if (!t) return '--:--'
  return t.slice(11, 16)
}

/** 单段时长文本：已结束取时长，进行中显示「进行中」 */
function segmentDuration(s: DevelopmentSession): string {
  if (s.status === 0) return '进行中'
  const m = s.durationMinutes ?? 0
  return `${Math.floor(m / 60)}h ${m % 60}m`
}

/** 加载：今日汇总 + 各天开发内容（按日期去重逐个拉取）；silent=true 后台静默刷新（开始/结束开发后联动，不显示遮罩防闪烁） */
async function load(silent = false) {
  if (!silent) loading.value = true
  try {
    const [today, stat] = await Promise.all([getDevSummary(), getDevStatistics()])
    summary.value = today

    const dates = [...new Set((stat.sessions ?? []).map((s) => s.sessionDate))]
    const list: DevFeatureRow[] = []
    for (const d of dates) {
      const sum = await getDevSummary(d).catch(() => null)
      for (const f of sum?.features ?? []) {
        list.push({
          date: formatDate(d),
          type: f.type,
          module: f.module,
          content: f.content,
          time: formatDateTime(f.createdAt)
        })
      }
    }
    // 排序：整体按时间倒序——① 日期新的在前；② 同日时间晚的在前（即最新一条置顶）
    // time='yyyy-MM-dd HH:mm:ss' 字典序=时间序，直接字符串比较即可
    list.sort((a, b) => (a.time < b.time ? 1 : a.time > b.time ? -1 : 0))
    rows.value = list
  } finally {
    if (!silent) loading.value = false
  }
}

/** 开始开发 */
async function onStart() {
  await startDevSession()
  ElMessage.success('开始开发')
  load(true)
}

/* ================= 结束开发：弹窗收集功能变更 ================= */
const endDialog = ref(false)
const drafts = ref<FeatureDraft[]>([])
const ending = ref(false)

function openEndDialog() {
  drafts.value = [{ type: '新增', module: '记账', content: '' }]
  endDialog.value = true
}

function addDraft() {
  drafts.value.push({ type: '新增', module: '记账', content: '' })
}

function removeDraft(i: number) {
  drafts.value.splice(i, 1)
}

/** 导入 md 文件：解析规范格式预填草稿 */
function onImportMd(f: { raw?: File }) {
  if (!f.raw) return
  const reader = new FileReader()
  reader.onload = () => {
    const text = String(reader.result ?? '')
    const parsed = parseMdDraft(text)
    if (parsed.length) {
      drafts.value = parsed
      ElMessage.success(`已从 md 导入 ${parsed.length} 条功能，可继续编辑`)
    } else {
      ElMessage.warning('未解析到有效功能条目（格式：### [新增] 模块 标题）')
    }
  }
  reader.readAsText(f.raw)
}

/** 有内容待录入的功能条数 */
const validCount = computed(() => drafts.value.filter((d) => d.content.trim()).length)

/** 确认结束：先批量录入功能，再结束会话；无论成败都刷新列表 */
async function onEndConfirm() {
  ending.value = true
  try {
    // 1) 逐条记录功能变更（空描述跳过）
    for (const d of drafts.value) {
      const c = d.content.trim()
      if (!c) continue
      await addFeature({ type: d.type, module: d.module, content: c })
    }
    // 2) 结束会话
    await endDevSession()
    ElMessage.success('功能已记录，开发已结束')
    endDialog.value = false
  } catch {
    /* requestApi 已弹错 */
  } finally {
    // 3) 无论成败都刷新列表（避免写库成功但列表未刷新的视觉错觉）
    try {
      await load(true)
    } catch {
      /* ignore */
    }
    ending.value = false
  }
}

/** 点击行：打开详情弹窗 */
const detail = ref<DevFeatureRow | null>(null)
const dialogVisible = computed(() => detail.value !== null)
function onRowClick(item: DevFeatureRow) {
  detail.value = item
}
function onDialogClose() {
  detail.value = null
}

/** 详情弹窗行（类型走 #cell-type 自定义，复用 dev-log__type 徽标） */
const detailRows = computed<DetailRow[]>(() => {
  const d = detail.value
  if (!d) return []
  return [
    { key: 'date', label: '日期', value: d.date, mono: true },
    { key: 'time', label: '时间', value: d.time, mono: true },
    { key: 'type', label: '类型', value: d.type },
    { key: 'module', label: '模块', value: d.module },
    { key: 'content', label: '内容', value: d.content, wide: true }
  ]
})

/* ================= 汇总 Tab ================= */
type ViewMode = 'detail' | 'summary'
const viewMode = ref<ViewMode>('detail')

/** 汇总范围：近 7 / 30 天 / 全部 */
const rangeOptions: RangeTabItem[] = [
  { key: '7', label: '近 7 天' },
  { key: '30', label: '近 30 天' },
  { key: 'all', label: '全部' }
]
const rangeKey = ref<'7' | '30' | 'all'>('7')
const rangeDays = computed(() => (rangeKey.value === 'all' ? undefined : Number(rangeKey.value)))

const stats = ref<DevRangeStats | null>(null)
const statsLoading = ref(false)

/** 汇总时长文本 */
const statsDurationText = computed(() => {
  const m = stats.value?.durationMinutes ?? 0
  return `${Math.floor(m / 60)}h ${m % 60}m`
})

/** 模块分布（按条数降序） */
const moduleDist = computed(() =>
  Object.entries(stats.value?.byModule ?? {})
    .map(([name, count]) => ({ name, count }))
    .sort((a, b) => b.count - a.count)
)
const moduleMax = computed(() => moduleDist.value[0]?.count ?? 1)
/** 类型分布（固定顺序：新增/修改/修复/删除） */
const typeKeys = ['新增', '修改', '修复', '删除'] as const
const typeDist = computed(() =>
  typeKeys.map((t) => ({ type: t, count: stats.value?.byType?.[t] ?? 0 }))
)

/** 按天柱状图数据 */
const dayChartEl = ref<HTMLDivElement | null>(null)
const dayChart = useECharts(dayChartEl, { redraw: () => renderDayChart() })

/** 容器未布局完成（宽为 0）时的短重试计数，成功后清零 */
let dayChartRetry = 0

function renderDayChart() {
  const chart = dayChart.ensure()
  if (!chart) {
    // 容器宽高为 0（v-if 刚挂载/主题重绘时）：短暂重试，避免图表被跳过而永久不显示
    if (dayChartRetry < 3) {
      dayChartRetry++
      setTimeout(renderDayChart, 250)
    }
    return
  }
  dayChartRetry = 0
  const base = dayChartEl.value ?? document.documentElement
  const c = {
    mod: cssVar('--cb-mod', cssVar('--cb-primary'), base),
    muted: cssVar('--cb-ink-muted'),
    hairline: cssVar('--cb-hairline')
  }
  const byDay = stats.value?.byDay ?? {}
  const dates = Object.keys(byDay)
  const mins = dates.map((d) => byDay[d])
  chart.setOption({
    grid: { top: 24, right: 12, bottom: 26, left: 44 },
    tooltip: {
      trigger: 'axis',
      formatter: (ps: any) => {
        const p = ps[0]
        return `${p.axisValue}<br/>开发 <b>${p.value}</b> 分钟`
      }
    },
    xAxis: {
      type: 'category',
      data: dates.map((d) => d.slice(5)),
      axisLine: { lineStyle: { color: c.hairline } },
      axisTick: { show: false },
      axisLabel: {
        color: c.muted,
        interval: dates.length > 10 ? Math.floor(dates.length / 10) : 0
      }
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: c.muted },
      splitLine: { lineStyle: { color: c.hairline, type: 'dashed' } }
    },
    series: [{
      type: 'bar',
      data: mins,
      barWidth: '45%',
      itemStyle: { color: c.mod, borderRadius: [3, 3, 0, 0] }
    }]
  } as any)
}

/** 加载汇总（首次/切范围带遮罩；silent 供开始/结束开发后联动静默刷新） */
async function loadStats(silent = false) {
  if (!silent) statsLoading.value = true
  try {
    stats.value = await getDevStatsRange(rangeDays.value)
    // 汇总视图 v-if 挂载/布局完成后才 init：避免接口过快返回时容器宽高为 0、图表被跳过（偶发不显示）
    await nextTick()
    renderDayChart()
  } finally {
    if (!silent) statsLoading.value = false
  }
}

function switchMode(mode: ViewMode) {
  viewMode.value = mode
  if (mode === 'summary') {
    // 汇总视图是 v-if：切回时容器被销毁重建。先销毁旧图表实例，
    // 再把「等 stats 后 nextTick 再 init」与「R 双保险去掉 rAF」交给 loadStats，避免绑到已移除 DOM
    dayChart.dispose()
    loadStats()
  }
}

watch(rangeKey, () => loadStats())

/* ---------- 进行中会话：今日时长动态增长（每分钟轻量刷新今日汇总，不重拉列表） ---------- */
let sessionTimer: ReturnType<typeof setInterval> | null = null
function stopSessionTimer() {
  if (sessionTimer) {
    clearInterval(sessionTimer)
    sessionTimer = null
  }
}
/** 只刷新今日 summary（进行中段时长由后端实时计长），静默失败不影响 */
async function loadSummaryOnly() {
  try {
    summary.value = await getDevSummary()
  } catch {
    /* ignore */
  }
}
watch(sessionRunning, (running) => {
  stopSessionTimer()
  if (running) {
    sessionTimer = setInterval(loadSummaryOnly, 60_000)
  }
}, { immediate: true })

onMounted(load)
onBeforeUnmount(stopSessionTimer)
</script>

<template>
  <div class="dev-log">
    <PagePanel :title="loading ? '开发日志' : `开发日志 · 今日 ${durationText}`" :loading="loading">
    <template #header>
      <span class="dev-log__status" :class="sessionRunning ? 'dev-log__status--running' : hasTodaySessions ? 'dev-log__status--done' : 'dev-log__status--idle'">
        {{ statusText }}
      </span>
      <DateRangePicker v-model="dateRange" @change="onFilter" />
      <el-button
        v-if="!sessionRunning"
        type="primary"
        :icon="VideoPlay"
        :loading="loading"
        @click="onStart"
      >
        开始开发
      </el-button>
      <el-button
        v-else
        type="warning"
        plain
        :icon="VideoPause"
        :loading="loading"
        @click="openEndDialog"
      >
        结束开发
      </el-button>
    </template>

    <!-- 今日开发时段卡（独立） -->
    <section v-if="todaySessions.length" class="dev-log__segs">
      <h3 class="dev-log__segs-title">
        今日开发时段
        <span class="num">共 {{ todaySessions.length }} 段 · 累计 {{ durationText }}</span>
      </h3>
      <div v-for="(s, i) in todaySessions" :key="s.id" class="dev-log__seg">
        <span class="dev-log__seg-idx num">{{ i + 1 }}</span>
        <span class="dev-log__seg-range num">{{ hhmm(s.startTime) }} – {{ s.status === 0 ? '进行中' : hhmm(s.endTime) }}</span>
        <span class="dev-log__seg-dur num">{{ segmentDuration(s) }}</span>
        <span class="dev-log__seg-tag" :class="s.status === 0 ? 'dev-log__seg-tag--run' : 'dev-log__seg-tag--done'">
          {{ s.status === 0 ? '进行中' : '已结束' }}
        </span>
      </div>
    </section>

    <!-- 明细 / 汇总 切换 -->
    <div class="dev-log__tabs">
      <button
        class="dev-log__tab"
        :class="{ 'is-on': viewMode === 'detail' }"
        type="button"
        @click="switchMode('detail')"
      >
        <component :is="markRaw(ListIcon)" :size="14" /> 明细
      </button>
      <button
        class="dev-log__tab"
        :class="{ 'is-on': viewMode === 'summary' }"
        type="button"
        @click="switchMode('summary')"
      >
        <component :is="markRaw(ChartBar)" :size="14" /> 汇总
      </button>
    </div>

    <!-- ===== 汇总视图 ===== -->
    <div v-if="viewMode === 'summary'" class="dev-log__summary">
      <div class="dev-log__summary-head">
        <RangeTabs
          :options="rangeOptions"
          :model-value="rangeKey"
          @update:model-value="(v: string) => rangeKey = v as '7' | '30' | 'all'"
        />
        <span class="dev-log__summary-hint">开发汇总 · 完成后记录的功能与耗时</span>
      </div>

      <div class="dev-log__metrics">
        <MetricCard label="开发时长">
          <template #default><span class="num">{{ statsDurationText }}</span></template>
        </MetricCard>
        <MetricCard label="开发会话">
          <template #default><span class="num">{{ stats?.sessionCount ?? 0 }}<i>段</i></span></template>
        </MetricCard>
        <MetricCard label="功能条数">
          <template #default><span class="num">{{ stats?.featureCount ?? 0 }}<i>条</i></span></template>
        </MetricCard>
      </div>

      <div class="dev-log__summary-panel">
        <p class="dev-log__summary-title">按天开发时长</p>
        <div ref="dayChartEl" class="dev-log__day-chart"></div>
      </div>

      <div class="dev-log__summary-cols">
        <div class="dev-log__summary-panel">
          <p class="dev-log__summary-title">按模块（功能条数）</p>
          <div v-if="moduleDist.length" class="dev-log__dist">
            <div v-for="m in moduleDist" :key="m.name" class="dev-log__dist-row">
              <span class="dev-log__dist-name">{{ m.name }}</span>
              <div class="dev-log__dist-track">
                <div class="dev-log__dist-fill" :style="{ width: (m.count / moduleMax * 100) + '%' }"></div>
              </div>
              <span class="dev-log__dist-val num">{{ m.count }}</span>
            </div>
          </div>
          <p v-else class="dev-log__summary-empty">该范围内暂无开发记录</p>
        </div>

        <div class="dev-log__summary-panel">
          <p class="dev-log__summary-title">按类型</p>
          <div class="dev-log__types">
            <div v-for="t in typeDist" :key="t.type" class="dev-log__type-item">
              <span>{{ t.type }}</span>
              <b class="num">{{ t.count }}</b>
            </div>
          </div>
        </div>
      </div>

      <LoadingMask :show="statsLoading" :size="28" text="加载汇总…" />
    </div>

    <!-- 列表区：搜索框 + 各天开发内容 -->
    <div v-else class="dev-log__list">
      <div class="dev-log__list-tool">
        <el-input v-model="keyword" placeholder="搜索内容" clearable style="width: 220px" @input="onFilter" @clear="onFilter" />
        <span class="dev-log__list-count num">共 {{ filteredRows.length }} 条</span>
      </div>

      <DataList :items="pagedRows" :columns="columns" :max-rows="size" clickable @row-click="onRowClick">
        <template #cell="{ item, column }">
          <span v-if="column.key === 'date'" class="num">{{ item.date }}</span>
          <span v-else-if="column.key === 'type'" class="dev-log__type">{{ item.type }}</span>
          <span v-else-if="column.key === 'module'">{{ item.module }}</span>
          <span v-else class="dev-log__content">{{ item.content }}</span>
        </template>
      </DataList>

      <div class="dev-log__pager">
        <PagePager
          v-model:current-page="page"
          v-model:page-size="size"
          :total="filteredRows.length"
          :show-total="false"
        />
      </div>
    </div>
  </PagePanel>

  <!-- 结束开发：收集功能变更弹窗 -->
  <el-dialog
    :model-value="endDialog"
    title="结束开发"
    width="640px"
    @close="endDialog = false"
  >
    <p class="dev-log__end-tip">记录今天开发了哪些内容（每条：类型 + 模块 + 描述），结束前保存，之后不可再改。</p>
    <div class="dev-log__end-import">
      <el-upload :auto-upload="false" :show-file-list="false" accept=".md,.markdown,text/markdown" :on-change="onImportMd">
        <el-button size="small" plain>导入 md 文件</el-button>
      </el-upload>
      <span class="dev-log__end-import-hint">格式：### [新增] 模块 标题 + 列表项明细</span>
    </div>
    <div v-for="(d, i) in drafts" :key="i" class="dev-log__end-row">
      <el-select v-model="d.type" style="width: 92px">
        <el-option v-for="t in FEATURE_TYPES" :key="t" :label="t" :value="t" />
      </el-select>
      <el-select v-model="d.module" style="width: 120px" filterable allow-create default-first-option>
        <el-option v-for="m in FEATURE_MODULES" :key="m" :label="m" :value="m" />
      </el-select>
      <el-input v-model="d.content" type="textarea" :autosize="{ minRows: 1, maxRows: 4 }" placeholder="描述本次功能变更…" />
      <el-button class="dev-log__end-del" text type="danger" :disabled="drafts.length === 1" @click="removeDraft(i)">删除</el-button>
    </div>
    <el-button class="dev-log__end-add" plain @click="addDraft">＋ 添加一条</el-button>

    <template #footer>
      <span class="dev-log__end-count num">共 {{ drafts.length }} 条</span>
      <el-button @click="endDialog = false">取消</el-button>
      <el-button type="primary" :loading="ending" @click="onEndConfirm">
        {{ validCount ? '结束开发并记录' : '直接结束开发' }}
      </el-button>
    </template>
  </el-dialog>

  <!-- 行详情弹窗 -->
  <RecordDetailDialog :model-value="dialogVisible" title="开发记录详情" :rows="detailRows" @update:model-value="onDialogClose">
    <template #cell-type="{ row }">
      <span class="dev-log__type">{{ row.value }}</span>
    </template>
  </RecordDetailDialog>
  </div>
</template>

<style lang="scss" scoped>
@use './devLog';
</style>