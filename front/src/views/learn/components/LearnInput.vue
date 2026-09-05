<script setup lang="ts">
/**
 * 学习 · 记一笔子页组件
 * 主题 + 方式宫格 + 时长 + 掌握星级 + 日期 + 笔记 + 附件 + 最近记录速览
 * 数据变更时 emit('changed') → 父级递增 tick，联动 统计/历史 刷新
 */
import { markRaw, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { BookOpen, Paperclip, Star, Timer, NotebookPen, Sparkles } from '@lucide/vue'
import {
  listLearnRecords,
  createLearnRecord,
  uploadNoteFile
} from '@/api/learn'
import type { LearnRecord } from '@/api/learn'
import { formatDate } from '@/utils/format'
import LoadingMask from '@/components/LoadingMask/LoadingMask.vue'
import BlockTitle from '@/components/BlockTitle/BlockTitle.vue'
import { WAYS, durationParts } from '../learnShared'

const props = defineProps<{
  /** 跨页刷新序号：任一子页数据变更后递增，本组件据此重载最近记录 */
  tick: number
}>()

const emit = defineEmits<{
  (e: 'changed'): void
  (e: 'navigate', tab: string): void
}>()

/* ---------- 表单 ---------- */
const title = ref('')
const way = ref('阅读')
const duration = ref('')
const mastery = ref(3)
const learnDate = ref(formatDate(new Date()))
const content = ref('')
const pendingFiles = ref<File[]>([])
const saving = ref(false)

/** 附件最多 5 个，允许类型 */
const MAX_FILES = 5
const FILE_ACCEPT = 'image/*,.pdf,.doc,.docx'

/** 校验并保存：先建记录，再逐个上传附件（附件需要记录 id） */
async function onSave() {
  const t = title.value.trim()
  if (!t) {
    ElMessage.warning('请填写学习主题')
    return
  }
  const dur = duration.value ? parseFloat(duration.value) : null
  if (dur != null && (dur <= 0 || !Number.isFinite(dur))) {
    ElMessage.warning('时长需为正数（分钟）')
    return
  }
  saving.value = true
  try {
    const rec = await createLearnRecord({
      title: t,
      content: content.value.trim() || undefined,
      duration: dur,
      way: way.value,
      mastery: mastery.value,
      learnDate: learnDate.value
    })
    for (const f of pendingFiles.value) {
      await uploadNoteFile(f, rec.id)
    }
    ElMessage.success('保存成功')
    title.value = ''
    duration.value = ''
    mastery.value = 3
    content.value = ''
    pendingFiles.value = []
    emit('changed')
  } finally {
    saving.value = false
  }
}

/** 选择附件（el-upload on-change） */
function onPickFile(f: { raw?: File }) {
  if (!f.raw) return
  if (pendingFiles.value.length >= MAX_FILES) {
    ElMessage.warning(`最多 ${MAX_FILES} 个附件`)
    return
  }
  pendingFiles.value.push(f.raw)
}

function removePendingFile(index: number) {
  pendingFiles.value.splice(index, 1)
}

/* ---------- 最近记录速览 ---------- */
const recentLoading = ref(false)
const recentRecords = ref<LearnRecord[]>([])
async function loadRecent() {
  recentLoading.value = true
  try {
    const res = await listLearnRecords({ page: 1, size: 5 })
    recentRecords.value = res.records
  } finally {
    recentLoading.value = false
  }
}

onMounted(loadRecent)
// 任一子页数据变更 → 重载最近记录
watch(() => props.tick, loadRecent)
</script>

<template>
  <section class="card lr__entry">
    <BlockTitle title="记录今天的学习" hint="主题必填，其余按需填写" />
    <div class="lr__entry-body">
      <!-- 表单 -->
      <div class="lr__form">
        <div class="lr__field">
          <label class="lr__field-label"><component :is="markRaw(BookOpen)" :size="13" /> 学习主题</label>
          <input v-model="title" class="lr__title-input" maxlength="100" placeholder="例如：Vue3 组合式 API 实战" />
        </div>
        <div class="lr__field">
          <label class="lr__field-label">学习方式</label>
          <div class="lr__waygrid">
            <button
              v-for="w in WAYS"
              :key="w.key"
              type="button"
              class="lr__way"
              :class="{ 'is-active': way === w.key }"
              @click="way = w.key"
            >
              <component :is="w.icon" :size="20" />
              <span>{{ w.key }}</span>
            </button>
          </div>
        </div>
        <div class="lr__row3">
          <div class="lr__field">
            <label class="lr__field-label"><component :is="markRaw(Timer)" :size="13" /> 时长（分钟）</label>
            <div class="lr__amount">
              <input v-model="duration" class="lr__amount-input" inputmode="numeric" placeholder="30" />
              <span class="lr__amount-unit">min</span>
            </div>
          </div>
          <div class="lr__field">
            <label class="lr__field-label">掌握程度</label>
            <div class="lr__stars">
              <button
                v-for="n in 5"
                :key="n"
                type="button"
                class="lr__star"
                :class="{ 'is-on': mastery >= n }"
                :aria-label="`${n} 星`"
                @click="mastery = n"
              >
                <component :is="markRaw(Star)" :size="20" />
              </button>
            </div>
          </div>
          <div class="lr__field">
            <label class="lr__field-label">日期</label>
            <el-date-picker v-model="learnDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
          </div>
        </div>
        <div class="lr__field">
          <label class="lr__field-label"><component :is="markRaw(NotebookPen)" :size="13" /> 收获笔记</label>
          <el-input v-model="content" type="textarea" :rows="3" maxlength="2000" placeholder="记下今天学到的关键点…" />
        </div>
        <div class="lr__field">
          <label class="lr__field-label"><component :is="markRaw(Paperclip)" :size="13" /> 附件</label>
          <el-upload
            :auto-upload="false"
            :show-file-list="false"
            :accept="FILE_ACCEPT"
            :on-change="onPickFile"
          >
            <button class="lr__file-btn" type="button">
              <component :is="markRaw(Paperclip)" :size="15" /> 选择附件（图片 / PDF / doc，最多 5 个）
            </button>
          </el-upload>
          <div v-if="pendingFiles.length" class="lr__files">
            <span v-for="(f, i) in pendingFiles" :key="i" class="lr__file">
              <span class="lr__file-name">{{ f.name }}</span>
              <button class="lr__file-rm" type="button" @click="removePendingFile(i)">×</button>
            </span>
          </div>
        </div>
      </div>

      <!-- 最近记录速览 -->
      <div class="lr__recent">
        <BlockTitle title="最近记录" hint="最新 5 条">
          <template #aside>
            <button class="lr__link" type="button" @click="emit('navigate', 'history')">查看全部</button>
          </template>
        </BlockTitle>
        <template v-if="recentRecords.length">
          <div v-for="r in recentRecords" :key="r.id" class="lr__row">
            <span class="lr__row-icon">
              <component :is="WAYS.find((w) => w.key === r.way)?.icon ?? Sparkles" :size="15" />
            </span>
            <div class="lr__row-main">
              <div class="lr__row-title">{{ r.title }}</div>
              <div class="lr__row-sub">
                {{ r.way }} · 掌握 {{ r.mastery ?? '-' }}
                <span v-if="r.files.length" class="lr__row-sub-files">
                  <component :is="markRaw(Paperclip)" :size="11" /> {{ r.files.length }}
                </span>
              </div>
            </div>
            <span class="lr__tag">{{ r.way }}</span>
            <span class="lr__row-min num">{{ durationParts(r.duration).h }}<i>h</i> {{ durationParts(r.duration).m }}<i>m</i></span>
          </div>
        </template>
        <p v-else-if="!recentLoading" class="lr__empty">还没有学习记录，先在上面记一笔吧</p>
        <LoadingMask :show="recentLoading" :size="22" text="加载最近记录…" />
      </div>
    </div>
    <el-button type="primary" size="large" :loading="saving" class="lr__save" @click="onSave">保存记录</el-button>
    <p class="lr__tip">保存后清空表单，可连续补记多天。</p>
  </section>
</template>

<style lang="scss" scoped>
@use '../learn';
</style>