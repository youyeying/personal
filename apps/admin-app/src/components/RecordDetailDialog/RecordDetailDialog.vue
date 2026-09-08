<script setup lang="ts">
/**
 * 公共 · 记录详情弹窗（只读）
 * 「列表点击行 → 查看详情」场景的公共封装，替代各页面重复的 el-dialog 模板：
 * - rows：key-value 行（label 固定 72px 副色；value 支持等宽/长文，空值显 '—'）
 * - 自定义行：#cell-{row.key} 具名插槽（星级、附件链接、类型徽标等复杂渲染）
 * - footer 默认「关闭」，可用 #footer 覆盖
 *
 * 用法：<RecordDetailDialog :model-value="visible" :title="'xx详情'" :rows="rows" @update:model-value="close" />
 */
export interface DetailRow {
  /** 唯一 key（自定义行用 #cell-{key} 插槽） */
  key: string
  label: string
  value?: string | number | null
  /** 等宽字体（时间/编号/金额/日期） */
  mono?: boolean
  /** 长文内容（pre-wrap 换行） */
  wide?: boolean
}

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    title: string
    rows: DetailRow[]
    width?: string
  }>(),
  { width: '480px' }
)

const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void }>()

/** 关闭（右上角 X / 遮罩 / footer 关闭按钮） */
function onClose() {
  emit('update:modelValue', false)
}
</script>

<template>
  <el-dialog :model-value="props.modelValue" :title="props.title" :width="props.width" @close="onClose">
    <div class="rdd">
      <template v-for="row in props.rows" :key="row.key">
        <div class="rdd__row" :class="{ 'rdd__row--note': row.wide }">
          <span class="rdd__label">{{ row.label }}</span>
          <!-- 允许父级自定义某一行渲染 -->
          <slot :name="`cell-${row.key}`" :row="row">
            <span v-if="row.mono" class="rdd__value num">{{ row.value ?? '—' }}</span>
            <span v-else-if="row.wide" class="rdd__value rdd__value--note">{{ row.value ?? '—' }}</span>
            <span v-else class="rdd__value">{{ row.value ?? '—' }}</span>
          </slot>
        </div>
      </template>
    </div>
    <template v-if="$slots.footer" #footer>
      <slot name="footer" />
    </template>
    <template v-else #footer>
      <el-button @click="onClose">关闭</el-button>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
@use './recordDetailDialog';
</style>