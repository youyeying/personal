<script setup lang="ts">
/**
 * 登录页（组合层）
 * 职责：
 * - 宽窄屏布局切换（全屏加载1号）
 * - 登录/注册模式切换
 * - 登录成功：存 token + 烟花 + 跳转
 * - 注册成功：预填登录表单 + 切回登录
 * 表单/壁纸逻辑在子组件中
 */
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import FullScreenLoading from '@/components/loading/FullScreenLoading.vue'
import Fireworks from '@/components/Fireworks/Fireworks.vue'
import WallpaperCard from './components/WallpaperCard.vue'
import LoginForm from './components/LoginForm.vue'
import RegisterForm from './components/RegisterForm.vue'
import type { AuthUserInfo } from '@/api/auth'
// 用户状态
import { useUserStore } from '@/store/user'
import { setAccessToken } from '@/utils/authToken'

const router = useRouter()
const userStore = useUserStore()

/** 当前模式：login 登录 / register 注册 */
const mode = ref<'login' | 'register'>('login')

/** 烟花组件引用 */
const fireworksRef = ref<InstanceType<typeof Fireworks> | null>(null)

/** 注册成功预填的登录表单（从注册表单带回） */
const prefillLogin = ref<{ username: string; password: string } | null>(null)

/** 切换登录/注册模式（注册成功自动回登录不受清空影响） */
function switchMode(next: 'login' | 'register') {
  mode.value = next
  if (next === 'register') {
    prefillLogin.value = null
  }
}

/** 登录成功：存 accessToken（内存） + 烟花 + 跳首页 */
function handleLoginSuccess(payload: { accessToken: string; userInfo: AuthUserInfo }) {
  setAccessToken(payload.accessToken)
  userStore.setUserInfo(payload.userInfo)
  // 烟花庆祝（播完再跳转，避免被页面卸载截断）
  fireworksRef.value?.play()
  const redirect = router.currentRoute.value.query.redirect as string | undefined
  window.setTimeout(() => {
    router.push(redirect || '/')
  }, 1700)
}

/** 注册成功：预填登录表单 + 切回登录 */
function handleRegisterSuccess(payload: { username: string; password: string }) {
  prefillLogin.value = payload
  mode.value = 'login'
}

/** 当前窗口宽度 */
const windowWidth = ref(window.innerWidth)
/** 是否处于窄屏模式（宽度 < 900px） */
const isNarrow = computed(() => windowWidth.value < 900)

/** 是否显示全屏加载1号 */
const showLoading = ref(false)

/** 窄屏单卡布局是否已生效 */
const singleApplied = ref(false)

/** 当前布局是否应为单卡 */
const isSingleCard = computed(() => singleApplied.value)

let resizeTimer: number | undefined
let swapTimer: number | undefined

/** 立即取消当前加载流程 */
function cancelLoading() {
  if (swapTimer) {
    window.clearTimeout(swapTimer)
    swapTimer = undefined
  }
  showLoading.value = false
}

/** 触发布局重建（宽↔窄） */
function startRebuild() {
  if (showLoading.value) return
  showLoading.value = true
  swapTimer = window.setTimeout(() => {
    singleApplied.value = isNarrow.value
    swapTimer = undefined
  }, 100)
}

/** 全屏加载淡出完成 */
function handleLoadingFadeOut() {
  showLoading.value = false
}

/** 监听窗口宽度变化（竞态处理） */
function handleResize() {
  if (resizeTimer) window.clearTimeout(resizeTimer)
  resizeTimer = window.setTimeout(() => {
    const narrow = window.innerWidth < 900
    const wasNarrow = windowWidth.value < 900
    windowWidth.value = window.innerWidth
    if (narrow !== wasNarrow) {
      cancelLoading()
      startRebuild()
    }
  }, 150)
}

onMounted(() => {
  window.addEventListener('resize', handleResize)
  if (isNarrow.value) {
    swapTimer = window.setTimeout(() => startRebuild(), 200)
  }
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  if (resizeTimer) window.clearTimeout(resizeTimer)
  cancelLoading()
})
</script>

<template>
  <div class="login-page">
    <!-- 右上角主题切换（苹果风格，占位） -->
    <button class="login-page__theme-toggle" type="button" aria-label="切换主题">
      <svg class="login-page__theme-icon" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <circle cx="12" cy="12" r="4" />
        <path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41" />
      </svg>
    </button>

    <!-- 双卡片区域 -->
    <div
      class="login-page__cards"
      :class="{
        'login-page__cards--single': isSingleCard,
        'login-page__cards--register': mode === 'register'
      }"
    >
      <!-- 壁纸浮空层（非单卡时显示） -->
      <WallpaperCard v-if="!isSingleCard" />

      <!-- 注册表单层（仅注册模式渲染，渐隐渐现） -->
      <Transition name="auth-card">
        <RegisterForm
          v-if="mode === 'register'"
          @register-success="handleRegisterSuccess"
          @switch-login="switchMode('login')"
        />
      </Transition>

      <!-- 登录表单层（仅登录模式渲染，渐隐渐现） -->
      <Transition name="auth-card">
        <LoginForm
          v-if="mode === 'login'"
          :prefill-username="prefillLogin?.username ?? ''"
          :prefill-password="prefillLogin?.password ?? ''"
          @login-success="handleLoginSuccess"
          @switch-register="switchMode('register')"
        />
      </Transition>
    </div>

    <!-- 全屏加载1号：布局切换时进入 -->
    <FullScreenLoading
      v-if="showLoading"
      :text="isNarrow ? '页面宽度不足，正在重新构建页面中…' : '正在重新构建页面中…'"
      :duration="800"
      @fade-out="handleLoadingFadeOut"
    />

    <!-- 登录成功烟花 -->
    <Fireworks ref="fireworksRef" type="circle" />
  </div>
</template>

<style lang="scss">
@use './login';
</style>
