<script setup lang="ts">
/**
 * 公共组件 · PagePager 分页（多页面统一的分页条）
 * 封装 el-pagination 常见配置（总条数/每页 15/30/50/后台分页与客户端分页通用）
 * - 与父组件双向绑定 current-page / page-size
 * - 监听 change 事件：换页或换每页条数时通知父组件刷新
 * - 容器自适应（v2.1.0）：ResizeObserver 监听父级容器宽度，窄屏收缩 layout
 *   （≥560 完整 ｜ 340~560 去总条数 ｜ <340 仅翻页），解决窄屏换行
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

const props = withDefaults(
  defineProps<{
    /** 总条数 */
    total: number
    /** 当前页 */
    currentPage: number
    /** 每页条数 */
    pageSize: number
    /** 每页条数选项 */
    pageSizes?: number[]
    /** 是否显示总条数（前端分页时可不显示，例如 dev-log） */
    showTotal?: boolean
    /** 页码按钮显示数（el-pagination 最小值 5，需奇数） */
    pagerCount?: number
  }>(),
  {
    pageSizes: () => [15, 30, 50],
    showTotal: true,
    pagerCount: 5
  }
)

const emit = defineEmits<{
  (e: 'update:currentPage', val: number): void
  (e: 'update:pageSize', val: number): void
  (e: 'change'): void
}>()

// ── 窄屏自适应：观察「父级容器」宽度（父级常为 flex+右对齐会把自身 shrink 到内容宽，
//    观察自身会形成"收缩→测得更窄→再收缩"死循环，父级宽度才反映真实可用空间），动态收缩 layout ──
const rootRef = ref<HTMLElement | null>(null)
const boxWidth = ref(Infinity)
let ro: ResizeObserver | null = null

onMounted(() => {
  // 观察父级容器；无父级时兜底观察自身
  const target = rootRef.value?.parentElement ?? rootRef.value
  if (target) {
    ro = new ResizeObserver((entries) => {
      for (const entry of entries) boxWidth.value = entry.contentRect.width
    })
    ro.observe(target)
  }
})
onBeforeUnmount(() => ro?.disconnect())

const layout = computed(() => {
  if (boxWidth.value < 340) return 'prev, pager, next'
  if (boxWidth.value < 560) return 'sizes, prev, pager, next'
  return props.showTotal ? 'total, sizes, prev, pager, next' : 'sizes, prev, pager, next'
})

function onCurrentChange(val: number) {
  emit('update:currentPage', val)
  emit('change')
}

function onSizeChange(val: number) {
  emit('update:pageSize', val)
  emit('update:currentPage', 1)
  emit('change')
}
</script>

<template>
  <div ref="rootRef" class="pager">
    <el-pagination
      :current-page="props.currentPage"
      :page-size="props.pageSize"
      :page-sizes="props.pageSizes"
      :pager-count="props.pagerCount"
      :total="props.total"
      :layout="layout"
      size="small"
      background
      @current-change="onCurrentChange"
      @size-change="onSizeChange"
    />
  </div>
</template>

<style lang="scss" scoped>
@use './pagePager';
</style>