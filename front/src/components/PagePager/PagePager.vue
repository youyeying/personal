<script setup lang="ts">
/**
 * 公共组件 · PagePager 分页（多页面统一的分页条）
 * 封装 el-pagination 常见配置（总条数/每页 15/30/50/后台分页与客户端分页通用）
 * - 与父组件双向绑定 current-page / page-size
 * - 监听 change 事件：换页或换每页条数时通知父组件刷新
 */
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
  <div class="pager">
    <el-pagination
      :current-page="props.currentPage"
      :page-size="props.pageSize"
      :page-sizes="props.pageSizes"
      :pager-count="props.pagerCount"
      :total="props.total"
      :layout="props.showTotal ? 'total, sizes, prev, pager, next' : 'sizes, prev, pager, next'"
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