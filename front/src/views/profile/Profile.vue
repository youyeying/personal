<script setup lang="ts">
/**
 * 个人中心页（views/profile）v1.25.0 重做版
 * 交互模式：概要卡（浏览） + 设置列表（点击项 → 弹窗内编辑保存）
 * - 设置项：更换头像 / 修改昵称 / 修改手机号(二期锁定) / 设置目标体重 / 修改密码
 * - 手机号与用户名本期不可改；忘记密码仅预留二期设计
 * - 改密后进入一个月冷却；改密成功退出登录重新登录
 */
import { computed, markRaw, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { User, LogOut, Camera, PenLine, Phone, Lock, Target, ShieldCheck, ChevronRight, CalendarRange, Download } from '@lucide/vue'
import { getMe, updateProfileApi, changePasswordApi, uploadAvatar, logout as logoutApi } from '@/api/auth'
import { listWeightRecords } from '@/api/health'
import { EXPORT_MODULES, exportModuleCsv, exportAllJson } from '@/api/export'
import { useUserStore, type UserInfo } from '@/store/user'
import { formatDate } from '@/utils/format'
import BlockTitle from '@/components/BlockTitle/BlockTitle.vue'

const router = useRouter()
const userStore = useUserStore()

/* ================= 用户概要 ================= */
const user = computed<UserInfo | null>(() => userStore.userInfo)
const avatarUrl = computed<string | null>(() => userStore.avatarUrl)
/** 手机号脱敏：138****5678 */
function maskPhone(p: string | undefined | null): string {
  if (!p || p.length < 7) return p ?? '-'
  return `${p.slice(0, 3)}****${p.slice(-4)}`
}

/** 进入页面刷新用户信息（拿到最新 nickname/targetWeight/passwordUpdatedAt） */
onMounted(async () => {
  try {
    const res = await getMe()
    userStore.setUserInfo(res.userInfo as never)
  } catch {
    /* 静默：requestApi 已弹错 */
  }
})

/** 退出登录：撤销服务端会话 + 清 Cookie，再清本地登录态 → 登录页 */
function onLogout() {
  ElMessageBox.confirm('确定退出当前账号吗？', '退出登录', { type: 'warning' })
    .then(async () => {
      try {
        await logoutApi()
      } catch {
        /* 忽略：仍执行本地登出 */
      }
      userStore.clearLocalAuth()
      router.push('/login')
    })
    .catch(() => {})
}

/* ================= 设置项 ================= */
/** 目标体重摘要（未设置时高亮提示） */
const targetText = computed(() =>
  user.value?.targetWeight != null ? `${user.value.targetWeight} kg` : '未设置'
)
const targetUnset = computed(() => user.value?.targetWeight == null)

/** 改密冷却摘要（设置行 + 弹窗共用） */
const cooldown = computed<{
  allowed: boolean
  first: boolean
  lastText: string
  daysLeft: number
}>(() => {
  const t = user.value?.passwordUpdatedAt
  if (!t) return { allowed: true, first: true, lastText: '尚未修改过密码', daysLeft: 0 }
  const last = new Date(t)
  const next = new Date(last.getFullYear(), last.getMonth() + 1, last.getDate())
  const diffMs = next.getTime() - Date.now()
  const daysLeft = Math.ceil(diffMs / 86400000)
  return {
    allowed: diffMs <= 0,
    first: false,
    lastText: formatDate(t),
    daysLeft: Math.max(daysLeft, 0)
  }
})
const pwdDesc = computed(() =>
  cooldown.value.allowed
    ? cooldown.value.first
      ? '首次修改免限'
      : `上次修改：${cooldown.value.lastText}`
    : `冷却中 · 还剩 ${cooldown.value.daysLeft} 天`
)

/* ================= 弹窗：目标体重 ================= */
const targetDialog = ref(false)
const targetInput = ref('')
const savingTarget = ref(false)
/** 当前最新体重（打开弹窗时拉取，用于展示「距目标」） */
const latestWeight = ref<number | null>(null)
const loadingWeight = ref(false)

function openTargetDialog() {
  targetInput.value = user.value?.targetWeight != null ? String(user.value.targetWeight) : ''
  targetDialog.value = true
  // 拉取最新体重（静默失败，不影响主流程）
  loadingWeight.value = true
  listWeightRecords({ page: 1, size: 1 })
    .then((res) => {
      const w = res.records?.[0]?.weight
      latestWeight.value = w != null ? Number(w) : null
    })
    .catch(() => {
      latestWeight.value = null
    })
    .finally(() => {
      loadingWeight.value = false
    })
}

async function onSaveTarget() {
  const v = targetInput.value.trim()
  if (!v) {
    ElMessage.warning('请输入目标体重，或点击下方「清除目标」')
    return
  }
  const n = parseFloat(v)
  if (!n || n <= 0) {
    ElMessage.warning('目标体重需为正数')
    return
  }
  savingTarget.value = true
  try {
    const res = await updateProfileApi({ targetWeight: n })
    userStore.setUserInfo(res.userInfo as never)
    targetDialog.value = false
    ElMessage.success('目标体重已保存')
  } finally {
    savingTarget.value = false
  }
}

async function onClearTarget() {
  try {
    await ElMessageBox.confirm('确定清除目标体重吗？清除后首页减重进度将不显示。', '清除目标', {
      type: 'warning'
    })
  } catch {
    return
  }
  savingTarget.value = true
  try {
    const res = await updateProfileApi({ clearTargetWeight: true })
    userStore.setUserInfo(res.userInfo as never)
    targetDialog.value = false
    ElMessage.success('已清除目标体重')
  } finally {
    savingTarget.value = false
  }
}

/* ================= 弹窗：身体数据 + 基础代谢（Katch-McArdle） ================= */
const bodyDialog = ref(false)
const bodyInput = ref({
  age: '',
  height: '',
  gender: 'male' as string
})
const savingBody = ref(false)

/** 身体数据摘要 */
const bodyText = computed(() => {
  const u = user.value
  const parts: string[] = []
  if (u?.age != null) parts.push(`${u.age}岁`)
  if (u?.height != null) parts.push(`${u.height}cm`)
  if (u?.gender === 'male') parts.push('男')
  if (u?.gender === 'female') parts.push('女')
  return parts.length ? parts.join(' · ') : '未设置'
})

function openBodyDialog() {
  bodyInput.value = {
    age: user.value?.age != null ? String(user.value.age) : '',
    height: user.value?.height != null ? String(user.value.height) : '',
    gender: user.value?.gender ?? 'male'
  }
  bodyDialog.value = true
}

async function onSaveBody() {
  const age = bodyInput.value.age ? Number(bodyInput.value.age) : null
  if (age != null && (age < 10 || age > 120)) {
    ElMessage.warning('年龄需在 10-120 之间')
    return
  }
  const height = bodyInput.value.height ? Number(bodyInput.value.height) : null
  if (height != null && (height < 50 || height > 250)) {
    ElMessage.warning('身高需在 50-250cm 之间')
    return
  }
  savingBody.value = true
  try {
    const res = await updateProfileApi({
      age: age ?? undefined,
      height: height ?? undefined,
      gender: bodyInput.value.gender
    })
    userStore.setUserInfo(res.userInfo as never)
    bodyDialog.value = false
    ElMessage.success('身体数据已保存')
  } finally {
    savingBody.value = false
  }
}

/* ================= 弹窗：昵称 ================= */
const nicknameDialog = ref(false)
const nicknameInput = ref('')
const savingNickname = ref(false)

function openNicknameDialog() {
  nicknameInput.value = user.value?.nickname ?? user.value?.username ?? ''
  nicknameDialog.value = true
}

async function onSaveNickname() {
  const nick = nicknameInput.value.trim()
  if (!nick || nick.length > 20) {
    ElMessage.warning('昵称长度需为 1-20 位')
    return
  }
  savingNickname.value = true
  try {
    const res = await updateProfileApi({ nickname: nick })
    userStore.setUserInfo(res.userInfo as never)
    nicknameDialog.value = false
    ElMessage.success('昵称已更新')
  } finally {
    savingNickname.value = false
  }
}

/* ================= 弹窗：头像 ================= */
const avatarDialog = ref(false)
const pendingAvatar = ref<File | null>(null)
const pendingAvatarUrl = ref('')
const savingAvatar = ref(false)

function openAvatarDialog() {
  pendingAvatar.value = null
  pendingAvatarUrl.value = ''
  avatarDialog.value = true
}

function onPickAvatar(f: { raw?: File }) {
  if (!f.raw) return
  if (f.raw.size > 2 * 1024 * 1024) {
    ElMessage.warning('头像大小不能超过 2MB')
    return
  }
  pendingAvatar.value = f.raw
  if (pendingAvatarUrl.value) URL.revokeObjectURL(pendingAvatarUrl.value)
  pendingAvatarUrl.value = URL.createObjectURL(f.raw)
}

async function onSaveAvatar() {
  if (!pendingAvatar.value) {
    ElMessage.warning('请先选择一张新头像')
    return
  }
  savingAvatar.value = true
  try {
    const up = await uploadAvatar(pendingAvatar.value)
    const res = await updateProfileApi({ avatar: up.path })
    userStore.setUserInfo(res.userInfo as never)
    if (pendingAvatarUrl.value) URL.revokeObjectURL(pendingAvatarUrl.value)
    pendingAvatarUrl.value = ''
    pendingAvatar.value = null
    avatarDialog.value = false
    ElMessage.success('头像已更新')
  } finally {
    savingAvatar.value = false
  }
}

/* ================= 弹窗：数据导出（CSV 分模块 / JSON 全量备份） ================= */
const exportDialog = ref(false)
const exporting = ref(false)

async function doExportCsv(module: string, label: string) {
  exporting.value = true
  try {
    await exportModuleCsv(module, label)
    ElMessage.success(`${label} CSV 已开始下载`)
  } catch {
    ElMessage.error(`${label} 导出失败，请重试`)
  } finally {
    exporting.value = false
  }
}

async function doExportAll() {
  exporting.value = true
  try {
    await exportAllJson()
    ElMessage.success('全量备份已开始下载')
  } catch {
    ElMessage.error('备份导出失败，请重试')
  } finally {
    exporting.value = false
  }
}

/* ================= 弹窗：修改密码（一月冷却） ================= */
const pwdDialog = ref(false)
const oldPwd = ref('')
const newPwd = ref('')
const confirmPwd = ref('')
const savingPwd = ref(false)

function openPwdDialog() {
  oldPwd.value = ''
  newPwd.value = ''
  confirmPwd.value = ''
  pwdDialog.value = true
}

async function onChangePwd() {
  const o = oldPwd.value
  const n = newPwd.value
  if (!o || !n) {
    ElMessage.warning('请填写原密码与新密码')
    return
  }
  if (n.length < 8 || n.length > 16) {
    ElMessage.warning('新密码长度需为 8-16 位')
    return
  }
  if (n === o) {
    ElMessage.warning('新密码不能与原密码相同')
    return
  }
  if (n !== confirmPwd.value) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  if (!cooldown.value.allowed) {
    ElMessage.warning(`距上次修改密码不足一个月（还剩 ${cooldown.value.daysLeft} 天）`)
    return
  }
  savingPwd.value = true
  try {
    await changePasswordApi({ oldPassword: o, newPassword: n })
    ElMessage.success('密码修改成功，请重新登录')
    // 改密成功：后端已撤销该用户所有会话并清 Cookie；前端清本地 token/用户信息 → 跳登录，要求用新密码重登
    userStore.clearLocalAuth()
    router.push('/login')
  } catch {
    /* requestApi 已弹错 */
  } finally {
    savingPwd.value = false
  }
}
</script>

<template>
  <div class="ppf">
    <!-- ===== 用户概要 ===== -->
    <section class="ppf__profile">
      <span class="ppf__avatar" :class="{ 'ppf__avatar--img': avatarUrl }">
        <img v-if="avatarUrl" :src="avatarUrl" alt="头像" />
        <component v-else :is="markRaw(User)" :size="34" />
      </span>
      <div class="ppf__meta">
        <h2 class="ppf__nickname">{{ user?.nickname || user?.username }}</h2>
        <div class="ppf__chips">
          <span class="ppf__chip">@{{ user?.username }}</span>
          <span class="ppf__chip num">加入于 {{ user ? formatDate(user.createdAt) : '-' }}</span>
        </div>
      </div>
      <button class="ppf__logout" type="button" @click="onLogout">
        <component :is="markRaw(LogOut)" :size="15" /> 退出登录
      </button>
    </section>

    <!-- ===== 设置列表 ===== -->
    <section class="ppf__settings">
      <BlockTitle title="设置" hint="点击设置项后弹窗中修改" />

      <div class="ppf__list">
        <!-- 更换头像 -->
        <button class="ppf__item" type="button" @click="openAvatarDialog">
          <span class="ppf__icon"><component :is="markRaw(Camera)" :size="18" /></span>
          <span class="ppf__item-body">
            <span class="ppf__item-title">更换头像</span>
            <span class="ppf__item-desc">png / jpg，不超过 2MB</span>
          </span>
          <span v-if="avatarUrl" class="ppf__thumb"><img :src="avatarUrl" alt="头像" /></span>
          <component :is="markRaw(ChevronRight)" :size="16" class="ppf__arrow" />
        </button>

        <!-- 修改昵称 -->
        <button class="ppf__item" type="button" @click="openNicknameDialog">
          <span class="ppf__icon"><component :is="markRaw(PenLine)" :size="18" /></span>
          <span class="ppf__item-body">
            <span class="ppf__item-title">修改昵称</span>
            <span class="ppf__item-desc">当前昵称：{{ user?.nickname || '未设置' }}</span>
          </span>
          <component :is="markRaw(ChevronRight)" :size="16" class="ppf__arrow" />
        </button>

        <!-- 修改手机号（二期锁定） -->
        <button class="ppf__item ppf__item--lock" type="button" @click="ElMessage.info('手机号暂不支持修改，二期接入短信验证码后开放')">
          <span class="ppf__icon"><component :is="markRaw(Phone)" :size="18" /></span>
          <span class="ppf__item-body">
            <span class="ppf__item-title">修改手机号</span>
            <span class="ppf__item-desc">{{ user ? maskPhone(user.phone) : '-' }}</span>
          </span>
          <span class="ppf__lock"><component :is="markRaw(Lock)" :size="13" /> 二期开放</span>
        </button>

        <!-- 设置目标体重 -->
        <button class="ppf__item" type="button" @click="openTargetDialog">
          <span class="ppf__icon"><component :is="markRaw(Target)" :size="18" /></span>
          <span class="ppf__item-body">
            <span class="ppf__item-title">设置目标体重</span>
            <span class="ppf__item-desc">用于健康「距目标」与首页减重进度</span>
          </span>
          <span class="ppf__value num" :class="targetUnset ? 'is-unset' : ''">{{ targetText }}</span>
          <component :is="markRaw(ChevronRight)" :size="16" class="ppf__arrow" />
        </button>

        <!-- 身体数据（年龄/身高/性别，BMR 二期用） -->
        <button class="ppf__item" type="button" @click="openBodyDialog">
          <span class="ppf__icon"><component :is="markRaw(CalendarRange)" :size="18" /></span>
          <span class="ppf__item-body">
            <span class="ppf__item-title">身体数据</span>
            <span class="ppf__item-desc">年龄 / 身高 / 性别，供基础代谢估算</span>
          </span>
          <span class="ppf__value num">{{ bodyText }}</span>
          <component :is="markRaw(ChevronRight)" :size="16" class="ppf__arrow" />
        </button>

        <!-- 修改密码 -->
        <button class="ppf__item" type="button" @click="openPwdDialog">
          <span class="ppf__icon"><component :is="markRaw(ShieldCheck)" :size="18" /></span>
          <span class="ppf__item-body">
            <span class="ppf__item-title">修改密码</span>
            <span class="ppf__item-desc">{{ pwdDesc }}</span>
          </span>
          <component :is="markRaw(ChevronRight)" :size="16" class="ppf__arrow" />
        </button>

        <!-- 数据导出 -->
        <button class="ppf__item" type="button" @click="exportDialog = true">
          <span class="ppf__icon"><component :is="markRaw(Download)" :size="18" /></span>
          <span class="ppf__item-body">
            <span class="ppf__item-title">数据导出</span>
            <span class="ppf__item-desc">CSV 分模块导出 / JSON 全量备份</span>
          </span>
          <component :is="markRaw(ChevronRight)" :size="16" class="ppf__arrow" />
        </button>
      </div>
    </section>

    <!-- ===== 弹窗：数据导出 ===== -->
    <el-dialog v-model="exportDialog" title="数据导出" width="min(440px,92vw)">
      <div class="ppf__dialog-body">
        <label class="ppf__label">分模块导出 CSV（Excel 可直接打开）</label>
        <div class="ppf__export-grid">
          <button
            v-for="m in EXPORT_MODULES" :key="m.key"
            class="ppf__export-btn" type="button"
            :disabled="exporting"
            @click="doExportCsv(m.key, m.label)"
          >{{ m.label }}</button>
        </div>
        <div class="ppf__export-sep"></div>
        <label class="ppf__label">全量备份（JSON，含记录与字典）</label>
        <el-button type="primary" :loading="exporting" style="width: 100%" @click="doExportAll">下载完整备份</el-button>
        <p class="ppf__export-tip">备份文件包含全部模块的原始记录与食物/动作字典，可用于本地留存或迁移。</p>
      </div>
    </el-dialog>

    <!-- ===== 弹窗：目标体重 ===== -->
    <el-dialog v-model="targetDialog" title="设置目标体重" width="420px">
      <div class="ppf__dialog-body">
        <div class="ppf__field">
          <label class="ppf__label">目标体重 (kg)</label>
          <input v-model="targetInput" class="ppf__input mono" inputmode="decimal" placeholder="如 70.0" />
          <span class="ppf__line">保存后首页「目标进度」与健康「距目标」立即生效</span>
        </div>
        <p v-if="latestWeight != null" class="ppf__target-stat">
          当前最新体重 <b class="num">{{ latestWeight }}</b> kg
          <template v-if="user?.targetWeight != null">
            · 距目标还需
            <b class="num">{{ (latestWeight - user.targetWeight) > 0 ? (latestWeight - user.targetWeight).toFixed(1) : '0.0' }}</b> kg
          </template>
        </p>
        <div class="ppf__dialog-actions">
          <button v-if="user?.targetWeight != null" class="ppf__btn ppf__btn--ghost" type="button" :disabled="savingTarget" @click="onClearTarget">
            清除目标
          </button>
          <button class="ppf__btn ppf__btn--primary" type="button" :disabled="savingTarget" @click="onSaveTarget">
            {{ savingTarget ? '保存中…' : '保存' }}
          </button>
        </div>
      </div>
    </el-dialog>

    <!-- ===== 弹窗：身体数据 ===== -->
    <el-dialog v-model="bodyDialog" title="身体数据" width="420px">
      <div class="ppf__dialog-body">
        <div class="ppf__field">
          <label class="ppf__label">年龄（岁）</label>
          <input v-model="bodyInput.age" class="ppf__input mono" inputmode="numeric" placeholder="如 26" />
        </div>
        <div class="ppf__field">
          <label class="ppf__label">身高 (cm)</label>
          <input v-model="bodyInput.height" class="ppf__input mono" inputmode="decimal" placeholder="如 184.5" />
        </div>
        <div class="ppf__field">
          <label class="ppf__label">性别</label>
          <select v-model="bodyInput.gender" class="ppf__input mono">
            <option value="male">男</option>
            <option value="female">女</option>
          </select>
        </div>
        <div class="ppf__dialog-actions">
          <button class="ppf__btn ppf__btn--primary" type="button" :disabled="savingBody" @click="onSaveBody">
            {{ savingBody ? '保存中…' : '保存' }}
          </button>
        </div>
      </div>
    </el-dialog>

    <!-- ===== 弹窗：昵称 ===== -->
    <el-dialog v-model="nicknameDialog" title="修改昵称" width="420px">
      <div class="ppf__dialog-body">
        <div class="ppf__field">
          <label class="ppf__label">昵称（1-20 位）</label>
          <input v-model="nicknameInput" class="ppf__input" maxlength="20" placeholder="输入新昵称" />
        </div>
        <div class="ppf__dialog-actions">
          <button class="ppf__btn ppf__btn--primary" type="button" :disabled="savingNickname" @click="onSaveNickname">
            {{ savingNickname ? '保存中…' : '保存' }}
          </button>
        </div>
      </div>
    </el-dialog>

    <!-- ===== 弹窗：头像 ===== -->
    <el-dialog v-model="avatarDialog" title="更换头像" width="420px">
      <div class="ppf__dialog-body">
        <div class="ppf__avatar-pick">
          <span class="ppf__avatar-lg">
            <img v-if="pendingAvatarUrl" :src="pendingAvatarUrl" alt="新头像" />
            <img v-else-if="avatarUrl" :src="avatarUrl" alt="头像" />
            <component v-else :is="markRaw(User)" :size="40" />
          </span>
          <el-upload :auto-upload="false" :show-file-list="false" accept="image/png,image/jpeg" :on-change="onPickAvatar">
            <button class="ppf__pick" type="button"><component :is="markRaw(Camera)" :size="14" /> 选择图片</button>
          </el-upload>
        </div>
        <div class="ppf__dialog-actions">
          <button class="ppf__btn ppf__btn--primary" type="button" :disabled="savingAvatar" @click="onSaveAvatar">
            {{ savingAvatar ? '上传中…' : '确认更换' }}
          </button>
        </div>
      </div>
    </el-dialog>

    <!-- ===== 弹窗：修改密码 ===== -->
    <el-dialog v-model="pwdDialog" title="修改密码" width="420px">
      <div class="ppf__dialog-body">
        <div class="ppf__band" :class="cooldown.allowed ? 'ppf__band--ok' : 'ppf__band--warn'">
          <span class="ppf__band-text">
            {{ cooldown.first ? '尚未修改过密码，首次修改免限' : `上次修改：${cooldown.lastText}` }}
            <template v-if="!cooldown.allowed"> · 距可再次修改还剩 <b class="num">{{ cooldown.daysLeft }}</b> 天</template>
          </span>
        </div>

        <div class="ppf__field">
          <label class="ppf__label">原密码</label>
          <input v-model="oldPwd" class="ppf__input" type="password" autocomplete="current-password" placeholder="输入原密码" />
        </div>
        <div class="ppf__field">
          <label class="ppf__label">新密码（8-16 位，不与原密码相同）</label>
          <input v-model="newPwd" class="ppf__input" type="password" autocomplete="new-password" placeholder="输入新密码" />
        </div>
        <div class="ppf__field">
          <label class="ppf__label">确认新密码</label>
          <input v-model="confirmPwd" class="ppf__input" type="password" autocomplete="new-password" placeholder="再次输入新密码" />
        </div>

        <div class="ppf__dialog-actions">
          <button class="ppf__btn ppf__btn--primary" type="button" :disabled="savingPwd || !cooldown.allowed" @click="onChangePwd">
            {{ savingPwd ? '修改中…' : '修改密码' }}
          </button>
        </div>
        <p class="ppf__tip">修改成功后需重新登录，请牢记新密码。</p>
      </div>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use './profile';
</style>