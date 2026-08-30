<script setup lang="ts">
/**
 * 操作日志页：分页查看当前用户的操作记录
 * 模板：PagePanel（header 卡片=筛选 + 数据卡片=日志列表，含不透明加载）
 * 列表：DataList 列模式（表头：时间/模块/动作/内容，列宽 5:2:3:15）
 * 交互：模块/动作中文筛选（变更即刷新，无刷新按钮）；点击行弹详情；可切换每页条数（默认 15）
 * 计数：加载中不显示数量，避免 0 → 21 闪现
 */
import { computed, onMounted, ref } from 'vue'
import PagePanel from '@/components/PagePanel/PagePanel.vue'
import DataList from '@/components/DataList/DataList.vue'
import PagePager from '@/components/PagePager/PagePager.vue'
import DateRangePicker from '@/components/DateRangePicker/DateRangePicker.vue'
import type { DataListColumn } from '@/components/DataList/DataList.vue'
import { listOperationLogs, LOG_MODULES, LOG_ACTIONS, MODULE_LABELS, ACTION_LABELS } from '@/api/operationLog'
import type { OperationLog } from '@/api/operationLog'
import { formatDateTime, formatShortTime } from '@/utils/format'

const loading = ref(false)
const logs = ref<OperationLog[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(15)

const filterModule = ref('')
const filterAction = ref('')
/** 日期范围筛选（yyyy-MM-dd） */
const dateRange = ref<[string, string] | null>(null)

/** 列定义：时间/模块/动作固定宽完整显示（不省略），仅内容列省略更多
 * 不启用 hideBelow：三个字段始终完整呈现 */
const columns: DataListColumn[] = [
  // 时间文本固定 11 个 mono 字符（MM-dd HH:mm），mono 字宽≈0.6em；列宽按 mono 实际宽度算，随响应式字号缩放，各字号下均完整显示
  { key: 'time', label: '时间', width: 'calc(7.2 * var(--cb-font-md) + 2 * var(--cb-space-3) + 4px)' },
  { key: 'module', label: '模块', width: 110 },
  { key: 'action', label: '动作', width: 90 },
  { key: 'content', label: '内容', ratio: 15 }
]

/** 加载列表 */
async function load() {
  loading.value = true
  try {
    const res = await listOperationLogs({
      module: filterModule.value || undefined,
      action: filterAction.value || undefined,
      startDate: dateRange.value?.[0] || undefined,
      endDate: dateRange.value?.[1] || undefined,
      page: page.value,
      size: size.value
    })
    logs.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

/** 筛选变更：回到第 1 页并加载 */
function onFilter() {
  page.value = 1
  load()
}

/** 点击行：打开详情弹窗 */
const detail = ref<OperationLog | null>(null)
const dialogVisible = computed(() => detail.value !== null)
function onRowClick(item: OperationLog) {
  detail.value = item
}
function onDialogClose() {
  detail.value = null
}

onMounted(load)
</script>

<template>
  <PagePanel :title="loading ? '操作日志' : `操作日志 · 共 ${total} 条`" :loading="loading">
    <template #header>
      <el-select v-model="filterModule" placeholder="模块" clearable style="width: 140px" @change="onFilter">
        <el-option v-for="m in LOG_MODULES" :key="m" :label="MODULE_LABELS[m] ?? m" :value="m" />
      </el-select>
      <el-select v-model="filterAction" placeholder="动作" clearable style="width: 140px" @change="onFilter">
        <el-option v-for="a in LOG_ACTIONS" :key="a" :label="ACTION_LABELS[a] ?? a" :value="a" />
      </el-select>
      <DateRangePicker v-model="dateRange" @change="onFilter" />
    </template>

    <DataList :items="logs" :columns="columns" :max-rows="size" clickable @row-click="onRowClick">
      <template #cell="{ item, column }">
        <span v-if="column.key === 'time'" class="num">{{ formatShortTime(item.createdAt) }}</span>
        <span v-else-if="column.key === 'module'">{{ MODULE_LABELS[item.module] ?? item.module }}</span>
        <span v-else-if="column.key === 'action'" class="op-log__action">{{ ACTION_LABELS[item.action] ?? item.action }}</span>
        <span v-else class="op-log__content">{{ item.content }}</span>
      </template>
    </DataList>

    <div class="op-log__pager">
      <PagePager
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        @change="load"
      />
    </div>
  </PagePanel>

  <!-- 行详情弹窗 -->
  <el-dialog :model-value="dialogVisible" title="操作日志详情" width="480px" @close="onDialogClose">
    <div v-if="detail" class="op-log-detail">
      <div class="op-log-detail__row">
        <span class="op-log-detail__label">时间</span>
        <span class="num">{{ formatDateTime(detail.createdAt) }}</span>
      </div>
      <div class="op-log-detail__row">
        <span class="op-log-detail__label">模块</span>
        <span>{{ MODULE_LABELS[detail.module] ?? detail.module }}</span>
      </div>
      <div class="op-log-detail__row">
        <span class="op-log-detail__label">动作</span>
        <span>{{ ACTION_LABELS[detail.action] ?? detail.action }}</span>
      </div>
      <div class="op-log-detail__row">
        <span class="op-log-detail__label">对象 ID</span>
        <span class="num">{{ detail.targetId ?? '-' }}</span>
      </div>
      <div class="op-log-detail__row">
        <span class="op-log-detail__label">内容</span>
        <span class="op-log-detail__content">{{ detail.content }}</span>
      </div>
    </div>
  </el-dialog>
</template>

<style lang="scss" scoped>
@use './operationLog';
</style>