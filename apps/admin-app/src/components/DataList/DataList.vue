<script lang="ts">
/** 列定义：key/label 必填；ratio 按比例分配宽度（如 5:2:3:15），width 固定宽优先（number→px，string→原样如 '11ch'） */
export interface DataListColumn {
  key: string
  label: string
  ratio?: number
  width?: number | string
  /** 视口宽度小于该值（px）时隐藏此列（用于窄屏下隐藏次要列，详情看行弹窗） */
  hideBelow?: number
  /** 是否归类到「操作」区：卡片降级模式下此列内容放入卡片底部（如 编辑/删除 按钮） */
  ops?: boolean
}
</script>

<script setup lang="ts" generic="T">
/**
 * 公共组件 · DataList 数据列表（自用，泛型 T=行数据类型）
 * - 默认高度为 15 行；行高/行数均可由父组件传入
 * - 传 columns 时：首行渲染表头（label），数据行按列渲染（cell 插槽），列宽按 ratio 比例
 * - 不传 columns 时：回退为默认插槽单行渲染
 * - 只有「有数据的行」有边框；空行不显示边框；无滚动、不换行、超长省略
 * - clickable=true 时点击数据行触发 row-click（父组件弹详情用）
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useWindowWidth } from '@/composables/useWindowWidth'

const props = withDefaults(
  defineProps<{
    /** 数据行数组（父组件传入） */
    items: T[]
    /** 列定义（传了则渲染表头 + 按列渲染） */
    columns?: DataListColumn[]
    /** 行高（px） */
    rowHeight?: number
    /** 可见数据行数（默认 15 行） */
    maxRows?: number
    /** 数据行是否可点击（触发 row-click） */
    clickable?: boolean
    /** 容器宽度小于该值（px）时，表格降级为堆叠卡片（传了才启用卡片化） */
    cardBelow?: number
  }>(),
  {
    rowHeight: 40,
    maxRows: 15,
    clickable: false,
    columns: undefined,
    cardBelow: undefined
  }
)

const emit = defineEmits<{
  (e: 'row-click', item: T): void
}>()

defineSlots<{
  /** columns 模式下的单元格渲染 */
  cell: (props: { item: T; column: DataListColumn; index: number }) => unknown
  /** 非 columns 模式下的整行渲染 */
  default: (props: { item: T; index: number }) => unknown
}>()

/** 响应式视口宽度（hideBelow 列隐藏依据） */
const { width: viewportWidth } = useWindowWidth()

/** 组件根节点实际宽度（卡片降级断点按「容器宽度」而非视口判断） */
const rootEl = ref<HTMLElement | null>(null)
const listWidth = ref(0)
let ro: ResizeObserver | null = null

function measure() {
  if (rootEl.value) listWidth.value = rootEl.value.clientWidth
}
onMounted(() => {
  if (props.cardBelow !== undefined && typeof ResizeObserver !== 'undefined') {
    ro = new ResizeObserver(measure)
    ro.observe(rootEl.value!)
  }
  measure()
})
onBeforeUnmount(() => ro?.disconnect())

/** 是否启用卡片降级（cardBelow 已设且容器过窄） */
const isCard = computed(() => props.cardBelow !== undefined && listWidth.value > 0 && listWidth.value < props.cardBelow)

/** 可见列：卡片模式展示全部列（不因 hideBelow 隐藏）；表格模式过滤 hideBelow */
const visibleColumns = computed<DataListColumn[]>(() => {
  const cols = props.columns ?? []
  return isCard.value ? [...cols] : cols.filter((c) => !c.hideBelow || viewportWidth.value >= c.hideBelow)
})

/* ---------- 卡片模式信息组织：按列定义推导 ---------- */
/** 卡片头部列（第一列，主字段，价值放大展示） */
const cardHeadCol = computed<DataListColumn | undefined>(() => visibleColumns.value[0])
/** 操作列（ops:true 或 key==='ops'，放入卡片底部） */
const cardOpsCol = computed<DataListColumn | undefined>(() =>
  visibleColumns.value.find((c) => c.ops || c.key === 'ops')
)
/** 卡片信息区列（去除头部列与操作列，按 label→value 对展示） */
const cardInfoCols = computed<DataListColumn[]>(() => {
  const cols = visibleColumns.value.filter((c) => c !== cardOpsCol.value && c !== cardHeadCol.value)
  return cols
})

