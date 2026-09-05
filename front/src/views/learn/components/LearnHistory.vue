<script setup lang="ts">
/**
 * 学习 · 历史子页组件
 * 日期范围 + 方式 + 关键词筛选 + 分页列表 + 修改弹窗（含附件增删）
 * 数据变更时 emit('changed') → 父级递增 tick，联动 记一笔/统计 刷新
 */
import { computed, markRaw, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Pencil, Trash2, Paperclip, Star } from '@lucide/vue'
import {
  listLearnRecords,
  updateLearnRecord,
  deleteLearnRecord,
  uploadNoteFile,
  deleteNoteFile
} from '@/api/learn'
import type { LearnRecord, NoteFile } from '@/api/learn'
import { confirmDelete } from '@/utils/confirm'
import PagePager from '@/components/PagePager/PagePager.vue'
import LoadingMask from '@/components/LoadingMask/LoadingMask.vue'
import BlockTitle from '@/components/BlockTitle/BlockTitle.vue'
import DateRangePicker from '@/components/DateRangePicker/DateRangePicker.vue'
import DataList from '@/components/DataList/DataList.vue'
import RecordDetailDialog from '@/components/RecordDetailDialog/RecordDetailDialog.vue'
import type { DetailRow } from '@/components/RecordDetailDialog/RecordDetailDialog.vue'
import type { DataListColumn } from '@/components/DataList/DataList.vue'
import { WAYS, durationParts } from '../learnShared'

const props = defineProps<{
  /** 跨页刷新序号：任一子页数据变更后递增，本组件据此重载列表 */
  tick: number
}>()

const emit = defineEmits<{
  (e: 'changed'): void
}>()

