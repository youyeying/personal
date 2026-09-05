<script setup lang="ts">
/**
 * 公共组件 · RecordHeatmap 记录热力图（V2：GitHub 式格点，把「记录」变成可看见的坚持）
 * - rows：每行一个维度（如 饮食/锻炼），cells 按日期升序（level 0-4 五档，0=无记录）
 * - color：格点主题色（默认 --cb-mod 模块主色，色阶 color-mix 派生深浅）
 * - cells.tip：hover 时原生 title 提示（如「09-05 · 3 条记录」）
 * - 窄屏 ≤560px 自动截断为最近 21 格（样式层处理）
 */
export interface HeatmapCell {
  date: string
  /** 强度档 0-4（0=无记录） */
  level: number
  /** hover 提示 */
  tip?: string
}
export interface HeatmapRow {
  label: string
  cells: HeatmapCell[]
}

withDefaults(
  defineProps<{
    rows: HeatmapRow[]
    color?: string
    /** 是否显示「少→多」图例 */
    showLegend?: boolean
    /** 图例/统计小字（如「连续 4 天 · 本月 21 天」） */
    legendText?: string
  }>(),
  { color: 'var(--cb-mod)', showLegend: true, legendText: '' }
)
</script>

<template>
  <div class="hm">
    <div v-for="row in rows" :key="row.label" class="hm__row">
      <span class="hm__label">{{ row.label }}</span>
      <div class="hm__cells">
        <span
          v-for="c in row.cells" :key="c.date"
          class="hm__cell" :class="`hm__cell--l${c.level}`"
          :style="{ '--hm-color': color }"
          :title="c.tip || c.date"
        ></span>
      </div>
    </div>
    <div v-if="showLegend || legendText" class="hm__legend">
      <template v-if="legendText">{{ legendText }}</template>
      <template v-if="showLegend">
        <span class="hm__legend-t">少</span>
        <span class="hm__cell hm__cell--l0" :style="{ '--hm-color': color }"></span>
        <span class="hm__cell hm__cell--l1" :style="{ '--hm-color': color }"></span>
        <span class="hm__cell hm__cell--l2" :style="{ '--hm-color': color }"></span>
        <span class="hm__cell hm__cell--l3" :style="{ '--hm-color': color }"></span>
        <span class="hm__cell hm__cell--l4" :style="{ '--hm-color': color }"></span>
        <span class="hm__legend-t">多</span>
      </template>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use './recordHeatmap';
</style>