/** 总高度 = 表头行（如有）+ maxRows 数据行 */
const listHeight = computed(() => {
  const headRows = visibleColumns.value.length ? 1 : 0
  return (props.maxRows + headRows) * props.rowHeight + 'px'
})

/**
 * 列模式下行容器用 grid（而非 flex）：
 * 弹性列 minmax(0, Nfr) 的最小固有宽度为 0，超长 nowrap 文本不参与祖先 fit-content/min-content 测量
 * （flex 下 min-width:0 只影响实际收缩，min-content 贡献仍为全文宽，会把上层 min-width:fit-content 的容器撑爆）
 */
const gridTemplate = computed(() =>
  visibleColumns.value
    .map((c) => (c.width ? (typeof c.width === 'string' ? c.width : `${c.width}px`) : `minmax(0, ${c.ratio ?? 1}fr)`))
    .join(' ')
)
/** 行容器样式：列模式=grid 模板；非列模式=flex（整行插槽） */
function rowStyle(): Record<string, string> {
  return visibleColumns.value.length
    ? { display: 'grid', gridTemplateColumns: gridTemplate.value }
    : { display: 'flex' }
}
</script>

<template>
  <div ref="rootEl" class="data-list" :style="isCard ? undefined : { height: listHeight }">
    <!-- ============ 卡片降级模式：一行为一张卡片 ============ -->
    <template v-if="isCard">
      <div v-for="(item, idx) in items" :key="idx" class="data-list__card">
        <div class="data-list__card-main" :class="clickable ? 'data-list__card-main--clickable' : ''" @click="clickable && emit('row-click', item)">
          <!-- 卡片头部：第一列主字段 -->
          <div v-if="cardHeadCol" class="data-list__card-head">
            <span class="data-list__card-head-label">{{ cardHeadCol.label }}</span>
            <span class="data-list__card-head-value num">
              <slot name="cell" :item="item" :column="cardHeadCol" :index="idx" />
            </span>
          </div>
          <!-- 卡片信息区：其余非操作列 label→value 对 -->
          <div v-if="cardInfoCols.length" class="data-list__card-grid">
            <div v-for="col in cardInfoCols" :key="col.key" class="data-list__card-pair">
              <span class="data-list__card-label">{{ col.label }}</span>
              <span class="data-list__card-value"><slot name="cell" :item="item" :column="col" :index="idx" /></span>
            </div>
          </div>
        </div>
        <!-- 卡片底部：操作列 -->
        <div v-if="cardOpsCol" class="data-list__card-foot" @click.stop>
          <slot name="cell" :item="item" :column="cardOpsCol" :index="idx" />
        </div>
      </div>
    </template>

    <!-- ============ 表格模式（默认） ============ -->
    <template v-else>
      <!-- 表头行 -->
      <div v-if="visibleColumns.length" class="data-list__head" :style="{ height: rowHeight + 'px', ...rowStyle() }">
        <div v-for="col in visibleColumns" :key="col.key" class="data-list__head-cell">
          {{ col.label }}
        </div>
      </div>

      <!-- 数据行 -->
      <div
        v-for="i in maxRows"
        :key="i"
        class="data-list__row"
        :style="{ height: rowHeight + 'px' }"
      >
        <div
          v-if="items[i - 1] !== undefined && items[i - 1] !== null"
          class="data-list__cell"
          :class="{
            'data-list__cell--clickable': clickable,
            'data-list__cell--first': !visibleColumns.length && i === 1
          }"
          :style="rowStyle()"
          @click="clickable && emit('row-click', items[i - 1])"
        >
          <template v-if="visibleColumns.length">
            <div v-for="col in visibleColumns" :key="col.key" class="data-list__col">
              <slot name="cell" :item="items[i - 1]" :column="col" :index="i - 1" />
            </div>
          </template>
          <slot v-else :item="items[i - 1]" :index="i - 1" />
        </div>
      </div>
    </template>
  </div>
</template>

<style lang="scss" scoped>
@use './dataList';
</style>