/* ---------- 列表 ---------- */
const listLoading = ref(false)
const records = ref<LearnRecord[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(15)
/** 日期范围筛选（服务端过滤） */
const dateRange = ref<[string, string] | null>(null)
const wayFilter = ref('')
const keyword = ref('')

/** 列定义：主题(自适应)/方式/时长/掌握/日期/操作
 * 窄屏卡片化（cardBelow 560）：容器 <560px 整表降级堆叠卡片，数据全展示（替代原 hideBelow 列隐藏） */
const columns: DataListColumn[] = [
  { key: 'title', label: '学习主题', ratio: 3 },
  { key: 'way', label: '方式', width: 84 },
  { key: 'duration', label: '时长', width: 92 },
  { key: 'mastery', label: '掌握', width: 112 },
  { key: 'date', label: '日期', width: 100 },
  { key: 'ops', label: '操作', width: 88, ops: true }
]

/** silent=true 后台静默刷新（编辑/删除后的 tick 联动），不显示加载遮罩防闪烁 */
async function loadList(silent = false) {
  if (!silent) listLoading.value = true
  try {
    const res = await listLearnRecords({
      way: wayFilter.value || undefined,
      keyword: keyword.value.trim() || undefined,
      startDate: dateRange.value?.[0],
      endDate: dateRange.value?.[1],
      page: page.value,
      size: size.value
    })
    records.value = res.records
    total.value = res.total
  } finally {
    if (!silent) listLoading.value = false
  }
}

function onFilterChange() {
  page.value = 1
  loadList()
}

function onSearch() {
  onFilterChange()
}

async function onDelete(row: LearnRecord) {
  await confirmDelete(`确定删除「${row.title}」的学习记录吗？`, () => deleteLearnRecord(row.id))
  emit('changed')
}

/* ---------- 行点击详情（只读） ---------- */
const detail = ref<LearnRecord | null>(null)
const dialogVisible = computed(() => detail.value !== null)
function onRowClick(item: LearnRecord) {
  detail.value = item
}
function onDialogClose() {
  detail.value = null
}

/** 附件访问地址（相对路径 → /uploads/** 回显） */
function fileUrl(f: NoteFile) {
  return `/uploads/${f.filePath}`
}

/** 详情弹窗行（方式/掌握/附件走自定义插槽：标签徽标/星级/文件链接） */
const detailRows = computed<DetailRow[]>(() => {
  const d = detail.value
  if (!d) return []
  return [
    { key: 'title', label: '学习主题', value: d.title },
    { key: 'way', label: '方式', value: d.way },
    { key: 'duration', label: '时长', value: `${durationParts(d.duration).h}h ${durationParts(d.duration).m}m` },
    { key: 'mastery', label: '掌握', value: null },
    { key: 'date', label: '日期', value: d.learnDate, mono: true },
    { key: 'note', label: '收获笔记', value: d.content || '—', wide: true },
    ...(d.files.length ? [{ key: 'files', label: '附件', value: null }] : [])
  ]
})

/* ---------- 修改弹窗 ---------- */
const editVisible = ref(false)
const editSaving = ref(false)
const MAX_FILES = 5
const FILE_ACCEPT = 'image/*,.pdf,.doc,.docx'
const editForm = ref<{
  id: number
  title: string
  way: string
  duration: string
  mastery: number
  learnDate: string
  content: string
  files: NoteFile[]
}>({ id: 0, title: '', way: '阅读', duration: '', mastery: 3, learnDate: '', content: '', files: [] })
/** 弹窗内新追加的附件（保存时上传） */
const editNewFiles = ref<File[]>([])

function openEdit(row: LearnRecord) {
  editForm.value = {
    id: row.id,
    title: row.title,
    way: row.way,
    duration: row.duration != null ? String(row.duration) : '',
    mastery: row.mastery ?? 3,
    learnDate: row.learnDate,
    content: row.content ?? '',
    files: [...row.files]
  }
  editNewFiles.value = []
  editVisible.value = true
}

function onPickEditFile(f: { raw?: File }) {
  if (!f.raw) return
  if (editForm.value.files.length + editNewFiles.value.length >= MAX_FILES) {
    ElMessage.warning(`最多 ${MAX_FILES} 个附件`)
    return
  }
  editNewFiles.value.push(f.raw)
}

function removeEditNewFile(index: number) {
  editNewFiles.value.splice(index, 1)
}

async function onRemoveFile(f: NoteFile) {
  await confirmDelete(`确定删除附件「${f.fileName}」吗？`, () => deleteNoteFile(f.id))
  editForm.value.files = editForm.value.files.filter((x) => x.id !== f.id)
}

async function onEditSave() {
  const t = editForm.value.title.trim()
  if (!t) {
    ElMessage.warning('请填写学习主题')
    return
  }
  const dur = editForm.value.duration ? parseFloat(editForm.value.duration) : null
  if (dur != null && (dur <= 0 || !Number.isFinite(dur))) {
    ElMessage.warning('时长需为正数（分钟）')
    return
  }
  editSaving.value = true
  try {
    await updateLearnRecord(editForm.value.id, {
      title: t,
      content: editForm.value.content.trim() || undefined,
      duration: dur,
      way: editForm.value.way,
      mastery: editForm.value.mastery,
      learnDate: editForm.value.learnDate
    })
    for (const f of editNewFiles.value) {
      await uploadNoteFile(f, editForm.value.id)
    }
    ElMessage.success('修改成功')
    editVisible.value = false
    emit('changed')
  } finally {
    editSaving.value = false
  }
}

onMounted(loadList)
// 任一子页数据变更 → 重载列表
watch(() => props.tick, () => loadList(true))
</script>

<template>
  <section class="card lr__listcard">
    <BlockTitle title="历史记录" :hint="!listLoading ? `共 ${total} 条` : undefined" />

    <!-- 筛选栏 -->
    <div class="lr__listbar">
      <DateRangePicker v-model="dateRange" @change="onFilterChange" />
      <el-select v-model="wayFilter" placeholder="全部方式" clearable style="width: 120px" @change="onFilterChange">
        <el-option v-for="w in WAYS" :key="w.key" :label="w.key" :value="w.key" />
      </el-select>
      <el-input
        v-model="keyword"
        placeholder="搜索主题 / 笔记…"
        clearable
        style="width: 220px"
        @keyup.enter="onSearch"
        @clear="onFilterChange"
      />
      <button v-if="dateRange || wayFilter || keyword" class="lr__link" type="button" @click="dateRange = null; wayFilter = ''; keyword = ''; onFilterChange()">× 清除筛选</button>
    </div>

    <!-- 列表 -->
    <template v-if="records.length">
      <DataList :items="records" :columns="columns" :max-rows="size" clickable :card-below="560" @row-click="onRowClick">
        <template #cell="{ item, column }">
          <span v-if="column.key === 'title'" class="lr__c-title">
            {{ item.title }}
            <span v-if="item.files.length" class="lr__file-count" :title="item.files.map((f) => f.fileName).join('、')">
              <component :is="markRaw(Paperclip)" :size="12" /> {{ item.files.length }}
            </span>
          </span>
          <span v-else-if="column.key === 'way'" class="lr__tag">{{ item.way }}</span>
          <span v-else-if="column.key === 'duration'" class="num">{{ durationParts(item.duration).h }}h {{ durationParts(item.duration).m }}m</span>
          <span v-else-if="column.key === 'mastery'">
            <span v-for="n in 5" :key="n" class="lr__c-star" :class="n <= (item.mastery ?? 0) ? 'is-on' : ''">
              <component :is="markRaw(Star)" :size="12" />
            </span>
          </span>
          <span v-else-if="column.key === 'date'" class="num">{{ item.learnDate }}</span>
          <span v-else-if="column.key === 'ops'">
            <el-tooltip content="修改" placement="top">
              <button class="lr__op" type="button" @click.stop="openEdit(item)"><component :is="markRaw(Pencil)" :size="14" /></button>
            </el-tooltip>
            <el-tooltip content="删除" placement="top">
              <button class="lr__op lr__op--del" type="button" @click.stop="onDelete(item)"><component :is="markRaw(Trash2)" :size="14" /></button>
            </el-tooltip>
          </span>
        </template>
      </DataList>
    </template>
    <p v-else class="lr__empty">暂无记录</p>

    <div class="lr__pager">
      <PagePager
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        @change="loadList"
      />
    </div>
    <LoadingMask :show="listLoading" :size="26" text="加载记录…" />
  </section>

  <!-- 修改弹窗 -->
  <el-dialog v-model="editVisible" title="修改学习记录" width="480px">
    <div class="lr__edit">
      <div class="lr__field">
        <label class="lr__field-label">学习主题 *</label>
        <el-input v-model="editForm.title" maxlength="100" />
      </div>
      <div class="lr__field">
        <label class="lr__field-label">学习方式</label>
        <div class="lr__waygrid">
          <button
            v-for="w in WAYS"
            :key="w.key"
            type="button"
            class="lr__way"
            :class="{ 'is-active': editForm.way === w.key }"
            @click="editForm.way = w.key"
          >
            <component :is="w.icon" :size="18" />
            <span>{{ w.key }}</span>
          </button>
        </div>
      </div>
      <div class="lr__row3">
        <div class="lr__field">
          <label class="lr__field-label">时长（分钟）</label>
          <el-input v-model="editForm.duration" inputmode="numeric" />
        </div>
        <div class="lr__field">
          <label class="lr__field-label">掌握程度</label>
          <div class="lr__stars">
            <button
              v-for="n in 5"
              :key="n"
              type="button"
              class="lr__star"
              :class="{ 'is-on': editForm.mastery >= n }"
              @click="editForm.mastery = n"
            >
              <component :is="markRaw(Star)" :size="18" />
            </button>
          </div>
        </div>
        <div class="lr__field">
          <label class="lr__field-label">日期</label>
          <el-date-picker v-model="editForm.learnDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </div>
      </div>
      <div class="lr__field">
        <label class="lr__field-label">收获笔记</label>
        <el-input v-model="editForm.content" type="textarea" :rows="3" maxlength="2000" />
      </div>
      <div class="lr__field">
        <label class="lr__field-label">附件</label>
        <!-- 已有附件 -->
        <div v-if="editForm.files.length" class="lr__files">
          <span v-for="f in editForm.files" :key="f.id" class="lr__file">
            <a class="lr__file-name" :href="fileUrl(f)" target="_blank" rel="noopener">{{ f.fileName }}</a>
            <button class="lr__file-rm" type="button" @click="onRemoveFile(f)">×</button>
          </span>
        </div>
        <!-- 新增附件 -->
        <el-upload
          :auto-upload="false"
          :show-file-list="false"
          :accept="FILE_ACCEPT"
          :on-change="onPickEditFile"
        >
          <button class="lr__file-btn" type="button">
            <component :is="markRaw(Paperclip)" :size="14" /> 添加附件
          </button>
        </el-upload>
        <div v-if="editNewFiles.length" class="lr__files">
          <span v-for="(f, i) in editNewFiles" :key="i" class="lr__file">
            <span class="lr__file-name">{{ f.name }}</span>
            <button class="lr__file-rm" type="button" @click="removeEditNewFile(i)">×</button>
          </span>
        </div>
      </div>
    </div>
    <template #footer>
      <el-button @click="editVisible = false">取消</el-button>
      <el-button type="primary" :loading="editSaving" @click="onEditSave">保存</el-button>
    </template>
  </el-dialog>

  <!-- 行详情（只读） -->
  <RecordDetailDialog :model-value="dialogVisible" title="学习记录详情" :rows="detailRows" @update:model-value="onDialogClose">
    <template #cell-way>
      <span class="lr__tag">{{ detail?.way }}</span>
    </template>
    <template #cell-mastery>
      <span v-if="detail">
        <span v-for="n in 5" :key="n" class="lr__c-star" :class="n <= (detail.mastery ?? 0) ? 'is-on' : ''">
          <component :is="markRaw(Star)" :size="14" />
        </span>
      </span>
    </template>
    <template #cell-files>
      <span v-if="detail" class="lr__dfiles">
        <a
          v-for="f in detail.files"
          :key="f.id"
          class="lr__file-name"
          :href="fileUrl(f)"
          target="_blank"
          rel="noopener"
        >{{ f.fileName }}</a>
      </span>
    </template>
  </RecordDetailDialog>
</template>

<style lang="scss" scoped>
@use '../learn';
</style>