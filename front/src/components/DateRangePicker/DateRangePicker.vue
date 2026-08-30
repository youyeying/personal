<script setup lang="ts">
/**
 * 公共组件 · DateRangePicker 日期范围选择（基于 el-date-picker）
 * - 宽屏：type="daterange" 整体范围选择（双日历面板）
 * - 窄屏（视口 < 620px，放不下 646px 双日历面板）→ 自动退化为上下两个单日选择，
 *   各自打开单日历面板（~322px），配合 preventOverflow 对齐视口，杜绝右端溢出不可见
 * - 统一 value-format=YYYY-MM-DD / range-separator=至 / 中文 placeholder
 * - 事件：update:modelValue / change 双向透传，change 供父组件触发重新筛选
 */
import { computed } from 'vue'
import { useWindowWidth } from '@/composables/useWindowWidth'

const props = withDefaults(
  defineProps<{
    modelValue: [string, string] | null
    /** 宽屏下范围输入框宽度（默认 260px，可传 'min(260px, 100%)' 等） */
    width?: string
    disabled?: boolean
    clearable?: boolean
    startPlaceholder?: string
    endPlaceholder?: string
  }>(),
  {
    width: '260px',
    disabled: false,
    clearable: true,
    startPlaceholder: '开始日期',
    endPlaceholder: '结束日期'
  }
)

const emit = defineEmits<{
  (e: 'update:modelValue', v: [string, string] | null): void
  (e: 'change', v: [string, string] | null): void
}>()

const { width: viewportWidth } = useWindowWidth()
/** 双日历面板约 646px 宽，视口过窄时拆分为两个单日选择（上下排列） */
const isNarrow = computed(() => viewportWidth.value < 620)

const inputStyle = computed(() => ({
  width: props.width,
  maxWidth: '100%',
  // 禁用 flex-grow：EP 的 .el-date-editor 默认会被 flex 容器拉伸，
  // 窄屏下会把输入框撑到超出可视区（用户反馈：右端溢出且被裁切）
  flex: '0 0 auto'
}))

/** 面板防溢出：禁止溢出视口，放不下自动翻转/对齐（单日 322px / 双日历 646px 通用） */
const popperOptions = {
  modifiers: [
    {
      name: 'preventOverflow',
      options: { boundary: 'viewport', rootBoundary: 'viewport', padding: 8, altAxis: true }
    }
  ]
}

/** 窄屏拆分模式的开始/结束值（与 modelValue 双向映射，允许单端为空） */
const startVal = computed<string>({
  get: () => props.modelValue?.[0] ?? '',
  set: (v) => push([v || null, props.modelValue?.[1] ?? null])
})
const endVal = computed<string>({
  get: () => props.modelValue?.[1] ?? '',
  set: (v) => push([props.modelValue?.[0] ?? null, v || null])
})

/** 单端变化 → 组合回 modelValue 并触发 change */
function push(v: [string | null, string | null]) {
  const both = v[0] && v[1]
  const next: [string, string] | null = both ? [v[0]!, v[1]!] : null
  emit('update:modelValue', next)
  emit('change', next)
}

/** el-date-picker 的 update 与 change 可能各自触发，统一转发避免双发 */
function onUpdate(v: [string, string] | null) {
  emit('update:modelValue', v)
}
function onChange(v: [string, string] | null) {
  emit('update:modelValue', v)
  emit('change', v)
}
</script>

<template>
  <!-- 宽屏：整体范围选择 -->
  <el-date-picker
    v-if="!isNarrow"
    :model-value="modelValue"
    type="daterange"
    value-format="YYYY-MM-DD"
    range-separator="至"
    :start-placeholder="startPlaceholder"
    :end-placeholder="endPlaceholder"
    :disabled="disabled"
    :clearable="clearable"
    :style="inputStyle"
    placement="bottom-start"
    :fallback-placements="['top-start', 'bottom-end', 'top-end']"
    :popper-options="popperOptions"
    @update:model-value="onUpdate"
    @change="onChange"
  />

  <!-- 窄屏：上下两个单日选择，各面板 preventOverflow 对齐视口 -->
  <div v-else class="date-range-picker__narrow">
    <el-date-picker
      v-model="startVal"
      type="date"
      value-format="YYYY-MM-DD"
      :placeholder="startPlaceholder"
      :disabled="disabled"
      :clearable="clearable"
      class="date-range-picker__single"
      placement="bottom-start"
      :fallback-placements="['top-start', 'bottom-end', 'top-end']"
      :popper-options="popperOptions"
    />
    <el-date-picker
      v-model="endVal"
      type="date"
      value-format="YYYY-MM-DD"
      :placeholder="endPlaceholder"
      :disabled="disabled"
      :clearable="clearable"
      class="date-range-picker__single"
      placement="bottom-start"
      :fallback-placements="['top-start', 'bottom-end', 'top-end']"
      :popper-options="popperOptions"
    />
  </div>
</template>

<style lang="scss" scoped>
@use './dateRangePicker';
</style>
