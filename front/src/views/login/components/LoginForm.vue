<script setup lang="ts">
/**
 * LoginForm 登录表单
 * 职责：登录数据 + 校验 + 提交请求 + 成功/失败通知
 * 成功时 emit login-success（携带 token/userInfo），跳转交给父级
 */
import { computed, ref, watch } from 'vue'
import { ElNotification } from 'element-plus'
import FloatingInput from '@/components/FloatingInput/FloatingInput.vue'
import BaseButton from '@/components/BaseButton/BaseButton.vue'
import AuthTitle from './AuthTitle.vue'
// 公共表单校验
import { validateUsername, validatePassword } from '@/utils/validators'
// 认证接口
import { login as loginApi } from '@/api/auth'
import type { AuthUserInfo } from '@/api/auth'

const props = withDefaults(
  defineProps<{
    /** 预填用户名（注册成功自动回填） */
    prefillUsername?: string
    /** 预填密码（注册成功自动回填） */
    prefillPassword?: string
  }>(),
  {
    prefillUsername: '',
    prefillPassword: ''
  }
)

const emit = defineEmits<{
  (e: 'login-success', payload: { accessToken: string; userInfo: AuthUserInfo }): void
  (e: 'switch-register'): void
}>()

/** 登录表单数据 */
const form = ref({ username: '', password: '' })

/** 注册成功回填：预填用户名/密码 */
watch(
  () => [props.prefillUsername, props.prefillPassword],
  ([username, password]) => {
    if (username || password) {
      form.value.username = username
      form.value.password = password
    }
  },
  { immediate: true }
)

/** 格式错误（前端即时校验，字段下方显示） */
const usernameError = computed(() => validateUsername(form.value.username))
const passwordError = computed(() => validatePassword(form.value.password))

/** 提交 loading */
const loading = ref(false)

/** 提交登录 */
async function handleSubmit() {
  if (usernameError.value || passwordError.value) return
  loading.value = true
  try {
    const res = await loginApi({
      username: form.value.username,
      password: form.value.password
    })
    // 成功：通知 + 交事件给父级（父级存 token/烟花/跳转）
    ElNotification({
      title: '登录成功',
      message: `欢迎回来，${res.userInfo.nickname || res.userInfo.username}`,
      type: 'success',
      duration: 2000
    })
    emit('login-success', { accessToken: res.accessToken, userInfo: res.userInfo })
  } catch (e) {
    const err = e as Error
    ElNotification({
      title: '登录失败',
      message: err.message || '登录失败，请重试',
      type: 'error',
      duration: 3000
    })
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="login-card login-card--login" aria-label="登录区域">
    <div class="login-card__auth-content">
      <AuthTitle :chars="['登', '录']" />

      <form class="login-card__auth-form" @submit.prevent="handleSubmit">
        <div class="login-card__auth-field">
          <FloatingInput v-model="form.username" label="用户名" :error="usernameError" />
        </div>
        <div class="login-card__auth-field">
          <FloatingInput v-model="form.password" label="密码" type="password" :error="passwordError" />
        </div>

        <BaseButton type="submit" class="login-card__submit" text="登录" :loading="loading">
          <template #icon>
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4" />
              <path d="M10 17l5-5-5-5M15 12H3" />
            </svg>
          </template>
        </BaseButton>
      </form>

      <button class="login-card__switch-link" type="button" @click="emit('switch-register')">
        需要注册？去注册
      </button>
    </div>
  </section>
</template>
