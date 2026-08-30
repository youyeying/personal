<script setup lang="ts">
/**
 * 每日总结 · 班表编辑弹窗（展示 + 交互；解析/保存由父级处理）
 * - draft：逐日班次草稿（v-model 经对象元素可变，父级持有同源数组）
 * - 上传播入文件 emit('import', rawFile)；确认 emit('save')
 */
import { Upload } from '@element-plus/icons-vue'
import { SHIFT_META } from '@/api/shift'

export interface ShiftDraftRow {
  date: string
  shiftName: string
}

defineProps<{
  show: boolean
  cycleLabel: string
  draft: ShiftDraftRow[]
  saving: boolean
}>()

const emit = defineEmits<{
  (e: 'update:show', v: boolean): void
  (e: 'import', raw: File): void
  (e: 'save'): void
}>()

function onPick(uploadFile: { raw?: File }) {
  if (uploadFile.raw) emit('import', uploadFile.raw)
}
</script>

<template>
  <el-dialog :model-value="show" :title="`编辑班表 · 班期 ${cycleLabel}`" width="560px" @update:model-value="(v: boolean) => emit('update:show', v)">
    <div class="daily-note__shift-import">
      <span class="daily-note__shift-import-label">导入班表文件：</span>
      <el-upload
        :auto-upload="false"
        accept=".xlsx,.xls"
        :show-file-list="false"
        :on-change="onPick"
      >
        <el-button size="small" :icon="Upload">选择 xlsx 文件</el-button>
      </el-upload>
      <span class="daily-note__shift-import-tip">格式：第1行日期、第2行班次代码（P7A1/P7C1/P10A1/P12A1/P15A1/P17A1/P23A1/休）</span>
    </div>
    <div class="daily-note__shift-grid">
      <div v-for="r in draft" :key="r.date" class="daily-note__shift-row">
        <span class="daily-note__shift-date num">{{ r.date.slice(5) }}</span>
        <el-select v-model="r.shiftName" filterable allow-create size="small">
          <el-option
            v-for="s in SHIFT_META"
            :key="s.value"
            :label="`${s.value}`"
            :value="s.value"
          >
            <span class="daily-note__shift-opt">
              <em class="daily-note__shift-dot" :style="{ background: s.color }"></em>
              {{ s.value }}
            </span>
          </el-option>
        </el-select>
      </div>
    </div>
    <template #footer>
      <el-button @click="emit('update:show', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="emit('save')">保存班表</el-button>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
@use '../dailyNote';
</style>